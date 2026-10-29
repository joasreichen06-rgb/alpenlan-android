package ch.alpenlan.kalender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.data.CalView
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.EventItem
import ch.alpenlan.kalender.data.TaskItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

// ------------------------------------------------------------------ Hilfen

private fun sortForDay(list: List<EventItem>): List<EventItem> =
    list.sortedWith(compareBy<EventItem>({ if (it.allDay || it.multiDay) 0 else 1 }, { it.startMin }, { it.title }))

private fun eventsByDay(s: UiState, from: LocalDate, toIncl: LocalDate): Map<LocalDate, List<EventItem>> {
    val map = HashMap<LocalDate, MutableList<EventItem>>()
    s.visibleEvents.forEach { e ->
        var d = maxOf(e.startDay, from)
        val end = minOf(e.endDay, toIncl)
        while (!d.isAfter(end)) {
            map.getOrPut(d) { mutableListOf() }.add(e)
            d = d.plusDays(1)
        }
    }
    return map.mapValues { sortForDay(it.value) }
}

private fun tasksByDay(s: UiState): Map<LocalDate, List<TaskItem>> =
    s.calendarTasks.groupBy { it.due!! }.mapValues { (_, v) -> v.sortedWith(compareBy({ it.done }, { it.dueTime ?: java.time.LocalTime.MAX }, { !it.highPriority }, { it.title })) }

private fun isWeekend(d: DayOfWeek) = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY

private val DAY_ALL_DAY_ROW = 20.dp

// ------------------------------------------------------------------ Monat

private val MONTH_LANE = 15.dp
private val MONTH_TOP = 22.dp

/** Ein Balken im Monatsraster: Spalten a..b einer Wochenzeile, auf Spur [lane]. */
private class MonthBar(val a: Int, val b: Int, val event: EventItem?, val task: TaskItem?, var lane: Int = 0)

/** Ordnet Termine und Aufgaben einer Woche Spuren zu – mehrtägige Termine laufen als durchgehender Balken. */
private fun monthRowLayout(s: UiState, rowStart: LocalDate): List<MonthBar> {
    val rowEnd = rowStart.plusDays(6)
    val col = { d: LocalDate -> ChronoUnit.DAYS.between(rowStart, d).toInt().coerceIn(0, 6) }
    val inRow = s.visibleEvents.filter { !it.endDay.isBefore(rowStart) && !it.startDay.isAfter(rowEnd) }
    val bars = mutableListOf<MonthBar>()
    inRow.filter { it.multiDay }
        .sortedWith(compareBy<EventItem>({ maxOf(it.startDay, rowStart) }, { -ChronoUnit.DAYS.between(it.startDay, it.endDay) }, { it.title }))
        .forEach { bars += MonthBar(col(it.startDay), col(it.endDay), it, null) }
    for (c in 0 until 7) {
        val d = rowStart.plusDays(c.toLong())
        inRow.filter { !it.multiDay && it.startDay == d }
            .sortedWith(compareBy<EventItem>({ if (it.allDay) 0 else 1 }, { it.startMin }, { it.title }))
            .forEach { bars += MonthBar(c, c, it, null) }
        s.calendarTasks.filter { it.due == d }
            .sortedWith(compareBy({ it.done }, { !it.highPriority }, { it.title }))
            .forEach { bars += MonthBar(c, c, null, it) }
    }
    val occupied = mutableListOf<BooleanArray>()
    bars.forEach { bar ->
        var l = 0
        while (true) {
            if (l == occupied.size) occupied += BooleanArray(7)
            if ((bar.a..bar.b).none { occupied[l][it] }) break
            l++
        }
        (bar.a..bar.b).forEach { occupied[l][it] = true }
        bar.lane = l
    }
    return bars
}

