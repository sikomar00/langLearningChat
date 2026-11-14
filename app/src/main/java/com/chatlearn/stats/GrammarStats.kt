package com.chatlearn.stats

import com.chatlearn.data.GrammarEventEntity
import com.chatlearn.domain.GrammarPoint
import com.chatlearn.domain.GrammarSource
import com.chatlearn.domain.grammarPoint

/** 문법 항목 하나의 기록: 틀린 횟수, 써 보기·복습에서 만난 횟수, 보낼 말에서 필요했던 횟수. */
data class GrammarWeakness(val point: GrammarPoint, val errors: Int, val seen: Int, val needed: Int)

object GrammarStats {
    /** 많이 틀린 순(같으면 자주 필요했던 순). 목록에 없는 키는 버린다. */
    fun weakest(events: List<GrammarEventEntity>): List<GrammarWeakness> =
        events.groupBy { it.pointKey }.mapNotNull { (key, list) ->
            val point = grammarPoint(key) ?: return@mapNotNull null
            val practiced = list.filter { it.source != GrammarSource.REQUEST }
            GrammarWeakness(point, practiced.count { it.isError }, practiced.size, list.count { it.source == GrammarSource.REQUEST })
        }.filter { it.errors > 0 || it.needed > 0 }
            .sortedWith(compareByDescending<GrammarWeakness> { it.errors }.thenByDescending { it.needed })
}
