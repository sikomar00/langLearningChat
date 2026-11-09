package com.chatlearn

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.chatlearn.ai.AiService
import com.chatlearn.ai.GeminiAiService
import com.chatlearn.ai.geminiHttpClient
import com.chatlearn.data.AppDatabase
import com.chatlearn.data.AutoBackup
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.Migrations
import com.chatlearn.expansion.ExpansionService
import com.chatlearn.review.AndroidTts
import com.chatlearn.review.ReviewSession
import com.chatlearn.review.Tts
import com.chatlearn.settings.DataStoreSettingsRepository
import com.chatlearn.settings.EncryptedSecretStore
import com.chatlearn.settings.SettingsRepository
import com.chatlearn.settings.voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.settingsStore by preferencesDataStore("settings")

class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "chatlearn.db")
        .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4)
        .build()
    val repo = EntryRepository(db)

    /** 기기 백업에 실어 보낼 스냅샷. 백업 규칙(res/xml)은 이 폴더만 포함한다. */
    val autoBackup = AutoBackup(java.io.File(context.filesDir, AutoBackup.DIR_NAME), repo)
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsStore, EncryptedSecretStore(context))
    private val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val ai: AiService = GeminiAiService(
        geminiHttpClient(),
        com.chatlearn.ai.aiBaseUrl(debuggable, if (debuggable) runCatching { java.io.File(context.filesDir, "ai_base_url.txt").readText() }.getOrNull() else null),
        apiKey = { settings.currentApiKey() },
        model = { settings.currentModel() },
        onCall = { appScope.launch { settings.recordAiCall() } },
        onHttpError = { code, body -> android.util.Log.w("GeminiAi", "HTTP $code: $body") },
    )

    // 설정한 억양·빠르기를 TTS가 매번 읽어 간다.
    @Volatile private var voicePrefs = com.chatlearn.settings.VoicePrefs()
    val tts: Tts = AndroidTts(context) { voicePrefs }
    val reminders = com.chatlearn.reminder.ReminderScheduler(context)
    val expansion = ExpansionService(repo, ai, System::currentTimeMillis)
    val keyPhrases = com.chatlearn.expansion.KeyPhraseService(repo, ai)

    private val appContext: Context = context.applicationContext

    init {
        appScope.launch { settings.settings.collect { voicePrefs = it.voice() } }
    }

    /** 홈 화면 위젯을 다시 그린다(앱을 나갈 때, 알림 시각에). */
    fun refreshWidget() {
        appScope.launch { com.chatlearn.widget.refreshReviewWidget(appContext) }
    }

    /** 스냅샷이 오래됐으면 새로 쓴다(앱을 나갈 때). 실패해도 앱에는 영향이 없다. */
    fun snapshotBackup() {
        appScope.launch { autoBackup.snapshotIfStale() }
    }

    fun reviewSession() = ReviewSession(
        repo, ai, System::currentTimeMillis, { settings.settings.first().newPerDay }, tts = tts,
        dailyGoal = { settings.settings.first().dailyGoal },
    )
}
