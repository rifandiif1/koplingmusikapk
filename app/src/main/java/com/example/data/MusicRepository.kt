package com.example.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.KoplingfyDao
import com.example.data.local.PlayHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SavedTrackEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.remote.KoplingfyNetwork
import com.example.data.remote.LrcParser
import com.example.data.remote.toAlbumSummary
import com.example.data.remote.toArtistSummary
import com.example.data.remote.toTrack
import com.example.model.AlbumSummary
import com.example.model.ArtistStatItem
import com.example.model.ArtistSummary
import com.example.model.ListeningStats
import com.example.model.LyricsState
import com.example.model.PlaylistSummary
import com.example.model.Track
import com.example.model.TrackStatItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HomeFeedResult(
    val chartTracks: List<Track>,
    val quickPicks: List<Track>,
    val featuredAlbums: List<AlbumSummary>,
    val featuredArtists: List<ArtistSummary>,
    val errorMessage: String? = null
)

data class SearchResultBundle(
    val tracks: List<Track>,
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
    val errorMessage: String? = null
)

class MusicRepository(
    private val context: Context,
    private val dao: KoplingfyDao
) {
    // ---- Local Reactive Flows ----
    val likedTracksFlow: Flow<List<Track>> = dao.observeLikedTracks()
        .map { list -> list.map { it.toTrack() } }

    val likedTrackIdsFlow: Flow<Set<String>> = dao.observeLikedTrackIds()
        .map { it.toSet() }

    val localTracksFlow: Flow<List<Track>> = dao.observeLocalTracks()
        .map { list -> list.map { it.toTrack() } }

    val playlistsFlow: Flow<List<PlaylistSummary>> = dao.observePlaylistsWithCount()
        .map { rows ->
            rows.map {
                PlaylistSummary(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    trackCount = it.trackCount,
                    coverUrl = it.coverUrl,
                    createdAt = it.createdAt
                )
            }
        }

    val playHistoryFlow: Flow<List<Track>> = dao.observePlayHistory()
        .map { entries -> entries.map { it.toTrack() } }

    val listeningStatsFlow: Flow<ListeningStats> = dao.observePlayHistory()
        .map { entries -> computeStats(entries) }

    val searchHistoryFlow: Flow<List<String>> = dao.observeSearchHistory()
        .map { list -> list.map { it.query } }

    fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
        dao.observeTracksForPlaylist(playlistId).map { list -> list.map { it.toTrack() } }

    // ---- Remote Catalog Fetching ----
    suspend fun loadHomeFeed(moodQuery: String = "top hits 2025"): HomeFeedResult = withContext(Dispatchers.IO) {
        var chartTracks = emptyList<Track>()
        var featuredAlbums = emptyList<AlbumSummary>()
        var featuredArtists = emptyList<ArtistSummary>()
        var quickPicks = emptyList<Track>()
        var errorMsg: String? = null

        // 1. Fetch Live Global Charts from Deezer
        runCatching {
            val chart = KoplingfyNetwork.deezerApi.getGlobalChart(limit = 20)
            chartTracks = chart.tracks?.data.orEmpty().mapIndexedNotNull { idx, dto ->
                dto.toTrack(rank = idx + 1)
            }
            featuredAlbums = chart.albums?.data.orEmpty().mapNotNull { it.toAlbumSummary() }
            featuredArtists = chart.artists?.data.orEmpty().mapNotNull { it.toArtistSummary() }
        }

        // 2. Fetch Mood-specific Quick Picks from iTunes
        runCatching {
            val itunesResp = KoplingfyNetwork.itunesApi.searchTracks(term = moodQuery, limit = 20)
            quickPicks = itunesResp.results.mapNotNull { it.toTrack() }
                .filter { !it.streamUrl.isNullOrBlank() }
        }.onFailure {
            // Fallback to Deezer search if iTunes fails
            runCatching {
                val dz = KoplingfyNetwork.deezerApi.searchTracks(query = moodQuery, limit = 20)
                quickPicks = dz.data.mapNotNull { it.toTrack() }
            }
        }

        // Fallback if Deezer chart was unreachable
        if (chartTracks.isEmpty() && quickPicks.isNotEmpty()) {
            chartTracks = quickPicks.mapIndexed { idx, track -> track.copy(rank = idx + 1) }
        }
        if (featuredAlbums.isEmpty()) {
            runCatching {
                val albumsResp = KoplingfyNetwork.itunesApi.searchAlbums(term = moodQuery, limit = 12)
                featuredAlbums = albumsResp.results.mapNotNull { it.toAlbumSummary() }
            }
        }
        if (featuredArtists.isEmpty() && chartTracks.isNotEmpty()) {
            featuredArtists = chartTracks
                .distinctBy { it.artist }
                .take(10)
                .mapIndexed { idx, t ->
                    ArtistSummary(
                        id = "artist_derived_$idx",
                        name = t.artist,
                        artworkUrl = t.artworkUrl,
                        genre = t.genre
                    )
                }
        }

        if (chartTracks.isEmpty() && quickPicks.isEmpty()) {
            errorMsg = "Tidak dapat memuat katalog musik. Periksa koneksi internet kamu."
        }

        HomeFeedResult(
            chartTracks = chartTracks,
            quickPicks = if (quickPicks.isNotEmpty()) quickPicks else chartTracks.shuffled(),
            featuredAlbums = featuredAlbums,
            featuredArtists = featuredArtists,
            errorMessage = errorMsg
        )
    }

    suspend fun fetchTracksByQuery(query: String, limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        val itunesTracks = runCatching {
            KoplingfyNetwork.itunesApi.searchTracks(term = query, limit = limit)
                .results.mapNotNull { it.toTrack() }
        }.getOrDefault(emptyList())

        if (itunesTracks.isNotEmpty()) return@withContext itunesTracks

        runCatching {
            KoplingfyNetwork.deezerApi.searchTracks(query = query, limit = limit)
                .data.mapNotNull { it.toTrack() }
        }.getOrDefault(emptyList())
    }

    suspend fun searchAll(query: String): SearchResultBundle = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext SearchResultBundle(emptyList(), emptyList(), emptyList())
        }
        dao.insertSearchQuery(SearchHistoryEntity(query = trimmed))

        var tracks = emptyList<Track>()
        var albums = emptyList<AlbumSummary>()
        var errorMsg: String? = null

        runCatching {
            tracks = KoplingfyNetwork.itunesApi.searchTracks(term = trimmed, limit = 30)
                .results.mapNotNull { it.toTrack() }
        }.onFailure {
            runCatching {
                tracks = KoplingfyNetwork.deezerApi.searchTracks(query = trimmed, limit = 25)
                    .data.mapNotNull { it.toTrack() }
            }.onFailure {
                errorMsg = "Pencarian gagal. Periksa koneksi internet."
            }
        }

        runCatching {
            albums = KoplingfyNetwork.itunesApi.searchAlbums(term = trimmed, limit = 12)
                .results.mapNotNull { it.toAlbumSummary() }
        }

        val artists = tracks
            .distinctBy { it.artist.lowercase(Locale.getDefault()) }
            .take(10)
            .mapIndexed { idx, t ->
                ArtistSummary(
                    id = "search_artist_${idx}_${t.artist}",
                    name = t.artist,
                    artworkUrl = t.artworkUrl,
                    genre = t.genre
                )
            }

        SearchResultBundle(
            tracks = tracks,
            albums = albums,
            artists = artists,
            errorMessage = errorMsg
        )
    }

    suspend fun fetchLyrics(track: Track): LyricsState = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = track.title
                .replace(Regex("""\s*\([^)]*\)"""), "")
                .replace(Regex("""\s*\[[^\]]*]"""), "")
                .trim()
            val results = KoplingfyNetwork.lrcLibApi.searchLyrics(
                trackName = cleanTitle,
                artistName = track.artist
            )
            val best = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                ?: results.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                ?: results.firstOrNull()
            LrcParser.fromLrcItem(best)
        }.getOrElse {
            LyricsState(error = "Gagal mengambil lirik saat ini.")
        }
    }

    // ---- Local Library & Playlists Operations ----
    suspend fun toggleLike(track: Track): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        val newLiked = !(existing?.isLiked ?: false)
        val now = if (newLiked) System.currentTimeMillis() else 0L
        if (existing == null) {
            dao.upsertTrack(SavedTrackEntity.fromTrack(track, isLiked = newLiked, likedAt = now))
        } else {
            dao.updateLikeStatus(track.id, newLiked, now)
        }
        newLiked
    }

    suspend fun recordPlay(track: Track) = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        if (existing == null) {
            dao.upsertTrack(SavedTrackEntity.fromTrack(track, isLiked = false))
        }
        dao.insertPlayHistory(
            PlayHistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                streamUrl = track.streamUrl,
                durationMs = track.durationMs,
                genre = track.genre,
                isLocal = track.isLocal
            )
        )
    }

    suspend fun createPlaylist(name: String, description: String): Long = withContext(Dispatchers.IO) {
        dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim()
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(track.id)
        if (existing == null) {
            dao.upsertTrack(SavedTrackEntity.fromTrack(track))
        }
        dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = track.id))
        if (!track.artworkUrl.isNullOrBlank()) {
            dao.updatePlaylistCover(playlistId, track.artworkUrl)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun clearPlayHistory() = withContext(Dispatchers.IO) {
        dao.clearPlayHistory()
    }

    suspend fun deleteSearchQuery(query: String) = withContext(Dispatchers.IO) {
        dao.deleteSearchQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearSearchHistory()
    }

    // ---- Zero-Permission SAF Local Audio Importer ----
    suspend fun importLocalAudioUris(uris: List<Uri>): List<Track> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<Track>()
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val track = extractLocalTrackMetadata(uri)
            if (track != null) {
                dao.upsertTrack(SavedTrackEntity.fromTrack(track))
                imported.add(track)
            }
        }
        imported
    }

    suspend fun deleteLocalTrack(trackId: String) = withContext(Dispatchers.IO) {
        dao.deleteLocalTrack(trackId)
    }

    private fun extractLocalTrackMetadata(uri: Uri): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val displayName = queryDisplayName(uri)
            val fallbackTitle = displayName
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Audio Lokal"
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Artis Lokal"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Penyimpanan Perangkat"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 180_000L
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() } ?: "Audio Lokal"

            Track(
                id = "local_${uri.toString().hashCode()}",
                title = title,
                artist = artist,
                album = album,
                artworkUrl = null,
                streamUrl = uri.toString(),
                durationMs = duration,
                genre = genre,
                isLocal = true
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun computeStats(entries: List<PlayHistoryEntity>): ListeningStats {
        if (entries.isEmpty()) return ListeningStats()
        val totalPlays = entries.size
        val totalMinutes = (entries.sumOf { it.durationMs.coerceAtMost(300_000L) } / 60_000L).toInt()
            .coerceAtLeast(1)
        val uniqueTracks = entries.distinctBy { it.trackId }.size

        val topTracks = entries.groupBy { it.trackId }
            .map { (_, list) -> TrackStatItem(list.first().toTrack(), list.size) }
            .sortedByDescending { it.playCount }
            .take(5)

        val topArtists = entries.groupBy { it.artist }
            .map { (artist, list) ->
                ArtistStatItem(
                    artist = artist,
                    playCount = list.size,
                    artworkUrl = list.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                )
            }
            .sortedByDescending { it.playCount }
            .take(5)

        val dayFmt = SimpleDateFormat("EEE", Locale("id", "ID"))
        val cal = Calendar.getInstance()
        val last7Days = (6 downTo 0).map { offset ->
            val dayCal = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = dayCal.timeInMillis
            val end = start + 86_400_000L
            val count = entries.count { it.playedAt in start until end }
            dayFmt.format(Date(start)) to count
        }

        return ListeningStats(
            totalPlays = totalPlays,
            totalMinutes = totalMinutes,
            uniqueTracks = uniqueTracks,
            topTracks = topTracks,
            topArtists = topArtists,
            dailyPlays = last7Days
        )
    }
}
