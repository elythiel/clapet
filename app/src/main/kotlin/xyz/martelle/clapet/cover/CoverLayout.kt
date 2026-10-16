package xyz.martelle.clapet.cover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

/**
 * Places each slot in its zone of the cover screen. Zones come from [CoverGeometry],
 * never from hard-coded sizes, so a different cutout simply moves them.
 */
@Composable
fun CoverLayout(
    geometry: CoverGeometry,
    modifier: Modifier = Modifier,
    topBand: @Composable BoxScope.() -> Unit = {},
    square: @Composable BoxScope.() -> Unit = {},
    column: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier) {
        Box(Modifier.zone(geometry.topBand), content = topBand)
        Box(Modifier.zone(geometry.square), content = square)
        Box(Modifier.zone(geometry.column), content = column)
    }
}

/** Sizes and positions a box on a window-pixel rectangle. */
@Composable
fun Modifier.zone(rect: IntRect): Modifier {
    val density = LocalDensity.current
    return with(density) {
        this@zone
            .offset { IntOffset(rect.left, rect.top) }
            .size(rect.width.toDp(), rect.height.toDp())
    }
}
