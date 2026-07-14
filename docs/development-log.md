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
- 상세 결과: [최소 벡터 검색 구현·검증 기록](verification/2026-07-13-minimal-vector-search.md)

## 2026-07-13 — 최소 문서 등록 세로 흐름

- 변경: TXT 업로드 파일을 로컬 저장소에 보관하고, `documents`·`document_versions`에 `QUARANTINED` 메타데이터를 기록·조회하는 API를 추가했다.
- 이유: 임베딩 처리 전에 원본 파일, 버전, 격리 상태를 분리해 안전하게 연결할 최소 기반이 필요했다.
- 검증: Flyway V1~V3를 빈 개발 DB에 적용하고, 통합 테스트가 문서·버전·청크를 직접 생성해 단위 테스트와 Docker Testcontainers·실제 Ollama 통합 테스트를 통과했다.
- 다음: QUARANTINED 버전의 TXT 내용 추출과 청크 생성은 비동기 처리 흐름을 설계한 뒤 별도 단계로 추가한다.
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
