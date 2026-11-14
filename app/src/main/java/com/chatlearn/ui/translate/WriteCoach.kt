package com.chatlearn.ui.translate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * 써 보기 대화: 내 시도(보낸 말풍선)와 코치의 답(받은 말풍선)이 차례로 쌓인다.
 * 틀리면 정답 대신 힌트가 한 단계씩 구체적이 되고, 맞히거나 "정답 보기"를 누르면 저장할 수 있다.
 */
@Composable
fun WriteCoach(vm: TranslateViewModel, onSpeak: (String, Lang) -> Unit, onCopy: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    s.attempts.forEach { a ->
        ChatBubble(outgoing = true) { Text(a.text, style = MaterialTheme.typography.bodyLarge) }
        val f = a.feedback
        ChatBubble(
            outgoing = false, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (f.correct) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (f.correct) Icons.Default.CheckCircle else Icons.Default.Lightbulb, contentDescription = null, Modifier.size(20.dp))
                Text(if (f.correct) "자연스러워요!" else "힌트", style = MaterialTheme.typography.titleSmall)
            }
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
    if (!f.correct || f.answer != last.text) {
        ChatBubble(outgoing = false, color = MaterialTheme.colorScheme.primaryContainer) {
            LabeledText(if (f.correct) "이렇게도 말해요" else "자연스러운 문장", f.answer, valueStyle = MaterialTheme.typography.titleMedium)
            if (f.explanationKo.isNotBlank()) Text(f.explanationKo, style = MaterialTheme.typography.bodyMedium)
        }
    }
    SelectionContainer { Text(sentence, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.statusColors.success) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onSpeak(sentence, s.targetLang) }) { Text("들어보기") }
        TextButton(onClick = { onCopy(sentence) }) { Text("복사") }
        Spacer(Modifier.weight(1f))
        Button(onClick = { vm.saveAttempt() }) { Text("저장") }
    }
}
