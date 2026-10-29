package ch.alpenlan.kalender.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.ui.A
import ch.alpenlan.kalender.ui.Mono
import ch.alpenlan.kalender.ui.MonoLabel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------ Belegung (wie am Stream Deck, ohne Control-Seite)

private data class DKey(val label: String, val icon: String, val action: String = "", val hold: String = "", val kind: String = "")
private data class DDial(
    val label: String, val icon: String, val rotate: String = "", val press: String = "", val touch: String = "",
    val big: Boolean = false, val altLabel: String? = null, val altRotate: String? = null,
)
private data class DPage(val id: String, val name: String, val keys: List<DKey>, val dials: List<DDial>)

private val NONE_K = DKey("", "")
private val NONE_D = DDial("", "")

private val PAGES = listOf(
    DPage("home", "Home", listOf(
        DKey("PC", "power", "pc_toggle", kind = "pc"), DKey("Licht", "light", "licht", kind = "licht"),
        DKey("Drucker", "printer", "page:drucker", kind = "printer"), DKey("Apps", "grid", "tab:apps"),
        DKey("Quellen", "disc", "input_step", kind = "avr"), DKey("Licht +", "palette", "licht", kind = "licht"),
        NONE_K, DKey("Server", "server", "tab:server", kind = "server"),
    ), listOf(
        DDial("Lautstärke", "speaker", "vol_step", "mute", "mute", big = true),
        DDial("Helligkeit", "light", "licht", "mode", "page:licht", altLabel = "Farbtemp.", altRotate = "licht"),
        NONE_D, NONE_D,
    )),
    DPage("licht", "Licht", listOf(
        DKey("Licht", "light", "licht", kind = "licht"), DKey("Hell", "light", "licht", kind = "licht"),
        DKey("Gemütlich", "light", "licht", kind = "licht"), DKey("Film", "film", "licht", kind = "licht"),
        DKey("Farbe", "palette", "licht", kind = "licht"), NONE_K,
        DKey("Alles aus", "power", "licht", kind = "licht"), DKey("Zurück", "back", "page:home", kind = "back"),
    ), listOf(
        DDial("Helligkeit", "light", "licht", "mode", "licht", altLabel = "Farbtemp.", altRotate = "licht"),
        DDial("Weiss", "light", "licht"), DDial("Farbe", "palette", "licht"), NONE_D,
    )),
    DPage("drucker", "Drucker", listOf(
        DKey("Status", "printer", "", kind = "printer"), DKey("Temp", "temp", "", kind = "temp"),
        DKey("Bewegen", "grid", "info:Achsen bewegen kommt zusammen mit dem Stream Deck"), DKey("Kamera", "camera", "camera", kind = "camera"),
        DKey("Pause", "pause", "prn_pause", "prn_cancel", kind = "pause"), DKey("Abbrechen", "stop", "prn_cancel", kind = "cancel"),
        DKey("Mainsail", "globe", "mainsail"), DKey("Zurück", "back", "page:home", kind = "back"),
    ), listOf(
        DDial("Tempo", "gauge", "prn_speed"), DDial("Fluss", "layers", "prn_flow"),
        DDial("Lüfter", "fan", "prn_fan"), DDial("Z-Offset", "gauge", "prn_zoff"),
    )),
)

private val STEP_DP = mapOf("vol_step" to 6f, "prn_speed" to 9f, "prn_flow" to 9f, "prn_fan" to 12f, "prn_zoff" to 14f)

// ------------------------------------------------------------------ Farben und Symbole

private object DC {
    val navy = Color(0xFF0F1A2E) to Color(0xFFE8EEF8)
    val blue = Color(0xFF4EA1FF) to Color(0xFF04101F)
    val green = Color(0xFF1F9E6A) to Color(0xFFEAFFF5)
    val amber = Color(0xFFE8A23A) to Color(0xFF1D1203)
    val red = Color(0xFFC9434A) to Color(0xFFFFF1F1)
    val slate = Color(0xFF2A3346) to Color(0xFFD6DEEA)
    val black = Color(0xFF05070B) to Color(0xFF7D8BA3)
    val empty = Color(0xFF0A0D13) to Color(0xFF4D5A72)
    val mutedBg = Color(0xFF1C0B10)
    val mutedText = Color(0xFFFF6B75)
    val mutedLabel = Color(0xFFFF9AA2)
}

