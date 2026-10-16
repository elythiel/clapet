package xyz.martelle.clapet.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Letters sit in a narrow lane against their edge. */
private val LetterLane = 12.dp

/**
 * Against the screen's edge, the letters are drawn this far outwards, straddling the band HyperOS
 * keeps there for its own gestures (touches in it never reach Clapet). The side next to the
 * cameras has no such band: no shift.
 */
private val LetterShift = (-8).dp

/**
 * One side index, drawing only: touches are handled by [alphabetScrub] on the whole screen, so the
 * wide scrub zones never block the content under them. The wave follows the thumb every frame:
 * it is read in the offset lambda (layout phase), so only a change of letter recomposes the strip.
 */
@Composable
fun AlphabetStrip(
    side: StripSide,
    scrub: AlphabetScrubState,
    selected: Char?,
    lettersWithApps: Set<Char>,
    /** For TalkBack, which cannot slide: each letter is its own button. */
    onSelect: (Char) -> Unit,
    modifier: Modifier = Modifier,
    /** Letters are drawn only when true or while this side is being scrubbed. */
    revealed: Boolean = true,
    /** When false the strip is hidden and silent, leaving its room to the list. */
    enabled: Boolean = true,
    /** Against the screen's edge (not the cameras): the letters straddle HyperOS's gesture band. */
    atScreenEdge: Boolean = side == StripSide.Start,
) {
    val letters = AlphabetIndex.letters
    val scrubbing = scrub.side == side
    val inwards = if (side == StripSide.Start) 1 else -1
    val density = LocalDensity.current
    val pullPx = with(density) { WavePull.toPx() } * inwards
    // Towards the edge: left on the start side, right on the end side.
    val shiftPx = if (atScreenEdge) with(density) { LetterShift.roundToPx() } * inwards else 0
    val accent = LocalClapetColors.current.accent
    val selectedIndex = ((if (scrubbing) scrub.letter else null) ?: selected)?.let { letters.indexOf(it) } ?: -1
    val alpha by animateFloatAsState(if (enabled && (revealed || scrubbing)) 1f else 0f, label = "lettersAlpha")
    val indexLabel = stringResource(R.string.alphabet_index)
    val settingsLabel = stringResource(R.string.list_settings)

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (side == StripSide.Start) Alignment.Start else Alignment.End,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .then(if (enabled) Modifier.semantics { contentDescription = indexLabel } else Modifier),
    ) {
        letters.forEachIndexed { index, letter ->
            val distance = if (selectedIndex < 0) Int.MAX_VALUE else abs(index - selectedIndex)
            val color = when {
                distance == 0 -> accent
                letter !in lettersWithApps -> Color(0xFF5A5752)
                distance <= 3 -> Color(0xFFD9D5CE)
                else -> Color(0xFF8F8B85)
            }
            val size = if (distance == 0) 13f else 9.5f + max(0, 3 - distance)
            val itemModifier = Modifier
                .width(LetterLane)
                .offset {
                    // The wave bulges on the scrubbed side, then fades out where it was.
                    val wave = if (scrubbing || scrub.side == null) scrub.strength.value else 0f
                    val d = index - scrub.center.value
                    IntOffset(shiftPx + (pullPx * wave * exp(-(d * d) / 6f)).roundToInt(), 0)
                }
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
                )
            if (letter == AlphabetIndex.SETTINGS) {
                // The settings close the index: a gear the size of a letter, in the letters' color.
                Box(itemModifier.height(11.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_settings),
                        contentDescription = settingsLabel,
                        tint = color,
                        modifier = Modifier.size(size.dp),
                    )
                }
            } else {
                Text(
                    text = letter.toString(),
                    color = color,
                    fontSize = size.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = itemModifier,
                )
            }
        }
    }
}
