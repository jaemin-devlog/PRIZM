# PRZ-016 검색 R&D 최종 기록

> **역사 기록:** 이 문서는 PRZ-016 연구 당시의 단계, 실패, rollback과 판단을 보존한다.
> `StructuredClaimSupportEvaluator`를 포함한 아래 "현재" 표현은 당시 snapshot을 뜻한다.
> 실제 현재 제품 검색은 [현재 검색 요약](SEARCH-FINAL-SUMMARY.md)을 따른다.

## 당시 최종 상태

- 당시 Competition baseline: 안정 `StructuredClaimSupportEvaluator` 경로
- Retrieval: BGE-M3 + pgvector exact Dense Top20
- ClaimVerifierV2: 연구 전용, Production 미채택
- Judge C 공식 판정: `HOLD`
- 당시 결정: 대회 전 추가 검색 튜닝 중단
- 제품 범위: Evidence-first Career Search

## 1. 목표

PRZ-016은 검색 점수를 한 번 높이는 작업이 아니었다. 개발용 질문에서는 좋아 보이던
변경이 처음 보는 문서와 질문에서도 유지되는지 확인하고, 실패했을 때 정답이 어느
단계에서 사라졌는지 설명할 수 있는 검색 구조를 만드는 작업이었다.

당시 PRIZM Search의 제품 범위는 다음과 같았다.

> 사용자가 등록한 이력서·포트폴리오·프로젝트 문서에서 질문과 관련된 확인 가능한
> 내용을 찾아 출처 문서와 위치와 함께 보여주는 Evidence-first Career Search

PRIZM은 경력의 사실 여부를 자동으로 확정하는 YES/NO 판정기가 아니다. 결과가 있다는
것은 등록 문서에서 관련 내용을 찾았다는 뜻이며, 결과가 없다고 해서 사용자의 경험이
없다고 단정할 수도 없다.

이 문서는 각 단계를 `문제 → 변경 → Before → After → 판정 → 다음 결정` 순서로
정리한다. 평가셋이 다르면 수치를 직접 비교하지 않는다. Judge A와 B처럼 이후 수정에
사용한 데이터는 regression set이며, 최종 unseen 성능으로 부르지 않는다.

## 2. 초기 문제와 Baseline

PRZ-001은 검색 평가의 분모, split, 결과 저장 방식을 먼저 고정했다. 당시 30문항
PostgreSQL·pgvector·BGE-M3 실행은 전체 Direct MRR@20 `0.8551`, 분리 TEST 10문항
`0.7917`을 기록했다. 이 값은 PRZ-016의 72문항 개발 benchmark와 데이터·metric이
다르므로 성능 추세에 합치지 않는다.

PRZ-008은 Dense 검색의 안전 경계를 정리하고 여러 후보를 비교했다. owner와 현재
ACTIVE version 격리, Dense Top20, 최종 최대 5건, `0.50` floor, 제한적 exact-token
rescue, 작은 문자열 boost가 이때 자리 잡았다. PostgreSQL FTS, BGE-M3 Sparse, RRF,
section 기반 chunking, 별도 BGE reranker도 시험했지만 당시 실문서 40문항에서 오탐,
순위 회귀, 비용 증가가 이득보다 컸다. 이 결과가 PRZ-016의 출발점이었다.

### P0 — Development baseline

- 문제: 현재 검색의 강점과 실패 유형을 같은 기준으로 측정할 기준선이 없었다.
- 변경: 56개 Positive와 16개 Negative, 총 72문항을 고정하고 Top1, Recall, MRR,
  Negative FPR을 함께 기록했다.
- Before: 비교 가능한 PRZ-016 기준선 없음.
- After: Top1 `57.14%`, Recall@5 `67.86%`, MRR@5 `0.6146`, Negative FPR `6.25%`.
- 판정: `BASELINE`.
- 다음 결정: 숫자·identifier 누락부터 좁게 다룬다.

## 3. Development benchmark에서의 개선

### P1 — Numeric / Identifier

- 문제: 숫자와 강한 identifier가 질문에 있어도 의미 점수만으로 무관 후보가 남거나
  정답이 경계 밖으로 밀렸다.
- 변경: owner·ACTIVE corpus 안의 identifier guard와 exact numeric rescue를 추가했다.
- Before: P0 Top1 `57.14%`, Recall@5 `67.86%`, FPR `6.25%`.
- After: Top1 `60.71%`, Recall@5 `71.43%`, MRR@5 `0.6503`, FPR `0%`.
- 판정: `ADOPTED`.
- 다음 결정: 새 후보를 만들지 않고 기존 후보의 근거 품질을 보정한다.

