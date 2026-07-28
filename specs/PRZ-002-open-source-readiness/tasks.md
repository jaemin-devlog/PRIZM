# PRZ-002 작업 목록

## 상태

| 항목 | 값 |
|---|---|
| Spec | [spec.md](spec.md) |
| Plan | [plan.md](plan.md) |
| Spec status | `IN_PROGRESS` |
| PLAN | `COMPLETE` |
| IMPLEMENT | `IN_PROGRESS_LOCAL_ONLY` |
| GitHub Issue | `NOT_CREATED` (`BLOCKED_GITHUB_WRITE`: 2026-07-26 connector write returned HTTP 403) |

체크박스는 계획의 존재가 아니라 실제 file·명령·환경·결과 evidence가 확인된
경우에만 완료한다. `UNKNOWN`, `CONFLICT`, `NOT_RUN`을 임의로 PASS로 바꾸지
않는다.

## P-00 — PLAN

- [x] 현재 source, Gradle/npm lockfile, Docker, application profile, CI,
  fixture·asset 경로와 Git 상태를 읽기 전용으로 확인한다.
- [x] 공식 source register, license/provenance 감사와 배포 경계를 정의한다.
- [x] outgoing license, SECURITY 신고 채널과 GitHub 쓰기를 사용자 Gate로 둔다.
- [x] IMPLEMENT 10단계의 완료 조건, 검증·감사와 중단 조건을 정의한다.
- [x] 이번 PLAN에서는 plan·tasks·development log 외 파일을 변경하지 않는다.

## G-03A — IMPLEMENT 전 GitHub Issue·branch 권한

- [ ] 사용자가 GitHub Issue 생성 권한을 명시적으로 승인한다.
- [ ] 실제 Issue를 생성한 뒤 spec의 `NOT_CREATED`를 실제 URL로 바꾼다.
- [x] 최신 main 관계, staged 0건, 기존 user change 보존을 확인한다.
- [x] 기존 `PRZ-002-open-source-readiness` branch에서 안전하게 작업한다.
- [x] branch 기준 commit과 `origin/main` 관계를 evidence에 기록한다.

**중단 조건:** 권한이 없거나 기존 user change 때문에 안전한 branch 전환이
불가능하면 reset·stash로 우회하지 않고 GitHub Issue·PR evidence를 만들지
않는다. 사용자가 local-only IMPLEMENT를 명시적으로 승인한 경우 그 작업은
별도로 기록하되 GitHub evidence나 G-03A 통과로 계산하지 않는다.

**현재 판정:** `BLOCKED_GITHUB_WRITE`, `LOCAL_ONLY_IMPLEMENT_AUTHORIZED`.
사용자는 PRZ-002 작업을 승인했지만 2026-07-26 GitHub connector의 실제 Issue
생성 요청은 HTTP 403 (`Resource not accessible by integration`)으로 거부됐다.
같은 환경의 `gh auth status`도 authenticated GitHub host가 없다고 보고했다.
따라서 G-03A는 완료 처리하지 않으며 이 로컬 작업을 Issue·PR·review evidence로
계산하지 않는다. 현재 SBOM implementation branch의 기준은 `main`·`origin/main`
`0ad549a8641b2b6ef18a8011dac93286052b65c0`이며, 실제 Issue 생성 권한이
확보되기 전에는 URL이나 번호를 만들지 않는다.

## T-01 — 공식 source register

**로컬 선행 조건:** IMPLEMENT 시작 승인, 안전한 기존 branch, staged 0건,
공식 URL 접근 가능. GitHub Issue·PR evidence를 만들기 전에는 별도로 G-03A를
완료해야 한다.

- [x] 대회 홈페이지·개요 URL과 수집일을 기록한다.
- [x] 운영 규정 PDF의 공식 URL, 15쪽, SHA-256, media type·byte 크기를
  사용자 제공본과 교차 검증한다.
- [x] 운영 규정 제8~10조·별표 2와 원문 재배포 조건을 조항·쪽 단위로
  PRZ-002 요구사항에 연결한다.
- [x] 결과보고서 양식 ZIP의 공식 URL·SHA-256과 SBOM·AI 모델 명세 필드를
  정확한 양식 위치에 연결한다.
