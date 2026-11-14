---
version: 1
slug: "com-chatlearn-ui-library-libraryscreen-kt-8cd0fc55"
primary_target: "app/src/main/java/com/chatlearn/ui/library/LibraryScreen.kt"
related_targets: ["app/src/main/java/com/chatlearn/ui/translate/TranslateScreen.kt"]
---

## Scope
앱 전체(Android, Operate). 처음 보여 줄 화면: 라이브러리, 번역·써 보기. 나머지 화면(오늘·복습·성장·설정·위젯)은 같은 세계를 따른다. 기능·흐름·4탭 구조는 그대로 둔다.

## Audience and task
한 사람: 외국 친구와 대화하며 영어·스페인어를 배우는 한국어 화자. 대화 중 몇 초 안의 도움, 자투리 복습, 앉아서 공부. 성공: 자꾸 열고 싶고, 실력이 쌓이는 게 보인다. 피할 것: 교과서·시험지 같은 느낌.

## Direction contract
THESIS: 문장 하나하나가 친구에게 가는 탑승권이고 라이브러리는 출발 안내판이다. 둥근 카드 목록과 크림·보라 공부 앱이라는 기본 배치를 거부한다.
OWN-WORLD: 안내판 검정 #15181D가 위 막대·아래 탭·열 머리를 칠하고, 호박색 #F6B400은 지금 할 일과 선택에만, 대합실 #F2F3F5 바탕에 흰 줄과 가는 구분선, 빨강 #D2382F 틀림, 초록 점 #2E8B57 출발 예정. 코드·시간·상태는 응축 대문자 고정폭 숫자, 문장은 시스템 산세리프, 모서리 4/8dp.
STORY: 문장을 그 친구·나라와 함께 보고, 지금 복습할 것을 한눈에 알고, 다음 복습까지의 거리와 익힌 단계로 실력이 쌓이는 걸 본다.
FIRST VIEWPORT: 라이브러리. 검은 위 막대에 검색, 칩 줄(선택 칩 호박), 검은 열 머리 "시간·목적지·편·상태", "지금 탑승"과 "출발 예정" 묶음, 줄마다 시간·외국어 문장·뜻·친구·나라·EN/ES 편 코드·상태 알약, 아래 길이 막대(실선 호박=지금, 점선=예정, 빈 막대=새 문장). 번역 결과는 FROM 한국어 → TO 외국어 탑승권, 절취선 아래 저장.
FORM: 출국장 출발 안내판 + 탑승권, 내 목록의 6번째(주사위 배정), seed:1952dec14751e762. 대표 동작: 상태가 바뀔 때 글자가 플랩처럼 넘어가는 짧은 전환과 다음 복습까지의 길이 막대.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Raises from declined challengers
- 춤 기보법: 다음 복습까지 날짜를 막대 길이로.
- 분광선 레일: 상태를 색과 함께 선 모양(실선·점선·빈 막대)으로.
- 식물 도감: 익힌 단계를 같은 축척의 띠로.
- 에미그레 견본: 한 글꼴 가족, 엄격한 크기 단계.

## Unresolved
실기기 취향 확인. 위젯은 Glance 제약으로 응축체 대신 시스템 글꼴.
