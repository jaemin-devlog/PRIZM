# PRZ-007 자체 호스팅 회원가입 — Tasks

최종 상태: `VERIFIED` — `main` `37bd737` 위 worktree, 2026-08-05

## 구현

- [x] `POST /api/auth/signup`과 `201` 빈 응답, 중복 `409`를 구현한다.
- [x] BCrypt, 정규화된 이메일과 서버 고정 활성 `USER`를 저장한다.
- [x] 로컬 데모 코드·설정·UI를 제거하고 bootstrap demo user는 유지한다.
- [x] 회원가입 성공 후 로그인으로 전환하고 양방향 이동을 제공한다.
- [x] migration·dependency·JWT·소유권 경로를 변경하지 않는다.

## 검증·감사

- [x] backend 전체 단위 테스트를 실행한다.
- [x] PostgreSQL 전체 통합 테스트를 실행한다. — 70개 중 67 pass, 3 skip, 실패·오류 0
- [x] frontend lint/build와 source compile을 실행한다.
- [x] Docker Compose runtime과 `http://localhost:5173` 브라우저 흐름을 실행한다.
- [x] 제거 문자열, 파일 상한, 보호 경로와 최종 diff를 감사한다.
- [x] bootstrap 도구 회귀와 OSS·Markdown·SBOM 검증을 실행한다.
- [ ] commit·push·PR을 수행한다. — 사용자 금지 범위
