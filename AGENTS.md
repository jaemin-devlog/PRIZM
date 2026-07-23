# PRIZM Project Direction

## Project name

PRIZM

Do not rename the project to CareerProof or PRIZM CareerProof.

## Current product direction

PRIZM is an open-source Career Intelligence Engine and a set of Reference
Applications for career-document analysis, information structuring,
evidence-backed search, and portfolio generation.

The product target is to provide reusable modules and extension points so
individuals, universities, career-support organizations, companies, and
developers can build career-management services suited to their environments.

The current Career Vault is the personal Reference App that demonstrates the
Engine's capabilities and integration patterns. It is not the whole PRIZM
product. Reusable engine modules, structured CareerFact data, and verified
portfolio generation remain planned work until source and executable tests prove
otherwise.

## Core principle

PRIZM must not invent career experiences, technologies, achievements, or numbers.

When evidence cannot be found, the system must state that evidence was not found
in the documents currently registered in PRIZM.

It must not state that the user's claim is false unless the system has sufficient
evidence to make that conclusion.

## Current implementation phase

The current phase is 2026 TmaxTibero contest P0/P1: official-source and license
readiness, followed by actual OpenSQL and clean-clone evidence.

For contest work, prioritize actual OpenSQL, DB failover evidence, change-log
synchronization, and MCP search before CareerFact or portfolio work. Do not implement
the full career product unless a later task explicitly requests it.

## Preserve

- document version management
- active_version_id
- document chunks
- embeddings
- pgvector search
- asynchronous processing jobs
- job lease
- claim-version fencing
- retry and backoff
- worker crash recovery
- atomic activation of completed document versions
- JWT authentication
- database revalidation of users and roles
- content hashing
- original file storage
- orphan-file cleanup job processing with lease, claim-version fencing, retry/backoff, and recovery
- descriptor-relative cleanup deletion with SecureDirectoryStream fail-closed behavior
- Flyway migrations
- OpenSQL compatibility work
- user ownership across documents, versions, jobs, and chunks
- the 12-value DocumentType contract and owner-scoped type filter
- TXT `TEXT_CHUNK` and PDF `PAGE` source metadata
- text-layer PDF validation, page extraction, and configured processing limits
- embedding dimension, finite-value, and non-zero-norm validation
- the existing single-result search and five-result Career Evidence API contracts
- frontend login, Career Vault list/filter, TXT/PDF upload, document detail/edit/delete,
  immutable version upload, PDF thumbnail/original viewing, and up-to-five-result Career
  Evidence search flows

## Do not assume

Do not claim OpenSQL, OpenProxy, or OpenHA compatibility unless it is proven by
actual code and executable tests.

Do not assume a feature is implemented based only on README files or planning documents.

Always inspect the current source code and tests.

## Previous direction

N2SF is no longer the product direction.

Do not add new N2SF classifications, N2SF approval flows, network-security-grade
features, or N2SF-specific terminology.

Old N2SF-only code and documents should be removed when they have no reusable
platform value.

## Current technical baseline

- Java 17, Spring Boot 4.1, Gradle Wrapper
- React, TypeScript, and Vite under `frontend/`
- PostgreSQL 16 with pgvector for local development and integration tests
- Flyway for forward-only schema changes
- JPA for document and user metadata, JdbcTemplate for vector search and job claims
- Ollama `bge-m3` embeddings with the currently verified dimension of 1024

## Working rules

- Treat source code, migrations, and executable tests as the implementation truth.
- Do not edit an already-applied Flyway migration. Add a forward migration instead.
- Preserve unrelated user changes and generated files.
- Do not commit `.env`, credentials, tokens, uploaded originals, database volumes,
  model files, IDE metadata, build output, or frontend dependency output.
- Keep the frontend honest about its current scope. Login, list/filter, TXT/PDF upload,
  document detail/edit/delete, immutable version upload, PDF thumbnail/original viewing,
  and up-to-five-result Career Evidence search are implemented. CareerFact and portfolio
  generation are not.
