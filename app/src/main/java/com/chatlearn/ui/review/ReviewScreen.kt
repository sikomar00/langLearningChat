package com.chatlearn.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.domain.ratingChoices
import com.chatlearn.domain.ratingLabel
import com.chatlearn.domain.ratingPreviews
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.specialChars
import com.chatlearn.review.isLeech
import com.chatlearn.stats.StatsScreen
import com.chatlearn.stats.StatsViewModel

/** 복습 탭: 위의 전환 버튼으로 "복습"과 "통계"를 오간다. */
@Composable
fun ReviewScreen(vm: ReviewViewModel, statsVm: StatsViewModel) {
    var showStats by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("복습", "통계").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = showStats == (i == 1), onClick = { showStats = i == 1 },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (showStats) StatsScreen(statsVm) else ReviewPane(vm)
    }
}

@Composable
private fun ReviewPane(vm: ReviewViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val done = ImeAction.Done
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    var focusedBlank by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.ensureStarted() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        custom?.let { Text("선택 복습 ${it}장", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if (state is ReviewUi.Prompt || state is ReviewUi.Graded) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${minOf(progress.done + 1, progress.total)} / ${progress.total}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (goal.enabled) {
                    Text(
                        "오늘 ${goal.done}/${goal.goal}${if (goal.reached) " ✓" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (goal.reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state is ReviewUi.Prompt) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "카드 메뉴") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("문장 수정") }, onClick = { menuOpen = false; editing = true })
                            DropdownMenuItem(text = { Text("오늘은 건너뛰기") }, onClick = { menuOpen = false; vm.skipToday() })
                            DropdownMenuItem(text = { Text("한 달 쉬기") }, onClick = { menuOpen = false; vm.snoozeMonth() })
                            DropdownMenuItem(text = { Text("이 문장 삭제") }, onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                }
            }
        }
        if (canUndo && (state is ReviewUi.Prompt || state is ReviewUi.Done)) {
            TextButton(onClick = { vm.undo() }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("방금 평가 취소") }
        }
        when (val s = state) {
            ReviewUi.Loading -> CircularProgressIndicator()
            ReviewUi.Empty -> {
                Text(if (custom != null) "고른 문장에 복습할 카드가 없어요." else "오늘 복습할 카드가 없어요.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.start(preview = true) }) { Text(if (custom != null) "평소 복습으로" else "다시 확인") }
            }
            is ReviewUi.Ready -> {
                Text("오늘 복습할 카드 ${s.total}장", style = MaterialTheme.typography.titleLarge)
                Text(if (s.newCards > 0) "복습 ${s.total - s.newCards}장 · 새 카드 ${s.newCards}장" else "모두 복습 카드예요.")
                if (goal.enabled) {
                    Text(
                        if (goal.reached) "오늘 목표 ${goal.goal}장을 달성했어요 ✓" else "오늘 목표 ${goal.done} / ${goal.goal}장",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(progress = { goal.fraction }, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = { vm.begin() }, Modifier.fillMaxWidth()) { Text("시작") }
            }
            is ReviewUi.Done -> {
                Text("오늘 ${s.count}개를 복습했어요.", style = MaterialTheme.typography.titleMedium)
                if (goal.reached) Text("🎉 오늘 목표를 달성했어요! 더 해도 좋아요.", color = MaterialTheme.colorScheme.primary)
                else if (goal.enabled) Text("오늘 목표까지 ${goal.goal - goal.done}장 남았어요.")
                OutlinedButton(onClick = { vm.start(preview = true) }) { Text(if (custom != null) "평소 복습으로" else "더 있는지 확인") }
            }
            is ReviewUi.Prompt -> {
                if (isLeech(s.item.card)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("이 카드를 ${s.item.card.lapses}번 틀렸어요.", style = MaterialTheme.typography.labelLarge)
                            Text("정답을 확인한 뒤 설명을 읽어 보거나, 한 달 쉬었다가 다시 만나 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.snoozeMonth() }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("한 달 쉬기") }
                        }
                    }
                }
                when (s.item.exercise) {
                    ExerciseKind.TRANSLATE -> {
                        Text("${langLabel(s.item.promptLang)} → ${langLabel(s.item.card.answerLang)}로 써 보세요", style = MaterialTheme.typography.labelLarge)
                        Text(s.item.promptText, style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            s.answers[0], { vm.setAnswer(it) }, Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focusedBlank = 0 }, minLines = 2, label = { Text("내 답") },
                            keyboardOptions = KeyboardOptions(imeAction = done), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                        )
                    }
                    ExerciseKind.CLOZE -> {
                        Text("빈칸을 채워 보세요 (${langLabel(s.item.card.answerLang)})", style = MaterialTheme.typography.labelLarge)
                        Text(s.item.cloze!!.display, style = MaterialTheme.typography.headlineSmall)
                        Text("뜻: ${s.item.hintKo}", style = MaterialTheme.typography.bodyMedium)
                        s.answers.forEachIndexed { i, a ->
                            val last = i == s.answers.lastIndex
                            OutlinedTextField(
                                a, { vm.setAnswer(it, i) }, Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focusedBlank = i }, singleLine = true, label = { Text("빈칸 ${i + 1}") },
                                keyboardOptions = KeyboardOptions(imeAction = if (last) done else ImeAction.Next),
                                keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                            )
                        }
                    }
                    ExerciseKind.DICTATION -> {
                        LaunchedEffect(s.item.card.id, s.shownAt) { vm.speak(false) }
                        Text("들리는 ${langLabel(s.item.card.answerLang)} 문장을 그대로 써 보세요", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.speak(false) }) { Text("다시 듣기") }
                            OutlinedButton(onClick = { vm.speak(true) }) { Text("느리게 듣기") }
                        }
                        OutlinedTextField(
                            s.answers[0], { vm.setAnswer(it) }, Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focusedBlank = 0 }, minLines = 2, label = { Text("들은 문장") },
                            keyboardOptions = KeyboardOptions(imeAction = done), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                        )
                    }
                }
                val chars = if (s.item.exercise == ExerciseKind.DICTATION || s.item.exercise == ExerciseKind.CLOZE || s.item.exercise == ExerciseKind.TRANSLATE) specialChars(s.item.card.answerLang) else emptyList()
                if (chars.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chars) { ch ->
                            OutlinedButton(onClick = { vm.appendToAnswer(ch, focusedBlank.coerceIn(0, s.answers.lastIndex)) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(ch) }
                        }
                    }
                }
                Button(onClick = { vm.submit() }, enabled = s.answers.none { it.isBlank() } && !s.checking, modifier = Modifier.fillMaxWidth()) {
                    if (s.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("제출")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !s.checking, onClick = { vm.giveUp() }) { Text("정답 보기") }
                    if (progress.total - progress.done > 1) {
                        OutlinedButton(enabled = !s.checking, onClick = { vm.postpone() }) { Text("나중에") }
                    }
                }
            }
            is ReviewUi.Graded -> {
                when (s.item.exercise) {
                    ExerciseKind.TRANSLATE -> Text(s.item.promptText, style = MaterialTheme.typography.titleMedium)
                    ExerciseKind.CLOZE -> Text(s.item.cloze!!.display, style = MaterialTheme.typography.titleMedium)
                    ExerciseKind.DICTATION -> Text("뜻: ${s.item.hintKo}", style = MaterialTheme.typography.titleMedium)
                }
                Text("내 답: ${s.answer}")
                if (s.item.note.isNotBlank()) Text("설명: ${s.item.note}", style = MaterialTheme.typography.bodyMedium)
                s.item.keyPhrase?.let { Text("핵심 표현: $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                when (val o = s.outcome) {
                    is GradeOutcome.Ai -> {
                        Text(verdictLabel(o.result.verdict.name), style = MaterialTheme.typography.titleMedium)
                        Text("더 자연스러운 표현: ${o.result.corrected}")
                        Text(o.result.explanationKo)
                    }
                    is GradeOutcome.SelfGrade -> {
                        Text("정답: ${o.reference}", style = MaterialTheme.typography.titleMedium)
                        Text(if (o.aiError == null) "스스로 채점해 주세요." else "AI를 쓸 수 없어서 스스로 채점해 주세요.")
                    }
                    is GradeOutcome.Local -> {
                        Text(verdictLabel(o.verdict.name), style = MaterialTheme.typography.titleMedium)
                        if (o.marks.isNotEmpty()) {
                            Text(buildAnnotatedString {
                                o.marks.forEachIndexed { i, m ->
                                    if (i > 0) append(" ")
                                    if (m.correct) append(m.word)
                                    else withStyle(SpanStyle(color = Color(0xFFB3261E), textDecoration = TextDecoration.Underline)) { append(m.word) }
                                }
                            })
                        }
                        Text("정답: ${o.reference}")
                        if (s.item.hintKo.isNotBlank() && s.item.exercise == ExerciseKind.CLOZE) Text("뜻: ${s.item.hintKo}")
                    }
                }
                val verdict = when (val o = s.outcome) {
                    is GradeOutcome.Ai -> o.result.verdict
                    is GradeOutcome.Local -> o.verdict
                    is GradeOutcome.SelfGrade -> null
                }
                val choices = ratingChoices(verdict)
                Text("얼마나 기억났나요?", style = MaterialTheme.typography.labelLarge)
                val previews = remember(s.item.card) { ratingPreviews(s.item.card.toState(), System.currentTimeMillis()) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { r ->
                        val label = ratingLabel(r)
                        Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            if (r == s.suggested) Button(onClick = { vm.rate(r) }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
                            else OutlinedButton(onClick = { vm.rate(r) }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
                            Text(previews.getValue(r), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    val promptItem = (state as? ReviewUi.Prompt)?.item
    if (editing && promptItem != null) {
        var answerText by remember(promptItem.card.id) { mutableStateOf(promptItem.reference) }
        var koText by remember(promptItem.card.id) { mutableStateOf(promptItem.hintKo) }
        var noteText by remember(promptItem.card.id) { mutableStateOf(promptItem.note) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("문장 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(answerText, { answerText = it }, label = { Text("${langLabel(promptItem.card.answerLang)} 문장") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(koText, { koText = it }, label = { Text("한국어 뜻") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(noteText, { noteText = it }, label = { Text("설명 · 메모") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = answerText.isNotBlank(), onClick = { vm.editCurrent(answerText, koText, noteText); editing = false }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("취소") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 문장을 삭제할까요?") },
            text = { Text("라이브러리에서도 지워지고 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCurrent() }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

private fun langLabel(l: Lang) = when (l) { Lang.KO -> "한국어"; Lang.EN -> "영어"; Lang.ES -> "스페인어" }
private fun verdictLabel(v: String) = when (v) { "CORRECT" -> "맞아요"; "CLOSE" -> "거의 맞아요"; else -> "틀렸어요" }
