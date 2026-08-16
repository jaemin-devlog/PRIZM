# P4 Evidence Localization

## Scope

P4 changes only the evidence snippet and evidence source selected after the final search candidate has
already been fixed. Candidate retrieval, P1/P2/P3, ranking, score, distance, threshold, Top20, and
max5 remain unchanged.

## Gate

- Reproduce and classify B02, B04, B05, C06, and E07 on the P3 runtime.
- Implement only failures classified as `LOCALIZATION`.
- Preserve owner and ACTIVE-version boundaries in `EvidenceExpansionRepository`.
- Run focused targets and regression guards before the unchanged 72-query benchmark.
