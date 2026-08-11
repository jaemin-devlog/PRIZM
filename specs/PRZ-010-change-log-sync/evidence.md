# PRZ-010 — 변경 로그 동기화 Evidence

## 판정

`VERIFIED`

- branch: `PRZ-010-change-log-sync`
- 검증 source: 현재 작업 트리(아직 commit하지 않음)
- 검증일: `2026-08-12`
- 환경: Windows PowerShell, Java 17, Gradle 9.5.1, Docker Desktop Engine
  29.6.2, PostgreSQL+pgvector Testcontainers, 실제 OpenSQL VM direct TCP/JDBC
  `5432`, 실제 Ollama `localhost:11434`의 `bge-m3` 1024차원 임베딩

PRZ-010은 문서 버전 생성 사실을 owner-scoped ChangeLog에 먼저 기록하고, 짧은
Dispatcher transaction이 기존 `INDEXING` ProcessingJob을 멱등적으로 확보한 뒤 기존
Indexing Worker가 색인하도록 분리한다. 모든 P1~P10 Gate가 통과했다. PostgreSQL
결과와 실제 OpenSQL 결과는 아래처럼 별도로 기록하며, PostgreSQL 통과만으로 OpenSQL
통과를 판정하지 않았다.

## Gate 결과

| Gate | 결과 | 실제 근거 |
|---|---|---|
| P1 Migration + ChangeLog domain | `PASS` | V14 `document_change_logs`, check/unique/index/composite FK와 nullable Job one-to-one 제약의 database test |
| P2 Upload → ChangeLog | `PASS` | 업로드 transaction의 QUARANTINED version + PENDING ChangeLog, ProcessingJob 0건과 rollback/보상 계약 test |
| P3 Dispatcher + idempotency | `PASS` | `FOR UPDATE SKIP LOCKED`, `INSERT ... ON CONFLICT DO NOTHING`, 기존 Job 재사용과 transaction rollback test |
| P4 Failure recorder + retry | `PASS` | Transaction A/B 분리, retry/backoff, 최종 dispatch 실패와 이전 active version 보존 test |
| P5 Version/delete guard | `PASS` | QUARANTINED/PROCESSING guard, owner isolation, ChangeLog → ProcessingJob → version 삭제 순서 test |
| P6 Frontend guard | `PASS` | QUARANTINED/PROCESSING in-flight 표시와 lint/build |
| P7 PostgreSQL integration | `PASS` | PostgreSQL+pgvector Testcontainers에서 schema, dispatch, replay, 경쟁, 실패, owner isolation |
| P8 실제 OpenSQL SQL Gate | `PASS` | 실제 OpenSQL direct JDBC `5432`에서 V1~V14와 V14 계약 assertion |
| P9 실제 OpenSQL+Ollama E2E | `PASS` | 실제 OpenSQL direct JDBC `5432`와 실제 `bge-m3`로 V1→V2 전환 및 실패 보존 3개 시나리오 |
| P10-A 회귀 | `PASS` | backend, integration, frontend, Compose, diff 감사 통과 |
| P10-B Evidence/상태 문서 | `PASS` | 이 Evidence, Tasks, Registry, Project Status와 `git diff --check` 일치 |

## PostgreSQL 검증

- ChangeLog 전용 PostgreSQL database integration에서 schema, dispatch, replay,
  동시 claim, failure, owner isolation을 통과했다.
- P10-A 전체 `integrationTest`는 PostgreSQL+pgvector Testcontainers에서
  `104 completed, 7 skipped, 0 failures`로 통과했다.
- 이 결과는 PostgreSQL 범위의 근거이며 P8/P9 OpenSQL `PASS`와 대체하지 않는다.

## 실제 OpenSQL direct `5432` 검증

