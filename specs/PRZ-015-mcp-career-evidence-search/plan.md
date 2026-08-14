# PRZ-015 — 읽기 전용 MCP Career Evidence 검색 Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** `origin/main` `13f6dd970a24a8966e36fab6b12aa4f894ebed4e`
>
> P0/P1과 P2 실제 OpenSQL/OpenProxy Gate를 통과했다. 후속 사용자 승인으로 P3
> OSS 문서 통합까지 완료했으며 commit, push, PR과 merge는 수행하지 않는다.

## P0. ORIENT / SPEC

- 최신 `main`, Registry, PRZ-014 통합과 clean worktree를 확인한다.
- Spring Boot 4.1.0, Spring AI 2.0.0 BOM, WebMVC와 기존 JWT·CurrentUserProvider·
  Career Evidence V2·owner/ACTIVE SQL·OpenProxy `:6432` 구성을 확인한다.
- 공식 Spring AI 2.0.0 문서와 resolved dependency로 WebMVC stateless transport,
  `/mcp`, annotation API, SDK와 protocol version을 확정한다.
- PRZ-015 Spec, Plan, Tasks, Evidence와 Registry를 등록한다.
- 중단 조건: starter 비호환, request SecurityContext 소실, 대규모 security redesign,
  SearchService 계약·migration 변경 또는 별도 server가 필요하면 P1을 시작하지 않는다.

## P1. 최소 MCP tool 구현

- `build.gradle`에 BOM 관리 공식 WebMVC MCP server starter 하나를 추가한다.
- `application.yml`에 sync stateless server, tool capability와 최소 endpoint 설정만 둔다.
- `@McpTool` component가 `CurrentUserProvider.userId()`와 기존 SearchService를 호출한다.
- MCP 전용 response record에서 V2 결과를 별도의 `score`, `distance`, `content` 필드
  없이 매핑하고 기존 `snippet`을 `evidence`로 보존한다.
- `SecurityConfiguration`은 `/mcp`를 `ROLE_USER`로 제한하고 나머지 deny-all 정책을
  유지한다.
- Rollback: MCP dependency/config/component/security matcher만 제거하면 기존 REST와
  검색 동작으로 돌아간다.

## P1. Focused VERIFY

- unit: user ID 전달, V2 state 3종과 field mapping, blank/overlong validation.
- protocol: authenticated initialize/tools/list/tool call/response parsing과 malformed
  input을 공식 SDK 또는 conformance-compatible client 경로로 확인한다.
- security: anonymous, invalid JWT, USER JWT와 SYSTEM_ADMIN-only JWT를 확인한다.
- isolation/parity: USER A/B owner 전달, ACTIVE 검색 회귀와 같은 SearchService 결과의
  REST/MCP identity·evidence parity를 확인한다.
- dependency: `dependencyInsight`, runtime graph, artifact license와 protocol constant를
  확인하고 backend SBOM을 재생성·검증한다.
- regression: `./gradlew test`, 적용 가능한 focused integration,
  `node scripts/verify-sbom.mjs`, `git diff --check`를 실행한다.
- 중단 조건: 필수 test 실패, 실제 SecurityContext 미전파 또는 검색 계약 변경이
  드러나면 `PASS`로 판정하지 않는다.

## P1. AUDIT

- spec/plan 대비 최종 diff, tool 개수와 schema를 다시 센다.
- JWT·DB revalidation·ROLE_USER·owner input 부재·ACTIVE 경계를 감사한다.
- production search source, migration, frontend, Career Keyword Map과 PRZ-009 diff가
  0인지 확인한다.
- dependency license·SBOM과 Origin/CORS 제한을 기록한다.
- blocking finding이 0이고 모든 P1 필수 조건이 증명된 경우에만 P1 `PASS`로 판정한다.

## P2. Actual OpenSQL/OpenProxy MCP Gate

- 운영 코드는 변경하지 않고 opt-in integration test만 추가한다.
- Flyway는 실제 OpenSQL direct `:5432`의 `prizm_owner`, runtime은 실제 OpenProxy
  `:6432`의 `prizm_app`으로 분리한다.
- 실제 signup/login JWT와 HTTP TXT upload, ChangeLog dispatch, Ollama `bge-m3`
  indexing, V1→V2 ACTIVE 전환을 사용한다.
- 공식 Java MCP client로 initialize, protocol negotiation, tools/list, tool call과
  structured result를 확인한다.
- anonymous·invalid JWT, USER A/B owner 격리, ACTIVE 격리, REST/MCP parity,
  `EVIDENCE_FOUND`·`NO_EVIDENCE`·`NO_RELEVANT_RESULTS`, blank query를 확인한다.
- OpenSQL/server·pgvector·Flyway V1∼V15/pending 0과 runtime/Flyway role 분리를
  실제 연결에서 확인한다.
- 합성 row와 source file은 추적한 ID·경로만 정리하고 기존 row count가 보존되는지
  확인한다.
- Gate 뒤 focused MCP·Search·Security, 전체 unit와 가능한 전체 integration을
  PostgreSQL 결과와 분리해 실행한다.
- host-only 네트워크가 unavailable이면 기존 VM NAT의 동일 host port를 쓰는 임시
  전달 규칙만 허용하며, Gate 뒤 정확한 규칙 이름으로 제거하고 제한사항을 기록한다.
- 중단 조건: 다른 owner/inactive evidence 반환, REST/MCP 불일치, runtime의 OpenProxy
  우회, schema/migration 필요, 운영 코드 3개 이상 수정 또는 실제 protocol call 실패.

## 검증 명령

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
.\gradlew.bat dependencyInsight --dependency mcp --configuration runtimeClasspath --no-daemon
.\gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict
node scripts/verify-sbom.mjs
git diff --check
```

## P3. OSS 문서 통합

- README, Quickstart와 현재 상태·아키텍처·로드맵의 기존 문서만 최소 수정한다.
- endpoint, protocol, tool/input, JWT `ROLE_USER`, 기존 검색 재사용과 owner·ACTIVE
  isolation을 공개 사용 계약으로 기록한다.
- 실제 OpenSQL/OpenProxy P2 E2E와 REST/MCP parity PASS를 Single-only 근거로만
  기록하고 OpenHA를 주장하지 않는다.
- 문서 링크, OSS readiness와 `git diff --check`만 실행한다.

전체 integration 환경이 unavailable이면 결과를 `NOT_RUN`으로 기록하며 PostgreSQL
결과를 P2 OpenSQL/OpenProxy 결과로 바꾸어 표현하지 않는다.

## PLAN Gate

- PRZ-015-R1–R7이 구현·test·audit 단계에 연결됐다.
- rollback, security·ownership·migration·dependency·license 영향과 중단 조건이 있다.
- P2/P3 및 Git 통합 금지가 보존됐다.

판정: `PASS`
