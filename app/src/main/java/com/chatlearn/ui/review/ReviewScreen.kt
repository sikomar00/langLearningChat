package com.chatlearn.ui.review

import com.chatlearn.domain.langLabel
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.components.SegmentBar
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.Lang
import com.chatlearn.domain.ratingChoices
import com.chatlearn.domain.ratingLabel
import com.chatlearn.review.specialChars
import com.chatlearn.review.spokenText
import com.chatlearn.settings.VoicePrefs
import com.chatlearn.ui.components.ButtonSpinner
import com.chatlearn.ui.components.GoalBoard
import com.chatlearn.ui.components.StreakBadge
import com.chatlearn.ui.components.LoadingBox
import com.chatlearn.ui.theme.statusColors

/** 복습 세션 전체 화면: 위에 진행, 가운데 대화처럼 이어지는 문제와 채점, 아래에 손이 닿는 행동 버튼. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewScreen(vm: ReviewViewModel, voice: VoicePrefs, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val moreNew by vm.moreNew.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val combo by vm.combo.collectAsStateWithLifecycle()
    val remembered by vm.remembered.collectAsStateWithLifecycle()
    val celebrate by vm.celebrate.collectAsStateWithLifecycle()
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
    BackHandler(onBack = onClose)
    // 앱이 다시 만들어져 이 화면만 되살아났으면(복습하던 중 프로세스 종료) 오늘 복습을 다시 연다.
    LaunchedEffect(Unit) { vm.ensureOpen() }

    val inSession = state is ReviewUi.Prompt || state is ReviewUi.Graded
    Box(Modifier.fillMaxSize()) {
        val world = MaterialTheme.statusColors
        Column(Modifier.fillMaxSize()) {
          // 출발 안내판 띠: 닫기 · 이번 복습의 불빛 칸 · 몇 번째인지.
          Surface(color = world.board, contentColor = world.onBoard) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "복습 닫기") }
                if (inSession && progress.total > 0) {
                    SegmentBar(progress.done, progress.total, Modifier.weight(1f).height(8.dp).padding(horizontal = 8.dp))
                    Text("${minOf(progress.done + 1, progress.total)}/${progress.total}", style = MaterialTheme.typography.labelLarge.board())
                } else Spacer(Modifier.weight(1f))
                if (state is ReviewUi.Prompt) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "카드 메뉴") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("문장 수정") }, onClick = { menuOpen = false; editing = true })
                            DropdownMenuItem(text = { Text("오늘은 건너뛰기") }, onClick = { menuOpen = false; vm.skipToday() })
                            DropdownMenuItem(text = { Text("한 달 쉬기") }, onClick = { menuOpen = false; vm.snoozeMonth() })
                            DropdownMenuItem(text = { Text("이 문장 삭제", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                }
            }
          }
          Column(Modifier.weight(1f).navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            if (inSession) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (custom != null) Text("선택 복습", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (goal.enabled) Text(
                    if (goal.reached) "오늘 목표 달성" else "오늘 ${goal.done}/${goal.goal}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (goal.reached) MaterialTheme.statusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (combo >= 3) StreakBadge(combo, label = "${combo}연속 기억")
                if (canUndo && state is ReviewUi.Prompt) TextButton(onClick = { vm.undo() }) { Text("방금 평가 취소") }
            }

            // 가운데: 본문만 스크롤한다. 카드가 바뀌면 옆으로 넘기듯 바뀐다.
            AnimatedContent(
                targetState = state, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentKey = { s -> (s as? ReviewUi.Prompt)?.item?.card?.id?.let { "p$it" } ?: (s as? ReviewUi.Graded)?.item?.card?.id?.let { "g$it" } ?: s::class.simpleName },
                transitionSpec = {
                    val nextCard = targetState is ReviewUi.Prompt
                    if (nextCard) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    else fadeIn() togetherWith fadeOut()
                },
                label = "review-body",
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (s) {
                        ReviewUi.Loading -> LoadingBox()
                        ReviewUi.Empty -> EmptyBody(custom != null, moreNew, vm, onClose)
                        is ReviewUi.Done -> DoneBody(SessionSummary(s, remembered, streak, goal, custom != null, moreNew), { vm.learnMore(LEARN_MORE) }, onClose)
                        is ReviewUi.Prompt -> PromptBody(
                            s, vm, canListen, voice,
                            onFocusBlank = { focusedBlank = it },
                            onMic = { listen(s.item.card.answerLang, SpeechTarget.Answer) },
                        )
                        is ReviewUi.Graded -> GradedBody(
                            s, vm, canListen,
                            heard = heard?.takeIf { it.first == s.item.card.id }?.second,
                            onShadow = { listen(s.item.card.answerLang, SpeechTarget.Shadow(s.item.card.id)) },
                        )
                    }
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
                                    shape = MaterialTheme.shapes.medium,
                                    onClick = { vm.appendToAnswer(ch, focusedBlank.coerceIn(0, s.answers.lastIndex)) },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                                ) { Text(ch) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !s.checking, onClick = { vm.giveUp() }) { Text("모르겠어요") }
                        if (progress.total - progress.done > 1) TextButton(enabled = !s.checking, onClick = { vm.postpone() }) { Text("나중에") }
                        Button(
                            onClick = { vm.submit() }, enabled = s.answers.none { it.isBlank() } && !s.checking,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium,
                        ) { if (s.checking) ButtonSpinner() else Text("확인") }
                    }
                }
                is ReviewUi.Graded -> BottomBar {
                    val choices = ratingChoices(verdictOf(s.outcome))
                    Text("얼마나 기억났나요?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = if (choices.size > 3) 2 else choices.size,
                    ) {
                        choices.forEach { r ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val label = ratingLabel(r)
                                if (r == s.suggested) Button(shape = MaterialTheme.shapes.medium, onClick = { vm.rate(r) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label) }
                                else OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { vm.rate(r) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label) }
                                Text(s.previews[r].orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                else -> Unit
            }
          }
        }
        if (celebrate) GoalBoard(onFinished = vm::celebrated)
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
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCurrent() }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

/** 아래에 고정되는 행동 영역. 본문과 구분되게 위에 얇은 선을 긋는다. */
@Composable
private fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
