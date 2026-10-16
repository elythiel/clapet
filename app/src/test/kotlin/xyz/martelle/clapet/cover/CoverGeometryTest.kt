package xyz.martelle.clapet.cover

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoverGeometryTest {

    // Values measured on the Mix Flip (ruyi_eea, HyperOS OS3.0.303), phone closed.
    private val width = 1208
    private val height = 1392
    private val cameras = IntRect(810, 664, 1208, 1392)

    @Test
    fun `upright, content is left of the cameras and the player above them`() {
        val geometry = assertNotNull(CoverGeometry.from(width, height, listOf(cameras)))

        assertEquals(false, geometry.upsideDown)
        assertEquals(IntRect(0, 0, 810, 1392), geometry.content)
        assertEquals(IntRect(810, 0, 1208, 664), geometry.square)
    }

    @Test
    fun `upside down, the cameras move top left and everything mirrors`() {
        // The same lenses once the display turns 180°.
        val turned = IntRect(0, 0, 398, 728)
        val geometry = assertNotNull(CoverGeometry.from(width, height, listOf(turned)))

        assertEquals(true, geometry.upsideDown)
        assertEquals(IntRect(398, 0, 1208, 1392), geometry.content)
        assertEquals(IntRect(0, 728, 398, 1392), geometry.square)
    }

    @Test
    fun `no zone overlaps the cameras, either way up`() {
        listOf(cameras, IntRect(0, 0, 398, 728)).forEach { lenses ->
            val geometry = assertNotNull(CoverGeometry.from(width, height, listOf(lenses)))
            listOf(geometry.content, geometry.square).forEach { zone ->
                assertEquals(false, zone.overlaps(geometry.cameras))
            }
        }
    }

    @Test
    fun `inner screen punch hole is not the cover cameras`() {
        val punchHole = IntRect(560, 0, 664, 110)

        assertNull(CoverGeometry.from(1224, 2912, listOf(punchHole)))
    }

    @Test
    fun `no cutout means not on the cover screen`() {
        assertNull(CoverGeometry.from(width, height, emptyList()))
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        assertNotNull(null, value)
        return value!!
    }
}