- [x] OT 공지와 사용자 제공 캡처를 `OT_AUXILIARY_USER_PROVIDED`로 등록하고
  공개 원본 URL 부재·재배포 불명을 기록한다.
- [x] source별 authority, title, canonical/artifact URL, 발행·수집일,
  hash, claim, rights, redistribution, supersession, 검증일을 채운다.
- [x] 공식 PDF·ZIP·캡처 원본이 tracked file이나 제출 source ZIP에 없음을
  확인한다.

**완료 evidence:** source register 경로, 원본 hash 검증 명령·결과, local
link 검사, 원문 비포함 `git ls-files` 결과

**실제 evidence (2026-07-24):**
[`2026-source-register.md`](../../docs/contest/2026-source-register.md),
사용자 보관 PDF 170,020 bytes·SHA-256 `5C129E…1DA1`, ZIP 142,434
bytes·SHA-256 `9A5D29…62D95` 대조 일치, OT 보조 캡처 3개의 local
bytes·SHA-256 기록, local link 누락 0건, tracked 공식 원문·OT artifact
0건. T-01은 `COMPLETE`다.

## T-02 — 전체 license·provenance 감사

**선행 조건:** T-01

### Java·Gradle

- [x] `build.gradle` 선언과 resolved `runtimeClasspath`,
  `testRuntimeClasspath`, `buildEnvironment`를 별도 inventory로 생성한다.
- [x] Gradle Wrapper 9.5.1 JAR·distribution URL·공식 checksum·license와
  `distributionSha256Sum`·verification metadata 상태를 기록한다.
- [x] 공식 Gradle 9.5.1 bin SHA-256을 Wrapper 설정에 고정하고, resolved
  graph 377 components·740 artifacts의 SHA-256 verification metadata를
  추가해 default strict mode Gradle 실행을 확인한다.
- [x] Spring Boot·dependency-management plugin과 plugin 전이를 감사한다.
- [x] dependency-management plugin `1.1.7`과 `org.tomlj:tomlj:1.0.0`의
  exact Maven Central POM·tagged LICENSE를 확인한다.
- [ ] Spring Boot, Spring AI Ollama, PDFBox, Flyway, PostgreSQL JDBC와
  모든 runtime 전이의 exact version·artifact hash·license·NOTICE를 확인한다.
- [x] PDFBox `META-INF/NOTICE`, PostgreSQL JDBC BSD-2-Clause, Logback
  복수 라이선스를 우선 판정한다.
- [x] Testcontainers, H2, JUnit·Mockito와 모든 test 전이를 production
  비포함 여부와 함께 기록한다.

### frontend·npm

- [x] package manifest와 lockfile 183 entry를 direct/transitive,
  runtime/dev/optional로 분류한다.
- [x] React·React DOM·scheduler가 실제 `dist`에 포함되는지 확인한다.
- [ ] MPL-2.0, CC-BY-4.0, BlueOak-1.0.0, BSD·ISC 항목의 license 원문과
  산출물 포함 여부를 판정한다.
- [ ] 모든 tarball integrity·upstream·license와 clean bundle을 대조한다.
- [x] Node 22.17.0, npm 10.9.2와 Docker builder tag의 재현성 차이를 기록한다.

### container·database·CI

- [ ] Temurin JDK/JRE, Node, Nginx, PostgreSQL·pgvector image의 tag,
  manifest/platform digest, base package SBOM, license·NOTICE를 확인한다.
- [ ] backend·frontend·database image가 배포되는지 사용자에게 확인한다.
- [x] GitHub Actions, runner, Java setup과 Ollama install script·model pull의
  upstream, license, exact revision과 공급망 위험을 기록한다.
- [x] 모든 third-party Action을 검증한 full commit SHA와 version 주석으로
  고정한다.
- [x] mutable Ollama install script를 exact `v0.32.3` Linux amd64 archive와
  SHA-256 검사로 교체한다.
- [x] `bge-m3` pull 전 registry manifest와 pull 후 local manifest를 exact
  digest로 검사하고 model·license blob 존재를 확인하는 fail-closed Gate를
  구현한다.
- [ ] 도입 후보 SBOM·license·link tool 자체를 먼저 감사한다.

### model·fixture·asset

- [x] Ollama source와 실제 binary/release의 version·license·약관을 구분한다.
- [x] `bge-m3` upstream revision, model card·LICENSE, Ollama manifest/blob
  digest, 사용 목적, cache·가중치 재배포 여부를 기록한다.
