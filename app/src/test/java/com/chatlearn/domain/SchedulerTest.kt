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
        s = Scheduler.next(s, Rating.GOOD, s.dueAt)
        assertEquals(3.0, s.intervalDays)
        s = Scheduler.next(s, Rating.GOOD, s.dueAt)
        assertEquals(7.5, s.intervalDays)
    }

    private val mature = CardState(intervalDays = 30.0, ease = 2.5, reps = 5, lapses = 0, dueAt = now + 29 * day, lastReviewedAt = now - day)

    @Test fun earlyGoodDoesNotInflateInterval() {
        var s = mature
        repeat(3) { s = Scheduler.next(s, Rating.GOOD, now) }
        assertEquals(30.0, s.intervalDays, 1e-9)
    }

    @Test fun earlyGoodCreditsOnlyTheTimeThatPassed() {
        val s = Scheduler.next(mature.copy(lastReviewedAt = now - 20 * day), Rating.GOOD, now)
        assertEquals(50.0, s.intervalDays, 1e-9)
    }

    @Test fun earlyHardAndEasyDoNotInflateEither() {
        assertEquals(30.0, Scheduler.next(mature, Rating.HARD, now).intervalDays, 1e-9)
        assertEquals(30.0, Scheduler.next(mature, Rating.EASY, now).intervalDays, 1e-9)
    }

    @Test fun aCardMadeDueEarlyByJitterIsAnOnTimeReview() {
        val due = CardState(10.0, 2.5, 5, 0, dueAt = now, lastReviewedAt = now - (9.5 * day).toLong())
        val s = Scheduler.next(due, Rating.HARD, now)
        assertEquals(12.0, s.intervalDays, 1e-9)
        assertEquals(2.35, s.ease, 1e-9)
    }

    @Test fun onTimeGoodStillMultipliesByEase() {
        val s = Scheduler.next(mature.copy(dueAt = now, lastReviewedAt = now - 30 * day), Rating.GOOD, now)
        assertEquals(75.0, s.intervalDays, 1e-9)
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
