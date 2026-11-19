package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentPreset
import com.example.model.AudioQuality
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.PlayerArtStyle
import com.example.model.PlayerBackgroundStyle
import com.example.model.ThemeMode
import com.example.model.YouTubeAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.koplingfyDataStore by preferencesDataStore(name = "koplingfy_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val PLAYER_ART_STYLE = stringPreferencesKey("player_art_style")
        val PLAYER_BG_STYLE = stringPreferencesKey("player_bg_style")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val CUSTOM_BANDS = stringPreferencesKey("custom_bands")
        val BASS_BOOST = floatPreferencesKey("bass_boost")
        val NORMALIZE_AUDIO = booleanPreferencesKey("normalize_audio")
        val AUTOPLAY_RADIO = booleanPreferencesKey("autoplay_radio")
        val KEEP_SCREEN_ON_LYRICS = booleanPreferencesKey("keep_screen_on_lyrics")
        val LYRICS_FONT_SCALE = floatPreferencesKey("lyrics_font_scale")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val HIGH_QUALITY = booleanPreferencesKey("high_quality")
        val GAPLESS = booleanPreferencesKey("gapless")

        // YouTube Music Account (Metrolist InnerTube auth)
        val YTM_COOKIE = stringPreferencesKey("ytm_cookie")
        val YTM_VISITOR_DATA = stringPreferencesKey("ytm_visitor_data")
        val YTM_ACCOUNT_NAME = stringPreferencesKey("ytm_account_name")
        val YTM_ACCOUNT_EMAIL = stringPreferencesKey("ytm_account_email")
        val YTM_ACCOUNT_AVATAR = stringPreferencesKey("ytm_account_avatar")
    }

    val settingsFlow: Flow<AudioSettings> = context.koplingfyDataStore.data.map { prefs ->
        val themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
        val accentPreset = prefs[Keys.ACCENT_PRESET]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
            ?: AccentPreset.EMERALD
        val artStyle = prefs[Keys.PLAYER_ART_STYLE]?.let { runCatching { PlayerArtStyle.valueOf(it) }.getOrNull() }
            ?: PlayerArtStyle.ROUNDED
        val bgStyle = prefs[Keys.PLAYER_BG_STYLE]?.let { runCatching { PlayerBackgroundStyle.valueOf(it) }.getOrNull() }
            ?: PlayerBackgroundStyle.GRADIENT
        val quality = prefs[Keys.AUDIO_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
            ?: AudioQuality.HIGH
        val eqPreset = prefs[Keys.EQ_PRESET]?.let { runCatching { EqPreset.valueOf(it) }.getOrNull() }
            ?: EqPreset.FLAT
        val customBands = prefs[Keys.CUSTOM_BANDS]
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 }
            ?: eqPreset.bandsDb

        AudioSettings(
            themeMode = themeMode,
            accentPreset = accentPreset,
            playerArtStyle = artStyle,
            playerBackgroundStyle = bgStyle,
            audioQuality = quality,
            eqPreset = eqPreset,
            customBandsDb = customBands,
            bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 0f,
            normalizeAudio = prefs[Keys.NORMALIZE_AUDIO] ?: true,
            autoplayRadio = prefs[Keys.AUTOPLAY_RADIO] ?: true,
            keepScreenOnLyrics = prefs[Keys.KEEP_SCREEN_ON_LYRICS] ?: true,
            lyricsFontScale = prefs[Keys.LYRICS_FONT_SCALE] ?: 1.0f,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            playbackPitch = prefs[Keys.PLAYBACK_PITCH] ?: 1.0f,
            highQualityStream = prefs[Keys.HIGH_QUALITY] ?: true,
            gaplessPlayback = prefs[Keys.GAPLESS] ?: true
        )
    }

    val accountFlow: Flow<YouTubeAccount> = context.koplingfyDataStore.data.map { prefs ->
        YouTubeAccount(
            cookie = prefs[Keys.YTM_COOKIE].orEmpty(),
            visitorData = prefs[Keys.YTM_VISITOR_DATA].orEmpty(),
            name = prefs[Keys.YTM_ACCOUNT_NAME].orEmpty(),
            email = prefs[Keys.YTM_ACCOUNT_EMAIL].orEmpty(),
            avatarUrl = prefs[Keys.YTM_ACCOUNT_AVATAR]
        )
    }

    suspend fun saveYouTubeAccount(account: YouTubeAccount) {
        context.koplingfyDataStore.edit { prefs ->
            prefs[Keys.YTM_COOKIE] = account.cookie
            prefs[Keys.YTM_VISITOR_DATA] = account.visitorData
            prefs[Keys.YTM_ACCOUNT_NAME] = account.name
            prefs[Keys.YTM_ACCOUNT_EMAIL] = account.email
            if (account.avatarUrl != null) {
                prefs[Keys.YTM_ACCOUNT_AVATAR] = account.avatarUrl
            } else {
                prefs.remove(Keys.YTM_ACCOUNT_AVATAR)
            }
        }
    }

    suspend fun clearYouTubeAccount() {
        context.koplingfyDataStore.edit { prefs ->
            prefs.remove(Keys.YTM_COOKIE)
            prefs.remove(Keys.YTM_VISITOR_DATA)
            prefs.remove(Keys.YTM_ACCOUNT_NAME)
            prefs.remove(Keys.YTM_ACCOUNT_EMAIL)
            prefs.remove(Keys.YTM_ACCOUNT_AVATAR)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.koplingfyDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPreset(accent: AccentPreset) {
        context.koplingfyDataStore.edit { it[Keys.ACCENT_PRESET] = accent.name }
    }

    suspend fun setPlayerArtStyle(style: PlayerArtStyle) {
        context.koplingfyDataStore.edit { it[Keys.PLAYER_ART_STYLE] = style.name }
    }

    suspend fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) {
        context.koplingfyDataStore.edit { it[Keys.PLAYER_BG_STYLE] = style.name }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.koplingfyDataStore.edit {
            it[Keys.AUDIO_QUALITY] = quality.name
            it[Keys.HIGH_QUALITY] = quality == AudioQuality.HIGH
        }
    }

    suspend fun setEqPreset(preset: EqPreset) {
        context.koplingfyDataStore.edit {
            it[Keys.EQ_PRESET] = preset.name
            it[Keys.CUSTOM_BANDS] = preset.bandsDb.joinToString(",")
        }
    }

    suspend fun setCustomBands(bands: List<Float>) {
        context.koplingfyDataStore.edit {
            it[Keys.CUSTOM_BANDS] = bands.joinToString(",")
        }
    }

    suspend fun setBassBoost(strength: Float) {
        context.koplingfyDataStore.edit { it[Keys.BASS_BOOST] = strength.coerceIn(0f, 1f) }
    }

    suspend fun setNormalizeAudio(enabled: Boolean) {
        context.koplingfyDataStore.edit { it[Keys.NORMALIZE_AUDIO] = enabled }
    }

    suspend fun setAutoplayRadio(enabled: Boolean) {
        context.koplingfyDataStore.edit { it[Keys.AUTOPLAY_RADIO] = enabled }
    }

    suspend fun setKeepScreenOnLyrics(enabled: Boolean) {
        context.koplingfyDataStore.edit { it[Keys.KEEP_SCREEN_ON_LYRICS] = enabled }
    }

    suspend fun setLyricsFontScale(scale: Float) {
        context.koplingfyDataStore.edit { it[Keys.LYRICS_FONT_SCALE] = scale.coerceIn(0.8f, 1.4f) }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.koplingfyDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setPlaybackPitch(pitch: Float) {
        context.koplingfyDataStore.edit { it[Keys.PLAYBACK_PITCH] = pitch.coerceIn(0.75f, 1.5f) }
    }

    suspend fun setHighQualityStream(enabled: Boolean) {
        context.koplingfyDataStore.edit {
            it[Keys.HIGH_QUALITY] = enabled
            it[Keys.AUDIO_QUALITY] = if (enabled) AudioQuality.HIGH.name else AudioQuality.NORMAL.name
        }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.koplingfyDataStore.edit { it[Keys.GAPLESS] = enabled }
    }
}
