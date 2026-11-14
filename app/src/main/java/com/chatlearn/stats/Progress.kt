package com.chatlearn.stats

import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.MAX_STAGE
import com.chatlearn.domain.stageOf

/** KNOWN은 "이미 알아요"로 표시해 복습 차례에서 뺀 문장. */
/** "이미 알아요"로 표시한 문장 id. */
fun List<LibraryItem>.knownIds(): Set<Long> = filter { it.entry.known }.mapTo(HashSet()) { it.entry.id }

enum class EntryState { NEW, DUE, LATER, KNOWN }

/**
 * 문장 하나의 복습 상태. 영어·스페인어 카드가 둘 다 있으면 둘을 합쳐서 본다.
 * [stage]는 익힌 단계 0~[MAX_STAGE]: 가장 오래 기억한 카드의 간격으로 정한다(0 새 문장, 5 익힘 = 21일 이상).
 */
data class EntryProgress(val state: EntryState, val nextDueAt: Long?, val lapses: Int, val stage: Int = 0, val recentMiss: Boolean = false, val excluded: Boolean = false)

fun progressOf(cards: List<ReviewCardEntity>, now: Long): EntryProgress {
    val nextDue = cards.minOfOrNull { it.dueAt }
    val lapses = cards.sumOf { it.lapses }
    val state = when {
        cards.none { it.lastReviewedAt != null } -> EntryState.NEW
        cards.any { it.dueAt <= now } -> EntryState.DUE // 아직 안 한 짝 카드가 기한이어도 "복습할 차례"
        else -> EntryState.LATER
    }
    val stage = cards.filter { it.lastReviewedAt != null }.maxOfOrNull { stageOf(it.intervalDays) } ?: 0
    return EntryProgress(state, nextDue, lapses, stage)
}
