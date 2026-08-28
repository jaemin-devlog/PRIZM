# PRZ-020 — 인증 bootstrap 정리와 signup/login 단일화

> **상태:** `VERIFIED`
> **유형:** Authentication / Configuration / Verification
> **기준 소스:** `332c0930d6dc3b9ae656b299f62832eb2ecf446e`
> **작성일:** 2026-08-28
> **검증일:** 2026-08-28

## 문제

현재 일반 사용자의 실제 진입 흐름은 브라우저 회원가입, 로그인, JWT 발급과 서비스
이용이다. 그러나 과거 새 설치 검증을 위해 만든 demo `USER` bootstrap과 실제 관리자
업무가 없는 `SYSTEM_ADMIN` bootstrap이 Production source·설정·`.env.example`에 남아
있다. clean-clone 검증도 실제 회원가입 대신 시작 시점 계정 생성에 의존한다.

이 이중 경로는 일반 사용자가 서버 secret과 웹 로그인 비밀번호를 혼동하게 하고,
관리자 기능이 없는 제품에 관리자 계정 생성 기능을 노출한다. 현재 검증도 사용자가
실제로 거치는 인증 경로와 다르다.

## ORIENT 감사 결과

| 항목 | 현재 용도 | 제품 필요 여부 | 판정 |
|---|---|---:|---|
| demo `USER` bootstrap | PRZ-004 clean-clone 계정 준비 | 불필요 | 제거 |
| `SYSTEM_ADMIN` bootstrap | 최초 관리자 계정 생성 | 불필요 | 제거 |
| `SYSTEM_ADMIN` role 값 | bootstrap과 관리자 접근 차단 test | 불필요 | forward migration과 함께 제거 |

- 관리자 전용 HTTP API: 0개
- 관리자 UI: 0개
- 관리자 전용 service: 0개
- bootstrap을 제외한 Production `SYSTEM_ADMIN` literal: backend enum과 frontend 응답 type
- 공개 `POST /api/auth/signup`은 role 입력을 받지 않고 `USER`를 생성한다.
- OpenSQL MCP 통합 test는 이미 signup → login → JWT 경로를 사용한다.

## 사용자 흐름

```text
.env에 서버용 JWT·DB secret 설정
→ Docker Compose 실행
→ 브라우저 회원가입
→ 로그인
→ USER JWT 발급
→ owner-scoped 서비스 이용
```

clean-clone 자동 검증도 같은 `POST /api/auth/signup`과 `POST /api/auth/login`을
사용한다. 검증 계정 email과 password는 검증 프로세스 메모리에서 생성하며 `.env`,
stdout과 저장소에 기록하지 않는다.

## 범위

### 포함

- demo `USER`와 `SYSTEM_ADMIN` bootstrap runner, properties, conflict guard 제거
- 공용 BCrypt 72-byte 정책을 bootstrap package 밖의 인증 보안 구성으로 이동
- `application.yml`과 `.env.example`의 두 bootstrap 설정 제거
- `SYSTEM_ADMIN` enum 값과 frontend role union 제거
- 기존 `SYSTEM_ADMIN` DB 행을 비활성 `USER` 행으로 보존하고 role check를 단일
  `USER`로 제한하는 forward-only migration
- clean-clone 환경 준비에서 사용자 credential 생성을 제거
- clean-clone 검증을 signup → login → JWT → TXT/PDF → `ACTIVE` → 검색 → anonymous
  401 흐름으로 전환
- 관련 unit·PostgreSQL integration·script test 교체
- README, Quickstart, Architecture, Project Status와 현재 범위 문서 현행화
- Spec Registry와 PRZ-020 Evidence 기록

### 제외

- OAuth/OIDC, 이메일 인증, refresh token, password reset와 계정 복구
- 새로운 관리자 역할·API·UI·업무
- JWT claim 구조, issuer, 만료와 서명 방식 변경
- `role` column 또는 `ROLE_USER` security matcher 제거
- owner isolation, 문서·검색·MCP 데이터 흐름 변경
- OpenSQL runtime 재검증을 필수 Gate로 승격
- PRZ-004/006/007과 archive의 당시 bootstrap 실행 사실 소급 수정

## SYSTEM_ADMIN role Gate

판정: `REMOVE_SYSTEM_ADMIN_ROLE`

