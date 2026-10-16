package xyz.martelle.clapet.settings

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import java.io.File
import xyz.martelle.clapet.cover.CoverGeometry
import xyz.martelle.clapet.cover.coverStatusBar
import xyz.martelle.clapet.wallpaper.Wallpaper

@Immutable
data class AppInfo(val versionName: String, val packageName: String)

/** Every change the settings screens can request. Persisted by the owner, observed back as state. */
@Immutable
class SettingsActions(
    val onAccentChange: (AccentMode, Int) -> Unit,
    val onWallpaperSourceChange: (WallpaperSource) -> Unit,
    val onWallpaperDimChange: (Int) -> Unit,
    /** Returns false when the picked image cannot be used. */
    val importWallpaper: suspend (Uri) -> Boolean,
)

enum class SettingsPage { Menu, Accent, Wallpaper }

/** The wallpaper is blurred behind every settings page; Material cards sit on top. */
private val SettingsBlur = 22.dp

/** Secondary text stays readable on a bright image whatever dimming the home uses. */
private const val SettingsMinDimPercent = 60

@Composable
fun SettingsScreen(
    accent: AccentSettings,
    wallpaper: WallpaperSettings,
    wallpaperFile: File,
    geometry: CoverGeometry?,
    appInfo: AppInfo,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Menu) }
    // Back from a sub-page returns to the menu; from the menu, the system closes the activity.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = page != SettingsPage.Menu,
        onBackCompleted = { page = SettingsPage.Menu },
    )

    Box(modifier.fillMaxSize()) {
        Wallpaper(
            settings = wallpaper.copy(dimPercent = maxOf(wallpaper.dimPercent, SettingsMinDimPercent)),
            file = wallpaperFile,
            blurRadius = SettingsBlur,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.contentArea(geometry)) {
            when (page) {
                SettingsPage.Menu -> SettingsMenu(
                    accent = accent,
                    wallpaper = wallpaper,
                    appInfo = appInfo,
                    onOpen = { page = it },
                )
                SettingsPage.Accent -> AccentPage(accent, actions.onAccentChange)
                SettingsPage.Wallpaper -> WallpaperPage(wallpaper, actions)
            }
        }
    }
}

/**
 * On the cover screen, everything stays left of the cameras, which already clears the
 * cutout, so only the status bar is padded; elsewhere, the full safe area.
 */
@Composable
private fun Modifier.contentArea(geometry: CoverGeometry?): Modifier =
    if (geometry == null) {
        fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        val width = with(LocalDensity.current) { geometry.cameras.left.toDp() }
        width(width).fillMaxHeight().windowInsetsPadding(WindowInsets.coverStatusBar)
    }
