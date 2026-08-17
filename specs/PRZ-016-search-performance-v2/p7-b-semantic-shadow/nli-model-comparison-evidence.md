# PRZ-016 NLI Model Comparison Final Evaluation

## Integrity

The frozen pair SHA and both frozen model-result SHAs matched before result content was read. Both result files contain exactly 113 IDs, with no missing, extra, or Ground Truth-mismatched rows.

## Group A+B confusion

| Model / GT | SUPPORT | UNKNOWN | CONTRADICT |
|---|---:|---:|---:|
| mDeBERTa SUPPORT GT | 40 | 0 | 0 |
| mDeBERTa CONTRADICT GT | 4 | 0 | 48 |
| mDeBERTa UNKNOWN GT | 0 | 3 | 13 |
| KLUE SUPPORT GT | 39 | 0 | 1 |
| KLUE CONTRADICT GT | 3 | 5 | 44 |
| KLUE UNKNOWN GT | 0 | 16 | 0 |

- mDeBERTa: Positive false reject 0, Negative false SUPPORT 4, Unknown false SUPPORT 0.
- KLUE: Positive false reject 1 (`v2-U02`, SUPPORT → CONTRADICT), Negative false SUPPORT 3, Unknown false SUPPORT 0.

## Group C diagnostic SUPPORT misses

| Query | mDeBERTa | KLUE | Recovered |
|---|---|---|---|
| V2-U01-IP01 | CONTRADICT | UNKNOWN | NO |
| V2-U02-D01 | UNKNOWN | UNKNOWN | NO |
| V2-U02-NV01 | UNKNOWN | UNKNOWN | NO |
| V2-U02-IP01 | UNKNOWN | UNKNOWN | NO |
| V2-U04-IP02 | UNKNOWN | UNKNOWN | NO |

mDeBERTa distribution is SUPPORT 0 / UNKNOWN 4 / CONTRADICT 1. KLUE distribution is SUPPORT 0 / UNKNOWN 5 / CONTRADICT 0. KLUE recovered none of the five target semantic-support misses.

## Accuracy and distribution

| Model | Group A | Group B | Group C | Overall |
|---|---:|---:|---:|---:|
| mDeBERTa | 65/72 (90.28%) | 26/36 (72.22%) | 0/5 (0%) | 91/113 (80.53%) |
| KLUE | 66/72 (91.67%) | 33/36 (91.67%) | 0/5 (0%) | 99/113 (87.61%) |

- mDeBERTa predicted distribution: SUPPORT 44 / UNKNOWN 7 / CONTRADICT 62.
- KLUE predicted distribution: SUPPORT 42 / UNKNOWN 26 / CONTRADICT 45.

## Latency

| Model | Average | P95 | Total |
|---|---:|---:|---:|
| mDeBERTa | 891.61 ms | 1146.98 ms | 100751.87 ms |
| KLUE | 62.93 ms | 81.20 ms | 7110.99 ms |

KLUE was materially faster on this CPU run, but latency is not part of the fixed quality Gate.

## Gate

1. Group C SUPPORT >= 3/5: FAIL — 0/5.
2. Group A+B SUPPORT false reject not increased: FAIL — 0 → 1.
3. Group A+B CONTRADICT false SUPPORT not increased: PASS — 4 → 3.
4. Group A+B UNKNOWN false SUPPORT <= 1: PASS — 0/16.

Final: `KOREAN_NLI_CANDIDATE_NO_GO`

Next: `NLI_MODEL_LIMITATION`

KLUE improved broad model-selection accuracy, UNKNOWN handling, negative false SUPPORT, and CPU latency. It nevertheless failed the task-specific purpose: all five P7 semantic SUPPORT misses remained non-support, and one previously preserved A+B SUPPORT became CONTRADICT. No Production model selection or application is authorized by this result.
