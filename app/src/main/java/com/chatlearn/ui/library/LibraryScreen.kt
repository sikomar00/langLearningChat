package com.chatlearn.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    var selected by remember { mutableStateOf<LibraryItem?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = query, onValueChange = { vm.query.value = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("검색") })
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

    selected?.let { item -> DetailDialog(item, vm) { selected = null } }
}

@Composable
private fun DetailDialog(item: LibraryItem, vm: LibraryViewModel, onClose: () -> Unit) {
    var ko by remember(item.entry.id) { mutableStateOf(item.texts[Lang.KO].orEmpty()) }
    var note by remember(item.entry.id) { mutableStateOf(item.entry.note) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.texts[item.entry.chatLang].orEmpty()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ko, { ko = it }, label = { Text("한국어") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("설명") }, modifier = Modifier.fillMaxWidth())
                if (item.tags.isNotEmpty()) Text("태그: " + item.tags.joinToString())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveText(item.entry.id, Lang.KO, ko); vm.saveNote(item.entry.id, note); onClose()
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
