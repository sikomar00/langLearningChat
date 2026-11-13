package com.chatlearn.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import com.chatlearn.review.exercise.AnswerGrader
import com.chatlearn.review.exercise.Cloze
import com.chatlearn.review.exercise.ClozeBuilder
import com.chatlearn.review.exercise.ExercisePolicy
import com.chatlearn.review.exercise.WordMark
import kotlin.random.Random

typealias ExercisePolicyFn = (card: ReviewCardEntity, canBlank: Boolean, ttsAvailable: Boolean, roll: Double) -> ExerciseKind

data class ReviewItem(
    val card: ReviewCardEntity,
    val texts: Map<Lang, String>,
    val promptLang: Lang,
    val exercise: ExerciseKind = ExerciseKind.TRANSLATE,
    val cloze: Cloze? = null,
    val keyPhrases: Map<Lang, String> = emptyMap(),
    val note: String = "",
    val context: String? = null,
) {
    /** 같은 카드를 큐에 다시 넣을 때(나중에, 다시, 평가 취소). */
    fun toDue(card: ReviewCardEntity = this.card) = DueCard(card, texts, keyPhrases, note, context)

    /** 정답 언어 문장의 핵심 표현. 없으면 null. */
    val keyPhrase: String? get() = keyPhrases[card.answerLang]
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
    private val dailyGoal: suspend () -> Int = { 0 },
    private val retention: suspend () -> Double = { Scheduler.DEFAULT_RETENTION },
    private val maxReviews: suspend () -> Int = { Scheduler.DEFAULT_MAX_REVIEWS },
) {
    fun now(): Long = clock()

    // 기억 목표는 큐를 불러올 때 한 번 읽어 그 세션 동안 쓴다(버튼 아래 보여 준 간격과 실제 일정이 어긋나지 않게).
    private var sessionRetention = Scheduler.DEFAULT_RETENTION

    /** 평가 버튼 아래에 보여 줄 다음 복습까지의 시간. */
    fun previews(card: ReviewCardEntity): Map<Rating, String> = ratingPreviews(card.toState(), clock(), sessionRetention)

    /** 연결·키·하루 한도 문제로 AI가 실패하면 그 큐가 끝날 때까지 AI를 부르지 않는다(카드마다 오래 기다리지 않게). */
    private var aiDown: AiError? = null

    /** [extraNew]는 오늘 새 카드 한도에 더 얹을 장 수("새 카드 더 배우기"). */
    suspend fun loadQueue(extraNew: Int = 0): List<DueCard> {
        val now = clock()
        aiDown = null
        sessionRetention = retention()
        return spreadSiblings(repo.queue(now, startOfDay(now), newPerDay(), maxReviews(), extraNew = extraNew))
    }

    /** 오늘 한도 때문에 아직 못 낸 새 카드 수. */
    suspend fun newCardsWaiting(): Int = repo.countDueNew(clock())

    private data class Undo(val before: ReviewCardEntity, val logId: Long)
    private var lastUndo: Undo? = null

    /** 마지막 평가를 취소한다. 취소할 게 없으면 false. */
    suspend fun undoLast(): Boolean {
        val u = lastUndo ?: return false
        repo.undoReview(u.before, u.logId)
        lastUndo = null
        return true
    }

    /** 오늘은 건너뛴다: 평가 기록 없이 다음 날 0시로 미룬다. */
    suspend fun skipToday(card: ReviewCardEntity) {
        repo.postponeCard(card.id, startOfDay(clock(), plusDays = 1))
    }

    private fun dayStart(): Long = startOfDay(clock())

    /** 연속 학습일(전체 통계 없이 복습 시각만으로). */
    suspend fun streak(): Int = com.chatlearn.stats.StatsCalculator.streak(repo.reviewTimes(), clock(), java.time.ZoneId.systemDefault())

    /** 오늘 복습한 카드 수와 하루 목표. */
    suspend fun todayProgress(): GoalProgress = GoalProgress(repo.cardsReviewedSince(dayStart()), dailyGoal())

    /** 여러 번 틀린 카드를 한 달 쉰다: 평가 기록 없이 30일 뒤로 미룬다. */
    suspend fun snoozeMonth(card: ReviewCardEntity) = repo.postponeCard(card.id, clock() + 30L * 86_400_000L)

    suspend fun deleteEntry(entryId: Long) = repo.deleteEntry(entryId)

    /** 정답 문장과 한국어 뜻을 고치고, 새 텍스트가 반영된 카드를 돌려준다. 문장이 그사이 삭제됐으면 null. */
    suspend fun edit(card: ReviewCardEntity, answerText: String, ko: String, note: String? = null): DueCard? {
        if (!repo.entryExists(card.entryId)) return null
        if (note != null) repo.updateNote(card.entryId, note.trim())
        if (answerText.isNotBlank()) repo.putText(card.entryId, card.answerLang, answerText, TextOrigin.USER)
        if (ko.isNotBlank()) repo.putText(card.entryId, Lang.KO, ko, TextOrigin.USER)
        return repo.dueCardOf(card)
    }

    suspend fun loadSelected(entryIds: Set<Long>, answerLang: Lang?): List<DueCard> {
        aiDown = null
        sessionRetention = retention()
        return spreadSiblings(repo.cardsFor(entryIds, answerLang))
    }

    suspend fun prepare(due: DueCard): ReviewItem {
        val answerLang = due.card.answerLang
        val reference = due.texts.getValue(answerLang)
        val cloze = ClozeBuilder.build(reference, seed = due.card.id * 31 + due.card.reps, keyPhrase = due.keyPhrases[answerLang])
        val kind = policy(due.card, cloze != null, tts.isAvailable(answerLang), roll())
        if (kind != ExerciseKind.TRANSLATE) {
            // 빈칸 채우기와 받아쓰기는 한국어 뜻이 힌트다.
            return ReviewItem(due.card, due.texts, Lang.KO, kind, cloze.takeIf { kind == ExerciseKind.CLOZE }, due.keyPhrases, due.note, due.context)
        }
        val otherLang = answerLang.otherForeign()
        var texts = due.texts
        var promptLang = Lang.KO
        if (chooseOther()) {
            if (otherLang !in texts && aiDown == null) {
                ai.translateTo(reference, answerLang, otherLang).onSuccess { t ->
                    repo.putText(due.card.entryId, otherLang, t, TextOrigin.AI)
                    texts = texts + (otherLang to t)
                }.onFailure { e -> (e as? AiException)?.kind?.takeIf { it in STICKY_AI_ERRORS }?.let { aiDown = it } }
            }
            if (otherLang in texts) promptLang = otherLang
        }
        return ReviewItem(due.card, texts, promptLang, keyPhrases = due.keyPhrases, note = due.note, context = due.context)
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
        ExerciseKind.TRANSLATE -> aiDown?.let { GradeOutcome.SelfGrade(item.reference, it) } ?: run {
            val req = GradeRequest(item.promptText, item.promptLang, answers.joinToString(" ").trim(), item.card.answerLang, item.reference)
            ai.gradeAnswer(req).fold(
                onSuccess = { GradeOutcome.Ai(it, suggestedRating(it.verdict)) },
                onFailure = {
                    val kind = (it as? AiException)?.kind
                    if (kind in STICKY_AI_ERRORS) aiDown = kind
                    GradeOutcome.SelfGrade(item.reference, kind)
                },
            )
        }
    }

    /** 평가를 저장하고, 갱신된 카드를 돌려준다(다시 평가를 같은 세션에 재출제할 때 쓴다). 카드가 그사이 삭제됐으면 null. */
    suspend fun rate(
        item: ReviewItem, rating: Rating, outcome: GradeOutcome, answer: String, responseMs: Long? = null,
    ): ReviewCardEntity? {
        val now = clock()
        val next = Scheduler.next(item.card.toState(), rating, now, jitter(), sessionRetention)
        val (verdict, feedback) = when (outcome) {
            is GradeOutcome.Ai -> outcome.result.verdict to outcome.result.explanationKo
            is GradeOutcome.Local -> outcome.verdict to null
            is GradeOutcome.SelfGrade -> null to null
        }
        val logId = repo.applyReview(
            item.card, next,
            ReviewLogEntity(
                cardId = item.card.id, reviewedAt = now, promptLang = item.promptLang, userAnswer = answer.trim(),
                aiVerdict = verdict, aiFeedback = feedback, rating = rating,
                responseMs = responseMs, exercise = item.exercise,
            ),
        ) ?: return null
        lastUndo = Undo(item.card, logId)
        return item.card.withState(next)
    }

    companion object {
        /** 다음 카드에서도 그대로일 실패. 일시적인 한도·과부하·시간 초과는 다음 카드에서 다시 시도한다. */
        private val STICKY_AI_ERRORS = setOf(AiError.OFFLINE, AiError.BAD_KEY, AiError.BAD_MODEL, AiError.DAILY_LIMIT)

        fun suggestedRating(v: Verdict) = when (v) {
            Verdict.CORRECT -> Rating.GOOD
            Verdict.CLOSE -> Rating.HARD
            Verdict.WRONG -> Rating.AGAIN
        }
    }
}
