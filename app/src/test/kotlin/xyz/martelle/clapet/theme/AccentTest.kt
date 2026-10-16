package xyz.martelle.clapet.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {

    @Test
    fun `contrast ratio spans 1 to 21`() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.Black, Color.Black), 0.01f)
    }

    @Test
    fun `default pale pink takes black text`() {
        assertEquals(Color.Black, contentColorOn(Color(AccentPalette.first().argb)))
    }

    @Test
    fun `dark custom accent takes white text`() {
        assertEquals(Color.White, contentColorOn(Color(0xFF1A237E)))
    }

    @Test
    fun `text on any accent reaches 4,5 to 1`() {
        // Sweep the grey axis, where neither black nor white text is a clear winner.
        (0..255).forEach { level ->
            val accent = Color(level, level, level)
            assertTrue("level $level", contrastRatio(accent, contentColorOn(accent)) >= 4.5f)
        }
    }

    @Test
    fun `every palette color is readable on black`() {
        AccentPalette.forEach { swatch ->
            assertTrue(swatch.toString(), contrastRatio(Color(swatch.argb), Color.Black) >= 4.5f)
        }
    }
}
