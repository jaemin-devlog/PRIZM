# PRIZM 현재 구현 현황

> 기준일: 2026-07-23
> 상태: PR #9 Docker 개발 스택·PR #10 문서 관리·검색 평가 파일럿과 `PRZ-000` AS_BUILT 기준선
> 이 문서는 현재 구현의 요약이며 최종 판단은 항상 실행 가능한 코드와 테스트를 기준으로 합니다.

PRIZM의 공식 제품 정의는 다음과 같습니다.

> PRIZM은 커리어 문서의 분석, 정보 구조화, 근거 검색 및 포트폴리오 생성을 위한 오픈소스 Career Intelligence Engine과 이를 검증하는 Reference App이다. 개인뿐 아니라 대학, 취업 지원기관, 기업 및 개발자가 각자의 환경에 맞는 커리어 관리 서비스를 구축할 수 있도록 재사용 가능한 모듈과 확장 지점을 제공한다.

현재 구현은 이 목표 전체가 아니라 커리어 문서에서 실제 경험의 원문 근거를 찾는 플랫폼 기반입니다. 저장소는 아직 독립 engine package가 아닌 단일 Spring Boot 서버와 React **Career Vault Reference App**이며, 개인용 Career Vault는 PRIZM 전체 제품이 아니라 현재 Engine 기능과 통합 방식을 보여주는 예제입니다. 책임 경계는 [오픈소스 제품 경계](architecture/oss-product-boundary.md), 장기 목표는 [PRIZM 최종 기획안](PRIZM_최종_기획안.md)을 참고합니다.

## 한눈에 보는 현재 흐름

```text
로그인
→ Career Vault에서 내 문서 목록과 유형 확인
→ TXT 또는 텍스트 PDF와 문서 유형 업로드
→ QUARANTINED version과 원본 파일·processing job 저장
→ Worker가 텍스트 추출·청킹·임베딩 수행
→ 모든 청크가 준비되면 현재 version을 ACTIVE로 원자적 전환
→ 내 ACTIVE 문서에서 원문과 TXT 구간 또는 PDF 페이지 출처 검색
```

새 version 처리에 실패해도 기존 `active_version_id`는 유지됩니다. 다른 사용자의 document, version, job, chunk는 검색 후보에도 포함되지 않습니다.

## 현재 구현된 기능

### 인증과 사용자 격리

- 이메일·비밀번호 로그인과 HS256 JWT Access Token 발급
- JWT issuer·만료·형식 검증 후 요청마다 DB에서 사용자 활성 상태·email·role 재확인
- 최소 32 UTF-8 byte secret, 알려진 placeholder와 앞뒤 공백 거부
- `/api/users/me` 현재 사용자 조회와 프런트엔드 세션 복구
- 로그아웃 및 401·403 발생 시 브라우저 토큰·사용자 정보 삭제
- document, version, processing job, chunk의 `owner_user_id`와 V8 composite FK
- 일반 사용자의 목록·상세·검색 격리
- `SYSTEM_ADMIN`의 개인 문서·검색 API 접근 차단

회원가입, 일반 `USER` bootstrap, refresh token, 비밀번호 재설정, OIDC, API key는 없습니다.

### Career Vault Reference App

- `/login`과 Career Vault shell의 `/career-vault/documents`, `/career-vault/evidence`, `/career-vault/upload` 세 화면
- 현재 사용자 email 표시와 로그아웃
- 내 문서 목록, 로딩·빈 목록·오류 상태
- 12개 문서 유형 중 하나를 적용하는 목록 필터
- TXT·PDF 단일 파일 업로드와 최대 10MB 클라이언트 사전 검사
- 업로드 성공 후 현재 필터를 유지한 목록 재조회
- 제목 부분 검색과 최신 처리 상태 필터
- 문서 상세 modal, 제목·문서 유형 수정과 명시적 삭제 확인
- 전체 version 이력, ACTIVE 표시와 같은 문서의 새 TXT/PDF version 등록
- owner-scoped PDF 첫 페이지 thumbnail과 원본 Blob viewer
- `POST /api/career-evidence/search`를 사용하는 자연어 검색
- 관련도 순 최대 5개 문서 제목·version·출처 라벨·원문·원래 score 표시
- 빈 배열을 `현재 PRIZM에 등록된 문서에서는 관련 근거를 찾지 못했습니다`라는 중립 문구로 안내

