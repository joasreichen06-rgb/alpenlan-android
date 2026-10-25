package ch.alpenlan.kalender.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId

/**
 * Zugriff auf Aufgaben über die OpenTasks-Schnittstelle.
 * Sowohl OpenTasks (org.dmfs.tasks) als auch Tasks.org (org.tasks.opentasks) bieten sie an;
 * DAVx⁵ synchronisiert sie mit Nextcloud Tasks (CalDAV VTODO).
 */
class TaskRepository(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    data class Provider(val authority: String, val label: String, val packageName: String, val readPermission: String, val writePermission: String)

    /** Welche Aufgaben-Apps sind installiert? */
    fun installedProviders(): List<Provider> = ALL.filter {
        ctx.packageManager.resolveContentProvider(it.authority, 0) != null
    }

    fun provider(preference: String): Provider? {
        val installed = installedProviders()
        return installed.firstOrNull { it.authority == preference } ?: installed.firstOrNull()
    }

    fun canRead(p: Provider) = granted(p.readPermission)
    fun canWrite(p: Provider) = granted(p.writePermission)
    private fun granted(perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun listsUri(p: Provider) = Uri.parse("content://${p.authority}/tasklists")
    private fun tasksUri(p: Provider) = Uri.parse("content://${p.authority}/tasks")
    fun observeUri(p: Provider): Uri = Uri.parse("content://${p.authority}")

    fun lists(p: Provider): List<TaskList> {
        if (!canRead(p)) return emptyList()
        val out = mutableListOf<TaskList>()
        runCatching {
            cr.query(listsUri(p), arrayOf("_id", "list_name", "list_color", "account_name", "account_type"), null, null, "list_name")?.use { c ->
                while (c.moveToNext()) {
                    out += TaskList(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Liste",
                        color = if (c.isNull(2)) 0xFF4DA3FF.toInt() else c.getInt(2),
                        accountName = c.getString(3) ?: "",
                        accountType = c.getString(4) ?: "",
                    )
                }
            }
        }
        return out
    }

    fun tasks(p: Provider): List<TaskItem> {
        if (!canRead(p)) return emptyList()
        val proj = arrayOf("_id", "list_id", "title", "description", "status", "priority", "due", "is_allday", "tz", "parent_id")
        val out = mutableListOf<TaskItem>()
        val query = { sel: String? ->
            cr.query(tasksUri(p), proj, sel, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val status = if (c.isNull(4)) 0 else c.getInt(4)
                    val prio = if (c.isNull(5)) 0 else c.getInt(5)
                    var dueDate: java.time.LocalDate? = null
                    var dueTime: java.time.LocalTime? = null
                    if (!c.isNull(6)) {
                        val millis = c.getLong(6)
                        val allDay = !c.isNull(7) && c.getInt(7) == 1
                        if (allDay) {
                            // Laut OpenTasks UTC-Mitternacht; manche Apps speichern lokale Mitternacht.
                            dueDate = if (millis % 86_400_000L == 0L) Dates.utcDate(millis) else Dates.localDate(millis)
                        } else {
                            val zone = c.getString(8)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: Dates.zone
                            val z = Instant.ofEpochMilli(millis).atZone(zone).withZoneSameInstant(Dates.zone)
                            dueDate = z.toLocalDate()
                            dueTime = z.toLocalTime()
                        }
                    }
                    out += TaskItem(
                        id = c.getLong(0),
                        listId = c.getLong(1),
                        title = c.getString(2)?.takeIf { it.isNotBlank() } ?: "(Ohne Titel)",
                        description = c.getString(3)?.takeIf { it.isNotBlank() },
                        done = status == STATUS_COMPLETED || status == STATUS_CANCELLED,
                        highPriority = prio in 1..4,
                        due = dueDate,
                        dueTime = dueTime,
                        parentId = if (c.isNull(9)) null else c.getLong(9),
                    )
                }
            }
        }
        // Gelöschte (noch nicht synchronisierte) Aufgaben ausblenden, falls die Spalte existiert.
        if (runCatching { query("_deleted=0") }.isFailure) {
            out.clear()
            runCatching { query(null) }
        }
        return out
    }

    fun setDone(p: Provider, id: Long, done: Boolean) {
        if (!canWrite(p)) return
        cr.update(ContentUris.withAppendedId(tasksUri(p), id), ContentValues().apply {
            put("status", if (done) STATUS_COMPLETED else STATUS_NEEDS_ACTION)
            if (!done) put("percent_complete", 0)
        }, null, null)
    }

    private fun values(d: TaskDraft): ContentValues = ContentValues().apply {
        put("title", d.title)
        put("description", d.description)
        put("priority", if (d.highPriority) 1 else 0)
        if (d.due != null) {
            put("due", Dates.utcMidnight(d.due))
            put("is_allday", 1)
            putNull("tz")
        } else {
            putNull("due")
        }
    }

    fun insert(p: Provider, d: TaskDraft): Long? {
        if (!canWrite(p)) return null
        val v = values(d).apply {
            put("list_id", d.listId)
            put("status", STATUS_NEEDS_ACTION)
            if (d.parentId != null) put("parent_id", d.parentId)
        }
        return cr.insert(tasksUri(p), v)?.let { ContentUris.parseId(it) }
    }

    fun update(p: Provider, d: TaskDraft, oldListId: Long): Long? {
        if (!canWrite(p)) return null
        val id = d.taskId ?: return insert(p, d)
        val v = values(d)
        if (d.listId != oldListId) v.put("list_id", d.listId)
        cr.update(ContentUris.withAppendedId(tasksUri(p), id), v, null, null)
        return id
    }

    fun delete(p: Provider, id: Long) {
        if (!canWrite(p)) return
        cr.delete(ContentUris.withAppendedId(tasksUri(p), id), null, null)
    }

    companion object {
        const val STATUS_NEEDS_ACTION = 0
        const val STATUS_COMPLETED = 2
        const val STATUS_CANCELLED = 3

        val OPENTASKS = Provider("org.dmfs.tasks", "OpenTasks", "org.dmfs.tasks", "org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS")
        val TASKS_ORG = Provider("org.tasks.opentasks", "Tasks.org", "org.tasks", "org.tasks.permission.READ_TASKS", "org.tasks.permission.WRITE_TASKS")
        val ALL = listOf(TASKS_ORG, OPENTASKS)
    }
}
