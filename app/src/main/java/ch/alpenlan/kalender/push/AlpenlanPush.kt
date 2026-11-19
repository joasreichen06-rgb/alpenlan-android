package ch.alpenlan.kalender.push

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.alpenlan.kalender.R
import ch.alpenlan.kalender.home.Auth
import ch.alpenlan.kalender.home.ControlApi
import ch.alpenlan.kalender.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Push-Nachrichten von Alpenlan Control (Stromausfall, Festplatten) über UnifiedPush.
 * Zustellung übernimmt eine Verteiler-App auf dem Handy (z. B. ntfy aus F-Droid) – ohne Google-Dienste.
 */
object PushSetup {
    private const val PREFS = "alpenlan_push"
    const val CHANNEL = "alarme"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** null = noch nicht geprüft, false = keine Verteiler-App installiert, true = angemeldet */
    fun status(ctx: Context): Boolean? = prefs(ctx).let { if (it.contains("ok")) it.getBoolean("ok", false) else null }
    fun registered(ctx: Context) = prefs(ctx).getString("sent", null) != null

    fun start(activity: Activity) {
        ensureChannel(activity)
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { ok ->
            prefs(activity).edit().putBoolean("ok", ok).apply()
            if (ok) UnifiedPush.register(activity)
        }
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alpenlan Alarme", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Stromausfall, USV und Festplatten im Server"
                enableVibration(true)
            })
        }
    }

    internal fun newEndpoint(ctx: Context, url: String) {
        prefs(ctx).edit().putString("endpoint", url).apply()
        sync(ctx)
    }

    /** Adresse an Control melden, falls noch nicht geschehen (braucht Anmeldung). */
    fun sync(ctx: Context) {
        val p = prefs(ctx)
        val url = p.getString("endpoint", null) ?: return
        if (p.getString("sent", null) == url) return
        val app = ctx.applicationContext
        scope.launch {
            val auth = Auth(app)
            if (!auth.loggedIn) return@launch
            if (runCatching { ControlApi(auth).registerPush(url) }.getOrDefault(false)) p.edit().putString("sent", url).apply()
        }
    }

    internal fun gone(ctx: Context) = prefs(ctx).edit().remove("endpoint").remove("sent").apply()

    internal fun show(ctx: Context, raw: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(ctx)
        val j = runCatching { JSONObject(String(raw)) }.getOrNull()
        val title = j?.optString("title")?.ifBlank { null } ?: "Alpenlan"
        val body = j?.optString("body") ?: String(raw)
        val tag = j?.optString("tag")?.ifBlank { null } ?: "alpenlan"
        val urgent = j?.optBoolean("urgent") == true
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Gleiche Art (z. B. USV) ersetzt die vorherige Meldung, statt sich zu stapeln
        val group = if (tag.startsWith("ups")) "ups" else tag
        NotificationManagerCompat.from(ctx).notify(group, 4711, n)
    }
}

class AlpenlanPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) = PushSetup.newEndpoint(this, endpoint.url)
    override fun onMessage(message: PushMessage, instance: String) = PushSetup.show(this, message.content)
    override fun onRegistrationFailed(reason: FailedReason, instance: String) {}
    override fun onUnregistered(instance: String) = PushSetup.gone(this)
}
