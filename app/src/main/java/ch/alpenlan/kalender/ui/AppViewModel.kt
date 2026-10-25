package ch.alpenlan.kalender.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.alpenlan.kalender.data.AppSettings
import ch.alpenlan.kalender.data.CalView
import ch.alpenlan.kalender.data.CalendarInfo
import ch.alpenlan.kalender.data.CalendarRepository
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.EventDraft
import ch.alpenlan.kalender.data.EventItem
import ch.alpenlan.kalender.data.SettingsStore
import ch.alpenlan.kalender.data.SyncHelper
import ch.alpenlan.kalender.data.TaskDraft
import ch.alpenlan.kalender.data.TaskItem
import ch.alpenlan.kalender.data.TaskList
import ch.alpenlan.kalender.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class Screen { MAIN, SETTINGS, SETUP }
enum class Tab { CALENDAR, TASKS }
enum class TaskFilter(val label: String) { TODAY("Heute"), PLANNED("Geplant"), ALL("Alle offen"), DONE("Erledigt") }

sealed interface Sheet {
    data class Day(val date: LocalDate) : Sheet
    data class Event(val item: EventItem) : Sheet
    data class Task(val id: Long) : Sheet
    data class EditEvent(val draft: EventDraft, val oldCalendarId: Long) : Sheet
    data class EditTask(val draft: TaskDraft, val oldListId: Long) : Sheet
    data object Visibility : Sheet
}

data class ToastMsg(val text: String, val undoKey: String? = null, val stamp: Long = System.nanoTime())

