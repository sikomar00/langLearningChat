package com.chatlearn.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.chatlearn.ui.components.InfoMessage
import com.chatlearn.ui.components.MessageKind
import com.chatlearn.ui.components.SwitchRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onPreviewVoice: (com.chatlearn.domain.Lang) -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val entryCount by vm.entryCount.collectAsStateWithLifecycle()
    val backupDue = com.chatlearn.settings.backupOverdue(s.lastBackupAt, vm.now(), entryCount)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val r = vm.export { text ->
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("파일을 열 수 없어요")
                }
            }
            message = if (r.isSuccess) "내보냈어요. 이 파일을 클라우드나 다른 기기에 보관해 두세요." else "내보내지 못했어요. 다른 위치를 골라 다시 해 보세요."
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // 기존 데이터를 덮어쓰므로 한 번 더 확인받는다.
        if (uri != null) pendingImport = uri
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("AI", "복습", "문장", "화면", "백업")
    Column(Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = tab) {
        tabs.forEachIndexed { i, t ->
            Tab(selected = tab == i, onClick = { tab = i }, text = {
                if (i == 4 && backupDue) BadgedBox(badge = { Badge() }) { Text(t) } else Text(t)
            })
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (tab == 0) {
        Text("Gemini API 키", style = MaterialTheme.typography.titleMedium)
        Text("Google AI Studio에서 무료 키를 발급받아 붙여넣으세요.", style = MaterialTheme.typography.bodySmall)
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        TextButton(onClick = { runCatching { uriHandler.openUri("https://aistudio.google.com/apikey") } }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text("키 발급 페이지 열기")
        }
        var showKey by remember { mutableStateOf(false) }
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("API 키") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showKey) "API 키 숨기기" else "API 키 보기")
                }
            },
        )
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("모델 이름") })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("gemini-flash-latest", "gemini-flash-lite-latest").forEach { preset ->
                FilterChip(selected = model == preset, onClick = { model = preset }, label = { Text(preset) })
            }
        }
        Button(onClick = { vm.saveKey(key); vm.saveModel(model); message = "저장했어요." }, Modifier.fillMaxWidth()) { Text("저장") }

        var checking by remember { mutableStateOf(false) }
        var checkResult by remember { mutableStateOf<String?>(null) }
        var checkOk by remember { mutableStateOf(false) }
        OutlinedButton(enabled = !checking, onClick = {
            checking = true; checkResult = null
            scope.launch {
                // 입력한 값으로 확인하도록 먼저 저장을 끝낸다.
                vm.saveKey(key).join(); vm.saveModel(model).join()
                val err = vm.checkConnection()
                checkOk = err == null
                checkResult = err?.let(::connectionMessage) ?: "연결됐어요. 키와 모델이 잘 동작해요."
                checking = false
            }
        }) { Text(if (checking) "확인 중…" else "연결 확인") }
        Text("입력한 키와 모델을 저장한 뒤 확인해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        checkResult?.let { InfoMessage(it, if (checkOk) MessageKind.SUCCESS else MessageKind.ERROR) }

    }
    if (tab == 1) {
        com.chatlearn.ui.components.Stepper("하루 새 카드", s.newPerDay, { vm.saveNewPerDay(it) }, com.chatlearn.ui.components.StepperSpec(0..100, 5))
        com.chatlearn.ui.components.Stepper("하루 복습 목표", s.dailyGoal, { vm.saveDailyGoal(it) }, com.chatlearn.ui.components.StepperSpec(0..200, 5, "끔"))

        Text("기억 목표", style = MaterialTheme.typography.labelLarge)
        Text("복습할 때 이만큼은 기억나도록 간격을 정해요. 높일수록 더 자주 복습해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.85 to "85% 여유", 0.9 to "90% 보통", 0.95 to "95% 꼼꼼").forEach { (r, label) ->
                FilterChip(selected = kotlin.math.abs(s.retention - r) < 0.001, onClick = { vm.saveRetention(r) }, label = { Text(label) })
            }
        }
        Text("하루 최대 복습", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 200).forEach { n ->
                FilterChip(selected = s.maxReviews == n, onClick = { vm.saveMaxReviews(n) }, label = { Text("${n}장") })
            }
        }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) message = "알림 권한이 꺼져 있어요. 휴대폰 설정 > 앱 > ChatLearn > 알림에서 켜 주세요."
        }
        SwitchRow("복습 알림", "정해둔 시각에 복습할 카드가 있으면 알려 줘요.", s.reminderEnabled, { on ->
            vm.saveReminder(on, s.reminderHour)
            if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        })
        if (s.reminderEnabled) {
            Text("알림 시각", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 12, 18, 20, 22).forEach { h ->
                    FilterChip(selected = s.reminderHour == h, onClick = { vm.saveReminder(true, h) }, label = { Text(if (h < 12) "오전 ${h}시" else if (h == 12) "낮 12시" else "오후 ${h - 12}시") })
                }
            }
            Text("알림 받을 요일", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, d ->
                    val on = s.reminderDays and (1 shl i) != 0
                    FilterChip(selected = on, onClick = {
                        val next = s.reminderDays xor (1 shl i)
                        // 전부 끄면 알림이 영영 오지 않으니 최소 하루는 남긴다.
                        if (next != 0) vm.saveReminderDays(next)
                    }, label = { Text(d) })
                }
            }
            Text("알림 말투", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.chatlearn.reminder.ReminderTone.entries.forEach { t ->
                    FilterChip(selected = s.reminderTone == t.name, onClick = { vm.saveReminderTone(t.name) }, label = { Text(t.label) })
                }
            }
            Text(
                "예: " + com.chatlearn.reminder.reminderText(5, 0, com.chatlearn.reminder.ReminderTone.fromName(s.reminderTone)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    }
    if (tab == 2) {
        SwitchRow("저장할 때 다른 언어도 만들기", "영어 문장이면 스페인어 카드도, 스페인어 문장이면 영어 카드도 함께 만들어요.", s.autoExpand, { vm.saveAutoExpand(it) })
        SwitchRow("보낼 말에 내 표현 쓰기", "보낼 말 후보를 만들 때 내가 저장한 핵심 표현을 자연스러울 때만 넣어 줘요.", s.useSavedExpressions, { vm.saveUseSaved(it) })

        Text("발음", style = MaterialTheme.typography.titleMedium)
        Text("듣기와 말로 답하기에 쓸 억양이에요. 친구가 쓰는 억양에 맞추면 좋아요.", style = MaterialTheme.typography.bodySmall)
        Text("영어", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            com.chatlearn.settings.EnAccent.entries.forEach { a ->
                FilterChip(selected = s.enAccent == a.name, onClick = { vm.saveEnAccent(a.name) }, label = { Text(a.label) })
            }
            TextButton(onClick = { onPreviewVoice(com.chatlearn.domain.Lang.EN) }) { Text("들어보기") }
        }
        Text("스페인어", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            com.chatlearn.settings.EsAccent.entries.forEach { a ->
                FilterChip(selected = s.esAccent == a.name, onClick = { vm.saveEsAccent(a.name) }, label = { Text(a.label) })
            }
            TextButton(onClick = { onPreviewVoice(com.chatlearn.domain.Lang.ES) }) { Text("들어보기") }
        }
        Text("말 빠르기", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.8f to "천천히", 1f to "보통", 1.2f to "빠르게").forEach { (rate, label) ->
                FilterChip(selected = kotlin.math.abs(s.speechRate - rate) < 0.01f, onClick = { vm.saveSpeechRate(rate) }, label = { Text(label) })
            }
        }
        Text("느리게 듣기는 고른 빠르기보다 더 천천히 들려줘요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (tab == 3) {
        Text("밝기", style = MaterialTheme.typography.titleMedium)
        val mode = com.chatlearn.settings.ThemeMode.fromName(s.themeMode)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = com.chatlearn.settings.ThemeMode.entries
            modes.forEachIndexed { i, m ->
                SegmentedButton(selected = mode == m, onClick = { vm.saveThemeMode(m.name) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(m.label) }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            SwitchRow("시스템 색 따르기", "배경화면에서 뽑은 색으로 앱 색을 바꿔요. 끄면 기본 보라색을 써요.", s.dynamicColor, { vm.saveDynamicColor(it) })
        } else {
            Text("이 기기(Android 12 미만)에서는 시스템 색을 쓸 수 없어요.", style = MaterialTheme.typography.bodyMedium)
        }

        Text("홈 화면 위젯", style = MaterialTheme.typography.titleMedium)
        Text("오늘 복습할 카드 수와 목표, 연속 학습일을 홈 화면에서 보고, 눌러서 바로 복습하거나 복사한 메시지를 번역해요.", style = MaterialTheme.typography.bodySmall)
        val canPin = remember { com.chatlearn.widget.canPinReviewWidget(ctx) }
        if (canPin) {
            OutlinedButton(onClick = {
                if (!com.chatlearn.widget.requestPinReviewWidget(ctx)) message = "위젯을 추가하지 못했어요. 홈 화면을 길게 눌러 위젯 목록에서 추가해 주세요."
            }) { Text("홈 화면에 위젯 추가") }
        } else {
            Text("홈 화면을 길게 누르고 위젯 > ChatLearn > 오늘의 복습을 골라 추가하세요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (tab == 4) {
        Text("오늘 AI 호출: ${s.aiCallsToday}회")

        Text("백업", style = MaterialTheme.typography.titleMedium)
        Text(com.chatlearn.settings.backupStatus(s.lastBackupAt, vm.now(), java.time.ZoneId.systemDefault()), style = MaterialTheme.typography.bodyMedium)
        if (backupDue) InfoMessage("앱 데이터는 휴대폰 백업에 들어가지 않아요. 폰을 바꾸거나 앱을 지우면 문장 ${entryCount}개가 사라지니 내보내기로 파일을 보관해 두세요.", MessageKind.ERROR)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("chatlearn-backup.json") }) { Text("내보내기") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("가져오기") }
        }
        Text("가져오면 지금 있는 데이터가 백업 파일의 내용으로 바뀝니다.", style = MaterialTheme.typography.bodySmall)
    }
        message?.let { InfoMessage(it, if (it.contains("못했") || it.contains("꺼져")) MessageKind.ERROR else MessageKind.SUCCESS) }
    }
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("백업 파일로 바꿀까요?") },
            text = { Text("지금 있는 데이터가 백업 파일의 내용으로 바뀌고 되돌릴 수 없어요. 먼저 내보내기로 현재 데이터를 저장해 두면 안전해요.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }.orEmpty() }
                        message = vm.importJson(text).fold({ "가져왔어요." }, { it.message ?: "가져오지 못했어요. 기존 데이터는 그대로예요." })
                    }
                }) { Text("가져오기") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("취소") } },
        )
    }
}

