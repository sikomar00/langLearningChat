package com.chatlearn.stats

import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.data.ReviewLogEntity
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 복습한 횟수와 그중 "기억함"(다시가 아닌 평가)의 수. */
data class Retention(val reviews: Int, val remembered: Int) {
    val rate: Double? get() = if (reviews == 0) null else remembered.toDouble() / reviews
}

data class DayCount(val date: LocalDate, val reviews: Int)
data class WeakItem(val entryId: Long, val text: String, val lapses: Int)

data class Stats(
    val last7: List<DayCount>,
    val streak: Int,
    val growth: Growth,
    val weakest: List<WeakItem>,
    val byLang: Map<Lang, Retention>,
    val byTag: Map<String, Retention>,
    val byExercise: Map<ExerciseKind, Retention>,
    val avgResponseMs: Long?,
    /** 최근 [StatsCalculator.HEATMAP_WEEKS]주(월요일부터 오늘까지) 날마다 복습한 횟수. */
    val heatmap: List<DayCount> = emptyList(),
)

data class SentenceStats(
    val reviews: Int,
    val remembered: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    val dueAt: Long?,
)

object StatsCalculator {
    const val HEATMAP_WEEKS = 12
    private const val WEAK_LIMIT = 5

    private fun remembered(l: ReviewLogEntity) = l.rating != Rating.AGAIN

    private fun retention(logs: List<ReviewLogEntity>) = Retention(logs.size, logs.count(::remembered))

    fun compute(
        logs: List<ReviewLogEntity>,
        cards: List<ReviewCardEntity>,
        items: List<LibraryItem>,
        now: Long,
        zone: ZoneId,
    ): Stats {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = day(now)
        val perDay = logs.groupBy { day(it.reviewedAt) }

        val last7 = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayCount(d, perDay[d]?.size ?: 0)
        }

        val heatStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(HEATMAP_WEEKS - 1L)
        val heatmap = generateSequence(heatStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
            .map { DayCount(it, perDay[it]?.size ?: 0) }.toList()

        val streak = streakOf(perDay.keys, today)

        val itemById = items.associateBy { it.entry.id }
        val weakest = cards.filter { it.lapses > 0 }
            .sortedWith(compareByDescending<ReviewCardEntity> { it.lapses }.thenBy { it.entryId })
            .mapNotNull { c ->
                itemById[c.entryId]?.let {
                    WeakItem(c.entryId, it.texts[c.answerLang] ?: it.texts[it.entry.chatLang].orEmpty(), c.lapses)
                }
            }
            .take(WEAK_LIMIT)

        val cardById = cards.associateBy { it.id }
        val byLang = logs.mapNotNull { l -> cardById[l.cardId]?.let { it.answerLang to l } }
            .groupBy({ it.first }, { it.second }).mapValues { retention(it.value) }

        val byTag = HashMap<String, MutableList<ReviewLogEntity>>()
        logs.forEach { l ->
            val tags = cardById[l.cardId]?.let { itemById[it.entryId]?.tags }.orEmpty()
            tags.forEach { byTag.getOrPut(it) { mutableListOf() }.add(l) }
        }

        return Stats(
            last7 = last7,
            streak = streak,
            growth = GrowthCalculator.compute(logs, cards, items, now, zone),
            weakest = weakest,
            byLang = byLang,
            byTag = byTag.mapValues { retention(it.value) },
            byExercise = logs.groupBy { it.exercise }.mapValues { retention(it.value) },
            avgResponseMs = median(logs.mapNotNull { it.responseMs }),
            heatmap = heatmap,
        )
    }

    /** 앞으로 [days]일 동안 날짜별로 복습할 카드 수. 이미 밀린 카드는 오늘에 넣는다. [dueTimes]는 한 번 이상 복습한 카드의 기한. */
    fun forecast(dueTimes: List<Long>, now: Long, zone: ZoneId, days: Int = 7): List<DayCount> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val perDay = dueTimes.groupingBy { maxOf(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today) }.eachCount()
        return (0 until days).map { i -> today.plusDays(i.toLong()).let { DayCount(it, perDay[it] ?: 0) } }
    }

    /** 연속 학습일. 위젯처럼 전체 통계가 필요 없는 곳은 복습 시각만으로 구한다. */
    fun streak(reviewTimes: Collection<Long>, now: Long, zone: ZoneId): Int {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return streakOf(reviewTimes.mapTo(HashSet(), ::day), day(now))
    }

    private fun streakOf(days: Set<java.time.LocalDate>, today: java.time.LocalDate): Int {
        // 오늘 아직 복습하지 않았어도 어제까지의 연속 기록은 유지한다.
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) { streak++; cursor = cursor.minusDays(1) }
        return streak
    }

    fun sentenceStats(entryId: Long, logs: List<ReviewLogEntity>, cards: List<ReviewCardEntity>): SentenceStats {
        val mine = cards.filter { it.entryId == entryId }
        val ids = mine.map { it.id }.toSet()
        val own = logs.filter { it.cardId in ids }
        return SentenceStats(
            reviews = own.size,
            remembered = own.count(::remembered),
            lapses = mine.sumOf { it.lapses },
            lastReviewedAt = mine.mapNotNull { it.lastReviewedAt }.maxOrNull(),
            dueAt = mine.minOfOrNull { it.dueAt },
        )
    }
}

/** 기록 칸의 진하기(0~4): 안 함, 1~4번, 5~9번, 10~19번, 20번 이상. */
fun heatLevel(reviews: Int): Int = when {
    reviews <= 0 -> 0
    reviews < 5 -> 1
    reviews < 10 -> 2
    reviews < 20 -> 3
    else -> 4
}

/** 중앙값. 문제를 열어 둔 채 자리를 비운 긴 시간 같은 이상치에 평균처럼 끌려가지 않는다. */
internal fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
