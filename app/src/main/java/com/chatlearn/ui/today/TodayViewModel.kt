package com.chatlearn.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.LibraryItem
import com.chatlearn.review.GoalProgress
import com.chatlearn.review.ReviewSession
import com.chatlearn.stats.DayCount
import com.chatlearn.stats.StatsCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/** 오늘 화면: 지금 복습할 카드, 목표, 연속 기록, 7일 예보, 최근 저장. */
data class TodayUi(
    val reviews: Int,
    val newCards: Int,
    /** 오늘 한도 밖에서 더 배울 수 있는 새 카드 수. */
    val moreNew: Int,
    val goal: GoalProgress,
    val streak: Int,
    val forecast: List<DayCount>,
    val recent: List<LibraryItem>,
    val hasEntries: Boolean,
) {
    val due: Int get() = reviews + newCards
}

class TodayViewModel(
    private val session: ReviewSession,
    private val repo: EntryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _ui = MutableStateFlow<TodayUi?>(null)
    /** 처음 읽기 전에는 null. */
    val ui: StateFlow<TodayUi?> = _ui.asStateFlow()

    /** 화면에 들어올 때와 복습을 마치고 돌아올 때 다시 읽는다. */
    fun refresh(): Job = viewModelScope.launch {
        val now = clock()
        val queue = session.loadQueue()
        val newCards = queue.count { it.card.lastReviewedAt == null }
        // shortcut: 최근 3개를 보려고 라이브러리 전체를 한 번 읽는다. 문장이 수천 개가 되면 최근 N개 조회로 바꾼다.
        val library = repo.observeLibrary().first()
        _ui.value = TodayUi(
            reviews = queue.size - newCards,
            newCards = newCards,
            moreNew = (session.newCardsWaiting() - newCards).coerceAtLeast(0),
            goal = session.todayProgress(),
            streak = session.streak(),
            forecast = StatsCalculator.forecast(repo.reviewDueTimes(now + 7 * DAY_MS), now, zone),
            recent = library.take(3),
            hasEntries = library.isNotEmpty(),
        )
    }

    private companion object { const val DAY_MS = 86_400_000L }
}
