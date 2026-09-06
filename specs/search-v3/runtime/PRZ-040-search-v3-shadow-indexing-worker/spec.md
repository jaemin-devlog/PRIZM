# PRZ-040 Search V3 Shadow Indexing Worker

- 상태: `SHADOW_INDEXING_WORKER_READY`
- 유형: Search V3 shadow 색인 Worker runtime
- branch: `PRZ-040-search-v3-shadow-indexing-worker`
- 기준: `refactor/search-v3@31500d449579937130f14e3608a07f625ffff28f`
- 선행 작업: PRZ-026 B3, PRZ-034 `CHILD_DENSE_V1`, PRZ-035 `PRECOMPUTE_CHILD_EMBEDDINGS`, PRZ-038 `JOB_FENCING_READY`, PRZ-039 `INVENTORY_ACTIVATION_READY`
- Production Search V2 적용: `NO_CHANGE`

## 목적과 범위

PRZ-038의 current claim으로 실제 `DocumentVersion` 원문을 읽고, 검증된 B3 구조의
`EvidenceChild`와 `RetrievalPassage`를 만든다. 두 단위의 BGE-M3 embedding을 미리 계산해 shadow
inventory에 저장하고, PRZ-039의 exact inventory 검증을 거쳐 `READY`와 같은-version shadow activation까지
수행한다.

Search V3는 계속 side-by-side shadow 경로다. Production Search V2 query·service·API, `document_chunks`,
`documents.active_version_id`, `document_versions.status`, frontend와 MCP는 변경하지 않는다. Search V3 query,
cutover, Child vector 재사용 최적화와 cleanup도 범위 밖이다.

## 구조와 원문 계보

Production shadow 구조 코드는 PRZ-026에서 검증한 다음 계약만 옮긴다.

- layout 신호로 `HEADING`, `PARAGRAPH`, `LIST_ITEM`, `TABLE_ROW`, `KEY_VALUE`, `OTHER`를 구분한다.
- `HEADING`은 경계와 provenance만 제공하며 독립 근거 후보가 아니다.
- `EvidenceChild.sourceText`는 실제 원문 span을 보존하고 global overlap을 만들지 않는다.
- B3 `RetrievalPassage`는 같은 owner·문서·version·page·structural parent 안의 인접 Child만 묶는다.
- Passage 목표·상한은 기존 B3의 `120 / 320 / 480` code point 계약을 유지한다.
- heading/Parent context, Parent Dense, reranker, Sparse와 query rewrite를 추가하지 않는다.

TXT는 전체 추출 text를 하나의 source unit으로 처리하고 `page_no`를 null로 저장한다. PDF는 text-layer의
원래 1-based page를 보존하며 page 사이 Passage grouping을 금지한다. logical key와 전체 order는 generation
범위에서 유일하고 결정적이어야 한다.

parser가 검증하는 source-unit hash와 generation 전체가 공유하는 추출 원문 hash를 분리한다. 후자는 format
version, file type과 순서가 있는 page number·UTF-8 byte length·text를 length-prefix canonicalization한
SHA-256이다. 원본 파일 byte hash인 `document_versions.content_hash`와 같다고 가정하지 않는다.

## Claim-first manifest 동결

V18은 generation 생성 시 expected manifest를 필수로 요구해 `claim → 원문 읽기 → 구조 생성 → manifest
동결` 순서와 충돌한다. 적용된 V18/V19는 수정하지 않고 additive V20으로 다음 상태만 허용한다.

- `UNFROZEN`: 세 expected manifest 필드가 모두 null이고 generation은 `BUILDING` 또는 `FAILED`
- `FROZEN`: count 두 개와 lowercase SHA-256이 모두 유효하며 모든 generation 상태에서 허용
- partial null과 manifest 없는 `READY`, `ACTIVE`, `SUPERSEDED`는 DB가 거부

current `PROCESSING` claim과 `BUILDING` generation을 full lineage로 잠근 뒤 artifact가 하나도 없을 때만 세
필드를 한 transaction에서 동결한다. 이미 같은 값이면 idempotent success, 다른 값·stale claim·recovery
lock이면 실패한다. expected manifest는 구조 생성 결과에서 DB insert 전에 만들며 persisted inventory로 다시
만드는 self-validation을 금지한다.

## Embedding 계약

- Passage input: `RetrievalPassage.retrievalText`
- Child input: `EvidenceChild.sourceText`
- model: BGE-M3
- dimension: `1024`
- 모든 Child vector는 PRZ-035 결정대로 색인 시 미리 계산
- model ID, 실제 resolved digest, dimension과 input policy가 generation/vector metadata에서 일치
- vector는 finite, non-zero norm이어야 함

