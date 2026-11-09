package com.chatlearn.ui.review

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict
import com.chatlearn.domain.ratingChoices
import com.chatlearn.domain.ratingLabel
import com.chatlearn.domain.ratingPreviews
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.exercise.WordMark
import com.chatlearn.review.isLeech
import com.chatlearn.review.shadowingResult
import com.chatlearn.review.specialChars
import com.chatlearn.review.speechLanguageTag
import com.chatlearn.review.spokenText
import com.chatlearn.settings.VoicePrefs
import com.chatlearn.stats.StatsScreen
import com.chatlearn.stats.StatsViewModel
import com.chatlearn.ui.components.ButtonSpinner
import com.chatlearn.ui.components.EmptyState
import com.chatlearn.ui.components.LabeledText
import com.chatlearn.ui.components.LoadingBox
import com.chatlearn.ui.components.aiErrorText
import com.chatlearn.ui.components.needsSettings
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.components.langLabel
import com.chatlearn.ui.theme.statusColors

/** 복습 탭: 위의 전환 버튼으로 "복습"과 "통계"를 오간다. */
@Composable
fun ReviewScreen(vm: ReviewViewModel, statsVm: StatsViewModel, voice: VoicePrefs = VoicePrefs(), onOpenSettings: () -> Unit = {}) {
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
        if (showStats) StatsScreen(statsVm) else ReviewPane(vm, statsVm, voice, onOpenSettings)
    }
}

/** 음성 인식 결과를 어디에 쓸지: 답 입력칸 또는 따라 말하기(카드 id). */
private sealed interface SpeechTarget {
    data object Answer : SpeechTarget
    data class Shadow(val cardId: Long) : SpeechTarget
}

private fun speechIntent(lang: Lang, voice: VoicePrefs): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguageTag(lang, voice))
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLanguageTag(lang, voice))
    .putExtra(RecognizerIntent.EXTRA_PROMPT, "${langLabel(lang)}로 말해 보세요")
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

/** 음성 인식 화면을 띄울 수 있는 앱(구글 앱 등)이 있는가. 없으면 마이크 버튼을 숨긴다. */
private fun speechAvailable(ctx: Context): Boolean =
    ctx.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()

