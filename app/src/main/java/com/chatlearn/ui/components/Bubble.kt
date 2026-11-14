package com.chatlearn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chatlearn.ui.theme.BubbleIn
import com.chatlearn.ui.theme.BubbleOut

/**
 * 채팅 말풍선. [outgoing]이면 내가 보낸 말(오른쪽, 꼬리 오른쪽 아래), 아니면 받은 말(왼쪽).
 * 앱이 친구 대화에서 배우는 곳이라, 문제·내 답·채점을 대화처럼 보여 준다.
 */
@Composable
fun ChatBubble(
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = if (outgoing) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerLowest,
    fillWidth: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = color, contentColor = contentColorFor(color),
            shape = if (outgoing) BubbleOut else BubbleIn,
            // 받은 말은 탑승권 용지처럼 가는 테두리로 바탕과 나눈다(그림자 대신).
            border = if (outgoing) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.88f).wrapContentWidth(if (outgoing) Alignment.End else Alignment.Start),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}
