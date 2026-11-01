package com.chatlearn.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.chatlearn.ui.components.langLabel
import java.util.Locale

fun percent(r: Retention): String = r.rate?.let { String.format(Locale.US, "%d%%", Math.round(it * 100)) } ?: "-"

@Composable
fun StatsScreen(vm: StatsViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val s = stats
    if (s == null) {
        LoadingBox()
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("오늘") {
            Text("복습 ${s.today.reviews}회 · 정답률 ${percent(s.today)}", style = MaterialTheme.typography.titleMedium)
            Text("연속 학습 ${s.streak}일")
        }
        SectionCard("최근 7일") { WeekBars(s.last7) }
        if (s.heatmap.isNotEmpty()) SectionCard("${StatsCalculator.HEATMAP_WEEKS}주 학습 기록") { Heatmap(s.heatmap) }
        SectionCard("카드 상태") {
            Text("새 카드 ${s.cards.new}개 · 학습 중 ${s.cards.learning}개 · 익숙함 ${s.cards.mature}개")
            Text("익숙함은 복습 간격이 21일 이상인 카드예요.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("자주 틀리는 문장") {
            if (s.weakest.isEmpty()) Text("아직 틀린 문장이 없어요.")
            s.weakest.forEachIndexed { i, w -> StatRow(w.text, "${w.lapses}회", showDivider = i < s.weakest.lastIndex) }
        }
        SectionCard("언어별 정답률") {
            if (s.byLang.isEmpty()) Text("아직 기록이 없어요.")
            s.byLang.entries.forEachIndexed { i, (lang, r) -> StatRow(langLabel(lang), "${percent(r)}  ·  ${r.reviews}회", showDivider = i < s.byLang.size - 1) }
        }
        SectionCard("방식별 정답률") {
            if (s.byExercise.isEmpty()) Text("아직 기록이 없어요.")
            s.byExercise.entries.forEachIndexed { i, (kind, r) ->
                val name = when (kind) { ExerciseKind.TRANSLATE -> "쓰기 번역"; ExerciseKind.CLOZE -> "빈칸 채우기"; ExerciseKind.DICTATION -> "듣고 받아쓰기" }
                StatRow(name, "${percent(r)}  ·  ${r.reviews}회", showDivider = i < s.byExercise.size - 1)
            }
            s.avgResponseMs?.let { Text("보통 응답 시간 ${String.format(Locale.US, "%.1f", it / 1000.0)}초") }
        }
        SectionCard("태그별 정답률") {
            if (s.byTag.isEmpty()) Text("복습한 태그가 없어요.")
            val tags = s.byTag.entries.sortedByDescending { it.value.reviews }
            tags.forEachIndexed { i, (tag, r) -> StatRow(tag, "${percent(r)}  ·  ${r.reviews}회", showDivider = i < tags.lastIndex) }
        }
    }
}

/** 최근 12주 달력: 세로 7칸이 월~일, 가로 한 줄이 한 주. 진할수록 많이 복습한 날. */
@Composable
private fun Heatmap(days: List<DayCount>) {
    val active = days.count { it.reviews > 0 }
    val best = days.maxOf { it.reviews }
    val primary = MaterialTheme.colorScheme.primary
    val emptyCell = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val alphas = listOf(0f, 0.3f, 0.55f, 0.8f, 1f)
    fun cellColor(level: Int) = if (level == 0) emptyCell else primary.copy(alpha = alphas[level])
    val weeks = days.chunked(7)
    Text(
        if (active == 0) "아직 복습한 날이 없어요" else "최근 ${StatsCalculator.HEATMAP_WEEKS}주 중 ${active}일 복습했어요",
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
                weeks.forEachIndexed { i, w ->
                    val first = w.firstOrNull { it.date.dayOfMonth == 1 }?.date ?: w.first().date.takeIf { i == 0 }
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
                            Box(Modifier.size(cell).clip(RoundedCornerShape(3.dp)).background(cellColor(heatLevel(d.reviews))))
                        }
                    }
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("적음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..4).forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(cellColor(it))) }
        Text("많음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekBars(days: List<DayCount>) {
    val max = (days.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(d.reviews.toString(), style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.fillMaxWidth().height((56.dp * d.reviews / max).coerceAtLeast(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text("${d.date.monthValue}/${d.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
