# PRZ-034 Evidence

- 상태: `VERIFIED / PROMISING`
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
- code freeze: `8838985a05653ec719bfc7e346186fda78a53da0`
- execution-contract commit: `178a531886083b5eaee499b24c6c6c158661e40c`
- official inference/prediction: `RUN_ONCE / PASS`
- input file SHA-256:
  `a709088efddfc7d4e849c0839718928e91634b38f80afb40c1e4ea65f9d9bb2c`
- prediction canonical/file SHA-256:
  `7d3023903fa4d1178dd0bf624f042d3fb09a9b54f2e1f4b1b5942f0ff241bab0` /
  `1fccea4a36893ec379bfa61d6bfeafbe823d59c963378ffc26d884b1b03b28b1`
- report SHA-256:
  `3ab5915c6fca15ceb30515f731c081c2321fb95a5f17c7141163005b55511ec1`
- Gold join: `AFTER_OUTPUT_VERIFIED`
- Oracle join: `AFTER_S0_S1_EVALUATED`
- 판정: `PROMISING`

## 공식 S0/S1 결과

117 query 중 Direct-positive 85개를 동일 B3 Passage candidate/order/score로 평가했다.

| metric | S0 기존 선택 | S1 CHILD_DENSE_V1 | PRZ-033 Oracle |
|---|---:|---:|---:|
| Direct Top1 | 0.5412 | 0.9059 | 0.9176 |
| MRR | 0.7576 | 0.9412 | 0.9471 |
| nDCG@5 | 0.7942 | 0.9258 | Top1 ceiling 진단 전용 |
| Recall@5 | 0.9882 | 0.9882 | 0.9882 |
| user-macro Top1 | 0.5880 | 0.9006 | 0.9224 |
| user-macro MRR | 0.7827 | 0.9397 | 0.9506 |

- win/loss/tie: `33 / 0 / 52`
- 기존 rank1 DIRECT retention: `46/46 = 1.0000`
- Top-Passage recoverable: `31/32`; recoverable user improvement: `9/9`
- Oracle capture: query Top1 `0.9688`, user-macro Top1 `0.9350`, MRR `0.9689`
- PRZ-033 headroom 39건 disposition: `CHILD_SELECTOR_FIXED 31`, `CHILD_SELECTOR_FAILED 1`,
  `PASSAGE_RANKING_LIMIT 6`, `MULTI_ASPECT_LIMIT 1`

## Slice

| profession | S0→S1 Top1 | S0→S1 MRR |
|---|---:|---:|
| backend | 1.000→1.000 | 1.000→1.000 |
| frontend/mobile | 0.636→0.955 | 0.818→0.977 |
| data/AI/infra | 0.400→0.867 | 0.680→0.917 |
| design/product | 0.733→0.867 | 0.867→0.933 |
| planning | 0.500→1.000 | 0.750→1.000 |
| marketing/sales | 0.333→0.867 | 0.613→0.883 |
| non-development/general | 0.455→0.909 | 0.727→0.955 |

| language | S0→S1 Top1 | S0→S1 MRR |
|---|---:|---:|
| Korean | 0.303→0.970 | 0.642→0.977 |
| English | 0.784→0.892 | 0.892→0.946 |
| Korean-English mixed | 0.467→0.800 | 0.680→0.850 |

사전 `-0.10` severe regression 기준에 걸린 slice는 `0`이다.

## Typed와 구조 안전

- Typed 24 query의 state accuracy/macro F1은 S0/S1 모두 `1.0000`; false NONE `0`이다.
- Typed Evidence precision은 `0.6316 → 0.6316`으로 개선되지 않았다. wrong value/date/version은 각각
  `2/2/2`, qualifier mismatch는 `8`로 동일하며, state-correct selection limit query는 `8`이다.
- Passage identity/order/score와 query vector identity parity `100%`, typed state와 provenance exact parity
  `100%`다.
- final-visible contamination/fragmentation/duplicate와 새 cross-parent merge는 모두 `0`이다.
- frozen B3 index의 Gold-parent overlap `1/160`은 PRZ-032/033에서 기록된 동일 candidate identity이며
  새 merge가 아니다. 이를 전체 index contamination `0`으로 재해석하지 않는다.

## 비용

- B3 Passage embedding `160`; 추가 unique Child embedding `227` (`8` batches)
- 영구 저장 가정 시 vector `160→387`, count/storage `+141.875%`; 추가 `929,792` bytes
  (`0.887 MiB`)
- 같은 공식 local run의 B3 Passage embedding `3,298.7ms`, 추가 Child embedding `1,946.8ms`
- precomputed Child cosine/sort p50/p95 `0.0322/0.0846ms`
- frozen query pipeline p50/p95 `25.9628/32.8050ms → 26.0014/32.8260ms`

이는 117-query local evaluation 관찰값이며 Production-scale 근거가 아니다. Child vector 영구 저장 여부도
이번 Phase에서 결정하지 않는다.

## 대표 결과와 남은 한계

- `SV3-LF-U103-Q01` marketing/sales English numeric-semantic query: Direct rank `2→1`
- `SV3-SS-U201-Q02` frontend/mobile Korean other-actor query: Direct rank `2→1`
- `SV3-SS-U206-Q02` non-development English other-actor query: Direct rank `2→1`
- 신규 ranking regression은 `0`이다.
- `SV3-LF-U106-Q02` completion English query는 Top-Passage Child recoverable인데 rank `2`로 남았다.
- lower-Passage limit 6건과 multi-aspect limit 1건은 Passage 순서를 바꾸지 않는 이 Selector의 범위 밖이다.

## 검증

- model-free input/Typed/provenance overlay preflight: `PASS`
- focused structural/typed/oracle/integrity Gradle tests, cache bypass: `PASS`
- official opt-in comparison: `1 test / 0 failures / 0 errors / 0 skipped`
- `git diff --check`: `PASS`
- `node scripts/verify-oss-readiness.mjs`: `PASS`; Node verifier `16/16`, SBOM와 link checks 포함
- frozen artifact/hash/Gate 독립 read-only audit: `PASS / BLOCKER 0`; 역사적 `1/160` 제한만 보존
- full backend unit/integration, frontend test/build: `NOT_RUN` (Production 변경 없음)
- Production source/migration/dependency/frontend/MCP/Docker diff: `0`

SEALED FINAL combined `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
manifest `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`, tree
`a129080861d7dafd32a9b3b3357b61aebb237e59`, `opened=false`, `searchExecuted=false`,
`CURRENT_FRESH_BASELINE=NOT_RUN`이다.
