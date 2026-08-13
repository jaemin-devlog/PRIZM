# PRZ-010 — 변경 로그 동기화

## 최종 실행 상태

`VERIFIED`

아래 상태·기준선 설명은 구현 시작 당시의 frozen specification snapshot이다. 최종 실행
결과는 [`tasks.md`](tasks.md)와 [`evidence.md`](evidence.md)를 따른다.

## 상태

`IN_PROGRESS` — SPEC·PLAN·TASKS Gate를 통과했고 P1 migration/domain, P2 upload
atomic record, P3 dispatch Transaction A, P4 Failure Recorder Transaction B, P5 Version/Delete Guard, P6 Frontend Guard와 P7 PostgreSQL Integration을 완료했다. P8 이후 구현과 제품 수직 테스트는
`NOT_RUN`이다.

기준 source: GitHub `main` commit
`5a8ea8d2b85e7d87342e11e96d1d58d1181ab6b8`

## 목적

문서 버전 생성 사실과 색인 작업 생성을 분리해, 문서 변경을 누락이나 중복 없이
기존 색인 파이프라인에 전달하는 최소 변경 로그 흐름을 만든다.

현재 `DocumentUploadService`는 문서 버전과 `ProcessingJob`을 같은 업로드
트랜잭션에서 직접 생성한다. 이 방식은 현재 색인 파이프라인에는 충분하지만,
문서 변경 사실과 색인 실행 작업을 구분하지 않으므로 변경을 독립적으로 재생하거나
후속 consumer에 전달할 영속 기준이 없다.

PRZ-010은 두 책임을 다음처럼 구분한다.

| 구성 요소 | 책임 |
|---|---|
| ChangeLog | PRIZM에서 문서 버전이 생성됐다는 변경 사실의 owner-scoped 영속 기록 |
| ProcessingJob | 한 문서 버전의 색인을 실제로 수행하기 위한 작업 큐 |

이번 범위는 `DOCUMENT_VERSION_CREATED`를 기존 `INDEXING` ProcessingJob으로 전달하는
consumer 하나만 구현한다. MCP나 다른 저장소 consumer는 구현하지 않는다.

## 사용자 시나리오

```text
V1 ACTIVE
  ↓
사용자가 기존 문서에 V2 업로드
  ↓
업로드 DB transaction
  ├─ DocumentVersion V2 = QUARANTINED
  └─ DOCUMENT_VERSION_CREATED ChangeLog = PENDING
  ↓ commit
ChangeLog Dispatcher
  ├─ INDEXING ProcessingJob 생성 또는 기존 작업 조회
  ├─ processing_job_id 연결
  └─ ChangeLog = DISPATCHED
  ↓
기존 Indexing Worker
  ↓
추출 → chunk → bge-m3 → vector 저장
  ↓
V2 ACTIVE + documents.active_version_id = V2
  ↓
검색은 V2 원문만 반환
```

새 버전의 ChangeLog 전달이나 색인이 끝나기 전에는 기존 V1이 검색 대상으로
유지된다. ChangeLog 전달 또는 V2 색인이 최종 실패해도 V1의
`active_version_id`를 변경하지 않는다.

## 현재 구현 기준선

다음 사실은 기준 source에서 확인했다.

- [`DocumentUploadService`](../../src/main/java/com/prizm/document/service/DocumentUploadService.java)는
  원본과 immutable `DocumentVersion`을 저장한 뒤
  `ProcessingJob.pendingIndexing()`을 직접 저장한다.
- 새 버전 허용 여부는 현재 version 상태가 아니라 연결된 ProcessingJob의
  비종료 상태만 확인한다.
- [`DocumentManagementService`](../../src/main/java/com/prizm/document/service/DocumentManagementService.java)도
  문서 삭제 가능 여부를 ProcessingJob의 비종료 상태로 판단한다.
- [`processing_jobs` migration](../../src/main/resources/db/migration/V4__create_processing_jobs.sql)은
  `(document_version_id, job_type)` 고유 제약을 둔다.
- [`V8 ownership migration`](../../src/main/resources/db/migration/V8__add_document_ownership.sql)은
  document·version·chunk·processing job의 owner를 복합 외래 키로 연결한다.
