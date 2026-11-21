package com.example.data.remote

import com.example.data.TrackGuard
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.AudioQuality
import com.example.model.RemotePlaylistSummary
import com.example.model.Track
import com.example.model.VideoCandidate
import com.example.model.YouTubeAccount
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class InnerTubeSearchBundle(
    val tracks: List<Track>,
    val videos: List<Track>,
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
    val playlists: List<RemotePlaylistSummary>
)

data class ResolvedStream(
    val url: String,
    val userAgent: String? = null,
    val cookie: String? = null,
    val durationMs: Long? = null,
    val bitrateKbps: Int? = null,
    val sourceLabel: String = "YouTube Music • FULL",
    val mimeType: String? = null,
    val contentLength: Long? = null,
    val cacheKey: String? = null
)

sealed interface StreamResolveResult {
    data class Success(val stream: ResolvedStream) : StreamResolveResult
    data class LoginRequired(val reason: String) : StreamResolveResult
    data class Unavailable(val reason: String) : StreamResolveResult
}

data class ResolvedVideoStream(
    val videoUrl: String,
    val audioUrl: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null,
    val durationMs: Long? = null,
    val qualityLabel: String = "Video",
    val isMuxed: Boolean = true
)

sealed interface VideoStreamResolveResult {
    data class Success(val stream: ResolvedVideoStream) : VideoStreamResolveResult
    data class Unavailable(val reason: String) : VideoStreamResolveResult
}

/**
 * Full YouTube Music InnerTube client matching Metrolist's InnerTubeX architecture:
 * - Cookie + SAPISIDHASH authentication when signed in to YouTube Music
 * - WEB_REMIX for search (songs, videos, albums, artists, playlists), live suggestions,
 *   browse (charts, artists, albums, playlists, user library), radio (/next), and native lyrics
 * - Multi-client player resolution (Authenticated WEB_REMIX/ANDROID_MUSIC/TVHTML5 +
 *   Unauthenticated VISIONOS / ANDROID_VR / IOS / TVHTML5_SIMPLY_EMBEDDED_PLAYER)
 * - ZERO 30-second preview fallbacks: streams full-length audio only.
 */
