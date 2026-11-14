package com.chatlearn.stats

import com.chatlearn.data.GrammarEventEntity
import com.chatlearn.domain.GrammarSource
import com.chatlearn.domain.Lang
import kotlin.test.Test
import kotlin.test.assertEquals

class GrammarStatsTest {
    private fun ev(key: String, source: GrammarSource, error: Boolean) =
        GrammarEventEntity(lang = Lang.ES, pointKey = key, source = source, isError = error, entryId = null, at = 0)

    @Test fun weakestGrammarComesFirstWithCountsBySource() {
        val events = listOf(
            ev("es.ser_estar", GrammarSource.ATTEMPT, true), ev("es.ser_estar", GrammarSource.REVIEW, true),
            ev("es.ser_estar", GrammarSource.REVIEW, false),
            ev("es.por_para", GrammarSource.ATTEMPT, true),
            ev("es.subjunctive_present", GrammarSource.REQUEST, false), ev("es.subjunctive_present", GrammarSource.REQUEST, false),
            ev("unknown.key", GrammarSource.ATTEMPT, true),
        )
        val w = GrammarStats.weakest(events)
        assertEquals(listOf("es.ser_estar", "es.por_para", "es.subjunctive_present"), w.map { it.point.key })
        val first = w.first()
        assertEquals(2, first.errors); assertEquals(3, first.seen); assertEquals(0, first.needed)
        assertEquals(2, w.last().needed)
    }
}
