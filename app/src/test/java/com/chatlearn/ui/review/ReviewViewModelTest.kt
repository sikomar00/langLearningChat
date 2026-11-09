package com.chatlearn.ui.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import com.chatlearn.review.FakeTts
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.ReviewSession
import com.chatlearn.review.exercise.ExercisePolicy
import com.chatlearn.review.GoalProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val ai = FakeAiService()
    private val now = 10_000_000L
    private lateinit var vm: ReviewViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryDb(); repo = EntryRepository(db)
        vm = ReviewViewModel(ReviewSession(repo, ai, { now }, { 10 }, { false }, { 0.0 }))
    }
    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    @Test fun noDueCardsShowsEmptyState() = runBlocking {
        vm.start().join()
        assertEquals(ReviewUi.Empty, vm.state.value)
    }

    @Test fun fullFlowEndsInDone() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        assertIs<ReviewUi.Prompt>(vm.state.value)
        vm.setAnswer("Hi")
        vm.submit().join()
        val graded = assertIs<ReviewUi.Graded>(vm.state.value)
        assertEquals(Rating.GOOD, graded.suggested)
        vm.rate(Rating.GOOD).join()
        assertEquals(ReviewUi.Done(1), vm.state.value)
    }

    @Test fun blankAnswerIsNotSubmitted() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.setAnswer("   ")
        vm.submit().join()
        assertIs<ReviewUi.Prompt>(vm.state.value)
        assertTrue("grade" !in ai.calls)
    }

    private var clockMs = now

    private fun goalVm(goal: Int) = ReviewViewModel(
        ReviewSession(repo, ai, { clockMs }, { 10 }, { false }, { 0.0 }, FakeTts(), ExercisePolicy::choose, { 0.99 }, dailyGoal = { goal })
    )

    private fun clozeVm(): ReviewViewModel = ReviewViewModel(
        ReviewSession(repo, ai, { clockMs }, { 10 }, { false }, { 0.0 }, FakeTts(), { _, _, _, _ -> ExerciseKind.CLOZE }, { 0.0 })
    )

    @Test fun clozeFlowGradesLocallyAndLogsResponseTime() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "See you tomorrow", "내일 봐", "", null, emptyList()), now - 1)
        val v = clozeVm()
        v.start().join()
        val p = assertIs<ReviewUi.Prompt>(v.state.value)
        assertEquals(1, p.answers.size)
        clockMs += 2_500
        v.setAnswer("tomorrow"); v.submit().join()
        val g = assertIs<ReviewUi.Graded>(v.state.value)
        assertEquals(Rating.GOOD, g.suggested)
        assertEquals(2_500L, g.responseMs)
        v.rate(Rating.GOOD).join()
        assertEquals(2_500L, db.dao().allLogs().single().responseMs)
        assertEquals(ExerciseKind.CLOZE, db.dao().allLogs().single().exercise)
    }

    @Test fun clozeWithTwoBlanksNeedsBothAnswers() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Yesterday morning everyone arrived early because traffic improved", "한", "", null, emptyList()), now - 1)
        val v = clozeVm()
        v.start().join()
        assertEquals(2, assertIs<ReviewUi.Prompt>(v.state.value).answers.size)
        v.setAnswer("x", 0); v.submit().join()
        assertIs<ReviewUi.Prompt>(v.state.value) // 두 번째 빈칸이 비어 있어 제출되지 않는다
        Unit
    }

    @Test fun ensureStartedDoesNotResetAReviewInProgress() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.ensureStarted().join()
        vm.begin().join()
        vm.setAnswer("Hi"); vm.submit().join()
        assertIs<ReviewUi.Graded>(vm.state.value)
        vm.ensureStarted().join() // 탭을 다녀와도 채점 결과가 남아 있어야 한다
        assertIs<ReviewUi.Graded>(vm.state.value)
        Unit
    }

    @Test fun spokenAnswerFillsTheFirstFieldOrIsAppended() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi there", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.putSpokenAnswer("hi")
        assertEquals("hi", assertIs<ReviewUi.Prompt>(vm.state.value).answers[0])
        vm.putSpokenAnswer("there")
        assertEquals("hi there", assertIs<ReviewUi.Prompt>(vm.state.value).answers[0])
    }

    @Test fun refreshPicksUpCardsThatBecameDueWhileTheTabWasEmpty() = runBlocking {
        vm.ensureStarted().join()
        assertEquals(ReviewUi.Empty, vm.state.value)
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.refresh(force = false).join()
        assertEquals(ReviewUi.Ready(1, 1), vm.state.value)
    }

    @Test fun refreshNeverInterruptsACardOnScreen() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.setAnswer("Hi")
        vm.refresh(force = true).join()
        assertEquals("Hi", assertIs<ReviewUi.Prompt>(vm.state.value).answers.single())
    }

    @Test fun onlyAForcedRefreshReplacesTheDoneSummary() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.setAnswer("Hi"); vm.submit().join(); vm.rate(Rating.GOOD).join()
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Bye", "잘 가", "", null, emptyList()), now - 1)
        vm.refresh(force = false).join()
        assertEquals(ReviewUi.Done(1), vm.state.value)
        vm.refresh(force = true).join()
        assertEquals(ReviewUi.Ready(1, 1), vm.state.value)
    }

    @Test fun afterTodaysNewCardLimitYouCanLearnMore() = runBlocking {
        val v = ReviewViewModel(ReviewSession(repo, ai, { now }, { 1 }, { false }, { 0.0 }))
        listOf("One" to "하나", "Two" to "둘", "Three" to "셋").forEach { (en, ko) ->
            repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, en, ko, "", null, emptyList()), now - 1)
        }
        v.start().join()
        v.setAnswer("x"); v.submit().join(); v.rate(Rating.GOOD).join()
        assertEquals(ReviewUi.Done(1), v.state.value)
        assertEquals(2, v.moreNew.value)
        v.learnMore(5).join()
        assertIs<ReviewUi.Prompt>(v.state.value)
        assertEquals(2, v.progress.value.total)
    }

    @Test fun doubleTapOnRatingRecordsOnlyOnce() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.setAnswer("Hi"); vm.submit().join()
        val first = vm.rate(Rating.GOOD)
        val second = vm.rate(Rating.GOOD)
        first.join(); second.join()
        assertEquals(1, db.dao().allLogs().size)
        assertEquals(ReviewUi.Done(1), vm.state.value)
    }

    @Test fun againRequeuesCardAtTheEnd() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.setAnswer("x"); vm.submit().join()
        vm.rate(Rating.AGAIN).join()
        assertIs<ReviewUi.Prompt>(vm.state.value)
        Unit
    }

    @Test fun doneCountsACardOnceEvenWhenRetriedAfterAgain() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        vm.start().join()
        vm.setAnswer("x"); vm.submit().join(); vm.rate(Rating.AGAIN).join()
        vm.setAnswer("Hi"); vm.submit().join(); vm.rate(Rating.GOOD).join()
        val d = assertIs<ReviewUi.Done>(vm.state.value)
        assertEquals(1, d.cards)
        assertEquals(2, d.count)
    }

    @Test fun startWithQueuesSelectedCardsEvenWhenNotDue() = runBlocking {
        val a = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now + 99_000_000) as SaveResult.Saved).id
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Bye", "잘가", "", null, emptyList()), now - 1)
        vm.startWith(setOf(a), null).join()
        val p = assertIs<ReviewUi.Prompt>(vm.state.value)
        assertEquals("Hi", p.item.reference)
        assertEquals(1, vm.custom.value)
        vm.setAnswer("Hi"); vm.submit().join(); vm.rate(Rating.GOOD).join()
        assertEquals(ReviewUi.Done(1), vm.state.value)
        assertEquals(1, db.dao().allLogs().size)
    }

    @Test fun startWithAgainRequeuesAndPlainStartReturnsToNormalQueue() = runBlocking {
        val a = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1) as SaveResult.Saved).id
        vm.startWith(setOf(a), null).join()
        vm.setAnswer("x"); vm.submit().join(); vm.rate(Rating.AGAIN).join()
        assertIs<ReviewUi.Prompt>(vm.state.value) // 같은 세션에서 다시 나온다
        vm.start().join()
        assertNull(vm.custom.value)
    }

    @Test fun startWithNothingMatchingShowsEmpty() = runBlocking {
        vm.startWith(setOf(12345L), null).join()
        assertEquals(ReviewUi.Empty, vm.state.value)
    }

    private suspend fun two(): Pair<Long, Long> {
        val a = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 2) as SaveResult.Saved).id
        val b = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Bye", "잘가", "", null, emptyList()), now - 1) as SaveResult.Saved).id
        return a to b
    }

    @Test fun progressCountsFinishedAndTotal() = runBlocking {
        two()
        vm.start().join()
        assertEquals(ReviewProgress(0, 2), vm.progress.value)
        vm.setAnswer("Hi"); vm.submit().join(); vm.rate(Rating.GOOD).join()
        assertEquals(ReviewProgress(1, 2), vm.progress.value)
    }

    @Test fun giveUpShowsTheAnswerAndSuggestsAgain() = runBlocking {
        two()
        vm.start().join()
        vm.giveUp().join()
        val g = assertIs<ReviewUi.Graded>(vm.state.value)
        assertEquals(Rating.AGAIN, g.suggested)
        assertIs<GradeOutcome.SelfGrade>(g.outcome)
        assertEquals("", g.answer)
        assertTrue(db.dao().allLogs().isEmpty()) // 평가를 누르기 전에는 기록하지 않는다
        vm.rate(Rating.AGAIN).join()
        assertEquals(Rating.AGAIN, db.dao().allLogs().single().rating)
    }

    @Test fun postponeMovesTheCardToTheEndWithoutRecording() = runBlocking {
        two()
        vm.start().join()
        val first = assertIs<ReviewUi.Prompt>(vm.state.value).item.card.id
        vm.postpone().join()
        val second = assertIs<ReviewUi.Prompt>(vm.state.value).item.card.id
        assertNotEquals(first, second)
        assertTrue(db.dao().allLogs().isEmpty())
        assertEquals(ReviewProgress(0, 2), vm.progress.value)
    }

    /** 채점 요청이 끝나지 않는 AI. 응답이 늦은 상황을 흉내 낸다. */
    private fun hangingGradeVm(): ReviewViewModel {
        val hanging = object : AiService by FakeAiService() {
            override suspend fun gradeAnswer(req: GradeRequest): Result<GradeResult> = awaitCancellation()
        }
        return ReviewViewModel(
            ReviewSession(repo, hanging, { clockMs }, { 10 }, { false }, { 0.0 }, FakeTts(), { _, _, _, _ -> ExerciseKind.TRANSLATE }, { 0.0 })
        )
    }

    @Test fun giveUpWhileTheAiIsStillGradingCancelsTheGradingAndShowsTheAnswer() = runBlocking {
        two()
        val v = hangingGradeVm()
        v.start().join()
        v.setAnswer("Hi")
        val grading = v.submit()
        assertTrue(assertIs<ReviewUi.Prompt>(v.state.value).checking)
        v.giveUp().join()
        val g = assertIs<ReviewUi.Graded>(v.state.value)
        assertIs<GradeOutcome.SelfGrade>(g.outcome)
        assertEquals(Rating.AGAIN, g.suggested)
        grading.join()
        assertIs<ReviewUi.Graded>(v.state.value) // 취소된 채점이 뒤늦게 화면을 덮어쓰지 않는다
        assertTrue(db.dao().allLogs().isEmpty())
    }

    @Test fun postponeWhileTheAiIsStillGradingMovesOnToTheNextCard() = runBlocking {
        two()
        val v = hangingGradeVm()
        v.start().join()
        val first = assertIs<ReviewUi.Prompt>(v.state.value).item.card.id
        v.setAnswer("Hi")
        val grading = v.submit()
        assertTrue(assertIs<ReviewUi.Prompt>(v.state.value).checking)
        v.postpone().join()
        val next = assertIs<ReviewUi.Prompt>(v.state.value)
        assertNotEquals(first, next.item.card.id)
        assertFalse(next.checking)
        grading.join()
        assertIs<ReviewUi.Prompt>(v.state.value)
        assertEquals(next.item.card.id, (v.state.value as ReviewUi.Prompt).item.card.id)
    }

    @Test fun previewShowsCountsBeforeAnyCardAndBeginStartsTheFirst() = runBlocking {
        two()
        vm.start(preview = true).join()
        assertEquals(ReviewUi.Ready(total = 2, newCards = 2), vm.state.value)
        vm.begin().join()
        assertIs<ReviewUi.Prompt>(vm.state.value)
        assertEquals(ReviewProgress(0, 2), vm.progress.value)
    }

    @Test fun previewWithNothingDueIsEmpty() = runBlocking {
        vm.start(preview = true).join()
        assertEquals(ReviewUi.Empty, vm.state.value)
    }

    @Test fun beginWithoutPreviewIsIgnored() = runBlocking {
        two()
        vm.start().join()
        val before = vm.state.value
        vm.begin().join()
        assertEquals(before, vm.state.value)
    }

    @Test fun ensureStartedShowsPreviewFirst() = runBlocking {
        two()
        vm.ensureStarted().join()
        assertIs<ReviewUi.Ready>(vm.state.value)
        Unit
    }

    private suspend fun firstCardId() = db.dao().allCards().minOf { it.id }

    @Test fun undoRestoresCardAndLogAndShowsTheSameCardAgain() = runBlocking {
        two()
        vm.start().join()
        val before = assertIs<ReviewUi.Prompt>(vm.state.value).item.card
        assertFalse(vm.canUndo.value)
        vm.setAnswer("x"); vm.submit().join(); vm.rate(Rating.GOOD).join()
        assertEquals(1, db.dao().allLogs().size)
        assertTrue(vm.canUndo.value)
        vm.undo().join()
        assertTrue(db.dao().allLogs().isEmpty())
        val restored = db.dao().allCards().single { it.id == before.id }
        assertEquals(before.reps, restored.reps)
        assertEquals(before.lastReviewedAt, restored.lastReviewedAt)
        val g = assertIs<ReviewUi.Graded>(vm.state.value)
        assertEquals(before.id, g.item.card.id)
        assertEquals(ReviewProgress(0, 2), vm.progress.value)
        assertFalse(vm.canUndo.value)
        vm.rate(Rating.EASY).join()
        assertEquals(Rating.EASY, db.dao().allLogs().single().rating)
    }

    @Test fun undoAfterAgainLeavesNoDuplicateInTheQueue() = runBlocking {
        val a = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1) as SaveResult.Saved).id
        vm.start().join()
        vm.setAnswer("x"); vm.submit().join(); vm.rate(Rating.AGAIN).join()
        assertIs<ReviewUi.Prompt>(vm.state.value) // 같은 세션에서 다시 나옴
        vm.undo().join()
        assertIs<ReviewUi.Graded>(vm.state.value)
        vm.rate(Rating.GOOD).join()
        assertEquals(ReviewUi.Done(1), vm.state.value)
        assertEquals(1, db.dao().allLogs().size)
        assertNotNull(a)
        Unit
    }

    @Test fun skipTodayMovesTheCardToTomorrowWithoutRecording() = runBlocking {
        two()
        vm.start().join()
        val skipped = assertIs<ReviewUi.Prompt>(vm.state.value).item.card.id
        vm.skipToday().join()
        assertNotEquals(skipped, assertIs<ReviewUi.Prompt>(vm.state.value).item.card.id)
        val due = db.dao().allCards().single { it.id == skipped }.dueAt
        assertTrue(due > now && due - now <= 86_400_000L)
        assertTrue(db.dao().allLogs().isEmpty())
        assertEquals(ReviewProgress(0, 1), vm.progress.value)
    }

    @Test fun deleteCurrentRemovesTheSentenceAndMovesOn() = runBlocking {
        two()
        vm.start().join()
        val gone = assertIs<ReviewUi.Prompt>(vm.state.value).item.card.entryId
        vm.deleteCurrent().join()
        assertEquals(1, repo.observeLibrary().first().size)
        assertNotEquals(gone, assertIs<ReviewUi.Prompt>(vm.state.value).item.card.entryId)
    }

    @Test fun editCurrentChangesTheShownSentence() = runBlocking {
        two()
        vm.start().join()
        val p = assertIs<ReviewUi.Prompt>(vm.state.value)
        vm.editCurrent("Fixed text", "고친 뜻").join()
        val after = assertIs<ReviewUi.Prompt>(vm.state.value)
        assertEquals(p.item.card.id, after.item.card.id)
        assertEquals("Fixed text", after.item.reference)
    }

    @Test fun appendToAnswerAddsTheCharacterToTheChosenBlank() = runBlocking {
        two()
        vm.start().join()
        vm.setAnswer("ma"); vm.appendToAnswer("ñ")
        assertEquals("mañ", assertIs<ReviewUi.Prompt>(vm.state.value).answers[0])
    }

    @Test fun clozeBlanksTheKeyPhraseWhenThereIsOne() = runBlocking {
        repo.saveEntry(
            EntryDraft(Direction.RECEIVED, Lang.ES, "Lo pensé por un momento que me dirías eso", "한", "", null, emptyList(), keyPhrase = "por un momento"),
            now - 1,
        )
        val v = clozeVm()
        v.start().join()
        val p = assertIs<ReviewUi.Prompt>(v.state.value)
        assertEquals("Lo pensé _____ que me dirías eso", p.item.cloze!!.display)
        assertEquals(listOf("por un momento"), p.item.cloze!!.answers)
        assertEquals("por un momento", p.item.keyPhrase)
    }

    @Test fun goalCountsDistinctCardsReviewedTodayAndUndoTakesItBack() = runBlocking {
        two()
        val v = goalVm(2)
        v.start().join()
        assertEquals(GoalProgress(0, 2), v.goal.value)
        v.setAnswer("x"); v.submit().join(); v.rate(Rating.GOOD).join()
        assertEquals(GoalProgress(1, 2), v.goal.value)
        v.setAnswer("y"); v.submit().join(); v.rate(Rating.GOOD).join()
        assertTrue(v.goal.value.reached)
        v.undo().join()
        assertEquals(GoalProgress(1, 2), v.goal.value)
    }

    @Test fun anAgainRepeatDoesNotCountTheCardTwice() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        val v = goalVm(5)
        v.start().join()
        v.setAnswer("x"); v.submit().join(); v.rate(Rating.AGAIN).join()
        v.setAnswer("x"); v.submit().join(); v.rate(Rating.GOOD).join()
        assertEquals(1, v.goal.value.done)
    }

    @Test fun snoozeMonthPushesTheCardOutWithoutRecording() = runBlocking {
        two()
        vm.start().join()
        val card = assertIs<ReviewUi.Prompt>(vm.state.value).item.card.id
        vm.snoozeMonth().join()
        val due = db.dao().allCards().single { it.id == card }.dueAt
        assertTrue(due >= now + 29L * 86_400_000L)
        assertTrue(db.dao().allLogs().isEmpty())
        assertNotEquals(card, assertIs<ReviewUi.Prompt>(vm.state.value).item.card.id)
    }

    @Test fun theNoteTravelsWithTheCardAndCanBeEdited() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "친근한 인사", null, emptyList()), now - 1)
        vm.start().join()
        assertEquals("친근한 인사", assertIs<ReviewUi.Prompt>(vm.state.value).item.note)
        vm.editCurrent("Hi", "안녕", note = "새 메모").join()
        assertEquals("새 메모", assertIs<ReviewUi.Prompt>(vm.state.value).item.note)
    }

    @Test fun theQueueNeverServesTheTwoLanguageCardsOfOneSentenceBackToBack() = runBlocking {
        repeat(3) { n ->
            repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Sentence number $n here", "한", "", null, emptyList(), "Frase $n aquí"), now - 100 + n)
        }
        val queue = ReviewSession(repo, ai, { now }, { 10 }, { false }, { 0.0 }).loadQueue()
        assertEquals(6, queue.size)
        queue.zipWithNext().forEach { (a, b) -> assertNotEquals(a.card.entryId, b.card.entryId) }
    }
}
