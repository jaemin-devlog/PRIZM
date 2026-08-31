# PRZ-030 Search V3 Semantic Evidence Validation Ceiling

- 상태: `IN_PROGRESS`
- 기준 branch: `PRZ-030-semantic-evidence-validation-ceiling`
- 기준 source: `PRZ-029-evidence-validation-selection@f7e4a7adffd5574526d6c00c76ece9113a68d69f`
- 선행 계약: `DEPENDS_ON_PRZ_025@5f8229f`, `DEPENDS_ON_PRZ_026_B3@1bbc1d7`,
  `DEPENDS_ON_PRZ_028@33c702a`, `DEPENDS_ON_PRZ_029@f7e4a7a`
- Production 적용: `NOT_RUN`

## 1. 목적과 비범위

동결된 B3 owner-scoped Dense Top20에 완벽한 semantic Evidence Validator가 있다고
가정했을 때의 품질 상한만 측정한다. S0는 B3 Dense 순서, O1은 같은 후보를
`DIRECT_SUPPORT → RELATED → CONTRADICTS → INSUFFICIENT`로 stable partition한 Gold
Oracle이다. O1은 평가 전용이며 runtime/Production 구현이 아니다.

일반 semantic validator, NLI/LLM/Cross Encoder, Sparse, FTS/BM25, RRF, Parent Dense,
QueryPlanner/rewrite, MMR, Grounded Answer와 신규 모델은 `NOT_RUN`이다. PRZ-029 Typed
Validation/Selection은 변경하지 않고 핵심 집계에서 제외한다.

## 2. 입력과 coverage

Original Seed, Long-form 1.1.0, Robustness 1.0.0 DEV/CAL의 69 semantic query를 그대로
사용한다. 사전 감사에서 `RELATED` 2건, `PARTIALLY_SUPPORTED` 2건,
semantic paraphrase/abstract negative 0건, other-actor/negation positive 0건이 확인돼
`semantic-support-stress-1.0.1`을 추가한다. 기존 Robustness의 여섯 synthetic
문서와 bundle/split을 재사용하고, 신규 문서는 만들지 않는다.

Stress는 DEV 12 / CALIBRATION 12 query, 6 bundles, KO/EN 및 여러 직무를
대상으로 한다. `SUPPORTED / PARTIALLY_SUPPORTED / NOT_SUPPORTED`와 네 support
relation, other actor, negation, completion state, related mention, abstract competency, semantic
paraphrase를 교차한다. source span·owner·version·split·lineage·SHA-256 검증 후
`INPUT_FROZEN`으로 봉인하며 검색 결과로 수정하지 않는다.

초기 `1.0.0`은 retrieval/model 실행 전에 PARTIALLY_SUPPORTED가 required aspect의
DIRECT_SUPPORT를 포함하지 않는 계약 오류가 발견돼 철회했다. `1.0.1`은 일부 required
aspect의 DIRECT와 나머지 미충족 relation을 함께 보존하며 `1.0.0` 결과는 존재하지 않는다.

## 3. Gold 경계와 Oracle

순서는 반드시 `candidate export → canonical identity SHA-256 freeze → Gold load/join →
Oracle`이다. Freeze 전 projection은 suite/split/query/owner, rank, candidate ID, cosine,
document/version, ordered EvidenceChild ID만 가지며 Gold relation·answerability·category를
받지 않는다. Gold 접근 guard가 freeze 전 read/join을 fail-closed로 거부한다.

Gold join은 runtime ID가 아닌 source-grounded Evidence Unit span을 passage의 ordered
EvidenceChild provenance에 대조한다. 한 candidate에 relation이 여럿이면 Oracle 순서의
가장 강한 relation을 적용한다. 같은 relation 내 candidate의 Dense 순서와 candidate
set/identity는 변경하지 않는다. Gold expectedEvidence가 exhaustive candidate judgment가
아니므로 일치하는 source-grounded unit이 없는 candidate를 `INSUFFICIENT`로 꾸미지 않고
`UNJUDGED`로 보존한다. O1은 `DIRECT/RELATED/CONTRADICTS/INSUFFICIENT/UNJUDGED` 순이며,
nDCG gain `3/2/1/0/0`은 judged relation 기준의 보수적 ceiling이다. Direct Recall/Top1/MRR은
source-grounded DIRECT inventory로 계산하지만, graded relation과 false-positive risk는
명시적으로 판단된 unit에 한정한 lower bound로 보고한다.

