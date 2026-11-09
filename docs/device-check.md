# 실기기 확인 절차 (릴리스 빌드)

CI가 만든 **releaseCheck** APK를 폰에 설치해서 확인합니다.
이 빌드는 릴리스와 같은 R8(코드 축소·난독화) 설정이지만 앱 ID가 `com.chatlearn.rc`라서
**기존 앱(`com.chatlearn`)과 그 안의 데이터를 건드리지 않고 나란히 설치**됩니다.

> **절대 하지 말 것:** `adb uninstall com.chatlearn` — 기존 앱의 문장·기록이 사라집니다.
> 이 절차의 모든 명령은 `com.chatlearn.rc`만 대상으로 합니다.

## 1. APK 받기
1. GitHub > Actions > 가장 최근 성공한 "Android unit tests" 실행을 엽니다.
2. 아래쪽 Artifacts의 **releaseCheck-apk**를 내려받아 압축을 풉니다. (`*.apk`와 `mapping.txt`가 들어 있습니다.)
3. `mapping.txt`는 크래시 로그를 해독할 때 씁니다. 보관해 두세요.

## 2. 설치
```bash
adb devices
```
```bash
adb install -r app-releaseCheck.apk
```
런처에 "ChatLearn"이 하나 더 생깁니다. (바로가기와 홈 화면 위젯은 앱 ID가 달라 이 빌드에서는 제대로 동작하지 않습니다. 배포 앱에서는 문제없습니다.)

## 3. 크래시 보기
다른 터미널에서 켜 두고 시험합니다.
```bash
adb logcat -s AndroidRuntime GeminiAi
```
`ClassNotFoundException`, `NoSuchMethodError`, `SerializationException` 같은 것이 보이면 R8 설정 문제입니다. 그대로 알려 주세요.

## 4. 시험 목록
릴리스에서만 생길 수 있는 문제(R8)와 이번에 바꾼 동작을 확인합니다. 새 앱이라 설정과 데이터가 비어 있습니다.

| # | 할 일 | 기대 결과 | 무엇을 확인하나 |
|---|---|---|---|
| 1 | 앱 실행 | 설정 > AI 탭이 열리고 "처음 시작하기" 3단계가 보임 | 첫 실행 안내 |
| 2 | API 키 붙여넣고 "저장하고 연결 확인" | "연결됐어요" | 키 저장(암호화 저장소), Gemini 호출, JSON 직렬화 — R8 위험 큼 |
| 3 | 번역 탭에서 영어 문장 번역 후 저장 | 번역 결과, "저장했어요" | Room, 직렬화 |
| 4 | 라이브러리에 문장이 보임 | 저장한 문장 | Room 읽기 |
| 5 | 복습 탭에서 쓰기 번역 문제 제출 | 판정과 해설 | AI 채점 |
| 6 | 5번 제출 직후, AI가 응답하는 동안 "정답 보기" 누르기 | 바로 정답 화면으로 넘어감 | 이번에 바꾼 채점 취소 |
| 7 | 비행기 모드에서 번역 | "인터넷에 연결되어 있지 않아요" | 오류 문구 |
| 8 | 설정 > AI 탭 맨 아래 "최근 AI 오류" | 7번의 연결 오류가 보임. "복사" 동작 | 진단 기능 |
| 9 | 설정 > 백업 > 내보내기, 가져오기 | 파일이 만들어지고 가져오기 성공 | 백업 직렬화 — R8 위험 큼 |
| 10 | 앱을 완전히 나가기(홈으로) 후 아래 명령 | `snapshot.json` 파일이 있음 | 자동 백업 스냅샷 |
| 11 | 알림 켜고 시각을 가까운 시각으로 | 알림이 오고 탭하면 복습 탭 | 알림, 수신기 |
| 12 | 화면 회전, 뒤로가기 제스처 | 정상 동작, 앱이 뜻하지 않게 종료되지 않음 | targetSdk 36(예측 뒤로가기) |

10번 확인:
```bash
adb shell run-as com.chatlearn.rc ls -l files/auto-backup
```
(이 빌드는 debuggable이라 `run-as`가 됩니다.)

## 5. 자동 백업 복원 시험 (선택, 위험이 있음)
기기에서 백업·복원이 실제로 되는지는 구글 백업 설정에 따라 달라서 이 문서만으로는 보장할 수 없습니다.
시험한다면 **`.rc` 앱에서만** 하세요.
```bash
adb shell bmgr backupnow com.chatlearn.rc
```
```bash
adb shell dumpsys backup | head -40
```
복원 안내가 뜨는 것은 라이브러리가 빈 상태에서 스냅샷이 있을 때입니다(앱 데이터 삭제 후 다시 실행 등).
`adb shell pm clear com.chatlearn.rc`는 `.rc` 앱의 데이터만 지웁니다.

## 6. 결과 알리기
번호별로 통과/실패와, 실패면 `adb logcat` 출력을 알려 주세요.
