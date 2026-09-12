# PRZ-041 Search V3 Runtime Completion

- 상태: `VERIFIED`
- 유형: Search V3 shadow runtime 연결
- branch: `PRZ-041-search-v3-runtime-completion`
- 기준: `refactor/search-v3@ded1adb4002e904eb4b5652db556faa9a0d6f8a2`
- 선행 작업: PRZ-034 `CHILD_DENSE_V1`, PRZ-035 `PRECOMPUTE_CHILD_EMBEDDINGS`, PRZ-038~040 runtime
- Production Search V2 적용: `NO_CHANGE`

## 목적과 범위

PRZ-040의 수동 shadow indexing Worker를 자동 dispatch·recovery에 연결하고, 활성화된 Search V3 generation을
실제 shadow DB에서 조회하는 application runtime을 구현한다.

```text
active DocumentVersion 감지와 V3 job 생성
→ claim·heartbeat·expired lease recovery
→ PRZ-040 indexing·READY·같은-version shadow activation
→ query embedding
→ ACTIVE+COMPLETED Passage exact cosine Top20
→ Top5 Passage 내부 CHILD_DENSE_V1
→ 최대 5개 EvidenceChild 원문 근거
```

Production Search V2 query·API·`document_chunks`, `documents.active_version_id`, frontend와 MCP는 변경하지
않는다. Search V3 API 공개와 V2 cutover도 범위 밖이다.

## 자동 dispatch와 recovery 계약

자동 dispatch는 Production에서 이미 `ACTIVE`인 `DocumentVersion`만 대상으로 한다. document row를
`FOR UPDATE SKIP LOCKED`로 잠그고 현재 structure·Passage·Child·embedding 계약과 같은 generation이 없는
경우에만 `BUILDING` generation과 `PENDING` job을 한 transaction에서 만든다. 같은 문서·version·계약의
중복 generation은 만들지 않는다.

Search V3 scheduler는 V2 scheduler를 교체하지 않고 side-by-side로 둔다. 기본 활성화 여부는 별도
`prizm.search-v3.worker-enabled` 설정으로 통제한다. 일반 claim은 PRZ-038의 `claimNext()`를 그대로 사용한다.
만료 lease는 exact recovery token을 얻어 reclaim한 뒤 새 claim을 즉시 동일 processor 경로로 넘긴다.
reclaim만 하고 소유자 없는 `PROCESSING` job을 남기지 않는다.

## 검색 가능 generation 계약

한 Passage는 다음 조건을 모두 만족할 때만 검색 후보가 된다.

- 인증된 owner가 document·generation·job·Passage·vector의 owner와 일치
- `documents.active_search_v3_generation_id = generation.id`
- `generation.status = ACTIVE`
- `job.status = COMPLETED`
- `generation.document_version_id = documents.active_version_id`
- generation·Passage vector의 model ID, resolved digest, dimension과 input policy가 현재 BGE-M3 계약과 일치

`BUILDING`, `READY`, `FAILED`, `SUPERSEDED`와 다른 owner의 artifact는 결과가 0건이어야 한다.

## Passage와 Child 선택

Passage는 저장된 `search_v3_passage_embeddings`에 query vector를 exact cosine으로 비교해 Top20을 구한다.
동점은 distance, `passage_order`, Passage ID 순으로 결정한다. query vector는 한 번 만들고 Passage와 Child
선택에 같이 사용한다.

PRZ-034 `CHILD_DENSE_V1`을 그대로 적용한다.

- Dense Top5 Passage만 Child 선택 대상
- Passage 순서와 membership은 변경하지 않음
- 같은 Passage 안에서만 저장된 Child vector cosine 내림차순으로 정렬
- 동점은 `passage_child_order`, Child ID 순
- Child 입력은 색인 시 저장한 `EvidenceChild.sourceText` vector
- 최대 5개 원문 EvidenceChild 반환
- 문서·version·page·line·code-point provenance를 그대로 보존

## 정확 조건 검증 경계

PRZ-028/029의 deterministic typed parser·sourceText observation·three-state validation과 근거 선택 의미를
바꾸지 않는다. parser가 지원하는 constraint를 하나 이상 만들었을 때만 typed 경로를 적용하며, parser가
비어 있으면 `UNASSESSED`로 두고 `CHILD_DENSE_V1` 결과를 그대로 반환한다. `FOUND/PARTIAL/NONE`은 원문
조건 검증 상태이지 사용자의 실제 경력 유무나 진위 판정이 아니다.

## 실제 BGE-M3 smoke

SEALED 데이터가 아닌 synthetic TXT 또는 text-layer PDF로 실제 로컬 Ollama `bge-m3`를 호출한다. model ID,
resolved digest, 1024차원, Passage·Child 저장 vector와 query vector의 동일 계약, 실제 shadow query 1건 이상을
확인한다. 실행할 수 없으면 `REAL_BGE_M3=NOT_RUN`이며 `SEARCH_V3_RUNTIME_READY`로 판정하지 않는다.

## 비범위

- Production Search API의 V3 교체와 Search V2 cutover
- Sparse, BM25/FTS, RRF, Cross Encoder, Qwen, Parent Dense/Context, QueryPlanner, rewrite, MMR
- Grounded Answer, Child vector 재사용 최적화, cleanup·retention
- `documents.active_version_id` 변경과 SEALED FINAL 검색

## 수용 기준

`SEARCH_V3_RUNTIME_READY`는 다음이 현재 branch에서 모두 검증될 때만 사용한다.

- 자동 dispatch·중복 claim 0·heartbeat·expired claim recovery PASS
- TXT와 text-layer PDF indexing E2E PASS
- exact inventory, READY와 같은-version activation PASS
- ACTIVE+COMPLETED owner-scoped Passage Top20 PASS
- `CHILD_DENSE_V1`, typed 상태와 provenance PASS
- same-version reindex 뒤 새 ACTIVE generation만 검색
- stale Worker·실패 artifact 노출과 duplicate/mixed inventory 0
- 실제 Ollama BGE-M3 smoke PASS
- Production Search V2 회귀 0, SEALED FINAL 불변, blocking finding 0

OpenSQL을 실행하지 않으면 `OPENSQL_VALIDATION=NOT_RUN`으로 남긴다.
