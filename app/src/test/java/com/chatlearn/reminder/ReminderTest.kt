package com.chatlearn.reminder

import java.time.DayOfWeek
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

    @Test fun gentleAndCheerTonesChangeWordingButNotCounts() {
        val gentle = reminderText(due = 5, newCards = 2, tone = ReminderTone.GENTLE)!!
        val cheer = reminderText(due = 5, newCards = 0, tone = ReminderTone.CHEER)!!
        assertEquals(true, "5" in gentle && "2" in gentle && gentle != reminderText(5, 2))
        assertEquals(true, "5" in cheer && cheer != reminderText(5, 0))
        assertNull(reminderText(0, 3, tone = ReminderTone.CHEER))
    }

    @Test fun dayMaskUsesMondayAsBitZero() {
        val weekdaysOnly = 0b0011111
        assertEquals(true, isReminderDay(weekdaysOnly, DayOfWeek.MONDAY))
        assertEquals(true, isReminderDay(weekdaysOnly, DayOfWeek.FRIDAY))
        assertEquals(false, isReminderDay(weekdaysOnly, DayOfWeek.SATURDAY))
        assertEquals(false, isReminderDay(weekdaysOnly, DayOfWeek.SUNDAY))
        assertEquals(true, isReminderDay(ALL_DAYS, DayOfWeek.SUNDAY))
    }

    @Test fun toneFromNameFallsBackToPlain() {
        assertEquals(ReminderTone.CHEER, ReminderTone.fromName("CHEER"))
        assertEquals(ReminderTone.PLAIN, ReminderTone.fromName("nope"))
    }
}
