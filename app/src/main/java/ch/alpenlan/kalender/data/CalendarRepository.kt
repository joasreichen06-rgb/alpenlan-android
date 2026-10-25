package ch.alpenlan.kalender.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Liest und schreibt Termine im Android-Kalenderspeicher.
 * DAVx⁵ synchronisiert diesen Speicher mit Nextcloud (CalDAV) – geänderte Einträge
 * werden als "dirty" markiert und beim nächsten Sync hochgeladen.
 */
class CalendarRepository(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    fun canRead() = granted(Manifest.permission.READ_CALENDAR)
    fun canWrite() = granted(Manifest.permission.WRITE_CALENDAR)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!canRead()) return emptyList()
        val out = mutableListOf<CalendarInfo>()
        val proj = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR,
            Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_ACCESS_LEVEL,
        )
        cr.query(Calendars.CONTENT_URI, proj, "${Calendars.SYNC_EVENTS}=1", null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}")?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "Kalender",
                    color = c.getInt(2),
                    accountName = c.getString(3) ?: "",
                    accountType = c.getString(4) ?: "",
                    writable = c.getInt(5) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        return out
    }

    /** Alle Vorkommen zwischen [from] (inkl.) und [toExcl] (exkl.). */
    fun instances(from: LocalDate, toExcl: LocalDate): List<EventItem> {
        if (!canRead()) return emptyList()
        // Einen Tag Puffer, weil ganztägige Termine in UTC gespeichert sind.
        val begin = Dates.startOfDayMillis(from.minusDays(1))
        val end = Dates.startOfDayMillis(toExcl.plusDays(1))
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(
            Instances._ID, Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE,
            Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_LOCATION,
            Instances.DESCRIPTION, Instances.DISPLAY_COLOR, Instances.RRULE, Instances.RDATE,
        )
        val out = mutableListOf<EventItem>()
        // Bewusst ohne VISIBLE-Filter: Das ist die Sichtbarkeit anderer Kalender-Apps (z.B. Samsung).
        // Was Alpenlan zeigt, steuern die eigenen Einstellungen (ausgeblendete Kalender).
        cr.query(uri, proj, null, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val b = c.getLong(4)
                var e = c.getLong(5)
                val allDay = c.getInt(6) == 1
                if (e < b) e = b
                val startDay: LocalDate
                val endDay: LocalDate
                var startMin = 0
                var endMin = 0
                if (allDay) {
                    startDay = Dates.utcDate(b)
                    endDay = if (e > b) Dates.utcDate(e - 1) else startDay
                } else {
                    startDay = Dates.localDate(b)
                    val endInstant = if (e > b) e - 1 else e
                    endDay = Dates.localDate(endInstant)
                    val st = Dates.localTime(b)
                    startMin = st.hour * 60 + st.minute
                    val et = Dates.localTime(e)
                    endMin = if (e > b && Dates.localDate(e) != endDay) 24 * 60 else et.hour * 60 + et.minute
                }
                if (endDay.isBefore(from) || !startDay.isBefore(toExcl)) continue
                out += EventItem(
                    instanceId = c.getLong(0),
                    eventId = c.getLong(1),
                    calendarId = c.getLong(2),
                    title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "(Ohne Titel)",
                    begin = b, end = e, allDay = allDay,
                    location = c.getString(7)?.takeIf { it.isNotBlank() },
                    description = c.getString(8)?.takeIf { it.isNotBlank() },
                    color = c.getInt(9),
                    recurring = !c.getString(10).isNullOrBlank() || !c.getString(11).isNullOrBlank(),
                    startDay = startDay, endDay = endDay, startMin = startMin, endMin = endMin,
                )
            }
        }
        return out
    }

    fun reminderMinutes(eventId: Long): Int {
        if (!canRead()) return -1
        cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC")?.use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return -1
    }

    private fun values(d: EventDraft, includeTimes: Boolean): ContentValues = ContentValues().apply {
        put(Events.TITLE, d.title)
        put(Events.EVENT_LOCATION, d.location)
        put(Events.DESCRIPTION, d.description)
        if (includeTimes) {
            if (d.allDay) {
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, Dates.utcMidnight(d.startDate))
                put(Events.DTEND, Dates.utcMidnight(d.endDate.plusDays(1)))
            } else {
                val zone = Dates.zone
                val start = d.startDate.atTime(d.startTime).atZone(zone).toInstant().toEpochMilli()
                var end = d.endDate.atTime(d.endTime).atZone(zone).toInstant().toEpochMilli()
                if (end <= start) end = start + 60 * 60_000L
                put(Events.ALL_DAY, 0)
                put(Events.EVENT_TIMEZONE, zone.id)
                put(Events.DTSTART, start)
                put(Events.DTEND, end)
            }
        }
    }

    fun insert(d: EventDraft): Long? {
        if (!canWrite()) return null
        val v = values(d, includeTimes = true).apply { put(Events.CALENDAR_ID, d.calendarId) }
        val uri = cr.insert(Events.CONTENT_URI, v) ?: return null
        val id = ContentUris.parseId(uri)
        setReminder(id, d.reminderMinutes)
        return id
    }

    /**
     * Ändert einen Termin. Bei Serien werden nur Titel, Ort und Notiz geändert,
     * damit die Wiederholungsregel unangetastet bleibt.
     * Ein Wechsel des Kalenders wird als Neu-Anlegen + Löschen umgesetzt.
     */
    fun update(d: EventDraft, oldCalendarId: Long): Long? {
        if (!canWrite()) return null
        val id = d.eventId ?: return insert(d)
        if (!d.recurring && d.calendarId != oldCalendarId) {
            val newId = insert(d.copy(eventId = null))
            if (newId != null) delete(id)
            return newId
        }
        cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values(d, includeTimes = !d.recurring), null, null)
        if (!d.recurring) setReminder(id, d.reminderMinutes)
        return id
    }

    fun delete(eventId: Long) {
        if (!canWrite()) return
        cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    private fun setReminder(eventId: Long, minutes: Int) {
        cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
        if (minutes < 0) return
        cr.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, eventId)
            put(Reminders.MINUTES, minutes)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
    }

    companion object {
        val OBSERVE_URI = CalendarContract.CONTENT_URI
        @Suppress("unused") private val UTC = ZoneOffset.UTC
    }
}
