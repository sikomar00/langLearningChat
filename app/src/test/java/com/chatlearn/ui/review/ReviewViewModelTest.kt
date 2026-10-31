package com.chatlearn.ui.review

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
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
}
