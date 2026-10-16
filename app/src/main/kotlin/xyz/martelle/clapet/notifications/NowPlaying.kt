package xyz.martelle.clapet.notifications

import android.media.session.PlaybackState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** What the cover player shows: the active media session, playing or paused. */
@Immutable
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    /** Position at [positionUpdatedAt], on the elapsed-realtime clock (what PlaybackState uses). */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    /** 0 when the app does not say (a live stream). */
    val durationMs: Long,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
) {
    /** The position at [elapsedRealtime]: it moves on its own while playing. */
    fun positionAt(elapsedRealtime: Long): Long {
        val moved = if (playing) ((elapsedRealtime - positionUpdatedAt) * speed).toLong() else 0L
        val position = (positionMs + moved).coerceAtLeast(0)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }

    /** 0 to 1, or null when the length is unknown. */
    fun progressAt(elapsedRealtime: Long): Float? =
        if (durationMs > 0) positionAt(elapsedRealtime).toFloat() / durationMs else null
}

/** What the player makes of a session: up while playing or paused, gone once stopped. */
enum class SessionState { Playing, Paused, Gone }

object Sessions {

    fun stateOf(playbackState: Int?): SessionState = when (playbackState) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        -> SessionState.Playing
        PlaybackState.STATE_PAUSED -> SessionState.Paused
        else -> SessionState.Gone
    }

    /**
     * The session to show among the active ones, in Android's order (most relevant first): the
     * first playing one, else the first paused one; null when none is up.
     */
    fun pick(states: List<SessionState>): Int? =
        states.indexOf(SessionState.Playing).takeIf { it >= 0 }
            ?: states.indexOf(SessionState.Paused).takeIf { it >= 0 }
}
