package ch.alpenlan.kalender.data

import java.time.LocalDate
import java.time.LocalTime

/** Ein Kalender aus dem Android-Kalenderspeicher (von DAVx⁵ befüllt). */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val color: Int,
    val accountName: String,
    val accountType: String,
    val writable: Boolean,
) {
    val isDavx: Boolean get() = accountType == DAVX_ACCOUNT_TYPE
}

/** Ein konkretes Vorkommen eines Termins (Serien sind aufgelöst). */
data class EventItem(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val color: Int,
    val recurring: Boolean,
    /** Erster und letzter Tag (inklusive), in lokaler Zeit (ganztägig: Kalendertag). */
    val startDay: LocalDate,
    val endDay: LocalDate,
    /** Minuten ab Mitternacht am Start- bzw. Endtag (nur zeitgebundene Termine). */
    val startMin: Int,
    val endMin: Int,
) {
    val multiDay: Boolean get() = endDay.isAfter(startDay)
    /** Im Stundenraster darstellbar: zeitgebunden und an einem einzigen Tag. */
    val timedSingleDay: Boolean get() = !allDay && !multiDay
    fun occursOn(d: LocalDate) = !d.isBefore(startDay) && !d.isAfter(endDay)
}

data class TaskList(
    val id: Long,
    val name: String,
    val color: Int,
    val accountName: String,
    val accountType: String,
) {
    /** Liste wird von DAVx⁵ mit Nextcloud synchronisiert (sonst nur auf dem Gerät). */
    val isSynced: Boolean get() = accountType == DAVX_ACCOUNT_TYPE
    val label: String get() = if (isSynced) name else "$name (nur Gerät)"
}

data class TaskItem(
    val id: Long,
    val listId: Long,
    val title: String,
    val description: String?,
    val done: Boolean,
    val highPriority: Boolean,
    val due: LocalDate?,
    val dueTime: LocalTime?,
    val parentId: Long?,
)

/** Eingabe aus dem Termin-Editor. Enddatum ist inklusive. */
data class EventDraft(
    val eventId: Long? = null,
    val calendarId: Long,
    val title: String,
    val location: String = "",
    val description: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime = LocalTime.of(18, 0),
    val endTime: LocalTime = LocalTime.of(19, 0),
    val recurring: Boolean = false,
    val reminderMinutes: Int = -1,
)

data class TaskDraft(
    val taskId: Long? = null,
    val listId: Long,
    val title: String,
    val description: String = "",
    val due: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val highPriority: Boolean = false,
    val parentId: Long? = null,
)

const val DAVX_ACCOUNT_TYPE = "bitfire.at.davdroid"
const val DAVX_PACKAGE = "at.bitfire.davdroid"
