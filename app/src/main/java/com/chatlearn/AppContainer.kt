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

    // 노션 토큰도 Gemini 키처럼 기기 암호화 저장소에만 둔다.
    val notionToken: com.chatlearn.settings.SecretStore by lazy { EncryptedSecretStore(context, "notion_token") }
    val notionClient = com.chatlearn.notion.NotionClient(geminiHttpClient(), token = { notionToken.get() })
    val notion = com.chatlearn.notion.NotionExporter(repo, notionClient) { settings.settings.first().notionDatabase }
    val keyPhrases = com.chatlearn.expansion.KeyPhraseService(repo, ai)

    private val appContext: Context = context.applicationContext

    init {
        appScope.launch { settings.settings.collect { voicePrefs = it.voice() } }
    }

    /**
     * 앱을 열 때: 자동 백업 폴더를 골라 두었고 마지막 백업에서 7일이 지났으면 백업 파일을 쓴다.
     * 폴더 권한이 사라졌거나 쓰기에 실패하면 조용히 건너뛰고(다음에 다시), 설정의 "마지막 백업"이 오래됐다고 알린다.
     */
    fun runAutoBackupIfDue() {
        appScope.launch(Dispatchers.IO) {
            val s = settings.settings.first()
            val now = System.currentTimeMillis()
            if (!com.chatlearn.settings.autoBackupDue(s.autoBackupFolder.isNotBlank(), s.lastBackupAt, now)) return@launch
            // 새 폰으로 복원하면 설정은 오지만 폴더 권한은 오지 않는다. 쓸 수 없으면 꺼서 설정 화면이 "켜짐"으로 속이지 않게 한다.
            val uri = android.net.Uri.parse(s.autoBackupFolder)
            if (appContext.contentResolver.persistedUriPermissions.none { it.uri == uri && it.isWritePermission }) {
                settings.setAutoBackupFolder("")
                return@launch
            }
            runCatching {
                val json = com.chatlearn.data.Backup.encode(repo.exportAll())
                com.chatlearn.settings.writeAutoBackup(appContext, uri, java.time.LocalDate.now(), json)
                settings.setLastBackupAt(now)
            }.onFailure { android.util.Log.w("AutoBackup", "auto backup failed", it) }
        }
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
