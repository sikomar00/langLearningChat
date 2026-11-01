package com.chatlearn.ui.translate

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LearnedDateTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 1)

    @Test fun nothingPickedOrTodayMeansNow() {
        assertNull(learnedAtMillis(null, today, seoul))
        assertNull(learnedAtMillis(today, today, seoul))
    }

    @Test fun futureDatesAreTreatedAsToday() = assertNull(learnedAtMillis(today.plusDays(3), today, seoul))

    @Test fun pastDateBecomesNoonOfThatDayInTheLocalZone() {
        val ms = learnedAtMillis(LocalDate.of(2026, 10, 20), today, seoul)!!
        val back = java.time.Instant.ofEpochMilli(ms).atZone(seoul)
        assertEquals(LocalDate.of(2026, 10, 20), back.toLocalDate())
        assertEquals(12, back.hour)
    }
}
