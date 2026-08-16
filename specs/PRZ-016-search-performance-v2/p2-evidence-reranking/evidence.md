# P2 Evidence-aware Reranking Evidence

> 역사 보존: 통합 전 임시 `PRZ-016 Evidence`를 현재 PRZ-016 P2로 이동했다. 아래 결과는
> 원래 실행 기록을 유지한다.

검증일: 2026-08-14

## Focused Gate

정상 `USER` 계정의 COMPLETED ACTIVE 이력서·포트폴리오로 기존 RANKING 실패 6건을
검증했다. 4건이 Top1으로 전환됐고, 나머지 2건은 candidate를 잃지 않은 채 rank 2와 3으로
남았다.

| ID | 질의 | Before | After | 정답 candidate의 dense / P4 / evidence / final |
|---|---|---:|---:|---|
| A01 | Spring Boot 백엔드 경험 | 2 | 1 | `0.518601 / +0.025000 / +0.065000 / 0.608601` |
| A03 | 동시성 처리 경험 | 4 | 1 | `0.513892 / +0.002500 / +0.065000 / 0.581392` |
| A05 | TourAPI 연동 경험 | 3 | 2 | `0.558188 / +0.020000 / +0.062500 / 0.640688` |
| B01 | 여러 요청이 동시에 들어오면 어떻게 처리했어? | 3 | 3 | `0.559175 / +0.003000 / +0.065000 / 0.627175` |
| C03 | 외부 호출 대기 시간이 누적되는 문제를 줄인 방법은? | 2 | 1 | `0.577880 / +0.003750 / +0.065000 / 0.646630` |
| E01 | Redis와 DB lock을 같이 사용해서 동시성 문제를 해결한 경험이 있어? | 2 | 1 | `0.653330 / +0.022000 / +0.065000 / 0.740330` |

상세 후보별 설명은 [focused-ranking.tsv](focused-ranking.tsv), 실제 API 결과와 회귀·격리
검사는 [focused-results.json](focused-results.json)에 기록했다.

- 기존 positive 13건: 13/13 근거 반환
- negative 7건: 7/7 `NO_RELEVANT_RESULTS`
- numeric 5건, 기존 numeric 3건, numeric near miss 3건: 모두 기존 P1 계약 유지
- Spring Boot 계열은 evidence 순서만 의도적으로 개선됐으며 candidate/score/distance는 보존
- 반환 document/version은 모두 로그인 USER 소유의 ACTIVE version과 일치

## 동일 72-query Benchmark V2

P1과 동일한 72개 질의 및 ground truth를 수정하지 않고 재실행했다.

| 지표 | P1 Before | P2 After |
|---|---:|---:|
| Top1 Accuracy | 60.71% | 67.86% |
| Recall@3 | 69.64% | 71.43% |
| Recall@5 | 71.43% | 71.43% |
| MRR@5 | 0.6503 | 0.6935 |
| Negative FPR | 0% | 0% |
| Warm average | 220.845 ms | 231.193 ms |
| Warm P95 | 285.769 ms | 280.405 ms |
| 실패 질의 | 22 | 18 |
| RANKING 실패 | 6 | 2 |

A01·A03·C03·E01이 새로 PASS했고, 기존 PASS에서 FAIL로 바뀐 질의는 0건이다. Recall@5는
유지됐고 warm 평균 증가는 4.69%, warm P95는 1.88% 감소했다. 전체 결과는
[benchmark-results.json](benchmark-results.json)에 기록했다.

## 자동 검증

- evidence reranker·P4 통합·SearchService focused tests: PASS
- 전체 backend tests: PASS
- `git diff --check`: PASS

P2 Gate: **PASS**
