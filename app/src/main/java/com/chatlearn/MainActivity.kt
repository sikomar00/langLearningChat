package com.chatlearn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import com.chatlearn.stats.StatsViewModel
import com.chatlearn.ui.library.LibraryScreen
import com.chatlearn.ui.library.LibraryViewModel
import com.chatlearn.ui.review.ReviewScreen
import com.chatlearn.ui.review.ReviewViewModel
import com.chatlearn.ui.settings.SettingsScreen
import com.chatlearn.ui.settings.SettingsViewModel
import com.chatlearn.ui.translate.TranslateScreen
import com.chatlearn.ui.translate.TranslateViewModel

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 회전 등으로 다시 만들어질 때 같은 문장을 또 번역하지 않는다.
        if (savedInstanceState == null) pendingLaunch = launchRequestOf(intent)
        val c = (application as ChatLearnApp).container
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
                SettingsViewModel::class.java -> SettingsViewModel(c.settings, c.repo, c.ai) { on, hour -> c.reminders.apply(on, hour) }
                StatsViewModel::class.java -> StatsViewModel(c.repo)
                else -> error("unknown ViewModel")
            } as T
        }
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            AppTheme(dynamicColor = settings?.dynamicColor ?: false) {
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
                val current = tab ?: if (settings != null && settings!!.apiKey.isBlank()) Tab.SETTINGS else Tab.TRANSLATE
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
                            when (shown) {
                                Tab.TRANSLATE -> TranslateScreen(translateVm, onSpeak = { text, lang -> c.tts.speak(text, lang, false) }, onOpenSettings = { tab = Tab.SETTINGS })
                                Tab.LIBRARY -> LibraryScreen(viewModel(factory = factory), onGoTranslate = { tab = Tab.TRANSLATE }) { ids, lang ->
                                    reviewVm.startWith(ids, lang)
                                    tab = Tab.REVIEW
                                }
                                Tab.REVIEW -> ReviewScreen(reviewVm, viewModel(factory = factory))
                                Tab.SETTINGS -> SettingsScreen(viewModel(factory = factory))
                            }
                        }
                    }
                }
            }
        }
    }
}
