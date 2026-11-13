package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict

/** RATE_LIMIT은 분당 한도(잠시 뒤 풀림), DAILY_LIMIT은 하루 한도, BLOCKED는 안전 필터, BAD_MODEL은 없는 모델 이름. */
enum class AiError { RATE_LIMIT, DAILY_LIMIT, OFFLINE, TIMEOUT, OVERLOADED, BAD_KEY, BAD_MODEL, BLOCKED, BAD_FORMAT, SERVER }

class AiException(val kind: AiError) : Exception(kind.name)

data class ReceivedResult(
    val detectedLang: Lang?, val koTranslation: String, val note: String, val altText: String = "",
    val keyPhrase: String = "", val altKeyPhrase: String = "",
)
data class SentCandidate(
    val text: String, val toneNote: String, val altText: String = "",
    val keyPhrase: String = "", val altKeyPhrase: String = "",
    /** 후보에 쓰인, 내가 저장해 둔 표현. 없으면 빈 문자열. */
    val usedPhrase: String = "",
)

/**
 * 내가 직접 쓴 영어·스페인어 문장을 고친 결과. [natural]이면 고칠 곳이 없다([corrected]는 원문과 같다).
 * [detectedLang]이 null이면 영어·스페인어가 아니다.
 */
data class CorrectionResult(
    val detectedLang: Lang?, val corrected: String, val natural: Boolean, val explanationKo: String,
    val koTranslation: String, val alternatives: List<String>, val keyPhrase: String = "",
    val altText: String = "", val altKeyPhrase: String = "",
)

/** 문장 속 단어 하나의 뜻([meaningKo], 문맥 기준), 사전형([baseForm]), 짧은 설명([note]). */
data class WordInfo(val meaningKo: String, val baseForm: String, val note: String)

/** 내가 저장한 핵심 표현([phrase])과 그 문장의 한국어 뜻([ko]). 보낼 말을 만들 때 AI에 참고로 준다. */
data class KnownExpression(val phrase: String, val ko: String)
data class GradeRequest(
    val promptText: String,
    val promptLang: Lang,
    val userAnswer: String,
    val answerLang: Lang,
    val reference: String,
)
data class GradeResult(val verdict: Verdict, val corrected: String, val explanationKo: String)

interface AiService {
    /** 연결 확인용 짧은 요청. 캐시를 쓰는 구현도 이것만은 매번 서버에 묻는다. */
    suspend fun ping(): Result<String> = translateTo("hello", Lang.EN, Lang.ES)
    suspend fun translateReceived(text: String): Result<ReceivedResult>
    suspend fun suggestSent(koText: String, target: Lang, known: List<KnownExpression> = emptyList()): Result<List<SentCandidate>>
    suspend fun gradeAnswer(req: GradeRequest): Result<GradeResult>
    suspend fun translateTo(text: String, from: Lang, to: Lang): Result<String>
    /** 여러 메시지를 한 번에 번역한다. 입력과 같은 수·순서로 돌려준다. */
    suspend fun translateReceivedBatch(items: List<String>): Result<List<ReceivedResult>>
    /** [sentence] 속 [word]의 뜻을 한국어 학습자에게 설명한다. */
    suspend fun lookupWord(word: String, sentence: String, lang: Lang): Result<WordInfo>
    /** 문장마다 배울 핵심 표현 하나(문장에서 그대로 떼어 낸 부분). 없으면 빈 문자열. 입력과 같은 수·순서로 돌려준다. */
    suspend fun extractKeyPhrases(items: List<String>, lang: Lang): Result<List<String>>
    suspend fun translateBatch(items: List<String>, from: Lang, to: Lang): Result<List<String>>
    /** 내가 쓴 문장을 원어민이 채팅에서 쓸 만한 문장으로 고치고 이유를 설명한다(첨삭). */
    suspend fun correctSentence(text: String): Result<CorrectionResult>
}
