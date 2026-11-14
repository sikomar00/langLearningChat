package com.chatlearn.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import com.chatlearn.domain.normalizeKeyPhrase
import com.chatlearn.domain.ChatLine
import com.chatlearn.domain.EntryKind
import com.chatlearn.domain.GrammarPoint
import com.chatlearn.domain.GrammarSource
import com.chatlearn.domain.contextAround
import com.chatlearn.domain.guessDraftLang
import com.chatlearn.domain.looksKorean
import com.chatlearn.domain.speakersOf
import com.chatlearn.domain.parseChat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 받은 말(번역), 보낼 말(한국어 → 후보), 써 보기(CORRECT: 내가 써 보고 힌트를 받으며 고치기). */
enum class TranslateMode { RECEIVED, SENT, CORRECT }

sealed interface TranslateError {
    data object UnsupportedLang : TranslateError
    data class Ai(val kind: AiError) : TranslateError
}

sealed interface LookupState {
    val word: String
    data class Loading(override val word: String) : LookupState
    data class Done(override val word: String, val info: WordInfo) : LookupState
    data class Failed(override val word: String, val kind: AiError) : LookupState
}

sealed interface Notice {
    data class Saved(val cards: Int) : Notice
    /** 이미 저장된 문장. [text]로 라이브러리에서 찾아 보여 준다. */
    data class Duplicate(val text: String) : Notice
    data class BatchSaved(val saved: Int, val duplicates: Int) : Notice
    data class WordSaved(val word: String) : Notice
}

data class TranslateUiState(
    val mode: TranslateMode = TranslateMode.RECEIVED,
    val input: String = "",
    val source: String = "", // 번역을 요청한 시점의 입력. 저장은 이 값으로 한다.
    val tag: String = "",
    val targetLang: Lang = Lang.EN,
    val loading: Boolean = false,
    val received: ReceivedResult? = null,
    val candidates: List<SentCandidate> = emptyList(),
    val error: TranslateError? = null,
    val notice: Notice? = null,
    val keyPhrase: String = "", // 받은 말의 핵심 표현(저장 전에 고칠 수 있다)
    val lookup: LookupState? = null, // 눌러 본 단어의 뜻
    val batch: List<BatchItem> = emptyList(), // 여러 줄을 붙여넣었을 때 줄별 번역 결과
    val learnedOn: java.time.LocalDate? = null, // 이 표현을 알게 된 날. null이면 오늘(저장하는 날)
    // 써 보기: 하고 싶은 말(한국어), 지금까지의 시도와 피드백, 정답을 열어 봤는지, 답장할 친구의 말
    val intent: String = "",
    val attempts: List<Attempt> = emptyList(),
    val revealed: Boolean = false,
    val replyTo: String? = null,
    // 저장할 때 붙일 사람·나라. 다음 문장도 같은 친구일 때가 많아 저장해도 남겨 둔다.
    val people: Set<String> = emptySet(),
    val countries: Set<String> = emptySet(),
    val truncated: Boolean = false, // 붙여넣은 대화가 길어 뒤를 잘랐다
    val speakers: List<String> = emptyList(), // 붙여넣은 대화에서 알아낸 사람(칩 후보로 먼저 보인다)
)

/** 써 보기 한 번: 내가 쓴 문장과 받은 피드백. */
data class Attempt(val text: String, val feedback: AttemptFeedback)

/** 붙여넣은 대화의 한 줄. [result]가 null이거나 지원하지 않는 언어면 저장할 수 없다. [context]는 앞뒤 대화. */
data class BatchItem(
    val text: String, val result: ReceivedResult?, val selected: Boolean, val keyPhrase: String,
    val speaker: String? = null, val context: String? = null,
) {
    val savable: Boolean get() = result?.detectedLang != null
}

private const val BATCH_CHUNK = 10