프런트엔드는 처리상태 자동 polling, CareerFact 확인·저장과 portfolio 생성을 제공하지 않습니다. 프런트엔드 test suite는 없고 lint와 production build만 구성되어 있습니다.

### 문서 등록과 원본 저장

- UTF-8 TXT와 텍스트 레이어가 있는 비암호화 PDF 업로드
- 원본 파일의 로컬 filesystem 저장과 SHA-256 hash 기록
- document와 document version 분리, 최초 version number 1, 업로드 직후 `QUARANTINED`
- 파일명·빈 파일·확장자·최대 10MB 검증
- 손상·암호화·무텍스트 PDF를 `INVALID_DOCUMENT_CONTENT`로 거부
- 12개 `DocumentType` 저장, 생략 시 `OTHER`
- 사용자 소유권과 문서 유형을 함께 적용하는 목록 필터
- DB rollback 뒤 동기 보상 삭제가 실패하면 V12 `file_cleanup_jobs`에 상대 storage key 등록
- V13 Cleanup Worker가 고아 원본을 비동기로 정리; 안전한 filesystem에서만 descriptor-relative 삭제를 수행

지원 문서 유형:

`RESUME`, `COVER_LETTER`, `PORTFOLIO`, `PROJECT_REPORT`, `PRESENTATION`, `CERTIFICATE`, `COURSE_COMPLETION`, `SCHOOL_ASSIGNMENT`, `CAREER_REVIEW`, `JOB_POSTING`, `INTERVIEW_FEEDBACK`, `OTHER`

### PDF 처리 안전장치

- Apache PDFBox 3.0.3으로 실제 PDF 구조 확인
- 페이지별 텍스트 추출과 빈 페이지 제외
- 최대 300페이지 제한
- 페이지별 `strip()` 결과를 누적한 최대 2,000,000자 제한
- 두 제한은 `prizm.document.pdf` 설정과 환경변수로 재정의 가능
- 제한 초과 시 업로드 단계에서 document, version, job 생성 전 거부
- Worker에서 발견하면 영구 오류로 재시도 없이 `FAILED`
- 제한 초과 시 청크와 새 ACTIVE version이 생성되지 않고 기존 ACTIVE version 유지

OCR, image-only PDF, PDF 좌표와 이미지 추출은 지원하지 않습니다.

### 비동기 색인과 장애 복구

- document version의 `QUARANTINED → PROCESSING → ACTIVE | FAILED` 흐름
- processing job의 `PENDING`, `RETRY_WAIT`, `PROCESSING`, `COMPLETED`, `FAILED` 상태
- `SELECT ... FOR UPDATE SKIP LOCKED` 선점과 DB 시간 기반 lease
- 1분·5분·15분 backoff, 최대 3회 재시도, 만료 작업 recovery
- `claim_version` fencing으로 이전 Worker의 늦은 완료 차단
- 별도 daemon heartbeat가 원본 읽기·PDF 추출·Ollama 호출을 포함한 처리 구간에서 lease duration의 1/3 주기로 갱신
- 기존 청크 처리 간격 lease 갱신도 유지
- 외부 파일·PDF·Ollama 처리를 완료 transaction 밖에서 수행
- 청크 교체, version ACTIVE, `active_version_id`, job COMPLETED를 하나의 transaction으로 처리
- 처리 실패 시 부분 청크 제거와 기존 ACTIVE version 유지

### 고아 원본 Cleanup Worker

- `PENDING`/`RETRY_WAIT → PROCESSING → COMPLETED | FAILED` 상태 전이, PostgreSQL `FOR UPDATE SKIP LOCKED` claim, DB 시간 lease와 `claim_version` fencing
- 파일 삭제는 짧은 claim transaction 밖에서 실행하며, 일시 오류와 예기치 않은 `RuntimeException`은 1분·5분·15분 backoff 및 최대 attempts 정책으로 재시도; 영구 오류와 소진 오류는 `FAILED`
- 만료된 PROCESSING lease는 recovery가 회수하며 stale completion·retry·fail은 id, PROCESSING, claim version 조건으로 차단
- 삭제 성공 뒤 COMPLETED 갱신이 실패하면 삭제 실패로 바꾸지 않고 lease recovery가 파일 없음 멱등 성공으로 수렴
- `SecureDirectoryStream` descriptor-relative 삭제로 storage root부터 부모와 최종 파일을 `NOFOLLOW_LINKS`로 확인해 symlink와 부모 교체 TOCTOU를 방어

