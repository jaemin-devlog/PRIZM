# PRZ-034 Atomic EvidenceChild Selector

- 상태: `IN_PROGRESS / OFFICIAL_COMPARISON_NOT_RUN`
- branch: `PRZ-034-atomic-evidence-child-selector`
- 기준 source: `PRZ-033-atomic-evidence-child-selection-ceiling@1a92add07be092457634b4dc83468ec3d951fe04`
- Production 적용: `NO_CHANGE`

## 1. 목적

PRZ-032의 frozen B3 Passage 후보와 순서를 바꾸지 않고, 같은 BGE-M3로 각 Passage 내부
`EvidenceChild.sourceText`를 query와 비교하는 실제 `CHILD_DENSE_V1` 한 구성만 평가한다. 경력의
진위가 아니라 `query ↔ source EvidenceChild relevance`만 다루며, PRZ-033 Gold Oracle은 prediction
봉인 이후 평가 ceiling으로만 사용한다.

## 2. S0와 S1

- `S0`: PRZ-032 Minimal V3 final 그대로
- `S1`: 동일 Passage 순위/score/candidate → 같은 Passage Child cosine 내림차순 → source-order
  stable tie-break → 최대 5 EvidenceChild

서로 다른 Passage 또는 Parent의 Child를 비교·이동·병합하지 않는다. 후보 추가/삭제, heading/Parent
context, Gold/category/profession/language/actor/completion/숫자 boost와 수동 score는 금지한다.

Semantic query는 Top5 Passage의 모든 Child를 정렬한다. Typed query는 PRZ-029의 source-order
`prepare`, parsed constraint, Passage/Child validation outcome과 eligibility를 그대로 재사용한다. S1은
준비된 corpus의 rank 1~5 Passage에서만 `PreparedChild` 순서를 Child cosine 내림차순과 source-order
stable tie-break로 overlay한 뒤 기존 `EvidenceValidationSelector.select`를 다시 호출한다. rank 6~20
Passage의 PreparedChild 순서는 바꾸지 않는다.

`typedApplicabilityVerified`, parsed constraint count와 최종 `FOUND/PARTIAL/NONE` state는 S0와 query별
exact parity여야 한다. selected Evidence 집합과 순서는 dense overlay 및 기존 최대 5건 selection에
따라 달라질 수 있으며, 그 변화는 Typed Evidence precision으로 평가한다. 단 각 selected Child는 기존
state가 요구하는 match tier를 충족하고 sourceText/provenance를 그대로 유지해야 한다. Child Dense가
Typed eligibility를 우회하거나 excluded tier의 Child를 되살리는 것은 금지한다.

## 3. Model과 embedding 계약

- model: `bge-m3:latest`
- digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- dimension/similarity: `1024 / COSINE`
- Child input: 원문 `sourceText`만
- Passage scope: frozen rank `1..5`
- batch size: 기존 evaluation client의 `32`

PRZ-032 artifact는 과거 query vector 값을 저장하지 않았으므로 artifact-only 소급 parity는
`NOT_VERIFIABLE_HISTORICAL_VECTOR_NOT_STORED`다. 따라서 공식 실행에서는 동일 B3 corpus를 같은
model/digest로 1회 재현하고, 각 query를 한 번 embedding한 동일 `float[1024]` instance를 B3 Passage
ranking과 모든 Child 비교에 공유한다. 재현한 Passage ID/order/score와 S0 final이 frozen PRZ-032와
100% 일치하지 않으면 semantic 평가 전에 중단한다. 재현은 parity 증명용이며 Passage 결과를
변경하거나 튜닝하지 않는다.

Frozen F0 final 결과의 최대 Passage rank는 5다. 입력 사전 감사 기준으로 117 queries, Top5 Passage
occurrence 507, Child occurrence 804, unique Child 227이므로 Top5 밖 embedding은 필요하지 않다.

## 4. Freeze와 Gold 순서

공식 실행 전 selector policy, Top5, model digest, PRZ-032/033 artifact hash, runtime input hash,
source/code-freeze commit과 comparison policy를 고정한다. 실행 순서는 다음과 같다.