P8은 OpenSQL VM의 direct TCP/JDBC `5432`에서 실행했다. 최신 Flyway V1~V14 적용과
V14 적용 성공, `document_change_logs` table, check/unique/index/FK를 확인했다. 또한
`event_key` 중복, `(document_version_id, event_type)` 중복, nullable
`processing_job_id` one-to-one 중복, owner/version/job가 맞지 않는 composite FK 연결이
DB에서 거부되는 것을 확인했다. `FOR UPDATE SKIP LOCKED` 동시 ChangeLog claim,
`INSERT ... ON CONFLICT DO NOTHING` ProcessingJob idempotency, owner isolation,
기존 migration data 및 PRZ-010 이전 ProcessingJob 보존과 ChangeLog backfill 없음도
직접 assertion으로 통과했다.

P9은 별도 임시 OpenSQL database/owner role과 실제 Ollama `bge-m3`를 사용했다.
V1을 ACTIVE로 만들고 V1 고유 질의가 검색되는 것을 확인한 뒤, V2 업로드 직후
`QUARANTINED` + ChangeLog `PENDING` + ProcessingJob 0건을 확인했다. Dispatcher 뒤에는
ChangeLog `DISPATCHED` + V2 ProcessingJob `PENDING`이 되었고, 기존 Indexing Worker가
실제 원문 추출·chunk·1024차원 embedding·OpenSQL pgvector 저장을 수행해 V2를 ACTIVE로
전환했다. `documents.active_version_id`는 V2가 되었으며 V2 고유 질의는 V2를 반환하고
V1은 결과 대상에서 제외됐다. dispatch 최종 실패와 indexing 최종 실패에서도 V1 ACTIVE와
V1 검색 가능 상태가 유지되는 시나리오도 통과했다.

P9 시작 전 P9 전용 role의 Flyway 인증이 실패한 적이 있다. 이는 credential bootstrap/
전달 경로의 환경 문제였고 제품 코드 문제는 아니었다. 기존 PRIZM credential을 추측하거나
변경하지 않았으며, 승인된 일회성 임시 경로로 정정한 뒤 실제 E2E를 통과했다. 검증 후
P8/P9 임시 database, role, runner와 credential 흔적은 제거했고 catalog 잔존은 0건으로
확인했다. credential 값은 출력하거나 기록하지 않았다.

## P10-A 회귀

| 명령 | 결과 | 범위 |
|---|---|---|
| `docker version`, `docker info` | `PASS` | Docker Desktop Engine 29.6.2와 Testcontainers 사용 가능 확인 |
| `.\gradlew.bat test --no-daemon` | `PASS` | backend unit/search-evaluation test task |
| `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` | 104 completed, 7 skipped, 0 failures |
| `npm.cmd --prefix frontend run lint` | `PASS` | PowerShell 실행 정책이 `npm.ps1`을 차단해 동일 npm script를 Windows shim으로 실행 |
| `npm.cmd --prefix frontend run build` | `PASS` | TypeScript와 Vite production build |
| `docker compose config --quiet` | `PASS` | Compose 구성 검증 |
| `git diff --check` | `PASS` | whitespace 오류 0 |

P10-A 중 기존 통합 테스트만 V14 계약에 맞췄다. 업로드 직후 ProcessingJob을 기대하던
test는 ChangeLog `PENDING`과 Job 0건을 확인한 뒤 Dispatcher를 명시 호출하도록 바꿨고,
cleanup은 FK 순서에 따라 ChangeLog를 먼저 삭제하도록 정정했으며 Flyway 기대값은 V14로
갱신했다. 이 변경은 제품 동작을 바꾸지 않았고 제품 코드는 수정하지 않았다.

최종 diff 감사에서 PRZ-008/search evaluation, `DocumentTextExtractor`, `TextChunker`,
embedding model/dimension, Flyway V1~V13, dependency, license와 SBOM 변경은 0건이었다.

## 남은 제한

- 검증 범위는 OpenSQL direct `5432`이다. OpenProxy SQL routing/인증, OpenHA, DB
  failover와 영구 journal은 검증하지 않았으며 여전히 별도 후속 범위다.
- P10은 회귀와 Evidence 단계만 수행했다. 새 검색 알고리즘, parser/chunker, embedding
  model, frontend 기능, MCP 또는 PRZ-008 변경은 수행하지 않았다.
- 이 Evidence의 검증 source는 아직 commit/PR/merge되지 않은 현재 작업 트리다.
