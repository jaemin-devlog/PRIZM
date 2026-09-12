# PRZ-028 Typed Constraint Stress DEV/CAL

- dataset: `search-v3-typed-constraints-stress-1.0.0`
- status: `FRESH_BENCHMARK_SEED_FROZEN`
- scope: synthetic DEV/CAL only; no personal data
- queries: 24 (DEV 12, CALIBRATION 12)
- purpose: freeze typed constraints, source-grounded observations, and expected match states before implementation
- SEALED FINAL: not copied, not opened, not searched
- generator: `scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs`

Run `node scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs --check` for deterministic byte, hash, lineage, grounding, and metadata-only SEALED FINAL verification. A non-check run refuses to overwrite this frozen directory.
