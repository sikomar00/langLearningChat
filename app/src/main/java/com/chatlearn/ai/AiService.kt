package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict

enum class AiError { RATE_LIMIT, OFFLINE, BAD_KEY, BAD_FORMAT, SERVER }

class AiException(val kind: AiError) : Exception(kind.name)

data class ReceivedResult(val detectedLang: Lang?, val koTranslation: String, val note: String)
data class SentCandidate(val text: String, val toneNote: String)
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
}
