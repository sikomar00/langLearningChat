package com.chatlearn.ui.onboarding

import com.chatlearn.ui.components.SecondaryButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chatlearn.ui.components.ButtonSpinner
import com.chatlearn.ui.components.ChatBubble
import com.chatlearn.ui.components.InfoMessage
import com.chatlearn.ui.components.MessageKind
import com.chatlearn.ui.settings.SettingsViewModel
import com.chatlearn.ui.settings.connectionMessage
import kotlinx.coroutines.launch

/** 첫 실행 안내에서 마지막에 번역해 볼 예시 문장. */
const val ONBOARDING_EXAMPLE = "Want to grab coffee later?"

/**
 * 첫 실행 한 번: 무엇을 하는 앱인지 → 메시지가 어디로 가는지 → 키 연결 → 예시 문장 번역.
 * 어느 단계에서든 "나중에"로 건너뛸 수 있다. [onFinish]의 인자가 true면 예시 문장을 번역해 본다.
 */
@Composable
fun OnboardingScreen(vm: SettingsViewModel, onFinish: (tryExample: Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var connected by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step > 0) { step-- }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("${step + 1} / 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (step < 3) TextButton(onClick = { onFinish(false) }) { Text("나중에") }
        }
        AnimatedContent(step, Modifier.weight(1f), label = "onboarding") { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (st) {
                    0 -> Welcome()
                    1 -> Privacy()
                    2 -> KeySetup(vm, onConnected = { connected = true; step = 3 })
                    else -> TryIt(connected)
                }
            }
        }
        when (step) {
            0, 1 -> Button(shape = MaterialTheme.shapes.medium, onClick = { step++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (step == 0) "시작하기" else "알겠어요") }
            2 -> SecondaryButton(shape = MaterialTheme.shapes.medium, onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("키는 나중에 넣을게요") }
            else -> {
                // 키가 없으면 번역이 오류로 끝나므로 예시 번역은 키를 연결했을 때만.
                if (connected) {
                    Button(shape = MaterialTheme.shapes.medium, onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("이 문장 번역해 보기") }
                    TextButton(onClick = { onFinish(false) }, Modifier.fillMaxWidth()) { Text("바로 시작할게요") }
                } else Button(shape = MaterialTheme.shapes.medium, onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("시작하기") }
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

@Composable
private fun Welcome() {
    Title("친구와 나눈 말로\n배우는 영어·스페인어")
    ChatBubble(outgoing = false) { Text("Want to grab coffee later?", style = MaterialTheme.typography.titleMedium) }
    ChatBubble(outgoing = true) { Text("이따 커피 한잔할래? 라는 뜻이에요", style = MaterialTheme.typography.bodyLarge) }
    Text("친구에게 받은 메시지를 붙여 넣으면 뜻과 표현을 알려 주고, 저장한 문장을 매일 조금씩 복습해요. 내가 하고 싶은 말도 영어·스페인어로 바꿔 줘요.", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Privacy() {
    Title("메시지는 이렇게 다뤄져요")
    Text("번역과 채점은 Google의 Gemini AI가 해요. 그래서 번역하거나 채점할 때 그 문장이 Google로 보내져요.", style = MaterialTheme.typography.bodyLarge)
    Text("무료 키로 보낸 내용은 Google이 서비스를 개선하는 데 쓸 수 있다고 안내하고 있어요. 이름·전화번호 같은 개인 정보는 빼고 붙여 넣어 주세요.", style = MaterialTheme.typography.bodyLarge)
    Text("저장한 문장과 복습 기록은 이 휴대폰에만 있어요. 백업 파일로 내보낼 수 있어요.", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun KeySetup(vm: SettingsViewModel, onConnected: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Title("Gemini 키 연결")
    Text("Google AI Studio에서 무료 키를 만들어 붙여 넣어 주세요. 1분이면 돼요.", style = MaterialTheme.typography.bodyLarge)
    SecondaryButton(shape = MaterialTheme.shapes.medium, onClick = { runCatching { uriHandler.openUri("https://aistudio.google.com/apikey") } }) { Text("키 발급 페이지 열기") }
    OutlinedTextField(
        key, { key = it; error = null }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("API 키") }, visualTransformation = PasswordVisualTransformation(),
    )
    Button(shape = MaterialTheme.shapes.medium, enabled = key.isNotBlank() && !checking, onClick = {
        checking = true
        scope.launch {
            val e = try {
                vm.saveKeyNow(key)
                vm.checkConnection()
            } catch (t: Exception) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                com.chatlearn.ai.AiError.SERVER
            } finally {
                checking = false
            }
            if (e == null) onConnected() else error = connectionMessage(e)
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { if (checking) ButtonSpinner() else Text("연결 확인") }
    error?.let { InfoMessage(it, MessageKind.ERROR) }
}

@Composable
private fun TryIt(connected: Boolean) {
    Title("준비됐어요!")
    if (connected) {
        Text("이 문장으로 한번 해 볼까요? 결과에서 단어를 누르면 뜻도 볼 수 있어요.", style = MaterialTheme.typography.bodyLarge)
        ChatBubble(outgoing = false) { Text(ONBOARDING_EXAMPLE, style = MaterialTheme.typography.titleMedium) }
    } else {
        Text("번역하려면 Gemini 키가 필요해요. 오늘 화면의 톱니 > AI에서 언제든 넣을 수 있어요.", style = MaterialTheme.typography.bodyLarge)
    }
    Text("복습 알림은 설정 > 복습에서 켤 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