### P2 — Evidence reranking

- 문제: Dense가 정답을 회수해도 질문에 직접 답하는 문장이 Top1이 아닌 경우가 있었다.
- 변경: identifier, core term, 숫자, evidence quality를 작은 범위 안에서만 점수에
  반영했다. Dense는 계속 주 신호로 남겼다.
- Before: P1 Top1 `60.71%`.
- After: Top1 `67.86%`, Recall@5 `71.43%`, MRR@5 `0.6935`, FPR `0%`.
- 판정: `ADOPTED`.
- 다음 결정: 질문 표현 차이를 제한적으로 보완한다.

### P3 — Query understanding

- 문제: 같은 경험을 묻는 자연어 표현 차이 때문에 최초 질의가 빈 결과가 되기도 했다.
- 변경: 일반 질문과 경험 요청에만 제한된 fallback variant를 사용하고, 원래 질문의
  강한 anchor를 보존했다.
- Before: P2 Top1 `67.86%`, Recall@5 `71.43%`.
- After: Top1 `75.00%`, Recall@5 `78.57%`, MRR@5 `0.7649`, FPR `0%`.
  fallback은 72문항 중 7문항에서 사용됐다.
- 판정: `ADOPTED_WITH_BOUNDS`.
- 다음 결정: 선택된 결과가 실제 답변 위치를 보여주는지 분리해 평가한다.

### P4 — 초기 Evidence localization

- 문제: 올바른 chunk를 찾고도 사용자에게는 질문과 직접 관련된 문장이 보이지 않았다.
- 변경: 동일 ACTIVE version 안에서 1~3개의 extractive sentence를 선택하고, 필요한
  경우에만 인접 evidence를 확장했다.
- Before: P3 Top1 `75.00%`, Recall@5 `78.57%`.
- After: Top1 `82.14%`, Recall@5 `85.71%`, MRR@5 `0.8363`, FPR `0%`.
- 판정: `ADOPTED`.
- 다음 결정: 개발셋을 닫고 unseen holdout으로 일반화를 확인한다.

## 4. 일반화 실패 발견

### P5 — Final holdout

- 문제: 개발셋 개선이 새로운 문서와 질문에도 유지되는지 알 수 없었다.
- 변경: 수정에 사용하지 않은 48문항 holdout을 한 번 실행했다.
- Before: Development P4 Top1 `82.14%`, Recall@5 `85.71%`, FPR `0%`.
- After: Holdout 36 Positive·12 Negative에서 Top1 `50.00%`, Recall@5 `61.11%`,
  MRR@5 `0.5509`, FPR `25.00%`. owner·ACTIVE isolation은 통과했다.
- 판정: `FAIL`.
- 다음 결정: holdout에 맞춰 즉시 튜닝하지 않고, retrieval을 바꿔야 하는지부터
  별도 실험한다.

이 시점이 PRZ-016의 전환점이었다. 개발셋의 높은 수치는 일반화 근거가 아니었다.
그 뒤로는 점수보다 실패 단계와 안전 회귀를 먼저 기록했다.

## 5. Retrieval 병목 여부 검증

### P6 — Retrieval shadow

- 문제: Holdout 실패가 Dense retrieval의 한계인지 판단할 근거가 부족했다.
- 변경: PostgreSQL lexical branch, Dense+FTS RRF, literal gate를 Production 변경 없이
  shadow로 비교했다.
- Before: Dense 단독 실패 원인 불명.
- After: candidate recall 순증은 `0%p`였고, 기존 72문항에서 5개 회귀가 생겼다.
- 판정: `NO_GO`.
- 다음 결정: 더 많은 검색 채널보다 candidate 이후 단계를 관찰한다.

### P7 — Semantic / NLI / LLM 계열

- 문제: 문자열 규칙을 semantic verifier로 대체하면 일반화가 나아지는지 확인할
  필요가 있었다.
- 변경: GPT evidence selector, mDeBERTa·KLUE NLI, deterministic localizer와 NLI,
  Qwen evidence sufficiency judge를 평가 전용으로 시험했다.
- Before: rule 기반 판단의 한계는 보였지만 대안의 비용과 안전성은 미확인.
- After: GPT는 FPR을 0으로 낮췄으나 완료된 호출에서 Positive 2건이 회귀하고 4건은
  완료되지 않았다. strict NLI는 핵심 support 5건을 모두 놓쳤다. Qwen result-level
  set judge는 기존 정답 21건 중 20건을 유지했지만 FPR `3/12`가 남았다.
