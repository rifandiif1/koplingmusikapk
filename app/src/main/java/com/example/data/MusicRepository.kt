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
import com.example.data.remote.InnerTubeClient
import com.example.data.remote.KoplingfyNetwork
import com.example.data.remote.LrcParser
import com.example.data.remote.ResolvedStream
import com.example.data.remote.StreamResolveResult
import com.example.data.remote.VideoStreamResolveResult
import com.example.model.AlbumSummary
import com.example.model.ArtistStatItem
import com.example.model.ArtistSummary
import com.example.model.AudioQuality
import com.example.model.ListeningStats
import com.example.model.LyricsState
import com.example.model.PlaylistSummary
import com.example.model.RemotePlaylistSummary
import com.example.model.Track
import com.example.model.TrackStatItem
import com.example.model.VideoCandidate
import com.example.model.YouTubeAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HomeFeedResult(
    val chartTracks: List<Track>,
    val quickPicks: List<Track>,
    val featuredAlbums: List<AlbumSummary>,
    val featuredArtists: List<ArtistSummary>,
    val featuredPlaylists: List<RemotePlaylistSummary> = emptyList(),
    val errorMessage: String? = null
)

data class SearchResultBundle(
    val tracks: List<Track>,
    val videos: List<Track> = emptyList(),
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
    val playlists: List<RemotePlaylistSummary> = emptyList(),
    val errorMessage: String? = null
)

