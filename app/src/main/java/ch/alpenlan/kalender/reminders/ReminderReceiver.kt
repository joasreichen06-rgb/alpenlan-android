package ch.alpenlan.kalender.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Gemeinsame Logik für alle Empfänger: kurz im Hintergrund prüfen und neu planen. */
private fun BroadcastReceiver.checkReminders(context: Context) {
    val pending = goAsync()
    val app = context.applicationContext
    thread {
        try { ReminderEngine.run(app) } catch (_: Throwable) {} finally { pending.finish() }
    }
}

/** System meldet eine fällige Erinnerung (falls es das tut) – wir prüfen dann selbst. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = checkReminders(context)

    companion object {
        fun ensureChannel(ctx: Context) = ReminderEngine.ensureChannel(ctx)
    }
}

/** Unser eigener Wecker (nächste Erinnerung oder stündliche Kontrolle). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = checkReminders(context)
}

/** Nach Neustart, Update, Zeit-/Zeitzonenwechsel und Kalenderänderungen neu planen. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = checkReminders(context)
}
