# PRZ-021 — Evidence

> **상태:** `VERIFIED`
> **기준선:** `fb8befe3fa876882f1c8b918097d3e7d4774d53d`
> **검증 대상:** `PRZ-021-first-user-experience` 구현 commit `a0c2977`
> **검증일:** 2026-08-29
> **통합 상태:** 원격 branch push 완료, [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65) `OPEN`, merge `NOT_RUN`

## 원인 확인

| 문제 | 확인한 원인 | 처리 |
|---|---|---|
| TXT 검색 결과에 원문 이동 버튼이 없음 | 일반 검색 화면이 유효한 PDF viewer target이 있을 때만 버튼을 표시함 | 기존 문서 상세 route로 연결 |
| Quickstart와 실제 UI가 다름 | 문서 유형 폴더와 화면별 상태 문구의 관계가 빠져 있음 | 실제 화면 기준으로 최소 교정 |
| clean-clone owner 설명이 모순됨 | 별도 browser USER에게 API verifier USER의 문서를 확인하도록 안내함 | 자동 API 검증과 수동 브라우저 검증을 USER별로 분리 |
| `notifications/initialized` warning | MCP Java SDK `2.0.0` stateless server에 해당 알림 handler가 없음 | upstream 한계로 기록하고 Production 코드는 유지 |
| 범용 MCP client 재현성 미확인 | 기존 증거는 raw protocol과 공식 Java SDK focused test에 한정됨 | 공식 MCP Inspector CLI로 실제 endpoint 검증 |

작업 전 local `main`, `origin/main`, 원격 `main`은 모두 기준선과 일치했고 tracked
worktree는 clean이었다. Registry와 Git ref에는 PRZ-000∼020만 있어 다음 ID로
`PRZ-021`을 사용했다.

## 구현 결과

### TXT 원문 이동

- 일반 검색 결과의 원문 이동 버튼을 `EvidenceSourceAction`으로 분리했다.
- `TEXT_CHUNK` 결과는 검색 응답의 `documentId`와 `documentVersionId`를 기존
  `/career-vault/documents` route에 전달한다.
- 요청한 version은 owner-scoped 문서 상세 응답의 `versions`에 실제로 있을 때만
  선택한다. 없거나 유효하지 않으면 기존 active/first fallback을 사용한다.
- version 행 선택과 브라우저 이동 기록도 `versionId` query와 동기화했다.
- `PAGE` 결과는 기존 PDF callback을 그대로 사용한다. 유효한 page target이 없으면
  이전과 마찬가지로 버튼을 표시하지 않는다.

### Quickstart와 clean-clone owner 흐름

- 업로드한 문서는 선택한 문서 유형 폴더에서 확인하도록 안내했다.
- 목록의 `검색 준비 완료`, 상세의 `검색에 사용 중`, version의 `현재 · ACTIVE`가
  같은 처리 완료 상태를 화면별로 표현한다는 설명을 추가했다.
- 고유 `COMPOSE_PROJECT_NAME`은 기존 Docker 환경과 데이터를 분리할 때만 선택적으로
  사용하고, 같은 volume을 다시 올릴 때는 같은 값을 유지하도록 안내했다.
- Docker/Ollama 명령을 찾지 못할 때의 설치·실행·PATH 확인, Docker Engine 실행과
  health `UP` 대기 기준을 추가했다.
- 자동 verifier USER의 문서는 별도 browser USER에게 보이지 않아야 한다. 브라우저
  검증은 빈 보관함과 교차 marker 0건을 먼저 확인한 뒤, 같은 browser USER가 합성
  TXT/PDF를 직접 업로드하는 절차로 고쳤다. helper와 owner 정책은 변경하지 않았다.

### MCP warning과 실제 client

- [MCP lifecycle `2025-11-25`](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle)는
  initialize 응답 뒤 client가 `notifications/initialized`를 보내도록 정한다.
