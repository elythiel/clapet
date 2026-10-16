package xyz.martelle.clapet.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {

    @Test
    fun `comma list matches whole package names`() {
        assertTrue(XiaomiSettings.commaListContains("com.a, xyz.martelle.clapet,com.b", "xyz.martelle.clapet"))
        assertFalse(XiaomiSettings.commaListContains("xyz.martelle.clapet.extra", "xyz.martelle.clapet"))
    }

    @Test
    fun `the home dot points to the first required setting that is off`() {
        val report = HealthReport(
            HealthItem.entries.associateWith { it != HealthItem.Battery && it != HealthItem.Calendar },
        )

        assertEquals(HealthItem.Battery, report.firstBroken)
        assertEquals(4, report.okCount)
        assertEquals(6, report.total)
    }

    @Test
    fun `an optional setting off raises no alert`() {
        val report = HealthReport(
            HealthItem.entries.associateWith { it.required },
        )

        assertNull(report.firstBroken)
    }
}
