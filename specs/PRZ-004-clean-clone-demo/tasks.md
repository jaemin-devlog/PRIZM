# PRZ-004 — 안전한 clean-clone demo Tasks

> **현재 상태:** `VERIFIED`
>
> 상세 계약과 결과는 [Spec](spec.md), [Plan](plan.md),
> [Evidence](evidence.md)를 함께 따른다.

## P1. 기준선과 인증 보호

- [x] `T1` 원격 main·환경·후보를 독립 확인했다.
- [x] `T2` 초기 후보를 Spec·Plan과 사후 대조했다.
- [x] `T3` demo `USER`, bootstrap 충돌과 BCrypt 보안 계약을 구현했다.

## P2. Clean-clone 실행 도구

- [x] `T4` 안전한 env, 합성 fixture와 API smoke 도구를 구현했다.
- [x] shell override, loopback URL, redirect와 allowlist를 fail-closed로 검증했다.

## P3. 공급망과 문서

- [x] `T5` npm high finding을 교정하고 SBOM·license를 동기화했다.
- [x] `T6` Quickstart와 상태 문서의 source·환경 경계를 갱신했다.

## P4. 검증과 통합

- [x] `T7` 최종 source 자동 검증은 `339 PASS / 18 SKIP / 0 FAIL`이었다.
- [x] `T8` 두 fresh clone의 API·browser 흐름을 검증했다.
- [x] 두 번째 빈 목록 UI 직접 관찰은 `NOT_RUN` 비차단 finding으로 남겼다.
- [x] `T9` 독립 보안·ownership·license 감사를 통과했다.
- [x] `T10` PR #25, CI 6건과 merge `1f9a5ad`를 Evidence에 기록했다.
