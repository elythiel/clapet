package xyz.martelle.clapet.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import xyz.martelle.clapet.cover.rememberCoverGeometry
import xyz.martelle.clapet.coverUnlock
import xyz.martelle.clapet.settingsRepository
import xyz.martelle.clapet.unlock.ShizukuDumpGranter
import xyz.martelle.clapet.unlock.UnlockResult
import xyz.martelle.clapet.theme.ClapetTheme

/** Settings, full screen on either display: from the home menu (cover) or the app icon (inner screen). */
class SettingsActivity : ComponentActivity() {

    /** Bumped after a grant attempt, so the page re-reads the permission at once. */
    private val accessVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = settingsRepository
        val unlock = coverUnlock
        val shizuku = ShizukuDumpGranter(this)
        val appInfo = AppInfo(
            versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
            packageName = packageName,
        )
        // Writes belong to the activity, not to the screen being shown.
        val actions = SettingsActions(
            onPseudoHomeEnabledChange = { lifecycleScope.launch { settings.setPseudoHomeEnabled(it) } },
            onAccentChange = { mode, color -> lifecycleScope.launch { settings.setAccent(mode, color) } },
            onWallpaperSourceChange = { lifecycleScope.launch { settings.setWallpaperSource(it) } },
            onWallpaperDimChange = { lifecycleScope.launch { settings.setWallpaperDim(it) } },
            // Runs in the activity scope: leaving the page does not abort a half-written import.
            importWallpaper = { uri -> lifecycleScope.async { settings.importWallpaper(uri) }.await() },
            onUnlockNow = { unlock.requestUnlock() },
            grantDumpWithShizuku = {
                // Owned by the activity, like the import: leaving the page does not abort it.
                lifecycleScope.async {
                    val granted = shizuku.grantDump()
                    if (granted) unlock.requestUnlock().join()
                    accessVersion.intValue++
                    granted
                }.await()
            },
        )
        setContent {
            val accent by settings.accent.collectAsStateWithLifecycle(initialValue = null)
            val wallpaper by settings.wallpaper.collectAsStateWithLifecycle(initialValue = null)
            val pseudoHome by settings.pseudoHome.collectAsStateWithLifecycle(initialValue = null)
            val unlockStatus by settings.unlockStatus.collectAsStateWithLifecycle(initialValue = UnlockStatus.Default)
            val lastResult by unlock.lastResult.collectAsStateWithLifecycle()
            // Permission and Shizuku can change outside the app: re-read them on every resume.
            var resumes by remember { mutableIntStateOf(0) }
            LifecycleResumeEffect(Unit) {
                resumes++
                onPauseOrDispose {}
            }
            val version = accessVersion.intValue
            val unlockState = remember(resumes, version, unlockStatus, lastResult) {
                UnlockUiState(
                    hasDumpPermission = unlock.hasDumpPermission(),
                    shizukuRunning = shizuku.isRunning(),
                    status = unlockStatus,
                    lastFailure = (lastResult as? UnlockResult.Failed)?.reason,
                )
            }
            val geometry = rememberCoverGeometry()

            val currentAccent = accent
            val currentWallpaper = wallpaper
            val currentPseudoHome = pseudoHome
            if (currentAccent == null || currentWallpaper == null || currentPseudoHome == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }
            ClapetTheme(currentAccent) {
                SettingsScreen(
                    accent = currentAccent,
                    wallpaper = currentWallpaper,
                    pseudoHome = currentPseudoHome,
                    unlock = unlockState,
                    wallpaperFile = settings.wallpaperFile,
                    geometry = geometry,
                    appInfo = appInfo,
                    actions = actions,
                )
            }
        }
    }
}
