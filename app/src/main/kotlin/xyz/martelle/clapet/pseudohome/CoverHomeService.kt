package xyz.martelle.clapet.pseudohome

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import xyz.martelle.clapet.R
import xyz.martelle.clapet.home.HomeActivity
import xyz.martelle.clapet.settings.PseudoHomeSettings
import xyz.martelle.clapet.settingsRepository

private const val FLIP_HOME_PACKAGE = "com.miui.fliphome"
private const val FLIP_HOME_ACTIVITY = "com.miui.fliphome.FlipLauncher"

/** FlipLauncher's "clear all" button: only visible in the real recents. */
private const val RECENTS_CLEAR_ALL_ID = "com.miui.fliphome:id/clearAnimView"

/**
 * Brings Clapet back whenever FlipLauncher (the cover home HyperOS forces) shows its home.
 * FlipLauncher's recents live in the same activity: they are left alone, and their closing
 * (content changes only) is watched to take over again.
 */
class CoverHomeService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val decision = Runnable { decide() }
    private var attempt = 0
    private var lastLaunch = 0L
    private var watchingRecents = false

    /** Null until DataStore answers: the service stays idle rather than guess. */
    private var settings: PseudoHomeSettings? = null
    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        // Lives exactly as long as the connection; cancelled in onDestroy.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            settingsRepository.pseudoHome.onEach { settings = it }.launchIn(scope)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(decision)
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != FLIP_HOME_PACKAGE) return
        when (event.eventType) {
            // FlipLauncher also reports its floating windows (the widget square, a FrameLayout)
            // when another app opens: only its activity means home or recents.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                if (event.className == FLIP_HOME_ACTIVITY) scheduleDecision()
            // Closing the recents (tap outside a card) changes content only.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (watchingRecents) scheduleDecision()
            // The service subscribes to the two types above only (cover_home_service.xml).
            else -> Unit
        }
    }

    /** One pending decision at a time, so a burst of events makes a single one. */
    private fun scheduleDecision() {
        handler.removeCallbacks(decision)
        attempt = 0
        handler.post(decision)
    }

    private fun decide() {
        val active = settings?.isActiveAt(System.currentTimeMillis()) == true
        if (!active || !isCoverScreenOn()) {
            watchingRecents = false
            return
        }
        val now = SystemClock.uptimeMillis()
        when (PseudoHomePolicy.decide(readFlipLauncher(), attempt, now - lastLaunch)) {
            Decision.Retry -> {
                attempt++
                handler.postDelayed(decision, PseudoHomePolicy.RETRY_DELAY_MS)
            }
            Decision.StandDown -> watchingRecents = false
            Decision.WatchRecents -> watchingRecents = true
            Decision.CoolDown -> Unit
            Decision.Relaunch -> {
                watchingRecents = false
                lastLaunch = now
                relaunchHome()
            }
        }
    }

    /**
     * Two system calls only: walking the whole tree node by node took ~40 ms, during which
     * FlipLauncher stayed visible. Targeted lookups take 4 to 15 ms.
     */
    private fun readFlipLauncher(): FlipLauncherState {
        val window = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstOrNull { it.root?.packageName == FLIP_HOME_PACKAGE }
        val root = window?.root ?: return FlipLauncherState(found = false, active = false, recents = false)
        // The recents container keeps fading out for ~500 ms after a home gesture:
        // only the "clear all" button tells real recents apart.
        val recents = root.findAccessibilityNodeInfosByViewId(RECENTS_CLEAR_ALL_ID).any { it.isVisibleToUser }
        return FlipLauncherState(found = true, active = window.isActive, recents = recents)
    }

    /**
     * Short fade over FlipLauncher. Between tasks HyperOS forces its slide, and
     * overrideTaskTransition is refused to third-party apps: the Home window being
     * translucent is what avoids the slide.
     */
    private fun relaunchHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeCustomAnimation(this, R.anim.home_fade_in, R.anim.home_stay).toBundle(),
        )
    }

    /**
     * Phone closed, the default display is the cover screen (1208 × 1392, almost square);
     * open, it is the inner screen (1224 × 2912). Unfolded, the service does nothing.
     */
    private fun isCoverScreenOn(): Boolean {
        val mode = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
        val long = maxOf(mode.physicalWidth, mode.physicalHeight)
        val short = minOf(mode.physicalWidth, mode.physicalHeight)
        return long.toFloat() / short < 1.5f
    }
}
