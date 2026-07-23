# 개발 기록

> PRIZM의 기능, 리팩토링, 설계 판단과 검증 결과를 오래된 기록부터 시간순으로 남깁니다. 현재 구현의 단일 요약은 [현재 구현 현황](project-status.md)을 기준으로 합니다.

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
- 다음: 이 검색 경로에 업로드와 문서 상태를 연결하되, `ACTIVE`가 아닌 문서는 검색하지 않는 규칙을 추가한다.
- 상세 결과: [최소 벡터 검색 구현·검증 기록](archive/verification/2026-07-13-minimal-vector-search.md)

## 2026-07-13 — 최소 문서 등록 세로 흐름

- 변경: TXT 업로드 파일을 로컬 저장소에 보관하고, `documents`·`document_versions`에 `QUARANTINED` 메타데이터를 기록·조회하는 API를 추가했다.
- 이유: 임베딩 처리 전에 원본 파일, 버전, 격리 상태를 분리해 안전하게 연결할 최소 기반이 필요했다.
- 검증: Flyway V1~V3를 빈 개발 DB에 적용하고, 통합 테스트가 문서·버전·청크를 직접 생성해 단위 테스트와 Docker Testcontainers·실제 Ollama 통합 테스트를 통과했다.
- 다음: QUARANTINED 버전의 TXT 내용 추출과 청크 생성은 비동기 처리 흐름을 설계한 뒤 별도 단계로 추가한다.
- 상세 결과: [최소 문서 등록 구현·검증 기록](archive/verification/2026-07-13-minimal-document-registration.md)

## 2026-07-13 — 프로젝트 현황 안내 문서

- 변경: 기술 배경이 없는 사람도 현재 구현 범위와 다음 단계를 이해할 수 있도록 `docs/project-status.md`를 추가했다.
- 이유: 포트폴리오·멘토링에서 구현 상태와 남은 범위를 짧고 정확하게 설명하기 위해서다.
- 검증: 실제 구현된 벡터 검색, TXT 등록, 테스트 결과만 반영하고 아직 없는 PDF·승인·권한 기능은 다음 단계로 구분했다.

## 2026-07-13 — 역할별 패키지 구조 정리

- 변경: `search`, `document`, `embedding`을 controller·DTO·entity·repository·service·exception 하위 패키지로 재배치하고 테스트도 같은 구조로 맞췄다.
- 이유: 기능이 늘어날 때 한 패키지에 클래스가 섞이지 않도록 책임과 탐색 경로를 명확히 하기 위해서다.
- 리팩터링: 벡터 Repository가 HTTP 응답 DTO를 직접 반환하지 않고 `VectorSearchResult`를 반환하며, Service가 API 응답으로 변환하도록 의존 방향을 정리했다.
- 검증: Java 컴파일, 단위 테스트, Testcontainers PostgreSQL·실제 Ollama 통합 테스트가 모두 통과했다.

## 2026-07-13 — TXT 문서 색인·검색 연결

- 변경: 당시 문서 처리 시작 API, `processing_jobs`, SKIP LOCKED Worker, TXT 청크 분할·BGE-M3 임베딩과 ACTIVE 문서 전용 출처 검색을 연결했다.
- 이유: 업로드된 격리 문서가 색인을 모두 마친 뒤에만 검색되도록 상태와 원본·벡터 정합성을 보장하기 위해서다.
- 설계: 파일·Ollama 처리는 작업 선점 트랜잭션 밖에서 수행하고, 청크 저장·활성 버전 전환·작업 완료는 한 DB 트랜잭션으로 확정한다. 일시 오류는 1·5·15분 간격으로 최대 세 번 재시도한다.
- 검증: 단위 테스트와 PostgreSQL 16·pgvector 0.8.2·실제 Ollama `bge-m3` 통합 테스트에서 업로드부터 처리 시작·1024차원 색인·출처 검색까지 통과했다.
- 다음: OpenSQL·OpenProxy·OpenHA는 실제 환경에서 아직 검증하지 않았으며, 다음 단계에서 인증과 역할 경계를 처리 흐름에 연결한다.

## 2026-07-13 — 중단 작업 lease 복구와 fencing 보강

- 변경: `processing_jobs`에 10분 lease와 `claim_version`을 추가하고, 만료 작업 재예약·최종 실패 처리와 내부 저장 경로 비노출 정책을 적용했다.
- 이유: Worker가 선점 후 종료돼도 작업을 복구하고, 복구 전에 실행되던 Worker가 늦게 돌아와 새 결과를 덮어쓰지 못하게 하기 위해서다.
- 설계: 선점·갱신·재시도 시간은 PostgreSQL 시간을 사용하고, 완료·실패는 현재 `claim_version` 검증을 통과한 뒤에만 청크와 문서 상태를 변경한다.
- 검증: 단위 테스트와 실제 PostgreSQL 독립 트랜잭션 동시 선점, lease 만료 복구, stale 완료 차단 및 실제 Ollama `bge-m3` 검색 통합 테스트를 통과했다.

## 2026-07-13 — 관리자 인증·인가 구현

- 변경: `users` V6 스키마와 BCrypt 로그인, JWT Access Token, 현재 사용자 조회 API를 추가하고 문서·검색 API에 인증을 적용했다.
- 이유: 당시 문서 처리 시작 API가 인증 없이 열려 있었으므로 지정된 서비스 역할만 색인을 시작할 수 있게 하기 위해서다.
- 설계: JWT 서명과 만료를 검증한 뒤 DB의 현재 사용자·활성 상태·역할을 다시 확인한다. 세션과 Refresh Token은 만들지 않고, 비밀키는 환경변수에서만 받는다.
- 검증: 단위 테스트 60개와 PostgreSQL·pgvector·실제 Ollama 통합 테스트 16개가 성공했다. 당시 운영자 역할의 처리 시작 성공, 일반 사용자 요청 403, 미인증 401, 일반 사용자 업로드·검색, 비활성 사용자 토큰 차단을 확인했다.
- 다음: 사용자 등록·비밀번호 재설정은 현재 범위가 아니며, 문서 처리 역할과 사용자 데이터 접근 경계를 분리해 설계한다.

