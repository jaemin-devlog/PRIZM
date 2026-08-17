# PRZ-016 P7-B Full Verification Pipeline Final Scoring

- Phase 0 integrity: PASS
- Judge result SHA-256: `777c24a3134f82ce47389b706ec8bd96186bc43cfbc78c02b4a1e434108acfaa`
- Judge checkpoint SHA-256: `9c108802a8d52cd39e3455f30ddad7d77e2f0e4f730af11ed9c833e348aa023d`
- Numeric result SHA-256: `79de993f0f482aceffefb905679b88d57fadbfbf550978ac1d2b7e7b1e58b0e0`
- Coverage: 430 candidates / 86 original results / 38 queries with results
- Baseline scoring rule reproduced: 48/48

## Metrics

| Metric | Baseline | Full pipeline | Delta |
|---|---:|---:|---:|
| Top1 | 33.33% | 36.11% | +2.78pp |
| Recall@3 | 58.33% | 52.78% | -5.56pp |
| Recall@5 | 58.33% | 52.78% | -5.56pp |
| MRR@5 | 0.4491 | 0.4444 | -0.0046 |
| Negative FPR | 41.67% | 33.33% | -8.33pp |

## Detail

- PASS/FAIL: 27/21
- Baseline-correct retained: 19/21
- Regression: 2 — V2-U01-CN01, V2-U03-NV01
- Rank improved: 1 — V2-U02-IP01
- Newly passed Positive: 0 — none
- Negative FP: 4/12 — V2-U01-N01, V2-U03-N01, V2-U04-N01, V2-U04-N02
- NUMERIC_UNRESOLVED: 14 candidates / 3 original results / 3 queries
- Filter: 59 SUPPORT / 27 NON_SUPPORT from 86 original results
- New evidence: 0

## Gate

1. negativeFprAtMostOneOfTwelve: FAIL
2. top1AtLeastBaseline: PASS
3. recallAt5AtLeastBaseline: FAIL
4. positiveRegressionAtMostOne: FAIL
5. newEvidenceZero: PASS

FINAL: **P7_FULL_VERIFICATION_SHADOW_FAIL**
