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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository, private val repo: EntryRepository, private val ai: AiService,
    private val onReminderChanged: (Boolean, Int) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    private val notionTokenStore: com.chatlearn.settings.SecretStore? = null,
    private val notion: com.chatlearn.notion.NotionClient? = null,
) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings("", "", 10, 0))

    fun saveKey(k: String) = viewModelScope.launch { settings.setApiKey(k) }
    /** 저장이 끝난 뒤 바로 연결을 확인할 때. */
    suspend fun saveKeyNow(k: String) = settings.setApiKey(k)
    fun finishOnboarding() = viewModelScope.launch { settings.finishOnboarding() }

    fun notionToken(): String = notionTokenStore?.get().orEmpty()
    suspend fun saveNotion(token: String, link: String) {
        // 공백·줄바꿈은 HTTP 헤더를 깨뜨리므로 인쇄 가능한 글자만 남긴다.
        notionTokenStore?.set(token.filter { it.code in 33..126 })
        settings.setNotionDatabase(link)
    }

    /** [link] 데이터베이스에 저장한 토큰으로 연결해 본다. 성공하면 찾은 속성 수. */
    suspend fun checkNotion(link: String): Result<Int> {
        val id = com.chatlearn.notion.notionIdOf(link)
            ?: return Result.failure(com.chatlearn.notion.NotionException(com.chatlearn.notion.NotionError.NOT_SHARED))
        return notion?.connect(id)?.map { it.properties.size } ?: Result.failure(IllegalStateException("notion"))
    }
    fun saveModel(m: String) = viewModelScope.launch { settings.setModel(m) }
    fun saveNewPerDay(n: Int) = viewModelScope.launch { settings.setNewPerDay(n) }
    fun saveReminder(enabled: Boolean, hour: Int) = viewModelScope.launch {
        settings.setReminder(enabled, hour)
        onReminderChanged(enabled, hour.coerceIn(0, 23))
    }
    fun saveDailyGoal(n: Int) = viewModelScope.launch { settings.setDailyGoal(n) }
    fun saveUseSaved(on: Boolean) = viewModelScope.launch { settings.setUseSavedExpressions(on) }
    fun saveReminderDays(mask: Int) = viewModelScope.launch { settings.setReminderDays(mask) }
    fun saveReminderTone(name: String) = viewModelScope.launch { settings.setReminderTone(name) }
    fun saveDynamicColor(on: Boolean) = viewModelScope.launch { settings.setDynamicColor(on) }
    fun saveAutoExpand(on: Boolean) = viewModelScope.launch { settings.setAutoExpand(on) }
    fun saveThemeMode(name: String) = viewModelScope.launch { settings.setThemeMode(name) }
    fun saveEnAccent(name: String) = viewModelScope.launch { settings.setEnAccent(name) }
    fun saveEsAccent(name: String) = viewModelScope.launch { settings.setEsAccent(name) }
    fun saveSpeechRate(rate: Float) = viewModelScope.launch { settings.setSpeechRate(rate) }
    fun saveRetention(r: Double) = viewModelScope.launch { settings.setRetention(r) }
    fun saveMaxReviews(n: Int) = viewModelScope.launch { settings.setMaxReviews(n) }

    /** 저장한 문장 수: 백업을 권할지 정할 때 쓴다. */
    val entryCount: StateFlow<Int> = repo.observeLibrary().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 저장된 키와 모델로 연결을 확인한다. 문제가 없으면 null. */
    suspend fun checkConnection(): AiError? = checkConnection(ai)

    /** 백업 파일을 [write]로 쓰고, 끝까지 쓴 뒤에만 백업 시각을 남긴다. */
    suspend fun export(write: suspend (String) -> Unit): Result<Unit> = runCatching {
        write(Backup.encode(repo.exportAll()))
        settings.setLastBackupAt(clock())
    }

    fun now(): Long = clock()
    fun saveAutoBackupFolder(uri: String) = viewModelScope.launch { settings.setAutoBackupFolder(uri) }
    suspend fun importJson(s: String): Result<Unit> = runCatching { repo.importAll(Backup.decode(s)) }
}
