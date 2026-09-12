# PRZ-038 Evidence

## 최종 판정

`JOB_FENCING_READY`

V18의 Search V3 indexing job에 claim, lease renew, recovery lock, exact-token reclaim, retry와 terminal
failure runtime을 연결했다. 실제 PostgreSQL에서 필수 26개 invariant를 6개 시나리오로 검증했고 concurrent
claim·recovery lock의 중복 소유는 모두 0이었다. 기존 Search V2 Worker는 수정하지 않았다.

## 기준과 범위

- 기준: `refactor/search-v3@f3bfab34d864f475b6ad3e3d79eeec7e94625fed`
- 시작 working tree: `CLEAN`
- branch: `PRZ-038-search-v3-job-fencing-runtime`
- Production Search V2 source 변경: `0`
- migration·dependency·frontend·MCP·Docker 변경: `0`
- Passage/Child 생성, embedding, exact inventory, 완료·activation: `NOT_IMPLEMENTED`

## 구현 구조

| 구성 | 책임 |
| --- | --- |
| `SearchV3IndexingJobClaim` | job·generation·owner·document·version·claim·attempt·lease를 한 Worker token으로 보존 |
| `SearchV3RecoveryLock` | 만료 claim, exact UUID token과 DB lock 시각 보존 |
| `SearchV3IndexingJobRepository` | V18 전용 claim·renew·lock·reclaim·retry·failure JDBC SQL |
| `SearchV3IndexingJobService` | 기존 1/5/15분 retry 정책, lease 설정, stale 예외와 error message 상한 적용 |

JPA entity나 범용 repository 계층은 추가하지 않았다. V18 composite identity를 모든 mutation의 SQL 조건에
직접 넣었고, DB `now()`를 claim·lease·recovery·retry·failure 시간의 기준으로 사용했다.

## 핵심 계약 결과

| 계약 | 상태 | 실제 근거 |
| --- | --- | --- |
| PENDING/due RETRY_WAIT claim | `PASS_POSTGRESQL` | 상태와 generation `BUILDING` 확인 뒤 claim·attempt 증가 |
| concurrent claim | `PASS_POSTGRESQL` | 열린 첫 transaction의 row lock 동안 두 번째 claimer는 empty, winner 1 |
| lease renew | `PASS_POSTGRESQL` | current·unexpired full identity만 갱신 |
| recovery lock | `PASS_POSTGRESQL` | expiry 전 0, expiry 후 exact token 1, concurrent loser 1 |
| reclaim | `PASS_POSTGRESQL` | exact identity·token·lock 시각만 성공, claim·attempt 증가 |
| claim version fencing | `PASS_POSTGRESQL` | reclaim 뒤 이전 renew·retry·failure·reclaim 모두 거부 |
| retry | `PASS_POSTGRESQL` | attempt 1/2/3 뒤 1/5/15분 RETRY_WAIT, due 전 claim 차단 |
| terminal failure | `PASS_POSTGRESQL` | attempt 4 또는 non-retryable에서 job과 generation 함께 FAILED |
| owner/document/version/generation fencing | `PASS_POSTGRESQL` | 네 identity를 바꾼 renew·retry·reclaim 모두 거부 |
| generation failure consistency | `PASS_POSTGRESQL` | terminal job-only failure 없이 한 statement에서 함께 전환 |
| completion/activation | `NOT_IMPLEMENTED` | PRZ-039 exact inventory·원자 활성화 범위 |

`attempt_count`는 실제 소유권을 받은 처리 시도 수로 고정했다. 최초 claim, due retry claim, reclaim에서만
증가하고 failure 전이 자체에서는 증가하지 않는다. 만료 lease는 renew할 수 없고 recovery lock이 기록된
직후부터 이전 Worker mutation을 차단한다.

## PostgreSQL 테스트

`SearchV3IndexingJobRuntimeTest`는 `pgvector/pgvector:0.8.2-pg16-bookworm`에서 `6/6 PASS`, failure·error·skip
`0`이었다. 6개 test method는 요청된 26개 invariant를 다음처럼 묶어 추적한다.

- PENDING·RETRY_WAIT claim, due 경계, counter와 full lineage
- transaction row lock을 유지한 concurrent claim winner 1 / loser 1
- current·stale·expired·non-PROCESSING lease renew
- recovery lock 전/후, concurrent token 경쟁, wrong/exact token reclaim
- retry budget과 terminal job/generation failure 정합성
- cross-owner·document·version·generation mutation 차단

## 회귀 검증

| 검증 | 결과 |
| --- | --- |
| PRZ-038 PostgreSQL runtime | `6/6 PASS`, failure·error·skip `0` |
| PRZ-037 shadow migration | `7/7 PASS`, failure·error·skip `0` |
| V1~V18 fresh migration | `9/9 PASS`, failure·error·skip `0` |
| 기존 V2 완료·lease·heartbeat·실패·복구 unit | `19/19 PASS` |
| PRZ-036 lifecycle·Child vector reuse | `28/28 PASS` |
| Search V3 dataset·SEALED integrity | `15/15 PASS` |
| 전체 backend `check` | unit `610`, integration `131`, failure·error `0`, skip `20`·`9` |
| OSS readiness | `PASS` |
| `git diff --check` | `PASS` |
| OpenSQL actual execution | `NOT_RUN` |

실행 명령:

```text
gradlew.bat integrationTest --tests ...SearchV3IndexingJobRuntimeTest --no-daemon --rerun-tasks
gradlew.bat test --tests ...IndexingCompletionOwnershipTest --tests ...ProcessingJobLeaseServiceTest --tests ...WorkerLeaseHeartbeatTest --tests ...IndexingFailureServiceTest --tests ...ProcessingJobRecoveryServiceTest --no-daemon --rerun-tasks
gradlew.bat searchEvaluation --tests ...SearchV3IndexLifecycleTest --tests ...SearchV3ChildEmbeddingReusePlannerTest --no-daemon --rerun-tasks
gradlew.bat searchEvaluation --tests ...SearchV3DenseAblationDatasetTest --tests ...SearchV3MinimalShadowIntegrityTest --no-daemon --rerun-tasks
gradlew.bat integrationTest --tests ...SearchV3ShadowStorageMigrationTest --tests ...CareerPlatformMigrationTest --no-daemon --rerun-tasks
gradlew.bat check --no-daemon --dependency-verification=strict --rerun-tasks
node scripts/verify-oss-readiness.mjs
git diff --check
```

## 남은 경계

- 실제 V3 Worker coordinator와 주기 heartbeat 연결은 `NOT_IMPLEMENTED`다. 이번 runtime의 renew API가 그
  fencing 경계다.
- frozen manifest와 실제 inventory exact equality, `BUILDING → READY`, `PROCESSING → COMPLETED`,
  `job → generation → version → document` 잠금과 active pointer 원자 전환은 PRZ-039 범위다.
- Search V3 query·API·cutover와 cleanup·retention은 구현하지 않았다.
- PostgreSQL 결과는 OpenSQL 근거가 아니다. `OPENSQL_VALIDATION=NOT_RUN`이다.

## SEALED FINAL

- combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- manifest SHA-256: `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`
- Git tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`

검색은 실행하지 않았고 metadata·manifest hash·Git tree 불변만 확인했다.
