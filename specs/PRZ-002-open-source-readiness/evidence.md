# PRZ-002 evidence

| 항목 | 값 |
|---|---|
| Spec | [PRZ-002](spec.md) |
| Evidence status | `VERIFY_COMPLETE_AUDIT_PENDING` |
| Implementation commit | `c28416e` — `추가: source-only SBOM과 AI 모델 명세` |
| Branch | `PRZ-002-sbom-model-manifest` |
| Baseline | `main` / `origin/main` `0ad549a8641b2b6ef18a8011dac93286052b65c0` |
| GitHub Issue | `NOT_CREATED`; connector write was blocked with HTTP 403 on 2026-07-26 |
| Primary / secondary evaluation IDs | `EVAL-R1-02` / `EVAL-R1-03`, `EVAL-R1-05` |

This evidence covers the T-05 SBOM and AI-model-manifest implementation only.
It does not claim that PRZ-002, OpenSQL, OpenProxy, or OpenHA is verified.

## Generated records

| Record | Result at `c28416e` |
|---|---|
| `sbom/prizm-backend-runtime.cdx.json` | CycloneDX 1.6, `prizm`, 169 runtime components, no timestamp or serial number |
| `sbom/prizm-frontend.cdx.json` | CycloneDX 1.6, `prizm-frontend`, 183 versioned lockfile components, no timestamp or serial number |
| `sbom/prizm-ai-model-manifest.json` | source-only model boundary, Ollama v0.32.3, BAAI `bge-m3` revision, registry hashes, `UNVERIFIED_LINEAGE` preserved |
| `sbom/prizm-scope-manifest.json` | backend runtime, test/build, frontend, CI, container, model, fixture/asset scope split |
| `sbom/SHA256SUMS` | backend `1c6c91fb990c4bf130ca07794789e65bdd7524e145ba7f6397bfeefce2e0447b`; frontend `c7c1651f8da873c68c352cef71cc8c4964be3fea56aa3b815f54e2d5bbc459b5`; AI model `9b4e4a805fffa38a9ea40567ede25ccaf0669970c0c1994e036148855fc2f728`; scope `d7358b6414f945dab76ef63920500a867ce14a229f24715a0ccd909600f007e7` |

The local `scripts/verify-sbom.mjs` structural gate passed after regeneration.
It checks JSON parsing, CycloneDX format and primary component identity, component
presence, deterministic-field policy, checksum drift, and prohibited local
path/JDBC URL/credential-shaped fields. A formal full-schema CI gate remains
T-09 work and is not claimed here.

## Commands and results

| Command | Result |
|---|---|
| `./gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict` | PASS; first-party Gradle runtime SBOM generation |
| `npm --prefix frontend run sbom` | PASS; first-party lockfile SBOM generation from package-lock SHA-256 `967063c8b12574a1467d492ad5fec7c6e080e89a6250f153e49ed1f1714fb66c` |
| `node scripts/verify-sbom.mjs --write-checksums` then `node scripts/verify-sbom.mjs` | PASS; deterministic checksum update then no-drift verification |
| `./gradlew.bat test --no-daemon --dependency-verification=strict` | PASS; 245 tests, 0 failures, 0 errors, 14 environment-condition skips |
| `./gradlew.bat integrationTest --no-daemon --rerun-tasks --dependency-verification=strict` | PASS; 68 tests, 0 failures, 0 errors, 3 environment-condition skips |
| `npm --prefix frontend run lint` | PASS |
| `npm --prefix frontend run build` | PASS |
| `docker compose config` | PASS |
| Markdown local-link/code-fence/trailing-whitespace scan and `git diff --check` | PASS before the implementation commit |

The Gradle test and integration task graphs no longer include a third-party
SBOM plugin task. Gradle still reports its existing Gradle 10 deprecation
warning and existing deprecated Java API notes; neither is a test failure nor
a claim that those issues are resolved by this spec.

## Environment use

| Environment / service | Actual result |
|---|---|
| Java / Gradle | USED for generation and test commands with strict dependency verification |
| Node 22.17.0 / npm 10.9.2 | USED for frontend SBOM, lint, and build |
| Docker | USED by the integration suite and `docker compose config` |
| PostgreSQL 16 + pgvector | USED through Testcontainers integration tests |
| Ollama | `NOT_RUN`; SBOM/model manifest records identity only and does not pull a model |
| OpenSQL | `NOT_RUN` |
| OpenProxy | `NOT_RUN` |
| OpenHA | `NOT_RUN` |

## Remaining verification and audit gates

- Reconcile the human license audit with the generated Java/npm component sets.
- Run the independent read-only audit for the complete branch diff.
- Add formal schema/structural SBOM enforcement to CI only in T-09.
- Do not create `SECURITY.md` until an actual confidential reporting channel is
  enabled or a monitored contact is supplied.
- No real GitHub Issue, PR, review, push, or merge evidence exists yet.
