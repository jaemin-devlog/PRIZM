# PRZ-032 최소 Search V3 Shadow 비교

- 상태: `VERIFIED / MIXED_NEEDS_NEXT_CAPABILITY`
- 기준 branch: `PRZ-032-minimal-v3-shadow-comparison`
- 기준 source: `PRZ-031-semantic-evidence-directness@a68e95a8b1adb9915fc6359cc6687e9d55068b45`
- 선행 계약: `DEPENDS_ON_PRZ_025@5f8229f`, `DEPENDS_ON_PRZ_026_B3@a7dbb12`,
  `DEPENDS_ON_PRZ_028@33c702a`, `DEPENDS_ON_PRZ_029@f7e4a7a`
- Production 적용: `NO_CHANGE`

## 1. 목적과 비교 구성

동일한 frozen DEV/CAL 원문과 canonical query에서 실제 Production Search V2와 지금까지
살아남은 Minimal Search V3를 한 번 비교한다. 새로운 retrieval, model, threshold 또는
heuristic은 추가하지 않는다.

- V2: Production `TextChunker(800/120)` → `bge-m3` exact cosine Top20 → 실제
  `SearchService`의 profile, identifier guard, fallback/rescue, dedup, localization → 최대 5건
- V3: Structural EvidenceChild → B3 RetrievalPassage → 같은 `bge-m3` exact cosine Top20 →
  Typed Stress 1.1.0에만 PRZ-028 Validation/PRZ-029 Selection → 최대 5 EvidenceChild
- 일반 semantic 및 typed applicability가 입증되지 않은 query: B3 순서의 ordered unique
  EvidenceChild 최대 5건, state는 `UNASSESSED`

PRZ-027 GTE, PRZ-031 Qwen, C1 heading context, Parent Dense/Context, Sparse, BM25/FTS,
RRF, QueryPlanner/rewrite, MMR과 새 no-answer 정책은 포함하지 않는다.

## 2. 입력과 Gold 경계

새 문서·질문은 만들지 않는다. Original, Long-form 1.1.0, Robustness 1.0.0, Typed Stress
1.1.0, Semantic Stress 1.0.1의 DEV/CAL만 사용한다. 같은 owner의 정규화 동일 query는
canonical aggregate에서 한 번만 계산하고 suite 진단에는 원래 lineage를 보존한다.
SEALED_FINAL_TEST는 어떤 loader, DB seed, embedding, query 또는 result에도 사용하지 않는다.

실행 순서는 `source/input/config freeze → Gold-free V2/V3 prediction → output SHA-256 freeze →
output identity verify → Gold join → metric`이다. runtime/DB ID는 Gold가 아니며, 두 경로 모두
document/version/source span으로 평가한다. Gold supplier는 output verify 전 fail-closed다.

## 3. 실제 V2와 evaluation V3 경계

V2는 실제 Production `TextChunker`, `EmbeddingService`, `EmbeddingValidator`,
`VectorSearchRepository`, `CompositeSearchProfile`, `SearchService`,
`EvidenceExpansionService` source object를 직접 사용한다. 현재 host에 Docker/PostgreSQL이 없어
JDBC 실행 경계만 frozen fixture의 owner/ACTIVE-scoped exact-cosine row provider로 대체한다.
따라서 profile/fallback/rescue/localization은 실제 Production 실행이지만, pgvector SQL runtime과
DB owner isolation은 이번 비교에서 재검증하지 않는다. 평가 adapter는 stable source span으로만
역매핑하며 서비스 선택·순위를 재구현하지 않는다.

V3는 evaluation/shadow code다. owner bundle 및 document/version scope leakage 0을 검증하지만
Production DB isolation 구현 완료로 표현하지 않는다.

## 4. Freeze와 1회 실행

