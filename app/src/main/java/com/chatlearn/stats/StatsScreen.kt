package com.chatlearn.stats

import androidx.compose.ui.graphics.luminance
import com.chatlearn.ui.components.ChoiceChip
import com.chatlearn.domain.langLabel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.semantics
import com.chatlearn.ui.theme.statusColors
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.components.BoardLabel
import com.chatlearn.ui.components.FlightCode
import com.chatlearn.ui.components.FlapText
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.ui.components.LoadingBox
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.components.StatRow
import java.util.Locale

fun percent(r: Retention): String = r.rate?.let { String.format(Locale.US, "%d%%", Math.round(it * 100)) } ?: "-"

@Composable
fun StatsScreen(vm: StatsViewModel, onPractice: (com.chatlearn.domain.GrammarPoint) -> Unit = {}, onOpenSentence: (Long) -> Unit = {}) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val grammar by vm.grammar.collectAsStateWithLifecycle()
    val outlook by vm.outlook.collectAsStateWithLifecycle()
    val s = stats
    if (s == null) {
        LoadingBox()
        return
    }
    val g = s.growth
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 맨 위는 출석이 아니라 지난주의 나와 견준 수. 줄어도 빨간 손실 표시는 쓰지 않는다.
        val diff = g.weekReviews - g.prevWeekReviews
        BoardStrip(listOf(
            BoardCell("익힌 문장", "${g.mastered}", "전체 ${g.stages.sumOf { it.second }}문장 중"),
            BoardCell("7일 복습", "${g.weekReviews}회", when {
                // "전주"는 그 앞 7일. 큰 글씨에서도 한 줄에 들어가게 짧게.
                diff > 0 -> "전주보다 +$diff"
                diff < 0 -> "전주보다 −${-diff}"
                else -> "전주와 같음"
            }, up = diff > 0),
            BoardCell("7일 정답률", percent(g.week), g.prevWeek.rate?.let { "전주 ${percent(g.prevWeek)}" }),
        ))
        Text(g.line, style = MaterialTheme.typography.bodyLarge)
        // 순서: 내 실력(익힌 정도·약점·틀린 문장) → 누구와(친구별) → 언제(지난·앞으로·12주) → 세부(정답률).
        SectionCard("익힌 정도") {
            MasteryBar(g.stages)
            Text("익힘은 21일 넘게 기억했거나 '이미 알아요'로 표시한 문장이에요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GrammarSection(grammar, onPractice)
        SectionCard("자주 틀린 문장") {
            if (s.weakest.isEmpty()) Text("아직 틀린 문장이 없어요.")
            s.weakest.forEachIndexed { i, w ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "문장 열기") { onOpenSentence(w.entryId) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlightCode(w.lang.code)
                    Text(w.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${w.lapses}번 틀림", style = MaterialTheme.typography.titleSmall.board())
                }
            }
        }
        if (g.friends.isNotEmpty()) SectionCard("친구별") { FriendBars(g.friends) }
        SectionCard("최근 7일") { com.chatlearn.ui.components.DayBars(s.last7, { com.chatlearn.ui.components.weekdayLabel(it.date, s.last7.last().date) }, s.last7.last().date) }
        if (outlook.isNotEmpty()) SectionCard("앞으로 30일") { OutlookBars(outlook) }
        if (s.heatmap.isNotEmpty()) SectionCard("${StatsCalculator.HEATMAP_WEEKS}주 학습 기록") { Heatmap(s.heatmap, g.monthDays, s.streak) }
        AccuracySection(s)
    }
}

private enum class AccuracyBy(val label: String) { LANG("언어"), EXERCISE("방식"), TAG("태그") }

