# PRIZM Search 최종 요약

## 한 문장 정의

PRIZM Search는 사용자가 등록한 이력서·포트폴리오·프로젝트 문서에서 질문과 관련된
확인 가능한 내용을 찾아 출처 문서와 위치와 함께 보여주는 Evidence-first Career
Search다.

경력의 사실 여부를 자동으로 YES/NO 판정하지 않는다. 검색 결과는 사용자가 원문을
확인할 수 있게 돕는 근거이며, 최종 판단을 대신하지 않는다.

## 문제

단순 의미 검색은 질문과 비슷한 문장을 잘 찾지만, 다음 차이를 스스로 구분하지 못한다.

- 직접 사용한 기술과 검토만 한 기술
- 본인이 구현한 기능과 다른 팀이 구현한 기능
- 실제 production 경험과 prototype 경험
- 질문의 숫자·metric과 비슷하지만 다른 성과
- 질문에 답하는 문장과 같은 페이지의 다른 프로젝트 문장

반대로 정답은 Dense Top20 안에 있는데 후속 filter나 consolidation이 먼저 버리는
경우도 있었다. 검색 품질을 높이려면 retrieval뿐 아니라 “왜 이 후보를 남겼는가”와
“사용자에게 어느 원문을 보여주는가”를 함께 다뤄야 했다.

## 접근

```text
BGE-M3 Dense 후보 검색
→ owner / ACTIVE version 격리
→ identifier·숫자 안전 조건
→ 실제 중복만 줄이는 consolidation
→ structured claim-support eligibility
→ 작은 범위의 ranking 보정
→ 안전한 fallback / numeric rescue
→ 질문과 직접 관련된 1~3문장 원문 표시
```

모든 단계는 같은 사용자의 현재 ACTIVE 문서만 다룬다. snippet은 생성하거나 요약하지
않고 원문에서 연속된 문장을 가져온다. 결과 선택과 표시 근거가 다르면 result chunk와
evidence chunk를 따로 기록한다.

## 가장 중요했던 발견

### 1. 개발셋 점수는 일반화 증거가 아니었다

72문항 development benchmark는 P0에서 P4까지 Top1 `57.14% → 82.14%`, Recall@5
`67.86% → 85.71%`, FPR `6.25% → 0%`로 좋아졌다. 그러나 새로운 48문항 holdout은
Top1 `50.00%`, Recall@5 `61.11%`, FPR `25.00%`였다.

이후에는 benchmark 점수에 맞춰 규칙을 더하기보다 독립 corpus, frozen manifest,
owner/ACTIVE isolation, query별 failure stage를 함께 확인했다.

### 2. 한동안 가장 큰 병목은 Dense retrieval 뒤에 있었다

SearchDecisionTrace를 만든 뒤 정답 후보가 Dense Top20에는 있지만 source
consolidation, eligibility, query-evidence consolidation, floor, localization에서
사라지는 사례를 구분할 수 있었다.

Production ingestion을 사용한 P8.1의 Judge-Realistic과 Retrieval-Stress 모두 Dense
Recall@20은 `100%`였다. 반면 최종 Recall@5는 각각 `62.50%`, `75.00%`, Negative
FPR은 `15.00%`, `75.00%`였다. 이 결과를 근거로 retrieval을 바꾸지 않고 P9
eligibility와 P10 localization에 집중했다.

### 3. Claim verification의 자연어 일반화는 별도 난제였다

Judge A/B/C에서는 새로운 action, metric, actor, negation 표현이 나올 때마다
deterministic evaluator의 경계가 흔들렸다. Judge C는 Dense R@20 `100%`와 Recall@5
`90%`를 기록했지만 FPR `20%`, displayed/localization `80%`로 Search Freeze 기준을
통과하지 못했다.

Safety-first ClaimVerifierV2와 local window preselector는 좁은 A/B/C replay에서
Positive `29/29`, FP `0/30`을 냈다. 하지만 Production 통합 뒤 더 넓은 P9/P10
negative에서 `5/20`, Stress negative에서 `2/8` false positive가 발생했고 Stress
Positive도 `20/20 → 17/20`으로 줄었다. 따라서 새 구조를 승격하지 않고 안정
`StructuredClaimSupportEvaluator` 경로로 되돌렸다.

## 대표 개선 사례