private val ICONS = mapOf(
    "power" to "M12 3v8 M6.3 6.8a8 8 0 1 0 11.4 0",
    "light" to "M9 18h6 M10 21h4 M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z",
    "printer" to "M4 20h16 M6 20V9h12v11 M6 9l2-5h8l2 5 M12 9v5l-2 2h4",
    "grid" to "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z",
    "disc" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z",
    "palette" to "M12 3a9 9 0 0 0 0 18c1.2 0 1.8-.9 1.4-1.9-.5-1.2.3-2.1 1.5-2.1H17a4 4 0 0 0 4-4c0-5.5-4-10-9-10z M7.5 11h.01 M10 7h.01 M14.5 7h.01",
    "server" to "M5.5 4h13A1.5 1.5 0 0 1 20 5.5v4A1.5 1.5 0 0 1 18.5 11h-13A1.5 1.5 0 0 1 4 9.5v-4A1.5 1.5 0 0 1 5.5 4z M5.5 13h13a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-4A1.5 1.5 0 0 1 5.5 13z M8 7.5h.01 M8 16.5h.01",
    "home" to "M4 11l8-7 8 7v9H4z M10 20v-5h4v5",
    "speaker" to "M4 9v6h4l5 4V5L8 9z M16.5 8.5a5 5 0 0 1 0 7 M19 6a8.5 8.5 0 0 1 0 12",
    "mute" to "M4 9v6h4l5 4V5L8 9z M17 9l5 6 M22 9l-5 6",
    "pause" to "M8 5v14 M16 5v14",
    "play" to "M7 4l12 8-12 8z",
    "stop" to "M6 6h12v12H6z",
    "temp" to "M10 14V5a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0z M12 10v6",
    "globe" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c3 3.5 3 14.5 0 18 M12 3c-3 3.5-3 14.5 0 18",
    "camera" to "M4 8h3l2-3h6l2 3h3v11H4z M12 9.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z",
    "back" to "M15 5l-7 7 7 7",
    "film" to "M4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11A1.5 1.5 0 0 1 4.5 5z M7 5v14 M17 5v14 M3 9h4 M3 15h4 M17 9h4 M17 15h4",
    "fan" to "M12 10.5C12 6 9 4 7 5s0 5 5 5.5 M13.5 12c4.5 0 6.5-3 5.5-5s-5 0-5.5 5 M12 13.5c0 4.5 3 6.5 5 5.5s0-5-5-5.5 M10.5 12C6 12 4 15 5 17s5 0 5.5-5",
    "gauge" to "M4 17a8 8 0 1 1 16 0 M12 17l4-5",
    "layers" to "M12 4l9 5-9 5-9-5z M3 14l9 5 9-5",
)

