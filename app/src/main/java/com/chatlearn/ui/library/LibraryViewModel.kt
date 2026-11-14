package com.chatlearn.ui.library

import com.chatlearn.stats.progressOf
import com.chatlearn.stats.EntryProgress
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.LibraryItem
import com.chatlearn.data.RenameResult
import com.chatlearn.data.StatsInput
import com.chatlearn.expansion.ExpansionReport
import com.chatlearn.expansion.ExpansionService
import com.chatlearn.expansion.KeyPhraseReport
import com.chatlearn.expansion.KeyPhraseService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import com.chatlearn.domain.Lang
import com.chatlearn.domain.TextOrigin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 폴더 필터에서 "폴더 없음"을 뜻하는 값. */
const val FOLDER_UNFILED = -1L

fun filterLibrary(items: List<LibraryItem>, query: String, lang: Lang?, tag: String?, folder: Long? = null): List<LibraryItem> {
    val q = query.trim()
    return items.filter { item ->
        (folder == null || (if (folder == FOLDER_UNFILED) item.entry.folderId == null else item.entry.folderId == folder)) &&
            (lang == null || item.entry.chatLang == lang) &&
            (tag == null || tag in item.tags) &&
            (q.isEmpty() || item.texts.values.any { it.contains(q, ignoreCase = true) } || item.tags.any { it.contains(q, ignoreCase = true) })
    }
}

