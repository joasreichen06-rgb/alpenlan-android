package ch.alpenlan.kalender.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ch.alpenlan.kalender.ui.AlpenlanTheme

class HomeActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handle(intent)
        setContent { AlpenlanTheme { HomeApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.setActive(true)
    }

    override fun onStop() {
        vm.setActive(false)
        super.onStop()
    }

    private fun handle(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "ch.alpenlan.app") {
            vm.handleRedirect(data)
            setIntent(Intent(this, HomeActivity::class.java))
        }
    }
}