@Composable
private fun deckIcon(name: String): ImageVector? {
    val d = ICONS[name] ?: return null
    return remember(name) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

// ------------------------------------------------------------------ Zustände aus dem Modell

private data class KeyLook(val colors: Pair<Color, Color>, val label: String, val status: String, val enabled: Boolean = true)

private fun lookOf(k: DKey, m: ControlModel, armed: Boolean): KeyLook {
    if (k.label.isEmpty()) return KeyLook(DC.empty, "", "", false)
    if (armed) return KeyLook(DC.red, "Nochmal?", "zum Bestätigen")
    val p = m.printer
    val printing = p.state == "printing"
    val paused = p.state == "paused"
    val pct = "${p.progress.roundToInt()} %"
    return when (k.kind) {
        "pc" -> when {
            m.pc.online -> KeyLook(DC.green, k.label, "Ein" + (m.pc.cpu?.let { " · CPU ${it.roundToInt()} %" } ?: ""))
            m.pc.booting -> KeyLook(DC.amber, "Startet", "startet …")
            else -> KeyLook(DC.black, k.label, "Aus")
        }
        "avr" -> if (m.avr.on) {
            val src = m.avr.sources.firstOrNull { it.id == m.avr.source }?.name ?: m.avr.source ?: ""
            KeyLook(DC.blue, k.label, src + " · " + (if (m.avr.muted) "stumm" else "${m.avr.volume ?: "–"}"))
        } else KeyLook(DC.black, k.label, "Aus · Druck = Ein")
        "licht" -> KeyLook(DC.black, k.label, "nicht verbunden", false)
        "printer" -> when {
            printing -> KeyLook(DC.amber, k.label, "$pct · druckt")
            paused -> KeyLook(DC.blue, k.label, "$pct · Pause")
            p.state == "complete" -> KeyLook(DC.green, "Fertig", "Druck fertig")
            p.state == "error" -> KeyLook(DC.red, k.label, "Fehler")
            p.state == "standby" || p.state == "cancelled" -> KeyLook(DC.slate, k.label, "bereit")
            else -> KeyLook(DC.black, k.label, "offline")
        }
        "pause" -> when {
            printing -> KeyLook(DC.blue, "Pause", "halten: abbrechen")
            paused -> KeyLook(DC.green, "Weiter", "halten: abbrechen")
            else -> KeyLook(DC.black, "–", "kein Druck", false)
        }
        "cancel" -> if (printing || paused) KeyLook(DC.red, k.label, "2× drücken") else KeyLook(DC.black, k.label, "kein Druck", false)
        "temp" -> KeyLook(DC.navy, k.label, "${p.nozzle?.roundToInt() ?: "–"} / ${p.bed?.roundToInt() ?: "–"} °C")
        "camera" -> if (p.camera) KeyLook(DC.navy, k.label, "Vollbild") else KeyLook(DC.black, k.label, "keine Kamera", false)
        "server" -> {
            val bad = m.services.count { !it.ok }
            if (bad == 0) KeyLook(DC.green, k.label, "alles ok") else KeyLook(DC.amber, k.label, "$bad Störung")
        }
        "back" -> KeyLook(DC.black, k.label, "")
        else -> KeyLook(DC.slate, k.label, "")
    }
}

private data class Face(
    val label: String, val value: String, val bar: Float, val muted: Boolean = false,
    val sub: String = "", val icon: String = "", val active: Boolean = true,
)

private fun faceOf(d: DDial, mode: Int, m: ControlModel): Face {
    if (d.label.isEmpty()) return Face("", "", 0f, active = false)
    val label = if (mode == 1 && d.altLabel != null) d.altLabel else d.label
    val t = m.printer.tune
    val tuneOk = t?.ok == true
    return when (if (mode == 1 && d.altRotate != null) d.altRotate else d.rotate) {
        "vol_step" -> {
            val v = m.avr.volume ?: 0
            val muted = m.avr.on && m.avr.muted
            Face(label, if (!m.avr.on) "Anlage aus" else if (muted) "Stumm" else "$v / ${m.avr.max}",
                if (m.avr.on) v / m.avr.max.toFloat() else 0f, muted, if (muted) "zurück auf $v" else "", if (muted) "mute" else "speaker")
        }
        "licht" -> Face(label, "nicht verbunden", 0f, icon = "light", active = false)
        "prn_speed" -> Face(label, if (tuneOk) "${t!!.speed} %" else "–", ((t?.speed ?: 100) / 200f), icon = "gauge", active = tuneOk)
        "prn_flow" -> Face(label, if (tuneOk) "${t!!.flow} %" else "–", ((t?.flow ?: 100) / 200f), icon = "layers", active = tuneOk)
        "prn_fan" -> Face(label, if (tuneOk) "${t!!.fan} %" else "–", ((t?.fan ?: 0) / 100f), icon = "fan", active = tuneOk)
        "prn_zoff" -> Face(label, if (tuneOk) "%+.3f mm".format(t!!.zoff ?: 0.0).replace('.', ',') else "–", 0f, icon = "gauge", active = tuneOk)
        else -> Face(label, "", 0f)
    }
}

private fun hintOf(d: DDial): String = when {
    d.label.isEmpty() -> ""
    d.press == "mute" -> "tippen: stumm"
    d.press == "mode" -> "tippen: umschalten"
    d.touch.startsWith("page:") -> "halten: ${PAGES.firstOrNull { it.id == d.touch.removePrefix("page:") }?.name ?: "Seite"}"
    else -> "⇆ ziehen"
}

// ------------------------------------------------------------------ Ansicht

@Composable
internal fun DeckControl(s: HomeState, vm: HomeViewModel) {
    val m = s.model ?: run { ch.alpenlan.kalender.ui.Hint("Warte auf Daten von Alpenlan Control …"); return }
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val modes = remember { mutableStateMapOf<String, Int>() }
    var overlay by remember { mutableStateOf<Int?>(null) }
    var overlayStamp by remember { mutableLongStateOf(0L) }
    var armed by remember { mutableStateOf<String?>(null) }
    val camFull = remember { mutableStateOf(false) }
    val p = PAGES[page.coerceIn(0, PAGES.lastIndex)]

    LaunchedEffect(overlayStamp) { if (overlay != null) { delay(1800); overlay = null } }
    LaunchedEffect(armed) { if (armed != null) { delay(2500); armed = null } }

    fun goPage(i: Int) { page = (i + PAGES.size) % PAGES.size; overlay = null }
    fun showOverlay(i: Int) { overlay = i; overlayStamp = System.nanoTime() }

    fun perform(a: String, dir: Int = 1, armKey: String? = null) {
        when {
            a.isEmpty() -> Unit
            a.startsWith("page:") -> PAGES.indexOfFirst { it.id == a.removePrefix("page:") }.takeIf { it >= 0 }?.let { goPage(it) }
            a == "tab:apps" -> vm.setTab(HomeTab.APPS)
            a == "tab:server" -> vm.setTab(HomeTab.SERVER)
            a.startsWith("info:") -> vm.info(a.removePrefix("info:"))
            a == "licht" -> vm.info("Licht ist noch nicht eingerichtet – kommt mit DMX")
            a == "pc_toggle" -> vm.pcToggle()
            a == "input_step" -> vm.act("input_step", dir, optimistic = { it.copy(avr = it.avr.copy(on = true)) },
                msg = if (!m.avr.on) "Anlage ein" else null)
            a == "mute" -> if (m.avr.on) vm.act("mute", optimistic = { it.copy(avr = it.avr.copy(muted = !it.avr.muted)) }) else vm.info("Anlage ist aus")
            a == "vol_step" -> vm.volume(dir)
            a == "prn_pause" -> when (m.printer.state) {
                "printing" -> vm.act("prn_pause", msg = "Druck pausiert")
                "paused" -> vm.act("prn_resume", msg = "Druck läuft weiter")
                else -> vm.info("Kein Druck aktiv")
            }
            a == "prn_cancel" -> {
                if (m.printer.state != "printing" && m.printer.state != "paused") { vm.info("Kein Druck aktiv"); return }
                if (armKey != null && armed != armKey) { armed = armKey; vm.info("Nochmal drücken zum Abbrechen"); return }
                armed = null
                vm.act("prn_cancel", msg = "Druck abgebrochen")
            }
            a.startsWith("prn_") -> vm.tune(a, dir)
            a == "camera" -> if (m.printer.camera) camFull.value = true
            a == "mainsail" -> openUrl(ctx, m.printer.web.ifBlank { "https://3d-drucker.alpenlan.com" })
        }
    }

    fun fireKey(i: Int, hold: Boolean) {
        val k = p.keys[i]
        val id = "${p.id}:$i"
        if (armed == id) { perform("prn_cancel", armKey = id); return }
        val a = if (hold) k.hold else k.action
        if (a.isEmpty()) return
        if (hold) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        perform(a, armKey = if (a == "prn_cancel") id else null)
    }

    // Seiten-Reiter
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PAGES.forEachIndexed { i, q ->
            val on = i == page
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
                    .then(if (on) Modifier.background(A.blue) else Modifier.border(1.dp, A.line2, RoundedCornerShape(17.dp)))
                    .clickable(role = Role.Tab, onClickLabel = "Seite ${q.name}") { goPage(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(q.name, color = if (on) A.blueInk else A.fg2, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }

    // Deck
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF121926), Color(0xFF0B1019))))
            .border(1.dp, Color(0xFF1C2738), RoundedCornerShape(22.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..3) {
                    val i = row * 4 + col
                    val k = p.keys[i]
                    DeckTile(
                        k, lookOf(k, m, armed == "${p.id}:$i"), Modifier.weight(1f), page,
                        onTap = { fireKey(i, false) }, onHold = { fireKey(i, true) },
                        onSwipe = { next -> goPage(page + if (next) 1 else -1) },
                    )
                }
            }
        }
        Box {
            val compact = p.dials.count { it.label.isNotEmpty() } > 2
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.dials.forEachIndexed { i, d ->
                    if (d.label.isEmpty()) return@forEachIndexed
                    val key = "${p.id}:$i"
                    val mode = modes[key] ?: 0
                    val f = faceOf(d, mode, m)
                    val rot = if (mode == 1 && d.altRotate != null) d.altRotate else d.rotate
                    DeckSlider(
                        f, if (d.press == "mode") (if (mode == 1) "2/2" else "1/2") else "", hintOf(d), overlay == i, STEP_DP[rot] ?: 10f,
                        Modifier.weight(1f), page, compact,
                        onStep = { dir -> if (f.active || rot == "vol_step") { perform(rot, dir); showOverlay(i) } },
                        onTap = {
                            when (d.press) {
                                "mode" -> { modes[key] = 1 - mode; showOverlay(i) }
                                "" -> Unit
                                else -> { perform(d.press); if (d.big) showOverlay(i) }
                            }
                        },
                        onHold = { if (d.touch.isNotEmpty()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); perform(d.touch) } },
                    )
                }
            }
            val oi = overlay
            if (oi != null) {
                // liegt genau über den Feldern; Berührungen gehen weiter an das Feld darunter
                Box(Modifier.matchParentSize()) { DeckOverlay(faceOf(p.dials[oi], modes["${p.id}:$oi"] ?: 0, m)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PAGES.indices.forEach { j ->
                Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape).background(if (j == page) A.blue else Color(0xFF2A3346)))
            }
        }
    }

    // Unter dem Deck: Kamera auf der Drucker-Seite, sonst die Kurzübersicht
    if (p.id == "drucker" && m.printer.camera) {
        CameraView(s, camFull)
        if (m.printer.file.isNotBlank()) Mono(m.printer.file, size = 10, spacing = 0.4)
    } else {
        Summary(m)
    }
    Text(
        "Tasten: tippen = Aktion, halten = zweite Funktion (Ecke), seitlich wischen = nächste Seite. Felder: seitlich ziehen = einstellen, tippen = drücken, halten = Zusatz.",
        color = A.dim, fontSize = 11.5.sp, lineHeight = 16.sp,
    )
}