## 2026-07-13 — 인증 보안 감사 후 보강

- 변경: 공개 placeholder JWT 키를 거부하고 UTF-8 32바이트 최소 길이·60초~24시간 만료 범위·issuer 검증을 적용했다. 사용자 ID claim은 `sub` 하나로 통일했다.
- 이유: 예시 설정을 그대로 사용한 토큰 위조와 중복 claim 불일치, 다른 발급자의 토큰 수용 가능성을 기동·인증 단계에서 차단하기 위해서다.
- 설계: 명시적으로 활성화한 한 번의 실행에서만 당시 최초 운영 계정을 BCrypt로 생성하고, strict Bearer resolver와 기본 `denyAll`, 명시적 CORS Origin, 정확한 `/actuator/health` 공개 범위를 사용한다. Access Token의 `exp`를 필수로 검증하고 인증 객체의 문자열 출력에서는 토큰을 마스킹한다.
- 검증: 단위 테스트 85개와 PostgreSQL·pgvector·실제 Ollama 통합 테스트 29개가 성공했다. `exp` 누락·만료·변조·잘못된 Bearer·issuer/DB 불일치·삭제 사용자·CORS·Actuator·당시 운영자/일반 사용자 경계를 실제 필터 체인에서 확인했다.
- 당시 범위: 회원가입·Refresh Token·로그아웃 블랙리스트·사용자별 문서 소유권은 이 시점에는 구현하지 않았다. 이후 사용자별 소유권은 별도 작업으로 추가했다.

## 2026-07-13 — 개인 커리어 문서 플랫폼으로 방향 전환

- 변경: 프로젝트 이름은 `PRIZM`으로 유지하면서 개인의 커리어 문서에서 실제 경험과 원문 근거를 찾는 방향으로 설명과 개발 기준을 전환했다. 문서 관리자 승인 의존성을 제거하고 업로드 후 자동 비동기 처리 흐름으로 정리했으며, 관리자 역할은 `SYSTEM_ADMIN`으로 바꿨다.
- 이유: 범용 문서 버전·벡터 검색·장애 복구 기반을 보존하면서 이전 주제의 등급·승인 개념이 앞으로의 커리어 기능 설계를 제약하지 않게 하기 위해서다.
- 유지: 원본 저장과 해시, 문서 버전, `active_version_id`, 청크·임베딩, exact cosine 검색, 처리 작업, lease, claim-version fencing, 재시도와 JWT 보안은 주제와 무관한 플랫폼 기반으로 유지했다.
- 문서: `AGENTS.md`, `README.md`와 현재 구현 현황을 실제 코드 기준으로 다시 작성하고 OpenSQL·OpenProxy·OpenHA는 미검증 상태로 명시했다.
- 검증: 단위 테스트 80개가 성공했다. 통합 테스트는 30개 중 Testcontainers PostgreSQL 16·pgvector 0.8.2·실제 Ollama와 V6→V7 데이터 전환을 포함한 29개가 성공했고, 실제 OpenSQL 접속 정보가 필요한 별도 테스트 1개만 제외됐다. 프런트엔드 lint·build와 Docker Compose 설정 검증도 성공했다.

## 2026-07-14 — 사용자별 문서 소유권

- 변경: `documents`, `document_versions`, `processing_jobs`, `document_chunks`에 인증 사용자 소유권을 연결하고 목록·상세·색인·검색 전 경로에서 같은 소유자를 강제했다.
- 이유: 다른 사용자의 문서나 벡터가 조회 결과뿐 아니라 검색 후보 단계에도 섞이지 않도록 다중 사용자 경계를 확정하기 위해서다.
- 설계: 사용자 ID는 요청에서 받지 않고 JWT 인증 사용자로 결정하며, 문서·버전·청크 owner 조건과 ACTIVE 현재 버전 조건을 SQL에 함께 적용한다. `SYSTEM_ADMIN`은 개인 문서 API를 우회하지 않는다.
- 검증: PostgreSQL·pgvector 통합 테스트에서 USER A/B의 동일·유사 문서를 사용해 목록·상세·단일 검색 격리와 SYSTEM_ADMIN 403을 확인했다.

## 2026-07-14 — 문서 유형 저장

- 변경: `documents.document_type`과 `DocumentType` enum을 추가하고, TXT 업로드에서 선택한 유형을 저장해 업로드·목록·상세 응답으로 반환한다.
- 이유: 향후 커리어 문서 활용 기능의 기준 정보를 보관하되, 자동 분류나 유형별 필터 없이 사용자가 지정한 값만 다루기 위해서다.
- 기본값: 요청에서 생략하거나 기존 V8 문서를 V9로 전환할 때는 `OTHER`를 사용하며, DB CHECK 제약으로 허용된 enum 문자열만 저장한다.
- 검증: 단위 테스트와 PostgreSQL·pgvector·실제 Ollama 통합 테스트로 기본값, 지정 값, 잘못된 요청 400, 기존 소유권·검색 회귀를 확인했다.

## 2026-07-14 — 문서 유형별 목록 필터

- 변경: 기존 `GET /api/documents`에 선택적 `documentType` 파라미터를 추가해, 한 가지 문서 유형만 목록에서 조회할 수 있게 했다.
- 이유: 이미 저장된 문서 유형을 바로 활용하되 여러 조건·페이지네이션 같은 범위 확장은 뒤 단계로 미루기 위해서다.
- 설계: 유형이 있으면 Repository에서 `owner_user_id`와 `document_type`을 함께 조건으로 조회하고, 없으면 기존 소유자별 전체 목록 조회를 유지한다.
- 검증: 단위 테스트 89개와 PostgreSQL·pgvector·실제 Ollama 통합 테스트 40개가 성공했다. 유형별 결과, 빈 목록, 잘못된 유형 400, USER A/B 격리와 SYSTEM_ADMIN 403을 확인했다.

## 2026-07-14 — 프런트엔드 로그인

