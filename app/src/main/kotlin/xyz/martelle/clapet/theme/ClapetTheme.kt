package xyz.martelle.clapet.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import xyz.martelle.clapet.settings.AccentMode
import xyz.martelle.clapet.settings.AccentSettings

@Immutable
data class ClapetColors(val accent: Color, val onAccent: Color)

/** Text on black and on the dimmed wallpaper. */
val TextPrimary = Color(0xFFF2EFE9)

val LocalClapetColors = staticCompositionLocalOf {
    val accent = Color(AccentSettings.Default.color)
    ClapetColors(accent, contentColorOn(accent))
}

/** Resolves the accent color once for the whole tree. Typography keeps the system font (HyperOS theme). */
@Composable
fun ClapetTheme(accent: AccentSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accentColor = when (accent.mode) {
        AccentMode.Auto -> dynamicDarkColorScheme(context).primary
        AccentMode.Palette, AccentMode.Custom -> Color(accent.color)
    }
    val colors = remember(accentColor) { ClapetColors(accentColor, contentColorOn(accentColor)) }
    val colorScheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = Color.Black,
        onBackground = TextPrimary,
        surface = Color.Black,
        onSurface = TextPrimary,
        // Neutral greys instead of Material's default purple-tinted containers.
        onSurfaceVariant = Color(0xFFB4B0A9),
        surfaceContainerLow = Color(0xFF161618),
        surfaceContainer = Color(0xFF1C1C1F),
        surfaceContainerHigh = Color(0xFF242427),
        surfaceContainerHighest = Color(0xFF2E2E31),
        outline = Color(0xFF5A5752),
    )
    MaterialTheme(colorScheme = colorScheme) {
        // No Material Surface sits under Clapet's screens, so set the default text color here.
        CompositionLocalProvider(
            LocalClapetColors provides colors,
            LocalContentColor provides TextPrimary,
            content = content,
        )
    }
}
