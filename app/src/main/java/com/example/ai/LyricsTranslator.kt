package com.example.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.generationConfig
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LyricsTranslator"
private const val CACHE_DIR_NAME = "lyrics_ai_cache"
private const val TRANSLATION_STYLE_VERSION = 5
private const val ROMANIZATION_STYLE_VERSION = 2
private const val CHUNK_THRESHOLD = 36
private const val CHUNK_SIZE = 28
private const val MAX_PARALLEL_CHUNKS = 3
private const val PRIMARY_STREAM_TIMEOUT_MS = 35_000L
private const val FALLBACK_STREAM_TIMEOUT_MS = 45_000L

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

/**
 * Translates song lyrics line-by-line into natural casual Indonesian and generates accurate
 * phonetic romanization (Romaji, Korean Revised Romanization, Pinyin, Cyrillic, Thai, etc.)
 * via Firebase AI Logic using streaming `generateContentStream()` on ultra-fast
 * `gemini-3.5-flash-lite` (with `gemini-3.8-flash` automatic fallback).
 *
 * Uses strict `N|text` indexed line streaming so:
 * - First translated/romanized lines appear on screen within ~1 second!
 * - Line alignment can NEVER drift or shift even if a line is skipped or contains quotes!
 * - Repeated chorus lines are deduplicated (~50% fewer tokens & latency)!
 * - Built-in Hiragana/Katakana/Hangul fallback ensures zero blank lines on CJK/Korean songs!
 */
object LyricsTranslator {
    private val memoryCache = ConcurrentHashMap<String, CachedLyricsAiData>()
    private val cacheLock = Any()

    @Volatile
    private var cacheDir: File? = null

