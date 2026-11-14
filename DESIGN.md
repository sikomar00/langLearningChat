---
name: ChatLearn
description: 친구와 나눈 문장이 탑승권이 되고, 라이브러리는 출국장의 출발 안내판이 되는 개인 언어 공부 앱
colors:
  board: "#15181D"
  board-raised: "#2A2F37"
  on-board-muted: "#AEB4BD"
  flap: "#F6B400"
  flap-ink: "#7A5400"
  flap-graphic: "#A87800"
  flap-container: "#FFE7A3"
  concourse: "#F2F3F5"
  stock: "#FFFFFF"
  ink: "#15181D"
  ink-soft: "#5B616B"
  rule: "#E1E3E7"
  outline: "#7D838C"
  surface-high: "#E4E7EB"
  delayed: "#C0322A"
  delayed-container: "#FCE1DE"
  on-time: "#2E7D4F"
  night-ground: "#15181C"
  night-board: "#050607"
  night-stock: "#1F2329"
  night-ink: "#E8EAED"
  night-ink-soft: "#A9AFB8"
  night-rule: "#2C3037"
  night-segment-off: "#4A5059"
  night-flap-container: "#4A3500"
  delayed-light: "#FF8A7A"
  on-time-light: "#7FCB98"
typography:
  board-number:
    fontFamily: "Roboto Condensed, sans-serif-condensed, sans-serif"
    fontSize: "44sp"
    fontWeight: 700
    lineHeight: "50sp"
    fontFeature: "tnum"
  headline:
    fontFamily: "sans-serif"
    fontSize: "26sp"
    fontWeight: 700
    lineHeight: "34sp"
  headline-small:
    fontFamily: "sans-serif"
    fontSize: "22sp"
    fontWeight: 700
    lineHeight: "30sp"
  title:
    fontFamily: "sans-serif"
    fontSize: "20sp"
    fontWeight: 500
    lineHeight: "28sp"
    fontFeature: "tnum"
  title-medium:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
    letterSpacing: "0.1sp"
  body:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-medium:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "21sp"
  label:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
  board-label:
    fontFamily: "Roboto Condensed, sans-serif-condensed, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.04em"
    fontFeature: "tnum"
rounded:
  tail: "2px"
  xs: "4px"
  md: "8px"
  lg: "12px"
  full: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
components:
  board-top-bar:
    backgroundColor: "{colors.board}"
    textColor: "{colors.stock}"
    typography: "{typography.title}"
  board-search-field:
    backgroundColor: "{colors.board-raised}"
    textColor: "{colors.stock}"
    rounded: "{rounded.lg}"
  board-columns:
    backgroundColor: "{colors.board}"
    textColor: "{colors.flap}"
    typography: "{typography.board-label}"
    rounded: "{rounded.xs}"
    padding: "6px 12px"
  departure-row:
    backgroundColor: "{colors.stock}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    padding: "12px"
  status-due:
    backgroundColor: "{colors.flap}"
    textColor: "{colors.ink}"
    typography: "{typography.board-label}"
    rounded: "{rounded.full}"
    padding: "3px 8px"
  status-new:
    textColor: "{colors.ink-soft}"
    typography: "{typography.board-label}"
    rounded: "{rounded.full}"
    padding: "2px 8px"
  flight-code:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.stock}"
    typography: "{typography.board-label}"
    rounded: "{rounded.xs}"
    padding: "1px 6px"
  ticket:
    backgroundColor: "{colors.stock}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "16px"
  ticket-header:
    backgroundColor: "{colors.board}"
    textColor: "{colors.flap}"
    typography: "{typography.board-label}"
    padding: "8px 16px"
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.stock}"
    rounded: "{rounded.md}"
    height: "52px"
  button-now:
    backgroundColor: "{colors.flap}"
    textColor: "{colors.ink}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.md}"
    height: "56px"
  chip:
    textColor: "{colors.ink}"
    rounded: "{rounded.xs}"
  chip-selected:
    backgroundColor: "{colors.flap}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xs}"
  nav-bar:
    backgroundColor: "{colors.board}"
    textColor: "{colors.on-board-muted}"
  nav-item-active:
    backgroundColor: "{colors.flap}"
    textColor: "{colors.flap}"
    rounded: "{rounded.full}"
  bubble-out:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.stock}"
    rounded: "{rounded.lg}"
    padding: "14px 18px"
  bubble-in:
    backgroundColor: "{colors.stock}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "14px 18px"
---

# Design System: ChatLearn

## Overview

**Creative North Star: "출국장의 출발 안내판"**

문장 하나하나는 친구에게 가는 탑승권이고, 라이브러리는 출국장의 출발 안내판이다. 화면의 위 막대·아래 탭·열 머리는 안내판 검정으로 칠하고, 그 사이 대합실 회색 바탕에 흰 용지 줄이 가는 구분선으로 놓인다. 둥근 카드 목록과 크림·보라 계열 공부 앱의 기본 배치는 쓰지 않는다. 교과서·시험지 느낌이 아니라, 지금 떠나야 할 편이 한눈에 보이는 운영 화면(Operate)이다.

