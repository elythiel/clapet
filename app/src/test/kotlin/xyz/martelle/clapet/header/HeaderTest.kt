package xyz.martelle.clapet.header

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val now = LocalDateTime.of(2026, 10, 16, 14, 0)

    private fun timed(id: Long, title: String, begin: LocalDateTime, end: LocalDateTime) = CalendarInstance(
        eventId = id,
        title = title,
        beginMillis = begin.atZone(paris).toInstant().toEpochMilli(),
        endMillis = end.atZone(paris).toInstant().toEpochMilli(),
        allDay = false,
        color = 0,
    )

    // As the provider stores them: UTC midnights.
    private fun allDay(id: Long, title: String, first: LocalDate, daysLong: Long = 1) = CalendarInstance(
        eventId = id,
        title = title,
        beginMillis = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        endMillis = first.plusDays(daysLong).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true,
        color = 0,
    )

    @Test
    fun `the next two events not over yet, soonest first`() {
        val picked = Upcoming.pick(
            listOf(
                timed(1, "Over", now.minusHours(2), now.minusHours(1)),
                timed(2, "Tomorrow", now.plusDays(1), now.plusDays(1).plusHours(1)),
                timed(3, "Ongoing", now.minusMinutes(30), now.plusMinutes(30)),
                timed(4, "Tonight", now.plusHours(4), now.plusHours(5)),
            ),
            now,
            paris,
        )
        assertEquals(listOf("Ongoing", "Tonight"), picked.map { it.title })
        assertTrue(picked[0].ongoing)
        assertFalse(picked[1].ongoing)
    }

    @Test
    fun `all-day events stay on their own date in any time zone`() {
        val today = now.toLocalDate()
        val event = allDay(1, "Holiday", today)
        // West of Greenwich, the UTC midnight is still yesterday evening: the date must not slip.
        val newYork = ZoneId.of("America/New_York")
        val picked = Upcoming.pick(listOf(event), now, newYork).single()
        assertEquals(today.atStartOfDay(), picked.startsAt)
        assertEquals(today.plusDays(1).atStartOfDay(), picked.endsAt)
        assertTrue(picked.ongoing)
    }

    @Test
    fun `an all-day event is over the next day`() {
        val yesterday = allDay(1, "Yesterday", now.toLocalDate().minusDays(1))
        assertTrue(Upcoming.pick(listOf(yesterday), now, paris).isEmpty())
    }

    @Test
    fun `a multi-day event is listed today once started`() {
        val trip = Upcoming.pick(listOf(allDay(1, "Trip", now.toLocalDate().minusDays(2), daysLong = 5)), now, paris).single()
        assertEquals(now.toLocalDate(), Upcoming.dayOf(trip, now.toLocalDate()))
    }

    @Test
    fun `refresh at the next event boundary, else at midnight`() {
        val events = Upcoming.pick(listOf(timed(1, "Soon", now.plusHours(1), now.plusHours(2))), now, paris)
        assertEquals(now.plusHours(1), Upcoming.refreshAt(events, alarm = null, now = now))
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(), Upcoming.refreshAt(emptyList(), alarm = null, now = now))
    }

    @Test
    fun `refresh when the alarm comes within a day`() {
        val alarm = now.plusHours(30)
        assertEquals(now.plusHours(6), Upcoming.refreshAt(emptyList(), alarm, now))
    }

    @Test
    fun `alarm label needs its day from 24 hours away`() {
        assertFalse(Upcoming.alarmNeedsDay(now.plusHours(17), now))
        assertTrue(Upcoming.alarmNeedsDay(now.plusDays(1), now))
    }

    @Test
    fun `days are today, tomorrow or later`() {
        val today = now.toLocalDate()
        assertEquals(DayKind.Today, Upcoming.dayKind(today, today))
        assertEquals(DayKind.Tomorrow, Upcoming.dayKind(today.plusDays(1), today))
        assertEquals(DayKind.Later, Upcoming.dayKind(today.plusDays(2), today))
    }
}
