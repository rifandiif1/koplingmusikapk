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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LyricsTranslator"
private const val CACHE_DIR_NAME = "lyrics_ai_cache"
private const val TRANSLATION_STYLE_VERSION = 3
private const val CHUNK_THRESHOLD = 42
private const val CHUNK_SIZE = 32

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

/**
 * Translates song lyrics line-by-line into natural casual Indonesian and generates phonetic
 * romanization (Romaji, Korean Revised Romanization, Pinyin, Cyrillic, Thai, etc.)
 * via Firebase AI Logic (Gemini 3.8 Flash with Gemini 3.5 Flash-Lite fallback) with
 * persistent local disk caching so results stay available offline and across restarts.
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
                description = "True if the majority of the original lyrics are already in Indonesian."
            ),
            "translations" to Schema.array(
                items = Schema.string(),
                description = "Line-by-line natural Indonesian translation matching the exact count and order of input lines."
            )
        ),
        optionalProperties = listOf("alreadyIndonesian")
    )

    private val romanizationSchema = Schema.obj(
        properties = mapOf(
            "needsRomanization" to Schema.boolean(
                description = "True if the lyrics contain non-Latin scripts such as Japanese, Korean, Chinese, Cyrillic, Thai, Arabic, Hindi, etc."
            ),
            "scriptLabel" to Schema.string(
                description = "Short label of the detected script/romanization style in Indonesian, e.g. 'Romaji', 'Hangul', 'Pinyin', 'Kiril', 'Thai', or 'Latin'."
            ),
            "romanizations" to Schema.array(
                items = Schema.string(),
                description = "Line-by-line phonetic Latin reading matching the exact count and order of input lines."
            )
        ),
        optionalProperties = listOf("needsRomanization", "scriptLabel")
    )

    private val translationModel by lazy {
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

    private val fallbackTranslationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.25f
            },
            safetySettings = lyricsSafetySettings
        )
    }

    private val romanizationModel by lazy {
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

    private val fallbackRomanizationModel by lazy {
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
            val validTransList = if (styleVersion >= TRANSLATION_STYLE_VERSION) transList else emptyList()

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

        val prompt = buildString {
            appendLine("Terjemahkan lirik lagu berikut ke Bahasa Indonesia sehari-hari yang SANTAI, LUWES, TIDAK BAKU, dan pas dengan emosi lagunya.")
            appendLine("Judul: $trackTitle")
            appendLine("Artis: $artist")
            appendLine("GAYA BAHASA:")
            appendLine("- Hindari bahasa baku/kaku seperti 'saya', 'Anda', 'engkau', 'diriku', 'dirimu', 'tidaklah', 'oleh karena itu', 'apabila'.")
            appendLine("- Gunakan gaya pop Indonesia natural ('aku / kamu' untuk lagu cinta/galau/santai; boleh 'gue / lo' untuk rap/slang).")
            appendLine("- Pakai kata mengalir alami ('nggak', 'cuma', 'kayak', 'bikin', 'udah', 'lagi', 'kangen', 'nggak apa-apa') sesuai nuansa lagu.")
            appendLine("- Terjemahkan makna & idiomnya secara luwes, bukan kata per kata.")
            appendLine("ATURAN WAJIB:")
            appendLine("- Kembalikan array `translations` berisi TEPAT ${chunk.size} elemen sesuai urutan input 1-per-1.")
            appendLine("- Jangan hapus atau gabung baris. Jika baris kosong atau simbol musik (♪), kembalikan \"\".")
            appendLine("- Jangan sertakan nomor baris di awal hasil terjemahan.")
            appendLine()
            appendLine("LIRIK (${chunk.size} baris):")
            append(numberedInput)
        }

        val modelsToTry = listOf(translationModel, fallbackTranslationModel)
        var lastError: Exception? = null

        for ((attemptIdx, model) in modelsToTry.withIndex()) {
            try {
                if (attemptIdx > 0) delay(650L)
                val response = withTimeout(50_000L) {
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
                if (cleaned.any { it.isNotBlank() } || chunk.all { it.isBlank() || it == "♪" }) {
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
        lines: List<String>
    ): Result<TranslatedLyricsResult> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada baris lirik untuk diterjemahkan."))
        }
        val existing = getCachedEntry(trackId)
        if (existing != null &&
            existing.translationStyleVersion >= TRANSLATION_STYLE_VERSION &&
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

        try {
            val allTranslated = mutableListOf<String>()
            var indonesianVoteCount = 0
            val chunks = if (lines.size > CHUNK_THRESHOLD) lines.chunked(CHUNK_SIZE) else listOf(lines)

            for (chunk in chunks) {
                val (chunkResult, chunkIsId) = translateChunk(trackTitle, artist, chunk)
                allTranslated.addAll(chunkResult)
                if (chunkIsId) indonesianVoteCount++
            }

            val alreadyId = indonesianVoteCount > (chunks.size / 2)
            val normalized = List(lines.size) { idx -> allTranslated.getOrElse(idx) { "" } }
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
            Result.failure(Exception("Terjemahan lirik melewati batas waktu. Ketuk untuk coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI lyrics translation failed", e)
            Result.failure(Exception(formatFriendlyAiError(e, "Gagal menerjemahkan lirik ke Bahasa Indonesia.")))
        }
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

        try {
            val allRomanized = mutableListOf<String>()
            var needsRomAny = false
            var detectedScript = "Romaji"
            val chunks = if (lines.size > CHUNK_THRESHOLD) lines.chunked(CHUNK_SIZE) else listOf(lines)

            for (chunk in chunks) {
                val numberedInput = chunk.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
                val prompt = buildString {
                    appendLine("Buat romanisasi / pelafalan alfabet Latin yang akurat dan mudah dinyanyikan untuk lirik lagu berikut.")
                    appendLine("Judul: $trackTitle")
                    appendLine("Artis: $artist")
                    appendLine("ATURAN WAJIB:")
                    appendLine("- Bahasa Jepang -> Romaji Hepburn natural dengan spasi antar kata.")
                    appendLine("- Bahasa Korea -> Revised Romanization of Korean yang natural.")
                    appendLine("- Bahasa Mandarin -> Pinyin yang mudah dibaca.")
                    appendLine("- Bahasa non-Latin lain (Rusia/Kiril, Thailand, Arab, Hindi, dll.) -> transliterasi Latin fonetik.")
                    appendLine("- Kembalikan array `romanizations` berisi TEPAT ${chunk.size} elemen sesuai urutan input 1-per-1.")
                    appendLine("- Untuk baris murni Latin atau simbol musik (♪), kembalikan \"\". Jangan sertakan nomor baris.")
                    appendLine()
                    appendLine("LIRIK (${chunk.size} baris):")
                    append(numberedInput)
                }

                val modelsToTry = listOf(romanizationModel, fallbackRomanizationModel)
                var chunkDone = false
                var lastErr: Exception? = null
                for ((attemptIdx, model) in modelsToTry.withIndex()) {
                    try {
                        if (attemptIdx > 0) delay(650L)
                        val response = withTimeout(50_000L) {
                            model.generateContent(prompt)
                        }
                        val rawJson = response.text?.takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("Respon romanisasi kosong.")
                        val obj = extractJsonObject(rawJson)
                        val needsRom = obj.optBoolean("needsRomanization", true)
                        val script = obj.optString("scriptLabel", "").trim()
                        if (script.isNotBlank()) detectedScript = script
                        val arr = obj.optJSONArray("romanizations")
                            ?: throw IllegalStateException("Format romanisasi tidak valid.")
                        val chunkList = List(chunk.size) { idx ->
                            if (idx < arr.length()) {
                                arr.optString(idx, "").trim().replace(Regex("^\\d+[.)]\\s*"), "")
                            } else ""
                        }
                        if (needsRom && chunkList.any { it.isNotBlank() }) needsRomAny = true
                        allRomanized.addAll(chunkList)
                        chunkDone = true
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastErr = e
                    }
                }
                if (!chunkDone) throw lastErr ?: IllegalStateException("Gagal membuat romanisasi lirik.")
            }

            val normalized = List(lines.size) { idx -> allRomanized.getOrElse(idx) { "" } }
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
