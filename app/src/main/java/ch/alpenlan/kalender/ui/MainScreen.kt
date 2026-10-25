package ch.alpenlan.kalender.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.alpenlan.kalender.data.CalView
import ch.alpenlan.kalender.data.Dates
import kotlinx.coroutines.delay

@Composable
fun App(vm: AppViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(A.groundTop, A.groundBottom)))
    ) {
        if (!s.settingsLoaded) return@Box
        when (s.screen) {
            Screen.SETUP -> SetupScreen(s, vm)
            Screen.SETTINGS -> SettingsScreen(s, vm)
            Screen.MAIN -> MainScreen(s, vm)
        }
    }
}

@Composable
private fun MainScreen(s: UiState, vm: AppViewModel) {
    BackHandler(enabled = s.tab == Tab.TASKS) { vm.setTab(Tab.CALENDAR) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(s, vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (s.tab == Tab.CALENDAR) {
                when (s.mode) {
                    CalView.MONTH -> MonthView(s, vm)
                    CalView.WEEK -> WeekView(s, vm)
                    CalView.DAY -> DayView(s, vm)
                }
            } else {
                TasksScreen(s, vm)
            }
            Fab(s, vm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            ToastHost(s, vm, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 88.dp, bottom = 18.dp))
        }
        BottomNav(s, vm)
    }
    Sheets(s, vm)
}

@Composable
private fun Header(s: UiState, vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    val isCal = s.tab == Tab.CALENDAR
    Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            AlpenlanLogo()
            Spacer(Modifier.width(10.dp))
            if (isCal) {
                ModeSwitch(s.mode) { vm.setMode(it) }
            } else {
                Mono("Alpenlan Cloud", color = A.blue)
            }
            Spacer(Modifier.weight(1f))
            if (isCal) {
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(A.blue.copy(alpha = 0.08f))
                        .border(1.dp, androidx.compose.ui.graphics.Color(0xFF2B4466), RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { vm.goToday() }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Mono("Heute", color = A.blueLight, spacing = 1.0) }
            }
            SyncButton(s.syncing) { vm.requestSync() }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menü", tint = A.muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = A.panel2) {
                    DropdownMenuItem(text = { Text("Kalender & Listen anzeigen") }, onClick = { menu = false; vm.openSheet(Sheet.Visibility) })
                    DropdownMenuItem(text = { Text("Jetzt synchronisieren") }, onClick = { menu = false; vm.requestSync() })
                    DropdownMenuItem(text = { Text("Einstellungen") }, onClick = { menu = false; vm.setScreen(Screen.SETTINGS) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            val (main, accent) = title(s)
            FitTitle(main, accent, Modifier.weight(1f))
            if (isCal) {
                IconButton(onClick = { vm.shift(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Zurück", tint = A.muted)
                }
                IconButton(onClick = { vm.shift(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Weiter", tint = A.muted)
                }
            }
        }
    }
}

/** Titel, der immer ganz sichtbar ist: Schriftgrösse wird an die verfügbare Breite angepasst. */
@Composable
private fun FitTitle(main: String, accent: String, modifier: Modifier) {
    val text = buildAnnotatedString {
        append(main.uppercase())
        withStyle(SpanStyle(color = A.blue)) { append(accent.uppercase()) }
    }
    val measurer = rememberTextMeasurer()
    val base = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.3).sp, color = A.fg)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val avail = with(density) { maxWidth.toPx() }
        val measured = remember(text, avail) { measurer.measure(text, base, softWrap = false, maxLines = 1).size.width.toFloat() }
        val scale = if (measured > 0f) (avail / measured * 0.97f).coerceAtMost(1f) else 1f
        Text(text, style = base.copy(fontSize = (30f * scale).sp), maxLines = 1, softWrap = false)
    }
}

private fun title(s: UiState): Pair<String, String> {
    if (s.tab == Tab.TASKS) return "Auf" to "gaben."
    val sel = s.selected
    return when (s.mode) {
        CalView.MONTH -> "${Dates.MONTHS[s.month.monthValue - 1]} " to "${s.month.year}"
        CalView.WEEK -> {
            val ws = Dates.weekStart(sel, s.settings.firstDayOfWeek)
            val we = ws.plusDays(6)
            val m = if (ws.month == we.month) Dates.MONTHS_3[ws.monthValue - 1] else "${Dates.MONTHS_3[ws.monthValue - 1]}/${Dates.MONTHS_3[we.monthValue - 1]}"
            "KW ${Dates.isoWeek(ws.plusDays(3))} " to "$m ${we.year}"
        }
        CalView.DAY -> "${Dates.wdShort(sel)} ${sel.dayOfMonth}. " to "${Dates.MONTHS_3[sel.monthValue - 1]} ${sel.year}"
    }
}

@Composable
private fun ModeSwitch(mode: CalView, onPick: (CalView) -> Unit) {
    Row(
        Modifier
            .border(1.dp, A.line2, RoundedCornerShape(10.dp))
            .padding(2.dp)
    ) {
        listOf(CalView.MONTH to "Monat", CalView.WEEK to "Woche", CalView.DAY to "Tag").forEach { (m, label) ->
            val on = m == mode
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) A.blue else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onPick(m) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) A.blueInk else A.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SyncButton(syncing: Boolean, onClick: () -> Unit) {
    val angle = if (syncing) {
        val t = rememberInfiniteTransition(label = "sync")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "rot").value
    } else 0f
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Refresh, contentDescription = if (syncing) "Synchronisiert…" else "Synchronisieren",
            tint = if (syncing) A.blue else A.muted, modifier = Modifier.rotate(angle),
        )
    }
}

