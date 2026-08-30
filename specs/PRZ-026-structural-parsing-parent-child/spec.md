# PRZ-026 Structural Parsing and Parent-Child Retrieval

- 상태: `IN_PROGRESS / PHASE_1_RETRIEVAL_PASSAGE_NEEDS_ADJUSTMENT`
- 현재 Phase: `Phase 1 Retrieval Passage Robustness — INPUT_READY / BENCHMARK_NOT_RUN`
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
| B1. Structural Child v1 + BGE-M3 Dense | `COMPLETED — NEEDS_ADJUSTMENT` |
| B2. Structural Child v2 + BGE-M3 Dense | `COMPLETED — NEEDS_ADJUSTMENT` |
| B3. Structural Retrieval Passage + BGE-M3 Dense | `COMPLETED — NEEDS_ADJUSTMENT` |
| C. Structural Child + Parent Context + BGE-M3 Dense | `NOT_RUN` |
| D. Parent-Child Retrieval + BGE-M3 Dense | `NOT_RUN` |

## 2. 범위와 보존 계약

구현은 `src/searchEvaluation`의 evaluation/shadow 경로와 이 PRZ 문서에만 둔다. A는 실제
Production `TextChunker`와 기본 `IngestionProperties(max=800, overlap=120)`를 호출한다.
B는 evaluation-only parser/builder다. Production Search, ingestion/parser, schema/migration,
API, MCP, frontend, dependency, Docker runtime과 `v1.0.0`은 변경하지 않는다.

아래 비교 조건은 역사적 Phase 1(B1)에 적용됐다. Adjustment(B2)는 이 입력 제한을
supersede하여 같은 Original Seed를 그대로 재실행하고, 별도 version의 Long-form DEV/CAL도
독립 실행한다. 두 dataset의 metric은 합치지 않는다.

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

`HEADING`은 source block과 parent boundary로 보존하지만 B2부터 기본적으로 독립 검색 Child가
아니다. paragraph/list/table/key-value와 assertion-bearing `OTHER`만 검색 후보가 된다. heading
문자열은 일반 Child의 `retrievalText`에 붙이지 않는다. 이 PRZ에서 C의 `Parent Context`는
Evidence Parent/section/heading context를 child embedding에 추가하는 실험을 뜻하며 `NOT_RUN`이다.
단, B1부터 명시된 same-table header 보존 예외는 B2에서도 활성 상태다. 따라서 B2를
"모든 cross-block context가 없는 sourceText-only"로 표현하지 않고,
`SOURCE_TABLE_HEADER_CONTEXT_EXCEPTION_ACTIVE`로 report에 노출한다.

Table header 판정은 B1과 동일하게 연속 table 영역의 첫 row를 header로 취급한다. 명시적 Markdown
divider는 evidence candidate가 아니며, blank/heading boundary를 넘겨 header를 전달하지 않는다.
Header가 없는 plain table도 첫 row를 header로 간주할 수 있다는 모호성은 B2에서 새로 튜닝하지
않고 `OPEN_LIMITATION`으로 남긴다. 이를 바꾸면 heading eligibility 외 두 번째 treatment change가
되므로 별도 ablation 없이 이번 결과에 섞지 않는다.

짧은 독립 행에 날짜·수치·명시적 값이 함께 있거나 바로 뒤의 값 행과 하나의 사실을 이루면
일반 구조 신호로 evidence-bearing block을 구성한다. 특정 자격증·직무·기술명 사전이나 Phase 1
실패 문자열 예외는 사용하지 않는다. 길이만으로 후보를 탈락시키거나 인접 block을 병합하지
않으며, 병합이 필요하다는 별도 근거가 생기기 전까지 B2의 유일한 eligibility 변경은
context-only heading이다.

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

## 9. Phase 1 Adjustment acceptance contract

수정 전에 Phase 1의 네 heading 회귀를 같은 source/model 조건으로 재현한다. 그 뒤 기존
`search-v3-fresh-seed-1.0.1`은 Original Seed로 그대로 재평가하고, 별도
`search-v3-fresh-devcal-1.1.0` 장문 DEV/CAL expansion을 추가한다. expansion은 split별 장문
문서 3개 이상, 전체 신규 query 24개 이상, 개발 직무 문서 비율 50% 이하와 KO/EN/mixed를
충족해야 한다. Original과 Long-form 수치를 하나의 aggregate로만 보고하지 않는다.

완료 Gate는 다음과 같다.

- Original Seed의 네 query 모두에서 원인이었던 `HEADING` rank 1이 사라지고,
  heading-only candidate/rank1이 0이다. 네 query가 모두 DIRECT_SUPPORT rank 1로 회복하는지는
  별도 ranking Gate이며, 하나라도 남으면 그 회귀를 숨기지 않고 판정에 반영한다.
- Long-form에서 Fixed 대비 retrieval/ranking 순증 또는 그 부재를 실제 BGE-M3 결과로 기록한다.
- contamination, fragmentation, fixed chunk별 Gold Parent 분포, 길이 구간, 후보·embedding 비용을
  함께 기록한다.
- PRZ-025 SEALED FINAL의 파일·hash·flags는 byte-level로 유지하고 검색하지 않는다.
- Parent Context, Parent Dense, sparse, reranker와 Production 경로는 `NOT_RUN`이다.

## 10. Phase 1 Retrieval Passage contract