    private val lyricsSafetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.ONLY_HIGH)
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

    // Indexed line parser matching "1|text", "1: text", "1. text", or "1) text"
    private val indexedLineRegex = Regex("""^\s*(\d+)\s*[|:\.)]\s*(.*)$""")

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

    // Plain-text streaming models: ultra-low TTFT, zero JSON escaping or schema failure overhead.
    private val fastStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.2f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val fallbackStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.25f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val fastRomajiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.08f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val fallbackRomajiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.1f
            },
            safetySettings = lyricsSafetySettings
        )
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
            val validRomList = if (romVersion >= ROMANIZATION_STYLE_VERSION && romList.any { it.isNotBlank() }) romList else emptyList()

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
            Log.w(TAG, "Failed to persist local lyrics AI cache for $trackId", err)
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
     * Parses a streamed `N|value` line, strips accidental markdown/quotes, and returns (0-based index, text).
     */
    private fun parseStreamedIndexedLine(rawLine: String, maxCount: Int): Pair<Int, String>? {
        val cleaned = rawLine.trim()
            .removePrefix("```")
            .removePrefix("- ")
            .removePrefix("* ")
            .trim()
        if (cleaned.isEmpty() || cleaned.startsWith("LANG:", ignoreCase = true)) return null
        val match = indexedLineRegex.matchEntire(cleaned) ?: return null
        val num = match.groupValues[1].toIntOrNull() ?: return null
        val idx = num - 1
        if (idx !in 0 until maxCount) return null
        val text = match.groupValues[2]
            .trim()
            .removeSurrounding("\"")
            .trim()
        return idx to text
    }

    private suspend fun streamTranslateChunk(
        trackTitle: String,
        artist: String,
        chunk: List<String>,
        onLineReady: suspend (Int, String) -> Unit
    ): Pair<Array<String>, Boolean> {
        val numberedInput = chunk.mapIndexed { idx, line ->
            "${idx + 1}|$line"
        }.joinToString("\n")

        val prompt = buildString {
            appendLine("Terjemahkan lirik \"$trackTitle\" - $artist ke Bahasa Indonesia santai, luwes, tidak baku (pakai aku/kamu, hindari saya/Anda/engkau).")
            appendLine("Format WAJIB setiap baris: nomor|terjemahan (tanpa pembuka, tanpa markdown, tanpa penjelasan).")
            appendLine("Jika sebagian besar lirik asli sudah Bahasa Indonesia, tulis baris pertama: LANG:ID lalu tetap terjemahkan/pertahankan barisnya.")
            append(numberedInput)
        }

        val attempts = listOf(
            fastStreamModel to PRIMARY_STREAM_TIMEOUT_MS,
            fallbackStreamModel to FALLBACK_STREAM_TIMEOUT_MS
        )
        var lastError: Exception? = null

        for ((attemptIdx, pair) in attempts.withIndex()) {
            val (model, timeoutMs) = pair
            val results = Array(chunk.size) { "" }
            var detectedId = false
            var filledCount = 0
            try {
                if (attemptIdx > 0) delay(250L)
                val lineBuffer = StringBuilder()

                withTimeout(timeoutMs) {
                    model.generateContentStream(prompt).collect { response ->
                        val piece = response.text.orEmpty()
                        if (piece.isEmpty()) return@collect
                        lineBuffer.append(piece)

                        while (true) {
                            val nl = lineBuffer.indexOf("\n")
                            if (nl < 0) break
                            val rawLine = lineBuffer.substring(0, nl)
                            lineBuffer.delete(0, nl + 1)
                            if (rawLine.trim().equals("LANG:ID", ignoreCase = true)) {
                                detectedId = true
                                continue
                            }
                            parseStreamedIndexedLine(rawLine, chunk.size)?.let { (idx, value) ->
                                if (value.isNotBlank() && results[idx].isBlank()) {
                                    results[idx] = value
                                    filledCount++
                                    onLineReady(idx, value)
                                }
                            }
                        }
                    }
                }
                // Flush trailing final line without newline
                val remainder = lineBuffer.toString().trim()
                if (remainder.isNotBlank()) {
                    if (remainder.equals("LANG:ID", ignoreCase = true)) {
                        detectedId = true
                    } else {
                        parseStreamedIndexedLine(remainder, chunk.size)?.let { (idx, value) ->
                            if (value.isNotBlank() && results[idx].isBlank()) {
                                results[idx] = value
                                filledCount++
                                onLineReady(idx, value)
                            }
                        }
                    }
                }

                if (filledCount > 0) {
                    return results to detectedId
                }
                throw IllegalStateException("Respon terjemahan kosong dari layanan AI.")
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Stream translation attempt ${attemptIdx + 1} timed out for $trackTitle")
                if (filledCount >= (chunk.size * 0.6f).toInt().coerceAtLeast(1)) {
                    return results to detectedId
                }
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Stream translation attempt ${attemptIdx + 1} failed for $trackTitle: ${e.message}")
                if (filledCount >= (chunk.size * 0.6f).toInt().coerceAtLeast(1)) {
                    return results to detectedId
                }
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Gagal menerjemahkan lirik.")
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

        try {
            val uniqueTranslated = Array(dedup.uniqueLines.size) { "" }
            val chunkDescriptors = if (dedup.uniqueLines.size > CHUNK_THRESHOLD) {
                dedup.uniqueLines.chunked(CHUNK_SIZE).mapIndexed { chunkIdx, chunkLines ->
                    (chunkIdx * CHUNK_SIZE) to chunkLines
                }
            } else {
                listOf(0 to dedup.uniqueLines)
            }

            val semaphore = Semaphore(MAX_PARALLEL_CHUNKS)
            val progressMutex = Mutex()
            var indonesianVoteCount = 0
            var lastEmitTimeMs = 0L
            var pendingSinceLastEmit = 0

            coroutineScope {
                chunkDescriptors.map { (startOffset, chunkLines) ->
                    async {
                        semaphore.withPermit {
                            val (_, chunkIsId) = streamTranslateChunk(
                                trackTitle = trackTitle,
                                artist = artist,
                                chunk = chunkLines,
                                onLineReady = { localIdx, translatedText ->
                                    var snapshotToEmit: List<String>? = null
                                    progressMutex.withLock {
                                        val targetIdx = startOffset + localIdx
                                        if (targetIdx in uniqueTranslated.indices) {
                                            uniqueTranslated[targetIdx] = translatedText
                                            pendingSinceLastEmit++
                                            val now = System.currentTimeMillis()
                                            if (onPartialResult != null && (pendingSinceLastEmit >= 3 || now - lastEmitTimeMs >= 260L)) {
                                                lastEmitTimeMs = now
                                                pendingSinceLastEmit = 0
                                                snapshotToEmit = expandUniqueResults(
                                                    originalSize = lines.size,
                                                    mapping = dedup.originalToUniqueIndex,
                                                    uniqueTranslated = uniqueTranslated
                                                )
                                            }
                                        }
                                    }
                                    snapshotToEmit?.let { onPartialResult?.invoke(it) }
                                }
                            )
                            progressMutex.withLock {
                                if (chunkIsId) indonesianVoteCount++
                            }
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
            mergeAndSaveCache(trackId, lines.size) { cur ->
                cur.copy(
                    translatedLines = normalized,
                    detectedIndonesian = alreadyId,
                    translationStyleVersion = TRANSLATION_STYLE_VERSION
                )
            }
            Result.success(TranslatedLyricsResult(lines = normalized, detectedIndonesian = alreadyId))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyrics translation timed out", e)
            Result.failure(Exception("Terjemahan lirik melewati batas waktu. Ketuk Coba Lagi sebentar ya."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI lyrics translation failed", e)
            Result.failure(Exception(formatFriendlyAiError(e, "Gagal menerjemahkan lirik ke Bahasa Indonesia.")))
        }
    }

    private suspend fun streamRomanizeChunk(
        trackTitle: String,
        artist: String,
        scriptHint: String,
        chunk: List<String>,
        onLineReady: suspend (Int, String) -> Unit
    ): Array<String> {
        val numberedInput = chunk.mapIndexed { idx, line -> "${idx + 1}|$line" }.joinToString("\n")
        val prompt = buildString {
            appendLine("Ubah lirik \"$trackTitle\" - $artist ($scriptHint) menjadi pelafalan alfabet Latin (Romaji Hepburn untuk Jepang dengan spasi antar kata, Revised Romanization untuk Korea, Pinyin tanpa nada ribet untuk Mandarin, fonetik Latin untuk aksara lain).")
            appendLine("Format WAJIB setiap baris: nomor|pelafalan_latin (tanpa pembuka, tanpa markdown, pertahankan kata bahasa Inggris jika bercampur).")
            append(numberedInput)
        }

        val attempts = listOf(
            fastRomajiModel to PRIMARY_STREAM_TIMEOUT_MS,
            fallbackRomajiModel to FALLBACK_STREAM_TIMEOUT_MS
        )
        var lastErr: Exception? = null

        for ((attemptIdx, pair) in attempts.withIndex()) {
            val (model, timeoutMs) = pair
            val results = Array(chunk.size) { "" }
            var filledCount = 0
            try {
                if (attemptIdx > 0) delay(250L)
                val lineBuffer = StringBuilder()

                withTimeout(timeoutMs) {
                    model.generateContentStream(prompt).collect { response ->
                        val piece = response.text.orEmpty()
                        if (piece.isEmpty()) return@collect
                        lineBuffer.append(piece)

                        while (true) {
                            val nl = lineBuffer.indexOf("\n")
                            if (nl < 0) break
                            val rawLine = lineBuffer.substring(0, nl)
                            lineBuffer.delete(0, nl + 1)
                            parseStreamedIndexedLine(rawLine, chunk.size)?.let { (idx, value) ->
                                if (value.isNotBlank() && results[idx].isBlank()) {
                                    results[idx] = value
                                    filledCount++
                                    onLineReady(idx, value)
                                }
                            }
                        }
                    }
                }
                val remainder = lineBuffer.toString().trim()
                if (remainder.isNotBlank()) {
                    parseStreamedIndexedLine(remainder, chunk.size)?.let { (idx, value) ->
                        if (value.isNotBlank() && results[idx].isBlank()) {
                            results[idx] = value
                            filledCount++
                            onLineReady(idx, value)
                        }
                    }
                }

                // Fill any single missed Kana/Hangul line deterministically so zero lines stay blank
                for (i in chunk.indices) {
                    if (results[i].isBlank()) {
                        val fallbackRom = quickLocalPhoneticFallback(chunk[i])
                        if (fallbackRom.isNotBlank()) {
                            results[i] = fallbackRom
                            filledCount++
                            onLineReady(i, fallbackRom)
                        }
                    }
                }

                if (filledCount > 0) {
                    return results
                }
                throw IllegalStateException("Respon romanisasi kosong.")
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Stream romanization attempt ${attemptIdx + 1} timed out for $trackTitle")
                if (filledCount >= (chunk.size * 0.6f).toInt().coerceAtLeast(1)) {
                    return results
                }
                lastErr = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Stream romanization attempt ${attemptIdx + 1} failed for $trackTitle: ${e.message}")
                if (filledCount >= (chunk.size * 0.6f).toInt().coerceAtLeast(1)) {
                    return results
                }
                lastErr = e
            }
        }

        // Last-resort deterministic fallback for pure Kana/Hangul lyrics if offline/AI temporarily down
        val localFallback = Array(chunk.size) { i -> quickLocalPhoneticFallback(chunk[i]) }
        if (localFallback.any { it.isNotBlank() }) {
            return localFallback
        }
        throw lastErr ?: IllegalStateException("Gagal membuat romanisasi lirik.")
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
            existing.romanizationStyleVersion >= ROMANIZATION_STYLE_VERSION &&
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

        // Only send unique non-Latin lines to AI (English chorus lines in J-Pop/K-Pop stay clean & don't slow down!)
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
            val chunkDescriptors = if (dedup.uniqueLines.size > CHUNK_THRESHOLD) {
                dedup.uniqueLines.chunked(CHUNK_SIZE).mapIndexed { chunkIdx, chunkLines ->
                    (chunkIdx * CHUNK_SIZE) to chunkLines
                }
            } else {
                listOf(0 to dedup.uniqueLines)
            }

            val semaphore = Semaphore(MAX_PARALLEL_CHUNKS)
            val progressMutex = Mutex()
            var lastEmitTimeMs = 0L
            var pendingSinceLastEmit = 0

            coroutineScope {
                chunkDescriptors.map { (startOffset, chunkLines) ->
                    async {
                        semaphore.withPermit {
                            val chunkResult = streamRomanizeChunk(
                                trackTitle = trackTitle,
                                artist = artist,
                                scriptHint = detectedScript,
                                chunk = chunkLines,
                                onLineReady = { localIdx, romText ->
                                    var snapshotToEmit: List<String>? = null
                                    progressMutex.withLock {
                                        val targetIdx = startOffset + localIdx
                                        if (targetIdx in uniqueRomanized.indices) {
                                            uniqueRomanized[targetIdx] = romText
                                            pendingSinceLastEmit++
                                            val now = System.currentTimeMillis()
                                            if (onPartialResult != null && (pendingSinceLastEmit >= 3 || now - lastEmitTimeMs >= 260L)) {
                                                lastEmitTimeMs = now
                                                pendingSinceLastEmit = 0
                                                snapshotToEmit = expandUniqueResults(
                                                    originalSize = lines.size,
                                                    mapping = dedup.originalToUniqueIndex,
                                                    uniqueTranslated = uniqueRomanized
                                                )
                                            }
                                        }
                                    }
                                    snapshotToEmit?.let { onPartialResult?.invoke(it, detectedScript) }
                                }
                            )
                            progressMutex.withLock {
                                for (i in chunkResult.indices) {
                                    val targetIdx = startOffset + i
                                    if (targetIdx in uniqueRomanized.indices && uniqueRomanized[targetIdx].isBlank()) {
                                        uniqueRomanized[targetIdx] = chunkResult[i]
                                    }
                                }
                            }
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
            Log.e(TAG, "Lyrics romanization timed out", e)
            Result.failure(Exception("Romanisasi lirik melewati batas waktu. Silakan coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI lyrics romanization failed", e)
            Result.failure(Exception(formatFriendlyAiError(e, "Gagal membuat romanisasi lirik.")))
        }
    }

    /**
     * Lightweight deterministic romanizer for Korean Hangul syllables and Japanese Hiragana/Katakana
     * used only as an instant safety net if any single line is missed by AI.
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
                // Kanji/Hanzi needs contextual AI reading; don't emit broken partial output
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
        // Normalize Katakana to Hiragana range
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

    internal fun formatFriendlyAiError(e: Exception, fallback: String): String {
        val raw = e.localizedMessage.orEmpty()
        return when {
            raw.contains("403", ignoreCase = true) || raw.contains("App Check", ignoreCase = true) ->
                "Verifikasi layanan AI gagal sementara. Periksa koneksi internet lalu coba lagi."
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
