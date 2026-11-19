package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import com.example.listentogether.onGoogleSignInClicked
import com.example.ui.components.AlbumArtImage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

private enum class YouTubeLoginMode {
    GOOGLE_NATIVE,
    WEBVIEW_COOKIE,
    MANUAL_COOKIE
}

/**
 * YouTube Music Login screen styled & powered identically to Play Bersama's
 * native Google Sign-In flow (CredentialManager + Firebase Auth), with optional
 * advanced Cookie / WebView session linking for private YTM playlist sync.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    isVerifying: Boolean,
    currentGoogleUser: FirebaseUser? = null,
    isFirebaseReady: Boolean = true,
    onBack: () -> Unit,
    onGoogleAccountConnected: (name: String, email: String, avatarUrl: String?) -> Unit,
    onAuthStateChanged: () -> Unit = {},
    onShowSnackbar: (String) -> Unit = {},
    onCookieCaptured: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(YouTubeLoginMode.GOOGLE_NATIVE) }

    BackHandler {
        if (mode != YouTubeLoginMode.GOOGLE_NATIVE) {
            mode = YouTubeLoginMode.GOOGLE_NATIVE
        } else {
            onBack()
        }
    }

    when (mode) {
        YouTubeLoginMode.GOOGLE_NATIVE -> {
            YouTubeGoogleAuthHeroScreen(
                isVerifying = isVerifying,
                currentGoogleUser = currentGoogleUser,
                isFirebaseReady = isFirebaseReady,
                onBack = onBack,
                onGoogleAccountConnected = onGoogleAccountConnected,
                onAuthStateChanged = onAuthStateChanged,
                onShowSnackbar = onShowSnackbar,
                onOpenAdvancedWebView = { mode = YouTubeLoginMode.WEBVIEW_COOKIE },
                onOpenManualCookie = { mode = YouTubeLoginMode.MANUAL_COOKIE }
            )
        }
        YouTubeLoginMode.WEBVIEW_COOKIE,
        YouTubeLoginMode.MANUAL_COOKIE -> {
            YouTubeAdvancedCookieScreen(
                isVerifying = isVerifying,
                showManualCookie = mode == YouTubeLoginMode.MANUAL_COOKIE,
                onToggleManual = {
                    mode = if (mode == YouTubeLoginMode.MANUAL_COOKIE) {
                        YouTubeLoginMode.WEBVIEW_COOKIE
                    } else {
                        YouTubeLoginMode.MANUAL_COOKIE
                    }
                },
                onBack = { mode = YouTubeLoginMode.GOOGLE_NATIVE },
                onCookieCaptured = onCookieCaptured
            )
        }
    }
}

@Composable
private fun YouTubeGoogleAuthHeroScreen(
    isVerifying: Boolean,
    currentGoogleUser: FirebaseUser?,
    isFirebaseReady: Boolean,
    onBack: () -> Unit,
    onGoogleAccountConnected: (name: String, email: String, avatarUrl: String?) -> Unit,
    onAuthStateChanged: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onOpenAdvancedWebView: () -> Unit,
    onOpenManualCookie: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val triggerGoogleSignIn: () -> Unit = {
        if (!isFirebaseReady) {
            val msg = "Konfigurasi Google Sign-In belum aktif di build ini. Gunakan opsi WebView / Cookie di bawah."
            authError = msg
            onShowSnackbar(msg)
        } else {
            authError = null
            isSigningIn = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isSigningIn = false
                    onAuthStateChanged()
                    val signedIn = Firebase.auth.currentUser
                    val name = signedIn?.displayName?.takeIf { it.isNotBlank() }
                        ?: signedIn?.email?.substringBefore("@").orEmpty()
                    val email = signedIn?.email.orEmpty()
                    val avatar = signedIn?.photoUrl?.toString()
                    onGoogleAccountConnected(name, email, avatar)
                },
                onAuthError = { msg ->
                    isSigningIn = false
                    authError = msg
                    onShowSnackbar(msg.lineSequence().firstOrNull() ?: msg)
                },
                scope = scope,
                onAuthCancelled = { hint ->
                    isSigningIn = false
                    if (!hint.isNullOrBlank()) {
                        authError = hint
                    }
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp)
    ) {
        // ---- Top Bar (Matches ListenTogetherAuthScreen) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("yt_login_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Kembali"
                )
            }
            Text(
                text = "Akun YouTube Music",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Emblem (identical rhythm to Play Bersama Auth)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Masuk YouTube Music\ndengan Akun Google",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Cukup satu ketukan dengan akun Google perangkatmu — sama cepat dan praktisnya seperti login Play Bersama.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Feature highlight card (identical visual language to Play Bersama)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    YtmFeatureRow(
                        icon = Icons.Rounded.VerifiedUser,
                        text = "Login instan 1-ketuk langsung via Google perangkatmu"
                    )
                    YtmFeatureRow(
                        icon = Icons.Rounded.MusicNote,
                        text = "Profil, avatar & sesi terhubung otomatis dengan Play Bersama"
                    )
                    YtmFeatureRow(
                        icon = Icons.Rounded.Sync,
                        text = "Tersedia opsi Cookie Lanjutan untuk sinkronisasi playlist pribadi"
                    )
                }
            }

            // If user already signed into Google (e.g. from Play Bersama), offer instant 1-tap continuation!
            if (currentGoogleUser != null) {
                val displayName = currentGoogleUser.displayName?.takeIf { it.isNotBlank() }
                    ?: currentGoogleUser.email?.substringBefore("@")
                    ?: "Pengguna Google"

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtImage(
                            url = currentGoogleUser.photoUrl?.toString(),
                            contentDescription = displayName,
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentGoogleUser.email ?: "Akun Google aktif di perangkat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            val busy = isSigningIn || isVerifying

            if (currentGoogleUser != null) {
                val shortName = currentGoogleUser.displayName?.takeIf { it.isNotBlank() }
                    ?: currentGoogleUser.email?.substringBefore("@")
                    ?: "Google"
                Button(
                    onClick = {
                        onGoogleAccountConnected(
                            currentGoogleUser.displayName?.takeIf { it.isNotBlank() } ?: shortName,
                            currentGoogleUser.email.orEmpty(),
                            currentGoogleUser.photoUrl?.toString()
                        )
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("yt_google_continue_button")
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Lanjutkan sebagai $shortName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = triggerGoogleSignIn,
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("yt_google_switch_button")
                ) {
                    Text(
                        text = "Pilih Akun Google Lain",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Button(
                    onClick = triggerGoogleSignIn,
                    enabled = !busy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("yt_google_sign_in_button")
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Masuk dengan Google",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary advanced option for full InnerTube cookie sync (private playlists / explicit streams)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenAdvancedWebView,
                    modifier = Modifier.testTag("open_ytm_webview_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sinkron Cookie YTM (WebView)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onOpenManualCookie,
                    modifier = Modifier.testTag("toggle_manual_cookie_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cookie",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun YtmFeatureRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeAdvancedCookieScreen(
    isVerifying: Boolean,
    showManualCookie: Boolean,
    onToggleManual: () -> Unit,
    onBack: () -> Unit,
    onCookieCaptured: (String) -> Unit
) {
    var manualCookieText by remember { mutableStateOf("") }
    var capturedOnce by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("Sinkron Sesi YouTube Music") }

    val loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(42.dp).testTag("yt_advanced_back")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sesi disimpan lokal di perangkatmu saja",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onToggleManual,
                    modifier = Modifier.testTag("switch_cookie_webview_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showManualCookie) "WebView" else "Manual")
                }
            }
        }

        if (isVerifying) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Memverifikasi sesi YouTube Music…",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        } else if (showManualCookie) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Mode Lanjutan (seperti Metrolist): Tempel header Cookie dari music.youtube.com yang berisi SAPISID untuk sinkronisasi playlist pribadi & lagu Explicit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                OutlinedTextField(
                    value = manualCookieText,
                    onValueChange = { manualCookieText = it },
                    label = { Text("YouTube Music Cookie") },
                    placeholder = { Text("SAPISID=...; SID=...; HSID=...;") },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().testTag("manual_cookie_input")
                )
                Button(
                    onClick = {
                        if (manualCookieText.isNotBlank()) {
                            onCookieCaptured(manualCookieText.trim())
                        }
                    },
                    enabled = manualCookieText.contains("SAPISID="),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_manual_cookie")
                ) {
                    Text("Simpan & Sambungkan Sesi")
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val currentUrl = url.orEmpty()
                                    if (currentUrl.startsWith("https://music.youtube.com")) {
                                        pageTitle = "YouTube Music Tersambung"
                                        val cookie = CookieManager.getInstance()
                                            .getCookie("https://music.youtube.com")
                                            .orEmpty()
                                        if (!capturedOnce && (cookie.contains("SAPISID=") || cookie.contains("__Secure-3PAPISID="))) {
                                            capturedOnce = true
                                            onCookieCaptured(cookie)
                                        }
                                    }
                                }
                            }
                            loadUrl(loginUrl)
                        }
                    },
                    onRelease = { webView ->
                        runCatching {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                )
            }
        }
    }
}
