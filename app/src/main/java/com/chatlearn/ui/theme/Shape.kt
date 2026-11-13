package com.chatlearn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 칩·버튼은 둥글게, 카드는 크게 둥글게.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// 앱의 표지 모양: 말풍선. 한 모서리만 뾰족해 꼬리처럼 보인다.
/** 친구(앱)가 보낸 말: 왼쪽 아래가 꼬리. 오늘 할 일, 복습 문제. */
val BubbleIn = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 6.dp)
/** 내가 보낸 말: 오른쪽 아래가 꼬리. 내 답. */
val BubbleOut = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp, bottomStart = 28.dp)
