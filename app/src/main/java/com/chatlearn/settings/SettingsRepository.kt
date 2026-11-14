package com.chatlearn.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId

data class AppSettings(
    val apiKey: String,
    val model: String,
    val newPerDay: Int,
    val aiCallsToday: Int,
    val autoExpand: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val dailyGoal: Int = 10,
    val useSavedExpressions: Boolean = true,
    val dynamicColor: Boolean = false,
    val reminderDays: Int = 0b1111111,
    val reminderTone: String = "PLAIN",
    val themeMode: String = "SYSTEM",
    val enAccent: String = "US",
    val esAccent: String = "ES",
    val speechRate: Float = 1f,
    val lastBackupAt: Long = 0L,
    val libraryHintDismissed: Boolean = false,
    /** 복습 일정의 목표 기억률(FSRS). 높을수록 자주 복습한다. */
    val retention: Double = com.chatlearn.domain.Scheduler.DEFAULT_RETENTION,
    /** 하루에 내는 복습 카드(새 카드 제외) 최대 수. */
    val maxReviews: Int = com.chatlearn.domain.Scheduler.DEFAULT_MAX_REVIEWS,
    /** 보낼 말을 마지막으로 만든 언어(EN/ES). */
    val sentTarget: String = "EN",
    /** 첫 실행 안내를 끝냈거나 건너뛰었다. */
    val onboardingDone: Boolean = false,
    /** 문장을 보낼 노션 데이터베이스 링크(또는 id). */
    val notionDatabase: String = "",
    /** 매주 자동 백업 파일을 둘 폴더(저장소 트리 URI). 비어 있으면 끔. */
    val autoBackupFolder: String = "",
)

interface SecretStore {
    fun get(): String
    fun set(v: String)
}

