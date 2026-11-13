package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatParseTest {
    @Test fun kakaoPcCopyGivesSpeakerAndMessage() {
        val p = parseChat("[Ana] [오후 3:15] ¿Vienes mañana?\n[나] [오후 3:16] Sí, claro")
        assertEquals(listOf(ChatLine("¿Vienes mañana?", "Ana"), ChatLine("Sí, claro", "나")), p.lines)
    }

    @Test fun kakaoExportLinesAndDateHeadersAreHandled() {
        val p = parseChat("2026년 11월 13일 금요일\n2026. 11. 13. 오후 3:15, Ana : Hola guapa\n오후 3:16")
        assertEquals(listOf(ChatLine("Hola guapa", "Ana")), p.lines)
    }

    @Test fun whatsappLinesGiveTheSpeaker() {
        assertEquals(ChatLine("Hola", "Alex"), parseChat("[13/11/26, 15:15:02] Alex: Hola").lines.single())
        assertEquals(ChatLine("See you", "Sam Lee"), parseChat("13/11/2026, 15:15 - Sam Lee: See you").lines.single())
    }

    @Test fun aColonWithoutAChatTimestampIsLeftAlone() {
        assertEquals(ChatLine("Note: bring food"), parseChat("Note: bring food").lines.single())
    }

    @Test fun tellsWhenLinesWereCutOff() {
        val many = (1..25).joinToString("\n") { "line $it" }
        val p = parseChat(many)
        assertEquals(20, p.lines.size)
        assertTrue(p.truncated)
        assertFalse(parseChat("a\nb").truncated)
    }

    @Test fun contextAroundALineUsesNeighboursWithSpeakers() {
        val lines = parseChat("[Ana] [오후 3:15] Hola\n[나] [오후 3:16] Hi\n[Ana] [오후 3:17] ¿Qué tal?\n[나] [오후 3:18] Bien").lines
        assertEquals("Ana: Hola\n나: Hi\n> Ana: ¿Qué tal?\n나: Bien", contextAround(lines, 2))
        assertEquals(null, contextAround(listOf(ChatLine("Solo")), 0))
    }

    @Test fun androidKakaoExportLinesAreMessagesNotDateHeaders() {
        val p = parseChat("2026년 11월 13일 금요일\n2026년 11월 13일 오후 3:15, Ana : Hola guapa")
        assertEquals(listOf(ChatLine("Hola guapa", "Ana")), p.lines)
    }

    @Test fun aPlainTimeBeforeAColonIsNotASpeaker() {
        assertEquals(ChatLine("Reminder: bring the tickets"), parseChat("10:30 Reminder: bring the tickets").lines.single())
        assertEquals(ChatLine("Hola", "Alex"), parseChat("[10:30] Alex: Hola").lines.single())
    }
}
