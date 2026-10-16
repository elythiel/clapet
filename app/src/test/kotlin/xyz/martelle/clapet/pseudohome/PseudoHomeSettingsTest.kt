package xyz.martelle.clapet.pseudohome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.martelle.clapet.settings.PseudoHomeSettings

class PseudoHomeSettingsTest {

    @Test
    fun `enabled and not paused is active`() {
        assertTrue(PseudoHomeSettings.Default.isActiveAt(nowMillis = 1_000))
    }

    @Test
    fun `a pause holds until its end, then lifts by itself`() {
        val paused = PseudoHomeSettings(enabled = true, pausedUntilMillis = 5_000)

        assertFalse(paused.isActiveAt(nowMillis = 4_999))
        assertTrue(paused.isActiveAt(nowMillis = 5_000))
    }

    @Test
    fun `disabled is never active`() {
        assertFalse(PseudoHomeSettings(enabled = false, pausedUntilMillis = 0).isActiveAt(nowMillis = 1_000))
    }
}
