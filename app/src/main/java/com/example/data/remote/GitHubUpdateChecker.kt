package com.example.data.remote

import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val isNewerThanCurrent: Boolean
)

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val repoSlug: String = GitHubUpdateChecker.DEFAULT_GITHUB_REPO,
    val autoCheckEnabled: Boolean = true,
    val latestRelease: GitHubReleaseInfo? = null,
    val hasUpdateAvailable: Boolean = false,
    val lastCheckedAtMillis: Long? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val showUpdateDialog: Boolean = false,
    val showShareApkDialog: Boolean = false,
    val isResolvingShareLink: Boolean = false,
    val showChangelogDialog: Boolean = false,
    val isAutoShownChangelog: Boolean = false,
    val releaseHistory: List<GitHubReleaseInfo> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null
)

sealed interface UpdateCheckResult {
    data class Success(val release: GitHubReleaseInfo) : UpdateCheckResult
    data class NoReleasesYet(val repoSlug: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

enum class ChangelogCategory(val label: String) {
    ALL("Semua"),
    NEW("Baru"),
    IMPROVEMENT("Peningkatan"),
    FIX("Perbaikan"),
    UI("UI & Visual")
}

data class LocalChangelogItem(
    val title: String,
    val summary: String,
    val details: String,
    val category: ChangelogCategory
)

/**
 * Checks GitHub Releases via the official HTTPS GitHub REST API and validates
 * download/release links strictly against trusted GitHub hosts before opening
 * in the system browser / download handler.
 */
object GitHubUpdateChecker {
    private const val TAG = "GitHubUpdateChecker"
    const val DEFAULT_GITHUB_REPO = "budikopling10/Koplingfy"
    const val OWNER_TIKTOK_HANDLE = "@budikopling10"
    const val OWNER_TIKTOK_URL = "https://www.tiktok.com/@budikopling10"

    private val repoSegmentRegex = Regex("^[A-Za-z0-9_.-]{1,100}$")
    private val trustedExternalHosts = setOf(
        "github.com",
        "www.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "tiktok.com",
        "www.tiktok.com",
        "vm.tiktok.com"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Accepts either "owner/repo" or a full "https://github.com/owner/repo" URL
     * and returns a strictly validated "owner/repo" slug, or null if invalid.
     */
    fun normalizeAndValidateRepo(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val candidatePath = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val host = uri.host?.lowercase().orEmpty()
            if (host != "github.com" && host != "www.github.com") return null
            uri.path?.trim('/').orEmpty()
        } else {
            trimmed.removePrefix("github.com/").removePrefix("www.github.com/").trim('/')
        }

        val parts = candidatePath.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (owner == "." || owner == ".." || repo == "." || repo == "..") return null
        if (!repoSegmentRegex.matches(owner) || !repoSegmentRegex.matches(repo)) return null
        return "$owner/$repo"
    }

    fun githubRepoUrl(repoSlug: String): String {
        val valid = normalizeAndValidateRepo(repoSlug) ?: DEFAULT_GITHUB_REPO
        return "https://github.com/$valid"
    }

    /**
     * Resolves the best link for sharing Koplingfy APK:
     * - Returns `(directApkUrl, true)` when the latest GitHub Release has an attached `.apk` asset
     *   (clicking or scanning the QR immediately triggers APK download!).
     * - Falls back to `(https://github.com/{owner}/{repo}/releases/latest, false)` if no `.apk` asset exists yet.
     */
    fun shareableApkDownloadLink(
        repoSlug: String,
        latestRelease: GitHubReleaseInfo?
    ): Pair<String, Boolean> {
        val directApk = latestRelease?.apkDownloadUrl?.takeIf { isTrustedExternalUrl(it) }
        if (directApk != null) return directApk to true
        val valid = normalizeAndValidateRepo(repoSlug) ?: DEFAULT_GITHUB_REPO
        return "https://github.com/$valid/releases/latest" to false
    }

    fun isTrustedExternalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host in trustedExternalHosts || host.endsWith(".githubusercontent.com")
    }

    suspend fun fetchLatestRelease(
        repoSlug: String,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val validSlug = normalizeAndValidateRepo(repoSlug)
            ?: return@withContext UpdateCheckResult.Error(
                "Format repositori GitHub tidak valid. Gunakan format username/namarepo."
            )

        try {
            // 1. Try official /releases/latest first
            val latestUrl = "https://api.github.com/repos/$validSlug/releases/latest"
            val latestReq = buildGitHubRequest(latestUrl)
            httpClient.newCall(latestReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                when (resp.code) {
                    in 200..299 -> {
                        val parsed = parseReleaseJson(JSONObject(bodyStr), validSlug, currentVersionName)
                        return@withContext UpdateCheckResult.Success(parsed)
                    }
                    404 -> {
                        // Fall through to check /releases?per_page=1 in case only pre-releases exist
                    }
                    403 -> {
                        return@withContext UpdateCheckResult.Error(
                            "Batas permintaan GitHub API tercapai sementara. Coba lagi beberapa menit lagi."
                        )
                    }
                    else -> {
                        return@withContext UpdateCheckResult.Error(
                            "Server GitHub merespons kode ${resp.code}. Periksa kembali nama repositori."
                        )
                    }
                }
            }

            // 2. Fallback: check /releases?per_page=1 (covers pre-releases)
            val listUrl = "https://api.github.com/repos/$validSlug/releases?per_page=1"
            httpClient.newCall(buildGitHubRequest(listUrl)).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                when (resp.code) {
                    in 200..299 -> {
                        val arr = JSONArray(bodyStr)
                        if (arr.length() == 0) {
                            return@withContext UpdateCheckResult.NoReleasesYet(validSlug)
                        }
                        val parsed = parseReleaseJson(arr.getJSONObject(0), validSlug, currentVersionName)
                        return@withContext UpdateCheckResult.Success(parsed)
                    }
                    404 -> {
                        return@withContext UpdateCheckResult.NoReleasesYet(validSlug)
                    }
                    else -> {
                        return@withContext UpdateCheckResult.Error(
                            "Tidak dapat memeriksa rilis GitHub (kode ${resp.code})."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub update check failed for $validSlug: ${e.message}")
            UpdateCheckResult.Error(
                "Gagal terhubung ke GitHub. Periksa koneksi internet Anda lalu coba lagi."
            )
        }
    }

    /**
     * Fetches up to [limit] published GitHub Releases for the interactive Changelog history tab.
     */
    suspend fun fetchReleaseHistory(
        repoSlug: String,
        limit: Int = 8,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): Result<List<GitHubReleaseInfo>> = withContext(Dispatchers.IO) {
        val validSlug = normalizeAndValidateRepo(repoSlug)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Format repositori GitHub tidak valid.")
            )
        try {
            val safeLimit = limit.coerceIn(1, 15)
            val url = "https://api.github.com/repos/$validSlug/releases?per_page=$safeLimit"
            httpClient.newCall(buildGitHubRequest(url)).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                when (resp.code) {
                    in 200..299 -> {
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<GitHubReleaseInfo>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            if (obj.optBoolean("draft", false)) continue
                            list.add(parseReleaseJson(obj, validSlug, currentVersionName))
                        }
                        Result.success(list)
                    }
                    404 -> Result.success(emptyList())
                    403 -> Result.failure(Exception("Batas permintaan GitHub API tercapai sementara. Coba lagi nanti."))
                    else -> Result.failure(Exception("Server GitHub merespons kode ${resp.code}."))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub release history fetch failed for $validSlug: ${e.message}")
            Result.failure(Exception("Gagal memuat riwayat rilis GitHub. Periksa koneksi internet Anda."))
        }
    }

    /**
     * Built-in structured changelog highlights for the installed version of Koplingfy.
     */
    val installedVersionHighlights: List<LocalChangelogItem> = listOf(
        LocalChangelogItem(
            title = "Bagikan Link Download APK Langsung + QR Code",
            summary = "Kirim link yang langsung mengunduh file APK Koplingfy dari GitHub lengkap dengan QR Code.",
            details = "Cukup scan QR Code dengan kamera HP lain atau salin/bagikan tautannya via WhatsApp. Saat diklik, tautan otomatis mengarahkan ke aset APK rilis terbaru dari repositori GitHub Anda.",
            category = ChangelogCategory.NEW
        ),
        LocalChangelogItem(
            title = "Cek Update Otomatis & Manual dari GitHub",
            summary = "Deteksi versi terbaru otomatis saat membuka aplikasi serta tombol cek manual di Pengaturan.",
            details = "Anda bisa mengatur nama repositori GitHub sendiri di Pengaturan. Begitu rilis baru terdeteksi, dialog pembaruan tampil dengan catatan rilis dan tombol unduh APK 1-ketuk.",
            category = ChangelogCategory.NEW
        ),
        LocalChangelogItem(
            title = "Tentang Koplingfy & Profil TikTok Owner",
            summary = "Kartu Tentang baru lengkap dengan tautan langsung TikTok @budikopling10 dan GitHub.",
            details = "Menampilkan informasi versi & build aktif, pintasan profil TikTok Owner tiktok.com/@budikopling10, repositori GitHub, dan tombol berbagi aplikasi.",
            category = ChangelogCategory.UI
        ),
        LocalChangelogItem(
            title = "Efek Partikel Adaptif di Latar Now Playing",
            summary = "Latar pemutar hidup 60fps yang menyesuaikan suasana lagu secara otomatis.",
            details = "6 tema atmosfer (Bara Api, Hujan Melankolis, Kelopak Romantis, Gelombang Neon, Aurora Kosmik, Bokeh Sinematik) yang beradaptasi dengan judul, genre, mood AI, dan palet sampul album.",
            category = ChangelogCategory.UI
        ),
        LocalChangelogItem(
            title = "Anti Lagu Duplikat & Auto-Hapus Lagu Gagal Putar",
            summary = "Mencegah duplikat di antrean/playlist dan menyingkirkan lagu rusak secara otomatis.",
            details = "Menyaring lagu tidak tersedia sejak pencarian, mencari upload alternatif yang valid, dan bila tetap tidak bisa diputar otomatis diblokir lokal & lanjut ke lagu berikutnya.",
            category = ChangelogCategory.IMPROVEMENT
        ),
        LocalChangelogItem(
            title = "Translate Indonesia & Romaji Super Cepat",
            summary = "Terjemahan & romanisasi streaming baris-per-baris langsung tampil tanpa selisih baris.",
            details = "Mendukung romanisasi otomatis untuk lagu Jepang/Korea/Mandarin dengan fallback fonetik lokal Hangul & Kana serta fallback model otomatis jika koneksi melambat.",
            category = ChangelogCategory.FIX
        ),
        LocalChangelogItem(
            title = "Sinkronisasi Play Bersama Anti Delay",
            summary = "Kalibrasi jam server ala NTP dan koreksi drift halus antar HP.",
            details = "Aktivitas chat & reaksi tidak lagi menggeser posisi lagu, ditambah sinkronisasi otomatis saat buffering selesai dan prefetch lagu berikutnya agar transisi serempak.",
            category = ChangelogCategory.FIX
        ),
        LocalChangelogItem(
            title = "Mode Meja Lirik Penuh & Dynamic Island Global",
            summary = "Tampilan lirik ekstra jelas di Mode Meja serta Dynamic Island di atas semua aplikasi.",
            details = "Dynamic Island lirik kini dapat mengambang di atas aplikasi lain tanpa mengganggu sentuhan layar, lengkap dengan lirik berjalan di notifikasi bar.",
            category = ChangelogCategory.IMPROVEMENT
        )
    )

    private fun buildGitHubRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Koplingfy-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
    }

    private fun parseReleaseJson(
        obj: JSONObject,
        validSlug: String,
        currentVersionName: String
    ): GitHubReleaseInfo {
        val tagName = obj.optString("tag_name").trim().ifBlank { "1.0" }
        val cleanVersion = normalizeVersionTag(tagName)
        val releaseTitle = obj.optString("name").trim().ifBlank { "Koplingfy $tagName" }
        val rawBody = obj.optString("body").trim()
        val changelog = rawBody.ifBlank {
            "Pembaruan performa, perbaikan bug, dan peningkatan stabilitas Koplingfy."
        }
        val rawHtmlUrl = obj.optString("html_url").trim()
        val safeReleaseUrl = if (isTrustedExternalUrl(rawHtmlUrl)) {
            rawHtmlUrl
        } else {
            "https://github.com/$validSlug/releases"
        }
        val publishedAt = obj.optString("published_at").trim()

        val assets = obj.optJSONArray("assets")
        var bestApkUrl: String? = null
        var bestApkName: String? = null
        var bestApkSize = 0L
        var bestScore = -1

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name").trim()
                val downloadUrl = asset.optString("browser_download_url").trim()
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (!isTrustedExternalUrl(downloadUrl)) continue

                val lower = name.lowercase()
                val score = when {
                    lower.contains("universal") || lower.contains("release") -> 30
                    lower.contains("arm64") -> 20
                    else -> 10
                }
                if (score > bestScore) {
                    bestScore = score
                    bestApkUrl = downloadUrl
                    bestApkName = name
                    bestApkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                }
            }
        }

        val isNewer = isRemoteVersionNewer(cleanVersion, currentVersionName)
        return GitHubReleaseInfo(
            tagName = tagName,
            versionName = cleanVersion,
            releaseTitle = releaseTitle,
            changelog = changelog,
            releasePageUrl = safeReleaseUrl,
            apkDownloadUrl = bestApkUrl,
            apkFileName = bestApkName,
            apkSizeBytes = bestApkSize,
            publishedAt = publishedAt,
            isNewerThanCurrent = isNewer
        )
    }

    fun normalizeVersionTag(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
            .trim()
            .ifBlank { "0.0" }
    }

    /**
     * Compares dotted semantic version strings (e.g., "1.2.0" > "1.0", "2.0" > "1.9.9").
     */
    fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteCore = normalizeVersionTag(remoteVersion).substringBefore("-")
        val currentCore = normalizeVersionTag(currentVersion).substringBefore("-")
        val remoteParts = remoteCore.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = currentCore.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
