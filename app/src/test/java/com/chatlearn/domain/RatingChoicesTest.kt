package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RatingChoicesTest {
    @Test fun wrongAnswersCannotBeRatedEasy() {
        assertEquals(listOf(Rating.AGAIN, Rating.HARD), ratingChoices(Verdict.WRONG))
    }

    @Test fun closeAnswersCannotBeRatedEasy() {
        assertEquals(listOf(Rating.AGAIN, Rating.HARD, Rating.GOOD), ratingChoices(Verdict.CLOSE))
    }

    @Test fun correctAnswersCannotBeRatedAgain() {
        assertEquals(listOf(Rating.HARD, Rating.GOOD, Rating.EASY), ratingChoices(Verdict.CORRECT))
    }

    @Test fun selfGradedOrGivenUpOffersEverything() {
        assertEquals(Rating.entries, ratingChoices(null))
    }

    @Test fun labelsDescribeRecallNotDifficulty() {
        assertEquals(
            listOf("몰랐어요", "겨우 기억", "기억했어요", "완벽해요"),
            Rating.entries.map(::ratingLabel),
        )
    }
}
