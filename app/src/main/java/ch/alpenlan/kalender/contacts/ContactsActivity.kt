package ch.alpenlan.kalender.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ch.alpenlan.kalender.ui.AlpenlanTheme

class ContactsActivity : ComponentActivity() {
    private val vm: ContactsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handle(intent)
        setContent { AlpenlanTheme { ContactsApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** Öffnet einen Kontakt, wenn eine andere App (z.B. Telefon) auf ihn verweist. */
    private fun handle(i: Intent?) {
        val data: Uri = i?.data ?: return
        if (i.action == Intent.ACTION_VIEW) {
            runCatching {
                val lookup = ContactsContract.Contacts.lookupContact(contentResolver, data)
                contentResolver.query(lookup ?: data, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { c ->
                    if (c.moveToFirst()) vm.open(c.getLong(0))
                }
            }
        }
    }
}