전체 처리 timeout과 version당 최대 청크 수 제한은 아직 없습니다.

### 청크·임베딩·출처

- 고정 길이·overlap 청킹을 TXT 전체와 PDF 페이지별로 적용
- Ollama `bge-m3` 1024차원 embedding
- 공통 `EmbeddingValidator`의 차원·finite 값·0보다 큰 L2 norm 검사
- 잘못된 vector의 DB 저장과 pgvector 검색 차단
- TXT 출처: `TEXT_CHUNK`, 1부터 시작하는 `sourceIndex`, `텍스트 구간 N`
- PDF 출처: `PAGE`, 실제 1부터 시작하는 페이지 번호인 `sourceIndex`, `N페이지`
- 한 PDF 페이지가 여러 청크로 나뉘어도 같은 PAGE 출처 유지

단일 검색 응답의 nullable `pageNo`는 현재 저장 시 채우지 않습니다. 현재 위치 식별 계약은 `sourceType`, `sourceIndex`, `sourceLabel`이며 char offset, PDF 좌표, quote hash는 없습니다.

### 검색

- PostgreSQL pgvector `<=>` exact cosine 검색
- document, version, chunk의 `owner_user_id`를 SQL 후보 단계에서 모두 적용
- `ACTIVE` 상태이며 `documents.active_version_id`와 일치하는 version만 검색
- 기존 단일 검색은 최대 1개를 반환하고 결과 없음은 404 `SEARCH_NO_RESULT`
- Career Evidence 검색은 거리순 최대 5개를 반환하고 결과 없음은 HTTP 200 빈 배열
- 단일 결과는 document/version/title/version/chunk/source/content/distance/score를 반환
- Career Evidence 결과는 위 정보에 `chunkId`를 포함하며 `chunkNo`와 `pageNo`는 포함하지 않음
- query embedding 검증 실패 시 pgvector SQL 실행 전 중단
- `score = 1 - distance`; 확률·정확도·합격 가능성으로 해석하지 않음

검색 threshold, tag/type 검색 필터, pagination, ANN index, 검색 기록은 없습니다.

## 현재 API

| API | 접근 | 현재 동작 |
|---|---|---|
| `POST /api/auth/login` | 공개 | 로그인과 Access Token 발급 |
| `GET /api/users/me` | 인증 | 현재 인증 사용자 조회 |
| `POST /api/documents` | `USER` | `title`, 선택적 `documentType`, TXT·PDF `file` 최초 업로드 |
| `GET /api/documents` | `USER` | 내 문서 목록; 선택적 `documentType`, 제목, 최신 처리 상태 필터 |
| `GET /api/documents?documentType=PORTFOLIO` | `USER` | 내 문서 유형별 목록 |
| `GET /api/documents/{documentId}` | `USER` | 내 문서와 version metadata 상세 조회 |
| `PATCH /api/documents/{documentId}` | `USER` | 제목과 12개 `DocumentType` metadata 수정 |
| `DELETE /api/documents/{documentId}` | `USER` | terminal 문서 metadata 삭제와 모든 원본 cleanup job 등록 |
| `POST /api/documents/{documentId}/versions` | `USER` | 같은 문서에 immutable TXT/PDF 다음 version 등록 |
| `GET /api/documents/{documentId}/versions/{versionId}/thumbnail` | `USER` | owner-scoped PDF 첫 페이지 PNG |
| `GET /api/documents/{documentId}/versions/{versionId}/original` | `USER` | owner-scoped PDF 원본 inline 응답 |
| `POST /api/search` | `USER` | 내 ACTIVE 청크 중 단일 검색 결과 |
| `POST /api/career-evidence/search` | `USER` | 내 ACTIVE 청크 중 최대 5개 근거 배열 |
| `GET /actuator/health` | 공개 | 애플리케이션 health |

현재 endpoint는 `/api/v1` versioned public API나 OpenAPI 계약이 아닙니다. 처리 job의 직접 조회·수동 재시도와 idempotency key는 없습니다.

## 데이터베이스와 migration

