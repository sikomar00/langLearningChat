package com.chatlearn.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.Backup
import com.chatlearn.data.EntryRepository
import com.chatlearn.settings.AppSettings
import com.chatlearn.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository, private val repo: EntryRepository) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings("", "", 10, 0))

    fun saveKey(k: String) = viewModelScope.launch { settings.setApiKey(k) }
    fun saveModel(m: String) = viewModelScope.launch { settings.setModel(m) }
    fun saveNewPerDay(n: Int) = viewModelScope.launch { settings.setNewPerDay(n) }

    suspend fun exportJson(): String = Backup.encode(repo.exportAll())
    suspend fun importJson(s: String): Result<Unit> = runCatching { repo.importAll(Backup.decode(s)) }
}