- 판정: `NO_GO_AS_PRODUCTION_FILTER`.
- 다음 결정: model을 더 붙이기 전에 Production 단계별 trace를 만든다.

### P7-B — 독립 일반화와 stage ceiling

- 문제: 최종 실패만으로는 Dense가 못 찾은 것인지, 이후 단계가 버린 것인지 알 수
  없었다.
- 변경: 독립 48문항과 corpus/chunk oracle을 만들고 단계별 ceiling을 계산했다.
- Before: 전체 실패를 retrieval 실패로 묶어 해석하기 쉬웠다.
- After: 36개 Positive의 corpus·chunk oracle은 `36/36`이었다. 최종 Recall@5는
  `58.33%`, Negative FPR은 `41.67%`였다. Positive 실패 14건의 최초 손실은 dense
  floor 7, query-evidence consolidation 4, source consolidation 1, negation 2였다.
  localization 손실도 3건 있었다. floor를 제거한 shadow는 Recall@5를 `75.00%`로
  올렸지만 FPR이 `83.33%`로 뛰었다.
- 판정: `RETRIEVAL_ONLY_HYPOTHESIS_REJECTED`.
- 다음 결정: threshold 완화가 아니라 관찰 가능한 평가 기반을 만든다.

### SearchDecisionTrace와 Fresh Generalization Benchmark V2

- 문제: candidate가 처음 사라진 지점과 result/evidence chunk 차이를 query 단위로
  재현할 수 없었다.
- 변경: query variant, Dense 후보, source group, eligibility reason,
  query-evidence group, ranking 성분, 최종 result, localization을 구조화된 trace로
  기록했다. Production response와 순위에는 손대지 않았다.
- Before: 수동 로그와 사후 추정에 의존했다.
- After: P7-B `48/48`, Fresh `44/44`에서 trace와 Production 결과 parity를 확인했다.
  Fresh는 4명, ACTIVE 문서 8개, inactive version 4개, Positive 24개, Negative 20개로
  동결했다. Candidate Recall@20은 `100%`, Top1·Recall@5는 `87.50%`, FPR은 `50%`,
  localization correctness는 `79.17%`였다.
- 판정: `EVALUATION_FOUNDATION_ADOPTED`.
- 다음 결정: 실제 업로드·추출·chunking·embedding·ACTIVE 전환을 통과하는 평가로
  옮긴다.

## 6. Production ingestion 기반 Eligibility / Localization 개선

### P8.1 — Judge-Realistic / Retrieval-Stress

- 문제: direct chunk seed 평가는 ingestion과 문서 밀도의 영향을 보여주지 못했다.
- 변경: Production 업로드부터 BGE-M3 indexing, ACTIVE 전환, SearchService, snippet까지
  실제 경로로 두 평가를 실행했다.
- Before: Fresh benchmark의 candidate-level 관찰만 확보.
- After: Judge-Realistic은 4명, 논리 문서 8개, 12 versions, ACTIVE chunks 8개,
  Positive 16개, Negative 20개였다. Candidate Recall@20 `100%`, Recall@5 `62.50%`,
  FPR `15.00%`, localization `43.75%`였다. Retrieval-Stress는 4명, 10쪽 PDF 4개,
  ACTIVE chunks 120개, Positive 20개, Negative 8개였다. Dense R@20 `100%`,
  Recall@5 `75.00%`, FPR `75.00%`, localization `45.00%`였다.
- 판정: `FAIL_WITH_CLEAR_BOTTLENECKS`.
- 다음 결정: Dense는 그대로 두고 eligibility와 localization을 분리해 고친다.

### P9 — Structured Claim Support Eligibility

- 문제: 관련 단어만 있는 문장, 부정·미도입·다른 actor·틀린 숫자/metric을 실제 경험으로
  통과시키는 반면, 직접 근거도 유한한 문법을 벗어나면 제거됐다.
- 변경: query requirement와 local claim window를 결합한 deterministic
  `StructuredClaimSupportEvaluator`를 Production eligibility에 연결했다.
- Before: Judge Recall@5 `62.50%`, FPR `15.00%`; Stress Recall@5 `75.00%`,
  FPR `75.00%`.
