package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Track

@Entity(tableName = "saved_tracks")
data class SavedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String,
    val isLocal: Boolean,
    val isExplicit: Boolean,
    val releaseYear: String,
    val isLiked: Boolean = false,
    val likedAt: Long = 0L,
    val isDownloaded: Boolean = false,
    val localDownloadPath: String? = null,
    val downloadedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        genre = genre,
        isLocal = isLocal,
        isExplicit = isExplicit,
        releaseYear = releaseYear,
        isDownloaded = isDownloaded,
        localDownloadPath = localDownloadPath
    )

    companion object {
        fun fromTrack(
            track: Track,
            isLiked: Boolean = false,
            likedAt: Long = 0L,
            isDownloaded: Boolean = track.isDownloaded,
            localDownloadPath: String? = track.localDownloadPath,
            downloadedAt: Long = if (track.isDownloaded) System.currentTimeMillis() else 0L
        ): SavedTrackEntity = SavedTrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            streamUrl = track.streamUrl,
            durationMs = track.durationMs,
            genre = track.genre,
            isLocal = track.isLocal,
            isExplicit = track.isExplicit,
            releaseYear = track.releaseYear,
            isLiked = isLiked,
            likedAt = likedAt,
            isDownloaded = isDownloaded,
            localDownloadPath = localDownloadPath,
            downloadedAt = downloadedAt
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val streamUrl: String?,
    val durationMs: Long,
    val genre: String,
    val isLocal: Boolean,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toTrack(): Track = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        genre = genre,
        isLocal = isLocal
    )
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
