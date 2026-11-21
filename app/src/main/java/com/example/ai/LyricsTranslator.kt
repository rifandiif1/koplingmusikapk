package com.example.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "LyricsTranslator"
private const val CACHE_DIR_NAME = "lyrics_ai_cache"
private const val TRANSLATION_STYLE_VERSION = 6
private const val ROMANIZATION_STYLE_VERSION = 3
private const val MAX_LINES_PER_CHUNK = 18
private const val MAX_CHARS_PER_CHUNK = 950
private const val MAX_PARALLEL_REQUESTS = 3
private const val MAX_PARALLEL_LINE_FALLBACK = 5
private const val REQUEST_TIMEOUT_MS = 25_000L

data class TranslatedLyricsResult(
    val lines: List<String>,
    val detectedIndonesian: Boolean = false
)

data class RomanizedLyricsResult(
    val lines: List<String>,
    val needsRomanization: Boolean = true,
    val scriptLabel: String = ""
)

data class CachedLyricsAiData(
    val lineCount: Int = 0,
    val translatedLines: List<String> = emptyList(),
    val detectedIndonesian: Boolean = false,
    val translationStyleVersion: Int = 0,
    val romanizedLines: List<String> = emptyList(),
    val needsRomanization: Boolean = true,
    val scriptLabel: String = "",
    val romanizationStyleVersion: Int = 0
)

private data class DeduplicatedLines(
    val uniqueLines: List<String>,
    val originalToUniqueIndex: IntArray
)

private data class GoogleTranslateResponse(
    val translatedText: String,
    val sourceTranslit: String,
    val detectedLang: String
)

private data class TranslateEndpoint(
    val baseUrl: String,
    val clientParam: String,
    val usePost: Boolean
)

/**
 * Translates song lyrics line-by-line into Indonesian and generates phonetic romanization
 * (Romaji with Kanji readings, Korean Revised Romanization, Pinyin, Cyrillic, Thai, Arabic, etc.)
 * directly via Google Translate (`translate.googleapis.com/translate_a/single`) — requiring
 * zero API key, zero App Check attestation, and zero user setup.
 *
 * Reliability & speed features:
 * - Deduplicates repeated chorus lines (~50% fewer lines sent).
 * - Parallel chunked requests with progressive UI emission (`onPartialResult`).
 * - Exact 1-to-1 line alignment with automatic per-line recovery if any chunk merges newlines.
 * - Automatic multi-endpoint fallback (POST/GET, `gtx`/`tw-ob`, `.googleapis.com`/`.google.com`).
 * - Persistent on-device JSON disk cache + deterministic Kana/Hangul offline safety net.
 */
object LyricsTranslator {
    private val memoryCache = ConcurrentHashMap<String, CachedLyricsAiData>()
    private val cacheLock = Any()

    @Volatile
    private var cacheDir: File? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val translateEndpoints = listOf(
        TranslateEndpoint(
            baseUrl = "https://translate.googleapis.com/translate_a/single",
            clientParam = "gtx",
            usePost = true
        ),
        TranslateEndpoint(
            baseUrl = "https://translate.googleapis.com/translate_a/single",
            clientParam = "gtx",
            usePost = false
        ),
        TranslateEndpoint(
            baseUrl = "https://translate.google.com/translate_a/single",
            clientParam = "gtx",
            usePost = false
        ),
        TranslateEndpoint(
            baseUrl = "https://translate.googleapis.com/translate_a/single",
            clientParam = "tw-ob",
            usePost = false
        )
    )

    private val indonesianStopWords = setOf(
        "aku", "kamu", "kau", "engkau", "kita", "kami", "mereka", "dia", "ku", "mu",
        "yang", "dan", "di", "ke", "dari", "ini", "itu", "untuk", "dengan", "pada",
        "tidak", "tak", "nggak", "gak", "bukan", "jangan", "belum", "sudah", "udah", "masih",
        "akan", "telah", "sedang", "lagi", "selalu", "pernah", "sampai", "hingga", "saat", "ketika",
        "tapi", "tetapi", "namun", "kalau", "jika", "bila", "karena", "sebab", "agar", "biar",
        "hanya", "cuma", "saja", "semua", "setiap", "ada", "tiada", "bisa", "dapat", "mau", "ingin",
        "hati", "cinta", "rindu", "rasa", "jiwa", "malam", "hari", "waktu", "dunia", "hidup",
        "pergi", "pulang", "datang", "tinggal", "hilang", "lupa", "ingat", "tahu", "mengerti",
        "sendiri", "bersama", "dirimu", "diriku", "padamu", "untukmu", "bersamamu", "tentang", "dalam"
    )

