# PRIZM SBOM and AI model manifest

This directory contains the machine-readable supply-chain records for PRIZM's
current **source-only** release profile. It does not claim that every listed
operational component is included in the Git source archive.

| File | Format | Scope |
|---|---|---|
| [`prizm-backend-runtime.cdx.json`](prizm-backend-runtime.cdx.json) | CycloneDX 1.6 JSON | Resolved Java runtime dependency graph. |
| [`prizm-frontend.cdx.json`](prizm-frontend.cdx.json) | CycloneDX 1.6 JSON | `frontend/package-lock.json` component inventory, including development and optional entries. |
| [`prizm-ai-model-manifest.json`](prizm-ai-model-manifest.json) | PRIZM JSON manifest 1.0 | Ollama, `bge-m3`, and development-assistance provenance and distribution boundary. |
| [`prizm-scope-manifest.json`](prizm-scope-manifest.json) | PRIZM JSON manifest 1.0 | Runtime, test/build, CI, container, model, and asset scope boundary. |
| [`SHA256SUMS`](SHA256SUMS) | SHA-256 checksums | Integrity record for the four JSON documents above. |

## Distribution boundary

The tracked source distribution contains PRIZM source, documentation,
configuration, the Gradle Wrapper, and synthetic fixtures. It does **not**
redistribute Java JARs, frontend `dist`, container images, PostgreSQL/pgvector
images or volumes, the Ollama binary, `bge-m3` weights/cache, or uploaded
documents. The CycloneDX files therefore record external/provided operational
dependencies, while [`../NOTICE`](../NOTICE) remains limited to the current
source-only distribution.

`bge-m3` is recorded separately from PRIZM's Apache-2.0 source license. Its
Ollama registry artifact is pinned by manifest/blob hashes, but the exact BAAI
upstream-to-registry conversion lineage remains `UNVERIFIED_LINEAGE`. PRIZM
does not redistribute those model bytes or claim that lineage as verified.

## Reproduce and verify

Run from the repository root with Java 17, Node 22.17.0, and npm 10.9.2:

```powershell
.\gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict
npm --prefix frontend run sbom
node scripts/verify-sbom.mjs --write-checksums
node scripts/verify-sbom.mjs
```

`--write-checksums` is an intentional update step. Review the JSON and checksum
diff before committing it. Normal verification must omit that option and fails
when the generated files drift from [`SHA256SUMS`](SHA256SUMS).

The frontend generator is a first-party, lockfile-only script. It does not
install packages, call a registry, infer missing licenses, or add a third-party
SBOM CLI to the source release. It deterministically maps every versioned
`package-lock.json` entry to a CycloneDX component and retains only lockfile
license fields, integrity hashes, resolved tarball URLs, and dependency scope.
The backend generator is a first-party Gradle task that reads the resolved
`runtimeClasspath`; it uses Gradle plus Groovy/JDK classes already present in
the build and adds no SBOM plugin. The repository structural verifier checks
both files' format, schema version, primary component, reproducibility fields,
checksum, and prohibited
local/secret-shaped data. Full license/SBOM CI enforcement is tracked by
`PRZ-002` T-09 and has not been added yet.

## Generator provenance

- backend: `generateBackendSbom` in [`build.gradle`](../build.gradle), first-party Apache-2.0 source; it uses Gradle's resolved runtime classpath plus Groovy/JDK classes already present in the build.
- frontend: [`scripts/generate-frontend-sbom.mjs`](../scripts/generate-frontend-sbom.mjs), first-party Apache-2.0 source; it uses only Node.js standard-library modules.

The runtime artifact identities are protected by
[`../gradle/verification-metadata.xml`](../gradle/verification-metadata.xml)
and [`../frontend/package-lock.json`](../frontend/package-lock.json). Both
generators are tracked PRIZM source and are covered by the repository's
Apache-2.0 license.
