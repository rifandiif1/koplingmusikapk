package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String = "Music",
    val isLocal: Boolean = false,
    val isExplicit: Boolean = false,
    val releaseYear: String = "",
    val rank: Int? = null
)

@Immutable
data class AlbumSummary(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val genre: String = "",
    val releaseYear: String = "",
    val trackCount: Int = 0
)

@Immutable
data class ArtistSummary(
    val id: String,
    val name: String,
    val artworkUrl: String?,
    val genre: String = "Artist"
)

@Immutable
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String,
    val trackCount: Int,
    val coverUrl: String?,
    val createdAt: Long
)

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

@Immutable
data class LyricsState(
    val isLoading: Boolean = false,
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val isInstrumental: Boolean = false,
    val error: String? = null
)

@Immutable
data class MoodFilter(
    val id: String,
    val label: String,
    val query: String,
    val subtitle: String,
    val startColor: Long,
    val endColor: Long
)

enum class RepeatMode {
    OFF, ALL, ONE
}

enum class ThemeMode(val label: String, val subtitle: String) {
    DARK("Obsidian Studio", "Gelap elegan dengan kedalaman kaca"),
    OLED("Pure OLED Black", "Hitam pekat hemat baterai AMOLED"),
    LIGHT("Alabaster Light", "Terang bersih dan kontras tajam")
}

enum class AccentPreset(val label: String, val hexColor: Long) {
    EMERALD("Electric Jade", 0xFF00E5A0),
    CORAL("Sunset Coral", 0xFFFF5C6C),
    VIOLET("Royal Violet", 0xFF8B7BFF),
    AMBER("Champagne Gold", 0xFFF5B942)
}

enum class PlayerArtStyle(val label: String, val subtitle: String) {
    ROUNDED("Modern Cover", "Kartu sampul lebar dengan cahaya ambien"),
    VINYL("Audiophile Vinyl", "Piringan hitam berputar klasik")
}

enum class EqPreset(val label: String, val bandsDb: List<Float>) {
    FLAT("Studio Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Deep Bass", listOf(6f, 4f, 0f, 1f, 2f)),
    VOCAL("Vocal Clarity", listOf(-2f, 1f, 5f, 4f, 1f)),
    ELECTRONIC("Club Pulse", listOf(5f, 3f, -1f, 3f, 5f)),
    ACOUSTIC("Warm Acoustic", listOf(2f, 3f, 2f, 3f, 1f)),
    LATE_NIGHT("Midnight Chill", listOf(3f, 1f, -1f, 1f, -2f))
}

@Immutable
data class AudioSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.EMERALD,
    val playerArtStyle: PlayerArtStyle = PlayerArtStyle.ROUNDED,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val customBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val highQualityStream: Boolean = true,
    val gaplessPlayback: Boolean = true
)

@Immutable
data class TrackStatItem(
    val track: Track,
    val playCount: Int
)

@Immutable
data class ArtistStatItem(
    val artist: String,
    val playCount: Int,
    val artworkUrl: String?
)

@Immutable
data class ListeningStats(
    val totalPlays: Int = 0,
    val totalMinutes: Int = 0,
    val uniqueTracks: Int = 0,
    val topTracks: List<TrackStatItem> = emptyList(),
    val topArtists: List<ArtistStatItem> = emptyList(),
    val dailyPlays: List<Pair<String, Int>> = emptyList()
)

sealed interface CollectionDetailType {
    data object LikedSongs : CollectionDetailType
    data object History : CollectionDetailType
    data object LocalAudio : CollectionDetailType
    data class CustomPlaylist(val playlistId: Long) : CollectionDetailType
    data class MoodCollection(val mood: MoodFilter) : CollectionDetailType
    data class ArtistCollection(val artist: ArtistSummary) : CollectionDetailType
    data class AlbumCollection(val album: AlbumSummary) : CollectionDetailType
}

object KoplingfyMoods {
    val allMoods = listOf(
        MoodFilter("all", "Untuk Kamu", "top hits 2025", "Kurasi terbaik hari ini", 0xFF00C98D, 0xFF0A473A),
        MoodFilter("indo", "Pop Indo", "pop indonesia hits", "Hits nusantara terpopuler", 0xFFE63946, 0xFF4A1021),
        MoodFilter("chill", "Santai", "lofi chill acoustic", "Relaksasi & suasana tenang", 0xFF2A9D8F, 0xFF143542),
        MoodFilter("focus", "Fokus", "instrumental piano focus", "Musik kerja & belajar mendalam", 0xFF4361EE, 0xFF1B1F4A),
        MoodFilter("energy", "Energi", "electronic dance workout", "Pompa semangat & olahraga", 0xFFFF6B35, 0xFF5C1E14),
        MoodFilter("night", "Malam", "rnb soul midnight", "Nuansa malam yang hangat", 0xFF7B2CBF, 0xFF240B42),
        MoodFilter("rock", "Indie & Rock", "indie rock alternative", "Gitar ekspresif & alternatif", 0xFFF4A261, 0xFF4D2B12),
        MoodFilter("jazz", "Jazz Lounge", "smooth jazz lounge", "Alunan elegan berkualitas tinggi", 0xFFD4A017, 0xFF3E2D08)
    )
}
