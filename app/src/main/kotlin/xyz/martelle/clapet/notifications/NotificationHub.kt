package xyz.martelle.clapet.notifications

import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notifications and the playing media, as the notification listener sees them, plus what the
 * home can do with them. Owned by the application; empty while the listener is not connected
 * (no access granted), so the home then works as without it. Main thread only: the listener's
 * callbacks and the home's actions both run there.
 */
class NotificationHub(context: Context) {

    private val appContext = context.applicationContext
    private val _notifications = MutableStateFlow<Map<String, AppNotifications>>(emptyMap())
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** Shown notifications by package name. */
    val notifications: StateFlow<Map<String, AppNotifications>> = _notifications.asStateFlow()

    /** The session the cover player shows, null when nothing plays or is paused. */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var listener: NotificationListenerService? = null
    private var posted: Map<String, StatusBarNotification> = emptyMap()
    private var controller: MediaController? = null

    // The cover art is rescaled once per image, not on every position update.
    private var artSource: Bitmap? = null
    private var art: ImageBitmap? = null

    internal fun onConnected(service: NotificationListenerService) {
        listener = service
    }

    internal fun onDisconnected() {
        listener = null
        posted = emptyMap()
        _notifications.value = emptyMap()
        showSession(null)
    }

    internal fun publish(active: List<StatusBarNotification>) {
        posted = active.associateBy { it.key }
        _notifications.value = NotificationRules.byPackage(active.map { it.toPosted() })
    }

    internal fun showSession(controller: MediaController?) {
        this.controller = controller
        _nowPlaying.value = controller?.toNowPlaying()
    }

    /**
     * Opens what the notification points to, on the screen [activity] is on. False when it points
     * nowhere: the caller then opens the app instead.
     */
    fun open(key: String, activity: Activity): Boolean {
        val notification = posted[key]?.notification ?: return false
        val content = notification.contentIntent ?: return false
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(activity.display.displayId)
            // The intent is another app's: Clapet, visible, lends it its right to open a screen.
            .setPendingIntentBackgroundActivityStartMode(allowWhileVisible())
        val sent = try {
            content.send(activity, 0, null, null, null, null, options.toBundle())
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
        if (sent && notification.flags and Notification.FLAG_AUTO_CANCEL != 0) clear(key)
        return sent
    }

    fun clear(key: String) {
        listener?.cancelNotification(key)
    }

    /** Sends [text] through the notification's reply action, as the system shade does. */
    fun reply(key: String, text: String): Boolean {
        val (action, input) = posted[key]?.notification?.replyAction() ?: return false
        val results = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), results, Bundle().apply { putCharSequence(input.resultKey, text) })
        RemoteInput.setResultsSource(results, RemoteInput.SOURCE_FREE_FORM_INPUT)
        return try {
            action.actionIntent.send(appContext, 0, results)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    fun playPause() {
        val current = controller ?: return
        if (Sessions.stateOf(current.playbackState?.state) == SessionState.Playing) {
            current.transportControls.pause()
        } else {
            current.transportControls.play()
        }
    }

    fun skipPrevious() {
        controller?.transportControls?.skipToPrevious()
    }

    fun skipNext() {
        controller?.transportControls?.skipToNext()
    }

    private fun MediaController.toNowPlaying(): NowPlaying {
        val state = playbackState
        val meta = metadata
        // Apps that declare no actions at all (0) usually still handle them.
        val actions = state?.actions ?: 0L
        fun can(action: Long) = actions == 0L || actions and action != 0L
        return NowPlaying(
            packageName = packageName,
            title = meta?.text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = meta?.text(
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            ).orEmpty(),
            art = artOf(meta),
            playing = Sessions.stateOf(state?.state) == SessionState.Playing,
            positionMs = state?.position ?: 0L,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed ?: 1f,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L,
            canSkipPrevious = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            canSkipNext = can(PlaybackState.ACTION_SKIP_TO_NEXT),
        )
    }

    private fun artOf(meta: MediaMetadata?): ImageBitmap? {
        val source = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (source !== artSource) {
            artSource = source
            art = source?.let { bitmap ->
                // A full-size cover for a 120 dp square: keep memory in check.
                val factor = ArtMaxPx.toFloat() / maxOf(bitmap.width, bitmap.height)
                (if (factor < 1f) bitmap.scale((bitmap.width * factor).toInt(), (bitmap.height * factor).toInt()) else bitmap)
                    .asImageBitmap()
            }
        }
        return art
    }

    private companion object {
        const val ArtMaxPx = 384

        fun MediaMetadata.text(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { getText(it)?.toString()?.takeIf(String::isNotBlank) }

        @Suppress("DEPRECATION")
        fun allowWhileVisible(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            } else {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
    }
}

/** The free-text reply of a notification, the one tagged « reply » first. */
private fun Notification.replyAction(): Pair<Notification.Action, RemoteInput>? =
    actions.orEmpty()
        .sortedByDescending { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
        .firstNotNullOfOrNull { action ->
            action.remoteInputs?.firstOrNull { it.allowFreeFormInput }?.let { action to it }
        }

private fun StatusBarNotification.toPosted(): PostedNotification {
    val extras = notification.extras
    return PostedNotification(
        key = key,
        packageName = packageName,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
        text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString().orEmpty(),
        postedAt = postTime,
        clearable = isClearable,
        ongoing = isOngoing,
        groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        media = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || notification.category == Notification.CATEGORY_TRANSPORT,
        canReply = notification.replyAction() != null,
    )
}