    fun init(context: Context) {
        if (cacheDir != null) return
        val dir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        cacheDir = dir
    }

    fun getCacheSizeBytes(): Long {
        val dir = cacheDir ?: return 0L
        return runCatching {
            dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
        }.getOrDefault(0L)
    }

    fun clearLocalCache(): Long {
        val freed = getCacheSizeBytes()
        synchronized(cacheLock) {
            memoryCache.clear()
            SongMeaningAnalyzer.clearMemoryCache()
            val dir = cacheDir
            if (dir != null && dir.exists()) {
                runCatching {
                    dir.listFiles()?.forEach { it.delete() }
                }
            }
        }
        return freed
    }

    private fun cacheFileFor(trackId: String): File? {
        val dir = cacheDir ?: return null
        val safeKey = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
        return File(dir, "$safeKey.json")
    }

    fun getCachedEntry(trackId: String): CachedLyricsAiData? = synchronized(cacheLock) {
        memoryCache[trackId]?.let { return it }
        val file = cacheFileFor(trackId) ?: return null
        if (!file.exists()) return null
        return runCatching {
            val obj = JSONObject(file.readText())
            val transArr = obj.optJSONArray("translatedLines")
            val transList = if (transArr != null) {
                List(transArr.length()) { idx -> transArr.optString(idx, "") }
            } else emptyList()

            val romArr = obj.optJSONArray("romanizedLines")
            val romList = if (romArr != null) {
                List(romArr.length()) { idx -> romArr.optString(idx, "") }
            } else emptyList()

            val styleVersion = obj.optInt("translationStyleVersion", 1)
            val romVersion = obj.optInt("romanizationStyleVersion", 1)
            val validTransList = if (styleVersion >= 3 && transList.any { it.isNotBlank() }) transList else emptyList()
            val validRomList = if (romVersion >= 2 && romList.any { it.isNotBlank() }) romList else emptyList()

            CachedLyricsAiData(
                lineCount = obj.optInt("lineCount", maxOf(validTransList.size, validRomList.size)),
                translatedLines = validTransList,
                detectedIndonesian = obj.optBoolean("detectedIndonesian", false),
                translationStyleVersion = styleVersion,
                romanizedLines = validRomList,
                needsRomanization = validRomList.any { it.isNotBlank() },
                scriptLabel = obj.optString("scriptLabel", ""),
                romanizationStyleVersion = romVersion
            ).also {
                memoryCache[trackId] = it
            }
        }.getOrNull()
    }