`SYSTEM_ADMIN`은 현재 제품 권한을 제공하지 않고 bootstrap 외 생성 경로도 없다.
다만 기존 DB 행과 JWT 호환성을 무시하고 enum만 제거할 수는 없다. 새 migration은
기존 `SYSTEM_ADMIN` 행을 삭제하지 않고 다음 순서로 정리한다.

1. 해당 행을 `enabled=false`, `role='USER'`로 갱신한다.
2. `ck_users_role`을 `USER`만 허용하도록 forward 변경한다.
3. 기존 FK, owner ID와 password hash는 보존한다.
4. 기존 관리자 JWT는 enabled·email·role DB 재검증에서 거부한다.

`role` column, JWT `role` claim과 `ROLE_USER` 인가 경계는 유지한다. 따라서 이번
판정은 역할 모델 전체 제거가 아니라 사용되지 않는 `SYSTEM_ADMIN` 값 제거다.

## 요구사항과 완료 조건

### `PRZ-020-R1` — 일반 인증 경로 단일화

Production application에는 계정을 시작 시 생성하는 bootstrap bean과 설정 binding이
없어야 한다. 회원가입은 언제나 `USER`를 BCrypt hash로 저장하고 성공 시 JWT를 만들지
않으며, 별도 로그인 성공 시에만 JWT를 발급해야 한다.

### `PRZ-020-R2` — 기존 관리자 행의 안전한 호환

V16까지의 DB에 활성 `SYSTEM_ADMIN` 행과 그 행을 참조하는 데이터가 있어도 최신
migration이 행과 FK를 보존한 채 계정을 비활성 `USER`로 전환해야 한다. migration 뒤
DB는 새 `SYSTEM_ADMIN` 값을 거부해야 한다. 적용된 과거 migration은 수정하지 않는다.

### `PRZ-020-R3` — 실제 인증 경로를 사용하는 clean-clone

환경 준비 script는 Compose project, port, JWT secret과 DB/Flyway password만 만든다.
검증 script는 loopback 경계 안에서 메모리 생성 credential로 signup과 login을 수행한
뒤 합성 TXT/PDF 업로드, `ACTIVE`, source-aware 검색과 anonymous 401을 확인한다.
password, JWT와 DB secret을 출력하지 않는다.

### `PRZ-020-R4` — 보안 회귀 방지

- signup 요청에 `role`을 추가해도 저장 역할은 `USER`다.
- BCrypt UTF-8 72-byte 경계와 disabled user 로그인 차단을 유지한다.
- JWT subject·email·role·enabled DB 재검증을 유지한다.
- 보호 API와 `/mcp`는 `ROLE_USER`를 요구한다.
- 문서·검색·태그·MCP owner isolation을 변경하지 않는다.
- anonymous 보호 API 요청은 계속 거부한다.

### `PRZ-020-R5` — 일반 사용자 문서

`.env.example`은 웹 계정 email/password를 요구하지 않는다. JWT secret은 서버 서명용,
DB/Flyway password는 DB 역할용이며 웹 로그인 비밀번호와 무관하다고 설명한다.
Quickstart와 README는 Docker 실행 뒤 브라우저 회원가입과 로그인을 첫 경로로 안내한다.

### `PRZ-020-R6` — 역사 기록과 검증 경계

PRZ-004/006/007과 archive의 과거 bootstrap `PASS`는 당시 source의 역사 기록으로
보존한다. 현재 문서와 PRZ-020 Evidence에서만 signup/login 기반 현행 구조와 새 실행
결과를 기록한다. 실행하지 않은 OpenSQL runtime은 `NOT_RUN`으로 남긴다.

### `PRZ-020-R7` — 검증

focused auth/JWT/security, migration, PostgreSQL signup/login·DB revalidation·owner
isolation, clean-clone script와 실제 Compose smoke, frontend lint/build, 전체 backend
unit·integration, Markdown link·환경 변수·`git diff --check` 검증이 통과해야 한다.

## SPEC Gate

- 두 bootstrap의 실제 사용처와 관리자 기능 부재를 source·test·schema에서 확인했다.
- 기존 관리자 행을 삭제하거나 활성 USER로 승격하지 않는 호환 전략이 정해졌다.
- JWT 구조, `ROLE_USER`와 owner isolation 보존 조건이 측정 가능하다.
- clean-clone을 별도 인증 우회 없이 현재 signup/login API로 재현할 수 있다.
- 현재 문서와 역사 기록의 수정 경계가 분리됐다.

판정: `PASS`