- 변경: `/login` 화면, 로그인 API 모듈, Access Token·현재 사용자 저장소와 `/career-vault` 임시 화면·로그아웃을 추가했다.
- 이유: 문서 화면을 만들기 전에 백엔드 JWT 인증을 실제 프런트엔드 흐름으로 연결하고, 인증된 사용자만 다음 화면으로 이동하게 하기 위해서다.
- 설계: 로그인 응답의 토큰을 저장한 뒤 `GET /api/users/me`를 Bearer 인증으로 확인해야만 성공 처리한다. 확인 실패와 로그아웃에서는 토큰·사용자 정보를 함께 삭제하며, 비밀번호와 토큰은 화면·로그에 출력하지 않는다.
- 검증: 기존 프런트엔드 테스트 환경은 없어 새 도구를 추가하지 않았고, `npm run lint`와 `npm run build`를 성공했다.

## 2026-07-14 — Career Vault 문서 목록

- 변경: `/career-vault`에서 로그인한 사용자의 문서 목록을 조회하고, 한 가지 문서 유형을 선택해 다시 조회하는 UI를 추가했다.
- 이유: 문서 업로드·상세·검색 화면을 추가하기 전에 이미 구현된 사용자별 문서 목록과 서버 필터를 실제 사용자 화면으로 연결하기 위해서다.
- 설계: 전체 선택은 `GET /api/documents`를 호출하고, 유형 선택은 `documentType` 쿼리와 Bearer Access Token을 함께 전송한다. 401·403 응답은 목록 오류를 보여 주지 않고 저장된 인증 정보를 삭제한 뒤 로그인 화면으로 이동한다.
- 검증: 기존 프런트엔드 테스트 환경은 없어 새 도구를 추가하지 않았고, `npm run lint`, `npm run build`, `git diff --check`를 성공했다.

## 2026-07-14 — Career Vault TXT 문서 업로드 UI

- 변경: Career Vault 화면에서 단일 TXT 파일, 문서 유형, 필수 제목을 입력해 `POST /api/documents`로 업로드하는 UI를 추가했다.
- 이유: 이미 구현된 TXT 업로드 API를 프런트엔드 문서 목록 화면에 연결하되 PDF, 상세, 검색, 진행률 같은 다음 범위는 포함하지 않기 위해서다.
- 설계: `FormData`에 `title`, `documentType`, `file`을 넣고 Bearer Access Token을 전송한다. 성공하면 입력을 초기화하고 현재 선택된 문서 유형 필터를 유지한 채 목록을 다시 조회한다.
- 검증: 기존 프런트엔드 테스트 환경은 없어 새 도구를 추가하지 않았고, `npm run lint`, `npm run build`, `git diff --check`로 확인한다.

## 2026-07-14 — TXT 청크 출처 정보

- 변경: `document_chunks`에 TXT 청크의 출처 유형·표시 순서·표시 라벨을 저장하고, 검색 응답에 함께 반환하도록 V10 migration과 JDBC 저장·검색 경로를 추가했다.
- 설계: 기존 `chunk_no`는 변경하지 않는다. TXT Worker는 `TEXT_CHUNK`, 1부터 시작하는 `source_index`, `텍스트 구간 N` 라벨을 저장하며, V9 기존 청크는 버전별 `chunk_no` 순서로 안전하게 backfill한다.
- 검증: PostgreSQL 16+pgvector Testcontainers와 Ollama를 사용해 `./gradlew.bat test --no-daemon --rerun-tasks`, `./gradlew.bat integrationTest --no-daemon --rerun-tasks`를 성공했다. OpenSQL 실환경 테스트는 기존 제외 정책에 따라 실행하지 않았다.

## 2026-07-14 — 텍스트 PDF 페이지 출처 처리

- 변경: Apache PDFBox로 텍스트 레이어 PDF를 업로드 시 검증하고, Worker가 페이지별 텍스트를 기존 청킹·임베딩 흐름으로 저장하도록 확장했다.
- 출처: PDF 청크는 `PAGE`, 1부터의 실제 페이지 번호, `N페이지` 라벨을 사용하며 V11에서 `PDF` 파일 형식과 `PAGE` 출처 제약을 추가했다.
- 검증: 단위 테스트와 Docker PostgreSQL 16·pgvector·실제 Ollama `bge-m3` 통합 테스트가 통과했다. V1~V11 migration, PDF PAGE 출처 저장·검색, 사용자별 격리를 확인했으며 OpenSQL 실환경 테스트는 기존 정책대로 제외했다.

## 2026-07-14 — 프런트엔드 PDF 업로드 지원

- 변경: Career Vault 기존 업로드 UI가 `.txt`와 `.pdf`를 선택하고, 확장자·10MB 제한을 클라이언트에서 먼저 확인하도록 확장했다.
- 안내: 텍스트 PDF만 지원하며 스캔·암호화 PDF는 지원하지 않는다는 제한을 표시하고, 서버의 `INVALID_DOCUMENT_CONTENT`는 이해하기 쉬운 PDF 안내 문구로 변환한다.
- 범위: 기존 FormData·Bearer 인증·성공 후 현재 문서 유형 필터 재조회 흐름을 유지했고, OCR·미리보기·백엔드 변경은 추가하지 않았다.

## 2026-07-14 — 프런트엔드 문서 검색 UI

- 변경: Career Vault에 자연어 검색 입력과 단일 벡터 검색 결과 표시를 추가하고, `POST /api/search` 요청을 별도 API 모듈로 분리했다.
- 표시: 문서 제목, 버전, `sourceLabel`, 원문, 유사도 점수만 표시하며 결과 없음은 현재 등록 문서에서 근거를 찾지 못했다는 중립 문구로 안내한다.
- 유지: Bearer 인증과 401·403 세션 만료 처리를 기존 흐름으로 사용하고, 목록 필터·TXT/PDF 업로드·백엔드 검색 계약은 변경하지 않았다.

## 2026-07-14 — Career Evidence 다중 검색 API