@Composable
private fun DeckTile(
    k: DKey, look: KeyLook, modifier: Modifier, page: Int,
    onTap: () -> Unit, onHold: () -> Unit, onSwipe: (next: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val swipe by rememberUpdatedState(onSwipe)
    val hasHold = k.hold.isNotEmpty()
    val icon = deckIcon(k.icon)
    val (bg, fg) = look.colors
    Box(
        modifier.aspectRatio(1f).scale(scale).clip(RoundedCornerShape(16.dp)).background(bg)
            .border(1.dp, Color(0xFF232A38), RoundedCornerShape(16.dp))
            .semantics { contentDescription = (k.label.ifEmpty { "leere Taste" }) + (if (look.status.isNotEmpty()) ", ${look.status}" else "") + (if (hasHold) ", gedrückt halten für zweite Funktion" else "") }
            .pointerInput(page, k) {
                val slop = with(density) { 10.dp.toPx() }
                val swipeMin = with(density) { 45.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val t0 = System.currentTimeMillis()
                    var moved = false
                    var held = false
                    var last = down.position
                    while (true) {
                        val ev = if (hasHold && !moved && !held) {
                            val left = 550L - (System.currentTimeMillis() - t0)
                            if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                        } else awaitPointerEvent()
                        if (ev == null) { held = true; pressed = false; hold(); continue }
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        last = c.position
                        if ((last - down.position).getDistance() > slop) moved = true
                        if (!c.pressed) break
                    }
                    pressed = false
                    val dx = last.x - down.position.x
                    val dy = last.y - down.position.y
                    if (abs(dx) > swipeMin && abs(dx) > abs(dy)) swipe(dx < 0)
                    else if (!moved && !held) tap()
                }
            }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            if (look.label.isNotEmpty()) Text(look.label, color = fg, fontSize = fs(11.5f), lineHeight = fs(13f), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (look.status.isNotEmpty()) Text(look.status, style = MonoLabel.copy(fontSize = fs(8.5f), lineHeight = fs(10f), letterSpacing = 0.sp, color = fg.copy(alpha = 0.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hasHold) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(2.dp).size(8.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp)).background(fg.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun DeckSlider(
    f: Face, modeTag: String, hint: String, active: Boolean, stepDp: Float, modifier: Modifier, page: Int, compact: Boolean,
    onStep: (Int) -> Unit, onTap: () -> Unit, onHold: () -> Unit,
) {
    val density = LocalDensity.current
    val step by rememberUpdatedState(onStep)
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val enabled = f.label.isNotEmpty()
    val shape = RoundedCornerShape(14.dp)
    val bar = f.bar.coerceIn(0f, 1f)
    Box(
        modifier.height(92.dp).clip(shape)
            .background(if (f.muted) DC.mutedBg else Color(0xFF0B1220))
            .border(1.dp, if (active) A.blue else if (f.muted) Color(0xFF5A1F29) else Color(0xFF1C2738), shape)
            .semantics { contentDescription = if (enabled) "${f.label}: ${f.value}. Seitlich ziehen zum Einstellen, tippen zum Drücken, halten für die Zusatzfunktion" else "leeres Feld" }
            .then(if (!enabled) Modifier else Modifier.pointerInput(page, f.label, stepDp) {
                val slop = with(density) { 8.dp.toPx() }
                val stepPx = with(density) { stepDp.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t0 = System.currentTimeMillis()
                    var moved = false
                    var held = false
                    var acc = 0f
                    while (true) {
                        val ev = if (!moved && !held) {
                            val left = 550L - (System.currentTimeMillis() - t0)
                            if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                        } else awaitPointerEvent()
                        if (ev == null) { held = true; hold(); continue }
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        val tdx = c.position.x - down.position.x
                        val tdy = c.position.y - down.position.y
                        if (!moved && !held && abs(tdx) > slop && abs(tdx) > abs(tdy)) moved = true
                        if (!moved && !held && abs(tdy) > slop) held = true // senkrecht: der Seite das Scrollen überlassen
                        if (moved) {
                            acc += c.position.x - c.previousPosition.x
                            c.consume()
                            while (abs(acc) >= stepPx) {
                                val dir = if (acc > 0) 1 else -1
                                acc -= dir * stepPx
                                step(dir)
                            }
                        }
                    }
                    if (!moved && !held) tap()
                }
            }),
    ) {
        if (enabled && bar > 0f) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(bar).background(
                    if (f.muted) Brush.linearGradient(listOf(Color(0xE66B2A33), Color(0xE64A1C24)))
                    else SolidColor(Color(0x384DA3FF))
                )
            )
        }
        Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.label.ifEmpty { "—" }.uppercase(), style = MonoLabel.copy(fontSize = fs(if (compact) 8f else 9f), letterSpacing = 0.6.sp, color = if (f.muted) DC.mutedLabel else Color(0xFF7D8BA3)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (modeTag.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(modeTag, style = MonoLabel.copy(fontSize = fs(8f), letterSpacing = 0.sp, color = A.blueLight)) }
            }
            Text(
                if (f.muted) f.value.uppercase() else f.value,
                color = if (f.muted) DC.mutedText else if (f.active) A.fg else A.dim,
                fontSize = fs(if (compact) 13.5f else 18f), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(hint, style = MonoLabel.copy(fontSize = fs(if (compact) 7.5f else 8.5f), letterSpacing = 0.sp, color = Color(0xFF6F7C92)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeckOverlay(f: Face) {
    val icon = deckIcon(f.icon.ifEmpty { "gauge" })
    Row(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
            .background(if (f.muted) Brush.horizontalGradient(listOf(Color(0xFF3A1118), DC.mutedBg)) else SolidColor(Color(0xFF0D1626)))
            .border(1.dp, Color(0xFF2B3B55), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (f.muted) Color(0xFFFF5D68) else A.blue, modifier = Modifier.size(24.dp))
        Column {
            Text(f.label.uppercase(), style = MonoLabel.copy(fontSize = fs(9.5f), letterSpacing = 1.2.sp, color = if (f.muted) DC.mutedLabel else Color(0xFF7D8BA3)))
            Text(if (f.muted) f.value.uppercase() else f.value, color = if (f.muted) DC.mutedText else A.fg, fontSize = fs(24f), fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        if (f.sub.isNotEmpty()) Text(f.sub, style = MonoLabel.copy(fontSize = fs(10f), letterSpacing = 0.sp, color = DC.mutedLabel), maxLines = 1)
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A2438))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(f.bar.coerceIn(0f, 1f)).background(if (f.muted) Color(0xFF6B2A33) else A.blue))
        }
    }
}

@Composable
private fun Summary(m: ControlModel) {
    val src = m.avr.sources.firstOrNull { it.id == m.avr.source }?.name ?: m.avr.source ?: ""
    val p = m.printer
    val items = listOf(
        Triple("PC", if (m.pc.online) "Ein" + (m.pc.cpu?.let { " · CPU ${it.roundToInt()} %" } ?: "") else if (m.pc.booting) "Startet …" else "Aus",
            if (m.pc.online) A.ok else if (m.pc.booting) A.warn else Color(0xFF5A6B88)),
        Triple("Anlage", if (m.avr.on) "$src · " + (if (m.avr.muted) "stumm" else "${m.avr.volume ?: "–"} / ${m.avr.max}") else "Aus",
            if (m.avr.on) (if (m.avr.muted) Color(0xFFFF5D68) else A.blue) else Color(0xFF5A6B88)),
        Triple("Licht", "nicht verbunden", Color(0xFF5A6B88)),
        Triple("Drucker", when (p.state) {
            "printing" -> "${p.progress.roundToInt()} % · ${p.remainMin / 60} h ${p.remainMin % 60}"
            "paused" -> "Pausiert · ${p.progress.roundToInt()} %"
            "complete" -> "Fertig"; "standby", "cancelled" -> "Bereit"; "error" -> "Fehler"; else -> "offline"
        }, when (p.state) { "printing" -> A.warn; "paused" -> A.blue; "complete" -> A.ok; "error" -> Color(0xFFFF5D68); else -> Color(0xFF5A6B88) }),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value, dot) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(A.panel.copy(alpha = 0.9f))
                            .border(1.dp, A.line, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                            Spacer(Modifier.width(6.dp))
                            Mono(label, size = 9, spacing = 1.0)
                        }
                        Text(value, color = A.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Schriftgrösse wie auf einem Gerätedisplay: grössere System-Schrift soll das Deck nicht sprengen. */
@Composable
private fun fs(v: Float) = (v / LocalDensity.current.fontScale.coerceAtLeast(1f)).sp
