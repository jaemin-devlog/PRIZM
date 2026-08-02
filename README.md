# PRIZM

> 이력서·자기소개서·경력기술서처럼 흩어진 커리어 문서의 원본, 버전, 처리 상태와
> 검색 근거를 한 흐름으로 관리하기 위한 오픈소스 프로젝트입니다.

## PRIZM이 해결하려는 문제

커리어 기록은 여러 문서에 흩어지고 같은 문서도 여러 버전으로 쌓입니다. 필요한
경험이나 성과를 찾더라도 어느 원문에서 나온 정보인지 확인하기 어렵습니다.
PRIZM은 문서 원본과 버전을 보존하고, 등록된 문서 안에서 관련 근거를 찾아
원문 위치와 함께 보여 줍니다.

등록 문서에 없는 경력·기술·성과·수치를 만들어 내지 않습니다. 근거를 찾지
못하면 현재 등록된 문서에서 찾지 못했다고 답합니다.

**P**otential · **R**ecord · **I**dentity · **Z**one · **M**emory

프리즘이 하나의 빛에서 여러 색을 드러내듯, PRIZM은 흩어진 기록의 근거를
분석해 개인의 경험·역량·성과를 이해할 수 있는 형태로 보여 주는 것을
지향합니다.

## 현재 제공하는 것

PRIZM의 장기 목표는 여러 환경에서 재사용할 수 있는 오픈소스 **Career
Intelligence Engine**과 Reference App을 제공하는 것입니다. 현재 저장소는 아직
독립 Engine 패키지가 아닙니다. 지금 제공하는 구현은 하나의 Spring Boot
애플리케이션과 React 기반 **Career Vault Reference App**입니다.

현재 구현은 다음과 같습니다.

- JWT 로그인 뒤 사용자를 DB에서 다시 확인하고, 사용자별 문서와 검색 결과를
  분리합니다.
- 기본적으로 꺼져 있는 one-time demo `USER` bootstrap과 합성 TXT/PDF 검증
  도구를 제공합니다. 공개 회원가입 API는 제공하지 않습니다.
- UTF-8 TXT와 텍스트가 포함된 PDF를 업로드하고 관리합니다.
- 등록한 문서 버전은 직접 고치지 않고 바뀌지 않는 새 버전(immutable version)으로
  보존합니다.
- 문서를 비동기로 추출·분할·임베딩하고, 처리가 끝난 버전만 검색에
  사용합니다. 이 버전을 검색 대상 버전(active version)이라고 합니다.
- Ollama `bge-m3`와 PostgreSQL pgvector로 원문 근거를 검색합니다.
- Career Vault에서 문서 목록·필터·상세·수정·삭제, 새 버전 등록, PDF 열람과
  최대 5개의 Career Evidence 검색을 제공합니다.

CareerFact, 근거 기반 portfolio 생성, `/api/v1`, MCP, 독립 Engine 패키지와
기관용 workspace는 아직 구현되지 않았습니다. 구체적인 기능과 제한은
[현재 구현 현황](docs/project-status.md)을 기준으로 확인합니다.

## 최소 실행

Docker Desktop과 호스트에서 실행 중인 Ollama가 필요합니다. 다음 명령은 도구,
감사된 `bge-m3` identity와 예시 포트를 확인한 뒤, 비밀값을 출력하지 않고 고유한
Compose project용 `.env`를 만듭니다.

```powershell
node scripts/check-clean-clone-prerequisites.mjs --db-port 15433 --backend-port 18081 --frontend-port 15174
node scripts/prepare-clean-clone-demo-env.mjs --db-port 15433 --backend-port 18081 --frontend-port 15174
node scripts/generate-clean-clone-demo-fixtures.mjs
node scripts/run-clean-clone-compose.mjs config --quiet
node scripts/run-clean-clone-compose.mjs up -d --build
```

최초 기동에서 demo 계정을 만든 뒤에는 bootstrap을 끄고 backend를 다시 만들어야
합니다. 로그인→합성 TXT/PDF 업로드→`ACTIVE`→원문 출처 검색, 브라우저 확인과
종료까지의 정확한 절차는 [로컬 Quickstart](docs/quickstart.md)를 따릅니다.
PRZ-004에서는 PostgreSQL·pgvector와 호스트 Ollama 기반 전체 흐름을 두 독립
환경에서 검증했습니다. 독립 감사와 GitHub PR #25의 CI를 통과해 `main`에
통합했습니다. 자세한 실행 기준과 아직 검증하지 않은 범위는
[PRZ-004 Evidence](specs/PRZ-004-clean-clone-demo/evidence.md)에서 확인합니다.

## 검증 범위

- 실제 OpenSQL single-node에서 Flyway V1~V13, `vector(1024)`, 검색과 Worker
  SQL을 실행해 통과했습니다(`PASS`).
- PRZ-005에서 Spring Boot와 Ollama `bge-m3`를 실제 OpenSQL `5432`에 연결해
  로그인→합성 TXT/PDF 업로드→임베딩→`ACTIVE`→원문 검색과 브라우저 흐름,
  두 사용자 격리를 검증했습니다. [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26)으로
  `main`에 통합했습니다(`VERIFIED`).
- OpenProxy는 Windows 호스트의 TCP 연결만 `VERIFIED`입니다. SQL routing은
  `NOT_VERIFIED`, 인증은 `AUTH_BLOCKED`, 애플리케이션 적용은 `DEFERRED`입니다.
  OpenHA·DB failover도 `DEFERRED`입니다.
- PostgreSQL·pgvector 테스트 성공은 OpenSQL 검증 결과로 사용하지 않습니다.

현재 배포물은 소스와 설정만 제공하는 Apache-2.0 소스 전용(source-only)
범위입니다. PostgreSQL·pgvector·Ollama·`bge-m3`는 사용자가 각 upstream에서
직접 준비합니다. 컨테이너 이미지(container image), 모델 가중치, OpenSQL 공급
자산과 DB 볼륨(volume)은 재배포하지 않습니다. 미래에 binary·image·model을
배포하려면 별도의 검증 단계(Gate)가 필요합니다. 현재 결론은
[source-only compliance](docs/contest/2026-compliance.md), 적용 라이선스는
[LICENSE](LICENSE)와 [NOTICE](NOTICE), 상세 근거는
[라이선스·출처 감사](docs/contest/2026-license-audit.md)와
[SBOM·AI 모델 명세](docs/contest/2026-sbom-model-manifest.md)에서 확인할 수 있습니다.

## 문서

- [문서 안내와 독자별 탐색 경로](docs/README.md)
- [시스템 아키텍처와 핵심 데이터 흐름](docs/architecture.md)
- [로컬 실행 절차](docs/quickstart.md)
- [현재 구현 현황](docs/project-status.md)
- [개발 로드맵](docs/roadmap.md)
- [대회 요구사항·평가기준 추적표](docs/contest/2026-requirements-traceability.md)
- [Spec Registry와 검증 근거](specs/README.md)