- 변경: `POST /api/career-evidence/search`를 추가해 인증 사용자의 ACTIVE 현재 문서 버전 청크를 관련도 순으로 최대 5개 배열로 반환한다. 기존 `POST /api/search` 단일 결과·404 계약은 변경하지 않았다.
- 구조: 기존 BGE-M3 임베딩과 pgvector exact cosine SQL의 소유권·ACTIVE 조건을 재사용하고, 새 응답에는 청크 ID, 문서·버전, 원문, 출처, 거리와 점수를 포함했다.
- 검증: 단위 테스트와 Docker PostgreSQL 16·pgvector 및 로컬 Ollama `bge-m3` 통합 테스트를 통과했다. 통합 테스트에서 사용자 A/B 격리, 현재 ACTIVE 버전 조건, 최대 5개 및 거리 정렬을 확인했으며 OpenSQL 실환경 테스트는 기존 제외 정책을 유지했다.

## 2026-07-14 — 0-norm 임베딩 검증

- 변경: 공통 `EmbeddingValidator`에서 1024차원, 유한값, 0보다 큰 L2 norm을 검증하고 Ollama 응답, 문서 색인·저장 직전, 단일 검색과 Career Evidence 검색에 적용했다.
- 실패 처리: 0-norm 응답은 `EMBEDDING_INVALID_RESPONSE`로 거부한다. 색인은 기존 임베딩 오류 정책에 따라 재시도 대상으로 처리하며 완료 트랜잭션을 호출하지 않고, 검색은 pgvector SQL 실행 전에 중단해 안전한 502 오류를 반환한다.
- 검증: 전체 단위 테스트와 Docker PostgreSQL 16·pgvector 및 로컬 Ollama `bge-m3` 통합 테스트를 통과했다. OpenSQL 실환경 테스트는 기존 제외 정책을 유지했다.

## 2026-07-14 — PDF 추출 처리량 제한

- 변경: `prizm.document.pdf.max-pages`(기본 300)와 `max-extracted-characters`(기본 2,000,000)를 추가하고, 공통 PDF 추출기에서 페이지 수와 페이지별 `strip()` 텍스트의 누적 길이를 제한했다.
- 실패 처리: 제한을 넘는 PDF는 업로드 시 `INVALID_DOCUMENT_CONTENT` 400으로 메타데이터·작업 생성 전에 거부한다. Worker에서는 영구 문서 오류로 분류되어 청크 저장·ACTIVE 전환 없이 FAILED 처리되며 기존 ACTIVE 버전은 유지된다.
- 검증: 단위 테스트와 Docker PostgreSQL 16·pgvector 및 로컬 Ollama `bge-m3` 통합 테스트를 통과했다. OpenSQL 실환경 테스트는 기존 제외 정책을 유지했다.

## 2026-07-14 — 프로젝트 문서 현행화와 중복 통합

- 변경: README, 작업 지침, 현재 구현 현황, 장기 기획안, 개발 기록, OpenSQL Gate와 초기 검증 기록을 실제 코드·migration·실행 결과에 맞춰 함께 갱신했다.
- 통합: 별도로 작성돼 내용이 겹치던 한국어 개발 현황 문서는 `docs/project-status.md`에 흡수하고, 이 파일을 현재 구현과 미구현 범위의 단일 기준으로 정했다.
- 구분: 장기 기획안의 outbox·generation·MCP·지원 패키지·OpenSQL HA는 미래 목표로 표시하고, 초기 검증 문서는 당시 단계의 역사적 기록임을 명시했다.
- 검증: 저장소의 모든 Markdown 로컬 링크와 오래된 API·enum 표기를 검사했고 `git diff --check`를 통과했다.

## 2026-07-14 — Worker 시간 기반 lease heartbeat

- 변경: Worker가 job을 선점한 직후 별도 daemon scheduler heartbeat를 시작하고, 문서 원본 읽기·PDF 추출·Ollama 임베딩을 포함한 전체 처리 구간 동안 기존 lease 갱신 SQL을 lease duration의 1/3 주기로 호출하도록 했다. 기존 청크 단위 갱신도 유지했다.
- fencing: heartbeat 갱신은 기존의 `processing_jobs.id`, `PROCESSING`, `claim_version` 조건을 그대로 사용하며 claim version을 변경하지 않는다. 0행(stale claim)이면 heartbeat를 중지하고 processor가 청크 저장·ACTIVE 전환·COMPLETED 처리 전에 stale 예외를 받는다.
- 검증: scheduler를 제어하는 단위 테스트와 PostgreSQL 통합 테스트로 갱신·종료·stale 차단·recovery 비회수를 확인했다. OpenSQL 실환경 테스트는 기존 제외 정책을 유지한다.

## 2026-07-14 — 원본 저장소 I/O 재시도 분류

- 변경: 원본 읽기에서 파일 없음·경로 이탈·유효하지 않은 경로·일반 파일이 아닌 대상은 `PermanentFileStorageException`으로, 실제 읽기 중 일반 `IOException`은 `TransientFileStorageException`으로 구분했다.
- Worker: `DocumentIndexingProcessor`는 일시 오류만 재시도 가능 `DocumentIndexingException`으로 변환한다. 기존 FailureService가 1분·5분·15분 backoff 및 최대 3회 후 FAILED를 적용하며 영구 오류는 즉시 FAILED로 처리한다.
- 보존: 안전한 일반 오류 메시지만 작업 상태에 남기고, 청크 저장·ACTIVE 전환·기존 active version·lease heartbeat·claim-version fencing 동작은 변경하지 않았다.

## 2026-07-14 — 고아 원본 파일 cleanup 작업 등록

- 변경: V12 `file_cleanup_jobs`에 상대 storage key별 PENDING cleanup 작업을 유일하게 기록하고, rollback 보상 삭제가 실패했을 때만 별도 `REQUIRES_NEW` transaction으로 등록하도록 했다.
- 안전성: 정상 삭제에는 작업을 만들지 않으며, cleanup 등록 실패는 원래 업로드 rollback 예외를 바꾸지 않는다. 로그에는 저장 경로나 원본 파일명·stack trace를 남기지 않는다.
- 보완: transaction 결과가 `STATUS_UNKNOWN`이면 원본 파일과 cleanup 작업을 모두 보존하고, 명확한 `STATUS_ROLLED_BACK`에서만 보상 삭제를 수행한다.
- 범위: cleanup Scheduler·Worker·실제 재삭제·attempts 증가·재시도 backoff·외부 조회 API는 추가하지 않았다.

