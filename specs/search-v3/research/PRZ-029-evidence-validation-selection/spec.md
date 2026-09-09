# PRZ-029 Search V3 근거 검증과 선택

- 상태: `VERIFIED / PROMISING`
- 기준 branch: `PRZ-029-evidence-validation-selection`
- 기준 source: `PRZ-028-typed-exact-constraints@33c702aa0bff86502f7f70a343b60c59c13eb80f`
- 선행 조건: `DEPENDS_ON_PRZ_025`, `DEPENDS_ON_PRZ_026_B3`, `DEPENDS_ON_PRZ_028_EVIDENCE_VALIDATION_ONLY`
- Production 적용: `NOT_RUN`

## 1. 목적과 범위

B3 Structural RetrievalPassage raw Dense 후보를 먼저 PRZ-028의 동결된 Typed Constraint
parser/extractor/evaluator로 검증하고, 그 뒤 최대 5개의 원자적 EvidenceChild를 선택한다. Typed
constraint가 완전하게 적용 가능한 query에 한해 `FOUND / PARTIAL / NONE`을 판정할 수 있는지
Original, Long-form, Robustness와 Typed Stress 1.1.0 DEV/CAL에서 검증한다.

일반 semantic no-answer, confidence threshold, Parent Context/Dense, Cross Encoder, Sparse,
FTS/BM25, RRF, QueryPlanner/rewrite, MMR, NLI/LLM, Grounded Answer는 `NOT_RUN`이다. 새 dataset은
만들지 않는다.

## 2. 동결 기준선과 처리 순서

B3의 StructuralBlock, EvidenceChild, heading eligibility, RetrievalPassage grouping/size/provenance와
Ollama `bge-m3` 1024차원 cosine raw Dense를 변경하지 않는다. PRZ-028의 constraint/observation
추출, qualifier 계약, `SATISFIED / CONTRADICTED / UNKNOWN` evaluator도 변경하지 않는다. PRZ-028에서
ranking component로 비채택된 stable partition은 재사용하지 않는다.

처리 순서는 다음으로 고정한다.

`B3 full owner-scoped Dense ranking → top-20 validation shortlist → Passage의 atomic EvidenceChild 연결
→ sourceText-only Typed Validation → Evidence Selection → Typed Evidence State`

E0/E1은 query embedding과 full B3 ranking을 한 번만 공유한다. 검증은 새 후보를 만들거나 Dense
후보를 삭제·재점수화하지 않으며 original Dense rank/cosine과 전체 candidate identity/order를 trace에
보존한다. Candidate Recall@20은 같은 shortlist이므로 반드시 E0와 동일해야 한다.

## 3. Validation scope와 selection

한 runtime validation scope는 하나의 RetrievalPassage다. 그 안의 child는 같은 owner의 ACTIVE
document/version/page/structural Parent이며 source 순서가 보존되어야 한다. 다른 passage, document,
version 또는 Parent의 observation은 절대 합치지 않는다. 위반은 `NONE`이 아니라 fail-closed invariant
error다. Typed 입력은 atomic child `sourceText`와 provenance뿐이며 retrievalText, heading context,
Gold, category, answerability를 받지 않는다.

각 constraint는 passage 전체와 child별로 검증한다. 한 passage 안에서 모든 required constraint가
충족될 때만 그 passage가 `SATISFIED`다. 서로 다른 Parent 또는 서로 다른 passage의 partial match를
합쳐 `FOUND`로 만들지 않는다. 최종 Evidence는 상태 tier와 기존 Dense 순서를 따르며 새 relevance
score를 만들지 않는다.

여러 constraint의 passage/child 상태는 `all SATISFIED → SATISFIED; else any UNKNOWN → UNKNOWN;
else CONTRADICTED`로 줄인다. Query 전체는 `any SATISFIED → FOUND; else related UNKNOWN → PARTIAL;
else any CONTRADICTED → NONE; else observation 부재 → PARTIAL` 순서다. 여기서 related UNKNOWN은
같은 scope에서 일부 constraint가 판정됐거나 source observation이 모호한 경우다. qualifier/unit이 다른
대상이나 observation 자체가 없는 candidate는 contradiction을 가리는 related UNKNOWN으로 사용하지 않는다.