- After: Judge Recall@5 `87.50%`, FPR `0%`; Stress Recall@5 `100%`, FPR `0%`.
- 판정: `ADOPTED`.
- 다음 결정: 선택 정확도와 별개로 evidence 표시를 고친다.

### P10 — Evidence localization

- 문제: 올바른 result chunk를 골라도 displayed evidence가 질문의 직접 근거가 아닐 수
  있었다.
- 변경: result correctness, displayed evidence correctness, localization correctness를
  분리하고 local sentence scoring과 bounded expansion을 정리했다.
- Before: Judge displayed/localization `68.75%`; Stress displayed `65.00%`,
  localization `60.00%`.
- After: Judge displayed/localization `87.50%`; Stress displayed/localization `100%`.
  선택 결과와 FPR은 바뀌지 않았다.
- 판정: `ADOPTED`.
- 다음 결정: 합성 평가에서 보이지 않던 실제 이력서 구조를 확인한다.

## 7. 실제 이력서에서 발견된 문제

실제 이력서를 Production-like Docker 환경에 올려 read-only trace를 실행했다. 이 단계는
개인 문서를 저장소 fixture로 복사하지 않았고, 원인은 합성 테스트로만 재현했다.

세 가지 문제가 두드러졌다.

1. 같은 PDF page/sourceIndex라는 이유로 서로 다른 프로젝트의 chunk가 하나의 evidence로
   합쳐졌다.
2. 프로젝트 기술 목록이나 직접 사용 문장이 P9의 action/entity 결속을 통과하지 못했다.
3. 올바른 result chunk를 골라도 expansion이나 짧은 snippet 때문에 사용자가 직접 근거를
   보지 못했다.

이 결과는 Dense를 바꾸기보다 consolidation, eligibility, localization의 계약을 좁게
수정해야 한다는 근거가 됐다.

## 8. P11~P14 수정 과정

### P11 — Source Consolidation Redesign

- 문제: `same version + PAGE + sourceIndex`가 evidence identity로 쓰여, 같은 페이지의
  다른 프로젝트·claim이 대표 1건으로 축약됐다.
- 변경: page identity와 evidence identity를 분리했다. 같은 source location이라도
  실제 source boundary/content overlap이 있어야 합치도록 했다.
- Before: 실제 이력서의 Spring Boot·Java 근거가 source consolidation 뒤 각각 2건,
  Docker·MySQL은 각각 2건 남았다.
- After: 각각 `4/4/3/3`으로 보존됐다. 다만 Stress `RS-S02-P02` 결과가 3건에서 5건으로
  늘고 normalized duplicate 2건이 노출됐다.
- 판정: `PARTIAL_PASS`.
- 다음 결정: page-level 규칙을 되돌리지 않고 query-evidence duplicate만 다룬다.

### P11.1 — Duplicate Evidence Consolidation

- 문제: P11이 보존한 후보 중 같은 document/version의 실질적으로 같은 claim이 최종
  결과에 반복됐다.
- 변경: strong shared content span과 query anchor가 함께 있는 경우만 query-evidence
  duplicate로 축약했다.
- Before: `RS-S02-P02` 5건.
- After: 3건으로 복구했고 실제 이력서 retention `4/4/3/3`은 유지했다.
- 판정: `ADOPTED`.
- 다음 결정: 단순 기술 사용 질문의 eligibility 경계를 확인한다.

### P12 — Simple Tech Usage Eligibility

- 문제: 프로젝트 기술 stack이나 기술과 직접 연결된 사용 문장이
  `ACTION_NOT_SUPPORTED` 또는 `ENTITY_NOT_BOUND_TO_ACTION`으로 거절됐다.
- 변경: project-scoped technology declaration 또는 entity와 직접 행동이 결속된 문장을
  support로 인정했다. 부정·미도입·다른 actor는 그대로 차단했다.
- Before: 실제 이력서 PostgreSQL eligibility/final `0/0`, OAuth2 `0/0`, Java `2/2`,
  Redis `1/1`.
- After: PostgreSQL `2/2`, OAuth2 `1/1`, Java `4/4`, Redis `2/2`.
- 판정: `ADOPTED`.
- 다음 결정: evaluator가 이미 직접 support로 판정한 후보와 dense floor의 계약을 맞춘다.

### P12.1 — Direct-Support Floor Bypass

- 문제: FCM 정답이 Dense rank 1, score `0.425492`, `SUPPORTED + directSupport=true`인데
  action/numeric requirement가 비어 있다는 이유로 floor에서 제거됐다.
