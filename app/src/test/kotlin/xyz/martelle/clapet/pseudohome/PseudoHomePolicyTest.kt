package xyz.martelle.clapet.pseudohome

import org.junit.Assert.assertEquals
import org.junit.Test

class PseudoHomePolicyTest {

    private val home = FlipLauncherState(found = true, active = true, recents = false)
    private val longAgo = PseudoHomePolicy.RELAUNCH_COOLDOWN_MS + 1

    @Test
    fun `FlipLauncher home brings Clapet back`() {
        assertEquals(Decision.Relaunch, PseudoHomePolicy.decide(home, attempt = 0, sinceLastLaunchMs = longAgo))
    }

    @Test
    fun `recents are left alone and watched`() {
        val recents = home.copy(recents = true)

        assertEquals(Decision.WatchRecents, PseudoHomePolicy.decide(recents, attempt = 0, sinceLastLaunchMs = longAgo))
    }

    @Test
    fun `window not ready yet is read again, then given up`() {
        val notReady = home.copy(active = false)
        val lastAttempt = PseudoHomePolicy.MAX_ATTEMPTS - 1

        assertEquals(Decision.Retry, PseudoHomePolicy.decide(notReady, attempt = 0, sinceLastLaunchMs = longAgo))
        assertEquals(Decision.StandDown, PseudoHomePolicy.decide(notReady, attempt = lastAttempt, sinceLastLaunchMs = longAgo))
    }

    @Test
    fun `missing window means an app replaced FlipLauncher`() {
        val gone = FlipLauncherState(found = false, active = false, recents = false)

        assertEquals(Decision.StandDown, PseudoHomePolicy.decide(gone, attempt = PseudoHomePolicy.MAX_ATTEMPTS, sinceLastLaunchMs = longAgo))
    }

    @Test
    fun `no relaunch right after the previous one`() {
        assertEquals(Decision.CoolDown, PseudoHomePolicy.decide(home, attempt = 0, sinceLastLaunchMs = 100))
    }
}
