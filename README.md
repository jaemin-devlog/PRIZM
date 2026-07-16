# PRIZM

PRIZM의 공식 제품 정의는 다음과 같습니다.

> PRIZM은 커리어 문서의 분석, 정보 구조화, 근거 검색 및 포트폴리오 생성을 위한 오픈소스 Career Intelligence Engine과 이를 검증하는 Reference App이다. 개인뿐 아니라 대학, 취업 지원기관, 기업 및 개발자가 각자의 환경에 맞는 커리어 관리 서비스를 구축할 수 있도록 재사용 가능한 모듈과 확장 지점을 제공한다.

등록 문서에서 확인되지 않은 경력·기술·성과·수치를 만들지 않으며, 근거가 없을 때는 현재 등록된 문서에서 찾지 못했다고 답하는 것이 핵심 원칙입니다.

이 정의는 목표 제품 경계입니다. 현재 저장소는 아직 재사용 가능한 모듈로 분리된 엔진 패키지가 아니라 하나의 Spring Boot 애플리케이션과 React 기반 **Career Vault Reference App**으로 구성됩니다. 개인용 Career Vault는 PRIZM 전체 제품이 아니라 Engine의 현재 기능과 통합 방식을 보여주는 Reference App입니다.

```text
업로드 → 비동기 추출·청킹·임베딩 → ACTIVE 전환 → 원문·출처 검색
```

## 현재 제공 범위

- Engine 기반: JWT 인증, 사용자별 문서·버전·작업·청크 격리, 로컬 원본 저장, TXT·텍스트 PDF 처리, Flyway schema, lease·retry·fencing 기반 비동기 색인, PostgreSQL pgvector exact cosine 검색, 고아 원본 파일 Cleanup Worker
- Reference App: 로그인, 문서 목록·12개 유형 필터, TXT·PDF 업로드, 관련 원문 근거 최대 5개 표시
- 현재 API: 로그인·현재 사용자·문서 업로드/목록/상세·단일 검색·Career Evidence 검색·health

CareerFact 구조화, 포트폴리오 생성, `/api/v1`, MCP, 멀티모듈 패키징, 기관용 workspace와 OpenSQL/OpenProxy/OpenHA 실환경 호환성은 계획 범위이며 현재 구현이 아닙니다. 자세한 현재/계획 경계는 [오픈소스 제품 경계](docs/architecture/oss-product-boundary.md)와 [현재 구현 현황](docs/project-status.md)을 참고합니다.

## 실행

현재 `compose.yaml`은 PostgreSQL 16+pgvector만 실행합니다. 백엔드, 프런트엔드와 Ollama는 호스트에서 별도로 실행해야 합니다.

```powershell
Copy-Item .env.example .env
ollama pull bge-m3
docker compose up -d
.\gradlew.bat bootRun
```

프런트엔드:

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

현재 bootstrap은 `SYSTEM_ADMIN`만 만들 수 있고 해당 역할은 개인 문서 API를 사용할 수 없습니다. 회원가입이나 demo `USER` 생성 경로가 아직 없어 위 절차만으로 신규 사용자의 Career Vault 흐름을 완주할 수 없습니다. 재현 가능한 Quickstart는 [오픈소스 엔진 전환 실행 계획](docs/oss-transition-execution-plan.md)의 후속 단계입니다.

Cleanup Worker는 `SecureDirectoryStream`을 지원하는 filesystem에서만 descriptor-relative 삭제를 수행합니다. 지원하지 않는 filesystem에서는 안전하지 않은 경로 기반 삭제로 fallback하지 않고 fail-closed하므로 자동 cleanup이 동작하지 않을 수 있습니다. 이 운영 제약과 환경 예제 보완은 단계 2의 Quickstart 작업으로 남아 있습니다.

## 검증

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config
```

## 문서

- [현재 구현 현황](docs/project-status.md)
- [오픈소스 제품 경계](docs/architecture/oss-product-boundary.md)
- [오픈소스 엔진 전환 실행 계획](docs/oss-transition-execution-plan.md)
- [개발 기록](docs/development-log.md)
- [장기 기획안](docs/PRIZM_최종_기획안.md)
- [OpenSQL 기술 Gate](docs/opensql-gate.md)