- [MCP Java SDK `2.0.0` stateless server](https://github.com/modelcontextprotocol/java-sdk/blob/v2.0.0/mcp-core/src/main/java/io/modelcontextprotocol/server/McpStatelessAsyncServer.java#L122-L125)는
  notification handler map을 비워 둔다. [기본 server handler](https://github.com/modelcontextprotocol/java-sdk/blob/v2.0.0/mcp-core/src/main/java/io/modelcontextprotocol/server/DefaultMcpStatelessServerHandler.java#L54-L60)는
  등록되지 않은 알림을 warning과 함께 소비한다. 이후 요청이 계속 처리되는지는 실제
  Inspector 호출로 확인했다.
- [MCP Java SDK `2.0.1`](https://github.com/modelcontextprotocol/java-sdk/blob/v2.0.1/mcp-core/src/main/java/io/modelcontextprotocol/server/McpStatelessAsyncServer.java#L124-L140)에는
  initialized 알림을 받는 no-op handler가 추가돼 있다.
- 이 warning은 PRIZM 설정 누락이 아니므로 Production MCP code, dependency, logger와
  transport는 변경하지 않았다.

## VERIFY

### 자동 검사

| 검사 | 결과 |
|---|---|
| focused frontend action/route test | `PASS` — 8/8 |
| `npm.cmd --prefix frontend run test:unit` | `PASS` — 89개, 0 fail/skip |
| `npm.cmd --prefix frontend run typecheck` | `PASS` |
| `npm.cmd --prefix frontend run lint` | `PASS` |
| `npm.cmd --prefix frontend run build` | `PASS` |
| `node --test scripts/clean-clone-demo.test.mjs` | `PASS` — 26개 중 25 pass, Windows POSIX mode 1 skip, 0 fail |
| `McpCareerEvidenceProtocolTest` 실제 rerun | `PASS` — 3개, 0 fail/skip |
| `node scripts/verify-oss-readiness.mjs` | `PASS` |
| Markdown·link 검사 | `PASS` — 161개 Markdown, local link 692개, external link 80개 OK |
| `git diff --check` | `PASS` |

backend Production source를 변경하지 않았으므로 그 밖의 backend test suite는 이번 필수
범위에서 제외했다.

### 실제 MCP client

[공식 MCP Inspector CLI](https://github.com/modelcontextprotocol/inspector/blob/main/clients/cli/README.md)
`2.4.0`과 Node `24.19.0`으로 fresh endpoint를 호출했다.

| 단계 | 결과 |
|---|---|
| initialize | `PASS` — protocol `2025-11-25` |
| `tools/list` | `PASS` — `search_career_evidence` 노출 확인 |
| `search_career_evidence` | `PASS` — 현재 USER 소유 TXT marker 반환 |

세 단계는 각각 독립된 one-shot 연결에서 실행했다. 각 연결 뒤 같은
`notifications/initialized` warning이 남았지만 목록 조회와 도구 호출은 성공했다.

판정: `MCP_REAL_CLIENT_VERIFIED`

Bearer JWT는 argv에 넣지 않고 ignored `local/` 임시 config에만 저장했다. 실행 직후
config를 삭제했으며 token과 credential은 tracked file이나 Evidence에 남기지 않았다.

### 실제 Fresh Compose / browser E2E

| 항목 | 검증 환경 |
|---|---|
| Compose project | `prizm-clean-clone-prz021-20260829` |
| host port | PostgreSQL `15434`, backend `18082`, frontend `15175` |
| Docker | CLI `29.6.2`, Compose `v5.3.1`, Engine `29.6.2` |
| Ollama | `0.33.1` |
| embedding model | `bge-m3:latest`, manifest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, 1024 dimensions |

1. 새 PostgreSQL/runtime volume 두 개를 만들고 `docker compose config --quiet`와
   backend health `status=UP`을 확인했다.
2. 자동 verifier는 별도 USER로 signup → login → TXT/PDF upload → `ACTIVE` → search를
   통과했고, 로그아웃 요청은 `401`이었다.
3. 별도 browser USER는 로그인 직후 문서가 0건이었고 verifier의 TXT marker 검색도
   0건이었다. 두 USER 사이의 owner isolation이 유지됐다.
4. browser USER가 같은 합성 TXT/PDF를 직접 업로드했다. 프로젝트 보고서와 포트폴리오
   폴더에 자기 소유 문서가 각각 1건씩 표시됐다.
5. TXT 목록의 `검색 준비 완료`, 상세의 `검색에 사용 중`, version의 `현재 · ACTIVE`를
   확인했다. 검색 결과의 `문서에서 보기`는
   `?documentId=3&versionId=3`으로 이동했고 v1과 TXT 원문 viewer를 열었다.
6. PDF 결과는 기존 blob viewer의 `#page=1&zoom=page-width`를 사용했다. 자동 브라우저는
   PDF 화면을 렌더링하지 않으므로 iframe source, page fragment와 `PAGE` 결과로 확인했다.
7. 브라우저 console `ERROR`는 재시작 전후 모두 0건이었다.
8. `down` 뒤 두 volume이 남았고 같은 project로 다시 `up`한 뒤 session, 폴더 2개와
   TXT 검색 결과가 유지됐다.

마지막 `down`은 container와 network만 제거했다. 두 volume과 재현용 ignored env는
`local/`에 보존했고, 작업 전 root `.env`는 시작 전 SHA-256과 같게 복원했다.

## 남은 한계

- MCP Java SDK `2.0.0`의 `notifications/initialized` warning은 남아 있다. 실제
  initialize·목록 조회·도구 호출에는 영향을 주지 않지만, warning을 없애려면 별도
  dependency upgrade 검증이 필요하다.
- 자동 브라우저에서는 PDF 본문 화면을 볼 수 없어 iframe URL의 page fragment까지
  검증했다. PDF viewer 자체의 기존 동작은 회귀하지 않았다.
- frontend image build의 `npm ci`는 기존 high severity audit 항목 1건을 보고했다.
  dependency 변경은 이번 UX 수정 범위가 아니므로 다루지 않았다.

## AUDIT와 판정

- backend Java, 검색 알고리즘, embedding, threshold, 인증, owner 정책, SQL/migration과
  DB schema 변경: 0
- MCP Production code, dependency, logger와 transport 변경: 0
- 임시 MCP credential config, tracked secret과 unrelated change: 0
- 구현 commit `a0c2977`, 원격 branch push와 [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65) 생성: 완료
- merge: `NOT_RUN`
- 독립 구현 재감사: blocking finding 0

`FIRST_USER_EXPERIENCE_READY`
