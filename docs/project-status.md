# PRIZM 현재 구현 현황

> PRIZM은 개인의 이력서, 포트폴리오, 프로젝트 보고서 같은 커리어 문서를 쌓아 두고,
> 나중에 실제 경험의 원문 근거를 찾을 수 있도록 돕는 문서 플랫폼입니다.

## 한눈에 보기

현재 PRIZM은 **TXT 문서를 안전하게 등록하고, 의미가 비슷한 내용을 검색하는 기반**까지 구현됐습니다.

```text
로그인
→ TXT 문서와 문서 유형 등록
→ QUARANTINED 상태로 안전하게 저장
→ 비동기 색인과 임베딩 생성
→ 문서 유형으로 내 목록 필터링
→ ACTIVE 문서만 자연어 검색
```

AI는 등록된 문서에 없는 경험, 기술, 성과, 수치를 새로 만들지 않습니다.
근거를 찾지 못하면 현재 등록된 자료에서 근거를 찾지 못했다고 안내하는 방향으로 개발합니다.

## 현재 가능한 기능

### 로그인

- `/login`에서 이메일과 비밀번호로 로그인
- JWT Access Token을 저장한 뒤 현재 사용자 정보를 다시 확인
- 확인이 끝나면 임시 Career Vault 화면으로 이동
- 로그아웃하면 저장된 토큰과 사용자 정보를 함께 삭제

Career Vault의 문서 목록·업로드·검색 화면은 아직 구현하지 않았습니다.

### 문서 등록과 보관

- TXT 파일 업로드
- 빈 파일, 허용되지 않은 확장자, 과도한 크기, 경로 조작 파일명 차단
- 원본 파일의 로컬 저장과 SHA-256 해시 기록
- 문서와 문서 버전 분리 관리
- 업로드 직후 `QUARANTINED` 상태 적용

### 문서 유형 저장

업로드할 때 문서 유형을 선택할 수 있습니다. 예를 들어 `RESUME`, `PORTFOLIO`,
`PROJECT_REPORT`, `CERTIFICATE`, `JOB_POSTING` 등을 저장할 수 있습니다.

유형을 선택하지 않으면 `OTHER`로 저장됩니다. 현재는 저장과 조회만 지원하며,
목록에서 한 가지 유형을 선택해 필터링할 수 있습니다. 여러 유형을 한 번에 고르는 필터와
AI 자동 분류는 아직 없습니다.

### 비동기 색인과 검색

- Worker가 TXT를 청크로 나누고 Ollama `bge-m3`로 1024차원 임베딩 생성
- PostgreSQL pgvector의 exact cosine 검색
- 처리 완료된 `ACTIVE` 문서의 활성 버전만 검색
- Worker 중단 시 lease, 재시도, backoff, claim-version fencing으로 복구

### 사용자별 데이터 격리

- 업로드한 문서·버전·처리 작업·청크는 인증된 사용자에게 귀속
- 문서 목록, 상세 조회, 자연어 검색은 사용자별로 분리
- 다른 사용자의 문서는 목록·상세·검색 결과에 포함되지 않음
- `SYSTEM_ADMIN`은 개인 문서와 검색 결과에 접근할 수 없음

## 현재 API

- `POST /api/auth/login`: 로그인
- `GET /api/users/me`: 현재 사용자 조회
- `POST /api/documents`: TXT와 선택적 `documentType` 등록
- `GET /api/documents`: 내 문서 목록 조회
- `GET /api/documents?documentType=PORTFOLIO`: 내 포트폴리오 문서만 조회
- `GET /api/documents/{documentId}`: 내 문서 상세 조회
- `POST /api/search`: 내 ACTIVE 문서의 자연어 검색
- `GET /actuator/health`: 상태 확인

## 아직 구현하지 않은 기능

- 문서 유형 수정 API와 여러 조건 필터
- Career Vault 문서 목록, 업로드, 검색 화면
- PDF, DOCX, PPTX, OCR
- 문서 유형 자동 분류와 메타데이터 추출
- 경력 근거 카드, 채용공고 분석, 지원 패키지, 면접 기능
- 생성형 AI 답변
- 실제 OpenSQL, OpenProxy, OpenHA 환경 검증

## 기술 기반과 검증 상태

- Java 17, Spring Boot, React/Vite
- PostgreSQL 16, pgvector 0.8.2, Flyway V1~V9
- Ollama `bge-m3` 1024차원 임베딩
- Docker Compose와 Testcontainers 기반 통합 테스트

현재 PostgreSQL·pgvector·실제 Ollama 환경에서 검증했습니다.
OpenSQL, OpenProxy, OpenHA는 아직 실제 환경 검증을 완료하지 않았습니다.

## 다음 개발 단계

다음 작업은 **Career Vault 문서 목록과 단일 문서 유형 필터 UI**입니다.
그 뒤 문서 업로드·PDF 지원처럼 문서 정보를 더 풍부하게 다루는 기능을 별도 단위로 추가합니다.
