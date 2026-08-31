# PRZ-031 Search V3 Semantic Evidence Directness

- 상태: `IN_PROGRESS / BLOCKED_MODEL_SELECTION`
- 기준 branch: `PRZ-031-semantic-evidence-directness`
- 기준 source: `PRZ-030-semantic-evidence-validation-ceiling@aca58a6c11b517557d6081756a3ea2cdc5f0550c`
- 선행 계약: `DEPENDS_ON_PRZ_025@5f8229f`, `DEPENDS_ON_PRZ_026_B3@1bbc1d7`,
  `DEPENDS_ON_PRZ_029@f7e4a7a`, `DEPENDS_ON_PRZ_030@aca58a6`
- Production 적용: `NOT_RUN`

## 1. 목적과 제품 경계

동결된 B3 Dense 후보에서 원문의 현실 진위가 아니라 `original query ↔ sourceText`의
직접 관련성만 분류하는 실제 selector의 가능성을 평가한다. 현실 경력·성과·근무 이력의
진위, 채용 요구사항 충족, 합격 가능성은 판단하지 않는다.

relation은 `DIRECT_MATCH`, `RELATED_CONTEXT`, `QUERY_CONFLICT`, `INSUFFICIENT` 네
가지다. `QUERY_CONFLICT`는 원문이 거짓이라는 뜻이 아니라 현재 질문의 구체적 의미와
방향이 다르다는 뜻이다. 기존 Gold는 평가 시에만 각각 `DIRECT_SUPPORT`, `RELATED`,
`CONTRADICTS`, `INSUFFICIENT`에서 위 relation으로 대응한다.

## 2. 동결할 실행 계약

`D0`은 B3 Dense Top20 원순서다. `D1`은 같은 후보 중 Top10만 단일 local instruction
model로 분류한 뒤 네 relation 순으로 stable partition하며, 같은 relation 내부 순서와
11~20위 Dense tail은 보존한다. 후보 추가·삭제, 복합 score, Typed Validation 결합은 없다.

`O10`은 Gold relation으로 Top10을 `DIRECT_SUPPORT → RELATED → CONTRADICTS →
INSUFFICIENT → UNJUDGED` 순서로 stable partition하고 11~20위는 Dense 위치와 순서를
그대로 둔 D1-addressable Oracle이다. 각 bucket 안에서도 Dense 순서를 유지한다. full
O1@20은 참고 ceiling일 뿐 capture 분모로 쓰지 않는다. metric `M`의 capture는
`(D1_M - D0_M) / (O10_M - D0_M)`이며, 분모가 0이면 `NOT_APPLICABLE`이고 음수나 1 초과
값을 clamp하지 않는다.

모델 입력은 original query와 Evidence `sourceText`뿐이다. Gold, answerability, category,
Gold Parent/relation/ID, Oracle 결과, C1 heading context는 금지한다. 공식 순서는
`Gold-free candidate/input freeze → model inference → output freeze/hash/verify → Gold join →
evaluation`이다. model output은 relation, 확률로 해석하지 않는 raw score, 제한된
reason code만 가진다. malformed/timeout은 relation으로 꾸미지 않고 실행 실패로 처리한다.

Gold relation은 candidate 전체를 exhaustive하게 판단하지 않는다. Gold에 없는 후보는
`UNJUDGED` 평가 상태로만 보존하며 `INSUFFICIENT`로 재라벨링하지 않는다. relation
accuracy와 macro F1은 judged candidate에 한정하고 coverage를 함께 보고한다.

핵심 Gate와 집계는 PRZ-030에서 동결한 parser-empty semantic core 79 query만 사용한다.
typed-overlap 14 query는 inventory를 보존하되 diagnostic weight 0이며 Typed Validation과
결합하지 않는다. valid `PARTIALLY_SUPPORTED`의 DIRECT-bearing aspect도 direct-positive
ranking 분모에 포함한다.

## 3. 사전 Capability Gate

결과 확인 전 다음 Gate를 고정한다. 모든 Safety와 Quality 조건을 통과하고, 추가 조건 중
하나 이상을 만족해야 한다.

