package com.chatlearn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chatlearn.ui.theme.statusColors
import kotlinx.coroutines.delay

/**
 * 오늘 목표를 채운 순간 빨간 도장 "참 잘했어요"가 한 번 찍힌다(약 1.6초). 공책에 선생님이 찍어 주던 도장.
 * 시스템에서 애니메이션을 끄면 움직이지 않고 잠깐 보였다가 사라진다.
 */
@Composable
fun GoalStamp(modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val animate = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val press = remember { Animatable(if (animate) 1.6f else 1f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (animate) press.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        delay(1100)
        if (animate) fade.animateTo(0f, tween(300))
        onFinished()
    }
    val red = MaterialTheme.statusColors.accent
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.scale(press.value).alpha(fade.value).rotate(-12f)
                .border(3.dp, red, CircleShape).padding(28.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            Text("참 잘했어요", color = red, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
        }
    }
}
