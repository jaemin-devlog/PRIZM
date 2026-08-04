# PRZ-006 로컬 보관함 빠른 시작 — Plan

## 현재 단계와 범위

- 현재 단계: `AUDIT`
- 기준 source commit: `b370cd91f93bd617abebd7afce56fc495eb7b161`
- GitHub Issue: `NOT_CREATED`
- 이번 구현은 기존 Docker 실행과 로그인 UI 안에 작은 local-session 진입점을 더하는
  수직 슬라이스다.

## 설계 결정

### D-01 — 두 실행 모드가 아니라 기존 로그인 위의 선택적 빠른 시작

처음에는 `.env`가 전혀 없는 단일 사용자 Docker Compose와 별도 다중 사용자 Compose를
나누는 방안을 검토했다. 이 방식은 실행 편의성을 높일 수 있지만 secret 생성·DB 초기화·
Ollama 모델 관리·운영 배포 경계를 한 기능에 동시에 도입한다.

이번에는 기존 `.env` 기반 Docker Compose를 유지한다. 기본 Compose에만
`PRIZM_LOCAL_DEMO_ENABLED=true`를 넣어 로그인 폼 대신 `PRIZM 시작하기`를 표시하고,
일반 Spring Boot 실행에서는 이 값을 기본 `false`로 두어 기존 로그인 폼을 유지한다. multi-user 배포 분리와 `.env` 제거는 별도
SPEC으로 다시 계획한다.

### D-02 — 빠른 시작도 정상 JWT를 발급

`userId=1` 하드코딩이나 Spring Security 비활성화 대신 local `USER`를 생성 또는 재사용하고
기존 `JwtTokenService`로 access token을 발급한다. 이어지는 요청은 현재 JWT DB 재검증과
owner-scoped repository/service 경로를 그대로 사용한다.

계정의 password hash는 엔티티 제약을 만족하기 위한 실행 시 난수 hash이며, 원문
비밀번호는 저장·로그·응답에 포함하지 않는다. 이 계정이 비활성화되었거나 `USER`가 아닌
role이면 빠른 시작은 실패한다.

### D-03 — 실행 방식에 맞는 진입 화면 표시

frontend는 공개 `GET /api/auth/local-demo`가 활성 상태를 반환하면 로그인 폼을 숨기고
`PRIZM 시작하기`를 표시한다. 비활성 상태에서는 기존 이메일·비밀번호 로그인 폼을 표시한다.
endpoint 조회 실패는 일반 로그인에 영향을 주지 않으며,
local-session 실패는 성공처럼 보이지 않는다.

## 예상 변경

- `compose.yaml`: 기존 topology에 local-demo 활성 환경 변수 하나만 추가
- auth config/controller/service/DTO: local-demo availability와 local-session JWT 발급
- `frontend/src/App.tsx`, `frontend/src/api/authApi.ts`, `frontend/src/styles.css`: local demo와
  일반 로그인 진입 화면 분기, 문서 목록·경력 근거 검색의 빈 상태와 문구·스타일 정리
- 관련 backend unit test, README·Quickstart의 최소 실행 안내, `tasks.md`

Flyway migration, Dockerfile, Ollama 구성, OpenSQL 구성, 검색·문서 API 계약은 변경하지
않는다.

## 검증 계획

1. backend unit: 기능 비활성 기본값, local account 생성·재사용·오류와 JWT 응답
2. PostgreSQL integration: local token의 DB 재검증과 owner isolation
3. frontend lint/build; 현재 공식 frontend unit runner가 없으면 이를 `NOT_RUN`으로 기록
4. Docker Compose config, local start, 일반 로그인 회귀와 브라우저 흐름
5. 독립 읽기 전용 AUDIT 후 실제 PR/CI 확인

Docker, PostgreSQL, pgvector, Ollama와 OpenSQL/OpenProxy/OpenHA 사용 여부를 VERIFY
evidence에 분리해 기록한다.

## 중단 조건과 후속 분리

- 새 비밀값 전달 방식, container image, 모델 downloader 또는 migration이 필요해지면
  IMPLEMENT를 멈추고 새 PLAN을 작성한다.
- 회원가입·비밀번호 재설정·이메일 인증·운영 다중 사용자 배포는 PRZ-006 범위 밖이다.
- OpenSQL 검증은 이 기능과 독립적으로 계속 `NOT_RUN` 또는 기존 검증 상태를 유지한다.