- 변경: 실제 claim 질문의 direct support는 requirement list의 모양만으로 bypass를
  잃지 않도록 했다. threshold 숫자는 바꾸지 않았다.
- Before: `BELOW_DENSE_FLOOR`, 최종 결과 없음.
- After: 정답 chunk 108이 eligibility를 통과해 최종 결과에 남았다.
- 판정: `ADOPTED`.
- 다음 결정: 선택된 근거가 expansion 뒤에도 유지되는지 확인한다.

### P13 — Evidence Expansion Safety

- 문제: FCM result chunk 108이 정답인데 expansion이 FCM 없는 chunk 106으로 이동했다.
- 변경: selected chunk에 충분한 직접 근거가 있으면 확장하지 않고, 확장 후보는 query의
  필수 entity/identifier anchor를 유지하도록 했다.
- Before: FCM `result 108 → evidence 106`.
- After: `result 108 → evidence 108`; Spring Boot 등 기존 정상 사례도 anchor를 유지했다.
- 판정: `ADOPTED`.
- 다음 결정: 같은 chunk 안에서 문제 문장만 고르고 해결 문장을 빠뜨리는 경우를 다룬다.

### P14 — Claim-Complete Snippet

- 문제: 문서 처리 실패 질문에서 incident 문장만 보이고 ACTIVE 전환·복구 행동과 이전
  검색 유지 결과가 빠졌다.
- 변경: 설명형 질문은 problem-only보다 연속된 problem+action, action+result 또는
  problem+action+result 1~3문장 window를 우선했다. 생성·요약은 하지 않았다.
- Before: 문제 문장만 표시.
- After: 같은 result/evidence chunk 106, expansion 없이 해결 행동을 포함한 2문장
  extractive span을 표시했다.
- 판정: `ADOPTED`.
- 다음 결정: 완전히 새로운 corpus에서 claim verification 일반화를 다시 시험한다.

## 9. Judge A/B/C가 보여준 한계

### Judge A — 최초 unseen, 이후 regression set

Judge A는 3명, 문서 6개, Positive 9개, Negative 10개였다. 최초 실행에서 Dense
R@20은 `100%`였지만 Recall@5와 selected correctness는 `77.78%`, FPR은 `10%`,
displayed/localization은 `66.67%`였다. Positive 2건은 eligibility, Positive 1건은
localization에서 실패했고, OTHER_ACTOR 1건이 통과했다.

이 결과로 claim scope와 actor/action binding을 수정했다. 같은 corpus를 다시 실행한
결과 selected correctness `100%`, FPR `0%`가 됐지만, 이 시점부터 Judge A는 unseen이
아닌 regression set이다. 기존 GT-localization 이슈 때문에 displayed/localization은
`88.89%`로 남았다.

#### Final Fix — Claim Scope & Actor Binding

- 문제: unrelated not-adopted 문맥이 정답 claim에 붙고, 복구 행동은 놓치며, 다른
  actor의 구현은 본인 경험처럼 통과했다.
- 변경: contradiction과 missing reason을 query-related local claim window에서만
  모으고, actor와 action을 같은 scope에서 결속했다.
- Before: Judge A Positive 2건 누락, OTHER_ACTOR FP 1건.
- After: 세 사례 모두 기대 동작으로 복구. Judge A selected `100%`, FPR `0%`.
- 판정: `ADOPTED`; Judge A는 이후 regression set으로 전환.
- 다음 결정: 새로운 corpus인 Judge B로 일반화를 다시 확인한다.

### Judge B — 최초 unseen, 이후 regression set

Judge B의 최초 실행은 Positive 10개, Negative 10개였다. Dense R@5/10/20은 모두
`100%`, FPR은 `0%`였지만 Recall@5·selected·displayed·localization은 `70%`였다.
정답 3건이 각각 unknown/부분 metric, floor contract, unknown action 해석 때문에
eligibility에서 사라졌다.

Positive eligibility boundary를 조정한 뒤 같은 corpus는 모든 Positive/evidence metric
`100%`, FPR `0%`를 기록했다. 이 수치는 수정 후 regression 결과이지 새로운 unseen
성능이 아니다.

#### Final Positive Eligibility Generalization

- 문제: 직접 근거가 있어도 evaluator가 모르는 action/metric 표현이면 자동으로
  거절되는 경계가 남아 있었다.
- 변경: entity, 숫자, subject가 하나의 affirmative window에 결속되고 명시적 모순이
  없을 때, vocabulary 미인식만으로는 reject하지 않도록 decision boundary를 조정했다.
