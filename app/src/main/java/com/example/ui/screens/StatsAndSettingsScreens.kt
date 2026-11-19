package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AccentPreset
import com.example.model.AudioQuality
import com.example.model.AudioSettings
import com.example.model.EqPreset
import com.example.model.ListeningStats
import com.example.model.PlayerArtStyle
import com.example.model.PlayerBackgroundStyle
import com.example.model.ThemeMode
import com.example.model.Track
import com.example.model.YouTubeAccount
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.SectionHeader
import com.example.viewmodel.StorageStats

@Composable
fun StatsScreen(
    stats: ListeningStats,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SubScreenTopBar(title = "Statistik Dengar", subtitle = "Ringkasan aktivitas musikmu", onBack = onBack)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile("Total Putar", stats.totalPlays.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                MetricTile("Menit Aktif", "${stats.totalMinutes}m", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                MetricTile("Lagu Unik", stats.uniqueTracks.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Aktivitas 7 Hari Terakhir",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Jumlah pemutaran harian",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    val daily = if (stats.dailyPlays.isNotEmpty()) stats.dailyPlays else {
                        listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").map { it to 0 }
                    }
                    val maxVal = (daily.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    val trackBg = MaterialTheme.colorScheme.surfaceVariant

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    ) {
                        val barCount = daily.size
                        val slotWidth = size.width / barCount
                        val barWidth = slotWidth * 0.48f

                        daily.forEachIndexed { idx, (_, value) ->
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            drawRoundRect(
                                color = trackBg,
                                topLeft = Offset(left, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                            if (value > 0) {
                                val fraction = (value.toFloat() / maxVal.toFloat()).coerceIn(0.12f, 1f)
                                val barHeight = size.height * fraction
                                drawRoundRect(
                                    brush = Brush.verticalGradient(listOf(primaryColor, secondaryColor)),
                                    topLeft = Offset(left, size.height - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(14f, 14f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        daily.forEach { (day, count) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day.take(3),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (stats.topArtists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artis Terfavorit", modifier = Modifier.padding(horizontal = 20.dp))
            }
            itemsIndexed(stats.topArtists) { idx, item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${idx + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(30.dp)
                        )
                        AlbumArtImage(
                            url = item.artworkUrl,
                            contentDescription = item.artist,
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.playCount}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (stats.topTracks.isNotEmpty()) {
            item {
                SectionHeader(title = "Lagu Paling Sering Diputar", modifier = Modifier.padding(horizontal = 20.dp))
            }
            itemsIndexed(stats.topTracks) { idx, item ->
                Surface(
                    onClick = { onPlayTrack(item.track, stats.topTracks.map { it.track }) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${idx + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(30.dp)
                        )
                        AlbumArtImage(
                            url = item.track.artworkUrl,
                            contentDescription = item.track.title,
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.track.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${item.playCount}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AudioSettings,
    youtubeAccount: YouTubeAccount,
    isSyncingAccount: Boolean,
    storageStats: StorageStats,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onOpenYouTubeLogin: () -> Unit,
    onSyncYouTubeLibrary: () -> Unit,
    onLogoutYouTube: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onArtStyleChange: (PlayerArtStyle) -> Unit,
    onBgStyleChange: (PlayerBackgroundStyle) -> Unit,
    onAudioQualityChange: (AudioQuality) -> Unit,
    onEqPresetChange: (EqPreset) -> Unit,
    onNormalizeAudioChange: (Boolean) -> Unit,
    onAutoplayRadioChange: (Boolean) -> Unit,
    onKeepScreenOnLyricsChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onClearDownloads: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SubScreenTopBar(title = "Pengaturan", subtitle = "Akun YouTube Music, visual, audio & cadangan", onBack = onBack)
        }

        // ---- YouTube Music Account (Metrolist InnerTube Login & Sync) ----
        item {
            SettingsSectionCard(title = "Akun YouTube Music", icon = Icons.Rounded.AccountCircle) {
                if (youtubeAccount.isLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtImage(
                            url = youtubeAccount.avatarUrl,
                            contentDescription = youtubeAccount.name,
                            modifier = Modifier.size(50.dp),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = youtubeAccount.name.ifBlank { "Akun YouTube Music" },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = youtubeAccount.email.ifBlank { "Sesi InnerTube Aktif" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSyncYouTubeLibrary,
                            enabled = !isSyncingAccount,
                            modifier = Modifier.weight(1f).testTag("settings_sync_ytm_button")
                        ) {
                            if (isSyncingAccount) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sinkronkan")
                        }
                        OutlinedButton(
                            onClick = onLogoutYouTube,
                            modifier = Modifier.weight(1f).testTag("settings_logout_ytm_button")
                        ) {
                            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keluar")
                        }
                    }
                } else {
                    Text(
                        text = "Masuk ke akun YouTube Music untuk memutar penuh lagu Explicit, sinkronisasi Lagu Disukai & Playlist, serta kualitas audio optimal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenYouTubeLogin,
                        modifier = Modifier.fillMaxWidth().testTag("settings_login_ytm_button")
                    ) {
                        Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Masuk Akun YouTube Music")
                    }
                }
            }
        }

        // ---- Appearance: Theme Mode ----
        item {
            SettingsSectionCard(title = "Tema Tampilan", icon = Icons.Rounded.Palette) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("theme_mode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.themeMode == mode, onClick = { onThemeModeChange(mode) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ---- Accent Swatches ----
        item {
            SettingsSectionCard(title = "Warna Aksen Signature", icon = Icons.Rounded.GraphicEq) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccentPreset.entries.forEach { preset ->
                        val selected = settings.accentPreset == preset
                        val swatch = Color(preset.hexColor)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onAccentChange(preset) }
                                .padding(8.dp)
                                .testTag("accent_${preset.name}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(swatch),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Aktif", tint = Color(0xFF0A0B0F))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.label.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ---- Player Style & Background ----
        item {
            SettingsSectionCard(title = "Gaya Pemutar Now Playing", icon = Icons.Rounded.Album) {
                Text("Gaya Sampul", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                PlayerArtStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onArtStyleChange(style) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.playerArtStyle == style, onClick = { onArtStyleChange(style) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(style.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(style.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Latar Belakang Pemutar (Solid Opaque)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                PlayerBackgroundStyle.entries.forEach { bgStyle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onBgStyleChange(bgStyle) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.playerBackgroundStyle == bgStyle, onClick = { onBgStyleChange(bgStyle) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(bgStyle.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(bgStyle.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SettingsToggleRow(
                    title = "Layar Tetap Menyala Saat Lirik",
                    subtitle = "Cegah layar mati otomatis saat membaca lirik",
                    checked = settings.keepScreenOnLyrics,
                    onCheckedChange = onKeepScreenOnLyricsChange
                )
            }
        }

        // ---- Audio Engine, Quality & Radio ----
        item {
            SettingsSectionCard(title = "Audio Hi-Fi & Equalizer", icon = Icons.Rounded.Equalizer) {
                Text("Kualitas Streaming Audio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                AudioQuality.entries.forEach { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onAudioQualityChange(q) }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.audioQuality == q, onClick = { onAudioQualityChange(q) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(q.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(q.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                SettingsToggleRow(
                    title = "Normalisasi Volume Audio",
                    subtitle = "Aktifkan LoudnessEnhancer agar volume stabil antar lagu",
                    checked = settings.normalizeAudio,
                    onCheckedChange = onNormalizeAudioChange
                )
                SettingsToggleRow(
                    title = "Putar Otomatis Radio Serupa",
                    subtitle = "Lanjutkan antrean secara otomatis dengan lagu mirip",
                    checked = settings.autoplayRadio,
                    onCheckedChange = onAutoplayRadioChange
                )
                SettingsToggleRow(
                    title = "Pemutaran Mulus (Gapless)",
                    subtitle = "Transisi cepat antar lagu dalam antrean",
                    checked = settings.gaplessPlayback,
                    onCheckedChange = onGaplessChange
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Preset Equalizer Aktif: ${settings.eqPreset.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPreset.entries.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val active = settings.eqPreset == preset
                                Surface(
                                    onClick = { onEqPresetChange(preset) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = preset.label.substringBefore(" "),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Storage, Cache & Backup / Restore (Metrolist feature) ----
        item {
            SettingsSectionCard(title = "Penyimpanan & Cadangan", icon = Icons.Rounded.Storage) {
                val dlMb = storageStats.downloadsBytes / (1024.0 * 1024.0)
                val cacheMb = storageStats.cacheBytes / (1024.0 * 1024.0)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile("Unduhan", "%.1f MB".format(dlMb), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricTile("Cache", "%.1f MB".format(cacheMb), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.weight(1f).testTag("settings_clear_cache")
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hapus Cache", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onClearDownloads,
                        modifier = Modifier.weight(1f).testTag("settings_clear_downloads")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hapus Unduh", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f).testTag("settings_export_backup")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup JSON", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f).testTag("settings_import_backup")
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", maxLines = 1)
                    }
                }
            }
        }

        // ---- About Koplingfy Card ----
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Koplingfy Studio v2.0",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Full InnerTube YTM Engine • Tanpa Preview • Offline Download • Lirik LRCLIB & YTM • DSP EQ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SubScreenTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(44.dp).testTag("subscreen_back_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
