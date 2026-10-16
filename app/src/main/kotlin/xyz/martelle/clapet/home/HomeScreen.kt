package xyz.martelle.clapet.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import java.io.File
import xyz.martelle.clapet.R
import xyz.martelle.clapet.apps.AlphabetIndex
import xyz.martelle.clapet.apps.AppEntry
import xyz.martelle.clapet.apps.Favorites
import xyz.martelle.clapet.cover.CoverGeometry
import xyz.martelle.clapet.cover.coverStatusBar
import xyz.martelle.clapet.header.HeaderInfo
import xyz.martelle.clapet.header.UpcomingEvent
import xyz.martelle.clapet.notifications.AppNotifications
import xyz.martelle.clapet.notifications.NowPlaying
import xyz.martelle.clapet.health.HealthItem
import xyz.martelle.clapet.settings.WallpaperSettings
import xyz.martelle.clapet.settings.label
import xyz.martelle.clapet.theme.LocalClapetColors
import xyz.martelle.clapet.wallpaper.Wallpaper

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

/**
 * Scrub zones overlap the content: only a quick vertical drag starts a scrub, so taps and long
 * presses there still reach the rows. On the home the whole width listens (each half picks its
 * side). Once the list is open, only the band before the content of the side in use listens, so
 * a scroll that starts on an app's icon stays a scroll: on the start side up to the icons
 * ([ContentStart]), on the end side a little past the list's end margin ([ContentEnd]).
 */
private val ScrubZoneStart = ContentStart - 4.dp
private val ScrubZoneEnd = ContentEnd + 8.dp

/** Vertical travel that starts a scrub: just past the system's touch slop (about 8 dp), so a tap never does. */
private val ScrubThreshold = 10.dp

/** Vertical bounds of the letters inside the content area. */
private val StripTopPadding = 8.dp
private val StripBottomPadding = 12.dp

/** Same treatment as the settings: readable text on any wallpaper. */
private val ListBlur = 22.dp
private const val ListMinDimPercent = 60

/** Short on purpose: the list must feel as immediate as the thumb that opens it, yet visibly fade. */
private const val ListFadeMillis = 150

/** At release, the sections around the one under the thumb fade in this fast. */
private const val RevealMillis = 200

/**
 * Room above the first section and below the last. The top one also places the section the index
 * jumps to: with the gap above its letter, the letter lands about where the favorites start.
 */
private val ListTopGap = 112.dp - SectionGap
private val ListBottomGap = 160.dp

