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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.viewmodel.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shadowElevation = 10.dp
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
    onDynamicArtworkColorChange: (Boolean) -> Unit = {},
    onCrossfadeSecondsChange: (Int) -> Unit = {},
    onDataSaverCellularChange: (Boolean) -> Unit = {},
    onDownloadWifiOnlyChange: (Boolean) -> Unit = {},
    onClearCache: () -> Unit,
    onClearDownloads: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    isFirebaseReady: Boolean = false,
    firebaseUserName: String? = null,
    firebaseUserEmail: String? = null,
    activeRoomCode: String? = null,
    onOpenListenTogether: () -> Unit = {},
    onSignOutFirebase: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val firestoreDbId = stringResource(R.string.firestore_database_id)

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
