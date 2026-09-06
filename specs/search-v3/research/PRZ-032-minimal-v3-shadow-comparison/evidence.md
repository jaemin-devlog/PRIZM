# PRZ-032 최소 Search V3 Shadow 비교 근거

## 최종 판정

`VERIFIED / MIXED_NEEDS_NEXT_CAPABILITY`

최소 V3는 후보 Top1 0.9176과 Recall@5/20 1.0000을 확보했지만 최종 Top1은 0.5412로
떨어졌다. 구조 오염과 localization은 개선됐으나 최종 `RetrievalPassage → EvidenceChild`
선택에서 후보 단계의 장점을 잃었다. 다음 병목은 새 retrieval이 아니라 atomic Child 선택이다.

## 기준선

- 시작: `PRZ-031-semantic-evidence-directness@a68e95a8b1adb9915fc6359cc6687e9d55068b45`
- code freeze: `6027494b80c765be905ae29a743b823dde05e96d`
- 공식 실행 HEAD: `6b7cfab` (`execution-contract.json`만 code freeze 이후 변경)
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- Production 변경: `0`

## 1. Push-only backup

force, rebase, squash, PR, merge와 tag 변경 없이 다음 local HEAD를 같은 이름의 origin
branch로 push하고 upstream을 연결했다.

| branch | backed-up HEAD |
| --- | --- |
| PRZ-025-search-v3-foundation | `5f8229f88251938dc5b34588676cc69edf409c99` |
| PRZ-026-structural-parsing-parent-child | `a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9` |
| PRZ-027-cross-encoder-reranking | `7271654b80ba7db3bc9cec89cba8ba1000660132` |
| PRZ-028-typed-exact-constraints | `33c702aa0bff86502f7f70a343b60c59c13eb80f` |
| PRZ-029-evidence-validation-selection | `f7e4a7adffd5574526d6c00c76ece9113a68d69f` |
| PRZ-030-semantic-evidence-validation-ceiling | `aca58a6c11b517557d6081756a3ea2cdc5f0550c` |
| PRZ-031-semantic-evidence-directness | `a68e95a8b1adb9915fc6359cc6687e9d55068b45` |

계보는 `main → 025 → 026`, 이후 PRZ-027 NO_GO side branch와
`026 → 028 → 029 → 030 → 031`이다. PRZ-027은 Minimal V3에 포함하지 않았다. 기본
worktree의 별도 PRZ-016 local 변경은 clean worktree와 섞지 않았다.

## 2. Freeze와 실행 무결성

- 입력: `117 queries / 61 DEV / 56 CALIBRATION / 23 users / 26 document versions`
  (`25 ACTIVE`, `1 inactive`), suite `21/24/24/24/24`
