package xyz.martelle.clapet.settings

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import java.io.File
import xyz.martelle.clapet.cover.CoverGeometry
import xyz.martelle.clapet.health.HealthItem
import xyz.martelle.clapet.health.HealthReport
import xyz.martelle.clapet.cover.coverStatusBar
import xyz.martelle.clapet.wallpaper.Wallpaper

@Immutable
data class AppInfo(val versionName: String, val packageName: String)

/** Every change the settings screens can request. Persisted by the owner, observed back as state. */
@Immutable
class SettingsActions(
    val onPseudoHomeEnabledChange: (Boolean) -> Unit,
    val onAccentChange: (AccentMode, Int) -> Unit,
    val onWallpaperSourceChange: (WallpaperSource) -> Unit,
    val onWallpaperDimChange: (Int) -> Unit,
    /** Returns false when the picked image cannot be used. */
    val importWallpaper: suspend (Uri) -> Boolean,
    val onUnlockNow: () -> Unit,
    /** Opens the system screen that fixes a setting (DUMP is handled in-app). */
    val onFixHealth: (HealthItem) -> Unit,
    val onOpenAppInfo: () -> Unit,
    /** Pauses the pseudo-home and steps aside, so FlipLauncher can be tidied up. */
    val onOpenXiaomiHome: () -> Unit,
    /** The health page was shown: the first-launch onboarding is done. */
    val onHealthShown: () -> Unit,
    /** Returns false when Shizuku could not grant DUMP. */
    val grantDumpWithShizuku: suspend () -> Boolean,
)

enum class SettingsPage { Menu, Health, Unlock, Accent, Wallpaper }

/** The wallpaper is blurred behind every settings page; Material cards sit on top. */
private val SettingsBlur = 22.dp

/** Secondary text stays readable on a bright image whatever dimming the home uses. */
private const val SettingsMinDimPercent = 60

@Composable
fun SettingsScreen(
    accent: AccentSettings,
    wallpaper: WallpaperSettings,
    pseudoHome: PseudoHomeSettings,
    unlock: UnlockUiState,
    health: HealthReport,
    wallpaperFile: File,
    geometry: CoverGeometry?,
    appInfo: AppInfo,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    /** Health on first launch, or the page a home alert points to; then the menu. */
    initialPage: SettingsPage = SettingsPage.Menu,
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    LaunchedEffect(page) {
        if (page == SettingsPage.Health) actions.onHealthShown()
    }
    // Back from a sub-page returns to the menu; from the menu, the system closes the activity.
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = page != SettingsPage.Menu,
        onBackCompleted = { page = SettingsPage.Menu },
    )

    Box(modifier.fillMaxSize()) {
        Wallpaper(
            settings = wallpaper,
            file = wallpaperFile,
            blurRadius = SettingsBlur,
            minDimPercent = SettingsMinDimPercent,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.contentArea(geometry)) {
            when (page) {
                SettingsPage.Menu -> SettingsMenu(
                    accent = accent,
                    wallpaper = wallpaper,
                    pseudoHome = pseudoHome,
                    unlock = unlock,
                    health = health,
                    appInfo = appInfo,
                    onPseudoHomeEnabledChange = actions.onPseudoHomeEnabledChange,
                    onOpen = { page = it },
                )
                SettingsPage.Health -> HealthPage(
                    report = health,
                    onFix = { item -> if (item == HealthItem.CoverUnlock) page = SettingsPage.Unlock else actions.onFixHealth(item) },
                    onOpenAppInfo = actions.onOpenAppInfo,
                    onOpenXiaomiHome = if (pseudoHome.enabled) actions.onOpenXiaomiHome else null,
                )
                SettingsPage.Unlock -> UnlockPage(unlock, actions.onUnlockNow, actions.grantDumpWithShizuku)
                SettingsPage.Accent -> AccentPage(accent, actions.onAccentChange)
                SettingsPage.Wallpaper -> WallpaperPage(wallpaper, actions)
            }
        }
    }
}

/**
 * On the cover screen, everything stays beside the cameras (left of them, right once upside
 * down), which already clears the cutout, so only the status bar is padded; elsewhere, the full
 * safe area.
 */
@Composable
private fun Modifier.contentArea(geometry: CoverGeometry?): Modifier =
    if (geometry == null) {
        fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        val width = with(LocalDensity.current) { geometry.content.width.toDp() }
        offset { IntOffset(geometry.content.left, 0) }
            .width(width)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.coverStatusBar)
    }
