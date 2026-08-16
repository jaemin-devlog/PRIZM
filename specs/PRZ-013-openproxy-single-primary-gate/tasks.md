# PRZ-013 — OpenProxy 단일 Primary SQL Gate Tasks

> **현재 상태:** `VERIFIED`

- [x] `T1` 기존 OpenProxy 설치·unit·포트·backend·인증 구조를 확인했다.
- [x] `T2` config를 백업하고 `prizm_app` 단일 pool로 최소 수정했다.
- [x] `T3` `:6432` LISTEN과 OpenProxy→Primary SQL SELECT/WRITE를 검증했다.
- [x] `T4` Flyway direct/runtime proxy 분리로 focused PRIZM E2E를 검증했다.
- [x] `T5` OpenProxy 재시작 후 새 SQL 연결 회복을 검증했다. 지속 실행
  application continuity는 `NOT_RUN`이다.
- [x] `T6` Evidence·상태 문서와 최종 diff를 감사했다.
