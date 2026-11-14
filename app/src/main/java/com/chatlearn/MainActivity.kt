package com.chatlearn

import com.chatlearn.ui.components.BoardSearchBar
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import com.chatlearn.ui.components.BoardTopBar
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.theme.statusColors
import com.chatlearn.ui.translate.TranslateScreen
import com.chatlearn.ui.translate.TranslateViewModel
import com.chatlearn.ui.translate.canReturnText
import com.chatlearn.ui.today.TodayScreen
import com.chatlearn.ui.onboarding.ONBOARDING_EXAMPLE
import com.chatlearn.ui.onboarding.OnboardingScreen
import com.chatlearn.ui.today.TodayViewModel
import com.chatlearn.stats.StatsScreen
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

// androidx.activity의 enableEdgeToEdge 기본 내비게이션 바 스크림과 같은 값.
private val LIGHT_NAV_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private enum class Tab(val label: String) { TODAY("오늘"), TRANSLATE("번역"), LIBRARY("라이브러리"), GROWTH("성장") }

/** 탭 위에 전체 화면으로 덮어 여는 곳. 뒤로가기로 닫으면 보던 탭으로 돌아간다. */
private enum class Overlay { REVIEW, SETTINGS, ONBOARDING }

class MainActivity : ComponentActivity() {
    // 공유·선택 메뉴로 넘어온 문장이나 알림·바로가기·위젯이 시킨 일. 화면이 처리하면 비운다.
    private var pendingLaunch by mutableStateOf<LaunchRequest?>(null)
    // 텍스트 선택 메뉴(고칠 수 있는 칸)로 열렸으면 고른 보낼 말을 그 칸에 돌려준다.
    private var returnsText by mutableStateOf(false)

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
        // 결과를 기다리는 앱(startActivityForResult)이 있을 때만. 이 화면이 열린 처음 요청 기준으로 정한다.
        returnsText = canReturnText(intent) && callingActivity != null
        val c = (application as ChatLearnApp).container
        c.warmUpTts()
        if (savedInstanceState == null) c.runAutoBackupIfDue()
        // 예약이 사라졌을 수 있으니(강제 종료, 설정 변경) 앱을 열 때마다 설정대로 다시 맞춘다.
        lifecycleScope.launch { c.settings.settings.first().let { c.reminders.apply(it.reminderEnabled, it.reminderHour) } }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                TranslateViewModel::class.java ->
                    TranslateViewModel(
                        c.ai, c.repo, System::currentTimeMillis,
                        useSaved = { c.settings.settings.first().useSavedExpressions },
                        autoExpand = { c.settings.settings.first().autoExpand },
                        savedTarget = { if (c.settings.settings.first().sentTarget == "ES") com.chatlearn.domain.Lang.ES else com.chatlearn.domain.Lang.EN },
                        rememberTarget = { c.settings.setSentTarget(it.name) },
                    )
                LibraryViewModel::class.java -> LibraryViewModel(c.repo, c.expansion, keyPhraseService = c.keyPhrases, notion = c.notion)
                ReviewViewModel::class.java -> ReviewViewModel(c.reviewSession())
                SettingsViewModel::class.java -> SettingsViewModel(
                    c.settings, c.repo, c.ai, onReminderChanged = { on, hour -> c.reminders.apply(on, hour) },
                    notionTokenStore = c.notionToken, notion = c.notionClient,
                )
                StatsViewModel::class.java -> StatsViewModel(c.repo)
                TodayViewModel::class.java -> TodayViewModel(c.reviewSession(), c.repo)
                else -> error("unknown ViewModel")
            } as T
        }
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = settings?.let { ThemeMode.fromName(it.themeMode) }
            val dark = (themeMode ?: ThemeMode.SYSTEM).isDark(androidx.compose.foundation.isSystemInDarkTheme())
            LaunchedEffect(themeMode) { themeMode?.let(::applyNightMode) }
            AppTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: false) {
                // 회전·프로세스 재생성 뒤에도 보던 탭과 덮어 연 화면을 유지한다.
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
                // 검은 안내판 막대가 맨 위에 있는 동안(탭·설정·복습) 상태 표시줄 아이콘은 흰색, 첫 안내 화면만 앱 밝기를 따른다.
                val boardOnTop = overlay != Overlay.ONBOARDING
                DisposableEffect(dark, boardOnTop, overlay == null) {
                    enableEdgeToEdge(
                        statusBarStyle = if (boardOnTop) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                        navigationBarStyle = if (overlay == null) SystemBarStyle.dark(DARK_NAV_SCRIM)
                        else SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                    )
                    onDispose {}
                }
                var firstRunChecked by rememberSaveable { mutableStateOf(false) }
                val reviewVm: ReviewViewModel = viewModel(factory = factory)
                val translateVm: TranslateViewModel = viewModel(factory = factory)
                val todayVm: TodayViewModel = viewModel(factory = factory)
                val libraryVm: LibraryViewModel = viewModel(factory = factory)
                val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                fun openReview(start: () -> Unit) { start(); overlay = Overlay.REVIEW }
                // 약한 문법 연습과 답장 연습은 번역 탭의 써 보기로 연다.
                val practice: (com.chatlearn.domain.GrammarPoint) -> Unit = { p -> translateVm.startPractice(p); tab = Tab.TRANSLATE }
                val replyTo by libraryVm.replyRequest.collectAsStateWithLifecycle()
                LaunchedEffect(replyTo) {
                    replyTo?.let { (text, lang) -> translateVm.startReply(text, lang); libraryVm.replyRequest.value = null; tab = Tab.TRANSLATE }
                }
                LaunchedEffect(pendingLaunch) {
                    when (val r = pendingLaunch ?: return@LaunchedEffect) {
                        is LaunchRequest.Shared -> { translateVm.receiveShared(r.text, editable = r.editable && returnsText); overlay = null; tab = Tab.TRANSLATE }
                        LaunchRequest.Review -> openReview { reviewVm.open() }
                        is LaunchRequest.Compose -> { translateVm.setMode(r.mode); overlay = null; tab = Tab.TRANSLATE }
                        LaunchRequest.PasteTranslate -> {
                            overlay = null; tab = Tab.TRANSLATE
                            translateVm.setMode(com.chatlearn.ui.translate.TranslateMode.RECEIVED)
                            // Android 10부터는 창이 포커스를 얻은 뒤에만 클립보드를 읽을 수 있다.
                            snapshotFlow { windowInfo.isWindowFocused }.first { it }
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { translateVm.pasteAndTranslate(it) }
                        }
                    }
                    pendingLaunch = null
                }
                // 키가 없으면 처음 한 번만 설정을 연다. 공유·알림으로 열려 이미 다른 화면을 띄웠으면 그 화면을 덮지 않는다.
                // 설정을 읽기 전에는 아무것도 그리지 않아 화면이 튀지 않는다.
                LaunchedEffect(settings == null) {
                    if (firstRunChecked) return@LaunchedEffect
                    settings?.let {
                        // 키를 이미 넣은 예전 사용자에게는 안내를 보이지 않는다.
                        if (!it.onboardingDone && it.apiKey.isBlank() && tab == Tab.TODAY && overlay == null && pendingLaunch == null) overlay = Overlay.ONBOARDING
                        firstRunChecked = true
                    }
                }
                if (!firstRunChecked) return@AppTheme
                // 탭을 오갈 때 스크롤·필터·선택 같은 화면 상태를 탭마다 따로 보관한다.
                val tabStates = rememberSaveableStateHolder()
                val closeOverlay = { overlay = null; todayVm.refresh(); Unit }
                when (overlay) {
                    Overlay.REVIEW -> Surface(Modifier.fillMaxSize()) { ReviewScreen(reviewVm, settings?.voice() ?: VoicePrefs(), onClose = closeOverlay) }
                    Overlay.ONBOARDING -> Surface(Modifier.fillMaxSize()) {
                        val settingsVm: SettingsViewModel = viewModel(factory = factory)
                        OnboardingScreen(settingsVm) { tryExample ->
                            settingsVm.finishOnboarding()
                            overlay = null
                            if (tryExample) { tab = Tab.TRANSLATE; translateVm.receiveShared(ONBOARDING_EXAMPLE) }
                        }
                    }
                    Overlay.SETTINGS -> Surface(Modifier.fillMaxSize()) {
                        BackHandler(onBack = closeOverlay)
                        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                            BoardTopBar("설정", navigationIcon = {
                                IconButton(onClick = closeOverlay) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                            })
                            SettingsScreen(viewModel(factory = factory), onPreviewVoice = { lang -> c.tts.speak(voiceSample(lang), lang, false) })
                        }
                    }
                    null -> {
                        // 첫 화면(오늘)이 아닌 탭에서 뒤로가기를 누르면 앱을 끝내지 않고 오늘 탭으로 돌아간다.
                        BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }
                        val todayUi = todayVm.ui.collectAsStateWithLifecycle().value
                        val due = todayUi?.due ?: 0
                        val world = MaterialTheme.statusColors
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = world.onFlap, indicatorColor = world.flap,
                            selectedTextColor = world.flap, unselectedIconColor = world.onBoardMuted, unselectedTextColor = world.onBoardMuted,
                        )
                        Scaffold(
                            topBar = {
                                val query by libraryVm.query.collectAsStateWithLifecycle()
                                if (tab == Tab.LIBRARY) BoardSearchBar(query, { libraryVm.query.value = it }) {
                                    IconButton(onClick = { overlay = Overlay.SETTINGS }) { Icon(Icons.Default.Settings, contentDescription = "설정") }
                                } else BoardTopBar(tab.label) {
                                    todayUi?.streak?.takeIf { it > 0 && tab == Tab.TODAY }?.let { BoardLabel("${it}일 연속", color = world.onBoardMuted) }
                                    IconButton(onClick = { overlay = Overlay.SETTINGS }) { Icon(Icons.Default.Settings, contentDescription = "설정") }
                                }
                            },
                            bottomBar = {
                                NavigationBar(containerColor = world.board) {
                                    Tab.entries.forEach { t ->
                                        NavigationBarItem(
                                            selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }, colors = navColors,
                                            icon = {
                                                val icon = when (t) {
                                                    Tab.TODAY -> if (tab == t) Icons.Filled.WbSunny else Icons.Outlined.WbSunny
                                                    Tab.TRANSLATE -> if (tab == t) Icons.Filled.Forum else Icons.Outlined.Forum
                                                    Tab.LIBRARY -> if (tab == t) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks
                                                    Tab.GROWTH -> if (tab == t) Icons.Filled.Insights else Icons.Outlined.Insights
                                                }
                                                if (t == Tab.TODAY && due > 0 && tab != Tab.TODAY) BadgedBox(badge = { Badge(containerColor = world.flap, contentColor = world.onFlap) { Text("$due") } }) { Icon(icon, contentDescription = "복습할 카드 ${due}장") }
                                                else Icon(icon, contentDescription = null)
                                            },
                                        )
                                    }
                                }
                            },
                        ) { pad ->
                            Surface(Modifier.padding(pad).consumeWindowInsets(pad)) {
                                Crossfade(targetState = tab, animationSpec = tween(200), label = "tab") { shown ->
                                    tabStates.SaveableStateProvider(shown.name) {
                                        when (shown) {
                                            Tab.TODAY -> TodayScreen(
                                                todayVm,
                                                onStartReview = { openReview { reviewVm.open() } },
                                                onLearnMore = { openReview { reviewVm.learnMore(5) } },
                                                onGoTranslate = { tab = Tab.TRANSLATE },
                                                onOpenLibrary = { tab = Tab.LIBRARY },
                                                onPractice = practice,
                                            )
                                            Tab.TRANSLATE -> TranslateScreen(
                                                translateVm, onSpeak = { text, lang -> c.tts.speak(text, lang, false) }, onOpenSettings = { overlay = Overlay.SETTINGS },
                                                onOpenLibrary = { q -> libraryVm.showOnly(q); tab = Tab.LIBRARY },
                                                onReplace = if (returnsText) { text -> setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text)); finish() } else null,
                                            )
                                            Tab.LIBRARY -> LibraryScreen(
                                                libraryVm, onGoTranslate = { tab = Tab.TRANSLATE },
                                                showGestureHint = settings?.libraryHintDismissed == false,
                                                onDismissGestureHint = { lifecycleScope.launch { c.settings.dismissLibraryHint() } },
                                            ) { ids, lang -> openReview { reviewVm.startWith(ids, lang) } }
                                            Tab.GROWTH -> Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { StatsScreen(viewModel(factory = factory), onPractice = practice, onOpenSentence = { id -> libraryVm.open(id); tab = Tab.LIBRARY }) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