## 2026-07-14 — 경력 근거 다중 검색 UI

- 변경: Career Vault의 기존 검색 입력을 `POST /api/career-evidence/search`에 연결해 인증 사용자의 관련 원문 근거를 최대 5개 카드로 표시한다.
- 표시: 문서 제목·버전·출처 라벨·원문·원래 score를 보여주며, 빈 배열은 근거를 찾지 못했다는 중립 문구로 처리한다. score는 `1 - distance` 계약이므로 퍼센트로 단정하지 않는다.
- 보존: 기존 문서 목록·유형 필터·TXT/PDF 업로드·인증 만료 처리를 유지하며, AI 답변·근거 저장·백엔드 변경은 추가하지 않았다.

## 2026-07-14 — 검색 품질 평가 기반

- 변경: 합성 `corpus.json`·`questions.jsonl` 형식과 개인 데이터가 Git에서 제외되는 `local/search-evaluation/` 경계를 추가했다.
- 측정: 별도 `searchEvaluation` task가 실제 PostgreSQL·pgvector와 Ollama `bge-m3`, 현재 청킹·owner·ACTIVE 조건을 사용해 Dense top 5·20을 조회하고 Recall@20, Precision@5, MRR@20, nDCG@5, 중복률과 지연을 로컬 JSON·CSV로 기록한다.
- 범위: 프로덕션 검색 API, score 임계값, Reranker, Hybrid Search, 청킹과 프런트엔드는 변경하지 않았다.
- 검증: 단위 테스트 162개, PostgreSQL·pgvector·실제 Ollama 통합 테스트 49개와 합성 기준선 평가를 통과했다. OpenSQL 실환경 테스트 1개는 기존 정책대로 제외했다.

## 2026-07-14 — 검색 평가 파일럿 데이터 확장

- 데이터: 기존 합성 사례를 보존하면서 가상 문서 11개, 질문 30개로 확장했다. 기술 8개, 문제 해결 6개, 협업 4개, 정확한 수치·표현 6개, 무근거 6개이며 hard negative 질문은 11개다.
- 분리: 질문을 TUNING 20개와 TEST 10개로 구분하고, 동일 정규화 질문 중복·잘못된 split·누락된 fixture 근거·무근거 라벨 충돌을 실행 전에 차단한다. 의미가 같은 패러프레이즈의 split 간 중복은 수동 검토했다.
- 출력: Dense 기준선 보고서에 전체·split·category별 Precision@5, Recall@20, MRR@20, nDCG@5, 중복률, score 분포와 지연을 추가했다.
- 기준선: 실제 PostgreSQL 16.14·pgvector와 Ollama `bge-m3` 최종 실행에서 전체 Recall@20 1.0000, Precision@5 0.1933, MRR@20 0.6556, nDCG@5 0.8543, 중복률 0.0067, 평균/p95 864.20/999ms를 기록했다. 합성 결과는 실제 서비스 성능을 보장하지 않는다.
- 범위: 운영 검색, 임계값, Reranker, Hybrid Search, 청킹, 프런트엔드와 DB migration은 변경하지 않았다.

## 2026-07-15 — 고아 원본 파일 Cleanup Worker

- 변경: V13에서 cleanup 작업을 `PENDING`, `PROCESSING`, `RETRY_WAIT`, `COMPLETED`, `FAILED` 상태로 확장하고, PostgreSQL `FOR UPDATE SKIP LOCKED`와 lease·claim version으로 짧게 선점한 뒤 파일 삭제를 DB 트랜잭션 밖에서 실행하도록 구현했다.
- 실패 처리: 상대 storage key의 경로 이탈·절대 경로·일반 파일이 아닌 대상은 영구 실패로, 일반 파일 시스템 I/O 오류는 기존 1분·5분·15분 backoff와 최대 3회 재시도 정책으로 처리한다. 이미 삭제된 파일은 멱등 성공으로 완료 처리한다.
- 복구·검증: 만료된 PROCESSING lease는 재시도 또는 최종 실패로 회수하며, Docker PostgreSQL 16+pgvector 통합 테스트로 V1~V13 migration, SKIP LOCKED 단일 선점, 만료 claim 회수와 실제 파일 삭제를 확인했다. OpenSQL 실환경 테스트는 기존 정책대로 제외한다.

## 2026-07-15 — Cleanup 저장 경로 부모 심볼릭 링크 차단

- 보완: Cleanup 삭제와 원본 읽기 경로에서 storage root부터 대상 부모까지 모든 기존 경로 요소를 `NOFOLLOW_LINKS`로 검사하고, 심볼릭 링크·비디렉터리 부모를 영구 저장소 오류로 거부한다. storage root와 대상 부모의 real path containment도 추가로 확인한다.
- 한계: 지원 파일 시스템별 `SecureDirectoryStream` 보장을 전제하지 않아 검사와 삭제 사이의 로컬 파일 시스템 TOCTOU 교체 가능성은 남아 있다. 부모 심볼릭 링크 생성 테스트는 현재 Windows 환경의 권한 제한 시 JUnit assumption으로 명시적으로 제외된다.

## 2026-07-15 — Cleanup 완료 DB 실패와 파일 삭제 실패 분리

- 보완: Cleanup Coordinator에서 파일 삭제 예외만 실패 분류·재시도 처리하고, 물리 삭제 후 COMPLETED 갱신 예외는 PROCESSING lease를 유지한 채 별도로 기록한다. 완료 갱신의 stale claim도 실패 상태 전이로 바꾸지 않는다.
- 검증: 실제 PostgreSQL에서 삭제 성공 뒤 완료 갱신 실패를 재현하고, lease 만료 회수 후 파일 없음 멱등 삭제와 새 claim version의 COMPLETED 전환으로 수렴함을 확인했다.