    private fun mergeAndSaveCache(
        trackId: String,
        lineCount: Int,
        transform: (CachedLyricsAiData) -> CachedLyricsAiData
    ) = synchronized(cacheLock) {
        val base = memoryCache[trackId] ?: getCachedEntry(trackId) ?: CachedLyricsAiData(lineCount = lineCount)
        val entry = transform(base).copy(lineCount = lineCount)
        memoryCache[trackId] = entry
        val file = cacheFileFor(trackId) ?: return@synchronized
        runCatching {
            file.parentFile?.mkdirs()
            val obj = JSONObject().apply {
                put("lineCount", entry.lineCount)
                put("detectedIndonesian", entry.detectedIndonesian)
                put("translationStyleVersion", entry.translationStyleVersion)
                put("translatedLines", JSONArray(entry.translatedLines))
                put("needsRomanization", entry.needsRomanization)
                put("scriptLabel", entry.scriptLabel)
                put("romanizationStyleVersion", entry.romanizationStyleVersion)
                put("romanizedLines", JSONArray(entry.romanizedLines))
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(obj.toString())
                tmp.delete()
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to persist local lyrics cache for $trackId", err)
        }
    }

    private fun deduplicateLines(lines: List<String>, nonLatinOnly: Boolean = false): DeduplicatedLines {
        val uniqueLines = mutableListOf<String>()
        val keyToUniqueIdx = LinkedHashMap<String, Int>()
        val mapping = IntArray(lines.size) { -1 }

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty() || isNonLyricMarker(trimmed)) {
                mapping[i] = -1
                continue
            }
            if (nonLatinOnly && !lineHasNonLatinScript(trimmed)) {
                mapping[i] = -1
                continue
            }
            val normKey = trimmed.lowercase().replace(Regex("\\s+"), " ")
            val existingIdx = keyToUniqueIdx[normKey]
            if (existingIdx != null) {
                mapping[i] = existingIdx
            } else {
                val newIdx = uniqueLines.size
                uniqueLines.add(trimmed)
                keyToUniqueIdx[normKey] = newIdx
                mapping[i] = newIdx
            }
        }
        return DeduplicatedLines(uniqueLines = uniqueLines, originalToUniqueIndex = mapping)
    }

    private fun isNonLyricMarker(line: String): Boolean {
        val stripped = line.replace(Regex("[♪♫🎵🎶•·.\\-—_()\\[\\]\\s]"), "")
        return stripped.isEmpty()
    }

    private fun expandUniqueResults(
        originalSize: Int,
        mapping: IntArray,
        uniqueTranslated: Array<String>
    ): List<String> {
        return List(originalSize) { idx ->
            val uIdx = mapping[idx]
            if (uIdx in uniqueTranslated.indices) uniqueTranslated[uIdx] else ""
        }
    }

    fun isLikelyAlreadyIndonesian(lines: List<String>): Boolean {
        if (lines.isEmpty() || containsNonLatinScript(lines)) return false
        val tokens = lines
            .take(50)
            .flatMap { it.lowercase().split(Regex("[^a-z]+")) }
            .filter { it.length >= 2 }
        if (tokens.size < 8) return false
        val idMatches = tokens.count { it in indonesianStopWords }
        return (idMatches.toFloat() / tokens.size.toFloat()) >= 0.24f
    }

    private fun lineHasNonLatinScript(line: String): Boolean {
        return line.any { ch ->
            val block = Character.UnicodeBlock.of(ch)
            block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
                block == Character.UnicodeBlock.CYRILLIC ||
                block == Character.UnicodeBlock.THAI ||
                block == Character.UnicodeBlock.ARABIC ||
                block == Character.UnicodeBlock.DEVANAGARI ||
                block == Character.UnicodeBlock.GREEK ||
                block == Character.UnicodeBlock.HEBREW
        }
    }

    fun containsNonLatinScript(lines: List<String>): Boolean = lines.any { lineHasNonLatinScript(it) }

    fun detectScriptLabel(lines: List<String>): String {
        var jpCount = 0
        var krCount = 0
        var zhCount = 0
        var ruCount = 0
        var thCount = 0
        var arCount = 0
        for (line in lines) {
            for (ch in line) {
                when (Character.UnicodeBlock.of(ch)) {
                    Character.UnicodeBlock.HIRAGANA,
                    Character.UnicodeBlock.KATAKANA,
                    Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS -> jpCount += 2
                    Character.UnicodeBlock.HANGUL_SYLLABLES,
                    Character.UnicodeBlock.HANGUL_JAMO,
                    Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> krCount += 2
                    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                    Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A -> zhCount += 1
                    Character.UnicodeBlock.CYRILLIC -> ruCount += 2
                    Character.UnicodeBlock.THAI -> thCount += 2
                    Character.UnicodeBlock.ARABIC -> arCount += 2
                    else -> Unit
                }
            }
        }
        return when {
            jpCount > 0 -> "Romaji"
            krCount > 0 -> "Hangul"
            zhCount > 0 -> "Pinyin"
            thCount > 0 -> "Thai"
            ruCount > 0 -> "Kiril"
            arCount > 0 -> "Arab"
            else -> "Fonetik"
        }
    }

    /**
     * Groups unique lines into compact chunks respecting both line count and character count limits
     * so every Google Translate HTTP call stays fast and safe from URL/payload limits.
     */
    private fun buildChunks(uniqueLines: List<String>): List<Pair<Int, List<String>>> {
        val chunks = mutableListOf<Pair<Int, List<String>>>()
        var currentStart = 0
        val currentList = mutableListOf<String>()
        var currentChars = 0

        for (i in uniqueLines.indices) {
            val line = uniqueLines[i]
            val lineCost = line.length + 2
            if (currentList.isNotEmpty() &&
                (currentList.size >= MAX_LINES_PER_CHUNK || currentChars + lineCost > MAX_CHARS_PER_CHUNK)
            ) {
                chunks.add(currentStart to currentList.toList())
                currentList.clear()
                currentStart = i
                currentChars = 0
            }
            currentList.add(line)
            currentChars += lineCost
        }
        if (currentList.isNotEmpty()) {
            chunks.add(currentStart to currentList.toList())
        }
        return chunks
    }

    private fun parseGoogleTranslatePayload(rawBody: String): GoogleTranslateResponse {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) {
            throw IOException("Respon kosong dari Google Translate.")
        }

        // Preferred structured `dj=1` JSON object response
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val sentences = root.optJSONArray("sentences")
            val transBuilder = StringBuilder()
            val translitBuilder = StringBuilder()
            if (sentences != null) {
                for (i in 0 until sentences.length()) {
                    val item = sentences.optJSONObject(i) ?: continue
                    if (item.has("trans") && !item.isNull("trans")) {
                        transBuilder.append(item.optString("trans", ""))
                    }
                    if (item.has("src_translit") && !item.isNull("src_translit")) {
                        translitBuilder.append(item.optString("src_translit", ""))
                    }
                }
            }
            val detectedLang = root.optString("src", "").trim().lowercase()
            return GoogleTranslateResponse(
                translatedText = transBuilder.toString(),
                sourceTranslit = translitBuilder.toString(),
                detectedLang = detectedLang
            )
        }

        // Classic nested JSON array response fallback
        if (trimmed.startsWith("[")) {
            val root = JSONArray(trimmed)
            val blocks = root.optJSONArray(0)
            val transBuilder = StringBuilder()
            val translitBuilder = StringBuilder()
            if (blocks != null) {
                for (i in 0 until blocks.length()) {
                    val seg = blocks.optJSONArray(i) ?: continue
                    val transPiece = if (!seg.isNull(0)) seg.optString(0, "") else ""
                    if (transPiece.isNotEmpty()) transBuilder.append(transPiece)
                    val translitPiece = if (seg.length() > 3 && !seg.isNull(3)) seg.optString(3, "") else ""
                    if (translitPiece.isNotEmpty()) translitBuilder.append(translitPiece)
                }
            }
            val detectedLang = if (root.length() > 2 && !root.isNull(2)) {
                root.optString(2, "").trim().lowercase()
            } else ""
            return GoogleTranslateResponse(
                translatedText = transBuilder.toString(),
                sourceTranslit = translitBuilder.toString(),
                detectedLang = detectedLang
            )
        }

        throw IOException("Format respon Google Translate tidak dikenali.")
    }

    private suspend fun callGoogleTranslate(
        text: String,
        targetLang: String = "id",
        sourceLang: String = "auto",
        includeRomanization: Boolean = false
    ): GoogleTranslateResponse {
        var lastError: Exception? = null
        val userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        for ((idx, endpoint) in translateEndpoints.withIndex()) {
            // Skip GET endpoints if payload is very large (> 1600 encoded chars risk HTTP 414)
            if (!endpoint.usePost && text.length > 1200) continue
            try {
                if (idx > 0) delay(150L)
                val urlBuilder = endpoint.baseUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("client", endpoint.clientParam)
                    .addQueryParameter("sl", sourceLang)
                    .addQueryParameter("tl", targetLang)
                    .addQueryParameter("dt", "t")
                    .addQueryParameter("dj", "1")
                    .addQueryParameter("ie", "UTF-8")
                    .addQueryParameter("oe", "UTF-8")

                if (includeRomanization) {
                    urlBuilder.addQueryParameter("dt", "rm")
                }

                val request = if (endpoint.usePost) {
                    val formBody = FormBody.Builder(Charsets.UTF_8)
                        .add("q", text)
                        .build()
                    Request.Builder()
                        .url(urlBuilder.build())
                        .post(formBody)
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json, text/plain, */*")
                        .build()
                } else {
                    urlBuilder.addQueryParameter("q", text)
                    Request.Builder()
                        .url(urlBuilder.build())
                        .get()
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json, text/plain, */*")
                        .build()
                }

                val body = withTimeout(REQUEST_TIMEOUT_MS) {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code} dari Google Translate")
                        }
                        response.body?.string().orEmpty()
                    }
                }
                val parsed = parseGoogleTranslatePayload(body)
                if (parsed.translatedText.isNotBlank() || parsed.sourceTranslit.isNotBlank()) {
                    return parsed
                }
                throw IOException("Respon Google Translate kosong.")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    lastError = e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Translate endpoint #${idx + 1} failed: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IOException("Gagal terhubung ke Google Translate.")
    }

    private fun cleanTranslatedLine(raw: String): String {
        return raw
            .replace("\r", "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun splitPreservingLines(raw: String, expectedCount: Int): List<String>? {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n").trim('\n')
        val split = normalized.split("\n").map { cleanTranslatedLine(it) }
        if (split.size == expectedCount) return split
        val nonEmpty = split.filter { it.isNotEmpty() }
        if (nonEmpty.size == expectedCount) return nonEmpty
        return null
    }

    /**
     * Translates a chunk via Google Translate with guaranteed 1-to-1 line alignment.
     * If Google Translate ever merges or splits newlines in a batch, automatically recovers
     * line-by-line in parallel so timestamps never drift.
     */
    private suspend fun translateChunkGoogle(
        chunk: List<String>,
        includeRomanization: Boolean
    ): Triple<Array<String>, Array<String>?, Boolean> {
        val joined = chunk.joinToString("\n")
        val response = callGoogleTranslate(
            text = joined,
            targetLang = "id",
            sourceLang = "auto",
            includeRomanization = includeRomanization
        )
        val isIndonesian = response.detectedLang == "id" || response.detectedLang == "in"
        val alignedTrans = splitPreservingLines(response.translatedText, chunk.size)
        val alignedRom = if (includeRomanization && response.sourceTranslit.isNotBlank()) {
            splitPreservingLines(response.sourceTranslit, chunk.size)?.toTypedArray()
        } else null

        if (alignedTrans != null) {
            return Triple(alignedTrans.toTypedArray(), alignedRom, isIndonesian)
        }

        // Exact line-by-line recovery if Google Translate altered newline count
        Log.d(TAG, "Chunk newline count mismatch (expected ${chunk.size}); falling back to exact per-line translation.")
        val recovered = Array(chunk.size) { "" }
        val recoveredRom = if (includeRomanization) Array(chunk.size) { "" } else null
        var idCount = 0
        val lineSem = Semaphore(MAX_PARALLEL_LINE_FALLBACK)
        val countLock = Mutex()

        coroutineScope {
            chunk.indices.map { idx ->
                async {
                    lineSem.withPermit {
                        val single = callGoogleTranslate(
                            text = chunk[idx],
                            targetLang = "id",
                            sourceLang = "auto",
                            includeRomanization = includeRomanization
                        )
                        recovered[idx] = cleanTranslatedLine(single.translatedText.replace("\n", " "))
                        if (recoveredRom != null && single.sourceTranslit.isNotBlank()) {
                            recoveredRom[idx] = cleanTranslatedLine(single.sourceTranslit.replace("\n", " "))
                        }
                        if (single.detectedLang == "id" || single.detectedLang == "in") {
                            countLock.withLock { idCount++ }
                        }
                    }
                }
            }.awaitAll()
        }
        val majorityId = idCount > (chunk.size / 2)
        return Triple(recovered, recoveredRom, majorityId)
    }

    suspend fun translateLinesToIndonesian(
        trackId: String,
        trackTitle: String,
        artist: String,
        lines: List<String>,
        onPartialResult: ((List<String>) -> Unit)? = null
    ): Result<TranslatedLyricsResult> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada baris lirik untuk diterjemahkan."))
        }
        val existing = getCachedEntry(trackId)
        if (existing != null &&
            existing.translationStyleVersion >= 3 &&
            existing.translatedLines.size == lines.size &&
            existing.translatedLines.any { it.isNotBlank() }
        ) {
            return@withContext Result.success(
                TranslatedLyricsResult(
                    lines = existing.translatedLines,
                    detectedIndonesian = existing.detectedIndonesian
                )
            )
        }

        val dedup = deduplicateLines(lines, nonLatinOnly = false)
        if (dedup.uniqueLines.isEmpty()) {
            val blank = List(lines.size) { "" }
            return@withContext Result.success(TranslatedLyricsResult(lines = blank, detectedIndonesian = false))
        }

        val hasNonLatin = containsNonLatinScript(lines)
        val detectedScript = if (hasNonLatin) detectScriptLabel(lines) else ""

        try {
            val uniqueTranslated = Array(dedup.uniqueLines.size) { "" }
            val uniqueTranslit = if (hasNonLatin) Array(dedup.uniqueLines.size) { "" } else null
            val chunkDescriptors = buildChunks(dedup.uniqueLines)

            val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
            val progressMutex = Mutex()
            var indonesianVoteCount = 0

            coroutineScope {
                chunkDescriptors.map { (startOffset, chunkLines) ->
                    async {
                        semaphore.withPermit {
                            val (chunkTranslated, chunkRom, chunkIsId) = translateChunkGoogle(
                                chunk = chunkLines,
                                includeRomanization = hasNonLatin
                            )
                            var snapshotToEmit: List<String>? = null
                            progressMutex.withLock {
                                for (i in chunkTranslated.indices) {
                                    val targetIdx = startOffset + i
                                    if (targetIdx in uniqueTranslated.indices) {
                                        uniqueTranslated[targetIdx] = chunkTranslated[i]
                                        if (uniqueTranslit != null && chunkRom != null && i in chunkRom.indices) {
                                            uniqueTranslit[targetIdx] = chunkRom[i]
                                        }
                                    }
                                }
                                if (chunkIsId) indonesianVoteCount++
                                if (onPartialResult != null) {
                                    snapshotToEmit = expandUniqueResults(
                                        originalSize = lines.size,
                                        mapping = dedup.originalToUniqueIndex,
                                        uniqueTranslated = uniqueTranslated
                                    )
                                }
                            }
                            snapshotToEmit?.let { onPartialResult?.invoke(it) }
                        }
                    }
                }.awaitAll()
            }

            val alreadyId = indonesianVoteCount > (chunkDescriptors.size / 2)
            val normalized = expandUniqueResults(
                originalSize = lines.size,
                mapping = dedup.originalToUniqueIndex,
                uniqueTranslated = uniqueTranslated
            )

            // Opportunistically cache aligned romanization obtained in the same Google Translate pass!
            val opportunisticRom = if (uniqueTranslit != null && uniqueTranslit.any { it.isNotBlank() }) {
                val expandedRom = expandUniqueResults(
                    originalSize = lines.size,
                    mapping = dedup.originalToUniqueIndex,
                    uniqueTranslated = uniqueTranslit
                )
                // Only keep romanization on lines that actually have non-Latin script
                expandedRom.mapIndexed { idx, rom ->
                    if (lineHasNonLatinScript(lines[idx])) rom else ""
                }
            } else emptyList()

            mergeAndSaveCache(trackId, lines.size) { cur ->
                val keepRom = cur.romanizedLines.any { it.isNotBlank() }
                cur.copy(
                    translatedLines = normalized,
                    detectedIndonesian = alreadyId,
                    translationStyleVersion = TRANSLATION_STYLE_VERSION,
                    romanizedLines = if (!keepRom && opportunisticRom.any { it.isNotBlank() }) opportunisticRom else cur.romanizedLines,
                    needsRomanization = if (!keepRom && opportunisticRom.any { it.isNotBlank() }) true else cur.needsRomanization,
                    scriptLabel = if (!keepRom && opportunisticRom.any { it.isNotBlank() }) detectedScript else cur.scriptLabel,
                    romanizationStyleVersion = if (!keepRom && opportunisticRom.any { it.isNotBlank() }) ROMANIZATION_STYLE_VERSION else cur.romanizationStyleVersion
                )
            }
            Result.success(TranslatedLyricsResult(lines = normalized, detectedIndonesian = alreadyId))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google Translate timed out for $trackTitle - $artist", e)
            Result.failure(Exception("Terjemahan lirik melewati batas waktu. Ketuk Coba Lagi sebentar ya."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Translate lyrics failed for $trackTitle - $artist", e)
            Result.failure(Exception(formatFriendlyTranslateError(e, "Gagal menerjemahkan lirik ke Bahasa Indonesia.")))
        }
    }

    private suspend fun romanizeChunkGoogle(chunk: List<String>): Array<String> {
        val joined = chunk.joinToString("\n")
        val response = callGoogleTranslate(
            text = joined,
            targetLang = "en",
            sourceLang = "auto",
            includeRomanization = true
        )

        val aligned = if (response.sourceTranslit.isNotBlank()) {
            splitPreservingLines(response.sourceTranslit, chunk.size)
        } else null

        if (aligned != null && aligned.count { it.isNotBlank() } >= (chunk.size * 0.7f).toInt().coerceAtLeast(1)) {
            return Array(chunk.size) { i ->
                aligned[i].ifBlank { quickLocalPhoneticFallback(chunk[i]) }
            }
        }

        // Exact per-line romanization when Google Translate merges `src_translit` lines
        val recovered = Array(chunk.size) { "" }
        val lineSem = Semaphore(MAX_PARALLEL_LINE_FALLBACK)
        coroutineScope {
            chunk.indices.map { idx ->
                async {
                    lineSem.withPermit {
                        val single = runCatching {
                            callGoogleTranslate(
                                text = chunk[idx],
                                targetLang = "en",
                                sourceLang = "auto",
                                includeRomanization = true
                            )
                        }.getOrNull()
                        val rom = single?.sourceTranslit
                            ?.replace("\r", "")
                            ?.replace("\n", " ")
                            ?.trim()
                            .orEmpty()
                        recovered[idx] = rom.ifBlank { quickLocalPhoneticFallback(chunk[idx]) }
                    }
                }
            }.awaitAll()
        }
        return recovered
    }

    suspend fun romanizeLines(
        trackId: String,
        trackTitle: String,
        artist: String,
        lines: List<String>,
        onPartialResult: ((List<String>, String) -> Unit)? = null
    ): Result<RomanizedLyricsResult> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada baris lirik untuk diromanisasi."))
        }
        val existing = getCachedEntry(trackId)
        if (existing != null &&
            existing.romanizationStyleVersion >= 2 &&
            existing.romanizedLines.size == lines.size &&
            existing.romanizedLines.any { it.isNotBlank() }
        ) {
            return@withContext Result.success(
                RomanizedLyricsResult(
                    lines = existing.romanizedLines,
                    needsRomanization = true,
                    scriptLabel = existing.scriptLabel.ifBlank { detectScriptLabel(lines) }
                )
            )
        }

        if (!containsNonLatinScript(lines)) {
            return@withContext Result.success(
                RomanizedLyricsResult(
                    lines = List(lines.size) { "" },
                    needsRomanization = false,
                    scriptLabel = "Latin"
                )
            )
        }

        // Only send unique non-Latin lines (English chorus lines in J-Pop/K-Pop stay clean & fast!)
        val dedup = deduplicateLines(lines, nonLatinOnly = true)
        if (dedup.uniqueLines.isEmpty()) {
            return@withContext Result.success(
                RomanizedLyricsResult(
                    lines = List(lines.size) { "" },
                    needsRomanization = false,
                    scriptLabel = "Latin"
                )
            )
        }

        val detectedScript = detectScriptLabel(lines)

        try {
            val uniqueRomanized = Array(dedup.uniqueLines.size) { "" }
            val chunkDescriptors = buildChunks(dedup.uniqueLines)

            val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
            val progressMutex = Mutex()

            coroutineScope {
                chunkDescriptors.map { (startOffset, chunkLines) ->
                    async {
                        semaphore.withPermit {
                            val chunkResult = try {
                                romanizeChunkGoogle(chunkLines)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Google romanize chunk failed, using local fallback: ${e.message}")
                                Array(chunkLines.size) { i -> quickLocalPhoneticFallback(chunkLines[i]) }
                            }
                            var snapshotToEmit: List<String>? = null
                            progressMutex.withLock {
                                for (i in chunkResult.indices) {
                                    val targetIdx = startOffset + i
                                    if (targetIdx in uniqueRomanized.indices) {
                                        uniqueRomanized[targetIdx] = chunkResult[i]
                                    }
                                }
                                if (onPartialResult != null) {
                                    snapshotToEmit = expandUniqueResults(
                                        originalSize = lines.size,
                                        mapping = dedup.originalToUniqueIndex,
                                        uniqueTranslated = uniqueRomanized
                                    )
                                }
                            }
                            snapshotToEmit?.let { onPartialResult?.invoke(it, detectedScript) }
                        }
                    }
                }.awaitAll()
            }

            val normalized = expandUniqueResults(
                originalSize = lines.size,
                mapping = dedup.originalToUniqueIndex,
                uniqueTranslated = uniqueRomanized
            )
            val hasAnyRom = normalized.any { it.isNotBlank() }
            if (hasAnyRom) {
                mergeAndSaveCache(trackId, lines.size) { cur ->
                    cur.copy(
                        romanizedLines = normalized,
                        needsRomanization = true,
                        scriptLabel = detectedScript,
                        romanizationStyleVersion = ROMANIZATION_STYLE_VERSION
                    )
                }
            }
            Result.success(
                RomanizedLyricsResult(
                    lines = normalized,
                    needsRomanization = hasAnyRom,
                    scriptLabel = detectedScript
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Google romanization timed out for $trackTitle - $artist", e)
            Result.failure(Exception("Romanisasi lirik melewati batas waktu. Silakan coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google romanization failed for $trackTitle - $artist", e)
            Result.failure(Exception(formatFriendlyTranslateError(e, "Gagal membuat romanisasi lirik.")))
        }
    }

    /**
     * Lightweight deterministic romanizer for Korean Hangul syllables and Japanese Hiragana/Katakana
     * used as an instant safety net if any single line has no remote transliteration.
     */
    private fun quickLocalPhoneticFallback(text: String): String {
        var hasHangulOrKana = false
        for (ch in text) {
            val block = Character.UnicodeBlock.of(ch)
            if (block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA
            ) {
                hasHangulOrKana = true
            } else if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return ""
            }
        }
        if (!hasHangulOrKana) return ""

        val initials = arrayOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
        val medials = arrayOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
        val finals = arrayOf("", "k", "k", "k", "n", "n", "n", "t", "l", "l", "l", "l", "l", "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t")

        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val code = ch.code
            if (code in 0xAC00..0xD7A3) {
                val offset = code - 0xAC00
                val initIdx = offset / 588
                val medIdx = (offset % 588) / 28
                val finIdx = offset % 28
                sb.append(initials[initIdx]).append(medials[medIdx]).append(finals[finIdx])
            } else {
                val kana = kanaToRomaji(ch)
                if (kana != null) sb.append(kana) else sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    private fun kanaToRomaji(ch: Char): String? {
        val h = if (ch.code in 0x30A1..0x30F6) (ch.code - 0x60).toChar() else ch
        return when (h) {
            'あ' -> "a"; 'い' -> "i"; 'う' -> "u"; 'え' -> "e"; 'お' -> "o"
            'か' -> "ka"; 'き' -> "ki"; 'く' -> "ku"; 'け' -> "ke"; 'こ' -> "ko"
            'が' -> "ga"; 'ぎ' -> "gi"; 'ぐ' -> "gu"; 'げ' -> "ge"; 'ご' -> "go"
            'さ' -> "sa"; 'し' -> "shi"; 'す' -> "su"; 'せ' -> "se"; 'そ' -> "so"
            'ざ' -> "za"; 'じ' -> "ji"; 'ず' -> "zu"; 'ぜ' -> "ze"; 'ぞ' -> "zo"
            'た' -> "ta"; 'ち' -> "chi"; 'つ' -> "tsu"; 'て' -> "te"; 'と' -> "to"
            'だ' -> "da"; 'ぢ' -> "ji"; 'づ' -> "zu"; 'で' -> "de"; 'ど' -> "do"
            'な' -> "na"; 'に' -> "ni"; 'ぬ' -> "nu"; 'ね' -> "ne"; 'の' -> "no"
            'は' -> "ha"; 'ひ' -> "hi"; 'ふ' -> "fu"; 'へ' -> "he"; 'ほ' -> "ho"
            'ば' -> "ba"; 'び' -> "bi"; 'ぶ' -> "bu"; 'べ' -> "be"; 'ぼ' -> "bo"
            'ぱ' -> "pa"; 'ぴ' -> "pi"; 'ぷ' -> "pu"; 'ぺ' -> "pe"; 'ぽ' -> "po"
            'ま' -> "ma"; 'み' -> "mi"; 'む' -> "mu"; 'め' -> "me"; 'も' -> "mo"
            'や' -> "ya"; 'ゆ' -> "yu"; 'よ' -> "yo"
            'ら' -> "ra"; 'り' -> "ri"; 'る' -> "ru"; 'れ' -> "re"; 'ろ' -> "ro"
            'わ' -> "wa"; 'を' -> "wo"; 'ん' -> "n"
            'ぁ' -> "a"; 'ぃ' -> "i"; 'ぅ' -> "u"; 'ぇ' -> "e"; 'ぉ' -> "o"
            'ゃ' -> "ya"; 'ゅ' -> "yu"; 'ょ' -> "yo"; 'っ' -> ""
            'ー' -> "-"
            else -> null
        }
    }

    private fun formatFriendlyTranslateError(e: Exception, fallback: String): String {
        val raw = e.localizedMessage.orEmpty()
        return when {
            raw.contains("429", ignoreCase = true) ->
                "Google Translate sedang padat sebentar. Tunggu beberapa detik lalu ketuk Coba Lagi."
            raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("timeout", ignoreCase = true) ||
                raw.contains("failed to connect", ignoreCase = true) ->
                "Koneksi internet tidak stabil. Periksa jaringan lalu coba lagi."
            raw.isNotBlank() -> raw
            else -> fallback
        }
    }

    internal fun formatFriendlyAiError(e: Exception, fallback: String): String {
        val raw = e.localizedMessage.orEmpty()
        return when {
            raw.contains("403", ignoreCase = true) ||
                raw.contains("App Check", ignoreCase = true) ||
                raw.contains("attestation", ignoreCase = true) -> {
                val diag = lastAppCheckDiagnostic?.take(120)
                if (!diag.isNullOrBlank()) {
                    "Verifikasi keamanan AI gagal ($diag). Pastikan tanggal/jam HP otomatis, layanan Google Play aktif, & tidak memakai VPN pemblokir lalu coba lagi."
                } else {
                    "Verifikasi layanan AI ditolak server (403). Pastikan jam HP otomatis & Google Play Services aktif lalu coba lagi."
                }
            }
            raw.contains("429", ignoreCase = true) || raw.contains("quota", ignoreCase = true) ->
                "Layanan AI sedang sibuk. Tunggu beberapa detik lalu coba lagi."
            raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("timeout", ignoreCase = true) ->
                "Koneksi internet tidak stabil. Periksa jaringan lalu coba lagi."
            raw.isNotBlank() -> raw
            else -> fallback
        }
    }
}
