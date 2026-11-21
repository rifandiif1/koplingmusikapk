package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.components.RecapCardGenerator
import com.example.ui.components.SectionHeader
import com.example.viewmodel.CloudSyncUiState
import com.example.viewmodel.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    stats: ListeningStats,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onShowSnackbar: (String) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSavingRecap by remember { mutableStateOf(false) }
    val (personaTitle, personaDesc) = remember(stats) { RecapCardGenerator.computePersona(stats) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            SubScreenTopBar(title = "Statistik & Recap", subtitle = "Ringkasan & kartu gaya dengar musikmu", onBack = onBack)
        }

        // ---- Koplingfy Recap Poster Card ----
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "KOPLINGFY RECAP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.6.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = personaTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = personaDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val topArtistName = stats.topArtists.firstOrNull()?.artist ?: "Belum ada artis favorit"
                        val topTrackName = stats.topTracks.firstOrNull()?.let { "${it.track.title} — ${it.track.artist}" }
                            ?: "Mulai putar lagu untuk mengisi Recap"

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Artis #1: $topArtistName",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Lagu #1: $topTrackName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isSavingRecap) return@Button
                                    isSavingRecap = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            RecapCardGenerator.saveRecapToGallery(context, stats)
                                        }
                                        isSavingRecap = false
                                        if (res.isSuccess) {
                                            onShowSnackbar("Poster Koplingfy Recap disimpan ke Galeri!")
                                        } else {
                                            onShowSnackbar("Gagal menyimpan Recap: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                },
                                enabled = !isSavingRecap,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("save_recap_card_button")
                            ) {
                                if (isSavingRecap) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simpan Poster", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareText = buildString {
                                        appendLine("🎵 Koplingfy Recap Saya")
                                        appendLine("Persona: $personaTitle")
                                        appendLine("Total Putar: ${stats.totalPlays}x • ${stats.totalMinutes} menit aktif")
                                        stats.topArtists.firstOrNull()?.let { appendLine("Artis #1: ${it.artist}") }
                                        stats.topTracks.firstOrNull()?.let { appendLine("Lagu #1: ${it.track.title} — ${it.track.artist}") }
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Koplingfy Recap")
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan Koplingfy Recap")) }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(46.dp)
                                    .testTag("share_recap_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Bagikan Recap", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan")
                            }
                        }
                    }
                }
            }
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
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
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
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onAutoplayRadioChange: (Boolean) -> Unit,
    onKeepScreenOnLyricsChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onDynamicArtworkColorChange: (Boolean) -> Unit = {},
    onDynamicIslandLyricsChange: (Boolean) -> Unit = {},
    onNotificationBarLyricsChange: (Boolean) -> Unit = {},
    onCrossfadeSecondsChange: (Int) -> Unit = {},
    onDataSaverCellularChange: (Boolean) -> Unit = {},
    onDownloadWifiOnlyChange: (Boolean) -> Unit = {},
    onClearCache: () -> Unit,
    onClearLyricsAiCache: () -> Unit = {},
    onClearDownloads: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    cloudSyncState: CloudSyncUiState = CloudSyncUiState(),
    onUploadLibraryToCloud: () -> Unit = {},
    onRestoreLibraryFromCloud: () -> Unit = {},
    onImportYouTubePlaylistUrl: (String, String?, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    isFirebaseReady: Boolean = false,
    firebaseUserName: String? = null,
    firebaseUserEmail: String? = null,
    activeRoomCode: String? = null,
    onOpenListenTogether: () -> Unit = {},
    onSignOutFirebase: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val firestoreDbId = stringResource(R.string.firestore_database_id)
    var showImportPlaylistDialog by remember { mutableStateOf(false) }

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

        // ---- YouTube Music Account (Google Sign-In & Optional Cookie Sync) ----
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
                                text = youtubeAccount.email.ifBlank {
                                    if (youtubeAccount.hasCookieSession) "Sesi Cookie YTM Aktif" else "Akun Google Terhubung"
                                },
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
                            onClick = if (youtubeAccount.hasCookieSession) onSyncYouTubeLibrary else onOpenYouTubeLogin,
                            enabled = !isSyncingAccount,
                            shape = RoundedCornerShape(14.dp),
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
                            Text(
                                text = if (youtubeAccount.hasCookieSession) "Sinkronkan" else "Sinkron Cookie",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onLogoutYouTube,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("settings_logout_ytm_button")
                        ) {
                            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keluar")
                        }
                    }
                } else {
                    Text(
                        text = "Masuk cepat 1-ketuk menggunakan akun Google (sama seperti Play Bersama) untuk profil, personalisasi, dan opsi sinkronisasi koleksi YouTube Music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenYouTubeLogin,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_login_ytm_button")
                    ) {
                        Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Masuk dengan Google")
                    }
                }
            }
        }

        // ---- Firebase & Play Bersama Configuration Card ----
        item {
            SettingsSectionCard(title = "Firebase & Play Bersama", icon = Icons.Rounded.CloudDone) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isFirebaseReady) "Cloud Firestore Terhubung" else "Firebase Belum Terpasang",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isFirebaseReady) "DB: ${firestoreDbId.take(28)}…"
                            else "Sertakan google-services.json saat build APK",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isFirebaseReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (isFirebaseReady) "AKTIF" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (isFirebaseReady) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (!firebaseUserName.isNullOrBlank()) {
                                "Akun Google: $firebaseUserName"
                            } else {
                                "Akun Google: Belum masuk"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!firebaseUserEmail.isNullOrBlank()) {
                            Text(
                                text = firebaseUserEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!activeRoomCode.isNullOrBlank()) {
                            Text(
                                text = "Ruang Play Bersama Aktif: $activeRoomCode",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenListenTogether,
                        modifier = Modifier.weight(1f).testTag("settings_open_play_together")
                    ) {
                        Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!firebaseUserName.isNullOrBlank()) "Buka Play Bersama" else "Masuk Google",
                            maxLines = 1
                        )
                    }
                    if (!firebaseUserName.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onSignOutFirebase,
                            modifier = Modifier.testTag("settings_signout_firebase")
                        ) {
                            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keluar")
                        }
                    }
                }

                // ---- Cloud Library Sync (Favorit & Playlist ke Firestore) ----
                if (isFirebaseReady && !firebaseUserName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Sinkronisasi Koleksi Cloud",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            val syncStatusLabel = remember(cloudSyncState.lastSyncedAtMillis, cloudSyncState.cloudLikedCount, cloudSyncState.cloudPlaylistCount) {
                                val ts = cloudSyncState.lastSyncedAtMillis
                                if (ts != null) {
                                    val formatted = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(ts))
                                    val likedStr = cloudSyncState.cloudLikedCount?.let { "$it disukai" }.orEmpty()
                                    val plStr = cloudSyncState.cloudPlaylistCount?.let { "$it playlist" }.orEmpty()
                                    val counts = listOf(likedStr, plStr).filter { it.isNotBlank() }.joinToString(" • ")
                                    if (counts.isNotBlank()) "Terakhir: $formatted ($counts)" else "Terakhir: $formatted"
                                } else {
                                    "Simpan & pulihkan Lagu Disukai serta Playlist lintas perangkat"
                                }
                            }
                            Text(
                                text = syncStatusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onUploadLibraryToCloud,
                                    enabled = !cloudSyncState.isSyncing,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_cloud_upload")
                                ) {
                                    if (cloudSyncState.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unggah", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onRestoreLibraryFromCloud,
                                    enabled = !cloudSyncState.isSyncing,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("settings_cloud_restore")
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pulihkan", maxLines = 1)
                                }
                            }
                        }
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
                    title = "Warna Dinamis Sampul Album",
                    subtitle = "Sesuaikan aksen pemutar & gradien mengikuti sampul lagu",
                    checked = settings.dynamicArtworkColor,
                    onCheckedChange = onDynamicArtworkColorChange
                )
                SettingsToggleRow(
                    title = "Layar Tetap Menyala Saat Lirik",
                    subtitle = "Cegah layar mati otomatis saat membaca lirik",
                    checked = settings.keepScreenOnLyrics,
                    onCheckedChange = onKeepScreenOnLyricsChange
                )
                SettingsToggleRow(
                    title = "Dynamic Island Lirik (Non-Klik)",
                    subtitle = "Tampilkan pill lirik berjalan di bawah status bar tanpa mengganggu sentuhan layar",
                    checked = settings.dynamicIslandLyrics,
                    onCheckedChange = onDynamicIslandLyricsChange
                )
                SettingsToggleRow(
                    title = "Lirik di Notifikasi Bar Mini Player",
                    subtitle = "Tampilkan baris lirik aktif di notifikasi pemutar & layar kunci",
                    checked = settings.notificationBarLyrics,
                    onCheckedChange = onNotificationBarLyricsChange
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
                    title = "Hemat Kuota Seluler",
                    subtitle = "Otomatis pakai kualitas Hemat Data saat menggunakan jaringan seluler",
                    checked = settings.dataSaverCellular,
                    onCheckedChange = onDataSaverCellularChange
                )
                SettingsToggleRow(
                    title = "Unduh Khusus Wi-Fi",
                    subtitle = "Cegah unduhan offline saat memakai data seluler",
                    checked = settings.downloadWifiOnly,
                    onCheckedChange = onDownloadWifiOnlyChange
                )
                SettingsToggleRow(
                    title = "Lewati Bagian Hening (Skip Silence)",
                    subtitle = "Otomatis percepat intro atau jeda sunyi panjang pada audio",
                    checked = settings.skipSilence,
                    onCheckedChange = onSkipSilenceChange
                )
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

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Crossfade Transisi Lagu",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = if (settings.crossfadeSeconds == 0) "Mati" else "${settings.crossfadeSeconds} detik",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { onCrossfadeSecondsChange(it.toInt().coerceIn(0, 10)) },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.testTag("settings_crossfade_slider")
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

        // ---- Storage, Cache, YouTube Playlist Importer & Backup / Restore ----
        item {
            SettingsSectionCard(title = "Penyimpanan, Impor & Cadangan", icon = Icons.Rounded.Storage) {
                val dlMb = storageStats.downloadsBytes / (1024.0 * 1024.0)
                val cacheMb = storageStats.cacheBytes / (1024.0 * 1024.0)
                val lyricsKb = storageStats.lyricsAiCacheBytes / 1024.0
                val lyricsSizeLabel = if (lyricsKb >= 1024.0) "%.1f MB".format(lyricsKb / 1024.0) else "%.0f KB".format(lyricsKb)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile("Unduhan", "%.1f MB".format(dlMb), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricTile("Cache Audio", "%.1f MB".format(cacheMb), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    MetricTile("Lirik AI", lyricsSizeLabel, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearCache,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("settings_clear_cache")
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cache Audio", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onClearLyricsAiCache,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("settings_clear_lyrics_cache")
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cache Lirik", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onClearDownloads,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_clear_downloads")
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus Semua Unduhan Offline", maxLines = 1)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showImportPlaylistDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_import_yt_playlist")
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Impor Link Playlist YouTube / YTM", maxLines = 1)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportBackup,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("settings_export_backup")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup JSON", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Koplingfy Studio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Full InnerTube YTM • Lyric Video + PiP • Lirik ID & Romaji • Cloud Sync • Visual DSP EQ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showImportPlaylistDialog) {
        ImportYouTubePlaylistDialog(
            isImporting = cloudSyncState.isImportingPlaylist,
            onDismiss = { if (!cloudSyncState.isImportingPlaylist) showImportPlaylistDialog = false },
            onImport = { rawUrl, customName ->
                onImportYouTubePlaylistUrl(rawUrl, customName) { success ->
                    if (success) showImportPlaylistDialog = false
                }
            }
        )
    }
}

@Composable
private fun ImportYouTubePlaylistDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onImport: (rawUrl: String, customName: String?) -> Unit
) {
    var playlistUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Impor Playlist YouTube",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Tempel link playlist publik dari YouTube atau YouTube Music (contoh: https://music.youtube.com/playlist?list=PL...)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it },
                    label = { Text("Link atau ID Playlist") },
                    placeholder = { Text("https://music.youtube.com/playlist?list=...") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth().testTag("import_yt_playlist_url_input")
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Nama Playlist (Opsional)") },
                    placeholder = { Text("Otomatis dari YouTube jika kosong") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth().testTag("import_yt_playlist_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(playlistUrl, customName.takeIf { it.isNotBlank() }) },
                enabled = !isImporting && playlistUrl.isNotBlank(),
                modifier = Modifier.testTag("confirm_import_yt_playlist_button")
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mengimpor…")
                } else {
                    Text("Impor")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Batal")
            }
        }
    )
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
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
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
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
            color = MaterialTheme.colorScheme.surfaceContainerLow,
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
            Text(text = title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
