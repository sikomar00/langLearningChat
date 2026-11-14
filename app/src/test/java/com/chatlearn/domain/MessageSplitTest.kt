package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSplitTest {
    @Test fun splitsOnLinesTrimsAndDropsBlanks() {
        assertEquals(listOf("Hola", "¿Qué tal?"), splitMessages("  Hola \n\n¿Qué tal?\n  "))
    }

    @Test fun aSingleLineStaysOne() = assertEquals(listOf("How are you?"), splitMessages("How are you?"))

    @Test fun chatTimestampPrefixesAreRemoved() {
        assertEquals(listOf("Hola"), splitMessages("[12:30] Hola"))
        assertEquals(listOf("Hola"), splitMessages("12:30 - Hola"))
        assertEquals(listOf("Hola"), splitMessages("12/31/25, 12:30 - Hola"))
        assertEquals(listOf("Hola"), splitMessages("3:05 PM Hola"))
        assertEquals(listOf("Hola"), splitMessages("[12/31/25, 12:30:15] Hola"))
    }

    // 시간 표시가 붙은 대화 형식의 "이름: " 은 말한 사람으로 떼어 낸다(parseChat이 이름을 따로 돌려준다).
    @Test fun speakerNamesAfterATimestampAreSplitOff() = assertEquals(listOf("Hola"), splitMessages("[12:30] Alex: Hola"))

    @Test fun linesThatAreOnlyDatesOrTimesAreDropped() {
        assertEquals(listOf("Hola"), splitMessages("12/31/2025\n12:30\nHola"))
    }

    @Test fun atMostTwentyLinesAreKept() {
        val many = (1..30).joinToString("\n") { "line $it" }
        assertEquals(20, splitMessages(many).size)
        assertEquals("line 1", splitMessages(many).first())
    }

    @Test fun contextLinesRoundTripTheSavedMarker() {
        val ctx = contextAround(listOf(ChatLine("Hola", "Lucia"), ChatLine("Qué tal"), ChatLine("Bien")), 1)!!
        assertEquals(listOf("Lucia: Hola" to false, "Qué tal" to true, "Bien" to false), contextLines(ctx))
        assertEquals(listOf("just a reply\nsecond line" to false), contextLines("just a reply\nsecond line"))
    }
}
