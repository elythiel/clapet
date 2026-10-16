package xyz.martelle.clapet.home

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.martelle.clapet.R
import xyz.martelle.clapet.cover.coverGeometry
import xyz.martelle.clapet.cover.rememberCoverGeometry
import xyz.martelle.clapet.settings.SettingsActivity
import xyz.martelle.clapet.settingsRepository
import xyz.martelle.clapet.theme.ClapetTheme

/** How long "Pause for 5 minutes" leaves FlipLauncher alone. */
private const val PAUSE_DURATION_MS = 5 * 60 * 1000L

/**
 * The cover-screen home. Translucent window (see the Home theme), always drawn opaque by Compose.
 * Back is swallowed: a launcher has nowhere to go back to.
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Our 100 ms fade for every opening, whoever the caller is (Android 14+).
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.home_fade_in, R.anim.home_stay)
        enableEdgeToEdge()
        val settings = settingsRepository
        setContent {
            NavigationBackHandler(
                state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                onBackCompleted = {},
            )

            val accent by settings.accent.collectAsStateWithLifecycle(initialValue = null)
            val wallpaper by settings.wallpaper.collectAsStateWithLifecycle(initialValue = null)
            val pseudoHome by settings.pseudoHome.collectAsStateWithLifecycle(initialValue = null)
            val shortcuts by produceState(emptyList<Shortcut>()) {
                value = withContext(Dispatchers.Default) { loadShortcuts(this@HomeActivity, TemporaryShortcutQueries) }
            }
            val geometry = rememberCoverGeometry()

            val currentAccent = accent
            val currentWallpaper = wallpaper
            if (currentAccent == null || currentWallpaper == null) {
                // The window is transparent: stay opaque while the settings are read.
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }
            ClapetTheme(currentAccent) {
                HomeScreen(
                    geometry = geometry,
                    wallpaper = currentWallpaper,
                    wallpaperFile = settings.wallpaperFile,
                    shortcuts = shortcuts,
                    onShortcutClick = { launch(it) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onPause = if (pseudoHome?.enabled == true) ::pauseAndStepAside else null,
                )
            }
        }
    }

    /** Unfolded, the home has no business on the inner screen: step aside for the inner launcher. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (coverGeometry() == null) moveTaskToBack(true)
    }

    /** Lets the user reach FlipLauncher (to empty it, for instance) without switching the service off. */
    private fun pauseAndStepAside() {
        lifecycleScope.launch {
            settingsRepository.pausePseudoHome(untilMillis = System.currentTimeMillis() + PAUSE_DURATION_MS)
            moveTaskToBack(true)
        }
    }
}
