package ch.alpenlan.kalender

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ch.alpenlan.kalender.data.CalView
import ch.alpenlan.kalender.data.Dates
import ch.alpenlan.kalender.reminders.ReminderReceiver
import ch.alpenlan.kalender.ui.AlpenlanTheme
import ch.alpenlan.kalender.ui.App
import ch.alpenlan.kalender.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        ReminderReceiver.ensureChannel(this)
        handleIntent(intent)
        setContent { AlpenlanTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val begin = i.getLongExtra(EXTRA_BEGIN, -1L).takeIf { it > 0 }
            ?: i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L).takeIf { it > 0 }
        if (begin != null) vm.select(Dates.localDate(begin), mode = CalView.DAY)
    }

    companion object {
        const val EXTRA_EVENT_ID = "ch.alpenlan.kalender.EVENT_ID"
        const val EXTRA_BEGIN = "ch.alpenlan.kalender.BEGIN"
    }
}