- Keep `main` as the only long-lived branch. Temporary branches must be integrated,
  documented as rejected experiments, or discarded with evidence, then deleted.
- Before deleting a temporary branch, inspect its merge base, unique commits, changed
  files, and linked pull request. Push the integrated `main` first, then delete the
  exact local and remote branch names.
- Preserve releases with annotated or signed tags and GitHub Releases, not long-lived
  `release/*` or `archive/*` branches.
- Record functionality that predates the spec registry only as `AS_BUILT_BASELINE`.
  Do not fabricate or backdate Issues, pull requests, reviews, or pre-implementation specs.
- Treat specs as intent and traceability documents, not implementation proof. Source code,
  Flyway migrations, and executable tests remain the implementation truth.

## Staged delivery workflow

Use `ORIENT -> SPEC -> PLAN -> IMPLEMENT -> VERIFY -> AUDIT -> INTEGRATE` as the
PRIZM internal contest evidence workflow for every contest-scoped feature, behavior
change, migration, refactor, or infrastructure change. It is stricter than the public
evaluation examples; do not describe every internal gate as an official contest
requirement. At task start, state the authorized stage or stages. Unless the user
explicitly requests an end-to-end run, stop after each stage and wait. Never mark a
stage complete while its gate is unmet.

| Stage | Required output and gate |
|---|---|
| `ORIENT` | Read `AGENTS.md`, `docs/README.md`, `docs/project-status.md`, `docs/roadmap.md`, `specs/README.md`, the related spec, source, migrations, and tests. For contest work also read `docs/contest/2026-tmaxtibero-plan.md`, `docs/contest/2026-requirements-traceability.md`, and the license audit when it exists. Report current behavior, scope, risks, affected files, and verification plan. Do not modify files. |
| `SPEC` | Allocate a new `PRZ-###` for a new capability, observable contract change, or material corrective work against an `AS_BUILT_BASELINE` or `VERIFIED` spec. Corrective work inside an active spec stays in that spec. Create `spec.md` with scenarios, requirements, preserved contracts, exclusions, and measurable acceptance criteria. A documentation-only correction with no product behavior change may omit a new spec when the reason is recorded in the development log. For contest implementation, create or identify a real current GitHub Issue only when that external write is authorized, then record its URL. Never create an Issue for completed work. |
| `PLAN` | For contest-scoped product code, create `plan.md` and `tasks.md` before implementation. Record expected file and API changes, Flyway strategy, ownership and security effects, dependency and license effects, test environments, recovery approach, and the temporary branch and PR plan. A documentation-only correction may omit them with the reason recorded in the development log. |
| `IMPLEMENT` | Start a temporary `codex/PRZ-###-<slug>` branch from updated `main`, implement only the approved vertical slice, add or update executable tests, keep commits focused, and update `tasks.md`. Material scope changes return to `SPEC` or `PLAN`. |
| `VERIFY` | Run the applicable commands below and all spec-specific tests. Record exact commands, commit, environment, pass/fail/skip counts, PostgreSQL, pgvector, Docker, Ollama, and OpenSQL usage, and any existing Issue URL in `evidence.md`. Unavailable required evidence is `NOT_RUN`, never `PASS`. |
| `AUDIT` | Perform an independent read-only review of the diff against the spec, preserved contracts, ownership and security rules, migrations, tests, documentation, and licenses. Resolve blocking findings or return to an earlier stage. An agent audit is evidence, but it is not a GitHub review. |
| `INTEGRATE` | When GitHub writes are authorized, open a real PR containing the actual change and link its Issue, spec, tasks, and evidence. Add the real PR URL to `evidence.md`, run required checks, and request genuine review when a reviewer is available; never manufacture approval. For a solo project with no reviewer, integration requires a completed independent audit, explicit user approval, and `REVIEW_NOT_AVAILABLE_SOLO` recorded in evidence. After merge, add the merge/source commit and last-verified date, update and push `main`, then apply the existing branch-inspection and deletion rules so `main` remains the only long-lived branch. |

