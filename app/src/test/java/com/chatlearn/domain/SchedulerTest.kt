package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchedulerTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000L

    @Test fun firstGoodIsOneDay() {
        val s = Scheduler.next(Scheduler.initial(now), Rating.GOOD, now)
        assertEquals(1.0, s.intervalDays)
        assertEquals(1, s.reps)
        assertEquals(now + day, s.dueAt)
        assertEquals(now, s.lastReviewedAt)
    }

    @Test fun goodSequenceIs1Then3ThenTimesEase() {
        var s = Scheduler.initial(now)
        s = Scheduler.next(s, Rating.GOOD, now)
        s = Scheduler.next(s, Rating.GOOD, now)
        assertEquals(3.0, s.intervalDays)
        s = Scheduler.next(s, Rating.GOOD, now)
        assertEquals(7.5, s.intervalDays)
    }

    @Test fun againRetriesInTenMinutesThenOneDay() {
        val s = Scheduler.next(Scheduler.initial(now), Rating.AGAIN, now)
        assertEquals(now + 10 * 60_000L, s.dueAt)
        assertEquals(1.0, s.intervalDays)
        assertEquals(1, s.lapses)
        assertEquals(0, s.reps)
        assertEquals(2.3, s.ease, 1e-9)
        val after = Scheduler.next(s, Rating.GOOD, now)
        assertEquals(1.0, after.intervalDays)
    }

    @Test fun easeNeverDropsBelowFloor() {
        var s = Scheduler.initial(now)
        repeat(20) { s = Scheduler.next(s, Rating.AGAIN, now) }
        assertEquals(1.3, s.ease, 1e-9)
        repeat(20) { s = Scheduler.next(s, Rating.HARD, now) }
        assertEquals(1.3, s.ease, 1e-9)
    }

    @Test fun hardFromNewIsOneDay() {
        val s = Scheduler.next(Scheduler.initial(now), Rating.HARD, now)
        assertEquals(1.0, s.intervalDays)
        assertEquals(2.35, s.ease, 1e-9)
    }

    @Test fun easyFromNewIsAboutThreeDays() {
        val s = Scheduler.next(Scheduler.initial(now), Rating.EASY, now)
        assertEquals(3.25, s.intervalDays, 1e-9)
        assertEquals(2.65, s.ease, 1e-9)
    }

    @Test fun jitterShiftsDueButNotStoredInterval() {
        val s = Scheduler.next(Scheduler.initial(now), Rating.GOOD, now, jitter = 0.05)
        assertEquals(1.0, s.intervalDays)
        assertEquals(now + (day * 1.05).toLong(), s.dueAt)
    }

    @Test fun jitterOutOfRangeRejected() {
        assertFailsWith<IllegalArgumentException> {
            Scheduler.next(Scheduler.initial(now), Rating.GOOD, now, jitter = 0.5)
        }
    }
}
