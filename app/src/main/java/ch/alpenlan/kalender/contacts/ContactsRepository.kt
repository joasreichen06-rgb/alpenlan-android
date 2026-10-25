package ch.alpenlan.kalender.contacts

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.MonthDay

enum class BookKind { NEXTCLOUD, DEVICE, OTHER, APP }

private val APP_HINTS = listOf(
    "whatsapp", "threema", "signal", "telegram", "tachyon", "messenger", "viber", "skype", "facebook", "linkedin",
    "wire", "element", "discord", "snapchat", "instagram", "teams", "zoom", "duo", "meet", "signin", "grok",
)

/** Ein Konto im Android-Kontaktspeicher (DAVx⁵-Adressbuch, Telefon, Google, Messenger …). */
data class AddressBook(val name: String, val type: String, val count: Int = 0) {
    val kind: BookKind
        get() {
            val t = type.lowercase()
            val n = name.lowercase()
            return when {
                // Nur echte DAVx⁵-Adressbücher – das DAVx⁵-Hauptkonto synchronisiert keine Kontakte.
                (t.contains("davdroid") || t.contains("bitfire")) && t.contains("address") -> BookKind.NEXTCLOUD
                t.contains("davdroid") || t.contains("bitfire") -> BookKind.OTHER
                t.isEmpty() || t.contains("contact.phone") || t.contains("localphone") || t == "com.android.contacts" ||
                    t.contains("contact.sim") || t.endsWith(".sim") || t.endsWith(".sim2") -> BookKind.DEVICE
                APP_HINTS.any { t.contains(it) || n.contains(it) } -> BookKind.APP
                else -> BookKind.OTHER
            }
        }
    val isNextcloud get() = kind == BookKind.NEXTCLOUD
    val key get() = "$type/$name"
    val label: String
        get() = when (kind) {
            BookKind.NEXTCLOUD -> name.replace(Regex("\\s*#\\d+$"), "")
            BookKind.DEVICE -> if (type.contains("sim")) "SIM-Karte" else "Telefon (nur Gerät)"
            else -> "$name (${type.substringAfterLast('.')})"
        }
    fun same(o: AddressBook?) = o != null && o.name == name && o.type == type
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
    /** Liegt (auch) in einem Gerät-/Google-Adressbuch und noch nicht in Nextcloud. */
    val movable get() = !synced && books.any { it.kind == BookKind.DEVICE || it.kind == BookKind.OTHER }
}

/** Datum ohne Pflicht-Jahr (vCard erlaubt "--MM-DD"). */
data class Birthday(val month: Int, val day: Int, val year: Int?) {
    fun nextOccurrence(today: LocalDate): LocalDate {
        val md = MonthDay.of(month, day)
        var d = if (md.isValidYear(today.year)) md.atYear(today.year) else LocalDate.of(today.year, 3, 1)
        if (d.isBefore(today)) d = if (md.isValidYear(today.year + 1)) md.atYear(today.year + 1) else LocalDate.of(today.year + 1, 3, 1)
        return d
    }
    fun ageOn(d: LocalDate): Int? = year?.let { d.year - it }
    fun store(): String = if (year != null) "%04d-%02d-%02d".format(year, month, day) else "--%02d-%02d".format(month, day)
    fun text(months: List<String>) = "$day. ${months[month - 1]}" + (year?.let { " $it" } ?: "")

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
                if (digits.length >= 8 && digits.take(8).all { it.isDigit() }) {
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

data class PostalAddr(
    val street: String = "", val postcode: String = "", val city: String = "", val region: String = "", val country: String = "",
    val type: Int = StructuredPostal.TYPE_HOME, val label: String? = null, val formatted: String = "",
) {
    val isBlank get() = listOf(street, postcode, city, region, country).all { it.isBlank() }
    fun display(): String {
        val parts = listOf(street, listOf(postcode, city).filter { it.isNotBlank() }.joinToString(" "), region, country).filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) parts.joinToString("\n") else formatted
    }
}

data class DateEntry(val date: Birthday, val type: Int = Event.TYPE_BIRTHDAY, val label: String? = null)

data class ContactGroup(val id: Long, val title: String)

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val rawId: Long?,
    val book: AddressBook?,
    val allRaw: List<Pair<Long, AddressBook>>,
    val displayName: String,
    val starred: Boolean,
    val hasPhoto: Boolean,
    val draft: ContactDraft,
) {
    val birthday get() = draft.events.firstOrNull { it.type == Event.TYPE_BIRTHDAY }?.date
}

