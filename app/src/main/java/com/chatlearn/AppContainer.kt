package com.chatlearn

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.chatlearn.ai.AiService
import com.chatlearn.ai.GeminiAiService
import com.chatlearn.ai.geminiHttpClient
import com.chatlearn.data.AppDatabase
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
        .addMigrations(*Migrations.ALL)
        .build()
    val repo = EntryRepository(db)
    val settings: SettingsRepository = DataStoreSettingsRepository(context.settingsStore, EncryptedSecretStore(context))
    private val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val ai: AiService = GeminiAiService(
        geminiHttpClient(),
        com.chatlearn.ai.aiBaseUrl(debuggable, if (debuggable) runCatching { java.io.File(context.filesDir, "ai_base_url.txt").readText() }.getOrNull() else null),
        apiKey = { settings.currentApiKey() },
        model = { settings.currentModel() },
        onCall = { appScope.launch { settings.recordAiCall() } },
        // 응답 본문에는 번역한 대화가 들어 있을 수 있어 디버그 빌드에서만 남긴다.
        onHttpError = { code, body -> if (debuggable) android.util.Log.w("GeminiAi", "HTTP $code: $body") },
    )

    // 설정한 억양·빠르기를 TTS가 매번 읽어 간다.
    @Volatile private var voicePrefs = com.chatlearn.settings.VoicePrefs()
    // 음성 엔진은 화면을 열 때 연결한다. 알림·위젯·부팅 때 뜨는 프로세스는 쓰지 않는 엔진을 띄우지 않는다.
    private val androidTts by lazy { AndroidTts(context) { voicePrefs } }
    val tts: Tts = object : Tts {
        override fun isAvailable(lang: com.chatlearn.domain.Lang) = androidTts.isAvailable(lang)
        override fun speak(text: String, lang: com.chatlearn.domain.Lang, slow: Boolean) = androidTts.speak(text, lang, slow)
        override fun stop() = androidTts.stop()
    }

    /** 첫 복습 카드 전에 엔진 초기화가 끝나 있도록 화면을 열 때 미리 연결한다. */
    fun warmUpTts() { androidTts }
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

    fun reviewSession() = ReviewSession(
        repo, ai, System::currentTimeMillis, { settings.settings.first().newPerDay }, tts = tts,
        dailyGoal = { settings.settings.first().dailyGoal },
        retention = { settings.settings.first().retention },
        maxReviews = { settings.settings.first().maxReviews },
    )
}
