package com.chatlearn.ui.library

import com.chatlearn.data.EntryEntity
import com.chatlearn.data.LibraryItem
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
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

    @Test fun noCardsMeansNewWithNoDate() {
        val p = progressOf(emptyList(), now)
        assertEquals(EntryState.NEW, p.state)
        assertNull(p.nextDueAt)
    }

    @Test fun badgeText() {
        assertEquals("새 카드", dueBadge(EntryProgress(EntryState.NEW, now, 0), now))
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
}
