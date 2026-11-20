package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Metrolist-style anti-throttling HTTP Range chunked DataSource for YouTube (`googlevideo.com`).
 *
 * YouTube's CDN throttles single open-ended HTTP audio requests (C.LENGTH_UNSET).
 * Splitting upstream reads into 512 KB HTTP Range requests over a pooled HTTP/2 OkHttp client
 * bypasses stream throttling so playback starts in ~0.5–1s and seeking stays instant.
 */
@OptIn(UnstableApi::class)
class YouTubeChunkedDataSource(
    private val upstream: HttpDataSource,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE
) : DataSource {

    private var directMode: Boolean = false
    private var currentSpec: DataSpec? = null
    private var currentPosition: Long = 0L
    private var bytesRemainingInRequest: Long = C.LENGTH_UNSET.toLong()
    private var totalContentLength: Long = C.LENGTH_UNSET.toLong()
    private var chunkBytesRemaining: Long = 0L
    private var upstreamOpened: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeUpstream()
        currentSpec = dataSpec
        currentPosition = dataSpec.position
        bytesRemainingInRequest = dataSpec.length
        totalContentLength = C.LENGTH_UNSET.toLong()
        chunkBytesRemaining = 0L

        val isGoogleVideo = dataSpec.uri.host?.contains("googlevideo.com", ignoreCase = true) == true
        val isSmallExplicitRange = dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length <= chunkSize

        if (!isGoogleVideo || isSmallExplicitRange) {
            directMode = true
            val opened = upstream.open(dataSpec)
            upstreamOpened = true
            return opened
        }

        directMode = false
        val firstChunkLen = if (bytesRemainingInRequest != C.LENGTH_UNSET.toLong()) {
            minOf(chunkSize, bytesRemainingInRequest)
        } else {
            chunkSize
        }

        val firstChunkSpec = dataSpec.buildUpon()
            .setPosition(currentPosition)
            .setLength(firstChunkLen)
            .build()

        val opened = try {
            upstream.open(firstChunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416) {
                return 0L
            }
            throw e
        }
        upstreamOpened = true

        totalContentLength = parseTotalLengthFromHeaders(upstream.responseHeaders)
        if (totalContentLength == C.LENGTH_UNSET.toLong() && opened != C.LENGTH_UNSET.toLong() && opened < firstChunkLen) {
            totalContentLength = currentPosition + opened
        }
        chunkBytesRemaining = when {
            opened != C.LENGTH_UNSET.toLong() -> opened
            totalContentLength != C.LENGTH_UNSET.toLong() ->
                minOf(firstChunkLen, (totalContentLength - currentPosition).coerceAtLeast(0L))
            else -> firstChunkLen
        }

        return when {
            bytesRemainingInRequest != C.LENGTH_UNSET.toLong() -> {
                if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                    minOf(bytesRemainingInRequest, (totalContentLength - currentPosition).coerceAtLeast(0L))
                } else {
                    bytesRemainingInRequest
                }
            }
            totalContentLength != C.LENGTH_UNSET.toLong() -> {
                (totalContentLength - currentPosition).coerceAtLeast(0L)
            }
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (directMode) {
            return upstream.read(buffer, offset, length)
        }

        while (true) {
            if (bytesRemainingInRequest == 0L) {
                return C.RESULT_END_OF_INPUT
            }
            if (totalContentLength != C.LENGTH_UNSET.toLong() && currentPosition >= totalContentLength) {
                return C.RESULT_END_OF_INPUT
            }

            if (chunkBytesRemaining <= 0L) {
                if (!openNextChunk()) {
                    return C.RESULT_END_OF_INPUT
                }
            }

            val toRead = minOf(length.toLong(), chunkBytesRemaining).toInt()
            if (toRead <= 0) return C.RESULT_END_OF_INPUT

            val bytesRead = upstream.read(buffer, offset, toRead)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                // Current chunk ended early; if more bytes remain according to totalContentLength, advance to next chunk
                if (totalContentLength != C.LENGTH_UNSET.toLong() && currentPosition < totalContentLength) {
                    chunkBytesRemaining = 0L
                    continue
                }
                return C.RESULT_END_OF_INPUT
            }

            currentPosition += bytesRead
            chunkBytesRemaining = (chunkBytesRemaining - bytesRead).coerceAtLeast(0L)
            if (bytesRemainingInRequest != C.LENGTH_UNSET.toLong()) {
                bytesRemainingInRequest = (bytesRemainingInRequest - bytesRead).coerceAtLeast(0L)
            }
            return bytesRead
        }
    }

    private fun openNextChunk(): Boolean {
        val baseSpec = currentSpec ?: return false
        closeUpstream()

        var nextLen = chunkSize
        if (bytesRemainingInRequest != C.LENGTH_UNSET.toLong()) {
            nextLen = minOf(nextLen, bytesRemainingInRequest)
        }
        if (totalContentLength != C.LENGTH_UNSET.toLong()) {
            nextLen = minOf(nextLen, (totalContentLength - currentPosition).coerceAtLeast(0L))
        }
        if (nextLen <= 0L) return false

        val chunkSpec = baseSpec.buildUpon()
            .setPosition(currentPosition)
            .setLength(nextLen)
            .build()

        val opened = try {
            upstream.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 416) {
                return false
            }
            throw e
        }
        upstreamOpened = true

        if (totalContentLength == C.LENGTH_UNSET.toLong()) {
            totalContentLength = parseTotalLengthFromHeaders(upstream.responseHeaders)
            if (totalContentLength == C.LENGTH_UNSET.toLong() && opened != C.LENGTH_UNSET.toLong() && opened < nextLen) {
                totalContentLength = currentPosition + opened
            }
        }

        chunkBytesRemaining = when {
            opened != C.LENGTH_UNSET.toLong() -> opened
            totalContentLength != C.LENGTH_UNSET.toLong() ->
                minOf(nextLen, (totalContentLength - currentPosition).coerceAtLeast(0L))
            else -> nextLen
        }
        return chunkBytesRemaining > 0L
    }

    private fun parseTotalLengthFromHeaders(headers: Map<String, List<String>>): Long {
        val contentRange = headers.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?: return C.LENGTH_UNSET.toLong()
        val slash = contentRange.lastIndexOf('/')
        if (slash < 0 || slash + 1 >= contentRange.length) return C.LENGTH_UNSET.toLong()
        val totalStr = contentRange.substring(slash + 1).trim()
        return totalStr.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
    }

    override fun getUri(): Uri? = upstream.uri ?: currentSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        currentSpec = null
        closeUpstream()
    }

    private fun closeUpstream() {
        if (upstreamOpened) {
            upstreamOpened = false
            try {
                upstream.close()
            } catch (_: IOException) {
            }
        }
    }

    class Factory(
        private val upstreamFactory: HttpDataSource.Factory,
        private val chunkSize: Long = DEFAULT_CHUNK_SIZE
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return YouTubeChunkedDataSource(
                upstream = upstreamFactory.createDataSource(),
                chunkSize = chunkSize
            )
        }
    }

    companion object {
        // Metrolist's proven 512 KB chunk size for unthrottled YouTube CDN audio streaming
        const val DEFAULT_CHUNK_SIZE: Long = 512L * 1024L
    }
}
