package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

// Tracks whether a debug token was explicitly injected by the runtime harness
// so physical devices running an exported debug APK without an injected token
// use reCAPTCHA Enterprise attestation instead of an unregistered random UUID.
private const val META_PREFS_NAME = "koplingfy_appcheck_meta"
private const val META_INJECTED_DEBUG_TOKEN_KEY = "injected_debug_token"

@Volatile
private var appCheckInstalledMode: String? = null

@Volatile
var lastAppCheckDiagnostic: String? = null
    private set

private fun isProbablyEmulator(): Boolean {
    val fp = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    return fp.startsWith("generic") ||
        fp.contains("emulator") ||
        (fp.contains("test-keys") && (model.contains("sdk") || product.contains("sdk"))) ||
        model.contains("google_sdk") ||
        model.contains("emulator") ||
        model.contains("android sdk built for") ||
        model.contains("cuttlefish") ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu") ||
        hardware.contains("cutf") ||
        product.contains("sdk_gphone") ||
        product.contains("emulator") ||
        product.contains("vsoc") ||
        product.contains("cf_x86")
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()

        // Avoid redundant provider re-installation on Activity recreation unless a new debug token arrived
        if (appCheckInstalledMode != null && intentDebugToken.isNullOrEmpty()) {
            return
        }

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val debugPrefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val metaPrefs = context.getSharedPreferences(
                META_PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val savedInjectedToken = metaPrefs.getString(META_INJECTED_DEBUG_TOKEN_KEY, null)?.trim()
            val existingSdkDebugToken = debugPrefs.getString(DEBUG_SECRET_KEY, null)?.trim()

            when {
                !intentDebugToken.isNullOrEmpty() -> {
                    // Synchronous commit() is required before DebugAppCheckProviderFactory reads it.
                    debugPrefs.edit()
                        .putString(DEBUG_SECRET_KEY, intentDebugToken)
                        .commit()
                    metaPrefs.edit()
                        .putString(META_INJECTED_DEBUG_TOKEN_KEY, intentDebugToken)
                        .commit()
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    appCheckInstalledMode = "debug_intent"
                    Log.d(TAG, "DebugAppCheckProviderFactory installed from Intent extra.")
                }

                !savedInjectedToken.isNullOrEmpty() -> {
                    if (existingSdkDebugToken != savedInjectedToken) {
                        debugPrefs.edit()
                            .putString(DEBUG_SECRET_KEY, savedInjectedToken)
                            .commit()
                    }
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    appCheckInstalledMode = "debug_saved"
                    Log.d(TAG, "DebugAppCheckProviderFactory installed using saved injected token.")
                }

                isProbablyEmulator() && !existingSdkDebugToken.isNullOrEmpty() -> {
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    appCheckInstalledMode = "debug_emulator_store"
                    Log.d(TAG, "DebugAppCheckProviderFactory installed using existing emulator debug secret.")
                }

                !isProbablyEmulator() && installRecaptchaProvider(context, appCheck) -> {
                    appCheckInstalledMode = "recaptcha_device"
                }

                else -> {
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    appCheckInstalledMode = "debug_fallback"
                    Log.w(TAG, "DebugAppCheckProviderFactory installed as fallback.")
                }
            }
        } else {
            if (installRecaptchaProvider(context, appCheck)) {
                appCheckInstalledMode = "recaptcha_release"
            }
        }

        // Enable auto-refresh and trigger an immediate non-blocking warmup so reCAPTCHA Enterprise /
        // Debug App Check finishes attestation before the user triggers Translate or Song Meaning.
        appCheck.setTokenAutoRefreshEnabled(true)
        appCheck.getAppCheckToken(false)
            .addOnSuccessListener { token ->
                if (token.token.isNotBlank()) {
                    lastAppCheckDiagnostic = null
                    Log.d(TAG, "App Check warmup token acquired successfully ($appCheckInstalledMode).")
                }
            }
            .addOnFailureListener { err ->
                lastAppCheckDiagnostic = err.localizedMessage
                Log.w(TAG, "App Check warmup failed ($appCheckInstalledMode): ${err.localizedMessage}")
            }
    } catch (e: Exception) {
        lastAppCheckDiagnostic = e.localizedMessage
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context, appCheck: FirebaseAppCheck): Boolean {
    val siteKey = resolveRecaptchaSiteKey(context)
    if (siteKey.isNullOrBlank()) {
        Log.e(TAG, "recaptcha_site_key string resource is missing or blank.")
        return false
    }
    appCheck.installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    runCatching {
        val direct = context.getString(R.string.recaptcha_site_key).trim()
        if (direct.isNotBlank()) return direct
    }
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return runCatching { context.getString(resId).trim() }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Awaits a valid App Check token (with optional force refresh) before invoking Firebase AI Logic
 * so cold-start reCAPTCHA Enterprise initialization never races with the first AI request.
 * Returns null if attested successfully, or a short diagnostic message if attestation failed.
 */
suspend fun ensureAppCheckTokenReady(forceRefresh: Boolean = false): String? {
    return withTimeoutOrNull(8_000L) {
        suspendCancellableCoroutine { cont ->
            try {
                FirebaseAppCheck.getInstance()
                    .getAppCheckToken(forceRefresh)
                    .addOnSuccessListener { result ->
                        if (result.token.isNotBlank()) {
                            lastAppCheckDiagnostic = null
                            if (cont.isActive) cont.resume(null)
                        } else {
                            val msg = "Token App Check kosong ($appCheckInstalledMode)"
                            lastAppCheckDiagnostic = msg
                            if (cont.isActive) cont.resume(msg)
                        }
                    }
                    .addOnFailureListener { err ->
                        val msg = err.localizedMessage ?: err.javaClass.simpleName
                        lastAppCheckDiagnostic = msg
                        Log.w(TAG, "ensureAppCheckTokenReady(force=$forceRefresh) failed: $msg")
                        if (cont.isActive) cont.resume(msg)
                    }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                lastAppCheckDiagnostic = msg
                if (cont.isActive) cont.resume(msg)
            }
        }
    }
}
