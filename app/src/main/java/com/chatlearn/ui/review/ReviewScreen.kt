package com.chatlearn.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    LaunchedEffect(Unit) { vm.ensureStarted() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        custom?.let { Text("선택 복습 ${it}장", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if (state is ReviewUi.Prompt || state is ReviewUi.Graded) {
            Text("${minOf(progress.done + 1, progress.total)} / ${progress.total}", style = MaterialTheme.typography.labelMedium)
        }
        when (val s = state) {
            ReviewUi.Loading -> CircularProgressIndicator()
            ReviewUi.Empty -> {
                Text(if (custom != null) "고른 문장에 복습할 카드가 없어요." else "오늘 복습할 카드가 없어요.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.start() }) { Text(if (custom != null) "평소 복습으로" else "다시 확인") }
            }
            is ReviewUi.Done -> {
                Text("오늘 ${s.count}개를 복습했어요.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.start() }) { Text(if (custom != null) "평소 복습으로" else "더 있는지 확인") }
            }
            is ReviewUi.Prompt -> {
                when (s.item.exercise) {
                    ExerciseKind.TRANSLATE -> {
                        Text("${langLabel(s.item.promptLang)} → ${langLabel(s.item.card.answerLang)}로 써 보세요", style = MaterialTheme.typography.labelLarge)
                        Text(s.item.promptText, style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            s.answers[0], { vm.setAnswer(it) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("내 답") },
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
                                a, { vm.setAnswer(it, i) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("빈칸 ${i + 1}") },
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
                            s.answers[0], { vm.setAnswer(it) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("들은 문장") },
                            keyboardOptions = KeyboardOptions(imeAction = done), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                        )
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
}

private fun langLabel(l: Lang) = when (l) { Lang.KO -> "한국어"; Lang.EN -> "영어"; Lang.ES -> "스페인어" }
private fun verdictLabel(v: String) = when (v) { "CORRECT" -> "맞아요"; "CLOSE" -> "거의 맞아요"; else -> "틀렸어요" }
