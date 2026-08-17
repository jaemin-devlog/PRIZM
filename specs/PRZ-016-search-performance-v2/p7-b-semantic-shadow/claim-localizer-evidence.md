# PRZ-016 Claim-Aware Content Localizer Shadow Final Evaluation

## Integrity and policy

- All seven required frozen artifact hashes matched before Ground Truth was read.
- C0 `SUPPORT` results were preserved without fallback.
- Only C0 `NON_SUPPORT` results used frozen candidate order, and the first combined `SUPPORT` candidate restored the verdict of the original result.
- No result, chunk, document, version, content, score, distance, order, or user-visible snippet was changed. No new evidence was created.

## Comparison

| Metric | Baseline | C0 Semantic + Numeric | Claim-Aware |
|---|---:|---:|---:|
| Top1 | 12/36 (33.33%) | 5/36 (13.89%) | 11/36 (30.56%) |
| Recall@3 | 21/36 (58.33%) | 7/36 (19.44%) | 14/36 (38.89%) |
| Recall@5 | 21/36 (58.33%) | 7/36 (19.44%) | 14/36 (38.89%) |
| MRR@5 | 0.4491 | 0.1667 | 0.3472 |
| Negative FPR | 5/12 (41.67%) | 1/12 (8.33%) | 1/12 (8.33%) |
| PASS / FAIL | 28 / 20 | 18 / 30 | 25 / 23 |

## Positive preservation and recovery

- Baseline-correct Positive retained: 14/21; regression: 7/21.
- C0 regression recovered: 7/14 — `V2-U01-D02`, `V2-U01-NV01`, `V2-U01-IP02`, `V2-U03-D01`, `V2-U03-IP02`, `V2-U04-NV02`, `V2-U04-CN01`.
- Remaining regression: `V2-U01-IP01`, `V2-U01-CN01`, `V2-U02-D01`, `V2-U02-NV01`, `V2-U02-IP01`, `V2-U03-NV01`, `V2-U04-IP02`.
- Existing C0 `SUPPORT` caused no new regression.
- Correct-result rank improved for `V2-U03-D01` and `V2-U03-IP02`.

## Root-cause recovery

- SAME_RESULT_CONTENT_NON_ADJACENT: 5/7 recovered; `V2-U01-CN01` and `V2-U03-NV01` were not recovered.
- MULTI_SENTENCE_CONTEXT_LOSS: 1/3 recovered; only `V2-U04-CN01` recovered.
- NLI_SEMANTIC_FAILURE: 1/3 recovered; only `V2-U01-IP02` recovered.
- `V2-U02-D01`: not recovered; all frozen candidate verdicts remained `UNKNOWN`.

The two unresolved non-adjacent cases show candidate preselection/localization loss: their correct-result top-five windows did not contain the supporting anchor. Several other correct windows contained relevant action evidence but still received `UNKNOWN` or `CONTRADICT`, showing an independent NLI semantic limitation.

## Negative results

- Four baseline false positives remained blocked: `V2-U01-N01`, `V2-U03-N01`, `V2-U04-N01`, `V2-U04-N02`.
- `V2-U01-N03` remained the only false positive. Its C0 verdict was already `SUPPORT`, so fallback did not run and the existing metric-binding miss was unchanged.
- No new Negative false positive was introduced.

## Filter and latency

- Original result pairs: 86; C0 SUPPORT: 22; fallback eligible: 64.
- Frozen candidate NLI pairs: 320; fallback recovered original results: 16; still non-support: 48.
- Final original-result verdicts: SUPPORT 38; NON_SUPPORT 48.
- Candidate inference latency: average 915.68 ms, p95 1327.70 ms, total 293016.42 ms (293.02 s).
- CPU execution may require up to five NLI calls per fallback result, which is a Production latency risk; latency was not part of this quality gate.

## Gate

1. Negative FPR <= 1/12: PASS — 1/12.
2. Positive retained >= 20/21: FAIL — 14/21.
3. Top1 >= 33.33%: FAIL — 30.56%.
4. Recall@5 >= 58.33%: FAIL — 38.89%.
5. Localization recovery >= 6/7: FAIL — 5/7.
6. New evidence = 0: PASS — 0.

Final: `CLAIM_AWARE_LOCALIZER_SHADOW_FAIL`

Next: `MIXED` — both candidate localization/preselection and NLI semantic behavior remain material bottlenecks. P7-B is diagnostic/tuning data, so this result is not a generalization claim.
