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
    val groundBottom = Color(0xFF0A1526)
    val panel = Color(0xFF0D1420)
    val panel2 = Color(0xFF111B2B)
    val line = Color(0xFF1F2C40)
    val line2 = Color(0xFF26344A)
    val lineSoft = Color(0xFF1A2638)
    val fg = Color(0xFFEEF2F7)
    val fg2 = Color(0xFFCFD8E3)
    val muted = Color(0xFF9AA6B8)
    val dim = Color(0xFF7A879C)
    val blue = Color(0xFF4DA3FF)
    val blueLight = Color(0xFF8CC4FF)
    val blueInk = Color(0xFF06101E)
    val ok = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB547)
    val red = Color(0xFFFF7B63)
    val redSoft = Color(0xFFFF9A86)
    val now = Color(0xFFFF7B63)
}

val Syne = FontFamily(Font(R.font.syne_extrabold, FontWeight.ExtraBold))
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)
val PlexMono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
)

val MonoLabel = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.4.sp, color = A.muted)

private val base = Typography()
private val typography = Typography(
    // Zeit-/Datumsauswahl nutzt diese Stile – Syne ist dafür zu breit (Ziffern wurden abgeschnitten).
    displayLarge = base.displayLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    displayMedium = base.displayMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    headlineLarge = base.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = base.bodySmall.copy(fontFamily = Manrope),
    labelLarge = base.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Manrope),
    labelSmall = base.labelSmall.copy(fontFamily = PlexMono),
)

private val scheme = darkColorScheme(
    primary = A.blue,
    onPrimary = A.blueInk,
    primaryContainer = Color(0xFF16304F),
    onPrimaryContainer = A.fg,
    secondary = A.blueLight,
    onSecondary = A.blueInk,
    background = A.ground,
    onBackground = A.fg,
    surface = A.panel,
    onSurface = A.fg,
    surfaceVariant = A.panel2,
    onSurfaceVariant = A.muted,
    surfaceContainer = A.panel,
    surfaceContainerHigh = A.panel2,
    surfaceContainerHighest = Color(0xFF16233A),
    surfaceContainerLow = A.panel,
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
