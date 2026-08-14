# PRIZM Project Rules

These rules are the project-wide invariants for every human contributor and AI
agent. The detailed delivery procedure lives in
[`docs/ai-agent-workflow.md`](docs/ai-agent-workflow.md).

## Project identity and direction

- The project name is **PRIZM**. Do not rename it to CareerProof or PRIZM
  CareerProof.
- PRIZM aims to become an open-source Career Intelligence Engine with Reference
  Applications for career-document analysis, information structuring,
  evidence-backed search, and portfolio generation.
- The current product focus is an automated AI document management platform:
  upload career documents, preserve immutable versions, dispatch ChangeLog-based
  indexing, generate embeddings, and provide owner-scoped evidence search. MCP
  search is the nearest planned interface, not an implemented feature.
- The current Career Vault is a personal Reference App implemented as one Spring
  Boot application and one React frontend. It is not the whole PRIZM product.
- Reusable Engine modules, structured CareerFact data, and verified portfolio
  generation remain planned until source and executable tests prove otherwise.
- N2SF is no longer the product direction. Do not add N2SF-only classifications,
  approval flows, terminology, or security features.
- Source-only readiness, clean-clone verification, and the PRZ-005 OpenSQL
  integration are complete and integrated into GitHub `main`.
- PRZ-005 is `VERIFIED` for the OpenSQL+Ollama direct-`5432` synthetic TXT/PDF
  API and browser flows, including two-user document and search isolation. Its
  isolated OpenSQL opt-in integration test and final backend, frontend,
  OSS-readiness, SBOM, and documentation audit are also complete. PR #26 merged
  source `eab32c8` into `main` as merge commit `6dc9822`. PRZ-013 subsequently
  verified OpenProxy single-Primary TCP and SQL routing, `prizm_app`
  authentication, and the focused Flyway-direct/runtime-proxy TXT/PDF and
  Ollama integration flow. The competition-provided OpenSQL environment is
  restricted to a single-server installation, so PRZ-014 multi-node OpenHA and
  DB failover were rejected and are not part of the project roadmap. OpenProxy
  redundancy and multi-node service-continuity work are explicitly out of scope.
  The persistent journal remains unimplemented.

## Career evidence principle

- Never invent career experiences, technologies, achievements, or numbers.
- When evidence is absent, state that it was not found in the documents currently
  registered in PRIZM.
- Do not claim that a user's statement is false without sufficient evidence.

## Implementation truth

- Source code, applied Flyway migrations, and executable tests are the
  implementation truth. README, roadmap, specs, checkboxes, and plans are not
  implementation proof.
- Inspect the current source and tests before describing a feature as implemented.
- Treat specs as intent and traceability documents. Record pre-registry behavior
  only as `AS_BUILT_BASELINE`; never fabricate or backdate a spec, Issue, pull
  request, review, commit, or merge.
- Do not edit an already-applied Flyway migration. Add a forward migration.
- Do not advertise planned functionality as implemented. CareerFact, portfolio
  generation, MCP, and the independent Engine package remain unimplemented or
  unverified according to the current status documents. Do not reintroduce
  multi-node OpenHA or DB failover into the competition scope.

## Data, security, and behavior invariants

- Preserve user ownership across documents, versions, processing jobs, cleanup
  jobs, chunks, queries, and search results. New read and write paths must remain
  owner-scoped at the appropriate service, repository, SQL, and database levels.
- Preserve JWT authentication and database revalidation of the user's active
  state, email, and role. Do not let `SYSTEM_ADMIN` bypass personal `USER` data
  boundaries.
- A failed or incomplete document version must never become a search candidate.
  Activate a new version only after its chunks are complete, and preserve the
  previous `active_version_id` when processing fails.
- Preserve atomic activation of a completed version and stale-worker protection,
  including lease, recovery, and claim-version fencing contracts.
- Preserve immutable document versions, original-file storage, content hashing,
  the 12-value `DocumentType` contract, owner-scoped type filtering, TXT
  `TEXT_CHUNK` and PDF `PAGE` source metadata, and configured TXT/PDF limits.
- Preserve embedding dimension, finite-value, and non-zero-norm validation, plus
  the existing single-result and five-result Career Evidence search contracts.
- Preserve orphan-file cleanup recovery and descriptor-relative deletion with
  `SecureDirectoryStream`. If the safe filesystem primitive is unavailable,
  remain fail-closed rather than using an unsafe path-based fallback.

## Repository safety

- Preserve unrelated user changes, generated files, and untracked work. Do not
  reformat, delete, reset, or rewrite adjacent work without explicit scope.
- Never commit `.env`, credentials, tokens, private keys, uploaded originals,
  database volumes, model files or caches, vendor OpenSQL assets, IDE metadata,
  build output, or frontend dependency output.
- Keep `main` as the only long-lived branch. Use annotated or signed tags and
  GitHub Releases for releases, not permanent `release/*` or `archive/*` branches.
- Before deleting a temporary branch, inspect its merge base, unique commits,
  changed files, linked pull request, and merged state. Push the integrated
  `main` first, then delete only the exact local and remote branch names that were
  inspected.
- If work is deferred, rejected, or discarded, preserve the decision and any
  required evidence before deleting its branch.

## Environment and evidence boundaries

- Keep PostgreSQL·pgvector results separate from OpenSQL results. PostgreSQL
  success is not OpenSQL evidence.
- Verified OpenSQL results are limited to the actual single-node SQL Gates,
  PRZ-005 direct-`5432` API/browser/two-user isolation, and PRZ-013 OpenProxy
  single-Primary SQL routing. Do not expand them to OpenProxy redundancy,
  multi-node DB failover, or service continuity.
- Record unavailable or unexecuted checks as `NOT_RUN` or `NOT_VERIFIED`, never
  `PASS`. Keep historical results separate from checks rerun on the current
  source.

## Document sources of truth

| Topic | Single source |
|---|---|
| Current implemented and verified state | [`docs/project-status.md`](docs/project-status.md) |
| Current architecture and data flow | [`docs/architecture.md`](docs/architecture.md) |
| Product development order | [`docs/roadmap.md`](docs/roadmap.md) |
| License, redistribution boundary, SBOM and checksums | [`LICENSE`](LICENSE), [`NOTICE`](NOTICE), [`sbom/`](sbom/README.md) |
| Spec lifecycle and evidence registry | [`specs/README.md`](specs/README.md) |
| Detailed contribution and agent workflow | [`docs/ai-agent-workflow.md`](docs/ai-agent-workflow.md) |

Treat `docs/archive/` as historical context, not current truth.

## Required workflow

- Use the full `ORIENT -> SPEC -> PLAN -> IMPLEMENT -> VERIFY -> AUDIT ->
  INTEGRATE` workflow for features, observable behavior or contract changes,
  migrations, security changes, infrastructure changes, release changes, and
  large structural refactors.
- A reduced documentation procedure is allowed only for typos, broken links,
  date corrections, explanation improvements that do not change implementation,
  and wording corrections of already established facts.
- Never use the reduced procedure when source, migration, configuration,
  dependency, test behavior, API behavior, security, ownership, infrastructure,
  or runtime claims change.
- Even a documentation-only change must report the files changed, the evidence
  checked, and the exact verification result.
- At task start, state the authorized stage or stages. Unless the user explicitly
  authorizes an end-to-end run, stop after the current stage Gate.
- Follow the stage Gates, stop conditions, document update matrix, branch and PR
  procedure, solo-maintainer review rule, deferred/rejected handling, and exact
  verification commands in
  [`docs/ai-agent-workflow.md`](docs/ai-agent-workflow.md).
