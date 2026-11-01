package com.chatlearn.ui.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.ai.AiError
import com.chatlearn.domain.Lang

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(vm: TranslateViewModel, onSpeak: (String, Lang) -> Unit = { _, _ -> }) {
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
            trailingIcon = {
                if (s.input.isNotEmpty()) {
                    IconButton(onClick = { vm.clearInput() }) { Icon(Icons.Default.Clear, contentDescription = "입력 지우기") }
                }
            },
        )
        OutlinedButton(
            onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(vm::replaceInput) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("클립보드 내용으로 바꾸기") }
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

        s.error?.let {
            Text(errorText(it), color = MaterialTheme.colorScheme.error)
            if (it is TranslateError.Ai && it.kind != AiError.BAD_KEY) {
                OutlinedButton(onClick = { vm.translate() }, enabled = !s.loading) { Text("다시 시도") }
            }
        }
        s.notice?.let {
            Text(
                when (it) {
                    is Notice.Saved -> if (it.cards >= 2) "저장했어요. 영어·스페인어 카드 ${it.cards}장을 만들었어요." else "저장했어요."
                    Notice.Duplicate -> "이미 저장된 문장이에요."
                },
                color = MaterialTheme.colorScheme.primary,
            )
        }

        s.received?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        textChunks(s.source).forEach { chunk ->
                            Text(chunk, Modifier.clickable { vm.lookupWord(chunk) }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text("단어를 누르면 이 문장에서의 뜻을 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                    s.lookup?.let { l ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(l.word, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                when (l) {
                                    is LookupState.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    is LookupState.Failed -> Text("뜻을 가져오지 못했어요. 다시 눌러 보세요.", color = MaterialTheme.colorScheme.error)
                                    is LookupState.Done -> {
                                        Text(l.info.meaningKo, style = MaterialTheme.typography.bodyLarge)
                                        if (l.info.baseForm.isNotBlank() && !l.info.baseForm.equals(l.word, ignoreCase = true)) {
                                            Text("기본형: ${l.info.baseForm}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (l.info.note.isNotBlank()) Text(l.info.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (l is LookupState.Done) TextButton(onClick = { vm.useAsKeyPhrase(l.word) }) { Text("핵심 표현으로 지정") }
                                    TextButton(onClick = { vm.dismissLookup() }) { Text("닫기") }
                                }
                            }
                        }
                    }
                    r.detectedLang?.let { l -> TextButton(onClick = { onSpeak(s.source, l) }) { Text("원문 듣기") } }
                    Text(r.koTranslation, style = MaterialTheme.typography.titleMedium)
                    Text(r.note, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        s.keyPhrase, vm::setKeyPhrase, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("핵심 표현(고칠 수 있어요)") },
                        supportingText = { Text("복습의 빈칸 채우기에서 이 표현을 맞히게 돼요.") },
                    )
                    Button(onClick = { vm.saveReceived() }) { Text("저장") }
                }
            }
        }
        s.candidates.forEach { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.text, style = MaterialTheme.typography.titleMedium)
                    Text(c.toneNote, style = MaterialTheme.typography.bodyMedium)
                    if (c.usedPhrase.isNotBlank() && c.text.contains(c.usedPhrase, ignoreCase = true)) {
                        Text("✨ 내가 저장한 표현을 썼어요: ${c.usedPhrase}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (c.keyPhrase.isNotBlank() && c.text.contains(c.keyPhrase, ignoreCase = true)) {
                        Text("핵심 표현: ${c.keyPhrase}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { onSpeak(c.text, s.targetLang) }) { Text("들어보기") }
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
