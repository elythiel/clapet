package xyz.martelle.clapet.header

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the header (date, next alarm, upcoming events) and keeps it current: on the system's
 * time and alarm broadcasts, on any calendar change, and at midnight or when a shown event starts
 * or ends. Listens only while collected: the home collects it while visible and re-reads on return.
 */
class HeaderSource(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun header(): Flow<HeaderInfo> = changes()
        .onStart { emit(Unit) }
        .transformLatest {
            while (true) {
                val header = read()
                emit(header)
                // A little past the boundary, so the next read sees the event over or the new day.
                val wait = Duration.between(LocalDateTime.now(), header.refreshAt).toMillis().coerceAtLeast(0)
                delay(wait + RefreshSlackMillis)
            }
        }
        .flowOn(Dispatchers.IO)

    private fun read(): HeaderInfo {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val events = if (hasCalendarPermission()) Upcoming.pick(queryInstances(now, zone), now, zone) else emptyList()
        val alarm = alarmManager.nextAlarmClock?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerTime), zone) }
        return HeaderInfo(
            today = now.toLocalDate(),
            nextAlarm = alarm,
            alarmShowsDay = alarm != null && Upcoming.alarmNeedsDay(alarm, now),
            events = events,
            use24HourFormat = DateFormat.is24HourFormat(appContext),
            refreshAt = Upcoming.refreshAt(events, alarm, now),
        )
    }

    /** Signals, conflated: the reader only needs to know that something changed. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            // Clock set, time zone or 12/24 h format changed; the next alarm set, changed or rung.
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        // System broadcasts only: nothing from other apps needs to reach this receiver.
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // Events added, changed or synced. Watching needs the permission: without it, nothing to watch.
        val observing = hasCalendarPermission() && try {
            appContext.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            true
        } catch (e: SecurityException) {
            false
        }
        awaitClose {
            appContext.unregisterReceiver(receiver)
            if (observing) appContext.contentResolver.unregisterContentObserver(observer)
        }
    }.conflate()

    private fun queryInstances(now: LocalDateTime, zone: ZoneId): List<CalendarInstance> {
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        // A day back: today's all-day events are UTC midnights, possibly before now west of Greenwich.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, nowMillis - Duration.ofDays(1).toMillis())
            ContentUris.appendId(it, nowMillis + Upcoming.WINDOW.toMillis())
        }.build()
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        return try {
            appContext.contentResolver.query(uri, Projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                CalendarInstance(
                                    eventId = cursor.getLong(0),
                                    title = cursor.getString(1).orEmpty(),
                                    beginMillis = cursor.getLong(2),
                                    endMillis = cursor.getLong(3),
                                    allDay = cursor.getInt(4) != 0,
                                    color = cursor.getInt(5),
                                ),
                            )
                        }
                    }
                }
                .orEmpty()
        } catch (e: SecurityException) {
            // Revoked between the check and the query.
            emptyList()
        }
    }

    private fun hasCalendarPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val RefreshSlackMillis = 1_000L

        // Read by index in queryInstances: keep the order.
        val Projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR,
        )
    }
}

/** Opens this occurrence of [event] in the calendar app. */
fun openEventIntent(event: UpcomingEvent): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
