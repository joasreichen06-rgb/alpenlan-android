@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ch.alpenlan.kalender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.data.CalView
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.EventItem
import ch.alpenlan.kalender.data.TaskItem
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun Sheets(s: UiState, vm: AppViewModel) {
    val sheet = s.sheet ?: return
    val full = sheet is Sheet.EditEvent || sheet is Sheet.EditTask
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = full || sheet is Sheet.Task || sheet is Sheet.Event)
    ModalBottomSheet(
        onDismissRequest = { vm.closeSheet() },
        sheetState = state,
        containerColor = A.panel,
        contentColor = A.fg,
        scrimColor = Color(0x9E02050A),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp).clip(CircleShape).background(Color(0xFF2B3B55))) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (sheet) {
                is Sheet.Day -> DaySheet(s, vm, sheet.date)
                is Sheet.Event -> EventDetail(s, vm, sheet.item)
                is Sheet.Task -> s.liveTasks.firstOrNull { it.id == sheet.id }?.let { TaskDetail(s, vm, it) }
                is Sheet.EditEvent -> EventEditor(s, vm, sheet)
                is Sheet.EditTask -> TaskEditor(s, vm, sheet)
                Sheet.Visibility -> VisibilitySheet(s, vm)
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, sub: String? = null, onClose: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = A.fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Mono(sub, size = 11, spacing = 0.4)
        }
        extra()
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Schliessen", tint = A.muted) }
    }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier = Modifier, color: Color = A.blue, textColor: Color = A.fg, fill: Color = A.blue.copy(alpha = 0.12f), onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

// ------------------------------------------------------------------ Tag

