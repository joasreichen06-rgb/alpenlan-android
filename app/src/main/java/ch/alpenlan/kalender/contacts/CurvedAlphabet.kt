package ch.alpenlan.kalender.contacts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.alpenlan.kalender.ui.A
import ch.alpenlan.kalender.ui.PlexMono
import kotlin.math.exp

/**
 * Buchstabenleiste am rechten Rand. Beim Ziehen biegen sich die Buchstaben um den Finger
 * nach links (wie beim Launcher), der aktuelle Buchstabe sitzt in einem Kreis und die Liste springt mit.
 * Nur der schmale Streifen ganz rechts nimmt Berührungen an; die Liste darunter bleibt bedienbar.
 */
@Composable
fun CurvedAlphabet(
    letters: List<String>,
    available: Set<String>,
    modifier: Modifier = Modifier,
    onLetter: (String) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val onLetterState by rememberUpdatedState(onLetter)
    var h by remember { mutableIntStateOf(1) }
    var touching by remember { mutableStateOf(false) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableStateOf<String?>(null) }
    val bendAmount by animateFloatAsState(if (touching) 1f else 0f, tween(if (touching) 140 else 260), label = "bend")
    val n = letters.size.coerceAtLeast(1)

    fun nearestAvailable(i: Int): String? {
        for (step in 0 until n) {
            listOf(i - step, i + step).forEach { j -> if (j in letters.indices && letters[j] in available) return letters[j] }
        }
        return null
    }

    fun pick(y: Float) {
        fingerY = y.coerceIn(0f, h.toFloat())
        val i = ((fingerY / h) * n).toInt().coerceIn(0, n - 1)
        val l = nearestAvailable(i) ?: return
        if (l != current) {
            current = l
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetterState(l)
        }
    }

    Box(modifier.width(150.dp).fillMaxHeight()) {
        val slot = h.toFloat() / n
        letters.forEachIndexed { i, l ->
            val cy = slot * (i + 0.5f)
            val d = (cy - fingerY) / slot
            val bend = exp(-(d * d) / (2f * 3.4f * 3.4f)) * bendAmount
            val isCur = l == current && bendAmount > 0.05f
            val has = l in available
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp - (84 * bend).dp, y = with(density) { cy.toDp() } - 13.dp)
                    .size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCur) Box(Modifier.size(36.dp * bendAmount.coerceAtLeast(0.6f)).clip(CircleShape).background(A.blue))
                Text(
                    l,
                    fontFamily = PlexMono,
                    fontWeight = if (isCur || bend > 0.5f) FontWeight.Medium else FontWeight.Normal,
                    fontSize = (10f + 7f * bend + if (isCur) 3f else 0f).sp,
                    color = when {
                        isCur -> A.blueInk
                        has -> if (bend > 0.15f) A.fg else A.blueLight
                        else -> A.dim
                    },
                )
            }
        }
        // Nur dieser Streifen reagiert auf Berührung
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(32.dp)
                .onSizeChanged { h = it.height.coerceAtLeast(1) }
                .semantics { contentDescription = "Buchstabenleiste: ziehen, um zu einem Buchstaben zu springen" }
                .pointerInput(letters, available) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touching = true
                        current = null
                        pick(down.position.y)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            pick(c.position.y)
                            c.consume()
                        }
                        touching = false
                    }
                },
        )
    }
}
