# PRIZM 대표 문제 해결 사례

이 문서는 현재 소스, Flyway migration과 자동 테스트로 확인되는 설계 선택의
배경과 트레이드오프를 정리한다. 전체 구성 요소와 데이터 흐름은
[Architecture](../architecture.md)에서 먼저 확인한다.

## 1. 사용자별 문서와 벡터 검색 격리

### 문제

서비스 계층에서만 소유자를 확인하면 새 repository나 SQL 경로에서 검사가 빠질 수 있다. pgvector 거리 계산 뒤 결과를 걸러내면 다른 사용자의 청크가 이미 후보 집합에 들어간다.

### 해결

- V8에서 document, version, chunk, processing job에 `owner_user_id`를 추가하고 상·하위 owner가 일치하는 composite FK를 적용했다.
- 소유자를 알 수 없는 legacy 문서를 임의 사용자에게 귀속하지 않고 migration을 중단한다.
- 목록·상세·관리 API는 owner-scoped repository를 사용한다.
- 벡터 검색 SQL은 거리 계산 후보 단계에서 document·version·chunk owner, ACTIVE 상태와 `active_version_id`를 함께 검사한다.
- 현재 제품 역할은 `USER` 하나이며 개인 문서·검색 API에 역할 기반 우회 권한을 두지 않는다.

### 검증과 트레이드오프

인증 통합 테스트는 두 사용자의 목록·상세·검색 격리와 타 사용자 접근 거부를 확인한다. DB 관계와 모든 생성·조회 경로에서 owner를 전달해야 하므로 구현 복잡도는 늘지만, 응답 직전 필터보다 누락 경로를 줄인다.

근거:

