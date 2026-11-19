package com.example.listentogether

import androidx.annotation.Keep
import com.example.model.Track
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class ListenRoom(
    @DocumentId val roomCode: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val roomName: String = "",
    val status: String = "active",
    val guestsCanControl: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val currentTrackId: String = "",
    val currentTrackTitle: String = "",
    val currentTrackArtist: String = "",
    val currentTrackAlbum: String = "",
    val currentTrackArtwork: String = "",
    val currentTrackDurationMs: Long = 0L,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val syncSequence: Long = 0L,
    val queueTracks: List<Map<String, Any>> = emptyList(),
    val lastActionBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun currentTrackOrNull(): Track? {
        if (currentTrackId.isBlank() || currentTrackTitle.isBlank()) return null
        return Track(
            id = currentTrackId,
            title = currentTrackTitle,
            artist = currentTrackArtist.ifBlank { "Artis Tidak Diketahui" },
            album = currentTrackAlbum,
            artworkUrl = currentTrackArtwork.ifBlank { null },
            streamUrl = null,
            durationMs = currentTrackDurationMs
        )
    }

    fun parsedParticipants(): List<RoomParticipant> {
        val byUid = linkedMapOf<String, String>()
        memberNames.forEach { entry ->
            val sep = entry.indexOf('|')
            if (sep > 0) {
                val uid = entry.substring(0, sep)
                val name = entry.substring(sep + 1).ifBlank { "Pendengar" }
                byUid[uid] = name
            }
        }
        return memberUids.map { uid ->
            RoomParticipant(
                uid = uid,
                displayName = byUid[uid] ?: if (uid == hostUid) hostName.ifBlank { "Host" } else "Pendengar",
                isHost = uid == hostUid
            )
        }
    }

    fun parsedQueue(): List<RoomQueueTrack> {
        return queueTracks.mapNotNull { raw ->
            val trackId = raw["trackId"] as? String ?: return@mapNotNull null
            val title = raw["title"] as? String ?: return@mapNotNull null
            RoomQueueTrack(
                trackId = trackId,
                title = title,
                artist = (raw["artist"] as? String).orEmpty(),
                album = (raw["album"] as? String).orEmpty(),
                artworkUrl = (raw["artworkUrl"] as? String).orEmpty(),
                durationMs = (raw["durationMs"] as? Number)?.toLong() ?: 0L,
                addedByName = (raw["addedByName"] as? String).orEmpty(),
                addedByUid = (raw["addedByUid"] as? String).orEmpty()
            )
        }
    }
}

data class RoomParticipant(
    val uid: String,
    val displayName: String,
    val isHost: Boolean
)

data class RoomQueueTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val addedByName: String = "",
    val addedByUid: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "trackId" to trackId.take(128),
        "title" to title.take(200),
        "artist" to artist.take(200),
        "album" to album.take(200),
        "artworkUrl" to artworkUrl.take(600),
        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
        "addedByName" to addedByName.take(60),
        "addedByUid" to addedByUid.take(128)
    )

    fun toTrack(): Track = Track(
        id = trackId,
        title = title,
        artist = artist.ifBlank { "Artis Tidak Diketahui" },
        album = album,
        artworkUrl = artworkUrl.ifBlank { null },
        streamUrl = null,
        durationMs = durationMs
    )
}

@Keep
@IgnoreExtraProperties
data class RecentRoomEntry(
    @DocumentId val roomCode: String = "",
    val userId: String = "",
    val roomName: String = "",
    val hostName: String = "",
    val role: String = "guest",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface ListenUiState<out T> {
    data object Loading : ListenUiState<Nothing>
    data class Success<T>(val data: T) : ListenUiState<T>
    data class Error(val message: String) : ListenUiState<Nothing>
}
