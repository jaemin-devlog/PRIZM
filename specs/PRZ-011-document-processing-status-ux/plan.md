# PRZ-011 — 문서 처리 진행 상태 UX Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** PRZ-011 구현 전 `main`
>
> 구현 전 접근과 Gate를 보존한다. 실제 결과는 [Tasks](tasks.md)와
> [Evidence](evidence.md)를 따른다.

## P1. 진행 상태 저장 계약

- 목표: 실제 처리 단계를 owner·claim-fenced DB 상태로 관찰한다.
- 변경 범위: forward-only V15, ProcessingJob 진행 필드와 progress repository.
- 검증: 단계 전이, completed·total과 stale worker 거부를 확인한다.
- Rollback: 적용 migration은 수정하지 않고 forward-only로 교정한다.
- 중단 조건: owner 또는 claim fencing을 약화해야 하면 중단한다.

## P2. Backend 상태와 오류 API

- 목표: polling에 필요한 진행·retry·안전 실패 정보를 기존 문서 API에 추가한다.
- 변경 범위: DTO·service와 allowlist 오류 분류.
- 검증: `PENDING`, `PROCESSING`, `RETRY_WAIT`, `FAILED`, `COMPLETED`, retry count와
  다음 시각, 내부 오류 비노출을 확인한다.
- Rollback: additive 응답 필드를 제거하되 기존 API를 유지한다.
- 중단 조건: credential·stack trace·원본 내부 경로가 노출되면 중단한다.

## P3. Polling과 진행 UX

- 목표: 약 2초 polling으로 단계·수치·retry·failure를 표시한다.
- 변경 범위: frontend polling, 상태 우선순위, progress와 오류 UI.
- 검증: terminal 상태에서 polling 중지, 저장 중 조기 완료 방지와 retry 표시를
  확인한다.
- Rollback: polling·progress UI를 제거하고 기존 상태 표시를 유지한다.
- 중단 조건: `COMPLETED` 전 완료로 표시하거나 server 오류를 무시하면 중단한다.

## P4. 검증과 재감사

- 목표: 자동·실환경 검증과 audit finding 교정을 완료한다.
- 변경 범위: unit·integration, frontend test·lint·build, Compose·Ollama·browser,
  Evidence와 상태 문서.
- 검증: 문서 처리·검색 회귀와 최초 AUDIT finding 2건 수정 뒤 재-AUDIT을 수행한다.
- Rollback: blocking finding이나 필수 test 실패 시 `VERIFIED`로 판정하지 않는다.
- 중단 조건: 기존 retry·activation·search 계약이 회귀하면 중단한다.

## 공통 위험과 대응

- `COMPLETED`일 때만 완료로 표시한다.
- 실패·retry·저장 중 상태를 진행률보다 먼저 판단한다.
- 전체 chunk 수 확정 전 percentage는 `null`로 유지한다.

## Dependency 및 license 영향

- 새 dependency는 추가하지 않는다.
- V1–V14는 수정하지 않는다.

## Branch와 통합 경계

- 문서 처리 상태와 UI 외 검색 profile·평가 source는 변경하지 않는다.

## 계획 대비 주요 변경

- 최초 AUDIT의 blocking 2건을 수정하고 같은 범위를 재검증했다.