B3는 EvidenceChild를 source-grounded 최소 근거로 그대로 보존하고, embedding/search 후보만
`RetrievalPassage`로 분리한다. 같은 document, version, page, structural Parent 안에서 source
순서상 인접한 Child만 greedy grouping한다. query, gold, 직무, 언어, actor, negation 또는 completion
state는 passage 생성 입력이 아니다.

- 정책은 한 번 고정한 `minimum target 120 / target maximum 320 / absolute maximum 480`
  code points를 Original과 Long-form 전체에 공통 적용한다.
- 현재 passage가 120 미만일 때만 320을 넘어 480까지 다음 인접 Child를 받을 수 있다.
- heading은 context-only boundary이고 retrieval text에 추가하지 않는다. overlap은 0이다.
- 각 passage는 ordered `evidenceChildIds[]`와 각 Child의 exact provenance range를 보존한다.
- Gold hit는 passage의 넓은 합성 span이 아니라, 포함된 하나의 atomic Child range가 Gold Unit의
  모든 span을 덮을 때만 인정한다.
- table row는 atomic Child ID를 유지하고 기존 source-table header context 예외만 중복 없이 유지한다.
- 480을 넘는 atomic Child는 조용히 자르거나 oversized passage로 만들지 않고 fail-closed한다.

B3 성공 Gate는 cross-parent/heading violation 0, fragmentation 비열화 0, Recall 비열화 0,
DIRECT Gold-mapped Child 보존 100%, B2 대비 candidate 감소, query-micro Top1/MRR 비열화 0이다.
profession/language 신규 회귀는 별도 blocking finding으로 판정한다. Parent Context, Parent Dense,
reranker와 semantic policy는 계속 `NOT_RUN`이다.

## 11. Phase 1 Retrieval Passage result

고정 정책 1회 비교에서 Original B2/B3 후보는 `17→15`, Long-form은 `128→72`였다. Long-form
indexing wall time은 최종 단일 local run에서 `1133.644ms→687.901ms`로 감소했다. 두 dataset 모두
Recall@5/10/20/50, query-micro Top1/MRR, contamination 0, fragmentation 0과 Gold Child 보존 100%를
유지했다. 그러나 Long-form `FRONTEND_MOBILE` Top1/MRR이 `1.0/1.0→0.6667/0.8333`으로 신규
회귀해 B3 판정은 `NEEDS_ADJUSTMENT`다. 따라서 C는 계속 `NOT_RUN`이다.

## 12. Phase 1 Retrieval Passage robustness contract

B3의 구조·크기 정책은 `01d9ae2f90eff691d96041579e42a02aa04a3486`에서 동결한다. 기존
`SV3-LF-U104-Q01`이나 다른 관측 결과에 맞춰 passage eligibility, `120/320/480`, heading 또는
retrieval text를 변경하지 않는다. 후속 판정은 별도 synthetic DEV/CAL suite
`search-v3-fresh-devcal-robustness-1.0.0`에서 B2와 B3를 재현해 수행한다.

새 suite는 기존 Original Seed, DEV/CAL 1.1.0과 source fact, query, template, generator seed,
document/version lineage를 공유하지 않는다. DEV/CAL 각 3 user bundles, 전체 6 documents와
24 DIRECT-support queries를 목표로 하며 KO/EN/mixed와 개발·비개발 직무를 모두 포함한다.
`FRONTEND_MOBILE`은 독립 bundle 2개와 direct query 8개를 추가하여 기존 1.1.0과 합친 누적
slice가 3 bundles, 10 queries 이상이 되게 한다. 이 분포는 관측된 query 문자열이나 Gold rank를
passage builder에 전달하지 않는다.

표본과 paired 판정은 실행 전에 다음처럼 고정한다.

- profession/language slice는 distinct user bundles 3개 이상이면서 DIRECT-support query 10개
  이상일 때만 `SUFFICIENT`; 그 미만은 `INSUFFICIENT_SAMPLE`이며 백분율을 blocking proof로 쓰지
  않는다.
- B3-B2의 query별 Top1과 reciprocal-rank delta, win/loss/tie, query-micro와 user-macro를 기록한다.
- uncertainty는 user bundle을 cluster로 10,000회 복원 추출하는 deterministic bootstrap
  (`seed=260830026`)의 percentile 95% interval로 기록한다.
- 충분한 slice에서 Top1 또는 reciprocal-rank delta의 interval 상한이 0보다 작을 때만
  `BLOCKING_REGRESSION`; interval 하한이 0 이상이면 `NON_INFERIOR`; 그 사이는 `INCONCLUSIVE`다.
- `INCONCLUSIVE`는 Production 채택 근거가 아니다. 다만 독립 robustness suite의 전체 및 새
  `FRONTEND_MOBILE` point delta가 음수가 아니고, 누적 충분 slice에 `BLOCKING_REGRESSION`이
  없으면 다음 evaluation-only ablation을 막지 않는다.

B3 robustness의 `PROMISING` Gate는 contamination/fragmentation/heading violation 0, DIRECT Gold
Child 보존 100%, Recall 비열화 0, 새 suite의 B3 candidate/embedding 수가 B2보다 최소 25% 감소,
새 suite 전체 및 새 `FRONTEND_MOBILE` Top1/MRR point delta 비음수, 누적 충분 profession/language
slice의 blocking regression 0이다. 이 Gate는 Parent Context 실험 진입 판단일 뿐 Search V3 채택
Gate가 아니다. Parent Context, Parent Dense, reranker와 SEALED FINAL search는 계속 `NOT_RUN`이다.