## 2026-07-15 — Cleanup SKIP LOCKED 행 잠금 통합 검증

- 검증: 첫 별도 transaction이 정렬상 먼저 선택될 PENDING cleanup 행을 `FOR UPDATE`로 잠근 상태에서, 두 번째 별도 transaction이 실제 claim SQL을 실행해 잠긴 행을 기다리지 않고 다음 행을 `PROCESSING`으로 선점함을 PostgreSQL에서 확인했다. 첫 lock 해제 전 두 번째 claim 완료와 lease·claim version 갱신을 assertion으로 검증했다.

## 2026-07-15 — 오픈소스 엔진 전환 실행 계획

- 방향: PRIZM의 본체를 문서 처리·근거 검색·커리어 구조화·포트폴리오 생성을 제공하는 self-hosted 오픈소스 엔진으로 정의하고, 현재 Career Vault는 개인용 참조 애플리케이션으로 역할을 변경했다.
- 계획: 구현 기준선, 거버넌스, Quickstart, port/adapter 경계, canonical source, CareerFact, portfolio, API v1, 멀티모듈 패키징, 기관 scope, 최종 감사의 11단계 실행 계획을 현재 [보관 문서](archive/oss-transition-execution-plan.md)에 기록했다.
- 운영: 각 단계에 Codex 권장 모델·추론 강도, 실행 프롬프트, 완료 조건과 독립 검토 프롬프트를 포함했으며 독립 검토가 통과하기 전에는 완료 상태로 바꾸지 않도록 정했다.

## 2026-07-15 — Engine과 Reference App 제품 경계 기준선

- 설계 결정: PRIZM Engine을 문서·version·원본·비동기 processing·출처 검색의 본체로, `frontend/` Career Vault를 개인용 Reference App으로 정의했다. 현재 단일 Spring Boot project는 아직 독립 engine artifact나 완전한 adapter 구조가 아님을 명시했다.
- 구현 진실성: 실제 프런트엔드의 최대 5개 Career Evidence UI와 전체 처리 구간 lease heartbeat를 현재 기능으로 바로잡고, CareerFact·portfolio·MCP·`/api/v1`·OpenSQL HA는 계획으로 분리했다. V12 cleanup 등록은 기준선, dirty worktree의 V13 cleanup Worker는 독립 검토 전 작업으로 구분했다.
- 제품 방향: 이전 B2C 가격·전환율 검토는 역사적 가설로 보존하되 현재 결론에서 내리고, 오픈소스 엔진 재사용성과 Reference App 검증을 우선하도록 장기 기획안을 현행화했다. 단계 0 상태는 독립 검토 전이므로 `IN_PROGRESS`로 유지한다.

## 2026-07-15 — 단계 0 독립 검토 지적 문서 정합성 보완

- 결정: 공식 제품 정의를 재사용 가능한 PRIZM Engine과 이를 검증하는 Reference App으로 통일하고, Career Vault를 개인용 활용·통합 예제로 한정했다.
- 정합성: 실행 계획 단계 8의 module graph를 유일한 canonical target으로 지정하고, TXT 전용 JavaDoc과 `.env.example`의 PDF·Cleanup 환경변수 누락을 알려진 기술 부채로 기록했다.
- 검증: Markdown 로컬 링크 37개와 code fence 39개를 검사해 누락·미종료 0건을 확인했고 `git diff --check`를 통과했다. 애플리케이션 테스트는 문서 전용 작업이므로 실행하지 않았다.
- 다음: 1차 독립 검토 지적은 수정했지만 독립 재검토 전이므로 단계 0은 `IN_PROGRESS`로 유지한다.

## 2026-07-15 — 단계 0 2차 독립 재검토 JavaDoc 기술 부채 보완

- 검토 결과: 2차 독립 재검토는 기존 목록에서 `DocumentIndexingProcessor`, `IngestionProperties`, `TextChunker`의 오래된 TXT 전용 JavaDoc이 빠진 점을 Medium finding으로 판정해 FAIL했다.
- 전수 검사: `src/main/java` 122개 Java 파일의 JavaDoc 100개 블록과 일반 주석을 검색해 `DocumentController`, `DocumentUploadService`를 포함한 다섯 곳을 부정확한 설명으로 확정했다. PDF 전용 설정과 TXT 전용 decode·오류·출처 분기는 실제 책임에 맞아 제외했다.
- 후속 결정: 다섯 JavaDoc을 `docs/project-status.md`의 알려진 기술 부채와 실행 계획 단계 3 범위에 모두 연결했다. Java source·`.env.example`·V13 Cleanup Worker는 수정하지 않았으며 단계 0은 재검토 전까지 `IN_PROGRESS`다.
- 검증: 저장소 Markdown 10개의 로컬 링크 37개와 code fence 균형을 확인하고 `git diff --check`를 통과했다. 문서 전용 작업이므로 애플리케이션 테스트는 실행하지 않았다.

## 2026-07-16 — Cleanup Worker 감사 완료와 단계 0 마감

- Cleanup 기준선: V13 Cleanup Worker는 descriptor-relative `SecureDirectoryStream` 삭제, symlink·부모 교체 TOCTOU 방어, lease·claim-version fencing, retry/backoff·recovery와 삭제 뒤 DB 완료 실패의 멱등 수렴을 포함해 최종 감사에서 CRITICAL/HIGH/MEDIUM finding 없이 통과했다. `86387e7c227ede3be96c538aafc48b0205bc5e18`가 main에 병합됐다.
- 단계 0 결정: 최종 독립 재검토 PASS에 따라 Engine/Reference App 제품 경계와 단계 8 canonical module graph를 확정하고 단계 0을 `COMPLETE`로 변경했다. 다섯 오래된 TXT 전용 JavaDoc은 단계 3, `.env.example` 불일치는 단계 2 후속 대상으로 남긴다.
- 남은 경계: OpenSQL profile·조건부 integration test는 실제 OpenSQL/OpenProxy/OpenHA 호환성 증명이 아니며, OpenSQL 단일 migration·vector 검색, OpenProxy runtime, OpenHA 장애전환·검색 복구 순으로 검증한다. V13 CHECK·backfill 회귀 테스트와 SecureDirectoryStream 미지원 filesystem의 fail-closed 운영 문서는 LOW backlog다.

