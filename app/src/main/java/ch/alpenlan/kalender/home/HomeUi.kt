@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ch.alpenlan.kalender.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.alpenlan.kalender.MainActivity
import ch.alpenlan.kalender.contacts.ContactsActivity
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.EventItem
import ch.alpenlan.kalender.ui.A
import ch.alpenlan.kalender.ui.AlpenlanLogo
import ch.alpenlan.kalender.ui.Hint
import ch.alpenlan.kalender.ui.LinkButton
import ch.alpenlan.kalender.ui.Mono
import ch.alpenlan.kalender.ui.MonoLabel
import ch.alpenlan.kalender.ui.Pill
import ch.alpenlan.kalender.ui.Syne
import ch.alpenlan.kalender.ui.chipColor
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Hilfen

private val GREEN = A.ok
private val GRAY = Color(0xFF5A6B88)
private val ON_TILE = Color(0x244DA3FF)

private fun gb(bytes: Double) = if (bytes >= 1e12) "%.1f TB".format(bytes / 1e12) else "%.0f GB".format(bytes / 1e9)
private fun days(sec: Long) = when {
    sec <= 0 -> "–"
    sec < 3600 -> "${sec / 60} Min."
    sec < 86400 -> "${sec / 3600} Std."
    else -> "${sec / 86400} Tage"
}

private fun printerState(s: String) = when (s) {
    "printing" -> "druckt"; "paused" -> "pausiert"; "standby" -> "bereit"; "complete" -> "fertig"
    "error" -> "Fehler"; "cancelled" -> "abgebrochen"; else -> "offline"
}

private fun launch(ctx: Context, pkgs: List<String>, fallbackUrl: String?) {
    for (p in pkgs) {
        ctx.packageManager.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
    }
    if (fallbackUrl != null) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(A.panel.copy(alpha = 0.92f))
            .border(1.dp, A.line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun CardHead(title: String, dot: Color? = null, right: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(10.dp)) }
        Text(title, color = A.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (right != null) Mono(right, size = 11, spacing = 0.2)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, height: Int = 8) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(A.lineSoft)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height.dp)).background(color))
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(A.panel2).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Mono(label, size = 10, spacing = 1.0)
        Text(value, color = A.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RowScope.Action(text: String, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.weight(1f).height(46.dp).clip(shape)
            .then(
                when {
                    primary -> Modifier.background(A.blue)
                    danger -> Modifier.background(Color(0x1AE02204)).border(1.dp, Color(0xFF6B2A20), shape)
                    else -> Modifier.border(1.dp, A.line2, shape)
                }
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) A.blueInk else if (danger) A.redSoft else A.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun RoundBtn(text: String, label: String, size: Int = 48, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).border(1.dp, A.line2, CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = A.fg, fontSize = (size / 2).sp) }
}

// ------------------------------------------------------------------ App

