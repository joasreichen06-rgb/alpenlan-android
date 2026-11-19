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
    private val notifPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handle(intent)
        setContent { AlpenlanTheme { HomeApp(vm) } }
        runCatching { ch.alpenlan.kalender.push.PushSetup.start(this) }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