An Issue, PR, review, commit, or merge counts as contest evidence only when it was
produced during the actual work it describes. If GitHub access or authorization is
unavailable, record that limitation and stop at the affected external gate instead of
inventing an identifier or URL.

If the user explicitly authorizes local-only work while GitHub is unavailable, it may
continue, but it does not count as Issue, PR, or review evidence. If work is deferred or
rejected, set the spec status accordingly, record the reason and restart condition or
rejection evidence in `evidence.md` and `docs/development-log.md` on `main`, inspect the
temporary branch and its unique commits, preserve any required decision evidence, and
then delete the branch under the existing safety rules.

Use these lifecycle transitions:

- approved `SPEC` -> `PLANNED`
- start `IMPLEMENT` -> `IN_PROGRESS`
- code complete with required evidence missing -> `IMPLEMENTED_UNVERIFIED`
- required `VERIFY` plus `AUDIT` complete -> `VERIFIED`
- paused or declined work -> `DEFERRED` or `REJECTED`
- after integration -> update the registry source commit and last-verified date

### Document update matrix

| Artifact | Update rule |
|---|---|
| `AGENTS.md` | Only when project-wide direction, preservation constraints, or workflow rules change. |
| `docs/README.md` | When documents are added, moved, renamed, archived, or removed. |
| `docs/project-status.md` | Only when source plus executable evidence changes the current implemented or verified state. |
| `docs/roadmap.md` | When priority, order, gate, or stage state changes; do not describe implementation here. |
| `docs/contest/2026-requirements-traceability.md` | When an official source changes or a mapped implementation, environment, evaluation, or submission state changes. |
| `docs/contest/2026-tmaxtibero-plan.md` | When the contest schedule, scope, priority, or stop condition changes. |
| `specs/PRZ-###/spec.md` | Define intent before implementation; update when approved scope or acceptance criteria change. |
| `specs/PRZ-###/plan.md` and `tasks.md` | Write before implementation; maintain design decisions, task state, and deviations while work proceeds. |
| `specs/PRZ-###/evidence.md` | During `VERIFY` and `AUDIT`, map each requirement to source, migration, test, environment result, license evidence, and any existing Issue link. During `INTEGRATE`, add the real PR, review status, merge commit, and final source commit. |
| `specs/README.md` | When a spec is created or its lifecycle status, source commit, or last-verified date changes. |
| `README.md` | When public scope, setup, demo flow, supported environment, or required attribution changes. Do not advertise planned features as implemented. |
| `LICENSE`, `NOTICE`, and `docs/contest/2026-license-audit.md` | Before integration whenever a dependency, model, sample dataset, asset, or redistributed component changes. Create the audit file during the current P0 license-audit task, not as an empty placeholder. |
| `docs/development-log.md` | Briefly after meaningful implementation, design decisions, verification, rejection, or integration; link the current spec and real GitHub artifacts when available. |

Treat everything under `docs/archive/` as historical context, not current truth.

### Stop conditions

- Stop before `IMPLEMENT` when the spec, acceptance criteria, or preservation impact is
  missing or ambiguous. Missing GitHub authorization follows the documented local-only
  or deferred path; it must never produce invented evidence.
- Stop before `VERIFY` and return to planning when implementation expands materially
  beyond the approved scope.
- Do not set `VERIFIED` or enter `INTEGRATE` while required tests fail, required
  environments are `NOT_RUN`, license conflicts remain, documentation contradicts
  source, or blocking audit findings remain.
- For contest-scoped code with no genuine reviewer, stop before integration until the
  user approves the documented solo exception or defers the work. Do not present an
  agent audit as GitHub approval.
- Do not delete a temporary branch until its ancestry, unique commits, changed files,
  linked PR, merged state, and pushed `main` have been confirmed under the existing
  branch rules.

## Verification

Use the repository's actual commands and do not silently skip required external
integration tests.

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

When PostgreSQL, pgvector, Docker, or Ollama is required, report whether it was
actually used. Keep OpenSQL-specific results separate from PostgreSQL results.
