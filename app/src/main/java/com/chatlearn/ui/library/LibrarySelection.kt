package com.chatlearn.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.Lang
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors

// 라이브러리 선택 모드: 검색 막대 자리에 "✕ · n개 선택 · 전체 선택", 아래 탭 자리에 행동 막대.

/** 지금 고른 문장(지워진 문장은 뺀다). */
@Composable
private fun chosenOf(vm: LibraryViewModel): Set<Long> {
    val picked by vm.selection.collectAsStateWithLifecycle()
    val everything by vm.everything.collectAsStateWithLifecycle()
    return remember(picked, everything) { picked.orEmpty().intersect(everything.mapTo(HashSet()) { it.entry.id }) }
}

/** 선택 중 라이브러리 맨 위 줄: ✕ · n개 선택 · 전체 선택/선택 해제. */
@Composable
internal fun LibrarySelectionHeader(vm: LibraryViewModel) {
    val chosen = chosenOf(vm)
    val items by vm.items.collectAsStateWithLifecycle()
    val visible = remember(items) { items.mapTo(HashSet()) { it.entry.id } }
    val all = visible.isNotEmpty() && chosen.containsAll(visible)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.selection.value = null }) { Icon(Icons.Default.Close, contentDescription = "선택 끝내기") }
        Text("${chosen.size}개 선택", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.board())
        TextButton(onClick = { vm.selection.value = if (all) chosen - visible else chosen + visible }) { Text(if (all) "선택 해제" else "전체 선택") }
    }
}

/** 엄지가 닿는 아래 검은 막대 한 줄: 선택 복습 · 폴더로 · ⋮(노션에 보내기 · 삭제). */
@Composable
internal fun LibrarySelectionBar(vm: LibraryViewModel, onReview: (Set<Long>, Lang?) -> Unit) {
    val chosen = chosenOf(vm)
    val lang by vm.langFilter.collectAsStateWithLifecycle()
    val sending by vm.sendingToNotion.collectAsStateWithLifecycle()
    val c = MaterialTheme.statusColors
    var more by remember { mutableStateOf(false) }
    val enabled = chosen.isNotEmpty()
    Surface(color = c.board, contentColor = c.onBoard) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { onReview(chosen, lang); vm.selection.value = null }, enabled = enabled,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.onBoard, contentColor = c.board, disabledContainerColor = c.boardRaised, disabledContentColor = c.onBoardMuted,
                ),
            ) { Text("선택 복습") }
            TextButton(onClick = { vm.selectionRequest.value = SelectionRequest.MOVE }, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = c.onBoard, disabledContentColor = c.onBoardMuted)) {
                Icon(Icons.Default.Folder, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("폴더로")
            }
            Box {
                IconButton(onClick = { more = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, contentDescription = "더 보기", tint = if (enabled) c.onBoard else c.onBoardMuted) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text(if (sending) "보내는 중…" else "노션에 보내기") }, enabled = !sending,
                        onClick = { more = false; vm.sendToNotion(chosen) },
                    )
                    DropdownMenuItem(
                        text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { more = false; vm.selectionRequest.value = SelectionRequest.DELETE },
                    )
                }
            }
        }
    }
}