/** Alle bearbeitbaren Felder eines Kontakts. */
data class ContactDraft(
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val phoneticGiven: String = "",
    val phoneticFamily: String = "",
    val nickname: String = "",
    val company: String = "",
    val department: String = "",
    val jobTitle: String = "",
    val phones: List<Labeled> = listOf(Labeled("", Phone.TYPE_MOBILE)),
    val emails: List<Labeled> = listOf(Labeled("", Email.TYPE_HOME)),
    val addresses: List<PostalAddr> = emptyList(),
    val events: List<DateEntry> = emptyList(),
    val websites: List<String> = emptyList(),
    val relations: List<Labeled> = emptyList(),
    val groups: Set<Long> = emptySet(),
    val note: String = "",
    val book: AddressBook? = null,
    /** null = Foto unverändert lassen; leeres Array = Foto entfernen. */
    val newPhoto: ByteArray? = null,
) {
    val hasName get() = listOf(given, family, middle, nickname, company).any { it.isNotBlank() }
}

object Types {
    val phone = listOf(Phone.TYPE_MOBILE to "Mobil", Phone.TYPE_HOME to "Privat", Phone.TYPE_WORK to "Arbeit", Phone.TYPE_MAIN to "Haupt", Phone.TYPE_OTHER to "Andere")
    val email = listOf(Email.TYPE_HOME to "Privat", Email.TYPE_WORK to "Arbeit", Email.TYPE_OTHER to "Andere")
    val postal = listOf(StructuredPostal.TYPE_HOME to "Privat", StructuredPostal.TYPE_WORK to "Arbeit", StructuredPostal.TYPE_OTHER to "Andere")
    val event = listOf(Event.TYPE_BIRTHDAY to "Geburtstag", Event.TYPE_ANNIVERSARY to "Jahrestag", Event.TYPE_OTHER to "Anderes Datum")
    val relation = listOf(
        Relation.TYPE_PARTNER to "Partner/in", Relation.TYPE_SPOUSE to "Ehepartner/in", Relation.TYPE_CHILD to "Kind",
        Relation.TYPE_MOTHER to "Mutter", Relation.TYPE_FATHER to "Vater", Relation.TYPE_SISTER to "Schwester",
        Relation.TYPE_BROTHER to "Bruder", Relation.TYPE_FRIEND to "Freund/in", Relation.TYPE_RELATIVE to "Verwandt",
        Relation.TYPE_ASSISTANT to "Assistenz", Relation.TYPE_MANAGER to "Vorgesetzt",
    )
    private fun lbl(list: List<Pair<Int, String>>, type: Int, custom: String?, customType: Int, fallback: String) =
        custom?.takeIf { type == customType && it.isNotBlank() } ?: list.firstOrNull { it.first == type }?.second ?: fallback
    fun phoneLabel(l: Labeled) = lbl(phone, l.type, l.label, Phone.TYPE_CUSTOM, if (l.type == Phone.TYPE_FAX_HOME || l.type == Phone.TYPE_FAX_WORK) "Fax" else "Telefon")
    fun emailLabel(l: Labeled) = lbl(email, l.type, l.label, Email.TYPE_CUSTOM, if (l.type == Email.TYPE_MOBILE) "Mobil" else "E-Mail")
    fun postalLabel(a: PostalAddr) = lbl(postal, a.type, a.label, StructuredPostal.TYPE_CUSTOM, "Adresse")
    fun eventLabel(e: DateEntry) = lbl(event, e.type, e.label, Event.TYPE_CUSTOM, "Datum")
    fun relationLabel(l: Labeled) = lbl(relation, l.type, l.label, Relation.TYPE_CUSTOM, "Beziehung")
}

class ContactsRepository(private val ctx: Context) {
    private val cr: ContentResolver get() = ctx.contentResolver

