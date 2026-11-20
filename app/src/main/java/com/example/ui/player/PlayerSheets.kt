package com.example.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.StreamTechStats
import com.example.model.Track
import com.example.ui.components.formatDuration
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    remainingSec: Int?,
    endOfTrack: Boolean,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Timer Tidur", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(
                text = when {
                    remainingSec != null -> "Aktif: sisa ${remainingSec / 60} menit ${remainingSec % 60} detik"
                    endOfTrack -> "Aktif: berhenti setelah lagu ini selesai"
                    else -> "Otomatis menjeda pemutaran dengan fade-out lembut"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            listOf(5, 15, 30, 45, 60).forEach { mins ->
                Surface(
                    onClick = { onSelectMinutes(mins) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$mins Menit",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
            Surface(
                onClick = onSelectEndOfTrack,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Akhir Lagu Ini",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
            if (remainingSec != null || endOfTrack) {
                TextButton(
                    onClick = { onSelectMinutes(0) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Matikan Timer Tidur", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFxBottomSheet(
    settings: AudioSettings,
    onDismiss: () -> Unit,
    onEqPreset: (EqPreset) -> Unit,
    onCustomBand: (Int, Float) -> Unit,
    onBassBoostChange: (Float) -> Unit,
    onNormalizeAudioChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onReverbChange: (Boolean) -> Unit = {},
    onApplyVibePreset: (String) -> Unit = {},
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onCrossfadeChange: (Int) -> Unit = {},
    abLoopStartMs: Long? = null,
    abLoopEndMs: Long? = null,
    onSetAbPointA: () -> Unit = {},
    onSetAbPointB: () -> Unit = {},
    onClearAbLoop: () -> Unit = {}
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    val activeVibe = remember(settings.playbackSpeed, settings.playbackPitch, settings.reverbEnabled) {
        when {
            settings.playbackSpeed in 0.80f..0.88f && settings.reverbEnabled -> "slowed"
            settings.playbackSpeed >= 1.18f && settings.playbackPitch >= 1.12f -> "nightcore"
            settings.playbackSpeed in 0.90f..0.97f && settings.reverbEnabled -> "lofi"
            abs(settings.playbackSpeed - 1.0f) < 0.03f && abs(settings.playbackPitch - 1.0f) < 0.03f && !settings.reverbEnabled -> "normal"
            else -> ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Audio FX & Equalizer",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Seret kurva EQ atau pilih preset suasana",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { onApplyVibePreset("normal") },
                    modifier = Modifier.testTag("fx_reset_button")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.primary)
                }
            }

            // ---- 1-Tap Vibe Presets (Slowed + Reverb / Nightcore / Lo-Fi / Normal) ----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Preset Suasana (1-Ketuk)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val vibes = listOf(
                    "normal" to "Normal",
                    "slowed" to "Slowed + Reverb",
                    "nightcore" to "Nightcore",
                    "lofi" to "Lo-Fi"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vibes.forEach { (id, label) ->
                        val selected = activeVibe == id
                        Surface(
                            onClick = { onApplyVibePreset(id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fx_vibe_$id"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // ---- Interactive Draggable Visual EQ Curve Canvas ----
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kurva Frekuensi 5-Band",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = settings.eqPreset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    InteractiveEqCurveCanvas(
                        bandsDb = settings.customBandsDb,
                        onBandChange = onCustomBand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                            .testTag("interactive_eq_curve")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        settings.customBandsDb.forEachIndexed { idx, db ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "%+.0fdB".format(db),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (abs(db) > 0.4f) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = freqLabels.getOrElse(idx) { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }
                }
            }

            // ---- EQ Preset Chips ----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EqPreset.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { preset ->
                            val selected = settings.eqPreset == preset
                            Surface(
                                onClick = { onEqPreset(preset) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Text(
                                    text = preset.label.substringBefore(" "),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---- Smart Audio Toggles (Normalize, Skip Silence, Hall Reverb) ----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    FxToggleRow(
                        title = "Lewati Bagian Hening (Skip Silence)",
                        subtitle = "Potong jeda sunyi panjang pada intro atau outro lagu",
                        checked = settings.skipSilence,
                        onCheckedChange = onSkipSilenceChange,
                        testTag = "fx_skip_silence_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    FxToggleRow(
                        title = "Efek Ruang (Hall Reverb)",
                        subtitle = "Tambahkan gema panggung untuk nuansa imersif / slowed",
                        checked = settings.reverbEnabled,
                        onCheckedChange = onReverbChange,
                        testTag = "fx_reverb_switch"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    FxToggleRow(
                        title = "Normalisasi Audio",
                        subtitle = "Seimbangkan kenyaringan volume antar lagu",
                        checked = settings.normalizeAudio,
                        onCheckedChange = onNormalizeAudioChange,
                        testTag = "fx_normalize_switch"
                    )
                }
            }

            // ---- Sliders (Bass Boost, Tempo, Pitch, Crossfade) ----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bass Boost DSP", style = MaterialTheme.typography.titleSmall)
                        Text("${(settings.bassBoostStrength * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = settings.bassBoostStrength,
                        onValueChange = onBassBoostChange,
                        valueRange = 0f..1f
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Kecepatan Putar (Tempo)", style = MaterialTheme.typography.titleSmall)
                        Text("%.2fx".format(settings.playbackSpeed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = settings.playbackSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.5f..2.0f
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Nada (Pitch)", style = MaterialTheme.typography.titleSmall)
                        Text("%.2fx".format(settings.playbackPitch), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Slider(
                        value = settings.playbackPitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.75f..1.5f
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Crossfade Transisi Lagu", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (settings.crossfadeSeconds == 0) "Mati" else "${settings.crossfadeSeconds} detik",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.crossfadeSeconds.toFloat(),
                        onValueChange = { onCrossfadeChange(it.toInt().coerceIn(0, 10)) },
                        valueRange = 0f..10f,
                        steps = 9
                    )
                }
            }

            // ---- A-B Loop Section ----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (abLoopStartMs != null && abLoopEndMs != null) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                } else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "A-B Loop (Ulang Bagian Lagu)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = when {
                                    abLoopStartMs != null && abLoopEndMs != null ->
                                        "Aktif: ${formatDuration(abLoopStartMs)} → ${formatDuration(abLoopEndMs)}"
                                    abLoopStartMs != null ->
                                        "Titik A: ${formatDuration(abLoopStartMs)} — ketuk Atur B untuk mulai loop"
                                    else -> "Ulangi bagian reff atau solo gitar favoritmu"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (abLoopStartMs != null && abLoopEndMs != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (abLoopStartMs != null || abLoopEndMs != null) {
                            TextButton(onClick = onClearAbLoop) {
                                Text("Reset", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSetAbPointA,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = abLoopStartMs?.let { "A: ${formatDuration(it)}" } ?: "Atur Titik A",
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onSetAbPointB,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = abLoopEndMs?.let { "B: ${formatDuration(it)}" } ?: "Atur Titik B",
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FxToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun InteractiveEqCurveCanvas(
    bandsDb: List<Float>,
    onBandChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val latestBands by rememberUpdatedState(bandsDb)
    val latestOnChange by rememberUpdatedState(onBandChange)
    var activeBand by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val count = latestBands.size.coerceAtLeast(1)
                    val padX = size.width * 0.08f
                    val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                    val stepX = if (count > 1) usableW / (count - 1) else 0f
                    val idx = (((offset.x - padX) / stepX).roundToInt()).coerceIn(0, count - 1)
                    val padY = 18.dp.toPx()
                    val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                    val normalized = 1f - ((offset.y - padY) / usableH).coerceIn(0f, 1f)
                    val db = ((normalized * 12f) - 6f).coerceIn(-6f, 6f)
                    latestOnChange(idx, db)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val count = latestBands.size.coerceAtLeast(1)
                        val padX = size.width * 0.08f
                        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val stepX = if (count > 1) usableW / (count - 1) else 0f
                        activeBand = (((offset.x - padX) / stepX).roundToInt()).coerceIn(0, count - 1)
                    },
                    onDragEnd = { activeBand = -1 },
                    onDragCancel = { activeBand = -1 }
                ) { change, _ ->
                    change.consume()
                    val count = latestBands.size.coerceAtLeast(1)
                    val padX = size.width * 0.08f
                    val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                    val stepX = if (count > 1) usableW / (count - 1) else 0f
                    val idx = if (activeBand in 0 until count) activeBand
                    else (((change.position.x - padX) / stepX).roundToInt()).coerceIn(0, count - 1)
                    val padY = 18.dp.toPx()
                    val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                    val normalized = 1f - ((change.position.y - padY) / usableH).coerceIn(0f, 1f)
                    val db = ((normalized * 12f) - 6f).coerceIn(-6f, 6f)
                    latestOnChange(idx, db)
                }
            }
    ) {
        val count = bandsDb.size
        if (count < 2) return@Canvas
        val padX = size.width * 0.08f
        val padY = 18.dp.toPx()
        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        val midY = padY + usableH / 2f

        // Subtle horizontal dB grid lines (+6, 0, -6)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        drawLine(gridColor, Offset(padX, padY), Offset(size.width - padX, padY), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        drawLine(gridColor.copy(alpha = 0.42f), Offset(padX, midY), Offset(size.width - padX, midY), strokeWidth = 1.2.dp.toPx())
        drawLine(gridColor, Offset(padX, size.height - padY), Offset(size.width - padX, size.height - padY), strokeWidth = 1.dp.toPx(), pathEffect = dash)

        val points = bandsDb.mapIndexed { idx, db ->
            val x = padX + (usableW * idx / (count - 1))
            val norm = ((db + 6f) / 12f).coerceIn(0f, 1f)
            val y = padY + (1f - norm) * usableH
            Offset(x, y)
        }

        // Vertical guide lines per band
        points.forEach { pt ->
            drawLine(
                color = gridColor.copy(alpha = 0.22f),
                start = Offset(pt.x, padY),
                end = Offset(pt.x, size.height - padY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Smooth cubic Bezier path
        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, size.height - padY)
            lineTo(points.first().x, size.height - padY)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.28f), primaryColor.copy(alpha = 0.02f)),
                startY = padY,
                endY = size.height - padY
            )
        )

        drawPath(
            path = curvePath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draggable circular band handles
        points.forEachIndexed { idx, pt ->
            val isDragging = idx == activeBand
            val outerRadius = if (isDragging) 11.dp.toPx() else 8.dp.toPx()
            drawCircle(
                color = primaryColor.copy(alpha = if (isDragging) 0.35f else 0.18f),
                radius = outerRadius + 4.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = primaryColor,
                radius = outerRadius,
                center = pt
            )
            drawCircle(
                color = surfaceColor,
                radius = outerRadius * 0.45f,
                center = pt
            )
        }
    }
}

@Composable
fun StreamTechStatsDialog(
    track: Track,
    streamSourceLabel: String,
    techStats: StreamTechStats,
    settings: AudioSettings,
    isVideoMode: Boolean,
    onDismiss: () -> Unit
) {
    val cleanId = track.id.removePrefix("yt_")
    val channelsLabel = when (techStats.channelCount) {
        1 -> "Mono (1 ch)"
        2 -> "Stereo (2 ch)"
        0 -> "-"
        else -> "${techStats.channelCount} ch"
    }
    val sampleRateLabel = if (techStats.sampleRateHz > 0) {
        "%.1f kHz".format(techStats.sampleRateHz / 1000f)
    } else "-"
    val bitrateLabel = if (techStats.bitrateKbps > 0) "${techStats.bitrateKbps} kbps" else "-"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Info Teknis (Stats for Nerds)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${track.title} — ${track.artist}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                StatTechRow("ID Stream", cleanId)
                StatTechRow("Sumber", streamSourceLabel)
                StatTechRow("Codec Audio", techStats.audioCodec)
                StatTechRow("Bitrate", bitrateLabel)
                StatTechRow("Sample Rate", sampleRateLabel)
                StatTechRow("Channel", channelsLabel)
                if (isVideoMode || techStats.videoResolution.isNotBlank()) {
                    StatTechRow(
                        "Video",
                        listOf(techStats.videoResolution, techStats.videoCodec)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                            .ifBlank { "Aktif" }
                    )
                }
                StatTechRow("Buffer", "${techStats.bufferedPercent}%")
                StatTechRow("Cache Lokal", if (techStats.isCached) "Tersimpan (0ms seek)" else "Streaming Langsung")
                StatTechRow(
                    "DSP Aktif",
                    buildList {
                        if (settings.skipSilence) add("Skip Silence")
                        if (settings.reverbEnabled) add("Reverb")
                        if (settings.normalizeAudio) add("Normalisasi")
                        if (settings.bassBoostStrength > 0.05f) add("Bass ${(settings.bassBoostStrength * 100).toInt()}%")
                        if (abs(settings.playbackSpeed - 1f) > 0.02f) add("%.2fx".format(settings.playbackSpeed))
                    }.joinToString(", ").ifBlank { "Standar (Flat)" }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun StatTechRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
