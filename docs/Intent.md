# Intent — 현재 작업

계획: `docs/superpowers/plans/2026-11-13-second-overhaul.md`

## 단계 1. 안정성·성능 (2026-11-13 시작)

1. **문제**: 복습 중 삭제된 문장을 평가하면 앱이 꺼질 수 있고, 미리 복습하면 간격이 부풀려지며, 첫 실행 때 키를 저장하면 탭이 튄다. 라이브러리·통계가 같은 전체 조회를 여러 번 메인 스레드에서 돌리고, AI 호출은 취소·재시도·오류 구분이 약하다. 출시용 빌드가 줄여지지 않는다.
2. **기대 결과**: 위 버그가 테스트로 재현된 뒤 고쳐지고, 화면은 그대로이면서 조회·계산이 줄고, AI 실패가 정확한 문구로 보이며, release 빌드가 R8로 줄어든다. 단위 테스트 407개 + 새 테스트 모두 통과.
3. **영향 범위**: `domain/Scheduler.kt`, `review/ReviewSession.kt`, `ui/review/ReviewViewModel.kt`, `MainActivity.kt`, `ui/library/*`, `data/EntryRepository.kt`, `stats/*`, `widget/*`, `reminder/*`, `ai/GeminiAiService.kt`, `AppContainer.kt`, `app/build.gradle.kts`.
4. **제약**: 화면 모양은 바꾸지 않는다(디자인은 단계 3). DB 스키마 변경은 단계 2에서 한 번에. 라이브러리 추가·버전 올리기는 승인 후.
5. **열린 질문**: 의존성 버전 올리기 승인 여부. 생각 예산·모델 고정(1-15)은 공식 모델 목록 확인 뒤 다시.

**상태(2026-11-13)**: 완료, 커밋 대기. 미리 복습은 "기한 전이면 지난 시간만 인정"으로 고쳤다(FSRS는 단계 2). 테스트 428개 통과, QA는 `docs/qa-guide.md`.
