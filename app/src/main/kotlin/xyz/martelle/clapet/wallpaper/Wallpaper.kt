package xyz.martelle.clapet.wallpaper

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import xyz.martelle.clapet.settings.WallpaperSettings
import xyz.martelle.clapet.settings.WallpaperSource

/**
 * The opaque background Clapet always draws: its window is translucent (no HyperOS
 * slide between tasks), so anything less would let FlipLauncher show through.
 *
 * The veil (blur up to [blurRadius], dim up to [minDimPercent]) is applied in proportion to
 * [veil], from 0 (the plain wallpaper) to 1. It is read at draw time: animating it never recomposes.
 */
@Composable
fun Wallpaper(
    settings: WallpaperSettings,
    file: File,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
    minDimPercent: Int = 0,
    veil: () -> Float = { 1f },
) {
    val image by produceState<ImageBitmap?>(null, file, settings.imageRevision) {
        value = if (settings.hasImage) {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
        } else {
            null
        }
    }
    Box(modifier.background(Color.Black)) {
        val bitmap = image
        if (settings.source == WallpaperSource.Gallery && bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val radius = blurRadius.toPx() * veil()
                        // Same as Modifier.blur: clamped edges, clipped to the bounds.
                        renderEffect = if (radius >= 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                        clip = true
                    },
            )
            val dimPercent = settings.dimPercent
            val veiledPercent = maxOf(dimPercent, minDimPercent)
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val percent = dimPercent + (veiledPercent - dimPercent) * veil()
                        drawRect(Color.Black.copy(alpha = percent / 100f))
                    },
            )
        }
    }
}
