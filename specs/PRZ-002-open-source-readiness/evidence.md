# PRZ-002 — 오픈소스 준비 Evidence

## 최종 상태

- **항목:** Spec 상태
  - 값: 현재 source-only 범위 `VERIFIED`
- **항목:** 기준 source commit
  - 값: `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`
- **항목:** 기준 tree
  - 값: `7e5f22fdbfbe1f4c87d8cd2c4fb579cba776e047`
- **항목:** 마지막 검증일
  - 값: 2026-07-30
- **항목:** GitHub Issue
  - 값: `NOT_CREATED` — 완료 작업을 설명하는 Issue를 소급 생성하지 않음
- **항목:** Review
  - 값: `REVIEW_NOT_AVAILABLE_SOLO`

이 판정은 공개 Git 저장소와 source ZIP 범위다. JAR, frontend `dist`, container
image, Ollama binary, 모델 가중치와 OpenSQL 공급 자산의 재배포를 검증하지 않았다.

## 판정 요약

공개 source, license·provenance, NOTICE, SBOM과 문서의 source-only 배포 경계를
검증했다. Ollama 모델 실행과 OpenSQL·OpenProxy·OpenHA runtime은 이 Evidence에서
`NOT_RUN`이다.

## 검증한 수직 흐름

```text
공개 파일·dependency·asset 목록 수집
↓
license·provenance·재배포 경계 검사
↓
LICENSE·NOTICE·SBOM·공개 문서 대조
↓
Windows·Linux clean clone·GitHub Actions 검증
```

## 요구사항별 판정

- **ID:** `OR-001`
  - 판정: `PASS`
  - 근거: [SBOM 범위 manifest](../../sbom/prizm-scope-manifest.json)와 [NOTICE](../../NOTICE)가 저장소 포함물과 외부 준비물의 배포 경계를 구분
- **ID:** `OR-002`
  - 판정: `PASS_SOURCE_ONLY`
  - 근거: machine-readable SBOM, package lock과 source-only Gate의 현재 배포 범위 blocker 0건
- **ID:** `OR-003`
  - 판정: `PASS`
  - 근거: Apache-2.0 [LICENSE](../../LICENSE), [NOTICE](../../NOTICE)와 감사 판정 일치
- **ID:** `OR-004`
  - 판정: `PASS`
  - 근거: 직접 작성 코드 저작권자와 Codex 보조도구 경계를 LICENSE·NOTICE·AI 명세에 분리
- **ID:** `OR-005`
  - 판정: `DEFERRED`
  - 근거: 외부 기여 접수 또는 첫 지원 release·외부 배포 중 먼저 도래하는 시점에 재개
- **ID:** `OR-006`
  - 판정: `DEFERRED`
  - 근거: 외부 Issue·PR 접수를 공식 지원하기 전에 재개
- **ID:** `OR-007`
  - 판정: `PASS`
  - 근거: README·Quickstart·문서 색인이 구현·계획·환경 한계와 OpenSQL 검증 범위를 구분
- **ID:** `OR-008`
  - 판정: `PASS`
  - 근거: Deterministic SBOM·AI 모델 manifest와 민감정보·모델 cache 제외 Gate 통과

## 실제 환경

- **범위:** 최종 source-only 감사
  - 실제 사용: Windows, Java/Gradle과 Node/npm 기반 `verify-oss-readiness`
- **범위:** 운영체제 독립성
  - 실제 사용: Linux clean clone에서 같은 OSS readiness 명령 실행
- **범위:** GitHub
  - 실제 사용: 공개 `main`과 GitHub Actions
- **범위:** T-08 clean clone
  - 실제 사용: Docker, PostgreSQL 16·pgvector 사용; backend·frontend health 확인
- **범위:** 전체 demo 사용자 흐름
  - 실제 사용: `NOT_RUN` — 안전한 demo `USER`와 host Ollama가 없었음
- **범위:** Ollama·`bge-m3`
  - 실제 사용: T-05·T-09의 모델 identity만 검증; 모델 실행은 `NOT_RUN`
- **범위:** OpenSQL·OpenProxy·OpenHA
  - 실제 사용: PRZ-002 실행 환경에서는 `NOT_RUN`

## 실행한 검증

- **검증:** `node scripts/verify-oss-readiness.mjs`
  - 결과: `PASS` — 필수 OSS 파일 9개, Markdown 38개·로컬 링크 264개, tracked file 300개 안전성, Node 회귀 12건
  - 기준: `f54e3d9` 최종 교정
- **검증:** License·NOTICE·source-only Gate
  - 결과: `PASS` — `UNKNOWN`·`CONFLICT`·`BLOCKED` 0건
  - 기준: `f54e3d9`
- **검증:** Backend SBOM
  - 결과: `PASS` — 169 artifacts, 고유 `bom-ref`, strict dependency verification와 checksum drift 없음
  - 기준: 최종 감사
- **검증:** Frontend SBOM
  - 결과: `PASS` — 183 components, CycloneDX `SHA-512` 표기와 checksum drift 없음
  - 기준: 최종 감사
- **검증:** 공식 CycloneDX 1.6 schema
  - 결과: `PASS` — backend·frontend BOM과 SPDX·JSF 참조
  - 기준: corrective 재검증
- **검증:** Windows unit test
  - 결과: `PASS` — 245 tests, failure·error 0, 환경 조건 skip 14
  - 기준: T-05 구현 검증
- **검증:** PostgreSQL integration
  - 결과: `PASS` — 68 tests, failure·error 0, 환경 조건 skip 3
  - 기준: T-05 구현 검증; OpenSQL 근거 아님
