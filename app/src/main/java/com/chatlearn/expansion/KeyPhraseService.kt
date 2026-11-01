package com.chatlearn.expansion

import com.chatlearn.ai.AiService
import com.chatlearn.data.EntryRepository

data class KeyPhraseReport(val filled: Int, val none: Int, val failedBatches: Int)

class KeyPhraseService(
    private val repo: EntryRepository,
    private val ai: AiService,
    private val batchSize: Int = 10,
) {
    /** 핵심 표현이 아직 없는 모든 외국어 문장을 언어별로 [batchSize]개씩 묶어 AI에 묻는다. 실패한 묶음은 다음에 다시 시도한다. */
    suspend fun fillAll(): KeyPhraseReport {
        var filled = 0; var none = 0; var failed = 0
        repo.keyPhraseTargets().groupBy { it.lang }.forEach { (lang, targets) ->
            targets.chunked(batchSize).forEach { batch ->
                ai.extractKeyPhrases(batch.map { it.text }, lang).fold(
                    onSuccess = { phrases ->
                        batch.zip(phrases).forEach { (t, p) -> if (repo.setKeyPhrase(t.entryId, t.lang, p)) filled++ else none++ }
                    },
                    onFailure = { failed++ },
                )
            }
        }
        return KeyPhraseReport(filled, none, failed)
    }
}