`artifact verify → Gold-free selector input freeze → model verify → shared-query-vector B3 parity replay →
Child inference → prediction freeze/hash → OUTPUT_VERIFIED → Gold join → S0/S1/Oracle evaluation`

Prediction 전에 Gold, support relation, answerability 또는 PRZ-033 Oracle trace를 읽으면 결과는
`INVALID`다. 공식 inference는 code-freeze HEAD에서 1회만 실행하고 결과 후 policy를 바꾸지 않는다.

## 5. Metric과 failure 진단

Direct Top1, MRR, nDCG@5, Recall@5, user-macro Top1/MRR, win/loss/tie, S0 rank1 retention,
profession/language slice를 동일 PRZ-032 evaluator로 계산한다. PRZ-033 ceiling 대비 micro/user-macro
Top1과 MRR capture ratio, recoverable 32건 중 복구 수, 9개 bundle 중 개선 수를 기록한다.

Typed query는 state accuracy/macro F1뿐 아니라 S0/S1의 constraint-correct selected Evidence
분자/분모, precision과 wrong value/date/version/qualifier mismatch를 함께 기록한다. selected Evidence
변화는 허용하지만 query state, applicability, parsed constraint count, match tier와 provenance 변화는
허용하지 않는다.

남은 Direct-positive 실패는 `CHILD_SELECTOR_FIXED`, `CHILD_SELECTOR_FAILED`,
`PASSAGE_RANKING_LIMIT`, `MULTI_ASPECT_LIMIT`로 배타 분류한다. `TYPED_SELECTION_LIMIT`은 state는
맞지만 selected Evidence에 constraint-wrong evidence가 남은 typed query의 별도 진단이라 앞 분류와
겹칠 수 있다.

## 6. 사전 동결 Gate

Safety:

- Passage identity/order/score parity `100%`
- EvidenceChild provenance parity `100%`
- final contamination 및 새 cross-parent merge `0`
- fragmentation/duplicate 비열화 없음
- Typed applicability/parsed constraint count/state exact parity
- Typed selected Evidence의 state별 match tier와 provenance 보존; 집합/순서 변화는 허용
- SEALED FINAL search `0`

Quality:

- S0 rank1 Direct retention `>= 98%`
- win `>` loss
- user-macro Top1 gain `> 0`
- Recall@5 비열화 없음
- 다음 중 하나 이상: micro Top1 Oracle headroom `>= 25%`, recoverable `>= 8/32`, 개선 bundle
  `>= 3/9`

Operational observation Gate:

- Top5-only unique Child `<= 250`, 신규 model/service/dependency `0`
- precomputed Child vector를 사용하는 same-Passage cosine/sort p95 `<= 5ms`
- embedding wall time과 vector storage는 별도 기록하며 Production-scale 근거로 사용하지 않음

Slice 판정은 profession/language별 final Top1의 S1-S0 delta가 `-0.10` 미만이면 severe 신규
회귀로 고정한다. 다른 Gate가 통과해도 이 경우 `PROMISING`으로 판정하지 않고 최소
`NEEDS_ADJUSTMENT`로 내린다. recoverable 9-user 개선 수는 복구 query 존재 여부가 아니라 각 user의
전체 Direct-positive query Top1 평균이 S0보다 실제로 높아진 경우만 센다.

판정은 모든 Gate와 비용 경계를 통과하고 순증이 있으면 `PROMISING`, 안전은 지키지만 일부 quality,
slice 또는 비용 Gate가 부족하면 `NEEDS_ADJUSTMENT`, 순증 없음·loss 우세·rank1 훼손 또는 비용만
증가하면 `NO_GO`다. 실패해도 다른 Selector를 시험하지 않는다.

## 7. 비범위

Qwen/reranker/Cross Encoder/Sparse/FTS/BM25/RRF/Parent Dense/Parent Context/QueryPlanner/rewrite/MMR/
Grounded Answer, 새 dataset/query, Production/DB/dependency/frontend/MCP/Docker 변경은 `NOT_RUN`이다.
SEALED FINAL은 combined `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 유지한다.
