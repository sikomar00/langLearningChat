# 복습 방식 다양화와 언어 확장 설계

작성일: 2026-11-01
기반: `2026-10-31-chat-sentence-learning-app-design.md` (MVP). 이 문서는 MVP 위에 더하는 변경만 다룬다.

## 1. 목적

저장한 문장을 (1) 영어↔스페인어로 확장해서 두 언어 모두 직접 써 보며 배우고, (2) 쓰기 번역 외에 빈칸 채우기와 듣고 받아쓰기로 복습하게 한다.

### 성공 기준
- 영어 문장을 저장하면 스페인어 카드가, 스페인어 문장을 저장하면 영어 카드가 함께 만들어져 각각 독립적으로 복습 일정이 관리된다.
- 이미 저장된 문장도 라이브러리에서 한 번에 확장할 수 있다.
- 복습할 때 카드 상태에 따라 방식이 자동으로 정해지고, 빈칸 채우기와 받아쓰기는 AI 호출 없이 채점된다.
- 기존 사용자 데이터(문장, 태그, 복습 기록)는 DB 업그레이드 후에도 그대로 남는다.

### 결정된 사항 (사용자 선택)
- 확장 시점: 저장할 때 자동 + 기존 문장은 일괄 확장. 설정에서 자동 확장을 끌 수 있다.
- 이번에 추가하는 방식: 빈칸 채우기, 듣고 받아쓰기. (객관식, 따라 말하기는 제외)
- 방식 선택: 카드 상태에 따라 자동.

### 근거와 한계
- 직접 떠올려 쓰는 연습이 다시 읽기보다 낫다는 근거는 강하다. 간격 두기와 섞어 풀기도 외국어 단어 학습에서 근거가 있다.
- 빈칸 채우기의 직접 비교 연구는 찾지 못했고(대부분 앱 블로그), 받아쓰기는 주로 측정 도구로 쓰인 연구가 있다. 그래서 두 방식은 보조 수단이며, 방식 선택 비율은 처음 값이고 사용하면서 조정한다.

## 2. 범위

### 포함
- DB v1 → v2 마이그레이션
- 언어 확장(저장 시 자동, 일괄, 문장별 수동, 확장 문장 수정)
- 복습 방식 3종(쓰기 번역, 빈칸 채우기, 듣고 받아쓰기)과 자동 선택 정책
- 응답 시간 기록, 방식별 정답률 통계
- 태그의 마지막 사용 시각(자동완성 순서 개선)

### 제외
- 객관식, 따라 말하기(음성 인식), FSRS 전환
- 방식별로 따로 관리되는 복습 일정

## 3. 데이터와 마이그레이션 (DB v1 → v2)

변경 (Room `Migration(1, 2)`, 파괴적 마이그레이션 금지):
1. `review_card`: 유일 인덱스를 `entryId` 단독에서 `(entryId, answerLang)` 쌍으로 바꾼다.
   - `DROP INDEX index_review_card_entryId` 후 `CREATE UNIQUE INDEX index_review_card_entryId_answerLang ON review_card(entryId, answerLang)`.
2. `tag`: `lastUsedAt INTEGER NOT NULL DEFAULT 0` 추가.
   - 기존 값은 `UPDATE tag SET lastUsedAt = COALESCE((SELECT MAX(e.createdAt) FROM entry e JOIN entry_tag et ON et.entryId = e.id WHERE et.tagId = tag.id), 0)`로 채운다.
   - 문장 저장과 `setTags`에서 쓰이는 태그의 `lastUsedAt`을 현재 시각으로 갱신한다.
   - 자동완성 순서는 `lastUsedAt DESC, id DESC`.
3. `review_log`: `responseMs INTEGER`(널 허용)와 `exercise TEXT NOT NULL DEFAULT 'TRANSLATE'` 추가.
4. `AppDatabase.version = 2`, `exportSchema = true`로 바꾸고 스키마 파일을 저장소에 넣는다.
5. 백업 JSON `version`을 2로 올린다. v1 파일도 읽을 수 있어야 하며, 없는 필드는 기본값(`lastUsedAt = 0`, `responseMs = null`, `exercise = TRANSLATE`)으로 채운다. 현재 `Backup.CURRENT_VERSION` 검사는 `> 2`를 거부한다.

