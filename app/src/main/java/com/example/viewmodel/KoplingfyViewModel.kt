package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.data.local.KoplingfyDatabase
import com.example.data.local.PreferencesRepository
import com.example.model.AccentPreset
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.AudioSettings
import com.example.model.CollectionDetailType
import com.example.model.EqPreset
import com.example.model.KoplingfyMoods
import com.example.model.ListeningStats
import com.example.model.LyricsState
import com.example.model.MoodFilter
import com.example.model.PlayerArtStyle
import com.example.model.PlaylistSummary
import com.example.model.RepeatMode
import com.example.model.ThemeMode
import com.example.model.Track
import com.example.player.KoplingfyAudioEngine
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
    val errorMessage: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val selectedFilter: Int = 0, // 0=All, 1=Songs, 2=Albums, 3=Artists
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val errorMessage: String? = null
)

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isPlayerExpanded: Boolean = false,
    val lyricsState: LyricsState = LyricsState(),
    val sleepTimerRemainingSec: Int? = null,
    val sleepAfterCurrentTrack: Boolean = false,
    val audioError: String? = null
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

class KoplingfyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KoplingfyDatabase.getInstance(application)
    private val repository = MusicRepository(application, database.dao())
    private val preferences = PreferencesRepository(application)

    private val audioEngine = KoplingfyAudioEngine(
        context = application,
        onTrackCompleted = { handleTrackCompleted() }
    )

    // ---- Flows from Persistence ----
    val settings: StateFlow<AudioSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioSettings())

    val likedTracks: StateFlow<List<Track>> = repository.likedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedTrackIds: StateFlow<Set<String>> = repository.likedTrackIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val localTracks: StateFlow<List<Track>> = repository.localTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var detailObserveJob: Job? = null

    init {
        observeAudioEngine()
        observeSettingsForAudio()
        loadHomeFeed(KoplingfyMoods.allMoods.first())
    }

    private fun observeAudioEngine() {
        viewModelScope.launch {
            audioEngine.state.collectLatest { engine ->
                _playerState.update { state ->
                    state.copy(
                        isPlaying = engine.isPlaying,
                        isBuffering = engine.isBuffering,
                        positionMs = engine.positionMs,
                        durationMs = if (engine.durationMs > 0) engine.durationMs else state.currentTrack?.durationMs ?: 0L,
                        audioError = engine.errorMessage
                    )
                }
            }
        }
    }

    private fun observeSettingsForAudio() {
        viewModelScope.launch {
            settings.collectLatest { cfg ->
                audioEngine.updatePlaybackParams(cfg.playbackSpeed, cfg.playbackPitch)
                audioEngine.updateEqualizerBands(cfg.customBandsDb)
            }
        }
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
                    errorMessage = result.errorMessage
                )
            }
        }
    }

    // ---- Search ----
    fun onSearchQueryChange(newQuery: String) {
        _searchState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _searchState.update {
                it.copy(isSearching = false, tracks = emptyList(), albums = emptyList(), artists = emptyList(), errorMessage = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(420L)
            performSearch(newQuery)
        }
    }

    fun submitSearch(query: String = _searchState.value.query) {
        searchJob?.cancel()
        if (query.isBlank()) return
        _searchState.update { it.copy(query = query) }
        viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(query: String) {
        _searchState.update { it.copy(isSearching = true, errorMessage = null) }
        val result = repository.searchAll(query)
        _searchState.update {
            it.copy(
                isSearching = false,
                tracks = result.tracks,
                albums = result.albums,
                artists = result.artists,
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

    // ---- Playback Controls ----
    fun playTrack(track: Track, queue: List<Track> = listOf(track), expandPlayer: Boolean = false) {
        val safeQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val idx = safeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _playerState.update {
            it.copy(
                currentTrack = track,
                queue = safeQueue,
                currentIndex = idx,
                positionMs = 0L,
                durationMs = track.durationMs,
                isPlayerExpanded = expandPlayer || it.isPlayerExpanded,
                lyricsState = LyricsState(isLoading = true),
                audioError = null
            )
        }
        audioEngine.playSource(track.streamUrl, isLocalUri = track.isLocal)
        viewModelScope.launch { repository.recordPlay(track) }
        fetchLyricsForTrack(track)
    }

    fun playAll(tracks: List<Track>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffle) tracks.shuffled() else tracks
        _playerState.update { it.copy(isShuffleEnabled = shuffle) }
        playTrack(ordered.first(), ordered, expandPlayer = false)
    }

    fun togglePlayPause() {
        val current = _playerState.value.currentTrack ?: return
        if (_playerState.value.audioError != null) {
            audioEngine.playSource(current.streamUrl, isLocalUri = current.isLocal)
        } else {
            audioEngine.togglePlayPause()
        }
    }

    fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
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
            else -> return
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
                state.currentTrack?.let { audioEngine.playSource(it.streamUrl, it.isLocal) }
            }
            else -> {
                val hasNext = state.currentIndex + 1 < state.queue.size ||
                    state.repeatMode == RepeatMode.ALL ||
                    (state.isShuffleEnabled && state.queue.size > 1)
                if (hasNext) skipNext()
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
        _playerState.update { state ->
            if (state.queue.isEmpty()) {
                return@update state
            }
            val mutable = state.queue.toMutableList()
            val insertAt = (state.currentIndex + 1).coerceAtMost(mutable.size)
            mutable.add(insertAt, track)
            state.copy(queue = mutable)
        }
        if (_playerState.value.currentTrack == null) {
            playTrack(track, listOf(track))
        } else {
            showMessage("\"${track.title}\" akan diputar berikutnya")
        }
    }

    fun addToQueueEnd(track: Track) {
        if (_playerState.value.currentTrack == null) {
            playTrack(track, listOf(track))
            return
        }
        _playerState.update { state -> state.copy(queue = state.queue + track) }
        showMessage("\"${track.title}\" ditambahkan ke antrean")
    }

    fun removeFromQueue(index: Int) {
        _playerState.update { state ->
            if (index !in state.queue.indices || index == state.currentIndex) return@update state
            val updated = state.queue.toMutableList().apply { removeAt(index) }
            val adjustedCurrent = if (index < state.currentIndex) state.currentIndex - 1 else state.currentIndex
            state.copy(queue = updated, currentIndex = adjustedCurrent)
        }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _playerState.update { it.copy(isPlayerExpanded = expanded) }
    }

    private fun fetchLyricsForTrack(track: Track) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _playerState.update { it.copy(lyricsState = LyricsState(isLoading = true)) }
            val result = repository.fetchLyrics(track)
            _playerState.update { it.copy(lyricsState = result) }
        }
    }

    // ---- Sleep Timer ----
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playerState.update { it.copy(sleepTimerRemainingSec = null, sleepAfterCurrentTrack = false) }
            showMessage("Timer tidur dimatikan")
            return
        }
        _playerState.update { it.copy(sleepAfterCurrentTrack = false, sleepTimerRemainingSec = minutes * 60) }
        showMessage("Timer tidur diatur $minutes menit")
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _playerState.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            audioEngine.pause()
            _playerState.update { it.copy(sleepTimerRemainingSec = null) }
            showMessage("Timer tidur selesai — musik dijeda")
        }
    }

    fun setSleepAfterCurrentTrack() {
        sleepTimerJob?.cancel()
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
                    subtitle = "Lagu Populer • ${type.artist.genre}",
                    coverUrl = type.artist.artworkUrl,
                    isLoading = true,
                    accentHex = 0xFF00E5A0
                )
                detailObserveJob = viewModelScope.launch {
                    val tracks = repository.fetchTracksByQuery(type.artist.name, limit = 25)
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
                    val tracks = repository.fetchTracksByQuery("${type.album.title} ${type.album.artist}", limit = 20)
                    _detailState.update {
                        it.copy(
                            isLoading = false,
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
    fun setEqPreset(preset: EqPreset) = viewModelScope.launch { preferences.setEqPreset(preset) }
    fun setCustomBand(index: Int, valueDb: Float) {
        val current = settings.value.customBandsDb.toMutableList()
        if (index in current.indices) {
            current[index] = valueDb.coerceIn(-6f, 6f)
            viewModelScope.launch { preferences.setCustomBands(current) }
        }
    }
    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch { preferences.setPlaybackSpeed(speed) }
    fun setPlaybackPitch(pitch: Float) = viewModelScope.launch { preferences.setPlaybackPitch(pitch) }
    fun setHighQualityStream(enabled: Boolean) = viewModelScope.launch { preferences.setHighQualityStream(enabled) }
    fun setGaplessPlayback(enabled: Boolean) = viewModelScope.launch { preferences.setGaplessPlayback(enabled) }

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
