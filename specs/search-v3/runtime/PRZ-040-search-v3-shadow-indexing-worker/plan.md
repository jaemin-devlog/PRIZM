# PRZ-040 Plan

## 구현 순서

1. V20에 claim-first manifest lifecycle을 forward-only로 추가한다.
2. PRZ-026 B3 구조 계약을 Search V3 shadow main package로 옮기고 TXT/PDF provenance를 보완한다.
3. model contract 확인, expected manifest 생성과 Passage·Child embedding plan을 구현한다.
4. full-claim-fenced inventory 전체 치환 저장을 구현한다.
5. Search V3 heartbeat, processor와 coordinator를 연결한다.
6. READY activation 연기·재개를 구현하되 inactive version에서는 Production pointer를 바꾸지 않는다.
7. 실제 PostgreSQL에서 성공·실패·stale/reclaim·retry·reindex를 검증한다.
8. PRZ-036~039와 Production Search V2 회귀, 전체 backend, OSS와 SEALED guard를 확인한다.
9. source·migration·문서 정합성을 감사하고 Gate 통과 시 PRZ-040 branch에만 commit·push한다.

## 예상 변경 범위

- `src/main/resources/db/migration/V20__*.sql`
- `src/main/java/com/prizm/search/v3/indexing/**`
- Search V3 구조 runtime용 main package
- PRZ-040 focused unit·PostgreSQL integration test
- V20 때문에 바뀌는 migration expectation
- PRZ-040 문서, Registry, architecture/status와 Search V3 안내

Production Search V2 query/service, `DocumentIndexingProcessor`, `document_chunks`, API, frontend, MCP와
dependency는 수정하지 않는다.

## 검증 계획

- PostgreSQL: PRZ-040 end-to-end, V20 migration, PRZ-039 activation, PRZ-038 fencing
- unit: 구조 parity, Worker heartbeat/coordinator, PRZ-036 lifecycle·reuse, SEALED guard
- Production 회귀: 기존 Search V2 Worker focused suite와 전체 backend `check`
- 배포 경계: OSS readiness, `git diff --check`, Production diff 감사

실행하지 못한 실제 Ollama BGE-M3와 OpenSQL 검증은 `NOT_RUN`으로 남긴다.