검증: v1 스키마로 DB를 만들어 문장 2개, 태그, 복습 기록을 넣고 v2로 올린 뒤 모든 행과 값이 그대로이며 새 컬럼 기본값이 맞는지 확인하는 마이그레이션 테스트를 먼저 쓴다.

## 4. 언어 확장

### 4.1 AI 응답
- `ReceivedResult`에 `altText: String`(채팅 언어의 반대 언어 번역)을 추가한다. 스키마의 필수 필드로 요청한다.
- `SentCandidate`에도 `altText`를 추가한다.
- 한 호출에 같이 받아오므로 추가 호출은 없다.
- 새 호출 `translateBatch(items: List<String>, from: Lang, to: Lang): Result<List<String>>`를 추가한다(일괄 확장용). 응답 개수가 요청과 다르면 `BAD_FORMAT`.

### 4.2 저장
- `EntryDraft`에 `altText: String?`을 추가한다.
- 자동 확장이 켜져 있고 `altText`가 비어 있지 않으면, 문장 저장 시 `EntryText(lang = 반대 언어, origin = AI)`를 넣고 `ReviewCard(answerLang = 반대 언어)`를 하나 더 만든다. 채팅 언어 카드는 그대로 만든다.
- 중복 검사는 지금처럼 채팅 언어 원문 기준이다.
- 설정에 "저장할 때 다른 언어도 만들기"(기본 켜짐)를 추가한다. 꺼져 있으면 카드는 하나만 만든다.

### 4.3 일괄 확장과 수동 확장
- 라이브러리의 "전체 확장"은 확장 카드가 없는 문장을 10개씩 묶어 `translateBatch`로 번역하고 텍스트와 카드를 만든다. 한 묶음이 실패하면 건너뛰고 나머지를 계속하며, 결과로 성공/실패 개수를 보여 준다. 이미 확장된 문장은 다시 만들지 않는다.
- 문장 상세에서 "확장 카드 만들기"(확장 카드가 없을 때)와 확장 문장 수정(`origin = USER`로 바뀜)을 지원한다. 확장 문장에는 AI 번역 표시를 붙인다.

### 4.4 복습 준비
- `ReviewSession.prepare`는 카드의 `answerLang` 텍스트를 정답으로, 한국어 또는 반대 언어 텍스트를 출제문으로 쓴다. 반대 언어 텍스트가 있으면(확장된 문장은 항상 있다) 지금처럼 일정 확률(30%)로 출제문에 쓴다. 확장되지 않은 문장에서 반대 언어 텍스트를 즉석 번역하던 기존 동작은 유지한다.
- 하루 새 카드 한도는 확장 카드를 포함해 센다.

## 5. 복습 방식

### 5.1 종류와 선택 정책
`enum ExerciseKind { TRANSLATE, CLOZE, DICTATION }`

`ExercisePolicy.choose(card: ReviewCardEntity, canBlank: Boolean, ttsAvailable: Boolean, roll: Double): ExerciseKind` (순수 함수)

| 카드 상태 | 방식 |
|---|---|
| 한 번도 복습 안 한 카드, 또는 `reps == 0`(다시를 받은 직후) | CLOZE |
| `reps` 1~2 | CLOZE 50%, TRANSLATE 50% |
| `reps` 3 이상이고 `intervalDays < 21` | TRANSLATE 60%, DICTATION 40% |
| `intervalDays >= 21` | TRANSLATE 50%, DICTATION 50% |

예외 처리:
- `canBlank == false`(빈칸으로 만들 단어가 없음)이면 CLOZE 대신 TRANSLATE.
- `ttsAvailable == false`이면 DICTATION 대신 TRANSLATE.

### 5.2 빈칸 채우기
- `ClozeBuilder.build(sentence: String, seed: Long): Cloze?` (순수 함수). 정답 언어 문장을 단어 단위로 나눠 길이 4자 이상이고 흔한 기능어 목록(영어/스페인어)에 없는 단어를 후보로 한다. 후보가 없으면 `null`.
- 단어 수가 8개 이상이면 2개, 아니면 1개를 비운다. 같은 `seed`면 같은 결과(카드 id를 시드로 쓴다).
- 한국어 뜻을 힌트로 함께 보여 준다.
- 채점은 앱 안에서 한다: 정규화(소문자, 앞뒤 구두점 제거) 후 완전 일치면 CORRECT. 악센트만 다르거나 편집 거리 1이면 CLOSE. 그 외 WRONG.

