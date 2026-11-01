package com.chatlearn.reminder

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(h: Int, m: Int = 0, d: Int = 1) =
        LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun laterTodayWhenTheHourHasNotPassed() =
        assertEquals(at(20), nextReminderAt(at(9), 20, zone))

    @Test fun tomorrowWhenTheHourHasPassed() =
        assertEquals(at(20, d = 2), nextReminderAt(at(21), 20, zone))

    @Test fun exactlyAtTheHourMeansTomorrowSoItDoesNotFireTwice() =
        assertEquals(at(20, d = 2), nextReminderAt(at(20), 20, zone))

    @Test fun midnightHourWorks() =
        assertEquals(at(0, d = 2), nextReminderAt(at(23, 59), 0, zone))

    @Test fun textMentionsDueAndNewCounts() {
        assertEquals("복습할 카드 5장이 기다려요.", reminderText(due = 5, newCards = 0))
        assertEquals("복습할 카드 5장이 기다려요. (새 카드 2장 포함)", reminderText(due = 5, newCards = 2))
    }

    @Test fun nothingDueMeansNoNotification() = assertNull(reminderText(0, 0))
}
