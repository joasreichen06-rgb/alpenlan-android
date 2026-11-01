package ch.alpenlan.kalender.light

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.ui.A
import ch.alpenlan.kalender.ui.AlpenlanLogo
import ch.alpenlan.kalender.ui.AlpenlanTheme
import ch.alpenlan.kalender.ui.Manrope
import ch.alpenlan.kalender.ui.Mono
import ch.alpenlan.kalender.ui.Pill
import ch.alpenlan.kalender.ui.SwitchRow
import ch.alpenlan.kalender.ui.Syne

/**
 * Alpenlan Light: das Handy als Lichtquelle im Publikum, gesteuert vom Lichtpult.
 * Diese Seite wählt Server, Zone und Taschenlampe; das Licht selbst zeigt [LightShowActivity].
 */
class LightActivity : ComponentActivity() {
    companion object {
        const val PREFS = "alpenlan-light"
        const val DEFAULT_URL = "https://light.alpenlan.com"
    }

    private lateinit var torch: Torch
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        torch = Torch(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContent {
            AlpenlanTheme {
                LightSetup(
                    initialUrl = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL,
                    initialZone = prefs.getInt("zone", 0),
                    initialTorch = prefs.getBoolean("torch", true),
                    torchAvailable = torch.available(),
                    onTorchTest = { testTorch() },
                    onStart = { test, url, zone, useTorch ->
                        prefs.edit().putString("url", url).putInt("zone", zone).putBoolean("torch", useTorch).apply()
                        start(test, url, zone, useTorch)
                    },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        torch.off()
    }

    private fun testTorch() {
        if (!torch.available()) {
            Toast.makeText(this, "Dieses Handy hat keine steuerbare Taschenlampe.", Toast.LENGTH_LONG).show()
            return
        }
        torch.set(true)
        main.postDelayed({ torch.off() }, 1200)
    }

    private fun start(test: Boolean, url: String, zone: Int, useTorch: Boolean) {
        if (!test && url.isBlank()) {
            Toast.makeText(this, "Bitte zuerst die Server-Adresse eingeben.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(
            Intent(this, LightShowActivity::class.java)
                .putExtra(LightShowActivity.EXTRA_TEST, test)
                .putExtra(LightShowActivity.EXTRA_URL, url.trim())
                .putExtra(LightShowActivity.EXTRA_ZONE, zone)
                .putExtra(LightShowActivity.EXTRA_TORCH, useTorch && torch.available()),
        )
    }
}

@Composable
private fun LightSetup(
    initialUrl: String,
    initialZone: Int,
    initialTorch: Boolean,
    torchAvailable: Boolean,
    onTorchTest: () -> Unit,
    onStart: (test: Boolean, url: String, zone: Int, torch: Boolean) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var zone by remember { mutableStateOf(initialZone) }
    var useTorch by remember { mutableStateOf(initialTorch && torchAvailable) }

    Column(
        Modifier
            .fillMaxSize()
            .background(A.ground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlpenlanLogo(Modifier.size(40.dp, 22.dp))
            Spacer(Modifier.width(10.dp))
            Mono("Alpenlan Light", color = A.blue)
        }
        Text(
            buildAnnotatedString {
                append("DU BIST\n")
                withStyle(SpanStyle(color = A.blue)) { append("DAS LICHT.") }
            },
            fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, color = A.fg,
        )
        Hint("Dein Handy leuchtet im Publikum mit – gesteuert vom Lichtpult. Bildschirm und Taschenlampe schalten auf allen Handys gleichzeitig.")

        Mono("Server")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://light.alpenlan.com", color = A.dim) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = A.blue,
                unfocusedBorderColor = A.line2,
                focusedTextColor = A.fg,
                unfocusedTextColor = A.fg,
                cursorColor = A.blue,
                focusedContainerColor = A.panel,
                unfocusedContainerColor = A.panel,
            ),
        )
        Hint("Im Heimnetz geht auch 192.168.1.212:8090.")

        Mono("Zone")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Zufall", zone == 0, { zone = 0 })
            for (z in 1..4) Pill("$z", zone == z, { zone = z })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (z in 5..8) Pill("$z", zone == z, { zone = z })
        }
        Hint(if (zone == 0) "Zufall: das Handy landet in einer zufälligen Gruppe – ideal, wenn sich alle frei bewegen." else "Feste Zone, z. B. für einen bestimmten Bereich im Saal.")

        SwitchRow(
            title = "Taschenlampe mitsteuern",
            checked = useTorch,
            onChange = { if (torchAvailable) useTorch = it },
            subtitle = if (torchAvailable) "Die Blitz-LED folgt dem Lichtpult (Kanal 6)." else "Auf diesem Handy nicht verfügbar.",
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onStart(false, url, zone, useTorch) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = A.blue, contentColor = A.blueInk),
        ) { Text("Live verbinden", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        SecondaryButton("Testmodus (ohne Server)") { onStart(true, url, zone, useTorch) }
        SecondaryButton("Taschenlampe kurz testen", onTorchTest)

        Hint("Testmodus: alle Effekte nacheinander, je 4 Sekunden. Tippen springt weiter, Zurück beendet.\nLive: Doppeltipp zeigt Verbindung und Verzögerung.")
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, A.line2),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = A.panel, contentColor = A.fg),
    ) { Text(text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = A.muted, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = Manrope)
}
