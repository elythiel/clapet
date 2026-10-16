package xyz.martelle.clapet.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import xyz.martelle.clapet.R
import xyz.martelle.clapet.apps.AlphabetIndex
import xyz.martelle.clapet.apps.AppEntry
import xyz.martelle.clapet.apps.Favorites
import xyz.martelle.clapet.theme.LocalClapetColors
import xyz.martelle.clapet.theme.TextPrimary

private val FavoriteRowHeight = 44.dp

/** What a long press on an app can do. */
@Immutable
class AppActions(
    val onLaunch: (AppEntry) -> Unit,
    val onAppInfo: (AppEntry) -> Unit,
    val onAddFavorite: (AppEntry) -> Unit,
    val onRemoveFavorite: (AppEntry) -> Unit,
    val onMoveFavorite: (from: Int, to: Int) -> Unit,
)

/**
 * The favorites, top to bottom. A long press then a drag moves a favorite (the others make
 * room as it passes); a long press released in place opens its menu instead.
 */
@Composable
fun FavoritesList(
    favorites: List<AppEntry>,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        Text(
            stringResource(R.string.favorites_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
        return
    }
    val rowHeightPx = with(LocalDensity.current) { FavoriteRowHeight.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Where the finger went down, in the dragged row's unshifted coordinates.
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val currentFavorites by rememberUpdatedState(favorites)
    val moveUp = stringResource(R.string.app_move_up)
    val moveDown = stringResource(R.string.app_move_down)

    fun targetOf(from: Int): Int =
        (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, currentFavorites.lastIndex)

    Column(modifier) {
        favorites.forEachIndexed { index, app ->
            Box(
                Modifier
                    .zIndex(if (dragging == index) 1f else 0f)
                    // Read at layout time: the drag moves rows without recomposing them.
                    .offset {
                        val from = dragging ?: return@offset IntOffset.Zero
                        val target = targetOf(from)
                        val y = when {
                            index == from -> dragOffset
                            index in (from + 1)..target -> -rowHeightPx
                            index in target until from -> rowHeightPx
                            else -> 0f
                        }
                        IntOffset(0, y.roundToInt())
                    }
                    .semantics {
                        onLongClick {
                            menuFor = app.key
                            true
                        }
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(moveUp) {
                                actions.onMoveFavorite(index, index - 1)
                                true
                            }.takeIf { index > 0 },
                            CustomAccessibilityAction(moveDown) {
                                actions.onMoveFavorite(index, index + 1)
                                true
                            }.takeIf { index < favorites.lastIndex },
                        )
                    },
            ) {
                AppRow(
                    app = app,
                    iconSize = 26.dp,
                    labelSize = 19.sp,
                    onClick = { actions.onLaunch(app) },
                    onLongClick = null,
                    // Inside the click handler: it sees, and consumes, the release of a drag first.
                    gestures = Modifier.pointerInput(app.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = currentFavorites.indexOf(app)
                                dragOffset = 0f
                                dragStartY = start.y
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                // The row follows the finger, so positions are relative to a moving
                                // node: add back its current shift to get the finger's real travel.
                                dragOffset = change.position.y + dragOffset - dragStartY
                            },
                            onDragEnd = {
                                val from = dragging
                                if (from != null) {
                                    if (abs(dragOffset) < touchSlop) menuFor = app.key else actions.onMoveFavorite(from, targetOf(from))
                                }
                                dragging = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragging = null
                                dragOffset = 0f
                            },
                        )
                    },
                    modifier = Modifier.heightIn(min = FavoriteRowHeight),
                )
                AppMenu(
                    expanded = menuFor == app.key,
                    isFavorite = true,
                    favoritesFull = true,
                    onDismiss = { menuFor = null },
                    onFavoriteToggle = { actions.onRemoveFavorite(app) },
                    onAppInfo = { actions.onAppInfo(app) },
                )
            }
        }
    }
}

/** All apps, one section per letter of the index, in the index order (# last). */
@Immutable
data class AppSection(val letter: Char, val apps: List<AppEntry>)