@Composable
private fun DaySheet(s: UiState, vm: AppViewModel, date: LocalDate) {
    val evs = s.visibleEvents.filter { it.occursOn(date) }
        .sortedWith(compareBy<EventItem>({ if (it.allDay || it.multiDay) 0 else 1 }, { it.startMin }))
    val tasks = s.calendarTasks.filter { it.due == date }.sortedWith(compareBy({ it.done }, { it.dueTime ?: LocalTime.MAX }, { !it.highPriority }))
    val open = tasks.count { !it.done }
    SheetHeader(
        Dates.longDate(date),
        (if (evs.size == 1) "1 Termin" else "${evs.size} Termine") + if (s.settings.tasksInCalendar) " · $open Aufgaben offen" else "",
        onClose = { vm.closeSheet() },
    ) {
        Box(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(A.blue.copy(alpha = 0.08f))
                .border(1.dp, Color(0xFF2B4466), RoundedCornerShape(18.dp))
                .clickable(role = Role.Button) { vm.closeSheet(); vm.select(date, mode = CalView.DAY) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Tagesansicht", color = A.blueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
    if (evs.isEmpty()) Text("Keine Termine", color = A.muted, fontSize = 14.sp)
    evs.forEach { e ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(A.panel2)
                .border(1.dp, A.line, RoundedCornerShape(12.dp))
                .clickable { vm.openSheet(Sheet.Event(e)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(dotColor(e.color), 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val cal = s.calendar(e.calendarId)?.name ?: ""
                val range = if (e.multiDay) " · ${Dates.short(e.startDay)} – ${Dates.short(e.endDay)}" else ""
                Text(cal + range + (e.location?.let { " · $it" } ?: ""), color = A.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (e.allDay) "Ganztägig" else Dates.hm(e.startMin),
                style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = A.fg2),
            )
        }
    }
    if (s.settings.tasksInCalendar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mono("Aufgaben", color = A.blue, modifier = Modifier.weight(1f))
            if (tasks.isNotEmpty()) Mono("$open offen", size = 11, spacing = 0.2)
        }
        if (tasks.isEmpty()) Text("Keine Aufgaben fällig", color = A.muted, fontSize = 14.sp)
        Column {
            tasks.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(t.done, { vm.toggleTask(t) }, label = if (t.done) "Als offen markieren" else "Erledigen")
                    Row(
                        Modifier.weight(1f).heightIn(min = 46.dp).clickable { vm.openSheet(Sheet.Task(t.id)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TaskTitle(t.title, t.done, modifier = Modifier.weight(1f))
                        Dot(dotColor(s.listColor(t.listId)), 6.dp)
                        Spacer(Modifier.width(5.dp))
                        Mono(s.list(t.listId)?.name ?: "", size = 10, spacing = 0.4)
                    }
                }
                HorizontalDivider(color = A.line)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlineButton("+ Termin", Modifier.weight(1f), color = A.line2, fill = Color.Transparent) {
            vm.newEventDraft(date)?.let { vm.openSheet(it) }
        }
        if (s.provider != null && s.lists.isNotEmpty()) {
            OutlineButton("+ Aufgabe", Modifier.weight(1f), color = A.line2, fill = Color.Transparent) {
                vm.newTaskDraft(date)?.let { vm.openSheet(it) }
            }
        }
    }
}

// ------------------------------------------------------------------ Termin

private fun reminderText(m: Int) = when {
    m < 0 -> "Keine Erinnerung"
    m == 0 -> "Zum Beginn"
    m < 60 -> "$m Min. vorher"
    m % 1440 == 0 -> "${m / 1440} Tag${if (m / 1440 > 1) "e" else ""} vorher"
    m % 60 == 0 -> "${m / 60} Std. vorher"
    else -> "$m Min. vorher"
}

@Composable
private fun EventDetail(s: UiState, vm: AppViewModel, e: EventItem) {
    val cal = s.calendar(e.calendarId)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(dotColor(e.color), 10.dp)
        Spacer(Modifier.width(8.dp))
        Mono(cal?.name ?: "Kalender", modifier = Modifier.weight(1f), size = 11, spacing = 1.2)
        IconButton(onClick = { vm.closeSheet() }) { Icon(Icons.Default.Close, contentDescription = "Schliessen", tint = A.muted) }
    }
    Text(e.title, color = A.fg, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
    val whenText = buildString {
        append(if (e.multiDay) "${Dates.wdShortDate(e.startDay)} – ${Dates.wdShortDate(e.endDay)}" else Dates.wdShortDate(e.startDay))
        append(" · ")
        append(if (e.allDay) "ganztägig" else "${Dates.hm(e.startMin)} – ${Dates.hm(e.endMin.coerceAtMost(1439))}")
    }
    InfoLine(Icons.Default.Info, whenText)
    if (e.recurring) InfoLine(Icons.Default.Info, "Wiederholt sich (Serie)")
    e.location?.let { InfoLine(Icons.Default.LocationOn, it) }
    val reminder = remember(e.eventId) { runCatching { vm.editEventDraft(e).draft.reminderMinutes }.getOrDefault(-1) }
    InfoLine(Icons.Default.Notifications, reminderText(reminder))
    e.description?.let { Text(it, color = A.fg2, fontSize = 14.sp) }
    if (cal?.writable != false) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlineButton("Bearbeiten", Modifier.weight(1f)) { vm.openSheet(vm.editEventDraft(e)) }
            OutlineButton(if (e.recurring) "Serie löschen" else "Löschen", Modifier.weight(1f), color = Color(0xFF6B2A20), textColor = A.redSoft, fill = Color(0x1AE02204)) {
                vm.deleteEvent(e)
            }
        }
    } else {
        Text("Dieser Kalender ist schreibgeschützt.", color = A.muted, fontSize = 13.sp)
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = A.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = A.fg2, fontSize = 14.sp)
    }
}

// ------------------------------------------------------------------ Aufgabe

@Composable
private fun TaskDetail(s: UiState, vm: AppViewModel, t: TaskItem) {
    val list = s.list(t.listId)
    val subs = s.liveTasks.filter { it.parentId == t.id }
    var newSub by remember(t.id) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(dotColor(s.listColor(t.listId)), 10.dp)
        Spacer(Modifier.width(8.dp))
        Mono(list?.name ?: "Liste", modifier = Modifier.weight(1f), size = 11, spacing = 1.2)
        IconButton(onClick = { vm.closeSheet() }) { Icon(Icons.Default.Close, contentDescription = "Schliessen", tint = A.muted) }
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(start = 0.dp)) { CheckCircle(t.done, { vm.toggleTask(t) }, size = 24.dp, label = if (t.done) "Als offen markieren" else "Erledigen") }
        TaskTitle(t.title, t.done, size = 22, weight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp), maxLines = 4)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DueChip(t, s.today, big = true)
        if (t.highPriority) {
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x24FFB547)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("!! Hohe Priorität", style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.4.sp, color = Color(0xFFFFC46E)))
            }
        }
    }
    t.description?.let { Text(it, color = A.fg2, fontSize = 14.sp) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(A.panel2)
            .border(1.dp, A.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row {
            Mono("Unteraufgaben", color = A.blue, modifier = Modifier.weight(1f))
            Mono(if (subs.isEmpty()) "keine" else "${subs.count { it.done }} von ${subs.size}", size = 11, spacing = 0.2)
        }
        subs.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(sub.done, { vm.toggleTask(sub) }, size = 18.dp, label = if (sub.done) "Als offen markieren" else "Erledigen")
                TaskTitle(sub.title, sub.done, weight = FontWeight.Normal, modifier = Modifier.weight(1f).clickable { vm.openSheet(Sheet.Task(sub.id)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = newSub, onValueChange = { newSub = it },
                placeholder = { Text("Unteraufgabe hinzufügen", color = A.dim) },
                singleLine = true, modifier = Modifier.weight(1f),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addSubtask(t, newSub); newSub = "" }),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(A.blue)
                    .clickable(role = Role.Button, onClickLabel = "Unteraufgabe hinzufügen") { vm.addSubtask(t, newSub); newSub = "" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = "Unteraufgabe hinzufügen", tint = A.blueInk) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlineButton("Bearbeiten", Modifier.weight(1f)) { vm.openSheet(vm.editTaskDraft(t)) }
        OutlineButton("Löschen", Modifier.weight(1f), color = Color(0xFF6B2A20), textColor = A.redSoft, fill = Color(0x1AE02204)) { vm.deleteTask(t) }
    }
}

// ------------------------------------------------------------------ Editoren

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = A.blue, unfocusedBorderColor = A.line2,
    focusedContainerColor = A.panel2, unfocusedContainerColor = A.panel2,
    focusedTextColor = A.fg, unfocusedTextColor = A.fg, cursorColor = A.blue,
    focusedLabelColor = A.blueLight, unfocusedLabelColor = A.muted,
)

