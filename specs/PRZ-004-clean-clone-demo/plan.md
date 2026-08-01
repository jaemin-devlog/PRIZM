# PRZ-004 — 구현·검증 계획

## 기준선

- 원격: `https://github.com/jaemin-devlog/PRIZM.git`
- 기준 main: `936e957132fcf54b5cee1f58d83f8d591e5786e2`
- 작업 branch: `PRZ-004-clean-clone-demo` (계획 작성 당시 local only)
- 공개 main 상태: 계획 작성 당시 PRZ-004 미통합

## 작성·사후 대조 이력

- 초기 구현 후보 일부는 최종 Spec·Plan이 확정되기 전에 만들어졌다.
- Spec·Plan과 구현이 처음 함께 기록된 commit은 `f7d600f`이며, 이를 사전 승인된
  계획으로 간주하지 않는다.
- 이후 범위를 축소하고 후보 commit `0d20454eb9a3c3d9b8c7812d54a20781415b0378`을
  Spec·Plan과 사후 대조했다.
- 이 Plan은 초기 후보를 요구사항과 비교하기 위한 conformance baseline이다.
  실행 결과의 단일 원본은 [Evidence](evidence.md)다.
- 이후 실제 실행 결과와 GitHub 통합 기록은 [Evidence](evidence.md)로 이동했다.
  이 Plan의 당시 기준선과 선택은 과거 계획으로 보존한다.

## 선택한 접근

1. 기존 `users` table과 인증 흐름을 그대로 사용하고, 명시적으로 켠 한 번의
   startup에서만 고정 역할 `USER`를 만드는 bootstrap을 추가한다.
2. 가장 먼저 실행되는 guard가 demo와 `SYSTEM_ADMIN` bootstrap 동시 활성화를
   막는다. 기존 email은 수정하지 않고 fail-closed 한다.
3. BCrypt UTF-8 72-byte 정책을 작은 공용 정책으로 두고 bootstrap encode와 login
   match 전에 적용한다.
4. Node 표준 라이브러리만 사용해 `.env`, 합성 fixture와 API smoke를 만든다.
   실행마다 random suffix가 붙은 Compose project 이름을 생성하고 host port는
   명시적 CLI override를 허용한다. Compose wrapper는 `.env`의 project·file을
   명시하고 상위 shell의 Compose override를 제거한다.
5. demo bootstrap을 끈 뒤 backend를 다시 만든 다음 smoke를 실행한다. verifier는
   loopback URL만 허용하고 HTTP redirect를 따라가지 않는다.
6. Quickstart를 단일 실행 문서로 유지하고 별도 상세 문서를 추가하지 않는다.
7. 원격 main에서 직접 재현된 npm high 2건만 exact override로 교정한다. frontend
   접근성 변경은 clean-clone 차단 문제가 아니므로 제외한다.

### 정적 감사 뒤 최소 교정

- 정적 감사에서 상위 shell이 `.env`의 Compose 설정을 덮어쓸 수 있음을 확인했다.
  방식 A를 선택해 child process에서 실제 `.env`가 관리하는 모든 key와 Compose
  제어 key를 제거한다. 관계없는 `PATH` 같은 환경은 보존하고 값은 출력하지 않는다.
- verifier는 bootstrap·demo credential·port·project·Ollama 관련 shell override가
  있으면 key 이름만 표시하고 fail-closed한다. 실제 값은 `.env`만 읽는다.
- version polling은 180초 deadline, 1초 간격과 최대 181회 요청을 함께 적용한다.
- 검색 결과는 현재 실행에서 이미 업로드한 document/version allowlist 전체와
  대조하고, 예상하지 않은 결과를 조용히 무시하지 않는다.

### CORS 교정 이력

- 초기 후보의 custom frontend port와 기본 `5173` origin 불일치는 정적
  설정·script 대조에서 발견했으며 브라우저에서 재현한 결과가 아니다.
- commit `207143b`에서 frontend port를 CORS origin에 연결했다.
- commit `0d20454`에서 URL 기본 port `80` 정규화 회귀를 교정했다.
- 두 독립 clone의 브라우저 시험에서 실제 frontend port와 CORS 연결을 확인했다.

## 예상 변경

### 인증·설정

- `.env.example`
- `src/main/resources/application.yml`
- `src/main/java/com/prizm/auth/bootstrap/BootstrapDemoUserProperties.java`
- `src/main/java/com/prizm/auth/bootstrap/DemoUserBootstrapRunner.java`
- `src/main/java/com/prizm/auth/bootstrap/BootstrapAccountConflictGuard.java`
- `src/main/java/com/prizm/auth/bootstrap/BcryptPasswordPolicy.java`
- 기존 SYSTEM_ADMIN bootstrap property·runner의 byte-limit 적용 지점
- `LoginRequest`와 `AuthService`의 BCrypt 입력 경계

### 실행·검증 도구

- `scripts/check-clean-clone-prerequisites.mjs`
- `scripts/prepare-clean-clone-demo-env.mjs`
- `scripts/generate-clean-clone-demo-fixtures.mjs`
- `scripts/run-clean-clone-compose.mjs`
- `scripts/verify-clean-clone-demo.mjs`
- `scripts/clean-clone-demo.test.mjs`
- `.github/workflows/ci.yml`

