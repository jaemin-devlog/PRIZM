# PRIZM 오픈소스 제품 경계와 구현 기준선

> 기준일: 2026-07-16
> 상태: 단계 0 최종 독립 재검토 PASS, 기준선 확정
> 구현 판단 기준: source code, Flyway migration, executable test, `frontend/`

이 문서는 PRIZM Engine과 Career Vault Reference App의 책임을 구분하고, 이후 리팩터링이 보존해야 할 현재 실행 기준선을 기록합니다. 장기 목표가 현재 구현을 뜻하지 않으며, 상세 현재 기능은 [현재 구현 현황](../project-status.md), 전환 순서는 [오픈소스 엔진 전환 실행 계획](../oss-transition-execution-plan.md)을 기준으로 합니다.

공식 제품 정의는 다음과 같습니다.

> PRIZM은 커리어 문서의 분석, 정보 구조화, 근거 검색 및 포트폴리오 생성을 위한 오픈소스 Career Intelligence Engine과 이를 검증하는 Reference App이다. 개인뿐 아니라 대학, 취업 지원기관, 기업 및 개발자가 각자의 환경에 맞는 커리어 관리 서비스를 구축할 수 있도록 재사용 가능한 모듈과 확장 지점을 제공한다.

이는 목표 제품 경계입니다. 현재 구현은 그중 문서 처리·근거 검색 기반과 개인용 Career Vault Reference App만 제공하며, 구조화·포트폴리오 생성·재사용 가능한 모듈과 기관·기업용 통합 지점은 후속 단계에서 구현하고 검증합니다.

## 1. 제품 경계

| 영역 | 제품 책임 | 현재 물리적 상태 | 이 단계에서의 판단 |
|---|---|---|---|
| PRIZM Engine | 문서·버전·원본 보존, 추출·청킹·임베딩, 비동기 처리, 출처가 있는 검색, 인증된 actor의 데이터 격리 | 단일 Spring Boot Gradle 프로젝트 안에 controller, service, repository, infrastructure가 함께 있음 | 실행 가능한 기반은 존재하지만 독립 artifact나 headless `/api/v1` 제품으로 분리되지 않음 |
| Engine adapter | 파일 저장, parser, chunker, embedding, vector retrieval 같은 외부 기술 연결 | `FileStorage`와 `EmbeddingService` 인터페이스는 있으나 PDFBox, 고정 청커, Ollama, pgvector JDBC가 현재 애플리케이션에 직접 결합 | 교체 가능한 공개 adapter 체계는 계획이며 현재 완료 상태가 아님 |
| Career Vault Reference App | 개인 사용자가 엔진의 업로드·검색 흐름을 경험하는 예제 UI | `frontend/`의 React·TypeScript·Vite 애플리케이션 | 엔진 본체나 완성형 B2C 상품이 아니라 현재 개인용 참조 애플리케이션 |
| 장기 career intelligence | canonical source, CareerFact, 검증된 portfolio, public API v1, 기관 scope | 실행 계획에만 존재 | 현재 구현이 아님 |
| 외부 통합·운영 | MCP, webhook/outbox, 여러 저장소·모델 adapter, OpenSQL/OpenProxy/OpenHA | 설정 초안·Gate 또는 계획 문서만 존재 | 실제 환경 검증 전에는 지원한다고 표현하지 않음 |

현재 repository 구조는 위의 **제품 책임 경계**를 선언하지만 아직 그 경계를 package나 Gradle module로 강제하지 않습니다. 따라서 지금의 Spring Boot 서버를 곧바로 재사용 가능한 엔진 SDK라고 부르지 않습니다.

## 2. 현재와 계획 기능 matrix

