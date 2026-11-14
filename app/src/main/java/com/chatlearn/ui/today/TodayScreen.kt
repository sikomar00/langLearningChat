package com.chatlearn.ui.today

import com.chatlearn.ui.components.weekdayLabel
import com.chatlearn.ui.components.FlightCode
import com.chatlearn.ui.components.SectionCard
import com.chatlearn.ui.components.SecondaryButton
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
    onOpenSentence: (Long) -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // 앱으로 돌아올 때마다 다시 읽는다(위젯에서 복습했거나 자정이 지났을 수 있다).
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose {} }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val s = ui ?: return@Column LoadingBox()
        Hero(s, onStartReview, onLearnMore, onGoTranslate)
        if (s.goal.enabled) GoalRow(s)
        // 성장 탭과 같은 구역 이름·같은 행동 이름. 빨강은 쓰지 않는다(첫 화면에서 실패 숫자를 먼저 보지 않게).
        s.weakGrammar?.let { w ->
            SectionCard("내 문법 약점") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlightCode(w.point.lang.code)
                    Text(w.point.nameKo, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${w.errors}번 틀림", style = MaterialTheme.typography.bodyMedium.board(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SecondaryButton(onClick = { onPractice(w.point) }, modifier = Modifier.fillMaxWidth()) { Text("이 문법으로 써 보기") }
            }
        }
        if (s.hasEntries) Forecast(s.forecast)
        if (s.recent.isNotEmpty()) Recent(s, onOpenLibrary, onOpenSentence)
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
                // 안내판 한 줄: 오늘 탑승 · 오늘 복습할 카드 수 · 그중 복습과 새 카드(카드 단위라 라이브러리 문장 수와 다를 수 있다).
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoardLabel("오늘 탑승", color = world.flap)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        FlapText("${s.due}", style = MaterialTheme.typography.headlineMedium.board(), color = world.onBoard)
                        Text("장", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                    }
                    Text(
                        listOfNotNull(s.reviews.takeIf { it > 0 }?.let { "복습 ${it}장" }, s.newCards.takeIf { it > 0 }?.let { "새 카드 ${it}장" }).joinToString(" · "),
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

/** 앞으로 7일 동안 날마다 복습할 카드 수. 성장의 "앞으로 30일"과 같은 이름 꼴. */
@Composable
private fun Forecast(days: List<DayCount>) {
    SectionCard("앞으로 7일") {
        DayBars(days, { weekdayLabel(it.date, days.first().date) }, days.first().date)
    }
}

/** 최근 저장한 문장: 라이브러리와 같은 출발 안내판 줄, 누르면 그 문장 상세. 머리는 라이브러리 묶음 머리 꼴. */
@Composable
private fun Recent(s: TodayUi, onOpenLibrary: () -> Unit, onOpenSentence: (Long) -> Unit) {
    Column {
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            Text("최근 저장한 문장", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenLibrary) { Text("모두 보기") }
        }
        val now = System.currentTimeMillis()
        s.recent.forEach { item ->
            com.chatlearn.ui.library.CompactRow(
                item, s.recentProgress[item.entry.id], now, null, selectionMode = false, checked = false,
                onClick = { onOpenSentence(item.entry.id) }, onLongClick = {},
            )
        }
    }
}
