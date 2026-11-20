package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "CloudLibraryRepo"

@Keep
@IgnoreExtraProperties
data class CloudLibraryMeta(
    val userId: String = "",
    val payloadJson: String = "",
    val likedCount: Int = 0,
    val playlistCount: Int = 0,
    val deviceLabel: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * User-scoped Cloud Library Sync repository backed by the provisioned named Firestore database.
 * Stores liked tracks and custom playlists at /users/{uid}/library_sync/snapshot.
 */
class CloudLibraryRepository(private val context: Context) {

    private fun getDb(): FirebaseFirestore =
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))

    private fun requireUserId(): String =
        Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Masuk dengan akun Google terlebih dahulu untuk sinkronisasi cloud.")

    private fun snapshotDoc(uid: String) =
        getDb().collection("users").document(uid).collection("library_sync").document("snapshot")

    suspend fun uploadLibrarySnapshot(
        payloadJson: String,
        likedCount: Int,
        playlistCount: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            if (payloadJson.length > 740_000) {
                return@withContext Result.failure(
                    IllegalArgumentException("Ukuran koleksi melebihi batas snapshot cloud (740 KB).")
                )
            }
            val safeDevice = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80).ifBlank { "Android Device" }
            val ref = snapshotDoc(uid)
            val existing = ref.get().await()

            if (existing.exists()) {
                val updates = mapOf<String, Any>(
                    "payloadJson" to payloadJson,
                    "likedCount" to likedCount.coerceIn(0, 10_000),
                    "playlistCount" to playlistCount.coerceIn(0, 500),
                    "deviceLabel" to safeDevice,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.update(updates).await()
            } else {
                val payload = mapOf<String, Any>(
                    "userId" to uid,
                    "payloadJson" to payloadJson,
                    "likedCount" to likedCount.coerceIn(0, 10_000),
                    "playlistCount" to playlistCount.coerceIn(0, 500),
                    "deviceLabel" to safeDevice,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Firestore upload failed: ${e.code}", e)
            Result.failure(Exception("Sinkronisasi cloud gagal (${e.code.name}): ${e.localizedMessage ?: "periksa koneksi"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Cloud library upload failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengunggah koleksi ke Cloud."))
        }
    }

    suspend fun downloadLibrarySnapshot(): Result<CloudLibraryMeta?> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            val snap = snapshotDoc(uid).get().await()
            if (!snap.exists()) {
                return@withContext Result.success(null)
            }
            val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@withContext Result.success(null)
            val meta = CloudLibraryMeta(
                userId = data["userId"] as? String ?: uid,
                payloadJson = data["payloadJson"] as? String ?: "",
                likedCount = (data["likedCount"] as? Number)?.toInt() ?: 0,
                playlistCount = (data["playlistCount"] as? Number)?.toInt() ?: 0,
                deviceLabel = data["deviceLabel"] as? String ?: "",
                createdAt = data["createdAt"] as? Timestamp,
                updatedAt = data["updatedAt"] as? Timestamp
            )
            Result.success(meta)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Firestore download failed: ${e.code}", e)
            Result.failure(Exception("Gagal memuat dari cloud (${e.code.name}): ${e.localizedMessage ?: "periksa koneksi"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Cloud library download failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengunduh koleksi dari Cloud."))
        }
    }
}
