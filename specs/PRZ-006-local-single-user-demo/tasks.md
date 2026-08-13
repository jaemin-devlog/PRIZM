# PRZ-006 — 로컬 보관함 빠른 시작 Tasks

> **현재 상태:** `VERIFIED`

## P1. 선택적 빠른 시작 설계

- [x] 기존 Compose에 local-demo opt-in 환경 변수를 추가했다.
- [x] 일반 Spring Boot 실행의 기본값을 `false`로 유지했다.

## P2. 정상 JWT와 owner 경계

- [x] `T-02` local-demo availability와 local-session JWT 발급을 구현했다.
- [x] local account 생성·재사용·오류 unit test를 통과했다.
- [x] PostgreSQL에서 token DB 재검증과 owner isolation을 검증했다.

## P3. 진입 화면과 문서

- [x] `T-03` opt-in 시작 버튼과 일반 로그인 회귀를 구현했다.
- [x] `T-04` README·Quickstart의 범위를 현행화했다.
- [x] `T-04A` 빈 상태와 로그인 문구·style을 기능 변경 없이 정리했다.
- [x] frontend lint·build와 browser 흐름을 확인했다.

## P4. 검증과 통합

- [x] backend unit·integration과 Docker Compose를 검증했다.
- [x] `T-13` 독립 audit finding을 수정했다.
- [x] `T-14` 재감사에서 MEDIUM finding 2건을 해소하고 `PASS`를 받았다.
- [x] 실제 GitHub 통합과 source를 [Evidence](evidence.md)에 기록했다.

## 후속 또는 제외 범위

- [ ] 회원가입·이메일 인증·비밀번호 재설정·OIDC는 후속 범위다.
- [ ] OpenSQL·OpenProxy·OpenHA, failover, MCP, CareerFact와 portfolio는 제외한다.