class MusicRepository(
    private val context: Context,
    private val dao: KoplingfyDao
) {
    init {
        TrackGuard.init(context)
    }

    // ---- Local Reactive Flows ----
    val likedTracksFlow: Flow<List<Track>> = dao.observeLikedTracks()
        .map { list -> list.map { it.toTrack() } }

    val likedTrackIdsFlow: Flow<Set<String>> = dao.observeLikedTrackIds()
        .map { it.toSet() }

    val localTracksFlow: Flow<List<Track>> = dao.observeLocalTracks()
        .map { list -> list.map { it.toTrack() } }

    val downloadedTracksFlow: Flow<List<Track>> = dao.observeDownloadedTracks()
        .map { list -> list.map { it.toTrack() } }

    val downloadedTrackIdsFlow: Flow<Set<String>> = dao.observeDownloadedTrackIds()
        .map { it.toSet() }

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

    // ---- Pure YouTube Music InnerTube Home & Explore Feed (ZERO Deezer/iTunes Preview) ----
    suspend fun loadHomeFeed(moodQuery: String = "top hits 2025"): HomeFeedResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val chartsDeferred = async {
                runCatching { InnerTubeClient.fetchChartsAndExplore() }.getOrNull()
            }
            val quickPicksDeferred = async {
                runCatching { InnerTubeClient.searchAll(moodQuery) }.getOrNull()
            }

            val charts = chartsDeferred.await()
            val moodBundle = quickPicksDeferred.await()

            val quickPicks = TrackGuard.deduplicateAndFilter(moodBundle?.tracks.orEmpty())
            val chartTracks = TrackGuard.deduplicateAndFilter(
                charts?.tracks?.takeIf { it.isNotEmpty() }
                    ?: quickPicks.mapIndexed { idx, t -> t.copy(rank = idx + 1) }
            ).mapIndexed { idx, t -> t.copy(rank = idx + 1) }

            val featuredAlbums = (charts?.albums.orEmpty() + moodBundle?.albums.orEmpty())
                .distinctBy { it.id }
                .take(12)

            val featuredArtists = (charts?.artists.orEmpty() + moodBundle?.artists.orEmpty())
                .distinctBy { it.id }
                .take(12)
                .ifEmpty {
                    chartTracks.distinctBy { it.artist }.take(10).mapIndexed { idx, t ->
                        ArtistSummary(
                            id = t.artistBrowseId?.let { "yt_artist_$it" } ?: "yt_artist_query_$idx",
                            name = t.artist,
                            artworkUrl = t.artworkUrl,
                            genre = "YouTube Music"
                        )
                    }
                }

            val featuredPlaylists = (charts?.playlists.orEmpty() + moodBundle?.playlists.orEmpty())
                .distinctBy { it.id }
                .take(10)

            val errorMsg = if (chartTracks.isEmpty() && quickPicks.isEmpty()) {
                "Tidak dapat memuat katalog YouTube Music. Periksa koneksi internet kamu."
            } else null

            HomeFeedResult(
                chartTracks = chartTracks,
                quickPicks = if (quickPicks.isNotEmpty()) quickPicks else chartTracks,
                featuredAlbums = featuredAlbums,
                featuredArtists = featuredArtists,
                featuredPlaylists = featuredPlaylists,
                errorMessage = errorMsg
            )
        }
    }

    suspend fun getSearchSuggestions(input: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { InnerTubeClient.getSearchSuggestions(input) }.getOrDefault(emptyList())
    }

    suspend fun fetchTracksByQuery(query: String, limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        runCatching { InnerTubeClient.searchSongs(query).take(limit) }.getOrDefault(emptyList())
    }

    suspend fun fetchArtistTracks(artist: ArtistSummary): List<Track> = withContext(Dispatchers.IO) {
        if (artist.id.startsWith("yt_artist_UC")) {
            val browseId = artist.id.removePrefix("yt_artist_")
            val ytTracks = runCatching {
                InnerTubeClient.browseCollection(browseId, fallbackArt = artist.artworkUrl)
            }.getOrDefault(emptyList())
            if (ytTracks.isNotEmpty()) return@withContext ytTracks
        }
        fetchTracksByQuery(artist.name, limit = 25)
    }

    suspend fun fetchAlbumTracks(album: AlbumSummary): List<Track> = withContext(Dispatchers.IO) {
        if (album.id.startsWith("yt_album_")) {
            val browseId = album.id.removePrefix("yt_album_")
            val ytTracks = runCatching {
                InnerTubeClient.browseCollection(
                    browseId = browseId,
                    fallbackTitle = album.title,
                    fallbackArt = album.artworkUrl
                )
            }.getOrDefault(emptyList())
            if (ytTracks.isNotEmpty()) return@withContext ytTracks
        }
        fetchTracksByQuery("${album.title} ${album.artist}", limit = 25)
    }

    suspend fun fetchRemotePlaylistTracks(playlist: RemotePlaylistSummary): List<Track> = withContext(Dispatchers.IO) {
        val tracks = runCatching {
            InnerTubeClient.browseCollection(
                browseId = playlist.id,
                fallbackTitle = playlist.title,
                fallbackArt = playlist.artworkUrl
            )
        }.getOrDefault(emptyList())
        if (tracks.isNotEmpty()) tracks else fetchTracksByQuery(playlist.title, limit = 20)
    }

    /**
     * Imports any public YouTube / YouTube Music playlist URL or list ID (PL..., RDCLAK..., OLAK...)
     * directly into a new local Koplingfy playlist.
     */
    suspend fun importYouTubePlaylistByUrl(
        rawInput: String,
        customPlaylistName: String? = null
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Tempelkan link atau ID playlist YouTube terlebih dahulu.")

            val extractedListId = extractYouTubePlaylistId(trimmed)
                ?: throw IllegalArgumentException("Link tidak valid. Gunakan link playlist YouTube/YTM yang mengandung '?list=...'")

            val browseId = if (extractedListId.startsWith("VL") || extractedListId.startsWith("MPREb_")) {
                extractedListId
            } else {
                "VL$extractedListId"
            }

            var tracks = runCatching {
                InnerTubeClient.browseCollection(browseId = browseId)
            }.getOrDefault(emptyList())

            if (tracks.isEmpty() && browseId.startsWith("VL")) {
                tracks = runCatching {
                    InnerTubeClient.browseCollection(browseId = extractedListId)
                }.getOrDefault(emptyList())
            }

            val dedupedTracks = TrackGuard.deduplicateAndFilter(tracks)
            if (dedupedTracks.isEmpty()) {
                throw IllegalStateException("Playlist kosong, bersifat pribadi, atau tidak dapat diakses dari YouTube Music.")
            }

            val finalName = customPlaylistName?.trim()?.takeIf { it.isNotEmpty() }
                ?: dedupedTracks.firstOrNull()?.album?.takeIf {
                    it.isNotBlank() && !it.equals("YouTube Music", ignoreCase = true) && !it.equals("Single", ignoreCase = true)
                }
                ?: "Impor YouTube (${extractedListId.take(8)})"

            val playlistId = dao.insertPlaylist(
                PlaylistEntity(
                    name = finalName,
                    description = "Diimpor dari YouTube Music • ${dedupedTracks.size} lagu",
                    coverUrl = dedupedTracks.firstOrNull()?.artworkUrl
                )
            )
            dedupedTracks.forEach { track ->
                val existing = dao.getTrackById(track.id)
                if (existing == null) {
                    dao.upsertTrack(SavedTrackEntity.fromTrack(track))
                }
                dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = track.id))
            }
            finalName to dedupedTracks.size
        }
    }

    private fun extractYouTubePlaylistId(input: String): String? {
        val listParamMatch = Regex("""[?&]list=([a-zA-Z0-9_\-]+)""").find(input)
        if (listParamMatch != null) return listParamMatch.groupValues[1]
        if (Regex("""^(VL|PL|RD|OLAK|UU|LL|FL)[a-zA-Z0-9_\-]{6,}$""").matches(input)) {
            return input
        }
        return null
    }

    // ---- Radio & Autoplay Queue ----
    suspend fun fetchRadioQueue(track: Track): List<Track> = withContext(Dispatchers.IO) {
        val videoId = if (track.id.startsWith("yt_")) {
            track.id.removePrefix("yt_")
        } else {
            runCatching {
                InnerTubeClient.searchSongs("${track.title} ${track.artist}").firstOrNull()?.id?.removePrefix("yt_")
            }.getOrNull()
        } ?: return@withContext emptyList()

        val radio = runCatching { InnerTubeClient.getNextRadioQueue(videoId) }.getOrDefault(emptyList())
        if (radio.size > 1) return@withContext radio

        // Fallback: search related artist songs on YouTube Music
        runCatching { InnerTubeClient.searchSongs("${track.artist} mix") }.getOrDefault(emptyList())
    }

    // ---- FULL-LENGTH ONLY Stream Resolution (Metrolist Cache + Parallel InnerTube Racing) ----
    suspend fun resolveFullAudioStream(
        track: Track,
        quality: AudioQuality = AudioQuality.HIGH,
        forceRefresh: Boolean = false
    ): StreamResolveResult = withContext(Dispatchers.IO) {
        // 1. Local SAF audio file
        if (track.isLocal && !track.streamUrl.isNullOrBlank()) {
            return@withContext StreamResolveResult.Success(
                ResolvedStream(
                    url = track.streamUrl,
                    durationMs = track.durationMs,
                    sourceLabel = "AUDIO LOKAL • FULL"
                )
            )
        }

        // 2. Offline downloaded file (plays 100% offline with zero network!)
        val saved = dao.getTrackById(track.id)
        val offlinePath = saved?.localDownloadPath ?: track.localDownloadPath
        if (!offlinePath.isNullOrBlank() && File(offlinePath).exists()) {
            return@withContext StreamResolveResult.Success(
                ResolvedStream(
                    url = Uri.fromFile(File(offlinePath)).toString(),
                    durationMs = saved?.durationMs ?: track.durationMs,
                    sourceLabel = "OFFLINE • FULL"
                )
            )
        }

        val cleanTitle = track.title
            .replace(Regex("""\s*\([^)]*\)"""), "")
            .replace(Regex("""\s*\[[^\]]*]"""), "")
            .trim()

        // 3. Check if a verified working replacement videoId was already cached for this track
        val savedReplacementId = TrackGuard.getReplacementVideoId(track.id)
        if (!savedReplacementId.isNullOrBlank()) {
            val cachedReplacement = InnerTubeClient.resolveAudioStream(savedReplacementId, quality, forceRefresh = forceRefresh)
            if (cachedReplacement is StreamResolveResult.Success) {
                TrackGuard.markPlayable(track.id)
                return@withContext cachedReplacement
            }
        }

        // 4. Resolve direct YouTube videoId via Metrolist InnerTube parallel player racing + cache
        var primaryResult: StreamResolveResult? = null
        val videoId = if (track.id.startsWith("yt_")) {
            track.id.removePrefix("yt_")
        } else {
            runCatching {
                InnerTubeClient.searchSongs("$cleanTitle ${track.artist}").firstOrNull()?.id?.removePrefix("yt_")
            }.getOrNull()
        }

        if (!videoId.isNullOrBlank()) {
            val direct = InnerTubeClient.resolveAudioStream(videoId, quality, forceRefresh = forceRefresh)
            if (direct is StreamResolveResult.Success) {
                TrackGuard.markPlayable(track.id)
                return@withContext direct
            }
            primaryResult = direct

            // 5. If primary videoId failed or was region/age restricted, try up to 4 alternate playable uploads
            val altCandidates = runCatching {
                val queries = listOf(
                    "$cleanTitle ${track.artist} audio",
                    "$cleanTitle ${track.artist} lyrics"
                )
                val found = LinkedHashSet<String>()
                for (q in queries) {
                    val searchRes = InnerTubeClient.searchSongs(q)
                    searchRes.forEach { cand ->
                        val altId = cand.id.removePrefix("yt_")
                        if (altId.length == 11 && altId != videoId && altId != savedReplacementId) {
                            found.add(altId)
                        }
                    }
                    if (found.size >= 4) break
                }
                found.take(4)
            }.getOrDefault(emptyList())

            for (altId in altCandidates) {
                val alt = InnerTubeClient.resolveAudioStream(altId, quality, forceRefresh = forceRefresh)
                if (alt is StreamResolveResult.Success) {
                    TrackGuard.recordReplacementVideoId(track.id, altId)
                    return@withContext alt
                }
            }
        }

        // NEVER fall back to 30s preview! Surface the exact InnerTube status so caller can blacklist/skip cleanly.
        primaryResult ?: StreamResolveResult.Unavailable(
            "Stream penuh YouTube Music tidak tersedia untuk lagu ini."
        )
    }

    /**
     * Silently resolves & caches the next track's InnerTube audio (and optionally Lyric Video)
     * stream URL in the background so skipping or auto-advancing begins playback immediately.
     */
    suspend fun prefetchTrackStream(
        track: Track,
        quality: AudioQuality = AudioQuality.HIGH,
        prefetchVideo: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (track.isLocal) return@withContext
        val videoId = if (track.id.startsWith("yt_")) {
            track.id.removePrefix("yt_")
        } else {
            runCatching {
                InnerTubeClient.searchSongs("${track.title} ${track.artist}")
                    .firstOrNull()?.id?.removePrefix("yt_")
            }.getOrNull()
        }

        if (!track.isDownloaded && !videoId.isNullOrBlank()) {
            if (InnerTubeClient.getCachedStream(videoId, quality) == null) {
                runCatching {
                    InnerTubeClient.resolveAudioStream(videoId, quality, forceRefresh = false)
                }
            }
        }

        if (prefetchVideo) {
            runCatching {
                val candidates = searchLyricVideosForTrack(track)
                val firstVid = candidates.firstOrNull()?.videoId
                if (!firstVid.isNullOrBlank()) {
                    InnerTubeClient.resolveVideoStream(firstVid, forceRefresh = false)
                }
            }
        }
    }

    suspend fun searchLyricVideosForTrack(track: Track): List<VideoCandidate> = withContext(Dispatchers.IO) {
        val originalVideoId = if (track.id.startsWith("yt_")) track.id.removePrefix("yt_") else null
        runCatching {
            InnerTubeClient.searchLyricVideosForTrack(
                title = track.title,
                artist = track.artist,
                originalVideoId = originalVideoId
            )
        }.getOrDefault(emptyList())
    }

    suspend fun resolveVideoStream(videoId: String, forceRefresh: Boolean = false): VideoStreamResolveResult = withContext(Dispatchers.IO) {
        runCatching {
            InnerTubeClient.resolveVideoStream(videoId, forceRefresh = forceRefresh)
        }.getOrElse {
            VideoStreamResolveResult.Unavailable("Gagal memuat stream video.")
        }
    }

    // ---- Search: Online YouTube Music OR Local Library ----
    suspend fun searchAll(query: String, localOnly: Boolean = false): SearchResultBundle = withContext(Dispatchers.IO) {
        val rawTrimmed = query.trim()
        if (rawTrimmed.isEmpty()) {
            return@withContext SearchResultBundle(emptyList(), emptyList(), emptyList(), emptyList())
        }
        dao.insertSearchQuery(SearchHistoryEntity(query = rawTrimmed))

        if (localOnly) {
            val localMatches = dao.searchLocalLibraryTracks(rawTrimmed).map { it.toTrack() }
            return@withContext SearchResultBundle(
                tracks = localMatches,
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList()
            )
        }

        val ytResult = runCatching { InnerTubeClient.searchAll(rawTrimmed) }
        val bundle = ytResult.getOrNull()
        if (bundle != null) {
            SearchResultBundle(
                tracks = TrackGuard.deduplicateAndFilter(bundle.tracks),
                videos = TrackGuard.deduplicateAndFilter(bundle.videos),
                albums = bundle.albums,
                artists = bundle.artists,
                playlists = bundle.playlists,
                errorMessage = null
            )
        } else {
            SearchResultBundle(
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                errorMessage = "Gagal terhubung ke YouTube Music InnerTube. Periksa koneksi internet kamu."
            )
        }
    }

    // ---- Multi-Provider Lyrics (LRCLIB Synced -> YouTube Music Native Fallback) ----
    suspend fun fetchLyrics(track: Track): LyricsState = withContext(Dispatchers.IO) {
        val cleanTitle = track.title
            .replace(Regex("""\s*\([^)]*\)"""), "")
            .replace(Regex("""\s*\[[^\]]*]"""), "")
            .trim()

        // Provider 1: LRCLIB (Synced timestamped LRC)
        val lrcResult = runCatching {
            val results = KoplingfyNetwork.lrcLibApi.searchLyrics(
                trackName = cleanTitle,
                artistName = track.artist
            )
            val best = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                ?: results.firstOrNull { !it.plainLyrics.isNullOrBlank() }
                ?: results.firstOrNull()
            LrcParser.fromLrcItem(best).copy(providerName = "LRCLIB")
        }.getOrNull()

        if (lrcResult != null && (lrcResult.syncedLines.isNotEmpty() || !lrcResult.plainLyrics.isNullOrBlank() || lrcResult.isInstrumental)) {
            return@withContext lrcResult
        }

        // Provider 2: YouTube Music Native InnerTube Lyrics (MPLYt_*)
        if (track.id.startsWith("yt_")) {
            val videoId = track.id.removePrefix("yt_")
            val ytLyrics = runCatching { InnerTubeClient.fetchYouTubeMusicLyrics(videoId) }.getOrNull()
            if (!ytLyrics.isNullOrBlank()) {
                return@withContext LyricsState(
                    plainLyrics = ytLyrics,
                    providerName = "YouTube Music"
                )
            }
        }

        LyricsState(error = "Lirik belum tersedia untuk lagu ini.")
    }

    // ---- YouTube Music Account Verification & Library Sync ----
    suspend fun verifyAndFetchAccount(cookie: String): YouTubeAccount? = withContext(Dispatchers.IO) {
        InnerTubeClient.updateAuth(cookie)
        TrackGuard.clearUnplayableCache()
        runCatching { InnerTubeClient.fetchAccountInfo(cookie) }.getOrNull()
    }

    suspend fun syncYouTubeMusicLibrary(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (!InnerTubeClient.isAuthenticated()) return@withContext (0 to 0)
        var syncedLiked = 0
        var syncedPlaylists = 0

        val ytLiked = runCatching { InnerTubeClient.fetchAccountLikedSongs() }.getOrDefault(emptyList())
        ytLiked.forEach { track ->
            val existing = dao.getTrackById(track.id)
            if (existing == null) {
                dao.upsertTrack(SavedTrackEntity.fromTrack(track, isLiked = true, likedAt = System.currentTimeMillis()))
            } else if (!existing.isLiked) {
                dao.updateLikeStatus(track.id, true, System.currentTimeMillis())
            }
            syncedLiked++
        }

        val ytPlaylists = runCatching { InnerTubeClient.fetchAccountPlaylists() }.getOrDefault(emptyList())
        val localPlaylistNames = dao.getAllPlaylistsOnce().map { it.name.lowercase() }.toSet()
        ytPlaylists.take(8).forEach { remotePl ->
            if (remotePl.title.lowercase() !in localPlaylistNames) {
                val plTracks = runCatching { InnerTubeClient.browseCollection(remotePl.id) }.getOrDefault(emptyList())
                if (plTracks.isNotEmpty()) {
                    val newId = dao.insertPlaylist(
                        PlaylistEntity(
                            name = remotePl.title,
                            description = "Disinkronkan dari YouTube Music",
                            coverUrl = remotePl.artworkUrl ?: plTracks.firstOrNull()?.artworkUrl
                        )
                    )
                    plTracks.forEach { t ->
                        if (dao.getTrackById(t.id) == null) {
                            dao.upsertTrack(SavedTrackEntity.fromTrack(t))
                        }
                        dao.addTrackToPlaylist(PlaylistTrackCrossRef(newId, t.id))
                    }
                    syncedPlaylists++
                }
            }
        }
        syncedLiked to syncedPlaylists
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

    suspend fun createPlaylistWithTracks(name: String, description: String, tracks: List<Track>): Long = withContext(Dispatchers.IO) {
        val uniqueTracks = TrackGuard.deduplicateAndFilter(tracks)
        val id = dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim(),
                coverUrl = uniqueTracks.firstOrNull()?.artworkUrl
            )
        )
        uniqueTracks.forEach { t ->
            if (dao.getTrackById(t.id) == null) {
                dao.upsertTrack(SavedTrackEntity.fromTrack(t))
            }
            dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = id, trackId = t.id))
        }
        id
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    /**
     * Adds [track] to playlist if neither the same ID nor an equivalent song (same title+artist)
     * already exists inside the playlist. Returns true if newly added, false if duplicate skipped.
     */
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean = withContext(Dispatchers.IO) {
        val existingTracks = dao.getTracksForPlaylistOnce(playlistId).map { it.toTrack() }
        if (existingTracks.any { TrackGuard.isSameSong(it, track) }) {
            return@withContext false
        }
        val existing = dao.getTrackById(track.id)
        if (existing == null) {
            dao.upsertTrack(SavedTrackEntity.fromTrack(track))
        }
        dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = playlistId, trackId = track.id))
        if (!track.artworkUrl.isNullOrBlank()) {
            dao.updatePlaylistCover(playlistId, track.artworkUrl)
        }
        true
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
        val totalMinutes = (entries.sumOf { it.durationMs.coerceAtMost(420_000L) } / 60_000L).toInt()
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
