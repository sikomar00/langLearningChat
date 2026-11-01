package com.chatlearn.ui.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordTapTest {
    @Test fun punctuationAroundAWordIsDropped() {
        assertEquals("qué", cleanWord("¿qué?"))
        assertEquals("tomorrow", cleanWord("tomorrow,"))
        assertEquals("Hola", cleanWord("\"Hola!\""))
    }

    @Test fun apostrophesAndHyphensInsideAWordStay() {
        assertEquals("don't", cleanWord("don't,"))
        assertEquals("well-known", cleanWord("well-known."))
    }

    @Test fun chunksWithoutLettersAreNotWords() {
        assertNull(cleanWord("..."))
        assertNull(cleanWord("123"))
        assertNull(cleanWord("😂"))
    }

    @Test fun chunksSplitOnWhitespaceKeepingOrder() {
        assertEquals(listOf("See", "you", "tomorrow!"), textChunks("See  you\ntomorrow!"))
    }
}
