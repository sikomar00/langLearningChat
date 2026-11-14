package com.chatlearn.ui.library

import androidx.compose.ui.semantics.stateDescription
import com.chatlearn.domain.stageName
import com.chatlearn.domain.MAX_STAGE
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.chatlearn.data.LibraryItem
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Rating
import com.chatlearn.domain.contextLines
import com.chatlearn.domain.ratingLabel
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.components.ChatBubble
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HISTORY_PREVIEW = 10
private val LOG_DATE = DateTimeFormatter.ofPattern("MM/dd")

// shortcut: "이름: 말" 꼴을 콜론으로 나눈다. 말 자체가 "Nota: ..."처럼 시작하면 이름으로 잘못 떼질 수 있다.
private val SPEAKER = Regex("""^([^:]{1,24}): (.+)$""")

/**
 * 저장할 때 함께 둔 앞뒤 대화를 받은 말 말풍선으로. 누가 나인지 알 수 없어 모두 왼쪽에 두고,
 * 말한 사람이 바뀔 때만 이름을 위에 작게. 저장한 줄은 굵은 테두리와 진한 글자, 나머지는 흐리게.
 */
@Composable
internal fun ContextBubbles(context: String) {
    SectionCard("대화") {
        var lastSpeaker: String? = null
        val lines = contextLines(context)
        // 이름을 떼는 건 대화 형식(저장한 줄 표시가 있는 맥락)일 때만.
        val chat = lines.any { it.second }
        lines.forEach { (line, saved) ->
            val m = if (chat) SPEAKER.matchEntire(line) else null
            val speaker = m?.groupValues?.get(1)
            if (speaker != null && speaker != lastSpeaker) {
                Text(speaker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lastSpeaker = speaker
            // 흰 구역 판 안이라 받은 말은 회색 말풍선으로, 저장한 줄만 흰 바탕 + 굵은 테두리로 띄운다.
            ChatBubble(
                outgoing = false,
                modifier = if (saved) Modifier.semantics { stateDescription = "저장한 문장" } else Modifier,
                color = if (saved) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surface,
                border = if (saved) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface) else null,
            ) {
                Text(
                    m?.groupValues?.get(2) ?: line, style = MaterialTheme.typography.bodyMedium, fontWeight = if (saved) FontWeight.Medium else null,
                    color = if (saved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 탑승 기록: 단계 변화 계단과, 복습할 때마다 날짜·방식·내 답·평가. 최근 10개만 먼저 보인다. */
@Composable
internal fun ReviewHistory(item: LibraryItem, vm: LibraryViewModel) {
    val entryId = item.entry.id
    val input by vm.statsInput.collectAsStateWithLifecycle()
    val logs = remember(entryId, input) {
        val si = input ?: return@remember emptyList()
        val cardIds = si.cards.filter { it.entryId == entryId }.map { it.id }.toSet()
        si.logs.filter { it.cardId in cardIds }.sortedByDescending { it.reviewedAt }
    }
    if (logs.isEmpty()) return
    var all by remember(entryId) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    SectionCard("탑승 기록") {
        if (logs.size >= STEPS_MIN) StageSteps(remember(logs) { stageSteps(logs) })
        (if (all) logs else logs.take(HISTORY_PREVIEW)).forEach { log ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(LOG_DATE.format(Instant.ofEpochMilli(log.reviewedAt).atZone(zone)), style = MaterialTheme.typography.bodyMedium.board())
                    BoardLabel(exerciseName(log.exercise), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RatingMark(log.rating)
                }
                if (log.userAnswer.isNotBlank()) Text(
                    log.userAnswer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (logs.size > HISTORY_PREVIEW) TextButton(onClick = { all = !all }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (all) "접기" else "기록 ${logs.size}개 모두 보기")
        }
    }
}

private const val STEPS_SHOWN = 10
private const val STEPS_MIN = 4

/** 복습마다의 익힌 단계를 5칸 불빛 기둥으로 시간순(왼쪽이 오래된 것)에 늘어놓아 계단처럼 보이게 한다. */
@Composable
private fun StageSteps(steps: List<Int>) {
    val shown = steps.takeLast(STEPS_SHOWN)
    val c = MaterialTheme.statusColors
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "복습할 때마다의 익힌 단계: " + shown.joinToString(", ") { stageName(it) } },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top,
    ) {
        BoardLabel(stageName(MAX_STAGE), color = MaterialTheme.colorScheme.onSurfaceVariant)
        shown.forEach { st ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                (MAX_STAGE downTo 1).forEach { lvl ->
                    val cell = Modifier.size(width = 14.dp, height = 5.dp)
                    Box(if (lvl <= st) cell.background(c.flapGraphic, MaterialTheme.shapes.extraSmall) else cell.border(1.dp, c.segmentOff, MaterialTheme.shapes.extraSmall))
                }
            }
        }
    }
}

@Composable
private fun RatingMark(r: Rating) {
    val color = when (r) {
        Rating.AGAIN -> MaterialTheme.colorScheme.error
        Rating.HARD -> MaterialTheme.colorScheme.outline
        Rating.GOOD, Rating.EASY -> MaterialTheme.statusColors.success
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // 색만으로 구분하지 않게 점 옆에 글자를 둔다. "겨우 기억"은 속이 빈 점.
        val dot = Modifier.size(8.dp)
        Box(if (r == Rating.HARD) dot.border(1.5.dp, color, CircleShape) else dot.background(color, CircleShape))
        Text(ratingLabel(r), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 성장의 정답률(방식)과 같은 이름. */
internal fun exerciseName(k: ExerciseKind) = when (k) {
    ExerciseKind.TRANSLATE -> "쓰기 번역"
    ExerciseKind.CLOZE -> "빈칸 채우기"
    ExerciseKind.DICTATION -> "듣고 받아쓰기"
}
