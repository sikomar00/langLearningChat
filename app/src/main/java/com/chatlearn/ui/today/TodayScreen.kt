package com.chatlearn.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatlearn.domain.Lang
import com.chatlearn.stats.DayCount
import com.chatlearn.ui.components.ChatBubble
import com.chatlearn.ui.components.DayBars
import com.chatlearn.ui.components.StreakBadge
import com.chatlearn.ui.components.LoadingBox
import com.chatlearn.ui.theme.Rounded
import com.chatlearn.ui.theme.statusColors
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
    onOpenSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ChatLearn", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Rounded), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            ui?.streak?.takeIf { it > 0 }?.let { StreakBadge(it, Modifier.padding(end = 4.dp)) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "설정") }
        }
        val s = ui ?: return@Column LoadingBox()
        Hero(s, onStartReview, onLearnMore, onGoTranslate)
        if (s.goal.enabled) GoalRow(s)
        if (s.hasEntries) Forecast(s.forecast)
        if (s.recent.isNotEmpty()) Recent(s, onOpenLibrary)
    }
}

@Composable
private fun Hero(s: TodayUi, onStart: () -> Unit, onLearnMore: () -> Unit, onGoTranslate: () -> Unit) {
    val sunButton = ButtonDefaults.buttonColors(containerColor = MaterialTheme.statusColors.sun, contentColor = MaterialTheme.statusColors.onSun)
    ChatBubble(outgoing = false, color = MaterialTheme.colorScheme.primary, fillWidth = true) {
        when {
            !s.hasEntries -> {
                Text("반가워요!", style = MaterialTheme.typography.headlineSmall)
                Text("친구에게 받은 메시지를 붙여 넣고 저장하면, 여기서 매일 조금씩 복습해요.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Button(onClick = onGoTranslate, colors = sunButton, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("첫 문장 저장하러 가기") }
            }
            s.due > 0 -> {
                Text("오늘 복습할 카드", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${s.due}", style = MaterialTheme.typography.displayLarge)
                    Text("장", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 14.dp))
                }
                Text(
                    listOfNotNull(s.reviews.takeIf { it > 0 }?.let { "복습 ${it}장" }, s.newCards.takeIf { it > 0 }?.let { "새 카드 ${it}장" }).joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onStart, colors = sunButton, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("복습 시작", style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> {
                Text("오늘 복습 끝!", style = MaterialTheme.typography.displaySmall)
                val tomorrow = s.forecast.getOrNull(1)?.reviews ?: 0
                Text(if (tomorrow > 0) "내일은 ${tomorrow}장이 기다려요." else "새 문장을 저장하면 내일 복습에 나와요.", style = MaterialTheme.typography.bodyLarge)
                if (s.moreNew > 0) {
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onLearnMore, colors = sunButton, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("새 카드 ${minOf(5, s.moreNew)}장 더 배우기")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalRow(s: TodayUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { s.goal.fraction }, modifier = Modifier.size(64.dp),
                color = if (s.goal.reached) MaterialTheme.statusColors.success else MaterialTheme.colorScheme.primary,
            )
            Text("${s.goal.done}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Rounded))
        }
        Column {
            Text("오늘 목표 ${s.goal.goal}장", style = MaterialTheme.typography.titleMedium)
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
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = "라이브러리에서 보기", onClick = onOpenLibrary).padding(vertical = 8.dp)) {
                Text(foreign, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.texts[Lang.KO].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
