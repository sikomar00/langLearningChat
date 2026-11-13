package com.chatlearn.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.DueCard
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.review.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewUi {
    data object Loading : ReviewUi
    data object Empty : ReviewUi
    data class Prompt(
        val item: ReviewItem,
        val answers: List<String>,
        val shownAt: Long,
        val checking: Boolean = false,
    ) : ReviewUi {
        val answer: String get() = answers.joinToString(" ").trim()
    }
    data class Graded(
        val item: ReviewItem,
        val outcome: GradeOutcome,
        val answer: String,
        val suggested: Rating,
        val responseMs: Long,
        /** 평가마다 다음 복습까지의 시간("3일"). */
        val previews: Map<Rating, String> = emptyMap(),
    ) : ReviewUi
    /** [count]: 평가한 횟수(다시 푼 것 포함), [cards]: 복습한 카드 수. */
    data class Done(val count: Int, val cards: Int = count) : ReviewUi
}

/** [done]개를 끝냈고 이번 세션에서 낼 카드가 [total]장(다시 평가한 카드 포함). */
data class ReviewProgress(val done: Int, val total: Int)

class ReviewViewModel(private val session: ReviewSession) : ViewModel() {
    private val _state = MutableStateFlow<ReviewUi>(ReviewUi.Loading)
    val state: StateFlow<ReviewUi> = _state.asStateFlow()

    /** 라이브러리에서 고른 복습이면 그 카드 수, 평소 복습이면 null. */
    private val _custom = MutableStateFlow<Int?>(null)
    val custom: StateFlow<Int?> = _custom.asStateFlow()

    private val _progress = MutableStateFlow(ReviewProgress(0, 0))
    val progress: StateFlow<ReviewProgress> = _progress.asStateFlow()

    /** 방금 한 평가를 취소할 수 있는가. */
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private var lastGraded: ReviewUi.Graded? = null
    private var lastRating: Rating? = null

    private val _goal = MutableStateFlow(GoalProgress(0, 0))
    /** 오늘 복습한 카드 수와 하루 목표. */
    val goal: StateFlow<GoalProgress> = _goal.asStateFlow()
    private suspend fun refreshGoal() { _goal.value = session.todayProgress() }

    // 연속일은 복습 기록 전체를 읽으므로 세션을 열 때와 오늘 첫 복습·취소 때만 다시 센다.
    private suspend fun refreshStreak() { _streak.value = session.streak() }

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _combo = MutableStateFlow(0)
    /** 이번 세션에서 이어서 기억한 카드 수. "다시"를 누르면 0으로. */
    val combo: StateFlow<Int> = _combo.asStateFlow()

    private val _remembered = MutableStateFlow(0)
    /** 이번 세션에서 "다시"가 아닌 평가를 한 횟수(요약의 기억률). */
    val remembered: StateFlow<Int> = _remembered.asStateFlow()

    private val _celebrate = MutableStateFlow(false)
    /** 이번 세션에서 오늘 목표를 막 달성했다. 화면이 축하를 보여 준 뒤 [celebrated]로 끈다. */
    val celebrate: StateFlow<Boolean> = _celebrate.asStateFlow()
    fun celebrated() { _celebrate.value = false }

    // 같은 세션에서 평가 취소 뒤 다시 평가해도 축하는 한 번만.
    private var celebratedThisSession = false
    private var comboBeforeLast = 0

    private fun resetSessionCounters() { _combo.value = 0; _remembered.value = 0; _celebrate.value = false; celebratedThisSession = false }

    private val _moreNew = MutableStateFlow(0)
    /** 다 끝냈거나 비었을 때, 오늘 한도 밖에서 더 배울 수 있는 새 카드 수. */
    val moreNew: StateFlow<Int> = _moreNew.asStateFlow()
    private suspend fun refreshMoreNew() { _moreNew.value = session.newCardsWaiting() }

    private val queue = ArrayDeque<DueCard>()
    private var reviewed = 0
    /** 이번 세션에서 평가한 카드 id(평가 순서대로, 다시 푼 카드는 여러 번). */
    private val ratedCards = mutableListOf<Long>()
    private var started = false
    private var rating = false

    /**
     * 오늘 화면에서 "복습 시작": 오늘 복습을 하던 중이면 그대로 이어 가고, 아니면 바로 시작한다.
     * 라이브러리에서 고른 복습이 남아 있으면 버리고 오늘 복습을 연다(버튼이 오늘 카드 수를 보여 주므로).
     */
    fun open(): Job {
        val s = _state.value
        if (started && _custom.value == null && (s is ReviewUi.Prompt || s is ReviewUi.Graded || s is ReviewUi.Loading)) return Job().apply { complete() }
        return start()
    }

