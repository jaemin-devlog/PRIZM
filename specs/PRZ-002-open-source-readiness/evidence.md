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
- A branch push exists, but no real GitHub Issue, PR, review, or merge evidence
  exists yet.

## External GitHub gate — 2026-07-26

The branch was published to `origin/PRZ-002-sbom-model-manifest`, but this
environment cannot create the required real Issue or PR evidence: the GitHub
connector Issue-create request returned HTTP 403 (`Resource not accessible by
integration`), and `gh auth status` reported no authenticated GitHub host.
The branch's remote push is not treated as an Issue, review, or merge record.
Private Vulnerability Reporting was not inspected or enabled from this
environment, so G-02 remains `BLOCKED_EXTERNAL_CONFIGURATION`.

## Read-only audit — 2026-07-26

**Scope:** `main...232915e` on `PRZ-002-sbom-model-manifest`; implementation
commits `c28416e` and `232915e`.

| Audit check | Result |
|---|---|
| Changed-file scope | PASS — build task, frontend script entry, SBOM/manifests, verifier, PRZ-002/license documentation only; no Java application source, Flyway migration, production config, Docker Compose, or frontend feature changes |
| Source-only boundary | PASS — no model/cache/upload/DB volume/generated build output tracked; all model bytes remain `NOT_DISTRIBUTED` |
| Sensitive data | PASS — SBOM/manifests contain no local path, JDBC URL, or credential-shaped field |
| Reproducibility | PASS — regenerate then checksum verification passed; deterministic timestamp/serial policy held |
| Machine/source reconciliation | PASS within implemented scope — frontend lockfile count `183` equals SBOM count `183`; all 169 backend components have Maven PURLs and SHA-256 artifact hashes |
| Documentation honesty | PASS — `UNVERIFIED_LINEAGE`, source-only boundary, and OpenSQL/OpenProxy/OpenHA `NOT_RUN` are preserved |
| Diff hygiene | PASS — `git diff --check main...HEAD`, local Markdown-link/code-fence/trailing-whitespace checks, and `git show --check` for both implementation commits passed |

**Findings:** no CRITICAL, HIGH, or MEDIUM finding in the reviewed implementation
scope. Formal full-schema validation/CI and the broader human license-audit
reconciliation are explicit unimplemented T-09/T-05 follow-up gates, not
evidence of completion. This audit is an agent read-only review, not a GitHub
review.

**Audit conclusion:** `PASS_FOR_IMPLEMENTED_SCOPE`; T-05 remains
`IMPLEMENTED_UNVERIFIED` until its remaining reconciliation gate is complete.
