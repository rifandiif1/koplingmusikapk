package com.example.ai

import android.content.Context
import android.util.Log
import com.example.model.SongMeaningAnalysis
import com.example.model.SongMeaningLineInsight
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

private const val TAG = "SongMeaningAnalyzer"
private const val CACHE_DIR_NAME = "lyrics_ai_cache"

/**
 * Generates an insightful Indonesian song meaning breakdown ("Bedah Makna Lagu")
 * via Firebase AI Logic (Gemini 3.8 Flash with Gemini 3.5 Flash-Lite fallback) with
 * structured JSON schema, permissive song-lyrics safety thresholds, support for tracks
 * both with and without fetched LrcLib lyrics, and persistent offline local disk caching.
 */
object SongMeaningAnalyzer {
    private val memoryCache = ConcurrentHashMap<String, SongMeaningAnalysis>()

    @Volatile
    private var cacheDir: File? = null

    private val meaningSafetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.ONLY_HIGH),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.ONLY_HIGH)
    )

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
                            description = "Potongan 1-2 baris lirik atau tema kunci yang paling kuat dan bermakna dari lagu."
                        ),
                        "explanation" to Schema.string(
                            description = "Penjelasan arti tersirat, metafora, atau rasa di balik potongan lirik/tema tersebut dalam Bahasa Indonesia yang santai dan mudah dicerna."
                        )
                    )
                ),
                description = "2 sampai 4 potongan lirik atau simbol kunci paling ikonik beserta bedah maknanya."
            ),
            "takeaway" to Schema.string(
                description = "1-2 kalimat penutup tentang pesan utama atau alasan kenapa lagu ini terasa relate buat pendengar."
            )
        ),
        optionalProperties = listOf("moodTags", "emotionalArc", "keyLineInsights", "takeaway")
    )

    private val meaningModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = meaningSchema
                temperature = 0.45f
            },
            safetySettings = meaningSafetySettings
        )
    }

    private val fallbackMeaningModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = meaningSchema
                temperature = 0.45f
            },
            safetySettings = meaningSafetySettings
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

    private fun extractJsonObject(raw: String): JSONObject {
        val trimmed = raw.trim()
        runCatching { return JSONObject(trimmed) }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return JSONObject(trimmed.substring(start, end + 1))
        }
        throw IllegalStateException("Format JSON analisis makna tidak valid.")
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

        val headline = obj.optString("headline", "").trim()
        val story = obj.optString("storyOverview", "").trim()
        val arc = obj.optString("emotionalArc", "").trim()
        val takeaway = obj.optString("takeaway", "").trim()

        return SongMeaningAnalysis(
            headline = headline.ifBlank { story.substringBefore(".").take(120) },
            moodTags = tags,
            storyOverview = story.ifBlank { headline },
            emotionalArc = arc,
            keyLineInsights = insights,
            takeaway = takeaway
        )
    }

    suspend fun analyzeSongMeaning(
        trackId: String,
        trackTitle: String,
        artist: String,
        album: String = "",
        lyricsLines: List<String> = emptyList(),
        forceRefresh: Boolean = false
    ): Result<SongMeaningAnalysis> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            getCached(trackId)?.let { cached ->
                return@withContext Result.success(cached)
            }
        }

        val cleanedLyrics = lyricsLines
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "♪" && it != "♫" }
            .take(100)
            .joinToString("\n")

        val prompt = buildString {
            appendLine("Bedah makna, cerita, nuansa emosi, dan metafora di balik lagu berikut.")
            appendLine("Judul: $trackTitle")
            appendLine("Artis: $artist")
            if (album.isNotBlank()) appendLine("Album: $album")
            appendLine("GAYA PENULISAN:")
            appendLine("- Tulis seluruh hasil dalam Bahasa Indonesia sehari-hari yang santai, hangat, mengalir, dan enak dibaca (hindari gaya bahasa kaku/akademik).")
            if (cleanedLyrics.isNotBlank()) {
                appendLine("- Fokus ke makna nyata dari teks lirik di bawah, jangan mengarang fakta biografi artis yang belum pasti.")
                appendLine("- Untuk `keyLineInsights`, kutip langsung 2-4 potongan baris paling ikonik dari lirik di bawah lalu jelaskan makna tersirat/emosinya.")
                appendLine()
                appendLine("LIRIK LAGU:")
                append(cleanedLyrics)
            } else {
                appendLine("- Teks lirik lengkap tidak tersedia di perangkat. Bedah makna, tema besar, nuansa emosional, dan pesan lagu berdasarkan pengetahuan tentang lagu \"$trackTitle\" oleh $artist.")
                appendLine("- Untuk `keyLineInsights`, sorot 2-3 tema, metafora, atau bagian paling berkesan dari lagu tersebut beserta penjelasannya (tanpa mengarang kutipan lirik palsu).")
            }
        }

        val modelsToTry = listOf(meaningModel, fallbackMeaningModel)
        var lastError: Exception? = null

        for ((attemptIdx, model) in modelsToTry.withIndex()) {
            try {
                if (attemptIdx > 0) delay(700L)
                val response = withTimeout(55_000L) {
                    model.generateContent(prompt)
                }
                val rawJson = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Respon bedah makna kosong dari layanan AI.")

                val parsed = parseMeaningJson(extractJsonObject(rawJson))
                if (parsed.headline.isBlank() && parsed.storyOverview.isBlank()) {
                    throw IllegalStateException("Format analisis makna lagu tidak lengkap.")
                }
                saveCache(trackId, parsed)
                return@withContext Result.success(parsed)
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Song meaning attempt ${attemptIdx + 1} timed out for $trackTitle")
                lastError = Exception("Bedah makna melewati batas waktu. Ketuk Coba Lagi sebentar ya.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Song meaning attempt ${attemptIdx + 1} failed for $trackTitle: ${e.message}", e)
                lastError = e
            }
        }

        val finalErr = lastError
        val message = if (finalErr != null) {
            LyricsTranslator.formatFriendlyAiError(finalErr, "Gagal membedah makna lagu.")
        } else {
            "Gagal membedah makna lagu."
        }
        Result.failure(Exception(message))
    }
}