/** Margin around the player, inside the square above the cameras. */
private val PlayerGap = 4.dp

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
    /** Null until first read. */
    header: HeaderInfo?,
    onOpenEvent: (UpcomingEvent) -> Unit,
    onOpenAlarms: () -> Unit,
    /** By package name; empty without notification access. */
    notifications: Map<String, AppNotifications>,
    notificationActions: NotificationActions,
    /** Null when nothing plays or is paused (or without notification access). */
    nowPlaying: NowPlaying?,
    mediaActions: MediaActions,
    /** First required setting that is off, if any: shown as a small accent dot. */
    healthAlert: HealthItem?,
    onHealthAlertClick: (HealthItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // False shows the favorites; true the list, opened by sliding on the index.
    var listOpen by remember { mutableStateOf(false) }
    // As in Niagara: while the thumb slides, only its section shows; the rest fades in at release.
    var listRevealed by remember { mutableStateOf(false) }
    var focusedSection by remember { mutableStateOf<Char?>(null) }
    // The app whose panel is open (its actions and notifications), by package name.
    var unfolded by remember { mutableStateOf<String?>(null) }
    // The vertical center, on screen, of the row the panel was opened from: it opens there.
    var unfoldedAt by remember { mutableFloatStateOf(0f) }
    val unfoldedApp = unfolded?.let { pkg -> apps.firstOrNull { it.component.packageName == pkg } }
    // An app removed while its panel is open closes it.
    LaunchedEffect(unfolded, unfoldedApp == null) {
        if (unfolded != null && unfoldedApp == null) unfolded = null
    }
    // Back to the favorites whenever the home leaves the screen (an app opened, the phone unfolded).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        listOpen = false
        unfolded = null
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = listOpen || unfolded != null,
        // The open panel first, then the list.
        onBackCompleted = { if (unfolded != null) unfolded = null else listOpen = false },
    )
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = remember(apps) { sectionsOf(apps) }
    val headers = remember(sections) { headerIndexes(sections) }
    // The gear always leads somewhere: Clapet's settings end the list.
    val lettersWithApps = remember(sections) { sections.mapTo(HashSet()) { it.letter } + AlphabetIndex.SETTINGS }
    // The section at the top of the list: highlighted in the index, so it follows free scrolling too.
    val visibleLetter by remember(headers) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            headers.entries.lastOrNull { it.value <= first }?.key
        }
    }
    // The index the thumb used: only that one shows while the list is open; the other side's room goes to the list.
    var activeSide by remember { mutableStateOf(StripSide.Start) }
    // Scrolls the list itself, hidden or not: the section is then drawn where the full list holds
    // it (lower near the end, where the list cannot scroll further), and nothing moves at release.
    val jumpTo: (Char, Boolean) -> Unit = { letter, reveal ->
        listOpen = true
        listRevealed = reveal
        AlphabetIndex.sectionFor(letter, lettersWithApps)?.let { target ->
            focusedSection = target
            // Applied at the list's next layout, so it opens straight on the section (no frame on "A").
            listState.requestScrollToItem(headers.getValue(target))
        }
    }
    val scrub = remember(scope) { AlphabetScrubState(scope) }
    val haptic = LocalHapticFeedback.current
    // Read through updated state: the scrub detector lives across recompositions.
    // Not over an open panel: its cards scroll vertically, right where the index listens.
    val canScrub by rememberUpdatedState { side: StripSide -> unfolded == null && (!listOpen || activeSide == side) }
    val onScrubLetter by rememberUpdatedState { side: StripSide, letter: Char ->
        activeSide = side
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        jumpTo(letter, false)
    }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val onHome by rememberUpdatedState(!listOpen)
    val launch: (AppEntry) -> Unit = { app ->
        listOpen = false
        actions.onLaunch(app)
    }
    val rowActions = remember(actions) {
        AppActions(
            launch,
            actions.onAppInfo,
            actions.onAddFavorite,
            actions.onRemoveFavorite,
            actions.onMoveFavorite,
            onOpenPanel = { app, centerY ->
                unfoldedAt = centerY
                unfolded = app.component.packageName
            },
        )
    }

    // 0 on the home, 1 with the list open: drives the crossfade and the wallpaper veil (the list
    // sits on a blurred, darker wallpaper, like every screen but the home itself). Kept as a State
    // and read at draw time only, so the animation never recomposes the screen.
    val listProgress = animateFloatAsState(if (listOpen) 1f else 0f, tween(ListFadeMillis), label = "listProgress")
    // How much of the sections other than the focused one shows: they vanish at once when a slide
    // starts, and fade in at release.
    val revealProgress = animateFloatAsState(
        if (listRevealed) 1f else 0f,
        if (listRevealed) tween(RevealMillis) else snap(),
        label = "revealProgress",
    )

    val density = LocalDensity.current
    val insets = if (geometry != null) WindowInsets.coverStatusBar else WindowInsets.safeDrawing
    val areaLeft = geometry?.content?.left?.toFloat() ?: insets.getLeft(density, LayoutDirection.Ltr).toFloat()
    val areaRight = geometry?.content?.right?.toFloat()
        ?: (rootSize.width - insets.getRight(density, LayoutDirection.Ltr)).toFloat()
    // The side against the screen's edge, where HyperOS keeps its gesture band; the other one
    // borders the cameras. Upright the edge is on the left, upside down on the right.
    val edgeSide = if (geometry?.upsideDown == true) StripSide.End else StripSide.Start
    // Per side: how far its index sits from that border, the room the list leaves beside the
    // index in use, and the list's margin when that index is idle.
    fun stripInset(side: StripSide) = if (side == edgeSide) StripStart else 0.dp
    fun besideStrip(side: StripSide) = if (side == edgeSide) ContentStart - 8.dp else ContentEnd
    fun listMargin(side: StripSide) = if (side == edgeSide) ListEdge else 0.dp
    val scrubZoneStartPx = with(density) { ScrubZoneStart.toPx() }
    val scrubZoneEndPx = with(density) { ScrubZoneEnd.toPx() }
    val stripTop = insets.getTop(density) + with(density) { StripTopPadding.toPx() }
    val stripBottom = rootSize.height - insets.getBottom(density) - with(density) { StripBottomPadding.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .alphabetScrub(
                state = scrub,
                left = areaLeft,
                right = areaRight,
                zoneWidth = { side ->
                    when {
                        onHome -> (areaRight - areaLeft) / 2f
                        side == edgeSide -> scrubZoneStartPx
                        else -> scrubZoneEndPx
                    }
                },
                threshold = with(density) { ScrubThreshold.toPx() },
                stripTop = stripTop,
                stripBottom = stripBottom,
                isEnabled = { canScrub(it) },
                onLetter = { side, letter -> onScrubLetter(side, letter) },
                onEnd = { listRevealed = true },
            ),
    ) {
        // Taps and long presses on empty areas: the background only receives touches no row takes.
        Wallpaper(
            settings = wallpaper,
            file = wallpaperFile,
            blurRadius = ListBlur,
            minDimPercent = ListMinDimPercent,
            veil = { listProgress.value },
            modifier = Modifier
                .fillMaxSize()
                // A tap on the background closes the list; a swipe there, however long, does nothing.
                .stillPressGestures(onTap = { listOpen = false }),
        )

        // On the cover screen everything stays beside the cameras; elsewhere, the safe area.
        val area = if (geometry != null) {
            Modifier
                .offset { IntOffset(geometry.content.left, 0) }
                .width(with(density) { geometry.content.width.toDp() })
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.coverStatusBar)
        } else {
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        }
        Box(area) {
            // No letters show on the home: the favorites take the whole width, from HyperOS's edge band.
            Column(
                Modifier
                    .fadeLayer { 1f - listProgress.value }
                    .padding(start = ListEdge, end = listMargin(StripSide.End)),
            ) {
                HomeHeader(
                    header = header,
                    onOpenEvent = onOpenEvent,
                    onOpenAlarms = onOpenAlarms,
                    reserveTopEnd = healthAlert != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeaderHeight),
                )
                FavoritesList(favorites, rowActions, notifications = notifications)
            }
            // Always composed, even on the home: opening it then costs no first composition, and
            // the thumb sees it fade in on the very next frame.
            AllApps(
                sections = sections,
                listState = listState,
                actions = rowActions,
                onOpenSettings = onOpenSettings,
                contentPadding = PaddingValues(top = ListTopGap, bottom = ListBottomGap),
                focusedSection = { focusedSection },
                othersAlpha = { revealProgress.value },
                notifications = notifications,
                // Aligned with the favorites: the wave, drawn from the screen edge, stops short of the icons.
                modifier = Modifier
                    .fadeLayer { listProgress.value }
                    .padding(
                        start = if (activeSide == StripSide.Start) besideStrip(StripSide.Start) else listMargin(StripSide.Start),
                        end = if (activeSide == StripSide.End) besideStrip(StripSide.End) else listMargin(StripSide.End),
                    ),
            )
            // Two indexes, one per edge, invisible on the home so either thumb can start. Once the
            // list is open, only the one in use shows and listens; the other leaves its room to the list.
            StripSide.entries.forEach { side ->
                AlphabetStrip(
                    side = side,
                    scrub = scrub,
                    selected = if (listOpen) visibleLetter else null,
                    lettersWithApps = lettersWithApps,
                    onSelect = { letter ->
                        activeSide = side
                        jumpTo(letter, true)
                    },
                    revealed = listOpen && activeSide == side,
                    enabled = !listOpen || activeSide == side,
                    atScreenEdge = side == edgeSide,
                    modifier = Modifier
                        .align(if (side == StripSide.Start) Alignment.TopStart else Alignment.TopEnd)
                        .padding(
                            start = if (side == StripSide.Start) stripInset(side) else 0.dp,
                            end = if (side == StripSide.End) stripInset(side) else 0.dp,
                            top = StripTopPadding,
                            bottom = StripBottomPadding,
                        )
                        .width(StripWidth)
                        .fillMaxHeight(),
                )
            }
            if (healthAlert != null && !listOpen) {
                HealthDot(
                    healthAlert,
                    onClick = { onHealthAlertClick(healthAlert) },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            if (unfoldedApp != null) {
                NotificationsPanel(
                    app = unfoldedApp,
                    notifications = notifications[unfoldedApp.component.packageName],
                    anchorY = unfoldedAt,
                    actions = notificationActions,
                    onDismiss = { unfolded = null },
                    isFavorite = unfoldedApp.key in favoriteKeys,
                    favoritesFull = favoriteKeys.size >= Favorites.MAX,
                    onFavoriteToggle = {
                        if (unfoldedApp.key in favoriteKeys) actions.onRemoveFavorite(unfoldedApp) else actions.onAddFavorite(unfoldedApp)
                    },
                    onAppInfo = { actions.onAppInfo(unfoldedApp) },
                    camerasOnStart = geometry?.upsideDown == true,
                )
            }
        }

        // The free square of the cameras' column (above them, below once upside down), clear of
        // the status bar. Hidden under an open panel, whose backdrop reaches past the cameras' edge.
        if (geometry != null && nowPlaying != null && unfolded == null) {
            val gap = with(density) { PlayerGap.roundToPx() }
            val square = geometry.square
            val top = maxOf(square.top, insets.getTop(density)) + gap
            val left = square.left + gap
            val right = square.right - gap
            val bottom = square.bottom - gap
            NowPlayingCard(
                nowPlaying = nowPlaying,
                actions = mediaActions,
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() }),
            )
        }
    }
}

/**
 * Measured (so composed) at all times, but placed only while [alpha] is above 0: fully faded out,
 * the content draws nothing and neither touches nor TalkBack reach it.
 */
private fun Modifier.fadeLayer(alpha: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (alpha() > 0f) placeable.placeRelativeWithLayer(0, 0) { this.alpha = alpha() }
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
