package com.chatlearn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import com.chatlearn.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import com.chatlearn.settings.ThemeMode
import com.chatlearn.settings.VoicePrefs
import com.chatlearn.settings.voice
import com.chatlearn.settings.voiceSample
import com.chatlearn.stats.StatsViewModel
import com.chatlearn.ui.library.LibraryScreen
import com.chatlearn.ui.library.LibraryViewModel
import com.chatlearn.ui.review.ReviewScreen
import com.chatlearn.ui.review.ReviewViewModel
import com.chatlearn.ui.settings.SettingsScreen
import com.chatlearn.ui.settings.SettingsViewModel
import com.chatlearn.ui.translate.TranslateScreen
import com.chatlearn.ui.translate.TranslateViewModel

// androidx.activity의 enableEdgeToEdge 기본 내비게이션 바 스크림과 같은 값.
private val LIGHT_NAV_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private enum class Tab(val label: String) { TRANSLATE("번역"), LIBRARY("라이브러리"), REVIEW("복습"), SETTINGS("설정") }

class MainActivity : ComponentActivity() {
    // 공유·선택 메뉴로 넘어온 문장이나 알림·바로가기·위젯이 시킨 일. 화면이 처리하면 비운다.
    private var pendingLaunch by mutableStateOf<LaunchRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLaunch = launchRequestOf(intent)
    }

    // 앱을 나갈 때 홈 화면 위젯의 복습 수·목표를 방금 한 복습과 저장에 맞춘다.
    override fun onStop() {
        super.onStop()
        (application as ChatLearnApp).container.refreshWidget()
    }

    /**
     * Android 12 이상: 앱 밝기를 시스템에 알려 둔다. 다음에 앱을 열 때 시작 화면과 창 배경도 같은 밝기로 뜬다.
     * 값이 그대로면 아무 일도 일어나지 않고, 바뀌면 화면이 한 번 다시 만들어진다(보던 탭은 유지된다).
     */
    private fun applyNightMode(mode: ThemeMode) {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        val ui = getSystemService(android.app.UiModeManager::class.java) ?: return
        ui.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 회전 등으로 다시 만들어질 때 같은 문장을 또 번역하지 않는다.
        if (savedInstanceState == null) pendingLaunch = launchRequestOf(intent)
        val c = (application as ChatLearnApp).container
        c.warmUpTts()
        // 예약이 사라졌을 수 있으니(강제 종료, 설정 변경) 앱을 열 때마다 설정대로 다시 맞춘다.
        lifecycleScope.launch { c.settings.settings.first().let { c.reminders.apply(it.reminderEnabled, it.reminderHour) } }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                TranslateViewModel::class.java ->
                    TranslateViewModel(c.ai, c.repo, System::currentTimeMillis, useSaved = { c.settings.settings.first().useSavedExpressions }) {
                        c.settings.settings.first().autoExpand
                    }
                LibraryViewModel::class.java -> LibraryViewModel(c.repo, c.expansion, keyPhraseService = c.keyPhrases)
                ReviewViewModel::class.java -> ReviewViewModel(c.reviewSession())
                SettingsViewModel::class.java -> SettingsViewModel(c.settings, c.repo, c.ai, onReminderChanged = { on, hour -> c.reminders.apply(on, hour) })
                StatsViewModel::class.java -> StatsViewModel(c.repo)
                else -> error("unknown ViewModel")
            } as T
        }
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = settings?.let { ThemeMode.fromName(it.themeMode) }
            val dark = (themeMode ?: ThemeMode.SYSTEM).isDark(androidx.compose.foundation.isSystemInDarkTheme())
            // 상태 표시줄·내비게이션 바 아이콘 색도 앱 밝기를 따른다(휴대폰과 다르게 고정했을 때).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                )
                onDispose {}
            }
            LaunchedEffect(themeMode) { themeMode?.let(::applyNightMode) }
            AppTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: false) {
                // 회전·프로세스 재생성 뒤에도 보던 탭을 유지한다.
                var tab by rememberSaveable { mutableStateOf<Tab?>(null) }
                // 키가 없으면 첫 화면을 설정으로 안내한다
                val reviewVm: ReviewViewModel = viewModel(factory = factory)
                val translateVm: TranslateViewModel = viewModel(factory = factory)
                val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                LaunchedEffect(pendingLaunch) {
                    when (val r = pendingLaunch ?: return@LaunchedEffect) {
                        is LaunchRequest.Shared -> { translateVm.receiveShared(r.text); tab = Tab.TRANSLATE }
                        LaunchRequest.Review -> { reviewVm.refresh(force = true); tab = Tab.REVIEW }
                        is LaunchRequest.Compose -> { translateVm.setMode(r.mode); tab = Tab.TRANSLATE }
                        LaunchRequest.PasteTranslate -> {
                            tab = Tab.TRANSLATE
                            translateVm.setMode(com.chatlearn.ui.translate.TranslateMode.RECEIVED)
                            // Android 10부터는 창이 포커스를 얻은 뒤에만 클립보드를 읽을 수 있다.
                            snapshotFlow { windowInfo.isWindowFocused }.first { it }
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { translateVm.pasteAndTranslate(it) }
                        }
                    }
                    pendingLaunch = null
                }
                // 첫 탭은 설정을 읽은 뒤 한 번만 정한다. 키를 저장하는 순간 탭이 바뀌거나, 읽기 전에 번역 탭이 잠깐 보이지 않게.
                LaunchedEffect(settings == null) {
                    if (tab == null) settings?.let { tab = if (it.apiKey.isBlank()) Tab.SETTINGS else Tab.TRANSLATE }
                }
                val current = tab ?: return@AppTheme
                // 탭을 오갈 때 스크롤·필터·선택 같은 화면 상태를 탭마다 따로 보관한다.
                val tabStates = rememberSaveableStateHolder()
                // 첫 화면이 아닌 탭에서 뒤로가기를 누르면 앱을 끝내지 않고 번역 탭으로 돌아간다.
                BackHandler(enabled = current != Tab.TRANSLATE) { tab = Tab.TRANSLATE }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = current == t, onClick = { tab = t }, label = { Text(t.label) },
                                    icon = {
                                        Icon(
                                            when (t) {
                                                Tab.TRANSLATE -> Icons.Default.Translate
                                                Tab.LIBRARY -> Icons.Default.Book
                                                Tab.REVIEW -> Icons.Default.Repeat
                                                Tab.SETTINGS -> Icons.Default.Settings
                                            }, null,
                                        )
                                    },
                                )
                            }
                        }
                    },
                ) { pad ->
                    Surface(Modifier.padding(pad).consumeWindowInsets(pad)) {
                        Crossfade(targetState = current, animationSpec = tween(200), label = "tab") { shown ->
                          tabStates.SaveableStateProvider(shown.name) {
                            when (shown) {
                                Tab.TRANSLATE -> TranslateScreen(translateVm, onSpeak = { text, lang -> c.tts.speak(text, lang, false) }, onOpenSettings = { tab = Tab.SETTINGS })
                                Tab.LIBRARY -> LibraryScreen(
                                    viewModel(factory = factory), onGoTranslate = { tab = Tab.TRANSLATE },
                                    showGestureHint = settings?.libraryHintDismissed == false,
                                    onDismissGestureHint = { lifecycleScope.launch { c.settings.dismissLibraryHint() } },
                                ) { ids, lang ->
                                    reviewVm.startWith(ids, lang)
                                    tab = Tab.REVIEW
                                }
                                Tab.REVIEW -> ReviewScreen(reviewVm, viewModel(factory = factory), settings?.voice() ?: VoicePrefs())
                                Tab.SETTINGS -> SettingsScreen(viewModel(factory = factory), onPreviewVoice = { lang -> c.tts.speak(voiceSample(lang), lang, false) })
                            }
                          }
                        }
                    }
                }
            }
        }
    }
}