- [`ProcessingJobClaimRepository`](../../src/main/java/com/prizm/ingestion/repository/ProcessingJobClaimRepository.java)는
  `FOR UPDATE SKIP LOCKED`로 색인 작업을 선점한다.
- [`IndexingCompletionService`](../../src/main/java/com/prizm/ingestion/service/IndexingCompletionService.java)는
  chunk 교체, 버전 `ACTIVE`, `active_version_id` 교체와 ProcessingJob 완료를 한
  트랜잭션에서 확정한다.
- [`VectorSearchRepository`](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java)는
  owner가 일치하고 `active_version_id`가 가리키는 `ACTIVE` 버전만 검색한다.
- 업로드 원본은 DB와 같은 원자적 저장소에 있지 않다. DB rollback 시
  [`DocumentUploadService`](../../src/main/java/com/prizm/document/service/DocumentUploadService.java)의
  기존 보상 삭제와 `FileCleanupJob` 복구를 사용한다.

ChangeLog 테이블, entity, repository, dispatcher와 ChangeLog 전용 scheduler는 현재
없다.

## 범위

### 포함

- 신규 문서와 기존 문서의 새 버전을 포함해, 앞으로 생성되는 모든
  `DocumentVersion`에 `DOCUMENT_VERSION_CREATED` ChangeLog를 기록한다.
- `DocumentVersion` 메타데이터와 ChangeLog를 같은 DB 트랜잭션에서 기록한다.
- 업로드 서비스의 직접 ProcessingJob 생성 경로를 제거한다.
- ChangeLog Dispatcher를 신규 `INDEXING` ProcessingJob을 생성하는 유일한 운영
  진입점으로 사용한다.
- DB 고유 제약과 원자적 dispatch로 이벤트와 ProcessingJob 중복을 막는다.
- Dispatcher의 재시도, backoff와 최종 `FAILED` 상태를 기록한다.
- ProcessingJob 생성 전의 `QUARANTINED` 구간에도 추가 버전 업로드와 문서 삭제를
  차단한다.
- V2 성공 경로를 ChangeLog 생성부터 검색 결과까지 하나의 통합 시나리오로
  검증한다.
- PostgreSQL·pgvector와 실제 OpenSQL direct `5432` 결과를 별도로 검증·기록한다.

### 제외

- `METADATA_UPDATED`, `DOCUMENT_DELETED`와 그 밖의 event type
- MCP, webhook, CDC, Kafka와 범용 event bus
- ChangeLog용 공개 REST API와 관리자 재처리 API
- 여러 consumer의 독립 delivery/checkpoint 테이블
- 기존 document/version/ProcessingJob에 대한 소급 ChangeLog 생성
- 기존 색인 Worker의 lease, heartbeat, fencing, retry와 chunk 처리 방식 변경
- embedding model, chunker, vector 검색 순위와 검색 API 계약 변경
- `DocumentTextExtractor`, `TextChunker`, `DocumentIndexingProcessor`와 embedding
  서비스의 파싱·청킹·임베딩 동작 변경
- PRZ-008이 다루는 `src/main/java/com/prizm/search/**`, `src/searchEvaluation/**`,
  `src/test/resources/search-evaluation/**`와 검색 평가 문서·dataset 변경
- 영구 감사 원장, 삭제된 문서의 변경 이력 보존과 법적 audit log
- OpenProxy SQL routing, OpenHA와 DB failover

## ChangeLog 데이터 계약

ChangeLog 테이블의 논리 이름은 `document_change_logs`로 한다. 실제 구현은 최신
`main`의 마지막 migration을 다시 확인한 뒤 다음 forward-only Flyway migration을
추가한다. 기준 source의 마지막 migration은 V13이며 현재 다음 후보는 V14다.
기존 migration은 수정하지 않는다.

### 필수 필드

