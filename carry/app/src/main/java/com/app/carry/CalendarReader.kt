package com.app.carry

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.app.carry.brain.ClassCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The owner's events on one day, as [ClassCalendar] needs them: title and start, nothing else.
 *
 * Instances, not Events, so a recurring class is found on each day it falls. All-day entries
 * are stored at UTC midnight while timed ones are in real time, so the query reaches half a
 * day either side and each kind is then kept by the date it belongs to: an all-day entry by
 * its UTC date, a timed one by its date here. The laptop's first calendar query matched only
 * all-day entries and was wrong for weeks without a sound, so both kinds are tested.
 */
interface CalendarSource {
    /** Whether the owner has let Carry read the calendar at all. */
    fun allowed(): Boolean

    /** The day's events in start order; null when the calendar is allowed but couldn't be read. */
    fun eventsOn(date: LocalDate): List<ClassCalendar.Event>?
}

class CalendarReader(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) : CalendarSource {
    override fun allowed() = granted(context)

    override fun eventsOn(date: LocalDate): List<ClassCalendar.Event>? {
        if (!granted(context)) return null
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli() - HALF_DAY_MS
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + HALF_DAY_MS
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        // Visible calendars only: the owner's own, not the hidden or shared ones a laptop feed
        // list would never have held.
        return runCatching {
            context.contentResolver.query(uri, columns, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val title = c.getString(0) ?: continue
                        keep(title, c.getLong(1), c.getInt(2) != 0, date, zone)?.let(::add)
                    }
                }
            }
        // A provider that answers nothing is a failed read, never an empty day: that is the
        // mistake that hid the laptop's calendar bug for weeks.
        }.getOrNull()
    }

    companion object {
        private const val HALF_DAY_MS = 12 * 60 * 60 * 1000L

        fun granted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

        /** One instance, if it belongs to [date]: an all-day one by its UTC date, a timed one by its local date. */
        internal fun keep(title: String, beginMs: Long, allDay: Boolean, date: LocalDate, zone: ZoneId): ClassCalendar.Event? {
            val instant = Instant.ofEpochMilli(beginMs)
            return if (allDay) {
                if (instant.atZone(ZoneOffset.UTC).toLocalDate() == date) ClassCalendar.Event(title, null) else null
            } else {
                val start = LocalDateTime.ofInstant(instant, zone)
                if (start.toLocalDate() == date) ClassCalendar.Event(title, start) else null
            }
        }

        fun asked(context: Context): Boolean =
            context.getSharedPreferences("carry", Context.MODE_PRIVATE).getBoolean("asked_calendar", false)

        fun markAsked(context: Context) {
            context.getSharedPreferences("carry", Context.MODE_PRIVATE).edit().putBoolean("asked_calendar", true).apply()
        }
    }
}