@Composable
private fun EditorHeader(title: String, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel) { Text("Abbrechen", color = A.muted, fontWeight = FontWeight.SemiBold) }
        Text(title, color = A.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(A.blue).clickable(role = Role.Button, onClick = onSave).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Speichern", color = A.blueInk, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    }
}

@Composable
private fun FieldButton(label: String, value: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(A.panel2)
            .border(1.dp, A.line2, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Mono(label, size = 9, spacing = 1.0)
        Text(value, color = if (enabled) A.fg else A.dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EventEditor(s: UiState, vm: AppViewModel, edit: Sheet.EditEvent) {
    var d by remember(edit) { mutableStateOf(edit.draft) }
    var err by remember(edit) { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf<Int?>(null) } // 0 = Start, 1 = Ende
    var pickTime by remember { mutableStateOf<Int?>(null) }
    val locked = d.recurring

    EditorHeader(if (d.eventId == null) "Neuer Termin" else "Termin bearbeiten", onCancel = {
        if (d.eventId == null) vm.closeSheet() else vm.closeSheet()
    }) {
        if (d.title.isBlank()) err = "Bitte einen Titel eingeben." else vm.saveEvent(edit.copy(draft = d))
    }
    OutlinedTextField(
        value = d.title, onValueChange = { d = d.copy(title = it); err = null },
        label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        isError = err != null, supportingText = { err?.let { Text(it, color = A.redSoft) } },
    )
    Mono("Kalender")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.writableCalendars.forEach { c ->
            Pill(c.name, d.calendarId == c.id, onClick = { if (!locked) d = d.copy(calendarId = c.id) }, dot = dotColor(c.color), fill = chipColor(c.color).copy(alpha = 0.55f))
        }
    }
    if (locked) Text("Serientermin: Datum, Zeit und Kalender bitte in der Nextcloud ändern. Titel, Ort und Notiz kannst du hier anpassen.", color = A.muted, fontSize = 12.sp)
    SwitchRow("Ganztägig", d.allDay, { if (!locked) d = d.copy(allDay = it) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldButton("Von", Dates.wdShortDate(d.startDate), Modifier.weight(1f), enabled = !locked) { pickDate = 0 }
        if (!d.allDay) FieldButton("Zeit", Dates.hm(d.startTime), Modifier.width(96.dp), enabled = !locked) { pickTime = 0 }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldButton("Bis", Dates.wdShortDate(d.endDate), Modifier.weight(1f), enabled = !locked) { pickDate = 1 }
        if (!d.allDay) FieldButton("Zeit", Dates.hm(d.endTime), Modifier.width(96.dp), enabled = !locked) { pickTime = 1 }
    }
    OutlinedTextField(
        value = d.location, onValueChange = { d = d.copy(location = it) }, label = { Text("Ort") },
        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
    )
    if (!locked) {
        Mono("Erinnerung")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1, 0, 10, 15, 30, 60, 1440).forEach { m ->
                Pill(if (m < 0) "Keine" else if (m == 0) "Beginn" else if (m == 1440) "1 Tag" else if (m == 60) "1 Std." else "$m Min.", d.reminderMinutes == m, { d = d.copy(reminderMinutes = m) })
            }
        }
    }
    OutlinedTextField(
        value = d.description, onValueChange = { d = d.copy(description = it) }, label = { Text("Notiz") },
        minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
    )

    pickDate?.let { which ->
        DateDialog(if (which == 0) d.startDate else d.endDate, onDismiss = { pickDate = null }) { picked ->
            d = if (which == 0) {
                val len = java.time.temporal.ChronoUnit.DAYS.between(d.startDate, d.endDate)
                d.copy(startDate = picked, endDate = picked.plusDays(len))
            } else d.copy(endDate = if (picked.isBefore(d.startDate)) d.startDate else picked)
            pickDate = null
        }
    }
    pickTime?.let { which ->
        TimeDialog(if (which == 0) d.startTime else d.endTime, onDismiss = { pickTime = null }) { t ->
            d = if (which == 0) {
                val dur = java.time.Duration.between(d.startTime, d.endTime).toMinutes().let { if (it <= 0) s.settings.defaultDuration.toLong() else it }
                val end = t.plusMinutes(dur)
                d.copy(startTime = t, endTime = if (end.isBefore(t)) LocalTime.of(23, 59) else end)
            } else d.copy(endTime = t)
            pickTime = null
        }
    }
}

@Composable
private fun TaskEditor(s: UiState, vm: AppViewModel, edit: Sheet.EditTask) {
    var d by remember(edit) { mutableStateOf(edit.draft) }
    var err by remember(edit) { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    EditorHeader(if (d.taskId == null) (if (d.parentId != null) "Neue Unteraufgabe" else "Neue Aufgabe") else "Aufgabe bearbeiten", onCancel = {
        if (d.taskId != null) vm.openSheet(Sheet.Task(d.taskId!!)) else vm.closeSheet()
    }) {
        if (d.title.isBlank()) err = "Bitte einen Titel eingeben." else vm.saveTask(edit.copy(draft = d))
    }
    OutlinedTextField(
        value = d.title, onValueChange = { d = d.copy(title = it); err = null },
        label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        isError = err != null, supportingText = { err?.let { Text(it, color = A.redSoft) } },
    )
    Mono("Liste")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.lists.forEach { l -> Pill(l.label, d.listId == l.id, { d = d.copy(listId = l.id) }, dot = dotColor(l.color)) }
    }
    if (s.list(d.listId)?.isSynced == false) {
        Text("Diese Liste liegt nur auf dem Handy und wird nicht mit Nextcloud synchronisiert.", color = A.warn, fontSize = 12.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldButton("Fällig", d.due?.let { Dates.wdShortDate(it) } ?: "Ohne Datum", Modifier.weight(1f)) { pick = true }
        OutlineButton("Ohne Datum", Modifier.width(120.dp), color = A.line2, textColor = A.muted, fill = Color.Transparent) { d = d.copy(due = null, dueTime = null) }
    }
    if (d.due != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldButton("Uhrzeit", d.dueTime?.let { Dates.hm(it) } ?: "Ganzer Tag", Modifier.weight(1f)) { pickTime = true }
            OutlineButton("Ganzer Tag", Modifier.width(120.dp), color = A.line2, textColor = A.muted, fill = Color.Transparent) { d = d.copy(dueTime = null) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(17, 0), LocalTime.of(20, 0)).forEach { t ->
                Pill(Dates.hm(t), d.dueTime == t, { d = d.copy(dueTime = t) })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Heute" to s.today, "Morgen" to s.today.plusDays(1), "Nächste Woche" to s.today.plusWeeks(1)).forEach { (label, date) ->
            Pill(label, d.due == date, { d = d.copy(due = date) })
        }
    }
    SwitchRow("Hohe Priorität", d.highPriority, { d = d.copy(highPriority = it) })
    OutlinedTextField(
        value = d.description, onValueChange = { d = d.copy(description = it) }, label = { Text("Notiz") },
        minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
    )
    if (pick) DateDialog(d.due ?: s.today, onDismiss = { pick = false }) { d = d.copy(due = it); pick = false }
    if (pickTime) TimeDialog(d.dueTime ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0), onDismiss = { pickTime = false }) { d = d.copy(dueTime = it); pickTime = false }
}

@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = Dates.utcMidnight(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(Dates.utcDate(it)) } ?: onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) { DatePicker(state = state) }
}

