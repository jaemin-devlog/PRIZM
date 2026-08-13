# PRZ-010 — 변경 로그 동기화 Evidence

> **최종 판정:** `VERIFIED`
> **검증 기준 소스:** `26c546b16eb9ea42d98460dd6e5aa0bf0752212a`
> **검증일:** 2026-08-12

## 판정 요약

문서 버전 생성 사실을 owner-scoped ChangeLog로 기록하고, 짧은 Dispatcher
transaction이 기존 `INDEXING` ProcessingJob을 멱등적으로 확보한 뒤 기존 Indexing
Worker에 전달하는 P1–P10 Gate를 모두 통과했다.

검증 환경은 Windows PowerShell, Java 17, Gradle 9.5.1, Docker Desktop Engine
29.6.2, PostgreSQL·pgvector Testcontainers, 실제 OpenSQL VM direct TCP/JDBC
`5432`, 실제 Ollama `localhost:11434`의 `bge-m3` 1024차원 임베딩이었다.
PostgreSQL과 OpenSQL 결과는 별도로 판정했다.

## 검증한 수직 흐름

실제 OpenSQL과 Ollama 환경에서 다음 흐름을 확인했다.

```text
V1 ACTIVE
→ V2 업로드
→ V2 QUARANTINED + ChangeLog PENDING
→ ProcessingJob 0건
→ Dispatcher 실행
→ ChangeLog DISPATCHED + ProcessingJob PENDING
→ 기존 Indexing Worker 실행
→ V2 chunk 및 vector 저장
→ V2 ACTIVE + active_version_id = V2
→ V2 검색 결과만 반환
```

- dispatch 최종 실패 시 V1 `ACTIVE`와 V1 검색 결과가 유지됐다.
- indexing 최종 실패 시에도 V1 `ACTIVE`와 V1 검색 결과가 유지됐다.
- 두 사용자의 ChangeLog, ProcessingJob, version, chunk와 검색 결과가 격리됐다.

## 요구사항별 근거

### PRZ-010-R1 — 원자적 ChangeLog 기록

- 판정: `PASS`
- 구현 근거: 업로드 transaction이 `QUARANTINED` version과 owner-scoped
  `DOCUMENT_VERSION_CREATED` ChangeLog를 함께 저장한다.
- 테스트 근거: V1·V2 업로드, 부분 실패 rollback과 원본 파일 보상 경로를 검증했다.
- 실행 환경: PostgreSQL·pgvector Testcontainers와 실제 OpenSQL direct `5432`.

### PRZ-010-R2 — 유일한 Job 생성 진입점

- 판정: `PASS`
- 구현 근거: 업로드 서비스의 직접 ProcessingJob 생성 경로를 제거하고 Dispatcher가
  신규 version의 `INDEXING` Job을 생성한다.
- 테스트 근거: 업로드 직후 Job 0건과 Dispatcher 뒤 Job 1건을 확인했다.

### PRZ-010-R3 — event와 Job 멱등성

- 판정: `PASS`
- 구현 근거: event key·version/event unique, ProcessingJob unique,
  `INSERT ... ON CONFLICT DO NOTHING`과 원자적 dispatch를 사용한다.
- 테스트 근거: replay, concurrent claim, 기존 Job 재사용과 중복 거부를 검증했다.

### PRZ-010-R4 — 짧은 Dispatcher transaction

- 판정: `PASS`
- 구현 근거: Transaction A가 `FOR UPDATE SKIP LOCKED`, Job 생성·조회,
  owner·version 확인, 연결과 `DISPATCHED`만 수행한다.
- 테스트 근거: transaction rollback 뒤 고아 Job과 거짓 `DISPATCHED`가 없고,
  FileStorage·parser·chunker·Ollama·embedding·vector 호출이 0건임을 확인했다.

### PRZ-010-R5 — version과 삭제 guard

