# 개발 기록

PRIZM의 기능, 리팩토링, 설계 판단과 검증 결과를 짧게 남기는 기록이다. 포트폴리오와 멘토링에서 개발 흐름을 설명할 수 있도록 작성하되, 일일 작업 내역이나 세부 구현을 모두 옮기지는 않는다.

## 작성 원칙

- 기능 추가, 의미 있는 리팩토링, 설계 결정, 외부 인프라 검증을 마칠 때 한 항목을 추가한다.
- 항목은 **무엇을 변경했는지**, **왜 필요한지**, **어떻게 확인했는지**만 3~5줄로 쓴다.
- 검증하지 못한 조건은 완료로 표현하지 않는다. 비밀번호, 주소, 토큰, 문서 본문은 기록하지 않는다.
- 단순 포맷 변경, 생성 파일, 진행 중인 실험은 기록하지 않는다. 날짜가 같은 작업은 가능한 한 한 항목으로 묶는다.

## 기록 양식

```md
## YYYY-MM-DD — 제목

- 변경: 무엇을 추가·수정·정리했는지
- 이유: 해결하려는 문제 또는 선택한 기준
- 검증: 실행한 테스트·확인 결과
- 다음: 남은 조건 또는 다음 단계 (없으면 생략)
```

## 2026-07-11 — 초기 개발 환경 구성

- 변경: Spring Boot 모듈형 모놀리스, React/Vite 프런트엔드, PostgreSQL 16 + pgvector Compose 환경과 Flyway 기반 스키마 관리를 구성했다.
- 이유: 도메인 기능보다 먼저 OpenSQL 이전이 가능한 벡터 검색 개발 기준과 재현 가능한 로컬 환경을 확보하기 위해서다.
- 검증: pgvector 0.8.2 Testcontainers 환경에서 Flyway 적용과 1024차원 exact cosine 검색을 수행하는 통합 테스트를 추가했다.
- 다음: 실제 OpenSQL, OpenProxy, OpenHA 환경은 별도 기술 Gate에서 검증한다.

## 2026-07-13 — DB·Flyway·pgvector 실행 검증

- 변경: 현재 Compose 설정으로 별도 PostgreSQL+pgvector 검증 DB를 기동하고, migration 계정과 runtime 계정을 분리한 접속 경로를 확인했다.
- 이유: 설정 파일만으로 DB, Flyway, 벡터 연산이 동작한다고 가정하지 않기 위해서다.
- 검증: Flyway V1 성공 이력, pgvector 0.8.2, `vector(1024)`, 동일 벡터 cosine distance `0`을 확인했다. runtime 계정에는 DB와 `public` 스키마의 `CREATE` 권한이 없음을 확인했고, Testcontainers 및 설정된 런타임 endpoint 대상 통합 테스트가 모두 성공했다.
- 다음: 이 결과는 PostgreSQL 개발 환경 검증이다. 실제 OpenSQL/OpenProxy/OpenHA 접속 정보와 설치 환경이 확보되면 같은 smoke test로 별도 기록한다.

## 2026-07-13 — 최소 벡터 검색 세로 흐름

- 변경: `document_chunks` V2 migration, Ollama 임베딩 어댑터, JdbcTemplate exact cosine 검색, `POST /api/search`와 오류 응답을 추가했다.
- 이유: 업로드·권한 기능을 넣기 전에 실제 로컬 모델과 pgvector가 연결된 가장 작은 검색 경로를 검증하기 위해서다.
- 검증: 실행 중인 `bge-m3:latest`가 1024차원 벡터를 반환하는 것을 확인했고, 세 문장을 저장한 뒤 휴가 질문에서 연차 신청 문장이 최상위로 반환되는 PostgreSQL 통합 테스트를 통과했다.
- 다음: 이 검색 경로에 업로드와 문서 상태를 연결하되, 승인 전 문서는 검색하지 않는 규칙을 추가한다.
- 상세 결과: [최소 벡터 검색 구현·검증 기록](verification/2026-07-13-minimal-vector-search.md)

## 2026-07-13 — 최소 문서 등록 세로 흐름

- 변경: TXT 업로드 파일을 로컬 저장소에 보관하고, `documents`·`document_versions`에 `QUARANTINED` 메타데이터를 기록·조회하는 API를 추가했다.
- 이유: 임베딩·승인 처리 전에 원본 파일, 버전, 격리 상태를 분리해 안전하게 연결할 최소 기반이 필요했다.
- 검증: Flyway V1~V3를 빈 개발 DB에 적용하고, 통합 테스트가 문서·버전·청크를 직접 생성해 단위 테스트와 Docker Testcontainers·실제 Ollama 통합 테스트를 통과했다.
- 다음: QUARANTINED 버전의 TXT 내용 추출과 청크 생성은 승인 흐름을 설계한 뒤 별도 단계로 추가한다.
- 상세 결과: [최소 문서 등록 구현·검증 기록](verification/2026-07-13-minimal-document-registration.md)

## 2026-07-13 — 프로젝트 현황 안내 문서

- 변경: 기술 배경이 없는 사람도 현재 구현 범위와 다음 단계를 이해할 수 있도록 `docs/project-status.md`를 추가했다.
- 이유: 포트폴리오·멘토링에서 구현 상태와 남은 범위를 짧고 정확하게 설명하기 위해서다.
- 검증: 실제 구현된 벡터 검색, TXT 등록, 테스트 결과만 반영하고 아직 없는 PDF·승인·권한 기능은 다음 단계로 구분했다.

## 2026-07-13 — 역할별 패키지 구조 정리

- 변경: `search`, `document`, `embedding`을 controller·DTO·entity·repository·service·exception 하위 패키지로 재배치하고 테스트도 같은 구조로 맞췄다.
- 이유: 기능이 늘어날 때 한 패키지에 클래스가 섞이지 않도록 책임과 탐색 경로를 명확히 하기 위해서다.
- 리팩터링: 벡터 Repository가 HTTP 응답 DTO를 직접 반환하지 않고 `VectorSearchResult`를 반환하며, Service가 API 응답으로 변환하도록 의존 방향을 정리했다.
- 검증: Java 컴파일, 단위 테스트, Testcontainers PostgreSQL·실제 Ollama 통합 테스트가 모두 통과했다.

## 2026-07-13 — TXT 문서 승인·색인·검색 연결

- 변경: 문서 승인 API, `processing_jobs`, SKIP LOCKED Worker, TXT 청크 분할·BGE-M3 임베딩과 ACTIVE 문서 전용 출처 검색을 연결했다.
- 이유: 업로드된 격리 문서가 승인과 색인을 모두 마친 뒤에만 검색되도록 상태와 원본·벡터 정합성을 보장하기 위해서다.
- 설계: 파일·Ollama 처리는 작업 선점 트랜잭션 밖에서 수행하고, 청크 저장·활성 버전 전환·작업 완료는 한 DB 트랜잭션으로 확정한다. 일시 오류는 1·5·15분 간격으로 최대 세 번 재시도한다.
- 검증: 단위 테스트와 PostgreSQL 16·pgvector 0.8.2·실제 Ollama `bge-m3` 통합 테스트에서 업로드부터 승인·1024차원 색인·출처 검색까지 통과했다.
- 다음: OpenSQL·OpenProxy·OpenHA는 실제 환경에서 아직 검증하지 않았으며, 다음 단계에서 관리자 인증과 C/S/O 정책을 승인 흐름에 연결한다.
