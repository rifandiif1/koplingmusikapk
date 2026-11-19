package com.example.listentogether

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reads the current APK's signing certificate SHA-1 (uppercase hex with colons)
 * for diagnosing OAuth SHA-1 mismatches when Google Sign-In fails on externally built APKs.
 */
@Suppress("DEPRECATION")
fun getInstalledAppSha1(context: Context): String? = runCatching {
    val pm = context.packageManager
    val pkg = context.packageName
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signingInfo = info.signingInfo ?: return null
        if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
        else signingInfo.signingCertificateHistory
    } else {
        pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
    }
    val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
    val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
    digest.joinToString(":") { "%02X".format(it) }
}.getOrNull()

private fun buildFriendlyAuthError(context: Context, rawError: Throwable): String {
    val msg = rawError.localizedMessage ?: rawError.message ?: "Gagal masuk dengan Google"
    val looksLikeOAuthConfigError = msg.contains("16", ignoreCase = true) ||
        msg.contains("28444", ignoreCase = true) ||
        msg.contains("reauth", ignoreCase = true) ||
        msg.contains("Developer", ignoreCase = true) ||
        msg.contains("10:", ignoreCase = true) ||
        msg.contains("SHA", ignoreCase = true)

    return if (looksLikeOAuthConfigError) {
        val sha1 = getInstalledAppSha1(context)
        if (!sha1.isNullOrBlank()) {
            "Verifikasi OAuth Google gagal ($msg).\n\nPastikan SHA-1 APK ini sudah terdaftar di Firebase / gunakan debug.keystore.base64 asli proyek:\nSHA-1 APK: $sha1"
        } else {
            "Verifikasi OAuth Google gagal ($msg). Pastikan google-services.json dan keystore (SHA-1) cocok dengan proyek Firebase."
        }
    } else {
        "Gagal masuk dengan Google: $msg"
    }
}

/**
 * Silent Auto-Sign-In using GetGoogleIdOption ONLY in its GetCredentialRequest.
 * Only swallows silent credential-lookup misses; surfaces real Firebase Auth errors so failures are never invisible.
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    onAuthError: (String) -> Unit = {},
    scope: CoroutineScope
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onUnauthenticated()
        return
    }
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    } ?: run {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        val activity = context.findActivity() ?: run {
            onUnauthenticated()
            return@launch
        }
        val cred = try {
            credentialManager.getCredential(activity, request).credential
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Silent Google auto-sign-in cancelled: ${e.message}", e)
            onUnauthenticated()
            return@launch
        } catch (e: NoCredentialException) {
            Log.d("Auth", "No previously authorized Google account for auto-sign-in")
            onUnauthenticated()
            return@launch
        } catch (e: Exception) {
            Log.w("Auth", "Silent Google auto-sign-in unavailable: ${e.message}", e)
            onUnauthenticated()
            return@launch
        }

        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } catch (e: Exception) {
                Log.e("Auth", "Firebase signInWithCredential failed during auto-sign-in", e)
                onAuthError(buildFriendlyAuthError(context, e))
                onUnauthenticated()
            }
        } else {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Google Sign-In using GetSignInWithGoogleOption ONLY in its GetCredentialRequest.
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (String?) -> Unit = {}
) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        onAuthError("Konfigurasi Firebase belum terpasang pada build ini (google-services.json belum disertakan).")
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    } ?: run {
        onAuthError("Konfigurasi Login Google belum tersedia (default_web_client_id kosong — pastikan app/google-services.json terpasang saat build).")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Tidak dapat membuka dialog Login Google.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Tipe kredensial Google tidak dikenali (${cred.type}).")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val rawMsg = e.errorMessage?.toString() ?: e.message.orEmpty()
            val sha1 = getInstalledAppSha1(context)
            // Google Play Services sometimes wraps SHA-1/OAuth failures as GetCredentialCancellationException
            val hint = if (
                rawMsg.contains("16", ignoreCase = true) ||
                rawMsg.contains("28444", ignoreCase = true) ||
                rawMsg.contains("reauth", ignoreCase = true) ||
                rawMsg.contains("Developer", ignoreCase = true)
            ) {
                buildFriendlyAuthError(context, e)
            } else if (!sha1.isNullOrBlank()) {
                "Login dibatalkan atau ditolak oleh Google Play Services. Jika tadi kamu sudah memilih akun tapi tidak masuk, kemungkinan SHA-1 tanda tangan APK tidak cocok dengan Firebase.\nSHA-1 APK ini: $sha1"
            } else {
                null
            }
            onAuthCancelled(hint)
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Google Credential Manager error: ${e.type} - ${e.errorMessage}", e)
            onAuthError(buildFriendlyAuthError(context, e))
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(buildFriendlyAuthError(context, e))
        }
    }
}

/**
 * Safe Sign-Out inside NonCancellable: run teardown callback BEFORE signing out to avoid PERMISSION_DENIED races.
 */
fun signOutGoogle(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onBeforeSignOut: suspend () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            runCatching { onBeforeSignOut() }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
