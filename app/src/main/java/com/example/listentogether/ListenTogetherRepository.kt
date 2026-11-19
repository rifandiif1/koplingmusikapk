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

            // Find an unused 6-char room code
            var roomCode = QrCodeUtils.generateRoomCode()
            for (attempt in 0 until 5) {
                val snap = db.collection("listen_rooms").document(roomCode).get().await()
                if (!snap.exists()) break
                roomCode = QrCodeUtils.generateRoomCode()
            }

            docPath = "listen_rooms/$roomCode"
            val queueMaps = initialQueue
                .filter { !it.isLocal }
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
                        addedByUid = uid
                    ).toFirestoreMap()
                }

            val safeTrack = initialTrack?.takeUnless { it.isLocal }
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
            if (updatedUids.isEmpty()) return Result.success(Unit)

            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "memberUids" to updatedUids,
                        "memberNames" to updatedNames,
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

    suspend fun addTrackToSharedQueue(
        room: ListenRoom,
        actorName: String,
        track: Track
    ): Result<Unit> {
        val docPath = "listen_rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            if (track.isLocal) {
                return Result.failure(IllegalArgumentException("Lagu lokal perangkat tidak bisa diputar bersama. Pilih lagu dari YouTube Music."))
            }
            val currentQueue = room.parsedQueue()
            if (currentQueue.size >= 30) {
                return Result.failure(IllegalStateException("Antrean bersama penuh (maksimal 30 lagu)."))
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
                addedByUid = uid
            )
            val updatedQueue = (currentQueue + newItem).take(30).map { it.toFirestoreMap() }
            db.collection("listen_rooms").document(room.roomCode)
                .update(
                    mapOf(
                        "queueTracks" to updatedQueue,
                        "syncSequence" to (room.syncSequence + 1L),
                        "lastActionBy" to "$safeActor menambahkan \"${track.title}\"".take(140),
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
