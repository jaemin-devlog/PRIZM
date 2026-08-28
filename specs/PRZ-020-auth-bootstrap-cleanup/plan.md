# PRZ-020 — 인증 bootstrap 정리와 signup/login 단일화 Plan

> **문서 상태:** `APPROVED`
> **계획 기준선:** `332c0930d6dc3b9ae656b299f62832eb2ecf446e`

## P1. 인증 모델과 DB 호환 정리

상태: `COMPLETED`

- demo `USER`·`SYSTEM_ADMIN` runner, properties와 conflict guard를 삭제한다.
- `BcryptPasswordPolicy`는 signup/login이 사용하는 공용 보안 정책이므로
  `com.prizm.auth.security`로 이동하고 기존 UTF-8 72-byte test를 유지한다.
- `UserRole`과 frontend `UserRole`에서 `SYSTEM_ADMIN` 값만 제거한다.
- `UserAccount.role`, JWT `role` claim, DB role 재검증과 `.hasRole("USER")`는 유지한다.
- `UserAccountRepository.existsByRole`처럼 bootstrap만 사용한 API를 제거한다.
- V17은 기존 `SYSTEM_ADMIN` 행을 비활성 `USER`로 보존한 뒤 DB check를 단일
  `USER`로 바꾼다. 기존 migration은 수정하지 않는다.
- migration integration test는 행·FK 보존, 비활성화, 새 legacy role 거부와 17개
  migration 적용을 검증한다.

예상 source/test:

- `src/main/java/com/prizm/auth/**`
- `src/main/java/com/prizm/user/**`
- `src/main/resources/db/migration/V17__remove_system_admin_role.sql`
- `src/test/java/com/prizm/auth/**`
- `src/test/java/com/prizm/mcp/McpCareerEvidenceProtocolTest.java`
- `src/integrationTest/java/com/prizm/infrastructure/AuthenticationIntegrationTest.java`
- `src/integrationTest/java/com/prizm/infrastructure/CareerPlatformMigrationTest.java`
- `src/integrationTest/java/com/prizm/infrastructure/OpenSqlMcpGateTest.java`
- `frontend/src/api/authApi.ts`

## P2. clean-clone signup/login 전환

상태: `COMPLETED`

- `prepare-clean-clone-demo-env.mjs`는 project, port, CORS, JWT와 DB/Flyway secret만
  생성하고 사용자 credential이나 bootstrap flag를 쓰지 않는다.
- `verify-clean-clone-demo.mjs`는 loopback URL 검증 뒤 메모리에서 합성 email과
  password를 생성한다.
- verifier는 signup `201`, login `200`과 `USER` 응답을 확인한 뒤 기존 TXT/PDF,
  `ACTIVE`, source 검색과 anonymous 401 검증을 수행한다.
- 동일 DB 재사용은 중복 signup 또는 비어 있지 않은 owner 문서에서 fail-closed 한다.
- 오류와 stdout에서 password·JWT·DB secret을 노출하지 않는다.
- `clean-clone-demo.test.mjs`는 bootstrap enable/disable test를 제거하고 환경에 사용자
  credential이 없으며 signup이 login보다 먼저 실행되는 계약을 검증한다.

예상 script:

- `scripts/prepare-clean-clone-demo-env.mjs`
- `scripts/verify-clean-clone-demo.mjs`
- `scripts/clean-clone-demo.test.mjs`
- 현재 bootstrap property를 명시적으로 끄는 integration/evaluation helper

## P3. 설정과 사용자 문서 현행화

상태: `COMPLETED`

- `.env.example`에서 여섯 bootstrap 변수를 제거한다.
- JWT secret, runtime DB password와 Flyway password의 대상을 웹 로그인 password와
  구분한다.
- `application.yml`의 두 bootstrap namespace를 제거한다.
- README·Quickstart를 `.env` → Compose → 브라우저 signup → login 흐름으로 정리한다.
- Architecture·Project Status·Roadmap·AGENTS의 관리자/현재 bootstrap 설명을 실제
  단일 USER 모델로 갱신한다.
- showcase의 과거 관리자 차단 사례는 당시 역사로 명확히 하거나 현재 주장에서는
  제거한다.
- PRZ-004/006/007, archive와 이미 기록된 당시 evidence는 수정하지 않는다.
- `specs/README.md`에 PRZ-020의 현재 local lifecycle을 추가하되 commit·PR·merge
  식별자는 만들지 않는다.

## P4. VERIFY와 AUDIT

상태: `COMPLETED`

검증 순서:

1. `node --test scripts/clean-clone-demo.test.mjs`
2. focused auth/bootstrap-removal/JWT/MCP unit test
3. `AuthenticationIntegrationTest`, `CareerPlatformMigrationTest`와 owner isolation
4. clean PostgreSQL·Ollama Compose에서 signup/login TXT/PDF smoke
5. `npm --prefix frontend run lint`, `npm --prefix frontend run build`
6. `./gradlew.bat test --no-daemon`
7. `./gradlew.bat integrationTest --no-daemon --rerun-tasks`
8. `docker compose config --quiet`
9. Markdown local link, `.env.example`↔Compose/application key 대조,
   `rg` current/historical 분류와 `git diff --check`

OpenSQL runtime은 필수 Gate가 아니다. 공통 source compile과 기존 opt-in test source는
확인하지만 실제 환경을 실행하지 않으면 `NOT_RUN`으로 기록한다.

## Migration·보안·ownership 영향

- V17은 데이터 삭제 없이 legacy 관리자 계정을 비활성 USER 행으로 보존한다.
- migration 뒤 role claim이 `SYSTEM_ADMIN`인 기존 JWT는 DB revalidation을 통과하지
  못한다.
- signup 고정 `USER`, BCrypt, JWT DB revalidation, `ROLE_USER`와 owner ID 전달 구조는
  변경하지 않는다.
- 문서·version·chunk·tag·job·cleanup FK와 owner-scoped SQL은 수정하지 않는다.
- dependency, license, SBOM과 배포 artifact 경계는 바뀌지 않는다.

## Rollback과 중단 조건

- 구현 중 관리자 전용 Production 업무가 발견되면 role 제거를 중단하고 Spec으로
  돌아간다.
- V17이 기존 행·FK를 보존하지 못하거나 PostgreSQL migration test가 실패하면
  `MIGRATION_RISK_BLOCKED`로 중단한다.
- signup/login clean-clone이 별도 우회 없이 기존 smoke를 재현하지 못하면
  `NEEDS_ADJUSTMENT`로 남긴다.
- branch가 통합되기 전에는 변경 파일을 되돌려 rollback할 수 있다. V17이 실제 DB에
  적용된 뒤 역할을 다시 도입해야 한다면 적용된 migration을 수정하지 않고 별도
  forward migration을 작성해야 한다.

## Branch와 통합 경계

- branch: `PRZ-020-auth-bootstrap-cleanup`
- 구현·VERIFY·AUDIT 단계에서는 당시 사용자 지시에 따라 commit, push와 PR을 수행하지
  않는다. 이후 GitHub 통합은 별도의 명시적 요청이 있을 때만 진행한다.
- branch VERIFY·AUDIT 결과와 실제 GitHub 통합 상태를 구분해 Evidence에 기록한다.

## PLAN Gate

- R1–R7이 source, migration, script, docs와 실행 검증에 연결됐다.
- 기존 관리자 행, JWT, `ROLE_USER`, ownership과 historical evidence의 처리 전략이
  빠지지 않았다.
- 실패 시 중단 판정과 forward-only rollback 경계가 정해졌다.

판정: `PASS`
