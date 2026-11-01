package com.chatlearn.ui.translate

import kotlin.test.Test
import kotlin.test.assertEquals

class WordDiffTest {
    private fun changed(original: String, corrected: String) =
        wordDiff(original, corrected).filter { it.changed }.map { it.text }

    @Test fun identicalSentenceHasNoChanges() = assertEquals(emptyList(), changed("See you soon!", "See you soon!"))

    @Test fun insertedWordIsMarked() = assertEquals(listOf("to"), changed("I go school", "I go to school"))

    @Test fun capitalAndSpellingFixesAreMarkedButAddedPeriodIsNot() =
        assertEquals(listOf("I", "don't"), changed("i dont know", "I don't know."))

    @Test fun keepsEveryWordOfTheCorrectedSentenceInOrder() =
        assertEquals(listOf("Nos", "vemos", "mañana."), wordDiff("nos vemos manana", "Nos vemos mañana.").map { it.text })

    @Test fun replacedWordIsMarked() = assertEquals(listOf("went"), changed("Yesterday I go there", "Yesterday I went there"))
}
