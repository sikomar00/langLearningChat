package com.chatlearn.ui.library

import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.data.ReviewLogEntity
import com.chatlearn.domain.Rating
import com.chatlearn.domain.TagKind
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
data class EntryProgress(val state: EntryState, val nextDueAt: Long?, val lapses: Int, val stage: Int = 0, val recentMiss: Boolean = false)

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
/** 익힌 단계 0~5의 이름. 라이브러리 필터·문장 상세·성장이 같은 이름을 쓴다. */
fun stageName(stage: Int): String = when {
    stage <= 0 -> "새 문장"
    stage <= 2 -> "익히는 중"
    stage < MAX_STAGE -> "거의"
    else -> "익힘"
}

enum class StatusFilter(val label: String) {
    ALL("전체"), UNREVIEWED(stageName(0)), LEARNING(stageName(1)), ALMOST(stageName(3)), MASTERED(stageName(MAX_STAGE)),
    RECENT_MISS("최근 틀림"), DUE_SOON("곧 복습"), STRUGGLING("자주 틀린 것"),
}

const val STRUGGLING_LAPSES = 2
const val RECENT_MISS_MS = 7 * 86_400_000L
const val DUE_SOON_MS = 3 * 86_400_000L

/** [since] 뒤에 "다시"를 누른 적이 있는 문장 id. */
fun recentMissIds(logs: List<ReviewLogEntity>, cards: List<ReviewCardEntity>, since: Long): Set<Long> {
    val entryOf = cards.associate { it.id to it.entryId }
    return logs.filter { it.rating == Rating.AGAIN && it.reviewedAt >= since }.mapNotNull { entryOf[it.cardId] }.toSet()
}

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

fun matchesStatus(p: EntryProgress, f: StatusFilter, now: Long): Boolean = when (f) {
    StatusFilter.ALL -> true
    StatusFilter.UNREVIEWED -> p.state == EntryState.NEW
    StatusFilter.LEARNING -> p.state != EntryState.NEW && p.stage <= 2
    StatusFilter.ALMOST -> p.state != EntryState.NEW && p.stage in 3 until MAX_STAGE
    StatusFilter.MASTERED -> p.stage >= MAX_STAGE
    StatusFilter.RECENT_MISS -> p.recentMiss
    // 이미 "복습할 차례"인 문장은 빼고, 아직 기한 전이지만 3일 안에 돌아오는 문장.
    StatusFilter.DUE_SOON -> p.state == EntryState.LATER && (p.nextDueAt ?: Long.MAX_VALUE) <= now + DUE_SOON_MS
    StatusFilter.STRUGGLING -> p.lapses >= STRUGGLING_LAPSES
}

fun filterByStatus(items: List<LibraryItem>, progress: Map<Long, EntryProgress>, f: StatusFilter, now: Long = System.currentTimeMillis()): List<LibraryItem> =
    if (f == StatusFilter.ALL) items else items.filter { matchesStatus(progress[it.entry.id] ?: NEW_PROGRESS, f, now) }

/** 필터 칩에 붙일 수: 지금 검색·폴더 조건 안에서 상태별 문장 수. */
fun statusCounts(items: List<LibraryItem>, progress: Map<Long, EntryProgress>, now: Long): Map<StatusFilter, Int> =
    StatusFilter.entries.associateWith { f -> items.count { matchesStatus(progress[it.entry.id] ?: NEW_PROGRESS, f, now) } }

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

enum class GroupBy(val label: String) { STATE("상태"), PERSON("친구"), DATE("날짜"), TAG("태그"), NONE("묶지 않음") }

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

const val NO_PERSON_TITLE = "친구 없음"

/** 첫 번째 친구(사람 태그)로 묶는다. 제목은 "친구 · 가장 많이 붙은 나라". 친구가 없는 문장은 마지막 묶음. */
fun groupByPerson(items: List<LibraryItem>): List<Pair<String, List<LibraryItem>>> {
    fun LibraryItem.ofKind(kind: TagKind) = tags.filter { tagKinds[it] == kind }
    val byPerson = items.groupBy { it.ofKind(TagKind.PERSON).firstOrNull() }
    val titled = byPerson.entries.mapNotNull { (person, list) ->
        person ?: return@mapNotNull null
        val country = list.flatMap { it.ofKind(TagKind.COUNTRY) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        listOfNotNull(person, country).joinToString(" · ") to list
    }
    return titled + listOfNotNull(byPerson[null]?.let { NO_PERSON_TITLE to it })
}

/**
 * 복습할 때마다의 익힌 단계(오래된 것부터). "다시"면 1, 아니면 같은 카드의 바로 앞 복습(처음이면 저장한 때)부터 지난 날로 [stageOf].
 * shortcut: 기록에 복습 뒤 간격이 없어 "그때까지 기억한 기간"으로 어림한다. 줄의 단계(예정 간격)와 조금 다를 수 있다.
 */
fun stageSteps(logs: List<ReviewLogEntity>, createdAt: Long): List<Int> {
    val prevOf = HashMap<Long, Long>()
    return logs.sortedBy { it.reviewedAt }.map { l ->
        val since = prevOf[l.cardId] ?: createdAt
        prevOf[l.cardId] = l.reviewedAt
        if (l.rating == Rating.AGAIN) 1 else stageOf((l.reviewedAt - since) / 86_400_000.0)
    }
}
