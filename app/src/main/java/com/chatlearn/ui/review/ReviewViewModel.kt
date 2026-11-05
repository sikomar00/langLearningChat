package com.chatlearn.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.DueCard
import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.review.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewUi {
    data object Loading : ReviewUi
    data object Empty : ReviewUi
    /** 복습을 시작하기 전 요약: 오늘 낼 카드 수와 그중 새 카드 수. */
    data class Ready(val total: Int, val newCards: Int) : ReviewUi
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
    ) : ReviewUi
    /** [count]: 평가한 횟수(다시 푼 것 포함), [cards]: 복습한 카드 수. */
    data class Done(val count: Int, val cards: Int = count) : ReviewUi
}

/** [done]개를 끝냈고 이번 세션에서 낼 카드가 [total]장(다시 평가한 카드 포함). */
data class ReviewProgress(val done: Int, val total: Int)

const val DELETED_MID_REVIEW_NOTICE = "복습하던 문장이 라이브러리에서 지워져 건너뛰었어요."
const val DELETED_NO_UNDO_NOTICE = "그 문장이 라이브러리에서 지워져 평가를 취소할 수 없어요."

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

    private val _goal = MutableStateFlow(GoalProgress(0, 0))
    /** 오늘 복습한 카드 수와 하루 목표. */
    val goal: StateFlow<GoalProgress> = _goal.asStateFlow()
    private suspend fun refreshGoal() { _goal.value = session.todayProgress() }

    private val _notice = MutableStateFlow<String?>(null)
    /** 한 번 알릴 안내(복습 도중 문장이 지워진 경우 등). 화면이 보여 준 뒤 [noticeShown]으로 비운다. */
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun noticeShown() { _notice.value = null }

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

    /** 화면이 다시 그려져도(탭 이동, 회전) 진행 중인 복습을 되돌리지 않는다. */
    fun ensureStarted(): Job {
        if (started) return Job().apply { complete() }
        return start(preview = true)
    }

    /**
     * 탭에 다시 들어왔거나 알림으로 열었을 때 큐를 새로 읽는다. 문제·채점 화면이면 건드리지 않는다.
     * 다 끝낸 요약(Done)은 [force]일 때만 바꾼다(되돌리기와 요약을 지키려고).
     */
    fun refresh(force: Boolean): Job {
        val s = _state.value
        val idle = s is ReviewUi.Empty || s is ReviewUi.Ready || (force && s is ReviewUi.Done)
        if (!started || !idle || rating) return if (!started) start(preview = true) else dropCurrentIfDeleted()
        return start(preview = true)
    }

    /** 지금 화면의 카드. 채점 중이거나 평가를 저장하는 중이면 바꾸면 안 되므로 null. */
    private fun replaceableCard(): ReviewCardEntity? = if (rating) null else when (val s = _state.value) {
        is ReviewUi.Prompt -> s.item.card.takeUnless { s.checking }
        is ReviewUi.Graded -> s.item.card
        else -> null
    }

    /** 다른 탭에서 지금 카드의 문장이 지워졌으면, 문제·채점 화면에 그대로 두지 않고 다음 카드로 넘어간다. */
    private fun dropCurrentIfDeleted(): Job = viewModelScope.launch {
        val card = replaceableCard() ?: return@launch
        if (session.exists(card)) return@launch
        if (replaceableCard()?.id != card.id) return@launch // 확인하는 사이 화면이 바뀌었다
        rating = true // 넘어가는 동안 평가 탭이 겹치지 않게 한다
        try {
            dropDeleted(card.entryId)
            showNext()
        } finally {
            rating = false
        }
    }

    /** 복습 도중 문장이 라이브러리에서 지워졌다: 같은 문장의 다른 언어 카드도 큐에서 빼고 안내한다. */
    private fun dropDeleted(entryId: Long) {
        queue.removeAll { it.card.entryId == entryId }
        _notice.value = DELETED_MID_REVIEW_NOTICE
    }

    /** 오늘 새 카드 한도를 다 쓴 뒤 [n]장을 더 배운다. 요약 없이 바로 시작한다. */
    fun learnMore(n: Int): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        _custom.value = null
        clearUndo()
        reviewed = 0
        ratedCards.clear()
        queue.clear()
        queue.addAll(session.loadQueue(extraNew = n))
        refreshGoal()
        if (queue.isEmpty()) { _progress.value = ReviewProgress(0, 0); _state.value = ReviewUi.Empty; refreshMoreNew() } else showNext()
    }

    /** [preview]면 첫 카드를 바로 내지 않고 요약(Ready)을 먼저 보여 준다. */
    fun start(preview: Boolean = false): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        _custom.value = null
        clearUndo()
        reviewed = 0
        ratedCards.clear()
        queue.clear()
        queue.addAll(session.loadQueue())
        refreshGoal()
        if (queue.isEmpty()) {
            _progress.value = ReviewProgress(0, 0); _state.value = ReviewUi.Empty; refreshMoreNew()
        } else if (preview) {
            _progress.value = ReviewProgress(0, queue.size)
            _state.value = ReviewUi.Ready(queue.size, queue.count { it.card.lastReviewedAt == null })
        } else showNext()
    }

    /** 요약 화면에서 "시작"을 눌렀을 때. */
    fun begin(): Job = viewModelScope.launch {
        if (_state.value is ReviewUi.Ready) showNext()
    }

    /** 라이브러리에서 고른 문장만 복습한다. 채점과 일정 반영은 평소 복습과 같다. */
    fun startWith(entryIds: Set<Long>, answerLang: Lang?): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        clearUndo()
        reviewed = 0
        ratedCards.clear()
        queue.clear()
        queue.addAll(session.loadSelected(entryIds, answerLang))
        _custom.value = queue.size
        refreshGoal()
        if (queue.isEmpty()) { _progress.value = ReviewProgress(0, 0); _state.value = ReviewUi.Empty } else showNext()
    }

    private suspend fun showNext() {
        var next = queue.removeFirstOrNull()
        // 큐에서 기다리는 사이 라이브러리에서 지워진 카드는 내지 않는다.
        while (next != null && !session.exists(next.card)) {
            dropDeleted(next.card.entryId)
            next = queue.removeFirstOrNull()
        }
        _progress.value = ReviewProgress(reviewed, reviewed + queue.size + if (next == null) 0 else 1)
        if (next == null) refreshMoreNew()
        _state.value = if (next == null) ReviewUi.Done(reviewed, ratedCards.distinct().size) else {
            val item = session.prepare(next)
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
        if (!session.undoLast()) {
            // 평가했던 카드의 문장이 그사이 지워졌다: 되돌릴 수 없으니 버튼을 거둔다.
            clearUndo()
            _notice.value = DELETED_NO_UNDO_NOTICE
            return@launch
        }
        // 평가 뒤 새로 나온 카드는 큐 맨 앞으로 돌려놓고, "다시"로 뒤에 붙었던 같은 카드는 뺀다.
        if (cur is ReviewUi.Prompt && cur.item.card.id != g.item.card.id) queue.addFirst(DueCard(cur.item.card, cur.item.texts, cur.item.keyPhrases, cur.item.note))
        queue.removeAll { it.card.id == g.item.card.id }
        reviewed = (reviewed - 1).coerceAtLeast(0)
        ratedCards.removeLastOrNull()
        clearUndo()
        _state.value = g
        refreshProgress()
        refreshGoal()
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
        val edited = session.edit(p.item.card, answerText, ko, note)
        if (edited == null) dropDeleted(p.item.card.entryId) else queue.addFirst(edited)
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
        _state.value = ReviewUi.Graded(p.item, outcome, p.answer, suggested, responseMs)
    }

    fun rate(r: Rating): Job {
        val g = _state.value as? ReviewUi.Graded
        // 저장이 끝나기 전의 두 번째 탭은 무시한다(같은 복습이 두 번 기록되지 않게).
        if (g == null || rating) return Job().apply { complete() }
        rating = true
        return viewModelScope.launch {
            try {
                val updated = session.rate(g.item, r, g.outcome, g.answer, g.responseMs)
                if (updated == null) {
                    // 평가하는 사이 문장이 라이브러리에서 지워졌다: 기록 없이 다음 카드로 넘어간다.
                    dropDeleted(g.item.card.entryId)
                    showNext()
                    return@launch
                }
                reviewed++
                ratedCards += g.item.card.id
                lastGraded = g
                _canUndo.value = true
                refreshGoal()
                // 다시: 갱신된 카드 상태로 같은 세션 끝에 한 번 더 낸다
                if (r == Rating.AGAIN) queue.addLast(DueCard(updated, g.item.texts, g.item.keyPhrases, g.item.note))
                showNext()
            } finally {
                rating = false
            }
        }
    }
}