## 2026-07-16 — OpenSQL 단일 환경 SQL 호환성 Gate 준비

- 테스트: PostgreSQL Testcontainers와 opt-in 외부 OpenSQL 실행이 같은 assertion suite를 사용해 V1~V13·schema·pgvector 1024차원·실제 `VectorSearchRepository`·Indexing/Cleanup claim·lease·fencing·recovery·두 connection의 `SKIP LOCKED`를 검증하도록 구성했다.
- 격리: 외부 실행은 `RUN_OPENSQL_TESTS=true`와 새 검증 전용 DB/schema 확인이 필요하며 runtime/Flyway 접속을 별도 환경변수로 받는다. Spring context, Scheduler, 파일 삭제, Ollama·Reranker·GPU는 사용하지 않고 실패 출력은 Migration·SQLState·SQL 기능으로 제한한다.
- PostgreSQL 검증: Docker Desktop 28.2.2의 PostgreSQL 16+pgvector 컨테이너에서 공통 suite 1개를 통과했고, 전체 unit test 186개는 176개 성공·환경 조건 10개 skip·실패 0개였다. OpenSQL 환경변수 없이 외부 Gate 1개가 실제 접속 없이 skip되는 것도 확인했다.
- 상태: 테스트 준비는 OpenSQL 호환성 통과가 아니다. 실제 OpenSQL 환경이 없어 현재 결과는 `NOT_RUN`이며 PostgreSQL 기준 검증과 분리한다. OpenProxy와 OpenHA도 계속 미검증이다.

## 2026-07-21 — OpenSQL Gate 대상 동일성·데이터 안전성 보강

- 안전성: Flyway와 runtime을 migration 전에 각각 빈 대상으로 확인하고, migration 뒤 Flyway가 만든 비활성 UUID marker를 runtime이 읽어야만 fixture를 시작하도록 했다. 전역 domain 삭제를 제거하고 실행별 UUID·생성 ID만 정리한다.
- 회귀 검증: 별도 datasource 정상 경로와 기존 V13 sentinel runtime·서로 다른 빈 runtime 오설정 경로를 Docker PostgreSQL 16+pgvector에서 3건 모두 통과했으며 sentinel 데이터 보존과 marker fail-closed를 확인했다.
- 상태: 실제 OpenSQL 환경은 사용하지 않아 결과는 계속 `NOT_RUN`이다. OpenProxy·OpenHA·Ollama도 이번 검증에 사용하지 않았다.

## 2026-07-22 — Career Vault 문서 관리·버전·원본 PDF 열람

- 문서 관리: owner-scoped 목록 필터·상세·제목/DocumentType 수정·안전한 처리 상태를 추가했다. 문서 삭제는 soft-delete 계약이 없는 현재 스키마에 맞춰 소유 문서를 잠그고, 비종료 indexing 작업을 거부하며, 모든 원본 storage key의 Cleanup Job을 동일 트랜잭션에 등록한 뒤 metadata를 하드 삭제한다. 실제 파일 삭제는 요청 트랜잭션 밖의 기존 Cleanup Worker가 수렴시킨다.
- 버전·미리보기: `POST /api/documents/{documentId}/versions`는 새 TXT/PDF immutable version과 PENDING indexing job을 만들고, 새 색인이 활성화될 때까지 기존 `active_version_id`를 유지한다. owner/document/version을 모두 확인한 PDF 썸네일과 원본 열람 API는 저장 경로·원문·JWT·worker 오류 문구를 노출하지 않는다.
- 화면: 좁고 반응형인 4:3 카드, 접근 가능한 상세 모달, 버전 이력·ACTIVE 상태·수정본 업로드, PDF 전용 Blob 뷰어, 명시적 삭제 확인과 안전한 오류/재시도 상태를 반영했다.
- 검증: backend unit 225건 중 211건 성공·환경 조건 14건 skip·실패 0건, PostgreSQL 16+pgvector 문서 관리 통합 5건 성공, frontend lint/build 및 Docker Compose 설정 검증을 통과했다. OpenSQL·OpenProxy·OpenHA·Ollama는 사용하지 않았다.

## 2026-07-23 — 미병합 브랜치 통합과 대회 대응 기준 정리

- 브랜치 판정: `codex/search-evaluation-baseline`과 `test/search-evaluation-pilot`의 Dense 평가 기반·합성 11문서/30질문·단위 테스트를 `main`에 통합했다. main의 V13 Cleanup Worker가 평가 중 실행되지 않도록 평가 profile에서 indexing·cleanup Worker를 모두 비활성화했다.
- 브랜치 근거: `codex/search-evaluation-baseline` tip `46e24ef`와 `test/search-evaluation-pilot` tip `347d54d`는 통합했다. `experiment/bge-reranker-evaluation` tip `617eacf`의 실행 코드는 비채택하고 실험 근거만 보존했으며, `portfolio/prizm-showcase` tip `377f615`의 오래된 설명은 폐기하고 유효한 근거만 현재 문서로 다시 작성했다.
- 실험 결정: `experiment/bge-reranker-evaluation`의 CPU Reranker 코드는 직접 근거 Precision 개선 실패와 p95 약 51.86초·peak RSS 약 2.10GB 비용 때문에 채택하지 않았다. 조건·수치·비채택 근거만 별도 결정 문서에 남겼다.
- 문서 선별: `portfolio/prizm-showcase`의 오래된 README와 V12·Cleanup 미구현 설명은 가져오지 않고, PR #9·#10과 V13 기준의 수치·문제 해결 사례로 다시 작성했다. `main`을 유일한 장기 브랜치로 두는 종료 절차도 문서화했다.
- 대회 계획: GitHub Spec Kit의 spec→plan→tasks 흐름과 robo-architect의 evidence 분리를 작은 수직 슬라이스에만 적용하도록 정했다. OpenSQL 실환경은 아직 `NOT_RUN`, CareerFact·portfolio는 계획 기능으로 유지하며 2026-08-27 제출일까지 OpenSQL Gate→clean-clone→작은 grounded slice→라이선스·제출 감사 순으로 배치했다.
- 검증: `cleanTest test --no-daemon --rerun-tasks`에서 backend 242건 중 228건 성공·환경 조건 14건 skip·실패/오류 0건, frontend lint/build를 통과했다. 현재 환경에는 Docker 실행 파일이 없어 `docker compose config`, PostgreSQL·pgvector 통합 테스트와 `searchEvaluation`을 재실행하지 못했고 Ollama·OpenSQL·OpenProxy·OpenHA도 사용하지 않았다.

