package com.chatlearn.ui.library

import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.intervalLabel

enum class EntryState { NEW, DUE, LATER }

/** 문장 하나의 복습 상태. 영어·스페인어 카드가 둘 다 있으면 둘을 합쳐서 본다. */
data class EntryProgress(val state: EntryState, val nextDueAt: Long?, val lapses: Int)

enum class LibrarySort(val label: String) { RECENT("최근 저장"), DUE("복습 기한"), LAPSES("많이 틀린 순") }
enum class StatusFilter(val label: String) { ALL("전체"), UNREVIEWED("복습 안 한 것"), STRUGGLING("자주 틀린 것") }

const val STRUGGLING_LAPSES = 2

fun progressOf(cards: List<ReviewCardEntity>, now: Long): EntryProgress {
    val nextDue = cards.minOfOrNull { it.dueAt }
    val lapses = cards.sumOf { it.lapses }
    val state = when {
        cards.none { it.lastReviewedAt != null } -> EntryState.NEW
        cards.any { it.dueAt <= now } -> EntryState.DUE // 아직 안 한 짝 카드가 기한이어도 "복습할 차례"
        else -> EntryState.LATER
    }
    return EntryProgress(state, nextDue, lapses)
}

fun dueBadge(p: EntryProgress, now: Long): String = when (p.state) {
    EntryState.NEW -> "새 카드"
    EntryState.DUE -> "복습할 차례"
    EntryState.LATER -> "${intervalLabel((p.nextDueAt ?: now) - now)} 후"
}

private val NEW_PROGRESS = EntryProgress(EntryState.NEW, null, 0)

fun sortLibrary(items: List<LibraryItem>, progress: Map<Long, EntryProgress>, sort: LibrarySort): List<LibraryItem> {
    val recent = compareByDescending<LibraryItem> { it.entry.createdAt }.thenByDescending { it.entry.id }
    return when (sort) {
        LibrarySort.RECENT -> items.sortedWith(recent)
        LibrarySort.DUE -> items.sortedWith(
            compareBy<LibraryItem> { progress[it.entry.id]?.nextDueAt ?: Long.MAX_VALUE }.then(recent),
        )
        LibrarySort.LAPSES -> items.sortedWith(compareByDescending<LibraryItem> { progress[it.entry.id]?.lapses ?: 0 }.then(recent))
    }
}

fun filterByStatus(items: List<LibraryItem>, progress: Map<Long, EntryProgress>, f: StatusFilter): List<LibraryItem> =
    when (f) {
        StatusFilter.ALL -> items
        StatusFilter.UNREVIEWED -> items.filter { (progress[it.entry.id] ?: NEW_PROGRESS).state == EntryState.NEW }
        StatusFilter.STRUGGLING -> items.filter { (progress[it.entry.id]?.lapses ?: 0) >= STRUGGLING_LAPSES }
    }

/** 목록을 "복습할 차례 → 새 카드 → 나중에" 순서로 묶는다. 묶음 안의 순서는 주어진 순서를 그대로 둔다. */
fun groupByState(items: List<LibraryItem>, progress: Map<Long, EntryProgress>): List<Pair<EntryState, List<LibraryItem>>> {
    val byState = items.groupBy { (progress[it.entry.id] ?: NEW_PROGRESS).state }
    return listOf(EntryState.DUE, EntryState.NEW, EntryState.LATER).mapNotNull { st -> byState[st]?.let { st to it } }
}

fun stateTitle(s: EntryState): String = when (s) {
    EntryState.DUE -> "복습할 차례"
    EntryState.NEW -> "새 카드"
    EntryState.LATER -> "나중에 복습"
}
