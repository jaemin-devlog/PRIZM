# PRZ-016 P7-B Result-Level Evidence Set Judge Final Scoring

## Integrity

- All five frozen Result-Level artifacts matched their required SHA-256 values.
- Result coverage: 86/86 original results and 430/430 frozen windows.
- Model output invalid: 0.
- Result identity mismatch: 0.
- The existing P7-B matching contract reproduced the baseline evaluation for 48/48 queries before filtering.
- No search, Judge, Numeric Verifier, or other inference was rerun.

## Final metrics

| Metric | Baseline | Candidate-level full | Result-level full |
|---|---:|---:|---:|
| Top1 | 12/36 (33.33%) | 13/36 (36.11%) | 14/36 (38.89%) |
| Recall@3 | 21/36 (58.33%) | 19/36 (52.78%) | 20/36 (55.56%) |
| Recall@5 | 21/36 (58.33%) | 19/36 (52.78%) | 20/36 (55.56%) |
| MRR@5 | 0.4491 | 0.4444 | 0.4676 |
| Negative FPR | 5/12 (41.67%) | 4/12 (33.33%) | 3/12 (25.00%) |
| PASS/FAIL | 28/20 | 27/21 | 29/19 |

Result-level filtering retained 20/21 baseline-correct Positive queries. `V2-U01-CN01` recovered from the candidate-level regression, while `V2-U03-NV01` did not recover. The remaining baseline-correct regression is therefore 1/21. Correct-result rank improved for `V2-U02-D01` and `V2-U03-CN01`. No previously failing baseline query newly passed.

The baseline Negative false positives blocked by the final policy are `V2-U01-N01` and `V2-U01-N03`. The remaining false positives are `V2-U03-N01`, `V2-U04-N01`, and `V2-U04-N02`. Of the four candidate-level remaining false positives, only `V2-U01-N01` was newly blocked at query level.

## Numeric unresolved

- `V2-U02-NI01#r1`: Positive, `PREMISE_METRIC_MISSING`.
- `V2-U03-NI01#r1`: Positive, `HYPOTHESIS_METRIC_MISSING`.

Both results were removed fail-closed. Neither was correct under the frozen baseline matching contract, neither caused a new Positive regression, and neither blocked a Negative false positive. They are unresolved cases, not successful numeric contradictions.

## Result set change

Candidate-level SUPPORT to Result-Level NON_SUPPORT:

- `V2-U01-N01#r1`
- `V2-U01-NV01#r4`
- `V2-U01-NV02#r1`
- `V2-U02-D01#r1`
- `V2-U02-NV02#r1`
- `V2-U03-CN01#r1`
- `V2-U03-D02#r3`
- `V2-U03-NV01#r4`
- `V2-U04-CN01#r3`
- `V2-U04-D01#r1`
- `V2-U04-D01#r2`
- `V2-U04-N02#r1`

Result-Level new SUPPORT:

- `V2-U01-CN01#r3`
- `V2-U01-CN02#r3`
- `V2-U01-IP02#r3`
- `V2-U02-IP01#r1`
- `V2-U02-IP01#r3`
- `V2-U02-NV01#r2`
- `V2-U03-IP02#r3`
- `V2-U03-NV01#r1`
- `V2-U03-NV01#r2`
- `V2-U04-CN02#r3`
- `V2-U04-N02#r3`

`V2-U01-CN01#r3` is the Positive recovery. Removing `V2-U02-D01#r1` and `V2-U03-CN01#r1` improved the correct result to final rank 1. Adding `V2-U02-IP01#r1` caused a rank-only regression from the candidate-level result, but the query remained correct. No Positive query newly failed compared with the candidate-level pipeline.

At original-result level, `V2-U01-N01#r1` and `V2-U04-N02#r1` were Negative false-support removals. `V2-U04-N02#r3` became a new false-support result, so `V2-U04-N02` remained a query-level false positive. The net query-level Negative improvement is only `V2-U01-N01`; no new Negative query became false positive.

## Gate

1. Negative FPR <= 1/12: **FAIL** — 3/12.
2. Top1 >= 33.33%: **PASS** — 14/36 (38.89%).
3. Recall@5 >= 58.33%: **FAIL** — 20/36 (55.56%).
4. Baseline-correct regression <= 1/21: **PASS** — 1/21.
5. New evidence = 0: **PASS** — 0.

## Final

`RESULT_LEVEL_EVIDENCE_SET_JUDGE_FAIL`

`NEXT = STOP_QWEN4B_VERIFIER_AND_REASSESS`

Result-level set judging improves Top1, MRR@5, and Negative FPR over both earlier views, but does not meet the frozen FPR or Recall@5 gates. The SUPPORT set changed substantially rather than only shrinking: 12 candidate-level SUPPORT results were removed and 11 different results became SUPPORT. This recovered one Positive query and blocked one additional Negative query, but three Negative false positives remain. Under the stop rule, Qwen 4B Semantic Judge is not adopted as the PRZ-016 generalization solution.
