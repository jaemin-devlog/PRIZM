# PRIZM 현재 구현 현황

> 기준일: 2026-07-23
>
> 기준선: `PRZ-000 AS_BUILT_BASELINE`
>
> 최종 판단 기준: source code, Flyway migration과 실행 가능한 test

## 한눈에 보기

| 구분 | 현재 상태 |
|---|---|
| 구현됨 | 로그인, 사용자별 문서 격리, TXT/PDF 업로드, version 관리, 비동기 색인·복구, pgvector 검색, Career Vault 문서 관리 |
| 현재 단계 | 기존 구현 기준선 완료, 대회 대응 P0 공식 요구사항·오픈소스 준비 |
| 미구현 | CareerFact, 근거 기반 portfolio, `/api/v1`, MCP, 독립 Engine package, OpenSQL 실환경 호환성 |

PRIZM의 목표는 커리어 문서 분석·구조화·근거 검색·portfolio 생성을 위한 오픈소스
Career Intelligence Engine과 Reference App이다. 현재 저장소는 그 목표 전체가 아니라
하나의 Spring Boot 애플리케이션과 React **Career Vault Reference App**으로 구현된
플랫폼 기반이다.

`FileStorage`와 `EmbeddingService` 인터페이스는 존재하지만 PDFBox, 고정 chunker,
Ollama와 pgvector JDBC 경로는 아직 단일 애플리케이션에 직접 결합되어 있다. 공개
adapter 체계나 독립 SDK가 구현된 상태는 아니다.

## 현재 사용자 흐름

```text
로그인
→ 내 문서 목록 확인
→ UTF-8 TXT 또는 text-layer PDF 업로드
→ 원본·QUARANTINED version·processing job 저장
→ Worker가 추출·청킹·1024차원 embedding 수행
→ 성공한 version을 ACTIVE로 원자적 전환
→ 내 ACTIVE 문서에서 원문과 페이지·텍스트 구간 근거 검색
```

새 version 처리가 실패해도 기존 `active_version_id`는 유지된다. 다른 사용자의
document, version, job과 chunk는 검색 후보에 포함되지 않는다.

## 현재 구현된 기능

### 인증과 사용자 격리

- 이메일·비밀번호 로그인과 HS256 JWT Access Token
- stateless Bearer 인증과 명시적으로 설정된 HTTP(S) CORS origin
- 요청마다 DB에서 사용자 활성 상태·email·role 재확인
- document, version, processing job과 chunk의 owner 일치 보장
- 일반 `USER`의 owner-scoped API와 `SYSTEM_ADMIN`의 개인 문서 API 접근 차단

회원가입, 일반 `USER` bootstrap, refresh token, OIDC와 API key는 없다.

### Career Vault Reference App

- `/login`
- `/career-vault/documents`: 목록·유형/제목/상태 필터, 상세·수정·삭제, version 이력
- `/career-vault/upload`: TXT/PDF 최초 업로드와 새 version 등록
- `/career-vault/evidence`: 자연어 질문과 최대 5개 원문 근거
- owner-scoped PDF thumbnail과 원본 viewer
- 근거 없음은 “현재 PRIZM에 등록된 문서에서는 관련 근거를 찾지 못했습니다”로 안내

처리상태 자동 polling, CareerFact 확인과 portfolio 생성은 제공하지 않는다.

### 문서·색인·검색

- UTF-8 TXT와 비암호화 text-layer PDF, 최대 10MiB
- PDF 최대 300페이지·추출 문자 2,000,000자
- 12개 `DocumentType`, 생략 시 `OTHER`, owner-scoped type filter
- 원본 로컬 저장, SHA-256 hash와 immutable version
- TXT `TEXT_CHUNK`, PDF `PAGE`, 1부터 시작하는 source index와 label
- Ollama `bge-m3`, 1024차원·finite 값·0보다 큰 L2 norm 검증
- PostgreSQL pgvector exact cosine `<=>`, owner와 ACTIVE version을 SQL 후보 단계에 적용
- 단일 검색은 최대 1개이며 결과 없음은 404 `SEARCH_NO_RESULT`
- Career Evidence는 최대 5개이며 결과 없음은 HTTP 200 빈 배열
- `score = 1 - distance`; 정확도나 확률로 해석하지 않음

OCR, image-only PDF, DOCX, PPTX, 검색 threshold, ANN index와 검색 기록은 지원하지
않는다.

### 비동기 처리와 파일 정리