| 기능 | 현재 상태 | 근거 |
|---|---|---|
| JWT 로그인·현재 사용자 조회 | 구현 | `/api/auth/login`, `/api/users/me`, DB 사용자 재검증 |
| TXT·텍스트 PDF 최초 등록 | 구현 | `POST /api/documents`, PDFBox 검증, 로컬 원본 저장 |
| 12개 DocumentType과 단일 owner-scoped 필터 | 구현 | V9, `GET /api/documents?documentType=...` |
| 문서·버전·청크·processing job owner 격리 | 구현 | V8 composite FK, repository 조건, 통합 테스트 |
| 자동 비동기 색인과 원자적 ACTIVE 전환 | 구현 | processing job claim, heartbeat, completion transaction |
| 단일 검색·최대 5개 Career Evidence 검색 | 구현 | `/api/search`, `/api/career-evidence/search` |
| Career Vault 로그인·목록·필터·업로드·다중 근거 UI | 구현 | `frontend/src/App.tsx` |
| 문서 상세 API | 구현 | `GET /api/documents/{documentId}` |
| 문서 상세 UI | 미구현 | Reference App에 route와 호출 없음 |
| 같은 논리문서의 새 버전 추가 API | 미구현 | 최초 등록 endpoint만 존재 |
| CareerFact와 사용자 확인 상태 | 미구현 | domain, table, API 없음 |
| 검증된 portfolio 생성 | 미구현 | composer, renderer, artifact table/API 없음 |
| MCP·OpenAPI `/api/v1`·webhook/outbox | 미구현 | endpoint와 contract 없음 |
| OpenSQL·OpenProxy·OpenHA 호환성 | 미검증 | 환경 Gate만 존재; PostgreSQL 결과와 분리 |
| cleanup job 등록 | 구현 | V12와 rollback 보상 실패 시 `REQUIRES_NEW` 등록 |
| cleanup Worker | 구현·감사 완료 | V13, scheduler·claim·completion·failure·recovery, PostgreSQL·Linux 감사; `86387e7c227ede3be96c538aafc48b0205bc5e18`가 main에 병합됨 |

## 3. 현재 API 기준선

| Method·path | 권한 | 요청·결과 계약 |
|---|---|---|
| `POST /api/auth/login` | 공개 | 이메일·비밀번호를 받아 HS256 Access Token과 사용자 요약 반환 |
| `GET /api/users/me` | 인증 사용자 | DB에서 다시 확인된 현재 사용자 반환 |
| `POST /api/documents` | `USER` | multipart `title`, 선택적 `documentType`, TXT/PDF `file`; 새 document와 version 1을 `QUARANTINED`로 등록 |
| `GET /api/documents` | `USER` | owner의 문서 목록; 선택적 단일 `documentType` 필터 |
| `GET /api/documents/{documentId}` | `USER` | owner의 문서와 버전 메타데이터 목록 |
| `POST /api/search` | `USER` | 최대 500자 query; 가장 가까운 한 청크, 없으면 404 `SEARCH_NO_RESULT` |
| `POST /api/career-evidence/search` | `USER` | 같은 query; 거리순 최대 5개 배열, 없으면 HTTP 200 빈 배열 |
| `GET /actuator/health` | 공개 | Spring Boot health |

`SYSTEM_ADMIN`은 `/api/users/me`를 호출할 수 있지만 개인 문서·검색 endpoint는 `ROLE_USER`로 제한됩니다. 현재 API는 versioned public API가 아니며 OpenAPI 문서도 없습니다.

검색 응답의 `score`는 `1 - cosine distance`로 계산한 표시값이며 확률·정확도·합격 가능성이 아닙니다. 현재 PDF 위치 계약은 `sourceType=PAGE`, 실제 페이지 번호인 `sourceIndex`, `sourceLabel=N페이지`입니다. 기존 단일 응답의 nullable `pageNo`는 저장 경로에서 채우지 않으므로 canonical 위치로 의존하지 않습니다.

## 4. 저장 모델과 Flyway 기준선

### 핵심 테이블

- `users`: 정규화 이메일, BCrypt hash, `USER`/`SYSTEM_ADMIN`, 활성 상태
- `documents`: `owner_user_id`, 제목, 12개 `document_type`, nullable `active_version_id`
- `document_versions`: owner와 document, 단조 증가를 의도한 `version_no`, 원본 파일명·상대 storage key, TXT/PDF, SHA-256, 상태
- `document_chunks`: owner와 version, chunk 번호, `vector(1024)`, 본문과 `TEXT_CHUNK`/`PAGE` 출처
- `processing_jobs`: owner와 version, 상태, retry, DB 시간 lease, `claim_version`, 안전한 오류 메시지
- `file_cleanup_jobs`: rollback 뒤 고아 원본 정리를 위한 storage key, 상태, attempts, lease, `claim_version`

