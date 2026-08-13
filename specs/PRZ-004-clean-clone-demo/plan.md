# PRZ-004 — 안전한 clean-clone demo Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** `936e957132fcf54b5cee1f58d83f8d591e5786e2`
>
> 초기 후보 일부는 최종 계획 전에 만들어졌으므로 이 문서는 사후 conformance
> baseline이다. 실제 결과는 [Tasks](tasks.md)와 [Evidence](evidence.md)를 따른다.

## P1. 기준선과 인증 보호

- 목표: 기존 인증을 재사용하는 opt-in demo `USER` bootstrap을 만든다.
- 변경 범위: bootstrap properties·runner·conflict guard와 공용 BCrypt byte policy.
- 검증: demo와 `SYSTEM_ADMIN` 동시 활성화 거부, 기존 email fail-closed와 정상 JWT를
  확인한다.
- Rollback: bootstrap을 끄고 기존 인증만 유지한다.
- 중단 조건: user schema·JWT·owner query 변경이 필요하면 중단한다.

## P2. Clean-clone 실행 도구

- 목표: clone 뒤 안전한 env·fixture·Compose·smoke 흐름을 제공한다.
- 변경 범위: Node 표준 라이브러리 기반 준비·fixture·wrapper·verifier script.
- 검증: random project, explicit port, shell override 제거, loopback URL, redirect 거부,
  version polling과 검색 allowlist를 확인한다.
- Rollback: script와 `.env.example` 변경을 함께 되돌린다.
- 중단 조건: credential value 출력, 공용 URL 또는 예상하지 않은 검색 결과를
  허용해야 하면 중단한다.

## P3. 공급망과 문서

- 목표: 재현된 npm finding만 교정하고 공개 문서를 동기화한다.
- 변경 범위: exact npm override, lockfile, frontend SBOM·checksum, Quickstart와
  상태 문서.
- 검증: full·production audit, component license와 OSS readiness를 확인한다.
- Rollback: lockfile·SBOM·문서를 함께 이전 상태로 되돌린다.
- 중단 조건: 감사되지 않은 dependency나 model identity 변경이 필요하면 중단한다.

## P4. 검증과 통합

- 목표: 서로 격리된 두 clean clone의 API·browser 흐름과 전체 회귀를 검증한다.
- 변경 범위: backend·frontend·Node test, Compose, Evidence와 CI.
- 검증: 두 project·port·volume, demo login, TXT·PDF upload, `ACTIVE`, 검색과 browser
  흐름을 실행한다.
- Rollback: 필수 Gate 실패 시 통합하지 않는다.
- 중단 조건: owner·credential·license finding이 blocking이면 중단한다.

## 공통 위험과 대응

- `.env`와 credential은 ignored local file에만 두고 값을 출력하지 않는다.
- demo account도 기존 `USER` 인증·인가와 owner 경계를 사용한다.
- Flyway, document·search·ingestion·cleanup과 frontend application source는
  변경하지 않는다.

## Dependency 및 license 영향

- 원격 main에서 재현한 npm high finding만 exact version으로 교정한다.
- Ollama `0.32.3`과 `bge-m3` model manifest는 변경하지 않는다.

## Branch와 통합 경계

- 계획 전 후보는 과거 이력으로 보존하며 사전 승인으로 표현하지 않는다.

## 계획 대비 주요 변경

- shell override fail-closed, URL port normalization과 CORS 연결을 감사 뒤 보완했다.
- 두 번째 clone의 빈 목록 UI 직접 관찰은 비차단 `NOT_RUN`으로 남겼다.