### test

- demo bootstrap, conflict guard, BCrypt byte 정책 unit test
- 기존 SYSTEM_ADMIN·AuthService 경계 test 보강
- `AuthenticationIntegrationTest`의 실제 demo USER HTTP login·JWT 확인
- Node script regression test

### dependency·compliance

- `frontend/package.json`, `frontend/package-lock.json`
- 필요성이 실제 Docker build에서 확인될 때만 `frontend/Dockerfile`
- `sbom/prizm-frontend.cdx.json`, `sbom/SHA256SUMS`
- `docs/contest/2026-license-audit.md`

### 문서·Evidence

- `README.md`, `AGENTS.md`
- `docs/README.md`, `docs/quickstart.md`, `docs/project-status.md`
- `docs/architecture.md`, `docs/roadmap.md`, `docs/ai-agent-workflow.md`
- `docs/contest/2026-tmaxtibero-plan.md`, `docs/development-log.md`
- `specs/README.md`
- `specs/PRZ-004-clean-clone-demo/{spec,plan,tasks,evidence}.md`

요구사항 추적표의 공식 evidence mapping은 GitHub 통합 전에는 갱신하지 않는다.

## 변경하지 않는 영역

- 모든 Flyway migration
- `UserAccount`, `UserRole`, repository schema
- SecurityConfiguration과 JWT 발급·DB 재검증 구조
- document, search, ingestion, cleanup 구현
- `frontend/src/App.tsx`와 CSS
- OpenSQL 실행 설정·공급 파일

## 보안·ownership 영향

- demo 계정은 기존 `USER` 인증·인가 경로만 사용한다.
- 공개 endpoint는 추가하지 않는다.
- owner ID 전달, repository/SQL owner filter와 ACTIVE-only 검색을 바꾸지 않는다.
- `.env`와 credential은 ignored local file에만 두고 출력·commit하지 않는다.
- 두 bootstrap 충돌은 repository write 이전에 거부한다.

## Dependency·license 판단

- 기준 main의 ORIENT에서 `brace-expansion 5.0.7`, `postcss 8.5.16` high finding과
  production-only audit 0건을 관찰했다. 구현 commit에서 full·production audit,
  SBOM과 OSS readiness 검증을 실행해 통과했다.
- 최소 safe exact version으로 올린 뒤 lockfile 183개 component와 license
  expression을 다시 대조한다.
- frontend SBOM·checksum·license audit input SHA를 같은 diff에서 갱신한다.
- 현재 Ollama `0.32.3`, `bge-m3:latest` manifest `790764...6bab`은 기존 감사와
  일치하므로 model manifest를 변경하지 않는다.
- `node:22-alpine`의 실제 builder version이 선언한 `22.17.0`과 다를 때만 exact
  builder tag로 고정하고 Docker identity 기록을 갱신한다.

## 검증 환경

- Windows 10, Java 17.0.12, Node 22.17.0, npm 10.9.2
- Docker Engine 29.6.2, Compose 5.3.1
- PostgreSQL 16 + pgvector 0.8.2 container
- 호스트 Ollama 0.32.3, `bge-m3:latest`, manifest `790764...6bab`, 1024차원
- 첫 clone: 별도 project, ports `15433/18081/15174`
- 둘째 clone: 별도 project, ports `15434/18082/15175`

## 필수 검증

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
node --test scripts/clean-clone-demo.test.mjs
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend audit --json
npm.cmd --prefix frontend audit --omit=dev --json
docker compose config --quiet
node scripts/verify-oss-readiness.mjs
node scripts/verify-sbom.mjs
git diff --check
```

최종 source commit을 만든 뒤 두 새 clone에서 환경 준비, fixture 생성, build/up,
bootstrap 비활성화·backend recreate, API smoke, browser UI 시험과 `compose down`을
각각 수행한다. volume은 삭제하지 않는다.

## 중단·복구 조건

- model manifest가 기존 감사값과 다르면 provenance Gate로 돌아간다.
- dependency safe version이 full audit 또는 SBOM/license 검증을 통과하지 못하면
  완료하지 않는다.
- demo 계정이 기존 계정을 바꾸거나 SYSTEM_ADMIN이 되거나 ownership test가
  실패하면 구현을 중단한다.
- 두 clone이 같은 project·volume을 사용하면 검증을 중단하고 격리 설계를 고친다.
- 비밀값 노출이 확인되면 해당 출력과 생성물을 Evidence에 복사하지 않고 원인을
  먼저 교정한다.
- rollback은 이 local branch의 명시적 파일만 되돌리는 방식으로 수행한다.
  기존 project·volume과 `jaemin`·`clone` 폴더는 수정·삭제하지 않는다.

## GitHub 경계

계획 작성 당시 승인 범위는 local commit까지였다. 이후 별도 승인으로 수행한
push, PR, CI와 merge 결과는 [Evidence](evidence.md)에만 기록한다. 별도 Issue와
GitHub review는 생성되지 않았다.
