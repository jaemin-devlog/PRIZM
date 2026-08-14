# PRZ-015 — 읽기 전용 MCP Career Evidence 검색 Tasks

> **현재 상태:** `VERIFIED`
>
> 계약은 [Spec](spec.md), 실행 계획은 [Plan](plan.md), 실제 결과는
> [Evidence](evidence.md)를 따른다.

## P0. ORIENT / SPEC

- [x] 원격 최신 `main`, clean worktree와 PRZ-014 통합을 확인했다.
- [x] Spring Boot/Spring AI/WebMVC, JWT와 CurrentUserProvider를 확인했다.
- [x] Career Evidence V2와 owner/ACTIVE SQL, OpenSQL/OpenProxy 경계를 확인했다.
- [x] 공식 stateless WebMVC starter, annotation API와 기본 `/mcp`를 확인했다.
- [x] PRZ-015 Spec, Plan, Tasks, Evidence와 Registry를 등록했다.

## P1. Minimal read-only tool

- [x] 공식 MCP WebMVC starter와 stateless 최소 설정을 추가했다.
- [x] `search_career_evidence` tool 하나와 MCP 전용 response를 구현했다.
- [x] `/mcp`를 유효한 `ROLE_USER` JWT로 제한했다.
- [x] 검색·migration·frontend·PRZ-009를 변경하지 않았다.

## P1. VERIFY / AUDIT

- [x] tool mapping, validation과 state unit test를 통과했다.
- [x] authenticated tools/list와 실제 tool call을 검증했다.
- [x] anonymous, invalid JWT, USER와 SYSTEM_ADMIN-only 결과를 검증했다.
- [x] USER A/B owner, ACTIVE isolation과 REST/MCP parity를 검증했다.
- [x] resolved SDK/protocol, dependency license와 SBOM을 기록했다.
- [x] backend unit·PostgreSQL integration 회귀를 실행했다.
- [x] 최종 범위 audit와 `git diff --check`를 통과했다.

## P2. Actual OpenSQL/OpenProxy Gate

> `PASS` — 실제 OpenSQL direct `:5432`, OpenProxy runtime `:6432/opensql`, Ollama
> `bge-m3`와 Official Java MCP Client E2E를 통과했다. 이전 환경 차단은 Evidence에
> historical record로 보존한다.

- [x] 실제 OpenSQL direct `:5432` Flyway와 OpenProxy `:6432` runtime을 분리 확인한다.
- [x] 실제 signup/login USER A/B JWT와 합성 TXT V1→V2 ACTIVE fixture를 준비한다.
- [x] 공식 Java MCP client initialize, tools/list와 actual tool call을 통과한다.
- [x] anonymous·invalid JWT, REST parity, owner·ACTIVE 격리와 상태·blank query를 검증한다.
- [x] OpenSQL·pgvector·V1∼V15/pending 0, Ollama `bge-m3` 1024차원을 기록한다.
- [x] 추적한 합성 ID·source file만 정리하고 기존 row mutation 0을 확인한다.
- [x] focused·unit·integration 회귀와 최종 P2 audit를 통과한다.

## P3. OSS 문서 통합

- [x] 기존 README와 Quickstart에 MCP endpoint, protocol, tool/input와 JWT 요구를 기록했다.
- [x] 기존 검색 재사용, owner·ACTIVE isolation과 REST/MCP parity를 기록했다.
- [x] 실제 OpenSQL/OpenProxy P2 E2E PASS를 Single-only 범위로 기록했다.
- [x] 현재 상태·아키텍처·로드맵·Spec Registry의 미구현 표현을 현재 근거와 맞췄다.
- [x] 문서 링크, OSS readiness와 `git diff --check`를 실행했다.
