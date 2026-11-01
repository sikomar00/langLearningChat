package com.chatlearn.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.ui.components.LoadingBox
import com.chatlearn.ui.components.SectionCard
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
        SectionCard("카드 상태") {
            Text("새 카드 ${s.cards.new}개 · 학습 중 ${s.cards.learning}개 · 익숙함 ${s.cards.mature}개")
            Text("익숙함은 복습 간격이 21일 이상인 카드예요.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("자주 틀리는 문장") {
            if (s.weakest.isEmpty()) Text("아직 틀린 문장이 없어요.")
            s.weakest.forEach { Text("${it.text}  (${it.lapses}회)") }
        }
        SectionCard("언어별 정답률") {
            if (s.byLang.isEmpty()) Text("아직 기록이 없어요.")
            s.byLang.forEach { (lang, r) -> Text("${langLabel(lang)}  ${percent(r)}  (${r.reviews}회)") }
        }
        SectionCard("방식별 정답률") {
            if (s.byExercise.isEmpty()) Text("아직 기록이 없어요.")
            s.byExercise.forEach { (kind, r) ->
                val name = when (kind) { ExerciseKind.TRANSLATE -> "쓰기 번역"; ExerciseKind.CLOZE -> "빈칸 채우기"; ExerciseKind.DICTATION -> "듣고 받아쓰기" }
                Text("$name  ${percent(r)}  (${r.reviews}회)")
            }
            s.avgResponseMs?.let { Text("평균 응답 시간 ${String.format(Locale.US, "%.1f", it / 1000.0)}초") }
        }
        SectionCard("태그별 정답률") {
            if (s.byTag.isEmpty()) Text("복습한 태그가 없어요.")
            s.byTag.entries.sortedByDescending { it.value.reviews }.forEach { (tag, r) -> Text("$tag  ${percent(r)}  (${r.reviews}회)") }
        }
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
