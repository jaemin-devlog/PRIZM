# 2026 티맥스티베로 지정과제 요구사항·평가기준 추적표

> 현재 검증 기준일: 2026-08-02
>
> 목적: 공식 요구사항과 평가기준을 PRIZM의 현재 대응, source·test,
> 실제 환경 검증과 다음 Gate에 연결한다.
>
> 상태: 공식 요구사항 매핑 완료; 오리엔테이션 슬라이드의 공개 공식 원본은
> 미확보

## 공식 근거

| 구분 | 자료 | 용도 |
|---|---|---|
| 지정과제 원문 | [KOSSA 티맥스티베로 지정과제](https://www.kossa.kr/materials/2026/ossp/tasks-tmax.html) | 과제명, 미션, 목표, 개발과제 예시 |
| 대회 일정·제출물 | [오픈소스 개발자대회 공식 페이지](https://www.oss.kr/pages/2) | 2026-08-27 마감, 결과보고서·3분 시연영상·소스코드 |
| 운영규정·결과보고서·오리엔테이션 근거 | [공식 source register](2026-source-register.md) | artifact URL, 수집일, SHA-256, 재배포 경계 |
| 평가기준 보조 자료 | 2026-07-24에 제공받은 오리엔테이션 슬라이드 `프로그램(2/4)~(4/4)` | 1차 30점, 기능·라이선스 검증, 2차 70점 |

제공받은 슬라이드 캡처는 공개 원본 URL과 재배포 조건이 확인되지 않아
저장소에 복사하지 않았다. 공식 PDF를 확보하면 source register에 URL,
version, hash를 추가한다. 공개 대회 페이지는 1차 평가 시작일을 9월 3일로,
상세 슬라이드는 9월 3~4일로 표시한다. 두 출처를 함께 보존한다.

## 공식 요구와 내부 판단의 경계

- 공식 페이지의 다섯 기능은 제목상 `개발과제 예시`다. 모두를 확정
  필수조건이라고 바꾸어 쓰지 않는다.
- 자동 임베딩·동기화·MCP 검색·무중단 복구는 미션과 목표에도 반복되므로
  제출 전 핵심 대응 항목으로 관리한다.
- 아래 `현재 대응`은 PRIZM의 내부 판정이다. 공식 점수나 주최 측 판정이 아니다.
- OpenSQL 기술 소개를 PRIZM의 구현 증거로 사용하지 않는다.
- PostgreSQL 성공, 애플리케이션 Worker 복구 또는 OpenSQL single-node 결과를
  OpenProxy·OpenHA·DB failover 결과로 확대하지 않는다.
- 숫자로 표시한 내부 예상 점수와 변경 이력은 공개 문서에서 관리하지 않는다.
  아래 표에는 공식 평가항목, 현재 evidence와 다음 Gate만 기록한다.

## 지정과제 추적

상태는 source와 실행 가능한 test를 기준으로 한다. 구현돼 있어도 현재
환경에서 다시 실행하지 않았다면 환경 검증은 별도로 표시한다.

| ID | 공식 미션·개발과제 예시 | PRIZM의 현재 대응 | source·test·evidence | 실제 환경 검증 | 다음 Gate |
|---|---|---|---|---|---|
| `TMAX-01` | OpenSQL 기반 AI 검색·벡터 데이터 플랫폼 | `VERIFIED_SINGLE_NODE_SQL_GATE_AND_DIRECT_APP_E2E`; HA 판정은 아님 | [OpenSQL integration test](../../src/integrationTest/java/com/prizm/infrastructure/OpenSqlInfrastructureTest.java), [공통 assertions](../../src/integrationTest/java/com/prizm/infrastructure/OpenSqlCompatibilityAssertions.java), [기술 Gate](../opensql-gate.md), [PRZ-003 evidence](../../specs/PRZ-003-opensql-single-node-gate/evidence.md), [PRZ-005 보고서](../../specs/PRZ-005-opensql-ollama-e2e/implementation-report.md) | 실제 OpenSQL single-node에서 Flyway V1~V13, `vector(1024)`, Worker SQL을 검증하고, 직접 `5432`에서 Ollama 색인·검색 API와 브라우저 E2E·두 사용자 격리 및 격리 DB opt-in test `PASS` | OpenProxy 안전 인증·SQL routing, OpenHA·DB failover는 별도 `NOT_VERIFIED`·`DEFERRED` Gate |
| `TMAX-02` | 문서 업로드 | `IMPLEMENTED_AND_OPENSQL_VERIFIED` | [controller](../../src/main/java/com/prizm/document/controller/DocumentController.java), [service](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [service test](../../src/test/java/com/prizm/document/service/DocumentUploadServiceTest.java), [PRZ-004 evidence](../../specs/PRZ-004-clean-clone-demo/evidence.md), [PRZ-005 보고서](../../specs/PRZ-005-opensql-ollama-e2e/implementation-report.md) | PostgreSQL 두 clean clone과 실제 OpenSQL 직접 `5432`에서 TXT/PDF 업로드→`ACTIVE` API·브라우저 흐름 `PASS`; PR #25·#26으로 통합 | 오류·복구 시나리오와 외부 사용자 재현 확대 |
| `TMAX-03` | 자동 임베딩 | `IMPLEMENTED_AND_OPENSQL_VERIFIED` | [Ollama service](../../src/main/java/com/prizm/embedding/service/OllamaEmbeddingService.java), [validator](../../src/main/java/com/prizm/embedding/service/EmbeddingValidator.java), [indexing test](../../src/test/java/com/prizm/ingestion/service/DocumentIndexingProcessorTest.java), [PRZ-005 보고서](../../specs/PRZ-005-opensql-ollama-e2e/implementation-report.md) | 실제 OpenSQL에 Ollama `bge-m3`의 1024차원·0이 아닌 임베딩을 저장하고 TXT/PDF 원문 검색 `PASS` | 검색 품질 평가와 오류·복구 시나리오 확대 |
| `TMAX-04` | 메타데이터·버전 관리 | `IMPLEMENTED_AND_OPENSQL_VERIFIED` | [upload/version service](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [V3 migration](../../src/main/resources/db/migration/V3__create_documents_and_document_versions.sql), [V8 ownership](../../src/main/resources/db/migration/V8__add_document_ownership.sql), [DB integration test](../../src/integrationTest/java/com/prizm/infrastructure/DocumentManagementDatabaseIntegrationTest.java), [PRZ-005 보고서](../../specs/PRZ-005-opensql-ollama-e2e/implementation-report.md) | 실제 OpenSQL에서 버전·`ACTIVE` 전환, owner 정합성과 두 사용자 격리 `PASS` | 처리 실패 때 이전 `ACTIVE` 보존을 실제 OpenSQL 오류 주입으로 검증 |
| `TMAX-05` | 변경 로그 기반 동기화 | `NOT_IMPLEMENTED`; processing job과 document version은 변경 로그 동기화가 아님 | 해당 source·test 없음 | `NOT_RUN` | 별도 spec에서 동기화 경계·멱등성·재시도·누락 방지 acceptance test와 실제 결과 |
| `TMAX-06` | MCP 기반 검색 API | `NOT_IMPLEMENTED`; 현재 Career Evidence API는 REST이며 MCP가 아님 | [REST controller](../../src/main/java/com/prizm/search/controller/CareerEvidenceSearchController.java), [contract test](../../src/test/java/com/prizm/search/controller/CareerEvidenceSearchControllerTest.java) | `NOT_RUN` | owner-scoped 읽기 전용 MCP 검색, 인증·출처·빈 결과 contract test와 demo |
| `TMAX-07` | DB 노드 장애에도 중단 없는 자동 복구 | `NOT_IMPLEMENTED`; Worker recovery는 DB failover 증거가 아님 | [Worker recovery](../../src/main/java/com/prizm/ingestion/service/ProcessingJobRecoveryService.java), [test](../../src/test/java/com/prizm/ingestion/service/ProcessingJobRecoveryServiceTest.java) | OpenSQL HA `NOT_RUN` | topology, 장애 시나리오, RTO·RPO, 허용 오류·중복·유실과 반복 횟수를 spec에 고정한 뒤 실제 장애 주입 |

`PRZ-000` 요구사항은 `TMAX-01 → FR-002/004/005/006/007/010`,
`TMAX-02 → FR-002/003/005/008/011`, `TMAX-03 → FR-005/006`,
`TMAX-04 → FR-002/004/008/010/013/014`로 연결된다. `TMAX-06`이 재사용할
현재 검색 계약은 `FR-001/002/007/012/014`다. `TMAX-05`와 DB 가용성
의미의 `TMAX-07`에는 현재 대응 요구사항·구현이 없다.

상세 구현 근거는 [현재 구현 현황](../project-status.md)과
[PRZ-000 Evidence](../../specs/PRZ-000-platform-baseline/evidence.md)를 따른다.

## 1차 서면평가 30점

- 기간: 2026-09-03(목)~09-04(금)
- 대상: 출품작 제출을 완료한 모든 참가팀
- 방식: 평가위원 서면평가 후 수상 규모의 약 2배 내외 선발
- 자료: 결과보고서, 소스코드, 시연 영상
- 합격 발표: 2026-09-09(수) 예정

| ID | 공식 평가항목 | 배점 | 현재 evidence | 다음 Gate |
|---|---|---:|---|---|
| `EVAL-R1-01` | 프로젝트 구조 및 코드 완성도 | 6 | 인증·ownership·version·비동기 복구·검색, 실제 OpenSQL single-node SQL Gate, 직접 OpenSQL+Ollama API·브라우저 E2E와 두 clean-clone 전체 흐름 | 오류·복구 시험표와 DB failover |
| `EVAL-R1-02` | 오픈소스 프로젝트 발전 가능성 | 6 | Engine·Reference App 방향, source-only Apache-2.0·NOTICE·SBOM·AI 모델 명세, clean-clone Quickstart·검증 CI·최종 감사 | 외부 사용자의 독립 재현과 안정된 확장 경계 |
| `EVAL-R1-03` | 개발 문서의 구체성 | 6 | 현황·roadmap·spec·OpenSQL·clean-clone evidence와 실제 PR·CI·merge 연결 | 외부 사용자의 설치·운영·troubleshooting feedback |
| `EVAL-R1-04` | 프로젝트 혁신성 | 6 | 원문 출처 검색, version fencing, 안전한 복구 | 동기화·MCP·DB 가용성 중 실제 수직 slice |
| `EVAL-R1-05` | 프로젝트 팀워크; 개인은 안정적·체계적 관리체계 | 6 | 실제 spec→branch→PR→CI→merge 흐름과 review 부재의 정직한 기록 | 신규 작업의 실제 Issue·review와 genuine community evidence |

준비도를 공식 점수로 환산하거나 내부 예상 점수로 공개하지 않는다.
`REVIEW_NOT_AVAILABLE_SOLO`는 review evidence가 아니며, community는 실제
제3자의 Issue, discussion, review, feedback 또는 contribution이 있을 때만
기록한다.

## 기능·라이선스 검증

- 대상: 1차 평가 선발팀
- 기간: 2026-10-12(월)~10-28(수)

| 공식 항목 | 배점 | 공식 확인 방식 | 현재 evidence | 다음 Gate |
|---|---:|---|---|---|
| 기능테스트 | 10 | 전문기관 온·오프라인 시험, 시스템 소개·구현 환경·소스 확인, 비정상 동작 없이 운영되는지 확인 | backend·frontend test, 실제 OpenSQL single-node Gate, 고정 합성 fixture를 사용한 두 clean-clone 전체 흐름 | OpenSQL·Ollama 전체 결과와 정상·예외·장애 시험표 |
| 라이선스 검증 | 5 | 오픈소스SW 역량프라자 온라인 검증, 소스 업로드, 사용 OSS와 복수 라이선스 충돌·해결 확인 | [source-only compliance 결론](2026-compliance.md), Apache-2.0 `LICENSE`·`NOTICE`, SBOM·AI 모델 명세, 검증 CI | 외부 분석 결과와 OpenSQL runtime의 사용·비공개·비재배포 경계 확인 |

검증 도구에는 credentials, `.env`, 업로드 원본, DB volume, model,
IDE·build 산출물을 올리지 않는다. 검증된 commit/tag의 tracked 파일만
`git archive` 또는 Release artifact로 패키징하고 manifest를 확인한다.

## 2차 발표평가 70점

- 일자: 2026-11-04(수)
- 방식: 오프라인 PPT, 팀별 15분(발표 10분+질의응답 5분)
- 대상: 1차 합격 후 기능·라이선스 검증을 완료한 팀

| 공식 평가항목 | 배점 | 현재 evidence | 다음 Gate |
|---|---:|---|---|
| 작품발표(PT) | 10 | `NOT_STARTED` | 평가항목 순서의 PPT, OSS 표기, 10+5분 리허설 |
| 활용성 | 15 | 대상 사용자와 Reference App 방향 | 반복 가능한 demo와 사용자 검증 근거 |
| 작품 데모(완성도) | 10 | 현재 Reference App, 두 clean-clone과 실제 OpenSQL API·브라우저 사용자 흐름 | 오류·근거 없음·복구 시나리오 |
| 커뮤니티 확장 가능성 | 10 | Apache-2.0·NOTICE, SBOM, spec·roadmap·개발 기록 | 실제 외부 기여 접수 또는 지원 release 전에 기여·보안 운영 경로 확정 |
| 오픈소스SW 적절성 | 10 | 실제 OpenSQL single-node SQL Gate·OpenSQL+Ollama 전체 사용자 흐름과 source-only OSS inventory | OSS 선택 이유·version·용도·license 설명 보강 |
| 기능테스트 | 10 | repository test와 환경별 evidence | 외부 검증 가능한 정상·예외·장애 test 결과 |
| 라이선스 검증 | 5 | source-only compliance·SBOM·AI 모델 명세·검증 CI | 외부 분석 결과와 OpenSQL 비공개·비재배포 경계 포함 충돌 여부·해결 방안 |

상세 운영 일정, 제출물 상태와 구현 우선순위는
[대회 계획](2026-tmaxtibero-plan.md)에 둔다.