| 필드 | 계약 |
|---|---|
| `id` | DB가 생성하는 ChangeLog 식별자 |
| `owner_user_id` | version·document와 같은 소유자 |
| `document_version_id` | 생성된 immutable version |
| `event_type` | 이번 범위에서는 `DOCUMENT_VERSION_CREATED`만 허용 |
| `event_key` | `DOCUMENT_VERSION_CREATED:{documentVersionId}` 형식의 멱등 키 |
| `dispatch_status` | `PENDING`, `RETRY_WAIT`, `DISPATCHED`, `FAILED` 중 하나 |
| `processing_job_id` | dispatch 성공 후 연결된 `INDEXING` ProcessingJob, 그 전에는 `NULL` |
| `retry_count` | Failure Recorder가 commit한 retry 예약 횟수. 최초 dispatch 전에는 0이고 최대 3이다. commit되지 않은 실패는 시도 예산을 소비하지 않는다. |
| `next_retry_at` | `RETRY_WAIT`의 다음 처리 가능 시각 |
| `dispatched_at` | ProcessingJob 전달이 성공해 `DISPATCHED`가 된 시각 |
| `failed_at` | dispatch가 최종 `FAILED`가 된 시각 |
| `last_error_message` | 마지막 dispatch 실패의 안전하게 제한된 메시지 |
| `created_at` | 변경 사실이 DB에 기록된 시각 |

`DISPATCHED`는 ProcessingJob 생성·조회와 연결이 완료됐다는 뜻이다. 색인 성공을
뜻하지 않는다. `DISPATCHED` 이후의 색인 성공·재시도·최종 실패는 기존
ProcessingJob과 DocumentVersion 상태가 나타내며 ChangeLog dispatch 상태로
되돌려 표현하지 않는다.

### 무결성과 idempotency

- `event_key`는 unique다.
- `(document_version_id, event_type)`는 unique다.
- 기존 `processing_jobs(document_version_id, job_type)` unique를 최종 작업 중복
  방어선으로 유지한다.
- 새 migration은 `processing_jobs(id, owner_user_id, document_version_id)` unique key를
  추가한다. ChangeLog의 `(processing_job_id, owner_user_id, document_version_id)`는
  이 key를 참조하는 composite foreign key여야 한다.
- nullable `processing_job_id`에는 별도 unique 제약을 둔다. 따라서 `NULL`인
  미전달 ChangeLog는 여러 건 존재할 수 있지만, 전달된 하나의 ProcessingJob은
  최대 하나의 ChangeLog에만 연결된다.
- 따라서 ChangeLog가 다른 사용자의 Job이나 다른 version의 Job에 연결되는 것은 DB가
  거부한다. 서비스 계층도 같은 owner·version 검증을 유지한다.
- 하나의 ChangeLog는 최대 하나의 ProcessingJob에 연결되고, 하나의 ProcessingJob도
  최대 하나의 신규 ChangeLog에 연결된다.
- event identity와 변경 사실 필드는 생성 뒤 수정하지 않는다. dispatch 상태와
  오류·연결 필드만 상태 전이에 따라 변경한다.

애플리케이션의 사전 조회만으로 중복을 막지 않는다. 같은 이벤트 기록이나 dispatch가
동시에 실행되어도 DB unique와 원자적 트랜잭션이 최종 일관성을 보장해야 한다.

## 업로드와 파일 저장 트랜잭션 계약

`DocumentVersion` 메타데이터와 ChangeLog는 동일 DB 트랜잭션에 기록한다. 둘 중
하나라도 DB 저장에 실패하면 둘 다 rollback되어야 한다.

원본 TXT/PDF 저장은 해당 DB 트랜잭션과 원자적이지 않다. 기존 계약을 그대로
유지한다.

- 원본 저장 뒤 DB rollback이면 즉시 보상 삭제를 시도한다.
- 보상 삭제 실패는 기존 `FileCleanupJob`으로 복구한다.
- transaction 결과가 unknown이면 기존처럼 원본을 보존하고 reconciliation 필요를
  로그로 남긴다.
- ChangeLog 도입을 이유로 원본 파일 경로, hash, immutable version과 안전한 삭제
  계약을 약화하지 않는다.

업로드 트랜잭션은 ProcessingJob을 직접 생성하지 않는다. controller, upload
service, 별도 scheduler와 다른 운영 서비스도 신규 version의 `INDEXING`
ProcessingJob을 우회 생성하지 않는다. 새 version의 API 응답은 기존처럼
`QUARANTINED`이며, Dispatcher가 비동기로 ProcessingJob을 만든다. 기존 데이터와
테스트 fixture가 보유한 ProcessingJob은 이 진입점 계약의 예외이며 소급
ChangeLog를 만들지 않는다.

