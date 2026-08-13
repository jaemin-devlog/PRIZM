# PRZ-011 Evidence — 문서 처리 진행 상태 UX

## 상태와 범위

- 상태: `VERIFIED`
- 검증 source commit: `fbb3481626a3cba6f36f070845ffae502511569e`
- 통합: [PR #41](https://github.com/jaemin-devlog/PRIZM/pull/41)을 통해 `main`에
  merge commit `e46d55f0c889bf570fa6fd796cb780b738ab75d7`로 병합
- 검증일: 2026-08-13 (Asia/Seoul)
- 승인 단계: `SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT 수정 → 재-AUDIT → INTEGRATE`
- VERIFY Gate: `PASS`
- AUDIT Gate: `PASS`
- INTEGRATE Gate: `PASS`

필수 VERIFY와 재-AUDIT에 blocking finding이 남지 않아 Registry를
`VERIFIED`로 올렸다. source commit을 push하고 PR #41의 필수 CI가 통과한 뒤
`main`에 병합했으며, 병합 commit의 CI와 OSS Readiness도 통과했다.

## 구현 근거

| 요구사항 | source·migration·test 근거 | 판정 |
|---|---|---|
| R1 자동 갱신·종료 상태 중지 | `frontend/src/App.tsx`의 2초 polling과 비종료 상태 판정, 실제 browser 전환 및 Nginx 요청 확인 | PASS |
| R2 실제 재시도 정보 | 기존 `retry_count`, `next_retry_at`, `IndexingRetryPolicy.MAX_RETRIES` API 응답과 DB 근거 browser countdown | PASS |
| R3 안전 실패 분류 | `ProcessingFailureCode`, `IndexingFailureClassifier`, coordinator·Ollama 단위 테스트, API에서 내부 `error_message` 비노출 | PASS |
| R4 실제 처리 단계 | V15, claim SQL, `ProcessingJobProgressService`, `DocumentIndexingProcessor`와 claim fencing test | PASS |
| R5 근거 있는 퍼센트 | `DocumentQueryService`의 nullable/실제 n/N 계산 test, 실제 API의 PENDING null → EMBEDDING 0/1 → COMPLETED 1/1·100 | PASS |
| R6 정상 완료 | PostgreSQL+pgvector Compose와 host Ollama `bge-m3`에서 합성 TXT가 ACTIVE/COMPLETED | PASS |
| R7 retry·ownership·fencing 보존 | 전체 unit/integration, owner+status+claim fenced 진행 UPDATE, 기존 completion/failure transaction 회귀 | PASS |
| R8 검색 회귀 없음 | 전체 integration과 실제 Career Evidence 검색에서 합성 marker 1건 반환 | PASS |

## migration·API·UI 결과

- forward-only `V15__add_processing_job_progress.sql`만 추가했다. 기존 migration은
  수정하지 않았다.
- `processing_jobs`에 nullable `progress_stage`, `completed_chunks`, `total_chunks`,
  `failure_code`와 allowlist/check 제약을 추가했다.
- 문서 요약과 버전 API에 단계, 완료/전체 청크 수, 실제 퍼센트, 안전 오류 코드,
  retry 횟수·최대 횟수·다음 시각을 추가했다. 내부 exception, stack trace,
  `error_message`는 응답하지 않는다.
- UI는 청크 수 미확정 시 spinner와 단계만, 확정 시 실제 n/N과 퍼센트,
  완료 시 100%, RETRY_WAIT 시 서버 시각 기반 countdown과 retry 횟수를 표시한다.
- UI는 status를 우선해 `COMPLETED`만 `완료 · 100%`로 표시한다.
  `FAILED` 100%는 `처리 실패`, `PROCESSING/SAVING` 100%는 `저장 중`이다.
- 임베딩 완료 수는 정수 퍼센트가 바뀌거나 최종 청크일 때만
  owner·state·claim-version fenced `REQUIRES_NEW` checkpoint로 저장한다.

## 자동 검증

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat test --no-daemon --rerun-tasks` | PASS — 464 tests, 15 skipped, 0 failures, 0 errors |
| `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | PASS — 112 tests, 7 skipped, 0 failures, 0 errors |
| `npm --prefix frontend run test:unit` | PASS — status 우선 표시 5 tests |
| `npm --prefix frontend run lint` | PASS |
| `npm --prefix frontend run build` | PASS — TypeScript와 Vite production build |
| `docker compose ... config --quiet` | PASS |
| `git diff --check` | PASS |
| [병합 후 CI](https://github.com/jaemin-devlog/PRIZM/actions/runs/31661636117) | PASS — merge commit `e46d55f` |
| [병합 후 OSS Readiness](https://github.com/jaemin-devlog/PRIZM/actions/runs/31661636156) | PASS — merge commit `e46d55f` |

전체 integration의 최초 재실행에서는 V15 추가 뒤에도 최신 migration을 V14로
가정한 assertion 3건과 저장소 `.env`의 frontend port가 test CORS 기본값을
덮어쓴 1건이 실패했다. V13→V15 순차 적용·V15 schema assertion으로 갱신하고
integration profile에 test origin을 명시한 뒤 관련 test와 전체 112개를 다시
실행해 실패 0건을 확인했다.

재-AUDIT 후 integration은 Windows 한글 경로를 피하려 임시 `R:` 드라이브
별칭을 사용했다. 첫 실행의 health test 1건은 별칭 드라이브가 여유 공간을
0 byte로 보고해 503이 되었다. 해당 환경성 disk-space indicator만 비활성화한
동일 전체 112개 재실행은 105 pass·7 skip·실패 0으로 통과했다.

7개 integration skip은 환경 조건형 테스트를 포함하며 OpenSQL opt-in target은
이번 작업에서 실행하지 않았다. 이번 PASS는 PostgreSQL+pgvector 결과이며 새 V15를
OpenSQL, OpenProxy, OpenHA 또는 DB failover 검증으로 확대하지 않는다.

## 실제 Compose·Ollama·browser 검증

- Docker Desktop Linux engine, PostgreSQL 16.14+pgvector, backend, frontend를
  기존 Compose 구조로 재빌드했다.
- backend 시작 로그에서 15개 migration 검증, V14→V15 단일 forward migration
  적용, health `UP`, frontend HTTP 200을 확인했다.
- 호스트 Ollama의 `bge-m3:latest`를 사용했다. 애플리케이션에서 GPU 설정을
  변경하거나 Docker 구조를 바꾸지 않았다.
- 합성 TXT 업로드의 API 관찰 순서:
  `PENDING(null) → PROCESSING/EMBEDDING 0/1·0% → COMPLETED/COMPLETED 1/1·100%`.
- 같은 합성 marker의 Career Evidence 검색은 owner-scoped `TEXT_CHUNK` 결과 1건을
  반환했다.
- browser 문서 카드에서 완료 `100%`를 확인했다. 실제 DB `next_retry_at`이 있는
  합성 RETRY_WAIT fixture는 `1분 36초 후 재시도 · 3회 중 2회 재시도`와
  `Ollama가 실행되지 않았거나 연결할 수 없어 임베딩을 만들 수 없습니다.`를
  표시했다. 내부 진단 문자열은 표시되지 않았다.
- 같은 job을 `COMPLETED`로 복원하자 페이지 새로고침 없이 약 2.95초 뒤
  `처리 완료 · 완료 100%`로 바뀌었다. 그 뒤 6초 동안 Nginx 로그에 추가
  `GET /api/documents` 요청이 없어 terminal polling 중지를 확인했다.

## 남은 경계

- Node 22 내장 test runner로 상태 우선 표시 unit test 5개를 추가해 실행했다.
- OpenSQL opt-in, OpenProxy, OpenHA, DB failover는 이번 요구사항과 검증 범위가
  아니므로 `NOT_RUN` 또는 기존 상태를 유지한다.
- PR #41 병합과 병합 후 필수 CI 확인까지 완료했다.

## AUDIT finding 수정과 재-AUDIT

최초 AUDIT에서 blocking 2건을 발견했다.

1. `progressPercent=100`을 status보다 우선해 `FAILED`·`SAVING`을 완료로
   표시할 수 있었다. status 우선 순수 표시 함수와 unit test로 수정했다.
2. 모든 청크에 `REQUIRES_NEW + UPDATE`가 발생했다. 정수 퍼센트 변경과
   최종 청크에만 checkpoint를 저장하도록 수정했다. 15,000 청크 단위
   test의 `updateCompletedChunks` 최대 횟수는 **100회**다. 최초 total 확정 1회,
   `SAVING` 단계 저장 1회와 완료 transaction은 별도이며, 청크 완료 수
   UPDATE 15,000회가 100회로 줄었다.

재-AUDIT에서 status 우선순위, 최종 청크 저장, owner·state·claim-version
fencing, `REQUIRES_NEW` 가시성, retry·검색/P18·ACTIVE version 보존 계약을
다시 확인했다. blocking finding은 0건이다.

## VERIFY Gate

필수 acceptance criterion에 source·자동 test·PostgreSQL+pgvector·Ollama·browser
결과가 있고 필수 test 실패가 없으므로 `PASS`다.

## AUDIT Gate

blocking finding 2건을 수정하고 전체 회귀 검증과 재-AUDIT에서 남은 blocking
finding 0건을 확인했으므로 `PASS`다.

## INTEGRATE Gate

source commit `fbb3481`을 PR #41로 `main`에 병합했고 merge commit `e46d55f`의
CI와 OSS Readiness가 모두 통과했다. Registry의 source commit과 검증일을 실제
통합 결과로 갱신했으므로 `PASS`다.
