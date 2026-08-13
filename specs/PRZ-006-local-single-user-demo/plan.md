# PRZ-006 — 로컬 보관함 빠른 시작 Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** `b370cd91f93bd617abebd7afce56fc495eb7b161`
>
> 구현 전 계획을 보존한다. 실제 결과는 [Tasks](tasks.md)와
> [Evidence](evidence.md)를 따른다.

## P1. 선택적 빠른 시작 설계

- 목표: 기존 Docker와 일반 로그인을 유지하면서 로컬 진입 절차를 줄인다.
- 변경 범위: Compose의 opt-in 환경 변수와 일반 실행 기본값 `false`.
- 검증: 두 진입 모드가 같은 JWT·owner 계약을 사용하는지 확인한다.
- Rollback: opt-in 설정과 local-session 경로를 함께 비활성화한다.
- 중단 조건: 비밀값 자동 생성, 별도 Compose나 migration이 필요하면 중단한다.

## P2. 정상 JWT와 owner 경계

- 목표: local `USER`를 생성·재사용하고 기존 JWT를 발급한다.
- 변경 범위: auth config·controller·service·DTO와 backend test.
- 검증: 활성 상태·role, DB 재검증과 두 사용자 owner isolation을 확인한다.
- Rollback: auth 변경을 되돌리고 기존 로그인만 유지한다.
- 중단 조건: 사용자 ID 하드코딩이나 Spring Security 우회가 필요하면 중단한다.

## P3. 진입 화면과 문서

- 목표: opt-in 실행에서만 `PRIZM 시작하기`를 표시한다.
- 변경 범위: `App.tsx`, auth API, style과 README·Quickstart.
- 검증: availability 실패 시 일반 로그인 유지, frontend lint·build와 browser 흐름을
  확인한다.
- Rollback: frontend 분기를 제거하고 로그인 화면을 유지한다.
- 중단 조건: 일반 실행에 local-demo가 노출되면 중단한다.

## P4. 검증과 통합

- 목표: backend·PostgreSQL·frontend·Docker 검증과 독립 감사를 완료한다.
- 변경 범위: test와 Evidence·상태 문서.
- 검증: unit·integration, lint·build, Compose·browser, diff와 audit을 수행한다.
- Rollback: 필수 검증 실패 시 `VERIFIED`로 판정하지 않는다.
- 중단 조건: OpenSQL 미실행을 `PASS`로 기록하면 중단한다.

## 공통 위험과 대응

- 기존 `.env`, host Ollama, port topology와 일반 로그인 계약을 유지한다.
- 원문 password는 저장·로그·응답하지 않는다.
- 회원가입, 비밀번호 재설정과 다중 사용자 배포는 제외한다.

## Dependency 및 license 영향

- Flyway, dependency, image와 model 배포 경계는 바꾸지 않는다.

## Branch와 통합 경계

- 실제 GitHub 통합 결과는 Evidence에만 기록한다.

## 계획 대비 주요 변경

- audit에서 availability 실패 시 일반 로그인 고정 문제와 문서 모순을 교정했다.
