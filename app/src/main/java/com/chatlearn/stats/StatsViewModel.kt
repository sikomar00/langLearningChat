package com.chatlearn.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.EntryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import java.time.ZoneId

class StatsViewModel(
    repo: EntryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    newPerDay: kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(10),
) : ViewModel() {
    private val input = repo.observeStatsInput().shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    /** 복습 기록이 바뀔 때마다 다시 계산한다. 처음 계산되기 전에는 null. */
    val stats: StateFlow<Stats?> = input
        .map { StatsCalculator.compute(it.logs, it.cards, it.items, clock(), zone) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 앞으로 30일 복습·새 카드 예보. */
    val outlook: StateFlow<List<OutlookDay>> = kotlinx.coroutines.flow.combine(input, newPerDay) { si, n -> GrowthCalculator.outlook(si.cards, si.items, clock(), zone, n) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 약한 문법(많이 틀린 순). */
    val grammar: StateFlow<List<GrammarWeakness>> = repo.observeGrammarEvents()
        .map { GrammarStats.weakest(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
