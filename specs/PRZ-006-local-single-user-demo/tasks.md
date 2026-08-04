# PRZ-006 로컬 보관함 빠른 시작 — Tasks

## 상태

- 현재 단계: `INTEGRATE`
- 구현 상태: `VERIFIED`
- 검증 source commit: `bfd86005862aa15927c707250330c70ebf81c133`
- GitHub Issue: `NOT_CREATED`

아래 항목은 구현·검증 순서를 위한 작업 목록이다. 체크되지 않은 항목은 구현 또는 검증
증거가 없다는 뜻이며, 계획 자체를 완료 증거로 사용하지 않는다.

## IMPLEMENT

- [x] T-01 기존 `compose.yaml`에 local-demo 활성 환경 변수를 추가한다.
  - 조건: 기존 `.env`, DB/host Ollama, port topology와 일반 로그인 계약을 바꾸지 않는다.
- [x] T-02 local-demo availability와 `POST /api/auth/local-session`을 구현한다.
  - 조건: 기존 JWT·DB revalidation·owner scope를 사용하며, user ID 또는 비밀번호를 하드코딩하지 않는다.
- [x] T-03 local demo에서 로그인 폼 대신 `PRIZM 시작하기` 흐름을 구현한다.
  - 조건: local-demo가 꺼진 일반 실행에서는 버튼이 보이지 않고, 일반 로그인 UX가 유지된다.
- [x] T-04 README·Quickstart에 기존 Docker 실행과 빠른 시작의 범위를 짧게 현행화한다.
- [x] T-04A 문서 목록·경력 근거 검색의 빈 상태와 로그인 문구·스타일을 기능 변경 없이 정리한다.

## TEST

- [x] T-05 local-demo availability, local-session 생성·재사용·오류의 backend unit test를 추가·갱신한다.
- [x] T-06 PostgreSQL·pgvector integration에서 local token의 DB 재검증과 owner isolation을 검증한다.
- [x] T-07 frontend local button과 기존 login 회귀를 검증한다. test runner가 없으면 lint/build와 수동 browser 결과를 명확히 기록한다.
- [x] T-08 기존 Compose config와 빠른 시작 버튼 노출을 Docker에서 확인한다.

## VERIFY

- [x] T-09 backend unit과 PostgreSQL integration을 현재 source에서 실행한다.
  - T-06의 local-session 전용 DB 재검증·owner isolation 시나리오를 실제 PostgreSQL에서 통과했다.
- [x] T-10 frontend lint/build를 현재 source에서 실행한다.
- [x] T-11 기존 Docker Compose와 브라우저 핵심 흐름을 실행한다.
- [x] T-12 `evidence.md`에 commit, 명령, 환경, PASS/FAIL/NOT_RUN, Docker/PostgreSQL/pgvector/Ollama/OpenSQL/OpenProxy/OpenHA 사용 여부를 기록한다.

## AUDIT / INTEGRATE

- [x] T-13 독립 읽기 전용 AUDIT으로 인증·소유권·Compose·문서·diff를 검토한다.
  - 초기 availability 조회 실패가 일반 로그인 화면에 고정되는 문제와 문서 모순을 확인해 수정했다.
- [x] T-14 CRITICAL/HIGH/MEDIUM finding을 모두 해소하고 재감사한다.
  - 재감사 결과 두 MEDIUM finding은 모두 `RESOLVED`였고 최종 판정은 `PASS`였다.
- [ ] T-15 사용자가 GitHub write를 승인한 경우에만 실제 PR, CI, solo review 예외와 merge evidence를 기록한다.
- [ ] T-16 병합 뒤 `main`과 registry/evidence의 실제 source commit·last verified를 갱신하고 branch 정리 조건을 확인한다.

## 범위 밖

- [ ] 회원가입, 이메일 인증, 비밀번호 재설정, refresh token, OIDC는 PRZ-006에서 구현하지 않는다.
- [ ] OpenSQL/OpenProxy/OpenHA, DB failover, MCP, 검색 알고리즘, CareerFact와 portfolio 생성은 PRZ-006에서 구현하지 않는다.
- [ ] Flyway migration, 모델 weight/cache, DB volume, 업로드 원본, `.env`와 credential을 Git에 추가하지 않는다.
