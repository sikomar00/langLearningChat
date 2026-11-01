package com.chatlearn.expansion

import com.chatlearn.ai.AiService
import com.chatlearn.data.EntryRepository
import com.chatlearn.domain.otherForeign

data class ExpansionReport(val created: Int, val skipped: Int, val failedBatches: Int)

class ExpansionService(
    private val repo: EntryRepository,
    private val ai: AiService,
    private val clock: () -> Long,
    private val batchSize: Int = 10,
) {
    /** 반대 언어 카드가 없는 모든 문장을 [batchSize]개씩 묶어 번역해서 확장한다. 실패한 묶음은 건너뛴다. */
    suspend fun expandAll(): ExpansionReport {
        var created = 0; var skipped = 0; var failed = 0
        repo.expansionTargets().groupBy { it.chatLang }.forEach { (from, targets) ->
            val to = from.otherForeign()
            targets.chunked(batchSize).forEach { batch ->
                ai.translateBatch(batch.map { it.chatText }, from, to).fold(
                    onSuccess = { texts ->
                        batch.zip(texts).forEach { (t, text) ->
                            if (repo.addExpansion(t.entryId, text, clock())) created++ else skipped++
                        }
                    },
                    onFailure = { failed++ },
                )
            }
        }
        return ExpansionReport(created, skipped, failed)
    }

    /** 문장 하나만 확장한다. 성공하면 true. */
    suspend fun expandOne(entryId: Long): Boolean {
        val target = repo.expansionTargets().firstOrNull { it.entryId == entryId } ?: return false
        val to = target.chatLang.otherForeign()
        val text = ai.translateTo(target.chatText, target.chatLang, to).getOrNull() ?: return false
        return repo.addExpansion(entryId, text, clock())
    }
}
