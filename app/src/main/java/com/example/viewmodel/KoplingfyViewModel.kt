package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.LyricsTranslator
import com.example.ai.SongMeaningAnalyzer
import com.example.ai.SongRecognitionResult
import com.example.ai.SongRecognitionService
import com.example.data.CloudLibraryRepository
import com.example.data.MusicRepository
import com.example.data.OfflineDownloadManager
import com.example.data.local.KoplingfyDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.remote.InnerTubeClient
import com.example.data.remote.StreamResolveResult
import com.example.data.remote.VideoStreamResolveResult
import com.example.model.AccentPreset
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.AudioQuality
import com.example.model.AudioSettings
import com.example.model.CollectionDetailType
import com.example.model.EqPreset
import com.example.model.KoplingfyMoods
import com.example.model.ListeningStats
import com.example.model.LyricsState
import com.example.model.MoodFilter
import com.example.model.PlayerArtStyle
import com.example.model.PlayerBackgroundStyle
import com.example.model.PlaylistSummary
import com.example.model.RemotePlaylistSummary
import com.example.model.RepeatMode
import com.example.model.StreamTechStats
import com.example.model.ThemeMode
import com.example.model.Track
import com.example.model.VideoCandidate
import com.example.model.YouTubeAccount
import com.example.player.KoplingfyAudioEngine
import com.example.player.KoplingfyMediaBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val selectedMood: MoodFilter = KoplingfyMoods.allMoods.first(),
    val chartTracks: List<Track> = emptyList(),
    val quickPicks: List<Track> = emptyList(),
    val featuredAlbums: List<AlbumSummary> = emptyList(),
    val featuredArtists: List<ArtistSummary> = emptyList(),
    val featuredPlaylists: List<RemotePlaylistSummary> = emptyList(),
    val errorMessage: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val localOnly: Boolean = false,
    val selectedFilter: Int = 0, // 0=Semua, 1=Lagu, 2=Video, 3=Album, 4=Artis, 5=Playlist
    val suggestions: List<String> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val videos: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<RemotePlaylistSummary> = emptyList(),
    val errorMessage: String? = null
)

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoadingRadio: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isPlayerExpanded: Boolean = false,
    val lyricsState: LyricsState = LyricsState(),
    val sleepTimerRemainingSec: Int? = null,
    val sleepAfterCurrentTrack: Boolean = false,
    val streamSourceLabel: String = "YOUTUBE MUSIC • FULL",
    val audioError: String? = null,
    val requiresYouTubeLogin: Boolean = false,
    val lyricsOffsetMs: Long = 0L,
    val abLoopStartMs: Long? = null,
    val abLoopEndMs: Long? = null,
    val isDrivingMode: Boolean = false,
    val isVideoMode: Boolean = false,
    val isLoadingVideo: Boolean = false,
    val videoError: String? = null,
    val videoCandidates: List<VideoCandidate> = emptyList(),
    val selectedVideoId: String? = null,
    val videoQualityLabel: String = "",
    val streamTechStats: StreamTechStats = StreamTechStats()
)

data class CollectionDetailState(
    val type: CollectionDetailType? = null,
    val title: String = "",
    val subtitle: String = "",
    val coverUrl: String? = null,
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val accentHex: Long? = null
)

data class StorageStats(
    val downloadsBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val lyricsAiCacheBytes: Long = 0L
)

data class CloudSyncUiState(
    val isSyncing: Boolean = false,
    val isImportingPlaylist: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val cloudLikedCount: Int? = null,
    val cloudPlaylistCount: Int? = null,
    val cloudDeviceLabel: String? = null
)

class KoplingfyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KoplingfyDatabase.getInstance(application)
    private val repository = MusicRepository(application, database.dao())
    private val downloadManager = OfflineDownloadManager(application, database.dao())
    private val preferences = PreferencesRepository(application)
    private val cloudLibraryRepository = CloudLibraryRepository(application)

    private val audioEngine = KoplingfyAudioEngine(
        context = application,
        onTrackCompleted = { handleTrackCompleted() },
        onStreamExpiredError = { handleStreamExpiredRetry() }
    )

    // ---- Flows from Persistence ----
    val settings: StateFlow<AudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioSettings())

    val youtubeAccount: StateFlow<YouTubeAccount> = preferences.accountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, YouTubeAccount())

    val likedTracks: StateFlow<List<Track>> = repository.likedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedTrackIds: StateFlow<Set<String>> = repository.likedTrackIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val localTracks: StateFlow<List<Track>> = repository.localTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTracks: StateFlow<List<Track>> = repository.downloadedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTrackIds: StateFlow<Set<String>> = repository.downloadedTrackIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, Float>> = downloadManager.activeDownloads

    val playlists: StateFlow<List<PlaylistSummary>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playHistory: StateFlow<List<Track>> = repository.playHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val listeningStats: StateFlow<ListeningStats> = repository.listeningStatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningStats())

    val searchHistory: StateFlow<List<String>> = repository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- UI States ----
    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _detailState = MutableStateFlow(CollectionDetailState())
    val detailState: StateFlow<CollectionDetailState> = _detailState.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _cloudSyncState = MutableStateFlow(CloudSyncUiState())
    val cloudSyncState: StateFlow<CloudSyncUiState> = _cloudSyncState.asStateFlow()

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var resolveStreamJob: Job? = null
    private var prefetchJob: Job? = null
    private var lyricsJob: Job? = null
    private var translateLyricsJob: Job? = null
    private var romanizeLyricsJob: Job? = null
    private var meaningLyricsJob: Job? = null
    private var videoResolveJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var detailObserveJob: Job? = null
    private var retriedExpiredTrackId: String? = null
    private var preEndPrefetchedTrackId: String? = null
    private var autoQueuedRadioForTrackId: String? = null
    private var consecutiveAutoSkipFailures: Int = 0
    private var isAutoAdvancingTrack: Boolean = false

    init {
        LyricsTranslator.init(getApplication())
        SongMeaningAnalyzer.init(getApplication())
        InnerTubeClient.warmupAsync()
        registerMediaBridgeCallbacks()
        observeAudioEngine()
        observeSettingsForAudio()
        observeAccountAuth()
        refreshStorageStats()
        loadHomeFeed(KoplingfyMoods.allMoods.first())
    }

    private fun registerMediaBridgeCallbacks() {
        KoplingfyMediaBridge.onTogglePlayPause = { togglePlayPause() }
        KoplingfyMediaBridge.onPlay = { audioEngine.resume() }
        KoplingfyMediaBridge.onPause = { audioEngine.pause() }
        KoplingfyMediaBridge.onNext = { skipNext() }
        KoplingfyMediaBridge.onPrevious = { skipPrevious() }
        KoplingfyMediaBridge.onSeekTo = { seekTo(it) }
    }

    private fun publishMediaBridgeState(state: PlayerUiState) {
        KoplingfyMediaBridge.publishState(
            context = getApplication(),
            track = state.currentTrack,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            positionMs = state.positionMs,
            durationMs = state.durationMs
        )
    }

    private fun observeAccountAuth() {
        viewModelScope.launch {
            youtubeAccount.collectLatest { acc ->
                InnerTubeClient.updateAuth(acc.cookie, acc.visitorData)
            }
        }
    }

    private fun observeAudioEngine() {
        viewModelScope.launch {
            audioEngine.state.collectLatest { engine ->
                var updatedState: PlayerUiState? = null
                _playerState.update { state ->
                    val next = state.copy(
                        isPlaying = engine.isPlaying,
                        isBuffering = engine.isBuffering,
                        positionMs = engine.positionMs,
                        durationMs = if (engine.durationMs > 0) engine.durationMs else state.currentTrack?.durationMs ?: 0L,
                        audioError = engine.errorMessage ?: state.audioError,
                        streamTechStats = engine.streamTechStats
                    )
                    updatedState = next
                    next
                }
                updatedState?.let { st ->
                    publishMediaBridgeState(st)
                    // Proactively pre-fetch the next song ~20s before current track ends for instant gapless transition
                    val curTrack = st.currentTrack
                    if (curTrack != null &&
                        st.isPlaying &&
                        !st.isBuffering &&
                        st.durationMs > 20_000L &&
                        st.positionMs > 5_000L &&
                        (st.durationMs - st.positionMs) in 1L..22_000L &&
                        preEndPrefetchedTrackId != curTrack.id
                    ) {
                        preEndPrefetchedTrackId = curTrack.id
                        schedulePrefetchNextTrack(effectiveAudioQuality(), delayMs = 0L)
                    }
                }
            }
        }
    }

    private fun effectiveAudioQuality(): AudioQuality {
        val useDataSaver = settings.value.dataSaverCellular && isOnCellular()
        return if (useDataSaver) AudioQuality.LOW else settings.value.audioQuality
    }

    private fun observeSettingsForAudio() {
        viewModelScope.launch {
            settings.collectLatest { cfg ->
                audioEngine.updatePlaybackParams(cfg.playbackSpeed, cfg.playbackPitch)
                audioEngine.updateEqualizerBands(cfg.customBandsDb)
                audioEngine.updateBassBoostAndNormalization(cfg.bassBoostStrength, cfg.normalizeAudio)
                audioEngine.updateSkipSilenceAndReverb(cfg.skipSilence, cfg.reverbEnabled)
                audioEngine.updateCrossfadeSeconds(cfg.crossfadeSeconds)
            }
        }
    }

    suspend fun quickSearchTracksForSharedQueue(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return repository.fetchTracksByQuery(query.trim(), limit = 15)
    }

    fun pausePlaybackForMicRecognition() {
        if (_playerState.value.isPlaying) {
            audioEngine.pause()
        }
    }

    suspend fun recognizeSongFromAudio(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<SongRecognitionResult> {
        val rawResult = SongRecognitionService.recognizeFromAudioBytes(audioBytes, mimeType)
        return rawResult.map { res ->
            if (!res.found || res.matches.isEmpty()) res
            else resolveRecognizedTracks(res)
        }
    }

    suspend fun findSongsByLyricsText(lyricSnippet: String): Result<SongRecognitionResult> {
        val rawResult = SongRecognitionService.searchByLyricsText(lyricSnippet)
        return rawResult.map { res ->
            if (!res.found || res.matches.isEmpty()) res
            else resolveRecognizedTracks(res)
        }
    }

    private suspend fun resolveRecognizedTracks(result: SongRecognitionResult): SongRecognitionResult =
        coroutineScope {
            val resolvedMatches = result.matches.map { match ->
                async {
                    val candidates = runCatching {
                        repository.fetchTracksByQuery("${match.title} ${match.artist}", limit = 4)
                    }.getOrDefault(emptyList())
                    val bestTrack = candidates.firstOrNull { candidate ->
                        candidate.title.contains(match.title, ignoreCase = true) ||
                            match.title.contains(candidate.title, ignoreCase = true)
                    } ?: candidates.firstOrNull()
                    match.copy(resolvedTrack = bestTrack)
                }
            }.awaitAll()
            result.copy(matches = resolvedMatches)
        }

    // ---- Home & Explore ----
    fun loadHomeFeed(mood: MoodFilter = _homeState.value.selectedMood) {
        viewModelScope.launch {
            _homeState.update { it.copy(isLoading = true, selectedMood = mood, errorMessage = null) }
            val result = repository.loadHomeFeed(mood.query)
            _homeState.update {
                it.copy(
                    isLoading = false,
                    chartTracks = result.chartTracks,
                    quickPicks = result.quickPicks,
                    featuredAlbums = result.featuredAlbums,
                    featuredArtists = result.featuredArtists,
                    featuredPlaylists = result.featuredPlaylists,
                    errorMessage = result.errorMessage
                )
            }
        }
    }

    // ---- Search (Online YTM + Local Library) ----
    fun onSearchQueryChange(newQuery: String) {
        _searchState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        suggestionJob?.cancel()
        if (newQuery.isBlank()) {
            _searchState.update {
                it.copy(
                    isSearching = false,
                    suggestions = emptyList(),
                    tracks = emptyList(),
                    videos = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    playlists = emptyList(),
                    errorMessage = null
                )
            }
            return
        }
        if (!_searchState.value.localOnly) {
            suggestionJob = viewModelScope.launch {
                delay(220L)
                val sug = repository.getSearchSuggestions(newQuery)
                _searchState.update { it.copy(suggestions = sug) }
            }
        }
        searchJob = viewModelScope.launch {
            delay(400L)
            performSearch(newQuery)
        }
    }

    fun toggleSearchScope(localOnly: Boolean) {
        _searchState.update { it.copy(localOnly = localOnly, suggestions = emptyList()) }
        val q = _searchState.value.query
        if (q.isNotBlank()) {
            submitSearch(q)
        }
    }

    fun submitSearch(query: String = _searchState.value.query) {
        searchJob?.cancel()
        suggestionJob?.cancel()
        if (query.isBlank()) return
        _searchState.update { it.copy(query = query, suggestions = emptyList()) }
        viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(query: String) {
        _searchState.update { it.copy(isSearching = true, errorMessage = null) }
        val result = repository.searchAll(query, localOnly = _searchState.value.localOnly)
        _searchState.update {
            it.copy(
                isSearching = false,
                tracks = result.tracks,
                videos = result.videos,
                albums = result.albums,
                artists = result.artists,
                playlists = result.playlists,
                errorMessage = result.errorMessage
            )
        }
    }

    fun setSearchFilter(index: Int) {
        _searchState.update { it.copy(selectedFilter = index) }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch { repository.deleteSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    // ---- Playback Controls (100% Full-Length, ZERO Preview) ----
    fun playTrack(track: Track, queue: List<Track> = listOf(track), expandPlayer: Boolean = false) {
        val safeQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val idx = safeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        audioEngine.setAbLoop(null, null)
        _playerState.update {
            it.copy(
                currentTrack = track,
                queue = safeQueue,
                currentIndex = idx,
                isPlaying = false,
                isBuffering = true,
                positionMs = 0L,
                durationMs = track.durationMs,
                isPlayerExpanded = expandPlayer || it.isPlayerExpanded,
                lyricsState = LyricsState(isLoading = true),
                audioError = null,
                requiresYouTubeLogin = false,
                lyricsOffsetMs = 0L,
                abLoopStartMs = null,
                abLoopEndMs = null,
                isLoadingVideo = it.isVideoMode && !track.isLocal,
                videoError = null,
                videoCandidates = emptyList(),
                selectedVideoId = null,
                videoQualityLabel = ""
            )
        }
        preEndPrefetchedTrackId = null
        val keepVideoMode = _playerState.value.isVideoMode && !track.isLocal
        if (keepVideoMode) {
            loadAndPlayLyricVideoForCurrentTrack(track, startPositionMs = 0L)
            schedulePrefetchNextTrack(effectiveAudioQuality(), delayMs = 2_000L)
        } else {
            if (_playerState.value.isVideoMode && track.isLocal) {
                _playerState.update { it.copy(isVideoMode = false) }
            }
            resolveAndPlayTrack(track)
        }
        viewModelScope.launch { repository.recordPlay(track) }
        fetchLyricsForTrack(track)
    }

    private fun isOnWifi(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun isOnCellular(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun handleStreamExpiredRetry(): Boolean {
        val state = _playerState.value
        val current = state.currentTrack ?: return false
        if (current.isLocal || retriedExpiredTrackId == current.id) return false
        retriedExpiredTrackId = current.id
        if (state.isVideoMode && !state.selectedVideoId.isNullOrBlank()) {
            audioEngine.setNextTrackStartOptions(startPositionMs = state.positionMs, autoStart = true)
            playSelectedVideoId(state.selectedVideoId, startPositionMs = state.positionMs)
        } else {
            audioEngine.setNextTrackStartOptions(startPositionMs = state.positionMs, autoStart = true)
            resolveAndPlayTrack(current, forceRefresh = true)
        }
        return true
    }

    private fun resolveAndPlayTrack(
        track: Track,
        forceRefresh: Boolean = false,
        fromAutoAdvance: Boolean = isAutoAdvancingTrack
    ) {
        if (!forceRefresh) {
            retriedExpiredTrackId = null
        }
        videoResolveJob?.cancel()
        resolveStreamJob?.cancel()
        prefetchJob?.cancel()
        resolveStreamJob = viewModelScope.launch {
            _playerState.update {
                it.copy(isBuffering = true, audioError = null, requiresYouTubeLogin = false)
            }
            val useDataSaver = settings.value.dataSaverCellular && isOnCellular()
            val effectiveQuality = if (useDataSaver) AudioQuality.LOW else settings.value.audioQuality
            when (val result = repository.resolveFullAudioStream(track, effectiveQuality, forceRefresh = forceRefresh)) {
                is StreamResolveResult.Success -> {
                    consecutiveAutoSkipFailures = 0
                    isAutoAdvancingTrack = false
                    val stream = result.stream
                    val sourceTag = if (useDataSaver && !track.isLocal && !track.isDownloaded) {
                        "${stream.sourceLabel} • HEMAT KUOTA"
                    } else stream.sourceLabel
                    _playerState.update {
                        it.copy(
                            durationMs = stream.durationMs?.takeIf { d -> d > 0L } ?: it.durationMs,
                            streamSourceLabel = sourceTag,
                            audioError = null,
                            requiresYouTubeLogin = false
                        )
                    }
                    audioEngine.playSource(
                        sourceUrl = stream.url,
                        isLocalUri = track.isLocal || stream.url.startsWith("file://"),
                        userAgent = stream.userAgent,
                        cookie = stream.cookie,
                        cacheKey = stream.cacheKey
                    )
                    schedulePrefetchNextTrack(effectiveQuality)
                }
                is StreamResolveResult.LoginRequired -> {
                    if (fromAutoAdvance && tryAutoSkipAfterFailure(track)) return@launch
                    isAutoAdvancingTrack = false
                    _playerState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            audioError = result.reason,
                            requiresYouTubeLogin = true
                        )
                    }
                }
                is StreamResolveResult.Unavailable -> {
                    if (fromAutoAdvance && tryAutoSkipAfterFailure(track)) return@launch
                    isAutoAdvancingTrack = false
                    _playerState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            audioError = result.reason,
                            requiresYouTubeLogin = !youtubeAccount.value.isLoggedIn
                        )
                    }
                }
            }
        }
    }

    /**
     * Prevents queue playback from stalling permanently if a single track in a playlist/radio
     * fails to resolve during automatic transition (capped at 3 consecutive skips).
     */
    private fun tryAutoSkipAfterFailure(failedTrack: Track): Boolean {
        val state = _playerState.value
        val hasNext = state.currentIndex + 1 < state.queue.size ||
            (state.repeatMode == RepeatMode.ALL && state.queue.size > 1)
        if (!hasNext || consecutiveAutoSkipFailures >= 3) {
            consecutiveAutoSkipFailures = 0
            return false
        }
        consecutiveAutoSkipFailures += 1
        showMessage("Melewati \"${failedTrack.title}\" (stream tidak tersedia)…")
        skipNext(fromAutoAdvance = true)
        return true
    }

    private fun schedulePrefetchNextTrack(quality: AudioQuality, delayMs: Long = 1_200L) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            if (delayMs > 0L) delay(delayMs)
            val state = _playerState.value
            val current = state.currentTrack ?: return@launch

            var nextTrack = when {
                state.currentIndex + 1 in state.queue.indices -> state.queue[state.currentIndex + 1]
                state.repeatMode == RepeatMode.ALL && state.queue.isNotEmpty() -> state.queue.first()
                else -> null
            }

            // If at the end of the queue with Autoplay Radio enabled, proactively queue related tracks
            // so the next song is already known and pre-fetched before the current track ends!
            if (nextTrack == null &&
                settings.value.autoplayRadio &&
                state.repeatMode != RepeatMode.ONE &&
                !current.isLocal &&
                autoQueuedRadioForTrackId != current.id
            ) {
                autoQueuedRadioForTrackId = current.id
                val existingIds = state.queue.map { it.id }.toSet()
                val radio = repository.fetchRadioQueue(current)
                    .filter { it.id != current.id && it.id !in existingIds }
                    .ifEmpty {
                        repository.fetchTracksByQuery("${current.artist} hits", limit = 10)
                            .filter { it.id != current.id && it.id !in existingIds }
                    }
                if (radio.isNotEmpty() && _playerState.value.currentTrack?.id == current.id) {
                    _playerState.update { cur ->
                        val deduped = (cur.queue + radio).distinctBy { it.id }
                        cur.copy(queue = deduped)
                    }
                    nextTrack = radio.firstOrNull()
                }
            }

            val target = nextTrack ?: return@launch
            if (target.id == current.id || target.isLocal) return@launch
            repository.prefetchTrackStream(
                track = target,
                quality = quality,
                prefetchVideo = _playerState.value.isVideoMode
            )
        }
    }

    fun startRadio(seedTrack: Track) {
        viewModelScope.launch {
            _playerState.update { it.copy(isLoadingRadio = true) }
            showMessage("Membuat Radio dari \"${seedTrack.title}\"…")
            val radioTracks = repository.fetchRadioQueue(seedTrack)
            _playerState.update { it.copy(isLoadingRadio = false) }
            if (radioTracks.isNotEmpty()) {
                val combined = (listOf(seedTrack) + radioTracks).distinctBy { it.id }
                playTrack(combined.first(), combined, expandPlayer = true)
                showMessage("Radio dimulai • ${combined.size} lagu serupa siap diputar")
            } else {
                playTrack(seedTrack, listOf(seedTrack), expandPlayer = true)
                showMessage("Memutar lagu (radio otomatis belum tersedia)")
            }
        }
    }

    fun playAll(tracks: List<Track>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffle) tracks.shuffled() else tracks
        _playerState.update { it.copy(isShuffleEnabled = shuffle) }
        playTrack(ordered.first(), ordered, expandPlayer = false)
    }

    fun togglePlayPause() {
        val current = _playerState.value.currentTrack ?: return
        audioEngine.setSleepFadeMultiplier(1.0f)
        if (_playerState.value.audioError != null) {
            resolveAndPlayTrack(current)
        } else {
            audioEngine.togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
    }

    var onAutoAdvanceOverride: (() -> Boolean)? = null

    fun syncRoomUpcomingQueue(roomQueue: List<Track>) {
        val current = _playerState.value.currentTrack ?: return
        val combinedQueue = listOf(current) + roomQueue.filter { it.id != current.id }
        _playerState.update { state ->
            state.copy(
                queue = combinedQueue,
                currentIndex = 0
            )
        }
    }

    fun applyRemoteRoomSync(
        track: Track?,
        shouldPlay: Boolean,
        targetPositionMs: Long,
        roomQueue: List<Track>
    ) {
        if (track == null) return
        val current = _playerState.value
        val combinedQueue = (listOf(track) + roomQueue.filter { it.id != track.id })
        if (current.currentTrack?.id != track.id) {
            audioEngine.setNextTrackStartOptions(
                startPositionMs = targetPositionMs,
                autoStart = shouldPlay
            )
            playTrack(track, combinedQueue, expandPlayer = false)
        } else {
            _playerState.update { it.copy(queue = combinedQueue, currentIndex = 0) }
            if (current.audioError != null && shouldPlay) {
                audioEngine.setNextTrackStartOptions(startPositionMs = targetPositionMs, autoStart = true)
                resolveAndPlayTrack(track)
                return
            }
            if (kotlin.math.abs(current.positionMs - targetPositionMs) > 2_500L && !current.isBuffering) {
                audioEngine.seekTo(targetPositionMs)
            }
            if (shouldPlay && !current.isPlaying && !current.isBuffering) {
                audioEngine.resume()
            } else if (!shouldPlay && current.isPlaying) {
                audioEngine.pause()
            }
        }
    }

    fun skipNext(fromAutoAdvance: Boolean = false) {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        isAutoAdvancingTrack = fromAutoAdvance
        val nextIndex = when {
            state.isShuffleEnabled && state.queue.size > 1 -> {
                val candidates = state.queue.indices.filter { it != state.currentIndex }
                candidates.random()
            }
            state.currentIndex + 1 < state.queue.size -> state.currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> {
                // Metrolist Autoplay Radio with multi-tier fallback so continuous playback never dies
                val seed = state.currentTrack
                if (settings.value.autoplayRadio && seed != null && !seed.isLocal) {
                    viewModelScope.launch {
                        _playerState.update { it.copy(isBuffering = true) }
                        val existingIds = state.queue.map { it.id }.toSet()
                        var extra = repository.fetchRadioQueue(seed)
                            .filter { it.id !in existingIds }
                        if (extra.isEmpty()) {
                            extra = repository.fetchTracksByQuery("${seed.artist} songs", limit = 15)
                                .filter { it.id !in existingIds }
                        }
                        if (extra.isEmpty()) {
                            extra = (_homeState.value.quickPicks + _homeState.value.chartTracks)
                                .distinctBy { it.id }
                                .filter { it.id !in existingIds }
                        }
                        if (extra.isNotEmpty()) {
                            val newQueue = state.queue + extra
                            isAutoAdvancingTrack = true
                            playTrack(extra.first(), newQueue)
                        } else {
                            isAutoAdvancingTrack = false
                            _playerState.update { it.copy(isBuffering = false) }
                        }
                    }
                } else {
                    isAutoAdvancingTrack = false
                }
                return
            }
        }
        playTrack(state.queue[nextIndex], state.queue)
    }

    fun skipPrevious() {
        val state = _playerState.value
        if (state.positionMs > 3_500L) {
            seekTo(0L)
            return
        }
        if (state.queue.isEmpty()) return
        isAutoAdvancingTrack = false
        val prevIndex = when {
            state.currentIndex - 1 >= 0 -> state.currentIndex - 1
            state.repeatMode == RepeatMode.ALL -> state.queue.lastIndex
            else -> 0
        }
        playTrack(state.queue[prevIndex], state.queue)
    }

    private fun handleTrackCompleted() {
        val state = _playerState.value
        if (state.sleepAfterCurrentTrack) {
            _playerState.update { it.copy(sleepAfterCurrentTrack = false) }
            showMessage("Timer tidur aktif — pemutaran dihentikan.")
            return
        }
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                state.currentTrack?.let { track ->
                    if (state.isVideoMode && !state.selectedVideoId.isNullOrBlank()) {
                        playSelectedVideoId(state.selectedVideoId, startPositionMs = 0L)
                    } else {
                        resolveAndPlayTrack(track, fromAutoAdvance = false)
                    }
                }
            }
            else -> {
                val handledByRoom = onAutoAdvanceOverride?.invoke() == true
                if (!handledByRoom) {
                    skipNext(fromAutoAdvance = true)
                }
            }
        }
    }

    fun toggleShuffle() {
        _playerState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun cycleRepeatMode() {
        _playerState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = next)
        }
    }

    fun addToQueueNext(track: Track) {
        if (_playerState.value.currentTrack == null) {
            playTrack(track, listOf(track))
            return
        }
        _playerState.update { state ->
            val mutable = state.queue.toMutableList()
            val insertAt = (state.currentIndex + 1).coerceAtMost(mutable.size)
            mutable.add(insertAt, track)
            state.copy(queue = mutable)
        }
        schedulePrefetchNextTrack(effectiveAudioQuality(), delayMs = 300L)
        showMessage("\"${track.title}\" akan diputar berikutnya")
    }

    fun addToQueueEnd(track: Track) {
        if (_playerState.value.currentTrack == null) {
            playTrack(track, listOf(track))
            return
        }
        _playerState.update { state -> state.copy(queue = state.queue + track) }
        schedulePrefetchNextTrack(effectiveAudioQuality(), delayMs = 500L)
        showMessage("\"${track.title}\" ditambahkan ke antrean")
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _playerState.update { state ->
            if (fromIndex !in state.queue.indices || toIndex !in state.queue.indices) return@update state
            val mutable = state.queue.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            val newCurrent = when (state.currentIndex) {
                fromIndex -> toIndex
                in (toIndex until fromIndex) -> state.currentIndex + 1
                in ((fromIndex + 1)..toIndex) -> state.currentIndex - 1
                else -> state.currentIndex
            }
            state.copy(queue = mutable, currentIndex = newCurrent)
        }
        schedulePrefetchNextTrack(effectiveAudioQuality(), delayMs = 400L)
    }

    fun removeFromQueue(index: Int) {
        _playerState.update { state ->
            if (index !in state.queue.indices || index == state.currentIndex) return@update state
            val updated = state.queue.toMutableList().apply { removeAt(index) }
            val adjustedCurrent = if (index < state.currentIndex) state.currentIndex - 1 else state.currentIndex
            state.copy(queue = updated, currentIndex = adjustedCurrent)
        }
        schedulePrefetchNextTrack(effectiveAudioQuality(), delayMs = 400L)
    }

    fun clearUpcomingQueue() {
        _playerState.update { state ->
            val current = state.currentTrack ?: return@update state
            state.copy(queue = listOf(current), currentIndex = 0)
        }
        showMessage("Antrean berikutnya dibersihkan")
    }

    fun saveQueueAsPlaylist(name: String) {
        val tracks = _playerState.value.queue
        if (name.isBlank() || tracks.isEmpty()) return
        viewModelScope.launch {
            repository.createPlaylistWithTracks(name, "Disimpan dari antrean putar", tracks)
            showMessage("Antrean disimpan ke playlist \"$name\"")
        }
    }

    fun saveTracksAsPlaylist(name: String, description: String, tracks: List<Track>) {
        if (name.isBlank() || tracks.isEmpty()) {
            showMessage("Tidak ada lagu untuk disimpan ke playlist")
            return
        }
        viewModelScope.launch {
            repository.createPlaylistWithTracks(name, description, tracks.distinctBy { it.id })
            showMessage("\"$name\" (${tracks.distinctBy { it.id }.size} lagu) disimpan ke Koleksi!")
        }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _playerState.update {
            it.copy(
                isPlayerExpanded = expanded,
                isDrivingMode = if (!expanded) false else it.isDrivingMode
            )
        }
    }

    fun setDrivingMode(enabled: Boolean) {
        _playerState.update { it.copy(isDrivingMode = enabled) }
    }

    fun nudgeLyricsOffset(deltaMs: Long) {
        _playerState.update { state ->
            val next = (state.lyricsOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
            state.copy(lyricsOffsetMs = next)
        }
    }

    fun resetLyricsOffset() {
        _playerState.update { it.copy(lyricsOffsetMs = 0L) }
    }

    fun setAbLoopPointA() {
        val pos = _playerState.value.positionMs
        val currentB = _playerState.value.abLoopEndMs
        val validB = if (currentB != null && currentB > pos + 500L) currentB else null
        _playerState.update { it.copy(abLoopStartMs = pos, abLoopEndMs = validB) }
        audioEngine.setAbLoop(pos, validB)
        showMessage("Titik A diatur pada ${pos / 1000}d")
    }

    fun setAbLoopPointB() {
        val pos = _playerState.value.positionMs
        val currentA = _playerState.value.abLoopStartMs ?: 0L
        if (pos <= currentA + 500L) {
            showMessage("Titik B harus berada setelah Titik A")
            return
        }
        _playerState.update { it.copy(abLoopStartMs = currentA, abLoopEndMs = pos) }
        audioEngine.setAbLoop(currentA, pos)
        showMessage("A-B Loop aktif!")
    }

    fun clearAbLoop() {
        _playerState.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
        audioEngine.setAbLoop(null, null)
        showMessage("A-B Loop dimatikan")
    }

    private fun fetchLyricsForTrack(track: Track) {
        lyricsJob?.cancel()
        translateLyricsJob?.cancel()
        romanizeLyricsJob?.cancel()
        meaningLyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _playerState.update { it.copy(lyricsState = LyricsState(isLoading = true)) }
            val result = repository.fetchLyrics(track)
            val cachedAi = LyricsTranslator.getCachedEntry(track.id)
            val cachedMeaning = SongMeaningAnalyzer.getCached(track.id)
            val hasSynced = result.syncedLines.isNotEmpty()
            val plainCount = result.plainLyrics?.lines()?.count { it.isNotBlank() } ?: 0

            var hydrated = result
            if (cachedAi != null) {
                if (cachedAi.translatedLines.isNotEmpty()) {
                    hydrated = if (hasSynced && cachedAi.translatedLines.size == result.syncedLines.size) {
                        hydrated.copy(translatedSyncedLines = cachedAi.translatedLines)
                    } else if (!hasSynced && cachedAi.translatedLines.size == plainCount) {
                        hydrated.copy(translatedPlainLines = cachedAi.translatedLines)
                    } else hydrated
                }
                if (cachedAi.romanizedLines.isNotEmpty() && cachedAi.needsRomanization) {
                    hydrated = if (hasSynced && cachedAi.romanizedLines.size == result.syncedLines.size) {
                        hydrated.copy(
                            romanizedSyncedLines = cachedAi.romanizedLines,
                            romanizationScriptLabel = cachedAi.scriptLabel
                        )
                    } else if (!hasSynced && cachedAi.romanizedLines.size == plainCount) {
                        hydrated.copy(
                            romanizedPlainLines = cachedAi.romanizedLines,
                            romanizationScriptLabel = cachedAi.scriptLabel
                        )
                    } else hydrated
                }
            }
            if (cachedMeaning != null) {
                hydrated = hydrated.copy(songMeaning = cachedMeaning)
            }
            _playerState.update { it.copy(lyricsState = hydrated) }
        }
    }

    // ---- Indonesian Lyrics Translation via Firebase AI Logic (Gemini 3.8 Flash + Local Disk Cache) ----
    fun toggleLyricsTranslation() {
        val state = _playerState.value
        val track = state.currentTrack ?: return
        val lyrics = state.lyricsState
        if (lyrics.isLoading || lyrics.isInstrumental) return

        // If already displaying translation, toggle off
        if (lyrics.showTranslation) {
            _playerState.update {
                it.copy(lyricsState = it.lyricsState.copy(showTranslation = false, translationError = null))
            }
            return
        }

        // If translation is already available in state (e.g. from local disk cache), show immediately
        val hasSynced = lyrics.syncedLines.isNotEmpty()
        val alreadyTranslated = if (hasSynced) {
            lyrics.translatedSyncedLines.size == lyrics.syncedLines.size && lyrics.translatedSyncedLines.isNotEmpty()
        } else {
            lyrics.translatedPlainLines.isNotEmpty()
        }
        if (alreadyTranslated) {
            _playerState.update {
                it.copy(lyricsState = it.lyricsState.copy(showTranslation = true, translationError = null))
            }
            return
        }

        val sourceLines = if (hasSynced) {
            lyrics.syncedLines.map { it.text }
        } else {
            lyrics.plainLyrics?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        }
        if (sourceLines.isEmpty()) {
            showMessage("Belum ada lirik untuk diterjemahkan")
            return
        }

        translateLyricsJob?.cancel()
        translateLyricsJob = viewModelScope.launch {
            _playerState.update {
                it.copy(
                    lyricsState = it.lyricsState.copy(
                        isTranslating = true,
                        translationError = null
                    )
                )
            }
            val result = LyricsTranslator.translateLinesToIndonesian(
                trackId = track.id,
                trackTitle = track.title,
                artist = track.artist,
                lines = sourceLines
            )
            // Guard against track change while translating
            if (_playerState.value.currentTrack?.id != track.id) return@launch

            result.onSuccess { translated ->
                _playerState.update { cur ->
                    val updatedLyrics = if (hasSynced) {
                        cur.lyricsState.copy(
                            translatedSyncedLines = translated.lines,
                            showTranslation = true,
                            isTranslating = false,
                            translationError = null
                        )
                    } else {
                        cur.lyricsState.copy(
                            translatedPlainLines = translated.lines,
                            showTranslation = true,
                            isTranslating = false,
                            translationError = null
                        )
                    }
                    cur.copy(lyricsState = updatedLyrics)
                }
            }.onFailure { err ->
                val message = err.message ?: "Gagal menerjemahkan lirik ke Bahasa Indonesia."
                _playerState.update { cur ->
                    cur.copy(
                        lyricsState = cur.lyricsState.copy(
                            isTranslating = false,
                            showTranslation = false,
                            translationError = message
                        )
                    )
                }
                showMessage(message)
            }
        }
    }

    // ---- Lyrics Romanization (Romaji, Hangul, Pinyin, etc.) via Firebase AI Logic + Local Disk Cache ----
    fun toggleLyricsRomanization() {
        val state = _playerState.value
        val track = state.currentTrack ?: return
        val lyrics = state.lyricsState
        if (lyrics.isLoading || lyrics.isInstrumental) return

        if (lyrics.showRomanization) {
            _playerState.update {
                it.copy(lyricsState = it.lyricsState.copy(showRomanization = false, romanizationError = null))
            }
            return
        }

        val hasSynced = lyrics.syncedLines.isNotEmpty()
        val alreadyRomanized = if (hasSynced) {
            lyrics.romanizedSyncedLines.size == lyrics.syncedLines.size &&
                lyrics.romanizedSyncedLines.any { it.isNotBlank() }
        } else {
            lyrics.romanizedPlainLines.any { it.isNotBlank() }
        }
        if (alreadyRomanized) {
            _playerState.update {
                it.copy(lyricsState = it.lyricsState.copy(showRomanization = true, romanizationError = null))
            }
            return
        }

        val sourceLines = if (hasSynced) {
            lyrics.syncedLines.map { it.text }
        } else {
            lyrics.plainLyrics?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        }
        if (sourceLines.isEmpty()) {
            showMessage("Belum ada lirik untuk diromanisasi")
            return
        }

        romanizeLyricsJob?.cancel()
        romanizeLyricsJob = viewModelScope.launch {
            _playerState.update {
                it.copy(
                    lyricsState = it.lyricsState.copy(
                        isRomanizing = true,
                        romanizationError = null
                    )
                )
            }
            val result = LyricsTranslator.romanizeLines(
                trackId = track.id,
                trackTitle = track.title,
                artist = track.artist,
                lines = sourceLines
            )
            if (_playerState.value.currentTrack?.id != track.id) return@launch

            result.onSuccess { romanized ->
                if (!romanized.needsRomanization || romanized.lines.none { it.isNotBlank() }) {
                    _playerState.update { cur ->
                        cur.copy(
                            lyricsState = cur.lyricsState.copy(
                                isRomanizing = false,
                                showRomanization = false,
                                romanizationError = null
                            )
                        )
                    }
                    showMessage("Lirik lagu ini sudah menggunakan huruf Latin")
                    return@launch
                }
                _playerState.update { cur ->
                    val updatedLyrics = if (hasSynced) {
                        cur.lyricsState.copy(
                            romanizedSyncedLines = romanized.lines,
                            romanizationScriptLabel = romanized.scriptLabel,
                            showRomanization = true,
                            isRomanizing = false,
                            romanizationError = null
                        )
                    } else {
                        cur.lyricsState.copy(
                            romanizedPlainLines = romanized.lines,
                            romanizationScriptLabel = romanized.scriptLabel,
                            showRomanization = true,
                            isRomanizing = false,
                            romanizationError = null
                        )
                    }
                    cur.copy(lyricsState = updatedLyrics)
                }
            }.onFailure { err ->
                val message = err.message ?: "Gagal membuat romanisasi lirik."
                _playerState.update { cur ->
                    cur.copy(
                        lyricsState = cur.lyricsState.copy(
                            isRomanizing = false,
                            showRomanization = false,
                            romanizationError = message
                        )
                    )
                }
                showMessage(message)
            }
        }
    }

    // ---- Song Meaning Breakdown ("Bedah Makna Lagu") via Firebase AI Logic + Local Disk Cache ----
    fun analyzeCurrentSongMeaning(forceRefresh: Boolean = false) {
        val state = _playerState.value
        val track = state.currentTrack ?: return
        val lyrics = state.lyricsState
        if (lyrics.isLoading) return
        if (lyrics.isInstrumental) {
            showMessage("Lagu instrumental tidak memiliki lirik untuk dibedah")
            return
        }
        if (!forceRefresh && lyrics.songMeaning != null) {
            return
        }

        val sourceLines = if (lyrics.syncedLines.isNotEmpty()) {
            lyrics.syncedLines.map { it.text }
        } else {
            lyrics.plainLyrics?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        }
        if (sourceLines.isEmpty()) {
            val msg = "Belum ada lirik yang bisa dibedah untuk lagu ini."
            _playerState.update { cur ->
                cur.copy(lyricsState = cur.lyricsState.copy(isAnalyzingMeaning = false, meaningError = msg))
            }
            showMessage(msg)
            return
        }

        meaningLyricsJob?.cancel()
        meaningLyricsJob = viewModelScope.launch {
            _playerState.update { cur ->
                cur.copy(
                    lyricsState = cur.lyricsState.copy(
                        isAnalyzingMeaning = true,
                        meaningError = null
                    )
                )
            }
            val result = SongMeaningAnalyzer.analyzeSongMeaning(
                trackId = track.id,
                trackTitle = track.title,
                artist = track.artist,
                lyricsLines = sourceLines,
                forceRefresh = forceRefresh
            )
            if (_playerState.value.currentTrack?.id != track.id) return@launch

            result.onSuccess { meaning ->
                _playerState.update { cur ->
                    cur.copy(
                        lyricsState = cur.lyricsState.copy(
                            songMeaning = meaning,
                            isAnalyzingMeaning = false,
                            meaningError = null
                        )
                    )
                }
            }.onFailure { err ->
                val message = err.message ?: "Gagal membedah makna lagu."
                _playerState.update { cur ->
                    cur.copy(
                        lyricsState = cur.lyricsState.copy(
                            isAnalyzingMeaning = false,
                            meaningError = message
                        )
                    )
                }
                showMessage(message)
            }
        }
    }

    // ---- Metrolist-Style Video Mode (Prioritizing Lyric Video) ----
    fun attachVideoTextureView(textureView: TextureView?) {
        audioEngine.attachVideoTextureView(textureView)
    }

    fun clearVideoTextureView(textureView: TextureView?) {
        audioEngine.clearVideoTextureView(textureView)
    }

    fun setVideoMode(enabled: Boolean) {
        val state = _playerState.value
        val track = state.currentTrack ?: return
        if (state.isVideoMode == enabled) return
        if (enabled && track.isLocal) {
            showMessage("Mode Video hanya tersedia untuk lagu streaming YouTube Music")
            return
        }

        val currentPos = state.positionMs
        if (enabled) {
            _playerState.update {
                it.copy(
                    isVideoMode = true,
                    isLoadingVideo = true,
                    videoError = null
                )
            }
            val existingCandidates = state.videoCandidates
            if (existingCandidates.isNotEmpty()) {
                val targetId = state.selectedVideoId ?: existingCandidates.first().videoId
                playSelectedVideoId(targetId, startPositionMs = currentPos)
            } else {
                loadAndPlayLyricVideoForCurrentTrack(track, startPositionMs = currentPos)
            }
        } else {
            videoResolveJob?.cancel()
            _playerState.update {
                it.copy(
                    isVideoMode = false,
                    isLoadingVideo = false,
                    videoError = null
                )
            }
            audioEngine.setNextTrackStartOptions(startPositionMs = currentPos, autoStart = state.isPlaying || state.isBuffering)
            resolveAndPlayTrack(track, fromAutoAdvance = false)
        }
    }

    fun selectVideoCandidate(candidate: VideoCandidate) {
        val state = _playerState.value
        if (state.selectedVideoId == candidate.videoId && state.videoError == null) return
        _playerState.update {
            it.copy(
                isVideoMode = true,
                selectedVideoId = candidate.videoId,
                isLoadingVideo = true,
                videoError = null
            )
        }
        playSelectedVideoId(candidate.videoId, startPositionMs = 0L)
    }

    private fun loadAndPlayLyricVideoForCurrentTrack(track: Track, startPositionMs: Long) {
        videoResolveJob?.cancel()
        resolveStreamJob?.cancel()
        videoResolveJob = viewModelScope.launch {
            _playerState.update {
                it.copy(
                    isLoadingVideo = true,
                    isBuffering = true,
                    videoError = null,
                    audioError = null
                )
            }
            val candidates = repository.searchLyricVideosForTrack(track)
            if (_playerState.value.currentTrack?.id != track.id || !_playerState.value.isVideoMode) return@launch

            if (candidates.isEmpty()) {
                _playerState.update {
                    it.copy(
                        isLoadingVideo = false,
                        videoError = "Lyric video tidak ditemukan untuk lagu ini."
                    )
                }
                return@launch
            }

            // Priority #1: Lyric Video first (sorted by InnerTubeClient.searchLyricVideosForTrack)
            val chosen = candidates.first()
            _playerState.update {
                it.copy(
                    videoCandidates = candidates,
                    selectedVideoId = chosen.videoId
                )
            }
            resolveAndStartVideoCandidate(chosen.videoId, candidates, startPositionMs)
        }
    }

    private fun playSelectedVideoId(videoId: String, startPositionMs: Long) {
        videoResolveJob?.cancel()
        resolveStreamJob?.cancel()
        videoResolveJob = viewModelScope.launch {
            _playerState.update {
                it.copy(
                    isLoadingVideo = true,
                    isBuffering = true,
                    videoError = null,
                    audioError = null
                )
            }
            resolveAndStartVideoCandidate(videoId, _playerState.value.videoCandidates, startPositionMs)
        }
    }

    private suspend fun resolveAndStartVideoCandidate(
        primaryVideoId: String,
        candidates: List<VideoCandidate>,
        startPositionMs: Long
    ) {
        val attemptOrder = (listOf(primaryVideoId) + candidates.map { it.videoId }).distinct().take(3)
        var lastError = "Stream video belum dapat diputar."
        for (vid in attemptOrder) {
            when (val res = repository.resolveVideoStream(vid)) {
                is VideoStreamResolveResult.Success -> {
                    val stream = res.stream
                    val matched = candidates.firstOrNull { it.videoId == vid }
                    val tag = if (matched?.isLyricVideo == true) {
                        "LYRIC VIDEO • ${stream.qualityLabel}"
                    } else {
                        "VIDEO • ${stream.qualityLabel}"
                    }
                    _playerState.update {
                        it.copy(
                            selectedVideoId = vid,
                            isLoadingVideo = false,
                            videoError = null,
                            audioError = null,
                            videoQualityLabel = stream.qualityLabel,
                            streamSourceLabel = tag,
                            durationMs = stream.durationMs?.takeIf { d -> d > 0L } ?: it.durationMs
                        )
                    }
                    audioEngine.setNextTrackStartOptions(startPositionMs = startPositionMs, autoStart = true)
                    audioEngine.playVideoSource(
                        videoUrl = stream.videoUrl,
                        audioUrl = stream.audioUrl,
                        userAgent = stream.userAgent,
                        cookie = stream.cookie
                    )
                    return
                }
                is VideoStreamResolveResult.Unavailable -> {
                    lastError = res.reason
                }
            }
        }

        _playerState.update {
            it.copy(
                isLoadingVideo = false,
                isBuffering = false,
                videoError = lastError
            )
        }
    }

    // ---- Offline Downloads & Cache Storage ----
    fun downloadTrack(track: Track) {
        if (track.isLocal) {
            showMessage("Lagu lokal sudah tersimpan di perangkat")
            return
        }
        if (settings.value.downloadWifiOnly && !isOnWifi()) {
            showMessage("Unduh Khusus Wi-Fi aktif — sambungkan ke Wi-Fi untuk mengunduh")
            return
        }
        if (activeDownloads.value.containsKey(track.id)) return
        viewModelScope.launch {
            showMessage("Mengunduh \"${track.title}\" untuk mode offline…")
            when (val resolved = repository.resolveFullAudioStream(track, settings.value.audioQuality)) {
                is StreamResolveResult.Success -> {
                    val res = downloadManager.downloadTrack(track, resolved.stream)
                    if (res.isSuccess) {
                        refreshStorageStats()
                        showMessage("\"${track.title}\" selesai diunduh (Offline Full)")
                    } else {
                        showMessage("Gagal mengunduh: ${res.exceptionOrNull()?.message ?: "Kesalahan jaringan"}")
                    }
                }
                is StreamResolveResult.LoginRequired -> {
                    showMessage("Login YouTube Music terlebih dahulu untuk mengunduh lagu ini")
                }
                is StreamResolveResult.Unavailable -> {
                    showMessage("Audio belum tersedia untuk diunduh saat ini")
                }
            }
        }
    }

    fun removeDownload(track: Track) {
        viewModelScope.launch {
            downloadManager.removeDownloadedTrack(track.id)
            refreshStorageStats()
            showMessage("Unduhan \"${track.title}\" dihapus")
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadManager.clearAllDownloads()
            refreshStorageStats()
            showMessage("Semua lagu unduhan offline telah dihapus")
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val freedDisk = downloadManager.clearAppCache()
            val freedStream = audioEngine.clearStreamCache()
            InnerTubeClient.clearStreamCache()
            refreshStorageStats()
            val mb = (freedDisk + freedStream) / (1024.0 * 1024.0)
            showMessage("Cache dibersihkan (%.1f MB dibebaskan)".format(mb))
        }
    }

    fun clearLyricsAiCache() {
        viewModelScope.launch {
            val freed = LyricsTranslator.clearLocalCache()
            refreshStorageStats()
            val kb = freed / 1024.0
            val label = if (kb >= 1024.0) "%.1f MB".format(kb / 1024.0) else "%.0f KB".format(kb)
            showMessage("Cache terjemahan & romanisasi lirik dibersihkan ($label)")
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val (dl, cache) = downloadManager.getStorageStatsBytes()
            val lyricsCache = LyricsTranslator.getCacheSizeBytes()
            _storageStats.value = StorageStats(
                downloadsBytes = dl,
                cacheBytes = cache,
                lyricsAiCacheBytes = lyricsCache
            )
        }
    }

    // ---- YouTube Music Account Login & Library Sync ----
    fun completeGoogleYouTubeLogin(
        name: String,
        email: String,
        avatarUrl: String?,
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSyncingAccount.value = true
            val existing = youtubeAccount.value
            val displayName = name.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                ?: "Pengguna Google"
            val updated = existing.copy(
                name = displayName,
                email = email,
                avatarUrl = avatarUrl ?: existing.avatarUrl
            )
            preferences.saveYouTubeAccount(updated)
            showMessage("Terhubung ke YouTube Music sebagai $displayName!")
            _isSyncingAccount.value = false
            onFinished()
        }
    }

    fun completeYouTubeLogin(cookie: String, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncingAccount.value = true
            val existing = youtubeAccount.value
            val account = repository.verifyAndFetchAccount(cookie)
            if (account != null && account.hasCookieSession) {
                val merged = account.copy(
                    name = account.name.ifBlank { existing.name },
                    email = account.email.ifBlank { existing.email },
                    avatarUrl = account.avatarUrl ?: existing.avatarUrl
                )
                preferences.saveYouTubeAccount(merged)
                InnerTubeClient.updateAuth(merged.cookie, merged.visitorData)
                showMessage("Sesi YouTube Music aktif untuk ${merged.name.ifBlank { "akunmu" }}!")
                // Retry current track immediately if it was waiting for login
                val current = _playerState.value.currentTrack
                if (current != null && _playerState.value.requiresYouTubeLogin) {
                    resolveAndPlayTrack(current)
                }
                onFinished()
            } else {
                showMessage("Cookie tidak valid. Pastikan kamu sudah masuk ke YouTube Music.")
            }
            _isSyncingAccount.value = false
        }
    }

    fun syncYouTubeAccountLibrary() {
        viewModelScope.launch {
            val acc = youtubeAccount.value
            if (!acc.isLoggedIn) {
                showMessage("Masuk ke akun YouTube Music terlebih dahulu")
                return@launch
            }
            if (!acc.hasCookieSession) {
                showMessage("Akun Google aktif. Buka Login YTM > Mode Lanjutan (Cookie) untuk tarik playlist pribadi.")
                return@launch
            }
            _isSyncingAccount.value = true
            val (likedCount, playlistCount) = repository.syncYouTubeMusicLibrary()
            _isSyncingAccount.value = false
            showMessage("Sinkronisasi selesai: $likedCount lagu disukai & $playlistCount playlist")
        }
    }

    fun logoutYouTubeAccount() {
        viewModelScope.launch {
            preferences.clearYouTubeAccount()
            InnerTubeClient.updateAuth("")
            showMessage("Berhasil keluar dari akun YouTube Music")
        }
    }

    // ---- Backup & Restore Library JSON ----
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val res = downloadManager.exportBackupToUri(uri)
            if (res.isSuccess) {
                showMessage("Backup berhasil disimpan (${res.getOrDefault(0)} item koleksi)")
            } else {
                showMessage("Gagal membuat backup: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val res = downloadManager.importBackupFromUri(uri)
            if (res.isSuccess) {
                val (liked, pls) = res.getOrDefault(0 to 0)
                showMessage("Restore selesai: $liked lagu disukai & $pls playlist dipulihkan")
            } else {
                showMessage("Gagal memulihkan backup: format file tidak sesuai")
            }
        }
    }

    // ---- Cloud Library Sync (Firebase Firestore per-user snapshot) ----
    fun refreshCloudSyncMeta() {
        viewModelScope.launch {
            val res = cloudLibraryRepository.downloadLibrarySnapshot()
            res.onSuccess { meta ->
                if (meta != null) {
                    _cloudSyncState.update {
                        it.copy(
                            lastSyncedAtMillis = meta.updatedAt?.toDate()?.time ?: meta.createdAt?.toDate()?.time,
                            cloudLikedCount = meta.likedCount,
                            cloudPlaylistCount = meta.playlistCount,
                            cloudDeviceLabel = meta.deviceLabel
                        )
                    }
                }
            }
        }
    }

    fun uploadLibraryToCloud() {
        if (_cloudSyncState.value.isSyncing) return
        viewModelScope.launch {
            _cloudSyncState.update { it.copy(isSyncing = true) }
            val built = downloadManager.buildBackupJsonPayload()
            built.onSuccess { (json, likedCount, playlistCount) ->
                val upload = cloudLibraryRepository.uploadLibrarySnapshot(
                    payloadJson = json,
                    likedCount = likedCount,
                    playlistCount = playlistCount
                )
                upload.onSuccess {
                    _cloudSyncState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            cloudLikedCount = likedCount,
                            cloudPlaylistCount = playlistCount
                        )
                    }
                    showMessage("Koleksi diunggah ke Cloud ($likedCount disukai & $playlistCount playlist)")
                }.onFailure { err ->
                    _cloudSyncState.update { it.copy(isSyncing = false) }
                    showMessage(err.message ?: "Gagal mengunggah koleksi ke Cloud")
                }
            }.onFailure { err ->
                _cloudSyncState.update { it.copy(isSyncing = false) }
                showMessage("Gagal menyiapkan data koleksi: ${err.message}")
            }
        }
    }

    fun restoreLibraryFromCloud() {
        if (_cloudSyncState.value.isSyncing) return
        viewModelScope.launch {
            _cloudSyncState.update { it.copy(isSyncing = true) }
            val downloaded = cloudLibraryRepository.downloadLibrarySnapshot()
            downloaded.onSuccess { meta ->
                if (meta == null || meta.payloadJson.isBlank()) {
                    _cloudSyncState.update { it.copy(isSyncing = false) }
                    showMessage("Belum ada cadangan koleksi di Cloud untuk akun ini")
                    return@onSuccess
                }
                val restored = downloadManager.importBackupJsonString(meta.payloadJson)
                restored.onSuccess { (liked, pls) ->
                    _cloudSyncState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncedAtMillis = meta.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                            cloudLikedCount = meta.likedCount,
                            cloudPlaylistCount = meta.playlistCount,
                            cloudDeviceLabel = meta.deviceLabel
                        )
                    }
                    showMessage("Pulih dari Cloud: $liked lagu disukai & $pls playlist")
                }.onFailure { err ->
                    _cloudSyncState.update { it.copy(isSyncing = false) }
                    showMessage("Gagal memulihkan data Cloud: ${err.message}")
                }
            }.onFailure { err ->
                _cloudSyncState.update { it.copy(isSyncing = false) }
                showMessage(err.message ?: "Gagal memuat koleksi dari Cloud")
            }
        }
    }

    // ---- Import YouTube / YouTube Music Playlist URL ----
    fun importYouTubePlaylistUrl(
        rawUrl: String,
        customName: String? = null,
        onFinished: (Boolean) -> Unit = {}
    ) {
        if (_cloudSyncState.value.isImportingPlaylist) return
        viewModelScope.launch {
            _cloudSyncState.update { it.copy(isImportingPlaylist = true) }
            val result = repository.importYouTubePlaylistByUrl(rawUrl, customName)
            _cloudSyncState.update { it.copy(isImportingPlaylist = false) }
            result.onSuccess { (playlistName, count) ->
                showMessage("Berhasil mengimpor \"$playlistName\" ($count lagu)")
                onFinished(true)
            }.onFailure { err ->
                showMessage(err.message ?: "Gagal mengimpor playlist YouTube")
                onFinished(false)
            }
        }
    }

    // ---- Artist Radio ----
    fun startArtistRadio(artistName: String, seedTrack: Track?) {
        viewModelScope.launch {
            showMessage("Menyiapkan Radio Artis $artistName…")
            val radioTracks = if (seedTrack != null) {
                repository.fetchRadioQueue(seedTrack)
            } else {
                repository.fetchTracksByQuery("$artistName mix", limit = 25)
            }
            if (radioTracks.isNotEmpty()) {
                playTrack(radioTracks.first(), radioTracks, expandPlayer = true)
            } else {
                showMessage("Gagal memuat Radio Artis untuk $artistName")
            }
        }
    }

    // ---- Sleep Timer with smooth 10-second volume fade-out ----
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        audioEngine.setSleepFadeMultiplier(1.0f)
        if (minutes <= 0) {
            _playerState.update { it.copy(sleepTimerRemainingSec = null, sleepAfterCurrentTrack = false) }
            showMessage("Timer tidur dimatikan")
            return
        }
        _playerState.update { it.copy(sleepAfterCurrentTrack = false, sleepTimerRemainingSec = minutes * 60) }
        showMessage("Timer tidur diatur $minutes menit (fade-out halus 10d terakhir)")
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _playerState.update { it.copy(sleepTimerRemainingSec = remaining) }
                if (remaining in 1..10) {
                    val fade = (remaining / 10f).coerceIn(0.08f, 1.0f)
                    audioEngine.setSleepFadeMultiplier(fade)
                }
            }
            audioEngine.pause()
            audioEngine.setSleepFadeMultiplier(1.0f)
            _playerState.update { it.copy(sleepTimerRemainingSec = null) }
            showMessage("Timer tidur selesai — musik dijeda lembut")
        }
    }

    fun setSleepAfterCurrentTrack() {
        sleepTimerJob?.cancel()
        audioEngine.setSleepFadeMultiplier(1.0f)
        _playerState.update { it.copy(sleepTimerRemainingSec = null, sleepAfterCurrentTrack = true) }
        showMessage("Musik akan berhenti setelah lagu ini selesai")
    }

    // ---- Library & Playlists ----
    fun toggleLike(track: Track) {
        viewModelScope.launch {
            val isNowLiked = repository.toggleLike(track)
            showMessage(
                if (isNowLiked) "Ditambahkan ke Lagu Disukai"
                else "Dihapus dari Lagu Disukai"
            )
        }
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description)
            showMessage("Playlist \"$name\" dibuat")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            showMessage("Playlist dihapus")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, playlistName: String, track: Track) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
            showMessage("Ditambahkan ke \"$playlistName\"")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            showMessage("Lagu dihapus dari playlist")
        }
    }

    fun importLocalAudio(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importLocalAudioUris(uris)
            if (imported.isNotEmpty()) {
                showMessage("${imported.size} file audio lokal berhasil diimpor")
            } else {
                showMessage("Gagal membaca file audio yang dipilih")
            }
        }
    }

    fun deleteLocalTrack(trackId: String) {
        viewModelScope.launch {
            repository.deleteLocalTrack(trackId)
            showMessage("Audio lokal dihapus dari daftar")
        }
    }

    fun clearPlayHistory() {
        viewModelScope.launch {
            repository.clearPlayHistory()
            showMessage("Riwayat pemutaran dibersihkan")
        }
    }

    // ---- Collection Detail Screen ----
    fun openCollectionDetail(type: CollectionDetailType) {
        detailObserveJob?.cancel()
        when (type) {
            CollectionDetailType.LikedSongs -> {
                detailObserveJob = viewModelScope.launch {
                    likedTracks.collectLatest { list ->
                        _detailState.value = CollectionDetailState(
                            type = type,
                            title = "Lagu Disukai",
                            subtitle = "${list.size} lagu favorit tersimpan",
                            coverUrl = list.firstOrNull()?.artworkUrl,
                            tracks = list,
                            accentHex = 0xFF00E5A0
                        )
                    }
                }
            }
            CollectionDetailType.Downloads -> {
                detailObserveJob = viewModelScope.launch {
                    downloadedTracks.collectLatest { list ->
                        _detailState.value = CollectionDetailState(
                            type = type,
                            title = "Unduhan Offline",
                            subtitle = "${list.size} lagu siap diputar tanpa internet",
                            coverUrl = list.firstOrNull()?.artworkUrl,
                            tracks = list,
                            accentHex = 0xFF00C2FF
                        )
                    }
                }
            }
            CollectionDetailType.History -> {
                detailObserveJob = viewModelScope.launch {
                    playHistory.collectLatest { list ->
                        _detailState.value = CollectionDetailState(
                            type = type,
                            title = "Riwayat Putar",
                            subtitle = "${list.size} pemutaran terakhir",
                            coverUrl = list.firstOrNull()?.artworkUrl,
                            tracks = list,
                            accentHex = 0xFFFFB547
                        )
                    }
                }
            }
            CollectionDetailType.LocalAudio -> {
                detailObserveJob = viewModelScope.launch {
                    localTracks.collectLatest { list ->
                        _detailState.value = CollectionDetailState(
                            type = type,
                            title = "Audio Perangkat",
                            subtitle = "${list.size} file audio lokal",
                            coverUrl = null,
                            tracks = list,
                            accentHex = 0xFF8B7BFF
                        )
                    }
                }
            }
            is CollectionDetailType.CustomPlaylist -> {
                val summary = playlists.value.find { it.id == type.playlistId }
                detailObserveJob = viewModelScope.launch {
                    repository.observePlaylistTracks(type.playlistId).collectLatest { list ->
                        _detailState.value = CollectionDetailState(
                            type = type,
                            title = summary?.name ?: "Playlist",
                            subtitle = summary?.description?.takeIf { it.isNotBlank() }
                                ?: "${list.size} lagu dalam playlist",
                            coverUrl = list.firstOrNull()?.artworkUrl ?: summary?.coverUrl,
                            tracks = list,
                            accentHex = 0xFF00E5A0
                        )
                    }
                }
            }
            is CollectionDetailType.MoodCollection -> {
                _detailState.value = CollectionDetailState(
                    type = type,
                    title = type.mood.label,
                    subtitle = type.mood.subtitle,
                    isLoading = true,
                    accentHex = type.mood.startColor
                )
                detailObserveJob = viewModelScope.launch {
                    val tracks = repository.fetchTracksByQuery(type.mood.query, limit = 25)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            coverUrl = tracks.firstOrNull()?.artworkUrl,
                            tracks = tracks
                        )
                    }
                }
            }
            is CollectionDetailType.ArtistCollection -> {
                _detailState.value = CollectionDetailState(
                    type = type,
                    title = type.artist.name,
                    subtitle = "YouTube Music • ${type.artist.genre}",
                    coverUrl = type.artist.artworkUrl,
                    isLoading = true,
                    accentHex = 0xFF00E5A0
                )
                detailObserveJob = viewModelScope.launch {
                    val tracks = repository.fetchArtistTracks(type.artist)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            coverUrl = it.coverUrl ?: tracks.firstOrNull()?.artworkUrl,
                            tracks = tracks
                        )
                    }
                }
            }
            is CollectionDetailType.AlbumCollection -> {
                _detailState.value = CollectionDetailState(
                    type = type,
                    title = type.album.title,
                    subtitle = "${type.album.artist} • ${type.album.releaseYear.ifBlank { type.album.genre }}",
                    coverUrl = type.album.artworkUrl,
                    isLoading = true,
                    accentHex = 0xFFFFB547
                )
                detailObserveJob = viewModelScope.launch {
                    val tracks = repository.fetchAlbumTracks(type.album)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            tracks = tracks
                        )
                    }
                }
            }
            is CollectionDetailType.RemotePlaylistCollection -> {
                _detailState.value = CollectionDetailState(
                    type = type,
                    title = type.playlist.title,
                    subtitle = type.playlist.author,
                    coverUrl = type.playlist.artworkUrl,
                    isLoading = true,
                    accentHex = 0xFF8B7BFF
                )
                detailObserveJob = viewModelScope.launch {
                    val tracks = repository.fetchRemotePlaylistTracks(type.playlist)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            coverUrl = it.coverUrl ?: tracks.firstOrNull()?.artworkUrl,
                            tracks = tracks
                        )
                    }
                }
            }
        }
    }

    fun closeCollectionDetail() {
        detailObserveJob?.cancel()
        _detailState.value = CollectionDetailState()
    }

    // ---- Settings & Audio FX ----
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun setAccentPreset(accent: AccentPreset) = viewModelScope.launch { preferences.setAccentPreset(accent) }
    fun setPlayerArtStyle(style: PlayerArtStyle) = viewModelScope.launch { preferences.setPlayerArtStyle(style) }
    fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) = viewModelScope.launch { preferences.setPlayerBackgroundStyle(style) }
    fun setAudioQuality(quality: AudioQuality) = viewModelScope.launch { preferences.setAudioQuality(quality) }
    fun setEqPreset(preset: EqPreset) = viewModelScope.launch { preferences.setEqPreset(preset) }
    fun setCustomBand(index: Int, valueDb: Float) {
        val current = settings.value.customBandsDb.toMutableList()
        if (index in current.indices) {
            current[index] = valueDb.coerceIn(-6f, 6f)
            viewModelScope.launch { preferences.setCustomBands(current) }
        }
    }
    fun setBassBoost(strength: Float) = viewModelScope.launch { preferences.setBassBoost(strength) }
    fun setNormalizeAudio(enabled: Boolean) = viewModelScope.launch { preferences.setNormalizeAudio(enabled) }
    fun setSkipSilence(enabled: Boolean) = viewModelScope.launch { preferences.setSkipSilence(enabled) }
    fun setReverbEnabled(enabled: Boolean) = viewModelScope.launch { preferences.setReverbEnabled(enabled) }
    fun applyAudioVibePreset(vibe: String) {
        viewModelScope.launch {
            when (vibe) {
                "slowed" -> {
                    preferences.setPlaybackSpeed(0.84f)
                    preferences.setPlaybackPitch(0.86f)
                    preferences.setReverbEnabled(true)
                    preferences.setBassBoost(0.45f)
                    preferences.setCustomBands(listOf(3.5f, 2.0f, 0f, -1.0f, -1.5f))
                    showMessage("Efek Slowed + Reverb aktif")
                }
                "nightcore" -> {
                    preferences.setPlaybackSpeed(1.22f)
                    preferences.setPlaybackPitch(1.18f)
                    preferences.setReverbEnabled(false)
                    preferences.setBassBoost(0.25f)
                    preferences.setCustomBands(listOf(1.5f, 0.5f, 1.0f, 2.5f, 3.0f))
                    showMessage("Efek Nightcore aktif")
                }
                "lofi" -> {
                    preferences.setPlaybackSpeed(0.94f)
                    preferences.setPlaybackPitch(0.96f)
                    preferences.setReverbEnabled(true)
                    preferences.setBassBoost(0.35f)
                    preferences.setCustomBands(listOf(3.0f, 2.5f, -0.5f, -2.5f, -4.0f))
                    showMessage("Efek Lo-Fi Hangat aktif")
                }
                else -> {
                    preferences.setPlaybackSpeed(1.0f)
                    preferences.setPlaybackPitch(1.0f)
                    preferences.setReverbEnabled(false)
                    preferences.setBassBoost(0f)
                    preferences.setEqPreset(EqPreset.FLAT)
                    showMessage("Audio kembali ke mode Normal")
                }
            }
        }
    }
    fun setAutoplayRadio(enabled: Boolean) = viewModelScope.launch { preferences.setAutoplayRadio(enabled) }
    fun setKeepScreenOnLyrics(enabled: Boolean) = viewModelScope.launch { preferences.setKeepScreenOnLyrics(enabled) }
    fun setLyricsFontScale(scale: Float) = viewModelScope.launch { preferences.setLyricsFontScale(scale) }
    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch { preferences.setPlaybackSpeed(speed) }
    fun setPlaybackPitch(pitch: Float) = viewModelScope.launch { preferences.setPlaybackPitch(pitch) }
    fun setGaplessPlayback(enabled: Boolean) = viewModelScope.launch { preferences.setGaplessPlayback(enabled) }
    fun setDynamicArtworkColor(enabled: Boolean) = viewModelScope.launch { preferences.setDynamicArtworkColor(enabled) }
    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch { preferences.setCrossfadeSeconds(seconds) }
    fun setDataSaverCellular(enabled: Boolean) = viewModelScope.launch { preferences.setDataSaverCellular(enabled) }
    fun setDownloadWifiOnly(enabled: Boolean) = viewModelScope.launch { preferences.setDownloadWifiOnly(enabled) }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun consumeMessage() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
