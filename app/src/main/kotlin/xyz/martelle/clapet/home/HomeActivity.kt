package xyz.martelle.clapet.home

import android.content.ActivityNotFoundException
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.martelle.clapet.R
import xyz.martelle.clapet.cover.coverGeometry
import xyz.martelle.clapet.cover.rememberCoverGeometry
import xyz.martelle.clapet.settings.SettingsActivity
import xyz.martelle.clapet.coverUnlock
import xyz.martelle.clapet.header.HeaderSource
import xyz.martelle.clapet.header.openEventIntent
import xyz.martelle.clapet.health.HealthChecker
import xyz.martelle.clapet.health.HealthReport
import xyz.martelle.clapet.health.openHealthFix
import xyz.martelle.clapet.apps.Favorites
import xyz.martelle.clapet.installedApps
import xyz.martelle.clapet.notificationHub
import xyz.martelle.clapet.settingsRepository
import xyz.martelle.clapet.theme.ClapetTheme

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
        // The allow list may be stale (apps installed while the process was dead): replay it.
        coverUnlock.requestUnlock()
        val settings = settingsRepository
        val healthChecker = HealthChecker(this)
        val appList = installedApps.also { it.start() }
        val headerSource = HeaderSource(this)
        val hub = notificationHub
        val notificationActions = NotificationActions(
            // A notification that points nowhere opens its app instead.
            onOpen = { app, notification -> if (!hub.open(notification.key, this)) appList.launch(app) },
            onReply = { notification, text -> hub.reply(notification.key, text) },
            onClear = { notification -> hub.clear(notification.key) },
        )
        val mediaActions = MediaActions(onPrevious = hub::skipPrevious, onPlayPause = hub::playPause, onNext = hub::skipNext)
        val updateFavorites: ((List<String>) -> List<String>) -> Unit = { transform ->
            lifecycleScope.launch { settings.updateFavorites(transform) }
        }
        val appActions = AppActions(
            onLaunch = appList::launch,
            onAppInfo = appList::openAppInfo,
            onAddFavorite = { app -> updateFavorites { Favorites.add(it, app.key) } },
            onRemoveFavorite = { app -> updateFavorites { Favorites.remove(it, app.key) } },
            onMoveFavorite = { from, to -> updateFavorites { Favorites.move(it, from, to) } },
        )
        setContent {
            NavigationBackHandler(
                state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                onBackCompleted = {},
            )

            val accent by settings.accent.collectAsStateWithLifecycle(initialValue = null)
            val wallpaper by settings.wallpaper.collectAsStateWithLifecycle(initialValue = null)
            val apps by appList.apps.collectAsStateWithLifecycle()
            val favoriteKeys by settings.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
            val allApps = apps.orEmpty()
            val favorites = remember(favoriteKeys, allApps) { Favorites.resolve(favoriteKeys, allApps) { it.key } }
            val geometry = rememberCoverGeometry()
            // Collected while the home is visible: listeners off when hidden, a fresh read on return.
            val header by remember { headerSource.header() }.collectAsStateWithLifecycle(initialValue = null)
            val notifications by hub.notifications.collectAsStateWithLifecycle()
            val nowPlaying by hub.nowPlaying.collectAsStateWithLifecycle()
            // Settings can break outside the app (an HyperOS update, for instance): re-check on every return.
            var health by remember { mutableStateOf<HealthReport?>(null) }
            LifecycleResumeEffect(Unit) {
                health = healthChecker.read()
                onPauseOrDispose {}
            }

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
                    apps = allApps,
                    favorites = favorites,
                    favoriteKeys = favoriteKeys,
                    actions = appActions,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    header = header,
                    onOpenEvent = { event ->
                        try {
                            startActivity(openEventIntent(event))
                        } catch (e: ActivityNotFoundException) {
                            // No calendar app to show it: the tap does nothing.
                        }
                    },
                    notifications = notifications,
                    notificationActions = notificationActions,
                    nowPlaying = nowPlaying,
                    mediaActions = mediaActions,
                    healthAlert = health?.firstBroken,
                    onHealthAlertClick = { openHealthFix(it) },
                )
            }
        }
    }

    /** Unfolded, the home has no business on the inner screen: step aside for the inner launcher. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (coverGeometry() == null) moveTaskToBack(true)
    }
}