- Before: Judge B Positive `7/10`, FPR `0/10`.
- After: 같은 corpus Positive `10/10`, FPR `0/10`.
- 판정: `ADOPTED_AS_REGRESSION_FIX`.
- 다음 결정: Judge B에 추가 튜닝하지 않고 독립 Judge C를 실행한다.

### Judge C — 독립 시험

Judge C는 3명, ACTIVE 문서 6개, Positive 10개, Negative 10개였다. 최초 실행에서 Dense
R@5/10/20은 모두 `100%`, Top1·Recall@5·selected correctness는 `90%`,
displayed/localization은 `80%`, FPR은 `20%`였다. owner·ACTIVE isolation, multi-project
retention, duplicate audit은 통과했다.

실패는 eligibility 3건과 localization 1건이었다. 직접 구현·배포를 표현한 Positive가
유한 action/entity/state 문법에 맞지 않아 거절됐고, 외부 actor와 본인 미수행을 함께
쓴 Negative 2건은 통과했다. 복구 질문 1건은 다음 문장의 행동과 결과를 snippet에
포함하지 못했다.

Judge C의 공식 판정은 `FAIL_SEARCH_FREEZE_HOLD`다. 이 corpus를 보고 Production
규칙을 추가하지 않았다. 이후 architecture shadow와 replay 산출물은 연구·회귀 결과로
분리한다.

Judge B/C 디렉터리의 현재 `judge-*-results.json`은 후속 수정 뒤 다시 실행한 regression
결과를 담고 있다. 최초 unseen 수치와 판정은 각 디렉터리의 `evidence.md`에 보존돼 있다.
따라서 현재 JSON의 B `100%`, C selected `100%`·FPR `0%`를 최초 unseen 결과로
해석하지 않는다. 이 문서는 최초 시험은 `evidence.md`, 후속 상태는 replay JSON이라는
실행 시점 차이를 명시해 숫자 충돌을 피했다.

세 Judge에서 같은 문제가 반복됐다. Dense는 정답 후보를 자주 Top20 안에 넣었지만,
새로운 자연어 표현이 나올 때마다 deterministic claim parser의 Positive 허용 문법과
actor/negation scope가 흔들렸다.

## 10. ClaimVerifierV2 연구와 Production 미채택 이유

### Claim Verification Architecture Audit

당시 Production claim-support core는 `StructuredClaimSupportEvaluator` 876 LOC,
`QueryClaimRequirements` 80 LOC, `ClaimSupportDecision` 38 LOC였다. evaluator 하나가
query 분류, entity/action/actor binding, negation, adoption/state, metric, numeric,
local window, positive support, contradiction, fallback을 함께 맡았다.

29 Positive·30 Negative shadow에서 현재 deterministic D0는 `28/29`, FP `3/30`이었다.
Safety-first D1은 `28/29`, FP `0/30`이었다. local Qwen D2는 `26/29`, FP `2/30`, 평균
`679.31ms`였고, D1 terminal 뒤 uncertain만 Qwen으로 보내는 hybrid는 `28/29`, FP
`0/30`이었다. semantic verifier가 D1보다 Positive를 더 복구하지 못했으므로 Production
후보에서 제외했다.

### Safety-first ClaimVerifierV2 spike

- 문제: 현재 evaluator를 더 확장하지 않고 hard contradiction과 direct support만 맡는
  작은 계약이 가능한지 확인할 필요가 있었다.
- 변경: evaluation-only `ClaimVerifierV2`를 257 LOC로 만들었다.
- Before: D0 `28/29`, FP `3/30`.
- After: V2 `27/29`, FP `0/30`. 직접 근거가 들어간 window만 보면 `27/27`이었다.
- 판정: `PARTIAL_PASS`.
- 다음 결정: 남은 2건을 verifier가 아닌 local window preselection 문제로 분리한다.

### Local Window Preselection spike

- 문제: chunk 안에 직접 근거가 있어도 기존 window가 latency 또는 incident 문장만
  선택했다.
- 변경: candidate chunk 내부의 연속 1~3문장만 열거하고 identifier, 숫자/unit,
  lexical binding, 설명 질문의 연속 span을 일반 규칙으로 평가했다.
- Before: V2 `27/29`, FP `0/30`.
- After: `29/29`, FP `0/30`.
- 판정: `EVALUATION_PASS`.
- 다음 결정: 한 번만 Production에 통합하고 더 넓은 frozen regression으로 판단한다.

