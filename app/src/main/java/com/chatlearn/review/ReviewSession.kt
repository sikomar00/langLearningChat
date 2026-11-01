package com.chatlearn.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import com.chatlearn.review.exercise.AnswerGrader
import com.chatlearn.review.exercise.Cloze
import com.chatlearn.review.exercise.ClozeBuilder
import com.chatlearn.review.exercise.ExercisePolicy
import com.chatlearn.review.exercise.WordMark
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

typealias ExercisePolicyFn = (card: ReviewCardEntity, canBlank: Boolean, ttsAvailable: Boolean, roll: Double) -> ExerciseKind

data class ReviewItem(
    val card: ReviewCardEntity,
    val texts: Map<Lang, String>,
    val promptLang: Lang,
    val exercise: ExerciseKind = ExerciseKind.TRANSLATE,
    val cloze: Cloze? = null,
) {
    val promptText: String get() = texts.getValue(promptLang)
    val reference: String get() = texts.getValue(card.answerLang)
    val hintKo: String get() = texts[Lang.KO].orEmpty()
    val blankCount: Int get() = cloze?.answers?.size ?: 1
}

sealed interface GradeOutcome {
    data class Ai(val result: GradeResult, val suggested: Rating) : GradeOutcome
    data class SelfGrade(val reference: String, val aiError: AiError?) : GradeOutcome
    /** 빈칸 채우기, 받아쓰기: AI 없이 앱 안에서 채점한 결과. */
    data class Local(
        val verdict: Verdict,
        val suggested: Rating,
        val reference: String,
        val marks: List<WordMark> = emptyList(),
    ) : GradeOutcome
}

class ReviewSession(
    private val repo: EntryRepository,
    private val ai: AiService,
    private val clock: () -> Long,
    private val newPerDay: suspend () -> Int,
    private val chooseOther: () -> Boolean = { Random.nextDouble() < 0.3 },
    private val jitter: () -> Double = { Random.nextDouble(-0.05, 0.05) },
    private val tts: Tts = NoTts,
    private val policy: ExercisePolicyFn = ExercisePolicy::choose,
    private val roll: () -> Double = { Random.nextDouble() },
) {
    fun now(): Long = clock()

    suspend fun loadQueue(): List<DueCard> {
        val now = clock()
        val dayStart = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return repo.queue(now, dayStart, newPerDay())
    }

    suspend fun loadSelected(entryIds: Set<Long>, answerLang: Lang?): List<DueCard> = repo.cardsFor(entryIds, answerLang)

    suspend fun prepare(due: DueCard): ReviewItem {
        val answerLang = due.card.answerLang
        val reference = due.texts.getValue(answerLang)
        val cloze = ClozeBuilder.build(reference, seed = due.card.id * 31 + due.card.reps)
        val kind = policy(due.card, cloze != null, tts.isAvailable(answerLang), roll())
        if (kind != ExerciseKind.TRANSLATE) {
            // 빈칸 채우기와 받아쓰기는 한국어 뜻이 힌트다.
            return ReviewItem(due.card, due.texts, Lang.KO, kind, cloze.takeIf { kind == ExerciseKind.CLOZE })
        }
        val otherLang = answerLang.otherForeign()
        var texts = due.texts
        var promptLang = Lang.KO
        if (chooseOther()) {
            if (otherLang !in texts) {
                ai.translateTo(reference, answerLang, otherLang).onSuccess { t ->
                    repo.putText(due.card.entryId, otherLang, t, TextOrigin.AI)
                    texts = texts + (otherLang to t)
                }
            }
            if (otherLang in texts) promptLang = otherLang
        }
        return ReviewItem(due.card, texts, promptLang)
    }

    fun speak(item: ReviewItem, slow: Boolean) = tts.speak(item.reference, item.card.answerLang, slow)

    suspend fun grade(item: ReviewItem, answer: String): GradeOutcome = grade(item, listOf(answer))

    suspend fun grade(item: ReviewItem, answers: List<String>): GradeOutcome = when (item.exercise) {
        ExerciseKind.CLOZE -> {
            val verdict = AnswerGrader.gradeCloze(item.cloze!!.answers, answers)
            GradeOutcome.Local(verdict, suggestedRating(verdict), item.reference)
        }
        ExerciseKind.DICTATION -> {
            val r = AnswerGrader.gradeDictation(item.reference, answers.joinToString(" "))
            GradeOutcome.Local(r.verdict, suggestedRating(r.verdict), item.reference, r.marks)
        }
        ExerciseKind.TRANSLATE -> {
            val req = GradeRequest(item.promptText, item.promptLang, answers.joinToString(" ").trim(), item.card.answerLang, item.reference)
            ai.gradeAnswer(req).fold(
                onSuccess = { GradeOutcome.Ai(it, suggestedRating(it.verdict)) },
                onFailure = { GradeOutcome.SelfGrade(item.reference, (it as? AiException)?.kind) },
            )
        }
    }

    /** 평가를 저장하고, 갱신된 카드를 돌려준다(다시 평가를 같은 세션에 재출제할 때 쓴다). */
    suspend fun rate(
        item: ReviewItem, rating: Rating, outcome: GradeOutcome, answer: String, responseMs: Long? = null,
    ): ReviewCardEntity {
        val now = clock()
        val next = Scheduler.next(item.card.toState(), rating, now, jitter())
        val (verdict, feedback) = when (outcome) {
            is GradeOutcome.Ai -> outcome.result.verdict to outcome.result.explanationKo
            is GradeOutcome.Local -> outcome.verdict to null
            is GradeOutcome.SelfGrade -> null to null
        }
        repo.applyReview(
            item.card, next,
            ReviewLogEntity(
                cardId = item.card.id, reviewedAt = now, promptLang = item.promptLang, userAnswer = answer.trim(),
                aiVerdict = verdict, aiFeedback = feedback, rating = rating,
                responseMs = responseMs, exercise = item.exercise,
            ),
        )
        return item.card.withState(next)
    }

    companion object {
        fun suggestedRating(v: Verdict) = when (v) {
            Verdict.CORRECT -> Rating.GOOD
            Verdict.CLOSE -> Rating.HARD
            Verdict.WRONG -> Rating.AGAIN
        }
    }
}