    /** 복습 화면이 열렸는데 아직 아무것도 시작하지 않았으면(프로세스가 다시 만들어진 경우) 오늘 복습을 연다. */
    fun ensureOpen(): Job = if (started) Job().apply { complete() } else open()

    /** 오늘 새 카드 한도를 다 쓴 뒤 [n]장을 더 배운다. */
    fun learnMore(n: Int): Job = begin(custom = false) { session.loadQueue(extraNew = n) }

    fun start(): Job = begin(custom = false) { session.loadQueue() }

    /** 라이브러리에서 고른 문장만 복습한다. 채점과 일정 반영은 평소 복습과 같다. */
    fun startWith(entryIds: Set<Long>, answerLang: Lang?): Job = begin(custom = true) { session.loadSelected(entryIds, answerLang) }

    private fun begin(custom: Boolean, load: suspend () -> List<DueCard>): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        clearUndo()
        reviewed = 0
        resetSessionCounters()
        ratedCards.clear()
        queue.clear()
        queue.addAll(load())
        _custom.value = if (custom) queue.size else null
        refreshGoal()
        refreshStreak()
        if (queue.isEmpty()) {
            _progress.value = ReviewProgress(0, 0)
            _state.value = ReviewUi.Empty
            if (!custom) refreshMoreNew()
        } else showNext()
    }

    private suspend fun showNext() {
        val next = queue.removeFirstOrNull()
        _progress.value = ReviewProgress(reviewed, reviewed + queue.size + if (next == null) 0 else 1)
        if (next == null) refreshMoreNew()
        _state.value = if (next == null) ReviewUi.Done(reviewed, ratedCards.distinct().size) else {
            // 카드 준비에 AI 번역이 끼면 오래 걸릴 수 있다. 그때만 이전 카드 대신 불러오는 중을 보여 준다.
            val item = coroutineScope {
                val slow = launch { delay(300); _state.value = ReviewUi.Loading }
                session.prepare(next).also { slow.cancel() }
            }
            ReviewUi.Prompt(item, List(item.blankCount) { "" }, session.now())
        }
    }

    private fun clearUndo() { lastGraded = null; _canUndo.value = false }

    private fun refreshProgress() {
        val cur = _state.value is ReviewUi.Prompt || _state.value is ReviewUi.Graded
        _progress.value = ReviewProgress(reviewed, reviewed + queue.size + if (cur) 1 else 0)
    }

    /** 방금 한 평가를 취소하고, 그 카드를 평가 직전 화면으로 되돌려 다시 고르게 한다. */
    fun undo(): Job = viewModelScope.launch {
        val g = lastGraded ?: return@launch
        val cur = _state.value
        if (rating || (cur !is ReviewUi.Prompt && cur !is ReviewUi.Done)) return@launch
        if (!session.undoLast()) return@launch
        // 평가 뒤 새로 나온 카드는 큐 맨 앞으로 돌려놓고, "다시"로 뒤에 붙었던 같은 카드는 뺀다.
        if (cur is ReviewUi.Prompt && cur.item.card.id != g.item.card.id) queue.addFirst(DueCard(cur.item.card, cur.item.texts, cur.item.keyPhrases, cur.item.note))
        queue.removeAll { it.card.id == g.item.card.id }
        reviewed = (reviewed - 1).coerceAtLeast(0)
        ratedCards.removeLastOrNull()
        _combo.value = comboBeforeLast
        if (lastRating != Rating.AGAIN) _remembered.value = (_remembered.value - 1).coerceAtLeast(0)
        clearUndo()
        _state.value = g
        refreshProgress()
        refreshGoal()
        if (_goal.value.done == 0) refreshStreak() // 오늘 유일한 복습을 취소했으면 연속일이 줄 수 있다
    }

    /** 복습 중 카드 메뉴: 오늘은 건너뛰기(평가 기록 없이 내일로). */
    fun skipToday(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        session.skipToday(p.item.card)
        showNext()
    }

    /** 자주 틀리는 카드를 한 달 쉰다(평가 기록 없이). */
    fun snoozeMonth(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        session.snoozeMonth(p.item.card)
        showNext()
    }

    /** 복습 중 카드 메뉴: 이 문장을 라이브러리에서 지운다(같은 문장의 다른 언어 카드도 큐에서 뺀다). */
    fun deleteCurrent(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        session.deleteEntry(p.item.card.entryId)
        queue.removeAll { it.card.entryId == p.item.card.entryId }
        clearUndo()
        showNext()
    }

    /** 복습 중 카드 메뉴: 정답 문장과 한국어 뜻을 고친다. 같은 카드를 새 내용으로 다시 낸다. */
    fun editCurrent(answerText: String, ko: String, note: String? = null): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        session.edit(p.item.card, answerText, ko, note)?.let { queue.addFirst(it) }
        showNext()
    }

    /** 특수문자 버튼: 고른 빈칸(기본 첫 칸)의 끝에 글자를 붙인다. */
    fun appendToAnswer(text: String, index: Int = 0) {
        val p = _state.value as? ReviewUi.Prompt ?: return
        if (index !in p.answers.indices) return
        setAnswer(p.answers[index] + text, index)
    }

    /** 모르겠을 때: 정답을 보여 주고 "다시"를 권한다. 기록은 평가를 누를 때 남는다. */
    fun giveUp(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        _state.value = ReviewUi.Graded(
            p.item, GradeOutcome.SelfGrade(p.item.reference, null), "", Rating.AGAIN, session.now() - p.shownAt,
            session.previews(p.item.card),
        )
    }

    /** 지금 카드를 기록 없이 큐 맨 뒤로 보낸다. */
    fun postpone(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        queue.addLast(DueCard(p.item.card, p.item.texts, p.item.keyPhrases, p.item.note))
        showNext()
    }

    /** 마이크로 받은 말을 첫 입력칸에 넣는다(이미 쓴 게 있으면 이어 붙인다). */
    fun putSpokenAnswer(spoken: String) {
        val p = _state.value as? ReviewUi.Prompt ?: return
        setAnswer(appendSpoken(p.answers[0], spoken), 0)
    }

    fun setAnswer(s: String, index: Int = 0) {
        val p = _state.value as? ReviewUi.Prompt ?: return
        if (index !in p.answers.indices) return
        _state.value = p.copy(answers = p.answers.toMutableList().also { it[index] = s })
    }

    fun speak(slow: Boolean) {
        when (val s = _state.value) {
            is ReviewUi.Prompt -> session.speak(s.item, slow)
            is ReviewUi.Graded -> session.speak(s.item, slow)
            else -> Unit
        }
    }

    fun submit(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.answers.any { it.isBlank() } || p.checking) return@launch
        _state.value = p.copy(checking = true)
        val responseMs = session.now() - p.shownAt // AI 채점 대기 시간은 제외한다
        val outcome = session.grade(p.item, p.answers)
        val suggested = when (outcome) {
            is GradeOutcome.Ai -> outcome.suggested
            is GradeOutcome.Local -> outcome.suggested
            is GradeOutcome.SelfGrade -> Rating.GOOD
        }
        _state.value = ReviewUi.Graded(p.item, outcome, p.answer, suggested, responseMs, session.previews(p.item.card))
    }

    fun rate(r: Rating): Job {
        val g = _state.value as? ReviewUi.Graded
        // 저장이 끝나기 전의 두 번째 탭은 무시한다(같은 복습이 두 번 기록되지 않게).
        if (g == null || rating) return Job().apply { complete() }
        rating = true
        return viewModelScope.launch {
            try {
                // 그사이 삭제된 카드는 기록 없이 넘어간다.
                val wasReached = _goal.value.reached
                val firstToday = _goal.value.done == 0
                val updated = session.rate(g.item, r, g.outcome, g.answer, g.responseMs) ?: return@launch showNext()
                reviewed++
                lastRating = r
                comboBeforeLast = _combo.value
                if (r == Rating.AGAIN) _combo.value = 0 else { _combo.value++; _remembered.value++ }
                ratedCards += g.item.card.id
                lastGraded = g
                _canUndo.value = true
                refreshGoal()
                if (firstToday) refreshStreak()
                if (!wasReached && _goal.value.reached && !celebratedThisSession) { _celebrate.value = true; celebratedThisSession = true }
                // 다시: 갱신된 카드 상태로 같은 세션 끝에 한 번 더 낸다
                if (r == Rating.AGAIN) queue.addLast(DueCard(updated, g.item.texts, g.item.keyPhrases, g.item.note))
                showNext()
            } finally {
                rating = false
            }
        }
    }
}
