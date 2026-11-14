package com.chatlearn.ui.library

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
}
