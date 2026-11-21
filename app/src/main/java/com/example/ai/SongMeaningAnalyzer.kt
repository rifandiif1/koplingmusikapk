package com.example.ai

import android.content.Context
import android.util.Log
import com.example.model.SongMeaningAnalysis
import com.example.model.SongMeaningLineInsight
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

private const val TAG = "SongMeaningAnalyzer"
private const val CACHE_DIR_NAME = "lyrics_ai_cache"

/**
 * Generates an insightful Indonesian song meaning breakdown ("Bedah Makna Lagu")
 * via Firebase AI Logic (Gemini 3.8 Flash) with structured JSON schema and local
 * disk caching inside `lyrics_ai_cache/` so analyzed songs remain available offline.
 */
object SongMeaningAnalyzer {
    private val memoryCache = ConcurrentHashMap<String, SongMeaningAnalysis>()

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

    fun clearMemoryCache() {
        memoryCache.clear()
    }

    private val meaningSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(
                description = "Satu kalimat singkat yang merangkum inti cerita atau perasaan utama lagu dalam Bahasa Indonesia santai dan kena di hati."
            ),
            "moodTags" to Schema.array(
                items = Schema.string(),
                description = "2 sampai 4 tag nuansa/emosi singkat (1-2 kata per tag) dalam Bahasa Indonesia, misalnya 'Nostalgia', 'Patah Hati', 'Rindu Diam-diam', 'Semangat'."
            ),
            "storyOverview" to Schema.string(
                description = "Penjelasan cerita dan konteks keseluruhan lagu dalam 2 paragraf pendek berbahasa Indonesia santai yang enak dibaca (bukan bahasa kaku/resmi)."
            ),
            "emotionalArc" to Schema.string(
                description = "Alur perubahan emosi dari awal lagu (verse), puncak perasaan (reff/chorus), sampai penutupnya dalam 2-3 kalimat ringkas."
            ),
            "keyLineInsights" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "lyricExcerpt" to Schema.string(
                            description = "Potongan 1-2 baris lirik asli yang paling kuat atau penuh metafora dari lagu."
                        ),
                        "explanation" to Schema.string(
                            description = "Penjelasan arti tersirat, metafora, atau rasa di balik potongan lirik tersebut dalam Bahasa Indonesia yang santai dan mudah dicerna."
                        )
                    )
                ),
                description = "2 sampai 4 potongan lirik kunci paling ikonik beserta bedah maknanya."
            ),
            "takeaway" to Schema.string(
                description = "1-2 kalimat penutup tentang pesan utama atau alasan kenapa lagu ini terasa relate buat pendengar."
            )
        )
    )

    private val meaningModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = meaningSchema
                temperature = 0.45f
            }
        )
    }

    private fun cacheFileFor(trackId: String): File? {
        val dir = cacheDir ?: return null
        val safeKey = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
        return File(dir, "meaning_$safeKey.json")
    }

    fun getCached(trackId: String): SongMeaningAnalysis? {
        memoryCache[trackId]?.let { return it }
        val file = cacheFileFor(trackId) ?: return null
        if (!file.exists()) return null
        return runCatching {
            val obj = JSONObject(file.readText())
            parseMeaningJson(obj).also { parsed ->
                if (parsed.headline.isNotBlank() && parsed.storyOverview.isNotBlank()) {
                    memoryCache[trackId] = parsed
                }
            }
        }.getOrNull()?.takeIf { it.headline.isNotBlank() && it.storyOverview.isNotBlank() }
    }

    private fun saveCache(trackId: String, analysis: SongMeaningAnalysis) {
        memoryCache[trackId] = analysis
        val file = cacheFileFor(trackId) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val insightsArr = JSONArray().apply {
                analysis.keyLineInsights.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("lyricExcerpt", item.lyricExcerpt)
                            put("explanation", item.explanation)
                        }
                    )
                }
            }
            val obj = JSONObject().apply {
                put("headline", analysis.headline)
                put("moodTags", JSONArray(analysis.moodTags))
                put("storyOverview", analysis.storyOverview)
                put("emotionalArc", analysis.emotionalArc)
                put("keyLineInsights", insightsArr)
                put("takeaway", analysis.takeaway)
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(obj.toString())
                tmp.delete()
            }
        }.onFailure { err ->
            Log.w(TAG, "Failed to persist song meaning cache for $trackId", err)
        }
    }

    private fun parseMeaningJson(obj: JSONObject): SongMeaningAnalysis {
        val tagsArr = obj.optJSONArray("moodTags")
        val tags = if (tagsArr != null) {
            (0 until tagsArr.length())
                .map { tagsArr.optString(it, "").trim() }
                .filter { it.isNotBlank() }
                .take(4)
        } else emptyList()

        val insightsArr = obj.optJSONArray("keyLineInsights")
        val insights = if (insightsArr != null) {
            (0 until insightsArr.length()).mapNotNull { idx ->
                val item = insightsArr.optJSONObject(idx) ?: return@mapNotNull null
                val excerpt = item.optString("lyricExcerpt", "").trim()
                val explanation = item.optString("explanation", "").trim()
                if (excerpt.isNotBlank() && explanation.isNotBlank()) {
                    SongMeaningLineInsight(excerpt, explanation)
                } else null
            }.take(4)
        } else emptyList()

        return SongMeaningAnalysis(
            headline = obj.optString("headline", "").trim(),
            moodTags = tags,
            storyOverview = obj.optString("storyOverview", "").trim(),
            emotionalArc = obj.optString("emotionalArc", "").trim(),
            keyLineInsights = insights,
            takeaway = obj.optString("takeaway", "").trim()
        )
    }

    suspend fun analyzeSongMeaning(
        trackId: String,
        trackTitle: String,
        artist: String,
        lyricsLines: List<String>,
        forceRefresh: Boolean = false
    ): Result<SongMeaningAnalysis> = withContext(Dispatchers.IO) {
        if (lyricsLines.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Belum ada lirik yang bisa dibedah untuk lagu ini.")
            )
        }
        if (!forceRefresh) {
            getCached(trackId)?.let { cached ->
                return@withContext Result.success(cached)
            }
        }

        try {
            val cleanedLyrics = lyricsLines
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "♪" && it != "♫" }
                .take(120)
                .joinToString("\n")

            val prompt = buildString {
                appendLine("Bedah makna, cerita, dan metafora di balik lirik lagu berikut.")
                appendLine("Judul: $trackTitle")
                appendLine("Artis: $artist")
                appendLine("GAYA PENULISAN:")
                appendLine("- Tulis seluruh hasil dalam Bahasa Indonesia yang santai, hangat, mengalir, dan enak dibaca (hindari gaya bahasa kaku seperti laporan resmi/esai akademik).")
                appendLine("- Fokus ke makna nyata dari teks liriknya, jangan mengarang fakta biografi artis yang belum pasti.")
                appendLine("- Untuk `keyLineInsights`, kutip langsung potongan baris asli dari lirik di bawah lalu jelaskan makna tersirat/emosinya.")
                appendLine()
                appendLine("LIRIK LAGU:")
                append(cleanedLyrics)
            }

            val response = withTimeout(60_000L) {
                meaningModel.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(
                    IllegalStateException("Respon bedah makna kosong dari layanan AI.")
                )

            val parsed = parseMeaningJson(JSONObject(rawJson))
            if (parsed.headline.isBlank() || parsed.storyOverview.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Format analisis makna lagu tidak lengkap.")
                )
            }
            saveCache(trackId, parsed)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Song meaning analysis timed out", e)
            Result.failure(Exception("Bedah makna melewati batas waktu (60 detik). Coba lagi sebentar ya."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI song meaning analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal membedah makna lagu."))
        }
    }
}
