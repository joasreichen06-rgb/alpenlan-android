@file:OptIn(ExperimentalLayoutApi::class)

package ch.alpenlan.kalender.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ch.alpenlan.kalender.reminders.ReminderEngine
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.BuildConfig
import ch.alpenlan.kalender.data.CalView
import ch.alpenlan.kalender.data.DAVX_PACKAGE
import ch.alpenlan.kalender.data.SyncHelper
import java.time.DayOfWeek

@Composable
fun SettingsScreen(s: UiState, vm: AppViewModel) {
    BackHandler { vm.setScreen(Screen.MAIN) }
    val ctx = LocalContext.current
    val st = s.settings
    var notifTick by remember { mutableIntStateOf(0) }
    var askedNotif by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifTick++; ReminderEngine.reschedule(ctx) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { notifTick++; onPauseOrDispose { } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.setScreen(Screen.MAIN) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = A.fg)
            }
            Text("EINSTELLUNGEN", fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = A.fg)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Ansicht") {
                Label("Startansicht")
                Pills(listOf(CalView.MONTH to "Monat", CalView.WEEK to "Woche", CalView.DAY to "Tag"), st.defaultView) { v -> vm.updateSettings { it.copy(defaultView = v) } }
                Label("Woche beginnt am")
                Pills(listOf(DayOfWeek.MONDAY to "Montag", DayOfWeek.SUNDAY to "Sonntag", DayOfWeek.SATURDAY to "Samstag"), st.firstDayOfWeek) { v -> vm.updateSettings { it.copy(firstDayOfWeek = v) } }
                SwitchRow("Kalenderwochen anzeigen", st.showWeekNumbers, { v -> vm.updateSettings { it.copy(showWeekNumbers = v) } })
                Label("Stundenraster beginnt um")
                Pills(listOf(0, 6, 7, 8, 9).map { it to "%02d:00".format(it) }, st.dayStartHour) { v -> vm.updateSettings { it.copy(dayStartHour = v) } }
                Label("Stundenhöhe")
                Pills(listOf(40 to "Kompakt", 52 to "Normal", 68 to "Gross"), st.hourHeight) { v -> vm.updateSettings { it.copy(hourHeight = v) } }
            }

            Section("Neue Termine") {
                Label("Standardkalender")
                if (s.writableCalendars.isEmpty()) Hint("Noch keine beschreibbaren Kalender – richte DAVx⁵ ein.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.writableCalendars.forEach { c ->
                        Pill(c.name, st.defaultCalendarId == c.id, { vm.updateSettings { it.copy(defaultCalendarId = c.id) } }, dot = dotColor(c.color))
                    }
                }
                Label("Standarddauer")
                Pills(listOf(30 to "30 Min.", 60 to "1 Std.", 90 to "1½ Std.", 120 to "2 Std."), st.defaultDuration) { v -> vm.updateSettings { it.copy(defaultDuration = v) } }
                Label("Standard-Erinnerung")
                Pills(listOf(-1 to "Keine", 0 to "Beginn", 10 to "10 Min.", 15 to "15 Min.", 30 to "30 Min.", 60 to "1 Std.", 1440 to "1 Tag"), st.defaultReminder) { v -> vm.updateSettings { it.copy(defaultReminder = v) } }
            }

            Section("Aufgaben") {
                Label("Aufgaben-App (Speicher für Nextcloud Tasks)")
                if (s.installedProviders.isEmpty()) {
                    Hint("Weder Tasks.org noch OpenTasks ist installiert.")
                    LinkButton("Tasks.org auf F-Droid") { SyncHelper.openUrl(ctx, SyncHelper.FDROID_TASKS_ORG) }
                } else {
                    Pills(listOf("auto" to "Automatisch") + s.installedProviders.map { it.authority to it.label }, st.taskProvider) { v -> vm.updateSettings { it.copy(taskProvider = v) } }
                    Hint("Aktiv: ${s.provider?.label ?: "–"}. In DAVx⁵ muss beim Konto dieselbe App für Aufgaben gewählt sein.")
                }
                Label("Standardliste")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.lists.forEach { l -> Pill(l.label, st.defaultListId == l.id, { vm.updateSettings { it.copy(defaultListId = l.id) } }, dot = dotColor(l.color)) }
                }
                SwitchRow("Aufgaben im Kalender anzeigen", st.tasksInCalendar, { v -> vm.updateSettings { it.copy(tasksInCalendar = v) } })
                SwitchRow("Erledigte Aufgaben im Kalender", st.doneTasksInCalendar, { v -> vm.updateSettings { it.copy(doneTasksInCalendar = v) } })
            }

            Section("Benachrichtigungen") {
                SwitchRow(
                    "Termin-Erinnerungen anzeigen", st.notifications, { v -> vm.updateSettings { it.copy(notifications = v) }; ReminderEngine.reschedule(ctx) },
                    subtitle = "Ausschalten, falls eine andere Kalender-App schon erinnert (sonst doppelt).",
                )
                val canNotify = remember(notifTick) { ReminderEngine.canNotify(ctx) }
                val canExact = remember(notifTick) { ReminderEngine.canExact(ctx) }
                if (!canNotify) {
                    Text("Benachrichtigungen sind für Alpenlan blockiert.", color = A.warn, fontSize = 13.sp)
                    LinkButton("Benachrichtigungen erlauben", primary = true) {
                        if (Build.VERSION.SDK_INT >= 33 && !askedNotif) { askedNotif = true; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                if (!canExact) {
                    Text("Exakte Wecker sind nicht erlaubt – Erinnerungen können sich verspäten.", color = A.warn, fontSize = 13.sp)
                    LinkButton("Wecker erlauben") {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                LinkButton("Test-Benachrichtigung senden") { ReminderEngine.test(ctx); notifTick++ }
                Hint("Tipp: Bei Samsung unter Einstellungen → Apps → Alpenlan Kalender → Akku „Nicht eingeschränkt“ wählen, sonst können Erinnerungen ausbleiben.")
                Hint("Aufgaben-Erinnerungen zeigt deine Aufgaben-App (${s.provider?.label ?: "Tasks.org/OpenTasks"}) an.")
            }

            Section("Synchronisation") {
                StatusLine("DAVx⁵", if (s.davxInstalled) "installiert" else "fehlt", s.davxInstalled)
                val accounts = s.davxAccounts
                StatusLine("Konten", if (accounts.isEmpty()) "keines" else accounts.joinToString { it.first }, accounts.isNotEmpty())
                StatusLine("Kalender", "${s.calendars.size}", s.calendars.isNotEmpty())
                StatusLine("Aufgabenlisten", "${s.lists.size}", s.lists.isNotEmpty())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkButton(if (s.syncing) "Synchronisiert…" else "Jetzt synchronisieren", Modifier.weight(1f)) { vm.requestSync() }
                    LinkButton("DAVx⁵ öffnen", Modifier.weight(1f)) {
                        if (!SyncHelper.openApp(ctx, DAVX_PACKAGE)) SyncHelper.openUrl(ctx, SyncHelper.FDROID_DAVX)
                    }
                }
                LinkButton("Einrichtung & Berechtigungen") { vm.setScreen(Screen.SETUP) }
            }

            Section("Über") {
                Text("Alpenlan Kalender ${BuildConfig.VERSION_NAME}", color = A.fg, fontWeight = FontWeight.SemiBold)
                Hint("Termine und Aufgaben liegen auf deinem Gerät und in deiner Alpenlan Cloud. Keine Google-Dienste, kein Tracking.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(A.panel.copy(alpha = 0.92f))
            .border(1.dp, A.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Mono(title, color = A.blue)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = A.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, color = A.muted, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun <T> Pills(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) -> Pill(label, v == selected, { onPick(v) }) }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (ok) A.ok else A.warn, 8.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, color = A.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = A.muted, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (primary) Modifier.background(A.blue) else Modifier.background(A.blue.copy(alpha = 0.10f)).border(1.dp, A.blue, RoundedCornerShape(12.dp)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) A.blueInk else A.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
