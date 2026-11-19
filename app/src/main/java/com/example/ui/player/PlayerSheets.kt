package com.example.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.AudioSettings
import com.example.model.EqPreset

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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Timer Tidur", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when {
                    remainingSec != null -> "Aktif: sisa ${remainingSec / 60} menit ${remainingSec % 60} detik"
                    endOfTrack -> "Aktif: berhenti setelah lagu ini selesai"
                    else -> "Otomatis menjeda pemutaran saat kamu beristirahat"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            listOf(5, 15, 30, 45, 60).forEach { mins ->
                Surface(
                    onClick = { onSelectMinutes(mins) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
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
                    style = MaterialTheme.typography.titleMedium,
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
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Koplingfy Audio FX", style = MaterialTheme.typography.headlineSmall)
                    Text("5-Band EQ, Bass Boost, Normalisasi & Tempo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    onEqPreset(EqPreset.FLAT)
                    onBassBoostChange(0f)
                    onSpeedChange(1.0f)
                    onPitchChange(1.0f)
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.primary)
                }
            }

            // Normalization toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Normalisasi Audio",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Seimbangkan kenyaringan volume antar lagu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.normalizeAudio,
                        onCheckedChange = onNormalizeAudioChange
                    )
                }
            }

            // Preset chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPreset.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            val selected = settings.eqPreset == preset
                            Surface(
                                onClick = { onEqPreset(preset) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = preset.label.substringBefore(" "),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5-Band EQ Sliders
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    settings.customBandsDb.forEachIndexed { idx, db ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = freqLabels.getOrElse(idx) { "" },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(52.dp)
                            )
                            Slider(
                                value = db,
                                onValueChange = { onCustomBand(idx, it) },
                                valueRange = -6f..6f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%+.0f dB".format(db),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(46.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // Bass Boost Slider
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bass Boost DSP", style = MaterialTheme.typography.titleSmall)
                    Text("${(settings.bassBoostStrength * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.bassBoostStrength,
                    onValueChange = onBassBoostChange,
                    valueRange = 0f..1f
                )
            }

            // Speed & Pitch controls
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kecepatan Putar (Tempo)", style = MaterialTheme.typography.titleSmall)
                    Text("%.2fx".format(settings.playbackSpeed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.playbackSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f
                )
            }

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Nada (Pitch)", style = MaterialTheme.typography.titleSmall)
                    Text("%.2fx".format(settings.playbackPitch), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                    value = settings.playbackPitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.75f..1.5f
                )
            }
        }
    }
}
