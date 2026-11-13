# ChatLearn 2차 전면 개선 계획 (2026-11-13)

## Context
11/2 1차 개선(위젯·바로가기·첨삭·히트맵 등, 테스트 407개) 이후, 앱 전체를 다시 촘촘히 점검했다(화면·디자인 / 성능·구조 / 사용자 여정 3갈래 정적 분석 + 스크린샷).
결론: 기능은 많지만 ① 데이터를 망가뜨리거나 앱을 죽이는 버그 2개, ② 기본 M3 보라색이라 앱 정체성이 없음, ③ 복습 첫 화면이 텅 비고 통계·설정이 깊이 숨음, ④ 첫 실행(키 발급) 길이 험함, ⑤ 배우는 과정이 "AI가 답을 준다"에 머물러 스스로 깨닫는 학습이 약함.

사용자 결정(2026-11-13):
- 대상: **나 혼자 쓰기**(스토어 출시 작업 제외, 문자열 리소스화·영어 UI 제외)
- DB: **v5 마이그레이션 허용**
- 디자인: **새 브랜드 — 따뜻·친근 + 선명·활기(게임화)**
- 기능: 온보딩+자동 백업, FSRS, 대화 맥락+답장 연습, 단어 저장+복습 예보, **+ 문법 약점 파악, 힌트로 스스로 고쳐 가는 첨삭, 사람·나라 태그 빠른 선택, 노션 내보내기(수동)**
- 첨삭: 기존 "보낼 말"(즉시 후보)은 유지하고 "스스로 써 보기"(힌트형)를 새로 둔다.
- 문법 데이터: 써 보기 실수 + 복습 실수 + 보낼 말 요청 패턴, 세 곳 모두.

원칙: 단계마다 테스트 먼저(RED→GREEN) → `code-review` 스킬 → 에뮬레이터 QA(`rules/qa.md`) → 사용자 요청 시 성격별 커밋. 파일 500줄 상한(`rules/code.md`). 새 라이브러리·라이브러리 버전 올리기·폰트 파일 추가는 그 단계에서 승인받는다.

---

## 단계 1. 안정성·성능 (화면 변화 없음)
**버그(P0)**
- 1-1. 미리 복습하면 간격이 부풀려짐: `domain/Scheduler.kt:32`가 실제 지난 시간을 안 본다. 라이브러리 "선택 복습"(`EntryRepository.cardsFor`)을 3번 하면 간격이 2.5³배. → 단계 2의 FSRS가 경과 시간을 쓰므로 근본 해결. 그 전에 실패 테스트부터 쓴다.
- 1-2. 복습 중 문장이 삭제/가져오기로 사라진 뒤 평가하면 FK 오류로 앱이 꺼짐(`ReviewViewModel.rate` :275, `review_log` FK). → `session.rate`/`editCurrent`에서 카드가 없으면 건너뛰고 다음 카드, VM 런처에 오류 처리.
- 1-3. 첫 실행 탭 점프: `MainActivity.kt:138` `tab ?: (키 없음→설정)` 때문에 키를 저장하는 순간 번역 탭으로 튐(결과 메시지 못 봄). → 단계 4 온보딩으로 대체, 그 전엔 시작 탭을 한 번만 정한다.
- 1-4. 라이브러리 선택 모드에서 뒤로가기가 탭을 떠남 → 선택 해제 BackHandler.
- 1-5. 탭 전환 시 스크롤·필터·선택 상태 소실(`Crossfade`) → `SaveableStateHolder`.
- 1-6. 상세 대화상자 X가 수정 내용을 묻지 않고 버림, 단건 삭제 확인 없음 → 변경 시 "버릴까요?" 확인.
- 1-7. `ReminderReceiver` 예외 처리 없음, 시간대 변경(TIMEZONE_CHANGED/TIME_SET) 미수신.

**성능**
- 1-8. 메인 스레드 계산 제거: `observeLibrary` 묶기, `StatsCalculator.compute`, 백업 인코딩 → `flowOn(Dispatchers.Default)`.
- 1-9. 같은 전체 조회 반복: `LibraryViewModel`에서 `observeLibrary` 5번, `observeStatsInput` 3번 수집 → 하나를 `shareIn`. 복습 화면이 연속일 표시만 위해 통계 전체를 다시 계산(`ReviewScreen.kt:115`) → 연속일 전용 쿼리.
- 1-10. `LibraryScreen.kt:310-315` 묶기·`:524` 문장 통계를 `remember`로.
- 1-11. 복습 큐 N+1(`EntryRepository.dueCardOf`) → 한 번에 조회.
- 1-12. 위젯: 30분 깨움 → 자정 + 데이터 변경 시 갱신, 전체 로드 대신 COUNT 쿼리(`widget/WidgetSnapshot.kt:15`).
- 1-13. 시작 비용: TTS를 처음 쓸 때 연결(lazy), 알람·위젯 프로세스에서는 안 띄움. AI 호출 수 기록이 앱 루트를 다시 그리지 않게(`AppContainer.kt:43`).

