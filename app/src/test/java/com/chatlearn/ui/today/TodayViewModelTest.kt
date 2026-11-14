package com.chatlearn.ui.today

import androidx.lifecycle.viewModelScope
import com.chatlearn.ai.FakeAiService
import com.chatlearn.data.*
import com.chatlearn.domain.*
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.ReviewSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val now = 1_790_000_000_000L
    private lateinit var session: ReviewSession
    private lateinit var vm: TodayViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryDb(); repo = EntryRepository(db)
        session = ReviewSession(repo, FakeAiService(), { now }, { 2 }, dailyGoal = { 3 })
        vm = TodayViewModel(session, repo, { now }, ZoneOffset.UTC)
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun save(text: String, at: Long) =
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, "뜻 $text", "", null, emptyList()), at, expand = false)

    @Test fun nothingSavedYet() = runBlocking {
        vm.refresh().join()
        val ui = assertNotNull(vm.ui.value)
        assertFalse(ui.hasEntries)
        assertEquals(0, ui.due)
    }

    @Test fun showsTodaysCardsGoalStreakForecastAndRecentSentences() = runBlocking {
        save("one", now - 3_000); save("two", now - 2_000); save("three", now - 1_000)
        // 하나는 미리 복습해 두면 7일 예보에 잡히고, 연속 1일이 된다.
        val first = session.prepare(session.loadQueue().first())
        session.rate(first, Rating.GOOD, GradeOutcome.SelfGrade(first.reference, null), "x")
        vm.refresh().join()
        val ui = assertNotNull(vm.ui.value)
        assertTrue(ui.hasEntries)
        assertEquals(1, ui.newCards) // 하루 새 카드 2장 중 1장을 이미 씀
        assertEquals(1, ui.moreNew)
        assertEquals(1, ui.goal.done); assertEquals(3, ui.goal.goal)
        assertEquals(1, ui.streak)
        assertEquals(7, ui.forecast.size)
        assertEquals(1, ui.forecast.sumOf { it.reviews })
        assertEquals(listOf("three", "two", "one"), ui.recent.map { it.texts[Lang.EN] })
        // 복습한 문장 하나만 익힌 단계가 1 이상, 나머지는 새 문장(0).
        assertEquals(1, ui.recent.count { (ui.recentStages[it.entry.id] ?: 0) >= 1 })
    }
}
