package ch.alpenlan.kalender.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.data.DAVX_PACKAGE
import ch.alpenlan.kalender.data.SyncHelper

@Composable
fun SetupScreen(s: UiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    BackHandler(enabled = s.settings.setupDone) { vm.setScreen(Screen.MAIN) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshAll() }
    val perms = buildList {
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.WRITE_CALENDAR)
        s.installedProviders.forEach { add(it.readPermission); add(it.writePermission) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val hasAccount = s.davxAccounts.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlpenlanLogo(Modifier.size(40.dp, 22.dp))
            Spacer(Modifier.width(10.dp))
            Mono("Alpenlan Kalender", color = A.blue)
        }
        Text(
            buildAnnotatedString {
                append("EINRICH")
                withStyle(SpanStyle(color = A.blue)) { append("TUNG.") }
            },
            fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = A.fg,
        )
        Hint("Die App zeigt Termine und Aufgaben aus deiner Alpenlan Cloud. Synchronisiert wird über DAVx⁵ – ohne Google-Dienste.")

        Step(
            n = 1, done = s.davxInstalled, title = "DAVx⁵ installieren",
            text = "Synchronisiert Kalender und Aufgaben mit Nextcloud. Gibt es gratis auf F-Droid.",
        ) {
            if (!s.davxInstalled) LinkButton("DAVx⁵ auf F-Droid") { SyncHelper.openUrl(ctx, SyncHelper.FDROID_DAVX) }
        }
        Step(
            n = 2, done = s.installedProviders.isNotEmpty(), title = "Aufgaben-App installieren",
            text = "Für Nextcloud Tasks: Tasks.org oder OpenTasks. Alpenlan nutzt deren Speicher, du musst sie nicht öffnen.",
        ) {
            if (s.installedProviders.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkButton("Tasks.org", Modifier.weight(1f)) { SyncHelper.openUrl(ctx, SyncHelper.FDROID_TASKS_ORG) }
                    LinkButton("OpenTasks", Modifier.weight(1f)) { SyncHelper.openUrl(ctx, SyncHelper.FDROID_OPENTASKS) }
                }
            }
        }
        Step(
            n = 3, done = hasAccount, title = "Konto in DAVx⁵ anlegen",
            text = "In DAVx⁵: + → „Mit URL und Benutzername anmelden“ → URL https://cloud.alpenlan.com → bei Nextcloud „Mit Browser anmelden“ (läuft über Authentik). Danach Kalender und Aufgabenlisten anhaken und als Aufgaben-App ${s.provider?.label ?: "Tasks.org/OpenTasks"} wählen.",
        ) {
            if (s.davxInstalled && !hasAccount) LinkButton("DAVx⁵ öffnen") { SyncHelper.openApp(ctx, DAVX_PACKAGE) }
            if (!s.calPermission && s.davxInstalled) Hint("Konten werden erkannt, sobald Alpenlan auf den Kalender zugreifen darf (Schritt 4).")
        }
        Step(
            n = 4, done = s.calPermission && (s.provider == null || s.taskPermission), title = "Zugriff erlauben",
            text = "Kalender lesen/schreiben, Aufgaben lesen/schreiben und Benachrichtigungen für Erinnerungen.",
        ) {
            LinkButton("Berechtigungen erteilen", primary = !s.calPermission) { launcher.launch(perms.toTypedArray()) }
        }

        Spacer(Modifier.height(6.dp))
        LinkButton(if (s.calPermission) "Los geht's" else "Ohne Berechtigung fortfahren", Modifier.fillMaxWidth(), primary = s.calPermission) { vm.finishSetup() }
        if (hasAccount) LinkButton("Jetzt synchronisieren", Modifier.fillMaxWidth()) { vm.requestSync() }
    }
}

@Composable
private fun Step(n: Int, done: Boolean, title: String, text: String, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(A.panel.copy(alpha = 0.92f))
            .border(1.dp, if (done) A.ok.copy(alpha = 0.4f) else A.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) A.ok else A.line2),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = "erledigt", tint = A.blueInk, modifier = Modifier.size(18.dp))
            else Text("$n", color = A.fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = A.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Hint(text)
            actions()
        }
    }
}
