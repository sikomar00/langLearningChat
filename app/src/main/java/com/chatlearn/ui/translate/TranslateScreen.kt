package com.chatlearn.ui.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.chatlearn.ui.components.LabeledText
import com.chatlearn.ui.theme.statusColors
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.chatlearn.ui.components.ButtonSpinner
import com.chatlearn.ui.components.InfoMessage
import com.chatlearn.ui.components.MessageKind
import com.chatlearn.ui.components.SectionCard
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
import com.chatlearn.domain.splitMessages

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranslateScreen(vm: TranslateViewModel, onSpeak: (String, Lang) -> Unit = { _, _ -> }, onOpenSettings: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 복사만: Android 13부터는 시스템이 복사 알림을 띄우므로 그 아래 버전에서만 짧게 알린다.
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(ctx, "복사했어요", android.widget.Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TranslateMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = s.mode == m, onClick = { vm.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, TranslateMode.entries.size),
                ) { Text(when (m) { TranslateMode.RECEIVED -> "받은 말"; TranslateMode.SENT -> "보낼 말"; TranslateMode.CORRECT -> "첨삭" }) }
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
            label = {
                Text(when (s.mode) {
                    TranslateMode.RECEIVED -> "친구가 보낸 메시지를 붙여넣기"
                    TranslateMode.SENT -> "하고 싶은 말(한국어)"
                    TranslateMode.CORRECT -> "내가 쓴 영어·스페인어 문장"
                })
            },
            trailingIcon = {
                if (s.input.isNotEmpty()) {
                    IconButton(onClick = { vm.clearInput() }) { Icon(Icons.Default.Clear, contentDescription = "입력 지우기") }
                } else {
                    // 비어 있을 때는 붙여넣기: 붙여넣자마자 지금 모드로 번역(첨삭)까지 한다.
                    IconButton(onClick = {
                        val clip = clipboard.getText()?.text?.takeIf { it.isNotBlank() }
                        if (clip != null) vm.pasteAndTranslate(clip)
                        else android.widget.Toast.makeText(ctx, "클립보드가 비어 있어요", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentPaste, contentDescription = if (s.mode == TranslateMode.CORRECT) "붙여넣고 첨삭" else "붙여넣고 번역") }
                }
            },
        )
        var tagFocused by remember { mutableStateOf(false) }
        var showTag by rememberSaveable { mutableStateOf(false) }
        val suggestions by vm.tagSuggestions.collectAsStateWithLifecycle()
        if (showTag || s.tag.isNotBlank()) {
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
        }
        // 알게 된 날: 지난 대화를 옮겨 적을 때 그날로 기록한다. 고른 날짜는 저장 뒤에도 남는다.
        var pickingDate by remember { mutableStateOf(false) }
        val today = remember { java.time.LocalDate.now() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!(showTag || s.tag.isNotBlank())) TextButton(onClick = { showTag = true }) { Text("+ 태그 추가") }
            AssistChip(
                onClick = { pickingDate = true },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) },
                label = { Text("알게 된 날 · ${learnedLabel(s.learnedOn, today)}") },
            )
            if (s.learnedOn != null && s.learnedOn!!.isBefore(today)) {
                IconButton(onClick = { vm.setLearnedOn(null) }) { Icon(Icons.Default.Close, contentDescription = "알게 된 날을 오늘로") }
            }
        }
        if (pickingDate) com.chatlearn.ui.components.PastDatePickerDialog(
            initial = s.learnedOn ?: today, today = today, onDismiss = { pickingDate = false },
            onPick = { d -> vm.setLearnedOn(d.takeIf { it.isBefore(today) }); pickingDate = false },
        )
        // 키보드가 결과와 저장 버튼을 가리지 않게 번역을 누르면 내린다.
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        Button(onClick = { focusManager.clearFocus(); vm.translate() }, enabled = s.input.isNotBlank() && !s.loading, modifier = Modifier.fillMaxWidth()) {
            val lineCount = if (s.mode == TranslateMode.RECEIVED) splitMessages(s.input).size else 0
            if (s.loading) ButtonSpinner()
            else Text(if (lineCount >= 2) "문장별로 번역 (${lineCount}개)" else if (s.mode == TranslateMode.CORRECT) "첨삭 받기" else "번역")
        }

        if (s.input.isBlank() && s.received == null && s.candidates.isEmpty() && s.batch.isEmpty() && s.correction == null && s.error == null) {
            SectionCard("이렇게 써 보세요") {
                when (s.mode) {
                    TranslateMode.RECEIVED -> {
                        Text("입력칸 오른쪽 붙여넣기 버튼을 누르면 복사한 메시지를 바로 번역해요.", style = MaterialTheme.typography.bodyMedium)
                        Text("여러 줄을 한꺼번에 붙여넣으면 문장별로 골라 저장할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                        Text("결과에서 단어를 누르면 그 문장에서의 뜻을 볼 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    }
                    TranslateMode.SENT -> {
                        Text("한국어로 하고 싶은 말을 쓰면 자연스러운 표현 후보를 만들어요.", style = MaterialTheme.typography.bodyMedium)
                        Text("내가 저장해 둔 표현이 어울리면 후보에 함께 써 줘요.", style = MaterialTheme.typography.bodyMedium)
                    }
                    TranslateMode.CORRECT -> {
                        Text("보내기 전에 영어나 스페인어로 직접 써 보세요. 어색한 곳을 고치고 이유를 알려 줘요.", style = MaterialTheme.typography.bodyMedium)
                        Text("고친 단어는 색으로 표시돼요. 마음에 들면 복사해서 보내고 저장해 두세요.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        s.error?.let {
            InfoMessage(errorText(it), MessageKind.ERROR)
            // 키·모델이 틀렸거나 하루 한도·안전 필터에 걸렸으면 다시 시도해도 같다.
            val settingsFix = it is TranslateError.Ai && (it.kind == AiError.BAD_KEY || it.kind == AiError.BAD_MODEL)
            val hopeless = it is TranslateError.Ai && (it.kind == AiError.DAILY_LIMIT || it.kind == AiError.BLOCKED)
            if (it is TranslateError.Ai && !settingsFix && !hopeless) {
                OutlinedButton(onClick = { vm.translate() }, enabled = !s.loading) { Text("다시 시도") }
            }
            if (settingsFix) {
                Button(onClick = onOpenSettings) { Text("설정 열기") }
            }
        }
        s.notice?.let {
            InfoMessage(
                when (it) {
                    is Notice.Saved -> if (it.cards >= 2) "저장했어요. 영어·스페인어 카드 ${it.cards}장을 만들었어요." else "저장했어요."
                    Notice.Duplicate -> "이미 저장된 문장이에요."
                    is Notice.BatchSaved -> "${it.saved}개를 저장했어요." + if (it.duplicates > 0) " (이미 있는 ${it.duplicates}개는 건너뛰었어요)" else ""
                },
                MessageKind.SUCCESS,
            )
        }

        if (s.batch.isNotEmpty()) {
            val chosen = s.batch.count { it.selected }
            Text("저장할 문장을 골라 주세요", style = MaterialTheme.typography.titleSmall)
            s.batch.forEachIndexed { i, b ->
                Card(Modifier.fillMaxWidth().toggleable(value = b.selected, enabled = b.savable, role = Role.Checkbox) { vm.toggleBatch(i) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Checkbox(checked = b.selected, onCheckedChange = null, enabled = b.savable)
                        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(b.text, style = MaterialTheme.typography.bodyLarge)
                            if (b.savable) {
                                Text(b.result!!.koTranslation, style = MaterialTheme.typography.bodyMedium)
                                if (b.keyPhrase.isNotBlank()) Text("핵심 표현: ${b.keyPhrase}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("영어나 스페인어가 아니라서 저장할 수 없어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Button(onClick = { vm.saveBatch() }, enabled = chosen > 0, modifier = Modifier.fillMaxWidth()) { Text("선택한 ${chosen}개 저장") }
        }

        s.received?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        textChunks(s.source).forEach { chunk ->
                            // 글자는 작아도 누를 영역은 48dp 이상으로 확보한다.
                            Box(
                                Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClickLabel = "뜻 보기") { vm.lookupWord(chunk) }.padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(chunk, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    Text("단어를 누르면 이 문장에서의 뜻을 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                    s.lookup?.let { l ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(l.word, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                when (l) {
                                    is LookupState.Loading -> ButtonSpinner()
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
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r.koTranslation, style = MaterialTheme.typography.titleMedium)
                            Text(r.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        s.keyPhrase, vm::setKeyPhrase, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("핵심 표현(고칠 수 있어요)") },
                        supportingText = { Text("복습의 빈칸 채우기에서 이 표현을 맞히게 돼요.") },
                    )
                    Button(onClick = { vm.saveReceived() }) { Text("저장") }
                }
            }
        }
        s.correction?.let { c -> CorrectionCard(c, s.source, onSpeak = { onSpeak(c.corrected, c.detectedLang ?: Lang.EN) }, onCopy = ::copy) {
            clipboard.setText(AnnotatedString(c.corrected))
            vm.saveCorrection()
        } }
        s.candidates.forEach { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer { Text(c.text, style = MaterialTheme.typography.titleMedium) }
                    Text(c.toneNote, style = MaterialTheme.typography.bodyMedium)
                    if (c.usedPhrase.isNotBlank() && c.text.contains(c.usedPhrase, ignoreCase = true)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text("내가 저장한 표현을 썼어요: ${c.usedPhrase}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (c.keyPhrase.isNotBlank() && c.text.contains(c.keyPhrase, ignoreCase = true)) {
                        Text("핵심 표현: ${c.keyPhrase}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSpeak(c.text, s.targetLang) }) { Text("들어보기") }
                        TextButton(onClick = { copy(c.text) }) { Text("복사만") }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(c.text))
                            vm.onCandidateCopied(c)
                        }) { Text("복사하고 저장") }
                    }
                }
            }
        }
    }
}

/** 첨삭 결과: 고친 문장(바뀐 단어 강조), 내가 쓴 문장, 뜻, 이유, 다른 표현, 복사·저장. */
@Composable
private fun CorrectionCard(c: com.chatlearn.ai.CorrectionResult, original: String, onSpeak: () -> Unit, onCopy: (String) -> Unit, onCopyAndSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (c.natural) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.statusColors.success)
                    Text("자연스러워요! 그대로 보내도 돼요.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.statusColors.success)
                }
                SelectionContainer { Text(c.corrected, style = MaterialTheme.typography.titleMedium) }
            } else {
                Text("고친 문장 (바뀐 곳은 색으로 표시)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val accent = MaterialTheme.colorScheme.primary
                SelectionContainer {
                    Text(buildAnnotatedString {
                        wordDiff(original, c.corrected).forEachIndexed { i, w ->
                            if (i > 0) append(" ")
                            if (w.changed) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(w.text) }
                            else append(w.text)
                        }
                    }, style = MaterialTheme.typography.titleMedium)
                }
                LabeledText("내가 쓴 문장", original, valueStyle = MaterialTheme.typography.bodyMedium, valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.koTranslation.isNotBlank()) LabeledText("뜻", c.koTranslation)
            if (c.explanationKo.isNotBlank()) LabeledText(if (c.natural) "한마디" else "왜 고쳤나요", c.explanationKo, valueStyle = MaterialTheme.typography.bodyMedium)
            if (c.alternatives.isNotEmpty()) {
                Text("이렇게도 말해요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.alternatives.forEach { alt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(Modifier.weight(1f)) { Text(alt, style = MaterialTheme.typography.bodyLarge) }
                        IconButton(onClick = { onCopy(alt) }) { Icon(Icons.Default.ContentCopy, contentDescription = "이 표현 복사") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSpeak) { Text("들어보기") }
                TextButton(onClick = { onCopy(c.corrected) }) { Text("복사만") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onCopyAndSave) { Text("복사하고 저장") }
            }
        }
    }
}

private fun errorText(e: TranslateError) = when (e) {
    TranslateError.UnsupportedLang -> "영어나 스페인어 문장만 다룰 수 있어요."
    is TranslateError.Ai -> when (e.kind) {
        AiError.RATE_LIMIT -> "AI 요청이 잠깐 너무 많았어요. 1분쯤 뒤에 다시 시도해 주세요."
        AiError.DAILY_LIMIT -> "오늘 AI 한도를 다 썼어요. 내일 다시 쓸 수 있어요."
        AiError.BAD_MODEL -> "모델 이름을 찾을 수 없어요. 설정에서 모델 이름을 확인해 주세요."
        AiError.BLOCKED -> "AI가 이 문장은 다루지 않겠다고 했어요(안전 필터)."
        AiError.OFFLINE -> "인터넷에 연결되어 있지 않아요."
        AiError.TIMEOUT -> "AI 응답이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요."
        AiError.OVERLOADED -> "지금 이 AI 모델에 요청이 몰려 있어요. 잠시 후 다시 시도하거나, 설정에서 다른 모델 이름으로 바꿔 보세요."
        AiError.BAD_KEY -> "API 키를 확인해 주세요. 설정 탭에서 바꿀 수 있어요."
        AiError.BAD_FORMAT -> "AI 응답을 읽지 못했어요. 다시 시도해 주세요."
        AiError.SERVER -> "AI 서버에 문제가 생겼어요."
    }
}
