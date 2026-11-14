package com.chatlearn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 모서리 단계 4 / 8 / 12dp. 공책처럼 반듯하게, 알약 모양은 칩·버튼에만.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// 대화에서 온 문장이라는 표시: 말풍선. 한 모서리만 뾰족해 꼬리처럼 보인다.
/** 친구(앱)가 보낸 말: 왼쪽 아래가 꼬리. 복습 문제. */
val BubbleIn = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
/** 내가 보낸 말: 오른쪽 아래가 꼬리. 내 답. */
val BubbleOut = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
