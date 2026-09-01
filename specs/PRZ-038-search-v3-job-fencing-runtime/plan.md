# PRZ-038 Plan

## 1. ORIENT / SPEC

- 기준 SHA, origin parity와 clean working tree를 확인한다.
- PRZ-036/037, V18과 기존 Search V2 claim·lease·failure·recovery를 감사한다.
- V3 full identity, attempt, DB time, recovery token과 PRZ-039 경계를 먼저 고정한다.

## 2. IMPLEMENT

- V18 전용 JDBC repository와 service, claim/recovery DTO를 추가한다.
- claim·renew·retry·terminal failure·recovery lock·reclaim을 full identity로 fence한다.
- 기존 V2 source, migration, 검색과 API는 수정하지 않는다.

## 3. VERIFY / AUDIT

- 실제 PostgreSQL에서 상태 전이, owner lineage, 동시 claim과 recovery 경쟁을 검증한다.
- 기존 V2 Worker, V18 migration, PRZ-036 lifecycle·SEALED guard와 전체 backend를 회귀 검증한다.
- diff scope, docs/Registry, SEALED/OpenSQL 경계를 감사하고 판정한다.

## 4. COMMIT / BACKUP

- `JOB_FENCING_READY` Gate를 통과한 경우에만 일반 commit을 만든다.
- PRZ-038 branch에만 push하고 PR, `refactor/search-v3`/`main` merge는 하지 않는다.