- [x] BAAI upstream reference revision과 Ollama 변환 lineage를 구분하고,
  확인되지 않은 대응은 `UNVERIFIED_LINEAGE`로 유지한다. 모델 bytes를
  배포하지 않는 source-only 경계상 PRIZM source license 충돌로 과장하지
  않되 future model 재배포와 정확한 lineage 주장은 차단한다.
- [x] Codex를 authoring assistant로만 기록하고 근거 없는 사용 비율을 쓰지
  않는다.
- [x] 검색 평가 fixture의 합성·작성 경위·재배포 권리와 개인정보 부재를 확인한다.
- [x] tracked test/frontend/docs asset·binary 전수를 file signature와
  provenance로 확인한다.
- [x] 초기 commit `b633f469`의 ZIP 생성 경위와 Spring Initializr·create-vite
  등 generator·template의 정확한 upstream·version·license를 확인한다.
- [x] frontend design token이 Toss Design System 또는 다른 외부 UI
  source에서 파생됐는지 확인하고, 파생됐다면 license·교체 결정을 기록한다.
  - [x] oh-my-design Toss reference 파생 사실과 권리 미확인 `BLOCKED` 판정
  - [x] 독립 PRIZM palette·spacing·radius 교체
- [x] archive 기획안의 inline Mermaid diagram 5개가 직접·Codex 보조
  제작인지, 외부 diagram·template에서 파생됐는지 확인한다.
- [x] Gamium 참고 저장소는 `DESIGN_REFERENCE_ONLY`로 기록하고 복사된
  code·문구·asset이 있는지 확인한다.

### 감사 종료

- [x] component마다 version/digest, upstream, SPDX, purpose, scope,
  distribution, NOTICE, decision, status, verified date가 있다.
- [x] 배포 범위의 `UNKNOWN`, `CONFLICT`, `BLOCKED`가 0건이거나 작업 전체를
  `BLOCKED`로 유지한다.

**완료 evidence:** license audit 경로, machine inventory hash, fat JAR·bundle·
image 대조 결과, unresolved component count

**현재 evidence (2026-07-24):**
[`2026-license-audit.md`](../../docs/contest/2026-license-audit.md),
[`2026-asset-provenance-audit.md`](../../docs/contest/2026-asset-provenance-audit.md),
Gradle runtime 167개·test 전체 217개·build environment 26개를 전수 기록했고
annotation processor를 포함한 Maven union은 238개다. npm lock 183/183의
version·resolved·integrity·license와 component별 scope·decision을 기록했다.
tracked binary 1개(wrapper JAR), tracked image·PDF·font·model 0개와 검색
평가 fixture 2개를 확인했다. Wrapper family 4개는 `VERIFIED_EXTERNAL`이다.
사용자는 fixture·초기 PRIZM 골격·Mermaid diagram을 본인 지휘 아래 Codex로
새로 작성했고 외부 자료를 복사·각색하지 않았으며 Apache-2.0 공개에
동의했다고 확인했다. 따라서 이 범위는 `VERIFIED_DIRECT`다. Spec Kit와
Robo Architect는 문서 구조의 외부 reference로만 기록했고 Gamium은 아직
반영되지 않았다. frontend color·spacing·radius token은 oh-my-design Toss
reference에서 가져온 사실을 확인한 뒤 2026-07-24 frontend source에서
제거하고 독립 `--prizm-*` 체계로 교체했다. Pretendard는 공식
`OFL-1.1`을 확인했으며 font binary·CDN 없이 system font preference로만
사용한다. 따라서 `BLOCKED_EXTERNAL_DESIGN_RIGHTS`는 해소됐다. G-01은
source-only로 확정했고 2026-07-25 Wrapper·dependency checksum, Action SHA,
Ollama archive와 `bge-m3` manifest Gate를 구현했다. 확인되지 않은 Ollama
변환 lineage는 model 미배포 경계와 함께 별도 제한으로 남겼다. 현재
source-only 배포에 실제 포함되는 component의 provenance·NOTICE 범위는
확정했으므로 T-02는 `COMPLETE_FOR_INITIAL_SOURCE_ONLY`다. future
binary·image·model release의 `UNKNOWN`·`BLOCKED`는 해당 release 전 재개할
별도 gate로 유지한다.

