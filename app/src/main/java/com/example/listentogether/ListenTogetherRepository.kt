package com.example.listentogether

import android.content.Context
import com.example.R
import com.example.model.Track
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ListenTogetherRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // CRITICAL: Always initialize with the named enterprise database ID from R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google untuk mengakses Play Bersama.")
    }

    fun observeRoom(roomCode: String): Flow<ListenRoom?> = flow {
        val uid = requireUserId()
        val normalized = QrCodeUtils.normalizeRoomCode(roomCode)
        require(QrCodeUtils.isValidRoomCode(normalized)) { "Kode ruang tidak valid" }
        val path = "listen_rooms/$normalized"
        emitAll(
            db.collection("listen_rooms").document(normalized)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        ListenRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyActiveRooms(userId: String): Flow<List<ListenRoom>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesi akun berubah." }
        val path = "listen_rooms"
        emitAll(
            db.collection("listen_rooms")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ListenRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { it.status == "active" }
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRecentRooms(userId: String): Flow<List<RecentRoomEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesi akun berubah." }
        val path = "users/$userId/recent_rooms"
        emitAll(
            db.collection("users").document(userId).collection("recent_rooms")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RecentRoomEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(
        roomName: String,
        hostName: String,
        guestsCanControl: Boolean,
        initialTrack: Track?,
        initialPlaying: Boolean,
        initialPositionMs: Long,
        initialQueue: List<Track>
    ): Result<String> {
        var docPath = "listen_rooms"
        return try {
            val uid = requireUserId()
            val safeRoomName = roomName.trim().ifBlank { "Ruang Koplingfy" }.take(60)
            val safeHostName = hostName.trim().ifBlank { "Host" }.take(60)

            var roomCode = QrCodeUtils.generateRoomCode()
            for (attempt in 0 until 5) {
                val snap = db.collection("listen_rooms").document(roomCode).get().await()
                if (!snap.exists()) break
                roomCode = QrCodeUtils.generateRoomCode()
            }

            docPath = "listen_rooms/$roomCode"
            val safeTrack = initialTrack?.takeUnless { it.isLocal }
            val queueMaps = initialQueue
                .filter { !it.isLocal && it.id != safeTrack?.id }
                .take(25)
                .map { t ->
                    RoomQueueTrack(
                        trackId = t.id,
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        artworkUrl = t.artworkUrl.orEmpty(),
                        durationMs = t.durationMs,
                        addedByName = safeHostName,
                        addedByUid = uid,
                        upvoterUids = listOf(uid)
                    ).toFirestoreMap()
                }

            val payload = mapOf(
                "hostUid" to uid,
                "hostName" to safeHostName,
                "roomName" to safeRoomName,
                "status" to "active",
                "guestsCanControl" to guestsCanControl,
                "memberUids" to listOf(uid),
                "memberNames" to listOf("${uid}|${safeHostName.replace("|", " ")}"),
                "currentTrackId" to (safeTrack?.id ?: "").take(128),
                "currentTrackTitle" to (safeTrack?.title ?: "").take(200),
                "currentTrackArtist" to (safeTrack?.artist ?: "").take(200),
                "currentTrackAlbum" to (safeTrack?.album ?: "").take(200),
                "currentTrackArtwork" to (safeTrack?.artworkUrl ?: "").take(600),
                "currentTrackDurationMs" to (safeTrack?.durationMs ?: 0L).coerceIn(0L, 86_400_000L),
                "playing" to (safeTrack != null && initialPlaying),
                "positionMs" to if (safeTrack != null) initialPositionMs.coerceIn(0L, 86_400_000L) else 0L,
                "syncSequence" to 1L,
                "queueTracks" to queueMaps,
                "chatMessages" to emptyList<Map<String, Any>>(),
                "skipVoteUids" to emptyList<String>(),
                "lastActionBy" to "$safeHostName membuat ruang".take(140),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("listen_rooms").document(roomCode).set(payload).await()
            saveRecentRoom(uid, roomCode, safeRoomName, safeHostName, "host")
            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoom(rawCodeOrQr: String, displayName: String): Result<ListenRoom> {
        val roomCode = QrCodeUtils.parseRoomCodeFromQr(rawCodeOrQr)
            ?: return Result.failure(IllegalArgumentException("Kode ruang atau QR tidak valid. Harus 6 karakter (contoh: KPL74M)."))
        val docPath = "listen_rooms/$roomCode"
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().ifBlank { "Pendengar" }.replace("|", " ").take(60)
            val docRef = db.collection("listen_rooms").document(roomCode)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(IllegalStateException("Ruang \"$roomCode\" tidak ditemukan."))
            }
            val room = snapshot.toObject(
                ListenRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Data ruang tidak dapat dibaca."))

            if (room.status != "active") {
                return Result.failure(IllegalStateException("Sesi ruang \"$roomCode\" sudah berakhir."))
            }

            if (uid !in room.memberUids) {
                if (room.memberUids.size >= 25) {
                    return Result.failure(IllegalStateException("Ruang sudah penuh (maksimal 25 pendengar)."))
                }
                val newMemberUids = room.memberUids + uid
                val newMemberNames = room.memberNames + "$uid|$safeName"
                docRef.update(
                    mapOf(
                        "memberUids" to newMemberUids,
                        "memberNames" to newMemberNames,
                        "lastActionBy" to "$safeName bergabung".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val role = if (room.hostUid == uid) "host" else "guest"
            saveRecentRoom(uid, roomCode, room.roomName, room.hostName, role)
            Result.success(room)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(room: ListenRoom, displayName: String): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            if (room.status != "active" || uid == room.hostUid || uid !in room.memberUids) {
                return Result.success(Unit)
            }
            val safeName = displayName.trim().ifBlank { "Pendengar" }.take(60)
            val updatedUids = room.memberUids.filterNot { it == uid }
            val updatedNames = room.memberNames.filterNot { it.startsWith("$uid|") }
            val updatedVotes = room.skipVoteUids.filterNot { it == uid }
            if (updatedUids.isEmpty()) return Result.success(Unit)

            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "memberUids" to updatedUids,
                        "memberNames" to updatedNames,
                        "skipVoteUids" to updatedVotes,
                        "lastActionBy" to "$safeName keluar ruang".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun endRoom(roomCode: String, hostName: String): Result<Unit> {
        val docPath = "listen_rooms/$roomCode"
        return try {
            requireUserId()
            val safeHost = hostName.trim().ifBlank { "Host" }.take(60)
            db.collection("listen_rooms").document(roomCode)
                .update(
                    mapOf(
                        "status" to "ended",
                        "lastActionBy" to "$safeHost mengakhiri sesi".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateGuestsCanControl(
        roomCode: String,
        hostName: String,
        enabled: Boolean
    ): Result<Unit> {
        val docPath = "listen_rooms/$roomCode"
        return try {
            requireUserId()
            val safeHost = hostName.trim().ifBlank { "Host" }.take(60)
            val actionText = if (enabled) {
                "$safeHost mengizinkan peserta kontrol lagu"
            } else {
                "$safeHost membatasi kontrol hanya untuk Host"
            }
            db.collection("listen_rooms").document(roomCode)
                .update(
                    mapOf(
                        "guestsCanControl" to enabled,
                        "lastActionBy" to actionText.take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncPlaybackState(
        room: ListenRoom,
        actorName: String,
        track: Track?,
        playing: Boolean,
        positionMs: Long,
        queueTracks: List<RoomQueueTrack>? = null,
        actionLabel: String
    ): Result<Long> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            requireUserId()
            if (room.status != "active") return Result.success(room.syncSequence)
            val nextSeq = room.syncSequence + 1L
            val safeTrack = track?.takeUnless { it.isLocal }
            val trackChanged = safeTrack != null && safeTrack.id != room.currentTrackId

            val updates = mutableMapOf<String, Any>(
                "playing" to playing,
                "positionMs" to positionMs.coerceIn(0L, 86_400_000L),
                "syncSequence" to nextSeq,
                "lastActionBy" to actionLabel.take(140),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safeTrack != null) {
                updates["currentTrackId"] = safeTrack.id.take(128)
                updates["currentTrackTitle"] = safeTrack.title.take(200)
                updates["currentTrackArtist"] = safeTrack.artist.take(200)
                updates["currentTrackAlbum"] = safeTrack.album.take(200)
                updates["currentTrackArtwork"] = (safeTrack.artworkUrl ?: "").take(600)
                updates["currentTrackDurationMs"] = safeTrack.durationMs.coerceIn(0L, 86_400_000L)
            }
            if (trackChanged) {
                updates["skipVoteUids"] = emptyList<String>()
            }
            if (queueTracks != null) {
                updates["queueTracks"] = queueTracks.take(30).map { it.toFirestoreMap() }
            }

            db.collection("listen_rooms").document(room.roomCode).update(updates).await()
            Result.success(nextSeq)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // ---- Collaborative Antrian Bersama (Any joined member can contribute!) ----
    suspend fun addTrackToSharedQueue(
        room: ListenRoom,
        actorName: String,
        track: Track,
        playNext: Boolean = false
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            if (track.isLocal) {
                return Result.failure(IllegalArgumentException("Lagu lokal perangkat tidak bisa diputar bersama. Pilih lagu dari YouTube Music."))
            }
            val currentQueue = room.parsedQueue()
            if (currentQueue.size >= 30) {
                return Result.failure(IllegalStateException("Antrian bersama penuh (maksimal 30 lagu)."))
            }
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            val newItem = RoomQueueTrack(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl.orEmpty(),
                durationMs = track.durationMs,
                addedByName = safeActor,
                addedByUid = uid,
                upvoterUids = listOf(uid)
            )
            val updatedList = if (playNext) {
                (listOf(newItem) + currentQueue).take(30)
            } else {
                (currentQueue + newItem).take(30)
            }
            val verb = if (playNext) "menempatkan di urutan berikutnya" else "menambahkan ke antrian bersama"
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "queueTracks" to updatedList.map { it.toFirestoreMap() },
                        "syncSequence" to (room.syncSequence + 1L),
                        "lastActionBy" to "$safeActor $verb: \"${track.title}\"".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleUpvoteSharedQueueTrack(
        room: ListenRoom,
        actorName: String,
        index: Int,
        autoSortByVotes: Boolean = true
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            val queue = room.parsedQueue().toMutableList()
            val target = queue.getOrNull(index) ?: return Result.success(Unit)
            val alreadyUpvoted = uid in target.upvoterUids
            val nextUpvoters = if (alreadyUpvoted) {
                target.upvoterUids.filterNot { it == uid }
            } else {
                (target.upvoterUids + uid).distinct().take(25)
            }
            queue[index] = target.copy(upvoterUids = nextUpvoters)
            val finalQueue = if (autoSortByVotes) {
                queue.sortedByDescending { it.upvoterUids.size }
            } else {
                queue
            }
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            val action = if (alreadyUpvoted) {
                "$safeActor membatalkan vote \"${target.title}\""
            } else {
                "🔥 $safeActor menaikkan \"${target.title}\" (${nextUpvoters.size} vote)"
            }
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "queueTracks" to finalQueue.take(30).map { it.toFirestoreMap() },
                        "syncSequence" to (room.syncSequence + 1L),
                        "lastActionBy" to action.take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sortSharedQueueByVotes(
        room: ListenRoom,
        actorName: String
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            requireUserId()
            val queue = room.parsedQueue()
            if (queue.size <= 1) return Result.success(Unit)
            val sorted = queue.sortedByDescending { it.upvoterUids.size }
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "queueTracks" to sorted.take(30).map { it.toFirestoreMap() },
                        "syncSequence" to (room.syncSequence + 1L),
                        "lastActionBy" to "🔥 $safeActor mengurutkan antrian via Mode Jukebox".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun moveSharedQueueTrack(
        room: ListenRoom,
        actorName: String,
        fromIndex: Int,
        toIndex: Int
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            requireUserId()
            val queue = room.parsedQueue().toMutableList()
            if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
                return Result.success(Unit)
            }
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "queueTracks" to queue.take(30).map { it.toFirestoreMap() },
                        "syncSequence" to (room.syncSequence + 1L),
                        "lastActionBy" to "$safeActor mengatur ulang antrian bersama".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeSharedQueueTrack(
        room: ListenRoom,
        actorName: String,
        index: Int
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            requireUserId()
            val queue = room.parsedQueue().toMutableList()
            val removed = queue.getOrNull(index) ?: return Result.success(Unit)
            queue.removeAt(index)
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "queueTracks" to queue.take(30).map { it.toFirestoreMap() },
                        "syncSequence" to (room.syncSequence + 1L),
                        "lastActionBy" to "$safeActor menghapus \"${removed.title}\" dari antrian".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // ---- Live Chat in Play Bersama ----
    suspend fun sendChatMessage(
        room: ListenRoom,
        senderName: String,
        text: String
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            val cleaned = text.trim().take(240)
            if (cleaned.isBlank()) return Result.success(Unit)
            val safeSender = senderName.trim().ifBlank { "Pendengar" }.take(60)
            val newMsg = RoomChatMessage(
                id = UUID.randomUUID().toString().take(12),
                uid = uid,
                senderName = safeSender,
                text = cleaned,
                sentAtMs = System.currentTimeMillis()
            )
            val updated = (room.parsedChatMessages() + newMsg).takeLast(40).map { it.toFirestoreMap() }
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "chatMessages" to updated,
                        "lastActionBy" to "💬 $safeSender: $cleaned".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // ---- Vote Skip (Majority auto-advances to next shared queue track!) ----
    suspend fun toggleVoteSkip(
        room: ListenRoom,
        actorName: String
    ): Result<Boolean> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            if (room.status != "active") return Result.success(false)
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            val currentVotes = room.skipVoteUids.filter { it in room.memberUids }.distinct()
            val alreadyVoted = uid in currentVotes

            if (alreadyVoted) {
                val updated = currentVotes.filterNot { it == uid }
                db.collection("listen_rooms").document(room.roomCode)
                    .update(
                        mapOf(
                            "skipVoteUids" to updated,
                            "lastActionBy" to "$safeActor membatalkan Vote Skip (${updated.size}/${room.votesNeededToSkip()})".take(140),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                return Result.success(false)
            }

            val nextVotes = (currentVotes + uid).distinct()
            val reachedMajority = nextVotes.size * 2 > room.memberUids.size
            val queue = room.parsedQueue()

            if (reachedMajority && queue.isNotEmpty()) {
                val nextTrack = queue.first()
                val remaining = queue.drop(1).map { it.toFirestoreMap() }
                db.collection("listen_rooms").document(room.roomCode)
                    .update(
                        mapOf(
                            "currentTrackId" to nextTrack.trackId.take(128),
                            "currentTrackTitle" to nextTrack.title.take(200),
                            "currentTrackArtist" to nextTrack.artist.take(200),
                            "currentTrackAlbum" to nextTrack.album.take(200),
                            "currentTrackArtwork" to nextTrack.artworkUrl.take(600),
                            "currentTrackDurationMs" to nextTrack.durationMs.coerceIn(0L, 86_400_000L),
                            "playing" to true,
                            "positionMs" to 0L,
                            "syncSequence" to (room.syncSequence + 1L),
                            "queueTracks" to remaining,
                            "skipVoteUids" to emptyList<String>(),
                            "lastActionBy" to "⏭️ Vote Skip tercapai! Memutar \"${nextTrack.title}\"".take(140),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                Result.success(true)
            } else {
                val note = if (reachedMajority && queue.isEmpty()) {
                    "⏭️ Vote Skip tercapai (${nextVotes.size}/${room.votesNeededToSkip()}), tambahkan lagu ke antrian!"
                } else {
                    "⏭️ $safeActor vote skip (${nextVotes.size}/${room.votesNeededToSkip()})"
                }
                db.collection("listen_rooms").document(room.roomCode)
                    .update(
                        mapOf(
                            "skipVoteUids" to nextVotes.take(25),
                            "lastActionBy" to note.take(140),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                Result.success(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendRoomReaction(room: ListenRoom, actorName: String, emoji: String): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            requireUserId()
            val safeActor = actorName.trim().ifBlank { "Pendengar" }.take(60)
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "lastActionBy" to "$emoji $safeActor bereaksi".take(140),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun saveRecentRoom(
        userId: String,
        roomCode: String,
        roomName: String,
        hostName: String,
        role: String
    ) {
        val docRef = db.collection("users").document(userId)
            .collection("recent_rooms").document(roomCode)
        runCatching {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "roomName" to roomName.take(60),
                        "hostName" to hostName.take(60),
                        "role" to role,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "roomName" to roomName.take(60),
                        "hostName" to hostName.take(60),
                        "role" to role,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }
}
