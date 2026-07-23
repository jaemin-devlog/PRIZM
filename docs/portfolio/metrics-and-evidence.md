# PRIZM 수치와 구현 근거

이 문서는 현재 `main`의 코드·migration·실행 가능한 테스트와 별도 실험 결과를 구분한다. 근거가 없는 사용자 수, 비용 절감, 합격률과 성능 수치는 추측하지 않는다.

상태:

- `IMPLEMENTED_AND_TESTED`: 현재 코드와 자동 테스트가 있다.
- `IMPLEMENTED`: 현재 코드가 있으나 이 문서 정리 과정에서 외부 환경 검증을 다시 실행하지 않았다.
- `EXPERIMENTED_NOT_ADOPTED`: 비교 실험은 했지만 운영 코드에 채택하지 않았다.
- `NOT_ENVIRONMENT_VERIFIED`: 대상 제품·환경에서 실행하지 않았다.
- `PLANNED`: 아직 구현하지 않았다.

## 현재 시스템 계약

| 항목 | 현재 값 | 상태 | 근거 |
|---|---:|---|---|
| 임베딩 차원 | 1024 | IMPLEMENTED_AND_TESTED | [설정](../../src/main/resources/application.yml), [검증기](../../src/main/java/com/prizm/embedding/service/EmbeddingValidator.java), [V2](../../src/main/resources/db/migration/V2__create_document_chunks.sql) |
| 지원 원본 | UTF-8 TXT, text-layer PDF | IMPLEMENTED_AND_TESTED | [업로드 서비스](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [추출기](../../src/main/java/com/prizm/ingestion/service/DocumentTextExtractor.java) |
| 문서 유형 | 12개 | IMPLEMENTED_AND_TESTED | [DocumentType](../../src/main/java/com/prizm/document/entity/DocumentType.java), [V9](../../src/main/resources/db/migration/V9__add_document_type.sql) |
| 업로드 한도 | 10,485,760 bytes | IMPLEMENTED_AND_TESTED | [설정](../../src/main/resources/application.yml), [업로드 서비스](../../src/main/java/com/prizm/document/service/DocumentUploadService.java) |
| PDF 한도 | 300페이지, 추출 문자 2,000,000자 | IMPLEMENTED_AND_TESTED | [설정](../../src/main/resources/application.yml), [추출기](../../src/main/java/com/prizm/ingestion/service/DocumentTextExtractor.java) |
| Flyway 기준선 | V1~V13 | IMPLEMENTED_AND_TESTED | [migration](../../src/main/resources/db/migration/), [migration 통합 테스트](../../src/integrationTest/java/com/prizm/infrastructure/CareerPlatformMigrationTest.java) |
| 검색 반환 계약 | 단일 1개 / Career Evidence 최대 5개 | IMPLEMENTED_AND_TESTED | [SearchService](../../src/main/java/com/prizm/search/service/SearchService.java), [Career Evidence controller](../../src/main/java/com/prizm/search/controller/CareerEvidenceSearchController.java) |
| 재시도 | 최대 3회, 1·5·15분 backoff | IMPLEMENTED_AND_TESTED | [정책](../../src/main/java/com/prizm/ingestion/service/IndexingRetryPolicy.java), [테스트](../../src/test/java/com/prizm/ingestion/service/IndexingRetryPolicyTest.java) |
| 색인 lease | 기본 10분, 1/3 주기 heartbeat | IMPLEMENTED_AND_TESTED | [설정](../../src/main/resources/application.yml), [heartbeat](../../src/main/java/com/prizm/ingestion/service/WorkerLeaseHeartbeat.java) |
| Cleanup lease | 기본 5분 | IMPLEMENTED_AND_TESTED | [설정](../../src/main/resources/application.yml), [claim repository](../../src/main/java/com/prizm/cleanup/repository/FileCleanupJobRepository.java) |

## 기능 근거

- document, version, processing job, chunk에 owner를 저장하고 FK·repository·검색 SQL에서 격리한다.
- 새 version의 색인이 성공하기 전까지 기존 `active_version_id`를 유지한다.
- 청크 교체, version ACTIVE, active version 변경과 job COMPLETED를 하나의 완료 transaction으로 확정한다.
- Career Vault는 목록·유형/제목/처리상태 필터, 상세, 메타데이터 수정, 삭제, 새 TXT/PDF version, PDF thumbnail·원본 열람과 최대 5개 근거 검색을 제공한다.
- Cleanup Worker는 lease·`claim_version` fencing·retry/backoff·recovery와 `SecureDirectoryStream` 기반 descriptor-relative 삭제를 사용하고, 지원하지 않는 filesystem에서는 fail-closed한다.

세부 코드와 테스트는 [대표 문제 해결 사례](problem-solving-case-studies.md)에서 연결한다.

## Dense 검색 기준선

2026-07-14 합성 파일럿의 실제 PostgreSQL 16.14·pgvector·Ollama `bge-m3` 실행 결과다. 평가 하네스와 fixture는 [검색 품질 평가](../search-evaluation.md)에 보존한다.

| 항목 | 결과 | 상태 |
|---|---:|---|
| 합성 문서 / 질문 | 11 / 30 | IMPLEMENTED_AND_TESTED |
| TUNING / TEST 질문 | 20 / 10 | IMPLEMENTED_AND_TESTED |
| Recall@20 | 1.0000 | EXPERIMENTED_NOT_ADOPTED |
| Precision@5 / Direct Precision@5 | 0.1933 / 0.1600 | EXPERIMENTED_NOT_ADOPTED |
| MRR@20 / nDCG@5 | 0.6556 / 0.8543 | EXPERIMENTED_NOT_ADOPTED |
| 중복 결과 비율 | 0.0067 | EXPERIMENTED_NOT_ADOPTED |
| 평균 / p95 검색 지연 | 864.20ms / 999ms | EXPERIMENTED_NOT_ADOPTED |

합성 corpus의 실제 청크는 14개이므로 Recall@20을 운영 규모의 회수율로 해석하지 않는다. score는 `1 - cosine distance` 표시값이며 정답 확률이 아니다.

## 채택하지 않은 Reranker

`BAAI/bge-reranker-v2-m3` CPU 실험은 TEST MRR·nDCG 일부를 높였지만 TEST Precision@5와 Direct Precision@5를 개선하지 못했고, 질문당 reranking p95가 약 51.86초, peak RSS가 약 2.10GB였다. 코드 도입을 거절한 근거와 전체 수치는 [실험 결정 기록](../experiments/2026-07-14-bge-reranker-evaluation.md)에 남긴다.

## 검증 경계

- 2026-07-23 backend 단위 테스트는 242건 중 228건 성공, 환경 조건 14건 skip, 실패·오류 0건이었다.
- 같은 날 frontend lint와 production build를 통과했다.
- 이 정리 환경에는 Docker 실행 파일이 없어 `docker compose config`, PostgreSQL·pgvector 통합 테스트와 `searchEvaluation`을 다시 실행하지 못했다. Ollama와 OpenSQL도 사용하지 않았다.
- PR #10 시점의 PostgreSQL 16+pgvector 문서 관리 전용 통합 시나리오 5건 성공은 [개발 기록](../development-log.md)의 역사적 증거이며 이번 실행 결과로 바꾸어 쓰지 않는다.
- PostgreSQL·pgvector 통합 테스트 결과를 OpenSQL 결과로 바꾸어 표현하지 않는다.
- 실제 OpenSQL 환경 Gate는 아직 `NOT_RUN`이다. OpenProxy·OpenHA도 `NOT_ENVIRONMENT_VERIFIED`다.
- 실제 서비스 검색 정확도, TPS, RTO·RPO, 사용자 수, 비용 절감률과 취업 결과는 측정하지 않았다.
- CareerFact 구조화와 근거 기반 portfolio 생성은 `PLANNED`이며 현재 기능이 아니다.
