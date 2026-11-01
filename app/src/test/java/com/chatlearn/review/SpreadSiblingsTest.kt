package com.chatlearn.review

import com.chatlearn.data.DueCard
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.Lang
import kotlin.test.Test
import kotlin.test.assertEquals

class SpreadSiblingsTest {
    private var nextId = 1L
    private fun due(entry: Long) = DueCard(
        ReviewCardEntity(id = nextId++, entryId = entry, answerLang = if (nextId % 2 == 0L) Lang.EN else Lang.ES, dueAt = 0,
            intervalDays = 0.0, ease = 2.5, reps = 0, lapses = 0, lastReviewedAt = null),
        emptyMap(),
    )
    private fun entries(l: List<DueCard>) = l.map { it.card.entryId }

    @Test fun siblingsAreSeparatedWhenPossible() {
        val a1 = due(1); val a2 = due(1); val b1 = due(2)
        assertEquals(listOf(a1, b1, a2), spreadSiblings(listOf(a1, a2, b1)))
    }

    @Test fun twoPairsAlternate() {
        val l = listOf(due(1), due(1), due(2), due(2))
        assertEquals(listOf(1L, 2L, 1L, 2L), entries(spreadSiblings(l)))
    }

    @Test fun nothingChangesWhenThereIsNoChoice() {
        val l = listOf(due(1), due(1))
        assertEquals(l, spreadSiblings(l))
    }

    @Test fun anAlreadySpreadOrderIsKept() {
        val l = listOf(due(1), due(2), due(1), due(3))
        assertEquals(l, spreadSiblings(l))
    }

    @Test fun everyCardIsKeptExactlyOnce() {
        val l = listOf(due(1), due(1), due(1), due(2), due(3), due(3))
        assertEquals(l.map { it.card.id }.sorted(), spreadSiblings(l).map { it.card.id }.sorted())
    }

    @Test fun emptyAndSingleListsAreFine() {
        assertEquals(emptyList(), spreadSiblings(emptyList()))
        val one = listOf(due(1))
        assertEquals(one, spreadSiblings(one))
    }
}
