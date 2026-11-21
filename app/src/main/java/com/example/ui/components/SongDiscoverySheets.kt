package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.RecognizedSongMatch
import com.example.ai.SongRecognitionResult
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ShazamStage {
    IDLE,
    LISTENING,
    ANALYZING,
    RESULT,
    ERROR
}

private const val MAX_RECORD_MS = 9_000L
private const val MIN_EARLY_STOP_MS = 2_500L

/**
 * Shazam-like Audio & Humming Song Recognition Sheet.
 * Captures real microphone audio (AAC/M4A) with live amplitude radar rings,
 * pauses playing music during capture, and identifies songs via Firebase AI Logic + YouTube Music.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShazamAudioSearchSheet(
    likedIds: Set<String>,
    onPauseMusicIfPlaying: () -> Unit,
    onRecognizeAudio: suspend (ByteArray, String) -> Result<SongRecognitionResult>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onTrackOptions: (Track) -> Unit,
    onFallbackSearchQuery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var stage by remember { mutableStateOf(ShazamStage.IDLE) }
    var amplitudeLevel by remember { mutableFloatStateOf(0f) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    var recognitionResult by remember { mutableStateOf<SongRecognitionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    var recorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFileRef by remember { mutableStateOf<File?>(null) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }

    fun releaseRecorderQuietly(deleteFile: Boolean = false) {
        val rec = recorderRef
        recorderRef = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.reset() }
            runCatching { rec.release() }
        }
        if (deleteFile) {
            outputFileRef?.let { runCatching { it.delete() } }
            outputFileRef = null
        }
    }

    fun finishCaptureAndAnalyze() {
        recordingJob?.cancel()
        recordingJob = null
        val clipFile = outputFileRef
        val rec = recorderRef
        recorderRef = null

        stage = ShazamStage.ANALYZING
        amplitudeLevel = 0f

        scope.launch {
            val bytesResult = withContext(Dispatchers.IO) {
                runCatching {
                    if (rec != null) {
                        runCatching { rec.stop() }
                        runCatching { rec.reset() }
                        runCatching { rec.release() }
                    }
                    val file = clipFile ?: throw IllegalStateException("File rekaman tidak ditemukan.")
                    val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
                    runCatching { file.delete() }
                    outputFileRef = null
                    bytes
                }
            }

            bytesResult.onFailure { err ->
                stage = ShazamStage.ERROR
                errorMessage = err.localizedMessage ?: "Gagal membaca rekaman mikrofon."
            }.onSuccess { bytes ->
                if (bytes.size < 1024) {
                    stage = ShazamStage.ERROR
                    errorMessage = "Suara terlalu singkat atau tidak tertangkap mikrofon. Coba rekam 5–8 detik di dekat sumber musik."
                    return@onSuccess
                }
                val res = onRecognizeAudio(bytes, "audio/mp4")
                res.onSuccess { result ->
                    recognitionResult = result
                    stage = ShazamStage.RESULT
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Gagal mengenali lagu."
                    stage = ShazamStage.ERROR
                }
            }
        }
    }

    fun startRecording() {
        permissionDenied = false
        errorMessage = null
        recognitionResult = null
        elapsedMs = 0
        amplitudeLevel = 0f
        onPauseMusicIfPlaying()

        val clipFile = File(context.cacheDir, "koplingfy_shazam_${System.currentTimeMillis()}.m4a")
        val started = runCatching {
            val rec = createMediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(clipFile.absolutePath)
                prepare()
                start()
            }
            recorderRef = rec
            outputFileRef = clipFile
        }

        if (started.isFailure) {
            releaseRecorderQuietly(deleteFile = true)
            stage = ShazamStage.ERROR
            errorMessage = "Mikrofon tidak dapat diakses (${started.exceptionOrNull()?.localizedMessage ?: "perangkat sibuk"})."
            return
        }

        stage = ShazamStage.LISTENING
        recordingJob?.cancel()
        recordingJob = scope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive && stage == ShazamStage.LISTENING) {
                val amp = runCatching { recorderRef?.maxAmplitude ?: 0 }.getOrDefault(0)
                val normalized = (amp / 18_000f).coerceIn(0f, 1f)
                amplitudeLevel = normalized
                val nowElapsed = (System.currentTimeMillis() - startTime).toInt()
                elapsedMs = nowElapsed
                if (nowElapsed >= MAX_RECORD_MS) {
                    finishCaptureAndAnalyze()
                    break
                }
                delay(70L)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            permissionDenied = true
            stage = ShazamStage.IDLE
        }
    }

    val triggerListenFlow: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recordingJob?.cancel()
            releaseRecorderQuietly(deleteFile = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            recordingJob?.cancel()
            releaseRecorderQuietly(deleteFile = true)
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Kenali Lagu",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Dengarkan musik sekitar, nyanyian, atau senandung",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        recordingJob?.cancel()
                        releaseRecorderQuietly(deleteFile = true)
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup Kenali Lagu")
                }
            }

            // Animated Radar Stage (shown when IDLE, LISTENING, ANALYZING, or ERROR)
            if (stage != ShazamStage.RESULT) {
                ShazamRadarButton(
                    stage = stage,
                    amplitudeLevel = amplitudeLevel,
                    progress = (elapsedMs.toFloat() / MAX_RECORD_MS.toFloat()).coerceIn(0f, 1f),
                    onClick = {
                        when (stage) {
                            ShazamStage.IDLE, ShazamStage.ERROR -> triggerListenFlow()
                            ShazamStage.LISTENING -> {
                                if (elapsedMs >= MIN_EARLY_STOP_MS) {
                                    finishCaptureAndAnalyze()
                                }
                            }
                            else -> Unit
                        }
                    }
                )

                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "shazam_status_text"
                ) { activeStage ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (activeStage) {
                            ShazamStage.IDLE -> {
                                Text(
                                    text = "Ketuk untuk Mulai Mendengarkan",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Dekatkan HP ke speaker, atau nyanyikan / senandungkan bagian reff selama beberapa detik.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                if (permissionDenied) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    ) {
                                        Text(
                                            text = "Izin mikrofon dibutuhkan agar Koplingfy bisa mendengarkan musik di sekitarmu.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            ShazamStage.LISTENING -> {
                                val secLeft = ((MAX_RECORD_MS - elapsedMs) / 1000L).coerceAtLeast(0L) + 1L
                                Text(
                                    text = "Mendengarkan suara… (${secLeft}d)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (elapsedMs >= MIN_EARLY_STOP_MS) {
                                        "Ketuk tombol di atas atau tekan tombol di bawah untuk langsung mengenali."
                                    } else {
                                        "Tahan sebentar agar melodi dan vokal terekam jelas…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            recordingJob?.cancel()
                                            releaseRecorderQuietly(deleteFile = true)
                                            stage = ShazamStage.IDLE
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("shazam_cancel_button")
                                    ) {
                                        Text("Batal")
                                    }
                                    Button(
                                        onClick = { finishCaptureAndAnalyze() },
                                        enabled = elapsedMs >= MIN_EARLY_STOP_MS,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("shazam_analyze_now_button")
                                    ) {
                                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Kenali Sekarang", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            ShazamStage.ANALYZING -> {
                                Text(
                                    text = "Menganalisis melodi & vokal…",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Mencocokkan audio dengan katalog lagu & YouTube Music",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            ShazamStage.ERROR -> {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = errorMessage ?: "Gagal mengenali audio.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                                Button(
                                    onClick = triggerListenFlow,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.padding(top = 4.dp).testTag("shazam_retry_button")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Coba Dengar Lagi", fontWeight = FontWeight.Bold)
                                }
                            }

                            ShazamStage.RESULT -> Unit
                        }
                    }
                }
            } else {
                // Result State
                val res = recognitionResult
                if (res == null || !res.found || res.matches.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Belum Berhasil Mengenali Lagunya",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = res?.heardSummary?.takeIf { it.isNotBlank() }
                                    ?: "Suara musik belum terdengar cukup jelas. Dekatkan ke speaker atau nyanyikan bagian reff lebih jelas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    if (res.heardSummary.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = res.heardSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    val playableQueue = res.matches.mapNotNull { it.resolvedTrack }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        res.matches.forEachIndexed { idx, match ->
                            RecognizedSongMatchCard(
                                match = match,
                                isTopMatch = idx == 0,
                                isLiked = match.resolvedTrack?.id in likedIds,
                                onPlay = { track ->
                                    onPlayTrack(track, playableQueue.ifEmpty { listOf(track) })
                                    onDismiss()
                                },
                                onToggleLike = onToggleLike,
                                onOptions = onTrackOptions,
                                onFallbackSearch = { query ->
                                    onDismiss()
                                    onFallbackSearchQuery(query)
                                }
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = triggerListenFlow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("shazam_listen_again_button")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dengarkan Lagu Lain", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShazamRadarButton(
    stage: ShazamStage,
    amplitudeLevel: Float,
    progress: Float,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "shazam_radar")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )
    val smoothAmp by animateFloatAsState(
        targetValue = if (stage == ShazamStage.LISTENING) amplitudeLevel else 0f,
        animationSpec = tween(110, easing = FastOutSlowInEasing),
        label = "smooth_amp"
    )
    val orbScale = when (stage) {
        ShazamStage.LISTENING -> 1f + smoothAmp * 0.14f
        ShazamStage.ANALYZING -> 0.96f
        else -> 1f
    }

    Box(
        modifier = Modifier
            .size(216.dp)
            .testTag("shazam_radar_button"),
        contentAlignment = Alignment.Center
    ) {
        // Animated Radar Ripples + Countdown Arc
        Canvas(modifier = Modifier.size(216.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.27f
            val maxRadius = size.minDimension * 0.49f

            if (stage == ShazamStage.LISTENING || stage == ShazamStage.ANALYZING) {
                for (ring in 0..2) {
                    val p = (pulsePhase + ring / 3f) % 1f
                    val r = baseRadius + (maxRadius - baseRadius) * p + smoothAmp * 16f
                    val alpha = (1f - p) * (0.48f + smoothAmp * 0.35f)
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha.coerceIn(0f, 0.8f)),
                        radius = r,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            } else {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.16f),
                    radius = baseRadius + 16.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            if (stage == ShazamStage.LISTENING && progress > 0f) {
                val arcSize = baseRadius * 2f + 22.dp.toPx()
                val topLeft = Offset(center.x - arcSize / 2f, center.y - arcSize / 2f)
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Main Interactive Orb
        Surface(
            onClick = onClick,
            shape = CircleShape,
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(118.dp)
                .scale(orbScale)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.78f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (stage == ShazamStage.ANALYZING) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (stage == ShazamStage.LISTENING) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                        contentDescription = "Mulai mengenali lagu",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }
    }
}

private val sampleLyricPrompts = listOf(
    "Ku kira kita akan bersama",
    "I'm blinded by the lights",
    "Bawa dia kembali",
    "Lagu barat sedih tentang hujan & perpisahan"
)

/**
 * Interactive "Cari dari Lirik" Bottom Sheet:
 * Lets users type a partial/misremembered lyric snippet or vague song description,
 * identifies matching songs via Firebase AI Logic, and resolves them into playable YouTube Music tracks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsTextSearchSheet(
    likedIds: Set<String>,
    onSearchByLyrics: suspend (String) -> Result<SongRecognitionResult>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onTrackOptions: (Track) -> Unit,
    onFallbackSearchQuery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var lyricInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SongRecognitionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val submitLyricsSearch: (String) -> Unit = { raw ->
        val query = raw.trim()
        if (query.length >= 3 && !isSearching) {
            keyboard?.hide()
            lyricInput = query
            isSearching = true
            errorMessage = null
            result = null
            scope.launch {
                val res = onSearchByLyrics(query)
                isSearching = false
                res.onSuccess { found ->
                    result = found
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Gagal mencari lagu dari lirik."
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Cari Lagu dari Lirik",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Ketik sepenggal lirik atau deskripsi samar lagu",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup Cari dari Lirik")
                }
            }

            // Lyrics Snippet Input Box
            OutlinedTextField(
                value = lyricInput,
                onValueChange = { if (it.length <= 240) lyricInput = it },
                placeholder = {
                    Text("Contoh: \"Ku kira kita akan bersama\" atau \"lagu pop 2010an tentang hujan…\"")
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                trailingIcon = {
                    if (lyricInput.isNotEmpty()) {
                        IconButton(onClick = { lyricInput = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Hapus teks lirik")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitLyricsSearch(lyricInput) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_search_input_field")
            )

            Button(
                onClick = { submitLyricsSearch(lyricInput) },
                enabled = lyricInput.trim().length >= 3 && !isSearching,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lyrics_search_submit_button")
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Mencari lagu yang cocok…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Temukan Lagunya", fontWeight = FontWeight.Bold)
                }
            }

            // Sample Prompts when idle
            if (!isSearching && result == null && errorMessage == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Coba contoh cepat:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sampleLyricPrompts.forEach { prompt ->
                            Surface(
                                onClick = { submitLyricsSearch(prompt) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "“$prompt”",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Error display
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Results
            val activeRes = result
            if (activeRes != null) {
                if (!activeRes.found || activeRes.matches.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = activeRes.heardSummary.ifBlank {
                                "Belum ketemu lagu yang cocok persis. Coba tambahkan beberapa kata lirik atau ciri penyanyinya."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    if (activeRes.heardSummary.isNotBlank()) {
                        Text(
                            text = activeRes.heardSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val playableList = activeRes.matches.mapNotNull { it.resolvedTrack }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        activeRes.matches.forEachIndexed { idx, match ->
                            RecognizedSongMatchCard(
                                match = match,
                                isTopMatch = idx == 0,
                                isLiked = match.resolvedTrack?.id in likedIds,
                                onPlay = { track ->
                                    onPlayTrack(track, playableList.ifEmpty { listOf(track) })
                                    onDismiss()
                                },
                                onToggleLike = onToggleLike,
                                onOptions = onTrackOptions,
                                onFallbackSearch = { query ->
                                    onDismiss()
                                    onFallbackSearchQuery(query)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognizedSongMatchCard(
    match: RecognizedSongMatch,
    isTopMatch: Boolean,
    isLiked: Boolean,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOptions: (Track) -> Unit,
    onFallbackSearch: (String) -> Unit
) {
    val track = match.resolvedTrack
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isTopMatch) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isTopMatch) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (track != null) onPlay(track)
                else onFallbackSearch("${match.title} ${match.artist}")
            }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(56.dp)) {
                    AlbumArtImage(
                        url = track?.artworkUrl,
                        contentDescription = match.title,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (track != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Putar lagu",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isTopMatch) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Text(
                                text = if (isTopMatch) "Paling Cocok • ${match.confidencePercent}%" else "${match.confidencePercent}% cocok",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isTopMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = track?.title ?: match.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: match.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (track != null) {
                    IconButton(onClick = { onToggleLike(track) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Sukai lagu",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { onOptions(track) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Opsi lagu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onFallbackSearch("${match.title} ${match.artist}") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Cari lagu ini di YouTube",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (match.matchedLyricOrClue.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${match.matchedLyricOrClue}”",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            if (match.reason.isNotBlank()) {
                Text(
                    text = match.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun createMediaRecorder(context: Context): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }
}