밀도는 안내판처럼 촘촘하다. 줄마다 시간·외국어 문장·뜻·친구와 나라·편 코드(EN/ES)·상태가 한 줄에 들어가고, 상태는 색과 함께 선 모양(실선·점선·빈 칸)으로도 구분된다. 숫자·시간·편 코드·상태는 응축체 대문자와 폭이 같은 숫자로, 한국어 문장은 시스템 산세리프로 쓴다. 호박색은 드물게 나온다: 지금 할 일 하나와 선택 표시에만 켜진다.

움직임은 하나의 문법만 쓴다. 상태 글자가 바뀔 때 안내판 플랩처럼 위에서 넘어 들어오고(약 180ms), 오늘 목표를 채우면 안내판 한 칸이 위에서 펼쳐진다. 그 밖의 버튼·칩·내비게이션 전환은 Material 3 Expressive 스프링에 맡긴다.

**Key Characteristics:**
- 검은 안내판 막대(위·아래·열 머리)와 대합실 회색 바탕, 흰 용지 줄의 3층 구조.
- 호박색 = "지금"과 "선택"만. 초록 = 정시·통과, 빨강 = 틀림·삭제만.
- 응축체(Roboto Condensed, 앱에 내장) 대문자·고정폭 숫자로 시간·편·상태를 적는다.
- 그림자 없이 색 단계와 1dp 선으로 층을 나눈다.
- 모서리는 반듯하게 4/8dp, 알약 모양은 상태 알약과 내비게이션 표시에만.
- 탑승권: 검은 머리띠의 경로, 절취선과 반원 홈, 떼어 가는 칸(저장).

## Colors

검정 안내판과 회색 대합실 위에 호박·초록·빨강 세 가지 신호등만 켜는, 공항 운영 화면의 팔레트다.

### Primary
- **안내판 검정** (board / ink): 위 막대, 아래 내비게이션, 열 머리, 탑승권 머리띠, 목표 완료 안내판. 라이트에서는 본문 글자색(ink)과 주 버튼(primary) 바탕도 같은 검정이다. 다크에서 안내판은 더 깊은 검정(night-board)으로 내려가 바탕(night-ground)과 구분된다.
- **한 단 밝은 판** (board-raised): 검은 판 안의 칸 구분선, 꺼진 버튼 바탕.
- **판 위 흐린 글자** (on-board-muted): 검은 판 위의 보조 글자, 선택되지 않은 탭 아이콘·이름, 검색칸 안내문.

