package com.chatlearn.ai

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict

/** RATE_LIMIT은 분당 한도(잠시 뒤 풀림), DAILY_LIMIT은 하루 한도, BLOCKED는 안전 필터, BAD_MODEL은 없는 모델 이름. */
enum class AiError { RATE_LIMIT, DAILY_LIMIT, OFFLINE, TIMEOUT, OVERLOADED, BAD_KEY, BAD_MODEL, BLOCKED, BAD_FORMAT, SERVER }

class AiException(val kind: AiError) : Exception(kind.name)

data class ReceivedResult(
    val detectedLang: Lang?, val koTranslation: String, val note: String, val altText: String = "",
    val keyPhrase: String = "", val altKeyPhrase: String = "",
    /** 이 문장에 쓰인 문법 항목 키(정해진 목록 중). 복습에서 틀리면 이 항목들의 약점으로 센다. */
    val grammar: List<String> = emptyList(),
)
data class SentCandidate(
    val text: String, val toneNote: String, val altText: String = "",
    val keyPhrase: String = "", val altKeyPhrase: String = "",
    /** 후보에 쓰인, 내가 저장해 둔 표현. 없으면 빈 문자열. */
    val usedPhrase: String = "",
    /** 이 후보에 쓰인 문법 항목 키. 내가 자주 필요로 하는 문법을 찾는 데 쓴다. */
    val grammar: List<String> = emptyList(),
)

/**
 * 스스로 써 보기 한 번: 한국어로 하고 싶은 말([koIntent], 비어 있으면 내가 쓴 문장을 다듬기)과 내 시도([attempt]).
 * [hintLevel] 1은 어떤 문법인지만, 2는 규칙 설명, 3은 일부를 보여 준다. [context]는 답장할 친구의 말.
 */
data class AttemptRequest(val target: Lang, val attempt: String, val koIntent: String = "", val context: String? = null, val hintLevel: Int = 1)

/**
 * [correct]면 자연스럽게 맞게 썼다. 아니면 [hint]로 정답을 주지 않고 방향만 알려 준다.
 * [answer]·[explanationKo]는 "정답 보기"나 맞혔을 때 보여 주고, 저장에 [koMeaning]·[altText]·[keyPhrase]를 쓴다.
 */
data class AttemptFeedback(
    val correct: Boolean, val hint: String, val grammarErrors: List<String>, val answer: String, val explanationKo: String,
    val koMeaning: String, val altText: String, val keyPhrase: String,
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
data class GradeResult(val verdict: Verdict, val corrected: String, val explanationKo: String, val grammarErrors: List<String> = emptyList())

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
    /** 스스로 써 보기: 내 시도를 보고 맞았는지, 아니면 단계별 힌트를 준다. */
    suspend fun coachAttempt(req: AttemptRequest): Result<AttemptFeedback>
    /** [point] 문법을 연습할 한국어 문장 하나(짧은 채팅 말투). */
    suspend fun practicePrompt(point: com.chatlearn.domain.GrammarPoint): Result<String>
}
