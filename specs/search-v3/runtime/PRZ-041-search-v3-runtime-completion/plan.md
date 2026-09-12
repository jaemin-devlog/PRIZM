# PRZ-041 Plan

## 구현 순서

1. PRZ-034~040과 V18~V20, V2 scheduler·embedding 구현을 감사한다.
2. active DocumentVersion을 current Search V3 계약의 `PENDING` job으로 만드는 fenced dispatch를 구현한다.
3. 일반 claim scheduler와 exact-token recovery 후 즉시 처리 경로를 연결한다.
4. ACTIVE+COMPLETED generation 전용 Passage Top20 repository를 구현한다.
5. 저장된 Child vector로 Top5 Passage 내부 `CHILD_DENSE_V1`을 적용한다.
6. PRZ-028/029의 deterministic typed validation·selection 의미를 runtime에 옮긴다.
7. PostgreSQL에서 dispatch·recovery·indexing·query·owner·lifecycle invariant를 검증한다.
8. SEALED가 아닌 fixture로 실제 Ollama BGE-M3 smoke를 시도한다.
9. PRZ-038~040, V2와 전체 backend 회귀를 실행하고 Gate를 감사한다.

## 변경 범위

- `src/main/java/com/prizm/search/v3/**`
- Search V3 runtime 전용 unit·PostgreSQL integration test
- Search V3 worker 설정
- PRZ-041 문서, Registry, architecture/status와 Search V3 안내

Production Search V2 query/service/API, `document_chunks`, frontend, MCP와 dependency는 수정하지 않는다.
기존 Flyway migration도 수정하지 않는다.

## 검증 계획

- PostgreSQL: 자동 job 생성, claim/recovery, PRZ-040 E2E, ACTIVE-only query, owner 격리, reindex
- unit: scheduler/coordinator, Child same-Passage 정렬, typed parser·selector, deterministic tie
- 실제 Ollama: model digest·1024차원·index/query smoke
- 회귀: PRZ-038~040, V1~latest migration, V2 Worker/query, 전체 backend `check`
- 경계: SEALED guard, OSS readiness, `git diff --check`, Production diff 감사
