# PRZ-007 — 자체 호스팅 회원가입 Tasks

> **현재 상태:** `VERIFIED`

## P1. 회원가입 API와 보안

- [x] `POST /api/auth/signup`, `201`, validation `400`과 duplicate `409`를 구현했다.
- [x] BCrypt, normalized email과 서버 고정 활성 `USER`를 저장했다.

## P2. 로컬 데모 제거

- [x] local-demo 코드·설정·UI를 제거했다.
- [x] 자동화용 bootstrap demo user는 유지했다.
- [x] 기존 login·JWT 보호 API와 owner isolation을 검증했다.

## P3. 회원가입 화면

- [x] 회원가입과 로그인 사이의 양방향 전환을 구현했다.
- [x] 성공 뒤 자동 로그인하지 않고 로그인 화면으로 이동하게 했다.
- [x] frontend lint·build를 통과했다.

## P4. 검증과 통합

- [x] backend unit과 PostgreSQL integration을 실행했다.
- [x] Docker runtime과 `http://localhost:5173` browser 흐름을 확인했다.
- [x] 제거 문자열, 보호 경로, bootstrap, OSS·Markdown·SBOM과 diff를 감사했다.
- [x] source `2b8b600`, PR #33, check 6건과 merge `f1fb341`을 Evidence에 기록했다.