## G-01 — 배포 경계 사용자 결정

**선행 조건:** T-02 inventory 초안

- [x] source ZIP, fat JAR, frontend `dist`, backend/frontend/database image,
  Ollama binary, model cache의 현재 사실·추가 검증·결정 항목 비교표를 만든다.
- [x] source ZIP, fat JAR, frontend `dist`, backend/frontend/database image,
  Ollama binary, model cache 중 제출·배포 대상을 사용자가 확정한다.
- [x] 배포 경계 변경 뒤 NOTICE·SBOM 범위를 다시 계산한다.

**사용자 승인 evidence (2026-07-24):**
PRIZM은 우선 source·문서·실행 설정만 배포한다. PostgreSQL·pgvector,
Ollama와 `bge-m3`는 사용자가 공식 upstream에서 직접 내려받는다. PRIZM은
fat JAR, frontend `dist`, container image/archive, Ollama binary, model
weights·cache를 initial release에서 재배포하지 않는다. source SBOM은
실제 포함 component와 `external/provided` 실행 의존성을 분리한다.

**현재 판정:** `COMPLETE`

**중단 조건:** 경계가 확정되지 않으면 T-03 이후를 시작하지 않는다.

## T-03 — MIT·Apache-2.0 후보 비교와 승인

**선행 조건:** T-02, G-01

- [x] OSI 공식 목록과 두 license의 canonical 원문·checksum을 확인한다.
- [x] permissive 조건, explicit patent grant, 고지·수정 표시, NOTICE 운영,
  contributor·release 부담을 비교한다.
- [x] 현재 source-only 배포물에 실제 포함되는 direct source·Wrapper·fixture와
  후보 license의 호환성을 확인한다.
- [x] 현재 source-only의 복수 라이선스 선택, 예외 허가와 충돌 해소 근거를 기록한다.
- [x] 사용자에게 추천안과 남은 위험을 제시한다.
- [x] 사용자가 outgoing license를 명시적으로 승인한다.

> future JAR·bundle·image·Ollama/model 재배포의 호환성은 현재 source-only
> T-03 범위가 아니다. 해당 artifact를 배포하려는 release 전에 T-02를 다시 열어
> 별도 compatibility·NOTICE·SBOM gate를 통과해야 한다.

**중단 조건:** 미승인 또는 배포 범위 `UNKNOWN/CONFLICT` 존재 시 `BLOCKED`

**완료 evidence:** 승인된 license 이름·canonical URL, 결정일, 승인 근거와
audit snapshot hash

