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
        Button(onClick = { vm.saveModel(model); message = "모델을 저장했어요." }) { Text("모델 저장") }

        OutlinedTextField(perDay, { perDay = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("하루 새 카드 수") })
        Button(onClick = { vm.saveNewPerDay(perDay.toIntOrNull() ?: 10); message = "저장했어요." }) { Text("새 카드 수 저장") }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("저장할 때 다른 언어도 만들기")
                Text("영어 문장이면 스페인어 카드도, 스페인어 문장이면 영어 카드도 함께 만들어요.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.autoExpand, onCheckedChange = { vm.saveAutoExpand(it) })
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
