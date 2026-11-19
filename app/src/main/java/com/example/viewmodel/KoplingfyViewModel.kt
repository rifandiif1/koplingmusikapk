package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.OfflineDownloadManager
import com.example.data.local.KoplingfyDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.remote.InnerTubeClient
import com.example.data.remote.StreamResolveResult
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
import com.example.model.ThemeMode
import com.example.model.Track
import com.example.model.YouTubeAccount
import com.example.player.KoplingfyAudioEngine
import com.example.player.KoplingfyMediaBridge
import kotlinx.coroutines.Job
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
    val isDrivingMode: Boolean = false
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
    val cacheBytes: Long = 0L
)

class KoplingfyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KoplingfyDatabase.getInstance(application)
    private val repository = MusicRepository(application, database.dao())
    private val downloadManager = OfflineDownloadManager(application, database.dao())
    private val preferences = PreferencesRepository(application)

    private val audioEngine = KoplingfyAudioEngine(
        context = application,
        onTrackCompleted = { handleTrackCompleted() }
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

    private val _isSyncingAccount = MutableStateFlow(false)
    val isSyncingAccount: StateFlow<Boolean> = _isSyncingAccount.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var resolveStreamJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var detailObserveJob: Job? = null

    init {
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
                        audioError = engine.errorMessage ?: state.audioError
                    )
                    updatedState = next
                    next
                }
                updatedState?.let { publishMediaBridgeState(it) }
            }
        }
    }

    private fun observeSettingsForAudio() {
        viewModelScope.launch {
            settings.collectLatest { cfg ->
                audioEngine.updatePlaybackParams(cfg.playbackSpeed, cfg.playbackPitch)
                audioEngine.updateEqualizerBands(cfg.customBandsDb)
                audioEngine.updateBassBoostAndNormalization(cfg.bassBoostStrength, cfg.normalizeAudio)
                audioEngine.updateCrossfadeSeconds(cfg.crossfadeSeconds)
            }
        }
    }

    suspend fun quickSearchTracksForSharedQueue(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return repository.fetchTracksByQuery(query.trim(), limit = 15)
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
                abLoopEndMs = null
            )
        }
        resolveAndPlayTrack(track)
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

    private fun resolveAndPlayTrack(track: Track) {
        resolveStreamJob?.cancel()
        resolveStreamJob = viewModelScope.launch {
            _playerState.update {
                it.copy(isBuffering = true, audioError = null, requiresYouTubeLogin = false)
            }
            val useDataSaver = settings.value.dataSaverCellular && isOnCellular()
            val effectiveQuality = if (useDataSaver) AudioQuality.LOW else settings.value.audioQuality
            when (val result = repository.resolveFullAudioStream(track, effectiveQuality)) {
                is StreamResolveResult.Success -> {
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
                        cookie = stream.cookie
                    )
                }
                is StreamResolveResult.LoginRequired -> {
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

    fun skipNext() {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        val nextIndex = when {
            state.isShuffleEnabled && state.queue.size > 1 -> {
                val candidates = state.queue.indices.filter { it != state.currentIndex }
                candidates.random()
            }
            state.currentIndex + 1 < state.queue.size -> state.currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> {
                // Metrolist Autoplay Radio: append similar tracks when reaching end of queue!
                val seed = state.currentTrack
                if (settings.value.autoplayRadio && seed != null && !seed.isLocal) {
                    viewModelScope.launch {
                        val extra = repository.fetchRadioQueue(seed)
                            .filter { candidate -> state.queue.none { it.id == candidate.id } }
                        if (extra.isNotEmpty()) {
                            val newQueue = state.queue + extra
                            playTrack(extra.first(), newQueue)
                        }
                    }
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
                state.currentTrack?.let { resolveAndPlayTrack(it) }
            }
            else -> skipNext()
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
        showMessage("\"${track.title}\" akan diputar berikutnya")
    }

    fun addToQueueEnd(track: Track) {
        if (_playerState.value.currentTrack == null) {
            playTrack(track, listOf(track))
            return
        }
        _playerState.update { state -> state.copy(queue = state.queue + track) }
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
    }

    fun removeFromQueue(index: Int) {
        _playerState.update { state ->
            if (index !in state.queue.indices || index == state.currentIndex) return@update state
            val updated = state.queue.toMutableList().apply { removeAt(index) }
            val adjustedCurrent = if (index < state.currentIndex) state.currentIndex - 1 else state.currentIndex
            state.copy(queue = updated, currentIndex = adjustedCurrent)
        }
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
        lyricsJob = viewModelScope.launch {
            _playerState.update { it.copy(lyricsState = LyricsState(isLoading = true)) }
            val result = repository.fetchLyrics(track)
            _playerState.update { it.copy(lyricsState = result) }
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
            val freedBytes = downloadManager.clearAppCache()
            refreshStorageStats()
            val mb = freedBytes / (1024.0 * 1024.0)
            showMessage("Cache dibersihkan (%.1f MB dibebaskan)".format(mb))
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val (dl, cache) = downloadManager.getStorageStatsBytes()
            _storageStats.value = StorageStats(downloadsBytes = dl, cacheBytes = cache)
        }
    }

    // ---- YouTube Music Account Login & Library Sync ----
    fun completeYouTubeLogin(cookie: String, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncingAccount.value = true
            val account = repository.verifyAndFetchAccount(cookie)
            if (account != null && account.isLoggedIn) {
                preferences.saveYouTubeAccount(account)
                InnerTubeClient.updateAuth(account.cookie, account.visitorData)
                showMessage("Berhasil masuk sebagai ${account.name}!")
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
            if (!youtubeAccount.value.isLoggedIn) {
                showMessage("Masuk ke akun YouTube Music terlebih dahulu")
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
