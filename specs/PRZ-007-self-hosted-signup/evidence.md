# PRZ-007 — 자체 호스팅 회원가입 Evidence

## 판정

`VERIFIED`

- 실행일: `2026-08-05`
- 로컬 검증 기준: `main` `37bd73756d677963ba26685a27041ef190beb3f7` 위 uncommitted worktree
- 통합 source: `2b8b60069c37eea91e485bffe2c54e62cd2117ab`
- GitHub Issue: `NOT_CREATED` — 이 작업을 위해 별도 Issue를 만들지 않음
- GitHub 통합: PR [#33](https://github.com/jaemin-devlog/PRIZM/pull/33) `MERGED`, merge commit `f1fb34145a7cb4a8d5025365764c11dac4516527`, review 없음
- Flyway migration·dependency 변경: 없음

Docker Compose의 PostgreSQL 16.14에서 Flyway V1–V13, 회원가입·로그인·보호 API와
두 사용자 문서 격리를 확인했다. Testcontainers 전체 통합 회귀와
`http://localhost:5173` 브라우저 흐름, backend·frontend·OSS 회귀도 통과했다.
검증용 secret은 추적 파일에 쓰지 않았고 임시 Compose override는 검증 후 삭제했다.

## 검증한 수직 흐름

```text
회원가입 요청과 입력 검증
↓
PostgreSQL에 BCrypt 활성 USER 저장
↓
자동 로그인 없이 로그인 화면 전환
↓
기존 로그인 JWT와 보호 API 호출
↓
Career Vault 진입·새로고침과 owner 격리 확인
```

## 실행 결과

- **범위:** 사전 조건
  - 명령: `node scripts/check-clean-clone-prerequisites.mjs`
  - 결과: `PASS` — Docker 29.6.2, Compose v5.3.1, Java 17, Node 22, Ollama와 필수 port 확인
- **범위:** Compose 설정
  - 명령: `docker compose --file compose.yaml --env-file .env --project-name prizm config --quiet`
  - 결과: `PASS`
- **범위:** Compose 기동
  - 명령: Docker Desktop 실행 파일로 `compose --file compose.yaml --env-file .env --project-name prizm up -d --build`
  - 결과: `PASS` — PostgreSQL 16.14, Flyway V1–V13, backend·frontend HTTP 200
- **범위:** PostgreSQL API·DB
  - 명령: 메모리 내 Node `fetch` 검증과 container 내부 `psql` 조회
  - 결과: `PASS` — signup `201` 빈 body, 중복 `409`, 잘못된 입력 `400`, BCrypt·활성 `USER`, login JWT와 `/api/users/me` `200`
- **범위:** 두 사용자 격리
  - 명령: 메모리 내 Node `fetch`로 A 문서 생성 후 A/B 목록·상세 조회
  - 결과: `PASS` — B 목록 제외, B의 A 상세 조회 `404`
- **범위:** signup 대상 unit
  - 명령: `.\gradlew.bat test --tests com.prizm.auth.controller.AuthControllerTest --tests com.prizm.auth.service.AuthServiceTest --no-daemon --rerun-tasks`
  - 결과: `PASS`
- **범위:** backend unit 전체
  - 명령: `.\gradlew.bat test --no-daemon --rerun-tasks`
  - 결과: `PASS` — 268개 중 253 pass, 15 skip, failure/error 0
- **범위:** PostgreSQL auth integration
  - 명령: `.\gradlew.bat integrationTest --tests com.prizm.infrastructure.AuthenticationIntegrationTest.signedUpUserLogsInAndJwtRevalidationIsolatesDocumentsByOwner --tests com.prizm.infrastructure.AuthenticationIntegrationTest.bootstrappedDemoUserLogsInThroughHttpAndUsesJwtProtectedRoute --no-daemon --rerun-tasks`
  - 결과: `PASS`
- **범위:** PostgreSQL integration 전체
  - 명령: `.\gradlew.bat integrationTest --no-daemon --rerun-tasks`
  - 결과: `PASS` — 70개 중 67 pass, 3 skip, failure/error 0
- **범위:** frontend lint
  - 명령: `npm.cmd --prefix frontend run lint`
  - 결과: `PASS`
- **범위:** frontend build
  - 명령: `npm.cmd --prefix frontend run build`
  - 결과: `PASS`
- **범위:** bootstrap 도구 회귀
  - 명령: `node --test scripts/clean-clone-demo.test.mjs`
  - 결과: `PASS` — 27개 중 26 pass, Windows의 POSIX mode 1개 skip
- **범위:** SBOM 구조·checksum
  - 명령: `node scripts/verify-sbom.mjs`
  - 결과: `PASS`
- **범위:** OSS·Markdown·SBOM 전체
  - 명령: 임시 Git index에서 `node scripts/verify-oss-readiness.mjs`
  - 결과: `PASS` — Markdown 47개·로컬 링크 369개, 외부 링크 22개, SBOM 회귀 12개
- **범위:** 브라우저
  - 명령: 인앱 브라우저에서 `http://localhost:5173` 접속 후 회원가입→로그인→보관함→새로고침
  - 결과: `PASS` — 가입 후 로그인 화면, 자동 로그인 없음, 로그인과 인증 유지 성공, console error/warn 0
- **범위:** 브라우저 HTTP
  - 명령: frontend Nginx access log 조회
  - 결과: `PASS` — 공식 흐름의 실패 응답 0건, local-demo 요청 0건. 브라우저 network panel 직접 조회는 도구 제약으로 `NOT_RUN`
- **범위:** local-demo 제거
  - 명령: 유효한 일반 `USER` JWT로 두 경로 요청 후 Security·controller source 대조
  - 결과: `PASS` — 두 요청 모두 deny-all `403`; `AuthController` 매핑은 login·signup만 존재해 제거 확인
- **범위:** GitHub PR
  - 명령: `gh pr view 33 --json number,title,url,state,mergedAt,mergeCommit,headRefName,headRefOid,baseRefName,statusCheckRollup,reviews,reviewDecision`
  - 결과: `PASS` — PR #33, head `2b8b600`, merge `f1fb341`, check 6건 성공, review 없음
- **범위:** 최종 whitespace
  - 명령: `git diff --check`
  - 결과: `PASS`
- **범위:** OpenSQL·OpenProxy·OpenHA
  - 명령: 실행하지 않음
  - 결과: `NOT_RUN`

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
- PR #33은 merge됐으며 review는 없어 `REVIEW_NOT_AVAILABLE_SOLO`다. 이는 GitHub review 근거가 아니다.
