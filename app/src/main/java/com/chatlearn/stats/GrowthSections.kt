package com.chatlearn.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chatlearn.ui.theme.board
import com.chatlearn.ui.theme.statusColors

/** 친구별 성장: 그 친구와의 대화에서 저장한 문장 수와, 그중 익힌 비율 막대. */
@Composable
internal fun FriendBars(friends: List<FriendGrowth>) {
    friends.forEach { f ->
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("익힘 ${f.mastered} / ${f.sentences}", style = MaterialTheme.typography.titleSmall.board())
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)) {
                if (f.mastered > 0) Box(
                    Modifier.fillMaxWidth(f.mastered.toFloat() / f.sentences).fillMaxHeight()
                        .background(MaterialTheme.statusColors.flapGraphic, MaterialTheme.shapes.extraSmall),
                )
            }
        }
    }
}

private const val BAR_MAX_DP = 56f

/** 앞으로 30일: 복습은 꽉 찬 막대, 새 카드는 테두리 막대를 위에 쌓는다. 오늘만 호박. 날짜는 한 주에 한 번. */
@Composable
internal fun OutlookBars(days: List<OutlookDay>) {
    if (days.isEmpty()) return
    val reviews = days.sumOf { it.reviews }
    val new = days.sumOf { it.new }
    Text("앞으로 ${days.size}일 복습할 카드 ${reviews}장 · 새 카드 ${new}장", style = MaterialTheme.typography.bodyMedium)
    val max = days.maxOf { it.reviews + it.new }.coerceAtLeast(1)
    fun h(n: Int) = (BAR_MAX_DP * n / max).coerceAtLeast(2f).dp
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().height(BAR_MAX_DP.dp).clearAndSetSemantics {
            contentDescription = "앞으로 ${days.size}일 복습 ${reviews}장, 새 카드 ${new}장, 가장 많은 날 ${max}장"
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        days.forEachIndexed { i, d ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                if (d.new > 0) Box(Modifier.fillMaxWidth().height(h(d.new)).border(1.dp, outline, MaterialTheme.shapes.extraSmall))
                if (d.reviews > 0) Box(
                    Modifier.fillMaxWidth().height(h(d.reviews))
                        .background(if (i == 0) MaterialTheme.statusColors.flapGraphic else outline, MaterialTheme.shapes.extraSmall),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        days.forEachIndexed { i, d ->
            Box(Modifier.weight(1f)) {
                // 한 주에 한 번, 마지막 날은 오른쪽 끝에 맞춰 화면 밖으로 넘치지 않게.
                val last = i == days.lastIndex
                if (last || (i % 7 == 0 && i < days.lastIndex - 3)) Text(
                    "${d.date.monthValue}/${d.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall.board(), softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(if (last) Alignment.CenterEnd else Alignment.CenterStart)
                        .wrapContentWidth(if (last) Alignment.End else Alignment.Start, unbounded = true),
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(outline, MaterialTheme.shapes.extraSmall))
        Text("복습", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(10.dp).border(1.dp, outline, MaterialTheme.shapes.extraSmall))
        Text("새 카드", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("한 문장에 영어·스페인어 카드가 하나씩 있을 수 있어 문장 수와 다를 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