/** 정답률 한 칸: 언어·방식·태그를 칩으로 바꿔 본다(표 네 개를 길게 늘어놓지 않게). */
@Composable
private fun AccuracySection(s: Stats) {
    var by by rememberSaveable { mutableStateOf(AccuracyBy.LANG) }
    SectionCard("정답률") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccuracyBy.entries.forEach { b -> ChoiceChip(selected = by == b, onClick = { by = b }, label = { Text(b.label) }) }
        }
        val rows: List<Pair<String, Retention>> = when (by) {
            AccuracyBy.LANG -> s.byLang.entries.map { (lang, r) -> langLabel(lang) to r }
            AccuracyBy.EXERCISE -> s.byExercise.entries.map { (kind, r) ->
                com.chatlearn.ui.library.exerciseName(kind) to r
            }
            AccuracyBy.TAG -> s.byTag.entries.sortedByDescending { it.value.reviews }.map { it.key to it.value }
        }
        if (rows.isEmpty()) Text(if (by == AccuracyBy.TAG) "복습한 태그가 없어요." else "아직 기록이 없어요.")
        rows.forEachIndexed { i, (name, r) -> StatRow(name, "${percent(r)}  ·  ${r.reviews}회", showDivider = i < rows.lastIndex) }
        if (by == AccuracyBy.EXERCISE) s.avgResponseMs?.let { Text("보통 응답 시간 ${String.format(Locale.US, "%.1f", it / 1000.0)}초") }
    }
}

/** 최근 12주 달력: 세로 7칸이 월~일, 가로 한 줄이 한 주. 진할수록 많이 복습한 날. */
@Composable
private fun Heatmap(days: List<DayCount>, monthDays: Int, streak: Int) {
    val active = days.count { it.reviews > 0 }
    val best = days.maxOf { it.reviews }
    val lamp = MaterialTheme.statusColors.flapGraphic
    // 빈 날은 옅은 선 색. 복습한 날은 빈 칸과 3:1 이상이 되게 불투명 단계로: 1단계는 호박 테두리(모양으로도 구분),
    // 2~4단계는 점점 짙은 호박(밤에는 점점 밝은 호박).
    val emptyCell = MaterialTheme.colorScheme.outlineVariant
    val night = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fills = if (night) listOf(lamp.copy(alpha = 0.6f), lamp.copy(alpha = 0.8f), lamp)
    else listOf(lamp, com.chatlearn.ui.theme.FlapInk, androidx.compose.ui.graphics.Color(0xFF4D3500))
    val cellShape = RoundedCornerShape(3.dp)
    fun Modifier.heat(level: Int) = when (level) {
        0 -> background(emptyCell, cellShape)
        1 -> background(emptyCell, cellShape).border(1.5.dp, lamp, cellShape)
        else -> background(fills[level - 2], cellShape)
    }
    val weeks = days.chunked(7)
    Text(
        // 연속일은 압박이 되지 않게 아래쪽, 이 줄 끝에 덧붙이기만 한다.
        if (active == 0) "아직 복습한 날이 없어요"
        else (if (monthDays > 0) "이번 달 ${monthDays}일 복습했어요" else "최근 ${StatsCalculator.HEATMAP_WEEKS}주 중 ${active}일 복습했어요") +
            (if (streak > 1) " · ${streak}일째 이어서" else ""),
        style = MaterialTheme.typography.bodyMedium,
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "최근 ${StatsCalculator.HEATMAP_WEEKS}주 중 ${active}일 복습, 가장 많이 한 날 ${best}번"
        },
    ) {
        val gap = 3.dp
        val labelWidth = 18.dp
        val cell = (maxWidth - labelWidth - gap * weeks.size) / weeks.size
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            // 달이 바뀌는 주 위에 달 이름
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.width(labelWidth))
                val starts = weeks.mapIndexed { i, w -> w.firstOrNull { it.date.dayOfMonth == 1 }?.date ?: w.first().date.takeIf { i == 0 } }
                // 첫 주 이름 바로 다음 주에 새 달이 시작하면 첫 주 이름은 뺀다(이름끼리 붙지 않게).
                val shown = starts.mapIndexed { i, d -> d.takeIf { i > 0 || starts.getOrNull(1) == null } }
                weeks.forEachIndexed { i, w ->
                    val first = shown[i]
                    Box(Modifier.width(cell)) {
                        if (first != null) Text(
                            "${first.monthValue}월", style = MaterialTheme.typography.labelSmall, softWrap = false,
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                    listOf("월", "", "수", "", "금", "", "").forEach { d ->
                        Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) { Text(d, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                weeks.forEach { w ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        w.forEach { d ->
                            Box(Modifier.size(cell).heat(heatLevel(d.reviews)))
                        }
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("적음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..4).forEach { Box(Modifier.size(10.dp).heat(it)) }
        Text("많음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 내 문법 약점: 써 보기·복습에서 틀린 문법과 보낼 말에서 자주 필요했던 문법. 누르면 설명과 연습. */
@Composable
private fun GrammarSection(items: List<GrammarWeakness>, onPractice: (com.chatlearn.domain.GrammarPoint) -> Unit) {
    var open by remember { mutableStateOf<GrammarWeakness?>(null) }
    SectionCard("내 문법 약점") {
        if (items.isEmpty()) {
            Text("써 보기와 복습을 하면 내가 자주 틀리는 문법이 여기에 모여요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.take(5).forEach { w ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "설명 보기") { open = w },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlightCode(w.point.lang.code, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.point.nameKo, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            w.errors.takeIf { it > 0 }?.let { "${it}번 틀림" },
                            w.needed.takeIf { it > 0 }?.let { "${it}번 필요했음" },
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (w.errors > 0) Text("${w.errors}", style = MaterialTheme.typography.headlineSmall.board())
            }
        }
    }
    open?.let { w ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(w.point.nameKo) },
            text = { Text(w.point.explainKo) },
            confirmButton = { TextButton(onClick = { open = null; onPractice(w.point) }) { Text("이 문법으로 써 보기") } },
            dismissButton = { TextButton(onClick = { open = null }) { Text("닫기") } },
        )
    }
}

private class BoardCell(val label: String, val value: String, val note: String?, val up: Boolean = false)

/** 안내판 띠: 검은 판에 칸마다 작은 이름표와 안내판 숫자, 아래에 작은 비교. 칸 사이는 가는 세로선. */
@Composable
private fun BoardStrip(cells: List<BoardCell>) {
    val c = MaterialTheme.statusColors
    Surface(color = c.board, contentColor = c.onBoard, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            cells.forEachIndexed { i, cell ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.boardRaised))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) {}) {
                    BoardLabel(cell.label, color = c.onBoardMuted)
                    FlapText(cell.value, MaterialTheme.typography.headlineSmall.board(), color = c.onBoard)
                    // 늘면 밝게, 줄거나 같으면 흐리게. 호박은 "지금·선택"에만 쓰고, 검은 판 위 초록은 작은 글씨 대비가 모자라다.
                    cell.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (cell.up) c.onBoard else c.onBoardMuted) }
                }
            }
        }
    }
}

