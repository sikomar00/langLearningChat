package com.chatlearn.ui.translate

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import com.chatlearn.domain.TagKind
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

    @Test fun pasteAndTranslateReplacesInputAndRunsTheCurrentMode() = runBlocking {
        vm.setMode(TranslateMode.SENT)
        vm.setInput("이전 입력")
        vm.pasteAndTranslate("  내일 보자 \n").join()
        assertEquals("내일 보자", vm.state.value.input)
        assertEquals(listOf("sent"), ai.calls)
        assertEquals("Hi!", vm.state.value.candidates.single().text)
    }

    @Test fun pickedPastDateIsSavedAsLearnedDateAndKeptForTheNextSave() = runBlocking {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(1_000L).atZone(zone).toLocalDate().minusDays(5)
        vm.setLearnedOn(day)
        vm.setInput("How are you?")
        vm.translate().join()
        vm.saveReceived().join()
        val saved = repo.observeLibrary().first().single()
        assertEquals(day, java.time.Instant.ofEpochMilli(saved.entry.createdAt).atZone(zone).toLocalDate())
        // 지난 대화를 몰아서 저장할 때 매번 다시 고르지 않게 고른 날짜는 남는다.
        assertEquals(day, vm.state.value.learnedOn)
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
        assertEquals(Notice.Duplicate("How are you?"), vm.state.value.notice)
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
        val off = TranslateViewModel(ai, repo, { 2_000L }, autoExpand = { false })
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

    private val chat = "Hello there\nSee you tomorrow\nOlá amigo"

    @Test fun severalLinesAreTranslatedInOneBatchCall() = runBlocking {
        vm.setInput(chat); vm.translate().join()
        val items = vm.state.value.batch
        assertEquals(listOf("Hello there", "See you tomorrow", "Olá amigo"), items.map { it.text })
        assertEquals(listOf("receivedBatch"), ai.calls)
        assertNull(vm.state.value.received)
    }

    @Test fun unsupportedLanguageLinesStartUnselected() = runBlocking {
        ai.receivedBatch = { items -> Result.success(items.map { ReceivedResult(if (it.startsWith("Ol")) null else Lang.EN, "k", "n", "a") }) }
        vm.setInput(chat); vm.translate().join()
        assertEquals(listOf(true, true, false), vm.state.value.batch.map { it.selected })
        vm.toggleBatch(2)
        assertFalse(vm.state.value.batch[2].selected) // 지원하지 않는 언어는 켤 수 없다
        vm.toggleBatch(0)
        assertFalse(vm.state.value.batch[0].selected)
    }

    @Test fun savingStoresOnlySelectedLinesAndCountsDuplicates() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hello there", "안녕", "", null, emptyList()), 1)
        vm.setInput(chat); vm.translate().join()
        vm.toggleBatch(1) // See you tomorrow 제외
        vm.setTag("Alex")
        vm.saveBatch().join()
        val notice = vm.state.value.notice
        assertEquals(Notice.BatchSaved(saved = 1, duplicates = 1), notice) // Olá amigo만 새로 저장, Hello there는 중복
        assertEquals(setOf("Hello there", "Olá amigo"), db.dao().allTexts().filter { it.lang == Lang.EN }.map { it.text }.toSet())
        assertTrue(vm.state.value.batch.isEmpty())
        assertEquals("", vm.state.value.input)
    }

    @Test fun moreThanTenLinesAreSentInChunks() = runBlocking {
        vm.setInput((1..15).joinToString("\n") { "line $it" }); vm.translate().join()
        assertEquals(2, ai.calls.count { it == "receivedBatch" })
        assertEquals(15, vm.state.value.batch.size)
    }

    @Test fun aFailedBatchShowsTheErrorAndKeepsTheInput() = runBlocking {
        ai.receivedBatch = { Result.failure(AiException(AiError.OVERLOADED)) }
        vm.setInput(chat); vm.translate().join()
        assertEquals(TranslateError.Ai(AiError.OVERLOADED), vm.state.value.error)
        assertTrue(vm.state.value.batch.isEmpty())
        assertEquals(chat, vm.state.value.input)
    }

    @Test fun aSingleLineStillUsesTheSingleMessagePath() = runBlocking {
        vm.setInput("Hello there"); vm.translate().join()
        assertEquals(listOf("received"), ai.calls)
        assertTrue(vm.state.value.batch.isEmpty())
    }

    @Test fun koreanTextSharedFromAnotherAppBecomesSomethingToSay() = runBlocking {
        vm.receiveShared("내일 몇 시에 만나?").join()
        assertEquals(TranslateMode.SENT, vm.state.value.mode)
        assertTrue("sent" in ai.calls)
        vm.receiveShared("See you at 7").join()
        assertEquals(TranslateMode.RECEIVED, vm.state.value.mode)
    }

    @Test fun myOwnDraftSelectedToFixOpensInWriteCoachWithoutAskingYet() = runBlocking {
        vm.setIntent("내일 보자"); vm.receiveShared("other").join()
        vm.receiveShared("  Yo fui a la playa ayer ", editable = true).join()
        val st = vm.state.value
        assertEquals(TranslateMode.CORRECT, st.mode)
        assertEquals("Yo fui a la playa ayer", st.input)
        assertEquals("", st.intent)
        assertEquals(Lang.ES, st.targetLang)
        // 언어를 확인하고 "확인"을 누를 때 묻는다.
        assertEquals(listOf("received"), ai.calls)
    }

    @Test fun anEditableSelectionWithoutLettersOrInKoreanIsNotADraft() = runBlocking {
        vm.receiveShared("010-1234-5678", editable = true).join()
        assertEquals(TranslateMode.RECEIVED, vm.state.value.mode)
        vm.receiveShared("내일 보자", editable = true).join()
        assertEquals(TranslateMode.SENT, vm.state.value.mode)
    }

    @Test fun switchingLanguageDropsFeedbackGivenInTheOtherLanguage() = runBlocking {
        vm.setMode(TranslateMode.CORRECT); vm.setInput("I go there"); vm.submitAttempt().join()
        assertTrue(vm.state.value.attempts.isNotEmpty())
        vm.setTarget(Lang.ES)
        assertTrue(vm.state.value.attempts.isEmpty())
    }

    @Test fun aPastedKakaoChatSplitsOffSpeakersPicksTheFriendAndKeepsTheContext() = runBlocking {
        vm.setInput("[Ana] [오후 3:15] Hola\n[나] [오후 3:16] Hi\n[Ana] [오후 3:17] Que tal")
        vm.translate().join()
        val st = vm.state.value
        assertEquals(listOf("Hola", "Hi", "Que tal"), st.batch.map { it.text })
        assertEquals(setOf("Ana"), st.people)
        vm.toggleBatch(0); vm.toggleBatch(1) // 마지막 줄만 저장
        vm.saveBatch().join()
        val saved = db.dao().allEntries().single()
        assertEquals("Ana: Hola\n나: Hi\n> Ana: Que tal", saved.contextText)
        assertEquals(listOf("Ana"), repo.observeLibrary().first().single().tags)
        assertEquals(TagKind.PERSON, repo.observeLibrary().first().single().tagKinds["Ana"])
    }

    @Test fun peopleAndCountriesCanBeToggledAndAreSavedWithTheSentence() = runBlocking {
        vm.togglePerson("Leo"); vm.toggleCountry("멕시코"); vm.toggleCountry("스페인"); vm.toggleCountry("스페인")
        vm.setInput("How are you?")
        vm.translate().join()
        vm.saveReceived().join()
        val item = repo.observeLibrary().first().single()
        assertEquals(setOf("Leo", "멕시코"), item.tags.toSet())
        assertEquals(TagKind.COUNTRY, item.tagKinds["멕시코"])
        assertEquals(setOf("Leo"), vm.state.value.people) // 다음 문장도 같은 친구일 때가 많아 남겨 둔다
    }

    @Test fun cuttingALongPasteIsReported() = runBlocking {
        vm.setInput((1..25).joinToString("\n") { "line $it" })
        vm.translate().join()
        assertTrue(vm.state.value.truncated)
        assertEquals(20, vm.state.value.batch.size)
    }

    @Test fun theReplyLanguageIsRememberedAndRestored() = runBlocking {
        var saved: Lang? = null
        val v = TranslateViewModel(ai, repo, { 1_000L }, savedTarget = { Lang.ES }, rememberTarget = { saved = it })
        v.awaitTarget()
        assertEquals(Lang.ES, v.state.value.targetLang)
        v.setTarget(Lang.EN)
        assertEquals(Lang.EN, saved)
    }

    @Test fun aDuplicateSaveTellsWhichSentenceItIs() = runBlocking {
        vm.setInput("How are you?"); vm.translate().join(); vm.saveReceived().join()
        vm.setInput("How are you?"); vm.translate().join(); vm.saveReceived().join()
        assertEquals(Notice.Duplicate("How are you?"), vm.state.value.notice)
    }

    @Test fun aNewChatReplacesTheFriendThatWasPickedForTheLastOne() = runBlocking {
        vm.setInput("[Ana] [오후 3:15] Hola\n[Ana] [오후 3:16] Que tal"); vm.translate().join()
        assertEquals(setOf("Ana"), vm.state.value.people)
        vm.setInput("[Bob] [오후 4:15] Hi\n[Bob] [오후 4:16] What's up"); vm.translate().join()
        assertEquals(setOf("Bob"), vm.state.value.people)
    }

    @Test fun withTwoPossibleFriendsNobodyIsPickedButBothAreOffered() = runBlocking {
        vm.setInput("[Ana] [오후 3:15] Hola\n[Minsu] [오후 3:16] Hi"); vm.translate().join()
        assertTrue(vm.state.value.people.isEmpty())
        assertEquals(listOf("Ana", "Minsu"), vm.state.value.speakers)
    }

    @Test fun theSavedReplyLanguageIsUsedEvenForTheFirstTranslation() = runBlocking {
        // 저장된 언어를 읽는 데 시간이 걸려도 첫 번역이 그 언어를 기다린다.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val v = TranslateViewModel(ai, repo, { 1_000L }, savedTarget = { gate.await(); Lang.ES })
        val job = v.receiveShared("내일 보자")
        gate.complete(Unit)
        job.join()
        assertEquals(Lang.ES, ai.lastSentTarget)
        v.setTarget(Lang.EN)
        assertEquals(Lang.EN, v.state.value.targetLang)
    }

    @Test fun writingItYourselfGivesStrongerHintsThenTheAnswerAndSaves() = runBlocking {
        vm.setMode(TranslateMode.CORRECT)
        vm.setIntent("개가 있어")
        vm.setInput("I have dog"); vm.submitAttempt().join()
        assertEquals(1, ai.lastCoach!!.hintLevel)
        assertEquals("힌트 1", vm.state.value.attempts.last().feedback.hint)
        vm.setInput("I has a dog"); vm.submitAttempt().join()
        assertEquals(2, ai.lastCoach!!.hintLevel)
        assertFalse(vm.state.value.revealed)
        vm.reveal()
        assertTrue(vm.state.value.revealed)
        vm.saveAttempt().join()
        val item = repo.observeLibrary().first().single()
        assertEquals("I have a dog.", item.texts[Lang.EN])
        assertEquals("개가 있어", item.texts[Lang.KO])
        assertEquals(Direction.SENT, item.entry.direction)
        // 시도마다 틀린 문법이 약점 기록으로 남는다.
        assertEquals(2, db.dao().allGrammarEvents().count { it.source == com.chatlearn.domain.GrammarSource.ATTEMPT && it.isError })
    }

    @Test fun aCorrectAttemptIsSavedAsWritten() = runBlocking {
        ai.coach = { Result.success(AttemptFeedback(true, "좋아요!", emptyList(), "I have a dog.", "", "개가 있어", "", "")) }
        vm.setMode(TranslateMode.CORRECT); vm.setIntent("개가 있어")
        vm.setInput("I've got a dog"); vm.submitAttempt().join()
        assertTrue(vm.state.value.attempts.last().feedback.correct)
        vm.saveAttempt().join()
        assertEquals("I've got a dog", repo.observeLibrary().first().single().texts[Lang.EN])
    }

    @Test fun replyPracticeSendsTheFriendsMessageAsContextAndKeepsIt() = runBlocking {
        vm.startReply("Want to grab coffee later?", Lang.EN)
        assertEquals(TranslateMode.CORRECT, vm.state.value.mode)
        vm.setInput("Sure, sounds good"); vm.submitAttempt().join()
        assertEquals("Want to grab coffee later?", ai.lastCoach!!.context)
        vm.reveal(); vm.saveAttempt().join()
        assertEquals("Want to grab coffee later?", db.dao().allEntries().single().contextText)
    }

    @Test fun practisingAGrammarPointStartsWithAPromptInThatLanguage() = runBlocking {
        vm.startPractice(com.chatlearn.domain.grammarPoint("es.ser_estar")!!).join()
        assertEquals("연습 문장", vm.state.value.intent)
        assertEquals(Lang.ES, vm.state.value.targetLang)
        assertEquals(TranslateMode.CORRECT, vm.state.value.mode)
    }

    @Test fun aLookedUpWordCanBeSavedAsAWordCardWithItsSentence() = runBlocking {
        vm.setInput("See you tomorrow"); vm.translate().join()
        vm.lookupWord("tomorrow").join()
        vm.saveWord().join()
        val e = db.dao().allEntries().single()
        assertEquals(com.chatlearn.domain.EntryKind.WORD, e.kind)
        assertEquals("See you tomorrow", e.contextText)
        assertEquals("tomorrow", repo.observeLibrary().first().single().texts[Lang.EN])
        assertEquals(1, db.dao().allCards().size) // 단어는 다른 언어 카드를 만들지 않는다
    }

    @Test fun grammarOfSavedSentencesAndCopiedRepliesIsRecorded() = runBlocking {
        ai.received = { Result.success(ReceivedResult(Lang.ES, "번역", "설명", grammar = listOf("es.ser_estar"))) }
        vm.setInput("Estoy cansado"); vm.translate().join(); vm.saveReceived().join()
        assertEquals(listOf("es.ser_estar"), db.dao().allEntryGrammar().map { it.pointKey })
        ai.sent = { _, _ -> Result.success(listOf(SentCandidate("Ojalá vengas", "n", grammar = listOf("es.subjunctive_present")))) }
        vm.setMode(TranslateMode.SENT); vm.setTarget(Lang.ES); vm.setInput("너 오면 좋겠다"); vm.translate().join()
        vm.onCandidateCopied(vm.state.value.candidates.single()).join()
        assertTrue(db.dao().allGrammarEvents().any { it.source == com.chatlearn.domain.GrammarSource.REQUEST && it.pointKey == "es.subjunctive_present" })
    }

    @Test fun aNewTryAfterSeeingTheAnswerStartsHidingItAgain() = runBlocking {
        vm.setMode(TranslateMode.CORRECT); vm.setIntent("개가 있어")
        vm.setInput("I have dog"); vm.submitAttempt().join(); vm.reveal()
        vm.setInput("I has dog"); vm.submitAttempt().join()
        assertFalse(vm.state.value.revealed)
    }

    @Test fun aNewSentenceStartsAtTheFirstHintAndForgetsTheLastIntent() = runBlocking {
        vm.setMode(TranslateMode.CORRECT); vm.setIntent("개가 있어")
        vm.setInput("I have dog"); vm.submitAttempt().join()
        vm.setInput("I has dog"); vm.submitAttempt().join()
        vm.setIntent("배고파")
        vm.setInput("I hungry"); vm.submitAttempt().join()
        assertEquals(1, ai.lastCoach!!.hintLevel)
        vm.reveal(); vm.saveAttempt().join()
        assertEquals("", vm.state.value.intent)
        assertNull(vm.state.value.replyTo)
    }

    @Test fun aReplyIsWrittenInTheFriendsLanguage() = runBlocking {
        vm.startReply("¿Vienes mañana?", Lang.ES)
        assertEquals(Lang.ES, vm.state.value.targetLang)
    }

    @Test fun copyingAnAlreadySavedReplyDoesNotCountItsGrammarAgain() = runBlocking {
        ai.sent = { _, _ -> Result.success(listOf(SentCandidate("Ojalá vengas", "n", grammar = listOf("es.subjunctive_present")))) }
        vm.setMode(TranslateMode.SENT); vm.setTarget(Lang.ES)
        repeat(2) { vm.setInput("너 오면 좋겠다"); vm.translate().join(); vm.onCandidateCopied(vm.state.value.candidates.single()).join() }
        assertEquals(1, db.dao().allGrammarEvents().size)
    }
}
