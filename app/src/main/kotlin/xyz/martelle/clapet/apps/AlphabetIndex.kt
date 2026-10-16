package xyz.martelle.clapet.apps

import java.text.Normalizer

/**
 * The entries of the side index, in order: # for everything that does not start with a letter
 * (digits, symbols, other scripts), A to Z, then Clapet's settings, which close the list.
 */
object AlphabetIndex {

    const val OTHER: Char = '#'

    /** Not a letter: drawn as a gear in the index, it leads to the settings row at the list's end. */
    const val SETTINGS: Char = '\u2699'

    val letters: List<Char> = listOf(OTHER) + ('A'..'Z') + SETTINGS

    /** "Écran" files under E, "Ölfarbe" under O, "7-Zip" or "微信" under #. */
    fun letterOf(label: String): Char {
        val first = label.trimStart().firstOrNull() ?: return OTHER
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
        return if (base in 'A'..'Z') base else OTHER
    }

    /**
     * The section to jump to for [selected]: itself when it has apps, else the next letter that has
     * some, else the last one before it. Null when no letter has apps.
     */
    fun sectionFor(selected: Char, available: Set<Char>): Char? {
        val from = letters.indexOf(selected).coerceAtLeast(0)
        return letters.drop(from).firstOrNull { it in available }
            ?: letters.take(from).lastOrNull { it in available }
    }

    /** Index of the letter under a touch at [y], for a strip of [height] split evenly between letters. */
    fun letterIndexAt(y: Float, height: Float): Int =
        (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
}
