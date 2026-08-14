# PRZ-015 — 읽기 전용 MCP Career Evidence 검색 Evidence

## 현재 판정

- 상태: `VERIFIED`
- P0 기준 source: `13f6dd970a24a8966e36fab6b12aa4f894ebed4e`
- 검증 source commit: `97c01cb076acf91e8433894e71a5d3c156b994f2`
- GitHub 통합: [PR #46](https://github.com/jaemin-devlog/PRIZM/pull/46) (`merge pending`)
- 검증일: 2026-08-15
- P0 Gate: `PASS`
- P1 VERIFY: `PASS`
- P1 AUDIT: `PASS`
- P1 최종 판정: `PASS`
- P2 실제 OpenSQL/OpenProxy MCP E2E: `PASS`
- P3 OSS 문서 통합: `PASS`

## P0 기준선

- remote fetch 뒤 local `main`, `origin/main`, `FETCH_HEAD`가 모두 `13f6dd9`였다.
- 작업 시작 시 worktree는 clean이고 PRZ Registry는 PRZ-014까지 사용했다.
- PR #45의 PRZ-014 merge가 HEAD이며 다중 OpenHA·DB failover는 `REJECTED`다.
- build는 Spring Boot `4.1.0`, Spring AI BOM `2.0.0`, Java 17과
  `spring-boot-starter-webmvc`를 사용한다.
- REST V2는 `CurrentUserProvider.userId()`를
  `SearchService.searchCareerEvidenceV2(...)`에 전달한다.
- `VectorSearchRepository`는 document/version/chunk owner와 ACTIVE version을 모두
  SQL에서 제한한다.
- 현재 OpenSQL 경계는 Flyway direct `:5432`, runtime OpenProxy `:6432` 단일 Primary
  verified 상태다. P1은 이 환경과 schema를 변경하지 않는다.

## P0 MCP 선택 근거

- Spring AI 2.0.0 공식 문서는 `spring-ai-starter-mcp-server-webmvc`에
  `spring.ai.mcp.server.protocol=STATELESS`를 설정하는 구성을 지원한다.
- stateless server는 단순 request-response tool과 맞고 sampling, elicitation,
  roots와 client request가 없다.
- 기본 stateless endpoint는 `/mcp`, 기본 annotation scanner는 enabled다.
- 정확한 resolved Java SDK version, protocol constant, license와 SecurityContext 전파는
  P1 dependency 해석·focused test에서 기록한다.
- starter가 광범위 Origin allowlist를 자동 구성한다고 확인되지 않았으므로 임의 CORS
  확장을 추가하지 않고 현재 allowed-origin 정책과 남은 위험을 P1 audit에 남긴다.

## P1 구현

- production 변경 파일은 4개다.
  - `build.gradle`: BOM 관리 MCP WebMVC server starter 추가
  - `src/main/java/com/prizm/mcp/CareerEvidenceMcpTool.java`: tool과 전용 response
  - `src/main/java/com/prizm/auth/config/SecurityConfiguration.java`: `/mcp` `ROLE_USER`
  - `src/main/resources/application.yml`: sync stateless server와 tool-only capabilities
- tool은 `CurrentUserProvider.userId()`를 읽고 기존
  `SearchService.searchCareerEvidenceV2(...)`만 호출한다.
- 검색 service·profile·repository·SQL·embedding·evidence localization, migration,
  frontend와 PRZ-009 변경은 0건이다.

## Resolved dependency와 protocol

- starter: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:2.0.0`
- resolved Java SDK: `io.modelcontextprotocol.sdk:mcp:2.0.0`, `mcp-core:2.0.0`,
  `mcp-json-jackson3:2.0.0`
- SDK `ProtocolVersions` constant: `2024-11-05`, `2025-03-26`, `2025-06-18`,
  `2025-11-25`
- 공식 SDK client와 실제 server가 협상한 protocol: `2025-11-25`
- transport: WebMVC stateless Streamable HTTP, sync, endpoint `/mcp`
- 선택 이유: 한 번의 읽기 전용 request-response만 필요하고 client 요청, sampling,
  elicitation, roots, subscription과 session state가 필요하지 않다.
- runtime SBOM에는 새 component 10개가 추가됐다. MCP SDK POM은 MIT, Spring AI
  starter·WebMVC transport POM은 Apache-2.0이다. PRIZM source-only archive가 dependency
  JAR를 재배포하지 않는 기존 경계는 바뀌지 않아 `NOTICE` 본문은 변경하지 않았다.
- `gradle/verification-metadata.xml`에 Maven Central에서 해석한 새 artifact의 SHA-256을
  추가했고 backend CycloneDX와 `SHA256SUMS`를 재생성했다.

## Tool 계약 확인

- 이름: `search_career_evidence`
- input schema property: 필수 `query` 하나
- output: state와 evidence, document/version/source/evidence-source identity
- 제외 확인: 별도의 `score`, `distance`, `content` 필드
- `evidence` 의미: 기존 Career Evidence V2의 `snippet`. 짧은 청크나 fallback에서는
  선택된 청크 전체와 같을 수 있으며 MCP 전용 축약은 적용하지 않음
- 등록 tool 수: 1

## Authentication·Origin·owner 결과

| 항목 | 실제 결과 | 판정 |
|---|---|---|
| anonymous initialize | HTTP 401 | PASS |
| malformed JWT | HTTP 401 | PASS |
| SYSTEM_ADMIN-only JWT | HTTP 403 | PASS |
| USER JWT | initialize, tools/list, tool call 성공 | PASS |
| DB revalidation | token subject로 repository를 조회하고 enabled/email/role 일치 확인 | PASS |
| untrusted Origin | HTTP 403 | PASS |
| configured Origin `http://localhost:5173` | HTTP 200 | PASS |
| USER A/B | 각각 user ID 7/8로 SearchService 호출, 반대 owner 결과 없음 | PASS |

Spring Security filter chain에서 기존 JWT Resource Server와 CORS allowlist가 `/mcp`에도
동일하게 적용됐고 tool 실행 중 request `SecurityContext`를 `CurrentUserProvider`가
읽었다. starter 자체의 별도 Origin policy를 가정하지 않고 기존 allowlist의 실제
허용·거부 결과를 검증했다. P2에서 원격 client origin 요구가 달라지면 광범위 허용이
아니라 명시적 allowlist만 별도 검토한다.

## State·validation·REST parity

- `EVIDENCE_FOUND`: 근거 1건의 state와 모든 MCP field 매핑 PASS
- `NO_EVIDENCE`: empty results와 state 보존 PASS
- `NO_RELEVANT_RESULTS`: empty results와 state 보존 PASS
- blank query: 기존 SearchService `query must not be blank`가 MCP error result로 전달됨
- 500자 초과 query: 기존 SearchService validation 예외 보존 PASS
- malformed input: 필수 `query` 누락을 SDK schema validator가 MCP error result로 반환
- 같은 USER·query의 실제 REST HTTP와 MCP SDK call에서 state, 결과 수, documentId,
  documentVersionId, chunkId, evidenceChunkId, evidence source와 snippet이 같음

REST/MCP parity test는 adapter 경계를 격리하기 위해 같은 mocked SearchService 결과를
두 실제 HTTP endpoint로 호출했다. 별도 검색 로직이 없음을 검증했고, 아래 PostgreSQL
integration은 실제 SearchService의 owner·ACTIVE SQL을 별도로 재검증했다.

## 자동 검증

| 명령·환경 | 결과 |
|---|---|
| `.\gradlew.bat compileJava --no-daemon` | PASS |
| `.\gradlew.bat dependencyInsight --dependency mcp --configuration runtimeClasspath --no-daemon` | PASS, Spring AI/MCP SDK `2.0.0` |
| `.\gradlew.bat test --tests com.prizm.mcp.* --no-daemon` | 6 tests, failure/error/skip 0 |
| `.\gradlew.bat test --no-daemon` | 485 tests, failure/error 0, conditional skip 15 |
| focused `PgVectorInfrastructureTest.returnsAtMostFiveProfileRankedCareerEvidenceChunksOnlyForTheCurrentActiveOwnerVersion` | PASS |
| `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | 112 tests, failure/error 0, conditional skip 7 |
| `.\gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict` | PASS |
| `node scripts/verify-sbom.mjs` | PASS |
| `node --test scripts/verify-sbom.test.mjs` | 7/7 PASS |
| `node scripts/verify-oss-readiness.mjs` | PASS, verifier 12/12·external links 28/28 |
| `git diff --check` | PASS |

전체 integration은 Docker PostgreSQL·pgvector와 설정된 Ollama를 사용했다. 조건부
OpenSQL test 7건은 기본 실행에서 skip됐으며 이 결과를 P2 OpenSQL/OpenProxy MCP
E2E로 표현하지 않는다.

## 실행 중 교정 이력

- 최초 strict dependency resolve는 새 artifact 15개의 verification metadata가 없어
  차단됐다. Maven Central artifact checksum만 생성한 뒤 strict compile을 통과했다.
- 첫 test compile은 Java 17 collection API와 SDK 2.0 Map schema 차이로 실패했고 test
  code만 교정했다.
- malformed tool input은 예외가 아니라 표준 `CallToolResult(isError=true)`로 반환됐다.
  실제 SDK 의미에 맞게 expectation을 교정한 뒤 focused test를 통과했다.
- 위 실패는 production data, DB schema, OpenSQL/OpenProxy와 외부 시스템을 변경하지
  않았다.

## 알려진 제한

- P2는 실제 연구실 OpenSQL Single·OpenProxy 한 경로를 검증했다. 이 결과를 OpenHA,
  replica/failover, OpenProxy 이중화나 PostgreSQL Testcontainers 결과로 확대하지 않는다.
- stateless SDK client는 initialize 뒤 `notifications/initialized`를 보내고 server는
  stateless handler가 없다는 warning을 기록하지만 tools/list와 tool call은 정상
  완료됐다.

## AUDIT

- blocking finding: 0건
- 전체 변경 파일: 14개
- production 변경: 4개
- test·spec/registry 변경: 7개
- dependency verification·SBOM metadata 변경: 3개
- 등록 `@McpTool`: 1개
- 검색 production source 변경: 0개
- migration 변경: 0개
- frontend 변경: 0개
- Career Keyword Map·PRZ-009 변경: 0개
- credential, uploaded original, DB volume, model/cache와 vendor OpenSQL asset: 0개
- Agent AUDIT은 GitHub review가 아니며 commit, push, PR과 merge는 수행하지 않았다.

P1 요구사항 R1–R7과 stop 조건을 최종 diff에 대조했다. 공식 starter, 단일 tool,
기존 SearchService, JWT USER, owner·ACTIVE, REST/MCP parity, 상태·validation, regression,
license·SBOM Gate가 모두 통과해 P1을 `PASS`로 판정한다. P2 실제 환경 Gate도 아래와
같이 `PASS`다. P3 공개 문서 통합과 문서 검증도 통과해 PRZ-015 전체 상태를
`VERIFIED`로 판정한다.

## P2 Actual OpenSQL/OpenProxy Gate — PASS

### 최종 실제 환경 결과

- 실행일: 2026-08-15
- 최종 판정: `P2 PASS`
- FAST CHECK: `localhost:5432` TCP, `localhost:6432` TCP, Ollama/`bge-m3` 모두 `PASS`
- production 변경: `0`
- migration·frontend·PRZ-009 변경: 각각 `0`
- test-only 변경: `OpenSqlMcpGateTest` 한 파일의 실제 서버 version assertion과 synthetic
  query 세 개를 현재 검증 계약에 맞게 교정
- runtime datasource: `jdbc:postgresql://localhost:6432/opensql`
- Flyway datasource: `jdbc:postgresql://localhost:5432/prizm_integration_test`
- runtime role: `prizm_app`; Flyway role: `prizm_owner`
- OpenSQL SQL server version 출력: PostgreSQL 17.8 호환 문자열
- pgvector: `0.8.1`; embedding: 실제 Ollama `bge-m3`, 1024차원
- Flyway: V1∼V15 applied, current V15, pending 0
- Official Java MCP SDK client: resolved SDK `2.0.0`; client가 보낸 implementation 정보는
  `Java SDK MCP Client` version `0.15.0`
- transport/endpoint: stateless Streamable HTTP `POST /mcp`
- negotiated protocol: `2025-11-25`
- tools/list: `search_career_evidence` 한 개, input property `query` 한 개

### MCP·보안·검색 결과

- anonymous initialize: HTTP 401
- invalid JWT initialize: HTTP 401
- 실제 signup/login USER A/B JWT: initialize, tools/list와 tool call `PASS`
- USER A query `Docker Compose Nginx Spring Boot`: `EVIDENCE_FOUND`; V2의 Docker
  Compose·Nginx 근거 반환
- USER B query `TourAPI 데이터 처리`: `EVIDENCE_FOUND`; TourAPI 근거 반환
- REST/MCP parity: 같은 USER A/query의 state, 결과 수, document/version/chunk/evidence
  identity, source metadata와 snippet 일치
- USER A → USER B evidence: 0; USER B → USER A evidence: 0
- ACTIVE isolation: document의 현재 V2만 반환하고 이전 V1 version ID는 결과에서 제외
- `Kubernetes Helm 클러스터`: `NO_RELEVANT_RESULTS`
- `Kafka를 출시한 이력이 있나요?`: `NO_EVIDENCE`
- blank query: MCP error result와 `query must not be blank`
- `notifications/initialized` handler warning: client 두 개에서 2회 재현. tools/list와 tool
  call 결과에는 영향 없음

### Synthetic data와 cleanup

- 최신 저장 실행(2026-08-15 04:19 KST) synthetic USER ID: A `405`, B `406`
- synthetic documents: 2; versions: 3
- USER A V1→V2와 USER B V1을 실제 signup/login, upload, ChangeLog dispatch, indexing,
  Ollama embedding과 ACTIVE 전환으로 생성
- cleanup: `PRZ015_P2_CLEANUP=PASS existing-row-counts-preserved=true`
- synthetic user/document/version/chunk/job/ChangeLog와 source file은 추적 ID·storage key로만
  삭제했고 임시 storage root 잔존 수 0
- 기존 table row count, pending ChangeLog와 claimable job baseline 보존
- 기존 사용자 데이터 mutation: 0

### P2 실행 중 test-only 교정

- 첫 실제 실행은 OpenSQL의 `SELECT version()`이 vendor명이 아니라
  `PostgreSQL 17.8 ...`을 반환해 `contains("OpenSQL")` test-only assertion에서 중단됐다.
  synthetic 생성 전이었고 환경·production·DB mutation은 없었다.
- 다음 실행은 `Docker Compose 배포 경험`이 현재 completed-release fail-closed 문법에서
  `NO_EVIDENCE`여서 중단됐다. 검색 production 계약을 바꾸지 않고 synthetic positive
  query를 일반 exact-term 형태로 교정했다. `finally` cleanup은 완료됐다.
- 최종 실행은 test 1, failure/error/skip 0으로 통과했고 secret process 환경변수도
  실행 뒤 제거했다.

### 회귀

- P2 focused MCP·SearchService·Security: 44 tests, failure/error/skip 0
- 전체 backend unit: 485 tests, 15 conditional skip, failure/error 0
- 전체 PostgreSQL integration: 113 tests, 8 conditional skip, failure/error 0
  - 이 실행의 P2 opt-in test 1건은 환경변수 미설정으로 skip됐고, 실제 P2 Gate는 별도
    명령에서 test 1, failure/error/skip 0으로 통과했다.
- Ollama version: `0.32.9`
- commit, push, PR, merge: `NOT_RUN`

### 이전 환경 차단 기록

- 이전 상태: `NOT_VERIFIED — LAB_ENVIRONMENT_BLOCKED`
- 당시 opt-in test는 application context 생성 전 Flyway direct `:5432` timeout으로
  중단돼 실제 test method와 synthetic 생성이 `NOT_RUN`이었다.
- 당시 차단 원인은 VM↔host `:5432`/`:6432` 불안정과 VM soft-lockup이었다.
- 당시 작업에서 만든 `prz015-direct`, `prz015-proxy` NAT 규칙만 제거했고 기존 연구실
  설정은 재구성하지 않았다.

## P3 OSS 문서 통합 — PASS

- 실행일: 2026-08-15
- 최종 상태: `VERIFIED`
- 기존 README, Quickstart, 문서 색인, 현재 상태, 아키텍처, 로드맵과 PRZ-015
  Spec/Plan/Tasks/Evidence/Registry를 갱신했고 새 MCP 문서는 만들지 않았다.
- 요청 주소 `POST /mcp`, protocol `2025-11-25`, `search_career_evidence`,
  `{"query":"..."}`, Bearer JWT·`ROLE_USER` 요구사항을 공개 문서에 기록했다. 기존
  Career Evidence Search를 재사용하며 사용자별 데이터와 현재 `ACTIVE` 버전만
  반환한다는 점도 함께 설명했다.
- 실제 단일 서버 OpenSQL/OpenProxy P2 전체 흐름(E2E)과 REST/MCP 결과 일치가
  `PASS`했음을 기록했다. 이 결과를 OpenHA 검증으로 확대하지 않았다.
- 첫 OSS 검사에서 기존 연구실 Runbook의 PowerShell static method 두 줄을 Markdown
  reference link로 오인했다. 같은 명령을 괄호로 감싸 의미를 유지한 뒤 재검사했다.
- Markdown: 82 files, 518 local links `PASS`
- external links: 28 OK, indeterminate/permanent failure 0
- OSS readiness: `PASS`
- `git diff --check`: `PASS`
- P3 production/test/DB·VM·OpenProxy 변경: 0
- commit, push, PR, merge: `NOT_RUN`

## 독립 감사 차단 항목 교정 — PASS

- 실행일: 2026-08-15
- `AGENTS.md`의 MCP 미구현 표기와 OpenSQL 검증 범위를 실제 PRZ-015 P2 결과에 맞게
  교정했다. 검증 범위는 Single-only OpenProxy/OpenSQL 경계를 넘지 않는다.
- `13f6dd9`를 P0 기준 source로 명확히 구분하고, 검증 대상은 현재 미커밋 worktree,
  통합 source commit은 `—`로 기록했다.
- MCP 응답 계약을 값 전체 제외가 아닌 별도 `content`·`score`·`distance` 필드 제외로
  명확히 했다. `evidence`는 REST/MCP 결과 일치를 위해 기존 V2 `snippet`을 보존한다.
- Production source/test 변경: 0
- commit, push, PR, merge: `NOT_RUN`

## INTEGRATE — 진행 중

- 사용자 승인: 2026-08-15 commit, push, PR 생성과 merge commit 병합 승인
- 검증 source commit: `97c01cb076acf91e8433894e71a5d3c156b994f2`
- branch push: `origin/PRZ-015-mcp-career-evidence-search` `PASS`
- GitHub PR: [#46](https://github.com/jaemin-devlog/PRIZM/pull/46)
- 독립 재감사: blocking finding 0
- `REVIEW_NOT_AVAILABLE_SOLO`: 제3자 reviewer가 없는 solo-maintainer 작업으로 독립
  감사와 사용자 승인을 병합 Gate 근거로 사용한다. 이는 GitHub 제3자 review 증거가
  아니다.
- merge: 필수 CI 확인 전 `NOT_RUN`
