package com.example.listentogether

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Track
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListenSessionUiState(
    val activeRoomCode: String? = null,
    val activeRoom: ListenRoom? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val showQrCardModal: Boolean = false,
    val lastSyncedSequence: Long = 0L,
    val jukeboxAutoSort: Boolean = true
) {
    fun isHost(uid: String): Boolean = activeRoom?.hostUid == uid
    fun canControl(uid: String): Boolean {
        val r = activeRoom ?: return false
        return r.status == "active" && (r.hostUid == uid || (r.guestsCanControl && uid in r.memberUids))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherViewModel(
    private val repository: ListenTogetherRepository,
    val currentUserId: String,
    val currentUserName: String
) : ViewModel() {

    private val _activeRoomCode = MutableStateFlow<String?>(null)
    private val _sessionState = MutableStateFlow(ListenSessionUiState())
    val sessionState: StateFlow<ListenSessionUiState> = _sessionState.asStateFlow()

    private var roomObserverJob: Job? = null
    private var lastPushedSeq: Long = -1L

    val myActiveRoomsState: StateFlow<ListenUiState<List<ListenRoom>>> =
        repository.observeMyActiveRooms(currentUserId)
            .map<List<ListenRoom>, ListenUiState<List<ListenRoom>>> { ListenUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing active rooms", rootError)
                emit(ListenUiState.Error(rootError.message ?: "Gagal memuat ruang aktif"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ListenUiState.Loading
            )

    val recentRoomsState: StateFlow<ListenUiState<List<RecentRoomEntry>>> =
        repository.observeRecentRooms(currentUserId)
            .map<List<RecentRoomEntry>, ListenUiState<List<RecentRoomEntry>>> { ListenUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing recent rooms", rootError)
                emit(ListenUiState.Error(rootError.message ?: "Gagal memuat riwayat ruang"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ListenUiState.Loading
            )

    init {
        observeActiveRoomStream()
    }

    private fun observeActiveRoomStream() {
        roomObserverJob?.cancel()
        roomObserverJob = viewModelScope.launch {
            _activeRoomCode
                .flatMapLatest { code ->
                    if (code.isNullOrBlank()) flowOf(null)
                    else repository.observeRoom(code)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing room", rootError)
                    _sessionState.update {
                        it.copy(errorMessage = rootError.message ?: "Koneksi ruang terputus")
                    }
                }
                .collect { room ->
                    if (room == null && _activeRoomCode.value != null) {
                        _sessionState.update {
                            it.copy(
                                activeRoom = null,
                                activeRoomCode = null,
                                showQrCardModal = false,
                                statusMessage = "Ruang tidak ditemukan atau telah dihapus."
                            )
                        }
                        _activeRoomCode.value = null
                    } else if (room != null) {
                        val endedMsg = if (room.status == "ended") {
                            "Sesi Play Bersama telah diakhiri oleh Host."
                        } else null
                        _sessionState.update {
                            it.copy(
                                activeRoomCode = room.roomCode,
                                activeRoom = room,
                                statusMessage = endedMsg ?: it.statusMessage
                            )
                        }
                    }
                }
        }
    }

    fun createRoom(
        roomName: String,
        guestsCanControl: Boolean,
        currentTrack: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        currentQueue: List<Track>
    ) {
        viewModelScope.launch {
            _sessionState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = repository.createRoom(
                roomName = roomName,
                hostName = currentUserName,
                guestsCanControl = guestsCanControl,
                initialTrack = currentTrack,
                initialPlaying = isPlaying,
                initialPositionMs = positionMs,
                initialQueue = currentQueue
            )
            result.onSuccess { code ->
                lastPushedSeq = 1L
                _activeRoomCode.value = code
                _sessionState.update {
                    it.copy(
                        isBusy = false,
                        activeRoomCode = code,
                        showQrCardModal = true,
                        statusMessage = "Ruang $code berhasil dibuat! Bagikan kode atau QR ke teman."
                    )
                }
            }.onFailure { err ->
                _sessionState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = err.message ?: "Gagal membuat ruang Play Bersama"
                    )
                }
            }
        }
    }

    fun joinRoomByCodeOrQr(rawInput: String) {
        val parsed = QrCodeUtils.parseRoomCodeFromQr(rawInput)
        if (parsed == null) {
            _sessionState.update {
                it.copy(errorMessage = "Kode ruang atau QR tidak valid. Pastikan berisi 6 karakter kode ruang Koplingfy.")
            }
            return
        }
        viewModelScope.launch {
            _sessionState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = repository.joinRoom(parsed, currentUserName)
            result.onSuccess { room ->
                lastPushedSeq = -1L // Force inbound sync to match host playback
                _activeRoomCode.value = room.roomCode
                _sessionState.update {
                    it.copy(
                        isBusy = false,
                        activeRoomCode = room.roomCode,
                        activeRoom = room,
                        lastSyncedSequence = 0L,
                        statusMessage = "Berhasil bergabung ke ruang \"${room.roomName}\" (${room.roomCode})!"
                    )
                }
            }.onFailure { err ->
                _sessionState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = err.message ?: "Gagal bergabung ke ruang $parsed"
                    )
                }
            }
        }
    }

    fun leaveOrEndCurrentRoom() {
        val room = _sessionState.value.activeRoom
        if (room == null) {
            detachSessionLocally()
            return
        }
        viewModelScope.launch {
            _sessionState.update { it.copy(isBusy = true) }
            if (room.hostUid == currentUserId && room.status == "active") {
                repository.endRoom(room.roomCode, currentUserName)
            } else {
                repository.leaveRoom(room, currentUserName)
            }
            detachSessionLocally(
                message = if (room.hostUid == currentUserId) "Sesi Play Bersama diakhiri."
                else "Anda keluar dari ruang ${room.roomCode}."
            )
        }
    }

    suspend fun detachBeforeSignOut() {
        val room = _sessionState.value.activeRoom
        if (room != null && room.status == "active" && room.hostUid != currentUserId) {
            runCatching { repository.leaveRoom(room, currentUserName) }
        }
        roomObserverJob?.cancel()
        _activeRoomCode.value = null
        _sessionState.value = ListenSessionUiState()
    }

    fun detachSessionLocally(message: String? = null) {
        _activeRoomCode.value = null
        lastPushedSeq = -1L
        _sessionState.value = ListenSessionUiState(statusMessage = message)
    }

    fun toggleGuestsCanControl(enabled: Boolean) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.hostUid != currentUserId || room.status != "active") return
        viewModelScope.launch {
            val res = repository.updateGuestsCanControl(room.roomCode, currentUserName, enabled)
            res.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal memperbarui izin peserta") }
            }
        }
    }

    fun serverNowMs(): Long = repository.serverNowMs()

    fun pushPlaybackSync(
        track: Track?,
        playing: Boolean,
        positionMs: Long,
        actionLabel: String,
        updatedQueue: List<RoomQueueTrack>? = null
    ) {
        val room = _sessionState.value.activeRoom ?: return
        if (!_sessionState.value.canControl(currentUserId)) return
        viewModelScope.launch {
            val res = repository.syncPlaybackState(
                room = room,
                actorName = currentUserName,
                track = track,
                playing = playing,
                positionMs = positionMs,
                queueTracks = updatedQueue,
                actionLabel = actionLabel
            )
            res.onSuccess { seq ->
                lastPushedSeq = seq
                _sessionState.update { it.copy(lastSyncedSequence = seq) }
            }.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal menyinkronkan pemutaran") }
            }
        }
    }

    /**
     * Pushes a crisp playback position anchor once the Host/Controller device finishes buffering
     * a new track (or periodically while playing), keeping Device 1 & Device 2 locked in phase.
     */
    fun pushSilentClockAnchor(track: Track, positionMs: Long, playing: Boolean) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || room.currentTrackId != track.id) return
        if (!_sessionState.value.canControl(currentUserId)) return
        val existingAction = room.lastActionBy.ifBlank { "$currentUserName menyinkronkan pemutaran" }
        viewModelScope.launch {
            val res = repository.syncPlaybackState(
                room = room,
                actorName = currentUserName,
                track = track,
                playing = playing,
                positionMs = positionMs,
                queueTracks = null,
                actionLabel = existingAction
            )
            res.onSuccess { seq ->
                lastPushedSeq = seq
                _sessionState.update { it.copy(lastSyncedSequence = seq) }
            }
        }
    }

    fun addTrackToRoomQueue(track: Track, playNext: Boolean = false) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) {
            _sessionState.update { it.copy(errorMessage = "Gabung ke ruang aktif untuk menambah antrian bersama.") }
            return
        }
        viewModelScope.launch {
            val res = repository.addTrackToSharedQueue(room, currentUserName, track, playNext = playNext)
            res.onSuccess {
                val msg = if (playNext) {
                    "\"${track.title}\" ditempatkan berikutnya di antrian bersama"
                } else {
                    "\"${track.title}\" ditambahkan ke antrian bersama"
                }
                _sessionState.update { it.copy(statusMessage = msg) }
            }.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal menambahkan lagu ke antrian ruang") }
            }
        }
    }

    fun toggleJukeboxAutoSort(enabled: Boolean) {
        _sessionState.update { it.copy(jukeboxAutoSort = enabled) }
        if (enabled) {
            sortRoomQueueByVotes()
        }
    }

    fun sortRoomQueueByVotes() {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) return
        viewModelScope.launch {
            val res = repository.sortSharedQueueByVotes(room, currentUserName)
            res.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal mengurutkan antrian via vote") }
            }
        }
    }

    fun upvoteRoomQueueTrack(index: Int) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) return
        val autoSort = _sessionState.value.jukeboxAutoSort
        viewModelScope.launch {
            val res = repository.toggleUpvoteSharedQueueTrack(
                room = room,
                actorName = currentUserName,
                index = index,
                autoSortByVotes = autoSort
            )
            res.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal memberi vote lagu") }
            }
        }
    }

    fun moveRoomQueueTrack(fromIndex: Int, toIndex: Int) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) return
        viewModelScope.launch {
            val res = repository.moveSharedQueueTrack(room, currentUserName, fromIndex, toIndex)
            res.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal memindahkan urutan antrian") }
            }
        }
    }

    fun removeRoomQueueTrack(index: Int) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) return
        viewModelScope.launch {
            val res = repository.removeSharedQueueTrack(room, currentUserName, index)
            res.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal menghapus lagu dari antrian") }
            }
        }
    }

    fun sendChatMessage(text: String) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) return
        viewModelScope.launch {
            val res = repository.sendChatMessage(room, currentUserName, text)
            res.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal mengirim pesan obrolan") }
            }
        }
    }

    fun toggleVoteSkip() {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active" || currentUserId !in room.memberUids) return
        viewModelScope.launch {
            val res = repository.toggleVoteSkip(room, currentUserName)
            res.onSuccess { skipped ->
                if (skipped) {
                    _sessionState.update { it.copy(statusMessage = "Vote Skip tercapai! Beralih ke lagu berikutnya.") }
                }
            }.onFailure { err ->
                _sessionState.update { it.copy(errorMessage = err.message ?: "Gagal memproses Vote Skip") }
            }
        }
    }

    fun playRoomQueueTrack(index: Int) {
        val room = _sessionState.value.activeRoom ?: return
        if (!_sessionState.value.canControl(currentUserId)) return
        val queue = room.parsedQueue()
        val chosen = queue.getOrNull(index) ?: return
        val remaining = queue.toMutableList().apply { removeAt(index) }
        pushPlaybackSync(
            track = chosen.toTrack(),
            playing = true,
            positionMs = 0L,
            actionLabel = "$currentUserName memutar \"${chosen.title}\"",
            updatedQueue = remaining
        )
    }

    fun sendReaction(emoji: String) {
        val room = _sessionState.value.activeRoom ?: return
        if (room.status != "active") return
        viewModelScope.launch {
            repository.sendRoomReaction(room, currentUserName, emoji)
        }
    }

    fun shouldApplyInboundSync(room: ListenRoom): Boolean {
        if (room.status != "active") return false
        if (room.syncSequence <= 0L) return false
        if (room.syncSequence == lastPushedSeq) return false
        return room.syncSequence > _sessionState.value.lastSyncedSequence
    }

    fun markInboundSynced(sequence: Long) {
        _sessionState.update { it.copy(lastSyncedSequence = sequence) }
    }

    fun setShowQrModal(show: Boolean) {
        _sessionState.update { it.copy(showQrCardModal = show) }
    }

    fun clearMessages() {
        _sessionState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        roomObserverJob?.cancel()
    }

    private companion object {
        const val TAG = "ListenTogetherVM"
    }
}
