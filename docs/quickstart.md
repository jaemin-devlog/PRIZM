# PRIZM 로컬 Quickstart

> 상태: Docker Compose 기동과 상태 확인(health) 절차는 제공하지만, 신규 사용자의
> 로그인·업로드·검색 전체 데모(demo)는 아직 실행하지 않았습니다(`NOT_RUN`).

이 문서는 PRIZM을 로컬에서 기동하는 최소 절차를 설명합니다. 현재 저장소는
소스와 실행 설정만 배포하는 소스 전용(source-only) 범위입니다. 실행 파일,
컨테이너 이미지(container image)와 AI 모델은 저장소에 포함하지 않습니다.

구현·검증의 최종 판단은 소스 코드(source code), Flyway migration과 실행 가능한
테스트(test)를 따릅니다. 현재 기능 범위는 [현재 구현 현황](project-status.md)에서
확인할 수 있습니다.

## 이 절차로 확인할 수 있는 범위

- Docker Compose로 PostgreSQL 16+pgvector, Spring Boot 백엔드(backend)와 React
  Career Vault 프런트엔드(frontend)를 기동할 수 있습니다.
- 백엔드가 요청을 받을 수 있는지 상태 확인 주소(health endpoint)로 확인할 수
  있습니다.
- 백엔드가 호스트에서 실행 중인 Ollama와 `bge-m3`를 사용하도록 연결할 수
  있습니다.

## 이 절차로 아직 확인할 수 없는 범위

- 신규 `USER`로 로그인한 뒤 문서를 업로드하고, 처리 완료된 검색 대상 버전
  (active version)으로 전환해 검색하는 전체 흐름
- OpenSQL과 Ollama를 함께 사용하는 전체 사용자 흐름
- OpenProxy 애플리케이션 연결(runtime connection)과 OpenHA·DB 장애 전환(failover)
- MCP, CareerFact와 portfolio 생성

회원가입·안전한 demo `USER` 생성 경로가 아직 없습니다. 초기 관리자 계정 생성
(bootstrap)은 `SYSTEM_ADMIN` 관리 역할만 만들며 개인 문서 API는 `USER` 역할만
허용하므로, 이를 demo 계정으로
사용하면 안 됩니다. 따라서 화면이 열리고 backend health가 정상이더라도 전체
사용자 흐름이 검증된 것은 아닙니다. 현재 결과는 `NOT_RUN`입니다.

## 사전 준비

- Docker Desktop과 Docker Compose
- 호스트에서 실행 중인 Ollama
- `ollama pull bge-m3`로 준비한 `bge-m3` 모델
- backend 소스 검증에 사용할 Java 17
- frontend 소스 검증에 사용할 Node `22.17.0`과 npm `10.9.2`

PostgreSQL·pgvector 컨테이너 이미지, Ollama 실행 파일과 `bge-m3` 모델
가중치·캐시는 PRIZM 저장소에 포함되지 않습니다. 사용자가 각각의 공식
배포처(upstream)에서 직접 설치합니다.
OpenSQL은 이 Quickstart의 대상이 아니며 [OpenSQL 기술 Gate](opensql-gate.md)에서
별도로 다룹니다.

## 1. 환경 변수 준비

```powershell
Copy-Item .env.example .env
```

`.env`에서 다음 값을 실제 로컬 비밀값으로 변경합니다.

| 변수 | 용도 | 설정 방법 |
|---|---|---|
| `PRIZM_JWT_SECRET` | 로그인 토큰에 서명하는 비밀값 | 32 UTF-8 bytes 이상의 공개되지 않은 값으로 설정합니다. |
| `PRIZM_DB_PASSWORD` | 애플리케이션 실행 계정(runtime DB account)의 비밀번호 | 로컬에서만 사용할 비밀번호로 변경합니다. |
| `PRIZM_FLYWAY_PASSWORD` | DB 구조를 적용하는 마이그레이션 계정(migration account)의 비밀번호 | 이 계정에 사용할 로컬 비밀번호로 변경합니다. |

`PRIZM_JWT_SECRET`은 비어 있거나 공개 placeholder이면 backend가 시작하지
않습니다. `.env`는 Git ignore 대상이므로 커밋하거나 공유하지 않습니다. 나머지
변수의 의미와 기본값은 [.env.example](../.env.example)을 따릅니다.

## 2. Ollama 모델 준비

```powershell
ollama pull bge-m3
```

Compose backend는 기본적으로 호스트 Ollama의 `http://host.docker.internal:11434`를
사용합니다. 다른 주소를 사용한다면 `.env`의
`PRIZM_COMPOSE_OLLAMA_BASE_URL`을 로컬 환경에 맞게 바꿉니다. 모델 이름은
`PRIZM_EMBEDDING_MODEL`로 바꿀 수 있지만, 현재 구현과 검증 기준은
`bge-m3` 1024차원입니다.

## 3. Compose 구성 확인과 실행

```powershell
docker compose config
docker compose up -d --build
```

`compose.yaml`은 `db`, `backend`, `frontend` 서비스 세 개를 실행합니다.
Ollama는 Compose 서비스가 아니며 호스트에서 별도로 실행해야 합니다.

## 4. 상태 확인

서비스 상태와 backend health를 확인합니다.

```powershell
docker compose ps
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object -ExpandProperty Content
```

기본 접속 주소는 다음과 같습니다.

| 대상 | 기본 주소 |
|---|---|
| Career Vault frontend | `http://localhost:5173` |
| Backend health | `http://localhost:8080/actuator/health` |
| PostgreSQL·pgvector | `127.0.0.1:5432` |

포트는 `.env`의 `PRIZM_FRONTEND_PORT`, `SERVER_PORT`, `PRIZM_DB_PORT`로
바꿀 수 있습니다. 코드를 수정한 뒤에는 `docker compose up -d --build`로
이미지를 다시 만듭니다.

health 응답은 backend가 실행 중이라는 뜻입니다. 신규 `USER`의
로그인·업로드·검색 전체 흐름이 통과했다는 증거는 아닙니다.

## 5. 종료

```powershell
docker compose down
```

이 명령은 Compose 컨테이너(container)만 중지합니다. 로컬 DB와 실행 데이터
볼륨(runtime volume)은 삭제하지 않습니다. 볼륨 삭제 명령은 이 Quickstart에
포함하지 않습니다.

## 추가 검증

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
```

이 명령의 환경별 결과는 [개발 기록](development-log.md)과 기능별
[Spec Registry](../specs/README.md)에서 확인합니다. 통합 테스트는 Docker와
Testcontainers를 사용해 PostgreSQL·pgvector를 검증합니다. 이 성공 결과는
OpenSQL 검증 결과가 아닙니다.

실제 OpenSQL single-node 검증 범위와 재실행 조건은
[OpenSQL 기술 Gate](opensql-gate.md)를 확인하세요.

## 공개·배포 경계

PRIZM은 현재 Apache-2.0 소스 전용(source-only) 배포입니다. 자세한 범위는
[NOTICE](../NOTICE), [license·provenance 감사](contest/2026-license-audit.md),
[SBOM 및 AI 모델 명세](contest/2026-sbom-model-manifest.md)를 따릅니다.
