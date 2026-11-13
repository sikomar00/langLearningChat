package com.chatlearn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import com.chatlearn.ui.theme.statusColors
import kotlin.random.Random

private class Piece(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Int, val w: Float)

/** 한 번 흩날리는 색종이(1.6초). 시스템에서 애니메이션을 끄면 그리지 않고 바로 끝낸다. */
@Composable
fun Confetti(modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val animate = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.statusColors.sun, MaterialTheme.statusColors.success, MaterialTheme.colorScheme.error)
    val pieces = remember {
        val r = Random(7)
        List(70) { Piece(r.nextFloat(), r.nextFloat() * 0.35f, 0.8f + r.nextFloat() * 0.6f, r.nextFloat() - 0.5f, r.nextFloat() * 720f, r.nextInt(4), 6f + r.nextFloat() * 8f) }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animate) t.animateTo(1f, tween(1600, easing = LinearEasing))
        onFinished()
    }
    if (!animate) return
    Canvas(modifier.fillMaxSize()) {
        pieces.forEach { p ->
            val life = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (life <= 0f || life >= 1f) return@forEach
            val x = size.width * (p.x + p.drift * life * 0.3f)
            val y = -20f + (size.height + 40f) * life * p.speed
            rotate(p.spin * life, Offset(x, y)) {
                drawRect(colors[p.color].copy(alpha = 1f - life * 0.6f), topLeft = Offset(x, y), size = Size(p.w, p.w * 1.6f))
            }
        }
    }
}
