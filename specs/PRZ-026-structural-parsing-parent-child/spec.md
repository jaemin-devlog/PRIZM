# PRZ-026 Structural Parsing and Parent-Child Retrieval

- 상태: `IN_PROGRESS / PHASE_1_NEEDS_ADJUSTMENT`
- 현재 Phase: `Phase 1 — Structural Child Dense Baseline`
- 선행 조건: `DEPENDS_ON_PRZ_025`
- 기준 source: `PRZ-025-search-v3-foundation@5f8229f88251938dc5b34588676cc69edf409c99`
- Production 적용: `NOT_RUN`

## 1. 목적과 가설

같은 source, query, owner scope와 Ollama `bge-m3` 1024차원 cosine 조건에서 Production
`TextChunker`의 800자/120자 overlap 결과(A)와 구조 경계를 보존한 Evidence Child(B)를
비교한다. 가설은 B가 직접 근거를 덜 오염·분절하면서 raw Dense candidate의 Top1, MRR과
Recall을 개선한다는 것이다. 결과를 본 뒤 gold, split 또는 SEALED FINAL을 바꾸지 않는다.

전체 PRZ의 예정 ablation은 아래와 같다. 이번 Phase는 A와 B만 실행한다.

| Ablation | 상태 |
| --- | --- |
| A. Fixed Chunk + BGE-M3 Dense | `COMPLETED` |
| B. Structural Child + BGE-M3 Dense | `COMPLETED — NEEDS_ADJUSTMENT` |
| C. Structural Child + Parent Context + BGE-M3 Dense | `NOT_RUN` |
| D. Parent-Child Retrieval + BGE-M3 Dense | `NOT_RUN` |

## 2. 범위와 보존 계약

구현은 `src/searchEvaluation`의 evaluation/shadow 경로와 이 PRZ 문서에만 둔다. A는 실제
Production `TextChunker`와 기본 `IngestionProperties(max=800, overlap=120)`를 호출한다.
B는 evaluation-only parser/builder다. Production Search, ingestion/parser, schema/migration,
API, MCP, frontend, dependency, Docker runtime과 `v1.0.0`은 변경하지 않는다.

비교 조건은 두 경로 모두 다음과 같다.

- PRZ-025 `search-v3-fresh-seed-1.0.1`의 `DEV`와 `CALIBRATION` ACTIVE source/query
- query별 `userBundleId` scope
- Ollama model `bge-m3`, vector dimension 1024, cosine similarity
- query rewrite, threshold, rescue, reranker, sparse/FTS/RRF, QueryPlanner 없음
- raw Dense score 내림차순, 동점은 stable candidate ID 오름차순

`SEALED_FINAL_TEST`는 runner가 경로 수준에서 거부하며 검색·prediction·result를 만들지 않는다.
`opened=false`, `searchExecuted=false`, sealed combined SHA-256을 유지한다.

## 3. StructuralBlock 계약

`StructuralBlock`은 career vocabulary가 아니라 관찰 가능한 layout 신호로만 만든다.

- 타입: `HEADING`, `PARAGRAPH`, `LIST_ITEM`, `TABLE_ROW`, `KEY_VALUE`, `OTHER`
- 신호: line/blank-line boundary, Markdown heading, 짧은 독립 행, bullet/number marker,
  indentation, key-value delimiter, table delimiter/column repetition, 문장 종료와 인접성
- 특정 회사·프로젝트·직무·기술명 사전 사용 금지
- source order, 원문 text와 source 좌표를 보존
- 명백한 heading이 바뀌면 새로운 `parentAnnotationCandidateId`를 부여

## 4. EvidenceChild 계약

각 block을 최소 검색 단위로 만들되 다음을 지킨다.

- paragraph/list item/table row/key-value의 경계를 우선 보존한다.
- 서로 다른 heading candidate를 하나의 child로 합치지 않는다.
- 800 code point를 넘는 block만 문장 경계를 우선해 분할하며 overlap은 만들지 않는다.
- `sourceText`는 원문 span의 exact text다.
- `retrievalText`는 기본적으로 `sourceText`와 같다.
- table data row만 원문 table header를 retrieval context로 붙일 수 있고, header block ID를
  `contextSourceBlockIds`에 기록한다.
- LLM 생성·요약·원문 밖 정보는 금지한다.

Heading도 독립 source block/child로 유지한다. 이는 Phase C의 Parent Context를 미리 구현하지
않고 heading 자체가 직접 근거일 수 있는 가능성을 보존하기 위한 선택이다.

## 5. Provenance 계약

모든 block/child는 benchmark `documentId`, `versionId`, nullable page, 1-based line range,
Unicode code-point 0-based `[start,end)`, source block ID, parent annotation candidate ID,
document SHA-256과 exact source text SHA-256을 가진다. runtime DB chunk/parent ID는 gold나
provenance ID로 허용하지 않는다.

## 6. 평가와 metric 정의

`DIRECT_SUPPORT`가 하나 이상 있는 `SUPPORTED` 또는 `PARTIALLY_SUPPORTED` query만 retrieval
metric 분모에 포함한다. `NOT_SUPPORTED` query는 contradiction/insufficient/related evidence의
raw rank 진단에는 남기지만 이 Phase는 answerability selector가 아니므로 FPR을 판정하지 않는다.

- `Recall@K`: query의 materialized direct-support group 요구를 top K가 충족한 비율
- Gold Unit hit: 모든 constituent source span을 한 candidate가 포함한 direct Unit 비율
- Parent/Group coverage: expected Parent/Group 중 top K에서 도달한 비율
- `Top1`, `MRR`: 첫 direct-support candidate 기준
- duplicate ratio: 같은 gold group에 매핑되는 중복 candidate 비율
- fragmentation: active gold Unit의 모든 span을 한 child가 보존하지 못한 비율
- contamination: 한 candidate가 서로 다른 gold Parent span과 겹치는 비율
- operation: child/embedding 수, 평균 code-point 길이, construction/indexing wall time,
  shared query embedding과 A/B cosine ranking p50/p95

모든 핵심 metric은 query-micro와 user-macro를 함께 보고한다. profession과 language slice도
기록하며 후보 수가 K보다 작은 ceiling은 숨기지 않는다.

## 7. 완료 조건

- parser/builder의 구조·provenance·no-overlap 계약을 실행 가능한 test가 검증한다.
- A와 B가 같은 ACTIVE DEV/CAL source/query와 동일 query embedding을 사용함을 검증한다.
- Ollama가 `bge-m3` 1024차원 embedding을 실제 반환하고 cosine A/B report를 생성한다.
- query별 rank/hit과 aggregate/user/profession/language/fragmentation/contamination/cost를 기록한다.
- PRZ-025 validator, sealed hash/flags, `git diff --check`, OSS readiness와 diff scope가 통과한다.
- Production, migration, dependency, frontend, MCP와 sealed-final 변경이 0이다.

## 8. 판정 계약

이번 Phase 판정은 `PROMISING`, `NEEDS_ADJUSTMENT`, `NO_GO` 중 하나만 사용한다. B를
Production으로 승격하지 않는다. Recall/Top1/MRR, contamination/fragmentation, user/profession
회귀와 embedding/latency 비용을 함께 보고 판단한다. seed의 candidate ceiling이나 표본 부족으로
순증을 입증하지 못하면 좋은 구조 metric만으로 `PROMISING`을 선언하지 않는다.