### 5.3 듣고 받아쓰기
- `Tts` 인터페이스(`isAvailable(lang)`, `speak(text, lang, slow)`, `stop()`)를 두고 Android `TextToSpeech` 구현과 테스트용 가짜를 만든다. 해당 언어 음성이 없으면 `isAvailable == false`.
- 화면: 다시 듣기, 느리게 듣기(말속도 0.7), 입력창. 제출 전에는 한국어 뜻을 가린다.
- 채점은 앱 안에서 단어 단위로 한다: 정규화(소문자, 구두점 제거) 후 단어 순서 비교. 모두 같으면 CORRECT. 단어 오류가 전체의 15% 이하이거나 악센트 차이만 있으면 CLOSE. 그 외 WRONG. 틀린 단어는 표시한다.

### 5.4 공통
- 세 방식 모두 결과는 지금처럼 `GradeOutcome`으로 표현하고, 기본 평가는 CORRECT→GOOD, CLOSE→HARD, WRONG→AGAIN이며 사용자가 바꿀 수 있다. 일정 계산(`Scheduler`)은 바꾸지 않는다.
- TRANSLATE만 AI로 채점한다. CLOZE와 DICTATION은 AI를 호출하지 않으므로 AI 한도에서 자유롭다. AI가 실패해도 TRANSLATE는 지금처럼 셀프 채점으로 전환한다.
- `ReviewLogEntity`에 `exercise`와 `responseMs`(문제를 보여 준 시점부터 제출까지)를 기록한다.

## 6. 통계 변경
- 방식별 정답률(TRANSLATE, CLOZE, DICTATION)을 통계 화면에 추가한다. 로그의 `exercise`로 계산한다.
- 평균 응답 시간(`responseMs`가 있는 기록만)을 표시한다.

## 7. 오류와 예외
- `translateBatch`나 응답의 `altText`가 비어 있으면 확장 카드를 만들지 않는다(문장 저장은 정상).
- 문장, 텍스트, 카드는 한 트랜잭션에서 저장한다. DB 오류가 나면 전체가 롤백되어 문장이 반쯤 저장되는 일은 없다. `altText`가 없거나 비어 있는 것은 오류가 아니며 이 경우 원문 카드만 만든다.
- 마이그레이션 실패 시 앱이 데이터를 지우지 않는다. 실패는 크래시로 드러나고 기존 DB 파일은 그대로 보존된다.

## 8. 테스트
- 마이그레이션 테스트: v1 DB → v2에서 데이터 보존과 새 기본값 확인, 카드 유일 조건 변경 확인(같은 문장에 서로 다른 `answerLang` 카드 2장 허용, 같은 `answerLang` 중복 거부).
- 순수 함수: `ExercisePolicy`(경계 포함), `ClozeBuilder`(후보 없음, 결정성, 8단어 경계), 빈칸과 받아쓰기 채점기(악센트, 구두점, 편집 거리, 15% 경계).
- 저장소: 자동 확장 on/off 저장, 일괄 확장(성공, 일부 실패, 이미 확장됨), 태그 `lastUsedAt` 갱신과 정렬.
- `ReviewSession`: 방식 선택에 따른 준비와 채점, 가짜 `Tts`, 응답 시간 기록.
- Gemini 구현: `altText`, `translateBatch` 파싱과 개수 불일치.
- 백업: v1 파일 읽기, v2 왕복.
- 폰에서 직접 확인: TTS 재생, 빈칸/받아쓰기 화면, 전체 확장 진행 표시.

## 9. 위험
- 확장 번역의 품질(AI). 표시와 수정으로 완화.
- 복습량 증가. 하루 새 카드 한도로 조절.
- 빈칸 단어 선택과 기능어 목록이 단순하다. 쓰면서 조정한다.
- 폰에 스페인어나 영어 TTS 음성이 없으면 받아쓰기가 나오지 않는다.