**결정 snapshot (2026-07-25):** PRIZM 직접 작성 source의 outgoing license로
`Apache-2.0`을 선택했다. [canonical 원문](https://www.apache.org/licenses/LICENSE-2.0.txt)의
SHA-256 `CFC7749B96F63BD31C3C42B5C471BF756814053E847C10F3EB003417BC523D30`을
확인했다. 현재 source-only 배포물은 직접 작성 source·문서·실행 설정,
Apache-2.0 Gradle Wrapper scripts/JAR 및 `VERIFIED_DIRECT` fixture만 포함한다.
따라서 future JAR·`dist`·image·Ollama/model 재배포 제한은 별도 release gate로
분리하며 T-04를 막지 않는다.

**현재 판정:** `COMPLETE_FOR_INITIAL_SOURCE_ONLY`; future artifact compatibility는
`BLOCKED_FUTURE_RELEASE`로 유지

## T-04 — `LICENSE`·`NOTICE`

**선행 조건:** T-03 승인

- [x] 승인된 표준 license 원문을 변형 없이 루트 `LICENSE`에 적용한다.
- [x] 저작권자를 `Jaemin Jeong`으로 기록한다.
- [x] 현재 source-only 배포물에 필요한 third-party 고지를 `NOTICE`에 반영한다.
- [ ] PDFBox 등 upstream NOTICE가 fat JAR/image에서 보존되는지 확인한다.
  - future fat JAR/image release 전 별도 gate; 현재 source-only 배포물에는 없음
- [x] 현재 source-only의 license·NOTICE 배치과 전달 방식을 검증한다.
  - JAR/`dist`/image별 검증은 future artifact release 전 별도 gate
- [x] Codex를 저작권자·공동 기여자·runtime dependency로 잘못 기록하지 않는다.

**완료 evidence:** canonical license 대조 결과, NOTICE coverage report,
배포물별 포함 검사

**현재 evidence (2026-07-25):** root `LICENSE`는 canonical Apache-2.0 원문과
SHA-256 `CFC7749B96F63BD31C3C42B5C471BF756814053E847C10F3EB003417BC523D30`이
일치한다. root `NOTICE`는 `Copyright 2026 Jaemin Jeong`, source-only 범위와
Gradle Wrapper의 embedded license/NOTICE 부재만 기록한다. 외부 JAR·npm package·
generated `dist`·image·Ollama binary·model bytes는 배포하지 않으므로 고지를
추측해 포함하지 않았다. future artifact 고지는 T-02 재개 조건이다.

**현재 판정:** `COMPLETE_FOR_INITIAL_SOURCE_ONLY`; future artifact coverage는
`BLOCKED_FUTURE_RELEASE`

## T-05 — SBOM·AI 모델 명세

**선행 조건:** T-02, G-01, 감사된 도구 승인

- [x] machine-readable CycloneDX 1.6 format과 backend/frontend first-party
  lockfile·resolved-graph generator를 구현한다. 2026-07-27 보완에서 frontend
  표준 hash algorithm, backend classifier-aware `bom-ref`, 운영체제 독립 LF
  출력을 구현했다. full formal-schema CI는 T-09로 남긴다.
- [x] backend runtime/test/build, frontend runtime/dev, CI, container,
  model, fixture·asset component를 구분한다.
- [x] 사람용 license audit와 machine SBOM component set을 상호 조정한다.
  Java module identity 167개에서 metadata-only 2개를 제외하고 Netty 한 module의
  classifier artifact 5개를 펼치면 machine artifact 169개가 됨을 기록했다.
- [x] clean checkout에서 재생성 가능한 local 명령과 checksum 검증을 완료한다.
  commit `8dd57c4`의 별도 local clone에서 backend·frontend를 다시 생성한 뒤
  Git 변경 0건과 checksum 검증 통과를 확인했다.
- [x] Ollama·`bge-m3`·Codex의 source, version/revision/digest, license·terms,
  purpose, execution·distribution boundary를 AI 명세에 기록한다.
- [x] 모델 파일·cache가 Git과 기본 제출물에 포함되지 않음을 검사한다.
- [x] credential, JWT, JDBC URL, host, 로컬 경로, 실제 업로드 문서가
  SBOM·명세에 없는지 검사한다.
- [ ] 제출 시점 commit·환경·생성 시각·결과 hash를 고정한다.

**현재 구현 기록 (2026-07-26):** [`SBOM·AI 모델 명세`](../../docs/contest/2026-sbom-model-manifest.md)를 추가했다. backend와 frontend 모두 external SBOM plugin/CLI를 추가하지 않는 first-party Gradle·lockfile generator를 사용한다. `@cyclonedx/cyclonedx-npm` 6.0.0은 high finding 10건으로, 4.0.1은 full audit endpoint가 신뢰 가능한 판정을 만들지 못해 채택하지 않았다. 구현 commit·명령·환경·hash는 [evidence.md](evidence.md)에 고정했다. T-05는 reconciliation·독립 감사 전까지 `IMPLEMENTED_UNVERIFIED`다.

**audit 기록 (2026-07-26):** `main...232915e` read-only audit은 SBOM source-only
경계, sensitive-data scan, regeneration/checksum, frontend 183-entry와 backend
169-component structural reconciliation에서 CRITICAL/HIGH/MEDIUM finding 없이
`PASS_FOR_IMPLEMENTED_SCOPE`였다. formal schema CI와 human/machine license
reconciliation은 미완료 gate이므로 T-05는 계속 `IMPLEMENTED_UNVERIFIED`다.

**최종 VERIFY 기록 (2026-07-27):** 병합된 `main`
`b36f6b236c2f70d26e243013df296b4dad1a54d9`의 깨끗한 archive와 JDK 17에서
재검증했다. frontend 183개 license cohort는 사람용 감사와 일치했지만,
깨끗한 checkout의 checksum 불일치, frontend 183개 hash algorithm의 공식
CycloneDX 1.6 schema 위반, Netty classifier 5개의 중복 `bom-ref`, 사람용 Java
runtime 167개와 machine 169개·고유 reference 165개의 미조정 차이가 확인됐다.
따라서 T-05는 `VERIFY_FAILED_RETURN_TO_IMPLEMENT`이며 위 결함을 수정하고
재검증하기 전에는 완료할 수 없다.

**IMPLEMENT 보완 기록 (2026-07-27):** `PRZ-002-sbom-conformance-fix`에서
backend 고정 LF·classifier-qualified PURL, frontend 표준 `SHA-512`, verifier의
hash algorithm·`bom-ref` 검사와 Node 회귀 테스트를 구현했다. 생성 결과는
backend 169개/고유 reference 169개, frontend 183개/`SHA-512`였고 로컬
재생성 hash는 결정적이었다. 이전 VERIFY 실패는 역사적 근거로 유지하며,
clean checkout·공식 schema·독립 AUDIT를 다시 통과하기 전에는 완료하지 않는다.

**최종 재VERIFY 기록 (2026-07-27):** commit `8dd57c4`의 별도 깨끗한 local
clone에서 checked-in verifier, 강제 backend 재생성, frontend 재생성, checksum,
Node 회귀 테스트를 실행했다. 재생성 뒤 Git 변경은 0건이었다. SHA-256
`1ebcb88a…e098f`의 공식 CycloneDX 1.6 BOM schema와 공식 SPDX·JSF schema를
사용한 validation은 backend·frontend 모두 통과했다. human/machine 조정은
167 module identity에서 metadata-only 2개를 제외하고 Netty classifier artifact
5개를 펼쳐 169개 artifact·169개 고유 reference가 됨을 다시 확인했다.

**독립 읽기 전용 AUDIT (2026-07-28):** corrective commit `8dd57c4`와 최종
VERIFY evidence를 수정자와 분리된 관점에서 검토했다. CycloneDX schema,
clean-checkout 재생성, checksum, human/machine 조정, 민감정보 검사, 생성기·검증기
범위를 확인한 결과 CRITICAL/HIGH/MEDIUM finding은 없었다. LOW 두 건은 (1) 이미
완료한 clean checkout·독립 감사 gate가 남은 작업처럼 보이던 문서 표현, (2) LF와
마지막 LF를 명시적으로 막는 Node 회귀 검증 부재였다. 이 후속 보완은 문서 현행화와
`scripts/verify-sbom.mjs`·Node 회귀 테스트로 반영한다. 이 agent AUDIT는 GitHub
review 증거가 아니다.

**현재 판정:** 현재 source-only 범위의 T-05 `VERIFIED`. 제출 직전 snapshot,
T-09 CI, 그리고 PRZ-002의 나머지 T-06~T-10은 별도 후속 작업이다.

**완료 evidence:** 재생성 명령, schema validation, human/machine diff,
AI model provenance와 secret scan 결과

## G-02 — SECURITY 신고 채널 결정

**선행 조건:** repository 설정 확인 권한

- [ ] GitHub Private Vulnerability Reporting을 활성화하고 접수 경로를
  실제 확인하거나, 사용자가 검증 가능한 전용 연락처를 제공한다.
- [ ] 선택한 경로의 maintainer 수신·응답 가능성을 확인한다.
- [ ] Issues·Discussions 지원 기능의 실제 활성화 상태를 확인한다.

**중단 조건:** 비공개 신고 채널이 없으면 `SECURITY.md` 게시 금지

## T-06 — 기여·행동강령·보안·지원·maintainer 정책

**선행 조건:** T-05, G-02

- [ ] CONTRIBUTING에 setup, test, Flyway forward-only, 문서·license,
  sensitive data, AI assistance disclosure 규칙을 기록한다.
- [ ] 실제 집행 가능한 CODE_OF_CONDUCT와 신고 경로를 연결한다.
- [ ] SECURITY에 지원 버전, 비공개 신고, 금지 정보, 응답 흐름을 기록한다.
- [ ] SUPPORT에 일반 질문·버그·보안 경로를 구분한다.
- [ ] maintainer 정책에 triage, release, dependency update, security,
  review·merge 책임과 solo 한계를 기록한다.
- [ ] 모든 링크와 실제 연락 경로를 검증한다.

**완료 evidence:** 운영 가능한 contact test, 문서 링크 검사, maintainer 승인

## T-07 — Issue Form·PR Template

**선행 조건:** T-06

- [ ] Bug Issue Form에 환경·version·재현·기대/실제·로그 정제·민감정보 금지를
  포함한다.
- [ ] Feature Issue Form에 문제·사용자·범위·대안·호환성·license 영향을
  포함한다.
- [ ] Documentation Issue Form에 위치·문제·현재/계획/`NOT_RUN` 정합성을
  포함한다.
- [ ] Issue config의 blank issue와 security contact link 정책을 정한다.
- [ ] PR Template에 spec/Issue, 변경 범위, test·환경, migration, owner/security,
  dependency/license, docs, `NOT_RUN`, reviewer 상태를 포함한다.
- [ ] GitHub schema validation과 실제 preview를 확인한다.

**완료 evidence:** GitHub preview URL 또는 캡처 hash, schema validation,
secret-safe field review

## T-08 — README·Quickstart·docs index

**선행 조건:** T-04~T-07

- [ ] README 첫 화면에 문제, Engine/Reference App 경계, 현재 기능, Quickstart,
  docs·license·contribution·security 경로를 배치한다.
- [ ] clean-clone Quickstart를 실제로 재현하고 필요한 환경·초기 사용자
  blocker를 정직하게 기록한다.
- [ ] 구현됨·계획됨·미검증을 표로 분리한다.
- [ ] OpenSQL·OpenProxy·OpenHA를 `NOT_RUN`으로 유지한다.
- [ ] CareerFact·portfolio·MCP·`/api/v1`·멀티모듈을 계획 상태로 유지한다.
- [ ] docs index, contest traceability, roadmap와 중복·깨진 링크를 정리한다.

**완료 evidence:** clean-clone log, Markdown link report, 현재/계획 표현 audit

## T-09 — Markdown·link·license/SBOM CI

**선행 조건:** T-05, T-08, CI tool 감사 완료

- [ ] required OSS file, local link, code fence, trailing whitespace 검사 명령을
  로컬에서 재현한다.
- [ ] external link의 일시 network 오류와 영구 404를 분리해 보고한다.
- [ ] dependency inventory coverage, forbidden/unknown/conflict license,
  SBOM schema·재생성 drift를 검사한다.
- [ ] model/cache·credential·업로드 원본·로컬 경로가 tracked/generated
  artifact에 없는지 검사한다.
- [x] 모든 third-party Action을 검증된 full commit SHA와 version 주석으로
  사용한다.
- [x] Ollama 설치와 model pull의 mutable identity를 허용하지 않는 Gate를 둔다.
- [ ] clean checkout local 결과와 GitHub Actions 결과를 대조한다.

**완료 evidence:** local command·exit code, GitHub check URL, pinned Action
inventory, SBOM diff report

**현재 supply-chain evidence (2026-07-25):** Action 4개 full SHA 고정,
Ollama `v0.32.3` archive checksum과 `bge-m3` pre/post manifest Gate 구현,
workflow YAML과 run block 10개 Bash 문법 통과. Gradle strict verification
단위 테스트 245건 중 231건 성공·14건 skip·실패/오류 0건,
`compileIntegrationTestJava` 성공. `PR #13` 병합 전후 backend·frontend push/PR
check 4건은 모두 성공했으며, backend job에서 Ollama archive·model Gate도 실제
실행됐다. SBOM·README/Quickstart·OSS file 검증 CI가 아직 없으므로 T-09 전체는
완료가 아니다.

## T-10 — 독립 읽기 전용 감사

**선행 조건:** T-01~T-09 VERIFY 완료

- [ ] source register의 공식 URL·hash·권리 상태를 재검증한다.
- [ ] lockfile/resolved graph/JAR/bundle/image/model과 audit·SBOM을 재대조한다.
- [ ] `LICENSE`, `NOTICE`, SBOM, AI 명세의 license·version·배포 경계를
  상호 확인한다.
- [ ] SECURITY·SUPPORT·Issue·PR 경로의 실제 동작을 확인한다.
- [ ] 공개 저장소와 generated artifact의 민감정보·모델 cache·로컬 경로
  부재를 확인한다.
- [ ] GitHub repository visibility가 실제 `PUBLIC`인지 API와 UI에서 확인한다.
- [ ] clean clone에 빌드에 필요한 직접 작성 backend·frontend source,
  V1~V13 migration, wrapper, 공개 config와 문서가 모두 있는지 확인한다.
- [ ] 제출 source의 commit·tree hash가 감사한 공개 commit과 일치하는지
  확인한다.
- [ ] README의 현재/계획/`NOT_RUN` 표현을 source와 대조한다.
- [ ] CRITICAL/HIGH/MEDIUM finding을 심각도·파일·근거로 보고한다.
- [ ] finding이 있으면 `IN_PROGRESS`, 외부 결정이 필요하면 `BLOCKED`로 둔다.
- [ ] finding이 0건일 때만 audit `PASS`를 기록한다.

**완료 evidence:** 독립 audit report, finding count, 검증 명령·환경,
repository visibility와 clean-clone inventory, 제출 commit·tree hash,
OpenSQL·OpenProxy·OpenHA `NOT_RUN`

## G-03B — AUDIT 후 PR·review·integration 권한

- [ ] IMPLEMENT·VERIFY·AUDIT가 끝난 뒤 PR을 만든다.
- [ ] 실제 reviewer가 없으면 `REVIEW_NOT_AVAILABLE_SOLO`를 기록하고
  Agent audit를 GitHub review로 주장하지 않는다.
- [ ] merge 뒤 source·merge commit, CI·audit evidence를 기록한다.
- [ ] main 포함 여부와 working tree를 확인한 뒤에만 임시 branch를 정리한다.

## VERIFY 실행표

| 검증 | IMPLEMENT 후 기대 | 미실행 처리 |
|---|---|---|
| Markdown local/external link·code fence·trailing whitespace | 필수 | `NOT_RUN` 불가 |
| `git diff --check`와 변경 범위 | 필수 | `NOT_RUN` 불가 |
| Gradle 전체 unit test | build/CI 영향 시 필수 | 이유·환경을 `NOT_RUN` |
| PostgreSQL·pgvector integration | dependency/container/Quickstart 영향 시 필수 | PostgreSQL 미사용을 명시 |
| frontend lint·build | frontend package/README Quickstart 영향 시 필수 | 이유를 `NOT_RUN` |
| `docker compose config`, runtime image SBOM | container audit 시 필수 | Docker 미사용을 명시 |
| Ollama·`bge-m3` identity·runtime smoke | model/Quickstart audit 시 필수 | Ollama 미사용을 명시 |
| OpenSQL·OpenProxy·OpenHA | 이번 범위 제외 | 항상 별도 `NOT_RUN` |

## 최종 완료 조건

- [ ] T-01~T-10이 evidence와 함께 완료됐다.
- [ ] G-01~G-03의 사용자·권한 결정이 실제로 기록됐다.
- [ ] 배포 범위에 `UNKNOWN`, `CONFLICT`, `BLOCKED`가 없다.
- [ ] 독립 감사에 CRITICAL/HIGH/MEDIUM finding이 없다.
- [ ] 실제 GitHub Issue·PR·CI·review 상태를 과장하지 않았다.
- [ ] PRZ-002 evidence와 registry가 실제 source·merge commit을 가리킨다.

현재는 T-01 source register, T-02 component inventory와 G-01 source-only
배포 경계, T-03 Apache-2.0 결정 snapshot이 현재 source-only 범위에서
완료됐다. T-04 Apache-2.0 `LICENSE`·source-only `NOTICE`와 T-05
machine-readable SBOM·AI 모델 명세도 구현됐으며, T-05는 PR #18 병합 기준
`VERIFIED`다. PRZ-002 전체 IMPLEMENT는 `IN_PROGRESS`다. 외부 design token
blocker와 build·CI artifact identity blocker는 해소됐고, future artifact
제한은 현재 source-only 배포를 막지 않는다. GitHub Issue와 T-06~T-10의
governance·template·README/Quickstart·license/SBOM 검사 CI·전체 독립 감사는
아직 완료하지 않았다.
공급망 pin은 PR #13으로 병합됐고 GitHub Actions backend·frontend push/PR
check 4건이 성공했다. 2026-07-25 보완 검증에서는 strict dependency
verification으로 단위 테스트 245건 중 231건 성공·14건 환경 조건 skip·실패
0건, PostgreSQL 16 + pgvector 통합 테스트 68건 중 65건 성공·3건 환경 조건
skip·실패 0건을 확인했다.