- 로컬·통합 테스트 기준: PostgreSQL 16, pgvector 0.8.2
- 안정 기준선: Flyway V1~V13
- V8: owner column, owner composite FK와 owner index; 기존 문서의 소유자를 추측할 수 있으면 실패가 아니라 migration 자체를 중단
- V9: `documents.document_type`과 12개 CHECK, 기존 문서 `OTHER` 보정
- V10: TXT 청크 출처 column과 기존 데이터 backfill
- V11: PDF file type과 `PAGE` 출처 허용
- V12: rollback 보상 실패용 `file_cleanup_jobs` PENDING 등록 table
- V13: Cleanup Worker의 상태, attempts, available/lease 시간, claim version과 claim/recovery query index
- 적용된 migration은 수정하지 않고 이후 변경은 forward migration으로 추가

### Cleanup Worker 감사 완료와 LOW backlog

V13 Cleanup Worker는 `86387e7c227ede3be96c538aafc48b0205bc5e18` (`feat: add resilient orphan file cleanup worker`)로 main에 병합되었습니다. 최종 보안·동시성 감사는 CRITICAL/HIGH/MEDIUM finding 없이 통과했으며, descriptor-relative 삭제, symlink·TOCTOU 방어, stale claim fencing, recovery 수렴을 포함합니다.

- V13에 `claim_version >= 0` CHECK 제약이 없습니다.
- populated V12 cleanup row의 V13 backfill 전용 회귀 테스트가 없습니다.
- `SecureDirectoryStream` 미지원 filesystem에서는 경로 기반 삭제로 fallback하지 않고 fail-closed하므로 자동 Cleanup이 동작하지 않을 수 있습니다. Quickstart·운영 제약은 단계 2에서 명시합니다.

## 외부 의존성과 실행 경계

- Java 17, Spring Boot 4.1, Gradle Wrapper 9.5.1
- Spring MVC·Security·JPA·JdbcTemplate·Flyway
- PostgreSQL 16+pgvector 0.8.2; `compose.yaml`은 DB·backend·frontend 3개 service 실행
- Apache PDFBox 3.0.3
- Spring AI Ollama와 host `bge-m3`; model 자동 다운로드 안 함
- 로컬 filesystem 원본 저장; object storage와 분산 durable storage 아님
- React 19, TypeScript 6, Vite 8

신규 사용자를 위한 signup/demo USER bootstrap이 없어 README 절차만으로 첫 Career Vault 흐름을 완주할 수 없습니다. OpenSQL profile과 조건부 integration test는 존재하지만 PostgreSQL 성공은 OpenSQL 성공이 아니며 OpenSQL·OpenProxy·OpenHA는 실제 환경 미검증입니다. 다음 기술 작업은 OpenSQL 단일 환경 migration·vector 검색, OpenProxy runtime 연결, OpenHA 장애전환·검색 복구 검증 순서입니다.

## 알려진 문서·환경 예제 불일치

다음 항목은 현재 실행 동작이 아니라 문서 주석과 설정 예제의 기술 부채입니다. 단계 0은 기준선에 이를 공개만 하고 Java source와 `.env.example`은 수정하지 않습니다. JavaDoc은 모두 단계 3의 port·adapter 경계 작업에서 실제 책임에 맞게 고칠 후속 대상입니다.

- `DocumentController`의 upload 메서드 JavaDoc은 `TXT 원본`만 QUARANTINED version으로 등록한다고 한정하지만, 실제 endpoint는 `DocumentUploadService`를 통해 TXT와 PDF를 모두 등록합니다. 런타임 지원 결함이 아니라 오래된 메서드 설명입니다.
- `DocumentUploadService`의 클래스 JavaDoc은 TXT 원본만 검증·저장한다고 설명하지만, 실제 구현은 확장자를 판별해 TXT와 PDF를 저장하고 PDF를 업로드 단계에서 검증합니다. 런타임 지원 결함이 아니라 오래된 클래스 설명입니다.
- `DocumentIndexingProcessor`의 클래스 JavaDoc은 락 없는 처리 구간에서 TXT를 읽는다고 설명하지만, 실제 Worker는 version의 file type에 따라 TXT 또는 PDF를 추출하고 TXT `TEXT_CHUNK`와 PDF `PAGE` 출처를 만듭니다. 런타임 지원 결함이 아니라 오래된 클래스 설명입니다.
- `IngestionProperties`의 클래스 JavaDoc은 TXT 청크 분할과 Worker 주기 설정으로 한정하지만, 실제 chunk·lease·polling·recovery 설정은 TXT 전체와 PDF 페이지 텍스트를 처리하는 공통 ingestion Worker에 적용됩니다. 런타임 설정 결함이 아니라 오래된 클래스 설명입니다.
- `TextChunker`의 클래스 JavaDoc은 TXT 문자열만 청킹한다고 설명하지만, 실제 컴포넌트는 TXT 전체 텍스트와 PDF에서 추출한 페이지별 텍스트에 공통으로 사용됩니다. 런타임 청킹 결함이 아니라 오래된 클래스 설명입니다.

