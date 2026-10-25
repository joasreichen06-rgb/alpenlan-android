@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package ch.alpenlan.kalender.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.alpenlan.kalender.data.DAVX_PACKAGE
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.data.SyncHelper
import ch.alpenlan.kalender.ui.A
import ch.alpenlan.kalender.ui.AlpenlanLogo
import ch.alpenlan.kalender.ui.Hint
import ch.alpenlan.kalender.ui.LinkButton
import ch.alpenlan.kalender.ui.Mono
import ch.alpenlan.kalender.ui.MonoLabel
import ch.alpenlan.kalender.ui.Pill
import ch.alpenlan.kalender.ui.SwitchRow
import ch.alpenlan.kalender.ui.Syne
import ch.alpenlan.kalender.ui.fieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ------------------------------------------------------------------ Hilfen

private val AVATAR_COLORS = listOf(
    Color(0xFF2B6CB8), Color(0xFF1D7F5C), Color(0xFF8A6414), Color(0xFFB8432C),
    Color(0xFF5B4BB0), Color(0xFF136F7A), Color(0xFF46536A), Color(0xFF8B3A6E),
)

private fun avatarColor(seed: String) = AVATAR_COLORS[(seed.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]

private fun initials(name: String): String {
    val parts = name.split(' ', ',', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        parts.isEmpty() -> name.take(1).uppercase()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun letterOf(name: String): String {
    val c = Normalizer.normalize(name.trim().take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

private fun digits(s: String) = s.filter { it.isDigit() }

private fun matches(c: ContactSummary, q: String): Boolean {
    val t = q.trim().lowercase()
    if (t.isEmpty()) return true
    if (c.name.lowercase().contains(t) || c.nameAlt.lowercase().contains(t)) return true
    if (c.company?.lowercase()?.contains(t) == true) return true
    if (c.emails.any { it.lowercase().contains(t) }) return true
    val d = digits(t)
    return d.length >= 3 && c.phones.any { digits(it).contains(d) }
}

private fun open(ctx: Context, uri: String, action: String = Intent.ACTION_VIEW) {
    runCatching { ctx.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun dial(ctx: Context, n: String) = open(ctx, "tel:${Uri.encode(n)}", Intent.ACTION_DIAL)
private fun sms(ctx: Context, n: String) = open(ctx, "smsto:${Uri.encode(n)}", Intent.ACTION_SENDTO)
private fun mail(ctx: Context, a: String) = open(ctx, "mailto:$a", Intent.ACTION_SENDTO)
private fun map(ctx: Context, a: String) = open(ctx, "geo:0,0?q=${Uri.encode(a)}")
private fun web(ctx: Context, u: String) = open(ctx, if (u.startsWith("http")) u else "https://$u")

@Composable
private fun Avatar(vm: ContactsViewModel, id: Long, name: String, hasPhoto: Boolean, size: Dp, highRes: Boolean = false) {
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, id, hasPhoto, highRes) {
        value = if (hasPhoto || highRes) vm.photo(id, highRes) else null
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        val p = photo
        if (p != null) {
            Image(p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }
}

@Composable
private fun SyncIcon(syncing: Boolean, onClick: () -> Unit) {
    val angle = if (syncing) {
        rememberInfiniteTransition(label = "s").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "r").value
    } else 0f
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Refresh, contentDescription = "Synchronisieren", tint = if (syncing) A.blue else A.muted, modifier = Modifier.rotate(angle))
    }
}

// ------------------------------------------------------------------ App

@Composable
fun ContactsApp(vm: ContactsViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(A.groundTop, A.groundBottom)))
    ) {
        if (!s.loaded) return@Box
        if (!s.permission) {
            PermissionScreen(vm)
        } else {
            when (val sc = s.screen) {
                CScreen.Home -> ListScreen(s, vm)
                is CScreen.Detail -> DetailScreen(s, vm)
                is CScreen.Edit -> EditorScreen(s, vm, sc)
            }
        }
        ToastBar(s, vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 88.dp, bottom = 20.dp))
        if (s.showSettings) SettingsSheet(s, vm)
    }
}

@Composable
private fun PermissionScreen(vm: ContactsViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AlpenlanLogo(Modifier.size(40.dp, 22.dp))
        Spacer(Modifier.height(12.dp))
        Text(buildAnnotatedString { append("KONTAK"); withStyle(SpanStyle(color = A.blue)) { append("TE.") } }, fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = A.fg)
        Spacer(Modifier.height(8.dp))
        Hint("Alpenlan Kontakte zeigt die Adressbücher aus deiner Alpenlan Cloud an, die DAVx⁵ synchronisiert. Dafür braucht die App Zugriff auf die Kontakte.")
        Spacer(Modifier.height(18.dp))
        LinkButton("Zugriff erlauben", Modifier.fillMaxWidth(), primary = true) {
            launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
        }
    }
}

