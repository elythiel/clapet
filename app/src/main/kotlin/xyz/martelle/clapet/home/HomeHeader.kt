package xyz.martelle.clapet.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import xyz.martelle.clapet.R
import xyz.martelle.clapet.header.DayKind
import xyz.martelle.clapet.header.HeaderInfo
import xyz.martelle.clapet.header.Upcoming
import xyz.martelle.clapet.header.UpcomingEvent
import xyz.martelle.clapet.theme.LocalClapetColors
import xyz.martelle.clapet.theme.TextPrimary

/** Taller than the mockup's 32 dp, for the thumb, while two still fit in the header. */
private val EventRowHeight = 36.dp

/** The date row stops short of the health dot, when one is drawn in the top-right corner. */
private val HealthDotRoom = 40.dp

private val DateStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
private val AlarmStyle = TextStyle(fontSize = 12.sp)
private val AlarmIconSize = 12.dp
private val AlarmIconGap = 4.dp
private val DateAlarmGap = 10.dp

/**
 * Date and next alarm, then « À venir » with the next two events, in the fixed header above the
 * favorites. Formats follow the locale and the 12/24 h setting.
 */
@Composable
fun HomeHeader(
    header: HeaderInfo?,
    onOpenEvent: (UpcomingEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** The health dot is shown: keep its corner free. */
    reserveTopEnd: Boolean = false,
) {
    // Read in a few milliseconds at start: nothing rather than a placeholder date.
    if (header == null) return
    val locale = LocalConfiguration.current.locales[0]
    val formats = remember(locale, header.use24HourFormat) { HeaderFormats(locale, header.use24HourFormat) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Stacked from the bottom, as in Niagara: the date sits right above the favorites, and the
    // events, when there are some, slide in between.
    Column(modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.Bottom) {
        val alarmLabel = header.nextAlarm?.let { formats.alarm(it, showsDay = header.alarmShowsDay) }
        Box(Modifier.padding(start = 8.dp, end = if (reserveTopEnd) HealthDotRoom else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DateAlarmGap)) {
                Text(
                    // Abbreviated (« Ven. 16 oct. »), so it fits beside a far alarm left of the cameras.
                    formats.shortDate(header.today),
                    color = TextPrimary,
                    style = DateStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (alarmLabel != null) {
                    val description = stringResource(R.string.header_alarm, alarmLabel)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AlarmIconGap),
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alarm),
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(AlarmIconSize),
                        )
                        Text(alarmLabel, color = muted, style = AlarmStyle, maxLines = 1)
                    }
                }
            }
        }
        if (header.events.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.header_upcoming),
                color = LocalClapetColors.current.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
            header.events.forEach { event ->
                EventRow(event, header, formats, onClick = { onOpenEvent(event) })
            }
        }
    }
}

@Composable
private fun EventRow(event: UpcomingEvent, header: HeaderInfo, formats: HeaderFormats, onClick: () -> Unit) {
    val title = event.title.ifBlank { stringResource(R.string.header_untitled) }
    val line = if (event.allDay) title else "${formats.time(event.startsAt)} · $title"
    val day = Upcoming.dayOf(event, header.today)
    val whenLabel = when {
        event.ongoing && !event.allDay -> stringResource(R.string.header_ongoing)
        else -> when (Upcoming.dayKind(day, header.today)) {
            DayKind.Today -> stringResource(R.string.header_today)
            DayKind.Tomorrow -> stringResource(R.string.header_tomorrow)
            DayKind.Later -> formats.shortDate(day)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EventRowHeight)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        // The calendar's own color, as in Niagara.
        Box(
            Modifier
                .width(4.dp)
                .height(30.dp)
                .background(Color(event.color), RoundedCornerShape(2.dp)),
        )
        Column {
            Text(
                line,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(whenLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
        }
    }
}

/** The locale's own patterns: « ven. 16 oct. », « Fri, Oct 16 », 18:30 or 6:30 PM. */
private class HeaderFormats(private val locale: Locale, use24Hour: Boolean) {
    private val shortDateFormat = pattern("EEEdMMM")
    private val timeFormat = pattern(if (use24Hour) "Hm" else "hma")
    private val dayTimeFormat = pattern(if (use24Hour) "EEEHm" else "EEEhma")

    fun shortDate(date: LocalDate): String = shortDateFormat.format(date).capitalized()
    fun time(time: LocalDateTime): String = timeFormat.format(time)
    fun alarm(alarm: LocalDateTime, showsDay: Boolean): String =
        (if (showsDay) dayTimeFormat else timeFormat).format(alarm).capitalized()

    private fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }
}
