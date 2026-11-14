package com.chatlearn.ui.components

import com.chatlearn.ui.theme.board
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.chatlearn.stats.DayCount
import com.chatlearn.ui.theme.statusColors
import java.time.LocalDate

/** 날짜별 복습 수 막대(지난 7일, 앞으로 7일). [today] 칸만 진하게. 스크린리더에는 한 문장으로 읽힌다. */
@Composable
fun DayBars(days: List<DayCount>, label: (DayCount) -> String, today: LocalDate, modifier: Modifier = Modifier) {
    val max = (days.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
    // 큰 글씨 설정에서도 날짜가 잘리지 않게 높이는 최소값만 정한다.
    Row(
        modifier.fillMaxWidth().heightIn(min = 96.dp).clearAndSetSemantics { contentDescription = days.joinToString { "${label(it)} ${it.reviews}장" } },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (d.reviews > 0) Text("${d.reviews}", style = MaterialTheme.typography.labelMedium.board())
                // 막대는 그림이라 배경 대비 3:1 이상인 outline, 오늘만 플랩 호박.
                val color = if (d.date == today) MaterialTheme.statusColors.flapGraphic else MaterialTheme.colorScheme.outline
                Box(
                    Modifier.fillMaxWidth().height((4 + 52f * d.reviews / max).dp)
                        .background(color, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                )
                Text(label(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 연속 학습일: 장식 배지 대신 작은 글자. [label]로 다른 연속 기록(예: "3연속 기억")에도 쓴다. */
@Composable
fun StreakBadge(days: Int, modifier: Modifier = Modifier, label: String = "${days}일 연속") {
    Text(label, modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
