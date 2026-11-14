package com.chatlearn.ui.theme

import androidx.compose.ui.graphics.Color

// 세계: 출국장의 출발 안내판과 탑승권. 문장마다 친구에게 가는 탑승권, 라이브러리는 출발 안내판.
// 안내판 검정이 위·아래 막대와 열 머리를 칠하고, 호박색은 지금 할 일과 선택에만 쓴다.
// 글자에 쓰는 색은 배경 대비 4.5:1 이상(호박색 바탕 위 글자는 늘 먹색).

// 라이트: 대합실 바탕과 탑승권 용지
internal val Board = Color(0xFF15181D)
internal val BoardRaised = Color(0xFF2A2F37)
internal val OnBoardMuted = Color(0xFFAEB4BD)
internal val Flap = Color(0xFFF6B400)
internal val Concourse = Color(0xFFF2F3F5)
internal val Stock = Color(0xFFFFFFFF)
internal val Ink = Color(0xFF15181D)
internal val InkSoft = Color(0xFF5B616B)
internal val Rule = Color(0xFFE1E3E7)
internal val FlapInk = Color(0xFF7A5400)
// 밝은 바탕 위 호박 그림(오늘 막대·켜진 칸·지금 복습 선): 바탕 대비 3.5:1, 꺼진 칸 대비 3:1.
internal val FlapGraphic = Color(0xFFA87800)
internal val Delayed = Color(0xFFC0322A)
internal val OnTime = Color(0xFF2E7D4F)

// 다크: 밤의 출국장
internal val NightGround = Color(0xFF15181C)
internal val NightBoard = Color(0xFF050607)
internal val NightInk = Color(0xFFE8EAED)
internal val NightInkSoft = Color(0xFFA9AFB8)
internal val NightRule = Color(0xFF2C3037)
// 다크의 꺼진 칸: 줄 바탕에서 보이고 켜진 칸(플랩)과 4.5:1.
internal val NightSegmentOff = Color(0xFF4A5059)
internal val DelayedLight = Color(0xFFFF8A7A)
internal val OnTimeLight = Color(0xFF7FCB98)
