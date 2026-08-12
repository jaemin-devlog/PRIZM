# PRZ-010 — 변경 로그 동기화 Plan

## 상태와 기준선

`IN_PROGRESS` — SPEC Gate `PASSED` 뒤 P1~P7을 완료했다. P8 이후에는 source,
migration, 테스트를 변경하거나 실행하지 않았다.

- 원격 기준: `origin/main` `5a8ea8d2b85e7d87342e11e96d1d58d1181ab6b8`
- 계획 수립 시 `git fetch origin`으로 원격 기준을 재확인했다.
- 마지막 Flyway는 `V13__add_file_cleanup_worker_fields.sql`이다. 구현 직전에 다시
  fetch/rebase와 migration 목록 확인을 수행하며, 현재 신규 migration 후보는 V14다.
- PRZ-008 검색 알고리즘·검색 API·평가·dataset은 변경 대상이 아니다.

## 파일 수 통제

P1~P10 중 해당 단계에서 필요한 파일만 변경한다. 한 단계에서 목록 밖의 파일이
필요해지면 구현을 중단하고 Plan을 갱신한다.

| 범위 | 예상 최소 파일 |
|---|---|
| DB/domain | 신규 V14 후보 migration, `com.prizm.changelog` entity·enum·repository |
| upload/delete | `DocumentUploadService`, `DocumentManagementService`, `DocumentVersion`과 필요한 repository |
| dispatch | 신규 ChangeLog dispatcher/failure recorder/scheduler/config |
| UI | `frontend/src/App.tsx` 한 파일 |
| test | ChangeLog 전용 unit test와 전용 database integration test, 필요한 OpenSQL compatibility test |

변경 금지: `src/main/java/com/prizm/search/**`, `src/searchEvaluation/**`,
`src/test/resources/search-evaluation/**`, 검색 평가 문서·dataset,
`DocumentTextExtractor`, `TextChunker`, `DocumentIndexingProcessor`, embedding service,
기존 Indexing Worker lease/heartbeat/recovery/fencing, Flyway V1~V13, dependency,
LICENSE·NOTICE·SBOM.

## 단계 계획

