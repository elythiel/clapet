package xyz.martelle.clapet.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppsTest {

    @Test
    fun `labels file under their first letter, accents folded`() {
        assertEquals('E', AlphabetIndex.letterOf("Écran"))
        assertEquals('O', AlphabetIndex.letterOf("ölfarbe"))
        assertEquals('M', AlphabetIndex.letterOf("  Messages"))
    }

    @Test
    fun `digits, symbols and other scripts go under the hash`() {
        assertEquals('#', AlphabetIndex.letterOf("7-Zip"))
        assertEquals('#', AlphabetIndex.letterOf("微信"))
        assertEquals('#', AlphabetIndex.letterOf(""))
    }

    @Test
    fun `a touch maps to the letter under it, clamped to the strip`() {
        val height = 270f // 27 letters, 10 px each
        assertEquals(0, AlphabetIndex.letterIndexAt(0f, height))
        assertEquals(12, AlphabetIndex.letterIndexAt(125f, height)) // M
        assertEquals(26, AlphabetIndex.letterIndexAt(400f, height)) // past the end: #
        assertEquals(0, AlphabetIndex.letterIndexAt(-20f, height))
    }

    @Test
    fun `a letter without apps jumps to the next section, or the previous at the end`() {
        val available = setOf('C', 'M', 'T')
        assertEquals('M', AlphabetIndex.sectionFor('M', available))
        assertEquals('M', AlphabetIndex.sectionFor('F', available))
        assertEquals('T', AlphabetIndex.sectionFor('#', available))
        assertEquals(null, AlphabetIndex.sectionFor('A', emptySet()))
    }

    @Test
    fun `favorites cap at five and ignore duplicates`() {
        val five = listOf("a", "b", "c", "d", "e")
        assertEquals(five, Favorites.add(five, "f"))
        assertEquals(listOf("a", "b"), Favorites.add(listOf("a", "b"), "a"))
        assertEquals(listOf("a", "b", "c"), Favorites.add(listOf("a", "b"), "c"))
    }

    @Test
    fun `moving a favorite shifts the others`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), Favorites.move(list, from = 0, to = 2))
        assertEquals(listOf("d", "a", "b", "c"), Favorites.move(list, from = 3, to = 0))
        assertEquals(list, Favorites.move(list, from = 1, to = 9))
    }

    @Test
    fun `uninstalled favorites are skipped, order kept`() {
        val installed = listOf("x", "z")
        assertEquals(listOf("z", "x"), Favorites.resolve(listOf("z", "gone", "x"), installed) { it })
    }
}
