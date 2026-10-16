package xyz.martelle.clapet.settings

import androidx.compose.runtime.Immutable
import xyz.martelle.clapet.theme.AccentPalette

enum class AccentMode { Auto, Palette, Custom }

/** [color] is an ARGB value; it is ignored in [AccentMode.Auto], which follows Material You. */
@Immutable
data class AccentSettings(val mode: AccentMode, val color: Int) {
    companion object {
        val Default = AccentSettings(AccentMode.Palette, AccentPalette.first().argb)
    }
}

enum class WallpaperSource { Black, Gallery }

/** [imageRevision] is 0 until an image has been imported. */
@Immutable
data class WallpaperSettings(val source: WallpaperSource, val dimPercent: Int, val imageRevision: Long) {
    val hasImage: Boolean get() = imageRevision != 0L

    companion object {
        const val MAX_DIM_PERCENT = 90
        val Default = WallpaperSettings(WallpaperSource.Black, dimPercent = 45, imageRevision = 0L)
    }
}

/**
 * Whether Clapet replaces FlipLauncher on the cover screen. [pausedUntilMillis] is a
 * wall-clock time (System.currentTimeMillis) until which the service stays idle; 0 = not paused.
 */
@Immutable
data class PseudoHomeSettings(val enabled: Boolean, val pausedUntilMillis: Long) {
    fun isActiveAt(nowMillis: Long): Boolean = enabled && nowMillis >= pausedUntilMillis

    companion object {
        val Default = PseudoHomeSettings(enabled = true, pausedUntilMillis = 0L)
    }
}
