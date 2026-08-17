# PRZ-016 P7-B Semantic + Numeric Shadow Phase B

## Integrity

Ground Truth를 읽기 전에 지정된 frozen artifact 네 건의 SHA-256을 다시 계산했고 모두 일치했다. 검색, NLI, numeric verifier는 재실행하지 않았다.

| Artifact | SHA-256 | Result |
|---|---|---|
| P7-B raw results | `defc5e35dbf26f48a640f3df673e2247c14437b0cb65e8c8c05a0bd3b6e2cb2e` | PASS |
| Frozen pairs | `e48fb53e26c2c88960d054d8fcea2a0df33f38774c235258e06dafbe5f4ee758` | PASS |
| NLI results | `1d1e42f7f9d42bde7a67fe3c574e7848334f6d9970ed7ee021465a6f18060f78` | PASS |
| Numeric shadow results | `fa72410888fd8c80aaeb8302a0fb1be1a29d80a19bac08d5d22086fef82b70be` | PASS |

기존 P7-B document, evidence source, acceptable-anchor 판정을 그대로 재현했으며 Positive 36건의 `correctRank`가 기존 `evaluated-results.json`과 36/36 일치했다.

## Shadow policy

`combinedLabel=SUPPORT`인 기존 Production result만 유지하고 `CONTRADICT`와 `UNKNOWN`은 제거했다. 유지된 result는 원래 순서를 보존해 rank를 1부터 다시 부여했다. 새로운 evidence는 만들지 않았다.

## Result

| Metric | Baseline | Semantic + Numeric Shadow |
|---|---:|---:|
| Top1 | 12/36 (33.33%) | 5/36 (13.89%) |
| Recall@3 | 21/36 (58.33%) | 7/36 (19.44%) |
| Recall@5 | 21/36 (58.33%) | 7/36 (19.44%) |
| MRR@5 | 0.4491 | 0.1667 |
| Negative FPR | 5/12 (41.67%) | 1/12 (8.33%) |
| PASS / FAIL | 28 / 20 | 18 / 30 |

- 기존 Negative FP 차단: 4/5 — `V2-U01-N01`, `V2-U03-N01`, `V2-U04-N01`, `V2-U04-N02`
- 남은 Negative FP: 1/5 — `V2-U01-N03`
- 기존 정답 Positive 유지: 7/21
- Positive regression: 14/21
- rank 개선 Positive: 0
- pair 유지/제거: SUPPORT 22 / NON_SUPPORT 64 (`CONTRADICT` 20, `UNKNOWN` 44)
- numeric veto: 0
- NLI latency: total 93,242.15ms, average 1,084.21ms, P95 1,628.63ms (86 pairs)

## Gate

| Gate | Result |
|---|---|
| Negative FPR <= 1/12 | PASS — 1/12 |
| Positive regression = 0 | FAIL — 14 |
| Recall@5 >= 58.33% | FAIL — 19.44% |
| Top1 >= 33.33% | FAIL — 13.89% |
| 신규 evidence 생성 없음 | PASS |

최종 판정: `P7_B_SEMANTIC_SHADOW_FAIL`.

Production 검색 결과와 P7-B frozen artifact에는 영향을 주지 않았다.