@Composable
private fun ToastBar(s: ContactsState, vm: ContactsViewModel, modifier: Modifier) {
    val t = s.toast ?: return
    LaunchedEffect(t.stamp) { delay(if (t.undoId != null) 5000 else 3000); vm.dismissToast() }
    Row(
        modifier.fillMaxWidth().height(52.dp).shadow(10.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            .background(A.lineSoft).border(1.dp, Color(0xFF2B3B55), RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.text, color = A.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
        if (t.undoId != null) TextButton(onClick = { vm.undo(t.undoId) }) { Text("Rückgängig", color = A.blueLight, fontWeight = FontWeight.Bold) }
    }
}

// ------------------------------------------------------------------ Liste

private sealed interface Entry {
    data class Header(val letter: String) : Entry
    data class Item(val c: ContactSummary) : Entry
}

@Composable
private fun ListScreen(s: ContactsState, vm: ContactsViewModel) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searching = s.query.isNotBlank()
    val sortName = { c: ContactSummary -> if (s.prefs.sortByFamily) c.nameAlt else c.name }
    val shownName = sortName
    val filtered = remember(s.visible, s.query, s.prefs.sortByFamily) {
        s.visible.filter { matches(it, s.query) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { sortName(it) })
    }
    val favorites = remember(s.visible) { s.visible.filter { it.starred } }
    val birthdays = remember(s.visible, s.today) {
        s.visible.mapNotNull { c -> c.birthday?.let { b -> Triple(c, b, b.nextOccurrence(s.today)) } }
            .filter { ChronoUnit.DAYS.between(s.today, it.third) <= 30 }
            .sortedBy { it.third }.take(6)
    }
    val entries = remember(filtered) {
        val out = mutableListOf<Entry>()
        var last = ""
        filtered.forEach { c ->
            val l = letterOf(sortName(c))
            if (l != last) { out += Entry.Header(l); last = l }
            out += Entry.Item(c)
        }
        out
    }
    val lead = if (searching) 0 else (if (favorites.isNotEmpty()) 1 else 0) + (if (birthdays.isNotEmpty()) 1 else 0) + (if (s.nextcloudBooks.isEmpty()) 1 else 0)
    val letterIndex = remember(entries, lead) {
        entries.mapIndexedNotNull { i, e -> (e as? Entry.Header)?.let { it.letter to i + lead } }.toMap()
    }

    BackHandler(enabled = searching) { vm.setQuery("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AlpenlanLogo()
            Spacer(Modifier.width(10.dp))
            Mono("Nextcloud Kontakte", color = A.blue)
            Spacer(Modifier.weight(1f))
            SyncIcon(s.syncing) { vm.sync() }
            IconButton(onClick = { vm.showSettings(true) }) { Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = A.muted) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString { append("KONTAK"); withStyle(SpanStyle(color = A.blue)) { append("TE.") } },
                fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = A.fg, modifier = Modifier.weight(1f),
            )
            Mono("${s.visible.size}", size = 12, spacing = 0.4, modifier = Modifier.padding(bottom = 6.dp))
        }
        OutlinedTextField(
            value = s.query, onValueChange = { vm.setQuery(it) },
            placeholder = { Text("Name, Nummer, E-Mail, Firma", color = A.dim) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = A.muted) },
            trailingIcon = { if (searching) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Suche leeren", tint = A.muted) } },
            singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 30.dp, bottom = 110.dp),
            ) {
                if (!searching) {
                    if (s.nextcloudBooks.isEmpty()) item(key = "hint") { NoBookHint(ctx) }
                    if (favorites.isNotEmpty()) item(key = "fav") { Favorites(vm, favorites) }
                    if (birthdays.isNotEmpty()) item(key = "bday") { Birthdays(vm, s.today, birthdays) }
                }
                if (filtered.isEmpty()) item(key = "empty") {
                    Text(if (searching) "Nichts gefunden" else "Noch keine Kontakte", color = A.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 30.dp))
                }
                entries.forEach { e ->
                    when (e) {
                        is Entry.Header -> stickyHeader(key = "h_${e.letter}") {
                            Box(Modifier.fillMaxWidth().background(A.groundTop.copy(alpha = 0.96f)).padding(top = 10.dp, bottom = 4.dp)) {
                                Mono(e.letter, color = A.blue, size = 12)
                            }
                        }
                        is Entry.Item -> item(key = e.c.id) { ContactRow(vm, e.c, shownName(e.c)) }
                    }
                }
            }
            if (!searching && letterIndex.size > 3) {
                AlphabetBar(letterIndex.keys.toList(), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp)) { l ->
                    letterIndex[l]?.let { idx -> scope.launch { listState.scrollToItem(idx) } }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).size(56.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(A.blue)
                    .clickable(role = Role.Button, onClickLabel = "Neuer Kontakt") { vm.newContact() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = "Neuer Kontakt", tint = A.blueInk, modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun NoBookHint(ctx: Context) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFB547))
            .border(1.dp, Color(0x55FFB547), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Kein Nextcloud-Adressbuch gefunden", color = A.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Hint("In DAVx⁵: dein Konto → Reiter „CardDAV“ → Adressbuch anhaken und synchronisieren. Bis dahin werden neue Kontakte nur auf dem Handy gespeichert.")
        LinkButton("DAVx⁵ öffnen") { if (!SyncHelper.openApp(ctx, DAVX_PACKAGE)) SyncHelper.openUrl(ctx, SyncHelper.FDROID_DAVX) }
    }
}

