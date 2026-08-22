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

## State Freeze와 PR 감사 보완

- P7-B 완료 뒤 frozen query·corpus·ground truth는 diagnostic/historical 자료로만 보존한다.
- 검색 순위·threshold·retrieval은 추가 조정하지 않는다.
- PR 감사에서 확인된 완료 경험 질의의 빈 결과 상태만 기존 `NO_EVIDENCE` 계약에 맞춘다.
- 동일 identifier guard의 일반 질의 `NO_RELEVANT_RESULTS` 계약은 유지한다.
- frozen 파일의 SHA를 바꾸지 않고, 기존 EOF 빈 줄은 정확한 파일에 한정한 Git whitespace
  attribute로만 처리한다.
- focused SearchService test와 실패했던 PostgreSQL integration test를 먼저 실행한 뒤,
  전체 unit·integration과 `git diff --check`를 수행한다.
- P7-B 결과에 맞춘 검색 tuning, 새 benchmark, 모델 inference는 수행하지 않는다.

## P15 PDF Document Confirmation UX

### 변경 경계

- `frontend/src/App.tsx`의 EvidencePage에 PDF page evidence용 viewer state와
  `문서에서 보기` 버튼을 추가한다.
- 현재 `getDocumentPdf` API, JWT Authorization header, Blob URL, `pdf-viewer-*` modal
  styles를 재사용한다.
- 필요한 경우 frontend presentation test와 style만 갱신한다.
- `src/main`, SearchService, search DTO/API, Flyway, ingestion, embedding, dependency는
  수정하지 않는다.

### 데이터 흐름

1. 검색 결과의 evidence source가 `PAGE`인지 확인한다.
2. 카드 버튼은 document ID, version ID, evidence page index와 document title을 viewer에
   전달한다.
3. viewer는 기존 authenticated original API에서 PDF Blob을 가져온다.
4. `blob:` URL에 `#page=N&zoom=page-width`를 붙여 iframe에 표시한다.
5. 원본 요청이 실패해도 검색 카드와 결과 state는 바꾸지 않는다.

### 보안·실패·rollback

- URL에는 서버 저장 경로나 access token을 넣지 않는다. API 호출은 기존 Bearer token과
  backend의 owner-scoped `findByIdAndOwnerUserIdAndDocumentId` 경계를 그대로 쓴다.
- PDF.js가 없어 snippet highlight는 구현하지 않는다. page fragment가 브라우저에서 지원되지
  않아도 PDF 자체는 열려야 한다.
- 401/403은 기존 `onSessionExpired`를 호출하고, 일반 오류는 viewer 안에서만 안내한다.
- rollback은 P15 frontend 변경만 되돌리는 것이다. 검색 결과 API 및 backend source는
  변경하지 않는다.

### 검증

```powershell
npm --prefix frontend run test:unit
npm --prefix frontend run lint
npm --prefix frontend run build
.\gradlew.bat test --tests com.prizm.document.controller.DocumentThumbnailControllerTest --no-daemon
git diff --check
```

Docker가 준비된 경우 실제 PDF 검색 결과의 page fragment와 카드 결과 불변성을 브라우저에서
확인한다. PDF.js가 없는 현재 dependency 상태에서는 highlight를 `NOT_IMPLEMENTED`로 기록한다.

## P16 Literal Candidate Phase A

Production 검색을 변경하지 않는 D0 parity와 literal candidate union 실험은
[P16 Plan](p16-literal-candidate-phase-a/plan.md)에서 관리한다. P6의 FTS·RRF·literal gate를
재사용하지 않고, BGE-M3 Dense Top20과 exact-boundary literal Top20을 chunk identity로만
합친다. Phase B, production 적용, migration, frontend, PRZ-009·PRZ-017 변경과 Git 통합은
수행하지 않는다.
