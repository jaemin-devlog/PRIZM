# PRZ-021 — Fresh Clone 첫 사용자 경험 정합화 Plan

> **문서 상태:** `APPROVED`
> **계획 기준선:** `fb8befe3fa876882f1c8b918097d3e7d4774d53d`
> **branch:** `PRZ-021-first-user-experience`

## P1. TXT source action과 version deep link

상태: `COMPLETED`

- 일반 Career Evidence 결과 action을 작은 testable component로 분리한다.
- `PAGE`는 기존 PDF viewer target과 callback을 그대로 사용한다.
- `TEXT_CHUNK`는 결과의 document/version ID를 기존 Document Detail route callback에 전달한다.
- 상세 URL에 선택적 `versionId`를 넣고 owner-scoped detail response에 포함된 version만
  preview로 선택한다. version이 없거나 유효하지 않으면 기존 active/first fallback을 유지한다.
- 상세를 닫을 때 `documentId`와 `versionId`만 제거하고 유형 folder query는 보존한다.
- component와 route parser/round-trip test를 추가한다.

예상 파일:

- `frontend/src/App.tsx`
- `frontend/src/evidenceSourceAction.ts`
- `frontend/src/documentFolderPresentation.ts`
- `frontend/test/evidence-source-action.test.ts`
- `frontend/test/document-folder-presentation.test.ts`
- `frontend/package.json`

## P2. Quickstart와 clean-clone owner 흐름

상태: `COMPLETED`

- 일반 Quickstart의 prerequisite troubleshooting, 선택적 Compose project 격리, health
  `UP`, 유형 folder와 상태 관계를 최소 문구로 교정한다.
- 유지관리자 자동 API USER와 수동 browser USER의 검증 범위를 분리한다.
- browser USER는 빈 owner 범위와 helper marker 0건을 확인한 뒤 자신의 계정으로 같은
  합성 fixture를 UI 업로드한다.
- helper source와 security behavior는 이미 올바르므로 수정하지 않고 script test로
  회귀만 확인한다.
- 현재 제품 문서에는 일반 TXT 결과의 상세 이동을 반영한다. 과거 evidence는 유지한다.

예상 파일:

- `docs/quickstart.md`
- `docs/project-status.md`
- `specs/README.md`
- `specs/PRZ-021-first-user-experience/*`

## P3. MCP 원인 기록과 실제 client 검증

상태: `COMPLETED`

- MCP 2025-11-25 lifecycle, Java SDK 2.0.0/2.0.1 source와 기존 PRZ-015 evidence를 대조한다.
- PRIZM Production code, dependency와 logger 설정은 수정하지 않는다.
- focused `McpCareerEvidenceProtocolTest`로 공식 Java SDK client baseline을 재검증한다.
- fresh Compose USER1 JWT를 메모리에서만 사용해 공식 MCP Inspector CLI의 initialize,
  tools/list와 tool call을 실행한다.
- client/version/transport와 owner/ACTIVE 결과를 기록하고, 실행 불가 시 정확히
  `MCP_REAL_CLIENT_NOT_VERIFIED`로 남긴다.

## P4. VERIFY와 fresh browser E2E

상태: `COMPLETED`

검증 순서:

1. focused frontend action/route tests
2. `npm.cmd --prefix frontend run test:unit`
3. `npm.cmd --prefix frontend run typecheck`
4. `npm.cmd --prefix frontend run lint`
5. `npm.cmd --prefix frontend run build`
6. `node --test scripts/clean-clone-demo.test.mjs`
7. `./gradlew.bat test --tests com.prizm.mcp.McpCareerEvidenceProtocolTest --no-daemon`
8. 고유 project/새 volume Compose build·health
9. USER1 signup/login, TXT/PDF upload/ACTIVE/search/source navigation
10. USER2 문서·검색 0건, down/up persistence, console `ERROR` 0
11. 실제 MCP Inspector live endpoint 검증
12. `node scripts/verify-oss-readiness.mjs`, Markdown local links와 `git diff --check`

전체 backend unit/integration은 backend Production source, auth, search, owner와 schema를
바꾸지 않으므로 필수 범위에서 제외한다. MCP나 backend source가 변경되면 즉시 PLAN으로
돌아가 관련 전체 test를 추가한다.

## 변경하지 않는 계약

- SearchService/profile/repository/threshold/embedding
- REST/MCP response와 original API
- JWT, `ROLE_USER`, DB user revalidation과 owner scope
- Flyway migration과 DB schema
- PDF page source와 viewer URL fragment
- clean-clone credential 비노출과 loopback fail-closed 경계
- dependency, license, NOTICE와 SBOM

## 실패·rollback·중단 조건

- TXT deep link가 다른 USER 문서/version을 열면 즉시 중단하고 `REGRESSION_FOUND`로 판정한다.
- PDF page 회귀, USER2 leakage, persistence 실패 또는 browser console error가 있으면 완료로 표시하지 않는다.
- MCP Inspector가 환경 문제로 실행되지 않으면 제품 코드를 우회 수정하지 않고
  `MCP_REAL_CLIENT_NOT_VERIFIED`로 남긴다.
- branch는 commit하지 않은 local 변경이므로 schema/data migration 없이 파일 단위로
  되돌릴 수 있다. 사용자 지시상 commit, push와 PR은 수행하지 않는다.

## PLAN Gate

- R1∼R6이 변경 파일과 실행 검증에 연결됐다.
- backend API/schema/search/auth/owner 불변식과 PDF 회귀 방어가 명시됐다.
- MCP warning을 숨기는 workaround와 dependency 변경을 범위에서 제외했다.
- fresh volume E2E와 cleanup 범위가 정해졌다.

판정: `PASS`
