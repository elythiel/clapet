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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import xyz.martelle.clapet.cover.coverStatusBar
import xyz.martelle.clapet.health.HealthItem
import xyz.martelle.clapet.settings.label
import xyz.martelle.clapet.theme.LocalClapetColors
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
    /** First required setting that is off, if any: shown as a small accent dot. */
    healthAlert: HealthItem?,
    onHealthAlertClick: (HealthItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Position and visibility are separate: closing keeps the position, so the menu fades
    // out where it was instead of jumping to the corner during its exit animation.
    var menuPosition by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    val openMenuLabel = stringResource(R.string.home_menu_open)

    Box(modifier.fillMaxSize()) {
        // Long press on any empty area: the background only receives touches no button takes.
        Wallpaper(
            settings = wallpaper,
            file = wallpaperFile,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        menuPosition = it
                        menuOpen = true
                    })
                }
                .semantics {
                    onLongClick(openMenuLabel) {
                        menuPosition = Offset.Zero
                        menuOpen = true
                        true
                    }
                },
        )

        val shortcutList = @Composable { ShortcutList(shortcuts, onShortcutClick) }
        val alert = @Composable {
            if (healthAlert != null) HealthDot(healthAlert, onClick = { onHealthAlertClick(healthAlert) })
        }
        if (geometry != null) {
            CoverLayout(
                geometry,
                Modifier.fillMaxSize(),
                topBand = { Box(Modifier.windowInsetsPadding(WindowInsets.coverStatusBar)) { alert() } },
                column = { shortcutList() },
            )
        } else {
            Column(Modifier.safeDrawingPadding()) {
                alert()
                shortcutList()
            }
        }

        HomeMenu(
            open = menuOpen,
            position = menuPosition,
            maxAnchorX = geometry?.cameras?.left,
            onDismiss = { menuOpen = false },
            onOpenSettings = {
                menuOpen = false
                onOpenSettings()
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

/** Discreet on purpose: a 10 dp accent dot, in a full 48 dp touch target. */
@Composable
private fun HealthDot(item: HealthItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_health_alert, stringResource(item.label))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(start = 8.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.size(10.dp).background(LocalClapetColors.current.accent, CircleShape))
    }
}

@Composable
private fun HomeMenu(
    open: Boolean,
    position: Offset,
    maxAnchorX: Int?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val menuWidthPx = with(LocalDensity.current) { MenuWidth.roundToPx() }
    val x = position.x.roundToInt()
    val clampedX = if (maxAnchorX != null) x.coerceIn(0, (maxAnchorX - menuWidthPx).coerceAtLeast(0)) else x
    Box(Modifier.offset { IntOffset(clampedX, position.y.roundToInt()) }) {
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings), style = MaterialTheme.typography.bodyLarge) },
                onClick = onOpenSettings,
            )
        }
    }
}
