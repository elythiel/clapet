package xyz.martelle.clapet.notifications

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/**
 * Follows the active media sessions (Android lists them only to an enabled notification listener,
 * named by [listener]) and reports the one the player should show, on every change of session,
 * track or state.
 */
internal class MediaWatcher(
    context: Context,
    private val listener: ComponentName,
    private val onShown: (MediaController?) -> Unit,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() = update()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    fun start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            track(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            // Access revoked meanwhile: no player.
            onShown(null)
        }
    }

    fun stop() {
        sessions.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
        onShown(null)
    }

    private fun track(active: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = active
        active.forEach { it.registerCallback(callback, handler) }
        update()
    }

    private fun update() {
        val shown = Sessions.pick(controllers.map { Sessions.stateOf(it.playbackState?.state) })
        onShown(shown?.let(controllers::get))
    }
}
