# Search V3 Fresh Generalization Seed

This tree materializes the PRZ-025 evaluation contract. It is intentionally
separate from `search-evaluation/v2*`; no file here is a relabel of a Search V2
dataset.

## Status and use boundary

- Dataset: `search-v3-fresh-seed-1.0.1`
- Schema: `1.0.0`
- Status: `FRESH_BENCHMARK_SEED_FROZEN`
- Purpose: schema, integrity, split-independence, and profession-generalization
  coverage validation
- Release-grade Search V3 performance evidence: `NOT_RUN`
- Current Search fresh baseline: `NOT_RUN`
- Search execution against `sealed-final/`: prohibited and `NOT_RUN`

The seven-bundle seed is not the proposed release-grade corpus of at least 50
user bundles. `sealed-final/` is a small frozen protocol fixture. It must never
be used for Search V3 development or calibration, even though its synthetic
contents are public. Its `opened` field means that search outputs were exposed,
not that the source/gold integrity validator read the input files.

## Layout

```text
search-v3-evaluation/
├── schema/
│   ├── search-v3-benchmark.schema.json
│   └── search-v3-prediction.schema.json
├── dev/
│   ├── documents/
│   ├── corpus.json
│   ├── gold-evidence.json
│   ├── questions.json
│   └── manifest.json
├── calibration/              # same shape as dev
├── sealed-final/             # immutable inputs/gold; no result files
├── lineage.json
└── manifest.json
```

Only synthetic, non-personal TXT fixtures are tracked in this seed. Public
fixtures require a fixture-level redistribution review before addition.
Consented real data must be anonymized and stored under the already ignored
`local/search-v3-evaluation/` tree with a separately approved consent, access,
retention, and deletion policy. Real personal source text must never be copied
into this directory.

## Stable gold coordinates

Gold IDs such as `SV3-U01-P01-E01` are benchmark annotation IDs, not database
IDs. Source text is UTF-8 without BOM and normalized to LF before annotation.
`charStart` is zero-based and inclusive, `charEnd` is zero-based and exclusive,
and both count Unicode code points. Lines are one-based and inclusive. A source
span is valid only when its exact UTF-8 text and SHA-256 match the referenced
document version.

A `DIRECT_SUPPORT` Evidence Unit can contain multiple constituent spans only
inside one Evidence Parent and one document version. Every required constituent
span must be localized for a direct hit. Content from different Parents cannot
be merged into a direct answer.

## Split and lineage policy

`userBundleId`, logical document/version lineage, `sourceFactId`, normalized
source-fact signature, normalized query, `questionGroupId`, `templateFamilyId`,
and generator name/revision/seed are blocking leakage keys. Reuse across any two
of `DEV`, `CALIBRATION`, and `SEALED_FINAL_TEST` fails validation. There is no
"near enough" exception for the materialized seed.

DEV may be repeatedly inspected. CALIBRATION may later be used for threshold,
K, model, parser/chunk, fusion/reranker, confidence, and operational profile
choices. SEALED FINAL may not be used for any such choice. Changing schema,
gold/evaluator semantics, query policy, or a sealed file requires a new dataset
version and new seal; the old run can only remain `HISTORICAL_RESULT`.

## Prediction adapter boundary

`schema/search-v3-prediction.schema.json` defines the future system-neutral
output adapter. Current Search and every V3 candidate must emit the same stable
document/version/source locators. Runtime chunk/parent IDs may be retained only
as diagnostics and are never gold. The evaluator maps a result to an Evidence
Unit only when all required source spans of that Unit are covered in the same
Parent. Unmapped returned evidence counts as non-direct for precision; it is not
silently ignored.

No prediction or result file exists in `sealed-final/` in this Phase.

## Deterministic commands

```powershell
node scripts/evaluation/search-v3/validate-search-v3-benchmark.mjs
node --test scripts/evaluation/search-v3/validate-search-v3-benchmark.test.mjs
```

The one-time generator refuses to overwrite an existing sealed manifest. A new
sealed version must use a new dataset directory/version rather than mutating this
one.
