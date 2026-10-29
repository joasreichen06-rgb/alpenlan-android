package ch.alpenlan.kalender.home

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.alpenlan.kalender.data.CalendarRepository
import ch.alpenlan.kalender.data.EventItem
import ch.alpenlan.kalender.data.SettingsStore
import ch.alpenlan.kalender.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class HomeTab(val label: String) { OVERVIEW("Übersicht"), ROOM("Control"), SERVER("Server"), APPS("Apps") }

data class HomeState(
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    val user: String? = null,
    val authError: String? = null,
    val tab: HomeTab = HomeTab.OVERVIEW,
    val model: ControlModel? = null,
    val offline: String? = null,
    val updatedAt: Long = 0,
    val camera: ImageBitmap? = null,
    val cameraLive: Boolean = false,
    val cameraFps: Double = 0.0,
    val agenda: List<EventItem> = emptyList(),
    val agendaToday: LocalDate = LocalDate.now(),
    val openTasks: Int = 0,
    val overdueTasks: Int = 0,
    val toast: String? = null,
    val toastStamp: Long = 0,
    val openGuest: Int? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = Auth(app)
    private val api = ControlApi(auth)
    private val cal = CalendarRepository(app)
    private val tasks = TaskRepository(app)
    private val store = SettingsStore(app)

    private val _s = MutableStateFlow(HomeState(loggedIn = auth.loggedIn, user = auth.userName))
    val state: StateFlow<HomeState> = _s

    private var pollJob: Job? = null
    private var camJob: Job? = null

    fun login() = auth.loginIntent()

    fun handleRedirect(uri: Uri) {
        viewModelScope.launch {
            _s.update { it.copy(busy = true, authError = null) }
            runCatching { auth.handleRedirect(uri) }
                .onSuccess { _s.update { it.copy(loggedIn = true, busy = false, user = auth.userName) }; setActive(true) }
                .onFailure { e -> _s.update { it.copy(busy = false, authError = e.message ?: "Anmeldung fehlgeschlagen") } }
        }
    }

    fun logout() {
        auth.logout()
        pollJob?.cancel(); camJob?.cancel()
        _s.update { HomeState(loggedIn = false) }
    }

    fun setTab(t: HomeTab) {
        _s.update { it.copy(tab = t) }
        updateCamera()
    }

    fun toggleGuest(id: Int) = _s.update { it.copy(openGuest = if (it.openGuest == id) null else id) }

    private fun toast(t: String) = _s.update { it.copy(toast = t, toastStamp = System.nanoTime()) }
    fun info(t: String) = toast(t)

    // Feineinstellungen am Drucker: sofort anzeigen, gesammelt senden
    private val tunePending = HashMap<String, Int>()
    private var tuneJob: Job? = null
    fun tune(a: String, dir: Int) {
        val t = _s.value.model?.printer?.tune
        if (t == null || !t.ok) { toast("Drucker-Feineinstellung nicht erreichbar"); return }
        if (a == "prn_zoff" && _s.value.model?.printer?.state != "printing" && !t.homed.contains("z")) { toast("Z-Offset nur während des Drucks"); return }
        val nt = when (a) {
            "prn_speed" -> t.copy(speed = ((t.speed ?: 100) + 5 * dir).coerceIn(10, 300))
            "prn_flow" -> t.copy(flow = ((t.flow ?: 100) + dir).coerceIn(50, 150))
            "prn_fan" -> t.copy(fan = ((t.fan ?: 0) + 10 * dir).coerceIn(0, 100))
            "prn_zoff" -> t.copy(zoff = Math.round(((t.zoff ?: 0.0) + 0.01 * dir) * 1000) / 1000.0)
            else -> return
        }
        _s.update { s -> s.copy(model = s.model?.let { m -> m.copy(printer = m.printer.copy(tune = nt)) }) }
        tunePending[a] = (tunePending[a] ?: 0) + dir
        tuneJob?.cancel()
        tuneJob = viewModelScope.launch {
            delay(300)
            val batch = HashMap(tunePending); tunePending.clear()
            batch.forEach { (act, steps) -> if (steps != 0) runCatching { api.action(act, steps) } }
            delay(400)
            refresh()
        }
    }
    fun dismissToast() = _s.update { it.copy(toast = null) }

    /** Läuft nur, solange die App sichtbar ist. */
    fun setActive(active: Boolean) {
        pollJob?.cancel()
        camJob?.cancel()
        if (!active) return
        loadLocal()
        if (!_s.value.loggedIn) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(3000)
            }
        }
        updateCamera()
    }

    private fun updateCamera() {
        camJob?.cancel()
        val s = _s.value
        if (!s.loggedIn || s.tab != HomeTab.ROOM) return
        camJob = viewModelScope.launch {
            var lastFrame = 0L
            while (isActive) {
                if (_s.value.model?.printer?.camera != true) { delay(2000); continue }
                // 1) Live-Video versuchen
                val started = System.currentTimeMillis()
                runCatching {
                    api.stream { bmp ->
                        val now = System.currentTimeMillis()
                        val fps = if (lastFrame > 0) 1000.0 / (now - lastFrame).coerceAtLeast(1) else 0.0
                        lastFrame = now
                        _s.update { it.copy(camera = bmp.asImageBitmap(), cameraLive = true, cameraFps = (it.cameraFps * 0.8 + fps * 0.2)) }
                    }
                }
                if (!isActive) break
                _s.update { it.copy(cameraLive = false) }
                // Lief der Stream eine Weile, sofort neu verbinden; sonst 30 s Einzelbilder
                if (System.currentTimeMillis() - started > 15_000) continue
                val until = System.currentTimeMillis() + 30_000
                while (isActive && System.currentTimeMillis() < until) {
                    api.camera()?.let { bmp -> _s.update { it.copy(camera = bmp.asImageBitmap()) } }
                    delay(1500)
                }
            }
        }
    }

    suspend fun refresh() {
        try {
            val m = api.state()
            _s.update { it.copy(model = m, offline = null, updatedAt = System.currentTimeMillis()) }
        } catch (e: AuthError) {
            _s.update { it.copy(offline = e.message, loggedIn = auth.loggedIn) }
            if (!auth.loggedIn) pollJob?.cancel()
        } catch (e: Exception) {
            _s.update { it.copy(offline = "Alpenlan Control nicht erreichbar") }
        }
    }

    /** Termine der nächsten Tage und offene Aufgaben direkt vom Handy. */
    private fun loadLocal() {
        viewModelScope.launch {
            val settings = runCatching { store.flow.first() }.getOrNull()
            val today = LocalDate.now()
            val (agenda, open, overdue) = withContext(Dispatchers.IO) {
                val hidden = settings?.hiddenCalendars ?: emptySet()
                val evs = runCatching { cal.instances(today, today.plusDays(2)) }.getOrDefault(emptyList())
                    .filter { it.calendarId !in hidden }
                    .sortedWith(compareBy({ it.startDay.isBefore(today) && !it.allDay }, { maxOf(it.startDay, today) }, { !it.allDay }, { it.startMin }))
                val p = tasks.provider(settings?.taskProvider ?: "auto")
                val t = if (p != null) runCatching { tasks.tasks(p) }.getOrDefault(emptyList()) else emptyList()
                val openList = t.filter { !it.done && it.parentId == null && it.due != null && !it.due.isAfter(today) }
                Triple(evs.take(6), openList.size, openList.count { it.due!!.isBefore(today) })
            }
            _s.update { it.copy(agenda = agenda, agendaToday = today, openTasks = open, overdueTasks = overdue) }
        }
    }

    fun act(a: String, v: Any? = null, optimistic: ((ControlModel) -> ControlModel)? = null, msg: String? = null) {
        val m = _s.value.model
        if (m != null && optimistic != null) _s.update { it.copy(model = optimistic(m)) }
        viewModelScope.launch {
            val ok = runCatching { api.action(a, v) }.getOrElse { e ->
                toast(if (e is AuthError) e.message ?: "Anmeldung nötig" else "Keine Verbindung zu Control"); return@launch
            }
            if (!ok) toast("Befehl wurde nicht ausgeführt") else if (msg != null) toast(msg)
            delay(400)
            refresh()
        }
    }

    // ---------- bequeme Aktionen ----------
    fun pcToggle() {
        val on = _s.value.model?.pc?.online == true
        if (on) act("pc_off", msg = "PC fährt herunter – Anlage schaltet mit aus")
        else act("pc_on", optimistic = { it.copy(pc = it.pc.copy(booting = true)) }, msg = "PC wird gestartet – Anlage folgt")
    }

    fun volume(step: Int) {
        val avr = _s.value.model?.avr ?: return
        if (!avr.on) { toast("Anlage ist aus – Lautstärke schaltet sie nicht ein"); return }
        act("vol_step", step, optimistic = { m -> m.copy(avr = m.avr.copy(volume = ((m.avr.volume ?: 0) + step).coerceIn(0, m.avr.max), muted = false)) })
    }
}
