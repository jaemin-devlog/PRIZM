# PRZ-016 P7-B Evidence Context C0/C1/C2 Final Comparison

지정된 raw, pair, NLI, numeric artifact 10개의 SHA-256을 Ground Truth 채점 전에 재검증했고 모두 일치했다. 검색, NLI, numeric verifier, context 생성은 재실행하지 않았다.

| Context | Top1 | Recall@3 | Recall@5 | MRR@5 | Negative FPR | Positive retained | PASS / FAIL |
|---|---:|---:|---:|---:|---:|---:|---:|
| P7-B baseline | 12/36 (33.33%) | 21/36 (58.33%) | 21/36 (58.33%) | 0.4491 | 5/12 (41.67%) | 21/21 | 28 / 20 |
| C0 | 5/36 (13.89%) | 7/36 (19.44%) | 7/36 (19.44%) | 0.1667 | 1/12 (8.33%) | 7/21 | 18 / 30 |
| C1 | 6/36 (16.67%) | 8/36 (22.22%) | 8/36 (22.22%) | 0.1944 | 1/12 (8.33%) | 8/21 | 19 / 29 |
| C2 | 7/36 (19.44%) | 8/36 (22.22%) | 8/36 (22.22%) | 0.2083 | 1/12 (8.33%) | 8/21 | 19 / 29 |

## Recovery

| Root cause | C1 | C2 |
|---|---:|---:|
| SNIPPET_LOCALIZATION_FAILURE | 0/8 | 0/8 |
| MULTI_SENTENCE_CONTEXT_LOSS | 1/3 | 1/3 |
| NLI_SEMANTIC_FAILURE | 1/3 | 0/3 |
| Total C0 regression recovered | 2/14 | 1/14 |

C1은 `V2-U01-IP02`, `V2-U04-CN01`을 복구했지만 기존 정답 `V2-U04-NV01`을 새로 잃어 최종 regression은 13/21이다. C2는 `V2-U04-CN01`만 복구했다. Rank 개선은 C1 `V2-U01-IP02`, C2 `V2-U03-CN01` 각 1건이다.

## Negative and numeric

C1/C2 모두 Negative FPR은 1/12이며 남은 ID는 `V2-U01-N03`이다. 두 context 모두 numeric veto는 0이고 `METRIC_BINDING_MISS`는 그대로 남았다.

## Gate

| Gate | C1 | C2 |
|---|---|---|
| Negative FPR <= 1/12 | PASS | PASS |
| Top1 >= 33.33% | FAIL | FAIL |
| Recall@5 >= 58.33% | FAIL | FAIL |
| Positive regression <= 1/21 | FAIL | FAIL |
| New evidence = 0 | PASS | PASS |
| Final | `CONTEXT_CANDIDATE_FAIL` | `CONTEXT_CANDIDATE_FAIL` |

`BEST = NONE`. 단순 인접 문장 또는 문자 중첩 기반 bounded context는 Negative 억제를 유지했지만 기존 Positive 품질을 회복하지 못했고, localization 원인 8건을 하나도 복구하지 못했다. 다음 단계는 `EVIDENCE_LOCALIZATION_REDESIGN`이다.
