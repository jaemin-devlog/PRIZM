# PRIZM 작업 지침

이 저장소는 OpenSQL 기반 N2SF 정책 라우팅형 AI 문서 검색 MVP다. 현재 단계의 우선순위는 기능 수를 늘리는 것이 아니라, 문서 변경과 임베딩의 정합성, 검색 권한, MCP/REST 정책 일치, OpenSQL 장애 복구를 재현 가능하게 만드는 것이다.

## 기준 문서와 범위

- 구현 판단의 원문은 사용자가 저장소 밖에서 제공한 `PRIZM_1인개발_MVP_기획_상세설계서.md`다. 원문이 없는 clean clone에서는 이 문서와 `README.md`에 요약된 필수/조건부/제외 경계를 임의로 넓히지 않는다.
- 필수 흐름은 PDF/TXT 업로드, 격리와 승인, 로컬 임베딩, OpenSQL exact 검색, 활성 버전 전환, C/S/O 권한 필터, MCP `search_documents`, DB 장애 실험이다.
- OCR, Kafka, Redis, RabbitMQ, Kubernetes, DOCX, HNSW, 외부 AI 실제 호출, 복잡한 RLS, API 이중화는 선행 Gate를 통과하기 전 추가하지 않는다.
- OpenSQL/OpenProxy/OpenHA/OpenCrypto 바이너리와 실제 기관 문서는 저장소에 넣지 않는다.

## 기술 기준

- Java 17, Spring Boot 4.1, Spring AI 2.0, Gradle Wrapper를 사용한다.
- Java 패키지 루트는 `com.prizm`이다.
- 백엔드는 모듈형 모놀리스이며 REST, MCP, Worker가 같은 애플리케이션 안에서 Application Service를 공유한다.
- JPA는 일반 CRUD에, `JdbcTemplate`은 벡터 검색, 작업 claim, 배치 저장에 사용한다.
- 로컬 개발 DB는 PostgreSQL 16 + pgvector이며, 실제 OpenSQL 검증은 `opensql` 프로필과 별도 환경에서 수행한다.
- 프런트엔드는 `frontend/`의 React + TypeScript + Vite 애플리케이션이다.

## 설계 불변식

- 승인되지 않은 문서와 `ACTIVE`가 아닌 버전은 검색 결과에 포함하지 않는다.
- 한 문서에는 활성 버전이 최대 하나만 존재한다.
- 새 버전의 모든 청크와 임베딩 저장이 성공하기 전에는 기존 활성 버전을 유지한다.
- REST와 MCP는 같은 `SearchService`와 `PolicyService`를 호출한다.
- 권한 없는 등급을 애플리케이션에서 조회한 뒤 필터링하지 말고 검색 SQL에 허용 등급을 전달한다.
- C/S 문서의 본문이나 검색 컨텍스트를 외부 AI 어댑터에 전달하지 않는다.
- 변경 요청은 자동 재시도하지 않는다. 장애 재시도는 연결 단절 계열의 읽기 전용 검색으로 제한한다.

## 설정과 비밀정보

- 로컬 설정은 `.env.example`을 복사한 `.env`에서 시작하며 `.env`는 커밋하지 않는다.
- 비밀번호, JWT secret, API key, 토큰, 문서 본문, 절대 파일 경로를 코드나 로그에 남기지 않는다.
- 브라우저에 포함되는 `VITE_*` 환경변수에는 비밀정보를 넣지 않는다.
- 임베딩 차원은 모델과 DB 컬럼에서 일치해야 한다. BGE-M3 후보의 기준값은 1024다.

## 변경 원칙

- 현재 작업에 필요한 최소 파일만 수정하고 사용자 또는 다른 에이전트의 관련 없는 변경을 보존한다.
- 기능 구현과 함께 Flyway migration, 실패 경로, 권한 경계 테스트를 추가한다.
- Flyway migration은 이미 적용된 파일을 수정하지 않고 새 버전 파일로 추가한다.
- 새로운 인프라나 라이브러리를 추가하기 전에 필수 범위로 해결할 수 없는 이유를 기록한다.
- 아직 구현하지 않는 기능의 starter, API key, 보안 설정은 미리 추가하지 않고 해당 기능의 첫 테스트와 함께 도입한다.
- 생성 파일, IDE 파일, 업로드 원본, DB 볼륨, 모델 파일은 커밋하지 않는다.

## 검증 명령

Windows:

```powershell
.\gradlew.bat clean check
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

Unix 계열:

```bash
./gradlew clean check
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

DB 또는 Ollama가 필요한 테스트를 실행했다면 사용한 프로필과 외부 서비스 상태도 결과에 함께 남긴다.
