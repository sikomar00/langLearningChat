package com.chatlearn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chatlearn.ui.theme.statusColors
import kotlinx.coroutines.delay

/**
 * 오늘 목표를 채운 순간 화면 가운데 출발 안내판 한 칸이 플랩처럼 넘어오며 "오늘 목표 완료"를 알린다(약 1.6초).
 * 시스템에서 애니메이션을 끄면 넘어오지 않고 잠깐 보였다가 사라진다.
 */
@Composable
fun GoalBoard(modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val animate = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val flip = remember { Animatable(if (animate) 0f else 1f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (animate) flip.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        delay(1100)
        if (animate) fade.animateTo(0f, tween(240))
        onFinished()
    }
    val c = MaterialTheme.statusColors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer { scaleY = flip.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) }
                .alpha(fade.value).background(c.board, MaterialTheme.shapes.medium).padding(horizontal = 28.dp, vertical = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("오늘 목표 완료", style = MaterialTheme.typography.headlineSmall, color = c.flap)
        }
    }
}
