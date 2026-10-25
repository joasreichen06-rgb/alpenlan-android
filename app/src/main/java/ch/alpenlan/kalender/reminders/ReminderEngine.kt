package ch.alpenlan.kalender.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.alpenlan.kalender.MainActivity
import ch.alpenlan.kalender.R
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Eigene Termin-Erinnerungen: Die App liest die Erinnerungen (VALARM, von DAVx⁵ synchronisiert)
 * selbst aus dem Kalenderspeicher und stellt einen Wecker auf die nächste. So funktioniert es
 * auch, wenn das System kein EVENT_REMINDER sendet (z.B. Samsung) oder keine Kalender-App
 * vorinstalliert ist (GrapheneOS).
 */
object ReminderEngine {
    const val CHANNEL = "termine"
    private const val PREFS = "alpenlan_reminders"
    private const val LOOKBACK = 2 * 60 * 60_000L      // verpasste Erinnerungen bis 2 h nachholen
    private const val RECHECK = 60 * 60_000L            // spätestens stündlich neu prüfen
    private const val ACTION = "ch.alpenlan.kalender.REMINDER_ALARM"

    @Volatile private var hidden: Set<Long> = emptySet()

    private data class Due(val key: String, val eventId: Long, val begin: Long, val title: String, val text: String, val alarm: Long)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Termin-Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Erinnerungen an Termine aus der Alpenlan Cloud"
                enableVibration(true)
            })
        }
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Fällige Erinnerungen anzeigen und den nächsten Wecker stellen. Läuft synchron (Hintergrund-Thread). */
    @Synchronized
    fun run(ctx: Context) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val settings = runCatching { runBlocking { SettingsStore(ctx).flow.first() } }.getOrNull()
        val enabled = settings?.notifications ?: true
        hidden = settings?.hiddenCalendars ?: emptySet()
        val now = System.currentTimeMillis()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fired = prefs.getStringSet("fired", emptySet())!!.toMutableSet()

        val all = collect(ctx, now - 26 * 60 * 60_000L, now + 8L * 24 * 60 * 60_000L)
        if (enabled) {
            ensureChannel(ctx)
            all.filter { it.alarm <= now + 30_000 && it.alarm > now - LOOKBACK && it.key !in fired }.forEach { d ->
                notify(ctx, d)
                fired += d.key
            }
        }
        // Alte Einträge (älter als 2 Tage) aufräumen
        val keep = fired.filter { k -> k.substringAfterLast('|').toLongOrNull()?.let { it > now - 2 * 24 * 60 * 60_000L } ?: false }.toSet()
        prefs.edit().putStringSet("fired", keep).apply()

        val next = if (enabled) all.filter { it.alarm > now + 30_000 && it.key !in keep }.minOfOrNull { it.alarm } else null
        schedule(ctx, next, now)
    }

    private fun collect(ctx: Context, from: Long, to: Long): List<Due> {
        val cr = ctx.contentResolver
        data class Inst(val eventId: Long, val begin: Long, val end: Long, val title: String, val allDay: Boolean, val loc: String?)
        val inst = mutableListOf<Inst>()
        val uri = Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        runCatching {
            cr.query(uri, arrayOf(Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE, Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.CALENDAR_ID),
                "${Instances.HAS_ALARM}=1", null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(6) in hidden) continue
                    inst += Inst(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3)?.takeIf { it.isNotBlank() } ?: "Termin", c.getInt(4) == 1, c.getString(5)?.takeIf { it.isNotBlank() })
                }
            }
        }
        if (inst.isEmpty()) return emptyList()
        val minutes = HashMap<Long, MutableList<Int>>()
        inst.map { it.eventId }.distinct().chunked(400).forEach { ids ->
            runCatching {
                cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD),
                    "${Reminders.EVENT_ID} IN (${ids.joinToString(",")})", null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val method = c.getInt(2)
                        if (method == Reminders.METHOD_EMAIL || method == Reminders.METHOD_SMS) continue
                        minutes.getOrPut(c.getLong(0)) { mutableListOf() } += c.getInt(1).let { if (it < 0) 15 else it }
                    }
                }
            }
        }
        val out = mutableListOf<Due>()
        inst.forEach { i ->
            // Ganztägige Termine: Beginn ist UTC-Mitternacht → lokale Mitternacht des Tages
            val start = if (i.allDay) Dates.startOfDayMillis(Dates.utcDate(i.begin)) else i.begin
            val day = if (i.allDay) Dates.utcDate(i.begin) else Dates.localDate(i.begin)
            val whenText = if (i.allDay) "${Dates.wdShortDate(day)} · ganztägig" else "${Dates.wdShortDate(day)} · ${Dates.hm(Dates.localTime(i.begin))}"
            val text = listOfNotNull(whenText, i.loc).joinToString(" · ")
            minutes[i.eventId].orEmpty().distinct().forEach { m ->
                val alarm = start - m * 60_000L
                out += Due("${i.eventId}|${i.begin}|$m|$alarm", i.eventId, i.begin, i.title, text, alarm)
            }
        }
        return out
    }

    private fun notify(ctx: Context, d: Due) {
        if (!canNotify(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, d.eventId)
            putExtra(MainActivity.EXTRA_BEGIN, d.begin)
        }
        val pi = PendingIntent.getActivity(ctx, d.key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(d.title)
            .setContentText(d.text)
            .setColor(0xFF4DA3FF.toInt())
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(d.begin)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(("${d.eventId}|${d.begin}").hashCode(), n) }
    }

    private fun schedule(ctx: Context, next: Long?, now: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        val recheck = now + RECHECK
        if (next != null && next <= recheck) {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recheck, pi)
        }
    }

    /** Aus der App heraus: im Hintergrund neu planen. */
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        Thread { runCatching { run(app) } }.start()
    }

    /** Test-Benachrichtigung aus den Einstellungen. */
    fun test(ctx: Context) {
        ensureChannel(ctx)
        val now = System.currentTimeMillis()
        notify(ctx, Due("test|$now", 0, now, "Alpenlan Kalender", "So sehen Termin-Erinnerungen aus.", now))
    }
}
