package com.chatlearn.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Verdict
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.exercise.WordMark
import com.chatlearn.review.shadowingResult
import com.chatlearn.ui.components.ChatBubble
import com.chatlearn.ui.components.LabeledText
import com.chatlearn.ui.theme.statusColors

internal fun verdictOf(o: GradeOutcome): Verdict? = when (o) {
    is GradeOutcome.Ai -> o.result.verdict
    is GradeOutcome.Local -> o.verdict
    is GradeOutcome.SelfGrade -> null
}

/** 채점: 문제(받은 말) → 내 답(보낸 말) → 판정과 바른 문장(받은 말) 순서의 대화. */
@Composable
internal fun GradedBody(s: ReviewUi.Graded, vm: ReviewViewModel, canListen: Boolean, heard: String?, onShadow: () -> Unit) {
    val verdict = verdictOf(s.outcome)
    ChatBubble(outgoing = false) {
        Text(
            when (s.item.exercise) { ExerciseKind.TRANSLATE -> s.item.promptText; ExerciseKind.CLOZE -> s.item.hintKo; ExerciseKind.DICTATION -> s.item.hintKo },
            style = MaterialTheme.typography.titleMedium,
        )
    }
    ChatBubble(outgoing = true) {
        val local = s.outcome as? GradeOutcome.Local
        if (local != null && local.marks.isNotEmpty()) MarkedWords(local.marks)
        else Text(s.answer.ifBlank { "(모르겠어요)" }, style = MaterialTheme.typography.bodyLarge)
    }
    verdict?.let { VerdictBanner(it) }
    ChatBubble(outgoing = false, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        when (val o = s.outcome) {
            is GradeOutcome.Ai -> {
                LabeledText("더 자연스러운 표현", o.result.corrected, valueColor = MaterialTheme.statusColors.success, valueStyle = MaterialTheme.typography.titleMedium)
                if (o.result.explanationKo.isNotBlank()) Text(o.result.explanationKo, style = MaterialTheme.typography.bodyMedium)
            }
            is GradeOutcome.SelfGrade -> {
                LabeledText("정답", o.reference, valueColor = MaterialTheme.statusColors.success, valueStyle = MaterialTheme.typography.titleMedium)
                Text(if (o.aiError == null) "스스로 채점해 주세요." else "AI를 쓸 수 없어서 스스로 채점해 주세요.", style = MaterialTheme.typography.bodyMedium)
            }
            is GradeOutcome.Local -> LabeledText("정답", o.reference, valueColor = MaterialTheme.statusColors.success, valueStyle = MaterialTheme.typography.titleMedium)
        }
        s.item.keyPhrase?.let { LabeledText("핵심 표현", it, valueColor = MaterialTheme.colorScheme.primary) }
        s.item.context?.let { ContextFold(it) }
        if (s.item.note.isNotBlank()) LabeledText("설명", s.item.note, valueStyle = MaterialTheme.typography.bodyMedium)
    }
    ShadowingRow(vm, canListen, heard, s.item.reference, onShadow)
}

/** 정답 듣기와, 음성 인식이 되면 따라 말하기. */
@Composable
private fun ShadowingRow(vm: ReviewViewModel, canListen: Boolean, heard: String?, reference: String, onShadow: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { vm.speak(false) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("듣기")
        }
        IconButton(onClick = { vm.speak(true) }) { Icon(Icons.Default.SlowMotionVideo, contentDescription = "느리게 듣기") }
        if (canListen) OutlinedButton(onClick = onShadow) {
            Icon(Icons.Default.Mic, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("따라 말하기")
        }
    }
    heard?.let { h ->
        val r = shadowingResult(reference, h)
        val (msg, color) = when (r.verdict) {
            Verdict.CORRECT -> "또렷하게 말했어요!" to MaterialTheme.statusColors.success
            Verdict.CLOSE -> "거의 맞아요. 밑줄 친 단어를 다시 말해 보세요." to MaterialTheme.colorScheme.tertiary
            Verdict.WRONG -> "잘 안 들렸어요. 천천히 다시 말해 보세요." to MaterialTheme.colorScheme.error
        }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(msg, style = MaterialTheme.typography.titleSmall, color = color)
            MarkedWords(r.marks)
        }
    }
}

/** 단어마다 맞았는지 표시: 틀린 단어는 밑줄(색만으로 구분하지 않게). */
@Composable
private fun MarkedWords(marks: List<WordMark>) {
    val wrongColor = MaterialTheme.colorScheme.error
    Text(buildAnnotatedString {
        marks.forEachIndexed { i, m ->
            if (i > 0) append(" ")
            if (m.correct) append(m.word)
            else withStyle(SpanStyle(color = wrongColor, textDecoration = TextDecoration.Underline)) { append(m.word) }
        }
    }, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun VerdictBanner(verdict: Verdict) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val ctx = LocalContext.current
    // 시스템에서 애니메이션을 끈 경우(접근성)에는 움직임을 생략한다.
    val animate = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val scale = remember { Animatable(if (animate) 0.85f else 1f) }
    LaunchedEffect(verdict) {
        if (verdict == Verdict.CORRECT) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (animate) scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
    }
    val (color, icon, label) = when (verdict) {
        Verdict.CORRECT -> Triple(MaterialTheme.statusColors.success, Icons.Default.CheckCircle, "맞아요")
        Verdict.CLOSE -> Triple(MaterialTheme.colorScheme.tertiary, Icons.Default.Info, "거의 맞아요")
        Verdict.WRONG -> Triple(MaterialTheme.colorScheme.error, Icons.Default.Cancel, "틀렸어요")
    }
    Row(
        Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

/** 저장할 때 함께 둔 앞뒤 대화. 접어 두었다가 눌러서 본다. ">" 줄이 이 문장. */
@Composable
internal fun ContextFold(context: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(if (open) "대화 접기" else "어떤 대화였는지 보기") }
    if (open) Text(context, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

