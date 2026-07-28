# PRZ-002 — Open-source readiness: source, license, and contribution baseline

| 항목 | 값 |
|---|---|
| Spec ID | `PRZ-002` |
| Status | `IN_PROGRESS` |
| 성격 | 2026 오픈소스 개발자대회 P0의 출처·라이선스·source-only 공개 준비 문서 |
| 시작 기준 commit | `9279d51b298765058fcf6f883f6e9701460ccacd` |
| GitHub Issue | `NOT_CREATED` — 외부 Issue 생성 권한을 아직 요청하지 않음 |
| 관련 기준선 | [PRZ-000](../PRZ-000-platform-baseline/spec.md) |

## 목적

PRIZM을 재사용 가능한 Career Intelligence Engine과 Career Vault Reference App으로
공개하기 전에, 출품작의 저작권·라이선스·외부 구성요소를 검증 가능한 형태로
정리한다. 외부 기여·보안 운영 체계는 실제 운영을 시작할 때 별도 Gate로 연다.
이 작업은 새 제품 기능이나 OpenSQL 호환성 주장이 아니라, 공개 소스와 제출
산출물의 신뢰 가능한 기준선을 만드는 P0 준비 작업이다.

## 공식 근거와 해석 경계

- 공식 홈페이지: [오픈소스 개발자대회](https://osscontest.kr/).
- `2026년 오픈소스 개발자대회 운영 규정`(2026-06, SHA-256
  `5C129ED9F389ECC04B6F7BA8B97F719A313EFAF32AEA9178E635500023AE1DA1`)의
  제7~10조에 따라, 직접 작성한 코드는 OSI 인증 라이선스를 적용하고, 사용한
  오픈소스 라이브러리·프레임워크·모델의 출처와 라이선스를 공개하며, 전체
  소스코드를 공개 저장소에 게시해야 한다.
- 같은 규정 제9조와 별표 2에 따라, PRIZM이 모델을 탑재·적용하는 경우에는 최소
  공개 가중치 모델 기준과 모델별 라이선스·이용 약관을 별도로 검토해야 한다.
- `2026년 오픈소스 개발자대회 결과보고서` 제출 양식 ZIP( SHA-256
  `9A5D2968D48FF8A8FD85CE991DC72DC2B0818D7E8C06EBB871CC97CE5CC62D95`)은
  SBOM과 AI 모델 활용·라이선스 기술 명세서를 요구한다.
- 참가자가 제공한 공식 OT 슬라이드 캡처의 원본 배포 URL은 확인되지 않았다.
  따라서 일정·평가 항목은 출처·해시와 함께 보조 근거로만 기록하며, 공개 저장소에
  슬라이드 이미지를 복사하거나 평가 점수를 확정하지 않는다.
- 운영 규정 원문은 재배포 제한을 명시한다. 저장소에는 원문 파일을 넣지 않고
  출처·버전·해시·필요 최소 인용과 PRIZM에 적용한 해석만 기록한다.

## 평가 영향

- Primary: `EVAL-R1-02` — 오픈소스 프로젝트로의 발전 가능성. OSI 라이선스,
  라이선스 감사, SBOM·AI 모델 명세와 재사용 경계를 실제 파일로 증명한다.
- Secondary: `EVAL-R1-03` — 개발 문서의 구체성. 공식 근거, SBOM, 모델·자산
  provenance, Quickstart와 한계를 연결한다.
- Secondary: `EVAL-R1-05` — 개인 프로젝트의 안정적·체계적 관리. 실제
  Issue/PR·CI·merge와 단계별 검증 기록만 evidence로 사용하고, deferred
  템플릿을 구현된 관리 체계로 주장하지 않는다.

이 spec은 점수 상승이나 제3자 review를 보장하지 않는다. 코드 완성도·혁신성·실제
커뮤니티 활동은 별도 실행·검증·외부 근거가 있어야 한다.

## 사용자 시나리오

### 시나리오 1 — 외부 개발자의 재사용 판단

1. 개발자가 README에서 PRIZM Engine과 Career Vault Reference App의 경계, 현재
   지원 범위, 미구현 기능과 환경 한계를 확인한다.
2. 개발자는 루트 라이선스와 NOTICE에서 자신의 사용·수정·재배포 조건을 확인한다.
3. 개발자는 OpenSQL, OpenProxy, OpenHA가 아직 실제 환경에서 검증되지 않았음을
   확인하고 PostgreSQL 성공을 호환성 보증으로 오해하지 않는다.

### 시나리오 2 — 대회 라이선스 검증

1. 검증자가 SBOM에서 라이브러리·프레임워크·컨테이너 기반 이미지·모델·fixture·
   자산의 버전, 출처, 라이선스, 사용 목적과 배포 범위를 찾는다.
2. `bge-m3`, Ollama와 직접 작성한 추론 코드의 라이선스 경계가 분리되어 있음을
   확인한다.
3. 미확인 또는 충돌 가능 항목은 숨기지 않고 `BLOCKED` 또는 후속 조치로 표시한다.

### 시나리오 3 — 외부 운영을 시작할 때의 안전한 기여와 취약점 제보

1. 외부 기여를 공식적으로 받기 전 CONTRIBUTING에서 개발·검증·문서 갱신·
   민감정보 금지 규칙을 확인할 수 있게 한다.
2. 외부 Issue·PR 운영을 열기 전 버그·기능·문서 제안 양식을 실제 흐름에 맞게
   검증한다.
3. 첫 지원 release 또는 외부 배포 전 공개 Issue에 JWT, 원본 문서, 로컬 경로
   또는 재현용 비밀정보를 올리지 않는 실제 비공개 보안 신고 경로를 확정한다.

## 요구사항

| ID | 요구사항 |
|---|---|
| `OR-001` | 공식 운영 규정, 공식 홈페이지, 결과보고서 양식, OT 보조 근거의 출처·발행일 또는 수집일·SHA-256·저작권/재배포 제한·PRIZM 적용 항목을 source register에 기록해야 한다. |
| `OR-002` | Java/Gradle, frontend npm, Docker/컨테이너, PostgreSQL·pgvector, Ollama와 `bge-m3`, 테스트 fixture·예제·이미지·문서 자산을 대상으로 버전·출처·라이선스·사용 목적·배포 여부·NOTICE 의무를 감사해야 한다. |
| `OR-003` | 감사 후 사용자가 승인한 OSI 인증 프로젝트 라이선스를 루트 `LICENSE`에 적용하고, 필요한 제3자 고지와 예외를 `NOTICE`와 라이선스 감사 문서에 일관되게 기록해야 한다. |
| `OR-004` | PRIZM의 직접 작성 코드 저작권자를 `Jaemin Jeong`으로 기록하고, Codex는 개발 보조도구 사용으로만 분리한다. AI 생성물이 특정 제3자 코드·자산을 복사하지 않았다는 보증으로 바꾸지 않으며, 외부 구성요소 감사로 검증한다. |
| `OR-005` | CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, SUPPORT 및 최소 maintainer 정책은 외부 기여 접수 또는 첫 지원 release·외부 배포 중 먼저 도래하는 시점에 재개한다. 구현할 때는 실제로 운영 가능한 연락·신고 경로만 사용하고, 개인 문서·JWT·비밀정보·로컬 경로를 공개 Issue에 올리지 않도록 안내해야 한다. |
| `OR-006` | Bug, feature, documentation Issue form과 PR template은 외부 Issue·PR 접수를 공식 지원하기 전에 재개한다. 문제·범위·테스트 환경·migration·ownership/security·dependency/license·문서·`NOT_RUN`을 구조화하며, 템플릿 자체를 review 증거로 주장하지 않는다. |
| `OR-007` | README와 문서 인덱스는 Quickstart, 현재/계획 기능, 검증 환경, 한계, 라이선스·SBOM 문서와 현재 운영 상태를 제공하고 구현되지 않은 OpenSQL/OpenProxy/OpenHA, CareerFact, portfolio, MCP를 구현된 것처럼 표현하지 않아야 한다. 기여·보안 문서 링크는 해당 문서와 실제 운영 경로가 마련된 뒤에만 추가한다. |
| `OR-008` | SBOM과 AI 모델 명세에 재사용할 수 있는 provenance 구조를 만들되, 실제 업로드 문서, credential, 모델 cache, 데이터베이스 volume, 사용자 로컬 경로를 저장소에 포함하지 않아야 한다. |

## 현재 범위 결정

- T-01~T-05의 공식 근거, 라이선스·provenance, Apache-2.0
  `LICENSE`·source-only `NOTICE`, SBOM·AI 모델 명세는 현재 P0 기준선이다.
- G-02와 T-06은 미구현 상태를 숨기지 않고 `DEFERRED`로 둔다. 외부 기여
  접수 또는 첫 지원 release·외부 배포를 준비하는 시점 중 먼저 도래하는 때
  재개한다. T-07은 외부 Issue·PR 접수를 공식 지원하기 전에 재개한다.
- 현재 P0의 활성 잔여 Gate는 T-08 README·Quickstart·문서 색인, T-09
  라이선스·SBOM 검증 CI, T-10 최종 독립 감사다.
- 이 범위 조정은 대회 공식 자료에서 직접 요구한 source 공개·라이선스·SBOM·
  AI 모델 명세를 우선하기 위한 것이며, 미구현 거버넌스를 완료로 간주하지 않는다.

## 보존 계약

- PRZ-000의 document/version/`active_version_id`, owner-scoped 검색, JWT DB 재검증,
  processing·cleanup lease와 fencing, Flyway V1~V13, PDF/TXT 계약을 변경하지 않는다.
- PostgreSQL 기반 테스트 성공을 OpenSQL·OpenProxy·OpenHA의 실제 호환성 증거로
  바꾸어 표현하지 않는다.
- 공개 문서에 실제 원본 경력 문서, JWT, 비밀번호, 전체 JDBC URL, 저장 경로, 모델
  파일 또는 빌드 산출물을 추가하지 않는다.

## 제외 범위

- OpenSQL 단일 환경 실행, OpenProxy runtime, OpenHA 장애전환 검증
- CareerFact, portfolio, MCP, `/api/v1`, 독립 Engine artifact 구현
- 검색 알고리즘·Reranker·Hybrid Search·embedding 모델 교체
- Flyway migration, production source/config, frontend 기능 변경
- Docusaurus 별도 문서 사이트, self-hosted CI matrix, 자기 자신만 지정하는
  CODEOWNERS, 인위적인 GitHub Issue/PR/review 생성

## 완료 조건

1. source register가 공식 원본과 OT 보조 근거의 권한·추적 한계를 분리한다.
2. 라이선스 감사가 모든 발견된 배포·실행 구성요소를 분류하고, 미확인·충돌 항목은
   해결 또는 명시적 `BLOCKED` 상태가 된다.
3. 루트 라이선스와 NOTICE가 감사 결과 및 사용자의 선택과 일치한다.
4. G-02·T-06·T-07이 실제 운영 경로와 함께 완료됐거나, 미구현 상태·재개 조건·
   현재 P0 비차단 근거가 `DEFERRED` 결정으로 일관되게 기록된다.
5. README와 docs index가 현재 구현·계획·미검증 환경을 정확히 연결한다.
6. 문서·라이선스 검사와 SBOM 생성 또는 검증이 CI 또는 재현 가능한 로컬 명령으로
   실행되며, 결과·환경·`NOT_RUN`이 evidence에 기록된다.
7. 독립 읽기 전용 감사에서 라이선스 충돌, 출처 누락, 민감정보 노출 또는 구현 과장에
   대한 CRITICAL/HIGH/MEDIUM finding이 없다.

## 다음 단계

현재는 T-08 README·Quickstart·문서 색인, T-09 라이선스·SBOM 검증 CI,
T-10 최종 독립 감사를 순서대로 진행한다. G-02·T-06·T-07은 위 재개 조건이
충족될 때 별도 IMPLEMENT로 돌아오며, GitHub Issue·PR과 외부 review는 실제로
발생한 경우에만 evidence로 기록한다.
