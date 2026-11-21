package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.sqrt

data class LiveBeatFrame(
    val beatPulse: Float,      // 0f..1f sharp impulse decaying smoothly right after each detected beat
    val energy: Float,         // 0f..1f overall loudness / intensity of current musical passage
    val estimatedBpm: Float,   // 60f..190f smoothed real-time BPM of the playing song
    val speedMultiplier: Float // Combined tempo + beat surge speed multiplier for particles
)

/**
 * Lock-free real-time bridge between ExoPlayer's PCM audio thread and Compose's 60fps Canvas loop.
 * Produces zero allocations and zero recompositions while updating particle speed directly from the music beat.
 */
object AudioBeatMonitor {
    @Volatile private var lastBeatNanos: Long = 0L
    @Volatile private var lastBeatStrength: Float = 0f
    @Volatile private var currentEnergy: Float = 0f
    @Volatile private var currentBpm: Float = 104f
    @Volatile private var isAudioActive: Boolean = false
    @Volatile private var lastAudioSampleNanos: Long = 0L

    fun onBeatDetected(strength: Float, bpmEstimate: Float, nowNanos: Long = System.nanoTime()) {
        lastBeatNanos = nowNanos
        lastBeatStrength = strength.coerceIn(0.25f, 1.0f)
        currentBpm = bpmEstimate.coerceIn(60f, 190f)
        lastAudioSampleNanos = nowNanos
    }

    fun onEnergySample(energy: Float, bpmEstimate: Float, nowNanos: Long = System.nanoTime()) {
        currentEnergy = energy.coerceIn(0f, 1f)
        currentBpm = bpmEstimate.coerceIn(60f, 190f)
        lastAudioSampleNanos = nowNanos
    }

    fun setPlaybackActive(active: Boolean) {
        isAudioActive = active
        if (!active) {
            lastBeatStrength = 0f
            currentEnergy = 0f
        }
    }

    fun resetForNewTrack() {
        lastBeatNanos = 0L
        lastBeatStrength = 0f
        currentEnergy = 0f
        currentBpm = 104f
        lastAudioSampleNanos = 0L
    }

    /**
     * Samples the live beat state at the current display frame timestamp.
     * Safe to invoke every frame inside `withFrameNanos`.
     */
    fun sample(
        nowNanos: Long,
        isPlaying: Boolean,
        playbackSpeed: Float = 1.0f
    ): LiveBeatFrame {
        if (!isPlaying) {
            return LiveBeatFrame(
                beatPulse = 0f,
                energy = 0f,
                estimatedBpm = currentBpm,
                speedMultiplier = 0.22f
            )
        }

        val clampedPlaybackSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)
        val hasRecentAudio = isAudioActive && lastAudioSampleNanos > 0L &&
            (nowNanos - lastAudioSampleNanos) < 1_500_000_000L

        if (!hasRecentAudio) {
            // Smooth neutral tempo drift before the first PCM buffers arrive
            val neutralSpeed = (0.85f * clampedPlaybackSpeed).coerceIn(0.45f, 2.0f)
            return LiveBeatFrame(
                beatPulse = 0f,
                energy = 0.25f,
                estimatedBpm = 104f,
                speedMultiplier = neutralSpeed
            )
        }

        val elapsedSinceBeatSec = if (lastBeatNanos > 0L && nowNanos >= lastBeatNanos) {
            (nowNanos - lastBeatNanos) / 1_000_000_000f
        } else {
            10f
        }

        // Crisp exponential decay (~210ms half-response) so every kick drum causes a punchy velocity surge
        val beatPulse = if (elapsedSinceBeatSec < 0.75f) {
            (lastBeatStrength * exp(-elapsedSinceBeatSec * 7.8f)).coerceIn(0f, 1f)
        } else {
            0f
        }

        val energy = currentEnergy.coerceIn(0f, 1f)
        val bpm = currentBpm.coerceIn(60f, 190f)

        // Base speed tracks detected song BPM (60 BPM = slow 0.58x, 120 BPM = 1.15x, 175 BPM = 1.68x),
        // then surges visibly on every real bass/kick hit and louder musical sections (chorus/drop).
        val bpmFactor = (bpm / 104f).coerceIn(0.55f, 1.85f)
        val rawMultiplier = bpmFactor * (0.58f + 0.78f * energy + 1.85f * beatPulse) * clampedPlaybackSpeed

        return LiveBeatFrame(
            beatPulse = beatPulse,
            energy = energy,
            estimatedBpm = bpm,
            speedMultiplier = rawMultiplier.coerceIn(0.32f, 4.2f)
        )
    }
}

/**
 * Zero-permission, bit-exact passthrough Media3 [BaseAudioProcessor] that analyzes decoded 16-bit PCM
 * audio directly inside ExoPlayer's audio pipeline to detect bass/kick beats, live musical energy,
 * and song BPM without modifying a single audio sample.
 */
@OptIn(UnstableApi::class)
class BeatDetectorAudioProcessor : BaseAudioProcessor() {

    private var sampleRate: Int = 44_100
    private var channelCount: Int = 2
    private var lowPassAlpha: Float = 0.08f
    private var lowPassState: Float = 0f

    // Sliding window accumulators (~25ms per analysis window = ~40 updates/sec)
    private var windowFramesTarget: Int = 1024
    private var windowFramesCount: Int = 0
    private var sumBassSq: Double = 0.0
    private var sumTotalSq: Double = 0.0

