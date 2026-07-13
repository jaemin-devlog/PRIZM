# 최소 문서 등록 구현·검증 기록

## 목표

TXT 원본을 로컬에 저장하고, 문서와 첫 버전을 `QUARANTINED` 상태로 등록·조회한다. 이 단계에서는 텍스트 추출, 청크, 임베딩, 승인 처리를 하지 않는다.

## 구현 요약

- `POST /api/documents`는 `title`과 TXT `file`을 multipart/form-data로 받는다.
- `GET /api/documents`, `GET /api/documents/{documentId}`로 문서와 버전 상태를 조회한다.
- 파일은 `PRIZM_STORAGE_ROOT/documents/{documentId}/{versionId}/{originalFileName}`에 저장한다. 기본 저장 루트는 `./var/storage`이고 기본 최대 크기는 10 MiB다.
- 빈 파일, TXT 이외 확장자, 경로 문자가 있는 파일명, 제한 초과 파일을 거부한다. 파일 내용의 SHA-256을 저장한다.
- 파일 저장이 실패하면 DB 트랜잭션은 커밋되지 않는다. 파일 저장 후 DB 커밋이 실패하면 트랜잭션 완료 콜백이 파일을 삭제한다.

## 스키마와 기존 데이터

- V3는 `documents`, `document_versions`와 `(document_id, version_no)` UNIQUE 제약을 만들고, `document_chunks`에 문서 버전·청크·페이지 정보를 추가한다.
- V3는 `content_hash`를 `VARCHAR(64)`와 길이 64 CHECK로 생성한다.
- 개발 검증 데이터는 migration에 넣지 않고 통합 테스트가 직접 문서·버전·청크를 생성한다.

## 검증 결과

- `./gradlew.bat test --no-daemon`: 성공 (20 tests)
- `./gradlew.bat integrationTest --no-daemon --rerun-tasks`: 성공 (Testcontainers PostgreSQL, Flyway V1~V3, 실제 Ollama 기반 벡터 검색 회귀 포함)
- 로컬 PostgreSQL: Flyway V1~V3 모두 성공하고 `documents`, `document_versions`, `document_chunks`가 비어 있는 초기 상태를 확인했다.

## 남은 조건

- 로컬 파일 시스템과 DB는 단일 원자 트랜잭션이 아니므로, 보상 파일 삭제마저 실패하면 운영 점검·정리 절차가 필요하다.
- 이 검증 시점에는 한 문서의 첫 버전만 생성했다. 이후 단계에서 비동기 색인 작업과 활성 버전 검색 조건을 연결했다.
