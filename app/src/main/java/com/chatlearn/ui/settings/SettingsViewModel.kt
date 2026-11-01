package com.chatlearn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.ai.AiError
import com.chatlearn.ai.AiService
import com.chatlearn.ai.checkConnection
import com.chatlearn.data.Backup
import com.chatlearn.data.EntryRepository
import com.chatlearn.settings.AppSettings
import com.chatlearn.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository, private val repo: EntryRepository, private val ai: AiService,
    private val onReminderChanged: (Boolean, Int) -> Unit = { _, _ -> },
) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings("", "", 10, 0))

    fun saveKey(k: String) = viewModelScope.launch { settings.setApiKey(k) }
    fun saveModel(m: String) = viewModelScope.launch { settings.setModel(m) }
    fun saveNewPerDay(n: Int) = viewModelScope.launch { settings.setNewPerDay(n) }
    fun saveReminder(enabled: Boolean, hour: Int) = viewModelScope.launch {
        settings.setReminder(enabled, hour)
        onReminderChanged(enabled, hour.coerceIn(0, 23))
    }
    fun saveDailyGoal(n: Int) = viewModelScope.launch { settings.setDailyGoal(n) }
    fun saveUseSaved(on: Boolean) = viewModelScope.launch { settings.setUseSavedExpressions(on) }
    fun saveAutoExpand(on: Boolean) = viewModelScope.launch { settings.setAutoExpand(on) }

    /** 저장된 키와 모델로 연결을 확인한다. 문제가 없으면 null. */
    suspend fun checkConnection(): AiError? = checkConnection(ai)

    suspend fun exportJson(): String = Backup.encode(repo.exportAll())
    suspend fun importJson(s: String): Result<Unit> = runCatching { repo.importAll(Backup.decode(s)) }
}
