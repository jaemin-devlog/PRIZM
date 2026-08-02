# PRZ-002 검증 근거

## 최종 상태

| 항목 | 값 |
|---|---|
| Spec 상태 | 현재 source-only 범위 `VERIFIED` |
| 기준 source commit | `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1` |
| 기준 tree | `7e5f22fdbfbe1f4c87d8cd2c4fb579cba776e047` |
| 마지막 검증일 | 2026-07-30 |
| GitHub Issue | `NOT_CREATED` — 완료 작업을 설명하는 Issue를 소급 생성하지 않음 |
| Review | `REVIEW_NOT_AVAILABLE_SOLO` |

이 판정은 공개 Git 저장소와 source ZIP 범위다. JAR, frontend `dist`, container
image, Ollama binary, 모델 가중치와 OpenSQL 공급 자산의 재배포를 검증하지 않았다.

## 요구사항별 판정

| ID | 판정 | 근거 |
|---|---|---|
| `OR-001` | `PASS` | [SBOM 범위 manifest](../../sbom/prizm-scope-manifest.json)와 [NOTICE](../../NOTICE)가 저장소 포함물과 외부 준비물의 배포 경계를 구분 |
| `OR-002` | `PASS_SOURCE_ONLY` | machine-readable SBOM, package lock과 source-only Gate의 현재 배포 범위 blocker 0건 |
| `OR-003` | `PASS` | Apache-2.0 [LICENSE](../../LICENSE), [NOTICE](../../NOTICE)와 감사 판정 일치 |
| `OR-004` | `PASS` | 직접 작성 코드 저작권자와 Codex 보조도구 경계를 LICENSE·NOTICE·AI 명세에 분리 |
| `OR-005` | `DEFERRED` | 외부 기여 접수 또는 첫 지원 release·외부 배포 중 먼저 도래하는 시점에 재개 |
| `OR-006` | `DEFERRED` | 외부 Issue·PR 접수를 공식 지원하기 전에 재개 |
| `OR-007` | `PASS` | README·Quickstart·문서 색인이 구현·계획·환경 한계와 OpenSQL 검증 범위를 구분 |
| `OR-008` | `PASS` | Deterministic SBOM·AI 모델 manifest와 민감정보·모델 cache 제외 Gate 통과 |

## 실제 환경

| 범위 | 실제 사용 |
|---|---|
| 최종 source-only 감사 | Windows, Java/Gradle과 Node/npm 기반 `verify-oss-readiness` |
| 운영체제 독립성 | Linux clean clone에서 같은 OSS readiness 명령 실행 |
| GitHub | 공개 `main`과 GitHub Actions |
| T-08 clean clone | Docker, PostgreSQL 16·pgvector 사용; backend·frontend health 확인 |
| 전체 demo 사용자 흐름 | `NOT_RUN` — 안전한 demo `USER`와 host Ollama가 없었음 |
| Ollama·`bge-m3` | T-05·T-09의 모델 identity만 검증; 모델 실행은 `NOT_RUN` |
| OpenSQL·OpenProxy·OpenHA | PRZ-002 실행 환경에서는 `NOT_RUN` |

## 실행한 검증

| 검증 | 결과 | 기준 |
|---|---|---|
| `node scripts/verify-oss-readiness.mjs` | `PASS` — 필수 OSS 파일 9개, Markdown 38개·로컬 링크 264개, tracked file 300개 안전성, Node 회귀 12건 | `f54e3d9` 최종 교정 |
| License·NOTICE·source-only Gate | `PASS` — `UNKNOWN`·`CONFLICT`·`BLOCKED` 0건 | `f54e3d9` |
| Backend SBOM | `PASS` — 169 artifacts, 고유 `bom-ref`, strict dependency verification와 checksum drift 없음 | 최종 감사 |
| Frontend SBOM | `PASS` — 183 components, CycloneDX `SHA-512` 표기와 checksum drift 없음 | 최종 감사 |
| 공식 CycloneDX 1.6 schema | `PASS` — backend·frontend BOM과 SPDX·JSF 참조 | corrective 재검증 |
| Windows unit test | `PASS` — 245 tests, failure·error 0, 환경 조건 skip 14 | T-05 구현 검증 |
| PostgreSQL integration | `PASS` — 68 tests, failure·error 0, 환경 조건 skip 3 | T-05 구현 검증; OpenSQL 근거 아님 |
| Frontend lint·build | `PASS` | T-05 구현 검증 |
| `docker compose config` | `PASS` | T-05 구현 검증 |
| `git diff --check` | `PASS` | 최종 감사 |

### PRZ-002 검증 snapshot

다음 값은 기준 source `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`을 검증한
PRZ-002 당시의 역사적 snapshot이다. 현재 작업 트리의
[`sbom/SHA256SUMS`](../../sbom/SHA256SUMS)를 이 과거 값의 직접 근거로 사용하지
않는다.

| 산출물 | SHA-256 |
|---|---|
| Backend runtime | `5809282a3f3ac5fcf7eaa2f484513195f19e243a7d73a1282332114dbc569b7d` |
| Frontend | `af0dfc4891ec7adfcb282614edabc0791f2afdfd34a561337aae0c90d838285c` |
| AI model manifest | `9b4e4a805fffa38a9ea40567ede25ccaf0669970c0c1994e036148855fc2f728` |
| Scope manifest | `ce82411416d020102b78de861c10b79bcd39924e0bc1ec9ee8e1a0673a4f3b0e` |

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

| 작업 | 실제 기록 |
|---|---|
| T-05 초기 구현 | [PR #16](https://github.com/jaemin-devlog/PRIZM/pull/16), source `c28416e`, merge `68f2183` |
| T-05 corrective | [PR #18](https://github.com/jaemin-devlog/PRIZM/pull/18), source `203c892`, merge `04afe7c` |
| T-09 CI | [PR #22](https://github.com/jaemin-devlog/PRIZM/pull/22), source `5c31305`, merge `42876b6` |
| 최초 실패 CI | [run 30442330201](https://github.com/jaemin-devlog/PRIZM/actions/runs/30442330201) `FAIL` |
| Corrective OSS Readiness | [run 30443185952](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443185952) `PASS` |
| Corrective 기존 CI | [run 30443184506](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443184506) `PASS` |
| 병합된 `main` OSS Readiness | [run 30477035697](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035697) `PASS` |
| 병합된 `main` CI | [run 30477035700](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035700) `PASS` |

PR #22에 requested reviewer·comment·review가 없었다. 독립 agent 감사와 사용자
병합 승인은 GitHub review가 아니므로 `REVIEW_NOT_AVAILABLE_SOLO`로 기록한다.

## 남은 제한

- G-02·T-06은 외부 기여 접수 또는 첫 지원 release·외부 배포 전에 재개한다.
- T-07은 외부 Issue·PR 접수를 공식 지원하기 전에 재개한다.
- JAR, `dist`, image, Ollama binary와 모델 가중치 배포는 별도 감사가 필요하다.
- release 전에 source·SBOM snapshot과 checksum은 별도 Gate에서 고정해야 한다.
- OpenSQL·OpenProxy·OpenHA 검증은 이 Spec의 결과가 아니다.
- `bge-m3` 변환 lineage의 `UNVERIFIED_LINEAGE` 경계는 그대로 유지한다.
