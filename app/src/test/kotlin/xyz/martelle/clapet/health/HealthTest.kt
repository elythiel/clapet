package xyz.martelle.clapet.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {

    // Value read on the Mix Flip (HyperOS OS3.0.303).
    private val lockedApps =
        """[{"u":0,"pkgs":["fr.martelle.flipspike","com.movix.app"]},{"u":-100,"pkgs":["com.xiaomi.bsgamecenter"]}]"""

    @Test
    fun `locked apps are read for the current user only`() {
        assertTrue(XiaomiSettings.lockedAppsContain(lockedApps, userId = 0, packageName = "com.movix.app"))
        assertFalse(XiaomiSettings.lockedAppsContain(lockedApps, userId = 0, packageName = "com.xiaomi.bsgamecenter"))
        assertFalse(XiaomiSettings.lockedAppsContain(lockedApps, userId = 0, packageName = "xyz.martelle.clapet"))
    }

    @Test
    fun `missing or broken locked apps mean not locked`() {
        assertFalse(XiaomiSettings.lockedAppsContain(null, userId = 0, packageName = "a"))
        assertFalse(XiaomiSettings.lockedAppsContain("not json", userId = 0, packageName = "a"))
    }

    @Test
    fun `comma list matches whole package names`() {
        assertTrue(XiaomiSettings.commaListContains("com.a, xyz.martelle.clapet,com.b", "xyz.martelle.clapet"))
        assertFalse(XiaomiSettings.commaListContains("xyz.martelle.clapet.extra", "xyz.martelle.clapet"))
    }

    @Test
    fun `the home dot points to the first required setting that is off`() {
        val report = HealthReport(
            HealthItem.entries.associateWith { it != HealthItem.Battery && it != HealthItem.RecentsLock },
        )

        assertEquals(HealthItem.Battery, report.firstBroken)
        assertEquals(4, report.okCount)
        assertEquals(6, report.total)
    }

    @Test
    fun `an optional setting off raises no alert`() {
        val report = HealthReport(
            HealthItem.entries.associateWith { it != HealthItem.RecentsLock && it != HealthItem.Calendar },
        )

        assertNull(report.firstBroken)
    }
}
