package ch.alpenlan.kalender.contacts

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.MonthDay

/** DAVx⁵ legt pro Nextcloud-Adressbuch ein Konto dieses Typs an. */
const val DAVX_ADDRESSBOOK_TYPE = "bitfire.at.davdroid.address_book"

data class AddressBook(val name: String, val type: String) {
    val isNextcloud get() = type == DAVX_ADDRESSBOOK_TYPE
    val label get() = if (isNextcloud) name else when (type) {
        "" -> "Gerät"
        else -> "$name (${type.substringAfterLast('.')})"
    }
}

/** Kurzinfo für die Liste. */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val nameAlt: String,
    val starred: Boolean,
    val hasPhoto: Boolean,
    val phones: List<String>,
    val emails: List<String>,
    val company: String?,
    val birthday: Birthday?,
    val books: Set<AddressBook>,
) {
    val synced get() = books.any { it.isNextcloud }
}

data class Birthday(val month: Int, val day: Int, val year: Int?) {
    fun nextOccurrence(today: LocalDate): LocalDate {
        val md = MonthDay.of(month, day)
        var d = if (md.isValidYear(today.year)) md.atYear(today.year) else LocalDate.of(today.year, 3, 1)
        if (d.isBefore(today)) d = if (md.isValidYear(today.year + 1)) md.atYear(today.year + 1) else LocalDate.of(today.year + 1, 3, 1)
        return d
    }
    fun ageOn(d: LocalDate): Int? = year?.let { d.year - it }
    fun store(): String = if (year != null) "%04d-%02d-%02d".format(year, month, day) else "--%02d-%02d".format(month, day)

    companion object {
        fun parse(raw: String?): Birthday? {
            if (raw.isNullOrBlank()) return null
            val s = raw.trim()
            runCatching {
                if (s.startsWith("--")) {
                    val p = s.removePrefix("--").split("-")
                    return Birthday(p[0].toInt(), p[1].take(2).toInt(), null)
                }
                val digits = s.take(10)
                if (digits.length == 8 && digits.all { it.isDigit() }) {
                    return Birthday(digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt(), digits.substring(0, 4).toInt())
                }
                val p = digits.split("-")
                return Birthday(p[1].toInt(), p[2].toInt(), p[0].toInt().takeIf { it > 1604 })
            }
            return null
        }
    }
}

data class Labeled(val value: String, val type: Int, val label: String? = null)

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val rawId: Long?,
    val book: AddressBook?,
    val displayName: String,
    val given: String,
    val family: String,
    val starred: Boolean,
    val company: String,
    val jobTitle: String,
    val phones: List<Labeled>,
    val emails: List<Labeled>,
    val addresses: List<Labeled>,
    val websites: List<String>,
    val birthday: Birthday?,
    val note: String,
)

data class ContactDraft(
    val given: String = "",
    val family: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val phones: List<Labeled> = listOf(Labeled("", Phone.TYPE_MOBILE)),
    val emails: List<Labeled> = listOf(Labeled("", Email.TYPE_HOME)),
    val addresses: List<Labeled> = emptyList(),
    val website: String = "",
    val birthday: Birthday? = null,
    val note: String = "",
    val book: AddressBook? = null,
)

object Types {
    val phone = listOf(Phone.TYPE_MOBILE to "Mobil", Phone.TYPE_HOME to "Privat", Phone.TYPE_WORK to "Arbeit", Phone.TYPE_OTHER to "Andere")
    val email = listOf(Email.TYPE_HOME to "Privat", Email.TYPE_WORK to "Arbeit", Email.TYPE_OTHER to "Andere")
    val postal = listOf(StructuredPostal.TYPE_HOME to "Privat", StructuredPostal.TYPE_WORK to "Arbeit", StructuredPostal.TYPE_OTHER to "Andere")
    fun phoneLabel(l: Labeled) = l.label?.takeIf { l.type == Phone.TYPE_CUSTOM && it.isNotBlank() }
        ?: phone.firstOrNull { it.first == l.type }?.second ?: if (l.type == Phone.TYPE_FAX_WORK || l.type == Phone.TYPE_FAX_HOME) "Fax" else "Telefon"
    fun emailLabel(l: Labeled) = l.label?.takeIf { l.type == Email.TYPE_CUSTOM && it.isNotBlank() }
        ?: email.firstOrNull { it.first == l.type }?.second ?: if (l.type == Email.TYPE_MOBILE) "Mobil" else "E-Mail"
    fun postalLabel(l: Labeled) = l.label?.takeIf { l.type == StructuredPostal.TYPE_CUSTOM && it.isNotBlank() }
        ?: postal.firstOrNull { it.first == l.type }?.second ?: "Adresse"
}

class ContactsRepository(private val ctx: Context) {
    private val cr: ContentResolver get() = ctx.contentResolver

