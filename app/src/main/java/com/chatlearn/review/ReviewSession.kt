package com.chatlearn.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

data class ReviewItem(val card: ReviewCardEntity, val texts: Map<Lang, String>, val promptLang: Lang) {
    val promptText: String get() = texts.getValue(promptLang)
    val reference: String get() = texts.getValue(card.answerLang)
}

sealed interface GradeOutcome {
    data class Ai(val result: GradeResult, val suggested: Rating) : GradeOutcome
    data class SelfGrade(val reference: String, val aiError: AiError?) : GradeOutcome
}

class ReviewSession(
    private val repo: EntryRepository,
    private val ai: AiService,
    private val clock: () -> Long,
    private val newPerDay: suspend () -> Int,
    private val chooseOther: () -> Boolean = { Random.nextDouble() < 0.3 },
    private val jitter: () -> Double = { Random.nextDouble(-0.05, 0.05) },
) {
    suspend fun loadQueue(): List<DueCard> {
        val now = clock()
        val dayStart = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return repo.queue(now, dayStart, newPerDay())
    }

    suspend fun prepare(due: DueCard): ReviewItem {
        val answerLang = due.card.answerLang
        val otherLang = if (answerLang == Lang.EN) Lang.ES else Lang.EN
        var texts = due.texts
        var promptLang = Lang.KO
        if (chooseOther()) {
            if (otherLang !in texts) {
                ai.translateTo(texts.getValue(answerLang), answerLang, otherLang).onSuccess { t ->
                    repo.putText(due.card.entryId, otherLang, t, TextOrigin.AI)
                    texts = texts + (otherLang to t)
                }
            }
            if (otherLang in texts) promptLang = otherLang
        }
        return ReviewItem(due.card, texts, promptLang)
    }

    suspend fun grade(item: ReviewItem, answer: String): GradeOutcome {
        val req = GradeRequest(item.promptText, item.promptLang, answer.trim(), item.card.answerLang, item.reference)
        return ai.gradeAnswer(req).fold(
            onSuccess = { GradeOutcome.Ai(it, suggestedRating(it.verdict)) },
            onFailure = { GradeOutcome.SelfGrade(item.reference, (it as? AiException)?.kind) },
        )
    }

    /** 평가를 저장하고, 갱신된 카드를 돌려준다(다시 평가를 같은 세션에 재출제할 때 쓴다). */
    suspend fun rate(item: ReviewItem, rating: Rating, outcome: GradeOutcome, answer: String): ReviewCardEntity {
        val now = clock()
        val next = Scheduler.next(item.card.toState(), rating, now, jitter())
        val ai = outcome as? GradeOutcome.Ai
        repo.applyReview(
            item.card, next,
            ReviewLogEntity(
                cardId = item.card.id, reviewedAt = now, promptLang = item.promptLang, userAnswer = answer.trim(),
                aiVerdict = ai?.result?.verdict, aiFeedback = ai?.result?.explanationKo, rating = rating,
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
