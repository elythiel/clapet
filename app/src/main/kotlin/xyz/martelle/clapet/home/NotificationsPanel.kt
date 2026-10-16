package xyz.martelle.clapet.home

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch
import xyz.martelle.clapet.R
import xyz.martelle.clapet.apps.AppEntry
import xyz.martelle.clapet.notifications.AppNotification
import xyz.martelle.clapet.notifications.AppNotifications
import xyz.martelle.clapet.theme.LocalClapetColors
import xyz.martelle.clapet.theme.TextPrimary

/** What can be done with a notification from the home. */
class NotificationActions(
    val onOpen: (AppEntry, AppNotification) -> Unit,
    /** True once sent: the field then closes. */
    val onReply: (AppNotification, String) -> Boolean,
    val onClear: (AppNotification) -> Unit,
)

/**
 * The app's panel, opened by a long press (or a swipe right when it has notifications): its two
 * actions (favorite, app info) next to its name, then one card per notification, to open, answer
 * in place or swipe away. Without notifications it is just that first line. A tap beside it, or
 * back, closes it.
 */
@Composable
fun NotificationsPanel(
    app: AppEntry,
    /** Null when the app has none: the panel then only holds its actions. */
    notifications: AppNotifications?,
    /** Vertical center, on screen, of the row it was opened from: the panel centers on it. */
    anchorY: Float,
    actions: NotificationActions,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    favoritesFull: Boolean,
    onFavoriteToggle: () -> Unit,
    onAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var replyingTo by remember { mutableStateOf<String?>(null) }
    val closeLabel = stringResource(R.string.notifications_close)
    // Opens from its center: the content stays in place and is uncovered upwards and downwards.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(RevealMillis, easing = FastOutSlowInEasing)) }
    Box(
        modifier
            .fillMaxSize()
            // The home behind: a tap there closes the panel.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .semantics { onClick(closeLabel) { onDismiss(); true } }
            .imePadding(),
    ) {
        Column(
            Modifier
                // Under the finger, kept off the screen's edges (they round off on the cover screen).
                .centeredOn(anchorY, top = 4.dp, bottom = PanelInset)
                .padding(start = PanelInset)
                .drawWithContent {
                    val half = size.height / 2f
                    val shown = half * reveal.value
                    // Wide enough for the backdrop's overhang under the cameras.
                    clipRect(left = -size.width, top = half - shown, right = size.width * 2, bottom = half + shown) {
                        this@drawWithContent.drawContent()
                    }
                }
                // As tall as its content. Wider than the column: past the cameras' edge, so the
                // cards keep room on the right and the corners land under the lenses.
                .drawBehind {
                    drawRoundRect(
                        Color.Black.copy(alpha = PanelOpacity),
                        size = Size(size.width + BackdropOverhang.toPx(), size.height),
                        cornerRadius = CornerRadius(PanelCorner.toPx()),
                    )
                }
                // Taps on the panel itself, between its parts, do not close it.
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(start = ListEdgeForPanel - PanelInset, top = 4.dp, bottom = 8.dp),
        ) {
            PanelHeader(app, isFavorite, favoritesFull, onFavoriteToggle, onAppInfo)
            if (notifications != null) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(notifications.items, key = { it.key }) { notification ->
                        NotificationCard(
                            notification = notification,
                            replying = replyingTo == notification.key,
                            onOpen = { actions.onOpen(app, notification) },
                            onStartReply = { replyingTo = notification.key },
                            onSend = { text -> if (actions.onReply(notification, text)) replyingTo = null },
                            onClear = { actions.onClear(notification) },
                            // The cards below slide up when one is swiped away.
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The app, then its actions as icons. The row reaches into the backdrop's overhang: up here, above
 * the lenses, that room is free and leaves more of it to the name.
 */
@Composable
private fun PanelHeader(
    app: AppEntry,
    isFavorite: Boolean,
    favoritesFull: Boolean,
    onFavoriteToggle: () -> Unit,
    onAppInfo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .layout { measurable, constraints ->
                val extra = HeaderOverhang.roundToPx()
                val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
            }
            .heightIn(min = 44.dp)
            .padding(start = 8.dp),
    ) {
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(
            app.label,
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        IconButton(onClick = onFavoriteToggle, enabled = isFavorite || !favoritesFull, modifier = Modifier.size(HeaderButton)) {
            Icon(
                painterResource(if (isFavorite) R.drawable.ic_star_off else R.drawable.ic_star),
                contentDescription = stringResource(
                    when {
                        isFavorite -> R.string.app_menu_remove_favorite
                        favoritesFull -> R.string.favorites_full
                        else -> R.string.app_menu_add_favorite
                    },
                ),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onAppInfo, modifier = Modifier.size(HeaderButton)) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.app_menu_info), modifier = Modifier.size(20.dp))
        }
    }
}

/** Drawn buttons, side by side; 44 dp each keeps them comfortable to the thumb. */
private val HeaderButton = 44.dp
private val HeaderOverhang = 16.dp

/** Nearly opaque: over the home, the rows under the panel must not show through its text. */
private const val PanelOpacity = 0.97f

/** How long the panel takes to open out from its center. */
private const val RevealMillis = 180

/**
 * Places the content (as tall as it needs, within the room) with its vertical center on
 * [anchorY], a position on screen, then slides it back inside if it would run past [top] or
 * [bottom].
 */
private fun Modifier.centeredOn(anchorY: Float, top: Dp, bottom: Dp): Modifier = layout { measurable, constraints ->
    val topPx = top.roundToPx()
    val bottomPx = bottom.roundToPx()
    val room = (constraints.maxHeight - topPx - bottomPx).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = room))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val origin = coordinates?.positionInWindow()?.y ?: 0f
        val wanted = (anchorY - origin - placeable.height / 2f).roundToInt()
        placeable.place(0, wanted.coerceIn(topPx, (constraints.maxHeight - bottomPx - placeable.height).coerceAtLeast(topPx)))
    }
}

