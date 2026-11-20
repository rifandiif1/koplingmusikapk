package com.example.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LyricsTranslator"
private const val CACHE_DIR_NAME = "lyrics_ai_cache"

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
    val romanizedLines: List<String> = emptyList(),
    val needsRomanization: Boolean = true,
    val scriptLabel: String = ""
)

/**
 * Translates song lyrics line-by-line into natural Indonesian and generates phonetic
 * romanization (Romaji, Korean Revised Romanization, Pinyin, Cyrillic, Thai, etc.)
 * via Firebase AI Logic (Gemini 3.8 Flash) with persistent local disk caching so
 * translations and romanizations stay available across app restarts and offline.
 */
object LyricsTranslator {
    private val memoryCache = ConcurrentHashMap<String, CachedLyricsAiData>()

    @Volatile
    private var cacheDir: File? = null

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
        )
    )

    private val romanizationSchema = Schema.obj(
        properties = mapOf(
            "needsRomanization" to Schema.boolean(
                description = "True if the lyrics contain non-Latin scripts such as Japanese (Kanji/Kana), Korean (Hangul), Chinese (Hanzi), Russian/Cyrillic, Thai, Arabic, Hindi, etc. False if already written entirely in Latin script."
            ),
            "scriptLabel" to Schema.string(
                description = "Short label of the detected script/romanization style in Indonesian, e.g. 'Romaji', 'Hangul', 'Pinyin', 'Kiril', 'Thai', or 'Latin'."
            ),
            "romanizations" to Schema.array(
                items = Schema.string(),
                description = "Line-by-line phonetic Latin reading (Romaji/Hangul Romanization/Pinyin/etc.) matching the exact count and order of input lines. For lines already purely Latin or music symbols, return an empty string."
            )
        )
    )

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            }
        )
    }

    private val romanizationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = romanizationSchema
                temperature = 0.1f
            }
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

            CachedLyricsAiData(
                lineCount = obj.optInt("lineCount", maxOf(transList.size, romList.size)),
                translatedLines = transList,
                detectedIndonesian = obj.optBoolean("detectedIndonesian", false),
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
     * Quick heuristic to detect if lyrics contain non-Latin scripts (Japanese, Korean, Chinese,
     * Cyrillic, Thai, Arabic, Devanagari, etc.) so we avoid unnecessary calls for pure Latin songs
     * while still supporting mixed-script tracks.
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
        if (existing != null && existing.translatedLines.size == lines.size && existing.translatedLines.isNotEmpty()) {
            return@withContext Result.success(
                TranslatedLyricsResult(
                    lines = existing.translatedLines,
                    detectedIndonesian = existing.detectedIndonesian
                )
            )
        }

        try {
            val numberedInput = lines.mapIndexed { idx, line ->
                "${idx + 1}. $line"
            }.joinToString("\n")

            val prompt = buildString {
                appendLine("Terjemahkan lirik lagu berikut ke Bahasa Indonesia yang natural, puitis, dan sesuai konteks lagunya.")
                appendLine("Judul: $trackTitle")
                appendLine("Artis: $artist")
                appendLine("ATURAN WAJIB:")
                appendLine("- Kembalikan array `translations` dengan JUMLAH ELEMEN YANG SAMA PERSIS (${lines.size} baris) dan urutan 1-per-1 sesuai input.")
                appendLine("- Jangan menggabungkan atau menghapus baris. Jika baris hanya berupa simbol musik (♪) atau kosong, kembalikan string kosong \"\".")
                appendLine("- Jika baris sudah berbahasa Indonesia, pertahankan atau rapikan maknanya secara singkat.")
                appendLine("- Jangan sertakan nomor baris pada hasil terjemahan.")
                appendLine()
                appendLine("LIRIK (${lines.size} baris):")
                append(numberedInput)
            }

            val response = withTimeout(60_000L) {
                translationModel.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(IllegalStateException("Respon terjemahan kosong dari layanan AI."))

            val obj = JSONObject(rawJson)
            val alreadyId = obj.optBoolean("alreadyIndonesian", false)
            val arr = obj.optJSONArray("translations")
                ?: return@withContext Result.failure(IllegalStateException("Format terjemahan tidak valid."))

            val translated = List(lines.size) { idx ->
                if (idx < arr.length()) arr.optString(idx, "").trim() else ""
            }
            val merged = (existing ?: CachedLyricsAiData(lineCount = lines.size)).copy(
                lineCount = lines.size,
                translatedLines = translated,
                detectedIndonesian = alreadyId
            )
            saveCacheEntry(trackId, merged)
            Result.success(TranslatedLyricsResult(lines = translated, detectedIndonesian = alreadyId))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyrics translation timed out", e)
            Result.failure(Exception("Terjemahan lirik melewati batas waktu (60 detik). Silakan coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI lyrics translation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal menerjemahkan lirik ke Bahasa Indonesia."))
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
            val numberedInput = lines.mapIndexed { idx, line ->
                "${idx + 1}. $line"
            }.joinToString("\n")

            val prompt = buildString {
                appendLine("Buat romanisasi / pelafalan alfabet Latin yang akurat dan mudah dinyanyikan untuk lirik lagu berikut.")
                appendLine("Judul: $trackTitle")
                appendLine("Artis: $artist")
                appendLine("ATURAN WAJIB:")
                appendLine("- Bahasa Jepang -> gunakan Romaji Hepburn natural dengan spasi antar kata yang enak dibaca saat bernyanyi.")
                appendLine("- Bahasa Korea -> gunakan Revised Romanization of Korean yang natural.")
                appendLine("- Bahasa Mandarin -> gunakan Pinyin yang mudah dibaca (spasi per kata/frasa).")
                appendLine("- Bahasa non-Latin lain (Rusia/Kiril, Thailand, Arab, Hindi, dll.) -> gunakan transliterasi Latin fonetik yang natural.")
                appendLine("- Kembalikan array `romanizations` dengan JUMLAH ELEMEN YANG SAMA PERSIS (${lines.size} baris) dan urutan 1-per-1 sesuai input.")
                appendLine("- Jika suatu baris sudah murni huruf Latin (Inggris/Indonesia) atau hanya simbol musik (♪), kembalikan string kosong \"\" untuk baris tersebut.")
                appendLine("- Jangan sertakan nomor baris pada hasil romanisasi.")
                appendLine()
                appendLine("LIRIK (${lines.size} baris):")
                append(numberedInput)
            }

            val response = withTimeout(60_000L) {
                romanizationModel.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(IllegalStateException("Respon romanisasi kosong dari layanan AI."))

            val obj = JSONObject(rawJson)
            val needsRom = obj.optBoolean("needsRomanization", true)
            val scriptLabel = obj.optString("scriptLabel", "Romaji").trim()
            val arr = obj.optJSONArray("romanizations")
                ?: return@withContext Result.failure(IllegalStateException("Format romanisasi tidak valid."))

            val romanized = List(lines.size) { idx ->
                if (idx < arr.length()) arr.optString(idx, "").trim() else ""
            }
            val merged = (existing ?: CachedLyricsAiData(lineCount = lines.size)).copy(
                lineCount = lines.size,
                romanizedLines = romanized,
                needsRomanization = needsRom && romanized.any { it.isNotBlank() },
                scriptLabel = scriptLabel
            )
            saveCacheEntry(trackId, merged)
            Result.success(
                RomanizedLyricsResult(
                    lines = romanized,
                    needsRomanization = merged.needsRomanization,
                    scriptLabel = scriptLabel
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyrics romanization timed out", e)
            Result.failure(Exception("Romanisasi lirik melewati batas waktu (60 detik). Silakan coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI lyrics romanization failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal membuat romanisasi lirik."))
        }
    }
}