object InnerTubeClient {
    private const val YTM_ORIGIN = "https://music.youtube.com"
    private const val WEB_REMIX_VERSION = "1.20260707.12.00"
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    // YouTube Music filter params
    private const val PARAMS_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    private const val PARAMS_VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
    private const val PARAMS_PLAYLISTS = "Eg-KAQwIABAAGAAgACgBMABqChAEEAMQCRAFEAo%3D"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val visitorRegex = Regex(""""(Cg[a-zA-Z0-9_\-%]{15,})"""")

    @Volatile
    private var cachedVisitorData: String? = null

    @Volatile
    private var visitorFetchedAtMs: Long = 0L

    @Volatile
    private var authCookie: String = ""

    private val sharedConnectionPool = ConnectionPool(12, 5, TimeUnit.MINUTES)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Tight timeout client for fast parallel /player profile racing (prevents 15s stalls)
    private val playerClient: OkHttpClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // Shared pooled HTTP/2 client exposed for ExoPlayer OkHttpDataSource & fast chunked downloads
    val streamHttpClient: OkHttpClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val raceExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "koplingfy-innertube-race").apply { isDaemon = true }
    }

    private data class CachedStreamEntry(
        val stream: ResolvedStream,
        val expiresAtMs: Long
    )

    private data class CachedVideoStreamEntry(
        val stream: ResolvedVideoStream,
        val expiresAtMs: Long
    )

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamEntry>()
    private val videoStreamUrlCache = ConcurrentHashMap<String, CachedVideoStreamEntry>()
    private val lyricVideoSearchCache = ConcurrentHashMap<String, List<VideoCandidate>>()

    @Volatile
    private var lastWinningUnauthProfile: String? = null

    @Volatile
    private var lastWinningAuthProfile: String? = null

    private val expireParamRegex = Regex("""[?&]expire=(\d+)""")

    fun warmupAsync() {
        if (!cachedVisitorData.isNullOrBlank()) return
        raceExecutor.execute {
            runCatching { getOrFetchVisitorData(forceRefresh = false) }
        }
    }

    fun getCachedStream(videoId: String, quality: AudioQuality): ResolvedStream? {
        val key = streamCacheKey(videoId, quality)
        val entry = streamUrlCache[key] ?: return null
        if (System.currentTimeMillis() < entry.expiresAtMs) {
            return entry.stream
        }
        streamUrlCache.remove(key)
        return null
    }

    fun invalidateStreamCache(videoId: String) {
        val prefix = "$videoId|"
        streamUrlCache.keys.removeIf { it.startsWith(prefix) }
    }

    fun clearStreamCache() {
        streamUrlCache.clear()
        videoStreamUrlCache.clear()
    }

    private fun streamCacheKey(videoId: String, quality: AudioQuality): String =
        "$videoId|${quality.name}|${isAuthenticated()}"

    private fun computeStreamExpiryMs(url: String): Long {
        val now = System.currentTimeMillis()
        val parsedSec = expireParamRegex.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
        return if (parsedSec != null && parsedSec * 1000L > now + 60_000L) {
            // 5-minute safety margin before YouTube's signed URL expires
            (parsedSec * 1000L - 5 * 60_000L).coerceAtLeast(now + 60_000L)
        } else {
            now + 25 * 60_000L
        }
    }

    fun updateAuth(cookie: String, visitorData: String? = null) {
        val trimmed = cookie.trim()
        if (trimmed != authCookie) {
            authCookie = trimmed
            streamUrlCache.clear()
        }
        if (!visitorData.isNullOrBlank()) {
            cachedVisitorData = visitorData.trim()
            visitorFetchedAtMs = System.currentTimeMillis()
        }
    }

    fun isAuthenticated(): Boolean =
        authCookie.contains("SAPISID=") || authCookie.contains("__Secure-3PAPISID=")

    private data class PlayerClientProfile(
        val friendlyName: String,
        val clientName: String,
        val clientVersion: String,
        val clientId: String,
        val userAgent: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null,
        val platform: String? = null,
        val includeUserAgentInContext: Boolean = false,
        val useAuth: Boolean = false,
        val thirdPartyEmbedUrl: String? = null
    )

    // Authenticated profiles used when user logs in via YouTube Music WebView
    private val authPlaybackProfiles = listOf(
        PlayerClientProfile(
            friendlyName = "Android Music Auth",
            clientName = "ANDROID_MUSIC",
            clientVersion = "8.10.52",
            clientId = "21",
            userAgent = "com.google.android.apps.youtube.music/8.10.52 (Linux; U; Android 14; id_ID; Pixel 8 Build/UQ1A.240205.004) gzip",
            osName = "Android",
            osVersion = "14",
            deviceMake = "Google",
            deviceModel = "Pixel 8",
            androidSdkVersion = 34,
            includeUserAgentInContext = true,
            useAuth = true
        ),
        PlayerClientProfile(
            friendlyName = "TVHTML5 Auth",
            clientName = "TVHTML5",
            clientVersion = "7.20250319.10.00",
            clientId = "7",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            useAuth = true
        ),
        PlayerClientProfile(
            friendlyName = "Android VR Auth",
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
            includeUserAgentInContext = true,
            useAuth = true
        )
    )

    // Metrolist's fastest unauthenticated direct-URL playback clients ordered by reliability & speed
    private val unauthPlaybackProfiles = listOf(
        PlayerClientProfile(
            friendlyName = "Android VR 1.65",
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
            includeUserAgentInContext = true
        ),
        PlayerClientProfile(
            friendlyName = "iOS",
            clientName = "IOS",
            clientVersion = "20.10.4",
            clientId = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2"
        ),
        PlayerClientProfile(
            friendlyName = "visionOS",
            clientName = "VISIONOS",
            clientVersion = "1.02",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "26.5.23O471",
            deviceMake = "Apple",
            deviceModel = "RealityDevice17,1"
        ),
        PlayerClientProfile(
            friendlyName = "Android VR 1.61",
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
            includeUserAgentInContext = true
        ),
        PlayerClientProfile(
            friendlyName = "TV Embedded",
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            thirdPartyEmbedUrl = "https://music.youtube.com"
        ),
        PlayerClientProfile(
            friendlyName = "Android VR 1.43",
            clientName = "ANDROID_VR",
            clientVersion = "1.43.32",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
            includeUserAgentInContext = true
        ),
        PlayerClientProfile(
            friendlyName = "visionOS 0.1",
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            osName = "VISION_OS",
            osVersion = "1.3",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            platform = "MOBILE"
        )
    )

    private fun extractSapisid(cookie: String): String? {
        if (cookie.isBlank()) return null
        val parts = cookie.split(";").map { it.trim() }
        val map = parts.mapNotNull {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq).trim() to it.substring(eq + 1).trim() else null
        }.toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"]
    }

    private fun buildSapisidHash(cookie: String, origin: String = YTM_ORIGIN): String? {
        val sapisid = extractSapisid(cookie) ?: return null
        val timestamp = System.currentTimeMillis() / 1000L
        val raw = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    fun getOrFetchVisitorData(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val existing = cachedVisitorData
        if (!forceRefresh && !existing.isNullOrBlank() && (now - visitorFetchedAtMs) < 30 * 60_000L) {
            return existing
        }
        return runCatching {
            val reqBuilder = Request.Builder()
                .url("$YTM_ORIGIN/sw.js_data")
                .header("User-Agent", WEB_USER_AGENT)
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
                .get()
            if (authCookie.isNotBlank()) {
                reqBuilder.header("Cookie", authCookie)
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching existing
                val body = resp.body?.string() ?: return@runCatching existing
                val match = visitorRegex.find(body)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) {
                    cachedVisitorData = match
                    visitorFetchedAtMs = now
                    match
                } else existing
            }
        }.getOrDefault(existing)
    }

    private fun webRemixContext(visitorData: String? = null): JSONObject = JSONObject().apply {
        put(
            "client",
            JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", WEB_REMIX_VERSION)
                put("hl", "id")
                put("gl", "ID")
                if (!visitorData.isNullOrBlank()) {
                    put("visitorData", visitorData)
                }
            }
        )
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        userAgent: String = WEB_USER_AGENT,
        clientId: String = "67",
        clientVersion: String = WEB_REMIX_VERSION,
        visitorData: String? = null,
        origin: String? = YTM_ORIGIN,
        cookieOverride: String? = null,
        includeAuth: Boolean = true,
        httpClient: OkHttpClient = client,
        callTracker: MutableList<Call>? = null
    ): JSONObject? {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("X-YouTube-Client-Name", clientId)
            .header("X-YouTube-Client-Version", clientVersion)
            .post(body.toString().toRequestBody(jsonMediaType))
        if (!visitorData.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorData)
        }
        if (origin != null) {
            reqBuilder.header("Origin", origin)
            reqBuilder.header("Referer", "$origin/")
        }
        val activeCookie = cookieOverride ?: if (includeAuth) authCookie else ""
        if (activeCookie.isNotBlank()) {
            reqBuilder.header("Cookie", activeCookie)
            reqBuilder.header("X-Goog-AuthUser", "0")
            buildSapisidHash(activeCookie, origin ?: YTM_ORIGIN)?.let { hash ->
                reqBuilder.header("Authorization", hash)
            }
        }
        val call = httpClient.newCall(reqBuilder.build())
        if (callTracker != null) {
            synchronized(callTracker) { callTracker.add(call) }
        }
        call.execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            return JSONObject(text)
        }
    }

    // ---- Account Profile & Library Sync ----
    fun fetchAccountInfo(cookie: String): YouTubeAccount? {
        if (cookie.isBlank()) return null
        val vd = getOrFetchVisitorData(forceRefresh = true).orEmpty()
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("deviceTheme", "DEVICE_THEME_SUPPORTED")
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/account/account_menu?prettyPrint=false",
            body = payload,
            visitorData = vd,
            cookieOverride = cookie,
            includeAuth = true
        ) ?: return YouTubeAccount(
            cookie = cookie,
            visitorData = vd,
            name = "Akun YouTube Music",
            email = "Tersambung via InnerTube"
        )

        val header = findFirstObjectByKey(root, "activeAccountHeaderRenderer")
        val name = extractRunsText(header?.optJSONObject("accountName")).ifBlank { "Pengguna YouTube Music" }
        val email = extractRunsText(header?.optJSONObject("email")).ifBlank {
            extractRunsText(header?.optJSONObject("channelHandle")).ifBlank { "Akun YouTube Music" }
        }
        val avatar = header?.optJSONObject("accountPhoto")
            ?.optJSONArray("thumbnails")
            ?.let { arr -> if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null }

        return YouTubeAccount(
            cookie = cookie,
            visitorData = vd,
            name = name,
            email = email,
            avatarUrl = avatar
        )
    }

    fun fetchAccountLikedSongs(): List<Track> {
        if (!isAuthenticated()) return emptyList()
        return browseCollection("FEmusic_liked_videos", fallbackTitle = "Lagu Disukai YTM")
    }

    fun fetchAccountPlaylists(): List<RemotePlaylistSummary> {
        if (!isAuthenticated()) return emptyList()
        val vd = getOrFetchVisitorData()
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("browseId", "FEmusic_liked_playlists")
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/browse?prettyPrint=false",
            body = payload,
            visitorData = vd,
            includeAuth = true
        ) ?: return emptyList()
        val playlists = mutableListOf<RemotePlaylistSummary>()
        walkTwoRowRenderers(root, mutableListOf(), mutableListOf(), mutableListOf(), playlists)
        return playlists.distinctBy { it.id }
    }

    // ---- Pure YouTube Music Home & Explore Charts ----
    fun fetchChartsAndExplore(): InnerTubeSearchBundle {
        val vd = getOrFetchVisitorData()
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<AlbumSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val playlists = mutableListOf<RemotePlaylistSummary>()

        runCatching {
            val chartsPayload = JSONObject().apply {
                put("context", webRemixContext(vd))
                put("browseId", "FEmusic_charts")
            }
            postJson("$YTM_ORIGIN/youtubei/v1/browse?prettyPrint=false", chartsPayload, visitorData = vd)?.let { root ->
                walkRenderers(root, tracks, albums, artists, playlists, null, null)
                walkTwoRowRenderers(root, tracks, albums, artists, playlists)
            }
        }

        if (tracks.size < 10) {
            runCatching {
                val explorePayload = JSONObject().apply {
                    put("context", webRemixContext(vd))
                    put("browseId", "FEmusic_explore")
                }
                postJson("$YTM_ORIGIN/youtubei/v1/browse?prettyPrint=false", explorePayload, visitorData = vd)?.let { root ->
                    walkRenderers(root, tracks, albums, artists, playlists, null, null)
                    walkTwoRowRenderers(root, tracks, albums, artists, playlists)
                }
            }
        }

        return InnerTubeSearchBundle(
            tracks = TrackGuard.deduplicateAndFilter(tracks).mapIndexed { idx, t -> t.copy(rank = idx + 1) },
            videos = emptyList(),
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id }
        )
    }

    // ---- Live Suggestions & Full Multi-Type Search ----
    fun getSearchSuggestions(input: String): List<String> {
        if (input.trim().length < 2) return emptyList()
        val vd = getOrFetchVisitorData()
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("input", input.trim())
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/music/get_search_suggestions?prettyPrint=false",
            body = payload,
            visitorData = vd
        ) ?: return emptyList()
        val suggestions = LinkedHashSet<String>()
        collectSuggestions(root, suggestions)
        return suggestions.take(8)
    }

    fun searchSongs(query: String): List<Track> {
        val vd = getOrFetchVisitorData()
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("query", query.trim())
            put("params", PARAMS_SONGS)
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false",
            body = payload,
            visitorData = vd
        ) ?: return emptyList()
        val tracks = mutableListOf<Track>()
        walkRenderers(root, tracks, mutableListOf(), mutableListOf(), mutableListOf(), null, null)
        return TrackGuard.deduplicateAndFilter(tracks)
    }

    fun searchVideos(query: String): List<Track> {
        val vd = getOrFetchVisitorData()
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("query", query.trim())
            put("params", PARAMS_VIDEOS)
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false",
            body = payload,
            visitorData = vd
        ) ?: return emptyList()
        val videos = mutableListOf<Track>()
        walkRenderers(root, videos, mutableListOf(), mutableListOf(), mutableListOf(), "Video Musik", null)
        return TrackGuard.deduplicateAndFilter(videos)
    }

    fun searchAll(query: String): InnerTubeSearchBundle {
        val trimmed = query.trim()
        val vd = getOrFetchVisitorData()

        val tracks = mutableListOf<Track>()
        val videos = mutableListOf<Track>()
        val albums = mutableListOf<AlbumSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val playlists = mutableListOf<RemotePlaylistSummary>()

        // Run songs, general, and videos queries in parallel for snappy Metrolist-speed search
        val songsFuture = raceExecutor.submit<JSONObject?> {
            runCatching {
                val songPayload = JSONObject().apply {
                    put("context", webRemixContext(vd))
                    put("query", trimmed)
                    put("params", PARAMS_SONGS)
                }
                postJson("$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false", songPayload, visitorData = vd)
            }.getOrNull()
        }
        val generalFuture = raceExecutor.submit<JSONObject?> {
            runCatching {
                val generalPayload = JSONObject().apply {
                    put("context", webRemixContext(vd))
                    put("query", trimmed)
                }
                postJson("$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false", generalPayload, visitorData = vd)
            }.getOrNull()
        }
        val videosFuture = raceExecutor.submit<JSONObject?> {
            runCatching {
                val videoPayload = JSONObject().apply {
                    put("context", webRemixContext(vd))
                    put("query", trimmed)
                    put("params", PARAMS_VIDEOS)
                }
                postJson("$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false", videoPayload, visitorData = vd)
            }.getOrNull()
        }

        runCatching { songsFuture.get(12, TimeUnit.SECONDS) }.getOrNull()?.let { root ->
            walkRenderers(root, tracks, mutableListOf(), mutableListOf(), mutableListOf(), null, null)
        }
        runCatching { generalFuture.get(12, TimeUnit.SECONDS) }.getOrNull()?.let { root ->
            walkRenderers(root, tracks, albums, artists, playlists, null, null)
        }
        runCatching { videosFuture.get(12, TimeUnit.SECONDS) }.getOrNull()?.let { root ->
            walkRenderers(root, videos, mutableListOf(), mutableListOf(), mutableListOf(), "Video Musik", null)
        }

        if (playlists.size < 4) {
            runCatching {
                val plPayload = JSONObject().apply {
                    put("context", webRemixContext(vd))
                    put("query", trimmed)
                    put("params", PARAMS_PLAYLISTS)
                }
                postJson("$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false", plPayload, visitorData = vd)?.let { root ->
                    walkRenderers(root, mutableListOf(), mutableListOf(), mutableListOf(), playlists, null, null)
                }
            }
        }

        return InnerTubeSearchBundle(
            tracks = TrackGuard.deduplicateAndFilter(tracks),
            videos = TrackGuard.deduplicateAndFilter(videos),
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id }
        )
    }

    fun browseCollection(browseId: String, fallbackTitle: String? = null, fallbackArt: String? = null): List<Track> {
        val vd = getOrFetchVisitorData()
        val normalizedId = if (browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("OLAK")) {
            "VL$browseId"
        } else browseId
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("browseId", normalizedId)
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/browse?prettyPrint=false",
            body = payload,
            visitorData = vd
        ) ?: return emptyList()
        val tracks = mutableListOf<Track>()
        walkRenderers(root, tracks, mutableListOf(), mutableListOf(), mutableListOf(), fallbackTitle, fallbackArt)
        return TrackGuard.deduplicateAndFilter(tracks)
    }

    // ---- Radio & Autoplay Queue (/youtubei/v1/next) ----
    fun getNextRadioQueue(videoId: String): List<Track> {
        val vd = getOrFetchVisitorData()
        val payload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        }
        val root = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/next?prettyPrint=false",
            body = payload,
            visitorData = vd
        ) ?: return emptyList()

        val tracks = mutableListOf<Track>()
        walkPlaylistPanelRenderers(root, tracks)
        return TrackGuard.deduplicateAndFilter(tracks)
    }

    // ---- Native YouTube Music Lyrics (/next -> MPLYt_ browse) ----
    fun fetchYouTubeMusicLyrics(videoId: String): String? {
        val vd = getOrFetchVisitorData()
        val nextPayload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("videoId", videoId)
            put("isAudioOnly", true)
        }
        val nextRoot = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/next?prettyPrint=false",
            body = nextPayload,
            visitorData = vd
        ) ?: return null

        val lyricsBrowseId = findBrowseIdWithPrefix(nextRoot, "MPLYt") ?: return null
        val browsePayload = JSONObject().apply {
            put("context", webRemixContext(vd))
            put("browseId", lyricsBrowseId)
        }
        val browseRoot = postJson(
            url = "$YTM_ORIGIN/youtubei/v1/browse?prettyPrint=false",
            body = browsePayload,
            visitorData = vd
        ) ?: return null

        val descRenderer = findFirstObjectByKey(browseRoot, "musicDescriptionShelfRenderer") ?: return null
        val text = extractRunsText(descRenderer.optJSONObject("description"))
        return text.takeIf { it.length > 20 }
    }

    // ---- Metrolist-Speed Full-Length Audio Stream Resolution (Cache + Sticky Profile + Parallel Racing) ----
    fun resolveAudioStream(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH,
        forceRefresh: Boolean = false
    ): StreamResolveResult {
        if (!forceRefresh) {
            getCachedStream(videoId, quality)?.let { cached ->
                return StreamResolveResult.Success(cached)
            }
        } else {
            invalidateStreamCache(videoId)
        }

        // Don't stall playback waiting on sw.js_data if visitorData isn't warmed yet; kick off async warmup
        val vd = cachedVisitorData ?: run {
            warmupAsync()
            null
        }

        val firstAttempt = tryProfilesForVideoId(videoId, vd, quality)
        if (firstAttempt is StreamResolveResult.Success) {
            cacheResolvedStream(videoId, quality, firstAttempt.stream)
            return firstAttempt
        }

        val freshVd = getOrFetchVisitorData(forceRefresh = true)
        if (freshVd != null && freshVd != vd) {
            val secondAttempt = tryProfilesForVideoId(videoId, freshVd, quality)
            if (secondAttempt is StreamResolveResult.Success) {
                cacheResolvedStream(videoId, quality, secondAttempt.stream)
                return secondAttempt
            }
            return secondAttempt
        }
        return firstAttempt
    }

    private fun cacheResolvedStream(videoId: String, quality: AudioQuality, stream: ResolvedStream) {
        val key = streamCacheKey(videoId, quality)
        streamUrlCache[key] = CachedStreamEntry(
            stream = stream,
            expiresAtMs = computeStreamExpiryMs(stream.url)
        )
    }

    private data class ProfileAttemptOutcome(
        val profile: PlayerClientProfile,
        val stream: ResolvedStream? = null,
        val loginRequired: Boolean = false,
        val reason: String = ""
    )

    private fun orderedProfilesForCurrentAuth(): List<PlayerClientProfile> {
        val isAuth = isAuthenticated()
        val base = if (isAuth) {
            authPlaybackProfiles + unauthPlaybackProfiles
        } else {
            unauthPlaybackProfiles
        }
        val winnerName = if (isAuth) lastWinningAuthProfile else lastWinningUnauthProfile
        if (winnerName.isNullOrBlank()) return base
        val winner = base.firstOrNull { it.friendlyName == winnerName } ?: return base
        return listOf(winner) + base.filterNot { it.friendlyName == winnerName }
    }

    private fun tryProfilesForVideoId(
        videoId: String,
        visitorData: String?,
        quality: AudioQuality
    ): StreamResolveResult {
        val ordered = orderedProfilesForCurrentAuth()
        val primaryBatch = ordered.take(3)
        val fallbackBatch = ordered.drop(3)

        var sawLoginRequired = false
        var lastReason = ""

        val primaryResult = raceProfileBatch(primaryBatch, videoId, visitorData, quality)
        if (primaryResult.stream != null) {
            recordWinningProfile(primaryResult.profile)
            return StreamResolveResult.Success(primaryResult.stream)
        }
        sawLoginRequired = sawLoginRequired || primaryResult.loginRequired
        if (primaryResult.reason.isNotBlank()) lastReason = primaryResult.reason

        if (fallbackBatch.isNotEmpty()) {
            val fallbackResult = raceProfileBatch(fallbackBatch, videoId, visitorData, quality)
            if (fallbackResult.stream != null) {
                recordWinningProfile(fallbackResult.profile)
                return StreamResolveResult.Success(fallbackResult.stream)
            }
            sawLoginRequired = sawLoginRequired || fallbackResult.loginRequired
            if (fallbackResult.reason.isNotBlank()) lastReason = fallbackResult.reason
        }

        return if (sawLoginRequired) {
            StreamResolveResult.LoginRequired(
                if (isAuthenticated()) {
                    "Sesi YouTube Music kadaluarsa atau lagu dibatasi wilayah. Silakan login ulang di Pengaturan."
                } else {
                    "Lagu ini memerlukan Login Akun YouTube Music (Explicit / verifikasi usia atau pembatasan jaringan)."
                }
            )
        } else {
            StreamResolveResult.Unavailable(
                lastReason.ifBlank { "Stream penuh YouTube Music sedang tidak dapat diakses. Coba lagi atau Login YouTube Music." }
            )
        }
    }

    private fun recordWinningProfile(profile: PlayerClientProfile) {
        if (isAuthenticated()) {
            lastWinningAuthProfile = profile.friendlyName
        } else {
            lastWinningUnauthProfile = profile.friendlyName
        }
    }

    private fun raceProfileBatch(
        profiles: List<PlayerClientProfile>,
        videoId: String,
        visitorData: String?,
        quality: AudioQuality
    ): ProfileAttemptOutcome {
        if (profiles.size == 1) {
            return executeSingleProfile(profiles.first(), videoId, visitorData, quality, null)
        }

        val completionService = ExecutorCompletionService<ProfileAttemptOutcome>(raceExecutor)
        val activeCalls = mutableListOf<Call>()
        val futures = mutableListOf<Future<ProfileAttemptOutcome>>()

        for (profile in profiles) {
            futures.add(
                completionService.submit {
                    executeSingleProfile(profile, videoId, visitorData, quality, activeCalls)
                }
            )
        }

        var sawLoginRequired = false
        var lastReason = ""
        try {
            for (i in profiles.indices) {
                val finished = completionService.poll(8, TimeUnit.SECONDS) ?: break
                val outcome = runCatching { finished.get() }.getOrNull() ?: continue
                if (outcome.stream != null) {
                    return outcome
                }
                if (outcome.loginRequired) sawLoginRequired = true
                if (outcome.reason.isNotBlank()) lastReason = outcome.reason
            }
        } finally {
            // Immediately cancel all losing/in-flight HTTP requests to free sockets & bandwidth
            futures.forEach { it.cancel(true) }
            synchronized(activeCalls) {
                activeCalls.forEach { runCatching { it.cancel() } }
                activeCalls.clear()
            }
        }

        return ProfileAttemptOutcome(
            profile = profiles.first(),
            stream = null,
            loginRequired = sawLoginRequired,
            reason = lastReason
        )
    }

    private fun executeSingleProfile(
        profile: PlayerClientProfile,
        videoId: String,
        visitorData: String?,
        quality: AudioQuality,
        callTracker: MutableList<Call>?
    ): ProfileAttemptOutcome {
        var loginReq = false
        var reasonText = ""
        val resolved = runCatching {
            val clientObj = JSONObject().apply {
                put("clientName", profile.clientName)
                put("clientVersion", profile.clientVersion)
                put("hl", "id")
                put("gl", "ID")
                if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                if (profile.includeUserAgentInContext) put("userAgent", profile.userAgent)
                profile.osName?.let { put("osName", it) }
                profile.osVersion?.let { put("osVersion", it) }
                profile.deviceMake?.let { put("deviceMake", it) }
                profile.deviceModel?.let { put("deviceModel", it) }
                profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
                profile.platform?.let { put("platform", it) }
            }

            val contextObj = JSONObject().put("client", clientObj)
            if (profile.thirdPartyEmbedUrl != null) {
                contextObj.put("thirdParty", JSONObject().put("embedUrl", profile.thirdPartyEmbedUrl))
            }

            val payload = JSONObject().apply {
                put("context", contextObj)
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val root = postJson(
                url = "$YTM_ORIGIN/youtubei/v1/player?prettyPrint=false",
                body = payload,
                userAgent = profile.userAgent,
                clientId = profile.clientId,
                clientVersion = profile.clientVersion,
                visitorData = visitorData,
                origin = YTM_ORIGIN,
                includeAuth = profile.useAuth,
                httpClient = playerClient,
                callTracker = callTracker
            ) ?: return@runCatching null

            val playability = root.optJSONObject("playabilityStatus")
            val status = playability?.optString("status").orEmpty()
            val reason = playability?.optString("reason").orEmpty()
            if (status != "OK") {
                if (status == "LOGIN_REQUIRED" || reason.contains("Sign in", ignoreCase = true) || reason.contains("age", ignoreCase = true)) {
                    loginReq = true
                }
                if (reason.isNotBlank()) reasonText = reason
                return@runCatching null
            }

            val streamingData = root.optJSONObject("streamingData") ?: return@runCatching null
            val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()

            val audioCandidates = mutableListOf<AudioFmtCandidate>()

            for (i in 0 until adaptive.length()) {
                val fmt = adaptive.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType")
                val directUrl = fmt.optString("url")
                if (directUrl.isBlank() || !mime.startsWith("audio/")) continue
                audioCandidates.add(
                    AudioFmtCandidate(
                        url = directUrl,
                        mime = mime,
                        itag = fmt.optInt("itag", 0),
                        bitrate = fmt.optInt("bitrate", 0),
                        durationMs = fmt.optString("approxDurationMs").toLongOrNull(),
                        contentLength = fmt.optString("contentLength").toLongOrNull()
                    )
                )
            }

            val chosen = selectBestCandidate(audioCandidates, quality)

            var finalUrl = chosen?.url
            var finalDur = chosen?.durationMs
            var finalMime = chosen?.mime
            var finalLength = chosen?.contentLength
            val finalBitrateKbps = chosen?.bitrate?.div(1000)?.takeIf { it > 0 }

            if (finalUrl == null) {
                val formats = streamingData.optJSONArray("formats") ?: JSONArray()
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val directUrl = fmt.optString("url")
                    if (directUrl.isNotBlank()) {
                        finalUrl = directUrl
                        finalDur = fmt.optString("approxDurationMs").toLongOrNull()
                        finalMime = fmt.optString("mimeType").takeIf { it.isNotBlank() }
                        finalLength = fmt.optString("contentLength").toLongOrNull()
                        break
                    }
                }
            }

            val fallbackVideoDur = root.optJSONObject("videoDetails")
                ?.optString("lengthSeconds")
                ?.toLongOrNull()
                ?.times(1000L)

            finalUrl?.let { url ->
                val codecTag = when {
                    finalMime?.contains("webm", ignoreCase = true) == true -> "OPUS"
                    finalMime?.contains("mp4", ignoreCase = true) == true -> "AAC"
                    else -> "YTM"
                }
                val qualityBadge = when {
                    finalBitrateKbps != null -> "$codecTag • ${finalBitrateKbps}kbps"
                    profile.useAuth -> "YOUTUBE MUSIC • AKUN"
                    else -> "YOUTUBE MUSIC • FULL"
                }
                ResolvedStream(
                    url = url,
                    userAgent = profile.userAgent,
                    cookie = if (profile.useAuth) authCookie else null,
                    durationMs = finalDur ?: fallbackVideoDur,
                    bitrateKbps = finalBitrateKbps,
                    sourceLabel = qualityBadge,
                    mimeType = finalMime,
                    contentLength = finalLength,
                    cacheKey = "yt_${videoId}_${quality.name}"
                )
            }
        }.getOrNull()

        return ProfileAttemptOutcome(
            profile = profile,
            stream = resolved,
            loginRequired = loginReq,
            reason = reasonText
        )
    }

    private fun selectBestCandidate(
        candidates: List<AudioFmtCandidate>,
        quality: AudioQuality
    ): AudioFmtCandidate? {
        if (candidates.isEmpty()) return null
        // Metrolist-style ExoPlayer format selection: supports both Opus (audio/webm) & AAC (audio/mp4).
        // Opus (itag 251/250/249) has faster initial buffering & superior efficiency at equivalent bitrates.
        return when (quality) {
            AudioQuality.HIGH -> candidates.maxByOrNull { fmt ->
                val codecBonus = when {
                    fmt.itag == 141 -> 600_000 // Premium AAC 256kbps
                    fmt.itag == 251 -> 550_000 // Opus ~160kbps (Metrolist default high)
                    fmt.itag == 140 -> 500_000 // AAC ~128kbps
                    fmt.mime.contains("webm", ignoreCase = true) -> 200_000
                    else -> 100_000
                }
                codecBonus + fmt.bitrate
            }
            AudioQuality.NORMAL -> candidates.minByOrNull { fmt ->
                val target = if (fmt.mime.contains("webm", ignoreCase = true)) 120_000 else 128_000
                kotlin.math.abs(fmt.bitrate - target) - (if (fmt.mime.contains("webm", ignoreCase = true)) 10_000 else 0)
            }
            AudioQuality.LOW -> candidates.minByOrNull { fmt ->
                fmt.bitrate - (if (fmt.itag == 249) 8_000 else 0)
            }
        }
    }

    private data class AudioFmtCandidate(
        val url: String,
        val mime: String,
        val itag: Int,
        val bitrate: Int,
        val durationMs: Long?,
        val contentLength: Long?
    )

    private data class VideoFmtCandidate(
        val url: String,
        val mime: String,
        val height: Int,
        val bitrate: Int,
        val qualityLabel: String,
        val durationMs: Long?
    )

    /**
     * Metrolist-style Video Discovery prioritizing Official Lyric Videos FIRST,
     * followed by other Lyric Videos, Official Music Videos, and the track's own videoId.
     */
    fun searchLyricVideosForTrack(
        title: String,
        artist: String,
        originalVideoId: String? = null
    ): List<VideoCandidate> {
        val cacheKey = "${title.lowercase().trim()}|${artist.lowercase().trim()}|${originalVideoId.orEmpty()}"
        lyricVideoSearchCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        val cleanTitle = title
            .replace(Regex("""\s*\([^)]*\)"""), "")
            .replace(Regex("""\s*\[[^\]]*]"""), "")
            .trim()
        val cleanArtist = artist.split(",", "&", "feat.", "ft.", ignoreCase = true)
            .firstOrNull()?.trim().orEmpty()

        val vd = getOrFetchVisitorData()
        val rawVideos = mutableListOf<Track>()

        // 1. Primary search explicitly targeting Lyric Video
        val lyricQuery = "$cleanTitle $cleanArtist lyric video".trim()
        runCatching {
            val payload = JSONObject().apply {
                put("context", webRemixContext(vd))
                put("query", lyricQuery)
                put("params", PARAMS_VIDEOS)
            }
            val root = postJson(
                url = "$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false",
                body = payload,
                visitorData = vd
            )
            if (root != null) {
                walkRenderers(root, rawVideos, mutableListOf(), mutableListOf(), mutableListOf(), "Video Musik", null)
            }
        }

        // 2. Secondary search for official music video / alternate uploads so user can switch
        val mvQuery = "$cleanTitle $cleanArtist official video".trim()
        runCatching {
            val payload = JSONObject().apply {
                put("context", webRemixContext(vd))
                put("query", mvQuery)
                put("params", PARAMS_VIDEOS)
            }
            val root = postJson(
                url = "$YTM_ORIGIN/youtubei/v1/search?prettyPrint=false",
                body = payload,
                visitorData = vd
            )
            if (root != null) {
                val mvList = mutableListOf<Track>()
                walkRenderers(root, mvList, mutableListOf(), mutableListOf(), mutableListOf(), "Video Musik", null)
                rawVideos.addAll(mvList.take(4))
            }
        }

        val lyricKeywords = listOf("lyric", "lyrics", "lirik", "kata-kata", "sub indo", "terjemahan")
        val scored = rawVideos
            .distinctBy { it.id }
            .mapNotNull { t ->
                val vid = t.id.removePrefix("yt_").takeIf { it.length >= 6 } ?: return@mapNotNull null
                val lowerTitle = t.title.lowercase()
                val lowerArtist = t.artist.lowercase()
                val isLyric = lyricKeywords.any { kw -> lowerTitle.contains(kw) }
                val matchesTitle = cleanTitle.length >= 3 && lowerTitle.contains(cleanTitle.lowercase().take(12))
                val matchesArtist = cleanArtist.length >= 2 &&
                    (lowerTitle.contains(cleanArtist.lowercase()) || lowerArtist.contains(cleanArtist.lowercase()))
                val isOfficial = lowerTitle.contains("official") || lowerArtist.contains("vevo")

                var score = 0
                if (isLyric) score += 120
                if (isLyric && isOfficial) score += 45
                if (matchesTitle) score += 35
                if (matchesArtist) score += 25
                if (lowerTitle.contains("cover") || lowerTitle.contains("karaoke") || lowerTitle.contains("8d")) {
                    score -= 70
                }
                if (lowerTitle.contains("shorts") || (t.durationMs in 1L..50_000L)) {
                    score -= 60
                }
                val durLabel = if (t.durationMs > 0L) {
                    val totalSec = t.durationMs / 1000L
                    "%d:%02d".format(totalSec / 60L, totalSec % 60L)
                } else ""

                score to VideoCandidate(
                    videoId = vid,
                    title = t.title,
                    channel = t.artist,
                    thumbnailUrl = t.artworkUrl ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                    durationLabel = durLabel,
                    isLyricVideo = isLyric
                )
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .take(8)
            .toMutableList()

        // Ensure original track videoId is available as fallback option if not already in the list
        if (!originalVideoId.isNullOrBlank() && scored.none { it.videoId == originalVideoId }) {
            scored.add(
                VideoCandidate(
                    videoId = originalVideoId,
                    title = "$title (Video Bawaan)",
                    channel = artist,
                    thumbnailUrl = "https://i.ytimg.com/vi/$originalVideoId/hqdefault.jpg",
                    isLyricVideo = false
                )
            )
        }

        if (scored.isNotEmpty()) {
            if (lyricVideoSearchCache.size > 60) {
                lyricVideoSearchCache.clear()
            }
            lyricVideoSearchCache[cacheKey] = scored
        }

        return scored
    }

    /**
     * Resolves a playable YouTube video stream for the selected videoId:
     * 1) Prefers fast muxed progressive MP4 (itag 22 / 18) for instant startup & zero A/V drift.
     * 2) Falls back to adaptive H.264 video (<=720p) + best audio for ExoPlayer MergingMediaSource.
     */
    fun resolveVideoStream(videoId: String, forceRefresh: Boolean = false): VideoStreamResolveResult {
        val vCacheKey = "$videoId|${isAuthenticated()}"
        if (!forceRefresh) {
            videoStreamUrlCache[vCacheKey]?.let { entry ->
                if (System.currentTimeMillis() < entry.expiresAtMs) {
                    return VideoStreamResolveResult.Success(entry.stream)
                }
                videoStreamUrlCache.remove(vCacheKey)
            }
        }
        val vd = cachedVisitorData ?: run {
            warmupAsync()
            null
        }
        val ordered = orderedProfilesForCurrentAuth()

        for (profile in ordered) {
            val resolved = runCatching {
                val clientObj = JSONObject().apply {
                    put("clientName", profile.clientName)
                    put("clientVersion", profile.clientVersion)
                    put("hl", "id")
                    put("gl", "ID")
                    if (!vd.isNullOrBlank()) put("visitorData", vd)
                    if (profile.includeUserAgentInContext) put("userAgent", profile.userAgent)
                    profile.osName?.let { put("osName", it) }
                    profile.osVersion?.let { put("osVersion", it) }
                    profile.deviceMake?.let { put("deviceMake", it) }
                    profile.deviceModel?.let { put("deviceModel", it) }
                    profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
                    profile.platform?.let { put("platform", it) }
                }
                val contextObj = JSONObject().put("client", clientObj)
                if (profile.thirdPartyEmbedUrl != null) {
                    contextObj.put("thirdParty", JSONObject().put("embedUrl", profile.thirdPartyEmbedUrl))
                }
                val payload = JSONObject().apply {
                    put("context", contextObj)
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                val root = postJson(
                    url = "$YTM_ORIGIN/youtubei/v1/player?prettyPrint=false",
                    body = payload,
                    userAgent = profile.userAgent,
                    clientId = profile.clientId,
                    clientVersion = profile.clientVersion,
                    visitorData = vd,
                    origin = YTM_ORIGIN,
                    includeAuth = profile.useAuth,
                    httpClient = playerClient
                ) ?: return@runCatching null

                val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
                if (status != "OK") return@runCatching null

                val streamingData = root.optJSONObject("streamingData") ?: return@runCatching null
                val fallbackDur = root.optJSONObject("videoDetails")
                    ?.optString("lengthSeconds")
                    ?.toLongOrNull()
                    ?.times(1000L)

                // 1. Check muxed progressive formats first (fast & rock-solid audio/video sync)
                val muxedFormats = streamingData.optJSONArray("formats") ?: JSONArray()
                val muxedCandidates = mutableListOf<VideoFmtCandidate>()
                for (i in 0 until muxedFormats.length()) {
                    val fmt = muxedFormats.optJSONObject(i) ?: continue
                    val directUrl = fmt.optString("url")
                    val mime = fmt.optString("mimeType")
                    if (directUrl.isBlank() || !mime.startsWith("video/")) continue
                    val h = fmt.optInt("height", 360)
                    muxedCandidates.add(
                        VideoFmtCandidate(
                            url = directUrl,
                            mime = mime,
                            height = h,
                            bitrate = fmt.optInt("bitrate", 0),
                            qualityLabel = fmt.optString("qualityLabel").ifBlank { "${h}p" },
                            durationMs = fmt.optString("approxDurationMs").toLongOrNull()
                        )
                    )
                }
                val bestMuxed = muxedCandidates
                    .filter { it.mime.contains("mp4", ignoreCase = true) }
                    .maxByOrNull { it.height }
                    ?: muxedCandidates.maxByOrNull { it.height }

                if (bestMuxed != null) {
                    return@runCatching ResolvedVideoStream(
                        videoUrl = bestMuxed.url,
                        audioUrl = null,
                        userAgent = profile.userAgent,
                        cookie = if (profile.useAuth) authCookie else null,
                        durationMs = bestMuxed.durationMs ?: fallbackDur,
                        qualityLabel = bestMuxed.qualityLabel,
                        isMuxed = true
                    )
                }

                // 2. Fallback to adaptive video (H.264/MP4 <= 720p) + best audio for MergingMediaSource
                val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
                val videoAdaptive = mutableListOf<VideoFmtCandidate>()
                val audioAdaptive = mutableListOf<AudioFmtCandidate>()

                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val directUrl = fmt.optString("url")
                    val mime = fmt.optString("mimeType")
                    if (directUrl.isBlank()) continue
                    if (mime.startsWith("video/mp4") || mime.startsWith("video/webm")) {
                        val h = fmt.optInt("height", 360)
                        if (h in 240..720) {
                            videoAdaptive.add(
                                VideoFmtCandidate(
                                    url = directUrl,
                                    mime = mime,
                                    height = h,
                                    bitrate = fmt.optInt("bitrate", 0),
                                    qualityLabel = fmt.optString("qualityLabel").ifBlank { "${h}p" },
                                    durationMs = fmt.optString("approxDurationMs").toLongOrNull()
                                )
                            )
                        }
                    } else if (mime.startsWith("audio/")) {
                        audioAdaptive.add(
                            AudioFmtCandidate(
                                url = directUrl,
                                mime = mime,
                                itag = fmt.optInt("itag", 0),
                                bitrate = fmt.optInt("bitrate", 0),
                                durationMs = fmt.optString("approxDurationMs").toLongOrNull(),
                                contentLength = fmt.optString("contentLength").toLongOrNull()
                            )
                        )
                    }
                }

                val bestVideo = videoAdaptive
                    .sortedWith(
                        compareByDescending<VideoFmtCandidate> { it.mime.contains("avc1", ignoreCase = true) || it.mime.contains("mp4", ignoreCase = true) }
                            .thenByDescending { it.height }
                    )
                    .firstOrNull()
                val bestAudio = selectBestCandidate(audioAdaptive, AudioQuality.NORMAL)

                if (bestVideo != null && bestAudio != null) {
                    return@runCatching ResolvedVideoStream(
                        videoUrl = bestVideo.url,
                        audioUrl = bestAudio.url,
                        userAgent = profile.userAgent,
                        cookie = if (profile.useAuth) authCookie else null,
                        durationMs = bestVideo.durationMs ?: bestAudio.durationMs ?: fallbackDur,
                        qualityLabel = bestVideo.qualityLabel,
                        isMuxed = false
                    )
                }
                null
            }.getOrNull()

            if (resolved != null) {
                videoStreamUrlCache[vCacheKey] = CachedVideoStreamEntry(
                    stream = resolved,
                    expiresAtMs = computeStreamExpiryMs(resolved.videoUrl)
                )
                return VideoStreamResolveResult.Success(resolved)
            }
        }

        return VideoStreamResolveResult.Unavailable("Video tidak tersedia untuk lagu ini. Coba pilih video lain.")
    }

    private fun findFirstObjectByKey(node: Any?, targetKey: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                val found = node.optJSONObject(targetKey)
                if (found != null) return found
                val keys = node.keys()
                while (keys.hasNext()) {
                    val res = findFirstObjectByKey(node.opt(keys.next()), targetKey)
                    if (res != null) return res
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val res = findFirstObjectByKey(node.opt(i), targetKey)
                    if (res != null) return res
                }
            }
        }
        return null
    }

    private fun findBrowseIdWithPrefix(node: Any?, prefix: String): String? {
        when (node) {
            is JSONObject -> {
                val bid = node.optString("browseId")
                if (bid.startsWith(prefix)) return bid
                val keys = node.keys()
                while (keys.hasNext()) {
                    val res = findBrowseIdWithPrefix(node.opt(keys.next()), prefix)
                    if (res != null) return res
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val res = findBrowseIdWithPrefix(node.opt(i), prefix)
                    if (res != null) return res
                }
            }
        }
        return null
    }

    private fun collectSuggestions(node: Any?, out: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("searchSuggestionRenderer")
                if (renderer != null) {
                    val text = extractRunsText(renderer.optJSONObject("suggestion"))
                    if (text.isNotBlank()) out.add(text)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectSuggestions(node.opt(keys.next()), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectSuggestions(node.opt(i), out)
                }
            }
        }
    }

    private fun walkPlaylistPanelRenderers(node: Any?, tracks: MutableList<Track>) {
        when (node) {
            is JSONObject -> {
                val item = node.optJSONObject("playlistPanelVideoRenderer")
                if (item != null && !item.has("unplayableText")) {
                    val videoId = item.optString("videoId").takeIf { it.isNotBlank() }
                    val title = extractRunsText(item.optJSONObject("title"))
                    val durText = extractRunsText(item.optJSONObject("lengthText"))
                    val parsedDur = parseDurationMs(durText)
                    if (!TrackGuard.isMetadataInvalid(videoId, title, parsedDur) && videoId != null) {
                        val byline = extractRunsText(item.optJSONObject("shortBylineText"))
                            .ifBlank { extractRunsText(item.optJSONObject("longBylineText")) }
                        val thumbs = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val art = thumbs?.let { arr ->
                            if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
                        }?.replace(Regex("""=w\d+-h\d+"""), "=w544-h544")
                        val meta = parseSubtitleMetadata(byline)
                        tracks.add(
                            Track(
                                id = "yt_$videoId",
                                title = title,
                                artist = meta.artist.ifBlank { byline.ifBlank { "YouTube Music" } },
                                album = meta.album.ifBlank { "Radio YouTube Music" },
                                artworkUrl = art ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                streamUrl = null,
                                durationMs = parsedDur ?: meta.durationMs,
                                genre = "YouTube Music"
                            )
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    walkPlaylistPanelRenderers(node.opt(keys.next()), tracks)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    walkPlaylistPanelRenderers(node.opt(i), tracks)
                }
            }
        }
    }

    private fun walkTwoRowRenderers(
        node: Any?,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<RemotePlaylistSummary>
    ) {
        when (node) {
            is JSONObject -> {
                val twoRow = node.optJSONObject("musicTwoRowItemRenderer")
                if (twoRow != null) {
                    parseTwoRowItem(twoRow, tracks, albums, artists, playlists)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    walkTwoRowRenderers(node.opt(keys.next()), tracks, albums, artists, playlists)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    walkTwoRowRenderers(node.opt(i), tracks, albums, artists, playlists)
                }
            }
        }
    }

    private fun parseTwoRowItem(
        item: JSONObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<RemotePlaylistSummary>
    ) {
        val title = extractRunsText(item.optJSONObject("title")).ifBlank { return }
        val subtitle = extractRunsText(item.optJSONObject("subtitle"))
        val art = extractBestThumbnail(item.optJSONObject("thumbnailRenderer"))
        val nav = item.optJSONObject("navigationEndpoint")
        val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
        val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }

        when {
            videoId != null -> {
                val meta = parseSubtitleMetadata(subtitle)
                if (!TrackGuard.isMetadataInvalid(videoId, title, meta.durationMs)) {
                    tracks.add(
                        Track(
                            id = "yt_$videoId",
                            title = title,
                            artist = meta.artist.ifBlank { "YouTube Music" },
                            album = meta.album.ifBlank { "YouTube Music" },
                            artworkUrl = art ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            streamUrl = null,
                            durationMs = meta.durationMs,
                            genre = "YouTube Music"
                        )
                    )
                }
            }
            browseId != null && browseId.startsWith("MPREb_") -> {
                val meta = parseSubtitleMetadata(subtitle)
                albums.add(
                    AlbumSummary(
                        id = "yt_album_$browseId",
                        title = title,
                        artist = meta.artist.ifBlank { "Album" },
                        artworkUrl = art,
                        genre = "YouTube Music",
                        releaseYear = meta.year
                    )
                )
            }
            browseId != null && browseId.startsWith("UC") -> {
                artists.add(
                    ArtistSummary(
                        id = "yt_artist_$browseId",
                        name = title,
                        artworkUrl = art,
                        genre = subtitle.ifBlank { "Artis" }
                    )
                )
            }
            browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD")) -> {
                playlists.add(
                    RemotePlaylistSummary(
                        id = browseId,
                        title = title,
                        author = subtitle.ifBlank { "YouTube Music" },
                        artworkUrl = art
                    )
                )
            }
        }
    }

    private fun walkRenderers(
        node: Any?,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<RemotePlaylistSummary>,
        fallbackAlbumTitle: String?,
        fallbackArt: String?
    ) {
        when (node) {
            is JSONObject -> {
                val listItem = node.optJSONObject("musicResponsiveListItemRenderer")
                if (listItem != null) {
                    parseListItem(listItem, tracks, albums, artists, playlists, fallbackAlbumTitle, fallbackArt)
                }
                val cardShelf = node.optJSONObject("musicCardShelfRenderer")
                if (cardShelf != null) {
                    parseCardShelfTopResult(cardShelf, tracks, albums, artists)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    walkRenderers(node.opt(keys.next()), tracks, albums, artists, playlists, fallbackAlbumTitle, fallbackArt)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    walkRenderers(node.opt(i), tracks, albums, artists, playlists, fallbackAlbumTitle, fallbackArt)
                }
            }
        }
    }

    private fun parseCardShelfTopResult(
        shelf: JSONObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>
    ) {
        val title = extractRunsText(shelf.optJSONObject("title")).ifBlank { return }
        val subtitle = extractRunsText(shelf.optJSONObject("subtitle"))
        val art = extractBestThumbnail(shelf.optJSONObject("thumbnail"))
        val nav = shelf.optJSONObject("onTap") ?: shelf.optJSONObject("title")
            ?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
        val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
        val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }

        if (videoId != null) {
            val parsedMeta = parseSubtitleMetadata(subtitle)
            if (!TrackGuard.isMetadataInvalid(videoId, title, parsedMeta.durationMs)) {
                tracks.add(
                    0,
                    Track(
                        id = "yt_$videoId",
                        title = title,
                        artist = parsedMeta.artist,
                        album = parsedMeta.album,
                        artworkUrl = art,
                        streamUrl = null,
                        durationMs = parsedMeta.durationMs,
                        genre = "YouTube Music"
                    )
                )
            }
        } else if (browseId != null) {
            if (browseId.startsWith("MPREb_")) {
                val parsedMeta = parseSubtitleMetadata(subtitle)
                albums.add(
                    0,
                    AlbumSummary(
                        id = "yt_album_$browseId",
                        title = title,
                        artist = parsedMeta.artist,
                        artworkUrl = art,
                        genre = "YouTube Music",
                        releaseYear = parsedMeta.year
                    )
                )
            } else if (browseId.startsWith("UC")) {
                artists.add(
                    0,
                    ArtistSummary(
                        id = "yt_artist_$browseId",
                        name = title,
                        artworkUrl = art,
                        genre = subtitle.ifBlank { "Artis" }
                    )
                )
            }
        }
    }

    private fun parseListItem(
        item: JSONObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<RemotePlaylistSummary>,
        fallbackAlbumTitle: String?,
        fallbackArt: String?
    ) {
        // Skip items explicitly marked grayed-out / unplayable by YouTube Music
        val displayPolicy = item.optString("musicItemRendererDisplayPolicy")
        if (displayPolicy.contains("GREY_OUT", ignoreCase = true) || item.has("unplayableText")) {
            return
        }

        val flexCols = item.optJSONArray("flexColumns") ?: return
        if (flexCols.length() == 0) return

        val col0 = extractFlexColumnText(flexCols.optJSONObject(0)).ifBlank { return }
        val col1Obj = if (flexCols.length() > 1) flexCols.optJSONObject(1) else null
        val col1 = extractFlexColumnText(col1Obj)

        // Extract linked artist/album browseIds from runs inside col1 if available
        var artistBrowseId: String? = null
        var albumBrowseId: String? = null
        val col1Runs = col1Obj?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
        if (col1Runs != null) {
            for (i in 0 until col1Runs.length()) {
                val bid = col1Runs.optJSONObject(i)
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    .orEmpty()
                if (bid.startsWith("UC") && artistBrowseId == null) artistBrowseId = bid
                if (bid.startsWith("MPREb_") && albumBrowseId == null) albumBrowseId = bid
            }
        }

        val fixedDuration = item.optJSONArray("fixedColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractRunsText(it) }
            ?.let { parseDurationMs(it) }

        val thumb = extractBestThumbnail(item.optJSONObject("thumbnail")) ?: fallbackArt
        val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }

        val browseId = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() }

        val isExplicit = item.optJSONArray("badges")?.toString()?.contains("EXPLICIT") == true

        if (videoId != null) {
            val meta = parseSubtitleMetadata(col1)
            val durationMs = fixedDuration ?: meta.durationMs
            if (TrackGuard.isMetadataInvalid(videoId, col0, durationMs)) return
            tracks.add(
                Track(
                    id = "yt_$videoId",
                    title = col0,
                    artist = meta.artist.ifBlank { "YouTube Music" },
                    album = meta.album.ifBlank { fallbackAlbumTitle ?: "YouTube Music" },
                    artworkUrl = thumb ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    streamUrl = null,
                    durationMs = durationMs,
                    genre = "YouTube Music",
                    isLocal = false,
                    isExplicit = isExplicit,
                    releaseYear = meta.year,
                    artistBrowseId = artistBrowseId,
                    albumBrowseId = albumBrowseId
                )
            )
        } else if (browseId != null) {
            when {
                browseId.startsWith("MPREb_") -> {
                    val meta = parseSubtitleMetadata(col1)
                    albums.add(
                        AlbumSummary(
                            id = "yt_album_$browseId",
                            title = col0,
                            artist = meta.artist.ifBlank { "Album" },
                            artworkUrl = thumb,
                            genre = "YouTube Music",
                            releaseYear = meta.year
                        )
                    )
                }
                browseId.startsWith("UC") -> {
                    artists.add(
                        ArtistSummary(
                            id = "yt_artist_$browseId",
                            name = col0,
                            artworkUrl = thumb,
                            genre = col1.ifBlank { "Artis" }
                        )
                    )
                }
                browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD") -> {
                    playlists.add(
                        RemotePlaylistSummary(
                            id = browseId,
                            title = col0,
                            author = col1.ifBlank { "Playlist Komunitas" },
                            artworkUrl = thumb
                        )
                    )
                }
            }
        }
    }

    private data class ParsedSubtitle(
        val artist: String,
        val album: String,
        val durationMs: Long,
        val year: String
    )

    private fun parseSubtitleMetadata(subtitle: String): ParsedSubtitle {
        val parts = subtitle.split("•")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var durationMs = 210_000L
        var year = ""
        val typePrefixes = setOf("lagu", "song", "video", "single", "album", "ep", "artis", "artist")
        val cleaned = mutableListOf<String>()

        for (part in parts) {
            val lower = part.lowercase()
            val parsedDur = parseDurationMs(part)
            when {
                parsedDur != null -> durationMs = parsedDur
                lower in typePrefixes -> Unit
                Regex("""^\d{4}$""").matches(part) -> year = part
                lower.contains("ditonton") || lower.contains("views") || lower.contains("pemutaran") || lower.contains("plays") || lower.contains("audiens") -> Unit
                else -> cleaned.add(part)
            }
        }

        val artist = cleaned.getOrNull(0) ?: "Unknown Artist"
        val album = cleaned.getOrNull(1) ?: ""
        return ParsedSubtitle(artist, album, durationMs, year)
    }

    private fun parseDurationMs(raw: String): Long? {
        val normalized = raw.trim().replace('.', ':')
        val match = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(normalized) ?: return null
        val p1 = match.groupValues[1].toLongOrNull() ?: return null
        val p2 = match.groupValues[2].toLongOrNull() ?: return null
        val p3 = match.groupValues[3].toLongOrNull()
        val totalSec = if (p3 != null) (p1 * 3600 + p2 * 60 + p3) else (p1 * 60 + p2)
        return totalSec * 1000L
    }

    private fun extractFlexColumnText(flexCol: JSONObject?): String {
        val textObj = flexCol?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        return extractRunsText(textObj)
    }

    private fun extractRunsText(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun extractBestThumbnail(thumbObj: JSONObject?): String? {
        val thumbs = thumbObj
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: return null
        if (thumbs.length() == 0) return null
        val rawUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
            ?: return null
        return rawUrl.replace(Regex("""=w\d+-h\d+"""), "=w544-h544")
    }
}