@Composable
fun HomeApp(vm: HomeViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(A.groundTop, A.groundBottom)))) {
        if (!s.loggedIn) LoginScreen(s, vm) else Main(s, vm)
        s.toast?.let { t ->
            LaunchedEffect(s.toastStamp) { delay(2800); vm.dismissToast() }
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                    .fillMaxWidth().heightIn(min = 48.dp).shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    .background(A.lineSoft).border(1.dp, Color(0xFF2B3B55), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text(t, color = A.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun LoginScreen(s: HomeState, vm: HomeViewModel) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        AlpenlanLogo(Modifier.size(48.dp, 26.dp))
        Spacer(Modifier.height(14.dp))
        Text(buildAnnotatedString { append("ALPEN"); withStyle(SpanStyle(color = A.blue)) { append("LAN.") } }, fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, color = A.fg)
        Spacer(Modifier.height(8.dp))
        Hint("PC, Anlage, Drucker, Server und Dienste an einem Ort. Die Anmeldung läuft über dein Alpenlan-Konto (Authentik), genau wie bei der Cloud.")
        Spacer(Modifier.height(20.dp))
        LinkButton(if (s.busy) "Anmelden …" else "Mit Alpenlan anmelden", Modifier.fillMaxWidth(), primary = true) {
            if (!s.busy) ctx.startActivity(vm.login())
        }
        s.authError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = A.redSoft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Main(s: HomeState, vm: HomeViewModel) {
    val m = s.model
    val (main, accent) = when (s.tab) {
        HomeTab.OVERVIEW -> "Über" to "sicht."
        HomeTab.ROOM -> "Con" to "trol."
        HomeTab.SERVER -> "Ser" to "ver."
        HomeTab.APPS -> "Ap" to "ps."
    }
    val eyebrow = when (s.tab) {
        HomeTab.OVERVIEW -> "Alpenlan"; HomeTab.ROOM -> "Alpenlan Control"; HomeTab.SERVER -> "Proxmox · Dienste"; HomeTab.APPS -> "Alpenlan Suite"
    }
    val live = s.offline == null && m != null
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                AlpenlanLogo()
                Spacer(Modifier.width(10.dp))
                Mono(eyebrow, color = A.blue, modifier = Modifier.weight(1f))
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (live) GREEN else A.warn))
                Spacer(Modifier.width(6.dp))
                Mono(if (live) "Live" else if (m == null) "Verbinde …" else "Offline", color = if (live) GREEN else A.warn, size = 10, spacing = 1.0)
            }
            Text(
                buildAnnotatedString { append(main.uppercase()); withStyle(SpanStyle(color = A.blue)) { append(accent.uppercase()) } },
                fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = A.fg, maxLines = 1,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            s.offline?.let { msg ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFB547)).border(1.dp, Color(0x55FFB547), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(msg, color = A.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (m != null) Hint("Angezeigt wird der letzte bekannte Stand.")
                }
            }
            when (s.tab) {
                HomeTab.OVERVIEW -> Overview(s, vm)
                HomeTab.ROOM -> DeckControl(s, vm)
                HomeTab.SERVER -> Server(s, vm)
                HomeTab.APPS -> Apps(s, vm)
            }
        }
        Column(Modifier.fillMaxWidth().background(A.ground.copy(alpha = 0.97f))) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(A.line))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
                HomeTab.entries.forEach { t ->
                    val on = t == s.tab
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) { vm.setTab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(22.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(if (on) A.blue else Color.Transparent))
                        Spacer(Modifier.height(6.dp))
                        Mono(t.label, color = if (on) A.blue else A.muted, size = 10, spacing = 1.0)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Übersicht

@Composable
private fun Overview(s: HomeState, vm: HomeViewModel) {
    val m = s.model
    val hour = LocalTime.now().hour
    val greet = when (hour) { in 5..10 -> "Guten Morgen"; in 11..17 -> "Hallo"; else -> "Guten Abend" }
    val name = s.user?.split(' ')?.firstOrNull()
    val px = m?.proxmox
    val servicesOk = m?.services?.count { it.ok } ?: 0
    val servicesAll = m?.services?.size ?: 0
    val guestsUp = px?.guests?.count { it.status == "running" } ?: 0
    val problems = (servicesAll - servicesOk) + ((px?.guests?.size ?: 0) - guestsUp)
    Text(
        "$greet${name?.let { ", $it" } ?: ""}. " + when {
            m == null -> "Verbinde mit Alpenlan …"
            problems == 0 -> "Alles läuft."
            problems == 1 -> "Eine Sache braucht Aufmerksamkeit."
            else -> "$problems Sachen brauchen Aufmerksamkeit."
        },
        color = A.muted, fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Health("Dienste", if (servicesAll == 0) "–" else "$servicesOk / $servicesAll", servicesAll > 0 && servicesOk == servicesAll)
        val guestsAll = px?.guests?.size ?: 0
        val pxOk = px?.ok == true
        Health("Server", if (pxOk) "$guestsUp / $guestsAll" else "–", pxOk && guestsUp == guestsAll)
        val b = px?.backup
        val lastEnd = b?.lastEnd
        Health(
            "Backup",
            when { b?.running == true -> "läuft"; lastEnd != null -> Dates.wdShort(Dates.localDate(lastEnd * 1000)) + " " + Dates.hm(Dates.localTime(lastEnd * 1000)); else -> "keins" },
            b?.lastOk == true,
        )
    }
    if (m != null) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("Control", color = A.blue, modifier = Modifier.weight(1f))
                Text("Alles anzeigen", color = A.blueLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { vm.setTab(HomeTab.ROOM) }.padding(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("PC", if (m.pc.online) "Online" else if (m.pc.booting) "Startet …" else "Aus", m.pc.online || m.pc.booting, Modifier.weight(1f)) { vm.pcToggle() }
                val src = m.avr.sources.firstOrNull { it.id == m.avr.source }?.name ?: m.avr.source ?: ""
                Tile("Anlage", if (m.avr.on) (if (m.avr.muted) "Stumm · " else "") + "$src · ${m.avr.volume ?: "–"}" else "Aus", m.avr.on, Modifier.weight(1f)) { vm.act("avr_toggle") }
            }
            VolumeRow(m, vm)
            PrinterRow(m.printer) { vm.setTab(HomeTab.ROOM) }
        }
    }
    Card {
        Mono("Heute & morgen", color = A.blue)
        if (s.agenda.isEmpty()) Text("Keine Termine", color = A.muted, fontSize = 14.sp)
        s.agenda.forEach { e -> AgendaRow(e, s) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(A.lineSoft))
        Text(
            when {
                s.openTasks == 0 -> "Keine Aufgaben fällig"
                s.overdueTasks > 0 -> "${s.openTasks} Aufgaben fällig, ${s.overdueTasks} überfällig"
                else -> "${s.openTasks} Aufgaben heute fällig"
            },
            color = if (s.overdueTasks > 0) A.warn else A.muted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun RowScope.Health(label: String, value: String, ok: Boolean) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(A.panel.copy(alpha = 0.9f)).border(1.dp, A.line, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) GREEN else A.warn))
            Spacer(Modifier.width(6.dp))
            Mono(label, size = 10, spacing = 1.0)
        }
        Text(value, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Tile(title: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(if (on) ON_TILE else A.panel2).border(1.dp, if (on) A.blue else A.line, shape)
            .clickable(role = Role.Switch, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = if (on) A.blueLight else A.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VolumeRow(m: ControlModel, vm: HomeViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundBtn("−", "Leiser", 44) { vm.volume(-2) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("Lautstärke", color = A.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (!m.avr.on) "aus" else if (m.avr.muted) "stumm" else "${m.avr.volume ?: "–"} / ${m.avr.max}", style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = A.fg))
            }
            Bar(if (m.avr.on && !m.avr.muted) (m.avr.volume ?: 0) / m.avr.max.toFloat() else 0f, A.blue)
        }
        RoundBtn("+", "Lauter", 44) { vm.volume(2) }
    }
}

