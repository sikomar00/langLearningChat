package com.chatlearn.stats

import com.chatlearn.data.*
import com.chatlearn.domain.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val now = 1_800_000_000_000L

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()); db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private suspend fun saveAndReview(text: String, tags: List<String>, rating: Rating) {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, "한", "", null, tags), now - 10)
        val due = repo.queue(now, 0, 10).first { it.texts[Lang.EN] == text }
        repo.applyReview(
            due.card, Scheduler.next(due.card.toState(), rating, now),
            ReviewLogEntity(cardId = due.card.id, reviewedAt = now, promptLang = Lang.KO, userAnswer = "x",
                aiVerdict = null, aiFeedback = null, rating = rating),
        )
    }

    @Test fun statsInputCombinesLogsCardsAndLibrary() = runBlocking {
        saveAndReview("a", listOf("Alex"), Rating.GOOD)
        val input = repo.observeStatsInput().first()
        assertEquals(1, input.logs.size)
        assertEquals(1, input.cards.size)
        assertEquals(listOf("Alex"), input.items.single().tags)
    }

    @Test fun viewModelExposesComputedStats() = runBlocking {
        saveAndReview("a", listOf("Alex"), Rating.GOOD)
        saveAndReview("b", listOf("Alex"), Rating.AGAIN)
        val vm = StatsViewModel(repo, { now }, ZoneOffset.UTC)
        val s = withTimeout(5_000) { vm.stats.filterNotNull().first { it.growth.weekReviews == 2 } }
        assertEquals(Retention(2, 1), s.growth.week)
        assertEquals(Retention(2, 1), s.byTag["Alex"])
        assertEquals(1, s.weakest.size)
        // 계산이 다른 스레드에서 돌므로 Main을 되돌리기 전에 멈춘다(경합 방지).
        vm.viewModelScope.cancel()
    }
}
