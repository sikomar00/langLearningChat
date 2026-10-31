package com.chatlearn.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.review.GradeOutcome

@Composable
fun ReviewScreen(vm: ReviewViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            ReviewUi.Loading -> CircularProgressIndicator()
            ReviewUi.Empty -> {
                Text("오늘 복습할 카드가 없어요.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.start() }) { Text("다시 확인") }
            }
            is ReviewUi.Done -> {
                Text("오늘 ${s.count}개를 복습했어요.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { vm.start() }) { Text("더 있는지 확인") }
            }
            is ReviewUi.Prompt -> {
                Text("${langLabel(s.item.promptLang)} → ${langLabel(s.item.card.answerLang)}로 써 보세요", style = MaterialTheme.typography.labelLarge)
                Text(s.item.promptText, style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(s.answer, vm::setAnswer, Modifier.fillMaxWidth(), minLines = 2, label = { Text("내 답") })
                Button(onClick = { vm.submit() }, enabled = s.answer.isNotBlank() && !s.checking, modifier = Modifier.fillMaxWidth()) {
                    if (s.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("제출")
                }
            }
            is ReviewUi.Graded -> {
                Text(s.item.promptText, style = MaterialTheme.typography.titleMedium)
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Rating.AGAIN to "다시", Rating.HARD to "어려움", Rating.GOOD to "좋음", Rating.EASY to "쉬움").forEach { (r, label) ->
                        if (r == s.suggested) Button(onClick = { vm.rate(r) }) { Text(label) }
                        else OutlinedButton(onClick = { vm.rate(r) }) { Text(label) }
                    }
                }
            }
        }
    }
}

private fun langLabel(l: Lang) = when (l) { Lang.KO -> "한국어"; Lang.EN -> "영어"; Lang.ES -> "스페인어" }
private fun verdictLabel(v: String) = when (v) { "CORRECT" -> "맞아요"; "CLOSE" -> "거의 맞아요"; else -> "틀렸어요" }
