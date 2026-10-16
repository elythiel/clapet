package xyz.martelle.clapet.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.martelle.clapet.cover.rememberCoverGeometry
import xyz.martelle.clapet.settings.SettingsActivity
import xyz.martelle.clapet.settingsRepository
import xyz.martelle.clapet.theme.ClapetTheme

/**
 * The cover-screen home. Translucent window (see the Home theme), always drawn opaque by Compose.
 * Back is swallowed: a launcher has nowhere to go back to.
 */
class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = settingsRepository
        setContent {
            NavigationBackHandler(
                state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                onBackCompleted = {},
            )

            val accent by settings.accent.collectAsStateWithLifecycle(initialValue = null)
            val wallpaper by settings.wallpaper.collectAsStateWithLifecycle(initialValue = null)
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
                )
            }
        }
    }
}
