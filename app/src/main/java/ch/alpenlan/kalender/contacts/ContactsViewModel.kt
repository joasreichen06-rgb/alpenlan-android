package ch.alpenlan.kalender.contacts

import android.app.Application
import android.content.Context
import android.database.ContentObserver
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
    data class Edit(val contactId: Long?, val rawId: Long?, val draft: ContactDraft) : CScreen
}

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
    val pendingDeletes: Set<Long> = emptySet(),
    val syncing: Boolean = false,
    val toast: CToast? = null,
    val showSettings: Boolean = false,
    val today: LocalDate = LocalDate.now(),
) {
    val nextcloudBooks get() = books.filter { it.isNextcloud }
    val defaultBook: AddressBook?
        get() = books.firstOrNull { "${it.type}/${it.name}" == prefs.defaultBook } ?: nextcloudBooks.firstOrNull() ?: books.firstOrNull()
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
        val detail = if (detailId != null) withContext(Dispatchers.IO) { repo.detail(detailId) } else null
        photos.evictAll(); noPhoto.clear()
        _s.update {
            it.copy(
                loaded = true, contacts = r.first, books = r.second, permission = r.third,
                davx = SyncHelper.isDavxInstalled(app),
                detail = detail ?: it.detail.takeIf { detailId == null },
                screen = if (detailId != null && detail == null) CScreen.Home else it.screen,
            )
        }
    }

    fun setQuery(q: String) = _s.update { it.copy(query = q) }
    fun showSettings(v: Boolean) = _s.update { it.copy(showSettings = v) }
    fun dismissToast() = _s.update { it.copy(toast = null) }

    fun open(id: Long) {
        _s.update { it.copy(screen = CScreen.Detail(id), detail = null) }
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { repo.detail(id) }
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

    fun newContact(prefill: ContactDraft = ContactDraft()) =
        _s.update { it.copy(screen = CScreen.Edit(null, null, prefill.copy(book = prefill.book ?: it.defaultBook))) }

    fun edit(d: ContactDetail) = _s.update {
        it.copy(screen = CScreen.Edit(d.id, d.rawId, ContactDraft(
            given = d.given, family = d.family, company = d.company, jobTitle = d.jobTitle,
            phones = d.phones.ifEmpty { listOf(Labeled("", android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)) },
            emails = d.emails.ifEmpty { listOf(Labeled("", android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME)) },
            addresses = d.addresses, website = d.websites.firstOrNull() ?: "", birthday = d.birthday, note = d.note, book = d.book,
        )))
    }

    fun save(e: CScreen.Edit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching {
                    if (e.rawId != null) { repo.update(e.rawId, e.draft); e.contactId } else repo.create(e.draft)
                }.getOrNull()
            }
            if (id == null) {
                _s.update { it.copy(toast = CToast("Speichern fehlgeschlagen – Berechtigung prüfen")) }
                return@launch
            }
            _s.update { it.copy(screen = CScreen.Detail(id), toast = CToast(if (e.rawId != null) "Kontakt gespeichert" else "Kontakt erstellt")) }
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
        _s.update { it.copy(pendingDeletes = it.pendingDeletes + d.id, screen = CScreen.Home, detail = null, toast = CToast("${d.displayName} gelöscht", d.id)) }
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

    fun sync() {
        val books = _s.value.nextcloudBooks
        if (books.isEmpty()) {
            _s.update { it.copy(toast = CToast("Kein Nextcloud-Adressbuch in DAVx⁵ gefunden")) }
            return
        }
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