기존 V2 `EmbeddingService` 계약은 바꾸지 않는다. Search V3 전용 model contract provider가 Ollama metadata의
model ID와 digest를 embedding 전후에 확인하며, 테스트용 deterministic embedder 결과를 실제 BGE-M3 실행
근거로 표현하지 않는다. 외부 관리자가 같은 tag를 색인 도중 두 번 바꿔 원래 digest로 되돌리는 적대적
TOCTOU까지 Ollama API로 원자 차단할 수 있다고 주장하지 않는다.

## Worker와 fencing

Worker는 `SearchV3IndexingJobClaim`의 job·generation·owner·문서·version·claim version을 유지한다. 원문 읽기
전후, 추출·구조 생성 후, Passage/Child embedding 중, 저장·READY·activation 전에 heartbeat 상태를 확인하고
lease를 갱신한다.

DB mutation은 heartbeat만 신뢰하지 않고 매번 full current claim을 다시 잠근다. reclaim 뒤 이전 Worker는
manifest 동결, artifact 저장, READY와 activation을 수행할 수 없다.

```text
claim
→ 원문 조회·읽기·추출
→ EvidenceChild·B3 RetrievalPassage
→ expected manifest 동결
→ Passage·Child embedding
→ claim-fenced inventory 전체 치환 저장
→ markReady
→ activateIfCurrentVersion
```

저장은 `job → generation` 잠금 뒤 기존 candidate inventory를 지우고 Passage → Child → 두 vector 계열을 한
transaction에서 넣는다. 일부 insert 실패는 delete까지 rollback한다. 재시도는 동결 manifest와 구조가 정확히
같을 때만 같은 generation을 전체 치환해 duplicate·mixed artifact를 남기지 않는다.

## READY와 activation 재개

같은 `DocumentVersion`이 Production `documents.active_version_id`일 때만 PRZ-039 activation을 수행한다.
다른 version 또는 null이면 generation은 `READY`에 남기고 job만 activation 전용 `RETRY_WAIT`으로 연기한다.
due retry로 다시 claim한 `READY` generation은 parsing·embedding·storage를 건너뛰고 activation만 재시도한다.
이 연기는 build failure retry budget을 소비하지 않는다.

이번 PRZ의 Worker 진입점은 수동 `processNext()`다. 자동 job 생성·dispatch scheduler와 recovery coordinator는
후속 운영 연결 범위이며, 구현되지 않은 자동 실행을 완료 상태로 표현하지 않는다.

실제 inventory/pointer invariant 위반은 `ACTIVATION` terminal failure로 기록한다. 같은-version 활성화 성공은
PRZ-039 transaction을 그대로 사용한다. `documents.active_version_id`와 `document_versions.status`는 읽기만 한다.

## 실패 분류

실패는 claim마다 다음 stage와 retryability를 보존한다.

- `PASSAGE_GENERATION`: 추출·구조 분석·Passage 생성
- `CHILD_GENERATION`: Child 생성 계약
- `PASSAGE_EMBEDDING`: Passage embedding
- `CHILD_EMBEDDING`: Child precompute
- `STORAGE`: 원문 storage 접근, manifest 동결, DB inventory 저장
- `ACTIVATION`: READY 검증·shadow activation

transient storage·Ollama·DB 장애는 기존 정책과 일치할 때만 retryable이다. malformed source, vector dimension,
manifest·lineage mismatch는 terminal이다. stale/reclaimed claim은 실패를 기록하지 않고 중단한다. terminal
failure는 PRZ-038의 job·generation 원자 실패 경계를 재사용해 기존 ACTIVE V3와 Production V2를 보존한다.

## 수용 기준

`SHADOW_INDEXING_WORKER_READY`는 실제 PostgreSQL/Testcontainers와 application component에서 다음이 모두
검증될 때만 사용한다.

- TXT와 지원 가능한 text-layer PDF에서 B3 Passage·Child 생성과 page-aware provenance PASS
- Passage·모든 Child embedding과 metadata PASS
- pre-insert manifest와 persisted inventory exact equality, `READY` PASS
- 같은-version shadow activation과 안전한 reindex PASS
- inactive/null active version은 READY까지만 유지하고 Production pointer 변경 0
- stale/reclaimed Worker의 저장·READY·activation 0
- 단계별 실패에서 기존 ACTIVE 보존, partial·duplicate·mixed artifact 0
- owner·문서·version·generation leakage 0
- Production Search V2와 PRZ-036~039 회귀 0
- SEALED FINAL 불변, blocking finding 0

실제 Ollama BGE-M3 또는 OpenSQL을 실행하지 않으면 각각 `NOT_RUN`으로 기록한다.