| 단계 | 목표와 최소 범위 | Gate 검증 | rollback | 중단 조건 |
|---|---|---|---|---|
| P1. Migration + ChangeLog Domain | V14 후보로 `document_change_logs`, event/status check, event key·version/event unique, Job composite FK, nullable Job one-to-one unique와 claim index를 추가한다. ChangeLog entity/enum/repository만 만든다. | V13 fixture 보존, duplicate·owner/version/job mismatch와 하나의 Job의 다중 ChangeLog 연결 DB 거부, migration 재실행 | 적용 전 branch 폐기; 적용 후 forward-only roll-forward | FK/unique가 기존 data 또는 owner 경계를 깨뜨림 |
| P2. Upload → ChangeLog | upload transaction에서 DocumentVersion+`DOCUMENT_VERSION_CREATED`를 함께 저장하고 직접 `ProcessingJob.pendingIndexing()`을 제거한다. 파일 보상은 보존한다. | V1/V2가 QUARANTINED+PENDING, Job 0건; rollback/원본 보상 | code rollback 가능, 적용 migration은 유지 | version/ChangeLog 부분 commit 또는 직접 Job 생성 잔존 |
| P3. Dispatcher + Idempotency | `FOR UPDATE SKIP LOCKED` Transaction A에서 Job 생성/재조회, 연결, DISPATCHED를 확정한다. scheduler는 짧은 DB 작업만 한다. | 빈 queue, replay, concurrent claim, Job 1건, A rollback 고아 0건, 외부 호출 0건 | dispatcher disable 시 PENDING fail-closed; 불일치는 roll-forward | Job 중복·거짓 DISPATCHED·외부 파싱/임베딩 호출 |
| P4. Failure Recorder + Retry | A 밖 Transaction B에서 RETRY_WAIT/FAILED를 기록한다. B가 commit한 실패만 예산을 소비하며 1분→5분→15분, 최초 1회+재시도 3회다. | B non-regression, B DB 실패 시 last commit 유지, 네 번째 영속 실패 FAILED, V1 active 유지 | dispatcher disable 후 마지막 상태 보존 | rollback-only A에서 상태 기록, DISPATCHED 회귀, 기존 Job retry 변경 |
| P5. Version/Delete Guard | server에서 QUARANTINED/PROCESSING version 1차, non-terminal Job 2차 검사; terminal delete에서 ChangeLog를 먼저 삭제한다. | V2 PENDING/Job 0건 V3·delete 거부, owner isolation, cleanup 선행 없음 | dispatcher 포함 roll-forward 또는 upload quiesce | UI만 차단하거나 owner/cleanup 계약 위반 |
| P6. Frontend Guard | `App.tsx`에서 Job이 null이어도 QUARANTINED/PROCESSING version을 처리 중으로 표시한다. | 업로드·삭제 입력 비활성화, 기존 상태 표기·build | source rollback 가능; 서버 guard가 최종 방어 | backend guard 없이 UI만으로 완료 처리 |
| P7. PostgreSQL Integration | 전용 ChangeLog database integration test로 schema·dispatch·failure·owner 격리를 검증한다. | Testcontainers PostgreSQL, migration/동시 dispatch/실패 시나리오 | test 실패 시 통합하지 않음 | 실제 DB에서 idempotency·격리 실패 |
| P8. 실제 OpenSQL 검증 | 기존 OpenSQL compatibility suite만 최소 확장해 V14, FK/unique, SKIP LOCKED, Job 재사용을 direct `5432`에서 확인한다. | PostgreSQL 기준과 OpenSQL 결과를 별도 기록 | OpenSQL 미실행은 NOT_RUN | PostgreSQL 성공을 OpenSQL 성공으로 대체 |
| P9. V1 → V2 전체 E2E | P7 전용 test에서 Ollama bge-m3로 V1 ACTIVE→V2 upload→dispatch→기존 worker→V2 ACTIVE→V2 검색을 검증한다. | V2만 검색, dispatch/indexing 실패에도 V1 검색 유지 | 실패 시 통합하지 않음 | V2가 ACTIVE 전 검색되거나 V1 active 손실 |
| P10. Regression / Evidence | 전체 backend/frontend/Compose 회귀, diff/audit, evidence와 registry의 실제 결과 기록을 수행한다. | backend test, integrationTest, frontend lint/build, Compose, diff check | 실패 시 IMPLEMENT로 복귀, migration 수정 금지 | 필수 test 실패, NOT_RUN을 PASS/VERIFIED로 표기 |

## 트랜잭션과 책임 경계

- Transaction A: ChangeLog lock → Job 생성/조회 → owner/version 확인 → Job 연결 →
  `DISPATCHED`. 파싱·청킹·Ollama·embedding·vector 작업은 절대 수행하지 않는다.
- Transaction B: A의 실패를 scheduler 바깥에서 받아 retry/최종 실패만 기록한다.
  B도 실패하면 마지막 commit 상태를 유지한다.
- `DISPATCHED` 뒤 indexing 실패/재시도는 기존 `ProcessingJob`과 version만 기록하며
  ChangeLog 상태를 되돌리지 않는다.

## 검증과 배포

P10의 기본 회귀 명령: `.\gradlew.bat test --no-daemon`,
`.\gradlew.bat integrationTest --no-daemon --rerun-tasks`,
`npm --prefix frontend run lint`, `npm --prefix frontend run build`,
`docker compose config --quiet`, `git diff --check`.

OpenSQL direct `5432`와 Ollama bge-m3이 없으면 해당 항목은 `NOT_RUN`이다. PostgreSQL
성공은 OpenSQL 성공이 아니다.

배포는 구 writer를 중지하거나 신규 업로드를 quiesce한 뒤 migration과 새 uploader·dispatcher를
전환한다. 구·신 writer의 신규 업로드 동시 처리는 금지한다. 새 코드 rollback만으로
PENDING ChangeLog를 처리할 수 없으므로, 복구는 dispatcher 포함 검증된 roll-forward를
우선한다. Flyway clean/repair, 적용 migration 수정, 과거 ChangeLog backfill은 하지 않는다.

## PLAN Gate

- P1~P10이 PRZ-010-R1~R12와 acceptance criteria를 순서대로 연결한다.
- 각 단계에 검증·rollback·중단 조건이 있다.
- V13 기준 V14 후보를 확인했고 구현 직전 재확인을 명시했다.
- PRZ-008 검색·평가 영역을 제외했고 구현은 시작하지 않았다.
