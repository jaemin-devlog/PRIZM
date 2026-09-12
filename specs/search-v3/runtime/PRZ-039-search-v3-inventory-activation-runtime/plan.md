# PRZ-039 Plan

## 구현 순서

1. V19에 verified inventory fingerprint column·형식 제약과 V2 active-version 변경 시 shadow pointer를
   안전하게 해제하는 호환 trigger를 forward-only로 추가한다.
2. DB inventory를 독립 load하는 JDBC repository와 canonical verifier를 구현한다.
3. full claim fencing 아래 READY와 같은-version activation service를 구현한다.
4. 실제 PostgreSQL fixture로 mismatch, stale Worker, rollback과 concurrency를 검증한다.
5. migration 기대값과 현재 architecture/status/Registry를 V19 source 사실에 맞춘다.
6. 집중 테스트, 전체 backend check, OSS readiness, SEALED guard와 최종 diff 감사를 수행한다.

## 예상 변경 범위

- `src/main/resources/db/migration/V19__add_verified_search_v3_inventory_fingerprint.sql`
- `src/main/java/com/prizm/search/v3/indexing/**`의 inventory/activation 전용 JDBC runtime
- `src/integrationTest/java/com/prizm/infrastructure/SearchV3InventoryActivationRuntimeTest.java`
- V19 때문에 바뀌는 기존 migration 기대값
- PRZ-039 문서, Registry와 현재 architecture/status 설명

기존 Search V2 service/repository, `document_chunks`, API, frontend, MCP와 dependency는 수정하지 않는다.

## 검증 계획

- PostgreSQL: PRZ-039 집중 test, V2 version 교체·active version 해제 호환, PRZ-038 fencing, PRZ-037 shadow migration,
  V1~V19 fresh migration
- unit/searchEvaluation: 기존 Search V2 Worker, PRZ-036 lifecycle/reuse, SEALED guard
- 전체 backend: `gradlew.bat check --no-daemon --dependency-verification=strict --rerun-tasks`
- 문서·배포 경계: OSS readiness와 `git diff --check`

실패한 필수 환경은 `NOT_RUN`으로 기록하고 Gate를 통과한 것으로 간주하지 않는다.