@Composable
fun MonthView(s: UiState, vm: AppViewModel) {
    val first = s.settings.firstDayOfWeek
    val order = Dates.weekdayOrder(first)
    val monthStart = s.month.atDay(1)
    val gridStart = Dates.weekStart(monthStart, first)
    val lead = ChronoUnit.DAYS.between(gridStart, monthStart).toInt()
    val rows = ceil((lead + s.month.lengthOfMonth()) / 7.0).toInt()
    val kwW = if (s.settings.showWeekNumbers) 22.dp else 0.dp

    Column(
        Modifier
            .fillMaxSize()
            .swipe(onPrev = { vm.shift(-1) }, onNext = { vm.shift(1) })
            .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (s.settings.showWeekNumbers) Mono("KW", Modifier.width(kwW), size = 9, spacing = 0.4)
            order.forEach { dow ->
                Text(
                    Dates.WD_SHORT[dow.value - 1].uppercase(), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.6.sp, color = if (isWeekend(dow)) A.blueLight else A.muted),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gap = 3.dp
            val rowH = (maxHeight - gap * (rows - 1)) / rows
            val maxLanes = floor(((rowH - MONTH_TOP - 3.dp) / MONTH_LANE)).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until rows) {
                    val rowStart = gridStart.plusDays(r * 7L)
                    Row(Modifier.fillMaxWidth().height(rowH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (s.settings.showWeekNumbers) {
                            Text(
                                "${Dates.isoWeek(rowStart.plusDays(3))}", Modifier.width(kwW).padding(top = 6.dp),
                                textAlign = TextAlign.Center, style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.sp, color = A.dim),
                            )
                        }
                        MonthRow(s, vm, rowStart, gap, maxLanes, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthRow(s: UiState, vm: AppViewModel, rowStart: LocalDate, gap: Dp, maxLanes: Int, modifier: Modifier) {
    val bars = remember(s.visibleEvents, s.calendarTasks, rowStart) { monthRowLayout(s, rowStart) }
    val overflow = bars.any { it.lane >= maxLanes }
    val visibleLanes = if (overflow) (maxLanes - 1).coerceAtLeast(0) else maxLanes
    val hidden = IntArray(7)
    bars.filter { it.lane >= visibleLanes }.forEach { b -> (b.a..b.b).forEach { hidden[it]++ } }
    val counts = IntArray(7)
    bars.forEach { b -> (b.a..b.b).forEach { counts[it]++ } }

    BoxWithConstraints(modifier) {
        val colW = (maxWidth - gap * 6) / 7
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (c in 0 until 7) {
                val d = rowStart.plusDays(c.toLong())
                DayCellBg(s, vm, d, d.month == s.month.month, counts[c], Modifier.weight(1f).fillMaxHeight())
            }
        }
        bars.filter { it.lane < visibleLanes }.forEach { bar ->
            val x = (colW + gap) * bar.a + 2.dp
            val w = (colW + gap) * (bar.b - bar.a + 1) - gap - 4.dp
            val y = MONTH_TOP + MONTH_LANE * bar.lane
            val allOut = (bar.a..bar.b).all { rowStart.plusDays(it.toLong()).month != s.month.month }
            val alpha = if (allOut) 0.5f else 1f
            val m = Modifier.offset(x = x, y = y).width(w).height(MONTH_LANE - 1.dp)
            val e = bar.event
            val t = bar.task
            if (e != null) {
                val rowEnd = rowStart.plusDays(6)
                val l = if (e.startDay.isBefore(rowStart)) 0.dp else 4.dp
                val rr = if (e.endDay.isAfter(rowEnd)) 0.dp else 4.dp
                val shape = RoundedCornerShape(topStart = l, bottomStart = l, topEnd = rr, bottomEnd = rr)
                Box(
                    m.clip(shape).background(chipColor(e.color).copy(alpha = alpha)).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        e.title, color = Color.White.copy(alpha = if (allOut) 0.7f else 1f),
                        fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip,
                    )
                }
            } else if (t != null) {
                val lc = dotColor(s.listColor(t.listId))
                Row(
                    m.clip(RoundedCornerShape(4.dp)).border(1.dp, lc.copy(alpha = alpha), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).then(if (t.done) Modifier.background(lc) else Modifier.border(1.5.dp, lc, CircleShape)))
                    Text(
                        t.title, color = if (t.done) A.dim else A.fg.copy(alpha = alpha), fontSize = 9.sp, lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
        for (c in 0 until 7) {
            if (hidden[c] > 0) {
                Text(
                    "+${hidden[c]}", color = A.muted, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.offset(x = (colW + gap) * c + 5.dp, y = MONTH_TOP + MONTH_LANE * visibleLanes),
                )
            }
        }
    }
}

@Composable
private fun DayCellBg(s: UiState, vm: AppViewModel, d: LocalDate, inMonth: Boolean, count: Int, modifier: Modifier) {
    val isSel = d == s.selected
    val isToday = d == s.today
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (isSel) A.blue.copy(alpha = 0.12f) else if (inMonth) A.panel else A.panel.copy(alpha = 0.45f))
            .border(1.5.dp, if (isSel) A.blue else if (inMonth) Color(0xFF172235) else Color.Transparent, shape)
            .clickable(onClickLabel = "Tagesübersicht öffnen") { vm.select(d, openDaySheet = true) }
            .semantics { contentDescription = "${d.dayOfMonth}. ${Dates.MONTHS[d.monthValue - 1]}, $count Einträge" }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .then(if (isToday) Modifier.clip(RoundedCornerShape(6.dp)).background(A.blue) else Modifier)
                .padding(horizontal = if (isToday) 4.dp else 2.dp)
        ) {
            Text(
                "${d.dayOfMonth}", fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.SemiBold,
                color = when { isToday -> A.blueInk; isSel -> A.blueLight; inMonth -> A.fg; else -> A.dim },
            )
        }
    }
}

// ------------------------------------------------------------------ Woche

private class Bar(val a: Int, val b: Int, val lane: Int, val event: EventItem?, val task: TaskItem?)

private fun weekBars(s: UiState, ws: LocalDate): List<Bar> {
    val we = ws.plusDays(6)
    val items = mutableListOf<Triple<Int, Int, Any>>()
    s.visibleEvents.filter { !it.timedSingleDay && !it.endDay.isBefore(ws) && !it.startDay.isAfter(we) }
        .sortedWith(compareBy<EventItem>({ it.startDay }, { -ChronoUnit.DAYS.between(it.startDay, it.endDay) }))
        .forEach { e ->
            val a = maxOf(0, ChronoUnit.DAYS.between(ws, e.startDay).toInt())
            val b = minOf(6, ChronoUnit.DAYS.between(ws, e.endDay).toInt())
            items += Triple(a, b, e)
        }
    s.calendarTasks.filter { !it.due!!.isBefore(ws) && !it.due.isAfter(we) }.sortedBy { it.due }.forEach { t ->
        val a = ChronoUnit.DAYS.between(ws, t.due!!).toInt()
        items += Triple(a, a, t)
    }
    val laneEnd = mutableListOf<Int>()
    return items.map { (a, b, obj) ->
        var l = 0
        while (l < laneEnd.size && laneEnd[l] >= a) l++
        if (l == laneEnd.size) laneEnd += b else laneEnd[l] = b
        Bar(a, b, l, obj as? EventItem, obj as? TaskItem)
    }
}

@Composable
fun WeekView(s: UiState, vm: AppViewModel) {
    val ws = Dates.weekStart(s.selected, s.settings.firstDayOfWeek)
    val days = (0L until 7L).map { ws.plusDays(it) }
    val axis = 40.dp
    val bars = remember(s.visibleEvents, s.calendarTasks, ws) { weekBars(s, ws) }
    Column(Modifier.fillMaxSize().swipe(onPrev = { vm.shift(-1) }, onNext = { vm.shift(1) })) {
        Row(Modifier.fillMaxWidth().padding(end = 6.dp)) {
            Spacer(Modifier.width(axis))
            days.forEach { d ->
                val today = d == s.today
                Column(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable(onClickLabel = "Tagesansicht öffnen") { vm.select(d, mode = CalView.DAY) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(Dates.wdShort(d).uppercase(), style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.8.sp, color = if (today) A.blue else A.muted))
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .then(
                                when {
                                    today -> Modifier.background(A.blue)
                                    d == s.selected -> Modifier.border(1.5.dp, A.blue, CircleShape)
                                    else -> Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.dayOfMonth}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (today) A.blueInk else if (d == s.selected) A.blueLight else A.fg)
                    }
                }
            }
        }
        // Ganztägiges + Aufgaben
        val lanes = (bars.maxOfOrNull { it.lane } ?: -1) + 1
        Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) {
            Text(
                "ganzt.", Modifier.width(axis).padding(top = 4.dp, end = 6.dp), textAlign = TextAlign.End,
                style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 0.sp, color = A.dim),
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .heightIn(min = DAY_ALL_DAY_ROW, max = 124.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val colW = maxWidth / 7
                Box(Modifier.fillMaxWidth().height(DAY_ALL_DAY_ROW * maxOf(1, lanes))) {
                    bars.forEach { bar -> WeekBar(s, vm, bar, colW) }
                }
            }
        }
        HorizontalDivider(color = A.line2)
        TimeGrid(s, vm, days, axis = axis, compact = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WeekBar(s: UiState, vm: AppViewModel, bar: Bar, colW: Dp) {
    val m = Modifier
        .offset(x = colW * bar.a + 1.dp, y = DAY_ALL_DAY_ROW * bar.lane)
        .width(colW * (bar.b - bar.a + 1) - 2.dp)
        .height(18.dp)
        .clip(RoundedCornerShape(4.dp))
    val e = bar.event
    val t = bar.task
    if (e != null) {
        Box(
            m.background(chipColor(e.color)).clickable { vm.openSheet(Sheet.Event(e)) }.padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(e.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else if (t != null) {
        val lc = dotColor(s.listColor(t.listId))
        Row(
            m.border(1.dp, lc, RoundedCornerShape(4.dp)).background(A.panel).clickable { vm.openSheet(Sheet.Task(t.id)) }.padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).then(if (t.done) Modifier.background(lc) else Modifier.border(1.5.dp, lc, CircleShape)))
            Text(
                (t.dueTime?.let { Dates.hm(it) + " " } ?: "") + t.title, color = if (t.done) A.dim else A.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (t.done) TextDecoration.LineThrough else null,
            )
        }
    }
}

// ------------------------------------------------------------------ Tag

@Composable
fun DayView(s: UiState, vm: AppViewModel) {
    val sel = s.selected
    val ws = Dates.weekStart(sel, s.settings.firstDayOfWeek)
    val evs = s.visibleEvents.filter { it.occursOn(sel) }
    val allDay = sortForDay(evs.filter { !it.timedSingleDay })
    val dayTasks = s.calendarTasks.filter { it.due == sel }.sortedWith(compareBy({ it.done }, { it.dueTime ?: java.time.LocalTime.MAX }, { !it.highPriority }))
    Column(Modifier.fillMaxSize().swipe(onPrev = { vm.shift(-1) }, onNext = { vm.shift(1) })) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            for (i in 0L until 7L) {
                val d = ws.plusDays(i)
                val on = d == sel
                val today = d == s.today
                val busy = s.visibleEvents.any { it.occursOn(d) } || s.calendarTasks.any { it.due == d && !it.done }
                Column(
                    Modifier.weight(1f).height(60.dp).clickable { vm.select(d) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(Dates.wdShort(d).uppercase(), style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.8.sp))
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .then(
                                when {
                                    on && today -> Modifier.background(A.blue)
                                    on -> Modifier.background(A.blue.copy(alpha = 0.18f)).border(1.5.dp, A.blue, CircleShape)
                                    today -> Modifier.border(1.5.dp, Color(0xFF2B4466), CircleShape)
                                    else -> Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on && today) A.blueInk else if (today) A.blue else A.fg)
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (busy) A.blue else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = A.line)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (allDay.isEmpty() && dayTasks.isEmpty()) {
                Text("Keine ganztägigen Termine oder Aufgaben", color = A.dim, fontSize = 12.sp)
            }
            allDay.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipColor(e.color))
                        .clickable { vm.openSheet(Sheet.Event(e)) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(e.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        if (e.multiDay) "${Dates.short(e.startDay)} – ${Dates.short(e.endDay)}" else if (e.allDay) "ganztägig" else "${Dates.hm(e.startMin)} – ${Dates.hm(e.endMin.coerceAtMost(1439))}",
                        color = Color.White, fontSize = 11.sp,
                    )
                }
            }
            dayTasks.forEach { t -> TaskRowCompact(s, vm, t) }
        }
        HorizontalDivider(color = A.line2)
        TimeGrid(s, vm, listOf(sel), axis = 50.dp, compact = false, modifier = Modifier.weight(1f))
    }
}

@Composable
fun TaskRowCompact(s: UiState, vm: AppViewModel, t: TaskItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(A.panel2.copy(alpha = 0.8f))
            .border(1.dp, A.line2, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(t.done, { vm.toggleTask(t) }, size = 18.dp, touch = 42.dp, tint = A.blue, label = if (t.done) "Als offen markieren" else "Erledigen")
        Row(
            Modifier.weight(1f).height(42.dp).clickable { vm.openSheet(Sheet.Task(t.id)) }.padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            t.dueTime?.let { Mono(Dates.hm(it), size = 11, spacing = 0.2, color = A.blueLight, modifier = Modifier.padding(end = 8.dp)) }
            TaskTitle(t.title, t.done, size = 13, modifier = Modifier.weight(1f), maxLines = 1)
            Dot(dotColor(s.listColor(t.listId)), 6.dp)
            Spacer(Modifier.width(5.dp))
            Mono(s.list(t.listId)?.name ?: "", size = 10, spacing = 0.4)
        }
    }
}

// ------------------------------------------------------------------ Stundenraster

private class Placed(val e: EventItem, val s: Int, val en: Int, var lane: Int = 0, var n: Int = 1)

/** Überlappende Termine nebeneinander legen (wie Google Kalender). */
private fun layoutDay(list: List<EventItem>): List<Placed> {
    val items = list.map { e ->
        val en = if (e.endMin > e.startMin) e.endMin else e.startMin + 60
        Placed(e, e.startMin, en)
    }.sortedWith(compareBy<Placed>({ it.s }, { -it.en }))
    val cluster = mutableListOf<Placed>()
    val lanes = mutableListOf<Int>()
    var cEnd = -1
    fun flush() { cluster.forEach { it.n = lanes.size }; cluster.clear(); lanes.clear(); cEnd = -1 }
    items.forEach { p ->
        if (cluster.isNotEmpty() && p.s >= cEnd) flush()
        var l = 0
        while (l < lanes.size && lanes[l] > p.s) l++
        if (l == lanes.size) lanes += p.en else lanes[l] = p.en
        p.lane = l
        cluster += p
        cEnd = maxOf(cEnd, p.en)
    }
    flush()
    return items
}

@Composable
fun TimeGrid(s: UiState, vm: AppViewModel, days: List<LocalDate>, axis: Dp, compact: Boolean, modifier: Modifier) {
    val hh = s.settings.hourHeight.dp
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hhPx = with(density) { hh.toPx() }
    LaunchedEffect(Unit) { scroll.scrollTo((hhPx * s.settings.dayStartHour).toInt()) }
    val now = LocalTime.now()
    val nowMin = now.hour * 60 + now.minute
    Box(modifier.fillMaxWidth().verticalScroll(scroll)) {
        Row(Modifier.fillMaxWidth().height(hh * 24).padding(end = if (compact) 6.dp else 12.dp)) {
            Column(Modifier.width(axis)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(hh).fillMaxWidth()) {
                        if (h > 0) Text(
                            Dates.hm(h * 60), Modifier.align(Alignment.TopEnd).offset(y = (-6).dp).padding(end = 6.dp),
                            style = MonoLabel.copy(fontSize = if (compact) 9.sp else 10.sp, letterSpacing = 0.sp, color = A.dim),
                        )
                    }
                }
            }
            days.forEach { d ->
                val timed = s.visibleEvents.filter { it.timedSingleDay && it.startDay == d }
                val placed = remember(timed) { layoutDay(timed) }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (d == s.today && compact) A.blue.copy(alpha = 0.05f) else Color.Transparent)
                        .drawBehind {
                            drawLine(A.lineSoft, Offset(0f, 0f), Offset(0f, size.height), 1f)
                            for (h in 0 until 24) {
                                val y = h * hhPx
                                drawLine(A.lineSoft, Offset(0f, y), Offset(size.width, y), 1f)
                            }
                        }
                        .pointerInput(d) {
                            detectTapGestures { pos ->
                                val hour = (pos.y / hhPx).toInt().coerceIn(0, 23)
                                vm.openSheet(vm.newEventDraft(d, hour))
                            }
                        }
                ) {
                    val colW = maxWidth
                    placed.forEach { p ->
                        val w = colW / p.n
                        val top = hh * (p.s / 60f)
                        val height = maxOf(if (compact) 20.dp else 24.dp, hh * ((p.en - p.s) / 60f) - 2.dp)
                        Column(
                            Modifier
                                .offset(x = w * p.lane + 1.dp, y = top)
                                .width(w - 2.dp)
                                .height(height)
                                .clip(RoundedCornerShape(if (compact) 5.dp else 8.dp))
                                .background(chipColor(p.e.color))
                                .clickable(role = Role.Button) { vm.openSheet(Sheet.Event(p.e)) }
                                .padding(horizontal = if (compact) 3.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
                        ) {
                            Text(
                                p.e.title, color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = if (compact) 10.sp else 13.sp, lineHeight = if (compact) 12.sp else 16.sp,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                Dates.hm(p.s) + if (!compact) " – ${Dates.hm(p.en.coerceAtMost(1439))}" + (p.e.location?.let { " · $it" } ?: "") else "",
                                color = Color.White.copy(alpha = 0.9f), fontSize = if (compact) 9.sp else 11.sp,
                                lineHeight = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (d == s.today) {
                        Box(Modifier.offset(y = hh * (nowMin / 60f) - 1.dp).fillMaxWidth().height(2.dp).background(A.now))
                        Box(Modifier.offset(x = (-4).dp, y = hh * (nowMin / 60f) - 4.dp).size(8.dp).clip(CircleShape).background(A.now))
                    }
                }
            }
        }
    }
}
