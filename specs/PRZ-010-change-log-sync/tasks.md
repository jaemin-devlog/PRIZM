# PRZ-010 — 변경 로그 동기화 Tasks

현재 상태: `IN_PROGRESS`. P1~P7 구현과 전용 DB test는 완료했고, P8 이후 구현·테스트는
시작하지 않았다. 각 P 단계의 Gate를 통과하기 전에는 다음 P 단계로 진행하지 않는다.

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

- [ ] compatibility assertion과 기존 OpenSQL test만 필요한 범위로 갱신한다.
- [ ] 실제 direct `5432` 결과를 PostgreSQL 결과와 분리해 기록한다.

## P9. V1 → V2 전체 E2E

- [ ] Ollama bge-m3로 V1 ACTIVE → V2 PENDING ChangeLog → DISPATCHED Job → V2 ACTIVE를 검증한다.
- [ ] V2 고유 질의가 V2만 반환하고 dispatch/indexing 실패에는 V1을 유지함을 검증한다.

## P10. Regression / Evidence

- [ ] backend test, integrationTest, frontend lint/build, Compose config, `git diff --check`를 실행한다.
- [ ] PRZ-008 검색/평가/dataset 및 parser/chunker/embedding 구현 변경이 0건인지 감사한다.
- [ ] 실제 결과와 NOT_RUN 환경을 evidence·registry에 정직하게 기록한다.

## 공통 중단 규칙

- [ ] 신규 파일·dependency·공개 API·검색 관련 변경이 P 단계 경계를 넘으면 구현을 중단하고
  Plan을 갱신한다.
- [ ] 적용 migration 수정, Flyway clean/repair, 과거 ChangeLog backfill, 구·신 writer 동시
  신규 업로드는 수행하지 않는다.
