package ch.alpenlan.kalender.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore by preferencesDataStore(name = "alpenlan_settings")

enum class CalView { MONTH, WEEK, DAY }

data class AppSettings(
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val showWeekNumbers: Boolean = true,
    val defaultView: CalView = CalView.MONTH,
    val dayStartHour: Int = 7,
    val hourHeight: Int = 52,
    val defaultCalendarId: Long = -1,
    val defaultListId: Long = -1,
    val defaultDuration: Int = 60,
    val defaultReminder: Int = 15,
    val tasksInCalendar: Boolean = true,
    val doneTasksInCalendar: Boolean = false,
    val taskProvider: String = "auto",
    val hiddenCalendars: Set<Long> = emptySet(),
    val hiddenLists: Set<Long> = emptySet(),
    val notifications: Boolean = true,
    val setupDone: Boolean = false,
)

class SettingsStore(private val ctx: Context) {
    private object K {
        val firstDay = intPreferencesKey("first_day")
        val weekNumbers = booleanPreferencesKey("week_numbers")
        val defaultView = stringPreferencesKey("default_view")
        val dayStart = intPreferencesKey("day_start")
        val hourHeight = intPreferencesKey("hour_height")
        val defCal = longPreferencesKey("default_calendar")
        val defList = longPreferencesKey("default_list")
        val duration = intPreferencesKey("default_duration")
        val reminder = intPreferencesKey("default_reminder")
        val tasksInCal = booleanPreferencesKey("tasks_in_calendar")
        val doneInCal = booleanPreferencesKey("done_tasks_in_calendar")
        val provider = stringPreferencesKey("task_provider")
        val hiddenCals = stringSetPreferencesKey("hidden_calendars")
        val hiddenLists = stringSetPreferencesKey("hidden_lists")
        val notifications = booleanPreferencesKey("notifications")
        val setupDone = booleanPreferencesKey("setup_done")
    }

    val flow: Flow<AppSettings> = ctx.dataStore.data.map { p -> read(p) }

    private fun read(p: Preferences) = AppSettings(
        firstDayOfWeek = DayOfWeek.of(p[K.firstDay] ?: 1),
        showWeekNumbers = p[K.weekNumbers] ?: true,
        defaultView = runCatching { CalView.valueOf(p[K.defaultView] ?: "MONTH") }.getOrDefault(CalView.MONTH),
        dayStartHour = p[K.dayStart] ?: 7,
        hourHeight = p[K.hourHeight] ?: 52,
        defaultCalendarId = p[K.defCal] ?: -1,
        defaultListId = p[K.defList] ?: -1,
        defaultDuration = p[K.duration] ?: 60,
        defaultReminder = p[K.reminder] ?: 15,
        tasksInCalendar = p[K.tasksInCal] ?: true,
        doneTasksInCalendar = p[K.doneInCal] ?: false,
        taskProvider = p[K.provider] ?: "auto",
        hiddenCalendars = p[K.hiddenCals]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
        hiddenLists = p[K.hiddenLists]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
        notifications = p[K.notifications] ?: true,
        setupDone = p[K.setupDone] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        ctx.dataStore.edit { p ->
            val s = transform(read(p))
            p[K.firstDay] = s.firstDayOfWeek.value
            p[K.weekNumbers] = s.showWeekNumbers
            p[K.defaultView] = s.defaultView.name
            p[K.dayStart] = s.dayStartHour
            p[K.hourHeight] = s.hourHeight
            p[K.defCal] = s.defaultCalendarId
            p[K.defList] = s.defaultListId
            p[K.duration] = s.defaultDuration
            p[K.reminder] = s.defaultReminder
            p[K.tasksInCal] = s.tasksInCalendar
            p[K.doneInCal] = s.doneTasksInCalendar
            p[K.provider] = s.taskProvider
            p[K.hiddenCals] = s.hiddenCalendars.map { it.toString() }.toSet()
            p[K.hiddenLists] = s.hiddenLists.map { it.toString() }.toSet()
            p[K.notifications] = s.notifications
            p[K.setupDone] = s.setupDone
        }
    }
}
