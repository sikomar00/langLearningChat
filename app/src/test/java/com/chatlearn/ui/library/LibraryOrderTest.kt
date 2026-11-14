package com.chatlearn.ui.library

import com.chatlearn.stats.progressOf
import com.chatlearn.stats.EntryProgress
import com.chatlearn.stats.EntryState
import com.chatlearn.domain.stageName
import com.chatlearn.domain.stageOf
import com.chatlearn.data.EntryEntity
import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryOrderTest {
    private val now = 1_000_000_000L
    private val day = 86_400_000L

    private fun card(entry: Long, due: Long, reviewed: Long? = 1, lapses: Int = 0, lang: Lang = Lang.EN) =
        ReviewCardEntity(entryId = entry, answerLang = lang, dueAt = due, intervalDays = 1.0, ease = 2.5, reps = 1, lapses = lapses, lastReviewedAt = reviewed)

    private fun item(id: Long, created: Long) =
        LibraryItem(EntryEntity(id, created, Direction.RECEIVED, Lang.EN, "", null), mapOf(Lang.EN to "t$id", Lang.KO to "k$id"), emptyList())

    @Test fun learnedStageFollowsTheLongestRememberedInterval() {
        assertEquals(listOf(1, 2, 3, 4, 5), listOf(0.5, 2.0, 5.0, 14.0, 30.0).map(::stageOf))
        val c = card(1, now + 9 * day).copy(intervalDays = 9.0)
        assertEquals(4, progressOf(listOf(card(1, now + day), c, card(1, now, reviewed = null, lang = Lang.ES).copy(intervalDays = 40.0)), now).stage)
        assertEquals(0, progressOf(listOf(card(1, now - 5, reviewed = null)), now).stage)
    }

    @Test fun unreviewedCardsAreNew() {
        val p = progressOf(listOf(card(1, now - 5, reviewed = null)), now)
        assertEquals(EntryState.NEW, p.state)
    }

    @Test fun reviewedCardPastDueIsDue() {
        assertEquals(EntryState.DUE, progressOf(listOf(card(1, now - 1)), now).state)
    }

    @Test fun reviewedCardInTheFutureIsLaterWithNextDue() {
        val p = progressOf(listOf(card(1, now + 3 * day), card(1, now + 5 * day, lang = Lang.ES)), now)
        assertEquals(EntryState.LATER, p.state)
        assertEquals(now + 3 * day, p.nextDueAt)
    }

    @Test fun anyDueCardMakesTheEntryDueAndLapsesAreSummed() {
        val p = progressOf(listOf(card(1, now + day, lapses = 1), card(1, now - 1, lapses = 2, lang = Lang.ES)), now)
        assertEquals(EntryState.DUE, p.state)
        assertEquals(3, p.lapses)
    }

    @Test fun anUnreviewedCardThatIsAlreadyDueMakesTheEntryDueNotLater() {
        val p = progressOf(listOf(card(1, now + 3 * day), card(1, now - 5, reviewed = null, lang = Lang.ES)), now)
        assertEquals(EntryState.DUE, p.state)
    }

    @Test fun laterAlwaysHasAFutureDate() {
        val p = progressOf(listOf(card(1, now + 3 * day), card(1, now + 9 * day, lang = Lang.ES)), now)
        assertEquals(EntryState.LATER, p.state)
        assertEquals(true, p.nextDueAt!! > now)
    }

    @Test fun noCardsMeansNewWithNoDate() {
        val p = progressOf(emptyList(), now)
        assertEquals(EntryState.NEW, p.state)
        assertNull(p.nextDueAt)
    }

    // 라이브러리는 문장을 센다. 한 문장에 영어·스페인어 카드가 둘 있을 수 있어 "새 카드"라고 하면 복습 탭의 카드 수와 어긋난다.
    @Test fun groupTitlesNameSentencesNotCards() {
        assertEquals(
            listOf("복습할 차례", "새 문장", "나중에 복습"),
            listOf(EntryState.DUE, EntryState.NEW, EntryState.LATER).map(::stateTitle),
        )
    }

    @Test fun badgeText() {
        assertEquals("새 문장", dueBadge(EntryProgress(EntryState.NEW, now, 0), now))
        assertEquals("복습할 차례", dueBadge(EntryProgress(EntryState.DUE, now - 1, 0), now))
        assertEquals("3일 후", dueBadge(EntryProgress(EntryState.LATER, now + 3 * day, 0), now))
    }

    private val a = item(1, 100)
    private val b = item(2, 300)
    private val c = item(3, 200)
    private val progress = mapOf(
        1L to EntryProgress(EntryState.LATER, now + 5 * day, 0),
        2L to EntryProgress(EntryState.DUE, now - day, 3),
        3L to EntryProgress(EntryState.NEW, null, 0),
    )

    @Test fun recentSortPutsNewestFirst() =
        assertEquals(listOf(b, c, a), sortLibrary(listOf(a, b, c), progress, LibrarySort.RECENT))

    @Test fun dueSortPutsEarliestFirstAndUndatedLast() =
        assertEquals(listOf(b, a, c), sortLibrary(listOf(a, b, c), progress, LibrarySort.DUE))

    @Test fun lapsesSortPutsMostMissedFirst() =
        assertEquals(b, sortLibrary(listOf(a, b, c), progress, LibrarySort.LAPSES).first())

    @Test fun statusFilters() {
        assertEquals(listOf(c), filterByStatus(listOf(a, b, c), progress, StatusFilter.UNREVIEWED))
        assertEquals(listOf(b), filterByStatus(listOf(a, b, c), progress, StatusFilter.STRUGGLING))
        assertEquals(listOf(a, b, c), filterByStatus(listOf(a, b, c), progress, StatusFilter.ALL))
    }

    @Test fun itemsWithoutProgressAreTreatedAsNew() {
        assertEquals(listOf(a), filterByStatus(listOf(a), emptyMap(), StatusFilter.UNREVIEWED))
    }

    @Test fun groupsByStateInFixedOrderKeepingGivenOrderAndSkippingEmpty() {
        val items = listOf(item(1, 5), item(2, 4), item(3, 3), item(4, 2))
        val progress = mapOf(
            1L to EntryProgress(EntryState.LATER, now + day, 0),
            2L to EntryProgress(EntryState.DUE, now - 1, 0),
            3L to EntryProgress(EntryState.DUE, now - 2, 0),
            // 4번은 진행 정보가 없으면 새 카드로 본다
        )
        val groups = groupByState(items, progress)
        assertEquals(listOf(EntryState.DUE, EntryState.NEW, EntryState.LATER), groups.map { it.first })
        assertEquals(listOf(2L, 3L), groups[0].second.map { it.entry.id })
        assertEquals(listOf(4L), groups[1].second.map { it.entry.id })
        assertEquals(listOf(1L), groups[2].second.map { it.entry.id })
        assertEquals(listOf(EntryState.LATER), groupByState(listOf(items[0]), progress).map { it.first })
    }

    private val seoul = ZoneId.of("Asia/Seoul")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = java.time.LocalDateTime.of(y, m, d, h, 0).atZone(seoul).toInstant().toEpochMilli()
    private fun tagged(id: Long, created: Long, vararg tags: String) =
        LibraryItem(EntryEntity(id, created, Direction.RECEIVED, Lang.EN, "", null), mapOf(Lang.EN to "t$id", Lang.KO to "k$id"), tags.toList())

    @Test fun groupsByDateWithTodayAndYesterdayTitlesKeepingOrder() {
        val today = LocalDate.of(2026, 11, 1)
        val items = listOf(item(1, ms(2026, 11, 1)), item(2, ms(2026, 11, 1, 8)), item(3, ms(2026, 10, 31)), item(4, ms(2026, 10, 20)))
        val g = groupByDate(items, seoul, today)
        assertEquals(listOf("오늘", "어제", "10월 20일"), g.map { it.first.substringBefore(" (") })
        assertEquals(listOf(1L, 2L), g[0].second.map { it.entry.id })
        assertEquals(listOf(4L), g[2].second.map { it.entry.id })
    }

    @Test fun groupsByFirstTagAndPutsUntaggedLast() {
        val items = listOf(tagged(1, 1), tagged(2, 2, "여행", "음식"), tagged(3, 3, "일상"), tagged(4, 4, "여행"))
        val g = groupByTag(items)
        assertEquals(listOf("일상", "여행", "태그 없음").sorted(), g.map { it.first }.sorted())
        assertEquals("태그 없음", g.last().first)
        assertEquals(listOf(2L, 4L), g.first { it.first == "여행" }.second.map { it.entry.id })
    }

    @Test fun stageNamesCoverAllStages() {
        assertEquals(listOf("새 문장", "익히는 중", "익히는 중", "거의", "거의", "익힘"), (0..5).map(::stageName))
    }

    @Test fun statusChipsMatchStagesRecentMissesAndSoonDue() {
        val ps = mapOf(
            1L to EntryProgress(EntryState.NEW, now, 0),
            2L to EntryProgress(EntryState.DUE, now - 1, 0, stage = 2, recentMiss = true),
            3L to EntryProgress(EntryState.LATER, now + 2 * day, 0, stage = 4),
            4L to EntryProgress(EntryState.LATER, now + 10 * day, 0, stage = 5),
        )
        val items = (1L..4L).map { item(it, it) }
        val counts = statusCounts(items, ps, now)
        assertEquals(4, counts[StatusFilter.ALL])
        assertEquals(1, counts[StatusFilter.UNREVIEWED]); assertEquals(1, counts[StatusFilter.LEARNING])
        assertEquals(1, counts[StatusFilter.ALMOST]); assertEquals(1, counts[StatusFilter.MASTERED])
        assertEquals(listOf(2L), filterByStatus(items, ps, StatusFilter.RECENT_MISS, now).map { it.entry.id })
        // 이미 기한인 2번은 "곧 복습"이 아니라 "복습할 차례"
        assertEquals(listOf(3L), filterByStatus(items, ps, StatusFilter.DUE_SOON, now).map { it.entry.id })
    }

    @Test fun recentMissesAreAgainRatingsWithinTheWindow() {
        fun log(card: Long, at: Long, rating: com.chatlearn.domain.Rating) =
            com.chatlearn.data.ReviewLogEntity(cardId = card, reviewedAt = at, promptLang = Lang.KO, userAnswer = "", aiVerdict = null, aiFeedback = null, rating = rating)
        val cards = listOf(card(1, now).copy(id = 10), card(2, now).copy(id = 20), card(3, now).copy(id = 30))
        val logs = listOf(log(10, now - day, com.chatlearn.domain.Rating.AGAIN), log(20, now - 9 * day, com.chatlearn.domain.Rating.AGAIN), log(30, now, com.chatlearn.domain.Rating.GOOD))
        assertEquals(setOf(1L), recentMissIds(logs, cards, now - RECENT_MISS_MS))
    }

    @Test fun groupsByFriendWithTheirCommonestCountryAndNoFriendLast() {
        fun withKinds(id: Long, vararg tk: Pair<String, com.chatlearn.domain.TagKind>) =
            item(id, id).copy(tags = tk.map { it.first }, tagKinds = tk.toMap())
        val P = com.chatlearn.domain.TagKind.PERSON; val C = com.chatlearn.domain.TagKind.COUNTRY
        val items = listOf(withKinds(1, "Lucia" to P, "스페인" to C), withKinds(2), withKinds(3, "Lucia" to P, "스페인" to C), withKinds(4, "Tom" to P))
        val g = groupByPerson(items)
        assertEquals(listOf("Lucia · 스페인", "Tom", "친구 없음"), g.map { it.first })
        assertEquals(listOf(1L, 3L), g[0].second.map { it.entry.id })
    }

    @Test fun stageStepsUseTheTimeRememberedSinceThePreviousReviewOfThatCard() {
        fun log(card: Long, at: Long, rating: com.chatlearn.domain.Rating) =
            com.chatlearn.data.ReviewLogEntity(cardId = card, reviewedAt = at, promptLang = Lang.KO, userAnswer = "", aiVerdict = null, aiFeedback = null, rating = rating)
        val g = com.chatlearn.domain.Rating.GOOD
        val logs = listOf(
            log(1, now + 10 * day, g), // 앞 복습에서 8일 → 4
            log(1, now + 2 * day, g), // 첫 복습 → 1
            log(2, now + 3 * day, com.chatlearn.domain.Rating.AGAIN), // 다시 → 1
        )
        assertEquals(listOf(1, 1, 4), stageSteps(logs))
    }

    @Test fun knownEntriesHaveTheirOwnChipAndLeaveTheStageChips() {
        val ps = mapOf(
            1L to EntryProgress(EntryState.KNOWN, now + day, 0, stage = 5),
            2L to EntryProgress(EntryState.LATER, now + 10 * day, 0, stage = 5, excluded = true),
        )
        val items = (1L..2L).map { item(it, it) }
        val counts = statusCounts(items, ps, now)
        assertEquals(1, counts[StatusFilter.KNOWN])
        assertEquals(1, counts[StatusFilter.MASTERED]) // 알아요로 뺀 1번은 익힘 칩에 넣지 않는다
        assertEquals(listOf(2L), filterByStatus(items, ps, StatusFilter.EXCLUDED, now).map { it.entry.id })
        assertEquals(listOf(EntryState.LATER, EntryState.KNOWN), groupByState(items, ps).map { it.first })
        // 알아요는 복습 기한 순에서 맨 뒤, 자주 틀린 것에서도 빠진다.
        val overdueKnown = ps + (1L to ps.getValue(1L).copy(nextDueAt = now - 30 * day, lapses = 5))
        assertEquals(listOf(2L, 1L), sortLibrary(items, overdueKnown, LibrarySort.DUE).map { it.entry.id })
        assertEquals(emptyList(), filterByStatus(items, overdueKnown, StatusFilter.STRUGGLING, now))
    }
}
