# PRIZM

> 문서를 업로드하면 원본과 버전을 보존하고, 변경 로그 기반 자동 임베딩과
> 사용자별 근거 검색까지 한 흐름으로 처리하는 오픈소스 AI 문서 관리 플랫폼입니다.

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

현재 대회 초점은 OpenSQL 실행 경로에서 문서 업로드, 자동 임베딩, ChangeLog
동기화, 안전한 `ACTIVE` 버전 전환과 원문 근거 검색을 원스톱으로 제공하는
**자동화된 AI 문서 관리 플랫폼**입니다.

현재 구현은 다음과 같습니다.

- 첫 화면에서 일반 `USER` 계정을 만든 뒤 기존 이메일·비밀번호 로그인으로
  Career Vault에 들어갑니다. 회원가입 직후 자동 로그인하거나 JWT를 발급하지 않습니다.
- 로그인 뒤에는 사용자를 DB에서 다시 확인하며 사용자별 문서와 검색 결과를 분리합니다.
- 기본적으로 꺼져 있는 one-time demo `USER` bootstrap과 합성 TXT/PDF 검증
  도구도 계속 제공합니다.
- UTF-8 TXT와 텍스트가 포함된 PDF를 업로드하고 관리합니다.
- 등록한 문서 버전은 직접 고치지 않고 바뀌지 않는 새 버전(immutable version)으로
  보존합니다.
- 새 문서 버전과 owner-scoped ChangeLog를 함께 저장하고, Dispatcher가 기존 색인
  작업으로 멱등 전달합니다.
- 문서를 비동기로 추출·분할·임베딩하고, 처리가 끝난 버전만 검색에
  사용합니다. 이 버전을 검색 대상 버전(active version)이라고 합니다.
- 문서 목록과 상세에서 실제 처리 단계·청크 진행 수·재시도 정보와 안전한 실패
  원인을 갱신하며, 최종 상태가 되면 polling을 중지합니다.
- Ollama `bge-m3`와 PostgreSQL pgvector로 원문 근거를 검색합니다.
- Career Vault에서 문서 목록·필터·상세·수정·삭제, 새 버전 등록, PDF 열람과
  최대 5개의 Career Evidence 검색을 제공합니다.
- 표준 MCP client에 Bearer JWT를 설정하면, 활성 `ROLE_USER`가 `POST /mcp`의
  `search_career_evidence` 도구로 같은 Career Evidence 검색을 읽기 전용으로 실행할
  수 있습니다.

CareerFact, 근거 기반 portfolio 생성, `/api/v1`, 독립 Engine 패키지와
기관용 workspace는 아직 구현되지 않았습니다. 구체적인 기능과 제한은
[현재 구현 현황](docs/project-status.md)을 기준으로 확인합니다.

### MCP Career Evidence 검색

- 요청 주소(endpoint): `POST /mcp`
- 통신 방식: 연결 상태를 서버에 저장하지 않는(stateless) Streamable HTTP
- 통신 규격(protocol): `2025-11-25`
- 도구와 입력값: `search_career_evidence`, `{"query":"..."}`
- 인증: `Authorization: Bearer <USER_JWT>` 헤더가 필요하며 활성 `ROLE_USER`만 허용

