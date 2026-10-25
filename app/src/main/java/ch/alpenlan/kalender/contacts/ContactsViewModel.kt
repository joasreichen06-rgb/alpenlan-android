package ch.alpenlan.kalender.contacts

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.alpenlan.kalender.data.SyncHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ContactsPrefs(
    val sortByFamily: Boolean = false,
    val onlyNextcloud: Boolean = false,
    val defaultBook: String = "",
)

sealed interface CScreen {
    data object Home : CScreen
    data class Detail(val id: Long) : CScreen
    data class Edit(val contactId: Long?, val rawId: Long?, val draft: ContactDraft, val hasPhoto: Boolean = false) : CScreen
}

data class MoveProgress(val done: Int, val total: Int, val failed: Int = 0)

data class ContactsState(
    val loaded: Boolean = false,
    val permission: Boolean = false,
    val davx: Boolean = false,
    val contacts: List<ContactSummary> = emptyList(),
    val books: List<AddressBook> = emptyList(),
    val prefs: ContactsPrefs = ContactsPrefs(),
    val query: String = "",
    val screen: CScreen = CScreen.Home,
    val detail: ContactDetail? = null,
    val editGroups: List<ContactGroup> = emptyList(),
    val pendingDeletes: Set<Long> = emptySet(),
    val syncing: Boolean = false,
    val moving: MoveProgress? = null,
    val toast: CToast? = null,
    val showSettings: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val photoStamp: Long = 0,
) {
    val nextcloudBooks get() = books.filter { it.isNextcloud }
    /** Adressbücher, in die man sinnvoll speichern kann: Nextcloud zuerst, dann das Telefon. */
    val targetBooks get() = books.filter { it.kind == BookKind.NEXTCLOUD || (it.kind == BookKind.DEVICE && !it.type.contains("sim")) }
    /** Quellen für die Übernahme nach Nextcloud. */
    val sourceBooks get() = books.filter { (it.kind == BookKind.DEVICE || it.kind == BookKind.OTHER) && it.count > 0 }
    val defaultBook: AddressBook?
        get() = targetBooks.firstOrNull { it.key == prefs.defaultBook } ?: nextcloudBooks.firstOrNull() ?: targetBooks.firstOrNull()
    val visible: List<ContactSummary>
        get() = contacts.filter { it.id !in pendingDeletes && (!prefs.onlyNextcloud || it.synced) }
}

