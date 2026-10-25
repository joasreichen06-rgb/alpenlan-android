package ch.alpenlan.kalender.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    val WD_LONG = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    val WD_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val MONTHS = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    val MONTHS_SHORT = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez.")
    val MONTHS_3 = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

    fun wdLong(d: LocalDate) = WD_LONG[d.dayOfWeek.value - 1]
    fun wdShort(d: LocalDate) = WD_SHORT[d.dayOfWeek.value - 1]
    fun short(d: LocalDate) = "${d.dayOfMonth}. ${MONTHS_SHORT[d.monthValue - 1]}"
    fun wdShortDate(d: LocalDate) = "${wdShort(d)}, ${short(d)}"
    fun longDate(d: LocalDate) = "${wdLong(d)}, ${d.dayOfMonth}. ${MONTHS[d.monthValue - 1]}"

    fun weekStart(d: LocalDate, first: DayOfWeek): LocalDate = d.with(TemporalAdjusters.previousOrSame(first))
    fun isoWeek(d: LocalDate): Int = d.get(WeekFields.ISO.weekOfWeekBasedYear())

    /** Spaltenreihenfolge der Wochentage ab dem gewählten ersten Tag. */
    fun weekdayOrder(first: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { first.plus(it) }

    fun startOfDayMillis(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun utcMidnight(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun localTime(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
    fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
}