    fun canRead() = granted(Manifest.permission.READ_CONTACTS)
    fun canWrite() = granted(Manifest.permission.WRITE_CONTACTS)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Alle Konten mit Anzahl Kontakte. */
    fun addressBooks(): List<AddressBook> {
        if (!canRead()) return emptyList()
        val counts = linkedMapOf<Pair<String, String>, Int>()
        runCatching {
            cr.query(ContactsContract.Settings.CONTENT_URI, arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE), null, null, null)?.use { c ->
                while (c.moveToNext()) counts.putIfAbsent((c.getString(0) ?: "") to (c.getString(1) ?: ""), 0)
            }
        }
        runCatching {
            cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), "${RawContacts.DELETED}=0", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val k = (c.getString(0) ?: "") to (c.getString(1) ?: "")
                    counts[k] = (counts[k] ?: 0) + 1
                }
            }
        }
        return counts.map { (k, n) -> AddressBook(k.first, k.second, n) }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
    }

    fun groups(book: AddressBook?): List<ContactGroup> {
        if (!canRead() || book == null) return emptyList()
        val out = mutableListOf<ContactGroup>()
        runCatching {
            cr.query(Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE),
                "${Groups.ACCOUNT_NAME}=? AND ${Groups.ACCOUNT_TYPE}=? AND ${Groups.DELETED}=0", arrayOf(book.name, book.type), "${Groups.TITLE} COLLATE LOCALIZED ASC")?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    out += ContactGroup(c.getLong(0), t)
                }
            }
        }
        return out
    }

    fun createGroup(book: AddressBook, title: String): Long? {
        if (!canWrite()) return null
        val uri = cr.insert(Groups.CONTENT_URI, ContentValues().apply {
            put(Groups.ACCOUNT_NAME, book.name); put(Groups.ACCOUNT_TYPE, book.type); put(Groups.TITLE, title); put(Groups.GROUP_VISIBLE, 1)
        }) ?: return null
        return ContentUris.parseId(uri)
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
        var hasPhoto = false
        val found = cr.query(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_ID), null, null, null)?.use { c ->
            if (!c.moveToFirst()) false else {
                lookup = c.getString(0) ?: ""
                display = c.getString(1) ?: ""
                starred = c.getInt(2) == 1
                hasPhoto = !c.isNull(3)
                true
            }
        } ?: false
        if (!found) return null

        // Rohkontakte: bevorzugt den aus Nextcloud bearbeiten, sonst Gerät/Google, Messenger nie.
        val raws = mutableListOf<Pair<Long, AddressBook>>()
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null)?.use { c ->
            while (c.moveToNext()) raws += c.getLong(0) to AddressBook(c.getString(1) ?: "", c.getString(2) ?: "")
        }
        val main = raws.minByOrNull { it.second.kind.ordinal }
        val rawId = main?.first

        var d = ContactDraft(phones = emptyList(), emails = emptyList(), book = main?.second)
        val phones = mutableListOf<Labeled>(); val emails = mutableListOf<Labeled>(); val addrs = mutableListOf<PostalAddr>()
        val events = mutableListOf<DateEntry>(); val webs = mutableListOf<String>(); val rels = mutableListOf<Labeled>(); val groups = mutableSetOf<Long>()
        val sel = if (rawId != null) "${Data.RAW_CONTACT_ID}=?" else "${Data.CONTACT_ID}=?"
        val proj = arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10)
        cr.query(Data.CONTENT_URI, proj, sel, arrayOf((rawId ?: contactId).toString()), "${Data.IS_PRIMARY} DESC")?.use { c ->
            fun s(i: Int) = c.getString(i) ?: ""
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> d = d.copy(
                        given = s(2), family = s(3), prefix = s(4), middle = s(5), suffix = s(6),
                        phoneticGiven = s(7), phoneticFamily = s(9),
                    )
                    Phone.CONTENT_ITEM_TYPE -> if (s(1).isNotBlank()) phones += Labeled(s(1), c.getInt(2), c.getString(3))
                    Email.CONTENT_ITEM_TYPE -> if (s(1).isNotBlank()) emails += Labeled(s(1), c.getInt(2), c.getString(3))
                    StructuredPostal.CONTENT_ITEM_TYPE -> {
                        // DATA4 Strasse, DATA7 Ort, DATA8 Region, DATA9 PLZ, DATA10 Land
                        val a = PostalAddr(street = s(4), city = s(7), region = s(8), postcode = s(9), country = s(10), type = c.getInt(2), label = c.getString(3), formatted = s(1))
                        addrs += if (a.isBlank && a.formatted.isNotBlank()) a.copy(street = a.formatted) else a
                    }
                    Organization.CONTENT_ITEM_TYPE -> d = d.copy(company = s(1), jobTitle = s(4), department = s(5))
                    Nickname.CONTENT_ITEM_TYPE -> if (d.nickname.isBlank()) d = d.copy(nickname = s(1))
                    Website.CONTENT_ITEM_TYPE -> if (s(1).isNotBlank()) webs += s(1)
                    Event.CONTENT_ITEM_TYPE -> Birthday.parse(s(1))?.let { events += DateEntry(it, c.getInt(2), c.getString(3)) }
                    Relation.CONTENT_ITEM_TYPE -> if (s(1).isNotBlank()) rels += Labeled(s(1), c.getInt(2), c.getString(3))
                    GroupMembership.CONTENT_ITEM_TYPE -> if (!c.isNull(1)) groups += c.getLong(1)
                    Note.CONTENT_ITEM_TYPE -> d = d.copy(note = s(1))
                }
            }
        }
        d = d.copy(
            phones = phones.distinctBy { it.value.filter { ch -> ch.isDigit() || ch == '+' } },
            emails = emails.distinctBy { it.value.lowercase() },
            addresses = addrs, events = events.sortedBy { it.type }, websites = webs.distinct(), relations = rels, groups = groups,
        )
        return ContactDetail(contactId, lookup, rawId, main?.second, raws, display, starred, hasPhoto, d)
    }

    fun photo(contactId: Long, highRes: Boolean): Bitmap? = runCatching {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        Contacts.openContactPhotoInputStream(cr, uri, highRes)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    /** Bild aus der Galerie verkleinern und als JPEG für den Kontakt vorbereiten. */
    fun preparePhoto(uri: Uri): ByteArray? = runCatching {
        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = if (side > 720) Bitmap.createScaledBitmap(square, 720, 720, true) else square
        ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
    }.getOrNull()

    private fun photoBytes(contactId: Long): ByteArray? = runCatching {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        Contacts.openContactPhotoInputStream(cr, uri, true)?.use { it.readBytes() }
    }.getOrNull()

    private val managed = listOf(
        StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
        Nickname.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE, GroupMembership.CONTENT_ITEM_TYPE,
    )

    private fun dataOps(d: ContactDraft, rawId: Long?, includeGroups: Boolean): List<ContentProviderOperation> {
        val ops = mutableListOf<ContentProviderOperation>()
        fun row(mime: String, vararg kv: Pair<String, Any?>) {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            if (rawId != null) b.withValue(Data.RAW_CONTACT_ID, rawId) else b.withValueBackReference(Data.RAW_CONTACT_ID, 0)
            b.withValue(Data.MIMETYPE, mime)
            kv.forEach { (k, v) -> b.withValue(k, v) }
            ops += b.build()
        }
        fun t(s: String) = s.trim().ifBlank { null }
        if (listOf(d.prefix, d.given, d.middle, d.family, d.suffix, d.phoneticGiven, d.phoneticFamily).any { it.isNotBlank() }) {
            row(
                StructuredName.CONTENT_ITEM_TYPE,
                StructuredName.PREFIX to t(d.prefix), StructuredName.GIVEN_NAME to t(d.given), StructuredName.MIDDLE_NAME to t(d.middle),
                StructuredName.FAMILY_NAME to t(d.family), StructuredName.SUFFIX to t(d.suffix),
                StructuredName.PHONETIC_GIVEN_NAME to t(d.phoneticGiven), StructuredName.PHONETIC_FAMILY_NAME to t(d.phoneticFamily),
            )
        } else if (d.company.isNotBlank()) {
            row(StructuredName.CONTENT_ITEM_TYPE, StructuredName.DISPLAY_NAME to d.company.trim())
        }
        if (d.nickname.isNotBlank()) row(Nickname.CONTENT_ITEM_TYPE, Nickname.NAME to d.nickname.trim(), Nickname.TYPE to Nickname.TYPE_DEFAULT)
        d.phones.filter { it.value.isNotBlank() }.forEach { row(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it.value.trim(), Phone.TYPE to it.type, Phone.LABEL to it.label) }
        d.emails.filter { it.value.isNotBlank() }.forEach { row(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to it.value.trim(), Email.TYPE to it.type, Email.LABEL to it.label) }
        d.addresses.filter { !it.isBlank }.forEach {
            row(
                StructuredPostal.CONTENT_ITEM_TYPE,
                StructuredPostal.STREET to t(it.street), StructuredPostal.POSTCODE to t(it.postcode), StructuredPostal.CITY to t(it.city),
                StructuredPostal.REGION to t(it.region), StructuredPostal.COUNTRY to t(it.country),
                StructuredPostal.FORMATTED_ADDRESS to it.display().replace("\n", ", "),
                StructuredPostal.TYPE to it.type, StructuredPostal.LABEL to it.label,
            )
        }
        if (listOf(d.company, d.department, d.jobTitle).any { it.isNotBlank() }) {
            row(Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to t(d.company), Organization.DEPARTMENT to t(d.department),
                Organization.TITLE to t(d.jobTitle), Organization.TYPE to Organization.TYPE_WORK)
        }
        d.websites.filter { it.isNotBlank() }.forEach { row(Website.CONTENT_ITEM_TYPE, Website.URL to it.trim(), Website.TYPE to Website.TYPE_HOMEPAGE) }
        d.events.forEach { row(Event.CONTENT_ITEM_TYPE, Event.START_DATE to it.date.store(), Event.TYPE to it.type, Event.LABEL to it.label) }
        d.relations.filter { it.value.isNotBlank() }.forEach { row(Relation.CONTENT_ITEM_TYPE, Relation.NAME to it.value.trim(), Relation.TYPE to it.type, Relation.LABEL to it.label) }
        if (includeGroups) d.groups.forEach { row(GroupMembership.CONTENT_ITEM_TYPE, GroupMembership.GROUP_ROW_ID to it) }
        if (d.note.isNotBlank()) row(Note.CONTENT_ITEM_TYPE, Note.NOTE to d.note.trim())
        if (d.newPhoto != null && d.newPhoto.isNotEmpty()) row(Photo.CONTENT_ITEM_TYPE, Photo.PHOTO to d.newPhoto, Data.IS_SUPER_PRIMARY to 1)
        return ops
    }

    private fun contactIdOfRaw(rawId: Long): Long? {
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID}=?", arrayOf(rawId.toString()), null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    /** Prüft, ob ein Kontakt wirklich einen Rohkontakt im Ziel-Adressbuch hat. */
    fun isIn(contactId: Long, book: AddressBook): Boolean {
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString(), book.name, book.type), null)?.use { return it.count > 0 }
        return false
    }

    /** Neuer Kontakt im gewählten Adressbuch. Gibt die Kontakt-ID zurück. */
    fun create(d: ContactDraft): Long? {
        if (!canWrite()) return null
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, d.book?.type?.takeIf { it.isNotBlank() })
            .withValue(RawContacts.ACCOUNT_NAME, d.book?.name?.takeIf { it.isNotBlank() })
            .build()
        ops += dataOps(d, null, includeGroups = true)
        val res = cr.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawUri = res.firstOrNull()?.uri ?: return null
        return contactIdOfRaw(ContentUris.parseId(rawUri))
    }

    /** Ersetzt die bearbeitbaren Felder eines bestehenden Rohkontakts. */
    fun update(rawId: Long, d: ContactDraft) {
        if (!canWrite()) return
        val mimes = if (d.newPhoto != null) managed + Photo.CONTENT_ITEM_TYPE else managed
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (${mimes.joinToString { "?" }})", (listOf(rawId.toString()) + mimes).toTypedArray())
            .build()
        ops += dataOps(d, rawId, includeGroups = true)
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    /**
     * Kopiert einen Kontakt in ein Nextcloud-Adressbuch und entfernt danach die Kopien
     * auf dem Gerät bzw. in Google (Messenger-Einträge bleiben, die verwalten die Apps selbst).
     */
    fun moveTo(contactId: Long, target: AddressBook): Long? {
        if (!canWrite()) return null
        val det = detail(contactId) ?: return null
        if (!target.isNextcloud) return null
        if (det.allRaw.any { it.second.same(target) }) return contactId
        val photo = if (det.hasPhoto) photoBytes(contactId) else null
        val draft = det.draft.copy(book = target, groups = emptySet(), newPhoto = photo)
        val newId = create(draft) ?: return null
        // Quelle erst löschen, wenn die Kopie sicher im Nextcloud-Adressbuch liegt
        if (!isIn(newId, target)) return null
        det.allRaw.filter { it.second.kind == BookKind.DEVICE || it.second.kind == BookKind.OTHER }.forEach { (raw, _) ->
            runCatching { cr.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw), null, null) }
        }
        if (det.starred) setStarred(newId, true)
        return newId
    }

    fun delete(contactId: Long, lookupKey: String) {
        if (!canWrite()) return
        val uri = Contacts.getLookupUri(contactId, lookupKey) ?: ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        cr.delete(uri, null, null)
    }

    fun setStarred(contactId: Long, starred: Boolean) {
        if (!canWrite()) return
        cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }, null, null)
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
