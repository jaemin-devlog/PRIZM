# PRZ-034 Evidence

- 상태: `IN_PROGRESS / OFFICIAL_COMPARISON_NOT_RUN`
- 시작 HEAD: `1a92add07be092457634b4dc83468ec3d951fe04`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- 시작 working tree: `CLEAN`
- Production 변경: `0`

## 시작 artifact와 model

- PRZ-032 output file/canonical:
  `647bf37eae00d5e8c9b909faf0767befeb69e2b31d77b36fa863d7cb2231b1f7` /
  `d6b29ce518f9571f7313a92feb7e1d8ac8b4b207d2fb7dc7fa0f8527dfc414a4`
- PRZ-033 candidate file/canonical:
  `b6d70c26164aa5234ad5f49148e490ca8b25571ef040113a7149cec5b4c526da` /
  `9d056dffc19a3e919b0da5bd6fd1ce0b2f3d2b7bb9d0dab892b95de1e8fd3c9b`
- PRZ-033 report: `700a39a80865af0c83c806e7f284f820448c43f902a4dc66230a38ecbe35f7d8`
- runtime input: `166a8aef77f59d322216d5b1b77cb872d0c18a6e78cfbab07757f281441e83cf`
- local model: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, F16, 1024 dimensions,
  size 1,157,672,605 bytes
- input inventory: 117 queries, 507 Top5 Passage occurrences, 804 Child occurrences, 227 unique Child
- frozen S0 final max Passage rank: `5`

PRZ-032 output에는 query vector bytes가 없어 artifact-only historical byte parity는 `NOT_VERIFIABLE`이다.
같은 B3를 재현해 frozen candidate/S0 parity를 먼저 검증하고, 그 Passage ranking에 사용한 단일 query
vector를 Child 비교에 그대로 넘기는 runtime identity parity를 공식 실행에서 검증한다.

## 결과 전 Typed 계약 교정

공식 inference와 Gold join 전에 Typed S1 계약을 교정했다. PRZ-029의 source-order `prepare`, parsed
constraint, Passage/Child validation outcome과 eligibility는 바꾸지 않는다. S1은 rank 1~5 Passage의
`PreparedChild` 순서에만 sourceText Child cosine overlay를 적용하고 기존
`EvidenceValidationSelector.select`를 재호출한다. rank 6~20 Child 순서는 source order를 유지한다.

따라서 `typedApplicabilityVerified`, parsed constraint count와 `FOUND/PARTIAL/NONE` state는 S0와 exact
parity를 요구하지만 selected Evidence 집합/순서는 변경될 수 있다. 변화는 constraint-correct Typed
Evidence precision으로 평가하며, 기존 state별 match tier와 sourceText/provenance는 유지해야 한다.
이 교정 시점에 selector 실행, prediction, Gold/Oracle join과 metric 평가는 모두 `NOT_RUN`이다.

## 현재 검증 상태

- selector input canonical SHA-256:
  `778b79117d47344433bed8d01f0f18a39ab4ae20f8f0ff444b2d8d5bd41c43ca` (`982,463` bytes)
- selector policy SHA-256:
  `29b211d18478f600356c4e9d92835eeae7f218d9a04fa8f9cb5751a937ad9e23`
- comparison policy SHA-256:
  `bcd0cbd76c1899498a26bd4ea70c5f77841e5a676954ef9feba6632eb1c1143e`
- source SHA-256:
  `447e2630a0217e11c870b0a4131b46d3f0d44d34afa967ae7771f9e6d13ec8ce`
- model-free Typed/provenance overlay preflight: `PASS`; 동일 vector tie에서 source order, query state,
  typed applicability, parsed constraint, candidate와 final source/provenance가 S0와 exact parity였다.
- 공식 실행 전 감사에서 recoverable-user 계산을 user-macro 기준으로 교정하고, state-correct Typed
  selection limit, multi-aspect fixed 분류, `-0.10` slice 회귀 downgrade와 transitive typed source freeze를
  결과 전에 추가했다.
- official inference/prediction: `NOT_RUN`
- Gold join: `NOT_RUN`
- S0/S1/Oracle evaluation: `NOT_RUN`
- 판정: `NOT_RUN`

SEALED FINAL combined `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
manifest `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`, tree
`a129080861d7dafd32a9b3b3357b61aebb237e59`, `opened=false`, `searchExecuted=false`,
`CURRENT_FRESH_BASELINE=NOT_RUN`이다.