V8은 document→version→chunk/job의 owner composite FK를 추가하고 기존 문서 데이터가 있으면 소유자를 추측하지 않고 migration을 중단합니다. V9는 12개 문서 유형, V10은 TXT 출처, V11은 PDF와 `PAGE`, V12는 cleanup job 등록 테이블, V13은 cleanup claim·lease·attempts·상태 전이 필드를 추가합니다.

V1~V13이 이 문서의 안정 기준선입니다. V13 Cleanup Worker는 보안·동시성 최종 감사에서 CRITICAL/HIGH/MEDIUM finding 없이 통과했고 `86387e7c227ede3be96c538aafc48b0205bc5e18`로 main에 병합되었습니다. 이미 적용한 migration은 수정하지 않고 다음 번호의 forward migration만 사용합니다.

## 5. 비동기 처리 기준선

```text
document version: QUARANTINED → PROCESSING → ACTIVE | FAILED
processing job:   PENDING/RETRY_WAIT → PROCESSING → COMPLETED | FAILED
cleanup job:      PENDING/RETRY_WAIT → PROCESSING → COMPLETED | FAILED
```

1. 업로드 transaction은 document, version, 원본 storage key와 `PENDING` indexing job을 연결합니다.
2. Worker는 PostgreSQL `FOR UPDATE SKIP LOCKED`로 한 작업을 짧게 선점하고 DB 시간 기반 lease와 증가한 `claim_version`을 받습니다.
3. 원본 읽기, PDF 추출, Ollama 호출은 완료 transaction 밖에서 수행합니다.
4. 별도 heartbeat가 lease duration의 1/3 주기로 전체 외부 처리 구간의 lease를 갱신하며, 청크 간격 갱신도 유지합니다.
5. 완료 transaction은 claim과 owner를 다시 확인하고 청크 교체, version `ACTIVE`, document `active_version_id`, job `COMPLETED`를 함께 커밋합니다.
6. retry 가능한 실패는 1분·5분·15분 backoff 후 최대 3회 재시도합니다. 영구 실패 또는 소진 시 부분 청크를 지우고 새 version만 `FAILED`로 전환하므로 기존 active version은 유지됩니다.
7. 만료된 lease는 recovery Worker가 회수하고 stale claim의 늦은 완료는 fencing으로 거부합니다.

Cleanup Worker도 PostgreSQL `FOR UPDATE SKIP LOCKED`로 짧게 claim하고 lease·증가한 `claim_version`으로 fencing합니다. 파일 삭제는 claim transaction 밖에서 수행하며, 1분·5분·15분 backoff와 만료 lease recovery를 사용합니다. 삭제 성공 뒤 `COMPLETED` 갱신이 실패하면 실패 상태로 오판하지 않고 PROCESSING lease를 보존해 recovery가 파일 없음 멱등 성공으로 수렴시킵니다. 로컬 삭제는 storage root부터 열린 `SecureDirectoryStream`으로 부모와 최종 일반 파일을 `NOFOLLOW_LINKS`로 확인한 뒤 열린 부모 descriptor의 `deleteFile`로 수행해 부모 symlink 교체·TOCTOU를 방어합니다.

처리 timeout과 최대 청크 수 제한은 없습니다. heartbeat가 lease를 보존하더라도 무한 처리나 자원 고갈을 제한하는 별도 정책을 대신하지 않습니다.

## 6. 검색과 출처 기준선

- Ollama `bge-m3`가 문서 청크와 query를 1024차원으로 임베딩합니다.
- `EmbeddingValidator`가 차원, finite 값, 0보다 큰 L2 norm을 SQL 전과 저장 전 확인합니다.
- pgvector `<=>` exact cosine distance로 전체 후보를 정렬하며 ANN index는 없습니다.
- SQL 후보 단계에서 document, version, chunk의 owner가 모두 일치하고 version이 `ACTIVE`이며 `documents.active_version_id`가 해당 version인지 확인합니다.
- TXT는 `TEXT_CHUNK`와 1부터의 청크 순서, PDF는 `PAGE`와 실제 1부터의 페이지 번호를 사용합니다.
- char offset, PDF 좌표, quote hash, processing/model provenance, tag와 pagination은 아직 없습니다.

## 7. 인증과 데이터 경계 기준선

