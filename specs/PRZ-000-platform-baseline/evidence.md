# PRZ-000 Evidence

| 항목 | 값 |
|---|---|
| Spec ID | `PRZ-000` |
| Status | `AS_BUILT_BASELINE` |
| Source commit | `e995a5fdecc63afbd383157dd5a8b6d74b607e3f` |
| Evidence captured | 2026-07-23 |
| Issue / PR | `N/A` — 소급 생성하지 않음 |

이 문서는 기존 구현의 근거를 연결한다. spec의 체크 표시나 설명은 구현 증거가 아니며, 아래 source·migration·test가 서로 다르면 실행 가능한 코드와 test를 우선한다.

## 요구사항 추적

| 요구사항 | Source·migration | 자동 검증 |
|---|---|---|
| `FR-001` 인증과 DB 재검증 | [DatabaseJwtAuthenticationConverter](../../src/main/java/com/prizm/auth/security/DatabaseJwtAuthenticationConverter.java), [SecurityConfiguration](../../src/main/java/com/prizm/auth/config/SecurityConfiguration.java) | [AuthenticationIntegrationTest](../../src/integrationTest/java/com/prizm/infrastructure/AuthenticationIntegrationTest.java), [DatabaseJwtAuthenticationConverterTest](../../src/test/java/com/prizm/auth/security/DatabaseJwtAuthenticationConverterTest.java) |
| `FR-002` owner 격리 | [V8 owner migration](../../src/main/resources/db/migration/V8__add_document_ownership.sql), [VectorSearchRepository](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java) | [AuthenticationIntegrationTest](../../src/integrationTest/java/com/prizm/infrastructure/AuthenticationIntegrationTest.java), [CareerPlatformMigrationTest](../../src/integrationTest/java/com/prizm/infrastructure/CareerPlatformMigrationTest.java) |
| `FR-003` TXT/PDF 등록·원본·한도 | [application.yml](../../src/main/resources/application.yml), [DocumentUploadService](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [DocumentTextExtractor](../../src/main/java/com/prizm/ingestion/service/DocumentTextExtractor.java), [LocalFileStorage](../../src/main/java/com/prizm/infrastructure/storage/LocalFileStorage.java) | [DocumentUploadServiceTest](../../src/test/java/com/prizm/document/service/DocumentUploadServiceTest.java), [DocumentTextExtractorTest](../../src/test/java/com/prizm/ingestion/service/DocumentTextExtractorTest.java), [PdfExtractionPropertiesTest](../../src/test/java/com/prizm/ingestion/config/PdfExtractionPropertiesTest.java), [LocalFileStorageTest](../../src/test/java/com/prizm/infrastructure/storage/LocalFileStorageTest.java) |
| `FR-004` immutable version·원자적 ACTIVE | [V3](../../src/main/resources/db/migration/V3__create_documents_and_document_versions.sql), [IndexingCompletionService](../../src/main/java/com/prizm/ingestion/service/IndexingCompletionService.java) | [DocumentManagementDatabaseIntegrationTest](../../src/integrationTest/java/com/prizm/infrastructure/DocumentManagementDatabaseIntegrationTest.java), [PgVectorInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java) |
| `FR-005` indexing lease·fencing·재시도·복구 | [설정](../../src/main/resources/application.yml), [IngestionProperties](../../src/main/java/com/prizm/ingestion/config/IngestionProperties.java), [V4](../../src/main/resources/db/migration/V4__create_processing_jobs.sql), [V5](../../src/main/resources/db/migration/V5__add_processing_job_lease.sql), [ProcessingJobClaimRepository](../../src/main/java/com/prizm/ingestion/repository/ProcessingJobClaimRepository.java), [WorkerLeaseHeartbeat](../../src/main/java/com/prizm/ingestion/service/WorkerLeaseHeartbeat.java), [IndexingRetryPolicy](../../src/main/java/com/prizm/ingestion/service/IndexingRetryPolicy.java), [IndexingFailureService](../../src/main/java/com/prizm/ingestion/service/IndexingFailureService.java), [ProcessingJobRecoveryService](../../src/main/java/com/prizm/ingestion/service/ProcessingJobRecoveryService.java) | [ProcessingJobLeaseServiceTest](../../src/test/java/com/prizm/ingestion/service/ProcessingJobLeaseServiceTest.java), [WorkerLeaseHeartbeatTest](../../src/test/java/com/prizm/ingestion/service/WorkerLeaseHeartbeatTest.java), [IndexingRetryPolicyTest](../../src/test/java/com/prizm/ingestion/service/IndexingRetryPolicyTest.java), [IndexingFailureServiceTest](../../src/test/java/com/prizm/ingestion/service/IndexingFailureServiceTest.java), [ProcessingJobRecoveryServiceTest](../../src/test/java/com/prizm/ingestion/service/ProcessingJobRecoveryServiceTest.java), [PgVectorInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java) |
| `FR-006` embedding 검증 | [EmbeddingValidator](../../src/main/java/com/prizm/embedding/service/EmbeddingValidator.java), [V2 vector schema](../../src/main/resources/db/migration/V2__create_document_chunks.sql) | [EmbeddingValidatorTest](../../src/test/java/com/prizm/embedding/service/EmbeddingValidatorTest.java), [PgVectorInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java) |
| `FR-007` 검색 계약 | [SearchService](../../src/main/java/com/prizm/search/service/SearchService.java), [VectorSearchRepository](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java) | [SearchServiceTest](../../src/test/java/com/prizm/search/service/SearchServiceTest.java), [PgVectorInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java) |
| `FR-008` 문서 관리·version·PDF | [DocumentController](../../src/main/java/com/prizm/document/controller/DocumentController.java), [DocumentManagementService](../../src/main/java/com/prizm/document/service/DocumentManagementService.java), [DocumentThumbnailService](../../src/main/java/com/prizm/document/service/DocumentThumbnailService.java) | [DocumentManagementServiceTest](../../src/test/java/com/prizm/document/service/DocumentManagementServiceTest.java), [DocumentThumbnailServiceTest](../../src/test/java/com/prizm/document/service/DocumentThumbnailServiceTest.java), [DocumentManagementDatabaseIntegrationTest](../../src/integrationTest/java/com/prizm/infrastructure/DocumentManagementDatabaseIntegrationTest.java) |
| `FR-009` orphan-file cleanup | [설정](../../src/main/resources/application.yml), [CleanupProperties](../../src/main/java/com/prizm/cleanup/config/CleanupProperties.java), [V12](../../src/main/resources/db/migration/V12__add_file_cleanup_jobs.sql), [V13](../../src/main/resources/db/migration/V13__add_file_cleanup_worker_fields.sql), [FileCleanupJobRepository](../../src/main/java/com/prizm/cleanup/repository/FileCleanupJobRepository.java), [FileCleanupCoordinator](../../src/main/java/com/prizm/cleanup/service/FileCleanupCoordinator.java), [FileCleanupFailureService](../../src/main/java/com/prizm/cleanup/service/FileCleanupFailureService.java), [FileCleanupJobRecoveryService](../../src/main/java/com/prizm/cleanup/service/FileCleanupJobRecoveryService.java), [LocalFileStorage](../../src/main/java/com/prizm/infrastructure/storage/LocalFileStorage.java) | [FileCleanupCoordinatorTest](../../src/test/java/com/prizm/cleanup/service/FileCleanupCoordinatorTest.java), [FileCleanupFailureServiceTest](../../src/test/java/com/prizm/cleanup/service/FileCleanupFailureServiceTest.java), [FileCleanupJobRecoveryServiceTest](../../src/test/java/com/prizm/cleanup/service/FileCleanupJobRecoveryServiceTest.java), [LocalFileStorageTest](../../src/test/java/com/prizm/infrastructure/storage/LocalFileStorageTest.java), [PgVectorInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java) |
| `FR-010` V1~V13 Flyway | [migration directory](../../src/main/resources/db/migration/), [Flyway configuration](../../src/main/resources/application.yml) | PostgreSQL: [CareerPlatformMigrationTest](../../src/integrationTest/java/com/prizm/infrastructure/CareerPlatformMigrationTest.java); OpenSQL 조건부 Gate: [OpenSqlInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/OpenSqlInfrastructureTest.java) (`NOT_RUN`) |
| `FR-011` Career Vault | [App.tsx](../../frontend/src/App.tsx), [documentApi.ts](../../frontend/src/api/documentApi.ts), [searchApi.ts](../../frontend/src/api/searchApi.ts) | frontend lint와 production build; 자동 UI test suite는 없음 |
| `FR-012` 중립적 근거 없음·score 경계 | [SearchService](../../src/main/java/com/prizm/search/service/SearchService.java), [Career Evidence UI](../../frontend/src/App.tsx), [검색 평가 문서](../../docs/evaluation/search-evaluation.md) | [SearchServiceTest](../../src/test/java/com/prizm/search/service/SearchServiceTest.java), [CareerEvidenceSearchControllerTest](../../src/test/java/com/prizm/search/controller/CareerEvidenceSearchControllerTest.java) |
| `FR-013` 12개 DocumentType | [DocumentType](../../src/main/java/com/prizm/document/entity/DocumentType.java), [V9](../../src/main/resources/db/migration/V9__add_document_type.sql), [DocumentUploadService](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [DocumentQueryService](../../src/main/java/com/prizm/document/service/DocumentQueryService.java) | [DocumentUploadServiceTest](../../src/test/java/com/prizm/document/service/DocumentUploadServiceTest.java), [DocumentControllerTest](../../src/test/java/com/prizm/document/controller/DocumentControllerTest.java), [CareerPlatformMigrationTest](../../src/integrationTest/java/com/prizm/infrastructure/CareerPlatformMigrationTest.java) |
| `FR-014` TXT/PDF 출처 metadata | [V10](../../src/main/resources/db/migration/V10__add_chunk_source.sql), [V11](../../src/main/resources/db/migration/V11__support_pdf_page_sources.sql), [DocumentIndexingProcessor](../../src/main/java/com/prizm/ingestion/service/DocumentIndexingProcessor.java) | [DocumentIndexingProcessorTest](../../src/test/java/com/prizm/ingestion/service/DocumentIndexingProcessorTest.java), [PgVectorInfrastructureTest](../../src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java) |

## 환경별 검증 상태

`Source commit` 통합 시점에 기록된 결과이며, 자세한 실행 이력은 해당 Git commit과
PR 기록을 따른다.

| 대상 | 상태 | 2026-07-23 기록 |
|---|---|---|
| Backend `test` task | `PASS` | 242건 중 228건 성공, Windows/filesystem 환경 조건 14건 skip, 실패·오류 0건 |
| Frontend lint | `PASS` | ESLint 통과 |
| Frontend build | `PASS` | TypeScript·Vite production build 통과 |
| Markdown local links / diff | `PASS` | 로컬 링크 누락 0, `git diff --check` 통과 |
| PostgreSQL·pgvector integration | `HISTORICAL_PASS_NOT_RERUN` | 기존 통합 기록은 있으나 branch 통합 환경에서 Docker 부재로 재실행하지 않음 |
| Document management PostgreSQL scenarios | `HISTORICAL_PASS_NOT_RERUN` | PR #10 시점 전용 5개 시나리오 성공; 이번 baseline 문서 작업에서 재실행하지 않음 |
| Docker Compose config | `NOT_RUN` | 현재 환경에 Docker 실행 파일 없음 |
| Dense `searchEvaluation` | `HISTORICAL_PASS_NOT_RERUN` | 2026-07-14 합성 기준선은 보존했으나 이번 환경에서 PostgreSQL·Ollama 미사용 |
| Ollama `bge-m3` | `NOT_RUN` | 이번 baseline 기록에서 사용하지 않음 |
| OpenSQL | `NOT_RUN` | Gate 준비만 존재하며 실제 OpenSQL 환경 검증 없음 |
| OpenProxy / OpenHA | `NOT_RUN` | 실제 runtime·failover 검증 없음 |

## 알려진 한계

- `OpenSqlInfrastructureTest`가 존재해도 실제 환경 결과가 `NOT_RUN`이면 OpenSQL 호환성 증거가 아니다.
- 합성 14청크 corpus의 Recall@20 1.0000은 운영 규모 검색 품질을 증명하지 않는다.
- frontend는 lint/build만 있으며 상호작용 자동 test suite가 없다.
- clean-clone demo `USER`와 합성 E2E가 없어 현재 Quickstart는 전체 사용자 흐름을 완주하지 못한다.
- CareerFact와 portfolio 생성은 source·domain·API·test가 없으므로 이 baseline에서 제외한다.
- 실제 서비스 검색 정확도, TPS, RTO·RPO, 사용자 수, 비용 절감률과 취업 결과는 측정하지 않았다.

## 변경 이력 처리

`PRZ-000`을 위해 과거 Issue, PR 또는 review를 새로 만들지 않았다. 기존 Git commit과 실제로 존재하는 과거 PR은 그대로 유지하며, 앞으로의 신규 spec만 실제 작업 시점의 Issue·PR과 연결한다.
