package xyz.martelle.clapet.notifications

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {

    private fun posted(
        key: String,
        packageName: String = "com.example.chat",
        postedAt: Long = 0,
        title: String = "Léa",
        text: String = "On se retrouve à 19 h ?",
        clearable: Boolean = true,
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
        media: Boolean = false,
    ) = PostedNotification(key, packageName, title, text, postedAt, clearable, ongoing, groupSummary, media, canReply = true)

    @Test
    fun `only notifications the user could clear are shown, silent ones included`() {
        assertTrue(NotificationRules.isShown(posted("a")))
        assertFalse(NotificationRules.isShown(posted("service", clearable = false)))
        assertFalse(NotificationRules.isShown(posted("download", ongoing = true)))
        assertFalse(NotificationRules.isShown(posted("summary", groupSummary = true)))
        assertFalse(NotificationRules.isShown(posted("player", media = true)))
        assertFalse(NotificationRules.isShown(posted("empty", title = "", text = " ")))
    }

    @Test
    fun `grouped by app, newest first`() {
        val byPackage = NotificationRules.byPackage(
            listOf(
                posted("old", postedAt = 1),
                posted("mail", packageName = "com.example.mail", postedAt = 2),
                posted("new", postedAt = 3),
                posted("summary", postedAt = 4, groupSummary = true),
            ),
        )
        assertEquals(setOf("com.example.chat", "com.example.mail"), byPackage.keys)
        val chat = byPackage.getValue("com.example.chat")
        assertEquals(listOf("new", "old"), chat.items.map { it.key })
        assertEquals(2, chat.count)
        assertEquals("new", chat.latest.key)
    }

    @Test
    fun `preview keeps whichever of sender and text is known`() {
        val both = AppNotification("k", "Léa", "Devant le cinéma.", 0, canReply = false)
        assertEquals(listOf("Léa", "Devant le cinéma."), NotificationRules.previewParts(both))
        assertEquals(listOf("Devant le cinéma."), NotificationRules.previewParts(both.copy(title = "")))
    }

    private val track = NowPlaying(
        packageName = "com.example.music",
        title = "Track",
        artist = "Artist",
        art = null,
        playing = true,
        positionMs = 10_000,
        positionUpdatedAt = 1_000,
        speed = 1f,
        durationMs = 60_000,
        canSkipPrevious = true,
        canSkipNext = true,
    )

    @Test
    fun `position moves while playing, stays while paused, never past the end`() {
        assertEquals(15_000, track.positionAt(6_000))
        assertEquals(10_000, track.copy(playing = false).positionAt(6_000))
        assertEquals(60_000, track.positionAt(500_000))
        assertEquals(0.25f, track.progressAt(6_000)!!, 0.001f)
        assertNull(track.copy(durationMs = 0).progressAt(6_000))
    }

    @Test
    fun `the player stays up while paused and goes once stopped`() {
        assertEquals(SessionState.Playing, Sessions.stateOf(PlaybackState.STATE_BUFFERING))
        assertEquals(SessionState.Paused, Sessions.stateOf(PlaybackState.STATE_PAUSED))
        assertEquals(SessionState.Gone, Sessions.stateOf(PlaybackState.STATE_STOPPED))
        assertEquals(SessionState.Gone, Sessions.stateOf(null))
    }

    @Test
    fun `a playing session wins over a paused one`() {
        assertEquals(1, Sessions.pick(listOf(SessionState.Paused, SessionState.Playing)))
        assertEquals(0, Sessions.pick(listOf(SessionState.Paused, SessionState.Gone)))
        assertNull(Sessions.pick(listOf(SessionState.Gone)))
        assertNull(Sessions.pick(emptyList()))
    }
}
