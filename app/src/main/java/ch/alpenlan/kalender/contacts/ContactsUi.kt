@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package ch.alpenlan.kalender.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
private fun Avatar(vm: ContactsViewModel, id: Long, name: String, hasPhoto: Boolean, size: Dp, stamp: Long, highRes: Boolean = false) {
    val photo by produceState<ImageBitmap?>(null, id, hasPhoto, highRes, stamp) {
        value = if (hasPhoto) vm.photo(id, highRes) else null
    }
    Box(Modifier.size(size).clip(CircleShape).background(avatarColor(name)), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) Image(p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(A.groundTop, A.groundBottom)))) {
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
        s.moving?.let { MovingDialog(it) }
    }
}

@Composable
private fun MovingDialog(p: MoveProgress) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = A.panel,
        title = { Text("Übernehme in Nextcloud …") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done / p.total.toFloat() }, modifier = Modifier.fillMaxWidth(), color = A.blue, trackColor = A.line2)
                Text("${p.done} von ${p.total}" + if (p.failed > 0) " · ${p.failed} Fehler" else "", color = A.fg2)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun PermissionScreen(vm: ContactsViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
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
    val filtered = remember(s.visible, s.query, s.prefs.sortByFamily) {
        s.visible.filter { matches(it, s.query) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { sortName(it) })
    }
    val favorites = remember(s.visible) { s.visible.filter { it.starred } }
    val birthdays = remember(s.visible, s.today) {
        s.visible.mapNotNull { c -> c.birthday?.let { b -> Triple(c, b, b.nextOccurrence(s.today)) } }
            .filter { ChronoUnit.DAYS.between(s.today, it.third) <= 30 }
            .sortedBy { it.third }.take(6)
    }
    val movable = remember(s.contacts) { s.contacts.count { it.movable } }
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
    val showHint = s.nextcloudBooks.isEmpty()
    val showMove = !showHint && movable > 0
    val lead = if (searching) 0 else listOf(showHint, showMove, favorites.isNotEmpty(), birthdays.isNotEmpty()).count { it }
    val letterIndex = remember(entries, lead) { entries.mapIndexedNotNull { i, e -> (e as? Entry.Header)?.let { it.letter to i + lead } }.toMap() }

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
            placeholder = { Text("Suchen", color = A.dim) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = A.muted) },
            trailingIcon = { if (searching) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Suche leeren", tint = A.muted) } },
            singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 30.dp, bottom = 110.dp)) {
                if (!searching) {
                    if (showHint) item(key = "hint") { NoBookHint(ctx) }
                    if (showMove) item(key = "move") { MoveBanner(movable) { vm.showSettings(true) } }
                    if (favorites.isNotEmpty()) item(key = "fav") { Favorites(s, vm, favorites) }
                    if (birthdays.isNotEmpty()) item(key = "bday") { Birthdays(s, vm, birthdays) }
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
                        is Entry.Item -> item(key = e.c.id) { ContactRow(s, vm, e.c, sortName(e.c)) }
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
private fun MoveBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(A.blue.copy(alpha = 0.10f))
            .border(1.dp, Color(0xFF2B4466), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$count Kontakte nur auf dem Gerät", color = A.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Hint("In die Nextcloud übernehmen, damit sie überall verfügbar sind.")
        }
        Text("Übernehmen", color = A.blueLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun Favorites(s: ContactsState, vm: ContactsViewModel, favs: List<ContactSummary>) {
    Column(Modifier.padding(top = 6.dp, bottom = 6.dp)) {
        Mono("Favoriten", color = A.blue)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            favs.forEach { c ->
                Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.open(c.id) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(vm, c.id, c.name, c.hasPhoto, 52.dp, s.photoStamp)
                    Spacer(Modifier.height(4.dp))
                    Text(c.name.split(' ').first(), color = A.fg2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Birthdays(s: ContactsState, vm: ContactsViewModel, list: List<Triple<ContactSummary, Birthday, LocalDate>>) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(A.panel.copy(alpha = 0.92f))
            .border(1.dp, A.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Mono("Geburtstage · 30 Tage", color = A.blue)
        Spacer(Modifier.height(4.dp))
        list.forEach { (c, b, next) ->
            val days = ChronoUnit.DAYS.between(s.today, next)
            val whenText = when (days) { 0L -> "Heute"; 1L -> "Morgen"; else -> "${Dates.wdShort(next)}, ${Dates.short(next)}" }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.open(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(vm, c.id, c.name, c.hasPhoto, 34.dp, s.photoStamp)
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
private fun ContactRow(s: ContactsState, vm: ContactsViewModel, c: ContactSummary, name: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.open(c.id) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(vm, c.id, c.name, c.hasPhoto, 42.dp, s.photoStamp)
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
                detectTapGestures { pos ->
                    onLetter(letters[(pos.y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)])
                }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ ->
                    onLetter(letters[(change.position.y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)])
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
    val det = s.detail
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(det?.book) { det?.book?.let { vm.loadGroups(it) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = A.fg) }
            Spacer(Modifier.weight(1f))
            if (det != null) {
                IconButton(onClick = { vm.toggleStar(det) }) {
                    Icon(Icons.Default.Star, contentDescription = if (det.starred) "Aus Favoriten entfernen" else "Zu Favoriten", tint = if (det.starred) A.warn else A.dim)
                }
                if (det.rawId != null && det.book?.kind != BookKind.APP) {
                    IconButton(onClick = { vm.edit(det) }) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = A.fg) }
                }
            }
        }
        if (det == null) return@Column
        val d = det.draft
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(vm, det.id, det.displayName, det.hasPhoto, 104.dp, s.photoStamp, highRes = true)
                Spacer(Modifier.height(12.dp))
                Text(det.displayName, color = A.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (d.nickname.isNotBlank()) Text("„${d.nickname}“", color = A.fg2, fontSize = 14.sp)
                val org = listOf(d.jobTitle, d.department, d.company).filter { it.isNotBlank() }.joinToString(" · ")
                if (org.isNotBlank()) Text(org, color = A.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                val b = det.book
                Mono(
                    when (b?.kind) { BookKind.NEXTCLOUD -> "Nextcloud · ${b?.label}"; BookKind.DEVICE -> "Nur auf dem Gerät"; null -> ""; else -> b?.label ?: "" },
                    size = 10, spacing = 1.0, color = if (b?.isNextcloud == true) A.blue else A.warn,
                )
            }
            if (det.book?.isNextcloud != true && s.nextcloudBooks.isNotEmpty()) {
                LinkButton("In Nextcloud verschieben", Modifier.fillMaxWidth(), primary = true) { vm.moveOne(det) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val phone = d.phones.firstOrNull()?.value
                val email = d.emails.firstOrNull()?.value
                val addr = d.addresses.firstOrNull()?.display()
                QuickAction(Icons.Default.Call, "Anrufen", phone != null) { phone?.let { dial(ctx, it) } }
                QuickAction(Icons.Default.Send, "SMS", phone != null) { phone?.let { sms(ctx, it) } }
                QuickAction(Icons.Default.Email, "E-Mail", email != null) { email?.let { mail(ctx, it) } }
                QuickAction(Icons.Default.Place, "Karte", addr != null) { addr?.let { map(ctx, it.replace("\n", ", ")) } }
            }
            if (d.phones.isNotEmpty()) Card("Telefon") {
                d.phones.forEach { p ->
                    InfoRow(p.value, Types.phoneLabel(p), onClick = { dial(ctx, p.value) }) {
                        IconButton(onClick = { sms(ctx, p.value) }) { Icon(Icons.Default.Send, contentDescription = "SMS an ${p.value}", tint = A.blueLight) }
                    }
                }
            }
            if (d.emails.isNotEmpty()) Card("E-Mail") { d.emails.forEach { e -> InfoRow(e.value, Types.emailLabel(e), onClick = { mail(ctx, e.value) }) } }
            if (d.addresses.isNotEmpty()) Card("Adresse") { d.addresses.forEach { a -> InfoRow(a.display(), Types.postalLabel(a), onClick = { map(ctx, a.display().replace("\n", ", ")) }) } }
            if (d.events.isNotEmpty()) Card("Wichtige Daten") {
                d.events.forEach { e ->
                    val next = e.date.nextOccurrence(s.today)
                    val days = ChronoUnit.DAYS.between(s.today, next)
                    val sub = buildString {
                        append(Types.eventLabel(e))
                        e.date.ageOn(if (next == s.today) next else next.minusYears(1))?.let { append(" · $it Jahre") }
                        append(" · ").append(when (days) { 0L -> "heute"; 1L -> "morgen"; else -> "in $days Tagen" })
                    }
                    InfoRow(e.date.text(Dates.MONTHS), sub)
                }
            }
            if (d.relations.isNotEmpty()) Card("Beziehungen") { d.relations.forEach { r -> InfoRow(r.value, Types.relationLabel(r)) } }
            val groupNames = s.editGroups.filter { it.id in d.groups }.map { it.title }
            if (groupNames.isNotEmpty()) Card("Gruppen") { Text(groupNames.joinToString(", "), color = A.fg, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (d.websites.isNotEmpty()) Card("Website") { d.websites.forEach { w -> InfoRow(w, "Web", onClick = { web(ctx, w) }) } }
            if (d.note.isNotBlank()) Card("Notiz") { Text(d.note, color = A.fg2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            if (det.allRaw.size > 1) {
                Hint("Verknüpft mit: " + det.allRaw.map { it.second.label }.distinct().joinToString(", "))
            }
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
    if (confirmDelete && det != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = A.panel,
            title = { Text("${det.displayName} löschen?") },
            text = { Text(if (det.book?.isNextcloud == true) "Der Kontakt wird auch in der Nextcloud gelöscht." else "Der Kontakt wird vom Gerät gelöscht.", color = A.fg2) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(det) }) { Text("Löschen", color = A.redSoft) } },
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
            Modifier.size(48.dp).clip(CircleShape).background(if (enabled) A.blue.copy(alpha = 0.16f) else A.panel2).border(1.dp, if (enabled) A.blue else A.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (enabled) A.blueLight else A.dim, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) A.fg2 else A.dim, fontSize = 12.sp)
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
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
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    var moreName by remember(e) { mutableStateOf(listOf(e.draft.prefix, e.draft.middle, e.draft.suffix, e.draft.phoneticGiven, e.draft.phoneticFamily).any { it.isNotBlank() }) }
    var pickDateFor by remember { mutableStateOf<Int?>(null) }
    var newGroup by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { vm.preparePhoto(uri)?.let { d = d.copy(newPhoto = it) } }
    }
    BackHandler { vm.back() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.back() }) { Text("Abbrechen", color = A.muted, fontWeight = FontWeight.SemiBold) }
            Text(if (e.rawId == null) "Neuer Kontakt" else "Bearbeiten", color = A.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(A.blue).clickable(role = Role.Button) {
                    if (!d.hasName) err = "Bitte einen Namen oder eine Firma eingeben." else vm.save(e.copy(draft = d))
                }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Speichern", color = A.blueInk, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Foto
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val newPhoto = d.newPhoto
                val preview = remember(newPhoto) {
                    newPhoto?.takeIf { it.isNotEmpty() }?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }
                Box(Modifier.size(84.dp).clip(CircleShape).background(avatarColor(d.given + d.family)), contentAlignment = Alignment.Center) {
                    when {
                        preview != null -> Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        newPhoto == null && e.hasPhoto && e.contactId != null -> Avatar(vm, e.contactId, d.given, true, 84.dp, s.photoStamp, highRes = true)
                        else -> Text(initials("${d.given} ${d.family}".ifBlank { d.company.ifBlank { "?" } }), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinkButton("Foto wählen") { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    if ((e.hasPhoto && newPhoto == null) || (newPhoto != null && newPhoto.isNotEmpty())) {
                        TextButton(onClick = { d = d.copy(newPhoto = ByteArray(0)) }) { Text("Foto entfernen", color = A.redSoft) }
                    }
                }
            }

            if (e.rawId == null && s.targetBooks.isNotEmpty()) {
                Mono("Speichern in")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.targetBooks.forEach { b ->
                        Pill(b.label, b.same(d.book), { d = d.copy(book = b, groups = emptySet()); vm.loadGroups(b) }, dot = if (b.isNextcloud) A.blue else A.warn)
                    }
                }
                if (d.book?.isNextcloud != true) Text("Dieses Adressbuch wird nicht mit Nextcloud synchronisiert.", color = A.warn, fontSize = 12.sp)
            }

            Section("Name")
            if (moreName) Field("Präfix (z.B. Dr.)", d.prefix, KeyboardCapitalization.Words) { d = d.copy(prefix = it) }
            Field("Vorname", d.given, KeyboardCapitalization.Words) { d = d.copy(given = it); err = null }
            if (moreName) Field("Zweiter Vorname", d.middle, KeyboardCapitalization.Words) { d = d.copy(middle = it) }
            Field("Nachname", d.family, KeyboardCapitalization.Words) { d = d.copy(family = it); err = null }
            if (moreName) {
                Field("Suffix (z.B. jun.)", d.suffix, KeyboardCapitalization.Words) { d = d.copy(suffix = it) }
                Field("Vorname phonetisch", d.phoneticGiven, KeyboardCapitalization.Words) { d = d.copy(phoneticGiven = it) }
                Field("Nachname phonetisch", d.phoneticFamily, KeyboardCapitalization.Words) { d = d.copy(phoneticFamily = it) }
            } else {
                TextButton(onClick = { moreName = true }) { Text("+ Präfix, zweiter Vorname, Suffix, phonetisch", color = A.blueLight) }
            }
            Field("Spitzname", d.nickname, KeyboardCapitalization.Words) { d = d.copy(nickname = it); err = null }
            err?.let { Text(it, color = A.redSoft, fontSize = 13.sp) }

            Section("Telefon")
            LabeledList(d.phones, Types.phone, KeyboardType.Phone, Types::phoneLabel, "Nummer", Labeled("", Phone.TYPE_MOBILE)) { d = d.copy(phones = it) }
            Section("E-Mail")
            LabeledList(d.emails, Types.email, KeyboardType.Email, Types::emailLabel, "E-Mail", Labeled("", Email.TYPE_HOME)) { d = d.copy(emails = it) }

            Section("Arbeit")
            Field("Firma", d.company, KeyboardCapitalization.Words) { d = d.copy(company = it); err = null }
            Field("Abteilung", d.department, KeyboardCapitalization.Words) { d = d.copy(department = it) }
            Field("Position", d.jobTitle, KeyboardCapitalization.Sentences) { d = d.copy(jobTitle = it) }

            Section("Adressen")
            d.addresses.forEachIndexed { i, a ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, A.line, RoundedCornerShape(14.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeChip(Types.postalLabel(a)) {
                            val idx = Types.postal.indexOfFirst { it.first == a.type }
                            d = d.copy(addresses = d.addresses.toMutableList().also { l -> l[i] = a.copy(type = Types.postal[(idx + 1).mod(Types.postal.size)].first, label = null) })
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { d = d.copy(addresses = d.addresses.toMutableList().also { l -> l.removeAt(i) }) }) {
                            Icon(Icons.Default.Close, contentDescription = "Adresse entfernen", tint = A.muted)
                        }
                    }
                    fun upd(n: PostalAddr) { d = d.copy(addresses = d.addresses.toMutableList().also { l -> l[i] = n }) }
                    Field("Strasse und Nr.", a.street, KeyboardCapitalization.Words) { upd(a.copy(street = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("PLZ", a.postcode, KeyboardCapitalization.None, KeyboardType.Number, Modifier.width(110.dp)) { upd(a.copy(postcode = it)) }
                        Field("Ort", a.city, KeyboardCapitalization.Words, modifier = Modifier.weight(1f)) { upd(a.copy(city = it)) }
                    }
                    Field("Kanton / Region", a.region, KeyboardCapitalization.Words) { upd(a.copy(region = it)) }
                    Field("Land", a.country, KeyboardCapitalization.Words) { upd(a.copy(country = it)) }
                }
            }
            AddButton("Adresse") { d = d.copy(addresses = d.addresses + PostalAddr()) }

            Section("Wichtige Daten")
            d.events.forEachIndexed { i, ev ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(A.panel2).border(1.dp, A.line2, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { pickDateFor = i }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(ev.date.text(Dates.MONTHS), color = A.fg, fontSize = 15.sp) }
                    TypeChip(Types.eventLabel(ev)) {
                        val idx = Types.event.indexOfFirst { it.first == ev.type }
                        d = d.copy(events = d.events.toMutableList().also { l -> l[i] = ev.copy(type = Types.event[(idx + 1).mod(Types.event.size)].first, label = null) })
                    }
                    TypeChip(if (ev.date.year == null) "ohne Jahr" else "mit Jahr") {
                        val nd = if (ev.date.year == null) ev.date.copy(year = LocalDate.now().year - 30) else ev.date.copy(year = null)
                        d = d.copy(events = d.events.toMutableList().also { l -> l[i] = ev.copy(date = nd) })
                    }
                    IconButton(onClick = { d = d.copy(events = d.events.toMutableList().also { l -> l.removeAt(i) }) }) {
                        Icon(Icons.Default.Close, contentDescription = "Datum entfernen", tint = A.muted)
                    }
                }
            }
            AddButton(if (d.events.none { it.type == Event.TYPE_BIRTHDAY }) "Geburtstag" else "Datum") {
                val type = if (d.events.none { it.type == Event.TYPE_BIRTHDAY }) Event.TYPE_BIRTHDAY else Event.TYPE_ANNIVERSARY
                d = d.copy(events = d.events + DateEntry(Birthday(1, 1, 1990), type))
                pickDateFor = d.events.lastIndex
            }

            Section("Gruppen")
            if (s.editGroups.isEmpty()) Hint(if (d.book?.isNextcloud == true) "In diesem Adressbuch gibt es noch keine Gruppen." else "Gruppen gibt es nur in Adressbüchern mit Gruppen.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.editGroups.forEach { g ->
                    val on = g.id in d.groups
                    Pill(g.title, on, { d = d.copy(groups = if (on) d.groups - g.id else d.groups + g.id) })
                }
            }
            if (d.book != null && d.book?.kind != BookKind.APP) AddButton("Neue Gruppe") { newGroup = "" }

            Section("Website")
            d.websites.forEachIndexed { i, w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Website", w, KeyboardCapitalization.None, KeyboardType.Uri, Modifier.weight(1f)) { v ->
                        d = d.copy(websites = d.websites.toMutableList().also { l -> l[i] = v })
                    }
                    IconButton(onClick = { d = d.copy(websites = d.websites.toMutableList().also { l -> l.removeAt(i) }) }) {
                        Icon(Icons.Default.Close, contentDescription = "Website entfernen", tint = A.muted)
                    }
                }
            }
            AddButton("Website") { d = d.copy(websites = d.websites + "") }

            Section("Beziehungen")
            LabeledList(d.relations, Types.relation, KeyboardType.Text, Types::relationLabel, "Name", Labeled("", Relation.TYPE_PARTNER), allowEmpty = true) { d = d.copy(relations = it) }

            Section("Notiz")
            Field("Notiz", d.note, KeyboardCapitalization.Sentences, lines = 3) { d = d.copy(note = it) }
            Spacer(Modifier.height(40.dp))
        }
    }

    pickDateFor?.let { i ->
        val ev = d.events.getOrNull(i)
        if (ev == null) { pickDateFor = null; return@let }
        val init = runCatching { LocalDate.of(ev.date.year ?: 2000, ev.date.month, ev.date.day) }.getOrDefault(LocalDate.of(2000, 1, 1))
        val st = rememberDatePickerState(initialSelectedDateMillis = Dates.utcMidnight(init), initialDisplayMode = DisplayMode.Input)
        DatePickerDialog(
            onDismissRequest = { pickDateFor = null },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { ms ->
                        val ld = Dates.utcDate(ms)
                        val nd = Birthday(ld.monthValue, ld.dayOfMonth, if (ev.date.year == null) null else ld.year)
                        d = d.copy(events = d.events.toMutableList().also { l -> l[i] = ev.copy(date = nd) })
                    }
                    pickDateFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDateFor = null }) { Text("Abbrechen") } },
        ) { DatePicker(state = st) }
    }

    newGroup?.let { name ->
        AlertDialog(
            onDismissRequest = { newGroup = null },
            containerColor = A.panel,
            title = { Text("Neue Gruppe") },
            text = {
                OutlinedTextField(value = name, onValueChange = { newGroup = it }, singleLine = true, label = { Text("Name") }, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            },
            confirmButton = {
                TextButton(onClick = {
                    val book = d.book
                    newGroup = null
                    vm.createGroup(book, name) { id -> d = d.copy(groups = d.groups + id) }
                }) { Text("Erstellen") }
            },
            dismissButton = { TextButton(onClick = { newGroup = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Mono(title, color = A.blue, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AddButton(what: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("+ $what hinzufügen", color = A.blueLight, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TypeChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, A.line2, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Typ ändern", onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = A.fg2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun Field(
    label: String, value: String, cap: KeyboardCapitalization = KeyboardCapitalization.None, type: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(), lines: Int = 1, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = lines == 1, minLines = lines, modifier = modifier, colors = fieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = cap, keyboardType = type),
    )
}

@Composable
private fun LabeledList(
    items: List<Labeled>, types: List<Pair<Int, String>>, keyboard: KeyboardType, labelOf: (Labeled) -> String,
    hint: String, add: Labeled, allowEmpty: Boolean = true, onChange: (List<Labeled>) -> Unit,
) {
    items.forEachIndexed { i, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.value, onValueChange = { v -> onChange(items.toMutableList().also { it[i] = item.copy(value = v) }) },
                label = { Text(hint) }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None),
            )
            Spacer(Modifier.width(6.dp))
            TypeChip(labelOf(item)) {
                val idx = types.indexOfFirst { it.first == item.type }
                onChange(items.toMutableList().also { it[i] = item.copy(type = types[(idx + 1).mod(types.size)].first, label = null) })
            }
            if (allowEmpty || items.size > 1) IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(i) }) }) {
                Icon(Icons.Default.Close, contentDescription = "Entfernen", tint = A.muted)
            }
        }
    }
    AddButton(hint) { onChange(items + add) }
}

// ------------------------------------------------------------------ Einstellungen

@Composable
private fun SettingsSheet(s: ContactsState, vm: ContactsViewModel) {
    val ctx = LocalContext.current
    var sources by remember(s.sourceBooks) { mutableStateOf(s.sourceBooks.filter { it.kind == BookKind.DEVICE }.map { it.key }.toSet()) }
    var confirm by remember { mutableStateOf(false) }
    val target = s.defaultBook?.takeIf { it.isNextcloud } ?: s.nextcloudBooks.firstOrNull()
    val toMove = s.contacts.count { c -> !c.synced && c.books.any { it.key in sources } }

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
            Mono("Neue Kontakte speichern in")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.targetBooks.forEach { b -> Pill(b.label, b.same(s.defaultBook), { vm.updatePrefs { it.copy(defaultBook = b.key) } }, dot = if (b.isNextcloud) A.blue else A.warn) }
            }

            HorizontalDivider(color = A.line)
            Mono("In Nextcloud übernehmen", color = A.blue)
            if (target == null) {
                Hint("Zuerst in DAVx⁵ im Reiter „CardDAV“ ein Adressbuch aktivieren.")
            } else if (s.sourceBooks.isEmpty()) {
                Hint("Alle Kontakte liegen bereits in der Nextcloud.")
            } else {
                Hint("Kopiert die Kontakte nach „${target.label}“ und entfernt sie danach vom Gerät bzw. aus Google. Fotos, Favoriten und alle Felder werden mitgenommen.")
                s.sourceBooks.forEach { b ->
                    val on = b.key in sources
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(role = Role.Checkbox) { sources = if (on) sources - b.key else sources + b.key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).then(if (on) Modifier.background(A.blue) else Modifier.border(2.dp, Color(0xFF5A6B88), RoundedCornerShape(6.dp))),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Default.Check, contentDescription = null, tint = A.blueInk, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Text(b.label, color = A.fg, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Mono("${b.count}", size = 11, spacing = 0.2)
                    }
                }
                LinkButton("$toMove Kontakte übernehmen", Modifier.fillMaxWidth(), primary = toMove > 0) { if (toMove > 0) confirm = true }
            }

            HorizontalDivider(color = A.line)
            Mono("Synchronisation", color = A.blue)
            Text("DAVx⁵: ${if (s.davx) "installiert" else "fehlt"} · Nextcloud-Adressbücher: ${s.nextcloudBooks.size}", color = A.fg2, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkButton(if (s.syncing) "Synchronisiert…" else "Jetzt synchronisieren", Modifier.weight(1f)) { vm.sync() }
                LinkButton("DAVx⁵ öffnen", Modifier.weight(1f)) { if (!SyncHelper.openApp(ctx, DAVX_PACKAGE)) SyncHelper.openUrl(ctx, SyncHelper.FDROID_DAVX) }
            }
        }
    }
    if (confirm && target != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = A.panel,
            title = { Text("$toMove Kontakte übernehmen?") },
            text = { Text("Sie werden nach „${target.label}“ kopiert und danach auf dem Gerät bzw. in Google gelöscht. Messenger-Einträge (WhatsApp, Threema …) bleiben unverändert.", color = A.fg2) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.moveAll(sources, target) }) { Text("Übernehmen", color = A.blueLight) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}
