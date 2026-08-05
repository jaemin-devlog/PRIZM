# PRZ-007 자체 호스팅 회원가입 — Evidence

## 판정

`VERIFIED`

- 실행일: `2026-08-05`
- 기준: `main` `37bd73756d677963ba26685a27041ef190beb3f7` 위 uncommitted worktree
- GitHub Issue·PR·review·merge: `NOT_CREATED` — 사용자 지시로 Git 작업 금지
- Flyway migration·dependency 변경: 없음

Docker Compose의 PostgreSQL 16.14에서 Flyway V1~V13, 회원가입·로그인·보호 API와
두 사용자 문서 격리를 확인했다. Testcontainers 전체 통합 회귀와
`http://localhost:5173` 브라우저 흐름, backend·frontend·OSS 회귀도 통과했다.
검증용 secret은 추적 파일에 쓰지 않았고 임시 Compose override는 검증 후 삭제했다.

## 실행 결과

| 범위 | 명령 | 결과 |
|---|---|---|
| 사전 조건 | `node scripts/check-clean-clone-prerequisites.mjs` | `PASS` — Docker 29.6.2, Compose v5.3.1, Java 17, Node 22, Ollama와 필수 port 확인 |
| Compose 설정 | `docker compose --file compose.yaml --env-file .env --project-name prizm config --quiet` | `PASS` |
| Compose 기동 | Docker Desktop 실행 파일로 `compose --file compose.yaml --env-file .env --project-name prizm up -d --build` | `PASS` — PostgreSQL 16.14, Flyway V1~V13, backend·frontend HTTP 200 |
| PostgreSQL API·DB | 메모리 내 Node `fetch` 검증과 container 내부 `psql` 조회 | `PASS` — signup `201` 빈 body, 중복 `409`, 잘못된 입력 `400`, BCrypt·활성 `USER`, login JWT와 `/api/users/me` `200` |
| 두 사용자 격리 | 메모리 내 Node `fetch`로 A 문서 생성 후 A/B 목록·상세 조회 | `PASS` — B 목록 제외, B의 A 상세 조회 `404` |
| signup 대상 unit | `.\gradlew.bat test --tests com.prizm.auth.controller.AuthControllerTest --tests com.prizm.auth.service.AuthServiceTest --no-daemon --rerun-tasks` | `PASS` |
| backend unit 전체 | `.\gradlew.bat test --no-daemon --rerun-tasks` | `PASS` — 268개 중 253 pass, 15 skip, failure/error 0 |
| PostgreSQL auth integration | `.\gradlew.bat integrationTest --tests com.prizm.infrastructure.AuthenticationIntegrationTest.signedUpUserLogsInAndJwtRevalidationIsolatesDocumentsByOwner --tests com.prizm.infrastructure.AuthenticationIntegrationTest.bootstrappedDemoUserLogsInThroughHttpAndUsesJwtProtectedRoute --no-daemon --rerun-tasks` | `PASS` |
| PostgreSQL integration 전체 | `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` — 70개 중 67 pass, 3 skip, failure/error 0 |
| frontend lint | `npm.cmd --prefix frontend run lint` | `PASS` |
| frontend build | `npm.cmd --prefix frontend run build` | `PASS` |
| bootstrap 도구 회귀 | `node --test scripts/clean-clone-demo.test.mjs` | `PASS` — 27개 중 26 pass, Windows의 POSIX mode 1개 skip |
| SBOM 구조·checksum | `node scripts/verify-sbom.mjs` | `PASS` |
| OSS·Markdown·SBOM 전체 | 임시 Git index에서 `node scripts/verify-oss-readiness.mjs` | `PASS` — Markdown 47개·로컬 링크 369개, 외부 링크 22개, SBOM 회귀 12개 |
| 브라우저 | 인앱 브라우저에서 `http://localhost:5173` 접속 후 회원가입→로그인→보관함→새로고침 | `PASS` — 가입 후 로그인 화면, 자동 로그인 없음, 로그인과 인증 유지 성공, console error/warn 0 |
| 브라우저 HTTP | frontend Nginx access log 조회 | `PASS` — 공식 흐름의 실패 응답 0건, local-demo 요청 0건. 브라우저 network panel 직접 조회는 도구 제약으로 `NOT_RUN` |
| local-demo 제거 | 유효한 일반 `USER` JWT로 두 경로 요청 후 Security·controller source 대조 | `PASS` — 두 요청 모두 deny-all `403`; `AuthController` 매핑은 login·signup만 존재해 제거 확인 |
| 최종 whitespace | `git diff --check` | `PASS` |
| OpenSQL·OpenProxy·OpenHA | 실행하지 않음 | `NOT_RUN` |

## 요구사항 근거

- controller test는 가입 `201` 빈 body와 중복 `409`를 확인한다.
- service test는 정규화 이메일, BCrypt hash, 활성 `USER`, JWT 미발급과 기존 로그인
  성공을 확인한다.
- PostgreSQL 시나리오는 signup 입력의 role 무시, 실제 hash, 기존 login JWT,
  DB 비활성화 재검증, 두 사용자 문서 격리, local-session 차단과 demo bootstrap을
  검증했다.
- Security 설정은 login·signup만 필요한 auth 공개 POST로 유지하며 기존 보호 API
  matcher를 변경하지 않았다.
- `PRIZM_BOOTSTRAP_DEMO_USER_*` 설정과 `DemoUserBootstrapRunner`는 유지되며 runner는
  삭제된 local-session 코드에 의존하지 않는다. bootstrap 사용자는 signup UI에
  노출되지 않고 기존 login API로 인증되는 통합 테스트가 통과했다.
- 제거 문자열 검색 결과는 제거 동작 통합 테스트, PRZ-007 명세와 PRZ-006 역사
  문서에만 남았다. 실행 코드와 현재 사용자 안내 문서에는 남지 않았다.
- `127.0.0.1:5173` 사전 시도는 허용 origin인 `localhost:5173`과 달라 signup이
  `403`이었으며 구현 결함으로 판정하지 않았다. 공식 브라우저 검증은
  `http://localhost:5173`에서 수행했다.

## 남은 제한

- frontend unit test 공식 명령은 없어 `NOT_RUN`이다.
- OpenSQL·OpenProxy·OpenHA는 PRZ-007 범위가 아니므로 `NOT_RUN`이다.
- 검증용 고유 사용자와 TXT 문서는 기존 영구 PostgreSQL volume에 남겼다. 안전한
  production 삭제 API가 없어 DB를 직접 수정하지 않았고 volume도 삭제하지 않았다.
- commit·push·PR과 GitHub CI·review는 사용자 금지 범위라 `NOT_RUN`이다.