- canonical 중복: raw lineage `117`, canonical `117`, collision `0`
- 입력 SHA-256: `166a8aef77f59d322216d5b1b77cb872d0c18a6e78cfbab07757f281441e83cf`
- V2 source: `2e5a1f1ae34cc177e8c867ac9625076cf56a7c8ac4568e3844143afa2431c122`
- V3 source: `65f301b96bb243b5f9393926a3a502adaa054a0aa7716d7f4fc48f4b6ab2cdad`
- comparison policy: `d4ad1d95997cebd9f4e6105df6fa42ecf671a116f22b178d89507a32d64e1060`
- Gold schema: `186bed560c9a84b699c05640ff1393a5048ecec4b0a6ed366c0ae59f58254084`
- model: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`,
  `1024`, cosine
- output canonical SHA-256: `d6b29ce518f9571f7313a92feb7e1d8ac8b4b207d2fb7dc7fa0f8527dfc414a4`
- output file SHA-256: `647bf37eae00d5e8c9b909faf0767befeb69e2b31d77b36fa863d7cb2231b1f7`
- report SHA-256: `29af223023a50564aaf276261459b60eb521c3fcd37045588248b0907ffd8847`

초기 contract commit `340c7ae`의 입력 SHA가 실제 loader 값과 달랐고, 검색 실행 전에 독립
재계산과 Gold-free integrity test로 발견해 `6b7cfab`에서 정정했다. 공식 검색은 정정 이후
한 번만 실행했다. 순서는 `input/source freeze → Gold-free output freeze/verify → Gold join →
report`였고 guard가 통과했다.

## 3. 정확한 비교 경계

- V2: Production `TextChunker(800/120)`, 실제 `SearchService`,
  `VectorSearchRepository`, `CompositeSearchProfile`, fallback/rescue, dedup, expansion/snippet,
  profile `source-dedup-evidence-signals-v1`, 최대 5건
- V3: Structural EvidenceChild → B3 RetrievalPassage → 같은 BGE-M3 Dense Top20 → Typed
  Stress 1.1.0에만 PRZ-028 Validation/PRZ-029 Selection → 최대 5 EvidenceChild. 그 밖의
  semantic query는 ordered unique EvidenceChild이며 `UNASSESSED`
- V2 JDBC 경계: `PRODUCTION_SERVICE_REPOSITORY_SOURCE_WITH_EVALUATION_JDBC_ROWS`.
  PostgreSQL/pgvector SQL runtime은 이 host에서 `NOT_REVERIFIED`
- 모든 평가 fixture는 TXT다. PDF 전체 일반화나 V3 Production DB owner isolation의 구현
  완료 근거가 아니다.

## 4. Candidate와 final 결과

85개 Direct-positive query를 source span으로 평가했다.

| query-micro | V2 candidate | V3 candidate | V2 final | V3 final |
| --- | ---: | ---: | ---: | ---: |
| Direct Top1 | 0.8471 | 0.9176 | 0.7059 | 0.5412 |
| MRR | 0.9137 | 0.9539 | 0.7294 | 0.7576 |
| nDCG@5 | 0.9300 | 0.9429 | 0.7493 | 0.7942 |
| Direct Recall@5 | 1.0000 | 1.0000 | 0.7412 | 0.9882 |
| Direct Recall@20 | 1.0000 | 1.0000 | 0.7412 | 0.9882 |
| retrieval miss | 0 | 0 | 22 | 1 |

Candidate recall은 비열화가 없고 V3 candidate Top1/MRR도 높았다. 그러나 B3 passage를
atomic child로 펼쳐 최종 1위를 정할 때 V3 Top1이 낮아졌다. 반대로 V3 final Recall,
MRR, nDCG는 높아 구조적 후보가 대부분 Top5 안에는 남았다.

User-macro final은 V2/V3 순서로 Top1 `0.6957/0.5880`, MRR `0.7200/0.7827`,
nDCG@5 `0.7424/0.8137`, Recall@5 `0.7298/0.9855`였다.

## 5. Slice

| profession | candidate Top1 V2→V3 | final Top1 V2→V3 | final MRR V2→V3 |
| --- | ---: | ---: | ---: |
| backend | 1.000→1.000 | 0.333→1.000 | 0.333→1.000 |
| frontend/mobile | 0.955→0.955 | 0.864→0.636 | 0.886→0.818 |
| data/AI/infra | 0.867→0.867 | 0.533→0.400 | 0.533→0.680 |
| design/product | 0.667→0.867 | 0.667→0.733 | 0.667→0.867 |
| planning | 0.750→1.000 | 0.500→0.500 | 0.625→0.750 |
| marketing/sales | 0.800→0.867 | 0.800→0.333 | 0.867→0.613 |
| non-development/general | 0.909→1.000 | 0.727→0.455 | 0.727→0.727 |

| language | candidate Top1 V2→V3 | final Top1 V2→V3 | final MRR V2→V3 |
| --- | ---: | ---: | ---: |
| KO | 0.879→0.970 | 0.667→0.303 | 0.712→0.642 |
| EN | 0.811→0.919 | 0.757→0.784 | 0.757→0.892 |
| KO_EN_MIXED | 0.867→0.800 | 0.667→0.467 | 0.700→0.680 |

한국어, marketing/sales, frontend/mobile final Top1 회귀를 전체 평균으로 숨기지 않는다.

## 6. 구조·Typed·Semantic

| 구조 metric | V2 | V3 |
| --- | ---: | ---: |
| index cross-parent contamination | 44/60 (73.33%) | 1/160 (0.63%) |
| final result contamination | 70.80% | 0.00% |
| index/result fragmentation | 0% / 0% | 0% / 0% |
| duplicate atomic span/result | 0% / 0% | 0% / 0% |
| localization precision | 0.4943 | 0.9573 |
| localization recall | 0.9454 | 1.0000 |
| localization IoU | 0.4889 | 0.9573 |

V3의 final-visible contamination은 0이지만 Gold parent 기준 index passage 1건은 남았다.
owner bundle leakage와 inactive-version leakage는 두 경로 모두 0이었다.

Typed Stress 24건에서 V3 state accuracy/macro F1은 `1.0000/1.0000`, false NONE은 `0`이다.
16개 Direct-positive의 final Top1/Recall@5는 V2 `0.7500/0.7500`, V3 `1.0000/1.0000`이며
V3-only correct 4건, V2-only 0건이다. 다만 constraint-correct selected Evidence precision은
`0.6316`이고 wrong value/date/version 각 2건, qualifier mismatch 8건이므로 Typed Evidence
선택도 완성으로 보지 않는다.

Semantic 93건은 V3 state `UNASSESSED`를 유지했다. 그중 NOT_SUPPORTED 24건에서 평균 결과
수는 V2 `1.17`, V3 `4.50`이다. rank-1 relation은 V2/V3 각각
`CONTRADICTS 16/6`, `RELATED 1/1`, `INSUFFICIENT 2/2`, `UNJUDGED 5/15`다. V3의 unjudged가
많으므로 no-answer 개선으로 해석하지 않는다.

## 7. 분류와 대표 사례

Direct-positive 85건의 주 분류는 `BOTH_CORRECT 35`, `V2_ONLY_CORRECT 25`,
`V3_ONLY_CORRECT 11`, `BOTH_WRONG 14`다. 나머지 NOT_SUPPORTED 32건은
`NOT_APPLICABLE`이다. V2-only 25건 중 24건의 V2 결과는 다른 Gold Parent도 함께 포함한
fixed chunk였지만, 계약대로 Direct hit과 contamination을 별도로 모두 기록했다.

대표 V3 개선:

- `SV3-RB-U202-Q01`: candidate Direct rank `3→1`, V2 final miss에서 V3 final rank 1
- `SV3-LF-U102-Q04`: 양 candidate rank 1, V2 final miss에서 V3 final rank 1
- `SV3-U41-Q01`: 600건 qualifier Typed query, V2 final miss에서 V3 final rank 1
- `SV3-U45-Q03`: 15~20개월 Typed query, V2 final miss에서 V3 final rank 1
- `SV3-SS-U202-Q02`: V2 candidate rank 3/final miss에서 V3 candidate/final rank 1

대표 V3 회귀:

- `SV3-U04-Q03`: 사용자 인터뷰+A/B test, final Direct rank `1→2`
- `SV3-LF-U103-Q01`: 800 qualified leads, final Direct rank `1→2`
- `SV3-RB-U201-Q01`: 불안정 연결 현장 앱 배포, final Direct rank `1→2`
- `SV3-RB-U205-Q01`: 1,000건 이상 상담 요청, final Direct rank `1→2`
- `SV3-SS-U205-Q02`: V3 passage candidate는 rank 1이지만 atomic final Direct는 rank 2

Raw per-query output/report는 ignored `local/search-v3-evaluation/prz032/`에만 보존한다.

## 8. 운영 관찰

| local one-shot | V2 | V3 |
| --- | ---: | ---: |
| indexing/embedding unit | 61 | 160 |
| vector storage estimate | 249,856 B | 655,360 B |
| construction | 5.18 ms | 73.05 ms |
| indexing wall | 2,757.28 ms | 1,571.61 ms |
| query p50 | 27.39 ms | 25.96 ms |
| query p95 | 39.83 ms | 32.80 ms |

V3는 embedding/storage가 `2.62×`다. 관측 wall time은 V3가 낮았지만 V2가 먼저 실행돼
model warm-up 순서가 고정된 단일 local run이므로 Production-scale 우위 근거로 쓰지 않는다.

## 9. 판정과 다음 병목

공식 판정은 `MIXED_NEEDS_NEXT_CAPABILITY`다. V3는 candidate Recall, final Recall/MRR/nDCG,
구조 경계, localization과 typed query에서 앞섰지만 user-macro Top1과 주요 profession/language
slice가 회귀했다. 공식 report의 병목 label은 `SHARED_SEMANTIC_RANK_ONE_MISS`다. 세부 결과상
다음 한 단계는 새 retrieval/Sparse가 아니라 **B3 RetrievalPassage 안에서 질문에 직접 답하는
atomic EvidenceChild를 선택·정렬하는 semantic rank-one attribution**이어야 한다. 새 모델이나
해법은 별도 계약·ablation 전에는 확정하지 않는다.

## 10. 검증 상태

- 공식 PRZ-032 one-shot benchmark: `PASS`, 재실행 `0`
- Gold-free input integrity, evaluator/unit, PRZ-026 B3·PRZ-029 selection 관련 test: `PASS`
- `compileSearchEvaluationJava`: `PASS`
- `node scripts/verify-oss-readiness.mjs`: `PASS`
- 전체 `searchEvaluation`: `FAIL (HISTORICAL/ENVIRONMENTAL)` — Docker/PostgreSQL 부재,
  과거 one-shot artifact와 historical Gate로 276개 중 22개 실패, 5개 skip. PRZ-032 focused
  tests는 통과했으며 이 전체 실행은 공식 비교 전에 수행됐다.
- backend 전체 / frontend 전체 / PostgreSQL integration: `NOT_RUN`

SEALED FINAL은 combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, manifest SHA-256
`d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`, git tree
`a129080861d7dafd32a9b3b3357b61aebb237e59`, `opened=false`, `searchExecuted=false`,
`CURRENT_FRESH_BASELINE=NOT_RUN`이다.