class TranslateViewModel(
    private val ai: AiService,
    private val repo: EntryRepository,
    private val clock: () -> Long,
    private val useSaved: suspend () -> Boolean = { true },
    private val autoExpand: suspend () -> Boolean = { true },
    private val savedTarget: suspend () -> Lang = { Lang.EN },
    private val rememberTarget: suspend (Lang) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    // 보낼 말 언어는 지난번 고른 것으로 시작한다(스페인어 친구가 많으면 매번 바꾸지 않게).
    // 읽기 전에 사용자가 이미 골랐으면 그 선택을 지킨다.
    private var targetChosen = false
    private val targetLoad: Job = viewModelScope.launch {
        val t = savedTarget()
        if (!targetChosen) _state.update { it.copy(targetLang = t) }
    }
    suspend fun awaitTarget() = targetLoad.join()
    // 대화에서 자동으로 골라 둔 친구. 다음 대화를 붙여 넣으면 이 사람만 빼고 새로 고른다(직접 고른 사람은 남긴다).
    private var autoPicked: String? = null

    fun togglePerson(name: String) = _state.update { it.copy(people = it.people.toggle(name.trim())) }
    fun toggleCountry(name: String) = _state.update { it.copy(countries = it.countries.toggle(name.trim())) }
    private fun Set<String>.toggle(x: String) = if (x.isEmpty()) this else if (x in this) this - x else this + x

    /** 저장 칩 후보: 최근에 쓴 사람·나라. */
    val recentPeople: StateFlow<List<String>> = repo.observeRecentTags(com.chatlearn.domain.TagKind.PERSON)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentCountries: StateFlow<List<String>> = repo.observeRecentTags(com.chatlearn.domain.TagKind.COUNTRY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun draftTags(d: EntryDraft, s: TranslateUiState) = d.copy(people = s.people.toList(), countries = s.countries.toList())

    fun setMode(m: TranslateMode) = _state.update {
        it.copy(mode = m, replyTo = null, received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, error = null, notice = null)
    }
    fun setInput(s: String) = _state.update { it.copy(input = s) }

    /** 알게 된 날을 고른다. null이면 오늘로 되돌린다. 저장해도 유지돼서 지난 대화를 몰아서 저장할 때 편하다. */
    fun setLearnedOn(date: java.time.LocalDate?) = _state.update { it.copy(learnedOn = date) }

    private fun learnedAt(): Long? {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        return learnedAtMillis(_state.value.learnedOn, today, zone)
    }

    /** 입력과 결과, 안내 문구를 모두 비운다. 태그와 모드는 그대로 둔다. */
    fun clearInput() = _state.update {
        it.copy(input = "", source = "", received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, error = null, notice = null)
    }

    /** 붙여넣기: 입력을 통째로 바꾸고, 이전 입력의 결과는 지운다. */
    fun replaceInput(text: String) = _state.update {
        it.copy(input = text.trim(), source = "", received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, error = null, notice = null)
    }
    /** 붙여넣기 버튼: 입력을 클립보드 내용으로 바꾸고 지금 모드로 바로 번역(첨삭)한다. */
    fun pasteAndTranslate(text: String): Job {
        replaceInput(text)
        return translate()
    }

    /**
     * 다른 앱에서 공유한 글: 한국어면 보낼 말로, 아니면 받은 말로 바로 번역한다.
     * [editable]이면(바꿔 넣을 칸에서 고른 글) 외국어는 친구 말이 아니라 내 초안이라 써 보기로 다듬는다.
     */
    fun receiveShared(text: String, editable: Boolean = false): Job {
        if (editable && !looksKorean(text) && text.any { it in 'a'..'z' || it in 'A'..'Z' }) return polishDraft(text)
        val mode = if (looksKorean(text)) TranslateMode.SENT else TranslateMode.RECEIVED
        _state.update {
            it.copy(mode = mode, input = text.trim(), source = "", received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, error = null, notice = null)
        }
        return translate()
    }
    // 내 외국어 초안을 써 보기에 넣어 둔다. 언어는 글에서 어림하고(못 하면 지난번 언어), 확인을 누르면 힌트를 받는다.
    private fun polishDraft(text: String): Job = viewModelScope.launch {
        targetLoad.join()
        val lang = guessDraftLang(text)
        if (lang != null) targetChosen = true // 이번 초안에만: 저장해 둔 보낼 말 언어는 바꾸지 않는다
        _state.update {
            it.copy(
                mode = TranslateMode.CORRECT, input = text.trim(), intent = "", replyTo = null, targetLang = lang ?: it.targetLang, source = "",
                received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, error = null, notice = null,
            )
        }
    }

    /** 받은 말의 단어를 눌렀을 때: 문장 맥락에서의 뜻을 AI에 묻는다. */
    fun lookupWord(chunk: String): Job = viewModelScope.launch {
        val word = cleanWord(chunk) ?: return@launch
        val s = _state.value
        val lang = s.received?.detectedLang ?: return@launch
        if ((s.lookup as? LookupState.Loading)?.word == word) return@launch
        _state.update { it.copy(lookup = LookupState.Loading(word)) }
        val result = ai.lookupWord(word, s.source, lang)
        // 그 사이 다른 번역·저장으로 화면이 바뀌었으면 결과를 버린다.
        if (_state.value.source != s.source) return@launch
        _state.update {
            it.copy(lookup = result.fold(
                onSuccess = { info -> LookupState.Done(word, info) },
                onFailure = { e -> LookupState.Failed(word, (e as? AiException)?.kind ?: AiError.SERVER) },
            ))
        }
    }

    fun dismissLookup() = _state.update { it.copy(lookup = null) }
    fun useAsKeyPhrase(word: String) = _state.update { it.copy(keyPhrase = word) }
    fun setKeyPhrase(s: String) = _state.update { it.copy(keyPhrase = s) }
    fun setTag(s: String) = _state.update { it.copy(tag = s) }
    fun pickTag(tag: String) = _state.update { it.copy(tag = applyTagSuggestion(it.tag, tag)) }

    /** 입력창 포커스 시 보여 줄 자동완성 후보(최근 사용 순, 입력한 글자로 걸러짐). */
    val tagSuggestions: StateFlow<List<String>> =
        combine(repo.observeRecentTags(), _state.map { it.tag }.distinctUntilChanged()) { recent, typed ->
            suggestTags(recent, typed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setTarget(l: Lang) {
        targetChosen = true
        // 언어가 바뀌면 앞서 받은 후보·힌트는 다른 언어 것이라 비운다.
        _state.update { it.copy(targetLang = l, candidates = emptyList(), attempts = emptyList(), revealed = false) }
        viewModelScope.launch { rememberTarget(l) }
    }

    fun translate(): Job = viewModelScope.launch {
        // 써 보기는 시도를 쌓아 가며 힌트를 키운다(결과를 비우지 않는다).
        if (_state.value.mode == TranslateMode.CORRECT) { submitAttempt().join(); return@launch }
        targetLoad.join() // 첫 번역도 저장해 둔 보낼 말 언어로
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.loading) return@launch
        _state.update { it.copy(loading = true, source = text, error = null, notice = null, received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, truncated = false) }
        val parsed = if (s.mode == TranslateMode.RECEIVED) parseChat(text) else null
        val lines = parsed?.lines.orEmpty()
        // 대화에서 친구가 한 명으로 정해지면 사람 칩에 미리 골라 둔다. 여럿이면(내 이름이 섞였을 수 있다) 칩 후보로만.
        if (parsed != null) {
            val speakers = speakersOf(lines)
            val friend = speakers.singleOrNull()
            _state.update { st ->
                val kept = autoPicked?.let { st.people - it } ?: st.people
                st.copy(speakers = speakers, people = friend?.let { kept + it } ?: kept)
            }
            autoPicked = friend
        }
        if (lines.size >= 2) {
            _state.update { it.copy(truncated = parsed!!.truncated) }
            translateBatch(lines)
        } else if (s.mode == TranslateMode.RECEIVED) {
            // 한 줄이라도 "[이름] [시각] 말" 형식이면 이름을 뗀 말만 번역·저장한다.
            val single = lines.singleOrNull()?.text ?: text
            if (single != text) _state.update { it.copy(source = single) }
            ai.translateReceived(single).fold(
                onSuccess = { r ->
                    if (r.detectedLang == null) _state.update { it.copy(loading = false, error = TranslateError.UnsupportedLang) }
                    else _state.update { it.copy(loading = false, received = r, keyPhrase = normalizeKeyPhrase(single, r.keyPhrase).orEmpty()) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
            )
        } else {
            ai.suggestSent(text, s.targetLang, if (useSaved()) repo.knownExpressions(s.targetLang) else emptyList()).fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, candidates = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
            )
        }
    }

    // 10줄씩 묶어 요청한다. 한 묶음이라도 실패하면 전체를 실패로 보고 입력은 그대로 둔다.
    private suspend fun translateBatch(lines: List<ChatLine>) {
        val results = mutableListOf<ReceivedResult>()
        for (chunk in lines.map { it.text }.chunked(BATCH_CHUNK)) {
            val r = ai.translateReceivedBatch(chunk)
            if (r.isFailure) {
                _state.update { it.copy(loading = false, error = r.exceptionOrNull()!!.toUiError()) }
                return
            }
            results += r.getOrThrow()
        }
        val items = lines.zip(results).mapIndexed { i, (line, r) ->
            BatchItem(
                line.text, r, selected = r.detectedLang != null, keyPhrase = normalizeKeyPhrase(line.text, r.keyPhrase).orEmpty(),
                speaker = line.speaker, context = contextAround(lines, i),
            )
        }
        _state.update { it.copy(loading = false, batch = items) }
    }

    fun toggleBatch(index: Int) = _state.update { st ->
        st.copy(batch = st.batch.mapIndexed { i, b -> if (i == index && b.savable) b.copy(selected = !b.selected) else b })
    }

    /** 고른 줄을 모두 저장한다. 이미 있는 문장은 건너뛰고 개수만 알려 준다. */
    fun saveBatch(): Job = viewModelScope.launch {
        val s = _state.value
        val expand = autoExpand()
        var saved = 0; var dup = 0
        s.batch.filter { it.selected && it.savable }.forEach { b ->
            val r = b.result!!
            val draft = EntryDraft(
                Direction.RECEIVED, r.detectedLang!!, b.text, r.koTranslation, r.note, null, parseTags(s.tag), r.altText,
                keyPhrase = b.keyPhrase.ifBlank { null }, altKeyPhrase = r.altKeyPhrase, learnedAt = learnedAt(), contextText = b.context, grammar = r.grammar,
            )
            if (repo.saveEntry(draftTags(draft, s), clock(), expand) is SaveResult.Saved) saved++ else dup++
        }
        _state.update {
            it.copy(input = "", source = "", received = null, lookup = null, batch = emptyList(), candidates = emptyList(),
                error = null, keyPhrase = "", notice = Notice.BatchSaved(saved, dup))
        }
    }

    fun saveReceived(): Job = viewModelScope.launch {
        val s = _state.value
        val r = s.received ?: return@launch
        val lang = r.detectedLang ?: return@launch
        val draft = EntryDraft(Direction.RECEIVED, lang, s.source, r.koTranslation, r.note, null, parseTags(s.tag), r.altText,
            keyPhrase = s.keyPhrase.ifBlank { null }, altKeyPhrase = r.altKeyPhrase, learnedAt = learnedAt(), grammar = r.grammar)
        report(repo.saveEntry(draftTags(draft, s), clock(), autoExpand()), s.source)
    }

    fun onCandidateCopied(c: SentCandidate): Job = viewModelScope.launch {
        val s = _state.value
        val draft = EntryDraft(Direction.SENT, s.targetLang, c.text, s.source, c.toneNote, null, parseTags(s.tag), c.altText,
            keyPhrase = c.keyPhrase, altKeyPhrase = c.altKeyPhrase, learnedAt = learnedAt(), grammar = c.grammar)
        val r = repo.saveEntry(draftTags(draft, s), clock(), autoExpand())
        // 보내려고 고른 후보의 문법 = 내가 말하려면 필요했던 문법. 이미 저장된 후보를 다시 복사하면 세지 않는다.
        if (r is SaveResult.Saved) {
            repo.recordGrammar(c.grammar.map { GrammarEventEntity(lang = s.targetLang, pointKey = it, source = GrammarSource.REQUEST, isError = false, entryId = r.id, at = clock()) })
        }
        report(r, c.text)
    }

    // ---- 써 보기 ----

    // 하고 싶은 말이 바뀌면 새 문장: 시도와 힌트 단계를 처음부터.
    fun setIntent(t: String) = _state.update { if (t == it.intent) it else it.copy(intent = t, attempts = emptyList(), revealed = false) }

    /** 내 시도를 보내 피드백을 받는다. 틀릴수록 힌트가 한 단계씩 구체적이 된다(최대 3). */
    fun submitAttempt(): Job = viewModelScope.launch {
        targetLoad.join()
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.loading) return@launch
        // 마지막으로 맞힌 뒤로 틀린 횟수만큼 힌트를 키운다(앞 문장의 실수는 세지 않는다).
        val level = (s.attempts.takeLastWhile { !it.feedback.correct }.size + 1).coerceAtMost(3)
        _state.update { it.copy(loading = true, error = null, notice = null, revealed = false) }
        ai.coachAttempt(AttemptRequest(s.targetLang, text, s.intent.trim(), s.replyTo, level)).fold(
            onSuccess = { f ->
                _state.update { it.copy(loading = false, attempts = it.attempts + Attempt(text, f)) }
                repo.recordGrammar(f.grammarErrors.map {
                    GrammarEventEntity(lang = s.targetLang, pointKey = it, source = GrammarSource.ATTEMPT, isError = true, entryId = null, at = clock())
                })
            },
            onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
        )
    }

    /** 정답 보기: 마지막 피드백의 자연스러운 문장과 설명을 연다. */
    fun reveal() = _state.update { it.copy(revealed = true) }

    /** 맞혔으면 내가 쓴 문장을, 정답을 열었으면 그 문장을 보낸 말로 저장한다. */
    fun saveAttempt(): Job = viewModelScope.launch {
        val s = _state.value
        val last = s.attempts.lastOrNull() ?: return@launch
        val f = last.feedback
        if (!f.correct && !s.revealed) return@launch
        val text = if (f.correct) last.text else f.answer
        // 맞힌 문장이 정답과 다를 수 있어, 다른 언어 문장·설명은 정답을 저장할 때만 함께 둔다.
        val draft = EntryDraft(
            Direction.SENT, s.targetLang, text, s.intent.trim().ifEmpty { f.koMeaning }, if (f.correct) "" else f.explanationKo, null, parseTags(s.tag),
            if (f.correct) null else f.altText.ifBlank { null },
            keyPhrase = normalizeKeyPhrase(text, f.keyPhrase), learnedAt = learnedAt(), contextText = s.replyTo,
        )
        report(repo.saveEntry(draftTags(draft, s), clock(), autoExpand()), text)
    }

    /** 받은 문장에 답장을 써 본다: 그 문장을 대화 맥락으로 두고 써 보기를 연다. */
    fun startReply(friendMessage: String, lang: Lang) {
        setMode(TranslateMode.CORRECT)
        targetChosen = true
        _state.update { it.copy(replyTo = friendMessage, intent = "", input = "", targetLang = lang) }
    }

    /** 약한 문법 하나를 골라 연습한다: 그 문법이 필요한 한국어 문장을 받아 써 보기를 연다. */
    fun startPractice(point: GrammarPoint): Job = viewModelScope.launch {
        setMode(TranslateMode.CORRECT)
        targetChosen = true
        _state.update { it.copy(targetLang = point.lang, input = "", intent = "", loading = true) }
        ai.practicePrompt(point).fold(
            onSuccess = { ko -> _state.update { it.copy(loading = false, intent = ko) } },
            onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
        )
    }

    /** 눌러 본 단어를 단어 카드로 저장한다. 그 단어가 나온 문장을 맥락으로 둔다. 다른 언어 카드는 만들지 않는다. */
    fun saveWord(): Job = viewModelScope.launch {
        val s = _state.value
        val l = s.lookup as? LookupState.Done ?: return@launch
        val lang = s.received?.detectedLang ?: return@launch
        val word = l.info.baseForm.ifBlank { l.word }
        val draft = EntryDraft(
            Direction.RECEIVED, lang, word, l.info.meaningKo, l.info.note, null, parseTags(s.tag),
            learnedAt = learnedAt(), contextText = s.source, kind = EntryKind.WORD,
        )
        val r = repo.saveEntry(draftTags(draft, s), clock(), expand = false)
        _state.update { it.copy(lookup = null, notice = if (r is SaveResult.Saved) Notice.WordSaved(word) else Notice.Duplicate(word)) }
    }

    // 저장에 성공하면 다음 표현을 바로 입력할 수 있게 입력과 결과를 비운다. 중복이면 그대로 둔다.
    private suspend fun report(r: SaveResult, text: String) {
        val cards = if (r is SaveResult.Saved) repo.cardCount(r.id) else 0
        _state.update {
            if (r is SaveResult.Saved) {
                it.copy(input = "", source = "", received = null, lookup = null, batch = emptyList(), candidates = emptyList(), attempts = emptyList(), revealed = false, error = null, notice = Notice.Saved(cards), keyPhrase = "", intent = "", replyTo = null)
            } else {
                it.copy(notice = Notice.Duplicate(text.trim()))
            }
        }
    }

    private fun Throwable.toUiError() = TranslateError.Ai((this as? AiException)?.kind ?: AiError.SERVER)
}
