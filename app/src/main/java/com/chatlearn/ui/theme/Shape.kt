package com.chatlearn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 탑승권·안내판처럼 반듯하게: 모서리 4 / 8dp(꽉 찬 너비 버튼도 8dp). 알약 모양은 상태 알약과 내비게이션 표시에만.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

// 대화에서 온 문장이라는 표시: 말풍선. 한 모서리만 뾰족해 꼬리처럼 보인다.
/** 친구(앱)가 보낸 말: 왼쪽 아래가 꼬리. 복습 문제. */
val BubbleIn = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 2.dp)
/** 내가 보낸 말: 오른쪽 아래가 꼬리. 내 답. */
val BubbleOut = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 2.dp, bottomStart = 12.dp)
