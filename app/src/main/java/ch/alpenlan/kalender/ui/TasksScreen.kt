package ch.alpenlan.kalender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.TaskItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun dueLabel(t: TaskItem, today: LocalDate): String {
    val due = t.due ?: return "Ohne Datum"
    val d = ChronoUnit.DAYS.between(today, due)
    val time = t.dueTime?.let { " · ${Dates.hm(it)}" } ?: ""
    return when {
        d == 0L -> "Heute$time"
        d == 1L -> "Morgen$time"
        d == -1L -> "Gestern$time"
        d < 0 -> "Überfällig · ${Dates.short(due)}"
        else -> Dates.wdShortDate(due) + time
    }
}

fun dueColors(t: TaskItem, today: LocalDate): Pair<Color, Color> {
    val due = t.due
    if (due == null || t.done) return A.lineSoft to A.muted
    return when {
        due.isBefore(today) -> Color(0x2EE02204) to A.redSoft
        due == today -> A.blue.copy(alpha = 0.18f) to A.blueLight
        else -> A.lineSoft to Color(0xFFB8C3D2)
    }
}

@Composable
fun DueChip(t: TaskItem, today: LocalDate, big: Boolean = false) {
    val (bg, fg) = dueColors(t, today)
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = if (big) 10.dp else 7.dp, vertical = if (big) 4.dp else 2.dp)
    ) {
        Text(dueLabel(t, today), style = MonoLabel.copy(fontSize = if (big) 11.sp else 10.sp, letterSpacing = 0.4.sp, color = fg))
    }
}

private fun matches(f: TaskFilter, t: TaskItem, today: LocalDate): Boolean = when (f) {
    TaskFilter.TODAY -> !t.done && t.due != null && !t.due.isAfter(today)
    TaskFilter.PLANNED -> !t.done && t.due != null && t.due.isAfter(today)
    TaskFilter.ALL -> !t.done
    TaskFilter.DONE -> t.done
}

@Composable
fun TasksScreen(s: UiState, vm: AppViewModel) {
    if (s.provider == null) {
        EmptyHint(
            "Keine Aufgaben-App gefunden",
            "Installiere Tasks.org oder OpenTasks (F-Droid) und aktiviere in DAVx⁵ die Aufgaben-Synchronisation.",
        ) { vm.setScreen(Screen.SETUP) }
        return
    }
    if (!s.taskPermission) {
        EmptyHint("Zugriff auf Aufgaben fehlt", "Erlaube Alpenlan den Zugriff auf ${s.provider.label}.") { vm.setScreen(Screen.SETUP) }
        return
    }
    val ids = s.liveTasks.map { it.id }.toSet()
    val top = s.liveTasks.filter { it.parentId == null || it.parentId !in ids }
    val counts = TaskFilter.entries.associateWith { f -> top.count { matches(f, it, s.today) } }
    val filtered = top.filter { matches(s.taskFilter, it, s.today) }
        .sortedWith(compareBy<TaskItem>({ !it.highPriority }, { it.due ?: LocalDate.MAX }, { it.title }))
    val groups = s.lists.map { l -> l to filtered.filter { it.listId == l.id } }.filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TaskFilter.entries.forEach { f ->
                val on = f == s.taskFilter
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .then(if (on) Modifier.background(A.blue.copy(alpha = 0.16f)).border(1.dp, A.blue, RoundedCornerShape(18.dp)) else Modifier.border(1.dp, A.line2, RoundedCornerShape(18.dp)))
                        .clickable(role = Role.Tab) { vm.setTaskFilter(f) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(f.label, color = if (on) A.fg else A.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${counts[f] ?: 0}", style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = if (on) A.fg else A.muted))
                }
            }
        }
        if (groups.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Alles erledigt", color = A.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Hier ist gerade nichts offen.", color = A.muted, fontSize = 13.sp)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(groups, key = { it.first.id }) { (list, tasks) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(A.panel.copy(alpha = 0.92f))
                        .border(1.dp, A.line, RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(dotColor(list.color), 10.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(list.label, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Mono(if (tasks.size == 1) "1 Aufgabe" else "${tasks.size} Aufgaben", size = 11, spacing = 0.2)
                    }
                    Spacer(Modifier.height(6.dp))
                    tasks.forEach { t ->
                        HorizontalDivider(color = A.lineSoft)
                        TaskRow(s, vm, t)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(s: UiState, vm: AppViewModel, t: TaskItem) {
    val subs = s.liveTasks.filter { it.parentId == t.id }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp)) {
            CheckCircle(t.done, { vm.toggleTask(t) }, label = if (t.done) "Als offen markieren" else "Erledigen")
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable { vm.openSheet(Sheet.Task(t.id)) }
                .padding(top = 13.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TaskTitle(t.title, t.done)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DueChip(t, s.today)
                    if (subs.isNotEmpty()) Mono("${subs.count { it.done }} von ${subs.size} erledigt", size = 10, spacing = 0.2)
                }
            }
            if (t.highPriority && !t.done) {
                Text("!!", color = A.warn, style = MonoLabel.copy(fontSize = 12.sp, color = A.warn))
            }
        }
    }
}

@Composable
fun EmptyHint(title: String, text: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = A.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = A.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(A.blue)
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Einrichtung öffnen", color = A.blueInk, fontWeight = FontWeight.Bold) }
    }
}
