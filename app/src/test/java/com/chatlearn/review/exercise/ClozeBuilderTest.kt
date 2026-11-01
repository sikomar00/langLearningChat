package com.chatlearn.review.exercise

import kotlin.test.*

class ClozeBuilderTest {
    @Test fun sentenceWithoutBlankableWordGivesNull() {
        assertNull(ClozeBuilder.build("I am ok", 1))
        assertNull(ClozeBuilder.build("this that with", 1)) // 흔한 단어만
    }

    @Test fun blanksTheOnlyCandidateAndKeepsPunctuation() {
        val c = ClozeBuilder.build("How are you, my friend?", 7)!!
        assertEquals("How are you, my _____?", c.display)
        assertEquals(listOf("friend"), c.answers)
    }

    @Test fun spanishWordsWithAccentsAreHandled() {
        val c = ClozeBuilder.build("Nos vemos mañana", 3)!!
        assertEquals(1, c.answers.size) // 후보: vemos, mañana
        assertTrue(c.display.contains("_____"))
    }

    @Test fun sameSeedGivesSameResult() =
        assertEquals(ClozeBuilder.build("We should meet tomorrow evening", 42), ClozeBuilder.build("We should meet tomorrow evening", 42))

    @Test fun eightOrMoreWordsBlankTwoOtherwiseOne() {
        val eight = ClozeBuilder.build("Yesterday morning everyone arrived early because traffic improved", 1)!!
        assertEquals(2, eight.answers.size)
        assertEquals(2, Regex("_____").findAll(eight.display).count())
        val seven = ClozeBuilder.build("Yesterday morning everyone arrived early because traffic", 1)!!
        assertEquals(1, seven.answers.size)
    }

    @Test fun answersAreInSentenceOrder() {
        val c = ClozeBuilder.build("Yesterday morning everyone arrived early because traffic improved", 5)!!
        val sentence = "Yesterday morning everyone arrived early because traffic improved"
        assertTrue(sentence.indexOf(c.answers[0]) < sentence.indexOf(c.answers[1]))
    }
}
