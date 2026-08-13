# PRZ-011 — 문서 처리 진행 상태 UX Tasks

> **현재 상태:** `VERIFIED`
>
> 계약은 [Spec](spec.md), 계획 단계는 [Plan](plan.md), 실제 검증과 감사 결과는
> [Evidence](evidence.md)를 따른다.

## P1. 진행 상태 저장 계약

- [x] `T1` Spec과 acceptance criteria를 확정했다.
- [x] `T3` V15와 owner·claim-fenced 진행 상태 저장을 구현했다.
- [x] 단계와 진행 수치의 DB 갱신을 검증했다.

## P2. Backend 상태와 오류 API

- [x] `T4` 안전 실패 분류와 문서 API 확장을 구현했다.
- [x] retry count·다음 시각과 allowlist 오류를 검증했다.
- [x] 내부 오류가 API에 노출되지 않는지 확인했다.

## P3. Polling과 진행 UX

- [x] `T5` polling·단계·retry·오류 UX를 구현했다.
- [x] `COMPLETED` 전 조기 완료를 막고 terminal polling을 중지했다.
- [x] frontend unit·lint·build를 통과했다.

## P4. 검증과 재감사

- [x] `T6` backend unit·integration과 frontend 검증을 완료했다.
- [x] `T7` 실제 Compose·Ollama 문서 처리와 검색 회귀를 검증했다.
- [x] `T8` Evidence와 상태 문서를 갱신했다.
- [x] `T9` 최초 AUDIT blocking 2건을 수정하고 회귀를 다시 검증했다.
- [x] `T10` 재-AUDIT을 통과했다.
