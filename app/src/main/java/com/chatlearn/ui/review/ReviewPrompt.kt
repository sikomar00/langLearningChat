package com.chatlearn.ui.review

import com.chatlearn.domain.langLabel
import androidx.compose.ui.Alignment
import com.chatlearn.ui.components.Ticket
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.review.isLeech
import com.chatlearn.settings.VoicePrefs

/** 문제: 탑승권 머리띠에 문제 종류와 경로, 본문에 문제, 절취선 아래 칸에 답한다. */
@Composable
internal fun PromptBody(s: ReviewUi.Prompt, vm: ReviewViewModel, canListen: Boolean, voice: VoicePrefs, onFocusBlank: (Int) -> Unit, onMic: () -> Unit) {
    val lang = s.item.card.answerLang
    val firstField = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 새 카드가 나오면 바로 입력할 수 있게 첫 칸에 포커스를 준다(카드마다 입력칸을 누르지 않아도 된다).
    LaunchedEffect(s.item.card.id, s.shownAt) {
        runCatching { firstField.requestFocus(); keyboard?.show() }
    }
    if (isLeech(s.item.card)) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이 카드를 ${s.item.card.lapses}번 틀렸어요", style = MaterialTheme.typography.labelLarge)
                Text("정답을 확인한 뒤 설명을 읽어 보거나, 한 달 쉬었다가 다시 만나 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { vm.restartCurrent() }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("처음부터 다시") }
                    TextButton(onClick = { vm.snoozeMonth() }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("한 달 쉬기") }
                }
            }
        }
    }
    val to = langLabel(lang)
    when (s.item.exercise) {
        ExerciseKind.TRANSLATE -> Ticket(route = "번역 · ${langLabel(s.item.promptLang)} → $to", stub = {
            OutlinedTextField(
                s.answers[0], { vm.setAnswer(it) },
                Modifier.fillMaxWidth().focusRequester(firstField).onFocusChanged { if (it.isFocused) onFocusBlank(0) },
                minLines = 2, label = { Text("내 답") },
                trailingIcon = if (canListen) ({ IconButton(onClick = onMic) { Icon(Icons.Default.Mic, contentDescription = "말로 답하기") } }) else null,
                keyboardOptions = answerKeyboard(ExerciseKind.TRANSLATE, lang, ImeAction.Done, voice), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            )
        }) {
            Text(s.item.promptText, style = MaterialTheme.typography.headlineSmall)
        }
        ExerciseKind.CLOZE -> Ticket(route = "빈칸 · $to", stub = {
            s.answers.forEachIndexed { i, a ->
                val last = i == s.answers.lastIndex
                OutlinedTextField(
                    a, { vm.setAnswer(it, i) },
                    Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstField) else Modifier).onFocusChanged { if (it.isFocused) onFocusBlank(i) },
                    singleLine = true, label = { Text(if (s.answers.size > 1) "빈칸 ${i + 1}" else "빈칸") },
                    keyboardOptions = answerKeyboard(ExerciseKind.CLOZE, lang, if (last) ImeAction.Done else ImeAction.Next, voice),
                    keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                )
            }
        }) {
            Text(s.item.cloze!!.display, style = MaterialTheme.typography.headlineSmall)
            Text(s.item.hintKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ExerciseKind.DICTATION -> {
            LaunchedEffect(s.item.card.id, s.shownAt) { vm.speak(false) }
            Ticket(route = "받아쓰기 · $to", stub = {
                OutlinedTextField(
                    s.answers[0], { vm.setAnswer(it) },
                    Modifier.fillMaxWidth().focusRequester(firstField).onFocusChanged { if (it.isFocused) onFocusBlank(0) },
                    minLines = 2, label = { Text("들은 문장") },
                    keyboardOptions = answerKeyboard(ExerciseKind.DICTATION, lang, ImeAction.Done, voice), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                )
            }) {
                Text("들리는 $to 문장을 그대로 써 보세요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(shape = MaterialTheme.shapes.medium, onClick = { vm.speak(false) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("다시 듣기")
                    }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { vm.speak(true) }) {
                        Icon(Icons.Default.SlowMotionVideo, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("느리게")
                    }
                }
            }
        }
    }
}
