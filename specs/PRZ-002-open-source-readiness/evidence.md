# PRZ-002 evidence

| 항목 | 값 |
|---|---|
| Spec | [PRZ-002](spec.md) |
| Evidence status | PRZ-002 현재 source-only 범위 `VERIFIED` — T-10 최종 독립 감사 PASS |
| Implementation commit | `c28416e` — `추가: source-only SBOM과 AI 모델 명세` |
| Corrective implementation commit | `8dd57c4897ff746db41df489201cacfc82c99f1b` — `수정: SBOM 생성기 정합성 보완` |
| Implementation branch | `PRZ-002-sbom-model-manifest` — PR #16 병합 후 삭제 |
| Baseline | `main` / `origin/main` `0ad549a8641b2b6ef18a8011dac93286052b65c0` |
| Integrated PR | [#16](https://github.com/jaemin-devlog/PRIZM/pull/16), merge commit `68f2183` |
| Final VERIFY baseline | `main` / `origin/main` `b36f6b236c2f70d26e243013df296b4dad1a54d9` |
| Final VERIFY branch | local-only `PRZ-002-sbom-final-verification` — 검증 후 삭제 |
| Corrective IMPLEMENT branch | `PRZ-002-sbom-conformance-fix` — PR #18 병합 후 삭제 |
| Corrective integrated PR | [#18](https://github.com/jaemin-devlog/PRIZM/pull/18), source commit `203c892`, merge commit `04afe7c` |
| T-09 integrated PR | [#22](https://github.com/jaemin-devlog/PRIZM/pull/22), source commit `5c31305`, merge commit `42876b6` |
| Initial public audit baseline | `main` `777e184f206d2a2770d055940ddabf139abfed9d`, tree `a6e9cb96cb4dc93d657397241d99d4367c7d2902`, 2026-07-30 |
| Final correction source | commit `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`, tree `7e5f22fdbfbe1f4c87d8cd2c4fb579cba776e047` |
| GitHub Issue | `NOT_CREATED`; connector write was blocked with HTTP 403 on 2026-07-26 |
| Primary / secondary evaluation IDs | `EVAL-R1-02` / `EVAL-R1-03`, `EVAL-R1-05` |

The early sections preserve the T-05 SBOM and AI-model-manifest implementation
history. The final sections close PRZ-002's current source-only scope; they do
not claim redistribution of model, container, or OpenSQL vendor artifacts.

## Generated records

| Record | Result at `c28416e` |
|---|---|
| `sbom/prizm-backend-runtime.cdx.json` | CycloneDX 1.6, `prizm`, 169 runtime components, no timestamp or serial number |
| `sbom/prizm-frontend.cdx.json` | CycloneDX 1.6, `prizm-frontend`, 183 versioned lockfile components, no timestamp or serial number |
| `sbom/prizm-ai-model-manifest.json` | source-only model boundary, Ollama v0.32.3, BAAI `bge-m3` revision, registry hashes, `UNVERIFIED_LINEAGE` preserved |
| `sbom/prizm-scope-manifest.json` | backend runtime, test/build, frontend, CI, container, model, fixture/asset scope split |
| `sbom/SHA256SUMS` | backend `1c6c91fb990c4bf130ca07794789e65bdd7524e145ba7f6397bfeefce2e0447b`; frontend `c7c1651f8da873c68c352cef71cc8c4964be3fea56aa3b815f54e2d5bbc459b5`; AI model `9b4e4a805fffa38a9ea40567ede25ccaf0669970c0c1994e036148855fc2f728`; scope `d7358b6414f945dab76ef63920500a867ce14a229f24715a0ccd909600f007e7` |

The local `scripts/verify-sbom.mjs` structural gate passed after regeneration.
It checks JSON parsing, CycloneDX format and primary component identity, component
presence, deterministic-field policy, checksum drift, and prohibited local
path/JDBC URL/credential-shaped fields. A formal full-schema CI gate remains
T-09 work and is not claimed here.

## Commands and results

| Command | Result |
|---|---|
| `./gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict` | PASS; first-party Gradle runtime SBOM generation |
| `npm --prefix frontend run sbom` | PASS; first-party lockfile SBOM generation from package-lock SHA-256 `967063c8b12574a1467d492ad5fec7c6e080e89a6250f153e49ed1f1714fb66c` |
| `node scripts/verify-sbom.mjs --write-checksums` then `node scripts/verify-sbom.mjs` | PASS; deterministic checksum update then no-drift verification |
| `./gradlew.bat test --no-daemon --dependency-verification=strict` | PASS; 245 tests, 0 failures, 0 errors, 14 environment-condition skips |
| `./gradlew.bat integrationTest --no-daemon --rerun-tasks --dependency-verification=strict` | PASS; 68 tests, 0 failures, 0 errors, 3 environment-condition skips |
| `npm --prefix frontend run lint` | PASS |
| `npm --prefix frontend run build` | PASS |
| `docker compose config` | PASS |
| Markdown local-link/code-fence/trailing-whitespace scan and `git diff --check` | PASS before the implementation commit |

The Gradle test and integration task graphs no longer include a third-party
SBOM plugin task. Gradle still reports its existing Gradle 10 deprecation
warning and existing deprecated Java API notes; neither is a test failure nor
a claim that those issues are resolved by this spec.

## Environment use

| Environment / service | Actual result |
|---|---|
| Java / Gradle | USED for generation and test commands with strict dependency verification |
| Node 22.17.0 / npm 10.9.2 | USED for frontend SBOM, lint, and build |
| Docker | USED by the integration suite and `docker compose config` |
| PostgreSQL 16 + pgvector | USED through Testcontainers integration tests |
| Ollama | `NOT_RUN`; SBOM/model manifest records identity only and does not pull a model |
| OpenSQL | `NOT_RUN` |
| OpenProxy | `NOT_RUN` |
| OpenHA | `NOT_RUN` |

## 2026-07-26 당시 남은 PRZ-002 Gate

- T-05의 사람용 감사와 Java/npm machine SBOM 대조, clean checkout 재생성,
  공식 schema 검사와 독립 읽기 전용 감사는 완료됐다.
- 당시 formal schema·구조·재생성 drift를 자동화하는 CI는 T-09 후속이었다.
- G-02·T-06은 실제 외부 기여 운영 또는 첫 지원 release·외부 배포 전까지,
  T-07은 외부 Issue·PR 접수를 공식 지원하기 전까지 `DEFERRED`다. 비공개
  신고 채널이 활성화되거나 운영 가능한 연락처가 정해지기 전에는
  `SECURITY.md`를 만들지 않는다.
- 당시 활성 잔여 Gate는 T-08 README·Quickstart·문서 색인, T-09
  라이선스·SBOM 검증 CI와 T-10 최종 독립 감사다.
- 2026-07-26 검증 시점에는 branch push만 있었고 실제 GitHub Issue, PR,
  review, merge evidence는 아직 없었다. 이후 PR #16과 merge commit
  `68f2183`, PR #18과 merge commit `04afe7c`가 생성됐지만 GitHub Issue와
  제3자 review는 여전히 없다.

## External GitHub gate — 2026-07-26

The branch was published to `origin/PRZ-002-sbom-model-manifest`, but this
environment could not create the required real Issue or PR evidence at that
time: the GitHub connector Issue-create request returned HTTP 403
(`Resource not accessible by integration`), and `gh auth status` reported no
authenticated GitHub host. The later PR #16 and merge are recorded separately;
the earlier remote push itself is not treated as an Issue, review, or merge
record.
Private Vulnerability Reporting was not inspected or enabled from this
environment. G-02 was later changed to `DEFERRED` for the current source-only
P0 scope; it must be reopened before the first supported release, external
deployment, or officially supported external-contribution intake.

## P0 governance deferral decision — 2026-07-28

- **결정:** G-02, T-06, T-07을 완료로 표시하지 않고 `DEFERRED`로 둔다.
- **이유:** 현재 공식 제출 근거에서 직접 요구되는 source 공개, OSI license,
  component·model provenance, SBOM·AI 모델 명세를 먼저 마감한다. 실제
  외부 운영이 없는 상태에서 가짜 연락처·형식적인 템플릿을 만드는 것은
  운영 가능성이나 review evidence를 증명하지 못한다.
- **재개 조건:** G-02·T-06은 외부 기여 접수 또는 첫 지원 release·외부 배포를
  준비하는 시점 중 먼저 도래하는 때, T-07은 외부 Issue·PR 접수를 공식
  지원하기 전이다.
- **재개 Gate:** 실제 Private Vulnerability Reporting 또는 검증 가능한
  비공개 연락 경로를 먼저 확정하고, 그 뒤 운영 문서와 템플릿을 구현·검증한다.
- **당시 활성 Gate:** T-08 README·Quickstart·문서 색인, T-09
  라이선스·SBOM 검증 CI, T-10 최종 독립 감사.
- **환경:** 문서 범위 결정이므로 Docker, PostgreSQL, pgvector, Ollama,
  OpenSQL, OpenProxy, OpenHA는 모두 `NOT_RUN`이다.

## 공개 저장소 거버넌스 범위 조정 독립 AUDIT — 2026-07-28

- **대상:** `origin/main` commit `be39aa5`를 기준으로 한 문서 7개의 working
  tree diff. 감사자는 파일·staging·Git history를 변경하지 않았다.
- **판정:** `PASS`. 두 차례의 LOW 문구 정합성 보완 뒤
  CRITICAL/HIGH/MEDIUM/LOW finding은 0건이다.
- **확인:** G-02·T-06은 외부 기여 접수 또는 첫 지원 release·외부 배포 전,
  T-07은 외부 Issue·PR 접수 공식 지원 전 재개하는 것으로 문서가 일치한다.
  당시 PRZ-002는 `IN_PROGRESS`, OpenSQL·OpenProxy·OpenHA는 `NOT_RUN`으로
  유지하며 활성 잔여 Gate는 T-08·T-09·T-10이었다.
- **검증:** 변경 Markdown 7개, 로컬 링크 누락 0, code fence 불균형 0,
  trailing whitespace 0, `git diff --check` 통과, application source·migration·
  config 변경 0.
- **미실행:** 문서 전용 범위 조정이므로 application unit·integration test와
  frontend lint·build는 재실행하지 않았다.
- **review 경계:** 이 Agent audit는 GitHub review가 아니다. 실제 PR을
  reviewer 없이 병합하려면 그 전에 사용자 승인을 받고
  `REVIEW_NOT_AVAILABLE_SOLO`를 별도로 기록해야 한다.

## Read-only audit — 2026-07-26

**Scope:** `main...232915e` on `PRZ-002-sbom-model-manifest`; implementation
commits `c28416e` and `232915e`.

| Audit check | Result |
|---|---|
| Changed-file scope | PASS — build task, frontend script entry, SBOM/manifests, verifier, PRZ-002/license documentation only; no Java application source, Flyway migration, production config, Docker Compose, or frontend feature changes |
| Source-only boundary | PASS — no model/cache/upload/DB volume/generated build output tracked; all model bytes remain `NOT_DISTRIBUTED` |
| Sensitive data | PASS — SBOM/manifests contain no local path, JDBC URL, or credential-shaped field |
| Reproducibility | PASS — regenerate then checksum verification passed; deterministic timestamp/serial policy held |
| Machine/source reconciliation | PASS within implemented scope — frontend lockfile count `183` equals SBOM count `183`; all 169 backend components have Maven PURLs and SHA-256 artifact hashes |
| Documentation honesty | PASS — `UNVERIFIED_LINEAGE`, source-only boundary, and OpenSQL/OpenProxy/OpenHA `NOT_RUN` are preserved |
| Diff hygiene | PASS — `git diff --check main...HEAD`, local Markdown-link/code-fence/trailing-whitespace checks, and `git show --check` for both implementation commits passed |

**Findings:** no CRITICAL, HIGH, or MEDIUM finding in the reviewed implementation
scope. Formal full-schema validation/CI and the broader human license-audit
reconciliation are explicit unimplemented T-09/T-05 follow-up gates, not
evidence of completion. This audit is an agent read-only review, not a GitHub
review.

**Audit conclusion:** `PASS_FOR_IMPLEMENTED_SCOPE`; T-05 remains
`IMPLEMENTED_UNVERIFIED` until its remaining reconciliation gate is complete.

## 최종 VERIFY — 2026-07-27

**대상:** 병합된 `main`·`origin/main`
`b36f6b236c2f70d26e243013df296b4dad1a54d9`. Git archive로 만든 깨끗한
HEAD 복제본과 로컬 작업 트리 양쪽을 사용했다. 기존 2026-07-26 감사는
formal schema와 사람용 license audit 대조를 범위 밖 후속 Gate로 남긴
역사적 결과이며, 아래 최종 VERIFY가 현재 판정을 대체한다.

| 검증 | 결과 |
|---|---|
| 깨끗한 HEAD에서 `node scripts/verify-sbom.mjs` | **FAIL** — tracked backend SBOM SHA-256은 `42bc9674f04a54c45166ce5e5f0bec6983619e9621f21375524dfb1ee2aecbfb`이나 `SHA256SUMS`는 `cd94a260e010a104ae2d994426630585baa6c183b3fd4bb51cfc9f8cdfe88a36`을 기대 |
| JDK 17.0.12에서 `gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict` | PASS — 169개 component 재생성, 생성 직후 hash `cd94a260…a36` |
| Node 22.17.0·npm 10.9.2에서 `npm --prefix frontend run sbom` | PASS — package-lock SHA-256 `967063c8…b66c`, 183개 component |
| 재생성 직후 `node scripts/verify-sbom.mjs` | PASS — 기존 structural·scope·sensitive-data·checksum 검사 |
| 공식 CycloneDX 1.6 JSON Schema 검증 | backend PASS, frontend **FAIL** — 183개 component 모두 hash algorithm `SHA512`를 사용하지만 공식 enum은 `SHA-512` |
| `bom-ref` 고유성 검사 | **FAIL** — `pkg:maven/io.netty/netty-codec-native-quic@4.2.15.Final`이 Linux x86_64·aarch64, macOS x86_64·aarch64, Windows x86_64 artifact 5개에 중복 |
| 사람용·machine frontend 대조 | PASS — 183개, 누락 license 0; MIT 135, Apache-2.0 15, MPL-2.0 12, ISC 10, BSD-2-Clause 6, BSD-3-Clause 2, BlueOak-1.0.0 1, CC-BY-4.0 1, 0BSD 1 |
| 사람용·machine backend 대조 | **FAIL** — 사람용 runtime exact set 167개, machine artifact 169개, 고유 `bom-ref` 165개로 동일 집합임을 증명하지 못함 |
| 민감정보·배포 경계 | PASS — 네 SBOM/manifest에 local user path, JDBC URL, password·authorization·access/refresh token 형태의 값 없음; model weight·cache는 배포하지 않는 경계 유지 |

backend 파일의 tracked LF 내용과 Windows JDK 17 재생성 CRLF 내용은 줄바꿈을
정규화하면 동일했다. 생성기가 `System.lineSeparator()`를 사용하고 Git은
JSON을 LF로 저장하기 때문에 생성 직후 작성한 checksum이 clean checkout
bytes와 달라진다. 단순히 `SHA256SUMS`만 다시 쓰면 운영체제에 따라 문제가
반복되므로 generator의 고정 LF 출력과 checksum 대상 bytes를 함께 바로잡아야
한다.

공식 schema는 검증 시점에
[`https://cyclonedx.org/schema/bom-1.6.schema.json`](https://cyclonedx.org/schema/bom-1.6.schema.json)에서
임시로 받아 사용했으며 SHA-256은
`1EBCB88A2C845ECB6FF7BEE7AEABDFF9422CB0347F3D6875B241BD444B7E098F`였다.
schema 파일은 저장소에 추가하지 않았다. CycloneDX의 `bom-ref`는 BOM 내부에서
고유해야 하므로 classifier를 PURL qualifier 또는 동등한 고유 identity에
반영해야 한다.

이번 VERIFY에서는 Java source, Flyway migration, frontend 기능, production
config와 Docker Compose를 변경하지 않았다. 애플리케이션 동작을 바꾸지 않은
SBOM 산출물 검증이므로 unit·integration·frontend lint/build는 재실행하지
않았다. Docker, PostgreSQL, pgvector, Ollama, OpenSQL, OpenProxy, OpenHA도
사용하지 않았으며 모두 이번 검증에서 `NOT_RUN`이다.

**최종 판정:** `FAIL`. T-05를 `VERIFY_COMPLETE`로 올리지 않고
`IMPLEMENT`로 되돌린다. 수정 대상은 backend 고정 LF·classifier identity,
frontend `SHA-512`, clean-checkout checksum 검증과 이 세 조건을 놓치지 않는
verifier test다. 수정 후 같은 명령과 공식 schema 검증을 다시 실행해야 한다.

## 결함 보완 IMPLEMENT — 2026-07-27

이 절은 위 최종 VERIFY 실패를 삭제하거나 PASS로 바꾸지 않는다. 지적된 세
생성기·검증기 결함을 보완한 구현 기록이며, 독립 VERIFY·AUDIT 전 상태는
`IMPLEMENTED_UNVERIFIED`다.

| 보완 항목 | 구현 및 집중 검사 결과 |
|---|---|
| backend 줄바꿈 | `generateBackendSbom`이 운영체제와 무관하게 LF로 끝나는 JSON을 생성한다. 재생성 파일의 CRLF 수 0, 마지막 byte LF |
| backend artifact identity | Netty native classifier 5개를 Maven PURL `classifier` qualifier와 property로 구분한다. backend component 169개와 고유 `bom-ref` 169개 |
| frontend hash enum | npm SRI `sha512`를 CycloneDX 1.6 표준 `SHA-512`로 변환한다. frontend 183개 hash algorithm은 모두 `SHA-512` |
| verifier | 모든 `bom-ref`의 전역 고유성과 CycloneDX hash algorithm enum을 검사한다 |
| regression test | `node --test scripts/verify-sbom.test.mjs`: 4개 통과, 실패·skip 0 |
| local checksum | backend `5809282a3f3ac5fcf7eaa2f484513195f19e243a7d73a1282332114dbc569b7d`; frontend `af0dfc4891ec7adfcb282614edabc0791f2afdfd34a561337aae0c90d838285c`; verifier 통과 |
| human/machine 조정 | Java module 167개에서 metadata-only platform/BOM 2개를 제외하고 Netty 한 module의 classifier JAR 5개를 펼쳐 machine artifact 169개가 됨을 기록 |

로컬 집중 검사에서는 Gradle backend 강제 재생성, frontend 재생성, checksum
갱신·무변경 검증과 회귀 테스트를 실행했다. Java 애플리케이션 source, Flyway
migration, frontend 기능, production config, Docker Compose는 변경하지 않았다.
애플리케이션 동작 변경이 없는 IMPLEMENT 보완이므로 전체 unit·integration·lint·
build는 이 단계에서 다시 실행하지 않았다. Docker, PostgreSQL, pgvector,
Ollama, OpenSQL, OpenProxy, OpenHA는 모두 `NOT_RUN`이다.

이 IMPLEMENT 시점에는 깨끗한 checkout 재생성, 공식 CycloneDX 1.6 schema,
checksum·human/machine 대조를 다시 실행해야 했다. 그 후 수행한 결과는 아래
최종 재VERIFY에 기록한다.

## 최종 재VERIFY — 2026-07-27

**대상:** corrective implementation commit
`8dd57c4897ff746db41df489201cacfc82c99f1b`. 현재 작업 트리를 재사용하지 않고
Windows 임시 폴더에 `--no-hardlinks` local clone을 만든 뒤 detached HEAD로
checkout했다.

| 검증 | 결과 |
|---|---|
| 재생성 전 `node scripts/verify-sbom.mjs` | PASS — committed JSON과 `SHA256SUMS` 일치 |
| `node --test scripts/verify-sbom.test.mjs` | PASS — 4 tests, 실패·skip 0; 비표준 hash와 중복 `bom-ref` 거부 포함 |
| JDK 17 `generateBackendSbom --rerun-tasks --no-daemon --dependency-verification=strict` | PASS — 169 artifacts |
| Node 22.17.0 `npm --prefix frontend run sbom` | PASS — package-lock SHA-256 `967063c8…b66c`, 183 components |
| 재생성 후 verifier·Git 상태 | PASS — checksum 일치, tracked/untracked 변경 0건 |
| backend 줄바꿈 | PASS — CRLF 0개, 마지막 byte LF |
| backend reference | PASS — 169 components, 고유 `bom-ref` 169개; Netty 5개 classifier PURL 분리 |
| frontend hash | PASS — 183 components의 algorithm이 모두 `SHA-512` |
| 공식 CycloneDX 1.6 schema | PASS — backend·frontend 모두 BOM/SPDX/JSF schema validation 통과 |
| human/machine 조정 | PASS — 167 module identity - metadata-only 2 + Netty 추가 classifier 4 = 169 artifact; base Maven identity 165개 |

공식 schema는 검증 시점에 `cyclonedx.org`에서 임시 폴더로만 내려받았고
저장소에는 추가하지 않았다.

| 공식 schema | SHA-256 |
|---|---|
| `bom-1.6.schema.json` | `1ebcb88a2c845ecb6ff7bee7aeabdff9422cb0347f3d6875b241bd444b7e098f` |
| `spdx.schema.json` | `c87aa7bb5eb503d40b52ec6bf00de8045df15da7a13cea48d290cf6d36a8d2ea` |
| `jsf-0.82.schema.json` | `2faf5eb3651f2ae5f46091a131770d8d847bbd121139d19c85fc7051bfa58c46` |

schema validation은 기존 frontend lockfile의 Ajv 6.15.0(MIT)을 사용했다.
Ajv 6이 기본 제공하지 않는 `iri-reference`·`idn-email` format annotation은
무시했으며, JSON Schema 구조·필수 필드·enum·pattern·SPDX `$ref` 검증은
적용됐다. URL·민감정보·checksum·`bom-ref`는 repository verifier가 별도로
검사했다.

재생성된 JSON SHA-256은 다음과 같다.

- backend: `5809282a3f3ac5fcf7eaa2f484513195f19e243a7d73a1282332114dbc569b7d`
- frontend: `af0dfc4891ec7adfcb282614edabc0791f2afdfd34a561337aae0c90d838285c`
- AI model: `9b4e4a805fffa38a9ea40567ede25ccaf0669970c0c1994e036148855fc2f728`
- scope: `d7358b6414f945dab76ef63920500a867ce14a229f24715a0ccd909600f007e7`

애플리케이션 동작을 변경하지 않은 SBOM conformance 재검증이므로 전체 unit,
integration, frontend lint/build는 재실행하지 않았다. Docker, PostgreSQL,
pgvector, Ollama, OpenSQL, OpenProxy, OpenHA는 모두 이번 VERIFY에서
`NOT_RUN`이다.

**최종 판정:** `PASS`. 당시 T-05는 `VERIFY_COMPLETE_AUDIT_PENDING`이었고,
다음 단계는 수정자와 분리된 관점의 독립 읽기 전용 AUDIT였다. 이 결과는
PRZ-002 전체 완료, T-09 CI 완료 또는 OpenSQL·OpenProxy·OpenHA 검증을 의미하지
않는다.

## 독립 읽기 전용 AUDIT — 2026-07-28

**대상:** corrective implementation commit
`8dd57c4897ff746db41df489201cacfc82c99f1b`와 위 최종 재VERIFY evidence.
감사자는 파일을 수정하지 않고 source-only 배포 경계, 생성기·검증기, 생성 JSON,
checksum, schema 검증 기록, human/machine 조정, 민감정보 노출 여부를 대조했다.

| 항목 | 결과 |
|---|---|
| CRITICAL/HIGH/MEDIUM | 없음 |
| CycloneDX 1.6·SPDX·JSF schema와 checksum 기록 | PASS |
| clean-checkout 재생성과 human/machine 조정 기록 | PASS |
| source-only 경계와 OpenSQL 미검증 표현 | PASS |
| LOW 후속 보완 | 문서의 과거 gate 현행화, generated JSON의 LF·마지막 LF 회귀 검증 추가 |

감사 당시의 LOW 두 건은 follow-up source commit `203c892`에서 수정했고
PR #18, merge commit `04afe7c`로 `main`에 병합됐다. 독립 AUDIT는 T-05의
현재 source-only 범위를 `VERIFIED`로 판정했지만, GitHub review가 아니며
T-09 CI, 제출 직전 snapshot, PRZ-002의 나머지 작업은 아직 완료되지 않았다.

## AUDIT 후속 보완 VERIFY — 2026-07-28

LOW 후속 보완은 generated JSON의 LF·마지막 LF 검증과 문서 gate 현행화로만
한정했다. Java 17 환경에서 backend SBOM을 강제 재생성하고, Node 22.17.0/npm
10.9.2 환경에서 frontend SBOM과 verifier를 다시 실행했다. 생성 JSON과
`SHA256SUMS`에는 변경이 없었다.

| 검증 | 결과 |
|---|---|
| JDK 17 `generateBackendSbom --rerun-tasks --no-daemon --dependency-verification=strict` | PASS |
| `npm --prefix frontend run sbom` | PASS — 183 components |
| `node scripts/verify-sbom.mjs` | PASS — CRLF와 마지막 LF 누락을 fail-closed로 검사 |
| `node --test scripts/verify-sbom.test.mjs` | PASS — 5 tests, 실패·skip 0; CRLF·마지막 LF regression 포함 |
| 재생성 뒤 Git 변경 | PASS — generated JSON과 `SHA256SUMS` 변경 0건 |

애플리케이션 동작을 변경하지 않은 generator/verifier·문서 보완이므로 전체 unit,
integration, frontend lint/build는 재실행하지 않았다. Docker, PostgreSQL,
pgvector, Ollama, OpenSQL, OpenProxy, OpenHA는 모두 `NOT_RUN`이다.

## T-08 README·Quickstart·docs index VERIFY — 2026-07-29

**대상:** 기준 commit `9b6352e75d1b56191322147a3953d5624739f58b`와
현재 T-08 문서 patch. 원 작업 트리의 dependency·build cache를 재사용하지 않도록
`build/verification/t08-clean-clone`에 `--no-hardlinks` local clone을 만들고 문서
patch를 적용했다. 원본과 clone의 변경 문서 7개는 줄바꿈을 정규화한 내용이 모두
일치했다.

| 검증 | 결과 |
|---|---|
| `docker compose config --quiet` | PASS |
| 격리된 project·port에서 `docker compose up -d --build` | PASS — PostgreSQL 16+pgvector, backend, frontend 기동 |
| Flyway | PASS — 빈 `public` schema에 V1~V13 13개 적용 |
| backend health | PASS — HTTP 200, `status=UP` |
| frontend | PASS — HTTP 200, React root 문서 확인 |
| 초기 사용자 | PASS — `users` 0건으로 안전한 demo `USER`가 자동 생성되지 않는 현재 제한 재현 |
| 전체 사용자 흐름 | `NOT_RUN` — 회원가입·demo `USER` 경로와 host Ollama `bge-m3`가 없음 |
| 저장소 전체 Markdown | PASS — 38개 파일, 로컬 링크 258개, 누락 0개, code fence 불균형 0개, trailing whitespace 0개 |
| `git diff --check` | PASS |
| 현재·계획·미검증 표현 | PASS — 구현 endpoint·권한·frontend route와 대조; CareerFact·portfolio·MCP·`/api/v1`·멀티모듈은 계획, OpenSQL·OpenProxy·OpenHA는 `NOT_RUN` 유지 |

첫 Compose 호출이 tool timeout 뒤에도 계속 실행되는 동안 같은 project에 대한
재호출이 겹쳐 두 번째 호출은 기존 network·container 이름 충돌을 반환했다. 최초
호출은 정상 완료됐고 최종 container 상태와 health를 별도로 확인했다. 이 중복
호출 오류는 제품 성공 근거로 사용하지 않았다.

Docker, PostgreSQL 16, pgvector는 실제 사용했다. Ollama 실행 파일과
`bge-m3`는 이 환경에 없어 `NOT_RUN`이며 OpenSQL, OpenProxy, OpenHA도
`NOT_RUN`이다. clean-clone Docker build가 backend `bootJar`와 frontend
production build를 수행했지만, 애플리케이션 source·migration·config 변경이 없는
문서 작업이므로 전체 unit·integration·frontend lint test는 재실행하지 않았다.

**VERIFY 판정:** 문서가 약속한 Compose 기동·health 범위는 `PASS`다. 신규
사용자의 로그인→업로드→ACTIVE→검색 전체 demo는 계속 `NOT_RUN`이며 T-08의
다음 단계는 최종 diff에 대한 독립 읽기 전용 AUDIT다.

## T-09 OSS readiness local VERIFY — 2026-07-29

**대상:** `PRZ-002-license-sbom-ci` 작업 branch의 미커밋 IMPLEMENT 결과.
T-08 README·Quickstart 변경이 남아 있는 원래 작업 트리를 건드리지 않기 위해
`origin/main` `9b6352e75d1b56191322147a3953d5624739f58b`에서 별도 worktree를
만들었다.

**단일 검증 명령:** `node scripts/verify-oss-readiness.mjs`

| 항목 | 결과 |
|---|---|
| required OSS file | PASS — `LICENSE`, `NOTICE`, audit·manifest·SBOM·checksum 9개 |
| Markdown | PASS — 37개 파일, local link 243개, code fence·trailing whitespace 문제 0건 |
| tracked-file safety | PASS — 295개; 금지 경로·binary, model cache, 업로드 원본, credential, private key, 사용자 로컬 절대 경로 0건 |
| source-only license Gate | PASS — Apache-2.0 canonical checksum, NOTICE 저작권, blocker 0건, `UNKNOWN`·`CONFLICT`·`BLOCKED` 차단 상태 확인 |
| dependency verification | PASS — Gradle `generateBackendSbom --no-daemon --dependency-verification=strict` |
| SBOM 재생성 | PASS — backend 169개, frontend 183개; committed JSON과 drift 0건 |
| SBOM 구조·checksum | PASS — CycloneDX 1.6 기본 구조, 필수 component field, canonical hash, 고유 `bom-ref`, LF와 SHA-256 |
| 회귀 테스트 | PASS — Node 11건, 실패·skip 0건 |
| 외부 링크 | 91개 성공, 1개 `INDETERMINATE` (`https://www.oss.kr/pages/2`, HTTP 403), 반복 404·410 0개 |
| `git diff --check` | PASS |

외부 링크 검사는 2xx·3xx를 성공으로, 반복 404·410을 영구 삭제로 판정한다.
403·429·5xx·timeout·network 오류는 일시 장애 또는 접근 정책 가능성이 있어
명확한 `INDETERMINATE` 경고로 분리하되 삭제로 오판하지 않는다.

Windows host에서 Java 21.0.6으로 검증기를 시작했으며 Gradle project toolchain은
Java 17을 대상으로 한다. Node 22.17.0, npm 10.9.2를 사용했다. 첫 sandbox
실행은 Gradle distribution network 접근이 차단돼 실패했고, 네트워크가 허용된
동일 명령 재실행은 통과했다.

애플리케이션 동작이나 dependency graph를 바꾸지 않은 CI·검증기 범위이므로
전체 unit, integration, frontend lint/build는 재실행하지 않았다. Docker,
PostgreSQL, pgvector, Ollama, OpenSQL, OpenProxy, OpenHA는 모두 `NOT_RUN`이다.

이 로컬 VERIFY 시점에는 GitHub Actions가 아직 실제 branch에 실행되지 않아
`NOT_RUN`이었다. clean checkout 재현, GitHub check URL과 local/GitHub 결과
대조가 끝나기 전에는 T-09를 `VERIFIED` 또는 완료로 표시하지 않는다.

### 최초 GitHub Actions 실패와 corrective IMPLEMENT

commit `c7d5adec551f4cb237d1edf63123bf2f1ac25eba`의 push run
[`30442330201`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30442330201)은
`OSS Readiness`에서 실패했다. 원격 branch를 Linux/JDK 17/Node 22.17
컨테이너에 clean clone해 재현한 결과, GitHub token 정규식이 실제 값의 길이를
요구하지 않아 tracked된 검증기 자신의 `github_pat_` 정규식 접두사를
비밀정보로 오탐한 것이 원인이었다. 실제 token이나 credential이 포함된 것은
아니다.

정규식을 token-shaped value에만 일치하도록 제한하고, 정규식 선언 자체는
통과하면서 동적으로 조립한 fake token은 차단하는 회귀 테스트를 추가했다.
수정 후 동일 로컬 명령은 tracked file 298개, Node 회귀 테스트 12건,
외부 링크 92개 성공·1개 `INDETERMINATE`·반복 404/410 0개로 통과했다.
corrective commit과 Linux clean-clone·GitHub 재실행 결과는 후속 VERIFY에서
기록한다.

## T-09 corrective 최종 VERIFY — 2026-07-29

**대상 commit:** `192295227f566815fa026259d2053b1c73e641f2`

| 환경·검증 | 결과 |
|---|---|
| Windows local 단일 명령 | PASS — 최종 증거 문서 포함 tracked 298개, 회귀 테스트 12건, 외부 링크 94 OK·1 `INDETERMINATE`·0 permanent |
| Linux clean clone | PASS — `gradle:9.5.1-jdk17` 기반, Node 22.17.0; 같은 단일 명령 전체 통과 |
| GitHub OSS Readiness | [push run `30443185952`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443185952) PASS |
| 기존 GitHub CI | [push run `30443184506`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443184506) backend·frontend PASS |
| local/GitHub command | PASS — 모두 `node scripts/verify-oss-readiness.mjs` |

Linux clean clone과 GitHub checkout에서는 commit된 검증기 자체도 tracked-file
검사에 포함됐다. 최초 CI에서 발견된 자기 참조 오탐은 회귀 테스트와 실제
원격 성공으로 해소됐다.

## T-09 독립 읽기 전용 재AUDIT — 2026-07-29

| 항목 | 판정 |
|---|---|
| CRITICAL/HIGH/MEDIUM | 없음 |
| required OSS file·Markdown·external link 분류 | PASS |
| source-only license Gate와 future 배포 경계 | PASS |
| strict dependency verification·SBOM 재생성·drift·구조 | PASS |
| tracked model/cache·업로드 원본·credential·로컬 경로 차단 | PASS |
| local Windows·Linux clean clone·GitHub Actions 결과 대조 | PASS |
| CI 규모와 변경 범위 | PASS — Node 표준 라이브러리 기반 단일 job; 애플리케이션 기능 변경 없음 |

**감사 판정:** `PASS_FOR_T09_SCOPE`. OpenSQL·OpenProxy·OpenHA 검증이나
PRZ-002 전체 완료를 의미하지 않는다. PR 생성은 연결된 GitHub 앱의 쓰기 권한이
없어 HTTP 403으로 차단됐으며, 이는 구현 finding이 아니라 남은 `INTEGRATE`
권한 Gate다.

## T-08 독립 읽기 전용 AUDIT 및 솔로 통합 기록 — 2026-07-29

**감사 범위:** README, Quickstart, 문서 색인·상태·로드맵·추적성 문서와
T-08 evidence/task 변경을 수정자가 아닌 관점에서 읽기 전용으로 검토했다.

| 항목 | 결과 |
|---|---|
| 구현·계획·`NOT_RUN` 구분 | PASS — Docker Compose 기동·health 범위만 검증됨으로 기록했고, 로그인→업로드→`ACTIVE`→검색 전체 흐름과 Ollama/`bge-m3`는 `NOT_RUN`으로 유지했다. |
| 현재 source·Compose 대조 | PASS — PostgreSQL 16+pgvector, backend 8080, frontend 5173, host Ollama 주소 및 사용자 역할 제한이 문서 설명과 일치한다. |
| 제품 범위 | PASS — OpenSQL·OpenProxy·OpenHA, CareerFact·portfolio·MCP·`/api/v1`·독립 Engine 모듈을 미구현 또는 미검증 상태로 유지한다. |
| Markdown 품질 | PASS — 대상 9개 문서의 로컬 링크·code fence·trailing whitespace 및 `git diff --check`에 문제가 없다. |

**감사 판정:** `PASS_FOR_T08_SCOPE`. CRITICAL/HIGH/MEDIUM finding은 없었다.
애플리케이션 테스트는 문서 독립 감사 범위가 아니어서 재실행하지 않았다.

**솔로 통합 기록:** 외부 GitHub reviewer는 없었다. 사용자가 2026-07-29에
최신 `main` 통합과 병합된 임시 브랜치 정리를 명시 승인했으므로,
`REVIEW_NOT_AVAILABLE_SOLO`로 기록한다. 이 기록은 실제 GitHub review나
제3자 승인을 뜻하지 않는다.

## T-09 실제 GitHub 통합 — 2026-07-30 확인

- 실제 통합 PR은 [#22](https://github.com/jaemin-devlog/PRIZM/pull/22)이며
  source `5c31305`, merge commit `42876b6`으로 병합됐다.
- T-09 구현의
  [OSS Readiness run `30443185952`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443185952)과
  [CI run `30443184506`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443184506)은
  성공했다. 현재 공개 `main` `777e184f206d2a2770d055940ddabf139abfed9d`에서도
  [OSS Readiness run `30477035697`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035697)과
  [CI run `30477035700`](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035700)이
  성공했다.
- PR #22의 requested reviewer·comment·review는 없었다.
  `REVIEW_NOT_AVAILABLE_SOLO`는 정직한 절차 기록이며 GitHub review 또는
  제3자 승인 증거가 아니다.
- 2026-07-26의 GitHub Issue 쓰기 제한은 역사적 사실로 남긴다. 완료 작업을
  설명하는 Issue는 소급 생성하지 않았다.

## T-10 최종 독립 읽기 전용 AUDIT — 2026-07-30

**최초 공개 감사 기준:** commit
`777e184f206d2a2770d055940ddabf139abfed9d`, tree
`a6e9cb96cb4dc93d657397241d99d4367c7d2902`. GitHub repository는
`PUBLIC`, 기본 branch는 `main`이며 익명 원격 조회가 가능했다. 이 commit의
tracked file 300개에 backend·frontend source, Gradle Wrapper, 공개 config와
Flyway V1~V13이 모두 있었다.

첫 읽기 전용 감사는 CRITICAL 0·HIGH 0·MEDIUM 2·LOW 0을 보고하고 PRZ-002를
`IN_PROGRESS`로 유지했다.

| Finding | 교정 |
|---|---|
| `M-01` 현재 문서가 실제 OpenSQL single-node SQL Gate `PASS`를 과거 `NOT_RUN`으로 표시 | README, roadmap, 추적표, PRZ-002·PRZ-003의 현재 상태를 `PASS` 범위와 OpenProxy·OpenHA·DB failover `NOT_RUN`·`NOT_VERIFIED` 경계로 교정 |
| `M-02` PRZ-002 T-09가 실제 PR #22·CI·merge 뒤에도 통합 대기 상태 | PR #22, source `5c31305`, merge `42876b6`, 성공 CI와 `REVIEW_NOT_AVAILABLE_SOLO`를 evidence·registry·tasks에 반영 |

교정 뒤 `node scripts/verify-oss-readiness.mjs`를 다시 실행했다.

| 최종 Gate | 결과 |
|---|---|
| 필수 OSS 파일 | PASS — 9개 |
| Markdown·로컬 링크 | PASS — 38개, 264개 |
| tracked-file 안전성 | PASS — 300개; credential·model/cache·업로드 원본·금지 공급 binary 0개 |
| LICENSE·NOTICE·source-only 정책 | PASS |
| backend/frontend SBOM | PASS — backend 169개, frontend 183개; strict dependency verification, 재생성·구조·checksum drift 없음 |
| 회귀 테스트 | PASS — Node 12건, 실패·skip 0건 |
| 외부 링크 | 100개 성공, 1개 HTTP 403 `INDETERMINATE`, 영구 실패 0개 |
| `git diff --check` | PASS |

저장소 root에 있던 미추적 공급사 archive 1개는 내용을 열지 않고 Git 밖의
비공개 보관 위치로 옮겼다. 삭제하지 않았으며 tracked/generated artifact에는
포함되지 않는다. 모델 가중치·cache, container image, OpenSQL 공급 파일과
개별 테스트 라이선스는 계속 저장소에 넣지 않는다.

문서 전용 교정 worktree의 최종 독립 재감사에서 CRITICAL/HIGH/MEDIUM
finding은 0건이다. 검증된 교정 source commit
`f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`과 tree
`7e5f22fdbfbe1f4c87d8cd2c4fb579cba776e047`를 위 표와 registry에 고정했다.
이 감사는
GitHub review 또는 제3자 review가 아니다. 현재 source-only 공개 범위와 P0를
`VERIFIED`로 닫는다. JAR·frontend `dist`·container image·Ollama binary·모델
가중치를 배포하는 미래 Gate, 제출 직전 source snapshot, 외부 기여·보안 운영
문서는 각 재개 조건에 따라 별도로 감사한다.

이번 교정은 문서만 변경하며 제품 source, Flyway migration, dependency,
`LICENSE`, `NOTICE`와 SBOM JSON은 바꾸지 않았다. 따라서 Gradle unit·integration,
frontend lint·build와 Compose는 재실행하지 않았고, 병합된 `main`의 성공
CI 결과와 이번 OSS readiness 재검증을 사용했다.
