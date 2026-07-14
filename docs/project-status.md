# PRIZM 현재 구현 현황

> 기준일: 2026-07-14
> 이 문서는 현재 구현의 단일 요약 문서입니다. 세부 구현 여부는 항상 소스 코드, Flyway migration, 실행 가능한 테스트를 최종 기준으로 판단합니다. 장기 목표는 [PRIZM 최종 기획안](PRIZM_최종_기획안.md), 변경 이력은 [개발 기록](development-log.md)에서 확인합니다.

## PRIZM은 어떤 서비스인가요?

PRIZM은 이력서, 포트폴리오, 프로젝트 보고서, 학교 과제, 자격증, 채용공고 같은 개인 커리어 문서를 쌓아 두고 나중에 필요한 경험의 **원문 근거**를 찾는 문서 플랫폼입니다.

AI가 등록된 문서에 없는 경력, 기술, 성과, 수치를 새로 만들지 않는 것이 핵심 원칙입니다. 근거가 없으면 사용자의 주장이 거짓이라고 단정하지 않고, 현재 PRIZM에 등록된 문서에서는 근거를 찾지 못했다고 안내합니다.

## 한눈에 보는 현재 흐름

```text
로그인
→ Career Vault에서 내 문서 목록과 유형 확인
→ TXT 또는 텍스트 PDF와 문서 유형 업로드
→ QUARANTINED 상태와 원본 파일 저장
→ Worker가 텍스트 추출·청킹·임베딩 수행
→ 모든 청크가 준비되면 현재 버전을 ACTIVE로 원자적 전환
→ 내 ACTIVE 문서에서 원문과 TXT 구간 또는 PDF 페이지 출처 검색
```

새 문서 버전 처리에 실패해도 기존 `active_version_id`는 유지됩니다. 다른 사용자의 문서·버전·작업·청크는 검색 후보에도 포함되지 않습니다.

## 현재 구현된 기능

### 인증과 사용자 격리

- 이메일·비밀번호 로그인과 JWT Access Token 발급
- 토큰 검증 후 DB에서 사용자 활성 상태와 역할 재확인
- `/api/users/me` 현재 사용자 조회와 프런트엔드 세션 복구
- 로그아웃 및 401·403 발생 시 토큰과 사용자 정보 삭제
- 문서·버전·처리 작업·청크의 사용자별 소유권
- 일반 사용자의 목록·상세·검색 결과 격리
- `SYSTEM_ADMIN`의 개인 문서·검색 API 접근 차단

### Career Vault 프런트엔드

- 로그인·로그아웃과 현재 사용자 이메일 표시
- 내 문서 목록, 로딩·빈 목록·오류 상태
- 단일 문서 유형 필터
- TXT·PDF 단일 파일 업로드와 최대 10MB 사전 검사
- 업로드 성공 후 현재 필터를 유지한 목록 재조회
- `POST /api/search`를 사용하는 자연어 단일 결과 검색
- 문서 제목, 원문, `텍스트 구간 N` 또는 `N페이지` 출처 표시
- 검색 결과 없음과 처리 중 문서 제외 가능성을 중립적으로 안내

프런트엔드는 아직 문서 상세·삭제·수정, 다중 Career Evidence 결과, PDF 미리보기를 제공하지 않습니다.

### 문서 등록과 유형

- TXT와 텍스트 레이어가 있는 비암호화 PDF 업로드
- 원본 파일 로컬 저장과 SHA-256 해시 기록
- 문서와 문서 버전 분리, 업로드 직후 `QUARANTINED` 적용
- 파일명·빈 파일·확장자·최대 10MB 검증
- 손상·암호화·무텍스트 PDF를 `INVALID_DOCUMENT_CONTENT`로 거부
- 12개 `DocumentType` 저장, 생략 시 `OTHER`
- 사용자 소유권과 문서 유형을 함께 적용하는 목록 필터

지원 문서 유형은 다음과 같습니다.

`RESUME`, `COVER_LETTER`, `PORTFOLIO`, `PROJECT_REPORT`, `PRESENTATION`, `CERTIFICATE`, `COURSE_COMPLETION`, `SCHOOL_ASSIGNMENT`, `CAREER_REVIEW`, `JOB_POSTING`, `INTERVIEW_FEEDBACK`, `OTHER`

### PDF 처리 안전장치

- Apache PDFBox 3.0.3으로 실제 PDF 구조 확인
- 페이지별 텍스트 추출과 빈 페이지 제외
- 최대 300페이지 제한
- 페이지별 `strip()` 결과를 누적한 최대 2,000,000자 제한
- 두 제한은 `prizm.document.pdf` 설정과 환경변수로 재정의 가능
- 제한 초과 시 업로드 단계에서 문서·버전·작업 생성 전 거부
- Worker에서 발견하면 영구 오류로 재시도 없이 FAILED 처리
- 제한 초과 시 청크와 새 ACTIVE 버전이 생성되지 않고 기존 ACTIVE 버전 유지

### 비동기 색인과 장애 복구

- `QUARANTINED → PROCESSING → ACTIVE` 문서 버전 흐름
- processing job 선점을 위한 `SELECT ... FOR UPDATE SKIP LOCKED`
- DB 시간 기반 lease, 재시도와 backoff, 만료 작업 회수
- `claim_version` fencing으로 이전 Worker의 늦은 완료 차단
- 텍스트 추출·PDF 파싱·Ollama 호출을 완료 트랜잭션 밖에서 수행
- 청크 저장, 버전 ACTIVE, `active_version_id` 변경, 작업 COMPLETED를 하나의 트랜잭션으로 처리
- 처리 실패 시 부분 청크 제거와 기존 ACTIVE 버전 유지

