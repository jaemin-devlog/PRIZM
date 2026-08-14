# P5 Final Holdout Validation

평가일: 2026-08-14

## Freeze

- Dataset: 48건(Positive 36, Negative 12)
- 분포: Direct 8, Natural Variation 10, Indirect Problem 8, Numeric/Identifier 4,
  Complex Natural Language 6, Negative 12
- 기존 development query 완전 중복: 0건
- 문자 bigram Dice 0.65 이상 near-duplicate: 0건
- Positive GT anchor 사전 확인: 36/36
- Negative ACTIVE corpus 부재 확인: 12/12
- Dataset SHA-256: `4e28c0fb2b99b31f15640eb39776e04a533938b63db02e1ba0bfec22168532aa`
- Ground Truth SHA-256: `da915300974c27967e859c0586ec1f76347c314c62f0ca57f77b5b64c3e0180d`
- Freeze 이후 dataset/GT 변경: 0건
- P5 production 검색 코드 변경: 0건

## Metrics

| 지표 | P4 Development | P5 Holdout |
|---|---:|---:|
| Top1 Accuracy | 82.14% | 50.00% |
| Recall@3 | 85.71% | 61.11% |
| Recall@5 | 85.71% | 61.11% |
| MRR@5 | 0.8363 | 0.5509 |
| Negative FPR | 0% | 25.00% |
| PASS / FAIL | 62 / 10 | 31 / 17 |

Positive 36건 중 rank 1은 18건, rank 2는 3건, rank 3은 1건이며 14건은 Top5에
acceptable evidence가 없었다. Negative 12건 중 3건이 false positive였다.

## 카테고리 결과

| Category | PASS | FAIL | Total |
|---|---:|---:|---:|
| Direct Experience | 4 | 4 | 8 |
| Natural Variation | 5 | 5 | 10 |
| Indirect Problem | 6 | 2 | 8 |
| Numeric / Identifier | 2 | 2 | 4 |
| Complex Natural Language | 5 | 1 | 6 |
| Negative | 9 | 3 | 12 |

Numeric 4건 중 H27과 H28은 PASS했다. H29는 수상 사실만 찾고 날짜 근거를 찾지 못했으며,
H30은 `30명 + 1,100번` exact evidence를 찾지 못했다. P3 Query Understanding은 새로운
자연어·활동 질의에서 50%만 통과했고 P4 Evidence Localization도 수동 확정 7건의 실패가
남아 일반화 Gate를 충족하지 못했다.

## Isolation

- Actual USER: owner 1, role `USER`
- USER 1 ACTIVE 문서: 15·16, ACTIVE version: 15·16
- 다른 owner 문서: 7개(3, 4, 10, 11, 12, 13, 14)
- Holdout 응답의 다른 owner 문서 포함: 0건
- inactive version 응답·evidence 포함: 0건
- Owner isolation: `PASS`
- ACTIVE version isolation: `PASS`

## Latency

| 지표 | P4 Development | P5 Holdout |
|---|---:|---:|
| 전체 average | 296.930ms | 392.182ms |
| Warm average | 296.972ms | 272.280ms |
| Median | 256.445ms | 259.931ms |
| Warm median | 256.445ms | 258.697ms |
| P95 | 729.198ms | 527.314ms |
| Warm P95 | 729.198ms | 330.281ms |
| Max | 776.127ms | 6,027.568ms |
| Cold first | 293.958ms | 6,027.568ms |

P5의 최대값과 전체 평균은 첫 요청의 cold latency가 크게 올렸다. warm latency는 P4
reference 대비 악화되지 않았으며 이번 Phase에서는 최적화하지 않았다.

## Regression and Gate

- P1~P4 focused backend tests: 68 tests, 실패 0, 오류 0, skipped 0
- 전체 backend tests: 511 tests, 실패 0, 오류 0, skipped 16
- Production search source aggregate SHA-256:
  `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`
- Production search source freeze 이후 변경: 0건
- benchmark-specific hardcoding: 0건
- `git diff --check`: PASS

Mandatory Gate는 Negative FPR 25% 때문에 실패했다. Search Quality Gate도 Top1, Recall@3,
Recall@5, MRR@5가 모두 권장 기준보다 낮아 실패했다.

## 판정

- P5 평가 상태: `DONE — FAIL`
- P5 최종 판정: `FAIL`
- PRZ-013 상태: `IN_PROGRESS`
- Search Performance V2: `NOT_FROZEN`
- production 검색 코드 수정: 없음
- 후속 P6·새 PRZ: 생성하지 않음
