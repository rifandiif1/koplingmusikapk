package com.example.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
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
private const val TRANSLATION_STYLE_VERSION = 4
private const val CHUNK_THRESHOLD = 32
private const val CHUNK_SIZE = 24
private const val MAX_PARALLEL_CHUNKS = 3
private const val PRIMARY_TIMEOUT_MS = 30_000L
private const val FALLBACK_TIMEOUT_MS = 45_000L

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
    val scriptLabel: String = ""
)

private data class DeduplicatedLines(
    val uniqueLines: List<String>,
    val originalToUniqueIndex: IntArray
)

/**
 * Translates song lyrics line-by-line into natural casual Indonesian and generates phonetic
 * romanization (Romaji, Korean Revised Romanization, Pinyin, Cyrillic, Thai, etc.)
 * via Firebase AI Logic using ultra-fast Gemini 3.5 Flash-Lite as primary model (with
 * Gemini 3.8 Flash fallback), repeated-line deduplication, concurrent chunk execution,
 * progressive partial updates, and persistent offline local disk caching.
 */
object LyricsTranslator {
    private val memoryCache = ConcurrentHashMap<String, CachedLyricsAiData>()

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
        memoryCache.clear()
        SongMeaningAnalyzer.clearMemoryCache()
        val dir = cacheDir
        if (dir != null && dir.exists()) {
            runCatching {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
        return freed
    }

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "alreadyIndonesian" to Schema.boolean(
                description = "True if most input lines are already Indonesian."
            ),
            "translations" to Schema.array(
                items = Schema.string(),
                description = "Casual natural Indonesian translation array matching exact count and order."
            )
        ),
        optionalProperties = listOf("alreadyIndonesian")
    )

    private val romanizationSchema = Schema.obj(
        properties = mapOf(
            "needsRomanization" to Schema.boolean(
                description = "True if input contains non-Latin scripts."
            ),
            "scriptLabel" to Schema.string(
                description = "Short script label: Romaji, Hangul, Pinyin, Kiril, Thai, or Latin."
            ),
            "romanizations" to Schema.array(
                items = Schema.string(),
                description = "Phonetic Latin reading array matching exact count and order."
            )
        ),
        optionalProperties = listOf("needsRomanization", "scriptLabel")
    )

    // Ultra-fast primary model for line-by-line translation so lyrics translate in ~2-4s without timeouts.
    private val fastTranslationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val fallbackTranslationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.25f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val fastRomanizationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = romanizationSchema
                temperature = 0.1f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val fallbackRomanizationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = romanizationSchema
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

    fun getCachedEntry(trackId: String): CachedLyricsAiData? {
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
            val validTransList = if (styleVersion >= 3) transList else emptyList()

            CachedLyricsAiData(
                lineCount = obj.optInt("lineCount", maxOf(validTransList.size, romList.size)),
                translatedLines = validTransList,
                detectedIndonesian = obj.optBoolean("detectedIndonesian", false),
                translationStyleVersion = styleVersion,
                romanizedLines = romList,
                needsRomanization = obj.optBoolean("needsRomanization", romList.any { it.isNotBlank() }),
                scriptLabel = obj.optString("scriptLabel", "")
            ).also {
                memoryCache[trackId] = it
            }
        }.getOrNull()
    }

    private fun saveCacheEntry(trackId: String, entry: CachedLyricsAiData) {
        memoryCache[trackId] = entry
        val file = cacheFileFor(trackId) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val obj = JSONObject().apply {
                put("lineCount", entry.lineCount)
                put("detectedIndonesian", entry.detectedIndonesian)
                put("translationStyleVersion", entry.translationStyleVersion)
                put("translatedLines", JSONArray(entry.translatedLines))
                put("needsRomanization", entry.needsRomanization)
                put("scriptLabel", entry.scriptLabel)
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

    /**
     * Removes empty / musical-symbol lines and deduplicates repeated chorus/verse lines so the
     * AI only translates each unique lyric line once, cutting latency and token usage by ~50%.
     */
    private fun deduplicateLines(lines: List<String>): DeduplicatedLines {
        val uniqueLines = mutableListOf<String>()
        val keyToUniqueIdx = LinkedHashMap<String, Int>()
        val mapping = IntArray(lines.size) { -1 }

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty() || isNonLyricMarker(trimmed)) {
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

    /**
     * Fast local language check so Indonesian songs don't trigger unnecessary auto-translation.
     */
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

    /**
     * Quick heuristic to detect if lyrics contain non-Latin scripts (Japanese, Korean, Chinese,
     * Cyrillic, Thai, Arabic, Devanagari, etc.).
     */
    fun containsNonLatinScript(lines: List<String>): Boolean {
        return lines.any { line ->
            line.any { ch ->
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
    }

    private fun extractJsonObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        runCatching { return JSONObject(trimmed) }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return JSONObject(trimmed.substring(start, end + 1))
        }
        throw IllegalStateException("Format JSON respon tidak valid.")
    }

    private suspend fun translateChunk(
        trackTitle: String,
        artist: String,
        chunk: List<String>
    ): Pair<List<String>, Boolean> {
        val numberedInput = chunk.mapIndexed { idx, line ->
            "${idx + 1}. $line"
        }.joinToString("\n")

        // Concise, high-signal prompt minimizes input tokens for fastest possible response
        val prompt = buildString {
            appendLine("Terjemahkan lirik \"$trackTitle\" ($artist) ke Bahasa Indonesia santai, luwes, tidak baku (pakai aku/kamu, hindari saya/Anda/engkau).")
            appendLine("Kembalikan JSON array `translations` dengan TEPAT ${chunk.size} item sesuai urutan, tanpa nomor baris.")
            append(numberedInput)
        }

        val attempts = listOf(
            fastTranslationModel to PRIMARY_TIMEOUT_MS,
            fallbackTranslationModel to FALLBACK_TIMEOUT_MS
        )
        var lastError: Exception? = null

        for ((attemptIdx, pair) in attempts.withIndex()) {
            val (model, timeoutMs) = pair
            try {
                if (attemptIdx > 0) delay(300L)
                val response = withTimeout(timeoutMs) {
                    model.generateContent(prompt)
                }
                val rawJson = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Respon terjemahan kosong dari layanan AI.")

                val obj = extractJsonObject(rawJson)
                val alreadyId = obj.optBoolean("alreadyIndonesian", false)
                val arr = obj.optJSONArray("translations")
                    ?: throw IllegalStateException("Format array terjemahan tidak ditemukan.")

                val cleaned = List(chunk.size) { idx ->
                    if (idx < arr.length()) {
                        arr.optString(idx, "")
                            .trim()
                            .replace(Regex("^\\d+[.)]\\s*"), "")
                    } else ""
                }
                if (cleaned.any { it.isNotBlank() }) {
                    return cleaned to alreadyId
                }
                throw IllegalStateException("Hasil terjemahan kosong.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Translation attempt ${attemptIdx + 1} failed for $trackTitle: ${e.message}")
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

        val dedup = deduplicateLines(lines)
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

            // Translate all chunks concurrently so even long songs finish in a few seconds
            coroutineScope {
                chunkDescriptors.map { (startOffset, chunkLines) ->
                    async {
                        semaphore.withPermit {
                            val (chunkResult, chunkIsId) = translateChunk(trackTitle, artist, chunkLines)
                            var snapshot: List<String>? = null
                            progressMutex.withLock {
                                for (i in chunkResult.indices) {
                                    val targetIdx = startOffset + i
                                    if (targetIdx in uniqueTranslated.indices) {
                                        uniqueTranslated[targetIdx] = chunkResult[i]
                                    }
                                }
                                if (chunkIsId) indonesianVoteCount++
                                if (chunkDescriptors.size > 1 && onPartialResult != null) {
                                    snapshot = expandUniqueResults(
                                        originalSize = lines.size,
                                        mapping = dedup.originalToUniqueIndex,
                                        uniqueTranslated = uniqueTranslated
                                    )
                                }
                            }
                            snapshot?.let { partial -> onPartialResult?.invoke(partial) }
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
            val merged = (existing ?: CachedLyricsAiData(lineCount = lines.size)).copy(
                lineCount = lines.size,
                translatedLines = normalized,
                detectedIndonesian = alreadyId,
                translationStyleVersion = TRANSLATION_STYLE_VERSION
            )
            saveCacheEntry(trackId, merged)
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

    private suspend fun romanizeChunk(
        trackTitle: String,
        artist: String,
        chunk: List<String>
    ): Triple<List<String>, Boolean, String> {
        val numberedInput = chunk.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
        val prompt = buildString {
            appendLine("Buat romanisasi/pelafalan alfabet Latin natural untuk lirik \"$trackTitle\" ($artist) (Jepang->Romaji Hepburn, Korea->Revised Romanization, Mandarin->Pinyin, aksara lain->fonetik Latin).")
            appendLine("Kembalikan JSON array `romanizations` TEPAT ${chunk.size} item sesuai urutan, tanpa nomor baris. Baris murni Latin -> \"\".")
            append(numberedInput)
        }

        val attempts = listOf(
            fastRomanizationModel to PRIMARY_TIMEOUT_MS,
            fallbackRomanizationModel to FALLBACK_TIMEOUT_MS
        )
        var lastErr: Exception? = null
        for ((attemptIdx, pair) in attempts.withIndex()) {
            val (model, timeoutMs) = pair
            try {
                if (attemptIdx > 0) delay(300L)
                val response = withTimeout(timeoutMs) {
                    model.generateContent(prompt)
                }
                val rawJson = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Respon romanisasi kosong.")
                val obj = extractJsonObject(rawJson)
                val needsRom = obj.optBoolean("needsRomanization", true)
                val script = obj.optString("scriptLabel", "").trim()
                val arr = obj.optJSONArray("romanizations")
                    ?: throw IllegalStateException("Format romanisasi tidak valid.")
                val chunkList = List(chunk.size) { idx ->
                    if (idx < arr.length()) {
                        arr.optString(idx, "").trim().replace(Regex("^\\d+[.)]\\s*"), "")
                    } else ""
                }
                return Triple(chunkList, needsRom, script)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException("Gagal membuat romanisasi lirik.")
    }

    suspend fun romanizeLines(
        trackId: String,
        trackTitle: String,
        artist: String,
        lines: List<String>
    ): Result<RomanizedLyricsResult> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada baris lirik untuk diromanisasi."))
        }
        val existing = getCachedEntry(trackId)
        if (existing != null && existing.romanizedLines.size == lines.size && existing.romanizedLines.isNotEmpty()) {
            return@withContext Result.success(
                RomanizedLyricsResult(
                    lines = existing.romanizedLines,
                    needsRomanization = existing.needsRomanization,
                    scriptLabel = existing.scriptLabel
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

        val dedup = deduplicateLines(lines)
        if (dedup.uniqueLines.isEmpty()) {
            return@withContext Result.success(
                RomanizedLyricsResult(
                    lines = List(lines.size) { "" },
                    needsRomanization = false,
                    scriptLabel = "Latin"
                )
            )
        }

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
            val mutex = Mutex()
            var needsRomAny = false
            var detectedScript = "Romaji"

            coroutineScope {
                chunkDescriptors.map { (startOffset, chunkLines) ->
                    async {
                        semaphore.withPermit {
                            val (chunkList, needsRom, script) = romanizeChunk(trackTitle, artist, chunkLines)
                            mutex.withLock {
                                for (i in chunkList.indices) {
                                    val targetIdx = startOffset + i
                                    if (targetIdx in uniqueRomanized.indices) {
                                        uniqueRomanized[targetIdx] = chunkList[i]
                                    }
                                }
                                if (needsRom && chunkList.any { it.isNotBlank() }) needsRomAny = true
                                if (script.isNotBlank()) detectedScript = script
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
            val merged = (existing ?: CachedLyricsAiData(lineCount = lines.size)).copy(
                lineCount = lines.size,
                romanizedLines = normalized,
                needsRomanization = needsRomAny && normalized.any { it.isNotBlank() },
                scriptLabel = detectedScript
            )
            saveCacheEntry(trackId, merged)
            Result.success(
                RomanizedLyricsResult(
                    lines = normalized,
                    needsRomanization = merged.needsRomanization,
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
