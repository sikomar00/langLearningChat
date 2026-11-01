package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict

enum class AiError { RATE_LIMIT, OFFLINE, TIMEOUT, OVERLOADED, BAD_KEY, BAD_FORMAT, SERVER }

class AiException(val kind: AiError) : Exception(kind.name)

data class ReceivedResult(
    val detectedLang: Lang?, val koTranslation: String, val note: String, val altText: String = "",
    val keyPhrase: String = "", val altKeyPhrase: String = "",
)
data class SentCandidate(
    val text: String, val toneNote: String, val altText: String = "",
    val keyPhrase: String = "", val altKeyPhrase: String = "",
)
data class GradeRequest(
    val promptText: String,
    val promptLang: Lang,
    val userAnswer: String,
    val answerLang: Lang,
    val reference: String,
)
data class GradeResult(val verdict: Verdict, val corrected: String, val explanationKo: String)

interface AiService {
    suspend fun translateReceived(text: String): Result<ReceivedResult>
    suspend fun suggestSent(koText: String, target: Lang): Result<List<SentCandidate>>
    suspend fun gradeAnswer(req: GradeRequest): Result<GradeResult>
    suspend fun translateTo(text: String, from: Lang, to: Lang): Result<String>
    /** 문장마다 배울 핵심 표현 하나(문장에서 그대로 떼어 낸 부분). 없으면 빈 문자열. 입력과 같은 수·순서로 돌려준다. */
    suspend fun extractKeyPhrases(items: List<String>, lang: Lang): Result<List<String>>
    suspend fun translateBatch(items: List<String>, from: Lang, to: Lang): Result<List<String>>
}
