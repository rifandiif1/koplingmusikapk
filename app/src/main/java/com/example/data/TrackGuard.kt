package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.Track
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TrackGuard"
private const val GUARD_FILE_NAME = "koplingfy_unplayable_guard.json"
private const val UNPLAYABLE_TTL_MS = 14L * 24L * 60L * 60L * 1000L // 14 days

/**
 * Central guard ensuring:
 * 1. Anti-duplicate songs across search, home charts, radio, playlists, and queues using
 *    canonical (normalized title + primary artist) matching while preserving distinct variants
 *    like Acoustic, Live, Remix, Slowed, or Koplo.
 * 2. Automatic elimination of unplayable/broken tracks via a lightweight persisted blacklist
 *    (with automatic replacement videoId memory when an alternate playable upload is found).
 */
object TrackGuard {
    private val unplayableTimestamps = ConcurrentHashMap<String, Long>()
    private val replacementVideoIds = ConcurrentHashMap<String, String>()

    @Volatile
    private var guardFile: File? = null

    private val meaningfulVariantKeywords = listOf(
        "remix", "acoustic", "akustik", "live", "unplugged", "slowed", "reverb",
        "sped up", "speed up", "nightcore", "koplo", "dangdut", "orchestra",
        "instrumental", "karaoke", "cover", "piano version", "demo", "extended",
        "stripped", "mashup", "medley", "versi", "lofi", "lo-fi", "8d"
    )

    private val noiseBracketRegex = Regex("""[\(\[\{]([^\)\]\}]*)[\)\]\}]""")
    private val trailingNoiseRegex = Regex(
        """(?i)\s*[-–—|•]\s*(official\s*(music\s*)?(video|audio|lyric(s)?\s*video|mv|visualizer)|lyric(s)?(\s*video)?|audio|hd|hq|4k|remaster(ed)?(\s*\d{4})?|explicit|clean|topic).*$"""
    )
    private val featSuffixRegex = Regex("""(?i)\s+(feat\.?|ft\.?|featuring|with)\s+.*$""")
    private val nonAlphaNumRegex = Regex("""[^\p{L}\p{Nd}\s]""")
    private val multiSpaceRegex = Regex("""\s+""")

    private val invalidTitles = setOf(
        "[deleted video]",
        "[private video]",
        "deleted video",
        "private video",
        "song unavailable",
        "video unavailable",
        "unavailable"
    )

    fun init(context: Context) {
        if (guardFile != null) return
        val file = File(context.applicationContext.filesDir, GUARD_FILE_NAME)
        guardFile = file
        loadFromDisk(file)
    }