/** 익힌 정도: 익힘·거의·익히는 중·새 문장의 비율 막대와 수. 익힘이 늘어나는 게 실력이 쌓이는 모습. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MasteryBar(stages: List<Pair<String, Int>>) {
    val lamp = MaterialTheme.statusColors.flapGraphic
    val colors = listOf(lamp, MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outline)
    val parts = stages.zip(colors) { (label, n), color -> Triple(label, n, color) }
    // 거의 = 흰 속 + 진한 호박 테두리, 새 문장 = 흰 속 + 회색 테두리(익히는 중과 농도로만 갈리지 않게).
    val shape = MaterialTheme.shapes.extraSmall
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val outline = MaterialTheme.colorScheme.outline
    fun Modifier.part(i: Int, color: androidx.compose.ui.graphics.Color) = when (i) {
        1 -> background(paper, shape).border(1.5.dp, lamp, shape)
        3 -> background(paper, shape).border(1.dp, outline, shape)
        else -> background(color, shape)
    }
    val total = parts.sumOf { it.second }
    Row(
        Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics { contentDescription = parts.joinToString(", ") { "${it.first} ${it.second}개" } },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        parts.forEachIndexed { i, (_, n, color) ->
            if (n > 0) Box(Modifier.weight(n.toFloat() / total).fillMaxHeight().part(i, color))
        }
        if (total == 0) Box(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall))
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { i, (label, n, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).part(i, color))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$n", style = MaterialTheme.typography.titleMedium.board())
            }
        }
    }
}