### 실패한 Production integration과 rollback

고립된 V2 replay는 `29/29`, FP `0/30`이었고 Judge A/B/C E2E도 통과했다. 그러나 더
넓은 P9/P10 계약에서 Judge Negative `5/20`, Stress Negative `2/8`이 false positive가
됐다. Stress Positive도 `20/20`에서 `17/20`으로 줄었고, 실제 이력서 PostgreSQL의
ORIGINAL eligibility가 비었다.

이 결과는 좁은 A/B/C replay의 성공이 Production 승격 근거로 충분하지 않다는 사실을
보여줬다. 새 예외를 더하지 않고 통합을 `FAIL`로 판정했다. Production의
`ClaimVerifierV2`, `LocalClaimWindowPreselector`, `ClaimVerificationDecision`과
`CompositeSearchProfile` wiring은 되돌렸다. 현재 `src/main`은 안정
`StructuredClaimSupportEvaluator` 경로를 사용하며, V2와 preselector는
`src/searchEvaluation`의 연구 artifact로만 남아 있다.

## 11. 당시 안정 버전

당시 as-built 흐름은 다음과 같다. 당시 세부 계약은
[과거 통합 아키텍처](history/2026-08-search-integration-architecture.md)에 보존했다.

```text
Query
  → BGE-M3 embedding
  → owner / ACTIVE scoped exact Dense Top20
  → identifier corpus guard
  → source consolidation
  → StructuredClaimSupportEvaluator eligibility
  → query-evidence consolidation
  → bounded ranking / reranking
  → post-filter, limited fallback, numeric rescue
  → exact presentation duplicate 제거 / Top5
  → expansion safety / extractive 1~3문장 localization
  → API response
```

최종 실제 이력서 regression은 P11~P14의 source retention, simple technology usage,
direct-support floor, expansion safety, claim-complete snippet을 다시 확인했다. Final
Positive Eligibility Generalization 시점에는 해당 read-only audit 5개가 통과했다.
실패한 V2 통합 뒤에는 V2 wiring을 제거하고 안정 evaluator 경로로 복구했다. 실제
이력서 원문을 저장소 fixture로 남기지는 않았다. 최종 수동 브라우저 확인의 screenshot이나
machine-readable 결과도 versioned artifact에는 없으므로, 이 문서는 read-only audit을
검증 근거로 쓰고 수동 확인을 별도 PASS 수치로 계산하지 않는다.

검색 결과 Presentation UI도 별도로 다듬었다. 결과 ID·순서·개수와 Backend API는
건드리지 않고, 사용자 용어를 일상적인 표현으로 바꿨다. 단순 기술 질문은 질문에 들어
있는 기술명이 실제로 포함된 1~2줄을 먼저 보여주고, 복합 질문은 해결 과정을 담은
1~3문장을 유지한다. 자세한 앞뒤 내용은 `주변 내용`에서 확인한다. 이 변경은 검색 품질
metric 개선으로 계산하지 않는다.

## 12. Competition baseline 고정 판단

두 판단을 구분해야 한다.

- 독립 Judge C의 공식 Search Freeze 판정은 `HOLD`였다. 이후 새로운 독립 Judge가
  안정 rollback 버전을 통과했다는 artifact는 없다.
- 반면 실패한 V2 통합보다 당시 안정 버전이 넓은 P9/P10·실제 이력서 계약에서 더
  안전하다는 근거는 있다. 따라서 운영 선택은 현재 버전을 보존하고, 새로운 rule이나
  verifier를 계속 붙이지 않는 것이다.

즉, 당시 상태는 “모든 자연어 claim을 해결했으므로 Search Freeze를 통과했다”가 아니다.
대회 전 추가 검색 튜닝을 중단하고, 검증된 안정 경로를 Competition baseline으로 고정한
선택이다. claim verification을 다시 열려면 새로운 architecture와 넓은 frozen suite를
함께 준비해야 한다.

## 13. 남은 한계

- 새로운 자연어 표현의 action, metric, actor, negation scope를 deterministic 규칙으로
  일반화하는 일은 여전히 어렵다.
- 단일 keyword보다 주체·행동·대상을 포함한 자연어 질문에서 더 안정적이다. UI는 짧은
  입력에 질문형 안내를 보여주지만 query를 자동으로 바꾸지 않는다.
- 관련 문서를 반환하는 것과 질문의 사실 여부를 확정하는 것은 다르다. 사용자는 원문과
  출처를 보고 판단해야 한다.
