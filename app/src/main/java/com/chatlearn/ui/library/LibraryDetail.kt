package com.chatlearn.ui.library

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.selection.SelectionContainer
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.components.langLabel
import com.chatlearn.ui.components.SegmentBar
import com.chatlearn.ui.components.FlightCode
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.components.Ticket
import com.chatlearn.ui.components.BoardTopBar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.foundation.background
import com.chatlearn.ui.theme.statusColors
import com.chatlearn.ui.components.EmptyState
import com.chatlearn.ui.components.LabeledText
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.components.LoadingBox
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import com.chatlearn.data.FolderEntity
import com.chatlearn.ui.components.PastDatePickerDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.chatlearn.data.RenameResult
import com.chatlearn.domain.TextOrigin
import com.chatlearn.stats.StatsCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.chatlearn.ui.translate.parseTags
import com.chatlearn.ui.translate.suggestTags
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Lang

// 문장 상세(보기·고치기)와 태그 관리 창. 목록 화면(LibraryScreen.kt)이 500줄을 넘어 나눴다.

@Composable
internal fun DetailDialog(item: LibraryItem, hasAltCard: Boolean, recentTags: List<String>, folders: List<FolderEntity>, vm: LibraryViewModel, onReview: () -> Unit, onClose: () -> Unit) {
    var ko by remember(item.entry.id) { mutableStateOf(item.texts[Lang.KO].orEmpty()) }
    var note by remember(item.entry.id) { mutableStateOf(item.entry.note) }
    val altLang = if (item.entry.chatLang == Lang.EN) Lang.ES else Lang.EN
    val altText = item.texts[altLang]
    var alt by remember(item.entry.id, altText) { mutableStateOf(altText.orEmpty()) }
    var chatKey by remember(item.entry.id, item.keyPhrases[item.entry.chatLang]) { mutableStateOf(item.keyPhrases[item.entry.chatLang].orEmpty()) }
    var altKey by remember(item.entry.id, item.keyPhrases[altLang]) { mutableStateOf(item.keyPhrases[altLang].orEmpty()) }
    val editedTags = remember(item.entry.id) { mutableStateListOf(*item.tags.toTypedArray()) }
    var tagInput by remember(item.entry.id) { mutableStateOf("") }

    fun addTags(names: List<String>) = names.forEach { if (it !in editedTags) editedTags.add(it) }

    // 창을 연 순간의 값과 비교한다(열려 있는 동안 다른 곳에서 바뀌어도 "고쳤다"로 보지 않게).
    val opened = remember(item.entry.id) { listOf(ko, note, alt, chatKey, altKey) to item.tags.toSet() }
    val dirty = listOf(ko, note, alt, chatKey, altKey) != opened.first || editedTags.toSet() != opened.second || tagInput.isNotBlank()
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (dirty) confirmDiscard = true else onClose() }
    // 저장 버튼과, 문장을 밖으로 보내는 버튼(노션·답장)이 같은 저장을 쓴다: 고친 내용을 버리지 않고 반영한 뒤 보낸다.
    val saveAll = {
                        vm.saveText(item.entry.id, Lang.KO, ko); vm.saveNote(item.entry.id, note)
                        if (hasAltCard && altText != null && alt.isNotBlank() && alt != altText) vm.saveText(item.entry.id, altLang, alt)
                        // 입력창에 쓰다 만 태그도 잃지 않게 함께 저장한다.
                        vm.saveTags(item.entry.id, (editedTags + parseTags(tagInput)).distinct())
                        if (chatKey != item.keyPhrases[item.entry.chatLang].orEmpty()) vm.saveKeyPhrase(item.entry.id, item.entry.chatLang, chatKey)
                        if (hasAltCard && altText != null && altKey != item.keyPhrases[altLang].orEmpty()) vm.saveKeyPhrase(item.entry.id, altLang, altKey)
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("고친 내용을 버릴까요?") },
        text = { Text("저장하지 않은 수정이 있어요.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("버리기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("계속 고치기") } },
    )

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                BoardTopBar("문장", navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = "닫기") } }) {
                    TextButton(onClick = { saveAll(); onClose() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.statusColors.flap)) { Text("저장") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SentenceTicket(item, vm)
                item.entry.contextText?.let { ContextBubbles(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.entry.direction == com.chatlearn.domain.Direction.RECEIVED && item.entry.kind == com.chatlearn.domain.EntryKind.SENTENCE) {
                        Button(shape = MaterialTheme.shapes.medium, onClick = { saveAll(); vm.replyRequest.value = item.texts[item.entry.chatLang].orEmpty() to item.entry.chatLang; onClose() }, modifier = Modifier.weight(1f)) {
                            Text("답장 써 보기")
                        }
                    }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { saveAll(); onReview() }, modifier = Modifier.weight(1f)) {
                        Text("이 문장 복습")
                    }
                }
                ReviewHistory(item, vm)
                SectionCard("뜻 고치기") {
                    OutlinedTextField(ko, { ko = it }, label = { Text("한국어 뜻") }, modifier = Modifier.fillMaxWidth())
                    if (hasAltCard && altText != null) {
                        OutlinedTextField(
                            alt, { alt = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (altLang == Lang.ES) "스페인어 확장" else "영어 확장") },
                            supportingText = { if (item.origins[altLang] == TextOrigin.AI) Text("AI 번역 · 고치면 내 문장으로 저장돼요") },
                        )
                    } else if (!hasAltCard) {
                        var making by remember { mutableStateOf(false) }
                        var failed by remember { mutableStateOf(false) }
                        val scope2 = rememberCoroutineScope()
                        OutlinedButton(shape = MaterialTheme.shapes.medium, enabled = !making, onClick = {
                            making = true; failed = false
                            scope2.launch { failed = !vm.expandOne(item.entry.id).await(); making = false }
                        }) { Text(if (making) "만드는 중…" else "다른 언어 카드 만들기") }
                        if (failed) Text("만들지 못했어요. 잠시 후 다시 시도해 주세요.", color = MaterialTheme.colorScheme.error)
                    }
                }
                SectionCard("핵심 표현") {
                    Text("빈칸 채우기에서 이 표현이 빈칸이 돼요. 문장 안에 있는 그대로 적어야 해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        chatKey, { chatKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (item.entry.chatLang == Lang.EN) "영어" else "스페인어") },
                    )
                    if (hasAltCard && altText != null) {
                        OutlinedTextField(
                            altKey, { altKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (altLang == Lang.ES) "스페인어" else "영어") },
                        )
                    }
                }
                SectionCard("설명 · 메모") {
                    OutlinedTextField(note, { note = it }, label = { Text("설명") }, modifier = Modifier.fillMaxWidth())
                }
                SectionCard("분류") {
                    Text("폴더", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = item.entry.folderId == null, onClick = { vm.moveToFolder(listOf(item.entry.id), null) }, label = { Text("없음") })
                        folders.forEach { f ->
                            FilterChip(selected = item.entry.folderId == f.id, onClick = { vm.moveToFolder(listOf(item.entry.id), f.id) }, label = { Text(f.name) })
                        }
                    }
                    var pickingDate by remember { mutableStateOf(false) }
                    val zone = ZoneId.systemDefault()
                    val learned = Instant.ofEpochMilli(item.entry.createdAt).atZone(zone).toLocalDate()
                    LabeledText("알게 된 날", learned.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", java.util.Locale.KOREAN)))
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { pickingDate = true }) { Text("날짜 바꾸기") }
                    if (pickingDate) PastDatePickerDialog(
                        initial = learned, today = java.time.LocalDate.now(zone), onDismiss = { pickingDate = false },
                        onPick = { d ->
                            // 같은 날을 다시 고르면 원래 시각을 유지한다.
                            if (d != learned) vm.saveLearnedAt(item.entry.id, d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
                            pickingDate = false
                        },
                    )
                }
                SectionCard("태그") {
                    if (editedTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(editedTags.toList()) { tag ->
                                InputChip(
                                    selected = false, onClick = { editedTags.remove(tag) }, label = { Text(tag) },
                                    // 사람·나라 태그는 아이콘으로 구분한다.
                                    leadingIcon = when (item.tagKinds[tag]) {
                                        com.chatlearn.domain.TagKind.PERSON -> ({ Icon(Icons.Default.Person, contentDescription = "사람", modifier = Modifier.size(18.dp)) })
                                        com.chatlearn.domain.TagKind.COUNTRY -> ({ Icon(Icons.Default.Public, contentDescription = "나라", modifier = Modifier.size(18.dp)) })
                                        else -> null
                                    },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "$tag 제거", modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = tagInput, onValueChange = { tagInput = it }, singleLine = true,
                        label = { Text("태그 추가(쉼표로 여러 개)") }, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { addTags(parseTags(tagInput)); tagInput = "" }, enabled = tagInput.isNotBlank()) { Text("추가") }
                        },
                    )
                    val suggestions = suggestTags(recentTags.filter { it !in editedTags }, tagInput)
                    if (suggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions) { tag ->
                                SuggestionChip(onClick = { addTags(listOf(tag)); tagInput = "" }, label = { Text(tag) })
                            }
                        }
                    }
                }
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { saveAll(); vm.sendToNotion(listOf(item.entry.id)); onClose() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (item.entry.notionPageId == null) "노션에 보내기" else "노션에 다시 보내기")
                    }
                    HorizontalDivider()
                    TextButton(onClick = { vm.delete(item.entry.id); onClose() }) {
                        Text("이 문장 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TagManagerDialog(tags: List<String>, vm: LibraryViewModel, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("태그 관리") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이름을 바꾸면 모든 문장에 적용돼요. 이미 있는 이름으로 바꾸면 두 태그가 합쳐져요.", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                tags.forEach { tag ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, Modifier.weight(1f))
                        IconButton(onClick = { editing = tag; newName = tag }) { Icon(Icons.Default.Edit, contentDescription = "$tag 이름 바꾸기") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )

    editing?.let { old ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("'$old' 이름 바꾸기") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("새 이름") }) },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    scope.launch {
                        message = when (vm.renameTag(old, newName).await()) {
                            RenameResult.Renamed -> "'$old' → '${newName.trim()}'로 바꿨어요."
                            RenameResult.Merged -> "'${newName.trim()}'와 합쳤어요."
                            RenameResult.Unchanged -> "바뀐 게 없어요."
                        }
                        editing = null
                    }
                }) { Text("바꾸기") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } },
        )
    }
}