**AI 계층** (`ai/GeminiAiService.kt`)
- 1-14. HTTP 취소 지원(`suspendCancellableCoroutine` + `call.cancel()`), 429는 `Retry-After`·분/일 한도 구분, 500/504·네트워크 오류 재시도, SAFETY/MAX_TOKENS/404(모델명 오류) 정확한 오류.
- 1-15. 생각(thinking) 예산 최소화 + `maxOutputTokens`, 모델은 별칭 대신 고정 버전(구현 때 현재 Gemini 모델 ID를 웹에서 확인).
- 1-16. 같은 문장/단어 재요청 캐시(메모리 LRU). 복습 중 AI가 실패하면 그 세션은 AI 끔(카드마다 90초 대기 방지), 다음 카드 준비 중 표시.
- 1-17. 중복 코드 정리: `translateReceived`/`Batch` 파싱 공유, 하루 시작 계산 4곳 → 하나, `MATURE_DAYS` 2곳 → 하나, 죽은 코드 `EntryRepository.textsOf` 삭제.

**빌드**
- 1-18. release `buildTypes`(R8 minify + shrinkResources), 릴리스에서 `Log.w`로 응답 본문 남기지 않기, versionCode 올리기. 의존성 버전 올리기(Kotlin 2.0→, Room 2.6→, material3 alpha 등)는 목록만 만들어 승인받는다.

## 단계 2. DB v5 + FSRS
- 2-1. 마이그레이션 4→5 한 번에(테스트 먼저, 기존 `MigrationTest.kt` 방식 그대로):
  - 인덱스: `review_log(reviewedAt)`, `review_log(cardId, reviewedAt)`, `entry_text(lang, text)`, `entry(createdAt)`
  - `review_card`: `stability REAL`, `difficulty REAL` (기존 간격·ease에서 변환)
  - `entry`: `kind`(SENTENCE/WORD), `contextText`(앞뒤 대화), `notionPageId`
  - `tag`: `kind`(TOPIC/PERSON/COUNTRY)
  - 새 표 `grammar_event`(lang, pointKey, source[ATTEMPT/REVIEW/REQUEST], isError, entryId?, at), `entry_grammar`(entryId, pointKey)
  - 백업 JSON 버전 3: 새 필드 + **설정 포함**, v1·v2 파일 계속 읽기(`data/Backup.kt`).
- 2-2. FSRS(순수 Kotlin, 라이브러리 없이 ~120줄): `Scheduler.next(card, rating, now)` 모양 유지, 경과 시간 반영, 목표 기억률 설정(기본 0.9). 공개된 기준값으로 테스트. 하루 복습 50장 고정값 → 설정.
- 2-3. 복습 예보: 앞으로 7일 날짜별 예정 카드 수 쿼리.

## 단계 3. 새 브랜드·정보 구조(IA)·UX
- 3-1. **시안 먼저**: "따뜻·친근 + 선명·활기" 방향으로 2개 변형을 클릭 가능한 HTML 시안(Artifact)으로 만들어 홈·복습·결과·라이브러리 4화면을 보여 주고 고르게 한다. 폰트(예: Pretendard, OFL)를 `res/font`에 넣을지 함께 승인.
- 3-2. 테마 토큰 재정의(`ui/theme/Color.kt`, `Theme.kt`, `Type.kt`): primary/secondary/**tertiary**/`surfaceContainer*` 모두 지정(지금은 tertiary·surface가 M3 기본값이라 회색·분홍이 섞임), 큰 숫자용 display 스타일, 모양·간격 토큰. 위젯·아이콘도 새 색.
- 3-3. 정보 구조 개편(하단 4탭): **오늘(홈)** / 번역 / 라이브러리 / **성장**. 설정은 홈 오른쪽 위 톱니.
  - 오늘: 큰 숫자로 오늘 복습 수 + 시작 버튼, 목표 링, 연속일, 7일 예보, "약한 문법" 카드, 최근 저장. (지금 텅 빈 복습 첫 화면 대체)
  - 복습은 홈에서 들어가는 전체 화면 흐름. 탭에 남은 카드 배지.
  - 성장: 지금 통계 + 문법 약점(단계 5) + 자주 틀리는 문장(눌러서 열기).