- 1~3문장 localization은 extractive이며 document 전체의 맥락을 항상 담지 못한다.
- Judge A/B는 수정에 사용된 regression data다. 수정 뒤 100% 결과를 unseen 성능으로
  표현하면 안 된다.
- Judge C의 Dense R@20 `100%`는 해당 20문항·6 ACTIVE 문서에서의 관측값일 뿐, 모든
  문서에서의 retrieval 보장이 아니다.
- PDF viewer, 해당 페이지 이동, highlight는 이 기록의 구현 범위가 아니다.

## 채택·미채택 기술 요약

| 접근 | 목적 | 결과 | 최종 상태 | 이유 |
|---|---|---|---|---|
| BGE-M3 Dense | 의미 기반 Top20 후보 회수 | Fresh와 P8.1, Judge A/B/C에서 높은 candidate recall을 관측 | 채택 | owner·ACTIVE scoped exact pgvector 검색의 주 신호 |
| PostgreSQL FTS | lexical 후보 보강 | 제한된 query만 도왔고 같은 순이득에 추가 조회와 회귀 발생 | 미채택 | 당시 query 구성과 corpus에서 비용 대비 이득 부족 |
| BGE-M3 Sparse | lexical/subword 후보 보강 | Recall은 올랐지만 무근거 오탐이 크게 증가 | 미채택 | sparse overlap을 독립 eligibility로 쓰기 어려웠음 |
| RRF | Dense와 lexical/sparse 결합 | 일부 정답을 복구했지만 무관 후보와 순위 회귀도 증가 | 미채택 | 안전한 candidate admission 조건을 증명하지 못함 |
| semantic/section chunking | 문서 구조 보존 | 특정 문항은 복구했으나 짧은 조각·문맥 손실·Kafka 오탐 발생 | 미채택 | 현재 ingestion 계약 전체를 바꿀 근거 부족 |
| bounded evidence reranker | 직접 근거를 상위로 이동 | Development Top1 개선, 작은 조정 범위 유지 | 채택 | Dense 주 신호를 보존하고 새 후보를 만들지 않음 |
| exact numeric rescue | 숫자 경계 정답 복구 | FPR 증가 없이 개발 benchmark 개선 | 채택 | owner·ACTIVE·정확한 숫자 경계로 제한 |
| identifier guard | 강한 identifier 안전성 | 없는 identifier의 semantic 유사 결과 차단 | 채택 | corpus 존재 여부를 owner·ACTIVE 범위에서 확인 |
| NLI | entailment 기반 support 판정 | task/window 불일치로 핵심 Positive를 다수 놓침 | 연구 종료 | 당시 local evidence와 career sufficiency에 맞지 않음 |
| GPT evidence judge | 후보 선택과 FPR 감소 | FPR 0, Positive 회귀와 미완료 호출 발생 | 미채택 | 안정성·완결성·외부 비용 계약 부족 |
| Qwen verifier | local semantic sufficiency | D2 `26/29`, FP `2/30`, 평균 `679.31ms` | 미채택 | safety-first deterministic보다 이득 없이 비용 증가 |
| ClaimVerifierV2 | safety-first claim verification | 좁은 replay `29/29`, FP `0/30`; 넓은 P9/P10에서 FP·Positive 회귀 | 연구 전용 | Production regression으로 통합 철회 |
| source consolidation | 실제 중복 제거와 다른 claim 보존 | P11/P11.1에서 page identity와 evidence identity 분리 | 채택 | 같은 page의 다른 프로젝트를 보존하면서 강한 overlap만 축약 |
| Structured claim support | 부정·미도입·actor·숫자/metric 안전성 | P9에서 P8.1 FPR을 0으로 낮춤, 새 표현 일반화 한계도 확인 | 당시 채택·안정 경로 | 넓은 frozen regression이 V2보다 안정적 |
| evidence localization | 질문 관련 원문 표시 | P10/P13/P14에서 selection과 표시를 분리해 개선 | 채택 | 결과 ID·순위는 유지하고 extractive evidence만 표시 |

## 관련 문서

- [현재 Production 검색 구조](SEARCH-FINAL-ARCHITECTURE.md)
- [5분 요약](SEARCH-FINAL-SUMMARY.md)
- [PRZ-001 평가 정합성](../PRZ-001-search-evaluation-integrity/evidence.md)
- [PRZ-008 검색 비교](../PRZ-008-search-evidence-reliability/evaluation-comparison.md)
- [PRZ-016 phase index](spec.md)
