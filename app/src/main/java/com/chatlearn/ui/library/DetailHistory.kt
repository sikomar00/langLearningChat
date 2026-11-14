package com.chatlearn.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Rating
import com.chatlearn.domain.contextLines
import com.chatlearn.domain.ratingLabel
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HISTORY_PREVIEW = 10
private val LOG_DATE = DateTimeFormatter.ofPattern("MM/dd")

/** 저장할 때 함께 둔 앞뒤 대화를 말풍선으로. 저장한 줄만 또렷하게, 나머지는 흐리게. */
@Composable
internal fun ContextBubbles(context: String) {
    SectionCard("대화") {
        contextLines(context).forEach { (line, saved) ->
            Surface(
                color = if (saved) MaterialTheme.statusColors.board else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (saved) MaterialTheme.statusColors.onBoard else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    line, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = if (saved) FontWeight.Medium else null,
                )
            }
        }
    }
}

/** 탑승 기록: 복습할 때마다 날짜·방식·내 답·평가. 최근 10개만 먼저 보인다. */
@Composable
internal fun ReviewHistory(entryId: Long, vm: LibraryViewModel) {
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
        (if (all) logs else logs.take(HISTORY_PREVIEW)).forEach { log ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(LOG_DATE.format(Instant.ofEpochMilli(log.reviewedAt).atZone(zone)), style = MaterialTheme.typography.bodyMedium.board())
                BoardLabel(exerciseShort(log.exercise), Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    log.userAnswer.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                RatingMark(log.rating)
            }
        }
        if (logs.size > HISTORY_PREVIEW) TextButton(onClick = { all = !all }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (all) "접기" else "기록 ${logs.size}개 모두 보기")
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

private fun exerciseShort(k: ExerciseKind) = when (k) {
    ExerciseKind.TRANSLATE -> "번역"
    ExerciseKind.CLOZE -> "빈칸"
    ExerciseKind.DICTATION -> "받아쓰기"
}
