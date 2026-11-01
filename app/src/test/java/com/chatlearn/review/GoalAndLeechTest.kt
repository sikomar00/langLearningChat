package com.chatlearn.review

import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalAndLeechTest {
    @Test fun goalIsReachedWhenDoneMeetsIt() {
        assertFalse(GoalProgress(9, 10).reached)
        assertTrue(GoalProgress(10, 10).reached)
        assertTrue(GoalProgress(15, 10).reached)
    }

    @Test fun zeroGoalMeansOff() {
        val g = GoalProgress(5, 0)
        assertFalse(g.enabled); assertFalse(g.reached)
    }

    @Test fun fractionIsClampedToOne() {
        assertEquals(0.5f, GoalProgress(5, 10).fraction)
        assertEquals(1f, GoalProgress(20, 10).fraction)
        assertEquals(0f, GoalProgress(3, 0).fraction)
    }

    private fun card(lapses: Int) = ReviewCardEntity(entryId = 1, answerLang = Lang.EN, dueAt = 0, intervalDays = 1.0,
        ease = 2.5, reps = 0, lapses = lapses, lastReviewedAt = null)

    @Test fun threeLapsesMakeACardALeech() {
        assertFalse(isLeech(card(2)))
        assertTrue(isLeech(card(3)))
        assertTrue(isLeech(card(7)))
    }
}
