# PRZ-010 — 변경 로그 동기화 Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** `origin/main` `5a8ea8d2b85e7d87342e11e96d1d58d1181ab6b8`
>
> 이 문서는 구현 전에 선택한 접근과 단계별 계획을 보존한다. 실제 완료 결과는
> [Tasks](tasks.md)와 [Evidence](evidence.md)를 따른다.

계획 수립 당시 마지막 Flyway migration은
`V13__add_file_cleanup_worker_fields.sql`이었다. 구현 직전 원격 기준과 migration
번호를 다시 확인하고, 다음 미사용 번호에 forward-only migration을 추가하는
조건으로 계획했다. PRZ-008 검색 알고리즘·검색 API·평가·dataset은 변경 범위에서
제외했다.

## P1. Migration + ChangeLog Domain

- 목표: owner-scoped ChangeLog의 schema와 domain을 만든다.
- 변경 범위:
  - 다음 미사용 Flyway migration에 `document_change_logs`를 추가한다.
  - event·status check, event key·version/event unique, ProcessingJob composite FK,
    nullable Job one-to-one unique와 claim index를 추가한다.
  - `com.prizm.changelog`의 entity, enum과 repository만 추가한다.
- 검증:
  - V13 fixture가 보존되는지 확인한다.
  - 중복 event와 owner·version·job 불일치를 DB가 거부하는지 확인한다.
  - 하나의 Job을 여러 ChangeLog에 연결할 수 없는지 확인한다.
  - migration 재실행이 안전한지 확인한다.
- Rollback: 적용 전에는 변경을 폐기할 수 있다. 적용 뒤에는 migration을 수정하지
  않고 forward-only로 교정한다.
- 중단 조건: FK나 unique 제약이 기존 data 또는 owner 경계를 깨뜨리면 중단한다.

## P2. Upload → ChangeLog

- 목표: 업로드 트랜잭션이 version과 ChangeLog를 함께 기록하게 한다.
- 변경 범위:
  - `DocumentVersion`과 `DOCUMENT_VERSION_CREATED` ChangeLog를 같은 DB
    트랜잭션에서 저장한다.
  - 업로드 서비스의 직접 `ProcessingJob.pendingIndexing()` 저장을 제거한다.
  - 기존 파일 rollback compensation과 `FileCleanupJob` 복구는 유지한다.
- 검증:
  - V1과 V2가 `QUARANTINED` version과 `PENDING` ChangeLog로 저장되는지 확인한다.
  - 업로드 직후 ProcessingJob이 0건인지 확인한다.
  - DB rollback과 원본 파일 보상 계약을 확인한다.
- Rollback: 코드 rollback은 가능하지만 적용한 migration은 유지한다.
- 중단 조건: version과 ChangeLog가 부분 commit되거나 직접 Job 생성 경로가 남으면
  중단한다.

## P3. Dispatcher + Idempotency

- 목표: ChangeLog를 기존 `INDEXING` ProcessingJob으로 원자적으로 전달한다.
- 변경 범위:
  - Transaction A에서 `FOR UPDATE SKIP LOCKED`로 ChangeLog를 선점한다.
  - ProcessingJob을 생성하거나 기존 작업을 조회하고 owner·version을 확인한다.
  - Job 연결과 ChangeLog `DISPATCHED` 전환을 같은 트랜잭션에서 확정한다.
  - scheduler는 짧은 DB 작업만 수행한다.
- 검증:
  - 빈 queue, replay, concurrent claim과 기존 Job 재사용을 확인한다.
  - 같은 version의 Job이 한 건만 남는지 확인한다.
  - Transaction A rollback 뒤 고아 Job과 거짓 `DISPATCHED`가 없는지 확인한다.
  - 외부 파싱·임베딩 호출이 없는지 확인한다.
- Rollback: Dispatcher를 비활성화하면 ChangeLog는 `PENDING`으로 남아 fail-closed
  동작한다. 데이터 불일치는 forward-only로 교정한다.