- 판정: `PASS`
- 구현 근거: owner-scoped 최신 version을 1차로, non-terminal Job을 2차로 검사한다.
- 테스트 근거: V2 `QUARANTINED`·Job 0건 구간에서 V3 업로드와 문서 삭제가
  `DOCUMENT_PROCESSING`으로 거부됐다.

### PRZ-010-R6 — 실패와 이전 active 보존

- 판정: `PASS`
- 구현 근거: dispatch 최종 실패는 ChangeLog와 아직 `QUARANTINED`인 version을
  `FAILED`로 만들고 기존 `active_version_id`는 바꾸지 않는다.
- 테스트 근거: dispatch와 indexing 최종 실패 시나리오에서 V1 검색을 확인했다.

### PRZ-010-R7 — 기존 기술 계약 보존

- 판정: `PASS`
- 구현 근거: immutable version, hash, owner 경계, 파일 보상, Worker lease·fencing과
  atomic activation 경로를 유지한다.
- 테스트 근거: backend 전체 test, integration test와 최종 diff 감사를 통과했다.

### PRZ-010-R8 — migration과 환경 분리

- 판정: `PASS`
- 구현 근거: V14 forward-only migration을 추가했으며 V1–V13은 수정하지 않았다.
- 테스트 근거: PostgreSQL과 OpenSQL direct 결과를 각각 실행·기록했다. 과거
  ChangeLog backfill은 없었다.

### PRZ-010-R9 — V1에서 V2까지의 수직 흐름

- 판정: `PASS`
- 구현 근거: ChangeLog Dispatcher가 기존 Indexing Worker와 검색 경로를 재사용한다.
- 테스트 근거: 실제 OpenSQL·Ollama 환경에서 V1 `ACTIVE`부터 V2 검색까지 확인했다.

### PRZ-010-R10 — 검색·평가 영역 분리

- 판정: `PASS`
- 구현 근거: PRZ-008 검색 알고리즘·평가·dataset과 기존 parser·chunker·embedding
  구현을 변경하지 않았다.
- 테스트 근거: 최종 diff 감사에서 해당 영역 변경이 0건이었다.

### PRZ-010-R11 — Transaction A와 B 분리

- 판정: `PASS`
- 구현 근거: A의 rollback 뒤 별도 Failure Recorder Transaction B가 retry 또는
  최종 실패를 기록한다. `DISPATCHED` 뒤 색인 상태는 ProcessingJob과 version만
  나타낸다.
- 테스트 근거: retry·backoff, B의 non-regression과 B DB 실패 시 마지막 상태 보존을
  확인했다.

### PRZ-010-R12 — writer 전환 경계

- 판정: `PASS`
- 구현 근거: 구 writer와 신 writer가 신규 업로드를 동시에 처리하지 않는 배포
  조건과 Dispatcher를 포함한 roll-forward 복구를 문서화했다.
- 제한: 실제 무중단 rolling deployment는 범위에 포함하지 않았다.

## 자동 검증

### Backend

- 명령: `./gradlew.bat test --no-daemon`
- 결과: `PASS`. backend unit과 search-evaluation test task를 통과했다.
- 명령: `./gradlew.bat integrationTest --no-daemon --rerun-tasks`
- 결과: `PASS`. `104 completed, 7 skipped, 0 failures`였다.
- 범위: PostgreSQL·pgvector Testcontainers의 schema, dispatch, replay, 경쟁,
  failure와 owner isolation을 포함한다.

### Frontend

- 명령: `npm.cmd --prefix frontend run lint`
- 결과: `PASS`.
- 명령: `npm.cmd --prefix frontend run build`
- 결과: `PASS`. TypeScript와 Vite production build를 통과했다.
- 참고: PowerShell 실행 정책이 `npm.ps1`을 차단해 같은 npm script를 Windows
  shim인 `npm.cmd`로 실행했다.

### 문서 및 정적 검사