@Composable
private fun Favorites(vm: ContactsViewModel, favs: List<ContactSummary>) {
    Column(Modifier.padding(top = 6.dp, bottom = 6.dp)) {
        Mono("Favoriten", color = A.blue)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            favs.forEach { c ->
                Column(
                    Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.open(c.id) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(vm, c.id, c.name, c.hasPhoto, 52.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(c.name.split(' ').first(), color = A.fg2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Birthdays(vm: ContactsViewModel, today: LocalDate, list: List<Triple<ContactSummary, Birthday, LocalDate>>) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(A.panel.copy(alpha = 0.92f))
            .border(1.dp, A.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Mono("Geburtstage · 30 Tage", color = A.blue)
        Spacer(Modifier.height(4.dp))
        list.forEach { (c, b, next) ->
            val days = ChronoUnit.DAYS.between(today, next)
            val whenText = when (days) { 0L -> "Heute"; 1L -> "Morgen"; else -> "${Dates.wdShort(next)}, ${Dates.short(next)}" }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.open(c.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(vm, c.id, c.name, c.hasPhoto, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = A.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    b.ageOn(next)?.let { Text("wird $it", color = A.muted, fontSize = 12.sp) }
                }
                Text(whenText, style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.2.sp, color = if (days == 0L) A.blue else A.fg2))
            }
        }
    }
}

@Composable
private fun ContactRow(vm: ContactsViewModel, c: ContactSummary, name: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.open(c.id) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(vm, c.id, c.name, c.hasPhoto, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = c.company ?: c.phones.firstOrNull() ?: c.emails.firstOrNull()
            if (sub != null) Text(sub, color = A.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (c.starred) Icon(Icons.Default.Star, contentDescription = "Favorit", tint = A.warn, modifier = Modifier.size(16.dp))
        if (!c.synced) {
            Spacer(Modifier.width(6.dp))
            Mono("Gerät", size = 9, spacing = 0.6, color = A.dim)
        }
    }
}

@Composable
private fun AlphabetBar(letters: List<String>, modifier: Modifier, onLetter: (String) -> Unit) {
    var height by remember { mutableStateOf(1) }
    Column(
        modifier
            .width(24.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                fun pick(y: Float) {
                    val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    onLetter(letters[i])
                }
                detectTapGestures { pick(it.y) }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ ->
                    val i = (change.position.y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    onLetter(letters[i])
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it, style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.sp, color = A.blueLight)) }
    }
}


// ------------------------------------------------------------------ Detail

@Composable
private fun DetailScreen(s: ContactsState, vm: ContactsViewModel) {
    BackHandler { vm.back() }
    val ctx = LocalContext.current
    val d = s.detail
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = A.fg) }
            Spacer(Modifier.weight(1f))
            if (d != null) {
                IconButton(onClick = { vm.toggleStar(d) }) {
                    Icon(Icons.Default.Star, contentDescription = if (d.starred) "Aus Favoriten entfernen" else "Zu Favoriten", tint = if (d.starred) A.warn else A.dim)
                }
                IconButton(onClick = { vm.edit(d) }) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = A.fg) }
            }
        }
        if (d == null) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(vm, d.id, d.displayName, true, 104.dp, highRes = true)
                Spacer(Modifier.height(12.dp))
                Text(d.displayName, color = A.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                val org = listOf(d.jobTitle, d.company).filter { it.isNotBlank() }.joinToString(" · ")
                if (org.isNotBlank()) Text(org, color = A.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Mono(d.book?.let { if (it.isNextcloud) "Nextcloud · ${it.name}" else "Nur auf dem Gerät" } ?: "", size = 10, spacing = 1.0,
                    color = if (d.book?.isNextcloud == true) A.blue else A.warn)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val phone = d.phones.firstOrNull()?.value
                val email = d.emails.firstOrNull()?.value
                val addr = d.addresses.firstOrNull()?.value
                QuickAction(Icons.Default.Call, "Anrufen", phone != null) { phone?.let { dial(ctx, it) } }
                QuickAction(Icons.Default.Send, "SMS", phone != null) { phone?.let { sms(ctx, it) } }
                QuickAction(Icons.Default.Email, "E-Mail", email != null) { email?.let { mail(ctx, it) } }
                QuickAction(Icons.Default.Place, "Karte", addr != null) { addr?.let { map(ctx, it) } }
            }
            if (d.phones.isNotEmpty()) Card("Telefon") {
                d.phones.forEach { p ->
                    InfoRow(p.value, Types.phoneLabel(p), onClick = { dial(ctx, p.value) }) {
                        IconButton(onClick = { sms(ctx, p.value) }) { Icon(Icons.Default.Send, contentDescription = "SMS an ${p.value}", tint = A.blueLight) }
                    }
                }
            }
            if (d.emails.isNotEmpty()) Card("E-Mail") { d.emails.forEach { e -> InfoRow(e.value, Types.emailLabel(e), onClick = { mail(ctx, e.value) }) } }
            if (d.addresses.isNotEmpty()) Card("Adresse") { d.addresses.forEach { a -> InfoRow(a.value, Types.postalLabel(a), onClick = { map(ctx, a.value) }) } }
            d.birthday?.let { b ->
                val next = b.nextOccurrence(s.today)
                val days = ChronoUnit.DAYS.between(s.today, next)
                val date = "${b.day}. ${Dates.MONTHS[b.month - 1]}" + (b.year?.let { " $it" } ?: "")
                val sub = buildString {
                    b.ageOn(s.today.let { t -> if (next == t) t else next.minusYears(1) })?.let { append("$it Jahre") }
                    if (isNotEmpty()) append(" · ")
                    append(when (days) { 0L -> "heute Geburtstag"; 1L -> "morgen"; else -> "in $days Tagen" })
                }
                Card("Geburtstag") { InfoRow(date, sub) }
            }
            if (d.websites.isNotEmpty()) Card("Website") { d.websites.forEach { w -> InfoRow(w, "Web", onClick = { web(ctx, w) }) } }
            if (d.note.isNotBlank()) Card("Notiz") { Text(d.note, color = A.fg2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp)) }

            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1AE02204))
                    .border(1.dp, Color(0xFF6B2A20), RoundedCornerShape(12.dp)).clickable(role = Role.Button) { confirmDelete = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = A.redSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kontakt löschen", color = A.redSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete && d != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = A.panel,
            title = { Text("${d.displayName} löschen?") },
            text = { Text("Der Kontakt wird auch in der Nextcloud gelöscht.", color = A.fg2) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(d) }) { Text("Löschen", color = A.redSoft) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (enabled) A.blue.copy(alpha = 0.16f) else A.panel2)
                .border(1.dp, if (enabled) A.blue else A.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (enabled) A.blueLight else A.dim, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) A.fg2 else A.dim, fontSize = 12.sp)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(A.panel.copy(alpha = 0.92f))
            .border(1.dp, A.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Mono(title, color = A.blue)
        content()
    }
}

