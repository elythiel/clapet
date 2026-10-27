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
        val height = 280f // #, 26 letters and the settings gear, 10 px each
        assertEquals(0, AlphabetIndex.letterIndexAt(0f, height)) // #
        assertEquals(13, AlphabetIndex.letterIndexAt(135f, height)) // M
        assertEquals(27, AlphabetIndex.letterIndexAt(400f, height)) // past the end: the gear
        assertEquals(0, AlphabetIndex.letterIndexAt(-20f, height))
    }

    @Test
    fun `a letter without apps jumps to the next section, or the previous at the end`() {
        val available = setOf('C', 'M', 'T')
        assertEquals('M', AlphabetIndex.sectionFor('M', available))
        assertEquals('M', AlphabetIndex.sectionFor('F', available))
        assertEquals('C', AlphabetIndex.sectionFor('#', available))
        assertEquals('T', AlphabetIndex.sectionFor('Z', available))
        assertEquals(null, AlphabetIndex.sectionFor('A', emptySet()))
    }

    @Test
    fun `favorites cap at six and ignore duplicates`() {
        val six = listOf("a", "b", "c", "d", "e", "f")
        assertEquals(six, Favorites.add(six, "g"))
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

    @Test
    fun `pruning drops uninstalled favorites, order kept`() {
        assertEquals(listOf("z", "x"), Favorites.prune(listOf("z", "gone", "x"), setOf("x", "z")))
    }

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `a launch adds one to the score faded by its half-life`() {
        val first = LaunchScores.record(emptyMap(), "a", nowMillis = 0)
        assertEquals(LaunchScore(1.0, 0), first["a"])
        val second = LaunchScores.record(first, "a", nowMillis = LaunchScores.HALF_LIFE_MILLIS)
        assertEquals(LaunchScore(1.5, LaunchScores.HALF_LIFE_MILLIS), second["a"])
    }

    @Test
    fun `most launched first, the rest in the given order`() {
        val now = 1_800_000_000_000L
        var scores = emptyMap<String, LaunchScore>()
        repeat(3) { scores = LaunchScores.record(scores, "c", now) }
        scores = LaunchScores.record(scores, "d", now)
        val sorted = LaunchScores.sort(listOf("a", "b", "c", "d", "e"), scores) { it }
        assertEquals(listOf("c", "d", "a", "b", "e"), sorted)
    }

    @Test
    fun `a few recent launches overtake many old ones`() {
        val now = 1_800_000_000_000L
        var scores = emptyMap<String, LaunchScore>()
        // 20 launches two months ago fade to about 1.0 today; 3 launches this week stay near 3.
        repeat(20) { scores = LaunchScores.record(scores, "old", now - 60 * day) }
        repeat(3) { scores = LaunchScores.record(scores, "new", now - 2 * day) }
        assertEquals(listOf("new", "old"), LaunchScores.sort(listOf("old", "new"), scores) { it })
    }

    @Test
    fun `pruning drops uninstalled apps' scores`() {
        val scores = LaunchScores.record(LaunchScores.record(emptyMap(), "kept", 0), "gone", 0)
        assertEquals(setOf("kept"), LaunchScores.prune(scores, setOf("kept")).keys)
    }
}