- **검증:** Frontend lint·build
  - 결과: `PASS`
  - 기준: T-05 구현 검증
- **검증:** `docker compose config`
  - 결과: `PASS`
  - 기준: T-05 구현 검증
- **검증:** `git diff --check`
  - 결과: `PASS`
  - 기준: 최종 감사

### PRZ-002 검증 snapshot

다음 값은 기준 source `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`을 검증한
PRZ-002 당시의 역사적 snapshot이다. 현재 작업 트리의
[`sbom/SHA256SUMS`](../../sbom/SHA256SUMS)를 이 과거 값의 직접 근거로 사용하지
않는다.

- **산출물:** Backend runtime
  - SHA-256: `5809282a3f3ac5fcf7eaa2f484513195f19e243a7d73a1282332114dbc569b7d`
- **산출물:** Frontend
  - SHA-256: `af0dfc4891ec7adfcb282614edabc0791f2afdfd34a561337aae0c90d838285c`
- **산출물:** AI model manifest
  - SHA-256: `9b4e4a805fffa38a9ea40567ede25ccaf0669970c0c1994e036148855fc2f728`
- **산출물:** Scope manifest
  - SHA-256: `ce82411416d020102b78de861c10b79bcd39924e0bc1ec9ee8e1a0673a4f3b0e`

### 현재 machine-readable 원본

현재 작업 트리 checksum의 단일 원본은
[`sbom/SHA256SUMS`](../../sbom/SHA256SUMS)다. PRZ-004 구현 commit의 frontend
SBOM SHA-256은
`cd1ed67bffefdaf4618bf9452d193f52c69aa37c646014b1daaf6354609c254a`이며,
[`verify-sbom.mjs`](../../scripts/verify-sbom.mjs)가 현재 파일과 checksum의
일치를 검증한다. PRZ-002 역사값을 현재 값으로 덮어쓰지 않는다.

## 주요 검증 이력

- 초기 공개 준비 작업에서 외부 design token을 제거하고 dependency·CI·모델
  identity를 고정했다. 실제 변경은
  [PR #13](https://github.com/jaemin-devlog/PRIZM/pull/13)과 이후 PR에 보존돼 있다.
- 최초 clean-checkout 검증은 Windows 줄바꿈, 중복 `bom-ref`와 비표준
  `SHA512` 표기 때문에 `FAIL`했다. Generator·verifier 교정 뒤 clean clone,
  schema와 checksum 재검증이 통과했다.
- 최초 GitHub Actions는 검증기 자체의 token 정규식 접두사를 credential로
  오탐했다. 실제 credential 노출은 없었고 교정 후 OSS Readiness와 기존 CI가
  모두 통과했다.
- Windows unit·PostgreSQL integration과 Linux clean-clone 검증을 통과했다.
  demo `USER` 전체 흐름과 실제 모델 실행은 계속 `NOT_RUN`이다.

날짜별 상세 과정은 아래 실제 PR·CI와 Git history에서 확인한다.

## GitHub 통합과 review

- **작업:** T-05 초기 구현
  - 실제 기록: [PR #16](https://github.com/jaemin-devlog/PRIZM/pull/16), source `c28416e`, merge `68f2183`
- **작업:** T-05 corrective
  - 실제 기록: [PR #18](https://github.com/jaemin-devlog/PRIZM/pull/18), source `203c892`, merge `04afe7c`
- **작업:** T-09 CI
  - 실제 기록: [PR #22](https://github.com/jaemin-devlog/PRIZM/pull/22), source `5c31305`, merge `42876b6`
- **작업:** 최초 실패 CI
  - 실제 기록: [run 30442330201](https://github.com/jaemin-devlog/PRIZM/actions/runs/30442330201) `FAIL`
- **작업:** Corrective OSS Readiness
  - 실제 기록: [run 30443185952](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443185952) `PASS`
- **작업:** Corrective 기존 CI
  - 실제 기록: [run 30443184506](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443184506) `PASS`
- **작업:** 병합된 `main` OSS Readiness
  - 실제 기록: [run 30477035697](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035697) `PASS`
- **작업:** 병합된 `main` CI
  - 실제 기록: [run 30477035700](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035700) `PASS`

PR #22에 requested reviewer·comment·review가 없었다. 독립 agent 감사와 사용자
병합 승인은 GitHub review가 아니므로 `REVIEW_NOT_AVAILABLE_SOLO`로 기록한다.

## 남은 제한

- G-02·T-06은 외부 기여 접수 또는 첫 지원 release·외부 배포 전에 재개한다.
- T-07은 외부 Issue·PR 접수를 공식 지원하기 전에 재개한다.
- JAR, `dist`, image, Ollama binary와 모델 가중치 배포는 별도 감사가 필요하다.
- release 전에 source·SBOM snapshot과 checksum은 별도 Gate에서 고정해야 한다.
- OpenSQL·OpenProxy·OpenHA 검증은 이 Spec의 결과가 아니다.
- `bge-m3` 변환 lineage의 `UNVERIFIED_LINEAGE` 경계는 그대로 유지한다.

## 2026-08-30 첫 소스 릴리스 Gate 재개

PRZ-024에서 첫 외부 소스 릴리스 준비를 시작해 `OR-005`·`OR-006`의 재개 조건이
충족됐다. GitHub Private Vulnerability Reporting은 API 조회 기준 `enabled=true`로
활성화했다. SECURITY·SUPPORT·유지관리 정책과 Issue/PR template은 PRZ-024
릴리스 준비 브랜치에서 구현 중이며, 기본 브랜치 통합과 실제 Release 검증 전까지
이 문서의 기존 `DEFERRED` 판정을 소급해 `PASS`로 바꾸지 않는다.
