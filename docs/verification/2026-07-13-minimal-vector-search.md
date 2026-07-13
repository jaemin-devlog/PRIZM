# 최소 벡터 검색 구현·검증 기록

> 작성일: 2026-07-13  
> 범위: Ollama 임베딩 → PostgreSQL pgvector 저장 → exact cosine 검색 → REST API

## 1. 목적

문서 업로드, 권한, 버전 관리 기능을 추가하기 전에 로컬 임베딩 모델과 pgvector가 실제로 연결되는 가장 작은 검색 경로를 검증한다.

## 2. Ollama 검증

| 항목 | 결과 |
|---|---|
| `ollama --version` | CLI는 설치되지 않음 |
| Ollama API | 실행 중 (`GET /api/version` → `0.31.2`) |
| 등록 모델 | `bge-m3:latest` |
| 애플리케이션 설정값 | `bge-m3` |
| 임베딩 API | `POST /api/embed` 성공 |
| 실제 벡터 차원 | `1024` |

`bge-m3` 별칭으로 문장 `연차 신청은 인사 시스템에서 진행합니다.`를 임베딩했을 때 `embeddings[0]`의 길이가 1024임을 확인했다. 이 결과가 확인된 뒤에만 `vector(1024)` 스키마와 검색 코드를 추가했다.

## 3. 구현 내용

- Flyway V2에서 최소 `document_chunks` 테이블을 생성했다.
- `EmbeddingService` 인터페이스 뒤에 `OllamaEmbeddingService`를 두어, 이후 다른 임베딩 제공자로 교체할 수 있게 했다.
- Ollama 연결 실패, 모델 미설치, 빈 응답, 벡터 차원 불일치를 명시적 오류 코드로 처리한다.
- `VectorSearchRepository`는 JPA 없이 `JdbcTemplate`으로 exact cosine 검색을 수행한다.
- `POST /api/search`는 빈 값과 500자 초과 검색어를 거부하며, 결과가 없으면 `SEARCH_NO_RESULT`를 반환한다.

### Flyway V2

```sql
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL CHECK (char_length(trim(content)) > 0),
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

이 단계에서는 문서·버전·분류·권한 컬럼을 추가하지 않았다.

### Exact cosine 검색 SQL

```sql
SELECT content,
       embedding <=> CAST(? AS vector) AS distance
FROM document_chunks
ORDER BY embedding <=> CAST(? AS vector), id
LIMIT 1;
```

- `distance`: pgvector cosine distance
- `score`: `1 - distance`

두 값 모두 검색 시 계산하며 하드코딩하지 않는다.

## 4. 실제 검색 결과

현재 회귀 테스트는 같은 `bge-m3` 모델로 아래 세 문장을 임베딩해 저장한다.

1. 연차 신청은 인사 시스템에서 진행합니다.
2. 서버 장애가 발생하면 운영 담당자에게 보고합니다.
3. 프로젝트 회고에는 장애 원인과 해결 과정이 기록되어 있습니다.

질문 `휴가는 어디에서 신청하나요?`를 호출하면 연차 신청 문장이 첫 번째로 반환되는지를 실제 Ollama·pgvector 통합 테스트에서 검증한다. `distance`와 `score`는 모델 응답과 저장 벡터로 매번 계산하며 특정 숫자를 하드코딩하지 않는다.

REST API도 저장소가 반환한 1위 문장과 동일한 `distance`, `score`를 그대로 응답한다.

## 5. 검증 명령과 결과

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
```

두 명령 모두 성공했다.

- 단위 테스트: 검색어 검증, 검색 결과 없음, Ollama 연결 실패, 모델 미설치, 빈 임베딩 응답, 벡터 차원 불일치
- 통합 테스트: Docker Testcontainers PostgreSQL+pgvector, Flyway V1/V2, 실제 Ollama `bge-m3` 임베딩, 세 문장의 저장과 검색 순위

Docker 또는 Ollama가 없으면 통합 테스트는 건너뛰지 않고 실패하도록 구성했다.

## 6. 남은 조건과 다음 단계

- Ollama API와 `bge-m3:latest` 모델이 실행 중이어야 검색이 가능하다.
- `latest` 태그는 변경될 수 있으므로 제출 전에는 모델 버전 또는 digest를 고정한다.
- 로컬 검증 DB에는 위 테스트 문장 세 개가 남아 있다.
- 다음 단계에서는 PDF/TXT 업로드보다 먼저 `QUARANTINED` 상태의 최소 문서·버전 모델과 로컬 파일 저장을 연결한다.
