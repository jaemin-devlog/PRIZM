# PRZ-010 — 변경 로그 동기화 Tasks

> **현재 상태:** `VERIFIED`
>
> P1–P10 Gate를 순서대로 통과했다. PostgreSQL, OpenSQL direct `5432`, 실제
> OpenSQL·Ollama `bge-m3` E2E와 전체 회귀 결과는 [Evidence](evidence.md)에 있다.

## P1. Migration + ChangeLog Domain

- [x] 구현 직전 원격 기준과 마지막 Flyway 번호를 재확인했다.
- [x] V14 migration에 ChangeLog table, check·unique·composite FK, nullable Job
  one-to-one unique와 claim index를 추가했다.
- [x] ChangeLog entity, event·status enum과 owner-scoped repository를 추가했다.
- [x] migration, idempotency와 owner·version·job DB 제약 검증을 통과했다.

## P2. Upload → ChangeLog

- [x] `DocumentUploadService`의 직접 ProcessingJob 생성 경로를 제거했다.
- [x] version과 ChangeLog를 같은 DB transaction에서 저장했다.
- [x] Job 0건, 동시 rollback과 기존 파일 보상 계약 검증을 통과했다.

## P3. Dispatcher + Idempotency

- [x] ChangeLog Dispatcher Transaction A와 최소 scheduler·config를 추가했다.
- [x] `ON CONFLICT DO NOTHING`으로 Job을 확보한 뒤 owner·version을 확인하고,
  ChangeLog 연결과 `DISPATCHED`를 한 transaction에서 확정했다.
- [x] 빈 queue, 기존 Job 재사용, 동시 Dispatcher, A rollback과 외부 의존성 부재
  검증을 통과했다.

## P4. Failure Recorder + Retry

- [x] Transaction A 밖에 별도 Transaction B Failure Recorder를 추가했다.
- [x] B가 commit한 실패만 소비하는 `retry_count` `0–3`과
  `1분 → 5분 → 15분` backoff를 구현했다.
- [x] dispatch 최종 실패, `QUARANTINED` version 실패와 `DISPATCHED`
  non-regression 검증을 통과했다.

## P5. Version/Delete Guard

- [x] 최신 `DocumentVersion`의 `QUARANTINED`·`PROCESSING` 상태를 1차 guard로
  검사했다.
- [x] upload와 delete에 owner-scoped version 1차, non-terminal Job 2차 guard를
  구현했다.
- [x] delete의 ChangeLog → ProcessingJob → version 삭제 순서와 관련 검증을
  통과했다.

## P6. Frontend Guard

- [x] `frontend/src/App.tsx`가 `QUARANTINED`·`PROCESSING` version도 in-flight로
  계산하도록 수정했다.
- [x] lint와 build로 기존 UI 계약을 확인했다.

## P7. PostgreSQL Integration

- [x] 전용 `ChangeLogSyncDatabaseIntegrationTest`를 추가했다.
- [x] PostgreSQL에서 schema, dispatch, replay, 경쟁, 실패와 owner 격리를
  검증했다.

## P8. 실제 OpenSQL 검증

- [x] 필요한 범위에서 compatibility assertion과 기존 OpenSQL test를 갱신했다.
- [x] 실제 OpenSQL direct `5432`에서 V1–V14, V14 schema·제약, `SKIP LOCKED`,
  `ON CONFLICT` idempotency, owner isolation과 기존 data 보존을 확인했다.

## P9. V1 → V2 전체 E2E

- [x] 실제 OpenSQL direct `5432`와 Ollama `bge-m3`로 V1 `ACTIVE` → V2
  `QUARANTINED` → ChangeLog → ProcessingJob → V2 `ACTIVE` 흐름을 검증했다.
- [x] V2 고유 질의가 V2만 반환하는지 확인했다.
- [x] dispatch와 indexing 최종 실패에도 V1 `ACTIVE`와 검색이 유지되는지
  확인했다.

## P10. Regression / Evidence

- [x] backend test와 integration test를 실행했다.
- [x] frontend lint·build와 Compose 구성을 검증했다.
- [x] 기존 통합 테스트를 V14 ChangeLog → Dispatcher → ProcessingJob 계약과 FK
  cleanup 순서에 맞췄다.
- [x] PRZ-008 검색·평가·dataset과 parser·chunker·embedding 구현 변경이 0건인지
  감사했다.
- [x] 실제 결과와 남은 제한을 Evidence, Registry와 상태 문서에 기록했다.

## 후속 또는 제외 범위

- [ ] MCP와 ChangeLog 다중 consumer별 delivery·checkpoint는 별도 후속 Spec에서
  다룬다.
- [ ] OpenProxy SQL routing·인증, OpenHA, DB failover와 영구 journal은 후속
  범위로 남긴다.
- [x] 적용 migration 수정, Flyway clean·repair와 과거 ChangeLog backfill은 하지
  않았다.
- [x] 새 dependency, 공개 API와 PRZ-008 검색 영역 변경은 하지 않았다.
