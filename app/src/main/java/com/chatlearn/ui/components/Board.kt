package com.chatlearn.ui.components

import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors

/** 화면 맨 위 안내판 막대: 검은 판 위에 화면 이름, 오른쪽에 행동. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTopBar(title: String, navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    val c = MaterialTheme.statusColors
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        navigationIcon = navigationIcon, actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.board, titleContentColor = c.onBoard,
            navigationIconContentColor = c.onBoard, actionIconContentColor = c.onBoard,
        ),
    )
}

/** 안내판 막대 안의 검색칸: 검은 판 위 한 단 밝은 칸. 라이브러리 탭의 맨 위. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSearchBar(query: String, onQueryChange: (String) -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val c = MaterialTheme.statusColors
    TopAppBar(
        title = {
            TextField(
                query, onQueryChange, Modifier.fillMaxWidth().padding(end = 4.dp), singleLine = true,
                placeholder = { Text("문장, 뜻, 친구 이름 검색") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "검색어 지우기") } }) else null,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.boardRaised, unfocusedContainerColor = c.boardRaised,
                    focusedTextColor = c.onBoard, unfocusedTextColor = c.onBoard, cursorColor = c.flap,
                    focusedPlaceholderColor = c.onBoardMuted, unfocusedPlaceholderColor = c.onBoardMuted,
                    focusedLeadingIconColor = c.onBoardMuted, unfocusedLeadingIconColor = c.onBoardMuted,
                    focusedTrailingIconColor = c.onBoard, unfocusedTrailingIconColor = c.onBoard,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.board, actionIconContentColor = c.onBoard),
    )
}

/** 출발 안내판의 플랩처럼 글자가 바뀔 때 위에서 넘어 들어온다(짧게, 상태가 바뀔 때만). */
@Composable
fun FlapText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = text, modifier = modifier.clipToBounds(), label = "flap",
        transitionSpec = {
            (slideInVertically(tween(180)) { -it } + fadeIn(tween(180))) togetherWith (slideOutVertically(tween(140)) { it } + fadeOut(tween(100)))
        },
    ) { t -> Text(t, style = style, color = color) }
}

/** 안내판 표기의 작은 글자(시간·편·상태). 라틴 글자는 대문자로. */
@Composable
fun BoardLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, style: TextStyle = MaterialTheme.typography.labelMedium) {
    Text(text.uppercase(), modifier, style = style.board(), color = color)
}

/** 편 표시: 본문과 반대 색 칸에 EN / ES(라이트는 검은 칸, 다크는 밝은 칸이라 줄 바탕에 묻히지 않는다). */
@Composable
fun FlightCode(lang: String, modifier: Modifier = Modifier) {
    Text(
        lang.uppercase(), modifier.background(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium.board(), color = MaterialTheme.colorScheme.inverseOnSurface,
    )
}

/**
 * 탑승권: 검은 머리띠에 경로(예: "ES → KO"), 본문, 절취선 아래 떼어 가는 칸(저장 등).
 * 절취선 양끝은 바탕색 반원으로 파여 실제 표처럼 보인다.
 */
@Composable
fun Ticket(route: String, modifier: Modifier = Modifier, stub: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.statusColors
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val ground = MaterialTheme.colorScheme.background
    val rule = MaterialTheme.colorScheme.outline
    Column(
        modifier.fillMaxWidth().background(paper, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.board, MaterialTheme.shapes.medium.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { BoardLabel(route, color = c.flap) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (stub != null) {
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val y = size.height / 2
                drawLine(rule, Offset(12.dp.toPx(), y), Offset(size.width - 12.dp.toPx(), y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawCircle(ground, 8.dp.toPx(), Offset(0f, y))
                drawCircle(ground, 8.dp.toPx(), Offset(size.width, y))
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = stub)
        }
    }
}

/** 0~[total] 중 [done]만큼 켜진 칸: 출발 안내판의 불빛 줄. 목표·익힌 단계처럼 셀 수 있는 양에만. 크기는 [modifier]로 정한다. */
@Composable
fun SegmentBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    val on = MaterialTheme.statusColors.flapGraphic
    val off = MaterialTheme.statusColors.segmentOff
    val n = total.coerceIn(1, 20)
    val lit = if (total <= 20) done.coerceIn(0, n) else (done * n / total).coerceIn(0, n)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(n) { i -> Box(Modifier.weight(1f).fillMaxHeight().background(if (i < lit) on else off, MaterialTheme.shapes.extraSmall)) }
    }
}