- 중단 조건: Job 중복, 거짓 `DISPATCHED` 또는 외부 작업 호출이 생기면 중단한다.

## P4. Failure Recorder + Retry

- 목표: Transaction A의 실패를 별도 Transaction B에서 안전하게 기록한다.
- 변경 범위:
  - A 바깥에 Failure Recorder를 둔다.
  - B가 commit한 실패만 예산을 소비하게 한다.
  - `1분 → 5분 → 15분` backoff와 최종 `FAILED` 전환을 구현한다.
- 검증:
  - B가 `DISPATCHED`를 이전 상태로 되돌리지 않는지 확인한다.
  - B의 DB 실패 시 마지막 commit 상태가 유지되는지 확인한다.
  - 네 번째로 영속된 dispatch 실패가 최종 `FAILED`가 되는지 확인한다.
  - 이전 active version이 유지되는지 확인한다.
- Rollback: Dispatcher를 비활성화하고 마지막 commit 상태를 보존한다.
- 중단 조건: rollback-only A에서 상태를 기록하거나 기존 ProcessingJob의 retry
  계약을 바꾸면 중단한다.

## P5. Version/Delete Guard

- 목표: ProcessingJob 생성 전 구간에도 추가 업로드와 삭제를 차단한다.
- 변경 범위:
  - owner-scoped 최신 version의 `QUARANTINED`·`PROCESSING`을 1차로 검사한다.
  - 기존 non-terminal ProcessingJob 상태를 2차로 검사한다.
  - terminal delete에서는 ChangeLog를 ProcessingJob과 version보다 먼저 삭제한다.
- 검증:
  - V2 `PENDING`·Job 0건 구간에서 V3 업로드와 삭제가 거부되는지 확인한다.
  - owner isolation과 cleanup 이전 차단을 확인한다.
- Rollback: Dispatcher를 포함해 roll-forward하거나 업로드를 일시 중지한다.
- 중단 조건: UI만 차단하거나 owner·cleanup 계약을 위반하면 중단한다.

## P6. Frontend Guard

- 목표: ProcessingJob이 아직 없어도 처리 중 version을 UI에 정확히 표시한다.
- 변경 범위: `frontend/src/App.tsx`에서 `QUARANTINED`·`PROCESSING` version을
  in-flight로 계산한다.
- 검증: 업로드·삭제 입력 비활성화, 기존 상태 표시와 production build를 확인한다.
- Rollback: frontend source는 되돌릴 수 있으며 서버 guard를 최종 방어선으로
  유지한다.
- 중단 조건: backend guard 없이 UI만으로 처리 완료를 판단하면 중단한다.

## P7. PostgreSQL Integration

- 목표: 실제 PostgreSQL에서 schema와 dispatch 계약을 검증한다.
- 변경 범위: ChangeLog 전용 database integration test를 추가한다.
- 검증: Testcontainers PostgreSQL에서 migration, concurrent dispatch, replay,
  failure와 owner isolation을 확인한다.
- Rollback: 통합 검증에 실패하면 다음 환경 단계로 진행하지 않는다.
- 중단 조건: 실제 DB에서 idempotency나 owner 격리가 깨지면 중단한다.

## P8. 실제 OpenSQL 검증

- 목표: PostgreSQL 결과와 분리해 OpenSQL SQL 호환성을 확인한다.
- 변경 범위: 기존 OpenSQL compatibility suite에 V14, FK·unique,
  `SKIP LOCKED`와 Job 재사용 검증만 추가한다.
- 검증: OpenSQL direct `5432`에서 migration과 SQL 계약을 확인하고 결과를
  PostgreSQL과 별도로 기록한다.
- Rollback: OpenSQL을 실행하지 못하면 `NOT_RUN`으로 남긴다.
- 중단 조건: PostgreSQL 성공을 OpenSQL 성공으로 대체하면 중단한다.

