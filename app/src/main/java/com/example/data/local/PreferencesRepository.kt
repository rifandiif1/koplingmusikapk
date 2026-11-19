package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentPreset
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.PlayerArtStyle
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.koplingfyDataStore by preferencesDataStore(name = "koplingfy_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val PLAYER_ART_STYLE = stringPreferencesKey("player_art_style")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val CUSTOM_BANDS = stringPreferencesKey("custom_bands")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val HIGH_QUALITY = booleanPreferencesKey("high_quality")
        val GAPLESS = booleanPreferencesKey("gapless")
    }

    val settingsFlow: Flow<AudioSettings> = context.koplingfyDataStore.data.map { prefs ->
        val themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
        val accentPreset = prefs[Keys.ACCENT_PRESET]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
            ?: AccentPreset.EMERALD
        val artStyle = prefs[Keys.PLAYER_ART_STYLE]?.let { runCatching { PlayerArtStyle.valueOf(it) }.getOrNull() }
            ?: PlayerArtStyle.ROUNDED
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
            eqPreset = eqPreset,
            customBandsDb = customBands,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            playbackPitch = prefs[Keys.PLAYBACK_PITCH] ?: 1.0f,
            highQualityStream = prefs[Keys.HIGH_QUALITY] ?: true,
            gaplessPlayback = prefs[Keys.GAPLESS] ?: true
        )
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

    suspend fun setPlaybackSpeed(speed: Float) {
        context.koplingfyDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setPlaybackPitch(pitch: Float) {
        context.koplingfyDataStore.edit { it[Keys.PLAYBACK_PITCH] = pitch.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setHighQualityStream(enabled: Boolean) {
        context.koplingfyDataStore.edit { it[Keys.HIGH_QUALITY] = enabled }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.koplingfyDataStore.edit { it[Keys.GAPLESS] = enabled }
    }
}