- 명령: `docker compose config --quiet`
- 결과: `PASS`.
- 명령: `git diff --check`
- 결과: `PASS`. whitespace 오류는 0건이었다.
- 감사 결과: PRZ-008·search evaluation, `DocumentTextExtractor`, `TextChunker`,
  embedding model·dimension, Flyway V1–V13, dependency, license와 SBOM 변경은
  0건이었다.

## 실제 환경 검증

### PostgreSQL

- ChangeLog 전용 database integration test에서 schema, dispatch, replay,
  concurrent claim, failure와 owner isolation을 통과했다.
- 이 결과는 PostgreSQL 범위의 근거이며 OpenSQL 판정을 대체하지 않는다.

### OpenSQL direct `5432`

- Flyway V1–V14와 V14 적용을 확인했다.
- `document_change_logs`의 check·unique·index·FK를 확인했다.
- event key 중복, version/event 중복, ProcessingJob one-to-one 중복과
  owner·version·job가 다른 composite FK 연결이 DB에서 거부됐다.
- `FOR UPDATE SKIP LOCKED` concurrent claim과 `ON CONFLICT` Job idempotency를
  확인했다.
- 기존 migration data와 PRZ-010 이전 ProcessingJob은 보존됐으며 소급
  ChangeLog는 생기지 않았다.

### OpenSQL·Ollama E2E

- 별도 임시 OpenSQL database·owner role과 실제 Ollama `bge-m3`를 사용했다.
- 원문 추출, chunk 생성, 1024차원 embedding과 OpenSQL pgvector 저장을 거쳐 V2가
  `ACTIVE`로 전환됐다.
- V2 고유 질의는 V2를 반환했고 V1은 결과에서 제외됐다.
- dispatch와 indexing 최종 실패 시 V1 보존 시나리오를 각각 통과했다.

## Audit 및 GitHub 통합

- Evidence anchor commit: `05a15337eb810e68c35e07f04e4b19a2c5f7785f`.
- GitHub 통합: [PR #39](https://github.com/jaemin-devlog/PRIZM/pull/39), `merged`.
- `main` 통합 merge commit:
  `d616dac95b5d29c6f45babb51435d95d20f39fa8`.
- 검증 source와 merge commit은 현재 `main`의 ancestor다.
- PR head는 merge commit의 두 번째 parent이며 `main`에 없는 고유 commit은 0건이다.
- GitHub Actions CI run `31510048694`와 OSS Readiness run `31510048703`은
  `completed / success`다.
- 제출된 PR review는 0건이다. `REVIEW_NOT_AVAILABLE_SOLO`는 절차 기록이며 실제
  review evidence가 아니다.

## 남은 제한

- OpenSQL 검증 경계는 direct `5432`다.
- OpenProxy SQL routing·인증, OpenHA, DB failover와 영구 journal은 검증하지
  않았으며 후속 범위다.
- P10은 회귀와 Evidence 정리 단계였다. 검색 알고리즘, parser·chunker, embedding
  model, frontend 신기능, MCP와 PRZ-008 변경은 수행하지 않았다.
- 이 Evidence의 후속 문서 commit은 제품과 test 동작을 변경하지 않는다.

## 주요 검증 이력

- P9 시작 전 전용 role의 Flyway 인증이 한 차례 실패했다. credential bootstrap·전달
  경로의 환경 문제였으며 제품 코드 문제는 아니었다.
- 기존 credential을 추측하거나 변경하지 않았다. 승인된 일회성 임시 경로로
  정정한 뒤 E2E를 통과했다.
- 검증 뒤 P8·P9 임시 database, role, runner와 credential 흔적을 제거했고 catalog
  잔존 0건을 확인했다. credential 값은 출력하거나 기록하지 않았다.
- P10-A에서는 기존 통합 테스트를 V14 ChangeLog → Dispatcher → ProcessingJob
  계약, FK cleanup 순서와 Flyway V14 기대값에 맞췄다. 제품 동작은 바꾸지 않았다.