fun sectionsOf(apps: List<AppEntry>): List<AppSection> {
    val byLetter = apps.groupBy { it.letter }
    return AlphabetIndex.letters.mapNotNull { letter -> byLetter[letter]?.let { AppSection(letter, it) } }
}

/** Position of each section header in [AllApps]' list: one header item, then one item per app. */
fun headerIndexes(sections: List<AppSection>): Map<Char, Int> {
    var index = 0
    return sections.associate { section ->
        (section.letter to index).also { index += 1 + section.apps.size }
    }
}

/**
 * The full list, shown while and after sliding on the index: the index jumps to a section,
 * and from there the list scrolls freely to fix a near miss. While the thumb slides, only the
 * section under it is drawn, right where the list holds it, so the others can fade in around it.
 */
@Composable
fun AllApps(
    sections: List<AppSection>,
    listState: LazyListState,
    favoriteKeys: List<String>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** The section that stays fully drawn while the others take [othersAlpha]; null for none. */
    focusedSection: () -> Char? = { null },
    othersAlpha: () -> Float = { 1f },
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    LazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier) {
        sections.forEach { section ->
            // Read at draw time: switching sections or fading the others never recomposes the rows.
            val sectionAlpha = Modifier.graphicsLayer {
                alpha = if (focusedSection() == section.letter) 1f else othersAlpha()
            }
            item(key = "header:${section.letter}") { SectionHeader(section, sectionAlpha) }
            items(section.apps, key = { it.key }) { app ->
                Box(sectionAlpha) {
                    ListAppRow(app, onClick = { actions.onLaunch(app) }, onLongClick = { menuFor = app.key })
                    val isFavorite = app.key in favoriteKeys
                    AppMenu(
                        expanded = menuFor == app.key,
                        isFavorite = isFavorite,
                        favoritesFull = favoriteKeys.size >= Favorites.MAX,
                        onDismiss = { menuFor = null },
                        onFavoriteToggle = { if (isFavorite) actions.onRemoveFavorite(app) else actions.onAddFavorite(app) },
                        onAppInfo = { actions.onAppInfo(app) },
                    )
                }
            }
        }
    }
}

/** Space above each letter of the list, separating the sections. */
val SectionGap = 24.dp

@Composable
private fun SectionHeader(section: AppSection, modifier: Modifier = Modifier) {
    Text(
        // The letter alone, as in Niagara.
        section.letter.toString(),
        color = LocalClapetColors.current.accent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        // A clear gap above each letter separates the sections.
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = SectionGap, bottom = 6.dp),
    )
}

@Composable
private fun ListAppRow(app: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    AppRow(
        app = app,
        iconSize = 24.dp,
        labelSize = 17.sp,
        onClick = onClick,
        onLongClick = onLongClick,
        // Tighter than the favorites, to show more of the list; still a comfortable target.
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    iconSize: Dp,
    labelSize: TextUnit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    gestures: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(gestures)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(iconSize))
        Text(
            text = app.label,
            color = TextPrimary,
            fontSize = labelSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Decorative: the item's text already says what it does. */
@Composable
private fun MenuIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
}

@Composable
private fun AppMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    favoritesFull: Boolean,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAppInfo: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when {
            isFavorite -> DropdownMenuItem(
                text = { Text(stringResource(R.string.app_menu_remove_favorite)) },
                leadingIcon = { MenuIcon(R.drawable.ic_star_off) },
                onClick = {
                    onDismiss()
                    onFavoriteToggle()
                },
            )
            favoritesFull -> DropdownMenuItem(
                text = { Text(stringResource(R.string.favorites_full)) },
                leadingIcon = { MenuIcon(R.drawable.ic_star) },
                onClick = {},
                enabled = false,
            )
            else -> DropdownMenuItem(
                text = { Text(stringResource(R.string.app_menu_add_favorite)) },
                leadingIcon = { MenuIcon(R.drawable.ic_star) },
                onClick = {
                    onDismiss()
                    onFavoriteToggle()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.app_menu_info)) },
            leadingIcon = { MenuIcon(R.drawable.ic_info) },
            onClick = {
                onDismiss()
                onAppInfo()
            },
        )
    }
}
