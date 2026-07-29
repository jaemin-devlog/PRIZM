# PRIZM

> 이력서·자기소개서·경력기술서처럼 흩어진 커리어 문서의 원본, 버전, 처리 상태와
> 검색 근거를 한 흐름으로 관리하기 위한 오픈소스 프로젝트입니다.

PRIZM은 커리어 문서의 분석, 정보 구조화, 근거 검색 및 포트폴리오 생성을 위한
오픈소스 **Career Intelligence Engine**과 이를 검증하는 **Reference App**입니다.
개인·대학·취업 지원기관·기업·개발자가 각자의 환경에 맞는 커리어 관리 서비스를
만들 수 있도록 재사용 가능한 모듈과 확장 지점을 제공하는 것이 목표입니다.

현재 저장소는 아직 독립 Engine 패키지 모음이 아닙니다. 하나의 Spring Boot
애플리케이션과 React 기반 **Career Vault Reference App**이 현재 Engine의 기능과
통합 방식을 보여 줍니다. Career Vault는 PRIZM 전체 제품이 아닙니다.

### 이름에 담긴 제품 비전

**P**otential · **R**ecord · **I**dentity · **Z**one · **M**emory

PRIZM은 흩어진 기록의 근거를 분석해, 거짓 없이 개인의 경험·역량·성과를
이해할 수 있는 형태로 보여 주는 것을 지향합니다.

등록 문서에 없는 경력·기술·성과·수치를 만들어 내지 않으며, 근거를 찾지 못하면
현재 등록된 문서에서 찾지 못했다고 답하는 것을 원칙으로 합니다.

```text
업로드 → 비동기 추출·청킹·임베딩 → ACTIVE 전환 → 원문·출처 검색
```

## 현재 범위

| 구분 | 상태 | 범위 |
|---|---|---|
| 구현됨 | Engine 기반 | JWT와 DB 사용자 재검증, 사용자별 문서·version·job·chunk 격리, TXT·text-layer PDF 처리, immutable version, 비동기 색인·복구, PostgreSQL pgvector exact cosine 검색, 고아 원본 Cleanup Worker |
| 구현됨 | Career Vault Reference App | 로그인, 목록·유형/제목/상태 필터, TXT·PDF 업로드, 상세·수정·삭제, 새 version 등록, PDF thumbnail·원본 열람, 최대 5개 Career Evidence 검색 |
| 구현됨 | 현재 API | 로그인·현재 사용자·문서 업로드/목록/상세/수정/삭제·새 version·PDF thumbnail/원본·단일 검색·Career Evidence 검색·health |
| 계획됨 | 제품 확장 | CareerFact, 근거 기반 portfolio, `/api/v1`, MCP, 멀티모듈 Engine 패키징, 기관용 workspace |
| 부분 검증 | 외부 DB 환경 | 실제 OpenSQL single-node에서 Flyway V1~V13·`vector(1024)`·검색·ownership·Worker SQL Gate `PASS`; OpenProxy·OpenHA·DB failover와 OpenSQL+Ollama 전체 흐름은 `NOT_RUN` 또는 `NOT_VERIFIED` |

구체적인 구현 근거와 제한은 [현재 구현 현황](docs/project-status.md)을 기준으로
합니다.

## 빠른 시작: 현재 재현 가능한 범위

이 절차는 로컬 Docker Compose 기동과 health 확인을 위한 현재 Quickstart입니다.
회원가입·안전한 demo `USER` 생성은 아직 없으므로, 새 설치자가 이 문서만으로
로그인→업로드→검색까지 완주할 수 있는 절차는 아닙니다. 정확한 제한과 명령은
[로컬 Quickstart](docs/quickstart.md)를 확인하세요.

사전 준비:

- Docker Desktop과 Docker Compose
- 호스트에서 실행되는 Ollama
- Ollama의 `bge-m3` 모델
- `.env`에 별도로 설정한 32 UTF-8 bytes 이상의 JWT secret과 로컬 DB 비밀번호

```powershell
Copy-Item .env.example .env
# .env의 PRIZM_JWT_SECRET, PRIZM_DB_PASSWORD,
# PRIZM_FLYWAY_PASSWORD를 로컬 비밀값으로 변경한다.
ollama pull bge-m3
docker compose config
docker compose up -d --build
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object -ExpandProperty Content
```

접속 주소:

- Career Vault: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`

`.env`와 업로드 원본, DB volume, 모델 cache는 커밋하지 않습니다. `SYSTEM_ADMIN`
bootstrap은 개인 문서 API를 사용할 수 없는 관리 역할만 만들므로 demo 사용자
대신 사용하면 안 됩니다. 안전한 demo `USER`와 clean-clone 전체 흐름은 별도
후속 작업입니다.

Cleanup Worker는 `SecureDirectoryStream`을 지원하는 filesystem에서만
descriptor-relative 삭제를 수행합니다. 미지원 filesystem에서는 안전하지 않은
경로 기반 삭제로 fallback하지 않고 fail-closed하므로 자동 cleanup이 동작하지
않을 수 있습니다.

## 검증 명령

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

PostgreSQL·pgvector 결과와 OpenSQL 결과는 구분합니다. 실제 OpenSQL
single-node SQL Gate만 검증됐으며, OpenProxy·OpenHA·DB failover와
OpenSQL+Ollama 전체 사용자 흐름은 아직 `NOT_RUN` 또는 `NOT_VERIFIED`입니다.

## 라이선스와 공급망

- [Apache-2.0 LICENSE](LICENSE)
- [source-only NOTICE](NOTICE)
- [license·provenance 감사](docs/contest/2026-license-audit.md)
- [SBOM 및 AI 모델 명세](docs/contest/2026-sbom-model-manifest.md)

현재 공개 범위는 source-only입니다. PostgreSQL·pgvector, Ollama, `bge-m3`는
사용자가 공식 upstream에서 직접 설치하며, PRIZM은 container image, Ollama binary,
모델 가중치·cache, DB volume을 재배포하지 않습니다.

## 문서

- [문서 안내](docs/README.md)
- [현재 구현 현황](docs/project-status.md)
- [개발 로드맵](docs/roadmap.md)
- [개발 기록](docs/development-log.md)
- [2026 티맥스티베로 지정과제 대응 계획](docs/contest/2026-tmaxtibero-plan.md)
- [공식 요구사항·평가기준 추적표](docs/contest/2026-requirements-traceability.md)
- [Spec Registry와 AS_BUILT 기준선](specs/README.md)