## Dispatcher 계약

Dispatcher는 외부 서비스나 파일 저장소를 호출하지 않고 하나의 ChangeLog를
ProcessingJob으로 전달하는 짧은 DB 트랜잭션만 수행한다. 원본 읽기, TXT/PDF
파싱, chunk 생성, Ollama 호출, embedding 검증과 vector 저장을 수행하거나 해당
서비스에 의존해서는 안 된다. 이 작업은 `DISPATCHED` 뒤 기존 Indexing Worker만
담당한다.

1. `PENDING` 또는 실행 시각이 지난 `RETRY_WAIT` 한 건을 생성 순서로 선택한다.
2. 여러 인스턴스가 같은 이벤트를 잡지 않도록 `FOR UPDATE SKIP LOCKED`를 사용한다.
3. 같은 owner·version의 `INDEXING` ProcessingJob을 생성한다.
4. unique 충돌로 작업이 이미 있으면 그 기존 작업을 조회해 재사용한다.
5. 같은 트랜잭션에서 `processing_job_id`를 연결하고 ChangeLog를 `DISPATCHED`로
   전환한다.

ProcessingJob 생성, ChangeLog 연결과 `DISPATCHED` 전환 중 하나라도 실패하면
transaction 전체를 rollback한다. 프로세스가 중간에 종료돼도 ChangeLog는 다시
처리 가능한 상태로 남고, 커밋된 고아 ProcessingJob이나 거짓 `DISPATCHED` 상태가
생기면 안 된다.

Dispatch Transaction A가 실패하거나 rollback-only가 되면, 그 transaction 안에서
ChangeLog를 `RETRY_WAIT`나 `FAILED`로 바꾸지 않는다. Scheduler는 예외를 바깥에서
잡은 뒤 별도의 짧은 Failure Recorder Transaction B를 사용한다. B는 ChangeLog를
다시 잠그고 다음 규칙으로 상태를 기록한다.

- 이미 다른 Dispatcher가 `DISPATCHED`로 전환했다면 상태를 되돌리지 않고 종료한다.
- 재시도 가능한 실패면 `retry_count`, `next_retry_at`, `last_error_message`를 갱신해
  `RETRY_WAIT`로 만든다.
- 영구 실패 또는 재시도 소진이면 ChangeLog를 `FAILED`로, 아직
  `QUARANTINED`인 version을 `FAILED`로 전환한다.
- DB 장애로 Transaction B도 시작·commit하지 못하면 마지막 커밋 상태를 유지하고
  다음 scheduler 실행에서 다시 시도한다.

짧은 단일 DB 트랜잭션이므로 PRZ-010 Dispatcher에는 별도 `PROCESSING` 상태,
lease, heartbeat와 claim fencing을 추가하지 않는다. 장시간 외부 작업에 대한 기존
Indexing Worker 보호 장치는 그대로 유지한다.

## ChangeLog 상태와 실패 계약

```text
PENDING ───────────────→ DISPATCHED
   │                         ▲
   ├─ 재시도 가능 실패 → RETRY_WAIT
   │                         │
   └─ 영구 실패 ───────→ FAILED

RETRY_WAIT ─ 성공 ─────→ DISPATCHED
RETRY_WAIT ─ 재실패 ───→ RETRY_WAIT 또는 FAILED
```

- dispatch 시도 예산은 Failure Recorder Transaction B가 DB에 commit한 실패만으로
  계산한다. B가 rollback되거나 commit하지 못한 A의 실패는 시도 예산을 소비하지
  않는다. 최초 dispatch 실패가 commit되면 `retry_count=1`과 1분 retry를 기록하고,
  두 번째·세 번째 commit된 실패는 각각 `retry_count=2`/5분,
  `retry_count=3`/15분 retry를 기록한다. 네 번째 commit된 실패는 추가 retry 없이
  `FAILED`로 확정한다. 즉 DB에 영속적으로 기록된 실패 기준 최초 1회와 재시도 3회,
  최대 네 번의 dispatch 실패다.
- DB transaction 자체가 rollback되면 ProcessingJob과 ChangeLog 상태 변경도 함께
  rollback되어야 한다.
