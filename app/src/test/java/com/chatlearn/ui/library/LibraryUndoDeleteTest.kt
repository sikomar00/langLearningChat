package com.chatlearn.ui.library

import com.chatlearn.ai.FakeAiService
import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import com.chatlearn.expansion.ExpansionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
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
class LibraryUndoDeleteTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: LibraryViewModel
    private val sched = TestCoroutineScheduler()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(sched))
        db = inMemoryDb(); repo = EntryRepository(db)
        vm = LibraryViewModel(repo, ExpansionService(repo, FakeAiService(), { 1L }), undoWindowMs = 5_000)
    }
    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private suspend fun save(text: String) =
        (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, "한", "", null, emptyList()), 1) as SaveResult.Saved).id

    @Test fun deleteHidesAtOnceButRemovesOnlyAfterTheUndoWindow() = runBlocking {
        val id = save("a")
        val job = vm.delete(id)
        assertEquals(id, vm.pendingDelete.value)
        assertEquals(1, db.dao().allCards().size) // 아직 지우지 않았다
        sched.advanceTimeBy(5_001); job.join()
        assertNull(vm.pendingDelete.value)
        assertTrue(repo.observeLibrary().first().isEmpty())
    }

    @Test fun undoKeepsTheEntry() = runBlocking {
        val id = save("a")
        val job = vm.delete(id)
        vm.undoDelete()
        sched.advanceTimeBy(10_000); job.join()
        assertNull(vm.pendingDelete.value)
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test fun aSecondDeleteDoesNotBringTheFirstBack() = runBlocking {
        val a = save("a"); val b = save("b")
        vm.delete(a)
        val second = vm.delete(b)
        assertEquals(b, vm.pendingDelete.value) // 되돌리기는 마지막 삭제만
        // 앞선 삭제(a)가 DB에서 확정된 뒤에야 두 번째 작업이 대기(delay)에 들어간다. 그걸 기다린 다음 시간을 진행한다.
        withTimeout(10_000) { while (repo.observeLibrary().first().size != 1) delay(10) }
        sched.advanceTimeBy(5_001); second.join()
        assertTrue(repo.observeLibrary().first().isEmpty())
    }
}
