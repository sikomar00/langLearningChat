package com.chatlearn.ui.translate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.ai.AiError
import com.chatlearn.domain.Lang

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(vm: TranslateViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TranslateMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = s.mode == m, onClick = { vm.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, TranslateMode.entries.size),
                ) { Text(if (m == TranslateMode.RECEIVED) "받은 말" else "보낼 말") }
            }
        }
        if (s.mode == TranslateMode.SENT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Lang.EN to "영어", Lang.ES to "스페인어").forEach { (l, label) ->
                    FilterChip(selected = s.targetLang == l, onClick = { vm.setTarget(l) }, label = { Text(label) })
                }
            }
        }
        OutlinedTextField(
            value = s.input, onValueChange = vm::setInput, modifier = Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(if (s.mode == TranslateMode.RECEIVED) "친구가 보낸 메시지를 붙여넣기" else "하고 싶은 말(한국어)") },
        )
        var tagFocused by remember { mutableStateOf(false) }
        val suggestions by vm.tagSuggestions.collectAsStateWithLifecycle()
        OutlinedTextField(
            value = s.tag, onValueChange = vm::setTag, singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { tagFocused = it.isFocused },
            label = { Text("태그(선택, 쉼표로 여러 개)") },
        )
        if (tagFocused && suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { tag ->
                    SuggestionChip(onClick = { vm.pickTag(tag) }, label = { Text(tag) })
                }
            }
        }
        Button(onClick = { vm.translate() }, enabled = s.input.isNotBlank() && !s.loading, modifier = Modifier.fillMaxWidth()) {
            if (s.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("번역")
        }

        s.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        s.notice?.let { Text(if (it == Notice.Saved) "저장했어요." else "이미 저장된 문장이에요.", color = MaterialTheme.colorScheme.primary) }

        s.received?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.koTranslation, style = MaterialTheme.typography.titleMedium)
                    Text(r.note, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.saveReceived() }) { Text("저장") }
                }
            }
        }
        s.candidates.forEach { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.text, style = MaterialTheme.typography.titleMedium)
                    Text(c.toneNote, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(c.text))
                        vm.onCandidateCopied(c)
                    }) { Text("복사하고 저장") }
                }
            }
        }
    }
}

private fun errorText(e: TranslateError) = when (e) {
    TranslateError.UnsupportedLang -> "영어나 스페인어 메시지만 저장할 수 있어요."
    is TranslateError.Ai -> when (e.kind) {
        AiError.RATE_LIMIT -> "오늘 AI 한도에 도달했어요. 잠시 후 다시 시도해 주세요."
        AiError.OFFLINE -> "인터넷에 연결되어 있지 않아요."
        AiError.TIMEOUT -> "AI 응답이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요."
        AiError.OVERLOADED -> "지금 이 AI 모델에 요청이 몰려 있어요. 잠시 후 다시 시도하거나, 설정에서 다른 모델 이름으로 바꿔 보세요."
        AiError.BAD_KEY -> "API 키를 확인해 주세요. 설정 탭에서 바꿀 수 있어요."
        AiError.BAD_FORMAT -> "AI 응답을 읽지 못했어요. 다시 시도해 주세요."
        AiError.SERVER -> "AI 서버에 문제가 생겼어요."
    }
}
