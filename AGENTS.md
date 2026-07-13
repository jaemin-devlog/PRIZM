# PRIZM Project Direction

## Project name

PRIZM

Do not rename the project to CareerProof or PRIZM CareerProof.

## Current product direction

PRIZM is a personal career evidence document platform.

Users will store resumes, portfolios, project reports, school assignments,
certificates, career reviews, job postings, and previous application documents.

PRIZM will search those documents to find real experiences and original evidence
that can be used for future job applications.

## Core principle

PRIZM must not invent career experiences, technologies, achievements, or numbers.

When evidence cannot be found, the system must state that evidence was not found
in the documents currently registered in PRIZM.

It must not state that the user's claim is false unless the system has sufficient
evidence to make that conclusion.

## Current implementation phase

The current phase is codebase transition and platform foundation cleanup.

Do not implement the full career product unless a later task explicitly requests it.

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
- Flyway migrations
- OpenSQL compatibility work

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
- Keep the frontend honest about its current scope. It is an initial shell until
  functional screens and API clients are actually implemented.
- Record meaningful implementation, refactoring, design, and infrastructure
  verification work briefly in `docs/development-log.md`.

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