/**
 * 문장 한 장을 탑승권으로: 머리띠에 경로와 저장한 때, 본문에 문장·뜻·편과 친구·나라·상태·익힌 단계·거리,
 * 절취선 아래에 복습 기록(안내판 숫자)과 다음 복습.
 */
@Composable
internal fun SentenceTicket(item: LibraryItem, vm: LibraryViewModel) {
    val input by vm.statsInput.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val st = remember(item.entry.id, input) { input?.let { StatsCalculator.sentenceStats(item.entry.id, it.logs, it.cards) } }
    val p = progress[item.entry.id]
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    fun at(ms: Long?) = ms?.let { REVIEWED_AT.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "-"
    val saved = Instant.ofEpochMilli(item.entry.createdAt).atZone(zone).format(SAVED_AT)
    // 기록을 읽기 전에는 떼는 칸을 비워 둔다(0회로 보였다가 바뀌지 않게).
    val stub: (@Composable ColumnScope.() -> Unit)? = st?.let { r -> {
        if (r.reviews == 0) Text("아직 복습하지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            listOf(Triple("복습", r.reviews, false), Triple("기억", r.remembered, false), Triple("틀림", r.lapses, r.lapses > 0)).forEach { (label, n, bad) ->
                Column(Modifier.weight(1f)) {
                    BoardLabel(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$n", style = MaterialTheme.typography.headlineSmall.board(), color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledText("마지막 복습", at(r.lastReviewedAt), Modifier.weight(1f), valueStyle = MaterialTheme.typography.bodyLarge.board())
            LabeledText("다음 복습", at(r.dueAt), Modifier.weight(1f), valueStyle = MaterialTheme.typography.bodyLarge.board())
        }
    } }
    Ticket(route = "${langLabel(item.entry.chatLang)} → 한국어 · $saved", stub = stub) {
        SelectionContainer { Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.headlineSmall) }
        Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlightCode(item.entry.chatLang.code)
            Text(destinationOf(item, null), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            StatusMark(p, now)
        }
        val stage = p?.stage ?: 0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardLabel("${stageName(stage)} · $stage/$MAX_STAGE", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentBar(stage, MAX_STAGE, Modifier.weight(1f).height(8.dp))
        }
        DistanceBar(p, now)
    }
}

sealed interface FolderDialogKind {
    data object Create : FolderDialogKind
    data class Rename(val folder: FolderEntity) : FolderDialogKind
    data class Delete(val folder: FolderEntity) : FolderDialogKind
}

/** 문장을 넣을 폴더를 고르거나 새로 만든다. */
@Composable
internal fun FolderPickerDialog(
    folders: List<FolderEntity>, counts: Map<Long, Int>, canRemove: Boolean,
    onPick: (Long?) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("폴더로 옮기기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (folders.isEmpty()) Text("아직 폴더가 없어요. 아래에서 만들어 보세요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                folders.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(f.id) }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${counts[f.id] ?: 0}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (canRemove) TextButton(onClick = { onPick(null) }) { Text("폴더에서 꺼내기") }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("새 폴더 이름") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                )
                Button(shape = MaterialTheme.shapes.medium, enabled = name.isNotBlank(), onClick = { onCreate(name) }) { Text("만들어서 옮기기") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 이름 입력 창. [onConfirm]이 문자열을 돌려주면 그것을 오류 문구로 보여 주고, null이면 성공으로 보고 호출한 쪽이 닫는다. */
@Composable
internal fun NameDialog(title: String, initial: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: suspend (String) -> String?) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun submit() { scope.launch { error = onConfirm(name) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it; error = null }, singleLine = true, label = { Text("폴더 이름") },
                isError = error != null, supportingText = { error?.let { Text(it) } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { submit() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val SAVED_AT = DateTimeFormatter.ofPattern("MM/dd HH:mm")
private val REVIEWED_AT = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

