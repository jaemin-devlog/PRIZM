# PRZ-020 — 인증 bootstrap 정리와 signup/login 단일화 Tasks

> **현재 상태:** `VERIFIED`

## P0. ORIENT / SPEC / PLAN

- [x] `T1` 현재 인증·bootstrap·SYSTEM_ADMIN·clean-clone 생성/호출 경로를 감사한다.
- [x] `T2` 관리자 API·UI·service가 각각 0개임을 확인한다.
- [x] `T3` `REMOVE_SYSTEM_ADMIN_ROLE`과 forward migration 전략을 승인한다.
- [x] `T4` R1–R7, 비범위, 검증과 중단 조건을 고정한다.

## P1. 인증 모델과 migration

- [x] `T5` 두 bootstrap runner·properties·guard와 전용 test를 제거한다.
- [x] `T6` BCrypt 정책을 공용 인증 보안 package로 이동한다.
- [x] `T7` `SYSTEM_ADMIN` enum/type와 bootstrap 전용 repository API를 제거한다.
- [x] `T8` legacy 관리자 행을 비활성 USER로 보존하는 V17과 migration test를 추가한다.
- [x] `T9` auth/JWT/MCP/PostgreSQL test를 단일 USER 모델에 맞게 재설계한다.

## P2. clean-clone

- [x] `T10` 환경 준비에서 bootstrap과 사용자 credential을 제거한다.
- [x] `T11` verifier를 메모리 credential signup → login 흐름으로 전환한다.
- [x] `T12` script unit test를 새 인증 흐름과 secret 비노출 계약에 맞춘다.

## P3. 설정과 문서

- [x] `T13` `.env.example`과 `application.yml`의 bootstrap 설정을 제거한다.
- [x] `T14` README·Quickstart를 일반 사용자 signup/login 기준으로 정리한다.
- [x] `T15` Architecture·Project Status·Roadmap·AGENTS와 현재 showcase를 현행화한다.
- [x] `T16` PRZ-004/006/007과 archive의 역사 기록이 변경되지 않았는지 확인한다.
- [x] `T17` Registry와 PRZ-020 Evidence를 실제 상태로 갱신한다.

## P4. VERIFY / AUDIT / INTEGRATE

- [x] `T18` focused auth/JWT/MCP와 clean-clone script test를 통과한다.
- [x] `T19` PostgreSQL auth·migration·owner isolation과 전체 integration을 통과한다.
- [x] `T20` clean Compose signup/login TXT/PDF smoke를 통과한다.
- [x] `T21` frontend lint/build와 전체 backend unit을 통과한다.
- [x] `T22` Markdown·환경 변수·현재/역사 ref·secret·diff를 감사한다.
- [x] `T23` OpenSQL `NOT_RUN`과 local-only integration 경계를 정확히 기록한다.
