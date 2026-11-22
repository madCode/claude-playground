package spotter.app.today

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import spotter.coach.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Calendar entries for the days asked (two days back to tomorrow, in all): titles and starts, nothing else. */
class Calendar(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    fun allowed(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * The entries on [today] and the day after; null when Spotter may not read the calendar or the
     * read failed. A failed read is never an empty day: that would say "no class booked".
     *
     * Instances, not Events, so a recurring class is found on each day. All-day entries are kept
     * at UTC midnight and timed ones in real time, so the query reaches half a day either side
     * and each kind is kept by the date it belongs to.
     */
    fun events(today: LocalDate): List<CalendarEvent>? {
        if (!allowed()) return null
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli() - HALF_DAY
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() + HALF_DAY
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        // Visible calendars only, and not a cancelled class or a declined invitation.
        val where = "${CalendarContract.Instances.VISIBLE} = 1" +
            " AND ${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}" +
            " AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        return runCatching {
            context.contentResolver.query(uri, columns, where, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val title = c.getString(0) ?: continue
                        add(event(title, c.getLong(1), c.getInt(2) != 0, zone))
                    }
                }
            }
        }.getOrNull()
    }

    companion object {
        private const val HALF_DAY = 12L * 60 * 60 * 1000

        /** An entry by the day it belongs to: an all-day one by its UTC date, a timed one by its date here. */
        internal fun event(title: String, begin: Long, allDay: Boolean, zone: ZoneId): CalendarEvent {
            val instant = Instant.ofEpochMilli(begin)
            return if (allDay) {
                CalendarEvent(title, instant.atOffset(ZoneOffset.UTC).toLocalDate(), null)
            } else {
                val at = instant.atZone(zone).toLocalDateTime()
                CalendarEvent(title, at.toLocalDate(), at)
            }
        }
    }
}