- 3-4. 게임화·모션: 목표 달성 축하(Compose Canvas 색종이, 라이브러리 없이), 카드 넘김 전환, 세션 진행 막대(Expressive wavy), 연속일 마일스톤 배지, 세션 콤보. 시스템 애니메이션 끄기 설정 존중.
- 3-5. 화면별 UX: 번역 결과는 한국어 뜻을 맨 위·저장 버튼 크게, 저장 되돌리기, 검색창 아이콘·지우기, 라이브러리 머리 영역 접기, 상세는 보기 모드 → 편집, 상세에 듣기 버튼, 피드백 방식 통일(스낵바), 설정 숫자 입력 → 스테퍼.
- 3-6. 접근성: 선택 모드 checked 의미, 밀기 동작의 대체 동작, 판정·알림 `liveRegion`, 제목 `heading`, 색만으로 구분하던 곳에 아이콘, 큰 글씨에서 잘리는 고정 높이(`StatsScreen.kt:131`, `ReviewScreen.kt:287`).
- 3-7. 큰 파일 나누기: `LibraryScreen.kt`(737줄) → 화면/상세/행/대화상자 4개, `ReviewScreen.kt`(533줄) → 요약/문제/채점/음성. `"CORRECT"` 문자열 대신 enum.

## 단계 4. 들어오는 길·맥락·태그
- 4-1. **온보딩**(첫 실행 1회): 앱 소개 → "친구 메시지가 Google Gemini로 보내져요" 안내 → 키 발급 안내·붙여넣기·연결 확인 → 예시 문장으로 첫 번역 → 알림 켜기 제안. `AppSettings.onboardingDone`.
- 4-2. 공유/텍스트 선택으로 한국어가 들어오면 자동으로 "보낼 말"(지금은 오류로 막힘, `TranslateViewModel.kt:157`). PROCESS_TEXT는 결과를 원래 입력칸에 돌려주기(`setResult`, 읽기 전용이 아닐 때).
- 4-3. 카톡 형식 `[이름] [오후 3:15] 메시지` 파싱, 말한 사람 이름을 문장에서 떼고 **사람 태그 후보로**(`domain/MessageSplit.kt`). 20줄 넘으면 잘렸다고 알림.
- 4-4. **사람·나라 태그**: 저장하는 곳(번역 결과, 써 보기 결과, 상세)에 "사람" 줄(최근 사람 칩 + 추가)과 "나라" 줄(스페인어·영어권 나라 기본 목록 + 최근) 한 번 탭으로 선택. 라이브러리 필터·묶기에 사람/나라.
- 4-5. **대화 맥락**: 여러 줄을 붙여 넣고 한 줄을 저장하면 앞뒤 줄을 `contextText`로 함께 저장, 복습·상세에서 접어서 보기.
- 4-6. 보낼 말 목표 언어(EN/ES) 기억, 중복 저장 시 "저장된 문장 열기".

## 단계 5. 배우는 기능
- 5-1. **스스로 써 보기**(번역 탭 세 번째 모드 "첨삭"을 대체): 한국어로 하고 싶은 말 → 내가 영어/스페인어로 시도 → AI가 맞았는지 + **정답을 주지 않고 힌트 1단계**(어떤 문법 개념인지) → 다시 시도 → 2단계(규칙 설명) → 3단계(일부 보여 주기) → 언제든 "정답 보기". 맞히면 바로 저장(사람·나라 태그 포함). 이미 쓴 문장만 고치고 싶으면 한국어 칸 비우기.
  - 화면 상태는 VM의 단순 상태 머신(입력 → 시도 → 힌트 n → 완료), AI 응답 스키마에 `correct`, `hint`, `hintLevel`, `grammarPoints[]`(정해진 목록 enum).
- 5-2. **답장 연습**: 라이브러리/복습에서 받은 문장 → "답장 연습" → 그 문장을 맥락으로 한 5-1 흐름.
- 5-3. **문법 약점 파악**
  - 문법 항목 목록: 언어별 30개 안팎 고정 목록(예: 접속법, ser/estar, 현재완료, 가정법, 관사…)을 코드 상수로 두고 AI 스키마 enum으로 써서 집계가 흔들리지 않게.
  - 수집: ① 써 보기 실수(시도마다 틀린 문법) ② 복습: 문장 저장 때 받은 그 문장의 문법 항목(`entry_grammar`)에 복습 오답을 더함(빈칸·받아쓰기도 AI 추가 호출 없음), 번역 채점은 AI가 틀린 문법을 직접 표시 ③ 보낼 말 요청: 후보에 쓰인 문법 항목을 "자주 필요한 문법"으로 기록.
  - 보기(성장 탭): 약한 문법 순위(오답률·횟수, 출처별), 항목을 누르면 짧은 설명 + 내가 틀린 실제 문장 + "이 문법으로 3문제 써 보기"(5-1로 연결).
