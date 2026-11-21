package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterFrames
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactive Lyric Quote Card Studio Sheet:
 * Lets the user preview 4 editorial themes, choose 9:16 Story vs 4:5 Feed ratio,
 * optionally include Indonesian translations, and Save to Gallery or Share directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricShareStudioSheet(
    track: Track,
    selectedLines: List<String>,
    translatedLines: List<String>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onExportCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTheme by remember { mutableStateOf(LyricCardTheme.EDITORIAL_GLASS) }
    var selectedRatio by remember { mutableStateOf(LyricCardRatio.STORY_9_16) }
    val hasTranslations = remember(translatedLines) { translatedLines.any { it.isNotBlank() } }
    var includeTranslation by remember(hasTranslations) { mutableStateOf(hasTranslations) }
    var isExporting by remember { mutableStateOf(false) }

    val effectiveTranslations = if (includeTranslation && hasTranslations) translatedLines else emptyList()

    val exportCard: (Boolean) -> Unit = { shareNow ->
        if (!isExporting) {
            isExporting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    LyricCardGenerator.saveLyricCardToGallery(
                        context = context,
                        track = track,
                        selectedLines = selectedLines,
                        accentColorInt = accentColor.toArgb(),
                        translatedLines = effectiveTranslations,
                        theme = selectedTheme,
                        ratio = selectedRatio
                    )
                }
                isExporting = false
                result.onSuccess { uri ->
                    onExportCompleted()
                    if (shareNow) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Bagikan Kartu Lirik ke Story"))
                        }
                        onDismiss()
                    } else {
                        onShowSnackbar("Kartu Lirik estetik disimpan ke Galeri!")
                        onDismiss()
                    }
                }.onFailure { err ->
                    onShowSnackbar("Gagal membuat Kartu Lirik: ${err.message}")
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
                .padding(bottom = 20.dp),
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
                        color = accentColor.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Studio Kartu Lirik",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "${selectedLines.size} baris dipilih • Siap untuk IG Story & WA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup Studio Kartu Lirik")
                }
            }

            // Live Card Preview Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val previewRatio = if (selectedRatio == LyricCardRatio.STORY_9_16) 9f / 16f else 4f / 5f
                val previewHeight = if (selectedRatio == LyricCardRatio.STORY_9_16) 356.dp else 310.dp
                AnimatedContent(
                    targetState = Triple(selectedTheme, selectedRatio, includeTranslation),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "lyric_preview_anim"
                ) { (theme, _, showTrans) ->
                    LyricCardLivePreview(
                        track = track,
                        lines = selectedLines,
                        translatedLines = if (showTrans && hasTranslations) translatedLines else emptyList(),
                        theme = theme,
                        accentColor = accentColor,
                        modifier = Modifier
                            .height(previewHeight)
                            .aspectRatio(previewRatio)
                            .clip(RoundedCornerShape(22.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(22.dp)
                            )
                    )
                }
            }

            // Theme Picker
            Text(
                text = "Gaya Kartu",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LyricCardTheme.entries) { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { selectedTheme = theme },
                        label = { Text(theme.label) },
                        leadingIcon = if (selectedTheme == theme) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("lyric_theme_${theme.name.lowercase()}")
                    )
                }
            }

            // Ratio + Translation toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricCardRatio.entries.forEach { ratio ->
                    FilterChip(
                        selected = selectedRatio == ratio,
                        onClick = { selectedRatio = ratio },
                        label = { Text(ratio.label) },
                        modifier = Modifier.testTag("lyric_ratio_${ratio.name.lowercase()}")
                    )
                }
            }

            if (hasTranslations) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Sertakan Terjemahan ID",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Tampilkan arti santai di bawah lirik",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = includeTranslation,
                            onCheckedChange = { includeTranslation = it },
                            modifier = Modifier.testTag("lyric_include_translation_switch")
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { exportCard(false) },
                    enabled = !isExporting,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f).testTag("lyric_studio_save_button")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan Galeri", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { exportCard(true) },
                    enabled = !isExporting,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f).testTag("lyric_studio_share_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bagikan Story", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LyricCardLivePreview(
    track: Track,
    lines: List<String>,
    translatedLines: List<String>,
    theme: LyricCardTheme,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val deepBg = lerp(Color(0xFF0A0B10), accentColor, 0.25f)
    when (theme) {
        LyricCardTheme.EDITORIAL_GLASS -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        listOf(
                            lerp(Color(0xFF0E1018), accentColor, 0.44f),
                            Color(0xFF07080D),
                            lerp(Color(0xFF06070B), accentColor, 0.26f)
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight(0.76f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(accentColor.copy(alpha = 0.4f))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "“",
                                color = lerp(accentColor, Color.White, 0.45f),
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 16.sp
                            )
                            lines.take(4).forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontSize = if (lines.size >= 4) 10.5.sp else 12.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val tr = translatedLines.getOrNull(idx)?.takeIf { it.isNotBlank() && !it.equals(line, true) }
                                if (tr != null) {
                                    Text(
                                        text = tr,
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 8.5.sp,
                                        lineHeight = 11.sp,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PreviewMiniWaveform(accentColor = lerp(accentColor, Color.White, 0.45f), barCount = 12)
                            Text(
                                text = "KOPLINGFY",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }
            }
        }

        LyricCardTheme.WARM_POLAROID -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        listOf(lerp(Color(0xFF1A1512), accentColor, 0.34f), Color(0xFF0E0D0C))
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF7F2E8),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .fillMaxHeight(0.80f)
                        .rotate(-1.8f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(lerp(Color(0xFF151519), accentColor, 0.32f), Color(0xFF0C0C0F))
                                    )
                                )
                                .padding(10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                lines.take(4).forEachIndexed { idx, line ->
                                    Text(
                                        text = line,
                                        color = Color(0xFFFAF6EE),
                                        fontSize = if (lines.size >= 4) 10.sp else 11.5.sp,
                                        lineHeight = 13.5.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tr = translatedLines.getOrNull(idx)?.takeIf { it.isNotBlank() && !it.equals(line, true) }
                                    if (tr != null) {
                                        Text(
                                            text = tr,
                                            color = Color(0xFFE5DDD0).copy(alpha = 0.8f),
                                            fontSize = 8.sp,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accentColor.copy(alpha = 0.4f))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color(0xFF1D1B18),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = Color(0xFF6B655D),
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        LyricCardTheme.NOIR_MINIMAL -> {
            Box(
                modifier = modifier
                    .background(Color(0xFF070709))
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "LYRIC QUOTE",
                            color = lerp(accentColor, Color.White, 0.3f),
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height(78.dp)
                                    .background(accentColor, CircleShape)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                lines.take(4).forEachIndexed { idx, line ->
                                    Text(
                                        text = line,
                                        color = Color(0xFFF5F5F7),
                                        fontSize = 11.5.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tr = translatedLines.getOrNull(idx)?.takeIf { it.isNotBlank() && !it.equals(line, true) }
                                    if (tr != null) {
                                        Text(
                                            text = tr,
                                            color = Color.White.copy(alpha = 0.62f),
                                            fontSize = 8.sp,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(deepBg)
                            )
                            Column {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • KOPLINGFY",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 7.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        LyricCardTheme.COLOR_POP -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        listOf(
                            lerp(accentColor, Color(0xFF141620), 0.22f),
                            lerp(accentColor, Color(0xFF0A0A10), 0.54f)
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = lerp(accentColor, Color(0xFF14141C), 0.18f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight(0.76f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            lines.take(4).forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val tr = translatedLines.getOrNull(idx)?.takeIf { it.isNotBlank() && !it.equals(line, true) }
                                if (tr != null) {
                                    Text(
                                        text = tr,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 8.5.sp,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.28f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • KOPLINGFY",
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontSize = 7.5.sp,
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
    }
}

/**
 * Unified Aesthetic Story Card Studio Sheet for sharing:
 * - Single Track ("Now Playing" Polaroid / Vinyl / Frosted Glass)
 * - Multiple Songs ("Beberapa Lagu": Mixtape Receipt / Glass Poster / Polaroid Collage)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStoryStudioSheet(
    seedTrack: Track?,
    candidateTracks: List<Track>,
    initialMultiMode: Boolean,
    initialSelectedTracks: List<Track>,
    defaultMixTitle: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onShareTextFallback: (Track) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val uniqueCandidates = remember(seedTrack, candidateTracks, initialSelectedTracks) {
        buildList {
            if (seedTrack != null) add(seedTrack)
            addAll(initialSelectedTracks)
            addAll(candidateTracks)
        }.distinctBy { it.id }.take(24)
    }

    var isMultiMode by remember(initialMultiMode) {
        mutableStateOf(initialMultiMode || (seedTrack == null && uniqueCandidates.size >= 2))
    }
    var singleStyle by remember { mutableStateOf(SingleStoryStyle.POLAROID) }
    var multiStyle by remember { mutableStateOf(MultiStoryStyle.MIXTAPE_RECEIPT) }
    var singleCaption by remember { mutableStateOf("") }
    var mixTitle by remember(defaultMixTitle) {
        mutableStateOf(defaultMixTitle.ifBlank { "Lagi Dengerin Ini" })
    }

    val chosenMultiTracks = remember(initialSelectedTracks, uniqueCandidates) {
        mutableStateListOf<Track>().apply {
            val initial = initialSelectedTracks.ifEmpty { uniqueCandidates.take(4) }.take(6)
            addAll(initial)
        }
    }
    var showTrackPicker by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val activeSingleTrack = seedTrack ?: chosenMultiTracks.firstOrNull() ?: uniqueCandidates.firstOrNull()

    val exportStory: (Boolean) -> Unit = { shareNow ->
        if (!isExporting) {
            isExporting = true
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    if (isMultiMode) {
                        StoryCardGenerator.saveMultiTrackStoryToGallery(
                            context = context,
                            title = mixTitle,
                            tracks = chosenMultiTracks.toList(),
                            style = multiStyle,
                            accentColorInt = accentColor.toArgb()
                        )
                    } else {
                        val t = activeSingleTrack ?: return@withContext Result.failure(IllegalStateException("Belum ada lagu dipilih"))
                        StoryCardGenerator.saveSingleTrackStoryToGallery(
                            context = context,
                            track = t,
                            style = singleStyle,
                            accentColorInt = accentColor.toArgb(),
                            caption = singleCaption
                        )
                    }
                }
                isExporting = false
                res.onSuccess { uri ->
                    if (shareNow) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Bagikan Kartu Story Estetik"))
                        }
                        onDismiss()
                    } else {
                        onShowSnackbar("Gambar Story 9:16 disimpan ke Galeri!")
                        onDismiss()
                    }
                }.onFailure { err ->
                    onShowSnackbar("Gagal membuat gambar Story: ${err.message}")
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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(shape = CircleShape, color = accentColor.copy(alpha = 0.16f)) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.padding(8.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Studio Kartu Story",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Format 9:16 HD untuk IG Story & Status WA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup Studio Story")
                }
            }

            // Mode Switcher: 1 Lagu (Now Playing) vs Beberapa Lagu
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StoryModePill(
                        label = "1 Lagu (Now Playing)",
                        icon = Icons.Rounded.MusicNote,
                        selected = !isMultiMode,
                        enabled = activeSingleTrack != null,
                        onClick = { isMultiMode = false },
                        modifier = Modifier.weight(1f).testTag("story_mode_single")
                    )
                    StoryModePill(
                        label = "Beberapa Lagu (${chosenMultiTracks.size})",
                        icon = Icons.Rounded.LibraryMusic,
                        selected = isMultiMode,
                        enabled = uniqueCandidates.isNotEmpty(),
                        onClick = { isMultiMode = true },
                        modifier = Modifier.weight(1f).testTag("story_mode_multi")
                    )
                }
            }

            // Live 9:16 Story Preview
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = if (isMultiMode) "multi_${multiStyle.name}" else "single_${singleStyle.name}",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "story_preview_anim"
                ) { _ ->
                    val previewModifier = Modifier
                        .height(354.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(22.dp)
                        )

                    if (!isMultiMode && activeSingleTrack != null) {
                        SingleStoryLivePreview(
                            track = activeSingleTrack,
                            style = singleStyle,
                            accentColor = accentColor,
                            caption = singleCaption,
                            modifier = previewModifier
                        )
                    } else {
                        MultiStoryLivePreview(
                            title = mixTitle.ifBlank { "Lagi Dengerin Ini" },
                            tracks = chosenMultiTracks.toList(),
                            style = multiStyle,
                            accentColor = accentColor,
                            modifier = previewModifier
                        )
                    }
                }
            }

            // Style Picker Chips
            Text(
                text = if (isMultiMode) "Template Story Multi-Lagu" else "Template Now Playing",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )

            if (!isMultiMode) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SingleStoryStyle.entries) { style ->
                        val icon = when (style) {
                            SingleStoryStyle.POLAROID -> Icons.Rounded.FilterFrames
                            SingleStoryStyle.VINYL -> Icons.Rounded.Album
                            SingleStoryStyle.GLASS -> Icons.Rounded.AutoAwesome
                        }
                        FilterChip(
                            selected = singleStyle == style,
                            onClick = { singleStyle = style },
                            label = { Text(style.label) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier.testTag("single_story_style_${style.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = singleCaption,
                    onValueChange = { if (it.length <= 42) singleCaption = it },
                    label = { Text("Catatan singkat di kartu (opsional)") },
                    placeholder = { Text("Misal: Vibe malam ini ✨") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("single_story_caption_input")
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MultiStoryStyle.entries) { style ->
                        val icon = when (style) {
                            MultiStoryStyle.MIXTAPE_RECEIPT -> Icons.Rounded.ReceiptLong
                            MultiStoryStyle.GLASS_POSTER -> Icons.Rounded.AutoAwesome
                            MultiStoryStyle.POLAROID_COLLAGE -> Icons.Rounded.FilterFrames
                        }
                        FilterChip(
                            selected = multiStyle == style,
                            onClick = { multiStyle = style },
                            label = { Text(style.label) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier.testTag("multi_story_style_${style.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = mixTitle,
                    onValueChange = { if (it.length <= 32) mixTitle = it },
                    label = { Text("Judul Kartu Story") },
                    placeholder = { Text("Lagi Dengerin Ini") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("multi_story_title_input")
                )

                // Track Picker Collapsible Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTrackPicker = !showTrackPicker },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Pilih Lagu (${chosenMultiTracks.size}/6)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (showTrackPicker) "Ketuk lagu untuk tambah atau hapus" else "Ketuk untuk ubah daftar lagu di kartu",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (showTrackPicker) "Selesai" else "Ubah",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = accentColor
                            )
                        }

                        if (showTrackPicker) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 210.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uniqueCandidates.forEach { candidate ->
                                    val isSelected = chosenMultiTracks.any { it.id == candidate.id }
                                    Surface(
                                        onClick = {
                                            if (isSelected) {
                                                if (chosenMultiTracks.size > 1) {
                                                    chosenMultiTracks.removeAll { it.id == candidate.id }
                                                } else {
                                                    onShowSnackbar("Minimal 1 lagu untuk kartu Story")
                                                }
                                            } else if (chosenMultiTracks.size < 6) {
                                                chosenMultiTracks.add(candidate)
                                            } else {
                                                onShowSnackbar("Maksimal 6 lagu agar tampilan Story tetap estetik")
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = candidate.artworkUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = candidate.title,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = candidate.artist,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "Dipilih",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Primary Export CTA Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { exportStory(false) },
                    enabled = !isExporting && (if (isMultiMode) chosenMultiTracks.isNotEmpty() else activeSingleTrack != null),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f).testTag("story_studio_save_button")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan Galeri", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { exportStory(true) },
                    enabled = !isExporting && (if (isMultiMode) chosenMultiTracks.isNotEmpty() else activeSingleTrack != null),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.weight(1f).testTag("story_studio_share_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bagikan Story", fontWeight = FontWeight.Bold)
                }
            }

            // Optional Text Link Share Fallback for Single Track
            if (!isMultiMode && activeSingleTrack != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onShareTextFallback(activeSingleTrack)
                        },
                        modifier = Modifier.testTag("story_studio_share_text_button")
                    ) {
                        Text(
                            text = "Bagikan tautan teks saja",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SingleStoryLivePreview(
    track: Track,
    style: SingleStoryStyle,
    accentColor: Color,
    caption: String,
    modifier: Modifier = Modifier
) {
    val bgBrush = Brush.linearGradient(
        listOf(
            lerp(Color(0xFF141316), accentColor, 0.42f),
            Color(0xFF0A0A0E),
            lerp(Color(0xFF08080C), accentColor, 0.24f)
        )
    )

    Box(
        modifier = modifier.background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            SingleStoryStyle.POLAROID -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 18.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    StoryPreviewTopTag("NOW PLAYING • FILM", accentColor)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF8F4EB),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .rotate(-2.4f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(accentColor.copy(alpha = 0.35f))
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = track.title,
                                color = Color(0xFF1A1816),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = Color(0xFF665E55),
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (caption.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "“$caption”",
                                    color = Color(0xFF423C36),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PreviewMiniWaveform(accentColor = Color(0xFF1E1C1A), inactiveColor = Color(0xFFD2C9BC), barCount = 14)
                                Text(
                                    text = formatDuration(track.durationMs),
                                    color = Color(0xFF7D7469),
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    StoryPreviewFooter()
                }
            }

            SingleStoryStyle.VINYL -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 18.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    StoryPreviewTopTag("SPINNING ON VINYL", accentColor)

                    // Vinyl sleeve + peeking disc
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Peeking Vinyl Disc
                        Canvas(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-6).dp)
                                .size(112.dp)
                        ) {
                            val r = size.minDimension / 2f
                            drawCircle(Color(0xFF111116), radius = r)
                            drawCircle(Color.White.copy(alpha = 0.16f), radius = r * 0.84f, style = Stroke(1f))
                            drawCircle(Color.White.copy(alpha = 0.12f), radius = r * 0.66f, style = Stroke(1f))
                            drawCircle(accentColor, radius = r * 0.34f)
                            drawCircle(Color(0xFF0E0E12), radius = r * 0.09f)
                        }
                        // Album Sleeve
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(118.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.4f))
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (caption.isNotBlank()) {
                            Text(
                                text = caption,
                                color = lerp(accentColor, Color.White, 0.52f),
                                fontSize = 8.5.sp,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        PreviewMiniWaveform(accentColor = lerp(accentColor, Color.White, 0.4f), barCount = 24, modifier = Modifier.fillMaxWidth())
                    }

                    StoryPreviewFooter()
                }
            }

            SingleStoryStyle.GLASS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 18.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.35f))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (caption.isNotBlank()) {
                                    Text(
                                        text = caption,
                                        color = lerp(accentColor, Color.White, 0.55f),
                                        fontSize = 8.sp,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            PreviewMiniWaveform(accentColor = Color.White, barCount = 20, modifier = Modifier.fillMaxWidth())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(24.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Pause, contentDescription = null, tint = Color(0xFF101116), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    StoryPreviewFooter()
                }
            }
        }
    }
}

@Composable
private fun MultiStoryLivePreview(
    title: String,
    tracks: List<Track>,
    style: MultiStoryStyle,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val bgBrush = Brush.linearGradient(
        listOf(
            lerp(Color(0xFF181412), accentColor, 0.38f),
            Color(0xFF0A0A0E),
            lerp(Color(0xFF07080B), accentColor, 0.24f)
        )
    )
    Box(
        modifier = modifier.background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            MultiStoryStyle.MIXTAPE_RECEIPT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 14.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF5F1E7),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "KOPLINGFY MIX",
                                    color = Color(0xFF191816),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = title,
                                    color = Color(0xFF191816),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    tracks.take(4).forEach { t ->
                                        AsyncImage(
                                            model = t.artworkUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(accentColor.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tracks.take(6).forEachIndexed { idx, t ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "0${idx + 1}",
                                            color = Color(0xFF6A645B),
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = t.title,
                                                color = Color(0xFF191816),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = t.artist,
                                                color = Color(0xFF6A645B),
                                                fontSize = 6.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = formatDuration(t.durationMs),
                                            color = Color(0xFF191816),
                                            fontSize = 7.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            // Mini barcode strip
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth(0.76f)
                                    .height(16.dp)
                            ) {
                                val bars = 24
                                val step = size.width / bars
                                for (i in 0 until bars) {
                                    val bw = if (i % 3 == 0) step * 0.65f else step * 0.35f
                                    drawRect(
                                        color = Color(0xFF191816),
                                        topLeft = Offset(i * step, 0f),
                                        size = Size(bw, size.height)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    StoryPreviewFooter()
                }
            }

            MultiStoryStyle.GLASS_POSTER -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 2x2 Mosaic
                            val mosaicTracks = (0 until 4).mapNotNull { i ->
                                tracks.getOrNull(i % tracks.size.coerceAtLeast(1))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    mosaicTracks.take(2).forEach { t ->
                                        AsyncImage(
                                            model = t.artworkUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(accentColor.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    mosaicTracks.drop(2).take(2).forEach { t ->
                                        AsyncImage(
                                            model = t.artworkUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(accentColor.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }

                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tracks.take(5).forEach { t ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.24f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AsyncImage(
                                                model = t.artworkUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(accentColor.copy(alpha = 0.35f))
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = t.title,
                                                    color = Color.White,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = t.artist,
                                                    color = Color.White.copy(alpha = 0.72f),
                                                    fontSize = 6.5.sp,
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
                    Spacer(Modifier.height(6.dp))
                    StoryPreviewFooter()
                }
            }

            MultiStoryStyle.POLAROID_COLLAGE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    StoryPreviewTopTag("PLAYLIST MOODBOARD", accentColor)

                    // Mini tilted polaroid collage preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val offsets = listOf(
                            Triple((-34).dp, (-14).dp, -9f),
                            Triple(34.dp, (-10).dp, 8f),
                            Triple((-22).dp, 26.dp, 5f),
                            Triple(28.dp, 28.dp, -6f)
                        )
                        tracks.take(4).forEachIndexed { idx, t ->
                            val (ox, oy, rot) = offsets[idx]
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF7F2E8),
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .offset(x = ox, y = oy)
                                    .rotate(rot)
                                    .size(width = 58.dp, height = 68.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    AsyncImage(
                                        model = t.artworkUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(accentColor.copy(alpha = 0.35f))
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF141211).copy(alpha = 0.90f),
                        border = BorderStroke(0.8.dp, Color(0xFFF7F2E8).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = title,
                                color = Color(0xFFF7F2E8),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            tracks.take(5).forEachIndexed { idx, t ->
                                Text(
                                    text = "${idx + 1}. ${t.title} — ${t.artist}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 7.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    StoryPreviewFooter()
                }
            }
        }
    }
}

@Composable
private fun StoryPreviewTopTag(label: String, accentColor: Color) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(lerp(accentColor, Color.White, 0.35f), CircleShape)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
        }
    }
}

@Composable
private fun StoryPreviewFooter() {
    Text(
        text = "KOPLINGFY",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 7.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PreviewMiniWaveform(
    accentColor: Color,
    inactiveColor: Color = Color.White.copy(alpha = 0.28f),
    barCount: Int = 16,
    modifier: Modifier = Modifier.width(64.dp)
) {
    val heights = remember { floatArrayOf(0.35f, 0.7f, 0.95f, 0.5f, 0.85f, 1f, 0.45f, 0.8f, 0.9f, 0.55f, 0.4f, 0.88f) }
    Canvas(modifier = modifier.height(14.dp)) {
        val step = size.width / barCount.coerceAtLeast(1)
        val bw = (step * 0.52f).coerceAtLeast(1.5f)
        for (i in 0 until barCount) {
            val h = (heights[i % heights.size] * size.height).coerceAtLeast(3f)
            val color = if (i <= (barCount * 0.62f).toInt()) accentColor else inactiveColor
            drawRoundRect(
                color = color,
                topLeft = Offset(i * step, (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}
