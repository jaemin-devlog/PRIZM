# PRZ-002 — Open-source readiness

## 목적

PRIZM의 공개 source와 제출 산출물을 재사용할 수 있도록 공식 출처, 저작권,
라이선스, 외부 구성요소와 배포 경계를 검증 가능한 형태로 정리한다. 이 작업은
새 제품 기능이나 OpenSQL 호환성을 구현하는 작업이 아니다.

## 범위

- 대회 공식 규정·홈페이지·결과보고서 양식과 OT 보조 근거의 출처 등록
- Java·Gradle, npm, container·database, CI, Ollama·`bge-m3`, fixture·asset의
  license·provenance 감사
- 사용자가 승인한 OSI 라이선스, 제3자 고지와 source-only 배포 경계
- 재현 가능한 SBOM과 AI 모델 명세
- README·Quickstart·문서 색인과 license·SBOM 검증 자동화
- 외부 기여·보안 운영을 실제로 시작하기 전까지의 명시적 재개 조건

## 요구사항

| ID | 요구사항 |
|---|---|
| `OR-001` | 공식 운영 규정, 공식 홈페이지, 결과보고서 양식과 OT 보조 근거의 출처·발행일 또는 수집일·SHA-256·저작권/재배포 제한·PRIZM 적용 항목을 source register에 기록해야 한다. |
| `OR-002` | Java/Gradle, frontend npm, Docker/컨테이너, PostgreSQL·pgvector, Ollama와 `bge-m3`, 테스트 fixture·예제·이미지·문서 자산을 대상으로 버전·출처·라이선스·사용 목적·배포 여부·NOTICE 의무를 감사해야 한다. |
| `OR-003` | 감사 후 사용자가 승인한 OSI 인증 프로젝트 라이선스를 루트 `LICENSE`에 적용하고, 필요한 제3자 고지와 예외를 `NOTICE`와 라이선스 감사 문서에 일관되게 기록해야 한다. |
| `OR-004` | PRIZM의 직접 작성 코드 저작권자를 `Jaemin Jeong`으로 기록하고 Codex는 개발 보조도구로 분리해야 한다. AI 사용을 특정 제3자 코드·자산을 복사하지 않았다는 보증으로 바꾸지 않아야 한다. |
| `OR-005` | CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, SUPPORT와 최소 maintainer 정책은 외부 기여 접수 또는 첫 지원 release·외부 배포 중 먼저 도래하는 시점에 재개해야 한다. 실제 운영 가능한 신고 경로만 사용하고 비밀정보를 공개 Issue에 올리지 않도록 안내해야 한다. |
| `OR-006` | Bug·feature·documentation Issue form과 PR template은 외부 Issue·PR 접수를 공식 지원하기 전에 재개해야 한다. 템플릿 자체를 review 증거로 주장하지 않아야 한다. |
| `OR-007` | 공개 진입 문서는 Quickstart, 현재·계획 기능, 검증 환경, 한계와 license·SBOM을 연결해야 한다. OpenSQL single-node 결과를 OpenProxy·OpenHA·DB failover 또는 전체 사용자 흐름으로 확대하지 않고 CareerFact·portfolio·MCP를 구현된 것처럼 표현하지 않아야 한다. |
| `OR-008` | SBOM과 AI 모델 명세에 재사용 가능한 provenance 구조를 제공하되 업로드 문서, credential, 모델 cache, DB volume과 사용자 로컬 경로를 포함하지 않아야 한다. |

## 보존 계약

- PRZ-000의 문서·버전·`active_version_id`, owner-scoped 검색, JWT DB 재검증,
  processing·cleanup lease와 fencing, Flyway V1~V13과 TXT/PDF 계약을 바꾸지 않는다.
- PostgreSQL 결과를 OpenSQL·OpenProxy·OpenHA 호환성 증거로 바꾸지 않는다.
- 원본 경력 문서, JWT, 비밀번호, 전체 JDBC URL, 저장 경로, 모델 파일과 빌드
  산출물을 공개 source에 추가하지 않는다.
- 공식 원문과 OT 캡처는 재배포 권한을 가정하지 않고 source register의 링크,
  식별 정보와 필요한 최소 해석만 공개한다.

## 제외 범위

- OpenSQL 실행과 OpenProxy·OpenHA·DB failover 검증
- CareerFact, portfolio, MCP, `/api/v1`와 독립 Engine artifact 구현
- 검색 알고리즘, Reranker, Hybrid Search와 embedding 모델 교체
- Flyway migration, production source/config와 frontend 기능 변경
- Docusaurus 사이트, self-hosted CI matrix와 인위적인 Issue·PR·review 생성
- JAR·`dist`·container image·Ollama binary·모델 가중치의 향후 배포 결정

## 측정 가능한 완료 조건

1. Source register가 공식 원본과 OT 보조 근거의 권한·추적 한계를 구분한다.
2. 현재 source-only 배포 범위의 모든 발견 구성요소가 판정되고 `UNKNOWN`,
   `CONFLICT`, `BLOCKED`가 0건이다.
3. 루트 `LICENSE`·`NOTICE`와 라이선스 감사 결과가 일치한다.
4. 미구현 거버넌스 항목은 `DEFERRED`와 재개 조건으로 일관되게 기록한다.
5. README·Quickstart·문서 색인이 현재 구현·계획·미검증 환경을 구분한다.
6. SBOM·AI 모델 명세를 clean checkout에서 재생성·검증할 수 있고 checksum과
   민감정보 제외 검사가 통과한다.
7. 독립 감사에서 라이선스 충돌, 출처 누락, 민감정보 노출과 구현 과장에 대한
   CRITICAL·HIGH·MEDIUM finding이 0건이다.
