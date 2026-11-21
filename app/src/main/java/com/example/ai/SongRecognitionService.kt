package com.example.ai

import android.util.Log
import com.example.model.Track
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "SongRecognitionService"

data class RecognizedSongMatch(
    val title: String,
    val artist: String,
    val confidencePercent: Int,
    val matchedLyricOrClue: String = "",
    val reason: String = "",
    val resolvedTrack: Track? = null
)

data class SongRecognitionResult(
    val found: Boolean,
    val heardSummary: String = "",
    val matches: List<RecognizedSongMatch> = emptyList()
)

/**
 * Powered by Firebase AI Logic (Gemini 3.8 Flash) for:
 * 1. Shazam-style audio / ambient music / singing / humming recognition via multimodal inlineData.
 * 2. Song search from partial, misremembered, or descriptive lyric snippets in any language.
 */
object SongRecognitionService {

    private val songMatchSchema = Schema.obj(
        properties = mapOf(
            "found" to Schema.boolean(
                description = "True jika berhasil menemukan minimal 1 kandidat lagu yang cocok."
            ),
            "heardSummary" to Schema.string(
                description = "Ringkasan singkat (1 kalimat Bahasa Indonesia santai) tentang suara/melodi/potongan lirik yang dikenali atau dianalisis."
            ),
            "matches" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Judul resmi lagu"),
                        "artist" to Schema.string(description = "Nama penyanyi atau band utama"),
                        "confidencePercent" to Schema.integer(description = "Tingkat keyakinan 0 sampai 100"),
                        "matchedLyricOrClue" to Schema.string(
                            description = "Potongan 1 baris lirik kunci yang paling cocok dari lagu tersebut"
                        ),
                        "reason" to Schema.string(
                            description = "Alasan singkat (maks 1 kalimat Bahasa Indonesia santai) kenapa lagu ini cocok"
                        )
                    )
                ),
                description = "Daftar kandidat lagu terbaik, diurutkan dari yang paling cocok."
            )
        )
    )

    private val recognitionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = songMatchSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun recognizeFromAudioBytes(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4"
    ): Result<SongRecognitionResult> = withContext(Dispatchers.IO) {
        if (audioBytes.size < 1024) {
            return@withContext Result.failure(
                IllegalArgumentException("Rekaman audio terlalu singkat atau kosong. Dekatkan HP ke sumber suara lalu coba lagi.")
            )
        }
        if (audioBytes.size > 18 * 1024 * 1024) {
            return@withContext Result.failure(
                IllegalArgumentException("Ukuran rekaman audio terlalu besar.")
            )
        }

        try {
            val prompt = """
                Kamu adalah sistem pengenal musik cerdas seperti Shazam.
                Dengarkan rekaman audio berikut dengan teliti. Audio bisa berupa:
                1. Lagu yang sedang diputar di speaker, TV, radio, atau kafe (musik asli beserta vokal/instrumen).
                2. Seseorang yang sedang menyanyikan, bersenandung (humming), atau bersiul melodi sebuah lagu.
                
                TUGAS:
                - Identifikasi lagu yang terdengar (mendukung lagu Indonesia, Dangdut/Koplo, Pop, Barat, K-Pop, J-Pop, anime, dan internasional).
                - Jika hanya ada suara hening, noise acak, atau percakapan tanpa musik/nyanyian yang jelas, set `found = false` dan jelaskan di `heardSummary` dalam Bahasa Indonesia santai.
                - Jika lagu dikenali, set `found = true`, berikan hingga 3 kandidat terbaik di `matches` diurutkan dari yang paling yakin.
                - Tulis `heardSummary` dan `reason` dalam Bahasa Indonesia yang santai dan ringkas.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                recognitionModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(
                    IllegalStateException("Respon kosong dari layanan pengenalan audio AI.")
                )

            Result.success(parseRecognitionJson(JSONObject(rawJson), maxMatches = 3))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio recognition timed out", e)
            Result.failure(Exception("Pengenalan audio melewati batas waktu. Pastikan koneksi stabil lalu coba lagi."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI audio recognition failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal mengenali audio saat ini."))
        }
    }

    suspend fun searchByLyricsText(lyricQuery: String): Result<SongRecognitionResult> = withContext(Dispatchers.IO) {
        val cleaned = lyricQuery.trim()
        if (cleaned.length < 3) {
            return@withContext Result.failure(
                IllegalArgumentException("Ketik minimal beberapa kata lirik atau petunjuk lagu.")
            )
        }

        try {
            val prompt = """
                Kamu adalah mesin pencari lagu dari potongan lirik dan ingatan samar pendengar.
                Pengguna mengetik potongan lirik (bisa tidak lengkap, typo, bahasa Indonesia, Inggris, Jepang/Romaji, Korea/Romanisasi, Jawa/Koplo, atau deskripsi samar cerita lagunya):
                
                INPUT PENGGUNA:
                "$cleaned"
                
                TUGAS:
                - Temukan 3 sampai 5 lagu nyata yang paling cocok dengan potongan lirik atau deskripsi tersebut.
                - Prioritaskan kecocokan lirik paling akurat di posisi pertama.
                - Sertakan potongan baris lirik yang cocok di `matchedLyricOrClue`.
                - Tulis `heardSummary` dan `reason` dalam Bahasa Indonesia santai & membantu.
                - Jangan mengarang lagu fiktif. Jika benar-benar tidak ada yang cocok, set `found = false`.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                recognitionModel.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(
                    IllegalStateException("Respon kosong dari pencarian lirik AI.")
                )

            Result.success(parseRecognitionJson(JSONObject(rawJson), maxMatches = 5))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyrics search timed out", e)
            Result.failure(Exception("Pencarian lirik melewati batas waktu. Coba lagi sebentar ya."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI lyrics search failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gagal mencari lagu dari lirik."))
        }
    }

    private fun parseRecognitionJson(obj: JSONObject, maxMatches: Int): SongRecognitionResult {
        val found = obj.optBoolean("found", false)
        val summary = obj.optString("heardSummary", "").trim()
        val arr = obj.optJSONArray("matches")
        val matches = if (arr != null) {
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title", "").trim()
                val artist = item.optString("artist", "").trim()
                if (title.isBlank() || artist.isBlank()) return@mapNotNull null
                RecognizedSongMatch(
                    title = title,
                    artist = artist,
                    confidencePercent = item.optInt("confidencePercent", 75).coerceIn(1, 100),
                    matchedLyricOrClue = item.optString("matchedLyricOrClue", "").trim(),
                    reason = item.optString("reason", "").trim()
                )
            }.take(maxMatches)
        } else emptyList()

        return SongRecognitionResult(
            found = found && matches.isNotEmpty(),
            heardSummary = summary,
            matches = matches
        )
    }
}
