package com.chatlearn.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Lang

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onStartReview: (Set<Long>, Lang?) -> Unit = { _, _ -> }) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val lang by vm.langFilter.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    val tags by vm.allTags.collectAsStateWithLifecycle()
    val recentTags by vm.recentTags.collectAsStateWithLifecycle()
    // id만 기억하고 항목은 매번 최신 데이터에서 찾아서, 열린 상세 창이 확장 결과를 바로 반영하게 한다.
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = items.firstOrNull { it.entry.id == selectedId }
    val expandedIds by vm.expandedIds.collectAsStateWithLifecycle()
    var managingTags by remember { mutableStateOf(false) }
    // 길게 눌러 들어가는 선택 모드. 필터가 바뀌어 사라진 항목은 선택에서 뺀다.
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    val visibleIds = items.map { it.entry.id }.toSet()
    val chosen = picked?.intersect(visibleIds).orEmpty()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (picked != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${chosen.size}개 선택", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { picked = if (chosen.size == visibleIds.size) emptySet() else visibleIds }) {
                    Text(if (chosen.size == visibleIds.size) "선택 해제" else "전체 선택")
                }
                TextButton(onClick = { picked = null }) { Text("취소") }
                Button(enabled = chosen.isNotEmpty(), onClick = { onStartReview(chosen, lang); picked = null }) { Text("선택 복습") }
            }
        }
        OutlinedTextField(value = query, onValueChange = { vm.query.value = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("검색") })
        val unexpanded by vm.unexpanded.collectAsStateWithLifecycle()
        var expandMessage by remember { mutableStateOf<String?>(null) }
        val expanding by vm.expanding.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tags.isNotEmpty()) TextButton(onClick = { managingTags = true }) { Text("태그 관리") }
            if (unexpanded > 0) TextButton(enabled = !expanding, onClick = {
                scope.launch {
                    val r = vm.expandAll().await()
                    expandMessage = "다른 언어 카드 ${r.created}개를 만들었어요." +
                        if (r.failedBatches > 0) " (실패한 묶음 ${r.failedBatches}개는 다시 눌러 보세요)" else ""
                }
            }) { Text(if (expanding) "확장 중…" else "전체 확장 ($unexpanded)") }
        }
        expandMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Lang.EN to "영어", Lang.ES to "스페인어").forEach { (l, label) ->
                FilterChip(selected = lang == l, onClick = { vm.langFilter.value = if (lang == l) null else l }, label = { Text(label) })
            }
            tags.forEach { t ->
                FilterChip(selected = tag == t, onClick = { vm.tagFilter.value = if (tag == t) null else t }, label = { Text(t) })
            }
        }
        if (items.isEmpty()) {
            Text("저장된 문장이 없어요. 번역 탭에서 문장을 저장해 보세요.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.entry.id }) { item ->
                val id = item.entry.id
                Card(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (picked != null) picked = if (id in chosen) chosen - id else chosen + id else selectedId = id },
                    onLongClick = { if (picked == null) picked = setOf(id) },
                )) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (picked != null) Checkbox(checked = id in chosen, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    selected?.let { item -> DetailDialog(item, item.entry.id in expandedIds, recentTags, vm) { selectedId = null } }
    if (managingTags) TagManagerDialog(tags, vm) { managingTags = false }
}

@Composable
private fun DetailDialog(item: LibraryItem, hasAltCard: Boolean, recentTags: List<String>, vm: LibraryViewModel, onClose: () -> Unit) {
    var ko by remember(item.entry.id) { mutableStateOf(item.texts[Lang.KO].orEmpty()) }
    var note by remember(item.entry.id) { mutableStateOf(item.entry.note) }
    val altLang = if (item.entry.chatLang == Lang.EN) Lang.ES else Lang.EN
    val altText = item.texts[altLang]
    var alt by remember(item.entry.id, altText) { mutableStateOf(altText.orEmpty()) }
    val editedTags = remember(item.entry.id) { mutableStateListOf(*item.tags.toTypedArray()) }
    var tagInput by remember(item.entry.id) { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun addTags(names: List<String>) = names.forEach { if (it !in editedTags) editedTags.add(it) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.texts[item.entry.chatLang].orEmpty()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SentenceStatsLine(item.entry.id, vm)
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
                    OutlinedButton(enabled = !making, onClick = {
                        making = true; failed = false
                        scope2.launch { failed = !vm.expandOne(item.entry.id).await(); making = false }
                    }) { Text(if (making) "만드는 중…" else "다른 언어 카드 만들기") }
                    if (failed) Text("만들지 못했어요. 잠시 후 다시 시도해 주세요.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(ko, { ko = it }, label = { Text("한국어") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("설명") }, modifier = Modifier.fillMaxWidth())
                Text("태그", style = MaterialTheme.typography.labelLarge)
                if (editedTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(editedTags.toList()) { tag ->
                            InputChip(
                                selected = false, onClick = { editedTags.remove(tag) }, label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "$tag 제거", modifier = Modifier.size(16.dp)) },
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
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveText(item.entry.id, Lang.KO, ko); vm.saveNote(item.entry.id, note)
                if (hasAltCard && altText != null && alt.isNotBlank() && alt != altText) vm.saveText(item.entry.id, altLang, alt)
                // 입력창에 쓰다 만 태그도 잃지 않게 함께 저장한다.
                vm.saveTags(item.entry.id, (editedTags + parseTags(tagInput)).distinct())
                onClose()
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { if (confirmDelete) { vm.delete(item.entry.id); onClose() } else confirmDelete = true }) {
                    Text(if (confirmDelete) "정말 삭제" else "삭제")
                }
                TextButton(onClick = onClose) { Text("닫기") }
            }
        },
    )
}

@Composable
private fun TagManagerDialog(tags: List<String>, vm: LibraryViewModel, onClose: () -> Unit) {
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

@Composable
private fun SentenceStatsLine(entryId: Long, vm: LibraryViewModel) {
    val input by vm.statsInput.collectAsStateWithLifecycle()
    val st = input?.let { StatsCalculator.sentenceStats(entryId, it.logs, it.cards) } ?: return
    val fmt = remember { DateTimeFormatter.ofPattern("M월 d일 HH:mm") }
    fun at(ms: Long?) = ms?.let { fmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "-"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (st.reviews == 0) "아직 복습하지 않았어요."
                else "복습 ${st.reviews}회 · 기억 ${st.remembered}회 · 틀림 ${st.lapses}회",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("마지막 복습 ${at(st.lastReviewedAt)}  ·  다음 복습 ${at(st.dueAt)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
