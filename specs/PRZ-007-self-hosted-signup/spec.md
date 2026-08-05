# PRZ-007 자체 호스팅 회원가입

## 상태

`VERIFIED`

기준 source: `37bd73756d677963ba26685a27041ef190beb3f7 + uncommitted worktree`

## 목적과 흐름

로컬 데모 바로 시작을 제거하고 Career Vault의 기본 진입을
`회원가입 → 로그인 → 기존 기능`으로 바꾼다. 공개 SaaS용 계정 체계가 아니라 자체
호스팅 사용자가 일반 `USER` 계정을 직접 만드는 최소 기능이다.

## API 계약

- `POST /api/auth/signup`
- 요청: `{"email":"user@example.com","password":"password"}`
- 성공: `201 Created`, 빈 body. JWT·refresh token·session을 만들지 않는다.
- 검증 실패: `400 Bad Request`
- 정규화된 이메일 중복: `409 Conflict`, 기존 `ErrorResponse` 형식
- 입력에는 role·enabled·status가 없으며 서버가 `UserRole.USER`를 지정한다.

기존 `LoginRequest`, `users` 테이블, `UserAccount.create`, `BcryptPasswordPolicy`와
`UserAccountRepository.findByEmail`을 재사용한다. Flyway migration은 추가하지 않는다.

## 화면 계약

첫 화면은 이메일·비밀번호·비밀번호 확인을 받는 회원가입 화면이다. 비밀번호 확인은
서버로 보내지 않는다. 성공하면 자동 로그인하지 않고 로그인 화면으로 전환한다.
로그인 화면에서도 회원가입 화면으로 돌아갈 수 있다.

## 제거·보존 범위

제거: `PRIZM_LOCAL_DEMO_ENABLED`, local-demo availability, `local-session`, 로컬 단일
사용자 생성 로직과 바로 시작 UI.

보존: 일반 로그인, JWT 발급·검증, DB 사용자 상태 재확인, 사용자별 문서 격리,
`PRIZM_BOOTSTRAP_DEMO_USER_*`와 자동화용 demo bootstrap.

제외: 이메일 인증·발송, 비밀번호 재설정, refresh token, OAuth, CAPTCHA, rate limit,
계정 복구·승인·프로필·삭제, JWT 변경, DB migration과 공개 서비스 배포 보호.

## 변경 파일과 완료 조건

운영 코드·설정 13파일, 테스트 5파일, PRZ-007·현재 문서 9파일의 승인 상한 안에서만
수정한다. 완료 조건은 회원가입·중복 차단·BCrypt·고정 `USER`·기존 로그인과 보호
계약, 로컬 세션 제거, bootstrap 유지, frontend lint/build와 관련 backend 검증이다.
Docker/PostgreSQL 환경 검증이 실행되지 않으면 상태를 `VERIFIED`로 올리지 않는다.
