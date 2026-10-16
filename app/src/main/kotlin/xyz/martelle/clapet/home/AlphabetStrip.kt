package xyz.martelle.clapet.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import xyz.martelle.clapet.R
import xyz.martelle.clapet.apps.AlphabetIndex
import xyz.martelle.clapet.theme.LocalClapetColors

/** How far the letters under the finger bulge inwards, at the peak of the wave. */
private val WavePull = 20.dp

/** Which edge the strip hugs; the wave always bulges towards the content. */
enum class StripSide { Start, End }

/** Letters sit in a narrow lane at the start of the strip; the rest of its width is touch area. */
private val LetterLane = 12.dp

/**
 * On the start side, the letters are drawn this far left of the touch area, straddling the edge
 * band HyperOS keeps for its own gestures (touches there never reach Clapet): closer to the screen
 * edge, while their right half and everything right of them stays touchable. The end side, next to
 * the cameras, has no such band: no shift.
 */
private val LetterShift = (-8).dp

/**
 * The side index. Sliding along it bulges the letters around the finger (the "wave") and
 * selects the letter under it; releasing keeps the selection so the app can be tapped.
 *
 * The wave position follows the finger every frame: it is read in the offset lambda
 * (layout phase), so only a change of letter recomposes the strip.
 */
@Composable
fun AlphabetStrip(
    selected: Char?,
    lettersWithApps: Set<Char>,
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
    side: StripSide = StripSide.Start,
    /** Letters are drawn only when true or while the finger slides. */
    revealed: Boolean = true,
    /** When false the strip neither draws nor takes touches, leaving its room to the list below. */
    enabled: Boolean = true,
) {
    val letters = AlphabetIndex.letters
    val center = remember { Animatable(0f) }
    val strength = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    // The letter under the finger while sliding: highlighted, and the reference for "letter changed"
    // (the selection may differ, when a letter without apps sends the list to the next section).
    var dragLetter by remember { mutableStateOf<Char?>(null) }
    val inwards = if (side == StripSide.Start) 1 else -1
    val pullPx = with(LocalDensity.current) { WavePull.toPx() } * inwards
    val shiftPx = if (side == StripSide.Start) with(LocalDensity.current) { LetterShift.roundToPx() } else 0
    val accent = LocalClapetColors.current.accent
    val selectedIndex = (dragLetter ?: selected)?.let { letters.indexOf(it) } ?: -1
    val alpha by animateFloatAsState(if (revealed || dragLetter != null) 1f else 0f, label = "lettersAlpha")
    val indexLabel = stringResource(R.string.alphabet_index)

    fun follow(y: Float) {
        val index = AlphabetIndex.letterIndexAt(y, height.toFloat())
        scope.launch { center.snapTo(y / height * letters.size - 0.5f) }
        val letter = letters[index]
        if (letter != dragLetter) {
            dragLetter = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnSelect(letter)
        }
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (side == StripSide.Start) Alignment.Start else Alignment.End,
        modifier = modifier
            .onSizeChanged { height = max(it.height, 1) }
            .graphicsLayer { this.alpha = alpha }
            .then(if (enabled) Modifier.semantics { contentDescription = indexLabel } else Modifier)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    scope.launch { strength.animateTo(1f, tween(120)) }
                    follow(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        follow(change.position.y)
                    }
                    scope.launch { strength.animateTo(0f, tween(220)) }
                    dragLetter = null
                }
            },
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = if (selectedIndex < 0) Int.MAX_VALUE else abs(index - selectedIndex)
            val color = when {
                distance == 0 -> accent
                letter !in lettersWithApps -> Color(0xFF5A5752)
                distance <= 3 -> Color(0xFFD9D5CE)
                else -> Color(0xFF8F8B85)
            }
            Text(
                text = letter.toString(),
                color = color,
                fontSize = if (distance == 0) 13.sp else (9.5f + max(0, 3 - distance)).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 11.sp,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(LetterLane)
                    .offset {
                        val d = index - center.value
                        IntOffset(shiftPx + (pullPx * strength.value * exp(-(d * d) / 6f)).roundToInt(), 0)
                    }
                    // TalkBack cannot slide: each letter is its own button there.
                    .then(
                        if (enabled) {
                            Modifier.semantics {
                                onClick {
                                    onSelect(letter)
                                    true
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
