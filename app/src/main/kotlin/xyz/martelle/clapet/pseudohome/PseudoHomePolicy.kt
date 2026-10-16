package xyz.martelle.clapet.pseudohome

/** What the service reads from FlipLauncher's window, in two cheap system calls. */
data class FlipLauncherState(
    /** FlipLauncher's application window exists. */
    val found: Boolean,
    /** That window has focus, so no app has replaced it. */
    val active: Boolean,
    /** The real recents are open (their "clear all" button is visible). */
    val recents: Boolean,
)

enum class Decision {
    /** The window is not ready right after the event: read it again shortly. */
    Retry,

    /** An app took FlipLauncher's place (opened from recents): stand down. */
    StandDown,

    /** Recents are open: leave them alone, but watch for them closing. */
    WatchRecents,

    /** Clapet came back moments ago: do nothing, keep the current watch. */
    CoolDown,

    /** FlipLauncher shows its home: bring Clapet back. */
    Relaunch,
}

/**
 * The pseudo-home rules validated on the prototype. HyperOS hard-wires FlipLauncher as
 * the cover home, so Clapet steps in whenever FlipLauncher's home becomes visible.
 */
object PseudoHomePolicy {
    /** Minimal time between two relaunches, a guard against any loop. */
    const val RELAUNCH_COOLDOWN_MS = 800L
    const val RETRY_DELAY_MS = 40L

    /**
     * About 600 ms of retries: after a home swipe, FlipLauncher's window only gets focus at the
     * end of HyperOS's animation, ~460 ms after the finger lifts.
     */
    const val MAX_ATTEMPTS = 15

    fun decide(state: FlipLauncherState, attempt: Int, sinceLastLaunchMs: Long): Decision {
        val visible = state.found && state.active
        return when {
            !visible && attempt < MAX_ATTEMPTS - 1 -> Decision.Retry
            !visible -> Decision.StandDown
            state.recents -> Decision.WatchRecents
            sinceLastLaunchMs < RELAUNCH_COOLDOWN_MS -> Decision.CoolDown
            else -> Decision.Relaunch
        }
    }
}
