package com.chatlearn.ui.today

import com.chatlearn.domain.MAX_STAGE
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.components.BoardLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.Lang
import com.chatlearn.stats.DayCount
import com.chatlearn.ui.components.DayBars
import com.chatlearn.ui.components.StreakBadge
import com.chatlearn.ui.components.LoadingBox
import com.chatlearn.ui.theme.statusColors
import com.chatlearn.ui.components.SegmentBar
import com.chatlearn.ui.components.FlapText
import java.time.format.TextStyle
import java.util.Locale

/** 앱을 열면 처음 보는 화면: 오늘 할 일을 친구가 보낸 말풍선처럼 보여 주고, 한 번에 복습을 시작한다. */
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onStartReview: () -> Unit,
    onLearnMore: () -> Unit,
    onGoTranslate: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPractice: (com.chatlearn.domain.GrammarPoint) -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // 앱으로 돌아올 때마다 다시 읽는다(위젯에서 복습했거나 자정이 지났을 수 있다).
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose {} }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val s = ui ?: return@Column LoadingBox()
        Hero(s, onStartReview, onLearnMore, onGoTranslate)
        if (s.goal.enabled) GoalRow(s)
        s.weakGrammar?.let { w ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${w.point.nameKo} 다시 보기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("${w.errors}번 틀렸어요", style = MaterialTheme.typography.bodySmall.board(), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = { onPractice(w.point) }, shape = MaterialTheme.shapes.medium) { Text("써 보기") }
                }
            }
        }
        if (s.hasEntries) Forecast(s.forecast)
        if (s.recent.isNotEmpty()) Recent(s, onOpenLibrary)
    }
}

@Composable
private fun Hero(s: TodayUi, onStart: () -> Unit, onLearnMore: () -> Unit, onGoTranslate: () -> Unit) {
    // 출발 안내판 한 칸: 검은 판에 플랩 숫자, 지금 할 일 하나만 호박 버튼.
    val world = MaterialTheme.statusColors
    val go = ButtonDefaults.buttonColors(containerColor = world.flap, contentColor = world.onFlap)
    Surface(color = world.board, contentColor = world.onBoard, shape = MaterialTheme.shapes.medium) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            !s.hasEntries -> {
                Text("반가워요!", style = MaterialTheme.typography.headlineSmall)
                Text("친구에게 받은 메시지를 붙여 넣고 저장하면, 여기서 매일 조금씩 복습해요.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Button(shape = MaterialTheme.shapes.medium, onClick = onGoTranslate, colors = go, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("첫 문장 저장하러 가기") }
            }
            s.due > 0 -> {
                // 안내판 한 줄: 지금 탑승 · 오늘 복습할 카드 수 · 그중 복습과 새 카드.
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoardLabel("지금 탑승", color = world.flap)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        FlapText("${s.due}", style = MaterialTheme.typography.headlineMedium.board(), color = world.onBoard)
                        Text("장", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                    }
                    Text(
                        listOfNotNull(s.reviews.takeIf { it > 0 }?.let { "복습 ${it}" }, s.newCards.takeIf { it > 0 }?.let { "새 카드 ${it}" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge.board(), color = world.onBoardMuted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(shape = MaterialTheme.shapes.medium, onClick = onStart, colors = go, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("복습 시작", style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> {
                Text("오늘 복습 끝!", style = MaterialTheme.typography.displaySmall)
                val tomorrow = s.forecast.getOrNull(1)?.reviews ?: 0
                Text(if (tomorrow > 0) "내일은 ${tomorrow}장이 기다려요." else "새 문장을 저장하면 내일 복습에 나와요.", style = MaterialTheme.typography.bodyLarge, color = world.onBoardMuted)
                if (s.moreNew > 0) {
                    Spacer(Modifier.height(4.dp))
                    Button(shape = MaterialTheme.shapes.medium, onClick = onLearnMore, colors = go, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("새 카드 ${minOf(5, s.moreNew)}장 더 배우기")
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun GoalRow(s: TodayUi) {
    // 목표는 셀 수 있는 양이라 안내판 불빛 칸으로(한 장 = 한 칸).
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentBar(s.goal.done, s.goal.goal, Modifier.fillMaxWidth().height(10.dp))
        Column {
            Text("오늘 목표 ${s.goal.goal}장 중 ${minOf(s.goal.done, s.goal.goal)}장", style = MaterialTheme.typography.titleMedium.board())
            Text(
                if (s.goal.reached) "달성했어요. 더 해도 좋아요." else "${s.goal.goal - s.goal.done}장 남았어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (s.goal.reached) MaterialTheme.statusColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 앞으로 7일 동안 날마다 복습할 카드 수. */
@Composable
private fun Forecast(days: List<DayCount>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("다음 7일", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        DayBars(days, { dayLabel(it, days.first()) }, days.first().date)
    }
}

private fun dayLabel(d: DayCount, today: DayCount): String =
    if (d.date == today.date) "오늘" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

@Composable
private fun Recent(s: TodayUi, onOpenLibrary: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("최근 저장한 문장", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onOpenLibrary) { Text("모두 보기") }
        }
        s.recent.forEach { item ->
            val foreign = item.texts[item.entry.chatLang].orEmpty()
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "라이브러리에서 보기", onClick = onOpenLibrary).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(foreign, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val stage = s.recentStages[item.entry.id] ?: 0
                SegmentBar(stage, MAX_STAGE, Modifier.width(40.dp).height(4.dp).semantics { contentDescription = "익힌 단계 $stage/$MAX_STAGE" })
            }
        }
    }
}