/** 기기 암호화 저장소의 비밀 값 하나([key]: Gemini 키, 노션 토큰…). */
class EncryptedSecretStore(context: Context, private val key: String = "api_key") : SecretStore {
    private val prefs = EncryptedSharedPreferences.create(
        context, "secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    override fun get() = prefs.getString(key, "").orEmpty()
    override fun set(v: String) { prefs.edit().putString(key, v).apply() }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    fun currentApiKey(): String
    fun currentModel(): String
    suspend fun setApiKey(k: String)
    suspend fun setModel(m: String)
    suspend fun setNewPerDay(n: Int)
    suspend fun setAutoExpand(on: Boolean)
    suspend fun setReminder(enabled: Boolean, hour: Int)
    suspend fun setDailyGoal(n: Int)
    suspend fun setUseSavedExpressions(on: Boolean)
    suspend fun setDynamicColor(on: Boolean) {}
    suspend fun setReminderDays(mask: Int) {}
    suspend fun setReminderTone(name: String) {}
    suspend fun setThemeMode(name: String) {}
    suspend fun setEnAccent(name: String) {}
    suspend fun setEsAccent(name: String) {}
    suspend fun setSpeechRate(rate: Float) {}
    suspend fun setLastBackupAt(at: Long) {}
    suspend fun dismissLibraryHint() {}
    suspend fun setRetention(r: Double) {}
    suspend fun setMaxReviews(n: Int) {}
    suspend fun setSentTarget(code: String) {}
    suspend fun finishOnboarding() {}
    suspend fun setNotionDatabase(link: String) {}
    suspend fun setAutoBackupFolder(uri: String) {}
    suspend fun recordAiCall()
}

class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
    private val secret: SecretStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : SettingsRepository {
    private val keyFlow = MutableStateFlow(secret.get())

    private val MODEL = stringPreferencesKey("model")
    private val NEW_PER_DAY = intPreferencesKey("new_per_day")
    private val CALL_DAY = stringPreferencesKey("call_day")
    private val CALL_COUNT = intPreferencesKey("call_count")
    private val AUTO_EXPAND = booleanPreferencesKey("auto_expand")
    private val REMINDER_ON = booleanPreferencesKey("reminder_on")
    private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    private val DAILY_GOAL = intPreferencesKey("daily_goal")
    private val USE_SAVED = booleanPreferencesKey("use_saved_expressions")
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val REMINDER_DAYS = intPreferencesKey("reminder_days")
    private val REMINDER_TONE = stringPreferencesKey("reminder_tone")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val EN_ACCENT = stringPreferencesKey("en_accent")
    private val ES_ACCENT = stringPreferencesKey("es_accent")
    private val SPEECH_RATE = floatPreferencesKey("speech_rate")
    private val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    private val LIBRARY_HINT_DISMISSED = booleanPreferencesKey("library_hint_dismissed")
    private val RETENTION = doublePreferencesKey("retention")
    private val MAX_REVIEWS = intPreferencesKey("max_reviews")
    private val SENT_TARGET = stringPreferencesKey("sent_target")
    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val NOTION_DATABASE = stringPreferencesKey("notion_database")
    private val AUTO_BACKUP_FOLDER = stringPreferencesKey("auto_backup_folder")

    private fun today() = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    override val settings: Flow<AppSettings> = combine(store.data, keyFlow) { p, key ->
        val sameDay = p[CALL_DAY] == today()
        AppSettings(
            key, p[MODEL] ?: DEFAULT_MODEL, p[NEW_PER_DAY] ?: 10, if (sameDay) p[CALL_COUNT] ?: 0 else 0, p[AUTO_EXPAND] ?: true, p[REMINDER_ON] ?: false, p[REMINDER_HOUR] ?: 20, p[DAILY_GOAL] ?: 10, p[USE_SAVED] ?: true, p[DYNAMIC_COLOR] ?: false, p[REMINDER_DAYS] ?: 0b1111111, p[REMINDER_TONE] ?: "PLAIN",
            themeMode = p[THEME_MODE] ?: "SYSTEM", enAccent = p[EN_ACCENT] ?: "US", esAccent = p[ES_ACCENT] ?: "ES",
            speechRate = p[SPEECH_RATE] ?: 1f, lastBackupAt = p[LAST_BACKUP_AT] ?: 0L,
            libraryHintDismissed = p[LIBRARY_HINT_DISMISSED] ?: false,
            retention = p[RETENTION] ?: com.chatlearn.domain.Scheduler.DEFAULT_RETENTION,
            maxReviews = p[MAX_REVIEWS] ?: com.chatlearn.domain.Scheduler.DEFAULT_MAX_REVIEWS,
            sentTarget = p[SENT_TARGET] ?: "EN",
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            notionDatabase = p[NOTION_DATABASE] ?: "",
            autoBackupFolder = p[AUTO_BACKUP_FOLDER] ?: "",
        )
    }

    override fun currentApiKey() = secret.get()
    override fun currentModel(): String = runBlocking { store.data.first()[MODEL] ?: DEFAULT_MODEL }

    override suspend fun setApiKey(k: String) {
        // 공백, 줄바꿈, 보이지 않는 문자는 HTTP 헤더를 깨뜨리므로 인쇄 가능한 ASCII만 남긴다.
        val clean = k.filter { it.code in 33..126 }
        secret.set(clean)
        keyFlow.value = clean
    }
    override suspend fun setModel(m: String) { store.edit { it[MODEL] = m.trim().ifEmpty { DEFAULT_MODEL } } }
    override suspend fun setNewPerDay(n: Int) { store.edit { it[NEW_PER_DAY] = n.coerceAtLeast(0) } }

    override suspend fun setAutoExpand(on: Boolean) { store.edit { it[AUTO_EXPAND] = on } }

    override suspend fun setReminder(enabled: Boolean, hour: Int) {
        store.edit { it[REMINDER_ON] = enabled; it[REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    override suspend fun setDailyGoal(n: Int) { store.edit { it[DAILY_GOAL] = n.coerceIn(0, 200) } }

    override suspend fun setUseSavedExpressions(on: Boolean) { store.edit { it[USE_SAVED] = on } }

    override suspend fun setReminderDays(mask: Int) { store.edit { it[REMINDER_DAYS] = mask and 0b1111111 } }
    override suspend fun setReminderTone(name: String) { store.edit { it[REMINDER_TONE] = name } }

    override suspend fun setDynamicColor(on: Boolean) { store.edit { it[DYNAMIC_COLOR] = on } }

    override suspend fun setThemeMode(name: String) { store.edit { it[THEME_MODE] = name } }
    override suspend fun setEnAccent(name: String) { store.edit { it[EN_ACCENT] = name } }
    override suspend fun setEsAccent(name: String) { store.edit { it[ES_ACCENT] = name } }
    override suspend fun setSpeechRate(rate: Float) { store.edit { it[SPEECH_RATE] = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE) } }
    override suspend fun setLastBackupAt(at: Long) { store.edit { it[LAST_BACKUP_AT] = at } }
    override suspend fun dismissLibraryHint() { store.edit { it[LIBRARY_HINT_DISMISSED] = true } }
    override suspend fun setRetention(r: Double) { store.edit { it[RETENTION] = r.coerceIn(com.chatlearn.domain.Scheduler.RETENTION_RANGE) } }
    override suspend fun setSentTarget(code: String) { store.edit { it[SENT_TARGET] = code } }
    override suspend fun finishOnboarding() { store.edit { it[ONBOARDING_DONE] = true } }
    override suspend fun setNotionDatabase(link: String) { store.edit { it[NOTION_DATABASE] = link.trim() } }
    override suspend fun setAutoBackupFolder(uri: String) { store.edit { it[AUTO_BACKUP_FOLDER] = uri } }
    override suspend fun setMaxReviews(n: Int) { store.edit { it[MAX_REVIEWS] = n.coerceIn(10, 1000) } }

    override suspend fun recordAiCall() {
        store.edit {
            val day = today()
            val count = if (it[CALL_DAY] == day) it[CALL_COUNT] ?: 0 else 0
            it[CALL_DAY] = day
            it[CALL_COUNT] = count + 1
        }
    }

    companion object { const val DEFAULT_MODEL = "gemini-flash-latest" }
}