## 4. 상태와 failure stage

PRZ-025의 multi-aspect 계약을 적용한다. `PARTIALLY_SUPPORTED`는 required aspect 중
일부에 DIRECT가 있고 다른 required aspect는 직접 입증되지 않은 상태다. 따라서 ceiling
state는 supported의 모든 required aspect를 Top20 DIRECT가 충족하면 `FOUND`, partial의
DIRECT aspect가 Top20에 있으면 `PARTIAL`, Gold가 not-supported이면 `NONE`, 나머지는
`UNRESOLVED`다. 요청서의 "DIRECT 없이 RELATED가 있는 partial" 정의는 PRZ-025 계약과
충돌해 retrieval 실행 전에 이 문구로 바로잡았으며, 그런 입력은 validator가 거부한다.
- direct-positive query: expected relation에 DIRECT_SUPPORT가 하나라도 있는 query.
- failure stage: supported와 valid partial을 포함한 direct-positive는
  `ALREADY_CORRECT / RANKING_RECOVERABLE / RETRIEVAL_MISS`; not-supported의 Dense rank 1이
  RELATED 또는 CONTRADICTS면 `FALSE_POSITIVE_RISK`; 나머지 DIRECT 없는 query는
  `NO_SUPPORT`다. 요청된 `PARTIAL_ONLY` 집계 필드는 호환 목적으로 유지하지만 PRZ-025에
  유효한 partial은 DIRECT aspect를 반드시 가지므로 이 계약에서는 항상 0이어야 한다.

`FALSE_POSITIVE_RISK`는 Dense score threshold 추정이 아니라 Gold에 명시된 상위 비직접
근거를 직접 근거로 선택할 수 있는 구조적 risk lower bound다. `UNJUDGED`는 risk로
재라벨링하지 않는다.

## 5. 측정과 `CAPABILITY_GATE`

suite를 합치지 않고 Original, Long-form, Robustness, Stress를 각각 보고 전체
query-micro/user-macro와 profession, language, other-actor, negation, completion, abstract,
paraphrase slice를 함께 보고한다. S0/O1에서 Direct Recall@5/20, Top1, MRR, nDCG@5,
ceiling state accuracy, no-support risk, failure stage를 측정한다.

다음 중 하나라도 만족하면 `CAPABILITY_GATE=PASS`다.

1. O1 user-macro Direct Top1이 S0보다 `5 percentage points` 이상 향상
2. `RANKING_RECOVERABLE`이 서로 다른 최소 3 user bundles에서 관찰
3. `FALSE_POSITIVE_RISK`를 최소 2 query이자 2 user bundles에서 `NONE` ceiling으로 제거 가능

결과를 보고 이 수치를 변경하지 않는다. 최종 판정은 다음으로 고정한다.

- `RETRIEVAL_FIRST`: Direct Recall@20 < 0.90 또는 retrieval miss user bundle가 3개 이상
- `BUILD_SEMANTIC_VALIDATOR`: 위 retrieval 차단 없음 + `CAPABILITY_GATE=PASS`
- `VALIDATOR_NOT_JUSTIFIED`: 위 retrieval 차단 없음 + `CAPABILITY_GATE=FAIL`

Retrieval 차단이 있으면 `RETRIEVAL_AUGMENTATION_NEEDED`를 남기되 Sparse나 Parent Dense를
자동 채택하지 않는다. 그 외에는 둘 다 `DEFER`를 유지한다.

## 6. 보존 경계

허용 범위는 `src/searchEvaluation/**`, PRZ-030 문서, Registry와 ignored
`local/search-v3-evaluation/prz030/**`다. `src/main/**`, migration, dependency/build, frontend,
MCP, Docker, `v1.0.0`, PRZ-029 code/evidence와 기존 dataset은 변경 0이어야 한다.

SEALED FINAL은 semantic load/search/prediction/result를 실행하지 않는다. manifest
metadata/hash/flags와 tracked bytes 무결성만 확인하며 combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 유지한다.
