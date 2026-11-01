package com.chatlearn.ui.library

import com.chatlearn.ai.FakeAiService
import com.chatlearn.data.*
import com.chatlearn.expansion.ExpansionService
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class LibraryViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: LibraryViewModel
    private val ai = FakeAiService()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryDb(); repo = EntryRepository(db)
        vm = LibraryViewModel(repo, ExpansionService(repo, ai, { 1L }))
    }
    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private suspend fun save(text: String, tags: List<String>, at: Long) =
        (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, "한국어", "", null, tags), at) as SaveResult.Saved).id

    @Test fun saveTagsUpdatesTheEntry() = runBlocking {
        val id = save("a", listOf("Alex"), 1)
        vm.saveTags(id, listOf("Alex", "약속")).join()
        assertEquals(listOf("Alex", "약속"), repo.observeLibrary().first().single().tags)
    }

    @Test fun renameTagReportsWhatHappened() = runBlocking {
        save("a", listOf("Alex"), 1)
        save("b", listOf("알렉스"), 2)
        assertEquals(RenameResult.Merged, vm.renameTag("Alex", "알렉스").await())
        assertEquals(listOf("알렉스"), repo.observeLibrary().first().flatMap { it.tags }.distinct())
    }

    @Test fun unexpandedCountDropsAfterExpandAll() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "a", "한", "", null, emptyList()), 1)
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "b", "한", "", null, emptyList()), 2)
        assertEquals(2, vm.unexpanded.first { it == 2 })
        val report = vm.expandAll().await()
        assertEquals(2, report.created)
        assertEquals(0, vm.unexpanded.first { it == 0 })
    }

    @Test fun anEntryWithOtherLanguageTextButNoCardIsStillUnexpanded() = runBlocking {
        val id = save("a", emptyList(), 1)
        repo.putText(id, Lang.ES, "texto", com.chatlearn.domain.TextOrigin.AI) // 복습 중 즉석 번역으로 생긴 텍스트
        assertEquals(1, vm.unexpanded.first { it == 1 })
        assertFalse(id in vm.expandedIds.first())
        assertTrue(vm.expandOne(id).await())
        assertTrue(id in vm.expandedIds.first { id in it })
        assertEquals(0, vm.unexpanded.first { it == 0 })
    }

    @Test fun aSecondExpandAllWhileOneIsRunningReusesItAndCallsAiOnce() = runBlocking {
        repeat(3) { save("s$it", emptyList(), it.toLong()) }
        val first = vm.expandAll()
        val second = vm.expandAll()
        first.await(); second.await()
        assertEquals(1, ai.calls.count { it == "batch" })
    }

    @Test fun expandAllFailureIsReportedNotThrown() = runBlocking {
        save("a", emptyList(), 1)
        ai.batch = { _, _, _ -> throw IllegalStateException("boom") }
        val report = vm.expandAll().await()
        assertEquals(1, report.failedBatches)
        assertEquals(0, report.created)
    }

    @Test fun tagFilterIsClearedWhenItsTagDisappears() = runBlocking {
        val id = save("a", listOf("Alex"), 1)
        vm.tagFilter.value = "Alex"
        vm.saveTags(id, emptyList()).join()
        vm.dropStaleTagFilter(repo.observeLibrary().first().flatMap { it.tags })
        assertNull(vm.tagFilter.value)
    }
}