`src/main/java`의 JavaDoc과 일반 주석을 전수 검색한 결과, TXT/PDF 지원 범위를 잘못 한정하는 설명은 위 다섯 곳입니다. `PdfExtractionProperties`의 PDF 페이지 추출 한도 설명과 TXT UTF-8 decode·빈 TXT 오류·`TEXT_CHUNK` 분기처럼 특정 형식의 실제 동작만 설명하는 코드와 메시지는 현재 구현에 맞으므로 기술 부채에서 제외합니다.

`.env.example`은 2026-07-23에 TXT/PDF 처리 제한과 V13 Cleanup Worker 설정을 `application.yml` 기본값에 맞춰 동기화했습니다.

## 검증 기준

Repository가 요구하는 명령:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

Integration test는 Testcontainers PostgreSQL 16+pgvector, Flyway, 실제 host Ollama를 사용하며 `OpenSqlInfrastructureTest`만 `RUN_OPENSQL_TESTS` 환경변수가 있어야 실행됩니다. PostgreSQL 결과를 OpenSQL, OpenProxy, OpenHA 검증 결과로 표현하지 않습니다. 실제 환경 Gate는 [OpenSQL 기술 Gate](opensql-gate.md)에 분리되어 있습니다.

단계 0 최종 독립 재검토는 제품 경계, canonical module graph와 당시 기술 부채 기록을 PASS로 판정했습니다. 현재 환경별 실행 결과는 [PRZ-000 evidence](../specs/PRZ-000-platform-baseline/evidence.md), 시간순 변경·검증 이력은 [개발 기록](development-log.md)을 기준으로 확인합니다.

## 계획 기능: 현재 구현 아님

- 재사용 가능한 Engine module, Spring Boot starter와 adapter contract test kit
- clean-clone Docker Quickstart와 안전한 demo USER 생성 경로
- canonical SourceLocator, quote hash, parser/chunker/model provenance
- idempotency key와 처리상태 직접 조회·재시도 API
- CareerFact 후보·확인·거절과 `INSUFFICIENT_EVIDENCE`
- 검증된 CareerFact만 사용하는 JSON·Markdown portfolio와 source manifest
- `/api/v1` OpenAPI, capability/provider 조회, MCP, webhook/outbox
- workspace, career profile, membership와 기관 권한
- DOCX, PPTX, OCR, 여러 vector DB·embedding·object storage adapter
- 실제 OpenSQL·OpenProxy·OpenHA 호환성과 장애전환

## 문서 역할

- [README](../README.md): 프로젝트 소개, 현재 실행 진입점과 Quickstart 한계
- [현재 구현 현황](project-status.md): 지금 가능한 기능과 미구현 범위
- [오픈소스 제품 경계](architecture/oss-product-boundary.md): Engine·adapter·Reference App 책임과 보존 기준선
- [오픈소스 엔진 전환 실행 계획](oss-transition-execution-plan.md): 단계별 미래 작업과 검토 상태
- [최종 기획안](PRIZM_최종_기획안.md): 장기 목표와 보존된 제품·시장 가설
- [개발 기록](development-log.md): 날짜별 변경·검증 이력
- [OpenSQL 기술 Gate](opensql-gate.md): 실제 OpenSQL 환경 검증 체크리스트
- [2026 티맥스티베로 지정과제 대응 계획](contest/2026-tmaxtibero-plan.md): 검증 우선순위와 제출 일정
- [검색 품질 평가](search-evaluation.md): 합성 Dense 검색 평가 방법과 재현 절차
- [브랜치 운영 정책](branch-policy.md): `main` 단일 장기 브랜치와 실험 보존 기준
- [수치와 구현 근거](portfolio/metrics-and-evidence.md): 현재 코드·검증·실험 수치의 상태 분리
- [Spec Registry](../specs/README.md): 기존 구현의 `AS_BUILT_BASELINE`과 이후 기능 spec 상태
- `docs/verification/`: 특정 초기 구현 시점의 역사적 상세 기록
