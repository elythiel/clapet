package xyz.martelle.clapet.apps

import kotlin.math.log2
import kotlin.math.pow

/** How much an app is launched from Clapet: [score] as of its last launch, at [lastMillis]. */
data class LaunchScore(val score: Double, val lastMillis: Long)

/**
 * Launch counts that fade, by app key: each launch is worth 1, halved every [HALF_LIFE_MILLIS],
 * so a new habit soon overtakes an old one. Pure operations; persistence is SettingsRepository's.
 */
object LaunchScores {

    const val HALF_LIFE_MILLIS = 14L * 24 * 60 * 60 * 1000

    fun record(scores: Map<String, LaunchScore>, key: String, nowMillis: Long): Map<String, LaunchScore> {
        val faded = scores[key]?.let { previous ->
            // A clock set back never makes an old launch weigh more.
            val elapsed = (nowMillis - previous.lastMillis).coerceAtLeast(0)
            previous.score * 0.5.pow(elapsed.toDouble() / HALF_LIFE_MILLIS)
        } ?: 0.0
        return scores + (key to LaunchScore(faded + 1, nowMillis))
    }

    /** Drops uninstalled apps' scores, so the stored list does not grow forever. */
    fun prune(scores: Map<String, LaunchScore>, installed: Set<String>): Map<String, LaunchScore> =
        scores.filterKeys { it in installed }

    /**
     * Orders scores as they stand at any moment: all of them fade by the same factor, so the order
     * never changes with time alone. In log form, as 2^(lastMillis / half-life) overflows a Double.
     */
    fun rank(score: LaunchScore): Double = log2(score.score) + score.lastMillis.toDouble() / HALF_LIFE_MILLIS

    /** Most launched first; ties, apps never launched included, keep the order of [apps]. */
    fun <T> sort(apps: List<T>, scores: Map<String, LaunchScore>, keyOf: (T) -> String): List<T> {
        if (scores.isEmpty()) return apps
        // sortedByDescending is stable: the alphabetical order stays among equals.
        return apps.sortedByDescending { app -> scores[keyOf(app)]?.let(::rank) ?: Double.NEGATIVE_INFINITY }
    }
}