class LibraryViewModel(
    private val repo: EntryRepository,
    private val expansion: ExpansionService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val undoWindowMs: Long = 5_000,
    private val keyPhraseService: KeyPhraseService? = null,
    private val notion: com.chatlearn.notion.NotionExporter? = null,
) : ViewModel() {
    val query = MutableStateFlow("")
    val langFilter = MutableStateFlow<Lang?>(null)
    val tagFilter = MutableStateFlow<String?>(null)
    /** null=전체, [FOLDER_UNFILED]=폴더 없는 문장, 그 외=그 폴더. */
    val folderFilter = MutableStateFlow<Long?>(null)

    val sort = MutableStateFlow(LibrarySort.RECENT)
    val statusFilter = MutableStateFlow(StatusFilter.ALL)

    // 라이브러리와 통계 재료를 한 번씩만 구독해 아래 여러 상태가 나눠 쓴다. 따로 구독하면 바뀔 때마다 같은 전체 조회가 여러 번 돈다.
    // 복습 기록만 바뀔 때는 라이브러리 목록을 다시 만들지 않는다.
    private val all = repo.observeLibrary().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    private val source = repo.observeStatsInput(all).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    private val _pendingDelete = MutableStateFlow<Long?>(null)
    /** 삭제를 눌렀지만 되돌릴 수 있는 문장. 목록에서는 이미 숨긴다. */
    val pendingDelete: StateFlow<Long?> = _pendingDelete.asStateFlow()

    /** 문장별 복습 상태(새 카드, 복습할 차례, N일 후)와 틀린 횟수. */
    val progress: StateFlow<Map<Long, EntryProgress>> = source
        .map { si ->
            val now = clock()
            val misses = recentMissIds(si.logs, si.cards, now - RECENT_MISS_MS)
            si.cards.groupBy { it.entryId }.mapValues { (id, cards) -> progressOf(cards, now).copy(recentMiss = id in misses) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private data class Filters(val q: String, val lang: Lang?, val tag: String?, val folder: Long?)
    private val filters = combine(query, langFilter, tagFilter, folderFilter) { q, lang, tag, folder -> Filters(q, lang, tag, folder) }
    private val searched = combine(all, filters, _pendingDelete) { l, f, pending ->
        filterLibrary(l.filter { it.entry.id != pending }, f.q, f.lang, f.tag, f.folder)
    }

    val folders: StateFlow<List<com.chatlearn.data.FolderEntity>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 폴더별 문장 수. 키가 [FOLDER_UNFILED]이면 폴더 없는 문장 수. 삭제 대기 중인 문장은 센다(되돌릴 수 있으므로). */
    val folderCounts: StateFlow<Map<Long, Int>> = all
        .map { l -> l.groupingBy { it.entry.folderId ?: FOLDER_UNFILED }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val items: StateFlow<List<LibraryItem>> =
        combine(searched, statusFilter, sort, progress) { l, sf, so, pr -> sortLibrary(filterByStatus(l, pr, sf, clock()), pr, so) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** 상태 칩에 붙일 문장 수(검색·폴더·언어·태그 조건 안에서). */
    val statusCounts: StateFlow<Map<StatusFilter, Int>> =
        combine(searched, progress) { l, pr -> statusCounts(l, pr, clock()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    /** 친구(사람 태그) 이름. 필터의 친구 줄에 쓴다. */
    val people: StateFlow<List<String>> =
        all.map { list -> list.flatMap { it.tagsOf(com.chatlearn.domain.TagKind.PERSON) }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTags: StateFlow<List<String>> =
        all.map { list -> list.flatMap { it.tags }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 문장별 통계(복습 횟수, 기억 비율 등)를 계산할 재료. 상세 창에서 쓴다. */
    val statsInput: StateFlow<StatsInput?> =
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 반대 언어 카드까지 가진(=확장된) 문장의 id. 텍스트만 있고 카드가 없는 문장은 포함되지 않는다. */
    val expandedIds: StateFlow<Set<Long>> = source
        .map { si -> si.cards.groupingBy { it.entryId }.eachCount().filterValues { it >= 2 }.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** 반대 언어 카드가 아직 없는 문장 수. "전체 확장" 버튼 표시에 쓴다. */
    val unexpanded: StateFlow<Int> = combine(all, expandedIds) { items, ids -> items.count { it.entry.id !in ids } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var expandRun: Deferred<ExpansionReport>? = null
    private val _expanding = MutableStateFlow(false)
    /** 전체 확장이 진행 중인가. 화면을 벗어났다 돌아와도 유지된다. */
    val expanding: StateFlow<Boolean> = _expanding.asStateFlow()

    /** 이미 실행 중이면 그 실행을 그대로 돌려줘서 같은 번역 요청이 중복되지 않게 한다. 예외는 실패한 묶음 1개로 보고한다. */
    fun expandAll(): Deferred<ExpansionReport> {
        expandRun?.takeIf { it.isActive }?.let { return it }
        val run = viewModelScope.async {
            _expanding.value = true
            try {
                runCatching { expansion.expandAll() }.getOrElse { ExpansionReport(created = 0, skipped = 0, failedBatches = 1) }
            } finally {
                _expanding.value = false
            }
        }
        expandRun = run
        return run
    }
    /** 핵심 표현이 아직 없는 외국어 문장 수. "핵심 표현 만들기" 버튼 표시에 쓴다. */
    val missingKeyPhrases: StateFlow<Int> = repo.observeMissingKeyPhrases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var keyPhraseRun: Deferred<KeyPhraseReport>? = null
    private val _fillingKeyPhrases = MutableStateFlow(false)
    val fillingKeyPhrases: StateFlow<Boolean> = _fillingKeyPhrases.asStateFlow()

    /** 이미 실행 중이면 그 실행을 돌려준다. 예외는 실패한 묶음 1개로 보고한다. */
    fun fillKeyPhrases(): Deferred<KeyPhraseReport> {
        keyPhraseRun?.takeIf { it.isActive }?.let { return it }
        val run = viewModelScope.async {
            _fillingKeyPhrases.value = true
            try {
                runCatching { keyPhraseService?.fillAll() ?: KeyPhraseReport(0, 0, 0) }.getOrElse { KeyPhraseReport(0, 0, 1) }
            } finally {
                _fillingKeyPhrases.value = false
            }
        }
        keyPhraseRun = run
        return run
    }

    fun saveKeyPhrase(id: Long, lang: Lang, text: String) = viewModelScope.launch { repo.setKeyPhrase(id, lang, text) }

    fun expandOne(id: Long) = viewModelScope.async { expansion.expandOne(id) }

    /** 태그 자동완성 후보(최근 사용 순). */
    val recentTags: StateFlow<List<String>> =
        repo.observeRecentTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 필터로 선택해 둔 태그가 사라지면(마지막 문장에서 제거, 이름 변경 등) 필터를 풀어서 빈 목록에 갇히지 않게 한다.
        viewModelScope.launch { all.collect { l -> dropStaleTagFilter(l.flatMap { it.tags }) } }
        // 보고 있던 폴더가 지워지면 전체로 돌아간다.
        viewModelScope.launch {
            repo.observeFolders().collect { fs ->
                val cur = folderFilter.value
                if (cur != null && cur != FOLDER_UNFILED && fs.none { it.id == cur }) folderFilter.value = null
            }
        }
    }

    private val _sendingToNotion = MutableStateFlow(false)
    val sendingToNotion: StateFlow<Boolean> = _sendingToNotion.asStateFlow()

    private val _notionReport = MutableStateFlow<com.chatlearn.notion.NotionReport?>(null)
    /** 마지막 노션 보내기 결과. 화면이 안내로 보여 준 뒤 [notionReported]로 비운다. */
    val notionReport: StateFlow<com.chatlearn.notion.NotionReport?> = _notionReport.asStateFlow()
    fun notionReported() { _notionReport.value = null }

    /** 고른 문장을 노션에 보낸다. 이미 보낸 문장은 같은 페이지를 고친다. */
    fun sendToNotion(ids: Collection<Long>): Job = viewModelScope.launch {
        val n = notion ?: return@launch
        if (_sendingToNotion.value || ids.isEmpty()) return@launch
        _sendingToNotion.value = true
        try { _notionReport.value = n.export(ids) } finally { _sendingToNotion.value = false }
    }

    /** 상세에서 "답장 연습"을 누른 친구의 말. 앱이 써 보기로 옮겨 가며 비운다. */
    val replyRequest = MutableStateFlow<Pair<String, Lang>?>(null)

    /** 다른 화면에서 문장 하나를 보여 달라고 할 때: 걸려 있던 필터를 풀고 그 글자로 찾는다. */
    fun showOnly(text: String) {
        langFilter.value = null; tagFilter.value = null; folderFilter.value = null; statusFilter.value = StatusFilter.ALL
        query.value = text
    }

    /** 다른 화면(성장)에서 문장 하나를 열어 달라고 할 때. 필터를 풀어 목록에 보이게 하고, 화면이 상세를 연 뒤 비운다. */
    val openRequest = MutableStateFlow<Long?>(null)
    fun open(id: Long) {
        showOnly("")
        openRequest.value = id
    }

    internal fun dropStaleTagFilter(existing: List<String>) {
        val current = tagFilter.value
        if (current != null && current !in existing) tagFilter.value = null
    }

    fun saveTags(id: Long, tags: List<String>) = viewModelScope.launch { repo.setTags(id, tags, clock()) }

    fun renameTag(old: String, new: String) = viewModelScope.async {
        repo.renameTag(old, new).also { r ->
            if (r != RenameResult.Unchanged && tagFilter.value == old) tagFilter.value = new.trim()
        }
    }

    private var deleteJob: Job? = null

    /** 바로 지우지 않고 [undoWindowMs] 동안 숨겨 둔다. 그 안에 [undoDelete]하면 그대로 남는다. */
    fun delete(id: Long): Job {
        val previous = _pendingDelete.value
        deleteJob?.cancel()
        _pendingDelete.value = id
        val job = viewModelScope.launch {
            if (previous != null) repo.deleteEntry(previous) // 앞선 삭제는 되돌리기 없이 확정한다
            delay(undoWindowMs)
            repo.deleteEntry(id)
            if (_pendingDelete.value == id) _pendingDelete.value = null
        }
        deleteJob = job
        return job
    }

    fun undoDelete() {
        deleteJob?.cancel()
        _pendingDelete.value = null
    }

    fun saveNote(id: Long, note: String) = viewModelScope.launch { repo.updateNote(id, note) }
    fun saveText(id: Long, lang: Lang, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) repo.putText(id, lang, text, TextOrigin.USER)
    }

    /** 폴더를 만들어(이미 있으면 그대로 써서) id를 돌려준다. 이름이 비면 null. */
    fun createFolder(name: String) = viewModelScope.async { repo.createFolder(name, clock()) }
    fun renameFolder(id: Long, name: String) = viewModelScope.async { repo.renameFolder(id, name) }
    fun deleteFolder(id: Long) = viewModelScope.launch { repo.deleteFolder(id) }

    /** [folderId]가 null이면 폴더에서 꺼낸다. */
    fun moveToFolder(ids: Collection<Long>, folderId: Long?) = viewModelScope.launch { repo.moveToFolder(ids, folderId) }

    /** 여러 문장을 한 번에 지운다. 되돌리기는 없으니 호출 전에 확인을 받는다. */
    fun deleteMany(ids: Collection<Long>) = viewModelScope.launch { repo.deleteEntries(ids) }

    /** 알게 된 날(저장일)을 고친다. */
    fun saveLearnedAt(id: Long, at: Long) = viewModelScope.launch { repo.setLearnedAt(id, at) }
}
