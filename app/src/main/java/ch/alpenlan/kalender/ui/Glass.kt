package ch.alpenlan.kalender.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Leuchtfarben je Bereich (wie auf der Website). */
object Glow {
    val blue = Color(0xFF2F86E6) to Color(0xFF1D5F7C)
    val teal = Color(0xFF2FA7BB) to Color(0xFF1D6F7C)
    val indigo = Color(0xFF5A62E0) to Color(0xFF2F86E6)
    val amber = Color(0xFFE8A23A) to Color(0xFFC9434A)
}

private class Star(val x: Float, val y: Float, val r: Float, val a: Float)

/** Kann das Gerät echt verwischen? (Android 12+). Sonst: dunklere, dichtere Flächen. */
val canBlur = Build.VERSION.SDK_INT >= 31

/**
 * Nachthimmel mit Sternen, Mond und Bergkette (blaue Linie) plus zwei weiche Farbkreise.
 * [blurred] = Unterseite: Himmel stark verwischt und abgedunkelt.
 */
@Composable
fun NightSky(glow: Pair<Color, Color> = Glow.blue, blurred: Boolean = true, modifier: Modifier = Modifier) {
    val g1 by animateColorAsState(glow.first, tween(800), label = "glow1")
    val g2 by animateColorAsState(glow.second, tween(800), label = "glow2")
    val dim by animateFloatAsState(if (blurred) 1f else 0f, tween(450), label = "dim")
    val stars = remember {
        val r = Random(7)
        List(130) { Star(r.nextFloat(), r.nextFloat() * 0.7f, if (r.nextFloat() < 0.12f) 1.6f else 0.6f + r.nextFloat() * 0.6f, 0.25f + r.nextFloat() * 0.6f) }
    }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(A.groundTop, A.groundBottom)))) {
        Canvas(Modifier.fillMaxSize().then(if (blurred && canBlur) Modifier.blur(26.dp) else Modifier)) {
            drawGlow(g1, Offset(-0.1f * size.width, -0.12f * size.height), size.width * 1.0f, 0.5f)
            drawGlow(g2, Offset(1.1f * size.width, 1.05f * size.height), size.width * 0.95f, 0.45f)
            stars.forEach { s -> drawCircle(Color.White.copy(alpha = s.a), s.r * density, Offset(s.x * size.width, s.y * size.height)) }
            drawMoon(Offset(size.width * 0.8f, size.height * 0.11f), 18.dp.toPx())
            drawRidge(
                floatArrayOf(0f, .78f, .12f, .71f, .22f, .75f, .34f, .66f, .46f, .72f, .58f, .64f, .7f, .71f, .82f, .65f, .93f, .7f, 1f, .67f),
                Color(0xFF0A1D38).copy(alpha = 0.9f), 0.55f,
            )
            drawRidge(
                floatArrayOf(0f, .86f, .1f, .81f, .2f, .84f, .31f, .77f, .43f, .83f, .54f, .79f, .66f, .85f, .77f, .78f, .88f, .83f, 1f, .8f),
                Color(0xFF071426), 0.85f,
            )
        }
        // Unterseiten: abgedunkelt (ohne echtes Verwischen etwas stärker)
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color(0xFF060A14).copy(alpha = dim * if (canBlur) 0.38f else 0.62f)))
    }
}

private fun DrawScope.drawGlow(c: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.35f), Color.Transparent), center, radius), radius, center)
}

private fun DrawScope.drawMoon(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0x38E8EEF8), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.5f, c.y - r * 0.2f), r)) }
    drawPath(Path.combine(PathOperation.Difference, full, cut), Color(0xEBE8EEF8))
}

private fun DrawScope.drawRidge(pts: FloatArray, fill: Color, lineAlpha: Float) {
    val line = Path()
    for (i in pts.indices step 2) {
        val x = pts[i] * size.width; val y = pts[i + 1] * size.height
        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
    drawPath(area, fill)
    drawPath(line, Color(0xFF4DA3FF).copy(alpha = lineAlpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Glasfläche: halbdurchsichtig mit feiner heller Kante. Compose kann den Hintergrund dahinter
 * nicht live verwischen – darum liegt eine etwas dichtere dunkle Schicht darunter, damit Text gut lesbar bleibt.
 * [tint] färbt das Glas (z. B. Blau für «an»), [rim] = heller Rand wie beim Fokus auf dem Apple TV.
 */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), tint: Color? = null, rim: Boolean = false): Modifier =
    this.clip(shape)
        .background(A.glassBack)
        .background(
            if (tint != null) Brush.linearGradient(listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.06f)))
            else Brush.linearGradient(listOf(Color(0x17EEF2F7), Color(0x0AEEF2F7))),
        )
        .border(if (rim) 2.dp else 1.dp, if (rim) Color(0xE6EEF2F7) else A.glassLine, shape)
