package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchedulerTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    private fun first(r: Rating) = Scheduler.next(Scheduler.initial(now), r, now)

    @Test fun firstReviewUsesTheFsrsDefaultStabilities() {
        // FSRS-5 기본값: 다시 0.40일, 어려움 1.18일, 기억함 3.17일, 쉬움 15.69일.
        assertEquals(0.40255, first(Rating.AGAIN).stability!!, 1e-9)
        assertEquals(1.0, first(Rating.HARD).intervalDays)
        assertEquals(3.0, first(Rating.GOOD).intervalDays)
        assertEquals(now + 3 * day, first(Rating.GOOD).dueAt)
        assertEquals(16.0, first(Rating.EASY).intervalDays)
        assertEquals(1, first(Rating.GOOD).reps)
        assertEquals(now, first(Rating.GOOD).lastReviewedAt)
    }

    @Test fun againRetriesInTenMinutesAndCountsAsWrong() {
        val s = first(Rating.AGAIN)
        assertEquals(now + 10 * 60_000L, s.dueAt)
        assertEquals(1, s.lapses)
        assertEquals(0, s.reps)
    }

    @Test fun difficultyStaysWithinOneToTen() {
        var s = Scheduler.initial(now)
        var t = now
        repeat(30) { s = Scheduler.next(s, Rating.AGAIN, t); t += day }
        assertEquals(10.0, s.difficulty!!, 0.5)
        repeat(30) { s = Scheduler.next(s, Rating.EASY, s.dueAt) }
        assertTrue(s.difficulty!! >= 1.0)
    }

    private val mature = CardState(30.0, 2.5, 5, 0, dueAt = now, lastReviewedAt = now - 30 * day, stability = 30.0, difficulty = 5.0)

    @Test fun onTimeRecallGrowsAndForgettingShrinks() {
        assertTrue(Scheduler.next(mature, Rating.GOOD, now).stability!! > 30.0)
        val lapsed = Scheduler.next(mature, Rating.AGAIN, now)
        assertTrue(lapsed.stability!! < 30.0)
        assertEquals(now + 10 * 60_000L, lapsed.dueAt)
    }

    @Test fun intervalsAreOrderedHardGoodEasy() {
        val (h, g, e) = listOf(Rating.HARD, Rating.GOOD, Rating.EASY).map { Scheduler.next(mature, it, now).intervalDays }
        assertTrue(h <= g && g <= e, "hard=$h good=$g easy=$e")
    }

    @Test fun reviewingEarlyTheSameDayBarelyGrowsTheInterval() {
        val justSeen = mature.copy(dueAt = now + 30 * day, lastReviewedAt = now - 60_000)
        var s = justSeen
        repeat(3) { s = Scheduler.next(s, Rating.GOOD, now) }
        assertTrue(s.stability!! < 30.0 * 1.05, "stability=${s.stability}")
    }

    @Test fun recallingLateEarnsMoreThanRecallingOnTime() {
        val onTime = Scheduler.next(mature, Rating.GOOD, now).stability!!
        val late = Scheduler.next(mature.copy(lastReviewedAt = now - 60 * day), Rating.GOOD, now).stability!!
        assertTrue(late > onTime)
    }

    @Test fun higherTargetRetentionMeansShorterIntervals() {
        val relaxed = Scheduler.next(mature, Rating.GOOD, now, retention = 0.8).intervalDays
        val strict = Scheduler.next(mature, Rating.GOOD, now, retention = 0.95).intervalDays
        assertTrue(strict < relaxed)
        // 목표 90%에서는 간격이 안정도와 같다.
        val s = Scheduler.next(mature, Rating.GOOD, now)
        assertEquals(Math.round(s.stability!!).toDouble(), s.intervalDays)
    }

    @Test fun anOldCardWithoutStabilityContinuesFromItsInterval() {
        val old = CardState(10.0, 2.5, 3, 0, dueAt = now, lastReviewedAt = now - 10 * day)
        val s = Scheduler.next(old, Rating.GOOD, now)
        assertTrue(s.intervalDays > 10.0, "a reviewed card must not restart at 3 days")
        assertEquals(5.0, Scheduler.difficultyFromEase(2.5))
        assertEquals(10.0, Scheduler.difficultyFromEase(1.3))
        assertEquals(1.0, Scheduler.difficultyFromEase(4.0))
    }

    @Test fun jitterShiftsDueButNotStoredInterval() {
        val s = Scheduler.next(Scheduler.initial(now), Rating.GOOD, now, jitter = 0.05)
        assertEquals(3.0, s.intervalDays)
        assertEquals(now + (3 * day * 1.05).toLong(), s.dueAt)
    }

    @Test fun outOfRangeInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Scheduler.next(Scheduler.initial(now), Rating.GOOD, now, jitter = 0.5) }
        assertFailsWith<IllegalArgumentException> { Scheduler.next(Scheduler.initial(now), Rating.GOOD, now, retention = 0.5) }
    }
}