## P9. V1 → V2 전체 E2E

- 목표: 새 ChangeLog 경로가 기존 색인·검색 파이프라인까지 이어지는지 확인한다.
- 변경 범위: 실제 OpenSQL과 Ollama `bge-m3` 환경에서 V1부터 V2까지의 수직 흐름을
  검증한다.
- 검증:
  - V1 `ACTIVE` 뒤 V2 업로드·dispatch·기존 Worker·V2 `ACTIVE` 전환을 확인한다.
  - 검색에서 V2만 반환되는지 확인한다.
  - dispatch 또는 indexing 최종 실패에도 V1 검색이 유지되는지 확인한다.
- Rollback: 수직 검증에 실패하면 통합하지 않는다.
- 중단 조건: V2가 `ACTIVE` 전 검색되거나 V1 active가 손실되면 중단한다.

## P10. Regression / Evidence

- 목표: 전체 회귀와 문서·diff 감사를 마치고 실제 결과를 기록한다.
- 변경 범위:
  - backend·frontend·Compose 회귀와 정적 검사를 수행한다.
  - Evidence, Tasks, Registry와 상태 문서를 실제 결과에 맞춘다.
- 검증:
  - backend test와 integration test를 실행한다.
  - frontend lint와 build를 실행한다.
  - Compose 구성과 `git diff --check`를 확인한다.
  - PRZ-008, parser, chunker, embedding, dependency와 license 변경이 없는지 감사한다.
- Rollback: 실패하면 IMPLEMENT 단계로 돌아가되 적용 migration은 수정하지 않는다.
- 중단 조건: 필수 test가 실패하거나 `NOT_RUN`을 `PASS`·`VERIFIED`로 기록하면
  중단한다.

## 공통 위험과 대응

- Transaction A는 ChangeLog lock, Job 생성·조회, owner·version 확인, Job 연결과
  `DISPATCHED`만 수행한다. 파싱·청킹·Ollama·embedding·vector 작업은 하지 않는다.
- Transaction B는 A의 실패 뒤 retry 또는 최종 실패만 기록한다. B도 실패하면
  마지막 commit 상태를 유지한다.
- `DISPATCHED` 이후 indexing 실패와 재시도는 기존 ProcessingJob과 version이
  나타내며 ChangeLog 상태를 되돌리지 않는다.
- 구 writer와 신 writer가 신규 업로드를 동시에 처리하지 않도록 배포 중 업로드를
  일시 중지하거나 구 writer를 먼저 중지한다.
- 적용 뒤 단순 코드 rollback으로 `PENDING` ChangeLog를 처리할 수 없으므로,
  Dispatcher를 포함한 검증된 roll-forward를 우선한다.

## Dependency 및 license 영향

- 새 dependency는 추가하지 않는다.
- LICENSE·NOTICE·SBOM 경계는 바꾸지 않는다.
- Flyway V1–V13과 적용된 migration은 수정하지 않는다.

## Branch와 통합 경계

- 단계마다 필요한 최소 파일만 변경한다. 범위를 넘는 파일이 필요하면 구현을
  중단하고 계획을 갱신한다.
- `src/main/java/com/prizm/search/**`, `src/searchEvaluation/**`,
  `src/test/resources/search-evaluation/**`, 검색 평가 문서·dataset,
  `DocumentTextExtractor`, `TextChunker`, `DocumentIndexingProcessor`, embedding
  service와 기존 Worker의 lease·heartbeat·recovery·fencing은 변경하지 않는다.
- Flyway clean·repair, 적용 migration 수정과 과거 ChangeLog backfill은 하지 않는다.

## 계획 대비 주요 변경

- 계획 당시 후보였던 V14를 `V14__create_document_change_logs.sql`로 사용했다.
- 최종 검증 source는 `26c546b16eb9ea42d98460dd6e5aa0bf0752212a`이며,
  실제 결과와 제한은 [Evidence](evidence.md)에만 상세히 기록한다.
