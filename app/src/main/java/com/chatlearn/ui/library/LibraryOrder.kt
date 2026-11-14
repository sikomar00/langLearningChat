package com.chatlearn.ui.library

import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.intervalLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class EntryState { NEW, DUE, LATER }

/**
 * 문장 하나의 복습 상태. 영어·스페인어 카드가 둘 다 있으면 둘을 합쳐서 본다.
 * [stage]는 익힌 단계 0~5: 가장 오래 기억한 카드의 간격으로 정한다(0 새 문장, 5 익힘 = 21일 이상).
 */
data class EntryProgress(val state: EntryState, val nextDueAt: Long?, val lapses: Int, val stage: Int = 0)

const val MAX_STAGE = 5

/** 복습 간격(일) → 익힌 단계 1~5. */
fun stageOf(intervalDays: Double): Int = when {
    intervalDays < 1 -> 1
    intervalDays < 3 -> 2
    intervalDays < 7 -> 3
    intervalDays < 21 -> 4
    else -> MAX_STAGE
}

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
    val stage = cards.filter { it.lastReviewedAt != null }.maxOfOrNull { stageOf(it.intervalDays) } ?: 0
    return EntryProgress(state, nextDue, lapses, stage)
}

fun dueBadge(p: EntryProgress, now: Long): String = when (p.state) {
    EntryState.NEW -> "새 문장"
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

/** 목록을 "복습할 차례 → 새 문장 → 나중에" 순서로 묶는다. 묶음 안의 순서는 주어진 순서를 그대로 둔다. */
fun groupByState(items: List<LibraryItem>, progress: Map<Long, EntryProgress>): List<Pair<EntryState, List<LibraryItem>>> {
    val byState = items.groupBy { (progress[it.entry.id] ?: NEW_PROGRESS).state }
    return listOf(EntryState.DUE, EntryState.NEW, EntryState.LATER).mapNotNull { st -> byState[st]?.let { st to it } }
}

fun stateTitle(s: EntryState): String = when (s) {
    EntryState.DUE -> "복습할 차례"
    // 문장 수를 세므로 "카드"가 아니라 "문장": 한 문장에 카드가 둘일 수 있어 복습 탭의 카드 수와 다르다.
    EntryState.NEW -> "새 문장"
    EntryState.LATER -> "나중에 복습"
}

enum class GroupBy(val label: String) { STATE("상태"), DATE("날짜"), TAG("태그"), NONE("묶지 않음") }

private val DATE_TITLE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

/** 저장한 날짜별로 묶는다. 묶음과 묶음 안의 순서는 주어진 순서(처음 나온 순)를 그대로 따른다. */
fun groupByDate(items: List<LibraryItem>, zone: ZoneId, today: LocalDate): List<Pair<String, List<LibraryItem>>> =
    items.groupBy { Instant.ofEpochMilli(it.entry.createdAt).atZone(zone).toLocalDate() }
        .map { (d, list) ->
            val title = when (d) {
                today -> "오늘"
                today.minusDays(1) -> "어제"
                else -> DATE_TITLE.format(d)
            }
            title to list
        }

const val NO_TAG_TITLE = "태그 없음"

/** 첫 번째 태그로 묶는다. 태그가 없는 문장은 마지막 묶음. */
fun groupByTag(items: List<LibraryItem>): List<Pair<String, List<LibraryItem>>> {
    val byTag = items.groupBy { it.tags.firstOrNull() ?: NO_TAG_TITLE }
    return byTag.entries.filter { it.key != NO_TAG_TITLE }.map { it.key to it.value } +
        listOfNotNull(byTag[NO_TAG_TITLE]?.let { NO_TAG_TITLE to it })
}
