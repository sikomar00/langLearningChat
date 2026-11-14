package com.chatlearn.ui.library

import androidx.compose.ui.semantics.contentDescription
import com.chatlearn.ui.components.SegmentBar
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.chatlearn.domain.TagKind
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.components.FlapText
import com.chatlearn.ui.components.FlightCode
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 라이브러리 목록의 한 줄·카드·묶음 제목과 밀기 동작.

/** 상태 묶음의 이름: 안내판 말과 쉬운 말을 함께. */
internal fun boardTitle(s: EntryState): String = when (s) {
    EntryState.DUE -> "지금 탑승 · ${stateTitle(s)}"
    EntryState.LATER -> "출발 예정 · ${stateTitle(s)}"
    EntryState.NEW -> "탑승 대기 · ${stateTitle(s)}"
}

@Composable
internal fun GroupHeader(title: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("$count", style = MaterialTheme.typography.titleSmall.board(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            }
        }
    }
}


/** 출발 안내판의 열 머리(그림이라 스크린리더는 건너뛴다). 열 너비는 [CompactRow]와 같다. */
@Composable
internal fun BoardColumns() {
    val c = MaterialTheme.statusColors
    Row(
        Modifier.fillMaxWidth().background(c.board, MaterialTheme.shapes.extraSmall).padding(horizontal = 12.dp, vertical = 6.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoardLabel("시간", Modifier.width(TIME_COLUMN), color = c.flap)
        BoardLabel("목적지 · 편", Modifier.weight(1f), color = c.flap)
        BoardLabel("상태", color = c.flap)
    }
}

/**
 * 출발 안내판의 한 줄: 저장한 시각 · 외국어 문장과 뜻 · 함께 대화한 친구와 나라, 편(EN/ES) · 상태.
 * 아래 막대는 다음 복습까지의 거리: 지금 복습할 차례면 꽉 찬 호박 실선, 예정이면 날짜만큼 긴 점선, 새 문장은 빈 칸.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactRow(
    item: LibraryItem, progress: EntryProgress?, now: Long, folderName: String?,
    selectionMode: Boolean, checked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val saved = Instant.ofEpochMilli(item.entry.createdAt).atZone(ZoneId.systemDefault())
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).entryClick(selectionMode, checked, onClick, onLongClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectionMode) Checkbox(checked = checked, onCheckedChange = null)
            Column(Modifier.width(TIME_COLUMN)) {
                BoardLabel(saved.format(HOUR), color = MaterialTheme.colorScheme.onSurface)
                BoardLabel(saved.format(DAY), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(item.texts[item.entry.chatLang].orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlightCode(item.entry.chatLang.code)
                    Text(destinationOf(item, folderName), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DistanceBar(progress, now, Modifier.padding(top = 8.dp))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusMark(progress, now)
                val stage = progress?.stage ?: 0
                SegmentBar(stage, MAX_STAGE, Modifier.width(40.dp).height(4.dp).semantics { contentDescription = "익힌 단계 $stage/$MAX_STAGE" })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val TIME_COLUMN = 44.dp
private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("MM/dd")

/** 목적지: 함께 대화한 친구와 그 나라. 없으면 폴더 이름, 그것도 없으면 빈칸. */
internal fun destinationOf(item: LibraryItem, folderName: String?): String {
    fun of(kind: TagKind) = item.tags.filter { (item.tagKinds[it] ?: TagKind.TOPIC) == kind }
    return (of(TagKind.PERSON) + of(TagKind.COUNTRY)).joinToString(" · ").ifEmpty { folderName.orEmpty() }
}

/** 상태 열: 지금 복습할 차례는 호박 알약, 예정은 초록 점과 남은 날, 새 문장은 테두리만. 바뀌면 플랩처럼 넘어간다. */
@Composable
internal fun StatusMark(progress: EntryProgress?, now: Long) {
    val p = progress ?: EntryProgress(EntryState.NEW, null, 0)
    val style = MaterialTheme.typography.labelMedium.board()
    when (p.state) {
        EntryState.DUE -> FlapText(
            dueBadge(p, now), style, color = MaterialTheme.statusColors.onFlap,
            modifier = Modifier.background(MaterialTheme.statusColors.flap, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        EntryState.LATER -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(MaterialTheme.statusColors.success, CircleShape))
            FlapText(dueBadge(p, now), style, color = MaterialTheme.colorScheme.onSurface)
        }
        EntryState.NEW -> Text(
            dueBadge(p, now), Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
            style = style, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 다음 복습까지의 거리(그림). 상태는 색과 함께 선 모양으로도 다르다: 실선 · 점선 · 빈 칸. 14일 이상이면 꽉 찬다. */
@Composable
internal fun DistanceBar(progress: EntryProgress?, now: Long, modifier: Modifier = Modifier) {
    when (progress?.state ?: EntryState.NEW) {
        EntryState.DUE -> Box(modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.statusColors.flapGraphic))
        EntryState.NEW -> Box(modifier.size(24.dp, 6.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall))
        EntryState.LATER -> {
            val days = ((progress?.nextDueAt ?: now) - now) / 86_400_000f
            val color = MaterialTheme.colorScheme.outline
            Canvas(modifier.fillMaxWidth((days / 14f).coerceIn(0.08f, 1f)).height(2.dp)) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
        }
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

