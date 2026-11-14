package com.chatlearn.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chatlearn.review.GoalProgress
import com.chatlearn.ui.components.ChatBubble
import com.chatlearn.ui.components.EmptyState

internal const val LEARN_MORE = 5

/** 요약 화면에 보여 줄 이번 세션의 숫자들. */
internal data class SessionSummary(
    val done: ReviewUi.Done, val remembered: Int, val streak: Int, val goal: GoalProgress, val custom: Boolean, val moreNew: Int,
)

/** 다 끝낸 뒤 요약: 앱이 보낸 말풍선 하나에 오늘의 결과를 담는다. */
@Composable
internal fun DoneBody(sum: SessionSummary, onLearnMore: () -> Unit, onClose: () -> Unit) {
    val (s, remembered, streak, goal, custom, moreNew) = sum
    ChatBubble(outgoing = false, fillWidth = true) {
        Text(if (goal.reached) "오늘 목표 달성!" else "수고했어요", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${s.cards}", style = MaterialTheme.typography.displayMedium)
            Text("장 복습", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (s.count > s.cards) Text("다시 푼 것까지 ${s.count}번 풀었어요", style = MaterialTheme.typography.bodyMedium)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryStat("기억함", if (s.count > 0) "${remembered * 100 / s.count}%" else "-", Modifier.weight(1f))
        // 큰 숫자에 한글 단위를 붙이면 기준선이 어긋나서, 단위는 아래 설명에 둔다.
        SummaryStat("일 연속", if (streak > 0) "$streak" else "-", Modifier.weight(1f))
        if (goal.enabled) SummaryStat("오늘 목표", "${goal.done}/${goal.goal}", Modifier.weight(1f))
    }
    if (!goal.reached && goal.enabled) Text("오늘 목표까지 ${goal.goal - goal.done}장 남았어요.", style = MaterialTheme.typography.bodyMedium)
    if (!custom) LearnMore(moreNew, onLearnMore)
    Button(shape = MaterialTheme.shapes.medium, onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("오늘 화면으로") }
}

@Composable
internal fun EmptyBody(custom: Boolean, moreNew: Int, vm: ReviewViewModel, onClose: () -> Unit) {
    EmptyState(
        message = emptyReviewMessage(custom, moreNew),
        icon = Icons.Default.CheckCircle,
        actionLabel = "오늘 화면으로", onAction = onClose,
    )
    if (!custom) LearnMore(moreNew) { vm.learnMore(LEARN_MORE) }
}

/** 오늘 새 카드 한도 밖에 기다리는 새 카드가 있으면 더 배우기를 권한다. */
@Composable
internal fun LearnMore(waiting: Int, onClick: () -> Unit) {
    if (waiting <= 0) return
    Button(shape = MaterialTheme.shapes.medium, onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("새 카드 ${minOf(LEARN_MORE, waiting)}장 더 배우기") }
    Text(
        "기다리는 새 카드 ${waiting}장. 하루 새 카드 수는 설정 > 복습에서 바꿀 수 있어요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
