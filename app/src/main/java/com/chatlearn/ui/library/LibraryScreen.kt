package com.chatlearn.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Alignment
import com.chatlearn.data.RenameResult
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

@Composable
fun LibraryScreen(vm: LibraryViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val lang by vm.langFilter.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    val tags by vm.allTags.collectAsStateWithLifecycle()
    val recentTags by vm.recentTags.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LibraryItem?>(null) }
    var managingTags by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = query, onValueChange = { vm.query.value = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("검색") })
        if (tags.isNotEmpty()) TextButton(onClick = { managingTags = true }) { Text("태그 관리") }
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
                Card(Modifier.fillMaxWidth().clickable { selected = item }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    selected?.let { item -> DetailDialog(item, recentTags, vm) { selected = null } }
    if (managingTags) TagManagerDialog(tags, vm) { managingTags = false }
}

@Composable
private fun DetailDialog(item: LibraryItem, recentTags: List<String>, vm: LibraryViewModel, onClose: () -> Unit) {
    var ko by remember(item.entry.id) { mutableStateOf(item.texts[Lang.KO].orEmpty()) }
    var note by remember(item.entry.id) { mutableStateOf(item.entry.note) }
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
