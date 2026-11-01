package com.chatlearn.ui.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import com.chatlearn.review.FakeTts
import com.chatlearn.review.ReviewSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        vm.setAnswer("Hi"); vm.submit().join()
        assertIs<ReviewUi.Graded>(vm.state.value)
        vm.ensureStarted().join() // 탭을 다녀와도 채점 결과가 남아 있어야 한다
        assertIs<ReviewUi.Graded>(vm.state.value)
        Unit
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
}