@Composable
private fun InfoRow(value: String, label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(value, color = A.fg, fontSize = 15.sp)
            Text(label, color = A.muted, fontSize = 12.sp)
        }
        trailing()
    }
}

// ------------------------------------------------------------------ Editor

@Composable
private fun EditorScreen(s: ContactsState, vm: ContactsViewModel, e: CScreen.Edit) {
    var d by remember(e) { mutableStateOf(e.draft) }
    var err by remember(e) { mutableStateOf<String?>(null) }
    var pickBirthday by remember { mutableStateOf(false) }
    BackHandler { vm.back() }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.back() }) { Text("Abbrechen", color = A.muted, fontWeight = FontWeight.SemiBold) }
            Text(if (e.rawId == null) "Neuer Kontakt" else "Bearbeiten", color = A.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(A.blue).clickable(role = Role.Button) {
                    if (d.given.isBlank() && d.family.isBlank() && d.company.isBlank()) err = "Bitte einen Namen oder eine Firma eingeben."
                    else vm.save(e.copy(draft = d))
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Speichern", color = A.blueInk, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (e.rawId == null && s.books.isNotEmpty()) {
                Mono("Adressbuch")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.books.forEach { b -> Pill(b.label, d.book == b, { d = d.copy(book = b) }, dot = if (b.isNextcloud) A.blue else A.warn) }
                }
                if (d.book?.isNextcloud != true) Text("Dieses Adressbuch wird nicht mit Nextcloud synchronisiert.", color = A.warn, fontSize = 12.sp)
            }
            Field("Vorname", d.given, cap = KeyboardCapitalization.Words) { d = d.copy(given = it); err = null }
            Field("Nachname", d.family, cap = KeyboardCapitalization.Words) { d = d.copy(family = it); err = null }
            err?.let { Text(it, color = A.redSoft, fontSize = 13.sp) }
            Field("Firma", d.company, cap = KeyboardCapitalization.Words) { d = d.copy(company = it); err = null }
            Field("Position", d.jobTitle, cap = KeyboardCapitalization.Sentences) { d = d.copy(jobTitle = it) }

            MultiSection("Telefon", d.phones, Types.phone, KeyboardType.Phone, Types::phoneLabel, add = Labeled("", android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)) { d = d.copy(phones = it) }
            MultiSection("E-Mail", d.emails, Types.email, KeyboardType.Email, Types::emailLabel, add = Labeled("", android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME)) { d = d.copy(emails = it) }
            MultiSection("Adresse", d.addresses, Types.postal, KeyboardType.Text, Types::postalLabel, add = Labeled("", android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME), multiline = true) { d = d.copy(addresses = it) }

            Mono("Geburtstag")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val b = d.birthday
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(A.panel2).border(1.dp, A.line2, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { pickBirthday = true }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(b?.let { "${it.day}. ${Dates.MONTHS[it.month - 1]}" + (it.year?.let { y -> " $y" } ?: "") } ?: "Kein Geburtstag", color = if (b != null) A.fg else A.dim, fontSize = 15.sp)
                }
                if (b != null) {
                    Pill(if (b.year == null) "Ohne Jahr" else "Mit Jahr", b.year == null, {
                        d = d.copy(birthday = if (b.year == null) b.copy(year = LocalDate.now().year - 30) else b.copy(year = null))
                    })
                    IconButton(onClick = { d = d.copy(birthday = null) }) { Icon(Icons.Default.Close, contentDescription = "Geburtstag entfernen", tint = A.muted) }
                }
            }
            Field("Website", d.website, type = KeyboardType.Uri) { d = d.copy(website = it) }
            Field("Notiz", d.note, cap = KeyboardCapitalization.Sentences, lines = 3) { d = d.copy(note = it) }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (pickBirthday) {
        val b = d.birthday
        val init = LocalDate.of(b?.year ?: 1990, b?.month ?: 1, b?.day ?: 1)
        val st = rememberDatePickerState(initialSelectedDateMillis = Dates.utcMidnight(init), initialDisplayMode = androidx.compose.material3.DisplayMode.Input)
        DatePickerDialog(
            onDismissRequest = { pickBirthday = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val ld = Dates.utcDate(ms)
                        d = d.copy(birthday = Birthday(ld.monthValue, ld.dayOfMonth, if (b != null && b.year == null) null else ld.year))
                    }
                    pickBirthday = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickBirthday = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = st) }
    }
}

