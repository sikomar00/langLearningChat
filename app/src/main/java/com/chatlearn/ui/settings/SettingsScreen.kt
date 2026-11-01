package com.chatlearn.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var perDay by remember(s.newPerDay) { mutableStateOf(s.newPerDay.toString()) }
    var goalText by remember(s.dailyGoal) { mutableStateOf(s.dailyGoal.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportJson()
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            message = "내보냈어요."
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }.orEmpty() }
            message = vm.importJson(text).fold({ "가져왔어요." }, { it.message ?: "가져오지 못했어요. 기존 데이터는 그대로예요." })
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gemini API 키", style = MaterialTheme.typography.titleMedium)
        Text("Google AI Studio에서 무료 키를 발급받아 붙여넣으세요.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), label = { Text("API 키") })
        Button(onClick = { vm.saveKey(key); message = "키를 저장했어요." }) { Text("키 저장") }

        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("모델 이름") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("gemini-flash-latest", "gemini-flash-lite-latest").forEach { preset ->
                FilterChip(selected = model == preset, onClick = { model = preset }, label = { Text(preset) })
            }
        }
        Button(onClick = { vm.saveModel(model); message = "모델을 저장했어요." }) { Text("모델 저장") }

        var checking by remember { mutableStateOf(false) }
        var checkResult by remember { mutableStateOf<String?>(null) }
        OutlinedButton(enabled = !checking, onClick = {
            checking = true; checkResult = null
            scope.launch {
                checkResult = vm.checkConnection()?.let(::connectionMessage) ?: "연결됐어요. 키와 모델이 잘 동작해요."
                checking = false
            }
        }) { Text(if (checking) "확인 중…" else "연결 확인") }
        Text("저장한 키와 모델로 확인해요. 먼저 저장해 주세요.", style = MaterialTheme.typography.bodySmall)
        checkResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        OutlinedTextField(perDay, { perDay = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("하루 새 카드 수") })
        Button(onClick = { vm.saveNewPerDay(perDay.toIntOrNull() ?: 10); message = "저장했어요." }) { Text("새 카드 수 저장") }

        OutlinedTextField(goalText, { goalText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("하루 복습 목표(장, 0이면 끔)") })
        Button(onClick = { vm.saveDailyGoal(goalText.toIntOrNull() ?: 10); message = "목표를 저장했어요." }) { Text("목표 저장") }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("저장할 때 다른 언어도 만들기")
                Text("영어 문장이면 스페인어 카드도, 스페인어 문장이면 영어 카드도 함께 만들어요.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.autoExpand, onCheckedChange = { vm.saveAutoExpand(it) })
        }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) message = "알림 권한이 꺼져 있어요. 휴대폰 설정 > 앱 > ChatLearn > 알림에서 켜 주세요."
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("복습 알림")
                Text("정해둔 시각에 복습할 카드가 있으면 알려 줘요.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.reminderEnabled, onCheckedChange = { on ->
                vm.saveReminder(on, s.reminderHour)
                if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        if (s.reminderEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 12, 18, 20, 22).forEach { h ->
                    FilterChip(selected = s.reminderHour == h, onClick = { vm.saveReminder(true, h) }, label = { Text(if (h < 12) "오전 ${h}시" else if (h == 12) "낮 12시" else "오후 ${h - 12}시") })
                }
            }
        }

        Text("오늘 AI 호출: ${s.aiCallsToday}회")

        HorizontalDivider()
        Text("백업", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("chatlearn-backup.json") }) { Text("내보내기") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("가져오기") }
        }
        Text("가져오면 지금 있는 데이터가 백업 파일의 내용으로 바뀝니다.", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

private fun connectionMessage(e: com.chatlearn.ai.AiError) = when (e) {
    com.chatlearn.ai.AiError.BAD_KEY -> "API 키나 모델 이름이 올바르지 않아요."
    com.chatlearn.ai.AiError.OFFLINE -> "인터넷에 연결되어 있지 않아요."
    com.chatlearn.ai.AiError.TIMEOUT -> "응답이 너무 오래 걸려요. 잠시 후 다시 확인해 보세요."
    com.chatlearn.ai.AiError.RATE_LIMIT -> "한도에 도달했어요. 키는 유효해 보여요. 잠시 후 다시 시도해 주세요."
    com.chatlearn.ai.AiError.OVERLOADED -> "지금 이 모델에 요청이 몰려 있어요. 다른 모델로 바꿔 보세요."
    com.chatlearn.ai.AiError.BAD_FORMAT, com.chatlearn.ai.AiError.SERVER -> "AI 서버 응답에 문제가 있어요. 잠시 후 다시 시도해 주세요."
}