data class CToast(val text: String, val undoId: Long? = null, val stamp: Long = System.nanoTime())

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ContactsRepository(app)
    private val prefs = app.getSharedPreferences("alpenlan_contacts", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(ContactsState(prefs = readPrefs()))
    val state: StateFlow<ContactsState> = _s

    private val photos = LruCache<String, ImageBitmap>(200)
    private val noPhoto = HashSet<String>()
    private var reloadJob: Job? = null
    private val deleteJobs = HashMap<Long, Job>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (_s.value.moving != null) return
            reloadJob?.cancel()
            reloadJob = viewModelScope.launch { delay(400); load() }
        }
    }

    init {
        runCatching { app.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer) }
        refresh()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
    }

    private fun readPrefs() = ContactsPrefs(
        sortByFamily = prefs.getBoolean("sort_family", false),
        onlyNextcloud = prefs.getBoolean("only_nextcloud", false),
        defaultBook = prefs.getString("default_book", "") ?: "",
    )

    fun updatePrefs(t: (ContactsPrefs) -> ContactsPrefs) {
        val p = t(_s.value.prefs)
        prefs.edit().putBoolean("sort_family", p.sortByFamily).putBoolean("only_nextcloud", p.onlyNextcloud).putString("default_book", p.defaultBook).apply()
        _s.update { it.copy(prefs = p) }
    }

    fun refresh() { viewModelScope.launch { load() } }

    fun onResume() { _s.update { it.copy(today = LocalDate.now()) }; refresh() }

    private suspend fun load() {
        val app = getApplication<Application>()
        val r = withContext(Dispatchers.IO) {
            Triple(runCatching { repo.summaries() }.getOrDefault(emptyList()), runCatching { repo.addressBooks() }.getOrDefault(emptyList()), repo.canRead())
        }
        val detailId = (_s.value.screen as? CScreen.Detail)?.id
        val detail = if (detailId != null) withContext(Dispatchers.IO) { runCatching { repo.detail(detailId) }.getOrNull() } else null
        _s.update {
            it.copy(
                loaded = true, contacts = r.first, books = r.second, permission = r.third,
                davx = SyncHelper.isDavxInstalled(app),
                detail = detail ?: it.detail.takeIf { detailId == null },
                screen = if (detailId != null && detail == null) CScreen.Home else it.screen,
            )
        }
    }

    private fun clearPhotos() { photos.evictAll(); noPhoto.clear(); _s.update { it.copy(photoStamp = System.nanoTime()) } }

    fun setQuery(q: String) = _s.update { it.copy(query = q) }
    fun showSettings(v: Boolean) = _s.update { it.copy(showSettings = v) }
    fun dismissToast() = _s.update { it.copy(toast = null) }
    private fun toast(t: String, undo: Long? = null) = _s.update { it.copy(toast = CToast(t, undo)) }

    fun open(id: Long) {
        _s.update { it.copy(screen = CScreen.Detail(id), detail = null) }
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { runCatching { repo.detail(id) }.getOrNull() }
            _s.update { it.copy(detail = d) }
        }
    }

    fun back() {
        val sc = _s.value.screen
        _s.update {
            when (sc) {
                is CScreen.Edit -> if (sc.contactId != null) it.copy(screen = CScreen.Detail(sc.contactId)) else it.copy(screen = CScreen.Home)
                else -> it.copy(screen = CScreen.Home, detail = null)
            }
        }
    }

    fun loadGroups(book: AddressBook?) {
        viewModelScope.launch {
            val g = withContext(Dispatchers.IO) { repo.groups(book) }
            _s.update { it.copy(editGroups = g) }
        }
    }

    fun newContact() {
        val book = _s.value.defaultBook
        _s.update { it.copy(screen = CScreen.Edit(null, null, ContactDraft(book = book)), editGroups = emptyList()) }
        loadGroups(book)
    }

    fun edit(d: ContactDetail) {
        val draft = d.draft.let {
            it.copy(
                phones = it.phones.ifEmpty { listOf(Labeled("", android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)) },
                emails = it.emails.ifEmpty { listOf(Labeled("", android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME)) },
            )
        }
        _s.update { it.copy(screen = CScreen.Edit(d.id, d.rawId, draft, d.hasPhoto), editGroups = emptyList()) }
        loadGroups(d.book)
    }

    fun createGroup(book: AddressBook?, title: String, onCreated: (Long) -> Unit) {
        if (book == null || title.isBlank()) return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { repo.createGroup(book, title.trim()) }.getOrNull() }
            if (id != null) { loadGroups(book); onCreated(id) }
        }
    }

    suspend fun preparePhoto(uri: Uri): ByteArray? = withContext(Dispatchers.IO) { repo.preparePhoto(uri) }

    fun save(e: CScreen.Edit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    if (e.rawId != null) { repo.update(e.rawId, e.draft); e.contactId } else repo.create(e.draft)
                }.getOrNull()
            }
            if (id == null) {
                toast("Speichern fehlgeschlagen – Berechtigung prüfen")
                return@launch
            }
            if (e.draft.newPhoto != null) clearPhotos()
            _s.update { it.copy(screen = CScreen.Detail(id)) }
            toast(if (e.rawId != null) "Kontakt gespeichert" else "Kontakt erstellt")
            load()
            repo.requestSync(_s.value.books)
        }
    }

    fun toggleStar(d: ContactDetail) {
        _s.update { it.copy(detail = d.copy(starred = !d.starred)) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repo.setStarred(d.id, !d.starred) } }
            load()
        }
    }

    fun delete(d: ContactDetail) {
        _s.update { it.copy(pendingDeletes = it.pendingDeletes + d.id, screen = CScreen.Home, detail = null) }
        toast("${d.displayName} gelöscht", d.id)
        deleteJobs[d.id] = viewModelScope.launch {
            delay(5000)
            withContext(Dispatchers.IO) { runCatching { repo.delete(d.id, d.lookupKey) } }
            deleteJobs.remove(d.id)
            _s.update { it.copy(pendingDeletes = it.pendingDeletes - d.id) }
            load()
            repo.requestSync(_s.value.books)
        }
    }

    fun undo(id: Long) {
        deleteJobs.remove(id)?.cancel()
        _s.update { it.copy(pendingDeletes = it.pendingDeletes - id, toast = null) }
    }

    /** Einen Kontakt in die Nextcloud verschieben. */
    fun moveOne(d: ContactDetail) {
        val target = _s.value.defaultBook?.takeIf { it.isNextcloud } ?: _s.value.nextcloudBooks.firstOrNull() ?: run {
            toast("Kein Nextcloud-Adressbuch gefunden"); return
        }
        viewModelScope.launch {
            val newId = withContext(Dispatchers.IO) { runCatching { repo.moveTo(d.id, target) }.getOrNull() }
            if (newId == null) { toast("Verschieben fehlgeschlagen"); return@launch }
            clearPhotos()
            _s.update { it.copy(screen = CScreen.Detail(newId)) }
            toast("In Nextcloud verschoben")
            load()
            repo.requestSync(listOf(target))
        }
    }

    /** Alle Kontakte aus den gewählten Gerät-/Google-Adressbüchern in die Nextcloud übernehmen. */
    fun moveAll(sources: Set<String>, target: AddressBook) {
        val ids = _s.value.contacts.filter { c -> !c.synced && c.books.any { it.key in sources } }.map { it.id }
        if (ids.isEmpty()) { toast("Keine Kontakte zum Übernehmen"); return }
        viewModelScope.launch {
            _s.update { it.copy(moving = MoveProgress(0, ids.size), showSettings = false) }
            var failed = 0
            ids.forEachIndexed { i, id ->
                val ok = withContext(Dispatchers.IO) { runCatching { repo.moveTo(id, target) }.getOrNull() } != null
                if (!ok) failed++
                _s.update { it.copy(moving = MoveProgress(i + 1, ids.size, failed)) }
            }
            _s.update { it.copy(moving = null) }
            clearPhotos()
            toast(if (failed == 0) "${ids.size} Kontakte in Nextcloud übernommen" else "${ids.size - failed} übernommen, $failed fehlgeschlagen")
            load()
            repo.requestSync(listOf(target))
        }
    }

    fun sync() {
        val books = _s.value.nextcloudBooks
        if (books.isEmpty()) { toast("Kein Nextcloud-Adressbuch in DAVx⁵ gefunden"); return }
        repo.requestSync(books)
        viewModelScope.launch {
            _s.update { it.copy(syncing = true) }
            delay(1200)
            var n = 0
            while (n < 60 && withContext(Dispatchers.IO) { repo.isSyncing(books) }) { delay(1000); n++ }
            _s.update { it.copy(syncing = false) }
            load()
        }
    }

    /** Foto laden (zwischengespeichert). */
    suspend fun photo(id: Long, highRes: Boolean): ImageBitmap? {
        val key = "$id/$highRes"
        photos.get(key)?.let { return it }
        if (key in noPhoto) return null
        val bmp = withContext(Dispatchers.IO) { repo.photo(id, highRes) }?.asImageBitmap()
        if (bmp != null) photos.put(key, bmp) else noPhoto += key
        return bmp
    }
}