- 지원하지 않는 event type, owner/version 불일치와 복구할 수 없는 무결성 오류는
  재시도하지 않고 최종 실패시킨다.
- ProcessingJob이 이미 존재하는 replay는 실패가 아니다. 기존 작업을 연결하고
  `DISPATCHED`로 종료한다.
- dispatch가 최종 실패하면 아직 `QUARANTINED`인 해당 version도 같은 트랜잭션에서
  `FAILED`로 바꿔 추가 version 등록과 안전한 삭제가 영구 차단되지 않게 한다.
- dispatch 실패는 기존 `active_version_id`를 변경하거나 기존 active chunk를
  삭제하지 않는다.
- `DISPATCHED` 뒤 발생한 retryable embedding·원본 읽기·파싱 오류는 ChangeLog를
  `RETRY_WAIT`로 되돌리지 않는다. 기존 ProcessingJob의 `RETRY_WAIT`, lease,
  recovery와 fencing 계약만 사용한다.

두 종류의 최종 실패는 반드시 구분한다.

| 실패 시점 | ChangeLog | ProcessingJob | 새 version | 기존 ACTIVE |
|---|---|---|---|---|
| Job 전달 전 dispatch 최종 실패 | `FAILED` | 생성되지 않음 | `QUARANTINED → FAILED` | 유지 |
| `DISPATCHED` 뒤 기존 색인 최종 실패 | `DISPATCHED` 유지 | `FAILED` | `PROCESSING → FAILED` | 유지 |

## 버전 경쟁과 문서 삭제 계약

ProcessingJob이 아직 생성되지 않은 구간에도 DocumentVersion이 문서 처리 상태의
1차 기준이다. 기존 데이터와 비정상 상태를 방어하기 위해 owner-scoped version의
상태 검사와 기존 ProcessingJob의 non-terminal 상태 검사를 함께 수행한다.

| 최신 version 상태 | 새 version 업로드 | 문서 삭제 |
|---|---|---|
| `QUARANTINED` | 금지 | 금지 |
| `PROCESSING` | 금지 | 금지 |
| `ACTIVE` | 허용 | 기존 terminal 삭제 절차에 따라 허용 |
| `FAILED` | 허용 | 기존 terminal 삭제 절차에 따라 허용 |

- 서버는 owner-scoped document row를 잠근 뒤 최신 version 상태를 먼저 검사하고,
  연결된 모든 기존 ProcessingJob의 `PENDING`·`RETRY_WAIT`·`PROCESSING`도 2차
  방어선으로 검사한다.
- 프론트엔드는 ProcessingJob 상태가 아직 없어도 version 상태가 `QUARANTINED` 또는
  `PROCESSING`이면 새 version 입력을 비활성화한다.
- API의 기존 `DOCUMENT_PROCESSING` 오류 계약을 유지한다.
- 처리 중 삭제 차단은 CleanupJob을 만들거나 원본을 삭제하기 전에 판정한다.

현재 문서 삭제는 metadata, version, chunk와 ProcessingJob을 hard-delete하고 원본
정리를 예약한다. 이번 범위의 ChangeLog도 terminal 문서 삭제 트랜잭션에서
ProcessingJob·version보다 먼저 제거한다. 삭제된 문서의 ChangeLog를 감사 이력으로
보존하지 않으며 `DOCUMENT_DELETED` 이벤트도 만들지 않는다.

## 기존 데이터와 배포 호환성

- migration은 기존 document, version, chunk와 ProcessingJob을 수정하거나 삭제하지
  않는다.
- migration 전에 생성된 version과 ProcessingJob을 위해 과거 ChangeLog를 소급
  생성하지 않는다.
- 기존 PENDING·RETRY_WAIT·PROCESSING ProcessingJob은 기존 Indexing Worker가 그대로
  처리한다.
- 새 코드가 배포된 뒤 생성되는 모든 version부터 ChangeLog 경로를 사용한다.
- migration 적용과 새 writer 전환 중 구버전과 신버전 애플리케이션이 동시에 신규
  업로드를 처리하는 rolling deployment는 허용하지 않는다. 배포 Plan은 구버전
  writer를 먼저 중지하거나 신규 업로드를 quiesce한 뒤 migration과 신버전 writer를
  전환하는 조건을 포함해야 한다.