- 5-4. **단어 저장**: 번역 결과에서 단어를 눌러 본 뜻을 "단어 카드로 저장"(`entry.kind = WORD`, 원래 문장을 맥락으로). 단어 카드는 빈칸 대신 뜻→단어 떠올리기로 출제.
- 5-5. 끈질기게 틀리는 카드: 다시 배우기(초기화)·잠시 쉬기, 배너가 영원히 남는 문제(`review/GoalLeech.kt`) 해결.

## 단계 6. 노션 내보내기·백업
- 6-1. **노션 내보내기(수동)**: 설정에 노션 통합 토큰(기기 암호화 저장소, `settings/SecretStore`)과 데이터베이스 ID. 문장 상세 "노션에 보내기", 라이브러리 선택 → "노션에 보내기". 이미 보낸 문장은 `notionPageId`로 같은 페이지를 갱신(중복 생성 안 함). 속성: 문장, 뜻, 언어, 종류, 사람, 나라, 태그, 문법, 메모, 저장일. 노션 API 호출은 OkHttp로 `ai/`처럼 경계 한 곳(`notion/NotionClient.kt`)에. 노션 API 버전은 구현 때 공식 문서로 확인.
  - 연결된 Notion MCP로 사용자 워크스페이스에 데이터베이스를 만들어 줄 수 있다(실행 전 승인). 토큰은 사용자가 앱에 직접 넣는다.
- 6-2. **자동 백업**: ① Android 자동 백업 켜기(`allowBackup` + `dataExtractionRules`, 암호화 키 파일은 제외 — 새 기기에서 못 풀기 때문) ② 사용자가 고른 폴더(SAF)에 앱을 열 때 7일 넘었으면 자동 내보내기(WorkManager 없이). 가져오기는 설정까지 복원.

## 단계 7. 마무리 QA
- 전체 시나리오(아래 검증) + 자체 리뷰, 진행 기록을 계획 문서에 남긴다.

---

## 하지 않는 것 (이번 범위 밖)
문자열 리소스화·영어 UI, 플레이 스토어 출시 작업(개인정보처리방침·서명·targetSdk 36), 여러 기기 동기화·계정, 자유 대화형 AI 롤플레이, 리그·레벨 시스템, 자동 노션 전송.

## 핵심 파일
- 진입·탐색: `MainActivity.kt`, `AppContainer.kt`
- 데이터: `data/Entities.kt`, `data/AppDao.kt`, `data/AppDatabase.kt`, `data/EntryRepository.kt`, `data/Backup.kt`
- 일정: `domain/Scheduler.kt`, `review/ReviewSession.kt`, `review/exercise/ExercisePolicy.kt`
- AI: `ai/GeminiAiService.kt`, `ai/AiService.kt` (+ 새 `notion/NotionClient.kt`)
- 화면: `ui/theme/*`, `ui/translate/*`, `ui/library/LibraryScreen.kt`(나누기), `ui/review/ReviewScreen.kt`(나누기), `stats/*`, `ui/settings/*`, `widget/*`
- 다시 쓰는 것: `SectionCard`/`InfoMessage`(`ui/components/Components.kt`), `MigrationTest` 방식, `tools/fake_gemini.py`(새 응답 형식 추가), `SecretStore`, `Tts`

## 검증
- 단위 테스트: `./gradlew :app:testDebugUnitTest` — 시작 407개, 단계마다 통과 수 숫자로 보고. 새 테스트: 미리 복습 간격, 삭제 후 평가, 마이그레이션 4→5(데이터 보존·변환값), FSRS 기준값, 백업 v3 왕복·v1/v2 읽기, 카톡 파싱, 써 보기 상태 머신, 문법 집계, 노션 요청 본문(MockWebServer), AI 취소·오류 매핑.
- 빌드: `:app:assembleDebug`, `:app:assembleRelease`(R8 후 실행 확인).
- 화면: 에뮬레이터(Pixel_3_API_29) + `tools/fake_gemini.py`로 온보딩 → 붙여넣기 번역 → 사람/나라 태그 저장 → 써 보기 힌트 3단계 → 복습 → 축하 → 성장 탭 문법 약점 → 노션 보내기(가짜 서버)까지 클릭 확인, 스크린샷 비교.
- QA 기록: `docs/qa-guide.md`(기대값·실제값·Pass/Fail·날짜).
- **사람 QA로 넘길 것**: 실제 Gemini 키 연결, 실제 노션 토큰·데이터베이스로 보내기, 실기기 디자인·글씨 크기 체감, 자동 백업 후 새 기기 복원, 실제 음성 인식, 카톡에서 공유/텍스트 선택이 되는지.

