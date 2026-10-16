package xyz.martelle.clapet.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import xyz.martelle.clapet.R
import xyz.martelle.clapet.apps.AlphabetIndex
import xyz.martelle.clapet.apps.AppEntry
import xyz.martelle.clapet.cover.CoverGeometry
import xyz.martelle.clapet.cover.coverStatusBar
import xyz.martelle.clapet.health.HealthItem
import xyz.martelle.clapet.settings.WallpaperSettings
import xyz.martelle.clapet.settings.label
import xyz.martelle.clapet.theme.LocalClapetColors
import xyz.martelle.clapet.wallpaper.Wallpaper

/** Width kept free left of the cameras so the long-press menu never opens over them. */
private val MenuWidth = 220.dp

/**
 * HyperOS keeps a 62 px (≈ 19 dp) gesture window on the left edge of the cover screen
 * (FlipGestureStubLeft), out of reach of gesture exclusion: the index starts just past it,
 * and its touch area spans the whole gap up to the favorites, so the thumb need not be exact.
 */
private val StripStart = 20.dp
private val ContentStart = 52.dp
private val StripWidth = ContentStart - 8.dp - StripStart

/** The second index, against the cameras (or the right edge), so either thumb can slide; content stops before it. */
private val ContentEnd = StripWidth + 4.dp

/** List start when its left index is not in use: just past HyperOS's edge gesture band. */
private val ListEdge = 12.dp

/** Same treatment as the settings: readable text on any wallpaper. */
private val ListBlur = 22.dp
private const val ListMinDimPercent = 60

/** Fixed header height, as in Niagara (not adjustable here): favorites start 162 dp from the top. */
private val HeaderHeight = 127.dp

