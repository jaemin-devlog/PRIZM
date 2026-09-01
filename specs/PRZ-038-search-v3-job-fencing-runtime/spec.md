# PRZ-038 Search V3 Job Fencing Runtime

- 상태: `VERIFIED`
- 유형: Search V3 PostgreSQL Worker 소유권·복구 runtime
- branch: `PRZ-038-search-v3-job-fencing-runtime`
- 기준: `refactor/search-v3@f3bfab34d864f475b6ad3e3d79eeec7e94625fed`
- 선행 작업: PRZ-036 `SHADOW_INDEX_LIFECYCLE_READY`, PRZ-037 `SHADOW_STORAGE_READY`
- Production Search V2 적용: `NO_CHANGE`

## 목적과 범위

V18의 `search_v3_indexing_jobs`를 실제 PostgreSQL에서 안전하게 선점·갱신·복구한다. 이번 작업은
`claim → lease renew → retry/failure → recovery lock → reclaim`과 stale Worker 차단까지만 구현한다.
구조 분석, Passage/Child 생성, embedding, exact inventory, `READY`, 완료·활성화는 범위 밖이다.

기존 Search V2 Worker는 변경하지 않는다. Search V3는 V18 shadow table만 사용하는 별도 JDBC runtime으로
둔다.

## 기존 Search V2 Worker 감사

- 기존 claim은 JDBC의 `FOR UPDATE SKIP LOCKED`, DB `now()`, `claim_version`을 사용한다.
- 기존 완료·실패·복구는 `DocumentVersion` 중심 JPA aggregate와 `job → version → document` 잠금 순서를
  사용한다.
- Search V3는 같은 version에 여러 generation을 허용하므로 기존 `processing_jobs`나 claim DTO를 재사용할
  수 없다.
- V3의 모든 Worker mutation은 job뿐 아니라 owner·document·document version·generation·claim version을
  함께 확인해야 한다.

따라서 V3는 JPA entity를 미리 만들지 않고 V18 identity를 SQL 조건에 직접 묶는 전용 JDBC
repository/service를 사용한다. 기존 V2 component를 공통화하거나 리팩터링하지 않는다.

## Job identity

Worker claim은 다음 값을 모두 보존한다.

- job ID
- generation ID
- owner user ID
- document ID
- document version ID
- claim version
- attempt count
- lease expiry

Recovery lock은 위 identity와 lock token·lock 시각을 함께 보존한다. 후속 완료·활성화도 같은 full identity
predicate를 사용해야 하며 `jobId + claimVersion`만으로 완료할 수 없다.

## Claim 계약

- 대상은 `PENDING`과 `next_retry_at <= now()`인 `RETRY_WAIT`뿐이다.
- 연결된 generation은 `BUILDING`이어야 한다.
- 후보 선택과 `PROCESSING` 전환은 한 SQL에서 `FOR UPDATE SKIP LOCKED`로 수행한다.
- DB server time으로 `started_at`, `lease_expires_at`, `updated_at`을 계산한다.
- 성공하면 `claim_version`과 `attempt_count`를 각각 1 올리고 retry·failure metadata를 정리한다.
- 정상 lease의 `PROCESSING` job과 terminal job은 일반 claim 대상이 아니다.

`attempt_count`는 실제 Worker가 소유권을 받은 처리 시도 수다. 최초 claim, retry claim, recovery reclaim에서
증가하고, `RETRY_WAIT`이나 `FAILED`로 전환할 때는 증가하지 않는다.

## Lease와 heartbeat 계약

Lease renew는 full job identity, `PROCESSING`, 현재 claim version, recovery lock 없음과 만료 전 lease를 모두
확인한다. 만료됐거나 recovery lock이 걸린 claim은 갱신할 수 없다. 새 expiry는 DB server time으로 계산한다.
이번 PRZ는 renew runtime을 제공하지만 실제 Passage/Child Worker와 주기 scheduler 연결은 하지 않는다.

## Recovery lock과 reclaim 계약

Recovery는 만료된 `PROCESSING` job을 바로 reclaim하지 않는다.

```text
expired lease
→ recovery_lock_token과 recovery_locked_at 기록
→ exact full identity + token 검증
→ reclaim
```

Recovery lock 획득도 `FOR UPDATE SKIP LOCKED`를 사용해 동시 복구자 중 하나만 성공한다. lock이 기록되면 이전
Worker의 renew·retry·terminal failure를 즉시 거부한다. Reclaim은 exact token과 lock 시각까지 일치할 때만
claim version과 attempt count를 올리고 새 lease를 발급한 뒤 lock을 지운다.

## Retry와 failure 계약

- 기존 `IndexingRetryPolicy`의 최대 3회, 1분·5분·15분 간격을 재사용한다.
- 현재 attempt 1·2·3의 retryable failure는 각각 다음 retry를 예약한다.
- attempt 4 또는 non-retryable failure는 terminal `FAILED`로 전환한다.
- retry 시 generation은 `BUILDING`을 유지한다.
- terminal failure는 job과 generation을 한 PostgreSQL statement에서 함께 `FAILED`로 바꾼다. generation을
  `BUILDING`에 남기는 job-only failure는 허용하지 않는다.
- error message는 V18 상한인 2,000자로 제한한다.
- 모든 전이는 full identity, 현재 claim, `PROCESSING`, recovery lock 없음에 묶인다.

## Concurrency와 잠금

- claim과 recovery lock 후보는 가장 오래된 job부터 고르고 잠긴 행은 건너뛴다.
- concurrent claim/recovery 검증은 실제 PostgreSQL transaction을 열어 첫 번째 행 잠금을 유지한 상태에서
  두 번째 요청을 실행한다.
- terminal failure는 job을 먼저 잠근 뒤 같은 lineage의 generation을 갱신한다.
- 후속 PRZ-039 완료 경계는 `job → generation → version → document` 순서와 full claim fencing을 사용한다.

## PRZ-039에 남기는 경계

다음은 `NOT_IMPLEMENTED`로 남긴다.

- frozen manifest와 저장 inventory exact equality
- `BUILDING → READY`
- `PROCESSING → COMPLETED`
- 새/같은 version의 원자적 active pointer 전환
- Passage/Child/vector 생성과 저장
- 실제 Worker coordinator와 heartbeat scheduler 연결

## 보호 경계

- `document_chunks`, 기존 Production Search V2 Worker·검색 query·API·frontend·MCP는 수정하지 않는다.
- Flyway migration과 dependency를 추가하지 않는다.
- SEALED FINAL은 실행하지 않고 기존 metadata/hash만 확인한다.
- PostgreSQL 결과를 OpenSQL 근거로 사용하지 않는다. `OPENSQL_VALIDATION=NOT_RUN`을 유지한다.

## 판정 Gate

`JOB_FENCING_READY`는 실제 PostgreSQL에서 다음이 모두 통과할 때만 사용한다.

- claim과 concurrent duplicate claim 0
- 현재 claim lease renew와 stale/expired/recovery-locked 차단
- 만료 후 recovery lock, exact token reclaim와 claim version 증가
- stale retry/failure/recovery 차단
- cross-owner/document/version/generation mutation 0
- terminal job/generation failure 정합성
- 기존 Search V2와 V18 migration 회귀 없음
- SEALED 불변, blocking runtime finding 0

완료·activation이 PRZ-039 범위라는 이유만으로 이 Gate를 실패시키지는 않는다.