## 2026-07-23 — AS_BUILT spec registry 기준선

- registry: `specs/README.md`에 `PRZ-###` ID, 기능 상태와 환경별 검증 상태를 정의하고 `PRZ-000-platform-baseline`을 `AS_BUILT_BASELINE`으로 등록했다.
- 추적성: PRZ-000의 현재 계약을 14개 요구사항으로 정리하고 source·V1~V13 migration·unit/integration test와 2026-07-23 실행 결과를 `evidence.md`에 연결했다. PostgreSQL·Docker·Ollama·OpenSQL의 과거 성공·미재실행·`NOT_RUN`을 구분했다.
- 역사성: 이 기준선은 spec 도입 전 구현의 사후 기록이며, 존재하지 않았던 과거 Issue·PR·review를 생성하거나 사전 명세였던 것처럼 표현하지 않는다. 앞으로 착수하는 작은 수직 슬라이스만 실제 작업 시점의 spec과 Issue·PR에 연결한다.
- 문서: README, 현재 구현 현황, AGENTS와 대회 대응 계획을 `AS_BUILT_BASELINE` 정책에 맞췄다. 문서 전용 변경이므로 애플리케이션 test를 다시 실행하지 않고 로컬 Markdown 링크와 `git diff --check`를 검증했다.

## 2026-07-23 — 현재 문서·로드맵·보관 기록 구조 정리

- 탐색: `docs/README.md`를 문서 안내판으로 추가하고 현재 구현은 `project-status.md`, 앞으로의 순서는 `roadmap.md`, 대회 일정은 `contest/2026-tmaxtibero-plan.md`에서 확인하도록 기준을 분리했다.
- 축약·통합: 현재 현황을 핵심 기능·검증·한계 중심으로 축약했다. 제품 경계, 브랜치 정책과 수치·근거 문서의 고유 내용은 `AGENTS.md`, `PRZ-000`, 현재 현황과 이 기록에 흡수하고 중복 파일을 제거했다.
- 보관: 장기 종합 기획안, 과거 0~10단계 실행 계획, Reranker 비채택 실험과 초기 등록·검색 검증을 `docs/archive/`로 이동했다. Dense 검색 평가와 문제 해결 사례는 각각 `evaluation/`, `showcase/`로 분리했다.
- OpenSQL: 단일 환경 Gate와 OpenProxy·OpenHA 후속 범위를 분리하고, 실제 환경 결과는 계속 `NOT_RUN`으로 유지했다. 실제 착수 시 체크리스트를 `PRZ-001`로 이전한다.
- 검증: 문서 전용 변경이므로 애플리케이션 test를 다시 실행하지 않았다. 저장소 Markdown 18개의 로컬 링크 누락 0건, code fence 불균형 0건, trailing whitespace 0건과 `git diff --check` 통과를 확인했다.

## 2026-07-24 — 공식 지정과제·평가기준과 단계별 개발 Gate 반영

- 공식 기준: KOSSA 티맥스티베로 지정과제 원문, 오픈소스 개발자대회 일정과 제공받은 오리엔테이션 슬라이드를 대조해 OpenSQL 기반 업로드·자동 임베딩·메타데이터/버전, 변경 로그 동기화, MCP 검색과 DB 장애복구 목표를 정규화했다. 슬라이드 캡처는 공식 배포 URL과 재배포 조건이 확인되지 않아 저장소에 복사하지 않았고 추적표를 `CONTENT_EXTRACTED_SOURCE_PENDING`으로 표시했다.
- 추적성: 지정과제 항목을 `PRZ-000` 요구사항, 정확한 source·test, 환경과 다음 제출 증거에 연결하고 1차 30점, 기능·라이선스 15점, 2차 70점, 멘토링과 제출·후속 산출물 상태를 `docs/contest/2026-requirements-traceability.md`에 기록했다. 실제 OpenSQL·DB 장애전환은 계속 `NOT_RUN`, 동기화·MCP는 미구현이다.
- 우선순위: 대회 개발 순서를 라이선스·거버넌스 → OpenSQL·clean-clone → DB 장애복구 → 변경 로그 동기화·MCP → 조건부 CareerFact → 제출 감사로 변경했다. Portfolio는 검증된 CareerFact 이후 계획으로 유지했다.
- 작업 규칙: `AGENTS.md`에 `ORIENT → SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT → INTEGRATE` Gate, 상태 전이, 문서별 갱신 시점, 실제 GitHub 기록만 증거로 사용하는 원칙과 `main`만 남기는 성공·보류·비채택 경로를 추가했다. 실제 reviewer가 없는 개인 작업은 독립 감사, 사용자 승인과 `REVIEW_NOT_AVAILABLE_SOLO`를 요구하도록 분리했다.
- 독립 감사: 공식 기준, 현재 저장소 준비도와 Agent workflow를 세 갈래로 읽기 전용 검토해 추적표 완료 과장, 제출 3분 영상과 2차 demo 혼합, MCP 빈 결과 계약 혼합, 단계 번호 충돌과 review 막힘을 수정했다.
- 검증: 문서 전용 변경이므로 애플리케이션 test는 실행하지 않았다. 변경된 Markdown 7개의 로컬 링크 누락·code fence 불균형·trailing whitespace·EOF 여분 공백이 모두 0건이고 `git diff --check`를 통과했다.
