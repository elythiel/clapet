package xyz.martelle.clapet.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

private enum class PressOutcome { Tap, Moved }

/**
 * Tap and long press that both require a still finger. Unlike detectTapGestures, whose long
 * press survives any movement inside the bounds, a finger that travels past the touch slop is a
 * swipe: neither callback fires. That matters on a full-screen background, where a long swipe
 * never leaves the bounds.
 */
fun Modifier.stillPressGestures(
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = viewConfiguration.touchSlop
        var outcome: PressOutcome? = null
        // Null when the timeout elapses with the finger still down and still: a long press.
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (outcome == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                outcome = when {
                    change == null || change.isConsumed -> PressOutcome.Moved
                    (change.position - down.position).getDistance() > slop -> PressOutcome.Moved
                    !change.pressed -> PressOutcome.Tap
                    else -> null
                }
            }
        }
        when {
            ended == null -> onLongPress(down.position)
            outcome == PressOutcome.Tap -> onTap()
        }
    }
}