@Composable
private fun Field(label: String, value: String, cap: KeyboardCapitalization = KeyboardCapitalization.None, type: KeyboardType = KeyboardType.Text, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = lines == 1, minLines = lines, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = cap, keyboardType = type),
    )
}

@Composable
private fun MultiSection(
    title: String, items: List<Labeled>, types: List<Pair<Int, String>>, keyboard: KeyboardType,
    labelOf: (Labeled) -> String, add: Labeled, multiline: Boolean = false, onChange: (List<Labeled>) -> Unit,
) {
    Mono(title)
    items.forEachIndexed { i, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.value, onValueChange = { v -> onChange(items.toMutableList().also { it[i] = item.copy(value = v) }) },
                label = { Text(labelOf(item)) }, singleLine = !multiline, minLines = if (multiline) 2 else 1,
                modifier = Modifier.weight(1f), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (multiline) KeyboardCapitalization.Words else KeyboardCapitalization.None),
            )
            Spacer(Modifier.width(6.dp))
            // Typ durchschalten (Mobil → Privat → Arbeit → Andere)
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, A.line2, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClickLabel = "Typ ändern") {
                        val idx = types.indexOfFirst { it.first == item.type }
                        val next = types[(idx + 1).mod(types.size)].first
                        onChange(items.toMutableList().also { it[i] = item.copy(type = next, label = null) })
                    }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(labelOf(item), color = A.fg2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(i) }) }) {
                Icon(Icons.Default.Close, contentDescription = "$title entfernen", tint = A.muted)
            }
        }
    }
    TextButton(onClick = { onChange(items + add) }) { Text("+ $title hinzufügen", color = A.blueLight, fontWeight = FontWeight.SemiBold) }
}