- ChangeLog가 `PENDING`인 동안 Dispatcher가 중단돼도 새 version은 검색되지 않고
  기존 active version은 유지되는 fail-closed 동작을 한다.
- 새 migration 적용 뒤 애플리케이션을 구버전으로 되돌리면 새 PENDING ChangeLog가
  자동 dispatch되지 않을 수 있다. 구현 Plan은 단순 코드 rollback이 아니라
  Dispatcher 재배포 또는 검증된 roll-forward를 복구 전략으로 정의해야 한다.

## 다중 consumer 확장 경계

ChangeLog의 event identity는 향후 다른 consumer가 참조할 수 있지만, PRZ-010의
`dispatch_status`와 `processing_job_id`는 Indexing consumer 전달 상태만 나타낸다.

MCP나 다른 저장소 consumer를 추가할 때는 `change_log_id + consumer_type` 기준의
독립 delivery/checkpoint를 별도 Spec으로 설계해야 한다. PRZ-010의 단일
`DISPATCHED`를 모든 미래 consumer의 완료로 해석하지 않는다.

## 요구사항

| ID | 요구사항 |
|---|---|
| `PRZ-010-R1` | 앞으로 생성되는 모든 DocumentVersion은 같은 DB 트랜잭션에서 정확히 하나의 owner-scoped `DOCUMENT_VERSION_CREATED` ChangeLog를 가져야 한다. |
| `PRZ-010-R2` | 업로드의 직접 ProcessingJob 생성 경로를 제거하고 Dispatcher를 신규 `INDEXING` ProcessingJob 생성의 유일한 운영 진입점으로 사용해야 한다. |
| `PRZ-010-R3` | event와 ProcessingJob idempotency는 DB unique와 원자적 dispatch로 보장해야 한다. replay와 동시 Dispatcher는 작업을 중복 생성하지 않아야 한다. |
| `PRZ-010-R4` | Dispatcher는 짧은 `FOR UPDATE SKIP LOCKED` DB 트랜잭션에서 작업 생성·조회, 연결과 `DISPATCHED`를 함께 확정하며 파싱·청킹·임베딩·vector 저장을 수행하지 않아야 한다. |
| `PRZ-010-R5` | ProcessingJob이 아직 없어도 `QUARANTINED`·`PROCESSING` version은 추가 version 업로드와 문서 삭제를 차단해야 한다. |
| `PRZ-010-R6` | dispatch 재시도와 최종 실패를 기록하고 최종 실패 version을 `FAILED`로 만들되 기존 active version과 검색 결과를 보존해야 한다. |
| `PRZ-010-R7` | 기존 immutable version, 원본 hash, 파일 rollback compensation, owner 경계, 색인 lease·fencing과 atomic activation을 보존해야 한다. |
| `PRZ-010-R8` | migration은 forward-only이며 기존 데이터에 과거 ChangeLog를 조작해 넣지 않고 PostgreSQL과 OpenSQL 결과를 분리해야 한다. |
| `PRZ-010-R9` | V2 업로드부터 ChangeLog, ProcessingJob, 기존 색인, V2 ACTIVE와 V2 검색까지 하나의 통합 시나리오로 검증해야 한다. |
| `PRZ-010-R10` | PRZ-008과 병렬 작업할 수 있도록 검색 알고리즘·평가·dataset과 기존 파싱·청킹·임베딩 구현을 수정하지 않아야 한다. |
| `PRZ-010-R11` | Dispatch Transaction 실패는 별도 Failure Recorder Transaction으로만 `RETRY_WAIT` 또는 `FAILED`를 기록하고, `DISPATCHED` 이후 색인 실패는 기존 ProcessingJob만 기록해야 한다. |
| `PRZ-010-R12` | 배포 중 구버전과 신버전 writer가 동시에 신규 업로드를 처리하지 않아야 한다. |

## Acceptance criteria

### Schema와 migration

- 새 DB에 모든 migration을 적용하면 ChangeLog table, check·unique·owner/version/job
  외래 키와 dispatch claim index가 생성된다.
- 같은 `event_key` 또는 같은 `(document_version_id, event_type)`을 두 번 저장하면
  DB가 중복을 거부한다.
