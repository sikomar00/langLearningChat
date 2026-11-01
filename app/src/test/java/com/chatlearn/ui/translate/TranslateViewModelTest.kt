package com.chatlearn.ui.translate

import com.chatlearn.ai.*
import com.chatlearn.data.*
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
class TranslateViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val ai = FakeAiService()
    private lateinit var vm: TranslateViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = inMemoryDb(); repo = EntryRepository(db)
        vm = TranslateViewModel(ai, repo, { 1_000L })
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
        assertEquals(Notice.Saved(2), vm.state.value.notice)
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
        // 저장하면 입력이 비워지므로, 같은 메시지를 다시 붙여넣어 번역한 뒤 저장한다
        vm.setInput("How are you?")
        vm.translate().join()
        vm.saveReceived().join()
        assertEquals(Notice.Duplicate, vm.state.value.notice)
        assertEquals("How are you?", vm.state.value.input) // 중복이면 입력을 지우지 않는다
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test fun successfulSaveClearsInputAndResultsButKeepsTag() = runBlocking {
        vm.setInput("How are you?")
        vm.setTag("Alex")
        vm.translate().join()
        vm.saveReceived().join()
        val s = vm.state.value
        assertEquals("", s.input)
        assertNull(s.received)
        assertEquals("Alex", s.tag)
        assertEquals(Notice.Saved(2), s.notice)
    }

    @Test fun copyingACandidateClearsInputAndCandidates() = runBlocking {
        vm.setMode(TranslateMode.SENT)
        vm.setInput("곧 보자")
        vm.translate().join()
        vm.onCandidateCopied(vm.state.value.candidates.single()).join()
        assertEquals("", vm.state.value.input)
        assertTrue(vm.state.value.candidates.isEmpty())
    }

    @Test fun clearInputResetsTextResultsAndMessagesButNotTagOrMode() = runBlocking {
        vm.setInput("hi"); vm.setTag("Alex")
        vm.translate().join()
        vm.clearInput()
        val s = vm.state.value
        assertEquals("", s.input)
        assertNull(s.received)
        assertNull(s.error)
        assertEquals("Alex", s.tag)
    }

    @Test fun replaceInputSwapsTextAndDropsStaleResults() = runBlocking {
        vm.setInput("old"); vm.translate().join()
        vm.replaceInput("  brand new message ")
        val s = vm.state.value
        assertEquals("brand new message", s.input)
        assertNull(s.received)
        assertNull(s.notice)
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

    @Test fun saveUsesTheTranslatedTextEvenIfInputWasEditedAfterwards() = runBlocking {
        vm.setInput("How are you?")
        vm.translate().join()
        vm.setInput("Something completely different")
        vm.saveReceived().join()
        val item = repo.observeLibrary().first().single()
        assertEquals("How are you?", item.texts[Lang.EN])
    }

    @Test fun sentCandidateSavesTheKoreanThatWasTranslated() = runBlocking {
        vm.setMode(TranslateMode.SENT)
        vm.setInput("곧 보자")
        vm.translate().join()
        val c = vm.state.value.candidates.single()
        vm.setInput("다른 말")
        vm.onCandidateCopied(c).join()
        assertEquals("곧 보자", repo.observeLibrary().first().single().texts[Lang.KO])
    }

    @Test fun commaSeparatedTagsAreSavedAsSeparateTags() = runBlocking {
        vm.setInput("How are you?")
        vm.translate().join()
        vm.setTag("Alex, 약속")
        vm.saveReceived().join()
        assertEquals(listOf("Alex", "약속"), repo.observeLibrary().first().single().tags.sorted())
    }

    @Test fun pickingASuggestionFillsTheCurrentSegment() = runBlocking {
        vm.setTag("Alex, s")
        vm.pickTag("Sofía")
        assertEquals("Alex, Sofía, ", vm.state.value.tag)
    }

    @Test fun savingReceivedMessageCreatesExpansionCardWhenAutoExpandIsOn() = runBlocking {
        vm.setInput("How are you?"); vm.translate().join(); vm.saveReceived().join()
        assertEquals(2, db.dao().allCards().size)
        assertEquals("texto alterno", repo.observeLibrary().first().single().texts[Lang.ES])
    }

    @Test fun autoExpandOffSavesOnlyTheOriginalCard() = runBlocking {
        val off = TranslateViewModel(ai, repo, { 1_000L }, autoExpand = { false })
        off.setInput("How are you?"); off.translate().join(); off.saveReceived().join()
        assertEquals(1, db.dao().allCards().size)
    }

    @Test fun copiedCandidateAlsoExpands() = runBlocking {
        vm.setMode(TranslateMode.SENT); vm.setInput("곧 보자"); vm.translate().join()
        vm.onCandidateCopied(vm.state.value.candidates.single()).join()
        val item = repo.observeLibrary().first().single()
        assertEquals(Lang.EN, item.entry.chatLang)          // 기본 대상 언어는 영어
        assertEquals("¡Hola!", item.texts[Lang.ES])         // 후보의 반대 언어(스페인어) 버전
        assertEquals(2, db.dao().allCards().size)
    }

    @Test fun switchingModeClearsResults() = runBlocking {
        vm.setInput("hi"); vm.translate().join()
        vm.setMode(TranslateMode.SENT)
        assertNull(vm.state.value.received)
        assertTrue(vm.state.value.candidates.isEmpty())
    }

    @Test fun sharedTextSwitchesToReceivedModeAndTranslatesAtOnce() = runBlocking {
        vm.setMode(TranslateMode.SENT)
        vm.setInput("old")
        vm.receiveShared("  How are you?\n").join()
        val s = vm.state.value
        assertEquals(TranslateMode.RECEIVED, s.mode)
        assertEquals("How are you?", s.input)
        assertEquals("번역문", s.received?.koTranslation)
        assertEquals(listOf("received"), ai.calls)
    }

    @Test fun sharedTextReplacesPreviousResult() = runBlocking {
        vm.setInput("first"); vm.translate().join()
        vm.receiveShared("second").join()
        assertEquals("second", vm.state.value.source)
        assertEquals(2, ai.calls.size)
    }

    @Test fun savedNoticeCountsTheCardsThatWereCreated() = runBlocking {
        vm.setInput("Hello"); vm.translate().join(); vm.saveReceived().join()
        assertEquals(Notice.Saved(2), vm.state.value.notice) // 영어 + 스페인어 확장
        val off = TranslateViewModel(ai, repo, { 2_000L }) { false }
        off.setInput("Good night"); off.translate().join(); off.saveReceived().join()
        assertEquals(Notice.Saved(1), off.state.value.notice)
    }

    @Test fun receivedKeyPhraseIsShownEditableAndSaved() = runBlocking {
        ai.received = { Result.success(ReceivedResult(Lang.EN, "번역", "설명", "texto", keyPhrase = "see you", altKeyPhrase = "")) }
        vm.setInput("See you tomorrow"); vm.translate().join()
        assertEquals("See you", vm.state.value.keyPhrase) // 문장에 쓰인 표기로 맞춘다
        vm.setKeyPhrase("tomorrow")
        vm.saveReceived().join()
        val en = db.dao().allTexts().first { it.lang == Lang.EN }
        assertEquals("tomorrow", en.keyPhrase)
    }

    @Test fun clearingTheKeyPhraseSavesNone() = runBlocking {
        ai.received = { Result.success(ReceivedResult(Lang.EN, "번역", "설명", "texto", keyPhrase = "see you")) }
        vm.setInput("See you tomorrow"); vm.translate().join()
        vm.setKeyPhrase("")
        vm.saveReceived().join()
        assertNull(db.dao().allTexts().first { it.lang == Lang.EN }.keyPhrase)
    }

    private suspend fun seedKnown() {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "See you tomorrow", "내일 봐", "", null, emptyList(), keyPhrase = "See you"), 500)
    }

    @Test fun sentModePassesSavedExpressionsOfTheTargetLanguage() = runBlocking {
        seedKnown()
        vm.setMode(TranslateMode.SENT); vm.setTarget(Lang.EN)
        vm.setInput("곧 보자"); vm.translate().join()
        assertEquals(listOf("See you" to "내일 봐"), ai.lastKnown.map { it.phrase to it.ko })
    }

    @Test fun turningTheSettingOffSendsNothing() = runBlocking {
        seedKnown()
        val off = TranslateViewModel(ai, repo, { 1_000L }, useSaved = { false })
        off.setMode(TranslateMode.SENT); off.setTarget(Lang.EN)
        off.setInput("곧 보자"); off.translate().join()
        assertTrue(ai.lastKnown.isEmpty())
    }

    private suspend fun received(text: String = "It seems fine") {
        vm.setInput(text); vm.translate().join()
    }

    @Test fun tappingAWordLooksItUpInTheSentenceContext() = runBlocking {
        received()
        vm.lookupWord("seems,").join()
        val l = assertIs<LookupState.Done>(vm.state.value.lookup)
        assertEquals("seems", l.word)
        assertEquals("뜻:seems", l.info.meaningKo)
    }

    @Test fun lookupFailureIsReportedPerWord() = runBlocking {
        received()
        ai.lookup = { _, _, _ -> Result.failure(AiException(AiError.OFFLINE)) }
        vm.lookupWord("fine").join()
        assertEquals(LookupState.Failed("fine", AiError.OFFLINE), vm.state.value.lookup)
    }

    @Test fun nothingHappensWithoutAReceivedResultOrForNonWords() = runBlocking {
        vm.lookupWord("fine").join()
        assertNull(vm.state.value.lookup)
        received()
        vm.lookupWord("...").join()
        assertNull(vm.state.value.lookup)
        assertEquals(1, ai.calls.count { it == "received" })
        assertEquals(0, ai.calls.count { it == "lookup" })
    }

    @Test fun aWordCanBecomeTheKeyPhraseAndANewTranslationClearsTheLookup() = runBlocking {
        received()
        vm.lookupWord("fine").join()
        vm.useAsKeyPhrase("fine")
        assertEquals("fine", vm.state.value.keyPhrase)
        vm.setInput("Another one"); vm.translate().join()
        assertNull(vm.state.value.lookup)
    }

    @Test fun dismissingTheLookupClearsIt() = runBlocking {
        received()
        vm.lookupWord("fine").join()
        vm.dismissLookup()
        assertNull(vm.state.value.lookup)
    }
}