/** Same start as the favorites and the list. */
private val ListEdgeForPanel = 12.dp

/** The dimmed backdrop's corners, and how far it reaches past the panel, under the cameras. */
private val PanelCorner = 24.dp
private val BackdropOverhang = 24.dp
private val PanelInset = 6.dp

/** A card swiped past this share of its width is cleared; short of it, it springs back. */
private const val DismissFraction = 0.33f

@Composable
private fun NotificationCard(
    notification: AppNotification,
    replying: Boolean,
    onOpen: () -> Unit,
    onStartReply: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalClapetColors.current
    val time = remember(notification.postedAt) { DateFormat.getTimeFormat(context).format(Date(notification.postedAt)) }
    val swipe = remember(notification.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    val clearLabel = stringResource(R.string.notification_clear)
    // Which side the card uncovers, and whether letting go now clears it: only these two change
    // what is drawn under the card, so the swipe itself recomposes nothing.
    val towardsEnd by remember { derivedStateOf { swipe.value >= 0f } }
    val armed by remember { derivedStateOf { width > 0 && abs(swipe.value) > width * DismissFraction } }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(armed) { if (armed) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    // Swiped either way to clear, as in the system shade. The detector sits on this box, which
    // never moves: the card inside follows the finger.
    Box(
        modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .pointerInput(notification.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(swipe.value) > width * DismissFraction) {
                                swipe.animateTo(sign(swipe.value) * width, tween(150))
                                onClear()
                            } else {
                                swipe.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + dragAmount) }
                }
            }
            // TalkBack cannot swipe a card: clearing stays one action away.
            .semantics { customActions = listOf(CustomAccessibilityAction(clearLabel) { onClear(); true }) },
    ) {
    // Under the card, on the side it uncovers: a bin that grows as the card moves away, and
    // takes the accent once releasing would clear.
    Box(
        contentAlignment = if (towardsEnd) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = Modifier
            .matchParentSize()
            .padding(horizontal = 20.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = if (armed) LocalClapetColors.current.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val progress = (abs(swipe.value) / (width * DismissFraction).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = scaleX
                },
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = swipe.value
                alpha = 1f - (abs(swipe.value) / width.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        // Compact, so two or three fit under the finger: the reply is an icon beside the text.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notification.title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                if (notification.text.isNotBlank()) {
                    Text(
                        notification.text,
                        color = Color(0xFFD9D5CE),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (notification.canReply && !replying) {
                IconButton(onClick = onStartReply, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_reply),
                        contentDescription = stringResource(R.string.notification_reply),
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (replying) ReplyField(onSend)
    }
    }
}

/** The answer, typed on the cover screen's keyboard; sent like from the system shade. */
@Composable
private fun ReplyField(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val send = { if (text.isNotBlank()) onSend(text.trim()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.notification_reply_hint)) },
        maxLines = 3,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = text.isNotBlank()) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.notification_send))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
}
