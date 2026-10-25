package ch.alpenlan.kalender.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Alpenlan-Zeichen: Mondbogen über einem Grat. */
@Composable
fun AlpenlanLogo(modifier: Modifier = Modifier.size(30.dp, 16.dp)) {
    Canvas(modifier) {
        val sx = size.width / 40f
        val sy = size.height / 22f
        val stroke = 1.8f * sx
        drawArc(
            color = A.fg, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(9f * sx, 5f * sy), size = Size(22f * sx, 22f * sy),
            style = Stroke(width = stroke),
        )
        val p = Path().apply {
            val pts = listOf(1f to 15f, 7f to 18f, 13f to 14f, 20f to 18f, 27f to 13f, 33f to 16f, 39f to 14f)
            moveTo(pts[0].first * sx, pts[0].second * sy)
            pts.drop(1).forEach { lineTo(it.first * sx, it.second * sy) }
        }
        drawPath(p, A.blue, style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = A.muted, size: Int = 11, spacing: Double = 1.4) {
    Text(
        text.uppercase(), modifier = modifier, color = color,
        style = MonoLabel.copy(fontSize = size.sp, letterSpacing = spacing.sp),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** Runder Abhak-Kreis wie in Nextcloud Tasks. */
@Composable
fun CheckCircle(done: Boolean, onClick: () -> Unit, size: Dp = 20.dp, touch: Dp = 44.dp, tint: Color = A.blue, label: String = "") {
    Box(
        Modifier
            .size(touch)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (done) Modifier.background(tint) else Modifier.border(2.dp, Color(0xFF5A6B88), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = null, tint = A.blueInk, modifier = Modifier.size(size * 0.7f))
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = A.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = A.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = A.fg, checkedTrackColor = A.blue,
                uncheckedThumbColor = A.fg, uncheckedTrackColor = A.line2, uncheckedBorderColor = A.line2,
            ),
        )
    }
}

/** Auswahl-Pille (z.B. Kalender, Liste, Dauer). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, dot: Color? = null, fill: Color? = null) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .height(36.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(fill ?: A.blue.copy(alpha = 0.16f)).border(1.dp, dot ?: A.blue, shape)
                else Modifier.border(1.dp, A.line2, shape)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Dot(dot)
        Text(text, color = if (selected) A.fg else A.fg2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun TaskTitle(text: String, done: Boolean, size: Int = 14, weight: FontWeight = FontWeight.SemiBold, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Text(
        text, modifier = modifier,
        color = if (done) A.dim else A.fg, fontSize = size.sp, fontWeight = weight,
        textDecoration = if (done) TextDecoration.LineThrough else null,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Wischen nach links/rechts zum Blättern. */
fun Modifier.swipe(onPrev: () -> Unit, onNext: () -> Unit): Modifier = this.pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total > 120f) onPrev() else if (total < -120f) onNext()
            total = 0f
        },
        onHorizontalDrag = { _, dx -> total += dx },
    )
}
