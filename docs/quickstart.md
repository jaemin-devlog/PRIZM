# PRIZM 로컬 Quickstart

> 상태: Docker Compose 기동과 health 확인 절차는 제공하지만, 신규 사용자의
> 로그인·업로드·검색 전체 demo는 아직 `NOT_RUN`이다.

이 문서는 현재 source-only PRIZM 저장소를 로컬에서 기동하는 최소 절차를
설명한다. 구현·검증의 최종 판단은 source code, Flyway migration, 실행 가능한
test이며, 현재 기능 범위는 [현재 구현 현황](project-status.md)을 따른다.

## 이 절차로 확인할 수 있는 범위

- PostgreSQL 16+pgvector, Spring Boot backend, React Career Vault frontend의
  Docker Compose 기동
- backend health endpoint 응답
- 호스트 Ollama와 `bge-m3`를 사용할 수 있도록 한 연결 준비

## 이 절차로 아직 확인할 수 없는 범위

- 신규 `USER`의 로그인·문서 업로드·ACTIVE 전환·검색 전체 흐름
- 실제 OpenSQL, OpenProxy, OpenHA 호환성
- DB failover, MCP, CareerFact, portfolio 생성

회원가입·안전한 demo `USER` 생성 경로가 아직 없다. `SYSTEM_ADMIN` bootstrap은
관리 역할만 만들며 개인 문서 API는 `USER` 역할만 허용하므로, 이를 demo 계정으로
사용하면 안 된다. 이 제한은 숨기지 않으며 clean-clone demo 작업에서 해결한다.

## 사전 준비

- Docker Desktop과 Docker Compose
- 호스트에서 실행되는 Ollama
- `ollama pull bge-m3`로 준비한 `bge-m3` 모델
- backend source 검증에는 Java 17
- frontend source 검증에는 Node `22.17.0`과 npm `10.9.2`

PostgreSQL·pgvector, Ollama binary, `bge-m3` model weights/cache는 PRIZM
repository에 포함되지 않는다. 각각의 공식 upstream에서 사용자가 직접 설치한다.
OpenSQL은 이 Quickstart의 대상이 아니며 [OpenSQL 기술 Gate](opensql-gate.md)에서
별도로 다룬다.

## 1. 로컬 환경 파일 만들기

```powershell
Copy-Item .env.example .env
```

`.env`에서 최소한 다음 값을 실제 로컬 비밀값으로 변경한다.

```text
PRIZM_JWT_SECRET=<32 UTF-8 bytes 이상인 임의의 비밀값>
PRIZM_DB_PASSWORD=<로컬 runtime DB 비밀번호>
PRIZM_FLYWAY_PASSWORD=<로컬 migration DB 비밀번호>
```

`PRIZM_JWT_SECRET`은 비어 있거나 공개 placeholder이면 backend가 시작하지
않는다. `.env`는 Git ignore 대상이며 커밋하거나 공유하지 않는다. 나머지 변수의
의미와 기본값은 [.env.example](../.env.example)을 따른다.

## 2. Ollama 모델 준비

```powershell
ollama pull bge-m3
```

Compose backend는 기본적으로 호스트 Ollama의 `http://host.docker.internal:11434`를
사용한다. 다른 주소를 사용한다면 `.env`의 `PRIZM_COMPOSE_OLLAMA_BASE_URL`을
로컬 환경에 맞게 바꾼다. 모델 이름은 `PRIZM_EMBEDDING_MODEL`로 바꿀 수 있지만,
현재 구현과 검증 기준은 `bge-m3` 1024차원이다.

## 3. Compose 구성 확인 및 기동

```powershell
docker compose config
docker compose up -d --build
```

정상 기동 여부는 다음처럼 확인한다.

```powershell
docker compose ps
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object -ExpandProperty Content
```

브라우저에서는 `http://localhost:5173`에서 Career Vault frontend를 열 수 있다.
코드를 바꾼 뒤에는 `docker compose up -d --build`로 이미지를 다시 만든다.

## 4. 중지

```powershell
docker compose down
```

이 명령은 Compose container만 내린다. 로컬 DB·runtime volume을 삭제하려는
명령은 이 Quickstart에 포함하지 않는다.

## 추가 검증

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
```

이 명령의 환경별 결과는 [개발 기록](development-log.md)과 기능별
[Spec Registry](../specs/README.md)에서 확인한다. PostgreSQL·pgvector 테스트
성공은 OpenSQL 성공을 뜻하지 않는다.

## 공개·배포 경계

PRIZM은 현재 source-only Apache-2.0 배포다. 자세한 범위는
[NOTICE](../NOTICE), [license·provenance 감사](contest/2026-license-audit.md),
[SBOM 및 AI 모델 명세](contest/2026-sbom-model-manifest.md)를 따른다.
