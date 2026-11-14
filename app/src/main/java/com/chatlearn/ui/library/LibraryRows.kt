package com.chatlearn.ui.library

import com.chatlearn.ui.theme.sentence
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Lang

// 라이브러리 목록의 한 줄·카드·묶음 제목과 밀기 동작.

@Composable
internal fun GroupHeader(title: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun Pill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryCard(
    item: LibraryItem, badge: String?, badgeAccent: Boolean, folderName: String?,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().entryClick(selectionMode, checked, onClick, onLongClick)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1줄: 언어·태그 칩, 오른쪽에 상태 배지
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (item.entry.chatLang == Lang.EN) "EN" else "ES", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    folderName?.let { Pill(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                    item.tags.take(2).forEach { Pill(it, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (item.tags.size > 2) Text("+${item.tags.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    badge?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelMedium,
                            color = if (badgeAccent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.titleMedium.sentence(), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            }
        }
    }
}


/** 한 줄에 한 문장: 외국어 한 줄 + 한국어 한 줄. 정보 밀도를 높인 기본 보기. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactRow(
    item: LibraryItem, badge: String?, badgeAccent: Boolean, folderName: String?,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().entryClick(selectionMode, checked, onClick, onLongClick)) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.bodyLarge.sentence(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Pill(if (item.entry.chatLang == Lang.EN) "EN" else "ES", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                folderName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 72.dp)) }
                badge?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = if (badgeAccent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 한 줄을 옆으로 밀어 동작을 한다: 오른쪽으로 밀면 폴더 이동(제자리로 돌아옴), 왼쪽으로 밀면 삭제(되돌리기 가능).
 * 선택 모드에서는 밀기를 끈다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeRow(enabled: Boolean, onMove: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { onMove(); false }
            SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
            SwipeToDismissBoxValue.Settled -> false
        }
    })
    SwipeToDismissBox(
        state = state, enableDismissFromStartToEnd = enabled, enableDismissFromEndToStart = enabled,
        // 밀기 동작을 할 수 없는 사용자(스크린리더)도 같은 일을 메뉴로 할 수 있게.
        modifier = if (enabled) Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("폴더로 옮기기") { onMove(); true },
                CustomAccessibilityAction("삭제") { onDelete(); true },
            )
        } else Modifier,
        backgroundContent = {
            val toRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val toLeft = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val bg = when {
                toRight -> MaterialTheme.colorScheme.tertiaryContainer
                toLeft -> MaterialTheme.colorScheme.errorContainer
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            Box(Modifier.fillMaxSize().background(bg).padding(horizontal = 20.dp), contentAlignment = if (toLeft) Alignment.CenterEnd else Alignment.CenterStart) {
                if (toRight) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("폴더로", color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelLarge)
                }
                if (toLeft) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("삭제", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) {
        // 밀 때 뒤의 색이 비치지 않게 불투명 배경을 깐다.
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

/** 선택 모드에서는 체크 상자처럼(스크린리더가 "선택됨"을 읽는다), 평소에는 누르기·길게 누르기. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.entryClick(selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    if (selectionMode) toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
    else combinedClickable(onClickLabel = "자세히 보기", onLongClickLabel = "여러 개 선택", onClick = onClick, onLongClick = onLongClick)

