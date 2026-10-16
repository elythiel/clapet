package xyz.martelle.clapet.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import xyz.martelle.clapet.R
import kotlin.math.max
import kotlin.math.min

@Immutable
data class PaletteColor(val argb: Int, @param:StringRes val name: Int)

/** Seven accents readable on black. The first one is the default. */
val AccentPalette: List<PaletteColor> = listOf(
    PaletteColor(Color(0xFFF4C2D4).toArgb(), R.string.accent_pale_pink),
    PaletteColor(Color(0xFFF2B248).toArgb(), R.string.accent_amber),
    PaletteColor(Color(0xFFF08F6F).toArgb(), R.string.accent_coral),
    PaletteColor(Color(0xFFB9A3F2).toArgb(), R.string.accent_lavender),
    PaletteColor(Color(0xFF7FA7F5).toArgb(), R.string.accent_blue),
    PaletteColor(Color(0xFF6FD3C7).toArgb(), R.string.accent_turquoise),
    PaletteColor(Color(0xFF9FD88F).toArgb(), R.string.accent_green),
)

/** WCAG contrast ratio between two opaque colors, from 1 to 21. */
fun contrastRatio(first: Color, second: Color): Float {
    val lighter = max(first.luminance(), second.luminance())
    val darker = min(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Black or white, whichever reads better on [background]; one of them always reaches 4.5:1. */
fun contentColorOn(background: Color): Color =
    if (contrastRatio(background, Color.Black) >= contrastRatio(background, Color.White)) Color.Black else Color.White
