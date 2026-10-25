package ch.alpenlan.kalender.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract.CalendarAlerts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.alpenlan.kalender.MainActivity
import ch.alpenlan.kalender.R
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

/**
 * Zeigt Termin-Erinnerungen an. Der Android-Kalenderspeicher sendet EVENT_REMINDER,
 * sobald eine Erinnerung fällig ist – auf GrapheneOS gibt es sonst keine App, die sie anzeigt.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.EVENT_REMINDER") return
        val pending = goAsync()
        thread {
            try {
                handle(context.applicationContext)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(ctx: Context) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val enabled = runBlocking { SettingsStore(ctx).flow.first().notifications }
        if (!enabled) return
        ensureChannel(ctx)
        val now = System.currentTimeMillis()
        val proj = arrayOf(
            CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.TITLE, CalendarAlerts.BEGIN,
            CalendarAlerts.END, CalendarAlerts.ALL_DAY, CalendarAlerts.EVENT_LOCATION,
        )
        val sel = "${CalendarAlerts.STATE}=? AND ${CalendarAlerts.ALARM_TIME}<=?"
        val args = arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), (now + 60_000).toString())
        val cr = ctx.contentResolver
        val fired = mutableListOf<Long>()
        cr.query(CalendarAlerts.CONTENT_URI, proj, sel, args, "${CalendarAlerts.ALARM_TIME} ASC")?.use { c ->
            while (c.moveToNext()) {
                val alertId = c.getLong(0)
                val eventId = c.getLong(1)
                val title = c.getString(2)?.takeIf { it.isNotBlank() } ?: "Termin"
                val begin = c.getLong(3)
                val allDay = c.getInt(5) == 1
                val loc = c.getString(6)?.takeIf { it.isNotBlank() }
                val day = if (allDay) Dates.utcDate(begin) else Dates.localDate(begin)
                val whenText = if (allDay) "${Dates.wdShortDate(day)} · ganztägig" else "${Dates.wdShortDate(day)} · ${Dates.hm(Dates.localTime(begin))}"
                notify(ctx, eventId, begin, title, listOfNotNull(whenText, loc).joinToString(" · "))
                fired += alertId
            }
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            fired.forEach { id ->
                runCatching {
                    cr.update(CalendarAlerts.CONTENT_URI, ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED) },
                        "${CalendarAlerts._ID}=?", arrayOf(id.toString()))
                }
            }
        }
    }

    private fun notify(ctx: Context, eventId: Long, begin: Long, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
            putExtra(MainActivity.EXTRA_BEGIN, begin)
        }
        val pi = PendingIntent.getActivity(ctx, (eventId % Int.MAX_VALUE).toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFF4DA3FF.toInt())
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify((eventId xor begin).toInt(), n) }
    }

    companion object {
        const val CHANNEL = "termine"
        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Termin-Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Erinnerungen an Termine aus der Alpenlan Cloud"
                })
            }
        }
    }
}
