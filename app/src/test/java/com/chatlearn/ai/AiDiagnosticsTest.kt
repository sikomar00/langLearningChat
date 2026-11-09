package com.chatlearn.ai

import org.junit.Test
import java.time.ZoneId
import kotlin.test.*

class AiDiagnosticsTest {
    @Test fun keepsTheNewestFirstAndCapsTheList() {
        val d = AiDiagnostics(max = 3)
        (1..5).forEach { d.record(it.toLong(), "m", 503, "e$it") }
        assertEquals(listOf("e5", "e4", "e3"), d.entries.value.map { it.detail })
    }

    @Test fun collapsesWhitespaceAndTruncatesLongDetail() {
        val d = AiDiagnostics()
        d.record(0, "m", 503, "a\n  b\t" + "x".repeat(500))
        val e = d.entries.value.single()
        assertTrue(e.detail.startsWith("a b x"))
        assertEquals(AiDiagnostics.MAX_DETAIL, e.detail.length)
    }

    @Test fun formatsALineWithTimeModelAndStatus() {
        val d = AiDiagnostics()
        val e = AiDiagnostics.Entry(0L, "gemini-3.5-flash-lite", 503, "overloaded")
        assertEquals("01/01 09:00:00 · gemini-3.5-flash-lite · HTTP 503 · overloaded", d.line(e, ZoneId.of("Asia/Seoul")))
    }

    @Test fun connectionErrorsHaveNoHttpStatus() {
        val d = AiDiagnostics()
        val e = AiDiagnostics.Entry(0L, "m", 0, "SocketTimeoutException: timeout")
        assertEquals("01/01 00:00:00 · m · 연결 오류 · SocketTimeoutException: timeout", d.line(e, ZoneId.of("UTC")))
    }

    @Test fun clearEmptiesTheList() {
        val d = AiDiagnostics()
        d.record(0, "m", 500, "x")
        d.clear()
        assertTrue(d.entries.value.isEmpty())
    }
}