| 문제 | 바뀐 계약 | 확인된 효과 |
|---|---|---|
| 숫자·identifier | owner·ACTIVE corpus guard와 exact numeric rescue | P1 development FPR `6.25% → 0%` |
| 같은 PDF page의 다른 프로젝트가 합쳐짐 | page identity와 evidence identity 분리 | 실제 이력서 Spring Boot·Java source `2 → 4`, Docker·MySQL `2 → 3` |
| P11 뒤 반복 evidence 노출 | 같은 document/version의 강한 overlap claim만 축약 | Stress 결과 `5 → 3`, multi-project retention 유지 |
| 기술 목록이 사용 경험으로 인정되지 않음 | project-scoped 기술 선언과 직접 사용 문장 지원 | PostgreSQL `0 → 2`, OAuth2 `0 → 1` 최종 결과 복구 |
| direct support가 floor에서 탈락 | direct-support contract와 floor 연결 | score `0.425492` FCM 정답 복구, threshold 값은 유지 |
| expansion이 기술명을 잃음 | local direct evidence 우선, required anchor 보존 | FCM `result 108 → evidence 106`을 `108 → 108`로 복구 |
| 문제 문장만 snippet에 표시 | 설명 질문에 problem/action/result 1~3문장 우선 | 같은 chunk에서 해결 행동을 포함한 2문장 원문 표시 |

## 채택한 것과 채택하지 않은 것

채택한 핵심은 BGE-M3 Dense, owner/ACTIVE 격리, identifier guard, numeric rescue,
bounded evidence ranking, P11/P11.1 consolidation, 안정 structured claim support,
P10/P13/P14 localization이다.

PostgreSQL FTS, BGE-M3 Sparse, RRF, section/semantic chunking, 별도 BGE reranker, NLI,
GPT judge, Qwen verifier는 당시 구현과 평가 조건에서 Production 이득을 입증하지
못했다. 기술 자체의 가치가 없다는 뜻은 아니다. PRIZM의 frozen corpus에서는 오탐,
Positive 회귀, 추가 지연·메모리, 불완전 호출 같은 비용이 이득보다 컸다는 뜻이다.

ClaimVerifierV2도 연구 결과만 보면 유망했지만 Production 승격은 철회했다. 좁은
replay 성공보다 넓은 regression 실패를 최종 판단 근거로 삼았다.

## 현재 제품 경계

현재 안정 버전은 다음을 보장하려고 설계돼 있다.

- 다른 사용자의 문서와 inactive version을 검색 후보에서 제외한다.
- 질문과 관련된 후보를 Dense Top20에서 찾은 뒤 명백한 부정·미도입·actor·숫자/metric
  모순을 걸러낸다.
- 같은 page의 다른 프로젝트는 보존하고, 실제 overlap 또는 반복 claim만 줄인다.
- 사용자가 판단할 수 있도록 질문과 직접 관련된 extractive evidence와 문서 위치를
  보여준다.

다만 다음은 보장하지 않는다.

- 모든 이력서와 자연어 표현에서 같은 정확도
- 경력 사실 여부의 완전한 자동 판정
- 결과가 없을 때 경험이 없다는 결론
- 1~3문장만으로 문서 전체 맥락을 항상 전달하는 것

단일 keyword보다 주체·행동·대상을 포함한 자연어 질문에서 더 안정적이다. Frontend는
짧은 입력에 질문형 안내를 보여주지만, query를 자동으로 rewrite하거나 Backend 요청을
바꾸지 않는다.

## Competition baseline 고정 판단

독립 Judge C의 공식 판정은 `FAIL_SEARCH_FREEZE_HOLD`였다. 이후 Judge C를 포함한
replay 결과를 unseen 성능으로 다시 부르지 않는다.

현재 선택은 실패한 V2 통합을 더 튜닝하는 것이 아니라, 넓은 regression에서 더 안정적인
기존 Production 경로를 보존하고 PRIZM의 역할을 Evidence-first Search로 한정하는
것이다. 이는 Search Freeze PASS가 아니라, 대회 전 추가 검색 알고리즘 개선을 중단하고
안정 버전을 Competition baseline으로 고정하는 결정이다. claim verification을 다시
설계할 때는 새로운 독립 corpus와 P9/P10·실제 이력서 전체 회귀를 한 Gate로 묶어야
한다.

## 더 읽기

- [전체 실험과 판단 기록](PRZ-016-SEARCH-RND-HISTORY.md)
- [현재 Production pipeline](SEARCH-FINAL-ARCHITECTURE.md)
- Claim Verification Architecture Audit과 실패한 V2 Production 통합의 query-level
  evidence는 local-only 연구 artifact로 보존하며 공개 저장소에는 포함하지 않는다.
