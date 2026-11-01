package com.chatlearn

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
import androidx.lifecycle.viewmodel.compose.viewModel
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = (application as ChatLearnApp).container
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                TranslateViewModel::class.java -> TranslateViewModel(c.ai, c.repo, System::currentTimeMillis)
                LibraryViewModel::class.java -> LibraryViewModel(c.repo, c.expansion)
                ReviewViewModel::class.java -> ReviewViewModel(c.reviewSession())
                SettingsViewModel::class.java -> SettingsViewModel(c.settings, c.repo)
                StatsViewModel::class.java -> StatsViewModel(c.repo)
                else -> error("unknown ViewModel")
            } as T
        }
        setContent {
            MaterialTheme {
                val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                var tab by remember { mutableStateOf<Tab?>(null) }
                // 키가 없으면 첫 화면을 설정으로 안내한다
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
                            Tab.TRANSLATE -> TranslateScreen(viewModel(factory = factory))
                            Tab.LIBRARY -> LibraryScreen(viewModel(factory = factory))
                            Tab.REVIEW -> ReviewScreen(viewModel(factory = factory), viewModel(factory = factory))
                            Tab.SETTINGS -> SettingsScreen(viewModel(factory = factory))
                        }
                    }
                }
            }
        }
    }
}
