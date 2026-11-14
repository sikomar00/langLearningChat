package com.chatlearn.ui.translate

import androidx.compose.ui.semantics.contentDescription
import com.chatlearn.ui.components.langLabel
import com.chatlearn.ui.components.SegmentBar
import com.chatlearn.ui.components.Ticket
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.Lang
import com.chatlearn.domain.grammarPoint
import com.chatlearn.ui.components.ChatBubble
import com.chatlearn.ui.components.LabeledText
import com.chatlearn.ui.theme.statusColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 써 보기: 내 시도(보낸 말풍선) 아래 코치의 답이 검사표(탑승권)로 쌓인다.
 * 틀리면 정답 대신 힌트가 한 단계씩 구체적이 되고(불빛 칸으로 단계 표시), 맞히거나 "정답 보기"를 누르면
 * 완성 문장 탑승권의 떼는 칸에서 [details](친구·나라·태그)를 붙여 저장한다.
 */
@Composable
fun WriteCoach(
    vm: TranslateViewModel, onSpeak: (String, Lang) -> Unit, onCopy: (String) -> Unit,
    onReplace: ((String) -> Unit)? = null, details: @Composable () -> Unit = {},
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val code = s.targetLang.code.uppercase()
    var run = 0 // 마지막으로 맞힌 뒤 틀린 횟수 = 힌트 단계
    s.attempts.forEach { a ->
        ChatBubble(outgoing = true) { Text(a.text, style = MaterialTheme.typography.bodyLarge) }
        val f = a.feedback
        run = if (f.correct) 0 else minOf(run + 1, MAX_HINT)
        val level = run
        Ticket(route = if (f.correct) "$code 검사 · 통과" else "$code 검사 · 힌트 ${level}단계", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (!f.correct) SegmentBar(level, MAX_HINT, Modifier.width(72.dp).height(6.dp).semantics { contentDescription = "힌트 $level/${MAX_HINT}단계" })
            Text(f.hint, style = MaterialTheme.typography.bodyLarge)
            val names = f.grammarErrors.mapNotNull { grammarPoint(it)?.nameKo }
            if (names.isNotEmpty()) Text("다시 볼 문법: ${names.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val last = s.attempts.lastOrNull() ?: return
    val done = last.feedback.correct || s.revealed
    if (!done) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("힌트를 보고 위 칸을 고쳐 다시 확인해 보세요.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
            TextButton(onClick = vm::reveal) { Text("정답 보기") }
        }
        return
    }
    val f = last.feedback
    val sentence = if (f.correct) last.text else f.answer
    Ticket(route = "한국어 → ${langLabel(s.targetLang)} · 보낼 문장", stub = {
        details()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSpeak(sentence, s.targetLang) }) { Text("들어보기") }
            TextButton(onClick = { onCopy(sentence) }) { Text("복사") }
            Spacer(Modifier.weight(1f))
            // 바꿔 넣기에서는 저장도 함께 하므로 따로 저장 버튼을 두지 않는다(먼저 저장하면 바꿔 넣을 문장이 사라진다).
            if (onReplace == null) Button(onClick = { vm.saveAttempt() }, shape = MaterialTheme.shapes.medium) { Text("저장") }
        }
        if (onReplace != null) ReplaceButton(sentence, vm::saveAttempt, onReplace)
    }) {
        SelectionContainer { Text(sentence, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.statusColors.success) }
        if (!f.correct || f.answer != last.text) {
            LabeledText(if (f.correct) "이렇게도 말해요" else "자연스러운 문장", f.answer, valueStyle = MaterialTheme.typography.titleMedium)
        }
        if (f.explanationKo.isNotBlank()) Text(f.explanationKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val MAX_HINT = 3

/** 텍스트 선택 메뉴로 왔을 때: 저장을 마친 뒤 [text]를 원래 칸에 돌려준다(먼저 돌아가면 저장이 화면과 함께 취소된다). */
@Composable
internal fun ReplaceButton(text: String, save: () -> Job, onReplace: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var replacing by remember { mutableStateOf(false) }
    Button(shape = MaterialTheme.shapes.medium, onClick = { replacing = true; scope.launch { save().join(); onReplace(text) } }, modifier = Modifier.fillMaxWidth(), enabled = !replacing) {
        Text("이걸로 바꾸고 저장")
    }
}
