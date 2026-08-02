# PRIZM machine-readable SBOM records

This directory is the single source for PRIZM's full component identities,
exact versions, license expressions, source URLs, package hashes, generated
file checksums, and source-only distribution boundary.

PRIZM publishes source and configuration under Apache-2.0. Java artifacts,
frontend bundles, container images, PostgreSQL·pgvector, Ollama, model weights,
OpenSQL assets, database volumes, and uploaded documents are not redistributed
in the source archive. The current source-only license Gate is `PASS` with no
known blocking `UNKNOWN`, `CONFLICT`, or `BLOCKED` result. Any future binary,
image, or model distribution requires a new artifact-specific review.

## File roles

| File | Format | Role |
|---|---|---|
| [`prizm-backend-runtime.cdx.json`](prizm-backend-runtime.cdx.json) | CycloneDX 1.6 JSON | Resolved Java runtime artifact inventory. |
| [`prizm-frontend.cdx.json`](prizm-frontend.cdx.json) | CycloneDX 1.6 JSON | All versioned `frontend/package-lock.json` entries, including development and optional scope. |
| [`prizm-ai-model-manifest.json`](prizm-ai-model-manifest.json) | PRIZM JSON manifest 1.0 | Ollama, `bge-m3`, registry artifact, and authoring-assistance provenance and distribution boundary. |
| [`prizm-scope-manifest.json`](prizm-scope-manifest.json) | PRIZM JSON manifest 1.0 | Runtime, test/build, CI, container, model, and asset scope decisions. |
| [`SHA256SUMS`](SHA256SUMS) | SHA-256 | Integrity record for the four JSON files above. |

These records describe source-only and external operational scopes separately.
They do not claim that Java JARs, frontend `dist`, container images, Ollama,
model weights, OpenSQL files, database volumes, or uploaded documents are
included in the Git source archive.

## Generate

Run from the repository root with Java 17, Node 22.17.0, and npm 10.9.2.

```powershell
.\gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict
npm --prefix frontend run sbom
```

The backend generator is the first-party `generateBackendSbom` task in
[`build.gradle`](../build.gradle). The frontend generator is the first-party
[`generate-frontend-sbom.mjs`](../scripts/generate-frontend-sbom.mjs). Neither
generator adds an external SBOM plugin to the source release.

## Verify

Normal verification must not rewrite checksums.

```powershell
node scripts/verify-sbom.mjs
node --test scripts/verify-sbom.test.mjs
node scripts/verify-oss-readiness.mjs
```

The verifier checks JSON structure, CycloneDX 1.6 fields, unique `bom-ref`
values, canonical hash names, deterministic line endings, generated-file
drift, checksums, and local path or credential-shaped data.

## Check and update checksums

To compare a generated file with the recorded value, use the platform's
SHA-256 tool and compare the result with [`SHA256SUMS`](SHA256SUMS).

```powershell
Get-FileHash sbom\prizm-backend-runtime.cdx.json -Algorithm SHA256
Get-FileHash sbom\prizm-frontend.cdx.json -Algorithm SHA256
Get-FileHash sbom\prizm-ai-model-manifest.json -Algorithm SHA256
Get-FileHash sbom\prizm-scope-manifest.json -Algorithm SHA256
```

Only after an intentional, reviewed inventory change should the checksum file
be refreshed:

```powershell
node scripts/verify-sbom.mjs --write-checksums
node scripts/verify-sbom.mjs
```

CI never runs `--write-checksums`. An unexpected change must fail instead of
silently becoming the new baseline.
