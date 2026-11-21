package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.AppUpdateUiState
import com.example.data.remote.ChangelogCategory
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateChecker
import com.example.data.remote.LocalChangelogItem
import com.example.listentogether.QrCodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated modal for sharing the direct click-to-download Koplingfy APK link from GitHub
 * along with a scannable QR Code, Copy Link button, Save QR Card to Gallery, and Refresh action.
 */
@Composable
fun ShareApkLinkDialog(
    updateState: AppUpdateUiState,
    onDismiss: () -> Unit,
    onRefreshLink: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (url: String, versionLabel: String, isDirectApk: Boolean) -> Unit,
    onShowFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val (downloadUrl, isDirectApk) = remember(updateState.repoSlug, updateState.latestRelease) {
        GitHubUpdateChecker.shareableApkDownloadLink(
            repoSlug = updateState.repoSlug,
            latestRelease = updateState.latestRelease
        )
    }
    val versionLabel = remember(updateState.latestRelease, updateState.currentVersionName) {
        val v = updateState.latestRelease?.versionName ?: updateState.currentVersionName
        "v$v"
    }
    val apkFileName = updateState.latestRelease?.apkFileName
    val apkSizeLabel = remember(updateState.latestRelease?.apkSizeBytes) {
        val bytes = updateState.latestRelease?.apkSizeBytes ?: 0L
        if (bytes > 0L) "%.1f MB".format(bytes / (1024.0 * 1024.0)) else null
    }
    val qrBitmap = remember(downloadUrl) {
        QrCodeUtils.generateUrlQrBitmap(downloadUrl, 640)?.asImageBitmap()
    }
    var isSavingQr by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .testTag("share_apk_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, primary.copy(alpha = 0.25f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(primary.copy(alpha = 0.26f), secondary.copy(alpha = 0.18f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bagikan Link APK",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Koplingfy $versionLabel • Unduhan Resmi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onRefreshLink,
                        enabled = !updateState.isResolvingShareLink,
                        modifier = Modifier.testTag("share_apk_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Segarkan link APK terbaru",
                            tint = primary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("share_apk_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup dialog bagikan APK"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDirectApk) {
                        primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isDirectApk) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (isDirectApk) primary else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isDirectApk) {
                                "Klik / Scan = Langsung Unduh APK"
                            } else {
                                "Tautan Unduhan Resmi Terbaru"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isDirectApk) primary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Crisp High-Contrast QR Code Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, primary.copy(alpha = 0.35f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(228.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "QR Code tautan unduhan APK Koplingfy",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(200.dp)
                                    .testTag("share_apk_qr_image")
                            )
                        }
                        if (updateState.isResolvingShareLink) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.82f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isDirectApk && apkFileName != null) {
                    Text(
                        text = buildString {
                            append(apkFileName)
                            if (apkSizeLabel != null) append("  •  $apkSizeLabel")
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "Scan QR Code atau bagikan tautan di bawah agar teman dapat mengunduh versi terbaru Koplingfy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Masked Copyable Download Link Box (hides repository path on screen)
                Surface(
                    onClick = { onCopyLink(downloadUrl) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_apk_url_box")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = apkFileName ?: "Koplingfy-$versionLabel.apk",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Ketuk untuk menyalin tautan unduhan resmi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Salin link unduhan",
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action: Share Direct APK Link
                Button(
                    onClick = { onShareLink(downloadUrl, versionLabel, isDirectApk) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("share_apk_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bagikan Link APK",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Row: Salin Link + Simpan QR ke Galeri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopyLink(downloadUrl) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_apk_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Link", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            if (isSavingQr) return@OutlinedButton
                            isSavingQr = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    QrCodeUtils.saveApkShareQrToGallery(
                                        context = context,
                                        downloadUrl = downloadUrl,
                                        versionLabel = versionLabel
                                    )
                                }
                                isSavingQr = false
                                res.onSuccess {
                                    onShowFeedback("Kartu QR APK tersimpan ke Galeri (Pictures/Koplingfy)")
                                }.onFailure { err ->
                                    onShowFeedback(err.message ?: "Gagal menyimpan QR ke Galeri")
                                }
                            }
                        },
                        enabled = !isSavingQr && qrBitmap != null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_apk_save_qr_button")
                    ) {
                        if (isSavingQr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan QR", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Interactive "Apa yang Baru" Changelog dialog with:
 * - Tab 1 ("Versi Ini"): Filterable by category (Semua, Baru, Peningkatan, Perbaikan, UI & Visual)
 *   with expandable highlight cards.
 * - Tab 2 ("Rilis GitHub"): Live timeline of GitHub Releases with expandable release notes,
 *   direct APK download, and quick share link actions.
 */
@Composable
fun InteractiveChangelogDialog(
    updateState: AppUpdateUiState,
    onDismiss: () -> Unit,
    onRefreshGitHubHistory: () -> Unit,
    onDownloadRelease: (GitHubReleaseInfo) -> Unit,
    onShareReleaseLink: (url: String, versionLabel: String, isDirectApk: Boolean) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Versi Ini, 1 = Rilis GitHub
    var selectedCategory by remember { mutableStateOf(ChangelogCategory.ALL) }
    var expandedLocalTitle by remember {
        mutableStateOf<String?>(GitHubUpdateChecker.installedVersionHighlights.firstOrNull()?.title)
    }
    var expandedGitHubTag by remember(updateState.releaseHistory) {
        mutableStateOf<String?>(updateState.releaseHistory.firstOrNull()?.tagName)
    }

    val filteredHighlights = remember(selectedCategory) {
        if (selectedCategory == ChangelogCategory.ALL) {
            GitHubUpdateChecker.installedVersionHighlights
        } else {
            GitHubUpdateChecker.installedVersionHighlights.filter { it.category == selectedCategory }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp)
                .testTag("interactive_changelog_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, primary.copy(alpha = 0.25f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(primary.copy(alpha = 0.28f), secondary.copy(alpha = 0.2f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apa yang Baru",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = primary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "v${updateState.currentVersionName} • Build ${updateState.currentVersionCode}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (updateState.isAutoShownChangelog) {
                                Text(
                                    text = "Pembaruan terbaru",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("changelog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup changelog"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2-Tab Pill Switcher
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChangelogTabPill(
                            label = "Versi Ini",
                            icon = Icons.Rounded.NewReleases,
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("changelog_tab_local")
                        )
                        ChangelogTabPill(
                            label = "Riwayat Rilis",
                            icon = Icons.Rounded.History,
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                if (updateState.releaseHistory.isEmpty() && !updateState.isLoadingHistory) {
                                    onRefreshGitHubHistory()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("changelog_tab_github")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChangelogCategory.entries.forEach { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.18f),
                                    selectedLabelColor = primary
                                ),
                                modifier = Modifier.testTag("changelog_filter_${category.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable Expandable Highlights
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        filteredHighlights.forEach { item ->
                            val isExpanded = expandedLocalTitle == item.title
                            LocalChangelogHighlightCard(
                                item = item,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedLocalTitle = if (isExpanded) null else item.title
                                }
                            )
                        }
                    }
                } else {
                    // Tab 2: Official Releases Timeline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Riwayat Versi Resmi Koplingfy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onRefreshGitHubHistory,
                            enabled = !updateState.isLoadingHistory,
                            modifier = Modifier.testTag("changelog_github_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Muat ulang riwayat rilis",
                                tint = primary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 380.dp)
                    ) {
                        when {
                            updateState.isLoadingHistory && updateState.releaseHistory.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = primary, strokeWidth = 3.dp)
                                    Text(
                                        text = "Memuat riwayat rilis…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            !updateState.historyError.isNullOrBlank() && updateState.releaseHistory.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = updateState.historyError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedButton(
                                        onClick = onRefreshGitHubHistory,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Coba Lagi")
                                    }
                                }
                            }
                            updateState.releaseHistory.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = "Belum ada riwayat rilis yang dipublikasikan",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Catatan pembaruan dan tautan unduhan APK resmi Koplingfy akan tampil otomatis di sini saat versi baru tersedia.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    updateState.releaseHistory.forEach { release ->
                                        val isExpanded = expandedGitHubTag == release.tagName
                                        GitHubReleaseTimelineCard(
                                            release = release,
                                            isExpanded = isExpanded,
                                            onToggle = {
                                                expandedGitHubTag = if (isExpanded) null else release.tagName
                                            },
                                            onDownload = { onDownloadRelease(release) },
                                            onShare = {
                                                val directUrl = release.apkDownloadUrl ?: release.releasePageUrl
                                                onShareReleaseLink(
                                                    directUrl,
                                                    "v${release.versionName}",
                                                    release.apkDownloadUrl != null
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Dismiss CTA
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("changelog_got_it_button")
                ) {
                    Text(
                        text = if (updateState.isAutoShownChangelog) "Mulai Jelajahi" else "Tutup",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalChangelogHighlightCard(
    item: LocalChangelogItem,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val badgeColor = when (item.category) {
        ChangelogCategory.NEW -> MaterialTheme.colorScheme.primary
        ChangelogCategory.IMPROVEMENT -> MaterialTheme.colorScheme.tertiary
        ChangelogCategory.FIX -> Color(0xFFFFB74D)
        ChangelogCategory.UI -> MaterialTheme.colorScheme.secondary
        ChangelogCategory.ALL -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) badgeColor.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = item.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Tutup detail" else "Lihat detail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubReleaseTimelineCard(
    release: GitHubReleaseInfo,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val publishedShort = remember(release.publishedAt) {
        release.publishedAt.take(10).ifBlank { "Rilis Resmi" }
    }
    val apkSizeLabel = remember(release.apkSizeBytes) {
        if (release.apkSizeBytes > 0L) "%.1f MB".format(release.apkSizeBytes / (1024.0 * 1024.0)) else null
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (release.isNewerThanCurrent) primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (release.isNewerThanCurrent) {
                                primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = release.tagName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (release.isNewerThanCurrent) primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (release.isNewerThanCurrent) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = primary
                            ) {
                                Text(
                                    text = "BARU",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = publishedShort,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = release.releaseTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Tutup catatan rilis" else "Lihat catatan rilis",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = release.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (release.apkFileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                append("APK: ${release.apkFileName}")
                                if (apkSizeLabel != null) append(" ($apkSizeLabel)")
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (release.apkDownloadUrl != null) "Unduh APK" else "Buka Rilis",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onShare,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Bagikan link rilis",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
