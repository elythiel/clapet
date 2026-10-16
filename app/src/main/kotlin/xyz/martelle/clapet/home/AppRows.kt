package xyz.martelle.clapet.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import xyz.martelle.clapet.R
import xyz.martelle.clapet.apps.AlphabetIndex
import xyz.martelle.clapet.apps.AppEntry
import xyz.martelle.clapet.notifications.AppNotification
import xyz.martelle.clapet.notifications.AppNotifications
import xyz.martelle.clapet.notifications.NotificationRules
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
    /** Opens the app's panel: its actions and notifications (long press, or swipe right when it has some). */
    val onOpenPanel: (AppEntry) -> Unit = {},
)

/**
 * The favorites, top to bottom. A long press then a drag moves a favorite (the others make
 * room as it passes); a long press released in place opens its panel instead.
 */
@Composable
fun FavoritesList(
    favorites: List<AppEntry>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    /** By package name; empty without notification access. */
    notifications: Map<String, AppNotifications> = emptyMap(),
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
                            actions.onOpenPanel(app)
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
                val appNotifications = notifications[app.component.packageName]
                AppRow(
                    app = app,
                    iconSize = 26.dp,
                    labelSize = 19.sp,
                    onClick = { actions.onLaunch(app) },
                    onLongClick = null,
                    notifications = appNotifications,
                    onSwipe = { actions.onOpenPanel(app) },
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
                                    if (abs(dragOffset) < touchSlop) actions.onOpenPanel(app) else actions.onMoveFavorite(from, targetOf(from))
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

/**
 * Position of each section header in [AllApps]' list: one header item, then one item per app;
 * the index's gear ([AlphabetIndex.SETTINGS]) points at the settings item that closes the list.
 */
fun headerIndexes(sections: List<AppSection>): Map<Char, Int> {
    var index = 0
    val headers = sections.associate { section ->
        (section.letter to index).also { index += 1 + section.apps.size }
    }
    return headers + (AlphabetIndex.SETTINGS to index)
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
    actions: AppActions,
    /** The last row of the list, after « # »: Clapet's own settings. */
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** The section that stays fully drawn while the others take [othersAlpha]; null for none. */
    focusedSection: () -> Char? = { null },
    othersAlpha: () -> Float = { 1f },
    notifications: Map<String, AppNotifications> = emptyMap(),
) {
    LazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier) {
        sections.forEach { section ->
            // Read at draw time: switching sections or fading the others never recomposes the rows.
            val sectionAlpha = Modifier.graphicsLayer {
                alpha = if (focusedSection() == section.letter) 1f else othersAlpha()
            }
            item(key = "header:${section.letter}") { SectionHeader(section, sectionAlpha) }
            items(section.apps, key = { it.key }) { app ->
                AppRow(
                    app = app,
                    iconSize = 24.dp,
                    labelSize = 17.sp,
                    onClick = { actions.onLaunch(app) },
                    onLongClick = { actions.onOpenPanel(app) },
                    notifications = notifications[app.component.packageName],
                    onSwipe = { actions.onOpenPanel(app) },
                    // Tighter than the favorites, to show more of the list; still a comfortable target.
                    modifier = sectionAlpha.heightIn(min = 40.dp),
                )
            }
        }
        // Clapet's settings close the list, after Z, reached by the index's gear: no menu on the
        // home to look for them. Its own section, its gear in place of a letter.
        item(key = "settings") {
            Column(Modifier.graphicsLayer { alpha = if (focusedSection() == AlphabetIndex.SETTINGS) 1f else othersAlpha() }) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    tint = LocalClapetColors.current.accent,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, top = SectionGap, bottom = 6.dp)
                        .size(18.dp),
                )
                SettingsRow(onOpenSettings)
            }
        }
    }
}

@Composable
private fun SettingsRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_settings),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            stringResource(R.string.list_settings),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 24.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    notifications: AppNotifications? = null,
    /** Swiping right, only offered while the app has notifications. */
    onSwipe: () -> Unit = {},
) {
    val accent = LocalClapetColors.current.accent
    val swipe = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val density = LocalDensity.current
    val maxSwipePx = with(density) { SwipeMax.toPx() }
    val thresholdPx = with(density) { SwipeThreshold.toPx() }
    // The detector stays on the box, which never moves; only the content follows the finger.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(gestures)
            .then(
                if (notifications == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipe.value >= thresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    currentOnSwipe()
                                }
                                scope.launch { swipe.animateTo(0f) }
                            },
                            onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch { swipe.snapTo((swipe.value + dragAmount).coerceIn(0f, maxSwipePx)) }
                        }
                    }
                },
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.graphicsLayer { translationX = swipe.value },
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    color = TextPrimary,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notifications != null) {
                    // Fits the favorites' 44 dp row with the name: the drag-to-reorder math relies on it.
                    Text(
                        text = notificationPreview(notifications.latest),
                        color = accent,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (notifications != null) CountBadge(notifications.count)
        }
    }
}

/** How far a row follows a swipe right, and how far it must go to unfold its notifications. */
private val SwipeMax = 72.dp
private val SwipeThreshold = 40.dp

@Composable
private fun CountBadge(count: Int) {
    val colors = LocalClapetColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .background(colors.accent, RoundedCornerShape(9.dp))
            .padding(horizontal = 5.dp),
    ) {
        Text(count.toString(), color = colors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
    }
}

/** « Léa : On se retrouve à 19 h ? », or whichever of the two is known. */
@Composable
internal fun notificationPreview(notification: AppNotification): String {
    val parts = NotificationRules.previewParts(notification)
    return if (parts.size == 2) stringResource(R.string.notification_preview, parts[0], parts[1]) else parts.firstOrNull().orEmpty()
}
