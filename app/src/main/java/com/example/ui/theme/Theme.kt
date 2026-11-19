package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.AccentPreset
import com.example.model.ThemeMode

private val KoplingfyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private fun buildDarkColorScheme(
    accent: AccentPreset,
    pureBlack: Boolean
): ColorScheme {
    val (primary, onPrimary, primaryContainer) = when (accent) {
        AccentPreset.EMERALD -> Triple(EmeraldPrimary, EmeraldOnPrimary, EmeraldContainer)
        AccentPreset.CORAL -> Triple(CoralPrimary, CoralOnPrimary, CoralContainer)
        AccentPreset.VIOLET -> Triple(VioletPrimary, VioletOnPrimary, VioletContainer)
        AccentPreset.AMBER -> Triple(AmberPrimary, AmberOnPrimary, AmberContainer)
    }

    val bg = if (pureBlack) PureBlackBackground else ObsidianBackground
    val surface = if (pureBlack) PureBlackSurface else ObsidianSurface
    val surfaceVariant = if (pureBlack) PureBlackSurfaceVariant else ObsidianSurfaceVariant

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = primary,
        secondary = GoldSecondary,
        onSecondary = GoldOnSecondary,
        secondaryContainer = GoldContainer,
        onSecondaryContainer = GoldSecondary,
        tertiary = VioletPrimary,
        onTertiary = VioletOnPrimary,
        background = bg,
        onBackground = TextPrimaryDark,
        surface = surface,
        onSurface = TextPrimaryDark,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = TextSecondaryDark,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = ObsidianSurfaceHigh,
        outline = ObsidianOutline,
        outlineVariant = Color.White.copy(alpha = 0.07f),
        error = ErrorRed,
        onError = Color.White
    )
}

private fun buildLightColorScheme(accent: AccentPreset): ColorScheme {
    val primary = when (accent) {
        AccentPreset.EMERALD -> EmeraldLight
        AccentPreset.CORAL -> CoralLight
        AccentPreset.VIOLET -> VioletLight
        AccentPreset.AMBER -> AmberLight
    }

    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.14f),
        onPrimaryContainer = primary,
        secondary = Color(0xFFC47D0E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFF1D6),
        onSecondaryContainer = Color(0xFF5A3700),
        background = AlabasterBackground,
        onBackground = TextPrimaryLight,
        surface = AlabasterSurface,
        onSurface = TextPrimaryLight,
        surfaceVariant = AlabasterSurfaceVariant,
        onSurfaceVariant = TextSecondaryLight,
        surfaceContainerLow = AlabasterSurface,
        surfaceContainer = AlabasterSurfaceVariant,
        surfaceContainerHigh = Color(0xFFE2E7F2),
        outline = AlabasterOutline,
        outlineVariant = Color.Black.copy(alpha = 0.08f),
        error = Color(0xFFD32F2F),
        onError = Color.White
    )
}

@Composable
fun KoplingfyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.EMERALD,
    content: @Composable () -> Unit
) {
    val isDark = themeMode != ThemeMode.LIGHT
    val colorScheme = when (themeMode) {
        ThemeMode.DARK -> buildDarkColorScheme(accentPreset, pureBlack = false)
        ThemeMode.OLED -> buildDarkColorScheme(accentPreset, pureBlack = true)
        ThemeMode.LIGHT -> buildLightColorScheme(accentPreset)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KoplingfyTypography,
        shapes = KoplingfyShapes,
        content = content
    )
}
