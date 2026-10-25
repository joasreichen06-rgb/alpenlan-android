package ch.alpenlan.kalender.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract

/**
 * Stösst die Synchronisation von DAVx⁵ an. Die eigentliche Arbeit (CalDAV, Konflikte,
 * Login über Nextcloud/Authentik) macht DAVx⁵ selbst.
 */
object SyncHelper {
    fun isDavxInstalled(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(DAVX_PACKAGE, 0) }.isSuccess

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    /** Synchronisiert alle DAVx⁵-Konten, die Kalender oder Aufgabenlisten liefern. */
    fun requestSync(accounts: Collection<Pair<String, String>>, taskAuthority: String?): Boolean {
        var ok = false
        accounts.distinct().filter { it.first.isNotBlank() && it.second.isNotBlank() }.forEach { (name, type) ->
            val account = Account(name, type)
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            runCatching { ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras); ok = true }
            if (taskAuthority != null) runCatching { ContentResolver.requestSync(account, taskAuthority, extras) }
        }
        return ok
    }

    fun isSyncing(accounts: Collection<Pair<String, String>>, taskAuthority: String?): Boolean =
        accounts.distinct().any { (name, type) ->
            val a = Account(name, type)
            runCatching {
                ContentResolver.isSyncActive(a, CalendarContract.AUTHORITY) ||
                    ContentResolver.isSyncPending(a, CalendarContract.AUTHORITY) ||
                    (taskAuthority != null && ContentResolver.isSyncActive(a, taskAuthority))
            }.getOrDefault(false)
        }

    fun openApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    const val FDROID_DAVX = "https://f-droid.org/packages/at.bitfire.davdroid/"
    const val FDROID_TASKS_ORG = "https://f-droid.org/packages/org.tasks/"
    const val FDROID_OPENTASKS = "https://f-droid.org/packages/org.dmfs.tasks/"
}