- `processing_jobs(id, owner_user_id, document_version_id)` unique와 ChangeLog의
  composite foreign key가 다른 owner나 다른 version의 ProcessingJob 연결을 DB에서
  거부한다.
- nullable `processing_job_id` unique 제약은 하나의 ProcessingJob을 둘 이상의
  ChangeLog에 연결하려는 시도를 DB에서 거부한다.
- V13까지 존재하던 fixture를 다음 migration으로 올려도 기존 version,
  ProcessingJob, chunk와 vector가 변하지 않고 소급 ChangeLog가 생기지 않는다.
- migration 재실행은 pending migration 0건으로 끝난다.

### 업로드와 경쟁 조건

- V1과 V2 업로드 각각에서 version과 ChangeLog는 함께 commit되며 업로드 서비스가
  ProcessingJob을 직접 저장하지 않는다.
- 운영 source에서 신규 `INDEXING` ProcessingJob을 생성하는 경로는 Dispatcher
  하나뿐이며 controller·upload service와 다른 scheduler가 우회 생성하지 않는다.
- version 또는 ChangeLog DB 저장이 실패하면 둘 다 남지 않는다. 원본이 이미
  저장됐다면 기존 rollback compensation이 실행된다.
- V2가 `QUARANTINED`이고 ChangeLog만 `PENDING`인 구간에서 V3 업로드와 문서 삭제는
  `DOCUMENT_PROCESSING`으로 거부된다.
- version이 `ACTIVE` 또는 `FAILED`여도 같은 owner 문서에 기존
  `PENDING`·`RETRY_WAIT`·`PROCESSING` ProcessingJob이 남아 있으면 V3 업로드와 문서
  삭제는 `DOCUMENT_PROCESSING`으로 거부된다.
- `ACTIVE` 또는 `FAILED`가 최신 version이면 새 version 등록이 가능하다.
- 다른 사용자는 document, version과 ChangeLog 존재 여부를 추론하거나 처리할 수
  없다.

### Dispatcher와 idempotency

- PENDING ChangeLog 한 건을 dispatch하면 정확히 하나의 PENDING INDEXING
  ProcessingJob과 연결되고 ChangeLog가 `DISPATCHED`가 된다.
- 동일 ChangeLog를 재실행하거나 동일 owner/version ProcessingJob이 이미 있어도
  기존 작업을 연결하며 작업 수는 증가하지 않는다.
- 두 Dispatcher가 동시에 실행해도 각 ChangeLog는 한 번만 commit되고 같은
  version의 ProcessingJob은 하나다.
- ProcessingJob 생성 뒤 ChangeLog 연결 전에 강제로 실패시키면 같은 transaction이
  rollback되어 고아 작업과 거짓 `DISPATCHED`가 남지 않는다.
- 아직 처리할 ChangeLog가 없으면 Dispatcher는 변경 없이 종료한다.
- Dispatcher 실행에서 FileStorage, parser, chunker, Ollama, EmbeddingService와
  vector 저장 호출이 0건이다.
- Dispatch Transaction A가 실패하면 Failure Recorder Transaction B가 별도로
  `RETRY_WAIT` 또는 `FAILED`를 기록한다. A의 rollback-only transaction에서 상태를
  기록하려는 시도는 없다.
- A가 rollback된 뒤 다른 Dispatcher가 같은 ChangeLog를 `DISPATCHED`로 만들면 B는
  상태를 회귀시키지 않는다.

### Retry와 실패

- Failure Recorder가 DB에 commit한 실패 기준으로 최초 1회와 재시도 3회, 최대 4회
  dispatch 실패가 1분·5분·15분 backoff로 정확히 기록된다. B가 commit하지 못한
  실패는 시도 예산을 소비하지 않는다.
- 성공한 retry는 기존 또는 새 ProcessingJob을 연결하고 ChangeLog를
  `DISPATCHED`로 만든다.
- 영구 실패 또는 retry 소진은 ChangeLog와 `QUARANTINED` version을 `FAILED`로
  만들고 기존 `active_version_id`를 보존한다.
- ChangeLog `DISPATCHED` 뒤 Indexing Worker가 실패하거나 재시도를 예약해도
  ChangeLog는 `DISPATCHED`를 유지하고 ProcessingJob·version만 색인 상태를
  나타낸다.