### Secondary
- **플랩 호박** (flap): "지금" 하나와 선택에만 켜진다. 지금 복습할 차례인 상태 알약, 오늘 화면의 "복습 시작" 버튼, 선택된 칩, 내비게이션 표시, 탭 배지, 검은 판 위의 열 머리·경로 글자. M3에서는 secondaryContainer이고, 그 위 글자는 늘 먹색(ink)이다. 플랩 위 글자·아이콘 색은 배경화면 색(동적 색)을 켜도 바뀌지 않게 세계 색 onFlap(먹)으로 고정한다.
- **진한 호박** (flap-graphic, 라이트 전용): 밝은 바탕 위 호박 그림(불빛 칸의 켜진 칸, 날짜 막대의 오늘 칸, 지금 복습할 차례의 거리 실선). 바탕 대비 3.5:1, 꺼진 칸 대비 3:1을 지키려고 플랩보다 진하다. 다크에서는 플랩을 그대로 쓴다.
- **꺼진 칸** (segment-off #7D838C / night-segment-off #7A808A): 불빛 칸의 꺼진 칸 **테두리**(칠하지 않음). 흰 용지·대합실 바탕과 3:1 이상, 켜진 칸과는 "채움 대 테두리"로 갈린다. 검은 판 위에서는 on-board-muted 테두리.

### Tertiary
- **읽을 수 있는 짙은 호박** (flap-ink): 밝은 바탕 위에서 호박을 글자로 써야 할 때(핵심 표현, 거의 맞음 배지, 폴더 표시). 라이트 tertiary다. 다크 tertiary는 옅은 호박 글자색 #FFE7A3으로, 플랩(지금·선택)과 나눈다.
- **학습 기록 단계** (heat): 2~4단계 채움. 라이트는 flap-graphic → flap-ink → #4D3500(flap-deep), 다크는 플랩 60% → 80% → 100%. 1단계는 flap-graphic 1.5dp 테두리.
- **정보 그래픽 규칙:** 정보를 담은 칸·막대·점은 바탕과 3:1 이상(outline 이상). outline-variant(rule)는 장식 선과 빈 칸에만 쓴다.
- **옅은 호박 칸** (flap-container / night-flap-container): 폴더로 밀기 배경, 폴더 알약, 여러 번 틀린 카드 안내 칸.

### Neutral
- **대합실 회색** (concourse): 라이트 화면 바탕(background, surface). 시작 창 배경(window_background)도 같은 색이다.
- **탑승권 용지** (stock): 안내판 줄·탑승권·받은 말풍선 바탕(surfaceContainerLowest), 검은 판 위 글자.
- **먹색 보조** (ink-soft): 뜻·날짜·설명 같은 보조 글자(onSurfaceVariant).
- **가는 구분선** (rule): 줄 사이 1dp 선, 탑승권·받은 말풍선 테두리, 꺼진 불빛 칸(outlineVariant).
- **윤곽** (outline): 새 문장 상태 알약의 테두리, 예정 거리 막대 점선, 탑승권 절취선, 날짜 막대(3:1 이상 필요한 그림).
- **옅은 칸** (surface-high): primaryContainer와 surfaceVariant, 바로가기 아이콘 바탕.
- **밤의 출국장** (night-ground, night-stock, night-ink, night-ink-soft, night-rule): 다크 모드의 바탕·줄·글자·선. 줄과 탑승권(night-stock)은 바탕보다 한 단 밝아 밤에도 검은 안내판과 줄이 구분된다.

### 상태 신호
- **정시 초록** (on-time / on-time-light): 두 가지 뜻을 일부러 함께 진다. ① 라이브러리 상태 열의 "출발 예정"(정시) 점, ② 써 보기·복습에서 맞은 문장("통과", 완성 문장 글자, 목표 달성 문구, 성공 안내). 둘 다 "제대로 가고 있다"는 뜻이라 묶었다. M3 스킴에는 없어 `statusColors.success`로 준다.
- **지연 빨강** (delayed / delayed-light, delayed-container): 틀림·오류·삭제에만. M3 error 계열이다.

### Named Rules
**The Flap Only Means Now Rule.** 호박색은 "지금 할 일 하나"와 "선택된 것"에만 켠다. 장식, 제목 강조, 일반 버튼에는 쓰지 않는다. 한 화면에서 호박으로 켜진 행동 버튼은 하나다.

**The Amber Text Rule.** 밝은 바탕에 플랩 호박을 글자로 쓰지 않는다(흰 바탕 대비 약 1.8:1). 글자는 짙은 호박(flap-ink, 약 6.8:1)으로 쓰고, 플랩 호박 글자는 검은 판 위에서만 쓴다(약 9.7:1). 호박 바탕 위 글자는 늘 먹색이다.

**The Three Signals Rule.** 상태 색은 셋뿐이다: 호박 = 지금, 초록 = 정시·통과, 빨강 = 틀림·오류·삭제. 빨강을 "주의"나 강조에 빌려 쓰지 않는다.

**The No Purple Leak Rule.** M3 스킴의 surfaceContainer 다섯 단계와 inverse 색까지 모두 정해 둔다. 비워 두면 M3 기본 보라가 섞인다. 동적 색(배경화면 색)은 기본으로 끈다.

## Typography

**Display Font:** Roboto Condensed (앱에 내장, OFL; 대체 sans-serif-condensed)
**Body Font:** 시스템 산세리프 (한글 가독성)
**Label/Mono Font:** Roboto Condensed + 폭이 같은 숫자(tnum), 자간 0.04em — "안내판 표기"

**Character:** 화면 글자는 시스템 산세리프 한 가족으로 차분하게 읽히고, 시간·편·상태·큰 숫자만 응축체가 맡아 출발 안내판의 목소리를 낸다. 크기 단계는 몇 개만 쓴다.

### Hierarchy
- **Board Number** (Roboto Condensed Bold 700, 44sp/50sp, tnum): 복습을 마친 요약의 큰 장수. 오늘 화면의 "지금 탑승 N장"은 headline 크기(26sp)에 안내판 표기를 입힌다.
- **Headline** (700, 26sp/34sp · 22sp/30sp): 복습 문제 문장(22sp), 오늘 화면의 큰 안내, "오늘 목표 완료".
- **Title** (500, 20sp/28sp, tnum): 위 막대의 화면 이름, 숫자 설정값.
- **Title Medium** (500, 16sp/24sp): 탑승권 안의 완성 문장, 구역 제목, 큰 버튼 글자.
- **Body** (400, 16sp/24sp · 14sp/21sp): 라이브러리 줄의 외국어 문장(16sp)과 뜻(14sp, 보조 색), 힌트·설명.
- **Label** (500, 12sp/16sp): 보조 글자, 목적지(친구·나라). 가장 작은 글자도 12sp다.
- **Board Label** (Roboto Condensed 500, 12sp/16sp, 자간 0.04em, tnum, 라틴 글자는 대문자): 시간(HH:mm, MM/dd), 편 코드, 상태 알약, 열 머리, 탑승권 경로, 묶음 개수.

### Named Rules
**The Board Voice Rule.** 응축체는 시간·편 코드·상태·개수·큰 숫자에만 쓴다. 문장, 설명, 버튼 글자는 시스템 산세리프다. 라틴 글자는 부르는 쪽에서 대문자로 넘긴다.

**The Steady Digits Rule.** 바뀌는 숫자(시간, 장수, 개수, 설정값)는 폭이 같은 숫자(tnum)로 써서 자릿수가 바뀌어도 흔들리지 않게 한다.

**알려진 한계:** Roboto Condensed에는 한글이 없어 "시간·목적지·편·상태", "지금 탑승" 같은 한국어 안내판 글자는 시스템 산세리프로 대신 그려진다. 홈 화면 위젯(Glance)은 내장 글꼴을 쓸 수 없어 시스템 글꼴이다.

## Layout

휴대폰 한 열 레이아웃이다. 화면 좌우 여백은 16dp, 라이브러리 줄 안쪽 여백은 12dp, 줄 사이는 1dp 구분선만 두고 간격을 띄우지 않는다(안내판처럼 붙어 있다). 묶음 제목은 위 16dp·아래 8dp. 요소 사이 간격은 4·6·8·12dp, 오늘 화면의 구역 사이는 20dp다.

안내판 줄은 세 열이다: 시간 열(고정 44dp) · 목적지·편 열(남은 너비) · 상태 열(오른쪽 정렬). 열 머리(BoardColumns)는 같은 열 너비를 쓴다. 상태 열 아래 익힌 단계 불빛 칸(40×4dp)이, 문장 아래 다음 복습까지의 거리 막대(2dp, 14일이면 꽉 참)가 놓인다.

라이브러리는 "지금 탑승 · 복습할 차례", "탑승 대기 · 새 문장", "출발 예정 · 나중에 복습" 묶음으로 나뉜다. 자주 누르는 버튼은 꽉 찬 너비에 높이 52~56dp, 터치 영역은 48dp 이상이다. 시스템 글자 크기를 키워도 잘리지 않게 높이는 최소값(heightIn)으로만 정한다.

## Elevation & Depth

그림자를 쓰지 않는 평평한 세계다. 깊이는 세 겹의 색으로 만든다: 검은 안내판(가장 앞, 위·아래 막대) → 흰 용지(줄·탑승권) → 대합실 회색(바탕). 용지와 바탕 사이는 1dp 가는 선(rule)으로 나눈다. 다크에서는 줄·탑승권이 바탕보다 한 단 밝다(night-stock). 시스템 상태 막대는 위 막대가 검은 화면에서 밝은 아이콘으로 맞춘다.

### Named Rules
**The Tone Not Shadow Rule.** 층은 색 단계와 1dp 선으로만 나눈다. 받은 말풍선도 그림자 대신 가는 테두리를 쓴다. 떠 있는 그림자나 단단한 오프셋 그림자를 넣지 않는다.

## Shapes

탑승권과 안내판처럼 반듯하다. 작은 것(칩, 편 코드, 열 머리, 불빛 칸, 입력 보조)은 4dp, 큰 것(탑승권, 버튼, 오늘 화면 안내판 칸, 떠 있는 검색 막대)은 8dp다. 알약 모양(완전한 둥근 끝)은 상태 알약과 내비게이션 선택 표시에만 쓴다.

두 가지 고유한 모양이 있다:
- **탑승권 절취선:** 본문과 떼어 가는 칸 사이에 점선(6/6)을 긋고, 양 끝을 바탕색 반원(반지름 8dp)으로 파서 실제 표처럼 보이게 한다.
- **말풍선 꼬리:** 모서리 12dp에 한 모서리만 2dp로 뾰족하다. 받은 말은 왼쪽 아래, 내가 보낸 말은 오른쪽 아래가 꼬리다. 앱 아이콘도 겹친 말풍선 두 개(흰색 받은 말, 호박색 보낼 말)를 검은 바탕에 놓는다.

## Components

### Buttons
반듯하고 단단하다. 누르면 Expressive 스프링으로 반응한다.
- **Shape:** 반듯한 모서리 (8dp, `shapes.medium`을 명시해서 넘긴다).
- **Primary:** 먹색 바탕에 흰 글자. 번역 "확인", 번역 결과 아래 고정 "저장", 문장 상세의 "변경 저장", 설정 저장. 꽉 찬 너비일 때 높이 52dp.
- **Now (지금 할 일):** 검은 안내판 칸 안의 플랩 호박 버튼, 먹색 글자, 높이 56dp. 오늘 화면의 "복습 시작"처럼 화면에 하나뿐인 지금 할 일.
- **Secondary (SecondaryButton):** 1dp 테두리 + 먹색 글자. 이 앱의 테두리 버튼은 모두 이것이다(M3 기본 회색 글자는 꺼진 버튼처럼 보인다). 듣기, 이 문장 복습, 내보내기, 이 문법으로 써 보기, 평가의 나머지 선택지. 복습 평가에서는 추천 평가만 Primary이고, 버튼 안에 다음 간격(안내판 숫자)을 함께 쓴다.
- **FilledTonalButton은 쓰지 않는다:** secondaryContainer가 플랩이라 늘 호박이 되어 "지금 할 일" 규칙을 깬다.
- **Destructive:** 확인 대화상자의 Text 버튼에 지연 빨강 글자.

### Chips
- **Style (ChoiceChip):** 4dp 모서리, 투명 바탕에 가는 테두리, 고르지 않아도 먹색 글자.
- **State:** 선택되면 플랩 호박 바탕에 먹색 글자(secondaryContainer). 필터·정렬·언어·설정 선택에 같은 칩을 쓴다.
- **하나만 고르는 줄**(정렬·묶기·보기, 설정의 하루 최대 복습·기억 목표·억양·말 빠르기·알림)은 `radio = true`로 라디오 버튼으로 읽힌다. 세그먼트 버튼(번역 모드·밝기)도 4dp 모서리다.
- **가로로 넘기는 칩 줄**은 화면 끝까지 펼치고(`Modifier.bleed`), 같은 여백을 줄 안쪽에 둔다(칩이 여백 선에서 잘려 보이지 않게).

### Cards / Containers
- **Corner Style:** 탑승권 8dp.
- **구역(SectionCard):** 대합실 회색 바탕 위의 흰 용지 판이다(1dp rule 테두리, 8dp 모서리, 안쪽 16dp). 맨 위에 제목(titleMedium SemiBold)을 둔다. 판 사이의 회색 틈이 구역을 나눈다. 구분선 하나로만 나누면 구역이 이어져 보인다는 사용자 지적이 있었다(문장 상세·성장).
- **Background:** 탑승권 용지(stock), 바탕은 대합실 회색.
- **Shadow Strategy:** 없음(Elevation & Depth 참조).
- **Border:** 1dp rule.
- **Internal Padding:** 16dp, 내용 사이 8dp.

### Inputs / Fields
- **Style:** M3 OutlinedTextField(4dp 모서리 계열). 복습 답은 탑승권의 떼어 가는 칸 안에 놓인다.
- **Search:** 검은 위 막대 안의 한 단 밝은 칸(board-raised, 12dp), 밑줄 없음, 커서는 플랩 호박, 안내문·아이콘은 on-board-muted.
- **Error:** M3 error(지연 빨강).

### Navigation
- **위 막대는 탭 화면에 두지 않는다:** 화면 이름은 아래 탭이 이미 말하고, 한 줄을 차지할 만큼의 일이 없다(사용자 지적). 탭의 내용은 상태 표시줄 바로 아래에서 시작한다(오늘·성장은 검은 안내판 판, 번역은 모드 버튼, 라이브러리는 "≡ 보는 곳 · 필터" 줄, 설정은 탭 줄). 상태 표시줄 아이콘은 앱 밝기를 따르고, 복습(맨 위가 검은 띠)만 흰색이다.
- **위 막대(BoardTopBar)는 덮어 여는 화면에만:** 문장 상세("문장", ✕). 검은 판에 화면 이름(title 20sp, 흰색), heading으로 읽힌다.
- **아래 탭(5개: 오늘·번역·라이브러리·성장·설정):** 설정도 탭이다(톱니 버튼으로 덮어 열지 않는다). 검은 판, 선택되지 않은 아이콘·이름은 on-board-muted, 선택된 탭은 플랩 호박 알약 표시 위 먹색 아이콘과 플랩 호박 이름. 오늘 탭의 복습 수 배지도 호박에 먹색 숫자.

### 출발 안내판 줄 (Signature)
라이브러리의 "촘촘히" 보기. 흰 용지 줄, 아래 1dp 구분선. 왼쪽 시간 열에 저장 시각과 날짜(안내판 표기), 가운데 외국어 문장(body 16sp, 두 줄까지)·뜻(14sp, 한 줄)·편 코드(먹색 칸에 흰 EN/ES, 다크에서는 밝은 칸)·목적지(친구·나라), 오른쪽 상태 열.
- **상태 알약:** 지금 = 호박 알약에 먹색 글자 / 예정 = 초록 점(8dp)과 남은 날 / 새 문장 = 윤곽선만 있는 알약.
- **거리 막대:** 지금 = 꽉 찬 호박 실선 / 예정 = 남은 날만큼 긴 윤곽색 점선(8/6) / 새 문장 = 24×6dp 빈 칸.
- **익힌 단계:** 상태 아래 불빛 칸.
- **열 머리:** 검은 4dp 띠에 호박 안내판 글자 "시간 · 목적지 · 편 · 상태"(스크린리더는 건너뛴다).
- **밀기:** 오른쪽 = 폴더로(옅은 호박 칸), 왼쪽 = 삭제(옅은 빨강 칸). 같은 동작을 접근성 메뉴로도 제공한다.

### 탑승권 (Ticket, Signature)
번역 결과, 써 보기 검사, 복습 문제가 모두 탑승권이다. 검은 머리띠에 호박 안내판 글자로 경로("ES → KO", "한국어 → 스페인어 · 보낼 문장", "ES 검사 · 힌트 1단계"), 흰 본문, 절취선 아래 떼어 가는 칸(저장·답 입력·친구·나라 태그). 써 보기에서 맞은 문장은 정시 초록 글자로, 힌트 단계는 불빛 칸(3칸)으로 보인다.

### 불빛 칸 (SegmentBar)
셀 수 있는 양(오늘 목표, 익힌 단계, 힌트 단계, 복습 진행)을 켜진 칸으로 보인다. 켜진 칸은 flap-graphic 채움, 꺼진 칸은 segment-off 1dp 테두리. 검은 판 위(`onBoard = true`: 복습 위 막대, 오늘 목표)에서는 켜진 칸 플랩, 꺼진 칸 on-board-muted 테두리. 칸 사이 3dp, 칸 모서리 4dp, 20칸이 넘으면 비율로 줄인다.

### 문장 탑승권 (SentenceTicket, 문장 상세)
문장 상세의 맨 위.
- 머리띠: "<언어> → 한국어 · 저장한 때".
- 본문: 문장(headline)·뜻·편 표시와 친구·나라·상태 알약, "<단계 이름> · n/5" 불빛 칸(8dp), 거리 막대.
- 절취선 아래: 복습·기억·틀림 수(안내판 숫자, 셋 다 복습 기록에서 셈, 틀림이 있으면 빨강), 마지막·다음 복습.

그 아래 순서는 다음과 같다.
1. 앞뒤 대화 말풍선(있을 때).
2. "답장 써 보기"(주 버튼)와 "이 문장 복습"(테두리 버튼)을 나란히.
3. 탑승 기록, 복습과 통계.
4. **읽기가 기본:** "고치기" 판 하나에 뜻·다른 언어·핵심 표현·설명·분류·태그를 요약으로 보이고, "고치기"를 누르면 고치는 칸들이 펼쳐진다. 위쪽 저장 버튼은 없고, 고친 것이 있을 때만 화면 아래에 "변경 저장" 막대가 뜬다.
5. 맨 끝에 "노션에 보내기"(테두리, 전체 너비)와 빨간 글자 "이 문장 삭제".
- 다음 복습 표기: 알아요면 "없음 · 알아요", 카드가 없으면 "없음", 새 문장이면 "처음 복습 전", 기한이 지났으면 "지금 가능", 그 밖에는 MM/dd HH:mm.

### 안내판 띠 (BoardStrip, 성장)
검은 판에 칸 셋(익힌 문장 · 7일 복습 · 7일 정답률). 맨 위는 출석 숫자가 아니라 "전주의 나"와의 비교다.
- 칸 내용: 작은 이름표(Board Label, on-board-muted), 안내판 숫자(headlineSmall 응축, 바뀌면 플랩), 그 아래 작은 비교 글(labelSmall).
- 비교 글 색: 늘면 on-board(밝게), 줄거나 같으면 on-board-muted.
- 비교 글 꼴: "전주보다 +3 / −2 / 전주와 같음"처럼 +N 꼴로 쓴다. ▲▼ 기호와 빨간 손실 표시는 쓰지 않는다. 호박색은 쓰지 않는다(호박은 지금·선택 전용).
- 칸 사이는 1dp board-raised 세로선. 큰 숫자 하나를 내세우는 히어로 숫자 틀은 쓰지 않는다.
- 띠 아래 한 줄은 기록에서 나온 사실만 쓴다(예: "영어 문장 3개를 익혔어요. 가장 많이 나온 친구는 Lucia, 2개예요.").

### 익힌 정도 막대 (MasteryBar, 성장)
문장 단위 네 단계를 12dp 막대로, 아래에 칸 + 이름 + 안내판 숫자.
- 익힘: flap-graphic 꽉 찬 칸.
- 거의: 흰 용지에 1.5dp flap-graphic 테두리(색과 모양으로 함께 구분).
- 익히는 중: ink-soft.
- 새 문장: outline.

단계 이름은 앱 전체가 같다: 0 새 문장 · 1~2 익히는 중 · 3~4 거의 · 5 익힘(21일 이상). 학습 기록(12주)의 켜진 날은 flap-graphic 불빛 밝기 다섯 단계, 빈 날은 옅은 선 색(rule).

### 설정 묶음 (SettingsGroup · SettingItem)
묶음 이름(titleSmall, ink-soft) 아래 흰 탑승권 용지 판(stock, 8dp, 1dp rule 테두리) 하나에 항목들. 항목은 이름(bodyLarge)·설명(bodySmall)·조작 순서, 항목 사이는 1dp rule. 묶음끼리는 24dp, 항목 안은 8dp로 띄워 덩어리가 한눈에 갈린다. 숫자 설정(Stepper)의 −/+는 테두리 버튼, 값은 안내판 숫자(호박은 쓰지 않는다).

### 오늘 화면
검은 안내판 판 하나에 "오늘 탑승 n장 · 복습 a장 · 새 카드 b장", 호박 "복습 시작", 그 아래 오늘 목표 불빛 칸(판 위 색)과 "오늘 목표 n/m장 · k장 남았어요", 카드 단위 안내 한 줄. 판 아래는 흰 판 "내 문법 약점"(먹색 "n번 틀림"과 "이 문법으로 써 보기"), "앞으로 7일", 그리고 라이브러리와 같은 출발 안내판 줄의 "최근 저장한 문장"(누르면 그 문장 상세). 연속일은 오늘 화면에 두지 않는다.

### 번역 결과 (받은 말)
결과가 나오면 입력칸은 한 줄로 접히고, 입력칸 오른쪽에 "다시 번역"(↻)과 지우기(✕) 아이콘이 붙는다. 탑승권의 떼는 칸에는 친구 줄과, 나라·날짜·태그·핵심 표현의 요약 한 줄(누르면 펼침)이 있다. "저장"은 화면 아래 고정 주 버튼(52dp, 위에 1dp rule). 나라 칩은 문장 언어를 쓰는 나라가 먼저이고, 친구를 고르면 그 친구와 마지막으로 붙였던 나라가 자동으로 골라진다(직접 고른 나라는 건드리지 않음).

### 복습 답 화면
문제(받은 말) → 내 답(보낸 말) → 정답 탑승권("정답 · <언어>" 머리띠, 절취선 아래 "어떤 대화였는지 보기 ⌄" → 말풍선). 위 막대는 "이번 묶음 n/m", 아래 줄은 "오늘 목표 n/m".

### 라이브러리 선택 모드
**떠 있는 검색 막대:** 라이브러리 아래, 탭 바로 위에 떠 있는 검은 판 8dp 검색칸(엄지가 닿는 자리). 키보드가 올라오면 함께 올라오고, 목록 아래에는 그만큼 여백을 둔다. 선택 중에는 숨긴다.

길게 누르면 라이브러리 맨 위 줄이 "✕ · n개 선택 · 전체 선택/선택 해제"로, 아래 탭 자리가 행동 막대(검은 판 위 흰 "선택 복습" · "폴더로" · ⋮ 노션에 보내기·빨간 삭제)가 된다. 고른 문장은 검색을 바꿔도 남는다.

### 편성표 서랍 (라이브러리)
라이브러리 위쪽은 한 줄이다: ≡ · 지금 보는 곳("전체 · Lucia · 거의") · 문장 수(안내판 숫자) · 필터 · ⋮. ≡이나 그 줄을 누르면 왼쪽에서 검은 안내판 서랍(288dp)이 열린다.
- 서랍 안 순서: 폴더(전체 · 폴더 없음 · 각 폴더 · 새 폴더) → 친구 → 상태(모든 상태 · 새 문장 · 익히는 중 · 거의 · 익힘 · 최근 틀림(7일 안에 "몰랐어요") · 곧 복습(3일 안) · 자주 틀린 것 · 알아요 · 통계 제외).
- 줄마다 아이콘 · 이름 · 수(안내판 숫자)를 둔다. 수는 모두 같은 기준(전체 문장, 삭제 대기 제외)이다. 고른 줄은 호박, 수가 0인 상태는 숨긴다. 고르면 서랍이 닫힌다.
- 친구는 필터 칸의 주제 태그와 따로 걸리고, 함께 걸 수 있다.
- 서랍은 버튼으로만 연다. 문장 줄을 오른쪽으로 미는 것은 폴더 이동이라 겹치지 않게 했다.
- 필터 안에는 언어·태그 / 정렬 / 묶기 / 보기만 남고, 줄마다 앞에 이름표를 단다.

### 친구별 묶음 (라이브러리)
- 머리 줄: "Lucia · 스페인 n"(가장 많이 붙은 나라). 친구가 없는 문장은 "친구 없음"으로 맨 끝.
- 묶음 안의 줄은 목적지에서 친구 이름을 빼고 나라만 둔다.

### 탑승 기록 (문장 상세)
복습할 때마다 한 줄. 날짜(MM/dd 안내판 숫자) · 방식(성장 정답률과 같은 이름, 88dp) · 내 답(한 줄, 없으면 비움) · 평가.
- 평가 표시: 8dp 점 + 평가 글. 몰랐어요는 빨강, 겨우 기억은 속 빈 outline 점, 기억했어요·완벽해요는 초록.
- 최근 10개만 먼저 보이고 "기록 n개 모두 보기"로 펼친다.
- 기록이 4개 이상이면 위에 단계 계단을 둔다. 복습마다 5칸 불빛 기둥(14×5dp 칸)을 시간순으로 놓고, 앞에 "익힘" 축 이름을 단다.
- 단계는 그 카드의 앞 복습부터 기억한 기간으로 어림하고, 첫 복습은 1이다.

### 성장: 친구별 막대 · 앞으로 30일
- 친구별: 친구마다 이름 + "익힘 m / n"(안내판 숫자), 아래 6dp 막대(outline-variant 위에 flap-graphic으로 익힌 비율). 문장이 많은 친구부터 5명.
- 앞으로 30일: 날마다 막대 하나. 복습할 카드는 꽉 찬 outline 막대(오늘만 flap-graphic), 하루 새 카드 한도대로 들어올 새 카드는 1dp 테두리 막대를 위에 쌓는다.
  - 날짜는 한 주에 한 번, 마지막 날은 오른쪽 끝에 맞춘다.
  - 단위가 카드라서 문장 수와 다를 수 있음을 한 줄로 밝힌다.
- 정답률은 칸 하나에 언어·방식·태그 칩으로 바꿔 본다. 연속일은 12주 기록 줄 끝에 "· n일째 이어서"로만 붙인다.

### 끝 상태 (이미 알아요 · 통계에서 빼기)
문장 상세의 "복습과 통계" 칸에 스위치 두 줄을 둔다. 줄마다 이름(bodyLarge, SwitchRow)과 무슨 일이 생기는지(bodySmall)를 쓰고, 줄 전체를 누를 수 있다. 저장 버튼과 상관없이 바로 반영된다.
- 이미 알아요:
  - 복습 차례·위젯·예보에서 빠지고, 익힌 정도에서는 익힘으로 센다.
  - 라이브러리에서는 맨 끝 "도착 · 이미 알아요" 묶음에 들어간다. 상태 칸은 색 없이 체크 표시 + "알아요", 거리 막대는 없다.
- 통계에서 빼기: 정답률·익힌 정도·친구별·자주 틀리는 문장에서 빠진다. 복습과 복습한 날 기록(연속·달력)은 그대로다. 줄에는 따로 표시하지 않고, "통계 제외" 칩으로 찾는다.
- 이미 알아요 문장은 줄과 상세의 불빛 칸이 다섯 칸 모두 켜지고(익힘), 상세의 다음 복습은 "없음 · 알아요"다.

### 대화 말풍선 (문장 상세)
앞뒤 대화는 받은 말 말풍선(흰 용지·1dp 테두리·왼쪽)으로 보인다. 누가 "나"인지 알 수 없어 모두 왼쪽에 둔다.
- 말한 사람이 바뀔 때만 이름을 labelMedium으로 위에 단다.
- 흰 구역 판 안이라 받은 말은 대합실 회색 말풍선(테두리 없음)이다. 저장한 줄만 흰 바탕에 1.5dp on-surface 테두리와 진한 글자로 띄운다. 먹색 바탕은 "내가 보낸 말"의 뜻이라 쓰지 않는다. 나머지 줄은 ink-soft 글자.
- 저장한 줄 표시가 없는 맥락(답장한 말, 단어의 원래 문장)은 나누지 않고 한 덩어리로 둔다.

### 말풍선 (ChatBubble)
문제·내 답·채점을 대화처럼 보인다. 내가 보낸 말은 먹색 바탕에 흰 글자(오른쪽), 받은 말은 흰 용지에 1dp 테두리(왼쪽). 안쪽 여백 18×14dp, 너비는 88%까지.

### 플랩 전환 (FlapText, GoalBoard)
상태·개수 글자가 바뀌면 새 글자가 위에서 미끄러져 들어오고(180ms, 페이드 함께) 옛 글자는 아래로 빠진다(140ms). 오늘 목표를 채우면 화면 가운데 검은 안내판 칸이 위에서 펼쳐져(260ms) "오늘 목표 완료"를 호박 글자로 1.1초 보이고 사라진다. 시스템 애니메이션을 끄면 펼쳐지지 않고 잠깐 보였다 사라진다. 이 움직임은 코드로만 확인했고 화면 캡처로는 검증하지 못했다.

## Do's and Don'ts

### Do:
- **Do** 위 막대·아래 탭·열 머리·탑승권 머리띠를 안내판 검정(#15181D)으로 칠하고, 그 사이 바탕은 대합실 회색, 내용은 흰 용지 줄로 둔다.
- **Do** 호박색은 화면의 "지금 할 일 하나"와 선택 표시에만 켜고, 그 위 글자는 늘 먹색으로 쓴다.
- **Do** 밝은 바탕에서 호박을 글자로 써야 하면 짙은 호박(flap-ink #7A5400)을 쓴다.
- **Do** 초록은 "출발 예정(정시)"과 "통과(맞음)"에, 빨강은 틀림·오류·삭제에만 쓴다.
- **Do** 시간·편·상태·개수는 안내판 표기(Roboto Condensed, tnum, 자간 0.04em, 라틴 대문자)로 쓴다.
- **Do** 상태는 색과 함께 모양으로도 구분한다(실선·점선·빈 칸, 채운 알약·점·윤곽 알약).
- **Do** 버튼에 `MaterialTheme.shapes.medium`(8dp)을 명시해 넘긴다.
- **Do** 새 결과 화면은 탑승권(머리띠 경로 + 본문 + 절취선 아래 떼어 가는 칸)으로 만든다.
- **Do** 글자색은 배경 대비 4.5:1 이상, 그림 요소는 3:1 이상(outline)을 지킨다.

### Don't:
- **Don't** 둥근 카드 목록과 크림·보라 계열의 공부 앱 배치로 돌아가지 않는다.
- **Don't** M3 스킴의 색 슬롯(surfaceContainer 단계, inverse 색)을 비워 두지 않는다. 비우면 기본 보라가 섞인다.
- **Don't** 플랩 호박(#F6B400)을 흰·회색 바탕 위 글자색으로 쓰지 않는다.
- **Don't** 호박을 장식이나 제목 강조, 일반 버튼에 쓰지 않는다. 한 화면에 호박 행동 버튼은 하나다.
- **Don't** 빨강을 강조나 주의 표시에 빌려 쓰지 않는다.
- **Don't** 그림자로 층을 나누지 않는다. 색 단계와 1dp 선을 쓴다.
- **Don't** 알약 모양을 상태 알약과 내비게이션 표시 밖에서 쓰지 않는다.
- **Don't** 응축체를 한국어 문장이나 설명 글에 쓰지 않는다.