/** 답 입력칸 키보드: 철자를 보는 빈칸·받아쓰기는 자동 수정을 끄고, 정답 언어 키보드를 권한다. */
private fun answerKeyboard(kind: ExerciseKind, lang: Lang, ime: ImeAction, voice: VoicePrefs) = KeyboardOptions(
    capitalization = if (kind == ExerciseKind.CLOZE) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
    autoCorrectEnabled = kind == ExerciseKind.TRANSLATE,
    keyboardType = KeyboardType.Text,
    imeAction = ime,
    hintLocales = LocaleList(speechLanguageTag(lang, voice)),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPane(vm: ReviewViewModel, statsVm: StatsViewModel, voice: VoicePrefs, onOpenSettings: () -> Unit) {
    val stats by statsVm.stats.collectAsStateWithLifecycle()
    val streak = stats?.streak ?: 0
    val state by vm.state.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val moreNew by vm.moreNew.collectAsStateWithLifecycle()
    var focusedBlank by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val canListen = remember { speechAvailable(ctx) }
    var speechTarget by remember { mutableStateOf<SpeechTarget?>(null) }
    // 따라 말하기에서 들린 말: 카드 id와 문장. 다른 카드로 넘어가면 보이지 않는다.
    var heard by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = spokenText(res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        when (val t = speechTarget) {
            SpeechTarget.Answer -> text?.let(vm::putSpokenAnswer)
            is SpeechTarget.Shadow -> text?.let { heard = t.cardId to it }
            null -> Unit
        }
        speechTarget = null
    }
    fun listen(lang: Lang, target: SpeechTarget) {
        speechTarget = target
        runCatching { speechLauncher.launch(speechIntent(lang, voice)) }.onFailure {
            speechTarget = null
            android.widget.Toast.makeText(ctx, "음성 인식을 열 수 없어요", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // 탭에 들어올 때마다: 처음이면 시작하고, 비었거나 요약 화면이면 그사이 기한이 된 카드를 다시 읽는다.
    LaunchedEffect(Unit) { vm.refresh(force = false) }

    val inSession = state is ReviewUi.Prompt || state is ReviewUi.Graded
    Column(Modifier.fillMaxSize().imePadding()) {
        // 위: 진행 상황. 카드를 푸는 동안에는 한 줄로 줄여 키보드가 올라와도 본문 자리를 남긴다.
        if (!inSession && (custom != null || streak > 0)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    custom?.let { "선택 복습 ${it}장" }.orEmpty(), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                if (streak > 0) StreakBadge(streak)
            }
        }
        if (inSession) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (custom != null) "선택 복습 · " else "") + "${minOf(progress.done + 1, progress.total)}/${progress.total}",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                )
                if (goal.enabled) {
                    if (goal.reached) Icon(Icons.Default.CheckCircle, contentDescription = "목표 달성", Modifier.size(16.dp).padding(end = 2.dp), tint = MaterialTheme.statusColors.success)
                    Text(
                        "오늘 ${goal.done}/${goal.goal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (goal.reached) MaterialTheme.statusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (progress.total > 0) LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
        }
        if (canUndo && (state is ReviewUi.Prompt || state is ReviewUi.Done)) {
            TextButton(onClick = { vm.undo() }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("방금 평가 취소") }
        }

        // 가운데: 본문만 스크롤한다. 제출·평가 버튼은 아래에 고정해 손이 닿는 자리에 둔다.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                ReviewUi.Loading -> LoadingBox()
                ReviewUi.Empty -> {
                    EmptyState(
                        message = emptyReviewMessage(custom != null, moreNew),
                        icon = Icons.Default.CheckCircle,
                        actionLabel = if (custom != null) "평소 복습으로" else "더 있는지 확인",
                        onAction = { vm.start(preview = true) },
                    )
                    if (custom == null) LearnMore(moreNew) { vm.learnMore(LEARN_MORE) }
                }
                is ReviewUi.Ready -> {
                    Text("오늘 복습할 카드 ${s.total}장", style = MaterialTheme.typography.titleLarge)
                    Text(if (s.newCards > 0) "복습 ${s.total - s.newCards}장 · 새 카드 ${s.newCards}장" else "모두 복습 카드예요.")
                    if (goal.enabled) {
                        Text(
                            if (goal.reached) "오늘 목표 ${goal.goal}장을 달성했어요" else "오늘 목표 ${goal.done}/${goal.goal}장",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(progress = { goal.fraction }, modifier = Modifier.fillMaxWidth())
                    }
                    Button(onClick = { vm.begin() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("시작") }
                }
                is ReviewUi.Done -> {
                    val today = stats?.today
                    SectionCard("오늘의 복습") {
                        Text("${s.cards}장을 복습했어요", style = MaterialTheme.typography.headlineSmall)
                        if (s.count > s.cards) Text("다시 푼 것까지 ${s.count}회 풀었어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryStat("정답률", today?.let { com.chatlearn.stats.percent(it) } ?: "-", Modifier.weight(1f))
                            SummaryStat("연속 학습", if (streak > 0) "${streak}일" else "-", Modifier.weight(1f))
                            if (goal.enabled) SummaryStat("오늘 목표", "${goal.done}/${goal.goal}", Modifier.weight(1f))
                        }
                        if (goal.reached) Text("오늘 목표를 달성했어요! 더 해도 좋아요.", color = MaterialTheme.statusColors.success, style = MaterialTheme.typography.titleMedium)
                        else if (goal.enabled) Text("오늘 목표까지 ${goal.goal - goal.done}장 남았어요.")
                    }
                    OutlinedButton(onClick = { vm.start(preview = true) }) { Text(if (custom != null) "평소 복습으로" else "더 있는지 확인") }
                    if (custom == null) LearnMore(moreNew) { vm.learnMore(LEARN_MORE) }
                }
                is ReviewUi.Prompt -> PromptBody(
                    s, vm, canListen, voice,
                    onFocusBlank = { focusedBlank = it },
                    onMic = { listen(s.item.card.answerLang, SpeechTarget.Answer) },
                )
                is ReviewUi.Graded -> GradedBody(
                    s, vm, canListen,
                    heard = heard?.takeIf { it.first == s.item.card.id }?.second,
                    onShadow = { listen(s.item.card.answerLang, SpeechTarget.Shadow(s.item.card.id)) },
                    onOpenSettings = onOpenSettings,
                )
            }
        }

        // 아래: 지금 화면의 행동 버튼
        when (val s = state) {
            is ReviewUi.Prompt -> BottomBar {
                val chars = specialChars(s.item.card.answerLang)
                if (chars.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chars) { ch ->
                            OutlinedButton(
                                onClick = { vm.appendToAnswer(ch, focusedBlank.coerceIn(0, s.answers.lastIndex)) },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                            ) { Text(ch) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.giveUp() }) { Text("정답 보기") }
                    if (progress.total - progress.done > 1) OutlinedButton(onClick = { vm.postpone() }) { Text("나중에") }
                    Button(
                        onClick = { vm.submit() }, enabled = s.answers.none { it.isBlank() } && !s.checking,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { if (s.checking) ButtonSpinner() else Text("제출") }
                }
            }
            is ReviewUi.Graded -> BottomBar {
                val verdict = verdictOf(s.outcome)
                val choices = ratingChoices(verdict)
                Text("얼마나 기억났나요?", style = MaterialTheme.typography.labelLarge)
                val previews = remember(s.item.card) { ratingPreviews(s.item.card.toState(), System.currentTimeMillis()) }
                FlowRow(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (choices.size > 3) 2 else choices.size,
                ) {
                    choices.forEach { r ->
                        val label = ratingLabel(r)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (r == s.suggested) Button(onClick = { vm.rate(r) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, maxLines = 1) }
                            else OutlinedButton(onClick = { vm.rate(r) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, maxLines = 1) }
                            Text(previews.getValue(r), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> Unit
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

private const val LEARN_MORE = 5

/** 오늘 새 카드 한도 밖에 기다리는 새 카드가 있으면 더 배우기를 권한다. */
@Composable
private fun LearnMore(waiting: Int, onClick: () -> Unit) {
    if (waiting <= 0) return
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("새 카드 ${minOf(LEARN_MORE, waiting)}장 더 배우기") }
    Text("기다리는 새 카드 ${waiting}장 · 하루 새 카드 수는 설정 > 복습에서 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 아래에 고정되는 행동 영역. 본문과 구분되게 위에 얇은 선을 긋는다. */
@Composable
private fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private fun verdictOf(o: GradeOutcome): Verdict? = when (o) {
    is GradeOutcome.Ai -> o.result.verdict
    is GradeOutcome.Local -> o.verdict
    is GradeOutcome.SelfGrade -> null
}

@Composable
private fun PromptBody(s: ReviewUi.Prompt, vm: ReviewViewModel, canListen: Boolean, voice: VoicePrefs, onFocusBlank: (Int) -> Unit, onMic: () -> Unit) {
    val lang = s.item.card.answerLang
    val firstField = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // 새 카드가 나오면 바로 입력할 수 있게 첫 칸에 포커스를 준다(카드마다 입력칸을 누르지 않아도 된다).
    LaunchedEffect(s.item.card.id, s.shownAt) {
        runCatching { firstField.requestFocus(); keyboard?.show() }
    }
    if (isLeech(s.item.card)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이 카드를 ${s.item.card.lapses}회 틀렸어요.", style = MaterialTheme.typography.labelLarge)
                Text("정답을 확인한 뒤 설명을 읽어 보거나, 한 달 쉬었다가 다시 만나 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.snoozeMonth() }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("한 달 쉬기") }
            }
        }
    }
    when (s.item.exercise) {
        ExerciseKind.TRANSLATE -> {
            Text("${langLabel(s.item.promptLang)} → ${langLabel(lang)}로 써 보세요", style = MaterialTheme.typography.labelLarge)
            Text(s.item.promptText, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                s.answers[0], { vm.setAnswer(it) },
                Modifier.fillMaxWidth().focusRequester(firstField).onFocusChanged { if (it.isFocused) onFocusBlank(0) },
                minLines = 2, label = { Text("내 답") },
                trailingIcon = if (canListen) ({ IconButton(onClick = onMic) { Icon(Icons.Default.Mic, contentDescription = "말로 답하기") } }) else null,
                keyboardOptions = answerKeyboard(ExerciseKind.TRANSLATE, lang, ImeAction.Done, voice), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            )
            if (canListen) Text("마이크를 누르면 말로 답할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ExerciseKind.CLOZE -> {
            Text("빈칸을 채워 보세요 (${langLabel(lang)})", style = MaterialTheme.typography.labelLarge)
            Text(s.item.cloze!!.display, style = MaterialTheme.typography.headlineSmall)
            Text("뜻: ${s.item.hintKo}", style = MaterialTheme.typography.bodyMedium)
            s.answers.forEachIndexed { i, a ->
                val last = i == s.answers.lastIndex
                OutlinedTextField(
                    a, { vm.setAnswer(it, i) },
                    Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstField) else Modifier).onFocusChanged { if (it.isFocused) onFocusBlank(i) },
                    singleLine = true, label = { Text("빈칸 ${i + 1}") },
                    keyboardOptions = answerKeyboard(ExerciseKind.CLOZE, lang, if (last) ImeAction.Done else ImeAction.Next, voice),
                    keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                )
            }
        }
        ExerciseKind.DICTATION -> {
            LaunchedEffect(s.item.card.id, s.shownAt) { vm.speak(false) }
            Text("들리는 ${langLabel(lang)} 문장을 그대로 써 보세요", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.speak(false) }) { Text("다시 듣기") }
                OutlinedButton(onClick = { vm.speak(true) }) { Text("느리게 듣기") }
            }
            OutlinedTextField(
                s.answers[0], { vm.setAnswer(it) },
                Modifier.fillMaxWidth().focusRequester(firstField).onFocusChanged { if (it.isFocused) onFocusBlank(0) },
                minLines = 2, label = { Text("들은 문장") },
                keyboardOptions = answerKeyboard(ExerciseKind.DICTATION, lang, ImeAction.Done, voice), keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            )
        }
    }
}

@Composable
private fun GradedBody(s: ReviewUi.Graded, vm: ReviewViewModel, canListen: Boolean, heard: String?, onShadow: () -> Unit, onOpenSettings: () -> Unit) {
    verdictOf(s.outcome)?.let { VerdictBanner(it.name) }
    SectionCard(when (s.item.exercise) { ExerciseKind.TRANSLATE -> "문제"; ExerciseKind.CLOZE -> "완성된 문장"; ExerciseKind.DICTATION -> "뜻" }) {
        Text(
            when (s.item.exercise) { ExerciseKind.TRANSLATE -> s.item.promptText; ExerciseKind.CLOZE -> s.item.reference; ExerciseKind.DICTATION -> s.item.hintKo },
            style = MaterialTheme.typography.titleMedium,
        )
    }
    SectionCard("채점") {
        val local = s.outcome as? GradeOutcome.Local
        if (local != null && local.marks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("내 답 (틀린 단어에 밑줄)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MarkedWords(local.marks)
            }
        } else LabeledText("내 답", s.answer)
        when (val o = s.outcome) {
            is GradeOutcome.Ai -> {
                LabeledText("더 자연스러운 표현", o.result.corrected, valueColor = MaterialTheme.statusColors.success)
                LabeledText("해설", o.result.explanationKo, valueStyle = MaterialTheme.typography.bodyMedium)
            }
            is GradeOutcome.SelfGrade -> {
                LabeledText("정답", o.reference, valueColor = MaterialTheme.statusColors.success, valueStyle = MaterialTheme.typography.titleMedium)
                Text(if (o.aiError == null) "스스로 채점해 주세요." else "AI 채점을 쓸 수 없어서 스스로 채점해 주세요.", style = MaterialTheme.typography.bodyMedium)
                o.aiError?.let { kind ->
                    Text(aiErrorText(kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (kind.needsSettings()) OutlinedButton(onClick = onOpenSettings) { Text("설정 열기") }
                }
            }
            is GradeOutcome.Local -> if (s.item.exercise != ExerciseKind.CLOZE) {
                LabeledText("정답", o.reference, valueColor = MaterialTheme.statusColors.success, valueStyle = MaterialTheme.typography.titleMedium)
            }
        }
    }
    val showHint = s.item.exercise == ExerciseKind.CLOZE && s.item.hintKo.isNotBlank()
    if (showHint || s.item.note.isNotBlank() || s.item.keyPhrase != null) {
        SectionCard("참고") {
            if (showHint) LabeledText("뜻", s.item.hintKo)
            s.item.keyPhrase?.let { LabeledText("핵심 표현", it, valueColor = MaterialTheme.colorScheme.primary) }
            if (s.item.note.isNotBlank()) LabeledText("설명", s.item.note, valueStyle = MaterialTheme.typography.bodyMedium)
        }
    }
    // 듣고 따라 말하기: 정답 문장을 듣고, 음성 인식이 되면 소리 내어 말해 본다.
    SectionCard(if (canListen) "듣고 따라 말하기" else "정답 듣기") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.speak(false) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("듣기")
            }
            OutlinedButton(onClick = { vm.speak(true) }) { Text("느리게") }
            if (canListen) FilledTonalButton(onClick = onShadow) {
                Icon(Icons.Default.Mic, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("따라 말하기")
            }
        }
        heard?.let { h ->
            val r = shadowingResult(s.item.reference, h)
            val (msg, color) = when (r.verdict) {
                Verdict.CORRECT -> "또렷하게 말했어요!" to MaterialTheme.statusColors.success
                Verdict.CLOSE -> "거의 맞아요. 밑줄 친 단어를 다시 말해 보세요." to MaterialTheme.colorScheme.tertiary
                Verdict.WRONG -> "잘 안 들렸어요. 천천히 다시 말해 보세요." to MaterialTheme.colorScheme.error
            }
            Text(msg, style = MaterialTheme.typography.titleSmall, color = color)
            Text("들린 말", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MarkedWords(r.marks)
        }
    }
}

/** 단어마다 맞았는지 표시: 틀린 단어는 빨간 밑줄. */
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
private fun VerdictBanner(verdictName: String) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val ctx = LocalContext.current
    // 시스템에서 애니메이션을 끈 경우(접근성)에는 움직임을 생략한다.
    val animate = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val scale = remember { androidx.compose.animation.core.Animatable(if (animate) 0.92f else 1f) }
    LaunchedEffect(verdictName) {
        if (verdictName == "CORRECT") haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (animate) scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 400f))
    }
    val (color, bg, icon) = when (verdictName) {
        "CORRECT" -> Triple(MaterialTheme.statusColors.success, MaterialTheme.statusColors.success.copy(alpha = 0.12f), Icons.Default.CheckCircle)
        "CLOSE" -> Triple(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), Icons.Default.Info)
        else -> Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.12f), Icons.Default.Cancel)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Text(verdictLabel(verdictName), style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

@Composable
private fun StreakBadge(days: Int) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
            Text("${days}일 연속", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun verdictLabel(v: String) = when (v) { "CORRECT" -> "맞아요"; "CLOSE" -> "거의 맞아요"; else -> "틀렸어요" }
