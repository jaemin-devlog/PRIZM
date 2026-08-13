# PRZ-007 — 자체 호스팅 회원가입 Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** `37bd73756d677963ba26685a27041ef190beb3f7`
>
> 구현 전 고정한 범위와 접근을 보존한다. 최종 결과는 [Tasks](tasks.md)와
> [Evidence](evidence.md)를 따른다.

## P1. 회원가입 API와 보안

- 목표: 자체 호스팅 사용자가 일반 `USER` 계정을 만든다.
- 변경 범위: 기존 auth service·controller와 `LoginRequest` 재사용, signup test.
- 검증: `201`, validation `400`, duplicate `409`, BCrypt와 고정 `USER`를 확인한다.
- Rollback: signup source를 제거해 기존 login만 유지한다.
- 중단 조건: role 입력, migration 또는 dependency가 필요하면 중단한다.

## P2. 로컬 데모 제거

- 목표: 기본 진입을 회원가입 → 로그인으로 바꾼다.
- 변경 범위: local-demo 코드·공개 경로·설정 제거와 integration fixture 교정.
- 검증: bootstrap demo user는 유지하고 JWT 재검증·owner isolation을 확인한다.
- Rollback: signup과 local-demo 제거를 함께 되돌린다.
- 중단 조건: 기존 JWT·보호 API 계약이 달라지면 중단한다.

## P3. 회원가입 화면

- 목표: 기존 로그인 화면 안에 최소 회원가입 모드를 제공한다.
- 변경 범위: frontend form과 local-demo 호출 제거.
- 검증: 비밀번호 확인은 서버에 보내지 않고 성공 뒤 로그인으로 전환하는지 확인한다.
- Rollback: 회원가입 UI를 제거하고 login만 유지한다.
- 중단 조건: 자동 로그인·refresh token 같은 비범위가 필요하면 중단한다.

## P4. 검증과 통합

- 목표: 전체 회귀, Docker browser 흐름과 최종 감사를 완료한다.
- 변경 범위: test, Evidence와 상태 문서.
- 검증: backend unit·PostgreSQL integration, frontend lint·build, Compose, browser,
  OSS·SBOM과 diff 감사를 수행한다.
- Rollback: 필수 환경 검증 실패 시 `VERIFIED`로 판정하지 않는다.
- 중단 조건: 기존 login·owner 경계가 회귀하면 중단한다.

## 공통 위험과 대응

- unique email과 `saveAndFlush` 충돌을 같은 `409`로 처리한다.
- signup만 공개하고 문서·검색·현재 사용자 API 정책은 바꾸지 않는다.

## Dependency 및 license 영향

- migration과 dependency는 추가하지 않는다.

## Branch와 통합 경계

- 실제 PR·CI·merge 기록은 Evidence에만 둔다.

## 계획 대비 주요 변경

- 구현 결과는 계획한 4단계 안에서 완료됐다.
