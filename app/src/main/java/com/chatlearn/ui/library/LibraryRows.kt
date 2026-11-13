package com.chatlearn.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    item: LibraryItem, badge: String?, badgeAccent: Boolean, state: EntryState, folderName: String?,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().entryClick(selectionMode, checked, onClick, onLongClick)) {
        // 왼쪽 색 막대: 복습할 차례=보라, 새 문장=회색, 나중에 복습(이미 복습함)=초록
        val barColor = when (state) {
            EntryState.DUE -> MaterialTheme.colorScheme.primary
            EntryState.NEW -> MaterialTheme.colorScheme.outline
            EntryState.LATER -> MaterialTheme.statusColors.success
        }
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(barColor))
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1줄: 언어·태그 칩, 오른쪽에 상태 배지
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (item.entry.chatLang == Lang.EN) "EN" else "ES", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    folderName?.let { Pill(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                    item.tags.take(2).forEach { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                    if (item.tags.size > 2) Text("+${item.tags.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    badge?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelMedium,
                            color = if (badgeAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    item: LibraryItem, badge: String?, badgeAccent: Boolean, state: EntryState, folderName: String?,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val barColor = when (state) {
        EntryState.DUE -> MaterialTheme.colorScheme.primary
        EntryState.NEW -> MaterialTheme.colorScheme.outline
        EntryState.LATER -> MaterialTheme.statusColors.success
    }
    Column(Modifier.fillMaxWidth().entryClick(selectionMode, checked, onClick, onLongClick)) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().padding(vertical = 6.dp).background(barColor, MaterialTheme.shapes.extraSmall))
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Pill(if (item.entry.chatLang == Lang.EN) "EN" else "ES", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                folderName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 72.dp)) }
                badge?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = if (badgeAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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

