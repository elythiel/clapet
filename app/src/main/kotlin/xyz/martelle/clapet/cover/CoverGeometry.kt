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
 * Layout zones of the Mix Flip cover screen, in window pixels, derived from the camera cutout
 * Android declares: bottom right with the phone held upright ((810, 664) → (1208, 1392) in a
 * 1208 × 1392 window on the tested phone), top left once turned upside down.
 *
 * Only the two lenses actually hide pixels, so the wallpaper may draw everywhere,
 * but nothing interactive or textual may sit inside [cameras].
 */
@Immutable
data class CoverGeometry(val width: Int, val height: Int, val cameras: IntRect) {
    /** The phone is held upside down: the cameras sit top left instead of bottom right. */
    val upsideDown: Boolean get() = cameras.left <= 0

    /** Full height, beside the cameras: where everything textual or interactive goes. */
    val content: IntRect
        get() = if (upsideDown) IntRect(cameras.right, 0, width, height) else IntRect(0, 0, cameras.left, height)

    /** The rest of the cameras' column, above them (below once upside down): the player's square. */
    val square: IntRect
        get() = if (upsideDown) IntRect(0, cameras.bottom, cameras.right, height) else IntRect(cameras.left, 0, width, cameras.top)

    companion object {
        /** Returns null when no cutout fills a corner of the window, i.e. not on the cover screen. */
        fun from(width: Int, height: Int, cutouts: List<IntRect>): CoverGeometry? =
            cutouts
                .firstOrNull { it.isBottomRightBlock(width, height) || it.isTopLeftBlock(width, height) }
                ?.let { CoverGeometry(width, height, it) }

        private fun IntRect.isBottomRightBlock(width: Int, height: Int) =
            right >= width && bottom >= height && left > 0 && top > 0

        private fun IntRect.isTopLeftBlock(width: Int, height: Int) =
            left <= 0 && top <= 0 && right < width && bottom < height
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