1. `FOUND`: `SATISFIED` passage의 실제 constraint-contributing child만 선택
2. `PARTIAL`: `UNKNOWN` child를 보수적 fallback evidence로 선택
3. `NONE`: 같은 target을 명시적으로 위반한 `CONTRADICTED` child만 exclusion evidence로 선택
4. constraint가 적용되지 않는 query: E0와 같은 child 선택을 byte-for-byte 동일한 경로로 재사용

각 tier 내부는 Dense candidate 순서와 child source 순서를 유지한다. 최대 5개이며 child ID와 exact
source span으로만 stable dedupe한다. 같은 Parent라도 다른 source span은 제거하지 않고, 서로 다른
Parent의 유사 문장도 제거하지 않는다. `NONE`에서 선택된 contradiction은 `DIRECT_SUPPORT`가 아니며
사용자의 경력이 없다는 뜻으로 표현할 수 없다.

## 4. Typed state와 applicability

- `FOUND`: 한 validation scope가 모든 parsed required constraint를 `SATISFIED`
- `PARTIAL`: `FOUND`가 없고, 명시적 same-target contradiction도 없거나 판단 가능한 정보가
  `UNKNOWN`뿐임
- `NONE`: `FOUND`가 없고 same-target observation이 조건을 명시적으로 `CONTRADICTED`
- `UNASSESSED`: typed applicability가 입증되지 않은 일반 semantic query

Stress 1.1.0 expected state는 runtime 입력과 분리된 frozen per-Evidence-Unit state를 평가 시 query별로
`any SATISFIED → FOUND; else any CONTRADICTED → NONE; else all UNKNOWN → PARTIAL`로 줄인다.
사전 분포는 DEV `8/3/1`, CALIBRATION `8/3/1`, 전체 `FOUND 16 / NONE 6 / PARTIAL 2`다. 기존
questions의 일반 answerability는 이 typed-certainty oracle이 아니며 runtime verdict 입력에도 쓰지 않는다.

Parser-empty는 semantic bypass다. Typed Stress 1.1.0의 frozen runtime-input identity가 이번 실행의
typed applicability 범위를 정하며, selector는 Gold annotation을 입력받지 않는다. 모든 query의 선택이
끝난 뒤 runtime parse와 frozen expected constraint의 exact conformance를 평가 metric/Gate로 붙인다.
그 입력 계약이 없는 일반 query와 partial semantic parse의 typed state는 `UNASSESSED`로 유지한다.

## 5. 평가와 Gate

기존 네 DEV/CAL suite를 각각 보존해 보고한다. 최소 metric은 Candidate Recall@20, parser-empty
semantic candidate/order/selection/source/provenance exact parity, direct rank-1 loss, typed state accuracy와
macro F1/confusion, 상태 tier coverage와 모든 selected child의 precision, contradicted selection rate, UNKNOWN fallback,
duplicate ratio, cross-parent merge, provenance accuracy와 quantity/qualifier/date/identifier-number/
percentage-direction/range slice다. 후보별 validation, selection, parse를 포함한 query당 added p50/p95와 관찰 가능한
in-memory payload를 기록한다.

`PROMISING`은 semantic exact parity, Candidate Recall 비열화 0, direct rank-1 신규 loss 0,
cross-parent merge 0, provenance 100%, Stress typed state/selection의 높은 정확도, FOUND/PARTIAL support
output의 contradicted selection 0과 미미한 추가 latency를 모두 요구한다. 제한된 PARTIAL/NONE 또는
selection 오류는 `NEEDS_ADJUSTMENT`, semantic 회귀·false NONE·correct evidence 제거·cross-parent merge
또는 실질 가치 부재는 `NO_GO`다. 수치는 실행 결과를 보기 전에 임의 PASS로 만들지 않는다.

## 6. 안전·변경 경계

허용 범위는 `src/searchEvaluation/**`, `specs/search-v3/research/PRZ-029-evidence-validation-selection/**`와 Registry다.
`src/main/**`, migration, dependency/build, frontend, MCP, Docker와 `v1.0.0` 변경은 0이어야 한다.
Raw report는 ignored `local/search-v3-evaluation/prz029/`에만 둔다.

SEALED FINAL은 semantic artifact를 열거나 검색하지 않는다. 허용되는 확인은 이미 공개된 manifest
metadata/hash/flags와 tracked byte diff뿐이다. combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, `opened=false`,
`searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 유지한다. PR, push, merge는 실행하지 않는다.
