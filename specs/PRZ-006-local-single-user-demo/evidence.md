# PRZ-006 로컬 보관함 빠른 시작 — Evidence

## 판정

- 검증일: `2026-08-04`
- 판정: `VERIFY_PASS_AUDIT_PENDING`
- registry 권고 상태: `IN_PROGRESS` 유지
- GitHub Issue: `NOT_CREATED`
- GitHub PR·CI·review·merge: `NOT_RUN`

현재 범위의 backend unit, PostgreSQL·pgvector integration, frontend lint/build와
Docker 브라우저 흐름이 통과했다. local-session JWT의 DB 재검증과 owner isolation도
전체 통합 테스트에 포함됐다. 독립 AUDIT 전까지 PRZ-006은 `VERIFIED`가 아니다.

## 검증한 source

- branch: `PRZ-006-local-single-user-demo`
- 기준 HEAD: `b370cd91f93bd617abebd7afce56fc495eb7b161`
- 상태: 기준 HEAD 위의 미커밋 PRZ-006 working tree
- staged 파일: 없음

이 결과는 미커밋 working tree를 대상으로 하므로 최종 PR이나 merge commit에 고정된
증거가 아니다. AUDIT 통과와 커밋 뒤 최종 source commit을 기록해야 한다.

## 실행 환경

| 항목 | 실제 환경 |
|---|---|
| OS | Windows 개발 호스트 |
| Java runtime | `21.0.6` |
| Node.js | `22.17.0` |
| npm | `10.9.2` |
| Docker client/server | `29.6.2` / `29.6.2` |
| Docker Compose | `v5.3.1` |
| DB container | `pgvector/pgvector:0.8.2-pg16-bookworm` |

## 실행 결과

| 범위 | 명령 또는 검증 | 결과 |
|---|---|---|
| backend unit | `.\gradlew.bat test --no-daemon --rerun-tasks` | `PASS` — 267개 중 252 pass, 15 skip, failure 0, error 0 |
| PostgreSQL integration | `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` — 70개 중 67 pass, 3 skip, failure 0, error 0 |
| local-session PostgreSQL integration | `.\gradlew.bat integrationTest --tests com.prizm.infrastructure.AuthenticationIntegrationTest.localSessionJwtRevalidatesDatabaseUserAndIsolatesDocumentsByOwner --no-daemon --rerun-tasks` | `PASS` — 1개 pass, failure 0 |
| authentication integration regression | `.\gradlew.bat integrationTest --tests com.prizm.infrastructure.AuthenticationIntegrationTest --no-daemon --rerun-tasks` | `PASS` — 31개 pass, skip 0, failure 0, error 0 |
| frontend dependency | `npm.cmd --prefix frontend ci --cache .npm-cache` | `PASS` — 152 packages, vulnerability 0 |
| frontend lint | `npm.cmd --prefix frontend run lint` | `PASS` |
| frontend build | `npm.cmd --prefix frontend run build` | `PASS` |
| frontend unit | `npm --prefix frontend test` | `NOT_RUN` — `frontend/package.json`에 공식 test script가 없음 |
| Compose syntax | `docker compose config --quiet` | `PASS` |
| Docker build/start | `docker compose up -d --build` | `PASS` |
| OSS readiness | `node scripts/verify-oss-readiness.mjs` | `PASS` — Markdown 41개·로컬 링크 360개, 추적 파일 315개, SBOM 회귀 12개, 외부 링크 21개 |
| diff | `git diff --check` | `PASS` |

통합 테스트의 3개 skip은 OpenSQL opt-in 테스트 1개와 Windows에서
`SecureDirectoryStream`을 제공하지 않아 실행되지 않은 cleanup 파일시스템 시나리오
2개다. PostgreSQL·pgvector SQL 테스트 자체는 실제 Testcontainers에서 실행됐다.

## Docker와 브라우저 검증

기본 Compose는 DB·backend·frontend를 각각 `127.0.0.1`에만 바인딩했다.

1. `GET /api/auth/local-demo`가 `available=true`를 반환했다.
2. `POST /api/auth/local-session`이 기존 `JwtTokenService` 형식의 access token을 발급했다.
3. 해당 token으로 `GET /api/users/me`를 호출해 `local@prizm.local`, role `USER`를 확인했다.
4. local-session을 다시 요청했을 때 같은 사용자 ID가 재사용됐다.
5. 브라우저에는 이메일·비밀번호 입력 대신 `내 보관함`과 `PRIZM 시작하기`만 표시됐다.
6. 버튼을 누르면 `/career-vault/documents`로 이동하고 sidebar에 `local@prizm.local`이 표시됐다.
7. 로그아웃하면 local 시작 화면으로 돌아왔다.
8. 임시 Docker override로 local demo를 끄면 availability는 `false`, local-session은 `404`였고 기존 이메일·비밀번호 로그인 폼만 표시됐다.
9. 검증 뒤 임시 override를 삭제하고 기본 local demo 설정으로 복원했다.

access token, 비밀번호, `.env` 값은 출력하거나 문서에 기록하지 않았다. backend 로그에서
JWT 형태 문자열이 노출되지 않는 것도 확인했다.

## 실제 사용 여부

| 환경·의존성 | 상태 | 근거 |
|---|---|---|
| Docker | `USED` | Compose build/start와 브라우저 smoke |
| PostgreSQL | `USED` | Testcontainers와 Compose DB |
| pgvector | `USED` | PostgreSQL integration과 pgvector image |
| Ollama | `NOT_USED` | 이번 인증·화면 smoke에서는 embedding 요청을 실행하지 않음 |
| OpenSQL | `NOT_RUN` | 외부 OpenSQL 환경을 사용하지 않음 |
| OpenProxy | `NOT_RUN` | 구성·실행하지 않음 |
| OpenHA | `NOT_RUN` | 구성·실행하지 않음 |

PostgreSQL 성공은 OpenSQL·OpenProxy·OpenHA 성공을 의미하지 않는다.

## 남은 Gate

독립 읽기 전용 AUDIT과 source commit 고정이 남아 있다.

## 다음 단계

1. 독립 읽기 전용 `AUDIT`을 진행한다.
2. AUDIT 통과 뒤 commit에 검증 결과를 고정한다.
