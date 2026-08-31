# Semantic Support Stress 1.0.0

This tracked overlay adds semantic evidence-selection stress cases to the existing
`devcal-robustness-1.0.0` DEV and CALIBRATION corpus. It contains no document
copies: every gold span points to one of the six existing synthetic TXT fixtures.

- Scope: 24 new queries, 12 DEV and 12 CALIBRATION, four per referenced bundle.
- State balance: 8 `SUPPORTED`, 8 `PARTIALLY_SUPPORTED`, 8 `NOT_SUPPORTED`.
- Relation balance: 8 `DIRECT_SUPPORT`, 4 `RELATED`, 4 `INSUFFICIENT`,
  8 `CONTRADICTS`.
- Intended use: semantic selection/ranking development on DEV/CALIBRATION only.
- Prohibited use: SEALED FINAL access, tuning from SEALED results, or treating
  this small overlay as release-grade evidence.

`corpus-overlay.json` files declare references into the base dataset.
`runtime-questions.json` is the Gold-free runtime boundary; `questions.json` and
`gold-evidence.json` are evaluation-only metadata using overlay-owned stable
annotation IDs. No runtime chunk, passage, parent, or database ID is present.
`runtime-manifest.json` freezes only the Gold-free corpus references and runtime
questions used before candidate freeze. The root `manifest.json` freezes every
payload, including Gold and the runtime manifest, and may be opened only after a
verified candidate freeze. Each manifest excludes itself from its combined digest.

Status: `INPUT_FROZEN`. No retrieval or embedding run is represented by this
directory.
