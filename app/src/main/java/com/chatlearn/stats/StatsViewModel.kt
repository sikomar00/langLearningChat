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
import java.time.ZoneId

class StatsViewModel(
    repo: EntryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    /** 복습 기록이 바뀔 때마다 다시 계산한다. 처음 계산되기 전에는 null. */
    val stats: StateFlow<Stats?> = repo.observeStatsInput()
        .map { StatsCalculator.compute(it.logs, it.cards, it.items, clock(), zone) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
