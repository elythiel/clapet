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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
 */
@Composable
fun Wallpaper(
    settings: WallpaperSettings,
    file: File,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
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
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = settings.dimPercent / 100f)),
            )
        }
    }
}
