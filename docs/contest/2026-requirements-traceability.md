# 2026 티맥스티베로 지정과제 요구사항·평가기준 추적표

> 기준일: 2026-07-24
>
> 목적: 공식 요구사항과 평가기준을 현재 구현, 검증 환경, 다음 작업,
> 제출 증거에 연결한다.
>
> 추적표 상태: `CONTENT_EXTRACTED_SOURCE_PENDING`

## 기준 자료

| 구분 | 자료 | 이 문서에서의 용도 |
|---|---|---|
| 지정과제 원문 | [KOSSA 티맥스티베로 지정과제](https://www.kossa.kr/materials/2026/ossp/tasks-tmax.html) | 과제명, 미션, 목표, 개발과제 예시 |
| 대회 일정·제출물 | [오픈소스 개발자대회 공식 페이지](https://www.oss.kr/pages/2) | 2026-08-27 마감, 결과보고서·3분 시연영상·소스코드 |
| 평가기준 | 2026-07-24에 제공받은 오리엔테이션 슬라이드 `프로그램(2/4)~(4/4)` | 1차 30점, 기능·라이선스 검증, 2차 70점 |

제공받은 슬라이드 캡처는 공식 배포 파일의 URL과 재배포 조건이 확인되지 않아
저장소에 복사하지 않았다. 공식 PDF를 확보하면 이 표에 URL, 버전과 파일
해시를 추가하고 상태를 갱신한다. 공개 대회 페이지는 1차 평가 시작일을
9월 3일로 표시하고, 상세 오리엔테이션 슬라이드는 기간을 9월 3~4일로
표시한다. 이 문서는 상세 슬라이드의 기간을 사용하면서 두 출처를 함께
보존한다.

## 해석 원칙

- 공식 페이지의 다섯 기능은 제목상 `개발과제 예시`다. 모두를 확정
  필수조건이라고 바꾸어 쓰지 않는다.
- 그러나 자동 임베딩·동기화·MCP 검색·무중단 복구는 미션과 목표에도
  반복되므로 제출 전 핵심 대응 항목으로 관리한다.
- OpenSQL의 기술 소개를 PRIZM의 구현 증거로 사용하지 않는다.
- 애플리케이션 Worker 복구는 DB 노드 장애전환 증거가 아니다.
- PostgreSQL 성공을 OpenSQL 성공으로 바꾸어 기록하지 않는다.

## 지정과제 추적

상태는 source와 실행 가능한 test를 기준으로 한다. `구현됨`이어도 이번
환경에서 다시 실행하지 않았다면 검증 결과를 별도로 표시한다.

| ID | 공식 미션·예시 | 현재 상태 | source·test 근거 | 환경 검증 | 다음 Gate·제출 증거 |
|---|---|---|---|---|---|
| `TMAX-01` | OpenSQL 기반 AI 검색·벡터 데이터 플랫폼 | `GATE_READY` — OpenSQL 구현·호환성 판정 아님 | [OpenSQL 실행 test](../../src/integrationTest/java/com/prizm/infrastructure/OpenSqlInfrastructureTest.java), [공통 assertions](../../src/integrationTest/java/com/prizm/infrastructure/OpenSqlCompatibilityAssertions.java), [기술 Gate](../opensql-gate.md) | 실제 OpenSQL `NOT_RUN` | 실제 OpenSQL에서 migration, `vector(1024)`, 검색, ownership, claim·lease·fencing·`SKIP LOCKED` 결과 |
| `TMAX-02` | 문서 업로드 | `IMPLEMENTED` | [controller](../../src/main/java/com/prizm/document/controller/DocumentController.java), [service](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [service test](../../src/test/java/com/prizm/document/service/DocumentUploadServiceTest.java) | `HISTORICAL_PASS_NOT_RERUN` | clean clone에서 로그인→업로드→ACTIVE 재현 영상과 test 결과 |
| `TMAX-03` | 자동 임베딩 | `IMPLEMENTED` | [Ollama service](../../src/main/java/com/prizm/embedding/service/OllamaEmbeddingService.java), [validator](../../src/main/java/com/prizm/embedding/service/EmbeddingValidator.java), [indexing test](../../src/test/java/com/prizm/ingestion/service/DocumentIndexingProcessorTest.java) | `HISTORICAL_PASS_NOT_RERUN` | OpenSQL·Ollama를 함께 사용한 실제 색인과 검색 결과 |
| `TMAX-04` | 메타데이터·버전 관리 | `IMPLEMENTED` | [upload/version service](../../src/main/java/com/prizm/document/service/DocumentUploadService.java), [V3 migration](../../src/main/resources/db/migration/V3__create_documents_and_document_versions.sql), [V8 ownership](../../src/main/resources/db/migration/V8__add_document_ownership.sql), [DB integration test](../../src/integrationTest/java/com/prizm/infrastructure/DocumentManagementDatabaseIntegrationTest.java) | `HISTORICAL_PASS_NOT_RERUN` | clean clone과 OpenSQL에서 이전 ACTIVE 보존·새 version 전환 증거 |
| `TMAX-05` | 변경 로그 기반 동기화 | `NOT_IMPLEMENTED` | 해당 source·test 없음. 현재 processing job과 document version은 변경 로그 동기화가 아님 | `NOT_RUN` | 별도 spec에서 동기화 경계·멱등성·재시도·누락 방지 acceptance test와 실제 결과 |
| `TMAX-06` | MCP 기반 검색 API | `NOT_IMPLEMENTED` | 현재 [Career Evidence REST controller](../../src/main/java/com/prizm/search/controller/CareerEvidenceSearchController.java)와 [contract test](../../src/test/java/com/prizm/search/controller/CareerEvidenceSearchControllerTest.java)는 MCP가 아님 | `NOT_RUN` | 읽기 전용 MCP 검색 도구, 인증·ownership·출처와 기존 빈 결과 contract test 및 demo |
| `TMAX-07` | DB 노드 장애에도 중단 없는 자동 복구 | `NOT_IMPLEMENTED` | [Worker recovery](../../src/main/java/com/prizm/ingestion/service/ProcessingJobRecoveryService.java)와 [test](../../src/test/java/com/prizm/ingestion/service/ProcessingJobRecoveryServiceTest.java)는 DB failover 증거가 아님 | OpenSQL HA `NOT_RUN` | 구현 전 topology, 장애 시나리오, RTO·RPO, 허용 오류·중복·유실, 반복 횟수를 spec에 고정한 뒤 실제 장애 주입 결과 |

`PRZ-000` 요구사항은 `TMAX-01 → FR-002/004/005/006/007/010`,
`TMAX-02 → FR-002/003/005/008/011`, `TMAX-03 → FR-005/006`,
`TMAX-04 → FR-002/004/008/010/013/014`로 연결된다. `TMAX-06`이 재사용할
현재 검색 계약은 `FR-001/002/007/012/014`다. `TMAX-05`와 DB 가용성
의미의 `TMAX-07`에는 현재 대응 요구사항·구현이 없다.

상세 현재 구현 근거는 [현재 구현 현황](../project-status.md)과
[PRZ-000 Evidence](../../specs/PRZ-000-platform-baseline/evidence.md)를 따른다.

## 1차 서면평가 30점

- 기간: 2026-09-03(목)~09-04(금)
- 대상: 출품작 제출을 완료한 모든 참가팀
- 방식: 평가위원 서면평가 후 수상 규모의 약 2배 내외 선발
- 자료: 결과보고서, 소스코드, 시연 영상
- 합격 발표: 2026-09-09(수) 예정

| ID | 평가항목 | 배점 | 증거 기반 준비도(공식 점수 아님) | 남은 핵심 증거 |
|---|---|---:|---|---|
| `EVAL-R1-01` | 프로젝트 구조 및 코드 완성도 | 6 | `PARTIAL` — 인증·ownership·version·비동기 복구·검색과 test가 있음 | 실제 OpenSQL, clean-clone, demo `USER`, 처리 완료 확인, browser E2E 또는 고정 수동 UI 시험표 |
| `EVAL-R1-02` | 오픈소스 프로젝트 발전 가능성 | 6 | `PARTIAL` — Engine·Reference App 방향과 일부 교체 interface가 있음 | LICENSE·거버넌스, 기여자 Quickstart, 안정된 확장 경계 |
| `EVAL-R1-03` | 개발 문서의 구체성 | 6 | `PARTIAL` — 현황·roadmap·spec·evidence·이 추적표가 있음 | 검증된 설치·운영·troubleshooting, architecture/data flow |
| `EVAL-R1-04` | 프로젝트 혁신성 | 6 | `PARTIAL` — 원문 출처 검색, version fencing, 안전한 복구가 있음 | 공식 과제의 동기화·MCP·가용성 증거와 PRIZM 차별 slice |
| `EVAL-R1-05` | 프로젝트 팀워크 | 6 | `PARTIAL` — 개인 참가자의 실제 과거 PR·commit과 현재 관리 규칙이 있음 | 앞으로의 실제 Issue→spec→branch→PR→CI→review→merge와 community 증거 |

준비도는 공식 점수가 아니며 `PARTIAL`을 특정 점수로 자동 환산하지 않는다.
팀워크 증거를 위해 과거 Issue·PR·review를 소급 생성하지 않는다. 개인 참가자의
`REVIEW_NOT_AVAILABLE_SOLO`는 절차적 투명성일 뿐 GitHub review 증거가 아니다.
Community는 실제 제3자의 Issue, discussion, review, feedback 또는 contribution이
있을 때만 기록한다.

### 내부 평가 스냅샷

| 항목 | 값 |
|---|---|
| Assessment ID | `ASSESS-2026-07-24-01` |
| Type | `INTERNAL_ESTIMATE_NOT_OFFICIAL` |
| Scope | `PRE_SUBMISSION_REPOSITORY_ONLY` |
| Official score | `N/A` |
| Assessment target commit | `2cb1bc49c4bfdf40c51a0adb347367e0f4602491` |
| Assessment date | 2026-07-24 |
| Confidence | `LOW_TO_MEDIUM` |
| Status | `CURRENT` |

| ID | 보수적 내부 추정 | 다음 재평가 evidence Gate |
|---|---:|---|
| `EVAL-R1-01` | 4/6 | 실제 OpenSQL과 clean-clone에서 전체 사용자 흐름과 UI 시험 `PASS` |
| `EVAL-R1-02` | 3/6 | 라이선스 감사 뒤 실제 LICENSE·NOTICE·CONTRIBUTING·SECURITY와 clean-clone 기여자 Quickstart 검증 완료 |
| `EVAL-R1-03` | 4/6 | 별도의 깨끗한 환경에서 문서만 보고 설치·demo·검증 재현 `PASS` |
| `EVAL-R1-04` | 3/6 | OpenSQL에서 DB failover·변경 로그 동기화·MCP 중 하나의 실제 수직 slice `PASS` |
| `EVAL-R1-05` | 3/6 | 실제 신규 작업에서 Issue→spec→PR→CI→merge 흐름이 반복 재현되고, 가능한 경우 genuine third-party review가 별도 증거로 연결됨 |
| **합계** | **17/30** | 공식 점수나 수상 가능성 예측이 아님 |

표의 Gate는 재평가를 가능하게 하는 누적 증거이지 점수 상승을 보장하지 않으며,
여러 Gate를 하나의 큰 spec으로 합치라는 뜻이 아니다. 계획·문서 추가만으로
숫자를 바꾸지 않는다. `VERIFY` 뒤 독립 `AUDIT`가 Gate 충족 여부를 제안하고,
같은 evidence가 `main`에 통합된 뒤에만 새 Assessment ID를 발급해 이 단일
스냅샷과 아래 이력을 갱신한다. 긍정적이든 부정적이든 관련 evidence가
target commit 이후 바뀌면 재평가 전까지 상태를 `STALE`로 둔다. 회귀, test 실패,
라이선스 충돌, 문서·source 불일치나 공식 기준 변경은 점수를 낮출 수 있다.

| 날짜 | Assessment ID | Target commit | 변경 | 근거 | 판정 |
|---|---|---|---|---|---|
| 2026-07-24 | `ASSESS-2026-07-24-01` | `2cb1bc49c4bfdf40c51a0adb347367e0f4602491` | 기준선 `N/A → 17/30` | source·test·문서·GitHub 이력에 대한 세 갈래 읽기 전용 평가 | `CURRENT` |

## 멘토링

- 목적: 출품작의 기술 경쟁력과 참가자의 오픈소스 역량 강화
- 대상·기간: 1차 선발팀, 2026-09-18(금)~10-09(금)
- 수요조사: 2026-09-09(수)~09-11(금)
- 지정과제: 해당 과제 기업이 직접 멘토링 제공

## 기능·라이선스 검증

- 대상: 1차 평가 선발팀
- 기간: 2026-10-12(월)~10-28(수)

| 항목 | 배점 | 공식 확인 방식 | 현재 준비도 | 다음 증거 |
|---|---:|---|---|---|
| 기능테스트 | 10 | 전문기관 온·오프라인 시험, 시스템 소개·구현 환경·소스 확인, 오류·버그·정지·종료 등 비정상 동작 없이 운영되는지 확인 | `PARTIAL` | clean-clone 실행서, 고정 fixture, backend·browser 또는 고정 수동 UI 기능시험표, 처리 완료 확인, OpenSQL·Ollama 실제 결과 |
| 라이선스 검증 | 5 | 오픈소스SW 역량프라자 의뢰 온라인 검증, 소스 업로드, 사용 OSS와 복수 라이선스 충돌 및 해결 방안 확인 | `NOT_STARTED` | dependency·model·data·asset·OpenSQL 감사표, SBOM, 충돌 해결표, root LICENSE와 NOTICE |

검증 도구에 올릴 소스는 credentials, `.env`, 업로드 원본, DB volume, model,
IDE·build 산출물을 포함하지 않도록 사전 검사한다. 작업 폴더 전체를 압축하지
않고, 검증된 commit/tag의 tracked 파일만 `git archive` 또는 Release artifact로
패키징한 뒤 파일 manifest를 확인한다.

## 2차 발표평가 70점

- 일자: 2026-11-04(수)
- 방식: 오프라인 PPT, 팀별 15분(발표 10분+질의응답 5분)
- 대상: 1차 합격 후 기능·라이선스 검증을 완료한 팀

| 평가항목 | 배점 | 공식 관점 | 현재 준비도 | 남은 핵심 증거 |
|---|---:|---|---|---|
| 작품발표(PT) | 10 | 개발 계획 수행 수준, 발표자료 완성도, 정보 전달력, 사용 OSS 라이브러리 표기 | `NOT_STARTED` | 평가항목 순서의 PPT, OSS 표기, 10+5분 리허설 |
| 활용성 | 15 | 출품작의 잠재적 경쟁력 | `PARTIAL` | 대상 사용자 시나리오, 반복 가능한 demo, 경쟁력·사용자 검증 근거 |
| 작품 데모(완성도) | 10 | 체계적 demo, 결과 표현, 질의응답의 안정적 수행 | `PARTIAL` | clean-clone, 처리 완료 확인, browser E2E 또는 고정 수동 UI 시험표, 오류·근거 없음·복구 시나리오. 제출용 3분 영상 제한과는 별도 |
| 커뮤니티 확장 가능성 | 10 | 품질관리·개발방법론·roadmap 관리와 커뮤니티 참여·지속적 지식재산 공유 | `NOT_STARTED` | LICENSE·CONTRIBUTING·SECURITY·template, roadmap와 실제 기여 흐름 |
| 오픈소스SW 적절성 | 10 | 다른 OSS를 적절히 도입·활용해 정상 운영되는 수준 | `PARTIAL` | 실제 OpenSQL 결과, OSS 선택 이유·버전·용도·라이선스 표 |
| 기능테스트 | 10 | 외부 기능검증 결과 | `PARTIAL` | 외부 검증 가능한 실행 절차와 정상·예외·장애 test 결과 |
| 라이선스 검증 | 5 | 외부 라이선스 분석·식별·충돌 검증 결과 | `NOT_STARTED` | 충돌 여부와 해결 방안을 포함한 감사 결과 |

## 제출·후속 산출물 상태

| 산출물 | 현재 상태 | 완료 기준 |
|---|---|---|
| 8월 27일 결과보고서 | `NOT_STARTED` | 공식 요구사항, 문제·해결·검증·한계, source/test/environment 링크 |
| 8월 27일 3분 시연영상 | `NOT_STARTED` | 실제 화면과 문서의 기능 범위 일치, 고정 fixture와 재촬영 가능한 script |
| 제출 소스코드 | `PARTIAL` | 검증된 commit/tag의 tracked 파일만 포함한 archive와 manifest |
| 1차 평가 대응자료 | `NOT_STARTED` | 보고서·source·영상 간 버전과 주장 일치 |
| 멘토링 반영 기록 | `NOT_STARTED` | feedback→실제 Issue/spec/PR/evidence 연결 또는 비채택 사유 |
| 기능검증 패키지 | `NOT_STARTED` | 시스템 소개, 구현 환경, clean-clone, 정상·예외·장애 시험표 |
| 라이선스 검증 패키지 | `NOT_STARTED` | SBOM, 사용 목적·버전·license, 복수 license 충돌과 해결 방안 |
| 2차 PPT·Q&A | `NOT_STARTED` | OSS 라이브러리 표기, 10분 발표와 5분 질의응답 리허설 |

## 개발 순서 결정

공식 과제 정합성을 먼저 확보한 뒤 PRIZM 고유 기능을 확장한다.

1. 공식 오리엔테이션 원본 URL·version·hash 고정과 라이선스·거버넌스 감사
2. 실제 OpenSQL 단일 환경 Gate
3. clean-clone과 안전한 demo `USER`
4. 실제 다중 노드 환경의 DB failover·서비스 연속성 Gate
5. 변경 로그 동기화의 최소 수직 슬라이스
6. owner-scoped 읽기 전용 MCP 검색의 최소 수직 슬라이스
7. 위 Gate 통과 뒤 CareerFact, 이후 근거 기반 portfolio
8. 기능시험·보고서·영상·발표·라이선스 제출 감사

필요한 OpenSQL 다중 노드 환경을 확보하지 못하면 4번은 `NOT_RUN`으로
남긴다. 특정 OpenHA 제품을 사용했다는 주장도 실제 구성과 장애시험이 있을
때만 한다. 환경 부재를 숨기지는 않되, 5~6번의 독립 개발까지 중단시키지는
않는다.
