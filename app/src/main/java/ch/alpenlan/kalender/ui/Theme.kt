package ch.alpenlan.kalender.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.R

object A {
    val ground = Color(0xFF070B12)
    val groundTop = Color(0xFF060A14)
    val groundBottom = Color(0xFF0B2240)
    // Glas-Stil: Flächen halbdurchsichtig und leicht bläulich, Kanten fein und hell
    val panel = Color(0xD90C1628)       // Karten, Blätter, Dialoge (dichter, da Compose nicht live verwischt)
    val panel2 = Color(0xFF14223A)      // Eingabefelder, Menüs (deckend)
    val line = Color(0x24EEF2F7)
    val line2 = Color(0x33EEF2F7)
    val lineSoft = Color(0x1AEEF2F7)
    val fg = Color(0xFFEEF2F7)
    val fg2 = Color(0xFFC3CFDF)
    val muted = Color(0xFF9FB0C6)
    val dim = Color(0xFF7F90A8)
    val blue = Color(0xFF4DA3FF)
    val blueLight = Color(0xFF8CC4FF)
    val blueInk = Color(0xFF06101E)
    val ok = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB547)
    val red = Color(0xFFFF7B63)
    val redSoft = Color(0xFFFF9A86)
    val now = Color(0xFFFF7B63)
    // Glas-Stil
    val btn = Color(0xFF2F86E6)
    val glass = Color(0x0FEEF2F7)       // rgba(238,242,247,.06)
    val glassLine = Color(0x1FEEF2F7)   // rgba(238,242,247,.12)
    val ghost = Color(0x14EEF2F7)       // rgba(238,242,247,.08)
    val ghostOn = Color(0x2EEEF2F7)     // rgba(238,242,247,.18)
    // Ersatz für das fehlende Verwischen: etwas dunkler und dichter, damit Text lesbar bleibt
    val glassBack = Color(0x990C1628)
}

val Syne = FontFamily(Font(R.font.syne_extrabold, FontWeight.ExtraBold))
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val PlexMono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
)

val MonoLabel = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.4.sp, color = A.muted)

private val base = Typography()
private val typography = Typography(
    // Zeit-/Datumsauswahl nutzt diese Stile – Syne ist dafür zu breit (Ziffern wurden abgeschnitten).
    displayLarge = base.displayLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    displayMedium = base.displayMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Inter),
    labelSmall = base.labelSmall.copy(fontFamily = PlexMono),
)

private val scheme = darkColorScheme(
    primary = A.btn,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16304F),
    onPrimaryContainer = A.fg,
    secondary = A.blueLight,
    onSecondary = A.blueInk,
    background = A.ground,
    onBackground = A.fg,
    surface = Color(0xFF0D1830),
    onSurface = A.fg,
    surfaceVariant = A.panel2,
    onSurfaceVariant = A.muted,
    surfaceContainer = Color(0xFF0D1830),
    surfaceContainerHigh = A.panel2,
    surfaceContainerHighest = Color(0xFF16233A),
    surfaceContainerLow = Color(0xFF0D1830),
    surfaceContainerLowest = A.ground,
    outline = A.line2,
    outlineVariant = A.line,
    error = A.redSoft,
    onError = A.blueInk,
)

@Composable
fun AlpenlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** Kalenderfarbe so abdunkeln, dass weisse Schrift lesbar bleibt. */
fun chipColor(argb: Int): Color {
    val c = Color(argb)
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    val f = if (lum > 0.45f) 0.45f / lum else 1f
    return Color(c.red * f, c.green * f, c.blue * f, 1f)
}

/** Helle Variante einer Kalenderfarbe für Punkte/Ränder auf dunklem Grund. */
fun dotColor(argb: Int): Color {
    val c = Color(argb)
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    if (lum >= 0.35f) return Color(c.red, c.green, c.blue, 1f)
    val t = (0.35f - lum) / 0.35f * 0.6f
    return Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, 1f)
}
