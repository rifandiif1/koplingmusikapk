package com.example.data.remote

import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.LyricLine
import com.example.model.LyricsState
import com.example.model.Track
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---- iTunes Search API Models ----
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesItem> = emptyList()
)

data class ItunesItem(
    val wrapperType: String? = null,
    val trackId: Long? = null,
    val collectionId: Long? = null,
    val artistId: Long? = null,
    val trackName: String? = null,
    val collectionName: String? = null,
    val artistName: String? = null,
    val artworkUrl100: String? = null,
    val previewUrl: String? = null,
    val trackTimeMillis: Long? = null,
    val primaryGenreName: String? = null,
    val trackExplicitness: String? = null,
    val releaseDate: String? = null,
    val trackCount: Int? = null
)

interface ItunesApi {
    @GET("search")
    suspend fun searchTracks(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25,
        @Query("country") country: String = "ID"
    ): ItunesSearchResponse

    @GET("search")
    suspend fun searchAlbums(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 15,
        @Query("country") country: String = "ID"
    ): ItunesSearchResponse

    @GET("lookup")
    suspend fun lookupAlbumTracks(
        @Query("id") collectionId: String,
        @Query("entity") entity: String = "song"
    ): ItunesSearchResponse
}

// ---- Deezer Public API Models ----
data class DeezerChartResponse(
    val tracks: DeezerTrackList? = null,
    val albums: DeezerAlbumList? = null,
    val artists: DeezerArtistList? = null
)

data class DeezerTrackList(
    val data: List<DeezerTrackDto> = emptyList()
)

data class DeezerAlbumList(
    val data: List<DeezerAlbumDto> = emptyList()
)

data class DeezerArtistList(
    val data: List<DeezerArtistDto> = emptyList()
)

data class DeezerTrackDto(
    val id: Long = 0,
    val title: String? = null,
    val duration: Int = 0,
    val preview: String? = null,
    @Json(name = "explicit_lyrics") val explicitLyrics: Boolean = false,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null
)

data class DeezerArtistDto(
    val id: Long = 0,
    val name: String? = null,
    @Json(name = "picture_big") val pictureBig: String? = null,
    @Json(name = "picture_medium") val pictureMedium: String? = null
)

data class DeezerAlbumDto(
    val id: Long = 0,
    val title: String? = null,
    @Json(name = "cover_big") val coverBig: String? = null,
    @Json(name = "cover_medium") val coverMedium: String? = null,
    val artist: DeezerArtistDto? = null
)

interface DeezerApi {
    @GET("chart")
    suspend fun getGlobalChart(
        @Query("limit") limit: Int = 20
    ): DeezerChartResponse

    @GET("search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25
    ): DeezerTrackList
}

// ---- LRCLIB Open Synced Lyrics API ----
data class LrcLibItem(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

interface LrcLibApi {
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): List<LrcLibItem>
}

// ---- Network Client Factory ----
object KoplingfyNetwork {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Koplingfy-Android/1.0")
                .build()
            chain.proceed(request)
        }
        .build()

    val itunesApi: ItunesApi = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ItunesApi::class.java)

    val deezerApi: DeezerApi = Retrofit.Builder()
        .baseUrl("https://api.deezer.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(DeezerApi::class.java)

    val lrcLibApi: LrcLibApi = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(LrcLibApi::class.java)
}

// ---- Mappers & LRC Parser ----
fun ItunesItem.toTrack(rank: Int? = null): Track? {
    val tid = trackId ?: return null
    val name = trackName?.takeIf { it.isNotBlank() } ?: return null
    val art = artworkUrl100?.replace("100x100bb", "600x600bb")
    val year = releaseDate?.take(4) ?: ""
    return Track(
        id = "itunes_$tid",
        title = name,
        artist = artistName ?: "Unknown Artist",
        album = collectionName ?: "Single",
        artworkUrl = art,
        streamUrl = previewUrl,
        durationMs = trackTimeMillis ?: 30_000L,
        genre = primaryGenreName ?: "Music",
        isLocal = false,
        isExplicit = trackExplicitness == "explicit",
        releaseYear = year,
        rank = rank
    )
}

fun ItunesItem.toAlbumSummary(): AlbumSummary? {
    val cid = collectionId ?: return null
    val title = collectionName?.takeIf { it.isNotBlank() } ?: return null
    return AlbumSummary(
        id = "itunes_album_$cid",
        title = title,
        artist = artistName ?: "Unknown Artist",
        artworkUrl = artworkUrl100?.replace("100x100bb", "600x600bb"),
        genre = primaryGenreName ?: "Album",
        releaseYear = releaseDate?.take(4) ?: "",
        trackCount = trackCount ?: 0
    )
}

fun DeezerTrackDto.toTrack(rank: Int? = null): Track? {
    val name = title?.takeIf { it.isNotBlank() } ?: return null
    return Track(
        id = "deezer_$id",
        title = name,
        artist = artist?.name ?: "Unknown Artist",
        album = album?.title ?: "Single",
        artworkUrl = album?.coverBig ?: album?.coverMedium ?: artist?.pictureBig,
        streamUrl = preview?.takeIf { it.isNotBlank() },
        durationMs = if (duration > 0) duration * 1000L else 30_000L,
        genre = "Global Chart",
        isLocal = false,
        isExplicit = explicitLyrics,
        rank = rank
    )
}

fun DeezerAlbumDto.toAlbumSummary(): AlbumSummary? {
    val name = title?.takeIf { it.isNotBlank() } ?: return null
    return AlbumSummary(
        id = "deezer_album_$id",
        title = name,
        artist = artist?.name ?: "Featured Artist",
        artworkUrl = coverBig ?: coverMedium,
        genre = "Top Album"
    )
}

fun DeezerArtistDto.toArtistSummary(): ArtistSummary? {
    val artistName = name?.takeIf { it.isNotBlank() } ?: return null
    return ArtistSummary(
        id = "deezer_artist_$id",
        name = artistName,
        artworkUrl = pictureBig ?: pictureMedium,
        genre = "Top Artist"
    )
}

object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val parsed = mutableListOf<LyricLine>()
        lrcContent.lineSequence().forEach { rawLine ->
            val match = lineRegex.find(rawLine.trim())
            if (match != null) {
                val mins = match.groupValues[1].toLongOrNull() ?: 0L
                val secs = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val millis = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    parsed.add(LyricLine((mins * 60 + secs) * 1000L + millis, text))
                }
            }
        }
        return parsed.sortedBy { it.timestampMs }
    }

    fun fromLrcItem(item: LrcLibItem?): LyricsState {
        if (item == null) {
            return LyricsState(error = "Lirik tidak ditemukan untuk lagu ini.")
        }
        if (item.instrumental) {
            return LyricsState(isInstrumental = true)
        }
        val synced = parse(item.syncedLyrics)
        if (synced.isNotEmpty()) {
            return LyricsState(syncedLines = synced, plainLyrics = item.plainLyrics)
        }
        if (!item.plainLyrics.isNullOrBlank()) {
            return LyricsState(plainLyrics = item.plainLyrics)
        }
        return LyricsState(error = "Lirik belum tersedia untuk lagu ini.")
    }
}