### 전체 수직 흐름

- 실제 PostgreSQL·pgvector와 Ollama `bge-m3` 환경에서 다음 흐름을 자동 검증한다.

```text
V1 ACTIVE
→ V2 업로드
→ V2 QUARANTINED + ChangeLog PENDING + ProcessingJob 0건
→ Dispatcher 실행
→ ChangeLog DISPATCHED + V2 ProcessingJob PENDING 1건
→ 기존 Indexing Worker 실행
→ V2 chunk/vector 저장 + V2 ACTIVE + active_version_id=V2
→ V2 고유 질의 검색
→ V2 결과만 반환, V1 결과 제외
```

- V2 dispatch 또는 indexing 실패 시 V1 검색 유지 시나리오도 통과한다.
- 두 사용자의 ChangeLog, ProcessingJob, version, chunk와 검색 결과가 격리된다.

### 환경과 회귀

- backend unit test와 전체 PostgreSQL integration test가 통과한다.
- frontend lint·typecheck·production build가 통과한다.
- PRZ-010 diff에 검색 알고리즘·평가·dataset, 기존 parser·chunker·embedding 구현
  변경이 0건이다.
- Docker Compose 구성 검사가 통과한다.
- 실제 OpenSQL direct `5432`에서 새 migration, unique, 외래 키,
  `FOR UPDATE SKIP LOCKED`, ProcessingJob upsert와 owner 격리를 별도로 검증한다.
- 배포 절차는 구버전 writer를 중지 또는 신규 업로드 quiesce한 뒤 migration과
  신버전 writer를 전환하는 조건을 충족한다.
- PostgreSQL 성공을 OpenSQL 성공으로 기록하지 않는다. 필수 OpenSQL 검증이
  `NOT_RUN`이면 PRZ-010은 `VERIFIED`가 아니라 `IMPLEMENTED_UNVERIFIED` 이하로 남긴다.
- 새 dependency가 없고 LICENSE·NOTICE·SBOM 배포 경계가 바뀌지 않는다.
- 문서 링크, 구현·미구현 상태, `git diff --check`와 민감정보 검사가 통과한다.

## 보존 계약

- document·version·ChangeLog·ProcessingJob·chunk와 검색 결과의 ownership을 모든
  read/write/claim 경로와 DB 관계에서 유지한다.
- `SYSTEM_ADMIN`은 개인 `USER` 문서나 ChangeLog를 우회 조회·처리하지 않는다.
- 미완성·실패 version은 검색 후보가 아니며 새 version 성공 전까지 이전 active를
  유지한다.
- immutable version, 원본 저장, SHA-256 hash, 12-value `DocumentType`, TXT/PDF
  제한과 TXT `TEXT_CHUNK`·PDF `PAGE` source 계약을 유지한다.
- embedding 1024차원·finite·non-zero norm 검증과 기존 검색 결과 수·점수 계약을
  변경하지 않는다.
- Indexing Worker의 lease·heartbeat·recovery·claim fencing과 완료 transaction을
  변경하지 않는다.
- 파일 rollback compensation, cleanup recovery와 `SecureDirectoryStream`
  fail-closed 삭제 계약을 유지한다.
- 적용된 Flyway migration을 수정하지 않고 기존 데이터와 OpenSQL 검증 경계를
  보존한다.

## SPEC Gate

- `DOCUMENT_VERSION_CREATED → ChangeLog → ProcessingJob → 기존 Indexing Pipeline`
  수직 슬라이스와 비범위가 모순되지 않는다.
- ChangeLog `DISPATCHED`와 실제 indexing 완료의 의미가 구분된다.
- event·job idempotency, owner 관계, upload/file transaction 경계, V3·삭제 경쟁,
  retry·최종 실패와 기존 active 보존을 실행 가능한 acceptance criteria로 판정할 수
  있다.
- 최대 4회 dispatch 시도, Dispatch Transaction A와 Failure Recorder Transaction B의
  분리, version·기존 Job 이중 방어와 구·신 writer 동시 배포 금지가 명시돼 있다.
- PostgreSQL·Ollama와 OpenSQL 필수 환경이 분리돼 있다.
- 구현·test·migration·dependency를 아직 변경하거나 실행하지 않았다.
