package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

// Tracks whether a debug token was explicitly injected by the runtime harness
// so physical devices running an exported debug APK without an injected token
// can use reCAPTCHA Enterprise attestation instead of an unregistered random UUID.
private const val META_PREFS_NAME = "koplingfy_appcheck_meta"
private const val META_INJECTED_DEBUG_TOKEN_KEY = "injected_debug_token"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
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

            val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            val savedInjectedToken = metaPrefs.getString(META_INJECTED_DEBUG_TOKEN_KEY, null)?.trim()

            when {
                !intentDebugToken.isNullOrEmpty() -> {
                    // Synchronous commit() is required before DebugAppCheckProviderFactory reads it.
                    debugPrefs.edit()
                        .putString(DEBUG_SECRET_KEY, intentDebugToken)
                        .commit()
                    metaPrefs.edit()
                        .putString(META_INJECTED_DEBUG_TOKEN_KEY, intentDebugToken)
                        .commit()
                    Log.d(TAG, "App Check debug secret configured from Intent extra.")

                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "DebugAppCheckProviderFactory installed.")
                }

                !savedInjectedToken.isNullOrEmpty() -> {
                    if (debugPrefs.getString(DEBUG_SECRET_KEY, null) != savedInjectedToken) {
                        debugPrefs.edit()
                            .putString(DEBUG_SECRET_KEY, savedInjectedToken)
                            .commit()
                    }
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "DebugAppCheckProviderFactory installed using saved injected token.")
                }

                else -> {
                    Log.w(TAG, "No debug token found in Intent extra; checking reCAPTCHA site key.")
                    if (!installRecaptchaProvider(context)) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            DebugAppCheckProviderFactory.getInstance()
                        )
                        Log.d(TAG, "DebugAppCheckProviderFactory installed (fallback).")
                    }
                }
            }
        } else {
            installRecaptchaProvider(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun installRecaptchaProvider(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId).trim()
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