internal fun connectionMessage(e: com.chatlearn.ai.AiError) = when (e) {
    com.chatlearn.ai.AiError.BAD_KEY -> "API 키나 모델 이름이 올바르지 않아요."
    com.chatlearn.ai.AiError.OFFLINE -> "인터넷에 연결되어 있지 않아요."
    com.chatlearn.ai.AiError.TIMEOUT -> "응답이 너무 오래 걸려요. 잠시 후 다시 확인해 보세요."
    com.chatlearn.ai.AiError.RATE_LIMIT -> "요청이 잠깐 많았어요. 키는 유효해 보여요. 1분쯤 뒤에 다시 시도해 주세요."
    com.chatlearn.ai.AiError.DAILY_LIMIT -> "오늘 한도를 다 썼어요. 키는 유효해 보여요. 내일 다시 확인해 보세요."
    com.chatlearn.ai.AiError.BAD_MODEL -> "모델 이름을 찾을 수 없어요. 모델 이름을 확인해 주세요."
    com.chatlearn.ai.AiError.BLOCKED -> "키는 유효해 보여요. (확인 문장이 안전 필터에 걸렸어요)"
    com.chatlearn.ai.AiError.OVERLOADED -> "지금 이 모델에 요청이 몰려 있어요. 다른 모델로 바꿔 보세요."
    com.chatlearn.ai.AiError.BAD_FORMAT, com.chatlearn.ai.AiError.SERVER -> "AI 서버 응답에 문제가 있어요. 잠시 후 다시 시도해 주세요."
}
