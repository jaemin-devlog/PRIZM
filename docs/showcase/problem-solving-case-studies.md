# PRIZM 대표 문제 해결 사례

이 문서는 현재 소스, Flyway migration과 자동 테스트로 확인되는 설계 사례만 정리한다.

## 1. 사용자별 문서와 벡터 검색 격리

### 문제

서비스 계층에서만 소유자를 확인하면 새 repository나 SQL 경로에서 검사가 빠질 수 있다. pgvector 거리 계산 뒤 결과를 걸러내면 다른 사용자의 청크가 이미 후보 집합에 들어간다.

### 해결

- V8에서 document, version, chunk, processing job에 `owner_user_id`를 추가하고 상·하위 owner가 일치하는 composite FK를 적용했다.
- 소유자를 알 수 없는 legacy 문서를 임의 사용자에게 귀속하지 않고 migration을 중단한다.
- 목록·상세·관리 API는 owner-scoped repository를 사용한다.
- 벡터 검색 SQL은 거리 계산 후보 단계에서 document·version·chunk owner, ACTIVE 상태와 `active_version_id`를 함께 검사한다.
- `SYSTEM_ADMIN`은 개인 문서·검색 API를 사용할 수 없도록 역할 경계를 유지한다.

### 검증과 트레이드오프

인증 통합 테스트는 두 사용자의 목록·상세·검색 격리와 타 사용자 접근 거부를 확인한다. DB 관계와 모든 생성·조회 경로에서 owner를 전달해야 하므로 구현 복잡도는 늘지만, 응답 직전 필터보다 누락 경로를 줄인다.

근거:

- [V8 owner migration](../../src/main/resources/db/migration/V8__add_document_ownership.sql)
- [문서 조회 서비스](../../src/main/java/com/prizm/document/service/DocumentQueryService.java)
- [벡터 검색 SQL](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java)
- [인증·격리 통합 테스트](../../src/integrationTest/java/com/prizm/infrastructure/AuthenticationIntegrationTest.java)

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
