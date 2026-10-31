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

data class AppSettings(val apiKey: String, val model: String, val newPerDay: Int, val aiCallsToday: Int)

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

    private fun today() = Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    override val settings: Flow<AppSettings> = combine(store.data, keyFlow) { p, key ->
        val sameDay = p[CALL_DAY] == today()
        AppSettings(key, p[MODEL] ?: DEFAULT_MODEL, p[NEW_PER_DAY] ?: 10, if (sameDay) p[CALL_COUNT] ?: 0 else 0)
    }

    override fun currentApiKey() = secret.get()
    override fun currentModel(): String = runBlocking { store.data.first()[MODEL] ?: DEFAULT_MODEL }

    override suspend fun setApiKey(k: String) { secret.set(k.trim()); keyFlow.value = k.trim() }
    override suspend fun setModel(m: String) { store.edit { it[MODEL] = m.trim().ifEmpty { DEFAULT_MODEL } } }
    override suspend fun setNewPerDay(n: Int) { store.edit { it[NEW_PER_DAY] = n.coerceAtLeast(0) } }

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
