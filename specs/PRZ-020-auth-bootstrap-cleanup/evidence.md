# PRZ-020 — Evidence

> **상태:** `VERIFIED`
> **기준선:** `332c0930d6dc3b9ae656b299f62832eb2ecf446e`
> **검증 대상:** 기준선에서 시작한 로컬 미커밋 `PRZ-020-auth-bootstrap-cleanup` 작업 트리
> **검증일:** 2026-08-28
> **통합 상태:** `main` 통합 완료 ([PR #62](https://github.com/jaemin-devlog/PRIZM/pull/62), 병합 `adb033b`)

## ORIENT와 역할 Gate

| 항목 | 확인된 용도 | 일반 사용자 필요 여부 | 최종 처리 |
|---|---|---:|---|
| demo `USER` bootstrap | PRZ-004 당시 clean-clone 계정 준비 | 불필요 | Production runner·설정·test 제거 |
| `SYSTEM_ADMIN` bootstrap | 최초 관리자 계정 생성 | 불필요 | Production runner·설정·test 제거 |
| `SYSTEM_ADMIN` role 값 | bootstrap과 과거 관리자 차단 test | 불필요 | V17 호환 migration과 함께 현재 enum·type에서 제거 |

- 관리자 전용 HTTP API, UI와 service는 각각 0개였다.
- bootstrap을 제외한 Production 사용은 backend·frontend role type뿐이었고 관리자 업무나
  데이터 우회 권한은 없었다.
- 공개 signup은 이미 email·password만 받고 언제나 `USER`를 만들며, OpenSQL MCP
  integration도 signup → login → JWT 경로를 사용하고 있었다.
- Gate 판정은 `REMOVE_SYSTEM_ADMIN_ROLE`이다. V17은 기존 행을 삭제하거나 활성 USER로
  승격하지 않고 `enabled=false`, `role='USER'`로 바꾼다. ID, password hash와 소유 FK는
  보존하고 DB check constraint는 새 `SYSTEM_ADMIN` 값을 거부한다.

## 구현 결과

- demo·관리자 bootstrap runner, properties, conflict guard와 전용 repository method를
  제거했다. BCrypt 72 UTF-8 byte 정책은 일반 signup/login이 사용하는
  `com.prizm.auth.security` package로 이동했다.
- `application.yml`과 `.env.example`의 두 bootstrap namespace와 여섯 환경 변수를
  제거했다. JWT·runtime DB·Flyway 비밀번호는 서버·DB 비밀값이며 웹 로그인
  비밀번호가 아니라고 구분했다.
- backend와 frontend의 역할 값은 `USER` 하나다. JWT role claim, DB 재검증,
  `ROLE_USER` API·MCP 경계와 owner ID 전달 구조는 유지했다.
- clean-clone 환경 생성기는 Compose project·port·CORS·JWT·DB/Flyway secret만 쓴다.
  verifier는 실행 메모리에서 매번 새 email·BCrypt-safe password를 생성해 signup 201,
  login 200, 빈 owner 범위, TXT/PDF 업로드, `ACTIVE`, source-aware 검색과 anonymous
  401을 확인한다. credential과 JWT는 `.env`나 출력에 남기지 않는다.
- README와 Quickstart는 `.env` 서버 설정 → Compose → 브라우저 회원가입 → 로그인
  흐름으로 정리했다. Architecture, Project Status, AGENTS와 현재 showcase도 단일 USER
  모델로 맞췄다. Roadmap에는 수정할 현재 bootstrap 설명이 없었다.

## VERIFY

### 자동 검사

| 검사 | 결과 |
|---|---|
| `node --test scripts/clean-clone-demo.test.mjs` | `PASS` — 26개 중 25 pass, Windows의 POSIX mode 1 skip, 0 fail |
| focused `AuthControllerTest`, `AuthServiceTest`, `BcryptPasswordPolicyTest`, JWT·DB converter·MCP 및 Flyway unit | `PASS` — Gradle `BUILD SUCCESSFUL` |
| PostgreSQL `AuthenticationIntegrationTest` | `PASS` — 30개, 0 fail |
| PostgreSQL `CareerPlatformMigrationTest` | `PASS` — 9개, 0 fail |
| `gradlew integrationTest --no-daemon --rerun-tasks` | `PASS` — 115개, 0 fail, 8 skip |
| tracked backend unit 전체 | `PASS` — 610개, 0 fail, 16 skip |
| `npm --prefix frontend run test:unit` | `PASS` — 85개, 0 fail |
| `npm --prefix frontend run lint` | `PASS` |
| `npm --prefix frontend run build` | `PASS` — TypeScript와 Vite Production build |
| Markdown local link 검사 | `PASS` — 157개 Markdown, local link 689개 |
| tracked-file safety·license·NOTICE·source-only·SBOM 구조 | `PASS` — tracked 823개, readiness/SBOM test 14개 |
| `.env.example` 대조 | `PASS` — 37개 key, bootstrap key 0개 |
| `git diff --check` | `PASS` |

`.env.example`에 없는 application 환경 변수 3개는 모두 기본값이 있는 선택 설정이다:
ChangeLog scheduler enable·delay와 search profile. Compose/application startup이 삭제한
bootstrap 변수에 의존하는 참조는 없다.

### 실제 clean-clone Compose

- 고유 project `prizm-clean-clone-pfwxw00qzhq7`와 새 PostgreSQL volume을 사용했다.
- Docker Engine 29.7.2, backend Java 17 image, frontend Node 22.17.0 image,
  PostgreSQL·pgvector와 host Ollama `bge-m3` 경로에서 실행했다.
- backend health는 `UP`, frontend는 HTTP 200이었다.
- verifier는 signup → login 뒤 합성 TXT `TEXT_CHUNK`와 PDF `PAGE` 두 문서를
  `ACTIVE`로 만들고 두 검색과 로그아웃 상태의 401을 통과했다.
- DB는 Flyway 최신 version 17, `USER` role 한 종류와 새 활성 검증 계정 1개를 확인했다.
- 검증 뒤 container와 network는 내렸고 volume은 복구 가능한 증거로 보존했다. 임시
  `.env` secret 파일은 삭제하고 작업 전 사용자의 ignored `.env`를 원문 그대로 복원했다.

### 환경 한계와 비차단 관찰

- host prerequisite 검사는 Java 21.0.12, bundled Node 24.19, `npm.cmd` 부재와 기본
  locator의 Docker CLI 미발견 때문에 전체 `PASS`가 아니었다. Ollama 0.32.14,
  감사된 `bge-m3` digest, 1024 dimensions와 port 검사는 통과했다. 실제 smoke는
  저장소의 Docker CLI와 요구 Java 17·Node 22.17 container image로 통과했으므로 제품
  acceptance를 막지 않는다.
- 원형 `gradlew test --no-daemon`은 작업 전부터 있던 ignored P12 평가 Java 파일 2개가
  이미 제거된 `ClaimSupportDecision`을 참조해 `compileSearchEvaluationJava`에서 한 번
  실패했다. 사용자 파일은 삭제하지 않았다. 임시 Gradle init에서 정확히 두 ignored
  파일만 source set에서 제외하고 tracked source 전체를 다시 실행해 위 610개 결과를
  얻었으며 init 파일은 제거했다. 이는 PRZ-020 변경 회귀가 아니라 로컬 ignored artifact
  한계다.
- host에 `npm.cmd`가 없어 단일 `verify-oss-readiness.mjs` wrapper는 끝까지 실행되지
  않았다. frontend SBOM은 같은 저장소 generator를 Node로 직접 실행했고, wrapper의
  구성 검사와 readiness/SBOM test는 모두 통과했다.
- OpenSQL 실제 runtime은 이 Spec의 필수 Gate가 아니며 `NOT_RUN`이다. 공통 OpenSQL
  compile·migration assertion은 V17에 맞춰 통과했지만 PostgreSQL 결과를 OpenSQL
  실행 증거로 확대하지 않는다.

## Security regression과 AUDIT

- signup body에 `role: SYSTEM_ADMIN`을 넣어도 저장 역할은 `USER`인 test를 유지했다.
- BCrypt 72-byte 경계, disabled user 로그인 거부, JWT subject·email·role·enabled DB
  재검증, anonymous 401과 `/mcp` `ROLE_USER` 경계가 통과했다.
- PostgreSQL 전체 integration에서 문서·태그·검색·MCP owner isolation과 활성 version
  계약이 통과했다.
- V17만 추가했고 적용된 V1–V16은 수정하지 않았다. legacy 관리자 소유 문서 FK와 행
  보존, 비활성화, legacy role 재삽입 거부를 PostgreSQL에서 확인했다.
- dependency와 배포 artifact는 바꾸지 않았다. license·NOTICE·SBOM 결과에 diff가 없다.
- Production bootstrap reference는 0개다. 남은 문자열은 V7/V17 migration, legacy JWT·
  권한 상승 음성 test, PRZ-020 설명과 기존 평가·역사 기록으로 분류했다.
- PRZ-004/006/007과 `docs/archive/`에는 diff가 없다. 당시 bootstrap 실행과 PASS를
  소급 수정하지 않았다.
- secret 포함, staged 변경과 blocking audit finding은 0개다.

## INTEGRATE 경계와 판정

이 Evidence의 VERIFY·AUDIT는 `main` 통합 전에 완료했다. commit, PR, CI와 merge 상태는
실제 GitHub 기록을 기준으로 한다. 당시 branch VERIFY·AUDIT 판정은 다음과 같다.

`AUTH_BOOTSTRAP_CLEANUP_READY`

### GitHub 통합 결과

- implementation commit: `831b2bb5b0285ea15a1c2f0369e0c1412649e354`
- PR: [#62 — PRZ-020: 인증 초기화 흐름 단순화](https://github.com/jaemin-devlog/PRIZM/pull/62)
- merge commit: `adb033b185a2eebcb637946a612db3b121e6a5ac`
- merged at: 2026-08-28
- main integration: `COMPLETE`
- final lifecycle: `VERIFIED`

필수 PostgreSQL·clean-clone·frontend·tracked backend 검증과 보안·migration 감사가
통과했다. OpenSQL `NOT_RUN`과 host·ignored artifact 한계는 위 범위를 넘어 주장하지
않는 조건으로 비차단이다.