- [V8 owner migration](../../src/main/resources/db/migration/V8__add_document_ownership.sql)
- [문서 조회 서비스](../../src/main/java/com/prizm/document/service/DocumentQueryService.java)
- [벡터 검색 SQL](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java)
- [인증·격리 통합 테스트](../../src/integrationTest/java/com/prizm/infrastructure/AuthenticationIntegrationTest.java)
- [현재 기준 Evidence 감사](../../specs/PRZ-022-backend-reliability-evidence/evidence.md#3-user-owner-isolation)

## 2. 긴 비동기 처리에서 중복 선점과 늦은 완료 차단

### 문제

PDF 추출과 Ollama 호출 동안 DB 행 잠금을 유지하면 다른 작업을 막는다. 반대로 짧게만 선점하면 lease 만료 후 회수된 작업에 이전 Worker가 늦게 완료를 반영할 수 있다.

### 해결

- `FOR UPDATE SKIP LOCKED`로 짧은 transaction에서 작업 하나를 선점한다.
- DB 시간 기반 `lease_expires_at`과 매 선점마다 증가하는 `claim_version`을 사용한다.
- 전체 파일 읽기·PDF 추출·임베딩 구간을 lease duration의 1/3 주기 heartbeat로 덮는다.
- heartbeat, retry, 실패와 완료 갱신은 job ID·상태·claim version이 모두 일치할 때만 허용한다.
- 완료 transaction은 청크 교체, version ACTIVE, document의 `active_version_id`, job COMPLETED를 함께 확정한다.
- 새 version이 실패하면 기존 ACTIVE version과 검색 결과를 유지한다.

### 검증과 트레이드오프

단위·PostgreSQL 통합 테스트가 동시 claim, lease 회수, stale 완료 거부와 원자적 활성화를 검증한다. 상태 전이와 테스트 경우의 수가 늘고 heartbeat가 진행 중 외부 호출 자체를 취소하지는 못하므로, 반환 뒤 claim을 다시 확인해야 한다.

근거:

- [작업 claim repository](../../src/main/java/com/prizm/ingestion/repository/ProcessingJobClaimRepository.java)
- [lease heartbeat](../../src/main/java/com/prizm/ingestion/service/WorkerLeaseHeartbeat.java)
- [원자적 완료](../../src/main/java/com/prizm/ingestion/service/IndexingCompletionService.java)
- [Worker 통합 테스트](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java)
- [현재 기준 Evidence 감사](../../specs/PRZ-022-backend-reliability-evidence/evidence.md#2-비동기-worker-correctness)

## 3. DB transaction 밖의 원본 파일을 안전하게 정리

### 문제

DB rollback 뒤 원본 보상 삭제도 실패하면 고아 파일이 남는다. 반대로 commit 결과가 불확실한 `STATUS_UNKNOWN`에서 삭제하면 정상 DB row가 참조하는 원본을 잃을 수 있다. 지연 삭제 시 상대 경로 이탈, symlink와 부모 디렉터리 교체도 방어해야 한다.

### 해결

- 명확한 `STATUS_ROLLED_BACK`에서만 동기 보상 삭제를 시도하고 `STATUS_UNKNOWN`에서는 원본을 보존한다.
- 삭제 실패는 원래 rollback과 분리된 `REQUIRES_NEW` transaction으로 V12 cleanup job에 멱등 등록한다.
- V13 Worker는 짧은 claim, lease, fencing, 1·5·15분 backoff와 만료 recovery를 사용한다.
- 실제 삭제는 DB transaction 밖에서 수행하고, 삭제 뒤 완료 갱신 실패는 lease recovery와 파일 없음 멱등 성공으로 수렴한다.
- 지원 filesystem에서는 storage root부터 `SecureDirectoryStream` descriptor를 따라가며 `NOFOLLOW_LINKS`로 확인하고 최종 파일을 삭제한다. 미지원 filesystem은 경로 기반 fallback 없이 fail-closed한다.

### 검증과 트레이드오프

단위·PostgreSQL·filesystem 테스트가 transaction 상태 분리, 독립 cleanup 등록, 동시 claim, stale fencing, retry/recovery와 descriptor-relative 삭제를 검증한다. fail-closed는 안전성을 우선하지만 지원하지 않는 filesystem에서 자동 cleanup이 멈출 수 있어 운영 문서와 모니터링이 필요하다.

근거:

- [업로드 보상 처리](../../src/main/java/com/prizm/document/service/DocumentUploadService.java)
- [cleanup 등록 서비스](../../src/main/java/com/prizm/cleanup/service/FileCleanupJobService.java)
- [cleanup coordinator](../../src/main/java/com/prizm/cleanup/service/FileCleanupCoordinator.java)
- [안전한 파일 삭제](../../src/main/java/com/prizm/infrastructure/storage/LocalFileStorage.java)
- [V12](../../src/main/resources/db/migration/V12__add_file_cleanup_jobs.sql), [V13](../../src/main/resources/db/migration/V13__add_file_cleanup_worker_fields.sql)
- [현재 기준 Evidence 감사](../../specs/PRZ-022-backend-reliability-evidence/evidence.md#4-db--filesystem-cleanup-실패-복구)

## 4. 검색 개선 수치와 일반화 실패를 분리해 해석

### 문제

development dataset에서 좋아진 수치를 현재 제품의 일반화 정확도로 소개하면 unseen
질문이나 독립 사용자 문서에서 확인된 실패가 가려진다. 실패한 shadow 실험까지 현재
Production 검색에 섞으면 제3자가 실제 동작을 재현하기도 어렵다.

### 해결

- P0와 P4 development, P5 frozen holdout, P7-B independent corpus를 별도 단계로 보존했다.
- 단계별 원시 JSON에서 Top1·Recall@5·MRR@5·Negative FPR을 다시 계산하고 freeze hash를
  확인하는 스크립트를 제공한다.
- P4 Top1 82.14%를 현재 정확도라고 부르지 않고 P5와 P7-B의 `FAIL` 판정을 함께 제시한다.
- PostgreSQL FTS·RRF·Judge·NLI shadow가 현재 Production 검색 경로로 승격되지 않았는지도
  source에서 따로 확인한다.

### 검증과 트레이드오프

동결 원시 결과와 기록된 summary는 일치했다. 다만 이번 검증의 범위는 과거 결과의
무결성과 현재 source 경로까지다. 현재 `main`에서 Ollama 검색을 다시 실행한
generalization benchmark는 아니다. 현재 정확도를 주장하려면 corpus·질문·model·source를
새로 freeze한 뒤 독립 실행해야 한다.

근거:

- [PRZ-016 검색 연구 기록](../../specs/PRZ-016-search-performance-v2/README.md)
- [PRZ-022 검색 재계산 결과](../../specs/PRZ-022-backend-reliability-evidence/results/search.json)
- [PRZ-022 최종 판정](../../specs/PRZ-022-backend-reliability-evidence/evidence.md#1-검색-품질일반화)

## 5. 검색 규칙을 덧붙이지 않고 근거 구조부터 다시 설계

### 문제

`v1.0.0`의 검색은 문서를 800자 단위로 자르고 앞 조각과 120자를 겹쳤다. 구현은
단순했지만 서로 다른 경험이 한 검색 조각에 함께 들어가 사용자에게 보여 줄 근거의 경계가
흐려질 수 있었다. 개발용 데이터에서 좋아진 방법도 처음 보는 사용자 문서와 질문에서
실패했다. 실패 사례마다 규칙을 보태면 특정 문서와 질문에 맞춰질 위험이 있었고, 이미
릴리스한 검색을 곧바로 교체해서는 공정한 비교도 할 수 없었다.

### 해결

- Search V3를 구현하기 전에 데이터를 개발용, 조정용, 최종 검증용으로 나눴다. 같은
  사용자, 원문 사실, 문서 양식 계보가 평가 구간을 넘지 않게 했고 정답은 조각 ID가 아니라
  실제 원문 위치로 정의했다. 최종 검증 데이터는 검색에 사용하지 않고 봉인했다.
- 원문 근거인 `EvidenceChild`와 검색 단위인 `RetrievalPassage`를 분리했다. 화면에 보여 줄
  근거는 작고 정확하게 보존하고, 검색할 때만 같은 경험 안의 인접 근거를 묶었다. 서로 다른
  경험은 합치지 않았다.
- 숫자·날짜·버전 조건은 의미 유사도 점수에 섞지 않고, 검색된 원문이 질문의 정확한 조건과 맞는지 확인하는 데만 사용했다.
- 상위 제목 추가, 별도 Cross Encoder 재정렬, Qwen3-4B 직접 관련성 판별은 평가에서
  개선을 증명하지 못해 제외했다. Parent Dense, Sparse Search와 QueryPlanner도 보류했다.
  남은 병목은 올바른 `RetrievalPassage` 안에서 최종 `EvidenceChild`를 고르는 단계였다.
  새 모델 대신 기존 BGE-M3와 같은 질문 벡터로 한 묶음 안의 원문만 다시 비교했다.

### 검증과 트레이드오프

DEV/CAL 비교 평가에서 기존 검색과 V3의 검색 후보 Recall@5/20은 모두 100%였다. 반면
기존 검색 최종 결과에서 서로 다른 경험이 섞인 비율은 70.80%였고 V3는 0%였다. 원문
위치 정확도 지표도 49.43%에서 95.73%로 높아졌다.

같은 V3 검색 묶음 안에서 BGE-M3로 원문을 다시 고르자 Top1은 54.12%에서 90.59%,
MRR은 75.76%에서 94.12%, 사용자 단위 평균 Top1은 58.80%에서 90.06%로 개선됐다.
개선 33건, 회귀 0건이었고 기존 1위 직접 근거 46/46을 유지했다. 이론적으로 복구
가능했던 Child 선택 문제도 32건 중 31건을 해결했다. 대신 Child 227개를 추가로
임베딩해야 했다. 이 수치는 117개 DEV/CAL 질문의 평가 결과이며 실서비스 성능을 뜻하지
않는다. Search V3는 아직 평가 전용이고 최종 봉인 데이터도 열지 않았다.

근거:

- [Search V3 평가 기반](../../specs/search-v3/research/PRZ-025-search-v3-foundation/spec.md)
- [구조형 검색과 RetrievalPassage](../../specs/search-v3/research/PRZ-026-structural-parsing-parent-child/evidence.md)
- [기존 검색과 최소 V3 비교](../../specs/search-v3/research/PRZ-032-minimal-v3-shadow-comparison/evidence.md)
- [EvidenceChild 선택의 이론적 최대치](../../specs/search-v3/research/PRZ-033-atomic-evidence-child-selection-ceiling/evidence.md)
- [BGE-M3 EvidenceChild 선택 결과](../../specs/search-v3/research/PRZ-034-atomic-evidence-child-selector/evidence.md)
- [채택하지 않은 Qwen3-4B 실험](../../specs/search-v3/research/PRZ-031-semantic-evidence-directness/evidence.md)