- Safety: candidate identity parity 100%, source/provenance 변경 0, cross-parent merge 0,
  Gold-before-output access 0, SEALED FINAL semantic access/search 0
- Quality: rank1 DIRECT retention ≥ 98%, win > loss, user-macro Top1 개선 > 0,
  relation macro F1 ≥ 0.85
- 추가 조건 A: user-macro Direct Top1의 O10 headroom을 25% 이상 회수
- 추가 조건 B: D0 first-direct rank가 2~10이던 query를 D1 rank 1로 복구한 unique user
  bundle이 최소 3개
- 추가 조건 C: frozen comparator가 있을 때 authoritative `NOT_SUPPORTED` query의 최종
  ranked Top1이 predicted `DIRECT_MATCH`인 query 수·비율과 unique bundle 수가 감소

Recall@20은 동일 candidate set parity여야 한다. win/loss/tie는 direct-positive이면서
Top20에 DIRECT가 있는 query에서 D0와 D1의 first-DIRECT rank를 비교한다. D1 rank 숫자가
더 작으면 win, 더 크면 loss, 같으면 tie이고 retrieval miss는 별도다. rank1 retention은
D0 first-direct rank 1이 D1에서도 rank 1인 비율이며 현재 frozen semantic core 분모는
50건이다.

`NOT_SUPPORTED` false-positive는 전체 질문의 부재를 현실 경력 부재로 해석하지 않는다.
22 query에서 selector가 D0 Dense Top1 원문을 `DIRECT_MATCH`로 분류하는지와 D1 최종
ranked Top1이 predicted `DIRECT_MATCH`인지 각각 query 수·비율·unique bundle 수로
진단한다. FOUND/NONE 상태는 만들지 않는다. D0에는 relation classifier가 없으므로 직접
비교 가능한 frozen false-positive baseline도 없다. 따라서 추가 조건 C는 이번 D0/D1
비교에서는 `NOT_APPLICABLE`이며 PRZ-030의 judged risk 15 query/13 bundles를 D0 실측
FPR로 재해석해 PASS 근거로 쓰지 않는다.

PRZ-030의 `BUILD_SEMANTIC_VALIDATOR`는 실제 validator를 시험할 가치가 있다는 architecture
판정이지 PRZ-031 품질 PASS나 Production 승인 근거가 아니다.

## 4. Model Selection Gate

모델은 하나만 선택하며 local/self-hosted, 한국어·영어·혼합 지원, query/source relation
분류, exact model digest/revision/license/size 동결, Production dependency 0을 모두
만족해야 한다. 모델 쇼핑, 외부 유료 API, benchmark-specific fine-tuning, 결과 후 prompt
수정은 금지한다.

현재 호스트의 Ollama에는 embedding 전용 `bge-m3:latest`만 있다. repository의 과거
`qwen3:4b-instruct` harness는 재사용 가능한 transport이지만 해당 mutable tag의 exact
digest/revision/license가 보존되지 않았고 모델도 설치되어 있지 않다. 따라서 계약에 따라
`BLOCKED_MODEL_SELECTION`에서 중단한다. 별도 승인으로 적합한 exact local artifact가
준비되기 전에는 input/instruction/code freeze, 공식 inference와 metric evaluation을
실행하지 않는다.

## 5. 비범위와 보존 경계

Sparse, Parent Dense/Context, QueryPlanner/rewrite, RRF, FTS/BM25, MMR, Grounded Answer,
Typed 통합, Production 적용은 `NOT_RUN`이다. 새 dataset/query/retrieval 실험과 model
download도 하지 않는다.

허용 범위는 PRZ-031 문서, Registry, 향후 최소 evaluation-only runner/test와 ignored
`local/search-v3-evaluation/prz031/**`다. `src/main/**`, migration, dependency/build,
frontend, MCP, Docker, `v1.0.0`과 SEALED FINAL은 변경하지 않는다.

SEALED FINAL은 combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 유지한다.