@Composable
fun HomeScreen(
    geometry: CoverGeometry?,
    wallpaper: WallpaperSettings,
    wallpaperFile: File,
    apps: List<AppEntry>,
    favorites: List<AppEntry>,
    favoriteKeys: List<String>,
    actions: AppActions,
    onOpenSettings: () -> Unit,
    /** First required setting that is off, if any: shown as a small accent dot. */
    healthAlert: HealthItem?,
    onHealthAlertClick: (HealthItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Position and visibility are separate: closing keeps the position, so the menu fades
    // out where it was instead of jumping to the corner during its exit animation.
    var menuPosition by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    val openMenuLabel = stringResource(R.string.home_menu_open)

    // False shows the favorites; true the full list, opened by sliding on the index.
    var listOpen by remember { mutableStateOf(false) }
    // Back to the favorites whenever the home leaves the screen (an app opened, the phone unfolded).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { listOpen = false }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = listOpen,
        onBackCompleted = { listOpen = false },
    )
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = remember(apps) { sectionsOf(apps) }
    val headers = remember(sections) { headerIndexes(sections) }
    val lettersWithApps = remember(sections) { sections.mapTo(HashSet()) { it.letter } }
    // The section at the top of the list: highlighted in the index, so it follows free scrolling too.
    val visibleLetter by remember(headers) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            headers.entries.lastOrNull { it.value <= first }?.key
        }
    }
    // The index the thumb used: only that one shows while the list is open; the other side's room goes to the list.
    var activeSide by remember { mutableStateOf(StripSide.Start) }
    val jumpTo: (Char) -> Unit = { letter ->
        listOpen = true
        AlphabetIndex.sectionFor(letter, lettersWithApps)?.let { target ->
            scope.launch { listState.scrollToItem(headers.getValue(target)) }
        }
    }
    val launch: (AppEntry) -> Unit = { app ->
        listOpen = false
        actions.onLaunch(app)
    }
    val rowActions = remember(actions) {
        AppActions(launch, actions.onAppInfo, actions.onAddFavorite, actions.onRemoveFavorite, actions.onMoveFavorite)
    }

    // The letter list sits on a blurred, darker wallpaper, like every screen but the home itself.
    val listShown = listOpen
    val blur by animateDpAsState(if (listShown) ListBlur else 0.dp, label = "wallpaperBlur")
    val dim by animateIntAsState(
        if (listShown) maxOf(wallpaper.dimPercent, ListMinDimPercent) else wallpaper.dimPercent,
        label = "wallpaperDim",
    )

    Box(modifier.fillMaxSize()) {
        // Taps and long presses on empty areas: the background only receives touches no row takes.
        Wallpaper(
            settings = wallpaper.copy(dimPercent = dim),
            file = wallpaperFile,
            blurRadius = blur,
            modifier = Modifier
                .fillMaxSize()
                // A swipe on the background, however long, is not a long press.
                .stillPressGestures(
                    onTap = { listOpen = false },
                    onLongPress = {
                        menuPosition = it
                        menuOpen = true
                    },
                )
                .semantics {
                    onLongClick(openMenuLabel) {
                        menuPosition = Offset.Zero
                        menuOpen = true
                        true
                    }
                },
        )

        val density = LocalDensity.current
        // On the cover screen everything stays left of the cameras; elsewhere, the safe area.
        val area = if (geometry != null) {
            Modifier
                .width(with(density) { geometry.cameras.left.toDp() })
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.coverStatusBar)
        } else {
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        }
        Box(area) {
            AnimatedVisibility(visible = !listOpen, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.padding(start = ContentStart - 8.dp, end = ContentEnd)) {
                    // Milestone 6 fills the header (date, alarm, upcoming events).
                    Spacer(Modifier.height(HeaderHeight))
                    FavoritesList(favorites, rowActions)
                }
            }
            AnimatedVisibility(visible = listOpen, enter = fadeIn(), exit = fadeOut()) {
                AllApps(
                    sections = sections,
                    listState = listState,
                    favoriteKeys = favoriteKeys,
                    actions = rowActions,
                    // Aligned with the favorites: the wave, drawn from the screen edge, stops short of the icons.
                    modifier = Modifier.padding(
                        start = if (activeSide == StripSide.Start) ContentStart - 8.dp else ListEdge,
                        end = if (activeSide == StripSide.End) ContentEnd else 0.dp,
                        top = 8.dp,
                    ),
                )
            }
            // Two indexes, one per edge, invisible on the home so either thumb can start. Once the
            // list is open, only the one in use shows and listens; the other leaves its room to the list.
            StripSide.entries.forEach { side ->
                AlphabetStrip(
                    selected = if (listOpen) visibleLetter else null,
                    lettersWithApps = lettersWithApps,
                    onSelect = { letter ->
                        activeSide = side
                        jumpTo(letter)
                    },
                    side = side,
                    revealed = listOpen && activeSide == side,
                    enabled = !listOpen || activeSide == side,
                    modifier = Modifier
                        .align(if (side == StripSide.Start) Alignment.TopStart else Alignment.TopEnd)
                        .padding(
                            start = if (side == StripSide.Start) StripStart else 0.dp,
                            top = 8.dp,
                            bottom = 12.dp,
                        )
                        .width(StripWidth)
                        .fillMaxHeight(),
                )
            }
            if (healthAlert != null && !listOpen) {
                HealthDot(
                    healthAlert,
                    onClick = { onHealthAlertClick(healthAlert) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = ContentEnd),
                )
            }
        }

        HomeMenu(
            open = menuOpen,
            position = menuPosition,
            maxAnchorX = geometry?.cameras?.left,
            onDismiss = { menuOpen = false },
            onOpenSettings = {
                menuOpen = false
                onOpenSettings()
            },
        )
    }
}

/** Discreet on purpose: a 10 dp accent dot, in a full 48 dp touch target. */
@Composable
private fun HealthDot(item: HealthItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_health_alert, stringResource(item.label))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.size(10.dp).background(LocalClapetColors.current.accent, CircleShape))
    }
}

@Composable
private fun HomeMenu(
    open: Boolean,
    position: Offset,
    maxAnchorX: Int?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val menuWidthPx = with(LocalDensity.current) { MenuWidth.roundToPx() }
    val x = position.x.roundToInt()
    val clampedX = if (maxAnchorX != null) x.coerceIn(0, (maxAnchorX - menuWidthPx).coerceAtLeast(0)) else x
    Box(Modifier.offset { IntOffset(clampedX, position.y.roundToInt()) }) {
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings), style = MaterialTheme.typography.bodyLarge) },
                onClick = onOpenSettings,
            )
        }
    }
}
