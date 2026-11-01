package com.chatlearn.stats

import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.data.ReviewLogEntity
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 복습한 횟수와 그중 "기억함"(다시가 아닌 평가)의 수. */
data class Retention(val reviews: Int, val remembered: Int) {
    val rate: Double? get() = if (reviews == 0) null else remembered.toDouble() / reviews
}

data class DayCount(val date: LocalDate, val reviews: Int)
data class WeakItem(val entryId: Long, val text: String, val lapses: Int)
data class CardMix(val new: Int, val learning: Int, val mature: Int)

data class Stats(
    val today: Retention,
    val last7: List<DayCount>,
    val streak: Int,
    val cards: CardMix,
    val weakest: List<WeakItem>,
    val byLang: Map<Lang, Retention>,
    val byTag: Map<String, Retention>,
    val byExercise: Map<ExerciseKind, Retention>,
    val avgResponseMs: Long?,
)

data class SentenceStats(
    val reviews: Int,
    val remembered: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    val dueAt: Long?,
)

object StatsCalculator {
    /** 간격이 이 일수 이상이면 "익숙함"으로 본다. */
    const val MATURE_DAYS = 21.0
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

        // 오늘 아직 복습하지 않았어도 어제까지의 연속 기록은 유지한다.
        var cursor = if (today in perDay) today else today.minusDays(1)
        var streak = 0
        while (cursor in perDay) { streak++; cursor = cursor.minusDays(1) }

        val mix = CardMix(
            new = cards.count { it.lastReviewedAt == null },
            learning = cards.count { it.lastReviewedAt != null && it.intervalDays < MATURE_DAYS },
            mature = cards.count { it.lastReviewedAt != null && it.intervalDays >= MATURE_DAYS },
        )

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
            today = retention(perDay[today].orEmpty()),
            last7 = last7,
            streak = streak,
            cards = mix,
            weakest = weakest,
            byLang = byLang,
            byTag = byTag.mapValues { retention(it.value) },
            byExercise = logs.groupBy { it.exercise }.mapValues { retention(it.value) },
            avgResponseMs = logs.mapNotNull { it.responseMs }.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
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
