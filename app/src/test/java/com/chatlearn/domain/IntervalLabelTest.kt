package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalLabelTest {
    private val min = 60_000L
    private val day = 86_400_000L

    @Test fun labelsUseTheLargestSensibleUnit() {
        assertEquals("1분", intervalLabel(10_000))
        assertEquals("10분", intervalLabel(10 * min))
        assertEquals("2시간", intervalLabel(120 * min))
        assertEquals("1일", intervalLabel(day))
        assertEquals("3일", intervalLabel(3 * day + 5 * min))
        assertEquals("3개월", intervalLabel(90 * day))
    }

    @Test fun newCardPreviewShowsEachRatingsNextInterval() {
        val p = ratingPreviews(Scheduler.initial(0), 0)
        assertEquals("10분", p.getValue(Rating.AGAIN))
        assertEquals("1일", p.getValue(Rating.HARD))
        assertEquals("1일", p.getValue(Rating.GOOD))
        assertEquals("3일", p.getValue(Rating.EASY))
    }
}