data class UiState(
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val screen: Screen = Screen.MAIN,
    val tab: Tab = Tab.CALENDAR,
    val mode: CalView = CalView.MONTH,
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val calendars: List<CalendarInfo> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val provider: TaskRepository.Provider? = null,
    val installedProviders: List<TaskRepository.Provider> = emptyList(),
    val calPermission: Boolean = false,
    val taskPermission: Boolean = false,
    val davxInstalled: Boolean = false,
    val syncing: Boolean = false,
    val pendingEventDeletes: Set<Long> = emptySet(),
    val pendingTaskDeletes: Set<Long> = emptySet(),
    val toast: ToastMsg? = null,
    val sheet: Sheet? = null,
    val taskFilter: TaskFilter = TaskFilter.TODAY,
) {
    val visibleEvents: List<EventItem>
        get() = events.filter { it.calendarId !in settings.hiddenCalendars && it.eventId !in pendingEventDeletes }
    val liveTasks: List<TaskItem>
        get() = tasks.filter { it.id !in pendingTaskDeletes && it.parentId !in pendingTaskDeletes }
    val calendarTasks: List<TaskItem>
        get() = if (!settings.tasksInCalendar) emptyList() else liveTasks.filter {
            it.due != null && it.listId !in settings.hiddenLists && (settings.doneTasksInCalendar || !it.done)
        }
    val writableCalendars: List<CalendarInfo> get() = calendars.filter { it.writable }
    fun calendar(id: Long) = calendars.firstOrNull { it.id == id }
    fun list(id: Long) = lists.firstOrNull { it.id == id }
    fun listColor(id: Long): Int = list(id)?.color ?: 0xFF9AA6B8.toInt()
    val davxAccounts: List<Pair<String, String>>
        get() = (calendars.map { it.accountName to it.accountType } + lists.map { it.accountName to it.accountType })
            .filter { it.second == ch.alpenlan.kalender.data.DAVX_ACCOUNT_TYPE }.distinct()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val calRepo = CalendarRepository(app)
    private val taskRepo = TaskRepository(app)
    private val store = SettingsStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val handler = Handler(Looper.getMainLooper())
    private var reloadJob: Job? = null
    private var syncJob: Job? = null
    private val deleteJobs = mutableMapOf<String, Job>()
    private var registeredTaskUri: android.net.Uri? = null

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }

    init {
        viewModelScope.launch {
            val first = store.flow.first()
            _state.update {
                it.copy(
                    settings = first, settingsLoaded = true, mode = first.defaultView,
                    screen = if (first.setupDone) Screen.MAIN else Screen.SETUP,
                )
            }
            refreshAll()
            store.flow.collect { s ->
                val providerChanged = s.taskProvider != _state.value.settings.taskProvider
                _state.update { it.copy(settings = s) }
                if (providerChanged) refreshAll()
            }
        }
        runCatching { app.contentResolver.registerContentObserver(CalendarRepository.OBSERVE_URI, true, observer) }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
    }

    // ---------------- Laden ----------------

    fun onResume() {
        _state.update { it.copy(today = LocalDate.now()) }
        refreshAll()
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun refreshAll() {
        viewModelScope.launch { load() }
    }

    private fun loadRange(s: UiState): Pair<LocalDate, LocalDate> {
        val first = s.settings.firstDayOfWeek
        val gridStart = Dates.weekStart(s.month.atDay(1), first)
        val weekStart = Dates.weekStart(s.selected, first)
        val from = minOf(gridStart, weekStart).minusDays(7)
        val to = maxOf(gridStart.plusDays(42), weekStart.plusDays(7)).plusDays(7)
        return from to to
    }

    private suspend fun load() {
        val app = getApplication<Application>()
        val s0 = _state.value
        val (from, to) = loadRange(s0)
        val result = withContext(Dispatchers.IO) {
            val provider = taskRepo.provider(s0.settings.taskProvider)
            val installed = taskRepo.installedProviders()
            val calPerm = calRepo.canRead()
            val taskPerm = provider != null && taskRepo.canRead(provider)
            val cals = runCatching { calRepo.calendars() }.getOrDefault(emptyList())
            val evs = runCatching { calRepo.instances(from, to) }.getOrDefault(emptyList())
            val lists = if (provider != null) runCatching { taskRepo.lists(provider) }.getOrDefault(emptyList()) else emptyList()
            val tasks = if (provider != null) runCatching { taskRepo.tasks(provider) }.getOrDefault(emptyList()) else emptyList()
            LoadResult(provider, installed, calPerm, taskPerm, cals, evs, lists, tasks, SyncHelper.isDavxInstalled(app))
        }
        val p = result.provider
        if (p != null) {
            val uri = taskRepo.observeUri(p)
            if (uri != registeredTaskUri) {
                runCatching {
                    app.contentResolver.registerContentObserver(uri, true, observer)
                    registeredTaskUri = uri
                }
            }
        }
        _state.update {
            it.copy(
                provider = result.provider, installedProviders = result.installed,
                calPermission = result.calPerm, taskPermission = result.taskPerm,
                calendars = result.calendars, events = result.events, lists = result.lists, tasks = result.tasks,
                davxInstalled = result.davx,
                sheet = refreshSheet(it.sheet, result.events),
            )
        }
    }

    private fun refreshSheet(sheet: Sheet?, events: List<EventItem>): Sheet? = when (sheet) {
        is Sheet.Event -> events.firstOrNull { it.instanceId == sheet.item.instanceId }?.let { Sheet.Event(it) }
            ?: events.firstOrNull { it.eventId == sheet.item.eventId }?.let { Sheet.Event(it) }
            ?: sheet
        else -> sheet
    }

    private data class LoadResult(
        val provider: TaskRepository.Provider?, val installed: List<TaskRepository.Provider>,
        val calPerm: Boolean, val taskPerm: Boolean,
        val calendars: List<CalendarInfo>, val events: List<EventItem>,
        val lists: List<TaskList>, val tasks: List<TaskItem>, val davx: Boolean,
    )

    // ---------------- Navigation ----------------

    fun setScreen(s: Screen) = _state.update { it.copy(screen = s, sheet = null) }
    fun setTab(t: Tab) = _state.update { it.copy(tab = t, sheet = null) }
    fun setMode(m: CalView) = _state.update { it.copy(mode = m) }
    fun setTaskFilter(f: TaskFilter) = _state.update { it.copy(taskFilter = f) }

    fun select(d: LocalDate, openDaySheet: Boolean = false, mode: CalView? = null) {
        val before = _state.value
        _state.update {
            it.copy(
                selected = d, month = YearMonth.from(d),
                sheet = if (openDaySheet) Sheet.Day(d) else it.sheet,
                mode = mode ?: it.mode,
            )
        }
        if (YearMonth.from(d) != before.month || mode != null) refreshAll()
    }

    fun goToday() = select(LocalDate.now())

    fun shift(dir: Int) {
        val s = _state.value
        when (s.mode) {
            CalView.MONTH -> {
                val m = s.month.plusMonths(dir.toLong())
                val sel = if (YearMonth.from(s.today) == m) s.today else m.atDay(1)
                _state.update { it.copy(month = m, selected = sel) }
                refreshAll()
            }
            CalView.WEEK -> select(s.selected.plusWeeks(dir.toLong())).also { refreshAll() }
            CalView.DAY -> select(s.selected.plusDays(dir.toLong())).also { refreshAll() }
        }
    }

    fun openSheet(sheet: Sheet?) = _state.update { it.copy(sheet = sheet) }
    fun closeSheet() = _state.update { it.copy(sheet = null) }
    fun dismissToast() = _state.update { it.copy(toast = null) }
    private fun toast(text: String, undoKey: String? = null) = _state.update { it.copy(toast = ToastMsg(text, undoKey)) }

    // ---------------- Termine ----------------

    fun newEventDraft(date: LocalDate = _state.value.selected, startHour: Int? = null): Sheet.EditEvent? {
        val s = _state.value
        val cal = s.writableCalendars.firstOrNull { it.id == s.settings.defaultCalendarId && it.id !in s.settings.hiddenCalendars }
            ?: s.writableCalendars.firstOrNull { it.id !in s.settings.hiddenCalendars }
            ?: s.writableCalendars.firstOrNull()
            ?: return null
        val now = LocalTime.now()
        val start = if (startHour != null) LocalTime.of(startHour, 0) else LocalTime.of(minOf(22, now.hour + 1), 0)
        val end = start.plusMinutes(s.settings.defaultDuration.toLong()).let { if (it.isBefore(start)) LocalTime.of(23, 59) else it }
        return Sheet.EditEvent(
            EventDraft(calendarId = cal.id, title = "", startDate = date, endDate = date, startTime = start, endTime = end, reminderMinutes = s.settings.defaultReminder),
            oldCalendarId = cal.id,
        )
    }

    fun editEventDraft(e: EventItem): Sheet.EditEvent {
        val reminder = calRepo.reminderMinutes(e.eventId)
        return Sheet.EditEvent(
            EventDraft(
                eventId = e.eventId, calendarId = e.calendarId, title = e.title, location = e.location ?: "",
                description = e.description ?: "", allDay = e.allDay,
                startDate = e.startDay, endDate = e.endDay,
                startTime = if (e.allDay) LocalTime.of(18, 0) else LocalTime.of(e.startMin / 60, e.startMin % 60),
                endTime = if (e.allDay) LocalTime.of(19, 0) else LocalTime.of(minOf(23, e.endMin / 60), if (e.endMin >= 24 * 60) 59 else e.endMin % 60),
                recurring = e.recurring, reminderMinutes = reminder,
            ),
            oldCalendarId = e.calendarId,
        )
    }

    fun saveEvent(edit: Sheet.EditEvent) {
        val d = edit.draft
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { if (d.eventId == null) calRepo.insert(d) else calRepo.update(d, edit.oldCalendarId) }.getOrNull()
            }
            if (id == null) {
                toast("Speichern fehlgeschlagen – Berechtigung prüfen")
                return@launch
            }
            store.update { s -> s.copy(hiddenCalendars = s.hiddenCalendars - d.calendarId) }
            _state.update { it.copy(sheet = null, selected = d.startDate, month = YearMonth.from(d.startDate)) }
            toast(if (d.eventId == null) "Termin erstellt" else "Termin gespeichert")
            load()
            requestSync(quiet = true)
        }
    }

    fun deleteEvent(e: EventItem) {
        val key = "e${e.eventId}"
        _state.update { it.copy(pendingEventDeletes = it.pendingEventDeletes + e.eventId, sheet = null) }
        toast(if (e.recurring) "Serie gelöscht" else "Termin gelöscht", key)
        deleteJobs[key] = viewModelScope.launch {
            delay(5000)
            withContext(Dispatchers.IO) { runCatching { calRepo.delete(e.eventId) } }
            deleteJobs.remove(key)
            _state.update { it.copy(pendingEventDeletes = it.pendingEventDeletes - e.eventId) }
            load()
            requestSync(quiet = true)
        }
    }

    fun undo(key: String) {
        deleteJobs.remove(key)?.cancel()
        val id = key.drop(1).toLongOrNull() ?: return
        _state.update {
            if (key.startsWith("e")) it.copy(pendingEventDeletes = it.pendingEventDeletes - id, toast = null)
            else it.copy(pendingTaskDeletes = it.pendingTaskDeletes - id, toast = null)
        }
    }

    // ---------------- Aufgaben ----------------

    fun newTaskDraft(due: LocalDate?, parentId: Long? = null, listId: Long? = null): Sheet.EditTask? {
        val s = _state.value
        val list = listId?.let { s.list(it) }
            ?: s.lists.firstOrNull { it.id == s.settings.defaultListId }
            ?: s.lists.firstOrNull { it.id !in s.settings.hiddenLists }
            ?: s.lists.firstOrNull()
            ?: return null
        return Sheet.EditTask(TaskDraft(listId = list.id, title = "", due = due, parentId = parentId), list.id)
    }

    fun editTaskDraft(t: TaskItem) = Sheet.EditTask(
        TaskDraft(taskId = t.id, listId = t.listId, title = t.title, description = t.description ?: "", due = t.due, highPriority = t.highPriority, parentId = t.parentId),
        t.listId,
    )

    fun saveTask(edit: Sheet.EditTask) {
        val p = _state.value.provider ?: return
        val d = edit.draft
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { if (d.taskId == null) taskRepo.insert(p, d) else taskRepo.update(p, d, edit.oldListId) }.getOrNull()
            }
            if (id == null) {
                toast("Speichern fehlgeschlagen – Berechtigung prüfen")
                return@launch
            }
            store.update { s -> s.copy(hiddenLists = s.hiddenLists - d.listId) }
            _state.update { it.copy(sheet = Sheet.Task(id)) }
            toast(if (d.taskId == null) "Aufgabe erstellt" else "Aufgabe gespeichert")
            load()
            requestSync(quiet = true)
        }
    }

    fun addSubtask(parent: TaskItem, title: String) {
        val p = _state.value.provider ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { taskRepo.insert(p, TaskDraft(listId = parent.listId, title = title.trim(), parentId = parent.id)) }
            }
            load()
        }
    }

    fun toggleTask(t: TaskItem) {
        val p = _state.value.provider ?: return
        _state.update { s -> s.copy(tasks = s.tasks.map { if (it.id == t.id) it.copy(done = !t.done) else it }) }
        if (!t.done) toast("Erledigt: ${t.title}")
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { taskRepo.setDone(p, t.id, !t.done) } }
            load()
            requestSync(quiet = true)
        }
    }

    fun deleteTask(t: TaskItem) {
        val p = _state.value.provider ?: return
        val key = "t${t.id}"
        _state.update { it.copy(pendingTaskDeletes = it.pendingTaskDeletes + t.id, sheet = null) }
        toast("Aufgabe gelöscht", key)
        deleteJobs[key] = viewModelScope.launch {
            delay(5000)
            withContext(Dispatchers.IO) {
                runCatching {
                    _state.value.tasks.filter { it.parentId == t.id }.forEach { sub -> taskRepo.delete(p, sub.id) }
                    taskRepo.delete(p, t.id)
                }
            }
            deleteJobs.remove(key)
            _state.update { it.copy(pendingTaskDeletes = it.pendingTaskDeletes - t.id) }
            load()
            requestSync(quiet = true)
        }
    }

    // ---------------- Einstellungen & Sync ----------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.update(transform) }
    }

    fun toggleCalendar(id: Long) = updateSettings { s ->
        s.copy(hiddenCalendars = if (id in s.hiddenCalendars) s.hiddenCalendars - id else s.hiddenCalendars + id)
    }

    fun toggleList(id: Long) = updateSettings { s ->
        s.copy(hiddenLists = if (id in s.hiddenLists) s.hiddenLists - id else s.hiddenLists + id)
    }

    fun finishSetup() {
        updateSettings { it.copy(setupDone = true) }
        _state.update { it.copy(screen = Screen.MAIN) }
        refreshAll()
    }

    fun requestSync(quiet: Boolean = false) {
        val s = _state.value
        val accounts = s.davxAccounts
        if (accounts.isEmpty()) {
            if (!quiet) toast("Kein DAVx⁵-Konto gefunden")
            return
        }
        val ok = SyncHelper.requestSync(accounts, s.provider?.authority)
        if (quiet) return
        if (!ok) {
            toast("Sync konnte nicht gestartet werden")
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            delay(1200)
            var waited = 0
            while (waited < 60 && withContext(Dispatchers.IO) { SyncHelper.isSyncing(accounts, s.provider?.authority) }) {
                delay(1000); waited++
            }
            _state.update { it.copy(syncing = false) }
            load()
        }
    }
}
