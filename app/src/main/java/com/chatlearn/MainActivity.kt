package com.chatlearn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.chatlearn.ui.translate.sharedTextOf

private enum class Tab(val label: String) { TRANSLATE("번역"), LIBRARY("라이브러리"), REVIEW("복습"), SETTINGS("설정") }

class MainActivity : ComponentActivity() {
    // 다른 앱에서 공유/선택 메뉴로 넘어온 문장. 화면이 받아 번역하면 비운다.
    private var pendingShare by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = sharedTextOf(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 회전 등으로 다시 만들어질 때 같은 문장을 또 번역하지 않는다.
        if (savedInstanceState == null) pendingShare = sharedTextOf(intent)
        val c = (application as ChatLearnApp).container
        // 예약이 사라졌을 수 있으니(강제 종료, 설정 변경) 앱을 열 때마다 설정대로 다시 맞춘다.
        lifecycleScope.launch { c.settings.settings.first().let { c.reminders.apply(it.reminderEnabled, it.reminderHour) } }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                TranslateViewModel::class.java ->
                    TranslateViewModel(c.ai, c.repo, System::currentTimeMillis) { c.settings.settings.first().autoExpand }
                LibraryViewModel::class.java -> LibraryViewModel(c.repo, c.expansion, keyPhraseService = c.keyPhrases)
                ReviewViewModel::class.java -> ReviewViewModel(c.reviewSession())
                SettingsViewModel::class.java -> SettingsViewModel(c.settings, c.repo, c.ai) { on, hour -> c.reminders.apply(on, hour) }
                StatsViewModel::class.java -> StatsViewModel(c.repo)
                else -> error("unknown ViewModel")
            } as T
        }
        setContent {
            MaterialTheme {
                val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                var tab by remember { mutableStateOf<Tab?>(null) }
                // 키가 없으면 첫 화면을 설정으로 안내한다
                val reviewVm: ReviewViewModel = viewModel(factory = factory)
                val translateVm: TranslateViewModel = viewModel(factory = factory)
                LaunchedEffect(pendingShare) {
                    pendingShare?.let { translateVm.receiveShared(it); tab = Tab.TRANSLATE; pendingShare = null }
                }
                val current = tab ?: if (settings != null && settings!!.apiKey.isBlank()) Tab.SETTINGS else Tab.TRANSLATE
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
                    Surface(Modifier.padding(pad)) {
                        when (current) {
                            Tab.TRANSLATE -> TranslateScreen(translateVm) { text, lang -> c.tts.speak(text, lang, false) }
                            Tab.LIBRARY -> LibraryScreen(viewModel(factory = factory)) { ids, lang ->
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