- 로그인은 BCrypt 비밀번호를 확인하고 issuer·만료가 있는 HS256 JWT를 발급합니다.
- JWT secret은 최소 32 UTF-8 byte이며 공개 placeholder와 앞뒤 공백을 거부합니다.
- 요청마다 JWT subject의 사용자를 DB에서 조회해 enabled, email, role을 토큰 claim과 다시 대조합니다.
- CORS는 명시적 HTTP(S) origin만 허용하고 stateless Bearer 인증을 사용합니다.
- 애플리케이션 repository 조건과 V8 FK가 owner 무결성을 중복 방어합니다.
- signup, password reset, refresh token, OIDC, API key, workspace/profile scope는 없습니다.

## 8. 외부 의존성 기준선

| 의존성 | 현재 용도 | 현재 검증 경계 |
|---|---|---|
| Java 17, Spring Boot 4.1, Gradle 9.5.1 | 단일 API·Worker 애플리케이션 | unit/integration 명령 제공 |
| PostgreSQL 16, pgvector 0.8.2 | metadata, 상태, `vector(1024)`, exact search | Compose와 Testcontainers 기준 |
| Flyway | V1부터 순차 schema 적용 | PostgreSQL migration test |
| PDFBox 3.0.3 | 비암호화 text-layer PDF 검증·페이지 추출 | unit/integration test |
| Spring AI, host Ollama, `bge-m3` | 1024차원 embedding | 실제 Ollama가 필요한 integration test |
| 로컬 파일시스템 | 원본 storage | 서버 생성 상대 경로와 hash; 분산·durable object storage 아님 |
| React 19, TypeScript 6, Vite 8 | Career Vault Reference App | lint와 production build; frontend test suite 없음 |
| OpenSQL/OpenProxy/OpenHA | 목표 운영환경 | 실환경 미검증; [OpenSQL 기술 Gate](../opensql-gate.md) 분리 |

`compose.yaml`은 database만 정의합니다. API, Worker, Web, Ollama를 모두 묶는 배포 또는 clean-clone Quickstart는 아직 없습니다.

OpenSQL profile과 조건부 integration test는 존재하지만 PostgreSQL 성공은 OpenSQL 성공이 아닙니다. OpenSQL, OpenProxy, OpenHA는 실제 환경에서 아직 검증되지 않았으며, 다음 기술 작업 순서는 (1) OpenSQL 단일 환경의 migration·vector 검색 검증, (2) OpenProxy runtime 연결 검증, (3) OpenHA 장애전환과 검색 복구 검증입니다.

## 9. 보존해야 할 불변식

- `active_version_id`가 가리키는 완성된 version만 기본 검색에 사용합니다.
- document, version, chunk, processing job의 owner는 API·SQL·FK에서 일치해야 합니다.
- 외부 처리 중 DB transaction을 오래 열지 않고 최종 활성화만 원자적으로 커밋합니다.
- lease, heartbeat, retry/backoff, recovery, `claim_version` fencing을 약화하지 않습니다.
- 원본 hash와 상대 storage key, document version과 출처를 보존합니다.
- 기존 단일 결과와 최대 5개 Career Evidence API 계약을 호환 경로로 유지합니다.
- 12개 DocumentType과 TXT `TEXT_CHUNK`·PDF `PAGE` 계약을 임의 변경하지 않습니다.
- PostgreSQL 성공을 OpenSQL·OpenProxy·OpenHA 성공으로 표현하지 않습니다.
- Cleanup은 지원 filesystem에서만 descriptor-relative 삭제를 수행하고, 지원하지 않으면 fail-closed합니다.

## 10. 다음 경계 작업

이 문서는 목표 경계를 정했을 뿐 package 분리나 기능 구현을 승인하지 않습니다. 다음 단계는 거버넌스·재현 가능한 Quickstart를 먼저 갖춘 뒤, 동작 보존 테스트를 유지하면서 application port와 기본 adapter를 분리하는 순서입니다. CareerFact와 portfolio는 canonical source 계약 이후의 작은 수직 슬라이스로만 추가합니다.

## 11. 남은 LOW backlog

- V13에는 `claim_version >= 0` CHECK 제약이 아직 없습니다.
- populated V12 cleanup row를 V13으로 올리는 전용 backfill 회귀 테스트가 아직 없습니다.
- `SecureDirectoryStream`을 제공하지 않는 filesystem에서는 안전하지 않은 경로 기반 삭제 대신 fail-closed하므로 자동 Cleanup이 동작하지 않을 수 있습니다. 이 플랫폼 제약은 단계 2 Quickstart·운영 문서에서 명시합니다.
