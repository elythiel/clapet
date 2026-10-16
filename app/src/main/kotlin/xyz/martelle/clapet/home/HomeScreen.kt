package xyz.martelle.clapet.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt
import xyz.martelle.clapet.R
import xyz.martelle.clapet.cover.CoverGeometry
import xyz.martelle.clapet.cover.CoverLayout
import xyz.martelle.clapet.settings.WallpaperSettings
import xyz.martelle.clapet.wallpaper.Wallpaper

/** Width kept free left of the cameras so the long-press menu never opens over them. */
private val MenuWidth = 220.dp

@Composable
fun HomeScreen(
    geometry: CoverGeometry?,
    wallpaper: WallpaperSettings,
    wallpaperFile: File,
    shortcuts: List<Shortcut>,
    onShortcutClick: (Shortcut) -> Unit,
    onOpenSettings: () -> Unit,
    /** Null when the pseudo-home is off: there is nothing to pause. */
    onPause: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    val openMenuLabel = stringResource(R.string.home_menu_open)

    Box(modifier.fillMaxSize()) {
        // Long press on any empty area: the background only receives touches no button takes.
        Wallpaper(
            settings = wallpaper,
            file = wallpaperFile,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { menuAt = it }) }
                .semantics {
                    onLongClick(openMenuLabel) {
                        menuAt = Offset.Zero
                        true
                    }
                },
        )

        val shortcutList = @Composable { ShortcutList(shortcuts, onShortcutClick) }
        if (geometry != null) {
            CoverLayout(geometry, Modifier.fillMaxSize(), column = { shortcutList() })
        } else {
            Box(Modifier.safeDrawingPadding()) { shortcutList() }
        }

        HomeMenu(
            anchor = menuAt,
            maxAnchorX = geometry?.cameras?.left,
            onDismiss = { menuAt = null },
            onOpenSettings = {
                menuAt = null
                onOpenSettings()
            },
            onPause = onPause?.let { pause ->
                {
                    menuAt = null
                    pause()
                }
            },
        )
    }
}

@Composable
private fun ShortcutList(shortcuts: List<Shortcut>, onClick: (Shortcut) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shortcuts.forEach { shortcut ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick(shortcut) }
                    .padding(horizontal = 8.dp),
            ) {
                Image(bitmap = shortcut.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(
                    text = shortcut.label,
                    color = Color.White,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeMenu(
    anchor: Offset?,
    maxAnchorX: Int?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onPause: (() -> Unit)?,
) {
    val menuWidthPx = with(LocalDensity.current) { MenuWidth.roundToPx() }
    val x = anchor?.x?.roundToInt() ?: 0
    val clampedX = if (maxAnchorX != null) x.coerceIn(0, (maxAnchorX - menuWidthPx).coerceAtLeast(0)) else x
    Box(Modifier.offset { IntOffset(clampedX, anchor?.y?.roundToInt() ?: 0) }) {
        DropdownMenu(expanded = anchor != null, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings), style = MaterialTheme.typography.bodyLarge) },
                onClick = onOpenSettings,
            )
            if (onPause != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_pause), style = MaterialTheme.typography.bodyLarge) },
                    onClick = onPause,
                )
            }
        }
    }
}
