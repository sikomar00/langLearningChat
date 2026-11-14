package com.chatlearn.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.notion.NotionError
import com.chatlearn.ui.components.ButtonSpinner
import com.chatlearn.ui.components.InfoMessage
import com.chatlearn.ui.components.MessageKind
import kotlinx.coroutines.launch

/** 노션 내보내기 설정: 통합 토큰(기기 암호화 저장소)과 데이터베이스 링크, 연결 확인. */
@Composable
fun NotionSettings(vm: SettingsViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(vm.notionToken()) }
    var link by remember(s.notionDatabase) { mutableStateOf(s.notionDatabase) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    Text("노션", style = MaterialTheme.typography.titleMedium)
    Text(
        "라이브러리에서 고른 문장을 노션 데이터베이스에 한 줄씩 보낼 수 있어요. 노션에서 내부 통합을 만들어 토큰을 붙여 넣고, " +
            "데이터베이스 메뉴의 \"연결\"에서 그 통합을 추가한 뒤 데이터베이스 링크를 붙여 넣어 주세요.",
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = { runCatching { uriHandler.openUri("https://www.notion.so/profile/integrations") } }, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text("노션 통합 만들기 페이지 열기")
    }
    OutlinedTextField(token, { token = it; result = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("통합 토큰") }, visualTransformation = PasswordVisualTransformation())
    OutlinedTextField(link, { link = it; result = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("데이터베이스 링크") })
    Text(
        "속성 이름이 문장(제목)·뜻·언어·종류·사람·나라·태그·문법·메모·저장일이면 그 칸을 채워요. 없는 칸은 건너뛰어요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(enabled = token.isNotBlank() && link.isNotBlank() && !checking, onClick = {
        checking = true
        scope.launch {
            result = try {
                vm.saveNotion(token, link)
                vm.checkNotion(link).fold({ "연결됐어요. 데이터베이스의 칸 ${it}개를 찾았어요." to true }, { notionMessage(it) to false })
            } finally {
                checking = false
            }
        }
    }) { if (checking) ButtonSpinner() else Text("저장하고 연결 확인") }
    result?.let { (msg, ok) -> InfoMessage(msg, if (ok) MessageKind.SUCCESS else MessageKind.ERROR) }
}

internal fun notionMessage(e: Throwable): String = when ((e as? com.chatlearn.notion.NotionException)?.kind) {
    NotionError.BAD_TOKEN -> "토큰이 맞지 않아요. 통합 페이지에서 다시 복사해 주세요."
    NotionError.NOT_SHARED -> "데이터베이스를 찾지 못했어요. 링크를 확인하고, 데이터베이스의 \"연결\"에 통합을 추가해 주세요."
    NotionError.OFFLINE -> "인터넷에 연결되어 있지 않아요."
    NotionError.RATE_LIMIT -> "노션 요청이 너무 많았어요. 잠시 후 다시 시도해 주세요."
    else -> "노션 서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요."
}
