package xyz.martelle.clapet.cover

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeIntRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntRect

/**
 * Layout zones of the Mix Flip cover screen, in window pixels, derived from the camera
 * cutout Android declares in the bottom-right corner (1208 × 1392 window, cutout
 * (810, 664) → (1208, 1392) on the tested phone).
 *
 * Only the two lenses actually hide pixels, so the wallpaper may draw everywhere,
 * but nothing interactive or textual may sit inside [cameras].
 */
@Immutable
data class CoverGeometry(val width: Int, val height: Int, val cameras: IntRect) {
    /** Full width, above the cameras. */
    val topBand: IntRect get() = IntRect(0, 0, width, cameras.top)

    /** Left of the cameras, below the band. */
    val column: IntRect get() = IntRect(0, cameras.top, cameras.left, height)

    /** Right part of the band, straight above the cameras. */
    val square: IntRect get() = IntRect(cameras.left, 0, width, cameras.top)

    companion object {
        /** Returns null when no cutout sits in the bottom-right corner, i.e. not on the cover screen. */
        fun from(width: Int, height: Int, cutouts: List<IntRect>): CoverGeometry? =
            cutouts
                .firstOrNull { it.right >= width && it.bottom >= height && it.left > 0 && it.top > 0 }
                ?.let { CoverGeometry(width, height, it) }
    }
}

/** Reads the geometry of the activity's current window, or null when not on the cover screen. */
fun Activity.coverGeometry(): CoverGeometry? {
    val metrics = windowManager.currentWindowMetrics
    val cutouts = metrics.windowInsets.displayCutout?.boundingRects.orEmpty()
    return CoverGeometry.from(
        width = metrics.bounds.width(),
        height = metrics.bounds.height(),
        cutouts = cutouts.map { it.toComposeIntRect() },
    )
}

/** Recomputed on every configuration change, which is what folding or unfolding triggers. */
@Composable
fun rememberCoverGeometry(): CoverGeometry? {
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    return remember(activity, configuration) { activity?.coverGeometry() }
}
