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
)

interface SecretStore {
    fun get(): String
    fun set(v: String)
}

class EncryptedSecretStore(context: Context) : SecretStore {
    private val prefs = EncryptedSharedPreferences.create(
        context, "secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    override fun get() = prefs.getString("api_key", "").orEmpty()
    override fun set(v: String) { prefs.edit().putString("api_key", v).apply() }
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

    private fun today() = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    override val settings: Flow<AppSettings> = combine(store.data, keyFlow) { p, key ->
        val sameDay = p[CALL_DAY] == today()
        AppSettings(key, p[MODEL] ?: DEFAULT_MODEL, p[NEW_PER_DAY] ?: 10, if (sameDay) p[CALL_COUNT] ?: 0 else 0, p[AUTO_EXPAND] ?: true, p[REMINDER_ON] ?: false, p[REMINDER_HOUR] ?: 20, p[DAILY_GOAL] ?: 10, p[USE_SAVED] ?: true, p[DYNAMIC_COLOR] ?: false, p[REMINDER_DAYS] ?: 0b1111111, p[REMINDER_TONE] ?: "PLAIN")
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
