# PRZ-015 — 읽기 전용 MCP Career Evidence 검색

> **상태:** `VERIFIED`
> **유형:** Feature / Security boundary
> **선행 문서:** [PRZ-012](../PRZ-012-search-evidence-presentation/spec.md)
> **P0 기준 소스:** `13f6dd970a24a8966e36fab6b12aa4f894ebed4e`
> **검증 source commit:** `97c01cb076acf91e8433894e71a5d3c156b994f2`
> **GitHub 통합:** [PR #46](https://github.com/jaemin-devlog/PRIZM/pull/46), merge commit
> `23166e785899c046bc69974e1d2d27e163064d48`
> **최종 확인:** 2026-08-15

## 목적

인증된 `USER`가 표준 MCP client에서 자신의 ACTIVE 문서에 대한 기존 Career
Evidence V2 검색을 읽기 전용 tool 하나로 호출할 수 있게 한다. MCP는 검색 엔진을
복제하지 않고 `SearchService.searchCareerEvidenceV2(ownerUserId, query)`를 그대로
재사용하는 외부 인터페이스다.

## 동작 흐름

```text
MCP client + Authorization: Bearer <JWT>
→ PRIZM POST /mcp
→ 기존 JWT 검증과 DB 사용자 재검증
→ search_career_evidence({ query })
→ CurrentUserProvider.userId()
→ SearchService.searchCareerEvidenceV2(userId, query)
→ score·distance를 제외한 MCP 전용 응답
```

## 범위

### 포함

- Spring AI 2.0.0 BOM의 공식 WebMVC MCP server starter를 사용한다.
- stateless Streamable HTTP의 기본 `/mcp` endpoint를 사용한다.
- `search_career_evidence` tool 하나와 `query` 입력 하나만 노출한다.
- 현재 JWT Resource Server, DB 사용자 상태·email·role 재검증과 `ROLE_USER` 경계를
  MCP endpoint에도 적용한다.
- 현재 인증 주체는 `CurrentUserProvider`에서 얻고 MCP 입력으로 owner/user ID를 받지
  않는다.
- V2 검색 state와 직접 evidence snippet·source metadata를 MCP 전용 record에 매핑한다.
- unit, protocol, 인증, owner·ACTIVE, REST parity와 회귀 검증을 수행한다.
- 실제 OpenSQL direct Flyway, OpenProxy runtime, Ollama와 Official Java MCP Client로
  P2 E2E를 수행한다.
- 기존 공개 문서에 최소 MCP 실행 계약과 검증 경계를 반영한다.

### 제외

- MCP resources, prompts, sampling, elicitation, subscription과 write tool
- OAuth authorization server, 별도 Node/Python server와 deprecated SSE 신규 구성
- 검색 SQL·profile·threshold·ranking·embedding·evidence localization 변경
- Flyway migration, OpenSQL/OpenProxy, JWT 발급/login API와 frontend 변경
- Career Keyword Map과 PRZ-009 변경

## Tool 계약

- 이름: `search_career_evidence`
- 입력: `query` 문자열 하나, 필수, 공백 불가, 최대 500자
- 출력:
  - `state`
  - `results[]`: `evidence`, `documentTitle`, `versionNo`, `sourceType`,
    `sourceIndex`, `sourceLabel`, `evidenceSourceType`, `evidenceSourceIndex`,
    `evidenceSourceLabel`, `documentId`, `documentVersionId`, `chunkId`,
    `evidenceChunkId`
- 별도의 `content`, `distance`, `score` 필드는 출력하지 않는다.
- `evidence`는 기존 Career Evidence V2의 `snippet`을 그대로 사용한다. 짧은 청크나
  fallback에서는 이 값이 선택된 청크 전체와 같을 수 있으며, MCP 전용 축약 규칙을
  추가하지 않는다.
- 기존 state 의미를 그대로 보존하며 현재 구현의
  `EVIDENCE_FOUND`, `NO_SEARCHABLE_DOCUMENTS`, `NO_RELEVANT_RESULTS`,
  `NO_EVIDENCE`를 새 의미로 바꾸지 않는다.

## 보안·ownership·ACTIVE 계약

- `/mcp`는 anonymous, invalid JWT와 `SYSTEM_ADMIN` 전용 계정을 거부한다.
- 요청마다 현재 JWT 서명·issuer·expiry와 DB의 enabled 상태·email·role을 재검증한다.
- tool은 `CurrentUserProvider.userId()`만 `SearchService` owner 인자로 전달한다.
- 검색 결과는 repository의 document/version/chunk owner 3중 조건과
  `documents.active_version_id = version.id`, version `ACTIVE` 조건을 그대로 따른다.
- MCP 입력·transport metadata로 임의 사용자 impersonation을 허용하지 않는다.
- 광범위 CORS/Origin 허용을 추가하지 않는다. starter transport의 Origin 처리 여부와
  남은 원격 노출 위험은 evidence에 기록한다.

## 요구사항 및 완료 조건

### `PRZ-015-R1` — 공식 stateless MCP server

Spring AI BOM 2.0.0의 `spring-ai-starter-mcp-server-webmvc`와 공식 annotation/SDK를
사용하고 `/mcp`에서 정확히 한 tool만 등록한다. resolved Java SDK와 지원 protocol
version을 실제 dependency/source로 기록한다.

### `PRZ-015-R2` — 최소 tool 계약

입력은 `query` 하나뿐이며 MCP 전용 응답은 V2 state와 근거·출처·identity만 포함하고
별도의 `content`, `score`, `distance` 필드를 제외한다. `evidence`에는 기존 V2
`snippet`의 의미를 그대로 보존한다.

### `PRZ-015-R3` — JWT USER 경계

anonymous와 invalid JWT, `SYSTEM_ADMIN` 전용 JWT는 거부되고 유효한 `USER` JWT만
tools/list와 tool call을 수행할 수 있다. tool 실행 중 `CurrentUserProvider`가 같은
request의 인증 주체를 읽는다.

### `PRZ-015-R4` — owner·ACTIVE 격리

USER A/B 호출은 각자의 user ID만 기존 SearchService에 전달하고 다른 owner 또는
inactive/superseded/failed version의 근거를 반환하지 않는다.

### `PRZ-015-R5` — REST/MCP parity

같은 user와 query의 REST V2 응답과 MCP 응답은 state, 결과 수, document/version/
chunk/evidence identity, evidence source와 snippet 의미가 같다.

### `PRZ-015-R6` — validation과 상태 보존

blank·whitespace-only·500자 초과 query와 malformed tool input을 거부하고
`EVIDENCE_FOUND`, `NO_EVIDENCE`, `NO_RELEVANT_RESULTS`를 그대로 매핑한다.

### `PRZ-015-R7` — 회귀와 범위 보존

focused test와 `./gradlew test`가 통과하고 검색 production source·migration·frontend·
PRZ-009 변경은 0건이며 `git diff --check`가 통과한다.

## Dependency·license·SBOM 영향

- Spring Boot 4.1.0과 Spring AI BOM 2.0.0은 올리지 않는다.
- 공식 MCP WebMVC server starter의 runtime transitive dependency와 license를 실제
  resolved graph 및 artifact metadata에서 확인한다.
- runtime dependency inventory가 바뀌므로 backend CycloneDX SBOM과 checksum은 기존
  생성·검증 정책에 맞춰 갱신한다. source-only 배포 경계와 NOTICE 요구사항 변화는
  별도로 판정한다.
- 새 migration은 없다.

## SPEC Gate

- tool, 입력·출력, JWT·owner·ACTIVE와 상태·parity 계약이 실행 결과로 판정 가능하다.
- P2/P3와 검색·DB·frontend·PRZ-009 비범위가 분리됐다.
- dependency·license·SBOM과 중단 조건이 명시됐다.

판정: `PASS`

## 최종 완료 판정

P0/P1 구현·검증, 실제 OpenSQL Single/OpenProxy P2 E2E와 P3 OSS 문서 통합이 모두
통과했다. 상세 실행 결과와 Single-only 경계는 [Evidence](evidence.md)를 따른다.

최종 상태: `VERIFIED`