// ------------------------------------------------------------------ Einstellungen

@Composable
private fun SettingsSheet(s: ContactsState, vm: ContactsViewModel) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = { vm.showSettings(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = A.panel) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Einstellungen", color = A.fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Mono("Sortieren nach")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Vorname", !s.prefs.sortByFamily, { vm.updatePrefs { it.copy(sortByFamily = false) } })
                Pill("Nachname", s.prefs.sortByFamily, { vm.updatePrefs { it.copy(sortByFamily = true) } })
            }
            SwitchRow("Nur Nextcloud-Kontakte", s.prefs.onlyNextcloud, { v -> vm.updatePrefs { it.copy(onlyNextcloud = v) } }, subtitle = "Kontakte, die nur auf dem Handy liegen, ausblenden")
            Mono("Standard-Adressbuch für neue Kontakte")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.books.forEach { b ->
                    Pill(b.label, s.defaultBook == b, { vm.updatePrefs { it.copy(defaultBook = "${b.type}/${b.name}") } }, dot = if (b.isNextcloud) A.blue else A.warn)
                }
            }
            HorizontalDivider(color = A.line)
            Mono("Synchronisation", color = A.blue)
            Text("DAVx⁵: ${if (s.davx) "installiert" else "fehlt"} · Nextcloud-Adressbücher: ${s.nextcloudBooks.size}", color = A.fg2, fontSize = 14.sp)
            Hint("Adressbücher aktivierst du in DAVx⁵ beim Konto im Reiter „CardDAV“.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkButton(if (s.syncing) "Synchronisiert…" else "Jetzt synchronisieren", Modifier.weight(1f)) { vm.sync() }
                LinkButton("DAVx⁵ öffnen", Modifier.weight(1f)) { if (!SyncHelper.openApp(ctx, DAVX_PACKAGE)) SyncHelper.openUrl(ctx, SyncHelper.FDROID_DAVX) }
            }
        }
    }
}
