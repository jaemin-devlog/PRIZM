# PRZ-016 Search Performance V2 Plan

## P6 Retrieval Architecture Shadow Benchmark

### 변경 경계

- `src/searchEvaluation/java/com/prizm/search/evaluation/`에 외부 PostgreSQL·Ollama를 읽는
  P6 전용 runner, channel diagnostics와 literal gate를 둔다.
- `src/test/java/com/prizm/search/evaluation/`에는 RRF 재사용·literal anchor/gate·owner/ACTIVE·
  score/distance 불변 계약의 실행 가능한 test를 둔다.
- `specs/PRZ-016-search-performance-v2/p6-retrieval-shadow/`에 stress dataset/ground truth/freeze,
  P6-A/P6-B raw·summary 결과와 evidence를 둔다.
- `src/main`, Flyway, API, runtime configuration, dependency와 frontend는 수정하지 않는다.

### 데이터 흐름

1. 고정 USER와 ACTIVE 2문서·18청크를 확인한다.
2. Q0을 한 번 임베딩하여 dense Top20과 lexical Top20을 독립 조회한다.
3. L1은 lexical rank, H1은 chunk ID dedup + RRF `1/(60+rank)` 합으로 정렬한다.
4. 각 mode에서 기존 production profile과 P3 순차 variant를 평가 전용 orchestration으로 재사용한다.
5. H2는 H1 candidate와 같은 owner/document/ACTIVE version의 bounded expansion에 모든 strong
   literal anchor가 있는지 확인한다.
6. 원래 candidate의 ID/score/distance를 유지한 채 diagnostic JSON만 기록한다.

### 순서와 freeze

1. P6-A runner와 D0/L1/H1 contract tests를 구현한다.
2. D0/L1/H1을 실행해 P6-A 결과를 먼저 저장한다.
3. 실제 ACTIVE corpus를 읽어 24~32개 새 stress query와 ground truth를 작성·직접 검증한다.
4. dataset, ground truth, production search source를 SHA-256으로 동결한다.
5. 그 뒤에만 H2 literal gate와 tests를 구현한다.
6. P6-B와 전체 dataset/regression/격리/latency 검증을 실행한다.

### 안전·ownership·호환성

- 모든 SQL은 document/version/chunk owner가 동일하고 document.active_version_id와 version
  `ACTIVE`를 동시에 만족해야 한다.
- benchmark DB 사용은 읽기 전용이다. migration/index/schema/data를 변경하지 않는다.
- score/distance와 API response 모델은 변경하지 않는다.
- production scheduler/worker는 P6 runner에서 비활성화한다.
- dependency, license, redistribution과 SBOM 경계는 변경하지 않는다.

### 실패·중단·rollback

- production hash, owner/ACTIVE, API 계약이 달라지면 즉시 중단하고 P6를 FAIL로 기록한다.
- stress freeze 뒤 입력 hash가 달라지면 H2 실행을 거부한다.
- required environment가 없으면 해당 검증은 `NOT_RUN`이며 P6를 성공 판정하지 않는다.
- P6 변경은 새 evaluation/spec 파일만 제거하면 되돌릴 수 있으며 production에는 rollback이 없다.

### 검증 명령

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
.\gradlew.bat p6ShadowBenchmark --no-daemon --rerun-tasks
git diff --check
```

PostgreSQL `15433`, 실제 Ollama `bge-m3` `11434`, 현재 source의 production API를 구분해
기록한다. frontend·Compose build는 production/UI 변경이 없으므로 적용 대상이 아니다.

### Git

재번호화 뒤 `PRZ-016-search-performance-v2` branch의 기존 P0~P6 변경을 보존한다.
사용자 지시에 따라 P6에서는 commit, push, PR과 branch 정리를 수행하지 않는다.

## GPT-J1 Evidence Judge Shadow Spike

GPT-J1의 구현·검증 계획은
[전용 Plan](gpt-evidence-judge-shadow/plan.md)에서 관리한다. P6의 `NO_GO` 결과와
P4 production source를 그대로 기준선으로 사용하며 검색·API runtime에는 연결하지 않는다.

## P7-A Cross-Document Dataset Freeze

검색 개선이나 측정 없이 새로운 synthetic corpus와 pre-search Ground Truth만 만드는 절차는
[P7-A Plan](p7-cross-document-generalization/plan.md)에서 관리한다. P7-B는 새 Codex 세션에서
frozen hash 검증 뒤 수행하며 P7-A에서는 검색·benchmark를 실행하지 않는다.

v1 PDF의 문서 밀도 부족은 frozen v1을 수정하지 않고
[P7-A v2 Plan](p7-cross-document-generalization-v2/plan.md)으로 대체한다. P7-B는 v2 manifest만
권위 있는 입력으로 사용한다.