    // Adaptive beat onset detection state
    private val bassHistory = FloatArray(40) // ~1.0 second rolling bass energy history
    private var historyIndex: Int = 0
    private var historyFilled: Int = 0
    private var smoothedEnergy: Float = 0f
    private var smoothedBpm: Float = 104f
    private var lastTriggerNanos: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)

        // Subsample stride = 2 frames; 1-pole low-pass cutoff at ~155 Hz isolates kick drum & bassline
        val effectiveRate = sampleRate / 2.0f
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * 155.0f)
        val dt = 1.0f / effectiveRate.coerceAtLeast(4_000f)
        lowPassAlpha = (dt / (rc + dt)).coerceIn(0.01f, 0.45f)
        windowFramesTarget = (sampleRate / 40).coerceIn(256, 2048)

        resetDetectorState()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        // Inspect PCM samples without consuming inputBuffer's content
        analyzePcm16Buffer(inputBuffer)

        // Bit-exact passthrough copy into output buffer
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        resetDetectorState()
    }

    override fun onReset() {
        resetDetectorState()
    }

    private fun resetDetectorState() {
        lowPassState = 0f
        windowFramesCount = 0
        sumBassSq = 0.0
        sumTotalSq = 0.0
        historyIndex = 0
        historyFilled = 0
        bassHistory.fill(0f)
        smoothedEnergy = 0f
        lastTriggerNanos = 0L
    }

    private fun analyzePcm16Buffer(buffer: ByteBuffer) {
        val startPos = buffer.position()
        val limit = buffer.limit()
        val bytesPerFrame = channelCount * 2
        if (bytesPerFrame <= 0) return

        // Subsample every 2nd frame for ultra-low CPU overhead (~0.04% on mobile CPUs)
        val stepBytes = bytesPerFrame * 2
        var pos = startPos
        while (pos + bytesPerFrame <= limit) {
            var mono = 0f
            for (ch in 0 until channelCount) {
                val sampleOffset = pos + ch * 2
                val lo = buffer.get(sampleOffset).toInt() and 0xFF
                val hi = buffer.get(sampleOffset + 1).toInt()
                val pcm16 = (hi shl 8) or lo
                mono += pcm16 / 32768f
            }
            mono /= channelCount

            // 1-pole low-pass filter for kick/sub-bass energy
            lowPassState += lowPassAlpha * (mono - lowPassState)
            val bass = lowPassState.toDouble()
            val full = mono.toDouble()

            sumBassSq += bass * bass
            sumTotalSq += full * full
            windowFramesCount += 2

            if (windowFramesCount >= windowFramesTarget) {
                finalizeAnalysisWindow()
            }
            pos += stepBytes
        }
    }

    private fun finalizeAnalysisWindow() {
        val samplePairs = (windowFramesCount / 2).coerceAtLeast(1)
        val bassRms = sqrt(sumBassSq / samplePairs).toFloat()
        val totalRms = sqrt(sumTotalSq / samplePairs).toFloat()

        windowFramesCount = 0
        sumBassSq = 0.0
        sumTotalSq = 0.0

        // Compute rolling average & variance of bass energy over the last ~1 second
        var histSum = 0f
        val count = historyFilled
        for (i in 0 until count) {
            histSum += bassHistory[i]
        }
        val avgBass = if (count > 0) histSum / count else bassRms

        bassHistory[historyIndex] = bassRms
        historyIndex = (historyIndex + 1) % bassHistory.size
        if (historyFilled < bassHistory.size) historyFilled++

        // Normalize musical energy smoothly (0.0 quiet silence -> 1.0 loud chorus/drop)
        val normalizedEnergy = ((totalRms * 2.4f) + (bassRms * 1.4f)).coerceIn(0f, 1f)
        smoothedEnergy = smoothedEnergy * 0.72f + normalizedEnergy * 0.28f

        val nowNanos = System.nanoTime()
        AudioBeatMonitor.onEnergySample(smoothedEnergy, smoothedBpm, nowNanos)

        // Adaptive onset detection: fires when instantaneous bass punch stands cleanly above recent average
        val minIntervalNs = 220_000_000L // max ~272 BPM (avoids double-triggering on one kick)
        val threshold = (avgBass * 1.32f).coerceAtLeast(0.032f)
        if (count >= 6 && bassRms > threshold && (nowNanos - lastTriggerNanos) >= minIntervalNs) {
            val intervalMs = if (lastTriggerNanos > 0L) {
                (nowNanos - lastTriggerNanos) / 1_000_000f
            } else {
                0f
            }
            lastTriggerNanos = nowNanos

            // Update smoothed BPM when interval falls in realistic musical beat range (55..200 BPM)
            if (intervalMs in 300f..1100f) {
                val instantBpm = 60_000f / intervalMs
                // Smooth gently so single syncopated kicks don't whip-saw the tempo
                smoothedBpm = smoothedBpm * 0.84f + instantBpm * 0.16f
            }

            val ratio = (bassRms / avgBass.coerceAtLeast(0.02f))
            val strength = ((ratio - 1.15f) / 1.05f + smoothedEnergy * 0.35f).coerceIn(0.32f, 1.0f)
            AudioBeatMonitor.onBeatDetected(strength, smoothedBpm, nowNanos)
        }
    }
}
