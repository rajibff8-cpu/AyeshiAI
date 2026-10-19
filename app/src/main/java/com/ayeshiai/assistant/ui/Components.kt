package com.ayeshiai.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AyChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) c.primary.copy(alpha = 0.28f) else c.surfaceVariant)
            .border(1.dp, if (selected) c.primary else c.primary.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = c.onSurface, fontSize = 13.sp)
    }
}

@Composable
fun ToastBanner(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(3500)
        onDismiss()
    }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(message, Modifier.padding(12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AiOrb(status: Status, accent: Color, modifier: Modifier = Modifier, orbSize: Dp = 100.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val periodMs = when (status) {
        Status.LISTENING -> 700
        Status.THINKING -> 1000
        Status.SPEAKING -> 900
        else -> 2400
    }
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (status == Status.THINKING) 1800 else 6000, easing = LinearEasing)
        ),
        label = "angle"
    )
    val core = when (status) {
        Status.LISTENING -> Color(0xFFFF4081)
        Status.THINKING -> Color(0xFF7C4DFF)
        Status.SPEAKING -> Color(0xFF40C4FF)
        Status.SETUP -> Color(0xFF90A4AE)
        Status.ONLINE -> accent
    }
    Canvas(modifier.size(orbSize)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(core.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r),
            radius = r,
            center = c
        )
        val coreR = r * 0.5f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.9f), core, core.copy(alpha = 0.25f)),
                center = c,
                radius = coreR
            ),
            radius = coreR,
            center = c
        )
        rotate(angle, pivot = c) {
            drawArc(
                color = core,
                startAngle = 0f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f),
                size = Size(r * 1.6f, r * 1.6f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }
        rotate(-angle * 0.7f, pivot = c) {
            drawArc(
                color = Color(0xFF7C4DFF),
                startAngle = 180f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.68f, c.y - r * 0.68f),
                size = Size(r * 1.36f, r * 1.36f),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val glow = if (listening) Color(0xFFFF4081) else MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "mic")
    val a by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "micGlow"
    )
    Box(
        Modifier
            .size(72.dp)
            .shadow(16.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(glow.copy(alpha = a), glow.copy(alpha = 0.25f))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(if (listening) "⏹" else "🎤", fontSize = 28.sp)
    }
}