- indexing: 기본 10분 lease, 1/3 주기 heartbeat, `FOR UPDATE SKIP LOCKED`
- indexing: 최대 3회·1/5/15분 retry/backoff, recovery와 `claim_version` fencing
- chunk 교체, version ACTIVE, `active_version_id`와 job 완료의 원자적 transaction
- Cleanup: 기본 5분 lease, heartbeat 없이 retry/backoff·recovery·fencing
- `SecureDirectoryStream` descriptor-relative 삭제와 symlink·TOCTOU 방어
- 지원하지 않는 filesystem에서는 안전하지 않은 경로 삭제로 fallback하지 않고 fail-closed

## 현재 API 범위

| 영역 | endpoint |
|---|---|
| 인증 | `POST /api/auth/login`, `GET /api/users/me` |
| 문서 | `POST/GET /api/documents`, `GET/PATCH/DELETE /api/documents/{documentId}` |
| version·PDF | `POST /api/documents/{documentId}/versions`, thumbnail·original 조회 |
| 검색 | `POST /api/search`, `POST /api/career-evidence/search` |
| 상태 | `GET /actuator/health` |

현재 API는 `/api/v1` public API나 OpenAPI 계약이 아니다. 처리 job 직접 조회·수동
재시도와 idempotency key도 없다.

## 플랫폼 기준선

- Java 17, Spring Boot 4.1, Gradle Wrapper
- React, TypeScript와 Vite
- PostgreSQL 16+pgvector, Flyway V1~V13
- JPA metadata와 JdbcTemplate vector 검색·job claim
- Apache PDFBox와 host Ollama `bge-m3`
- 로컬 filesystem 원본 저장

적용된 Flyway migration은 수정하지 않고 이후 변경을 forward migration으로 추가한다.

## 검증 상태

| 대상 | 상태 | 최근 기록 |
|---|---|---|
| Backend `test` task | `PASS` | 242건 중 228건 성공, 환경 조건 14건 skip, 실패·오류 0건 |
| Frontend lint·build | `PASS` | ESLint와 production build 통과 |
| PostgreSQL·pgvector integration | `HISTORICAL_PASS_NOT_RERUN` | 기존 성공 기록은 있으나 2026-07-23 기준선 작업에서 재실행하지 않음 |
| Dense 검색 평가 | `HISTORICAL_PASS_NOT_RERUN` | 2026-07-14 합성 기준선 보존 |
| Docker Compose | `PASS` | 2026-07-29 clean-clone에서 config·build·기동과 backend·frontend health 확인. demo `USER` 기반 전체 사용자 흐름은 `NOT_RUN` |
| Ollama `bge-m3` | `NOT_RUN` | 기준선 문서 작업에서 사용하지 않음 |
| OpenSQL·OpenProxy·OpenHA | `NOT_RUN` | 실제 대상 환경 검증 없음 |

세부 환경별 결과와 코드·test 연결은
[PRZ-000 Evidence](../specs/PRZ-000-platform-baseline/evidence.md), T-08 clean-clone
결과는 [PRZ-002 Evidence](../specs/PRZ-002-open-source-readiness/evidence.md)를 기준으로 한다.

## 알려진 한계

- README 절차만으로 사용할 수 있는 안전한 demo `USER`가 없다.
- 전체 처리 timeout과 version당 최대 chunk 수 제한이 없다.
- 프런트엔드 자동 UI test suite가 없다.
- V13에 `claim_version >= 0` CHECK와 populated V12 row backfill 전용 회귀 test가 없다.
- 일부 JavaDoc이 현재 TXT/PDF 공통 동작을 TXT 전용으로 설명한다.
- `SecureDirectoryStream` 미지원 filesystem에서는 자동 Cleanup이 동작하지 않을 수 있다.
- OpenSQL profile과 조건부 test는 있지만 실제 OpenSQL 호환성은 검증하지 않았다.

## 계획 기능: 현재 구현 아님

- 안전한 demo `USER`와 이를 이용한 clean-clone 로그인→업로드→ACTIVE→검색 전체 재현
- canonical source, quote hash와 처리 provenance
- CareerFact 후보·확인·거절과 `INSUFFICIENT_EVIDENCE`
- 검증된 CareerFact만 사용하는 JSON·Markdown portfolio와 source manifest
- `/api/v1`, OpenAPI, MCP와 webhook/outbox
- 독립 Engine artifact, Spring Boot starter와 adapter contract test kit
- workspace, career profile, membership와 기관 권한
- 여러 vector DB·embedding·object storage adapter
- 실제 OpenSQL·OpenProxy·OpenHA 호환성과 장애전환

앞으로의 순서는 [개발 로드맵](roadmap.md), 대회 일정은
[티맥스티베로 과제 대응 계획](contest/2026-tmaxtibero-plan.md)을 따른다. 전체 문서
안내는 [문서 안내](README.md)에서 확인한다.