이 도구는 별도 검색 알고리즘을 두지 않고 기존
`SearchService.searchCareerEvidenceV2(...)`를 그대로 재사용합니다. 따라서 기존
사용자별 데이터 격리(owner isolation)와 현재 `ACTIVE` 버전만 검색하는 규칙
(ACTIVE isolation)도 동일하게 적용됩니다. 로컬 Compose 예제에서 표준 MCP client를
연결하는 방법은
[Quickstart](docs/quickstart.md#mcp-career-evidence-검색)를 따릅니다.

## 인증 진입 흐름

| 단계 | 화면과 동작 |
|---|---|
| 회원가입 | 이메일과 비밀번호로 활성 `USER` 계정을 만듭니다. JWT나 세션은 생성하지 않습니다. |
| 로그인 | 가입한 이메일·비밀번호로 기존 JWT 로그인을 수행한 뒤 Career Vault로 이동합니다. |

이 회원가입은 자체 호스팅용 최소 기능입니다. 이메일 인증, 비밀번호 재설정,
refresh token, OIDC와 공개 SaaS 운영 보호 기능은 제공하지 않습니다. 기본 Compose는
포트를 `127.0.0.1`에만 엽니다. 문서와 계정은 브라우저가 아니라 PostgreSQL과
Docker volume에 저장됩니다.

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

기본 Compose 화면에서는 새 계정을 가입한 뒤 로그인합니다. 위 스크립트가 만드는
별도의 `demo@prizm.local` 계정은 자동 검증용이며, 최초 기동 뒤 bootstrap을 끄고
backend를 다시 만들어야 합니다. 계정 로그인, 합성 TXT/PDF 업로드→`ACTIVE`→원문 출처 검색, 브라우저 확인과
종료까지의 정확한 절차는 [로컬 Quickstart](docs/quickstart.md)를 따릅니다.
PRZ-004에서는 PostgreSQL·pgvector와 호스트 Ollama 기반 전체 흐름을 두 독립
환경에서 검증했습니다. 독립 감사와 GitHub PR #25의 CI를 통과해 `main`에
통합했습니다. 자세한 실행 기준과 아직 검증하지 않은 범위는
[PRZ-004 Evidence](specs/PRZ-004-clean-clone-demo/evidence.md)에서 확인합니다.

## 검증 범위

- 실제 OpenSQL single-node에서 Flyway V1–V15, `vector(1024)`, 검색과 Worker
  SQL을 실행해 통과했습니다(`PASS`).
- PRZ-005에서 Spring Boot와 Ollama `bge-m3`를 실제 OpenSQL `5432`에 연결해
  로그인→합성 TXT/PDF 업로드→임베딩→`ACTIVE`→원문 검색과 브라우저 흐름,
  두 사용자 격리를 검증했습니다. [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26)으로
  `main`에 통합했습니다(`VERIFIED`).
- PRZ-010에서 OpenSQL direct `5432`의 V14 ChangeLog schema·멱등 dispatch와 실제
  OpenSQL·Ollama V1→V2 흐름을 검증했고, 후속 G0에서 V15 기준선을 재검증했습니다.
- PRZ-013에서 OpenProxy `:6432`의 단일 Primary SQL routing, `prizm_app` 인증,
  Flyway direct/runtime proxy 분리와 focused TXT/PDF·Ollama 흐름을
  `VERIFIED`했습니다.
- PRZ-015에서는 공식 Java MCP Client와 실제 `ROLE_USER` JWT로 MCP 전체 흐름(E2E)을
  검증했습니다. Flyway는 OpenSQL `:5432`에 직접 연결했고, 애플리케이션은 OpenProxy
  `:6432/opensql`을 거쳐 실행했으며, Ollama `bge-m3`로 임베딩했습니다. REST와 MCP
  결과 일치(REST/MCP parity), 사용자별 격리와 `ACTIVE` 버전 격리를 모두 통과해
  `VERIFIED`했습니다.
- 대회 제공 OpenSQL은 단일 서버 설치 범위이므로 다중 노드 OpenHA와 DB 장애
  전환은 현재 대회·제품 로드맵에서 제외합니다.
- PostgreSQL·pgvector 테스트 성공은 OpenSQL 검증 결과로 사용하지 않습니다.

현재 배포물은 소스와 설정만 제공하는 Apache-2.0 소스 전용(source-only)
범위입니다. PostgreSQL·pgvector·Ollama·`bge-m3`는 사용자가 각 upstream에서
직접 준비합니다. 컨테이너 이미지(container image), 모델 가중치, OpenSQL 공급
자산과 DB 볼륨(volume)은 재배포하지 않습니다. 미래에 binary·image·model을
배포하려면 별도의 검증 단계(Gate)가 필요합니다. 적용 라이선스는
[LICENSE](LICENSE)와 [NOTICE](NOTICE), 구성요소·모델·배포 경계와 checksum은
[SBOM 안내](sbom/README.md)에서 확인할 수 있습니다.

## 문서

- [문서 안내와 독자별 탐색 경로](docs/README.md)
- [시스템 아키텍처와 핵심 데이터 흐름](docs/architecture.md)
- [로컬 실행 절차](docs/quickstart.md)
- [현재 구현 현황](docs/project-status.md)
- [개발 로드맵](docs/roadmap.md)
- [Spec Registry와 검증 근거](specs/README.md)