## 진행 기록
* 2026-11-13 단계 1 완료(커밋 전). 테스트 407 → 428개 모두 통과. 에뮬레이터(Medium_Phone_API_37.0)에서 release·debug 확인.
  - 고침: 1-1~1-14, 1-16~1-18. 추가로 번역 누르면 키보드 내림, LibraryScreen 737줄 → 360줄(LibraryRows·LibraryDetail로 나눔).
  - 건너뜀: 1-15(생각 예산·모델 고정) — 모델마다 허용 값이 달라 잘못 보내면 모든 요청이 400으로 실패한다. 공식 문서로 현재 모델을 확인한 뒤 다시 본다.
  - 건너뜀: 1-13의 "AI 호출 기록이 앱 루트를 다시 그림" — 호출 한 번에 한 번이라 비용이 거의 없다.
  - 승인 대기: 의존성 버전 올리기(AGP 8.7→, Kotlin 2.0.21→, Room 2.6.1→, material3 alpha, security-crypto 지원 종료).
  - 환경: 단위 테스트는 JDK 21 필요(Android Studio JBR 25에서 Robolectric 실패).
* 2026-11-13 단계 2 완료(커밋 전). 테스트 428 → 432개 모두 통과.
  - DB v5 마이그레이션 1번으로 끝(인덱스 4개, FSRS 칸, 단어 카드·맥락·노션 칸, 태그 종류, 문법 표 2개). 실제 앱 업그레이드를 에뮬레이터에서 확인.
  - FSRS-5 기본 가중치. 단기(같은 날) 공식은 쓰지 않음 — 선택 복습 반복이 간격을 부풀리지 않게.
  - 옛 카드·v4 백업은 안정도가 없어도 예전 간격·ease에서 이어받는다.
  - 설정: 기억 목표(85/90/95%), 하루 최대 복습(50/100/200). 세션 시작 때 한 번 읽어 버튼 아래 간격과 실제 일정이 같다.
  - 리뷰 지적 중 "새 카드 실패는 lapse 아님"은 반영하지 않음: 이 앱의 lapses는 "틀린 횟수"(자주 틀리는 문장에 쓴다).
  - 아직 쓰는 곳이 없는 칸(단어·맥락·노션·문법 표, 7일 예보)은 단계 3~6에서 쓴다.
* 2026-11-13 단계 3 완료. 테스트 432 → 433개(오래된 요약 화면 테스트 8개를 지우고 새 동작 테스트로 바꿈) 모두 통과.
  - 브랜드: 포도·민트·햇살·코랄 토큰(라이트/다크, surfaceContainer까지), 큰 숫자에 Fredoka(OFL, 159KB, 라이선스는 assets/licenses), 말풍선 모양.
  - 정보 구조: 오늘/번역/라이브러리/성장 + 오늘 탭 배지, 복습·설정은 덮어 여는 전체 화면.
  - 오늘 화면(히어로 말풍선, 연속, 목표 링, 7일 예보, 최근 문장), 복습을 대화형으로(받은·보낸 말풍선), 카드 넘김, 콤보, 목표 축하 색종이.
  - ReviewScreen 533줄 → 5개 파일(화면·문제·채점·요약·음성). 세션 시작 함수 3개를 하나로.
  - 번역 결과 말풍선·큰 저장 버튼, 검색 아이콘·지우기, 설정 스테퍼, 라이브러리 선택 모드 체크 의미·밀기 대체 동작, 결과 문구 liveRegion, 제목 heading.
  - 건너뜀: 상세 보기 모드, 저장 되돌리기, 라이브러리 머리 접기, 토스트/스낵바 통일.
  - 승인 대기: 시작 속도 — release 차가운 시작이 에뮬레이터에서 약 8초. WorkManager 자동 초기화 끄기(~1초, work-runtime 직접 의존 필요)와 베이스라인 프로필(새 모듈·라이브러리) 모두 승인이 필요해 보류.