@Composable
private fun PrinterRow(p: Printer, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(A.panel2).border(1.dp, A.line, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("3D-Drucker · ${printerState(p.state)}", color = A.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (p.state == "printing" || p.state == "paused") Bar((p.progress / 100).toFloat(), if (p.state == "printing") A.warn else GRAY, 6)
        }
        if (p.state == "printing" || p.state == "paused") Text("${p.progress.roundToInt()} %", style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = Color(0xFFFFC46E)))
    }
}

@Composable
private fun AgendaRow(e: EventItem, s: HomeState) {
    val day = when {
        !e.startDay.isAfter(s.agendaToday) -> "heute"
        e.startDay == s.agendaToday.plusDays(1) -> "morgen"
        else -> Dates.wdShort(e.startDay)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (e.allDay || e.multiDay) "ganzt." else Dates.hm(e.startMin), style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = A.fg2), modifier = Modifier.width(48.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(chipColor(e.color)))
        Text(e.title, color = A.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(day, color = A.muted, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------------ Server

@Composable
private fun Server(s: HomeState, vm: HomeViewModel) {
    val m = s.model ?: run { Hint("Warte auf Daten von Alpenlan Control …"); return }
    val px = m.proxmox
    if (px == null || !px.configured) {
        Card { Text("Proxmox ist in Alpenlan Control noch nicht eingerichtet.", color = A.muted, fontSize = 14.sp) }
    } else if (!px.ok) {
        Card { CardHead("Proxmox", A.warn, "Fehler"); Text(px.error ?: "Keine Verbindung", color = A.warn, fontSize = 13.sp) }
    } else {
        run {
            Card {
                CardHead("Proxmox · ${px.node}", GREEN, "läuft seit ${days(px.uptime)}")
                Meter("CPU", px.cpu?.let { "${it.roundToInt()} %" } ?: "–", ((px.cpu ?: 0.0) / 100).toFloat(), A.blue)
                Meter("RAM", "${gb(px.memUsed)} / ${gb(px.memTotal)}", ((px.mem ?: 0.0) / 100).toFloat(), A.blue)
                px.disks.forEach { d -> Meter(d.name, "${gb(d.used)} / ${gb(d.total)}", ((d.pct ?: 0.0) / 100).toFloat(), if ((d.pct ?: 0.0) > 85) A.warn else GREEN) }
            }
            Card {
                Mono("Container & VMs", color = A.blue)
                px.guests.forEach { g ->
                    val open = s.openGuest == g.id
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.toggleGuest(g.id) }.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${g.type} ${g.id}", style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = A.muted), modifier = Modifier.width(56.dp))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (g.status == "running") GREEN else GRAY))
                            Text(g.name, color = A.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (g.status == "running") "${g.cpu.roundToInt()} %" else g.status, style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = A.fg2))
                        }
                        if (open) {
                            Text(
                                "Status: ${g.status} · läuft seit ${days(g.uptime)}" + (g.mem?.let { " · RAM ${it.roundToInt()} %" } ?: ""),
                                color = A.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 66.dp),
                            )
                        }
                    }
                }
            }
            px.backup?.let { b ->
                Card {
                    CardHead("Backups", if (b.lastOk == true) GREEN else A.warn, if (b.running) "läuft gerade" else null)
                    Text(
                        b.lastEnd?.let { "Letztes: ${Dates.wdShortDate(Dates.localDate(it * 1000))}, ${Dates.hm(Dates.localTime(it * 1000))} · " + if (b.lastOk == true) "erfolgreich" else "fehlgeschlagen" }
                            ?: "Noch kein Backup gefunden",
                        color = if (b.lastOk == false) A.warn else A.fg2, fontSize = 14.sp,
                    )
                    if (b.schedules.isNotEmpty()) Hint("Zeitplan: " + b.schedules.joinToString(", "))
                    else Hint("Kein Backup-Job geplant – richte in Proxmox unter Rechenzentrum → Backup einen Job ein.")
                }
            }
        }
    }
    if (m.services.isNotEmpty()) {
        Card {
            Mono("Dienste", color = A.blue)
            m.services.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { sv ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(A.panel2).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(if (sv.ok) GREEN else A.redSoft))
                                Spacer(Modifier.width(6.dp))
                                Text(sv.name, color = A.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(if (sv.ok) sv.ms?.let { "erreichbar · $it ms" } ?: "erreichbar" else "nicht erreichbar", color = if (sv.ok) A.muted else A.redSoft, fontSize = 12.sp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Meter(label: String, value: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(label, color = A.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(value, style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = A.fg))
        }
        Bar(fraction, color, 6)
    }
}

// ------------------------------------------------------------------ Apps

private data class AppLink(val name: String, val short: String, val color: Color, val open: (Context) -> Unit)

@Composable
private fun Apps(s: HomeState, vm: HomeViewModel) {
    val ctx = LocalContext.current
    val apps = listOf(
        AppLink("Kalender", "KAL", A.blueLight) { it.startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        AppLink("Kontakte", "KON", A.ok) { it.startActivity(Intent(it, ContactsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        AppLink("Mail", "TB", Color(0xFFFFC46E)) { launch(it, listOf("net.thunderbird.android", "net.thunderbird.android.beta", "com.fsck.k9"), "https://f-droid.org/packages/net.thunderbird.android/") },
        AppLink("Cloud", "NC", A.blueLight) { launch(it, listOf("com.nextcloud.client"), "https://cloud.alpenlan.com") },
        AppLink("Stream", "JF", A.redSoft) { launch(it, listOf("org.jellyfin.mobile"), null) },
        AppLink("Mainsail", "3D", Color(0xFFFFC46E)) { launch(it, emptyList(), "https://3d-drucker.alpenlan.com") },
    )
    apps.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { a ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(A.panel.copy(alpha = 0.9f)).border(1.dp, A.line, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { a.open(ctx) }.padding(vertical = 14.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(a.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text(a.short, style = MonoLabel.copy(fontSize = 15.sp, letterSpacing = 0.sp, color = a.color))
                    }
                    Text(a.name, color = A.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.wrapContentWidth())
                }
            }
        }
    }
    Card {
        Mono("Konto", color = A.blue)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.user ?: "Angemeldet", color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("über Authentik · control.alpenlan.com", color = A.muted, fontSize = 12.sp)
            }
            Mono(if (s.offline == null) "verbunden" else "offline", color = if (s.offline == null) GREEN else A.warn, size = 10, spacing = 0.6)
        }
        LinkButton("Abmelden", Modifier.fillMaxWidth()) { vm.logout() }
        Hint("Push-Benachrichtigungen (ntfy) und Widgets folgen in einer nächsten Version.")
    }
}

// ------------------------------------------------------------------ Kamera

@Composable
internal fun CameraView(s: HomeState, fullState: MutableState<Boolean> = remember { mutableStateOf(false) }) {
    val cam = s.camera
    var full by fullState
    val ratio = if (cam != null && cam.height > 0) cam.width.toFloat() / cam.height else 4f / 3f
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(Color.Black)
            .border(1.dp, A.line, RoundedCornerShape(12.dp))
            .clickable(enabled = cam != null, role = Role.Button, onClickLabel = "Vollbild") { full = true },
        contentAlignment = Alignment.Center,
    ) {
        if (cam != null) Image(cam, contentDescription = "Kamerabild des Druckers", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Mono("Kamera lädt …", size = 11)
        if (cam != null) CameraBadge(s, Modifier.align(Alignment.BottomEnd).padding(8.dp))
    }
    if (full && cam != null) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable(onClickLabel = "Schliessen") { full = false },
                contentAlignment = Alignment.Center,
            ) {
                val c = s.camera ?: cam
                Image(c, contentDescription = "Kamerabild des Druckers, Vollbild", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                CameraBadge(s, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
                Mono("Tippen zum Schliessen", size = 10, color = A.muted, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp))
            }
        }
    }
}

@Composable
private fun CameraBadge(s: HomeState, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xB3000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (s.cameraLive) GREEN else A.warn))
        Mono(if (s.cameraLive) "LIVE · ${s.cameraFps.roundToInt()} FPS" else "BILD", size = 10, spacing = 0.8, color = Color.White)
    }
}