    fun canRead() = granted(Manifest.permission.READ_CONTACTS)
    fun canWrite() = granted(Manifest.permission.WRITE_CONTACTS)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Alle Adressbücher (Konten), die Kontakte enthalten oder von DAVx⁵ angelegt wurden. */
    fun addressBooks(): List<AddressBook> {
        if (!canRead()) return emptyList()
        val set = linkedSetOf<AddressBook>()
        runCatching {
            cr.query(ContactsContract.Settings.CONTENT_URI, arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE), null, null, null)?.use { c ->
                while (c.moveToNext()) set += AddressBook(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        }
        runCatching {
            cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), "${RawContacts.DELETED}=0", null, null)?.use { c ->
                while (c.moveToNext()) set += AddressBook(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        }
        return set.filter { it.name.isNotBlank() || it.type.isNotBlank() }
            .sortedWith(compareBy({ !it.isNextcloud }, { it.name.lowercase() }))
    }

    fun summaries(): List<ContactSummary> {
        if (!canRead()) return emptyList()
        val base = linkedMapOf<Long, ContactSummary>()
        cr.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.DISPLAY_NAME_ALTERNATIVE, Contacts.STARRED, Contacts.PHOTO_THUMBNAIL_URI),
            null, null, "${Contacts.SORT_KEY_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                base[c.getLong(0)] = ContactSummary(
                    id = c.getLong(0), lookupKey = c.getString(1) ?: "", name = name,
                    nameAlt = c.getString(3)?.takeIf { it.isNotBlank() } ?: name,
                    starred = c.getInt(4) == 1, hasPhoto = !c.getString(5).isNullOrBlank(),
                    phones = emptyList(), emails = emptyList(), company = null, birthday = null, books = emptySet(),
                )
            }
        }
        val phones = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        val company = HashMap<Long, String>()
        val bday = HashMap<Long, Birthday>()
        val mimes = listOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE)
        cr.query(
            Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2),
            "${Data.MIMETYPE} IN (${mimes.joinToString { "?" }})", mimes.toTypedArray(), "${Data.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val v = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                when (c.getString(1)) {
                    Phone.CONTENT_ITEM_TYPE -> phones.getOrPut(id) { mutableListOf() } += v
                    Email.CONTENT_ITEM_TYPE -> emails.getOrPut(id) { mutableListOf() } += v
                    Organization.CONTENT_ITEM_TYPE -> company.putIfAbsent(id, v)
                    Event.CONTENT_ITEM_TYPE -> if (c.getInt(3) == Event.TYPE_BIRTHDAY) Birthday.parse(v)?.let { bday[id] = it }
                }
            }
        }
        val books = HashMap<Long, MutableSet<AddressBook>>()
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), "${RawContacts.DELETED}=0", null, null)?.use { c ->
            while (c.moveToNext()) books.getOrPut(c.getLong(0)) { mutableSetOf() } += AddressBook(c.getString(1) ?: "", c.getString(2) ?: "")
        }
        return base.values.map {
            it.copy(
                phones = phones[it.id]?.distinctBy { p -> p.filter { ch -> ch.isDigit() || ch == '+' } }.orEmpty(),
                emails = emails[it.id]?.distinct().orEmpty(),
                company = company[it.id], birthday = bday[it.id], books = books[it.id].orEmpty(),
            )
        }
    }

    fun detail(contactId: Long): ContactDetail? {
        if (!canRead()) return null
        var lookup = ""
        var display = ""
        var starred = false
        cr.query(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            lookup = c.getString(0) ?: ""
            display = c.getString(1) ?: ""
            starred = c.getInt(2) == 1
        } ?: return null

        // Bevorzugt den Rohkontakt aus einem Nextcloud-Adressbuch bearbeiten
        var rawId: Long? = null
        var book: AddressBook? = null
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val b = AddressBook(c.getString(1) ?: "", c.getString(2) ?: "")
                if (rawId == null || (b.isNextcloud && book?.isNextcloud != true)) { rawId = c.getLong(0); book = b }
            }
        }

        var given = ""; var family = ""; var comp = ""; var title = ""; var note = ""
        var birthday: Birthday? = null
        val phones = mutableListOf<Labeled>(); val emails = mutableListOf<Labeled>(); val addrs = mutableListOf<Labeled>(); val webs = mutableListOf<String>()
        val sel = if (rawId != null) "${Data.RAW_CONTACT_ID}=?" else "${Data.CONTACT_ID}=?"
        val arg = (rawId ?: contactId).toString()
        cr.query(Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5), sel, arrayOf(arg), "${Data.IS_PRIMARY} DESC")?.use { c ->
            while (c.moveToNext()) {
                val d1 = c.getString(1) ?: ""
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> { given = c.getString(2) ?: ""; family = c.getString(3) ?: "" }
                    Phone.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) phones += Labeled(d1, c.getInt(2), c.getString(3))
                    Email.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) emails += Labeled(d1, c.getInt(2), c.getString(3))
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) addrs += Labeled(d1, c.getInt(2), c.getString(3))
                    Organization.CONTENT_ITEM_TYPE -> { comp = d1; title = c.getString(4) ?: "" }
                    Website.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) webs += d1
                    Event.CONTENT_ITEM_TYPE -> if (c.getInt(2) == Event.TYPE_BIRTHDAY) birthday = Birthday.parse(d1)
                    Note.CONTENT_ITEM_TYPE -> note = d1
                }
            }
        }
        return ContactDetail(contactId, lookup, rawId, book, display, given, family, starred, comp, title,
            phones.distinctBy { it.value.filter { ch -> ch.isDigit() || ch == '+' } }, emails.distinctBy { it.value.lowercase() }, addrs, webs.distinct(), birthday, note)
    }

    fun photo(contactId: Long, highRes: Boolean): Bitmap? = runCatching {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        Contacts.openContactPhotoInputStream(cr, uri, highRes)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private val managed = listOf(
        StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
    )

    private fun dataOps(d: ContactDraft, rawId: Long?): List<ContentProviderOperation> {
        val ops = mutableListOf<ContentProviderOperation>()
        fun row(mime: String, vararg kv: Pair<String, Any?>) {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            if (rawId != null) b.withValue(Data.RAW_CONTACT_ID, rawId) else b.withValueBackReference(Data.RAW_CONTACT_ID, 0)
            b.withValue(Data.MIMETYPE, mime)
            kv.forEach { (k, v) -> b.withValue(k, v) }
            ops += b.build()
        }
        if (d.given.isNotBlank() || d.family.isNotBlank()) {
            row(StructuredName.CONTENT_ITEM_TYPE, StructuredName.GIVEN_NAME to d.given.trim(), StructuredName.FAMILY_NAME to d.family.trim())
        } else if (d.company.isNotBlank()) {
            row(StructuredName.CONTENT_ITEM_TYPE, StructuredName.DISPLAY_NAME to d.company.trim())
        }
        d.phones.filter { it.value.isNotBlank() }.forEach { row(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it.value.trim(), Phone.TYPE to it.type, Phone.LABEL to it.label) }
        d.emails.filter { it.value.isNotBlank() }.forEach { row(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to it.value.trim(), Email.TYPE to it.type, Email.LABEL to it.label) }
        d.addresses.filter { it.value.isNotBlank() }.forEach {
            row(StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.FORMATTED_ADDRESS to it.value.trim(), StructuredPostal.TYPE to it.type, StructuredPostal.LABEL to it.label)
        }
        if (d.company.isNotBlank() || d.jobTitle.isNotBlank()) {
            row(Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to d.company.trim(), Organization.TITLE to d.jobTitle.trim(), Organization.TYPE to Organization.TYPE_WORK)
        }
        if (d.website.isNotBlank()) row(Website.CONTENT_ITEM_TYPE, Website.URL to d.website.trim(), Website.TYPE to Website.TYPE_HOMEPAGE)
        d.birthday?.let { row(Event.CONTENT_ITEM_TYPE, Event.START_DATE to it.store(), Event.TYPE to Event.TYPE_BIRTHDAY) }
        if (d.note.isNotBlank()) row(Note.CONTENT_ITEM_TYPE, Note.NOTE to d.note.trim())
        return ops
    }

    /** Neuer Kontakt im gewählten Adressbuch. Gibt die Kontakt-ID zurück. */
    fun create(d: ContactDraft): Long? {
        if (!canWrite()) return null
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, d.book?.type?.takeIf { it.isNotBlank() })
            .withValue(RawContacts.ACCOUNT_NAME, d.book?.name?.takeIf { it.isNotBlank() })
            .build()
        ops += dataOps(d, null)
        val res = cr.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawUri = res.firstOrNull()?.uri ?: return null
        val rawId = ContentUris.parseId(rawUri)
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID}=?", arrayOf(rawId.toString()), null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    /** Ersetzt die bearbeitbaren Felder eines bestehenden Rohkontakts. */
    fun update(rawId: Long, d: ContactDraft) {
        if (!canWrite()) return
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (${managed.joinToString { "?" }})", (listOf(rawId.toString()) + managed).toTypedArray())
            .build()
        ops += dataOps(d, rawId)
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    fun delete(contactId: Long, lookupKey: String) {
        if (!canWrite()) return
        val uri = Contacts.getLookupUri(contactId, lookupKey) ?: ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        cr.delete(uri, null, null)
    }

    fun setStarred(contactId: Long, starred: Boolean) {
        if (!canWrite()) return
        cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), android.content.ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }, null, null)
    }

    fun requestSync(books: Collection<AddressBook>) {
        books.filter { it.isNextcloud }.forEach { b ->
            runCatching {
                ContentResolver.requestSync(Account(b.name, b.type), ContactsContract.AUTHORITY, Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                })
            }
        }
    }

    fun isSyncing(books: Collection<AddressBook>): Boolean = books.filter { it.isNextcloud }.any { b ->
        runCatching { ContentResolver.isSyncActive(Account(b.name, b.type), ContactsContract.AUTHORITY) }.getOrDefault(false)
    }
}