    private fun loadFromDisk(file: File) {
        if (!file.exists()) return
        runCatching {
            val now = System.currentTimeMillis()
            val obj = JSONObject(file.readText())
            val blocked = obj.optJSONObject("unplayable")
            if (blocked != null) {
                val keys = blocked.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val ts = blocked.optLong(id, 0L)
                    if (ts > 0L && now - ts < UNPLAYABLE_TTL_MS) {
                        unplayableTimestamps[id] = ts
                    }
                }
            }
            val replacements = obj.optJSONObject("replacements")
            if (replacements != null) {
                val keys = replacements.keys()
                while (keys.hasNext()) {
                    val origId = keys.next()
                    val altVideoId = replacements.optString(origId, "").trim()
                    if (origId.isNotBlank() && altVideoId.isNotBlank()) {
                        replacementVideoIds[origId] = altVideoId
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to load unplayable track guard file", err)
        }
    }

    private fun persistToDisk() {
        val file = guardFile ?: return
        runCatching {
            val now = System.currentTimeMillis()
            val blockedObj = JSONObject()
            unplayableTimestamps.forEach { (id, ts) ->
                if (now - ts < UNPLAYABLE_TTL_MS) {
                    blockedObj.put(id, ts)
                }
            }
            val repObj = JSONObject()
            replacementVideoIds.forEach { (origId, altVid) ->
                repObj.put(origId, altVid)
            }
            val root = JSONObject().apply {
                put("unplayable", blockedObj)
                put("replacements", repObj)
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to persist unplayable track guard file", err)
        }
    }

    /**
     * Checks whether raw metadata from YouTube Music looks obviously broken/unplayable
     * (e.g., deleted/private placeholders, malformed video IDs, or 0-second clips).
     */
    fun isMetadataInvalid(videoId: String?, title: String, durationMs: Long? = null): Boolean {
        if (videoId.isNullOrBlank()) return true
        val trimmedId = videoId.trim()
        if (trimmedId.length != 11 || !trimmedId.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            return true
        }
        val lowerTitle = title.trim().lowercase(Locale.ROOT)
        if (lowerTitle.isBlank() || lowerTitle in invalidTitles) return true
        if (durationMs != null && durationMs in 1L..8_000L) return true
        return false
    }

    fun isUnplayable(track: Track): Boolean {
        if (track.isLocal || track.isDownloaded) return false
        return isUnplayableId(track.id)
    }

    fun isUnplayableId(trackId: String): Boolean {
        val ts = unplayableTimestamps[trackId] ?: return false
        if (System.currentTimeMillis() - ts >= UNPLAYABLE_TTL_MS) {
            unplayableTimestamps.remove(trackId)
            return false
        }
        return true
    }

    fun markUnplayable(track: Track) {
        if (track.isLocal || track.isDownloaded) return
        markUnplayableId(track.id)
    }

    fun markUnplayableId(trackId: String) {
        if (trackId.isBlank() || trackId.startsWith("local_")) return
        unplayableTimestamps[trackId] = System.currentTimeMillis()
        replacementVideoIds.remove(trackId)
        persistToDisk()
    }

    fun markPlayable(trackId: String) {
        if (unplayableTimestamps.remove(trackId) != null) {
            persistToDisk()
        }
    }

    fun recordReplacementVideoId(originalTrackId: String, workingVideoId: String) {
        if (originalTrackId.isBlank() || workingVideoId.length != 11) return
        replacementVideoIds[originalTrackId] = workingVideoId
        unplayableTimestamps.remove(originalTrackId)
        persistToDisk()
    }

    fun getReplacementVideoId(originalTrackId: String): String? =
        replacementVideoIds[originalTrackId]?.takeIf { it.length == 11 }

    /**
     * Called after YouTube Music account login so tracks that previously required login
     * can be re-evaluated cleanly.
     */
    fun clearUnplayableCache() {
        unplayableTimestamps.clear()
        persistToDisk()
    }

    /**
     * Computes a canonical identity key for a song (normalized title + variant tag + primary artist)
     * so duplicate uploads of the exact same song across different YouTube video IDs are detected.
     */
    fun canonicalKey(track: Track): String {
        if (track.isLocal) return track.id
        val rawTitle = track.title.trim()
        if (rawTitle.isBlank()) return track.id

        // Extract meaningful variant markers (e.g. Remix, Acoustic, Live, Koplo, Slowed)
        val lowerRaw = rawTitle.lowercase(Locale.ROOT)
        val detectedVariants = meaningfulVariantKeywords
            .filter { kw -> lowerRaw.contains(kw) }
            .sorted()
            .joinToString("+")

        var cleanTitle = rawTitle
            .replace(noiseBracketRegex) { match ->
                val inner = match.groupValues.getOrNull(1).orEmpty().lowercase(Locale.ROOT)
                if (meaningfulVariantKeywords.any { inner.contains(it) }) " $inner " else " "
            }
            .replace(trailingNoiseRegex, "")
            .replace(featSuffixRegex, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlphaNumRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()

        if (cleanTitle.isBlank()) {
            cleanTitle = lowerRaw.replace(nonAlphaNumRegex, " ").replace(multiSpaceRegex, " ").trim()
        }
        if (cleanTitle.isBlank()) return track.id

        val primaryArtist = normalizePrimaryArtist(track.artist)
        return buildString {
            append(cleanTitle)
            if (detectedVariants.isNotBlank()) {
                append('|')
                append(detectedVariants)
            }
            if (primaryArtist.isNotBlank()) {
                append("::")
                append(primaryArtist)
            }
        }
    }

    fun isSameSong(a: Track, b: Track): Boolean {
        if (a.id == b.id) return true
        return canonicalKey(a) == canonicalKey(b)
    }

    private fun normalizePrimaryArtist(artist: String): String {
        val cleaned = artist.trim()
            .lowercase(Locale.ROOT)
            .replace(" - topic", "")
            .replace("vevo", "")
            .replace("official", "")
            .split(Regex("""(,|&|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|•|\band\b|\bx\b)"""))
            .firstOrNull()
            .orEmpty()
            .replace(nonAlphaNumRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
        return if (cleaned == "youtube music" || cleaned == "various artists" || cleaned == "unknown") "" else cleaned
    }

    private fun qualityScore(track: Track, preserveTrackId: String?): Int {
        var score = 0
        if (preserveTrackId != null && track.id == preserveTrackId) score += 1000
        if (track.isDownloaded) score += 200
        if (track.isLocal) score += 150
        if (!track.artworkUrl.isNullOrBlank()) score += 20
        if (track.durationMs in 45_000L..600_000L && track.durationMs != 210_000L) score += 15
        if (!track.artistBrowseId.isNullOrBlank()) score += 10
        if (!track.albumBrowseId.isNullOrBlank()) score += 8
        val lowerTitle = track.title.lowercase(Locale.ROOT)
        if (!lowerTitle.contains("official video") && !lowerTitle.contains("lyric video") && !lowerTitle.contains("mv")) {
            score += 12
        }
        return score
    }

    /**
     * Filters out known unplayable tracks and removes duplicate songs (both exact ID duplicates
     * and canonical title+artist duplicates), preserving list order and keeping the highest-quality
     * entry for each unique song.
     */
    fun deduplicateAndFilter(
        tracks: List<Track>,
        preserveTrackId: String? = null
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val chosenByKey = LinkedHashMap<String, Track>(tracks.size)
        val seenIds = HashSet<String>(tracks.size)

        for (track in tracks) {
            if (track.id != preserveTrackId && isUnplayable(track)) continue
            if (!seenIds.add(track.id)) continue

            val key = canonicalKey(track)
            val existing = chosenByKey[key]
            if (existing == null) {
                chosenByKey[key] = track
            } else if (qualityScore(track, preserveTrackId) > qualityScore(existing, preserveTrackId)) {
                chosenByKey[key] = track
            }
        }
        return chosenByKey.values.toList()
    }
}
