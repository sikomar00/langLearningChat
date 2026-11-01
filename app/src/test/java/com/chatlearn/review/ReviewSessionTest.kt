package com.chatlearn.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import com.chatlearn.review.exercise.ExercisePolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class ReviewSessionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val ai = FakeAiService()
    private var other = false
    private val now = 10_000_000L

    private fun session() = ReviewSession(repo, ai, { now }, { 10 }, { other }, { 0.0 })

    @Before fun setUp() { db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { db.close() }

    private suspend fun saveOne() {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "How are you?", "어떻게 지내?", "", null, emptyList()), now - 1)
    }

    @Test fun emptyQueueWhenNothingDue() = runBlocking {
        assertTrue(session().loadQueue().isEmpty())
    }

    @Test fun promptDefaultsToKorean() = runBlocking {
        saveOne()
        val s = session()
        val item = s.prepare(s.loadQueue().single())
        assertEquals(Lang.KO, item.promptLang)
        assertEquals("어떻게 지내?", item.promptText)
        assertEquals("How are you?", item.reference)
        assertFalse("translate" in ai.calls)
    }

    @Test fun otherLanguagePromptIsGeneratedOnceAndStored() = runBlocking {
        saveOne(); other = true
        val s = session()
        val item = s.prepare(s.loadQueue().single())
        assertEquals(Lang.ES, item.promptLang)
        assertEquals("[번역] How are you?", item.promptText)
        // 두 번째 준비에서는 저장된 텍스트를 쓰므로 AI를 다시 부르지 않는다
        s.prepare(s.loadQueue().single())
        assertEquals(1, ai.calls.count { it == "translate" })
    }

    @Test fun otherLanguageFailureFallsBackToKorean() = runBlocking {
        saveOne(); other = true
        ai.translate = { _, _, _ -> Result.failure(AiException(AiError.RATE_LIMIT)) }
        val s = session()
        assertEquals(Lang.KO, s.prepare(s.loadQueue().single()).promptLang)
    }

    @Test fun verdictMapsToSuggestedRating() = runBlocking {
        saveOne()
        val s = session()
        val item = s.prepare(s.loadQueue().single())
        listOf(Verdict.CORRECT to Rating.GOOD, Verdict.CLOSE to Rating.HARD, Verdict.WRONG to Rating.AGAIN).forEach { (v, r) ->
            ai.grade = { Result.success(GradeResult(v, "x", "y")) }
            val o = s.grade(item, "I'm fine") as GradeOutcome.Ai
            assertEquals(r, o.suggested)
        }
    }

    @Test fun aiFailureFallsBackToSelfGrade() = runBlocking {
        saveOne()
        ai.grade = { Result.failure(AiException(AiError.RATE_LIMIT)) }
        val s = session()
        val item = s.prepare(s.loadQueue().single())
        val o = s.grade(item, "I'm fine") as GradeOutcome.SelfGrade
        assertEquals("How are you?", o.reference)
        assertEquals(AiError.RATE_LIMIT, o.aiError)
    }

    private fun sessionWith(kind: ExerciseKind, tts: Tts = FakeTts()) =
        ReviewSession(repo, ai, { now }, { 10 }, { other }, { 0.0 }, tts, { _, _, _, _ -> kind }, { 0.0 })

    private suspend fun saveSentence(text: String, ko: String = "내일 봐") {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, ko, "", null, emptyList()), now - 1)
    }

    @Test fun clozeItemShowsTheBlankedSentenceWithKoreanHintAndNeverCallsAi() = runBlocking {
        saveSentence("See you tomorrow"); other = true // 다른 언어 즉석 번역이 일어나면 안 된다
        val s = sessionWith(ExerciseKind.CLOZE)
        val item = s.prepare(s.loadQueue().single())
        assertEquals(ExerciseKind.CLOZE, item.exercise)
        assertEquals(Lang.KO, item.promptLang)
        assertEquals("See you _____", item.cloze!!.display)
        assertEquals("내일 봐", item.hintKo)
        assertEquals(1, item.blankCount)
        assertTrue(ai.calls.isEmpty())
    }

    @Test fun clozeGradingIsLocalAndMapsToRatings() = runBlocking {
        saveSentence("See you tomorrow")
        val s = sessionWith(ExerciseKind.CLOZE)
        val item = s.prepare(s.loadQueue().single())
        assertEquals(Rating.GOOD, (s.grade(item, listOf("Tomorrow")) as GradeOutcome.Local).suggested)
        assertEquals(Rating.HARD, (s.grade(item, listOf("tomorow")) as GradeOutcome.Local).suggested)
        assertEquals(Rating.AGAIN, (s.grade(item, listOf("yesterday")) as GradeOutcome.Local).suggested)
        assertFalse("grade" in ai.calls)
    }

    @Test fun clozeFallsBackToTranslateForASentenceWithNothingToBlank() = runBlocking {
        saveOne() // "How are you?"
        val s = ReviewSession(repo, ai, { now }, { 10 }, { false }, { 0.0 }, FakeTts(), ExercisePolicy::choose, { 0.0 })
        assertEquals(ExerciseKind.TRANSLATE, s.prepare(s.loadQueue().single()).exercise)
    }

    @Test fun dictationUsesTtsAndMarksWrongWords() = runBlocking {
        saveSentence("I like green apples")
        val tts = FakeTts()
        val s = sessionWith(ExerciseKind.DICTATION, tts)
        val item = s.prepare(s.loadQueue().single())
        assertEquals(ExerciseKind.DICTATION, item.exercise)
        s.speak(item, slow = true)
        assertEquals(Triple("I like green apples", Lang.EN, true), tts.spoken.single())
        val out = s.grade(item, "I like red apples") as GradeOutcome.Local
        assertEquals(Verdict.WRONG, out.verdict)
        assertEquals(listOf(true, true, false, true), out.marks.map { it.correct })
        assertFalse("grade" in ai.calls)
    }

    @Test fun defaultPolicyAvoidsDictationWithoutTts() = runBlocking {
        saveSentence("See you tomorrow")
        val s = ReviewSession(repo, ai, { now }, { 10 }, { false }, { 0.0 }, NoTts, ExercisePolicy::choose, { 0.99 })
        // 새 카드는 CLOZE; TTS가 없어도 받아쓰기로 가지 않는다
        assertEquals(ExerciseKind.CLOZE, s.prepare(s.loadQueue().single()).exercise)
    }

    @Test fun rateRecordsExerciseResponseTimeAndLocalVerdict() = runBlocking {
        saveSentence("See you tomorrow")
        val s = sessionWith(ExerciseKind.CLOZE)
        val item = s.prepare(s.loadQueue().single())
        val out = s.grade(item, listOf("tomorrow"))
        s.rate(item, Rating.GOOD, out, "tomorrow", responseMs = 2500)
        val log = db.dao().allLogs().single()
        assertEquals(ExerciseKind.CLOZE, log.exercise)
        assertEquals(2500L, log.responseMs)
        assertEquals(Verdict.CORRECT, log.aiVerdict)
    }

    @Test fun rateUpdatesScheduleAndWritesLog() = runBlocking {
        saveOne()
        val s = session()
        val item = s.prepare(s.loadQueue().single())
        val outcome = s.grade(item, "I'm fine")
        val updated = s.rate(item, Rating.GOOD, outcome, "I'm fine")
        assertEquals(now + 86_400_000L, updated.dueAt)
        assertTrue(s.loadQueue().isEmpty())
        val log = db.dao().allLogs().single()
        assertEquals("I'm fine", log.userAnswer)
        assertEquals(Verdict.CORRECT, log.aiVerdict)
        assertEquals(now + 86_400_000L, db.dao().allCards().single().dueAt)
    }
}
