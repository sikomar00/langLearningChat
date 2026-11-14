package com.chatlearn.stats

import com.chatlearn.domain.langLabel
import com.chatlearn.domain.stageName
import com.chatlearn.domain.MAX_STAGE
import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.data.ReviewLogEntity
import com.chatlearn.domain.Rating
import com.chatlearn.domain.TagKind
import java.time.Instant
import java.time.ZoneId

/** 성장 탭 맨 위: 지난주의 나와 견준 수, 익힌 단계별 문장 수, 기록에서 나온 사실 한 줄. */
data class Growth(
    val mastered: Int,
    val weekReviews: Int,
    val prevWeekReviews: Int,
    val week: Retention,
    /** 익힘 → 거의 → 익히는 중 → 새 문장 순서의 (이름, 문장 수). */
    val stages: List<Pair<String, Int>>,
    val monthDays: Int,
    val line: String,
    /** 친구별 문장 수와 그중 익힌 수(문장이 많은 친구부터). */
    val friends: List<FriendGrowth> = emptyList(),
)

data class FriendGrowth(val name: String, val sentences: Int, val mastered: Int)

/** 앞으로의 하루: 복습할 카드 수와, 하루 새 카드 한도대로 들어올 새 카드 수. */
data class OutlookDay(val date: java.time.LocalDate, val reviews: Int, val new: Int)

object GrowthCalculator {
    private val STAGE_ORDER = listOf(MAX_STAGE, 3, 1, 0).map(::stageName)

    fun compute(logs: List<ReviewLogEntity>, cards: List<ReviewCardEntity>, items: List<LibraryItem>, now: Long, zone: ZoneId): Growth {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val prevStart = today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()
        val cardsOf = cards.groupBy { it.entryId }
        val stageOf = items.associate { it.entry.id to (cardsOf[it.entry.id]?.let { c -> progressOf(c, now).stage } ?: 0) }
        val masteredItems = items.filter { stageOf[it.entry.id] == MAX_STAGE }

        val weekLogs = logs.filter { it.reviewedAt >= weekStart }
        val month = today.withDayOfMonth(1)
        val monthDays = logs.map { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }.filter { !it.isBefore(month) }.distinct().size
        val stageCounts = items.groupingBy { stageName(stageOf[it.entry.id] ?: 0) }.eachCount()
        return Growth(
            mastered = masteredItems.size,
            weekReviews = weekLogs.size,
            prevWeekReviews = logs.count { it.reviewedAt in prevStart until weekStart },
            week = Retention(weekLogs.size, weekLogs.count { it.rating != Rating.AGAIN }),
            stages = STAGE_ORDER.map { it to (stageCounts[it] ?: 0) },
            monthDays = monthDays,
            line = factLine(masteredItems),
            friends = friendsOf(items, stageOf),
        )
    }

    private const val FRIEND_LIMIT = 5

    internal fun friendsOf(items: List<LibraryItem>, stageOf: Map<Long, Int>): List<FriendGrowth> =
        items.flatMap { i -> i.tagsOf(TagKind.PERSON).distinct().map { it to i } }
            .groupBy({ it.first }, { it.second })
            .map { (name, list) -> FriendGrowth(name, list.size, list.count { stageOf[it.entry.id] == MAX_STAGE }) }
            .sortedWith(compareByDescending<FriendGrowth> { it.sentences }.thenBy { it.name })
            .take(FRIEND_LIMIT)

    /**
     * 앞으로 [days]일: 복습한 카드는 기한 날짜에(밀린 카드는 오늘), 아직 안 본 카드는 하루 [newPerDay]장씩 오늘부터.
     * shortcut: 오늘 이미 시작한 새 카드 수는 빼지 않는다. 오늘 칸이 조금 많게 보일 수 있다.
     */
    fun outlook(cards: List<ReviewCardEntity>, now: Long, zone: ZoneId, newPerDay: Int, days: Int = 30): List<OutlookDay> {
        val reviews = StatsCalculator.forecast(cards.filter { it.lastReviewedAt != null }.map { it.dueAt }, now, zone, days)
        var waiting = cards.count { it.lastReviewedAt == null }
        return reviews.map { d ->
            val n = minOf(waiting, newPerDay.coerceAtLeast(0))
            waiting -= n
            OutlookDay(d.date, d.reviews, n)
        }
    }

    /** 기록으로 뒷받침되는 사실만: 언어별로 익힌 문장 수와, 그중 가장 많이 나온 친구. */
    internal fun factLine(mastered: List<LibraryItem>): String {
        if (mastered.isEmpty()) return "아직 익힌 문장이 없어요."
        val byLang = mastered.groupingBy { it.entry.chatLang }.eachCount().entries.sortedByDescending { it.value }
        val head = byLang.joinToString(", ") { (lang, n) -> "${langLabel(lang)} 문장 ${n}개" } + "를 익혔어요."
        val friend = mastered.mapNotNull { it.tagsOf(TagKind.PERSON).firstOrNull() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return head
        // 라틴 문자 이름 뒤 조사(와/과)를 고를 수 없어 조사가 붙지 않는 꼴로 쓴다.
        return "$head 가장 많이 나온 친구는 ${friend.key}, ${friend.value}개예요."
    }
}
