package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.local.KoplingfyDao
import com.example.data.local.PlayHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SavedTrackEntity
import com.example.data.remote.ResolvedStream
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages Metrolist-style offline audio downloads (app-private storage, zero broad storage permissions)
 * as well as JSON library Backup & Restore via Android Storage Access Framework (SAF).
 */
class OfflineDownloadManager(
    private val context: Context,
    private val dao: KoplingfyDao
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    // trackId -> download progress 0f..1f
    private val _activeDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Float>> = _activeDownloads.asStateFlow()

    private fun downloadsDir(): File {
        val dir = File(context.filesDir, "koplingfy_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun downloadTrack(track: Track, stream: ResolvedStream): Result<File> = withContext(Dispatchers.IO) {
        val safeName = track.id.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_") + ".m4a"
        val targetFile = File(downloadsDir(), safeName)
        val tempFile = File(downloadsDir(), "$safeName.part")

        _activeDownloads.update { it + (track.id to 0.05f) }
        try {
            val reqBuilder = Request.Builder().url(stream.url)
            if (!stream.userAgent.isNullOrBlank()) {
                reqBuilder.header("User-Agent", stream.userAgent)
            }
            if (!stream.cookie.isNullOrBlank()) {
                reqBuilder.header("Cookie", stream.cookie)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("Empty stream body")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)

                FileOutputStream(tempFile).use { out ->
                    body.byteStream().use { input ->
                        var read = input.read(buffer)
                        while (read >= 0) {
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                                _activeDownloads.update { it + (track.id to progress) }
                            }
                            read = input.read(buffer)
                        }
                        out.flush()
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            val existing = dao.getTrackById(track.id)
            if (existing == null) {
                dao.upsertTrack(
                    SavedTrackEntity.fromTrack(
                        track = track,
                        isDownloaded = true,
                        localDownloadPath = targetFile.absolutePath,
                        downloadedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.updateDownloadStatus(
                    trackId = track.id,
                    isDownloaded = true,
                    path = targetFile.absolutePath,
                    downloadedAt = System.currentTimeMillis()
                )
            }
            Result.success(targetFile)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            Result.failure(e)
        } finally {
            _activeDownloads.update { it - track.id }
        }
    }

    suspend fun removeDownloadedTrack(trackId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getTrackById(trackId)
        existing?.localDownloadPath?.let { path ->
            runCatching { File(path).delete() }
        }
        dao.updateDownloadStatus(trackId, isDownloaded = false, path = null, downloadedAt = 0L)
    }

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        downloadsDir().listFiles()?.forEach { runCatching { it.delete() } }
        dao.clearAllDownloadFlags()
    }

    suspend fun clearAppCache(): Long = withContext(Dispatchers.IO) {
        val before = dirSizeBytes(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    suspend fun getStorageStatsBytes(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val downloadsBytes = dirSizeBytes(downloadsDir())
        val cacheBytes = dirSizeBytes(context.cacheDir)
        downloadsBytes to cacheBytes
    }

    private fun dirSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
        return total
    }

    // ---- Backup & Restore Library to/from JSON via SAF ----
    suspend fun exportBackupToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val liked = dao.getLikedTracksOnce()
            val playlists = dao.getAllPlaylistsOnce()
            val history = dao.getPlayHistoryOnce()

            val root = JSONObject().apply {
                put("app", "Koplingfy")
                put("schemaVersion", 2)
                put("exportedAt", System.currentTimeMillis())

                put("likedTracks", JSONArray().apply {
                    liked.forEach { put(trackEntityToJson(it)) }
                })

                put("playlists", JSONArray().apply {
                    playlists.forEach { pl ->
                        val plTracks = dao.getTracksForPlaylistOnce(pl.id)
                        put(JSONObject().apply {
                            put("name", pl.name)
                            put("description", pl.description)
                            put("coverUrl", pl.coverUrl ?: "")
                            put("tracks", JSONArray().apply {
                                plTracks.forEach { put(trackEntityToJson(it)) }
                            })
                        })
                    }
                })

                put("history", JSONArray().apply {
                    history.take(50).forEach { h ->
                        put(JSONObject().apply {
                            put("id", h.trackId)
                            put("title", h.title)
                            put("artist", h.artist)
                            put("album", h.album)
                            put("artworkUrl", h.artworkUrl ?: "")
                            put("durationMs", h.durationMs)
                            put("genre", h.genre)
                            put("playedAt", h.playedAt)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IllegalStateException("Tidak dapat membuka file tujuan")

            liked.size + playlists.size
        }
    }

    suspend fun importBackupFromUri(uri: Uri): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw IllegalStateException("Tidak dapat membaca file backup")

            val root = JSONObject(jsonText)
            var likedCount = 0
            var playlistCount = 0

            val likedArr = root.optJSONArray("likedTracks") ?: JSONArray()
            for (i in 0 until likedArr.length()) {
                val obj = likedArr.optJSONObject(i) ?: continue
                val entity = jsonToTrackEntity(obj, isLiked = true)
                dao.upsertTrack(entity)
                likedCount++
            }

            val plArr = root.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until plArr.length()) {
                val plObj = plArr.optJSONObject(i) ?: continue
                val name = plObj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val desc = plObj.optString("description")
                val cover = plObj.optString("coverUrl").takeIf { it.isNotBlank() }
                val newPlId = dao.insertPlaylist(PlaylistEntity(name = name, description = desc, coverUrl = cover))
                val plTracks = plObj.optJSONArray("tracks") ?: JSONArray()
                for (j in 0 until plTracks.length()) {
                    val tObj = plTracks.optJSONObject(j) ?: continue
                    val trackId = tObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val existing = dao.getTrackById(trackId)
                    if (existing == null) {
                        dao.upsertTrack(jsonToTrackEntity(tObj, isLiked = false))
                    }
                    dao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId = newPlId, trackId = trackId))
                }
                playlistCount++
            }

            val histArr = root.optJSONArray("history") ?: JSONArray()
            for (i in 0 until histArr.length()) {
                val h = histArr.optJSONObject(i) ?: continue
                val id = h.optString("id").takeIf { it.isNotBlank() } ?: continue
                dao.insertPlayHistory(
                    PlayHistoryEntity(
                        trackId = id,
                        title = h.optString("title"),
                        artist = h.optString("artist"),
                        album = h.optString("album"),
                        artworkUrl = h.optString("artworkUrl").takeIf { it.isNotBlank() },
                        streamUrl = null,
                        durationMs = h.optLong("durationMs", 210_000L),
                        genre = h.optString("genre", "YouTube Music"),
                        isLocal = false,
                        playedAt = h.optLong("playedAt", System.currentTimeMillis())
                    )
                )
            }

            likedCount to playlistCount
        }
    }

    private fun trackEntityToJson(e: SavedTrackEntity): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("title", e.title)
        put("artist", e.artist)
        put("album", e.album)
        put("artworkUrl", e.artworkUrl ?: "")
        put("durationMs", e.durationMs)
        put("genre", e.genre)
        put("isExplicit", e.isExplicit)
        put("releaseYear", e.releaseYear)
    }

    private fun jsonToTrackEntity(obj: JSONObject, isLiked: Boolean): SavedTrackEntity = SavedTrackEntity(
        id = obj.optString("id"),
        title = obj.optString("title"),
        artist = obj.optString("artist"),
        album = obj.optString("album", "YouTube Music"),
        artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
        streamUrl = null,
        durationMs = obj.optLong("durationMs", 210_000L),
        genre = obj.optString("genre", "YouTube Music"),
        isLocal = false,
        isExplicit = obj.optBoolean("isExplicit", false),
        releaseYear = obj.optString("releaseYear"),
        isLiked = isLiked,
        likedAt = if (isLiked) System.currentTimeMillis() else 0L
    )
}
