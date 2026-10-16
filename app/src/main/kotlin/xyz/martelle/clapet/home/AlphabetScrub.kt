package xyz.martelle.clapet.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import xyz.martelle.clapet.apps.AlphabetIndex

/**
 * The scrub on the side index: which side the thumb is on, the letter under it, and the wave
 * (center in letter units, strength 0 to 1). Shared by both strips, which only draw it.
 */
@Stable
class AlphabetScrubState(private val scope: CoroutineScope) {
    val center = Animatable(0f)
    val strength = Animatable(0f)

    /** The side being scrubbed, while the thumb is down; null otherwise. */
    var side by mutableStateOf<StripSide?>(null)
        private set

    /** The letter under the thumb while scrubbing. */
    var letter by mutableStateOf<Char?>(null)
        private set

    internal fun start(side: StripSide) {
        this.side = side
        scope.launch { strength.animateTo(1f, tween(120)) }
    }

    /** Returns the letter under [fraction] (0 = top of the strip, 1 = bottom) when it changed. */
    internal fun follow(fraction: Float): Char? {
        val letters = AlphabetIndex.letters
        scope.launch { center.snapTo(fraction * letters.size - 0.5f) }
        val letter = letters[AlphabetIndex.letterIndexAt(fraction, 1f)]
        if (letter == this.letter) return null
        this.letter = letter
        return letter
    }

    internal fun end() {
        side = null
        letter = null
        scope.launch { strength.animateTo(0f, tween(220)) }
    }
}

/**
 * Watches every touch of the screen without taking any, until one becomes a scrub: a vertical
 * drag past [threshold], started near an edge (within [zoneWidth] of [left] or [right], read at each
 * touch since it depends on the screen state) and before the long-press timeout. From then on the scrub keeps the touches, so the content under the wide
 * zones still gets its taps, long presses and favorite drags.
 *
 * Coordinates are the modifier's own (the full screen). [stripTop]/[stripBottom] bound the letters.
 */
fun Modifier.alphabetScrub(
    state: AlphabetScrubState,
    left: Float,
    right: Float,
    zoneWidth: () -> Float,
    threshold: Float,
    stripTop: Float,
    stripBottom: Float,
    isEnabled: (StripSide) -> Boolean,
    onLetter: (StripSide, Char) -> Unit,
    /** The thumb lifted, after at least one [onLetter]. */
    onEnd: () -> Unit,
): Modifier = pointerInput(left, right, stripTop, stripBottom) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val zone = zoneWidth()
        val side = when (down.position.x) {
            in left..(left + zone) -> StripSide.Start
            in (right - zone)..right -> StripSide.End
            else -> null
        }
        if (side == null || !isEnabled(side)) return@awaitEachGesture

        // Phase 1: a quick vertical drag, or nothing. Taps, long presses and sideways moves stay
        // with the content, untouched.
        var trigger: PointerInputChange? = null
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (trigger == null) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed) return@withTimeoutOrNull
                val delta = change.position - down.position
                if (abs(delta.y) > threshold && abs(delta.y) > abs(delta.x)) trigger = change
            }
        }
        val first = trigger ?: return@awaitEachGesture

        // Phase 2: the scrub owns the touches until the thumb lifts, starting with the one that
        // triggered it, so the content below never acts on any of them.
        fun fractionOf(y: Float) = ((y - stripTop) / (stripBottom - stripTop)).coerceIn(0f, 0.9999f)
        state.start(side)
        var change: PointerInputChange = first
        while (true) {
            change.consume()
            state.follow(fractionOf(change.position.y))?.let { onLetter(side, it) }
            change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
        }
        state.end()
        onEnd()
    }
}
