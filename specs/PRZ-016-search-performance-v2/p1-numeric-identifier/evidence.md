# P1 Numeric + Strong Identifier Evidence

> 역사 보존: 초기 `PRZ-015 Evidence`를 PRZ-016 P1으로 이동했다. 아래 결과는
> 원래 실행 기록을 유지한다.

검증일: 2026-08-14

## Focused Gate

정상 `USER` 로그인과 해당 사용자의 COMPLETED ACTIVE 이력서·포트폴리오로 검증했다.

- 기존 실패 numeric 5건: 5/5 `EVIDENCE_FOUND`, 숫자+단위가 포함된 원문 snippet 확인
- 기존 성공 numeric 3건: 3/3 기존 결과 유지
- strong identifier positive 5건: P1 전 결과의 state·chunk 순서·score·distance 유지
- GraphQL/Kubernetes/Kafka: 3/3 `NO_RELEVANT_RESULTS`
- 4,401회/676건/2,330행 near miss: 3/3 `NO_RELEVANT_RESULTS`
- 반환 결과의 document/version은 모두 현재 USER의 ACTIVE version과 일치

상세 실행 결과는 [focused-results.json](focused-results.json)에 기록했다.

## 동일 72-query Benchmark V2

P0의 evaluation dataset과 ground truth를 수정하지 않고 재실행했다.

| 지표 | Before | After |
|---|---:|---:|
| Top1 Accuracy | 57.14% | 60.71% |
| Recall@3 | 66.07% | 69.64% |
| Recall@5 | 67.86% | 71.43% |
| MRR@5 | 0.6146 | 0.6503 |
| Negative FPR | 6.25% | 0% |
| Warm average | 291.675 ms | 220.845 ms |
| Warm P95 | 341.154 ms | 285.769 ms |
| 실패 질의 | 25 | 22 |
| NUMERIC_IDENTIFIER 실패 | 5 | 3 |

새로 실패한 기존 PASS 질의는 0건이다. D03·D07과 GraphQL negative(F06)가 strict
benchmark 기준에서 PASS로 전환됐다. D01·D02·D08은 실제 숫자 근거를 반환하지만 고정
ground-truth의 문서·페이지 조건과 달라 `NUMERIC_IDENTIFIER` 실패로 남았다.

전체 결과는 [benchmark-results.json](benchmark-results.json)에 기록했다.

## 자동 검증

- numeric/identifier focused backend tests: PASS
- 전체 backend unit tests: PASS
- `git diff --check`: PASS

P1 Gate: **PASS**