공식 실행 전에 V2/V3 source, application search profile, `bge-m3` digest/dimension/cosine,
tracked input, Gold schema와 comparison policy SHA-256을 고정한다. official output은 CREATE_NEW로
한 번 생성한다. 결과를 보고 V2 adapter, V3 rule, query, Gold 또는 metric을 바꾸지 않는다.
구현 결함으로 무효라면 해당 결과를 `INVALID_COMPARISON` 역사로 남기고 자동 재실행하지 않는다.

## 5. Metric 계약

Candidate Top20과 final Top5를 분리한다.

- Candidate: Direct Recall@5/20, required group/parent coverage, retrieval miss
- Final: Direct Top1, MRR, nDCG@5, Recall@5, duplicate, cross-parent contamination,
  fragmentation, source localization
- Slice: query-micro, user-macro, profession, KO/EN/KO_EN_MIXED
- Typed: constraint-correct Evidence, wrong value/date/version, qualifier mismatch,
  V3 `FOUND/PARTIAL/NONE`와 false NONE; V2는 원문 정확도로만 평가
- Semantic NOT_SUPPORTED: 결과 수와 rank-1 judged relation만 진단하며 V3 semantic state는
  `UNASSESSED`
- Operation: indexing unit/embedding count, wall time, query p50/p95, vector storage estimate

Direct hit은 result source span이 Gold `DIRECT_SUPPORT` Evidence Unit span을 포함할 때만 인정한다.
V2 fixed chunk가 Direct span과 다른 Gold Parent를 함께 포함하면 hit과 contamination을 동시에
기록한다. nDCG gain은 `DIRECT_SUPPORT=3`, `RELATED=2`, `CONTRADICTS=1`,
`INSUFFICIENT/unjudged=0`이며 두 경로에 동일하게 적용한다.

Query의 주 분류는 rank-1 Direct 기준의 `BOTH_CORRECT / V2_ONLY_CORRECT /
V3_ONLY_CORRECT / BOTH_WRONG`이다. 같은 Gold의 rank 차이, V2 structural contamination과 V3 typed
advantage는 직교 diagnostic flag로 둔다.

## 6. 판정

- `MINIMAL_V3_AHEAD`: candidate Recall 비열화 없음, user-macro ranking의 의미 있는 회귀 없음,
  구조/typed 개선과 비용이 설명 가능
- `MIXED_NEEDS_NEXT_CAPABILITY`: 구조/typed는 개선됐지만 semantic ranking/no-answer 병목이 명확
- `CURRENT_V2_AHEAD`: V3 구조 이득보다 ranking/Recall/운영 회귀가 큼
- `INVALID_COMPARISON`: input/model/source parity, Gold 순서 또는 adapter 경계가 깨짐

이번 판정은 Production cutover가 아니다. 결과가 mixed면 관찰된 가장 큰 병목 하나만 다음
Phase 후보로 제안하며 Sparse 등 특정 해법을 자동 선택하지 않는다.

공식 1회 비교 결과 candidate Direct Recall@5/20은 두 경로 모두 `1.0000`이었다. V3는
근거 경계·localization과 Typed Stress에서 개선됐지만, user-macro final Top1은
`0.6957 → 0.5880`으로 회귀했다. 따라서 판정은 `MIXED_NEEDS_NEXT_CAPABILITY`다. 다음 병목은
새 retrieval이 아니라 B3 RetrievalPassage 안에서 질문에 직접 답하는 atomic EvidenceChild를
최종 1위로 선택하는 단계다. Semantic no-answer는 계속 `UNASSESSED`다.

## 7. 보존 범위

허용 변경은 `src/searchEvaluation/**`, 이 PRZ 문서와 Registry, ignored
`local/search-v3-evaluation/prz032/**`다. `src/main/**`, migration, build/dependency,
frontend, MCP, Docker runtime와 `v1.0.0`은 변경하지 않는다.

SEALED FINAL은 combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 공식 실행 전후
유지한다. 이 `CURRENT_FRESH_BASELINE`은 SEALED FINAL의 상태이며 DEV/CAL V2 shadow 결과와
혼동하지 않는다.
