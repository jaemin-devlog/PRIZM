# PRZ-010 — 변경 로그 동기화 Tasks

현재 상태: `VERIFIED`. P1~P10 Gate를 순서대로 통과했다. PostgreSQL 검증, 실제 OpenSQL
direct `5432` SQL Gate, 실제 OpenSQL+Ollama `bge-m3` V1→V2 E2E와 P10-A 회귀 결과는
[`evidence.md`](evidence.md)에 기록한다.

## P1. Migration + ChangeLog Domain

- [x] 구현 직전 origin/main과 마지막 Flyway 번호를 재확인했다. V14는 사용 가능했고
  번호가 사용됐으면 다음 미사용 번호를 쓴다.
- [x] 새 migration에 ChangeLog table, check/unique/composite FK, nullable Job one-to-one
  unique, claim index를 추가한다.
- [x] ChangeLog entity, event/status enum, owner-scoped repository와 SKIP LOCKED claim을 추가했다.
- [x] migration·idempotency·owner/version/job DB constraint test를 추가하고 통과했다.

## P2. Upload → ChangeLog

- [x] `DocumentUploadService`에서 직접 ProcessingJob 생성 경로를 제거하고 기존
  in-flight 검사에 필요한 주입은 보존했다.
- [x] version과 ChangeLog를 동일 DB transaction에서 저장한다.
- [x] upload unit/database test를 Job 0건·동시 rollback·기존 파일 보상 계약으로 갱신하고 통과했다.

## P3. Dispatcher + Idempotency

- [x] ChangeLog dispatcher Transaction A와 최소 scheduler/config를 추가했다. integration
  profile에서는 scheduler를 비활성화하고 transaction bean을 명시 호출한다.
- [x] `ON CONFLICT DO NOTHING` Job 확보 뒤 owner/version을 검증하고 ChangeLog 연결·
  DISPATCHED를 한 transaction으로 구현했다.
- [x] 빈 queue·기존 Job 재사용·동시 dispatcher·A rollback·외부 의존성 부재 test를 추가하고 통과했다.

## P4. Failure Recorder + Retry

- [x] A 밖 별도 Transaction B Failure Recorder를 추가한다.
- [x] Failure Recorder가 DB에 commit한 실패만 소비하는 retry_count 0~3과
  1분·5분·15분 backoff를 구현한다.
- [x] dispatch 최종 실패의 ChangeLog FAILED + QUARANTINED version FAILED와
  DISPATCHED non-regression test를 추가한다.

## P5. Version/Delete Guard

- [x] 최신 `DocumentVersion`의 QUARANTINED/PROCESSING 상태를 1차 처리 중 guard로 검증한다.
- [x] upload/delete에서 version 1차 + non-terminal Job 2차 owner-scoped guard를 구현한다.
- [x] delete의 ChangeLog → ProcessingJob → version 삭제 순서와 관련 test를 추가한다.

## P6. Frontend Guard

- [x] `frontend/src/App.tsx`에서 QUARANTINED/PROCESSING version도 in-flight로 계산한다.
- [x] lint/build로 기존 UI 계약을 확인한다.

## P7. PostgreSQL Integration

- [x] 전용 `ChangeLogSyncDatabaseIntegrationTest`를 추가한다.
- [x] PostgreSQL에서 schema, dispatch, replay, 경쟁, 실패, owner 격리를 검증한다.

## P8. 실제 OpenSQL 검증

- [x] compatibility assertion과 기존 OpenSQL test를 필요한 범위로만 갱신했다.
- [x] 실제 OpenSQL direct `5432`에서 V1~V14, V14 schema/제약, SKIP LOCKED,
  ON CONFLICT idempotency, owner isolation과 기존 data 보존을 PostgreSQL과 분리해 통과했다.

## P9. V1 → V2 전체 E2E

- [x] 실제 OpenSQL direct `5432`와 Ollama `bge-m3`로 V1 ACTIVE → V2 QUARANTINED
  ChangeLog → DISPATCHED Job → V2 ACTIVE를 검증했다.
- [x] V2 고유 질의가 V2만 반환하고 dispatch/indexing 최종 실패에는 V1 ACTIVE·검색을
  유지함을 검증했다.

## P10. Regression / Evidence

- [x] P10-A에서 backend test, integrationTest, frontend lint/build, Compose config와
  `git diff --check`를 실행했다. integrationTest는 `104 completed, 7 skipped, 0 failures`다.
- [x] 기존 통합 테스트의 V14 ChangeLog → Dispatcher → ProcessingJob 계약, FK cleanup
  순서와 Flyway V14 기대값만 정정했고 제품 코드는 변경하지 않았다.
- [x] PRZ-008 검색/평가/dataset 및 parser/chunker/embedding 구현 변경이 0건임을 감사했다.
- [x] P10-B에서 실제 결과와 남은 제한을 Evidence·registry·project status에 기록했다.

## 공통 중단 규칙

- [ ] 신규 파일·dependency·공개 API·검색 관련 변경이 P 단계 경계를 넘으면 구현을 중단하고
  Plan을 갱신한다.
- [ ] 적용 migration 수정, Flyway clean/repair, 과거 ChangeLog backfill, 구·신 writer 동시
  신규 업로드는 수행하지 않는다.
