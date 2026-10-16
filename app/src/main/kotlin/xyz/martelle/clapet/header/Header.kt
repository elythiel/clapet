package xyz.martelle.clapet.header

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** One occurrence of a calendar event, as the calendar provider returns it. */
data class CalendarInstance(
    val eventId: Long,
    val title: String,
    /** Provider times. For an all-day event they are midnights in UTC, whatever the time zone. */
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    /** ARGB of its calendar. */
    val color: Int,
)

/** An event of « À venir », with its times already in local terms. */
@Immutable
data class UpcomingEvent(
    val eventId: Long,
    val title: String,
    val color: Int,
    val allDay: Boolean,
    /** For an all-day event, the start of its first day. */
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    /** Already started, not over yet. */
    val ongoing: Boolean,
    /** Provider times, to open this very occurrence. */
    val beginMillis: Long,
    val endMillis: Long,
)

/** What the home header shows. No time nor battery: the cover screen's status bar has them. */
@Immutable
data class HeaderInfo(
    val today: LocalDate,
    /** Local time of the next alarm, if one is set. */
    val nextAlarm: LocalDateTime?,
    /** The alarm is a day or more away: its day is shown with its time. */
    val alarmShowsDay: Boolean,
    /** Empty without the calendar permission: the header then shows the date and alarm only. */
    val events: List<UpcomingEvent>,
    val use24HourFormat: Boolean,
    /**
     * When the header goes stale on its own: next midnight, the next event starting or ending, or
     * the alarm coming within a day.
     */
    val refreshAt: LocalDateTime,
)

enum class DayKind { Today, Tomorrow, Later }

object Upcoming {

    /** As in Niagara: the next two events. */
    const val COUNT = 2

    /** How far ahead the calendar is read. */
    val WINDOW: Duration = Duration.ofDays(7)

    /** The next [count] events that are not over at [now], soonest first (started ones included). */
    fun pick(instances: List<CalendarInstance>, now: LocalDateTime, zone: ZoneId, count: Int = COUNT): List<UpcomingEvent> =
        instances
            .map { it.toUpcoming(now, zone) }
            .filter { it.endsAt > now }
            .sortedWith(compareBy<UpcomingEvent> { it.startsAt }.thenBy { it.title })
            .take(count)

    /** The next moment the date, the shown events or the alarm label change by themselves. */
    fun refreshAt(events: List<UpcomingEvent>, alarm: LocalDateTime?, now: LocalDateTime): LocalDateTime {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val changes = events.flatMap { listOf(it.startsAt, it.endsAt) } + listOfNotNull(alarm?.minusDays(1))
        return (changes.filter { it > now } + midnight).min()
    }

    fun dayKind(date: LocalDate, today: LocalDate): DayKind = when (date) {
        today -> DayKind.Today
        today.plusDays(1) -> DayKind.Tomorrow
        else -> DayKind.Later
    }

    /** The day an event is listed under: today once it has started. */
    fun dayOf(event: UpcomingEvent, today: LocalDate): LocalDate =
        if (event.ongoing) today else event.startsAt.toLocalDate()

    /** An alarm within a day is told by its time alone; further away, its day is needed too. */
    fun alarmNeedsDay(alarm: LocalDateTime, now: LocalDateTime): Boolean =
        Duration.between(now, alarm) >= Duration.ofDays(1)

    private fun CalendarInstance.toUpcoming(now: LocalDateTime, zone: ZoneId): UpcomingEvent {
        // All-day events are stored as UTC midnights: read back as dates, then placed on local days.
        val startsAt: LocalDateTime
        val endsAt: LocalDateTime
        if (allDay) {
            startsAt = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay()
            endsAt = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay()
        } else {
            startsAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(beginMillis), zone)
            endsAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone)
        }
        return UpcomingEvent(
            eventId = eventId,
            title = title,
            color = color,
            allDay = allDay,
            startsAt = startsAt,
            endsAt = endsAt,
            ongoing = startsAt <= now,
            beginMillis = beginMillis,
            endMillis = endMillis,
        )
    }
}