@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = A.panel,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = A.panel2, selectorColor = A.blue,
                    timeSelectorSelectedContainerColor = A.blue.copy(alpha = 0.25f),
                    timeSelectorUnselectedContainerColor = A.panel2,
                    timeSelectorSelectedContentColor = A.fg, timeSelectorUnselectedContentColor = A.fg2,
                ),
            )
        },
    )
}

// ------------------------------------------------------------------ Anzeigen

@Composable
private fun VisibilitySheet(s: UiState, vm: AppViewModel) {
    SheetHeader("Anzeigen", onClose = { vm.closeSheet() })
    if (s.calendars.isEmpty()) {
        Text("Noch keine Kalender gefunden. Richte DAVx⁵ ein und synchronisiere.", color = A.muted, fontSize = 14.sp)
    }
    s.calendars.groupBy { it.accountName }.forEach { (account, cals) ->
        Mono("Kalender · $account", color = A.blue, size = 10, spacing = 1.0)
        Column {
            cals.forEach { c ->
                val on = c.id !in s.settings.hiddenCalendars
                VisRow(c.name, on, dotColor(c.color), square = true) { vm.toggleCalendar(c.id) }
            }
        }
    }
    HorizontalDivider(color = A.line)
    SwitchRow("Aufgaben im Kalender", s.settings.tasksInCalendar, { v -> vm.updateSettings { it.copy(tasksInCalendar = v) } }, subtitle = s.provider?.let { "Nextcloud Tasks über ${it.label}" } ?: "Keine Aufgaben-App gefunden")
    if (s.settings.tasksInCalendar) {
        Column {
            s.lists.forEach { l ->
                val on = l.id !in s.settings.hiddenLists
                val open = s.liveTasks.count { it.listId == l.id && !it.done }
                VisRow(l.label, on, dotColor(l.color), square = false, trailing = "$open offen") { vm.toggleList(l.id) }
            }
        }
    }
}

@Composable
private fun VisRow(name: String, on: Boolean, color: Color, square: Boolean, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Checkbox, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = if (square) RoundedCornerShape(6.dp) else CircleShape
        Box(
            Modifier.size(22.dp).clip(shape).then(if (on) Modifier.background(color) else Modifier.border(2.dp, Color(0xFF5A6B88), shape)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = A.blueInk, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(name, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) Mono(trailing, size = 11, spacing = 0.2)
    }
}