@Composable
private fun Fab(s: UiState, vm: AppViewModel, modifier: Modifier) {
    Box(
        modifier
            .size(56.dp)
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(A.blue)
            .clickable(role = Role.Button, onClickLabel = if (s.tab == Tab.CALENDAR) "Neuer Termin" else "Neue Aufgabe") {
                if (s.tab == Tab.CALENDAR) vm.openSheet(vm.newEventDraft() ?: Sheet.Visibility)
                else {
                    val due = if (s.taskFilter == TaskFilter.TODAY) s.today else null
                    vm.openSheet(vm.newTaskDraft(due))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = if (s.tab == Tab.CALENDAR) "Neuer Termin" else "Neue Aufgabe", tint = A.blueInk, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ToastHost(s: UiState, vm: AppViewModel, modifier: Modifier) {
    val t = s.toast ?: return
    LaunchedEffect(t.stamp) {
        delay(if (t.undoKey != null) 5000 else 3000)
        vm.dismissToast()
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(A.lineSoft)
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFF2B3B55), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.text, color = A.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
        if (t.undoKey != null) {
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { vm.undo(t.undoKey) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Rückgängig", color = A.blueLight, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BottomNav(s: UiState, vm: AppViewModel) {
    val open = s.liveTasks.count { !it.done && it.parentId == null }
    Column(Modifier.fillMaxWidth().background(A.ground.copy(alpha = 0.97f))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(A.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
            NavItem("Kalender", s.tab == Tab.CALENDAR, Modifier.weight(1f), { Icon(Icons.Default.DateRange, null, Modifier.size(22.dp)) }) { vm.setTab(Tab.CALENDAR) }
            NavItem(if (open > 0) "Aufgaben · $open" else "Aufgaben", s.tab == Tab.TASKS, Modifier.weight(1f), { Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(22.dp)) }) { vm.setTab(Tab.TASKS) }
        }
    }
}

@Composable
private fun NavItem(label: String, on: Boolean, modifier: Modifier, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val color = if (on) A.blue else A.muted
    Column(
        modifier.fillMaxSize().clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides color) { icon() }
        Spacer(Modifier.height(4.dp))
        Mono(label, color = color, size = 10, spacing = 1.0)
    }
}
