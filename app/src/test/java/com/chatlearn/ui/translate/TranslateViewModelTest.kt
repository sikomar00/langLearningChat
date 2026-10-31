package com.chatlearn.ui.translate

import com.chatlearn.ai.*
import com.chatlearn.data.*
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
class TranslateViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val ai = FakeAiService()
    private lateinit var vm: TranslateViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryDb(); repo = EntryRepository(db)
        vm = TranslateViewModel(ai, repo) { 1_000L }
    }
    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    @Test fun blankInputNeverCallsAi() = runBlocking {
        vm.setInput("   \n ")
        vm.translate().join()
        assertTrue(ai.calls.isEmpty())
        assertNull(vm.state.value.received)
        assertNull(vm.state.value.error)
    }

    @Test fun receivedTranslationThenSave() = runBlocking {
        vm.setInput("How are you?")
        vm.translate().join()
        assertEquals("번역문", vm.state.value.received?.koTranslation)
        vm.setTag("Alex")
        vm.saveReceived().join()
        assertEquals(Notice.Saved, vm.state.value.notice)
        val item = repo.observeLibrary().first().single()
        assertEquals("How are you?", item.texts[Lang.EN])
        assertEquals(listOf("Alex"), item.tags)
    }

    @Test fun unsupportedLanguageShowsErrorAndCannotSave() = runBlocking {
        ai.received = { Result.success(ReceivedResult(null, "x", "y")) }
        vm.setInput("Olá, tudo bem?")
        vm.translate().join()
        assertEquals(TranslateError.UnsupportedLang, vm.state.value.error)
        assertNull(vm.state.value.received)
        vm.saveReceived().join()
        assertTrue(repo.observeLibrary().first().isEmpty())
    }

    @Test fun aiFailureKeepsInputAndReportsKind() = runBlocking {
        ai.received = { Result.failure(AiException(AiError.RATE_LIMIT)) }
        vm.setInput("hi")
        vm.translate().join()
        assertEquals(TranslateError.Ai(AiError.RATE_LIMIT), vm.state.value.error)
        assertEquals("hi", vm.state.value.input)
        assertFalse(vm.state.value.loading)
    }

    @Test fun savingTheSameMessageTwiceReportsDuplicate() = runBlocking {
        vm.setInput("How are you?")
        vm.translate().join()
        vm.saveReceived().join()
        vm.saveReceived().join()
        assertEquals(Notice.Duplicate, vm.state.value.notice)
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test fun sentModeCopySavesChosenCandidate() = runBlocking {
        vm.setMode(TranslateMode.SENT)
        vm.setTarget(Lang.ES)
        vm.setInput("곧 보자")
        vm.translate().join()
        val c = vm.state.value.candidates.single()
        vm.onCandidateCopied(c).join()
        val item = repo.observeLibrary().first().single()
        assertEquals(Lang.ES, item.entry.chatLang)
        assertEquals("Hi!", item.texts[Lang.ES])
        assertEquals("곧 보자", item.texts[Lang.KO])
    }

    @Test fun switchingModeClearsResults() = runBlocking {
        vm.setInput("hi"); vm.translate().join()
        vm.setMode(TranslateMode.SENT)
        assertNull(vm.state.value.received)
        assertTrue(vm.state.value.candidates.isEmpty())
    }
}