현재 lease는 청크 처리 간격으로 갱신합니다. PDF 추출 중 별도 heartbeat와 처리 timeout은 아직 구현하지 않았습니다.

### 청크·임베딩·출처

- 기존 청킹 알고리즘을 사용한 TXT·PDF 페이지별 청킹
- Ollama `bge-m3` 1024차원 임베딩
- 공통 `EmbeddingValidator`의 차원·유한값·0보다 큰 L2 norm 검사
- 0-norm 벡터의 DB 저장과 pgvector 검색 차단
- TXT 출처: `TEXT_CHUNK`, 1부터 시작하는 `sourceIndex`, `텍스트 구간 N`
- PDF 출처: `PAGE`, 실제 1부터 시작하는 페이지 번호, `N페이지`
- 한 PDF 페이지가 여러 청크로 나뉘어도 같은 PAGE 출처 유지

### 검색

- PostgreSQL pgvector의 exact cosine 검색
- 문서·버전·청크의 `owner_user_id` 조건을 SQL 후보 단계에서 적용
- `ACTIVE` 상태이며 `documents.active_version_id`와 일치하는 버전만 검색
- 기존 단일 검색은 최대 1개를 반환하고 결과 없음은 404 `SEARCH_NO_RESULT`
- Career Evidence 검색은 관련도 순으로 최대 5개를 반환하고 결과 없음은 HTTP 200 빈 배열
- 문서·버전·청크 ID, 제목, 원문, 출처, distance와 score 반환
- 검색 임베딩 검증 실패 시 pgvector SQL 실행 전 중단

## 현재 API

| API | 현재 동작 |
|---|---|
| `POST /api/auth/login` | 로그인과 Access Token 발급 |
| `GET /api/users/me` | 현재 인증 사용자 조회 |
| `POST /api/documents` | `title`, 선택적 `documentType`, TXT·PDF `file` 업로드 |
| `GET /api/documents` | 내 문서 목록 |
| `GET /api/documents?documentType=PORTFOLIO` | 내 문서 유형별 목록 |
| `GET /api/documents/{documentId}` | 내 문서 상세 조회 |
| `POST /api/search` | 내 ACTIVE 청크 중 단일 검색 결과 |
| `POST /api/career-evidence/search` | 내 ACTIVE 청크 중 최대 5개 근거 배열 |
| `GET /actuator/health` | 애플리케이션 상태 확인 |

## 데이터베이스와 migration

- PostgreSQL 16과 pgvector 0.8.2를 로컬·통합 테스트 기준으로 사용
- Flyway V1~V11 적용
- V9: `documents.document_type`과 12종 CHECK, 기존 문서 `OTHER` 보정
- V10: TXT 청크 출처 컬럼과 기존 데이터 backfill
- V11: PDF 파일 유형과 `PAGE` 출처 허용
- 이미 적용된 migration은 수정하지 않고 이후 변경은 새 버전으로 추가

## 검증 상태

2026-07-14 현재 최신 실행 결과는 다음과 같습니다.

- 단위 테스트: 128개 성공
- 통합 테스트: 45개 중 PostgreSQL·pgvector·실제 Ollama 테스트 44개 성공
- OpenSQL 실환경 테스트: 접속 정보가 필요한 기존 정책에 따라 1개 제외
- Docker Desktop 28.2.2와 Ollama `bge-m3:latest` 사용 확인
- `git diff --check` 성공

PostgreSQL 결과를 OpenSQL, OpenProxy, OpenHA 검증 결과로 표현하지 않습니다. 실제 환경 Gate는 [OpenSQL 기술 Gate](opensql-gate.md)에 별도로 기록합니다.

## 아직 구현하지 않은 기능

- DOCX, PPTX, OCR과 스캔 PDF 인식
- 문서 새 버전 업로드 API, 문서 삭제·유형 수정
- 프런트엔드 문서 상세, 삭제·수정, PDF 미리보기
- 프런트엔드 Career Evidence 다중 결과 화면
- PDF 처리 timeout, PDF 추출 중 lease heartbeat, 최대 청크 수 제한
- 문서 유형 자동 분류와 구조화 메타데이터 추출
- Career Evidence Card 저장과 사용자 확인 상태
- 채용공고 요구사항 분석, 경험 매칭, 지원 패키지
- 생성형 AI 답변과 MCP endpoint
- 페이지 좌표, 문자 offset, 검색 기록과 페이지네이션
- 실제 OpenSQL·OpenProxy·OpenHA 호환성과 장애전환 검증

## 문서 역할

- [README](../README.md): 프로젝트 소개와 실행 진입점
- [현재 구현 현황](project-status.md): 지금 가능한 기능과 미구현 범위의 단일 기준
- [최종 기획안](PRIZM_최종_기획안.md): 장기 제품 목표와 설계 가설
- [개발 기록](development-log.md): 날짜별 변경·검증 이력
- [OpenSQL 기술 Gate](opensql-gate.md): 실제 OpenSQL 환경 검증 체크리스트
- `docs/verification/`: 특정 초기 구현 시점의 상세 검증 기록
