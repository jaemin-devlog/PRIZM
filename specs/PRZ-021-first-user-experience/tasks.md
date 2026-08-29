# PRZ-021 — Fresh Clone 첫 사용자 경험 정합화 Tasks

> **현재 상태:** `VERIFIED`

## P0. ORIENT / SPEC / PLAN

- [x] `T1` main/원격 SHA, clean worktree와 다음 PRZ ID를 확인한다.
- [x] `T2` TXT action, Quickstart, clean-clone owner와 MCP warning 원인을 재현·감사한다.
- [x] `T3` 검색/auth/owner/schema를 바꾸지 않는 R1∼R6과 비범위를 고정한다.
- [x] `T4` PRZ-021 Spec과 Plan Gate를 통과한다.

## P1. Frontend

- [x] `T5` PAGE/TEXT_CHUNK source action을 testable component로 분리한다.
- [x] `T6` EvidencePage에서 TXT 결과를 기존 document detail route로 연결한다.
- [x] `T7` 선택적 version deep link와 안전한 owner-detail version 선택을 구현한다.
- [x] `T8` action과 route parser/round-trip/fallback test를 추가한다.

## P2. Quickstart / owner 흐름

- [x] `T9` 일반 Quickstart의 folder/status/Compose/PATH/Engine/health 설명을 교정한다.
- [x] `T10` 자동 API USER와 browser USER의 same-owner 검증 절차를 분리한다.
- [x] `T11` 현재 제품 상태와 Registry를 실제 변경 범위로 갱신한다.

## P3. MCP

- [x] `T12` warning의 upstream 근거와 PRIZM 비수정 판정을 Evidence에 기록한다.
- [x] `T13` 공식 Java SDK focused test를 재실행한다.
- [x] `T14` 공식 MCP Inspector로 fresh live endpoint initialize/list/call을 검증한다.

## P4. VERIFY / AUDIT

- [x] `T15` focused·전체 frontend unit/typecheck/lint/build를 통과한다.
- [x] `T16` clean-clone script test를 통과한다.
- [x] `T17` 새 project/volume에서 USER1 TXT/PDF navigation E2E를 통과한다.
- [x] `T18` USER2 isolation, down/up persistence와 console `ERROR` 0을 확인한다.
- [x] `T19` Markdown/local links, readiness, diff와 secret/unrelated-change 감사를 통과한다.
- [x] `T20` Evidence와 최종 판정을 실제 결과로 갱신한다.
