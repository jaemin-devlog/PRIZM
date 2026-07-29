# PRZ-002 실행 계획

## 문서 상태

| 항목 | 값 |
|---|---|
| Spec | [PRZ-002](spec.md) |
| Spec status | `VERIFIED` |
| PLAN | `COMPLETE` |
| IMPLEMENT | `COMPLETE_FOR_CURRENT_SOURCE_ONLY_SCOPE` |
| VERIFY / AUDIT | `PASS` — T-09 GitHub CI와 T-10 독립 읽기 전용 감사 |
| INTEGRATE | `IN_PROGRESS` — T-08 solo main 통합, T-09 PR #22 merge `42876b6`; T-10 교정 commit·push 기록 대기 |
| GitHub Issue | `NOT_CREATED` |
| Primary evaluation | `EVAL-R1-02` |
| Secondary evaluation | `EVAL-R1-03`, `EVAL-R1-05` |
| 계획 기준일 | 2026-07-24, 범위 조정 2026-07-28 |

이 문서는 8월 27일 출품작 제출과 10월 라이선스 검증 전에 수행할
오픈소스 준비 작업의 순서와 증거 Gate를 정의한다. 점수를 예측하지 않으며,
문서가 존재한다는 사실만으로 평가 조건을 충족했다고 판단하지 않는다.

## 현재 기준선과 보존 계약

- 직접 작성 코드의 저작권자 표기는 `Jaemin Jeong`으로 준비한다.
- 공동 개발자 또는 코드 기여자는 현재 확인된 범위에서 없다.
- 동일 PRIZM 프로젝트로 받은 정부 지원금·상금·개발비는 없다.
- Codex는 코드·테스트·문서 작성 보조도구로 사용했다. AI 사용 비율이나
  제3자 저작물 부재를 추정하지 않는다.
- 외부 코드·이미지·fixture·샘플 데이터가 없다고 아직 확정하지 않는다.
  전체 provenance 감사로 확인한다.
- 현재 저장소에는 루트 Apache-2.0 `LICENSE`, source-only `NOTICE`,
  machine-readable SBOM과 AI 모델 명세가 있다. 공개 기여·행동강령·보안·
  지원 문서와 GitHub Issue Form·PR Template은 아직 없으며 아래 재개
  조건까지 `DEFERRED`다.
- PostgreSQL 검증 결과를 OpenSQL·OpenProxy·OpenHA 결과로 바꾸어 표현하지
  않는다. PRZ-003의 실제 OpenSQL single-node SQL Gate만 별도 `PASS`이며,
  OpenProxy·OpenHA와 전체 사용자 흐름은 계속 `NOT_RUN` 또는 `NOT_VERIFIED`다.
- Java source, frontend, Flyway V1~V13, production config, Docker Compose와
  Career Vault 동작 계약은 이 작업에서 변경하지 않는다.
- 공식 규정·양식 원본, OT 캡처, credential, 실제 업로드 문서, DB volume,
  모델 가중치·cache와 로컬 생성물을 Git에 넣지 않는다.

## 공식 근거 등록 원칙

IMPLEMENT 첫 단계에서 단일 source register를 만들고 다음 자료를 등록한다.

| Source ID | 자료 | 공개 원본과 식별 정보 | 현재 근거 등급 | 재배포 계획 |
|---|---|---|---|---|
| `SRC-CONTEST-HOME` | 대회 홈페이지·개요 | `https://osscontest.kr/`, `https://osscontest.kr/overview`, 수집일 2026-07-24 KST | `OFFICIAL_WEB` | 링크·메타데이터만 기록 |
| `SRC-CONTEST-RULES` | 2026년 오픈소스 개발자 대회 운영 규정 | `https://api.osscontest.kr/static/uploads/b3b4491a-3bbe-454e-a1d8-6ed475b01b14.pdf`, 15쪽, SHA-256 `5C129ED9F389ECC04B6F7BA8B97F719A313EFAF32AEA9178E635500023AE1DA1` | `OFFICIAL_ARTIFACT` | 원본을 커밋하지 않고 조항·쪽·필요 최소 요약만 기록 |
| `SRC-CONTEST-REPORT` | 2026년 오픈소스 개발자 대회 결과보고서 양식 | `https://api.osscontest.kr/static/uploads/46414fba-c473-4dae-b595-7214d635b494.zip`, SHA-256 `9A5D2968D48FF8A8FD85CE991DC72DC2B0818D7E8C06EBB871CC97CE5CC62D95` | `OFFICIAL_ARTIFACT` | 원본과 작성본을 커밋하지 않고 요구 필드만 기록 |
| `SRC-CONTEST-OT` | 2026 OT 평가 슬라이드 캡처 | 공식 공지 `https://osscontest.kr/notice/31`, 공개 슬라이드 원본 URL 없음 | `OT_AUXILIARY_USER_PROVIDED` | 이미지 복사 금지, 사용자 제공 사실·해시·한계만 기록 |

각 source 행에는 `source_id`, 발행 주체, 정확한 제목, canonical URL과
artifact URL, 발행·게시일, 수집일과 시간대, media type·byte 크기·SHA-256,
근거 조항·쪽, 필요한 최소 인용 또는 요약, PRIZM 요구사항 ID, 원본 확인 방식,
저작권·재배포 상태, 대체·갱신 관계, 마지막 검증일을 둔다. 권리 조건이
불명확하면 `UNKNOWN_DO_NOT_COMMIT`으로 처리한다.

공식 규정은 다음 항목을 source register에서 조항·쪽 단위로 재검증한다.

- 제8조의 직접 작성 코드 OSI 인증 라이선스, non-OSI 사용 제한, 제3자
  저작물과 모든 OSS 라이브러리·프레임워크·모델 출처 공개
- 제9조와 별표 2의 공개 가중치 모델, 모델 라이선스·약관·재배포·로컬 실행
  조건과 직접 작성 추론 코드의 별도 OSI 라이선스
- 제10조의 전체 source 공개 및 공개 저장소 유지 조건
- 운영 규정 원문 자체의 인용·활용 조건과 무단 변형·재배포 제한

## 배포 경계와 감사 레코드

### 배포 경계

NOTICE와 SBOM 범위를 정하기 전에 아래 산출물을 각각 분류한다.

| 산출물 | 기본 판단 | 감사 시 확인할 내용 |
|---|---|---|
| 공개 Git 저장소·source ZIP | 배포 | wrapper, fixture, 문서·자산, 생성 스크립트 포함 여부 |
| Spring Boot fat JAR | 배포 | `runtimeClasspath` JAR와 각 `META-INF/LICENSE`, `NOTICE` 포함 |
| frontend `dist` | 배포 | 실제 bundle에 포함된 npm runtime 코드와 license text |
| backend·frontend 파생 이미지 | 조건부 배포 | 제출·registry 게시 여부, base OS/JRE/Nginx 구성요소 |
| PostgreSQL·pgvector 이미지 | 기본은 이용자 pull | image archive·파생 이미지 재배포 여부 |
| Gradle·npm·test·CI 도구 | 실행 전용 | 최종 산출물 비포함 증명, source/CI inventory에는 유지 |
| Ollama binary | 외부 runtime prerequisite | 설치·실행 버전과 약관, PRIZM이 binary를 동봉하는지 |
| `bge-m3` 가중치·cache | 외부 runtime download, Git 미포함 | 정확한 manifest/blob digest, upstream revision, 재배포 여부 |
| 제출용 보고서·SBOM | 별도 제출 산출물 | 개인정보·경로·credential 제거, 제출 시점 해시 |

제출 형태가 달라지면 NOTICE와 SBOM을 다시 계산한다. 특히 Docker image를
배포하지 않는다는 판단을 image 안 구성요소를 감사하지 않는 이유로 사용하지
않고, 재현 가능한 운영 환경 inventory에는 계속 기록한다.

### 감사 레코드 필드

모든 구성요소를 다음 필드로 기록한다.

- component ID, ecosystem, 이름·coordinate·package·image·model ID
- exact version, Git revision, container/model digest, artifact hash
- upstream project·release·license 원문 URL과 검증일
- SPDX identifier와 복수·선택 라이선스 조건
- 사용 목적과 실제 source/test/config 근거
- direct/transitive, runtime/build/test/CI/model/data/asset 범위
- 실행 전용·download-at-runtime·bundle·수정·재배포 여부
- 저작권 고지, LICENSE·NOTICE·source 제공·수정 표시 의무
- PRIZM `NOTICE` 반영 결정과 배포 산출물별 적용 위치
- 상태와 검토자: `VERIFIED`, `NOT_DISTRIBUTED`, `UNKNOWN`,
  `CONFLICT`, `BLOCKED`

POM, package manifest 또는 model card의 license 문자열만으로 끝내지 않는다.
실제 artifact의 LICENSE·NOTICE와 배포물 포함 여부를 대조한다. 자동 도구의
결과는 후보 inventory이며, 사람의 근거 검토를 대체하지 않는다.

## 정확한 감사 범위

### Gradle·Java

1. `build.gradle`, `settings.gradle`, resolved `runtimeClasspath`,
   `testRuntimeClasspath`, `buildEnvironment`를 서로 분리해 기록한다.
2. Gradle Wrapper 9.5.1의 wrapper JAR, 배포 ZIP URL·공식 checksum,
   Apache-2.0 근거와 `distributionSha256Sum` 부재를 확인한다.
3. Spring Boot plugin 4.1.0, dependency-management plugin 1.1.7,
   Java 내장 plugin과 plugin 전이를 build-only inventory에 넣는다.
4. Spring Boot 4.1.0 starters, Spring AI Ollama 2.0.0, PDFBox 3.0.3,
   Flyway PostgreSQL 12.4.0, PostgreSQL JDBC 42.7.11과 모든 runtime 전이를
   fat JAR 재배포 범위로 감사한다.
5. PDFBox의 실제 `META-INF/NOTICE`, PostgreSQL JDBC BSD-2-Clause,
   Logback의 복수 라이선스 조건을 우선 확인한다.
6. Testcontainers 2.0.5, H2 2.4.240, JUnit·Mockito와 모든 test 전이는
   production 비포함 여부를 증명하되 개발·CI SBOM에서 누락하지 않는다.
7. 도입할 SBOM·license plugin 자체의 version·upstream·license·전이도 먼저
   감사한다. 감사되지 않은 plugin을 Gate 도구로 바로 추가하지 않는다.

### npm·frontend

1. `frontend/package.json`, lockfile version 3과 총 183 package entry를
   lockfile 기준으로 다시 생성·대조한다.
2. React·React DOM 19.2.7과 scheduler 0.27.0이 실제 runtime bundle에
   포함되는지 확인하고 MIT 고지를 배포물에 보존한다.
3. ESLint, TypeScript, Vite와 모든 dev·optional·platform 전이를 감사한다.
4. MPL-2.0 계열 `lightningcss`, CC-BY-4.0 `caniuse-lite`,
   BlueOak-1.0.0·BSD·ISC 항목을 산출물 포함 여부와 함께 별도 판정한다.
5. package tarball의 license text·integrity와 clean build `dist`의 실제
   구성을 대조한다. dev-only라는 lockfile 표시만으로 제외하지 않는다.
6. `.nvmrc`의 Node 22.17.0, npm 10.9.2와 `node:22-alpine` builder의
   floating patch 차이를 기록하고 재현 가능한 버전 정책을 정한다.

### 컨테이너·데이터베이스

다음 image의 tag, multi-arch manifest digest, 실제 대상 architecture digest,
upstream, base OS/JRE package inventory, license와 고지 파일을 조사한다.

- `eclipse-temurin:17-jdk`, `eclipse-temurin:17-jre`
- `node:22-alpine`, `nginx:1.27-alpine`
- `pgvector/pgvector:0.8.2-pg16-bookworm`
- 그 안의 OpenJDK, Nginx, Alpine·Debian package, PostgreSQL·pgvector

`nginx:1.27-alpine`의 지원 상태와 모든 floating tag를 위험으로 기록한다.
향후 digest pinning은 현재 동작·업데이트 정책과 함께 별도 승인 후 수행하며,
이번 PRZ-002에서 Dockerfile·Compose는 변경하지 않는다. 최종 runtime image
SBOM은 backend, frontend, database를 구분한다.

### Ollama·`bge-m3`·AI 사용

1. Ollama source/project license와 실제 설치·실행 binary의 version,
   release artifact·checksum·약관을 분리한다. CI의 mutable install script와
   `ollama pull bge-m3` 별칭을 공급망 위험으로 기록한다.
2. `bge-m3`는 upstream model repository, model card, LICENSE, commit/revision,
   Ollama manifest와 blob digest, 1024차원 사용 목적, 다운로드·cache·배포
   방식을 기록한다. model card의 license tag만으로 검증을 끝내지 않는다.
3. Ollama source license, Ollama 배포물, `bge-m3` 가중치 license·약관,
   PRIZM 직접 작성 integration code와 PRIZM outgoing license를 서로 다른
   레코드로 유지한다.
4. 가중치와 cache는 Git·source ZIP·기본 제출물에 넣지 않는다. 동봉이
   필요해지면 모델 약관·NOTICE·SBOM·공개 가중치 요건을 다시 심사한다.
5. Codex는 개발 보조도구로 AI 명세에 기록한다. 확인 가능한 제품명·사용 목적·
   결과 검토 방식만 쓰고, 근거 없는 model version·코드 비율·독창성 보증은
   만들지 않는다. Codex는 PRIZM runtime 모델이나 배포 dependency로 계산하지
   않는다.
6. OpenSQL·OpenProxy·OpenHA는 이 PRZ-002 감사의 실행 대상이 아니므로
   `NOT_RUN_IN_THIS_TASK`로 남긴다. 저장소 전체 상태는 PRZ-003의 실제 OpenSQL
   single-node SQL Gate `PASS`와 OpenProxy·OpenHA 미검증을 별도로 기록한다.

### CI·자동화

- `.github/workflows/ci.yml`의 `actions/checkout@v6`,
  `actions/setup-java@v5`, `actions/setup-node@v6`, `ubuntu-latest`,
  Java 17과 Ollama install·model pull 경로를 감사한다.
- Action은 upstream license와 검증된 full commit SHA를 기록한다. mutable
  major tag나 remote install script를 그대로 신뢰하지 않는다.
- 향후 CI action·scanner·link checker·SBOM 도구도 inventory에 포함하며,
  검증된 SHA·version·checksum과 갱신 정책을 둔다.

### fixture·sample·이미지·문서 자산

- `src/test/resources/search-evaluation/sample/corpus.json`과
  `questions.jsonl`의 작성 경위, 합성 여부, source, 개인정보 부재와
  재배포 권리를 확인한다.
- test resources, frontend public/source, docs, README, GitHub metadata,
  Docker init script와 tracked binary를 전체 검색한다.
- 현재 tracked 범위에 이미지·PDF·office sample이 없다는 예비 결과를
  IMPLEMENT 시점의 `git ls-files`와 file signature 검사로 재확인한다.
- `local/`, `outputs/`, `tools/`, `build/`, 실제 업로드 문서와 모델 cache는
  감사용으로 Git에 추가하지 않는다. 존재 여부나 경로를 공개 문서에 기록하지
  않는다.
- Gamium 계열 저장소는 설계 참고 자료로만 등록한다. 문구·template·code를
  가져오게 되면 별도 third-party component로 exact commit과 license를
  감사하고 필요한 attribution을 추가한다.

## 사용자 결정 Gate

### G-01 — outgoing license

전체 감사와 배포 경계가 확정되기 전에는 PRIZM의 `LICENSE`를 선택하지 않는다.

| 비교 항목 | MIT 후보 | Apache-2.0 후보 |
|---|---|---|
| OSI 승인 | 확인 대상 | 확인 대상 |
| 기본 성격 | 간결한 permissive license | permissive license와 명시적 patent grant |
| 고지 | 저작권·허가문 보존 | LICENSE, NOTICE 전달과 수정 파일 고지 검토 |
| 특허 조항 | 명시적 patent grant 없음 | patent grant·termination 조항 있음 |
| 운영 비용 | 낮음 | NOTICE·수정 표시 관리가 더 큼 |
| 결정 전 확인 | 모든 dependency·model·asset 호환성 | 동일 항목과 NOTICE 운영 가능성 |

다음 조건을 모두 만족한 표와 추천안을 사용자에게 제시한다.

1. OSI 공식 목록과 후보 license 원문을 확인했다.
2. 배포 대상의 모든 component가 `VERIFIED` 또는 근거 있는
   `NOT_DISTRIBUTED`다.
3. 복수 라이선스의 선택 경로, NOTICE 의무와 모델 약관 충돌이 해소됐다.
4. 직접 작성 코드·제3자 code·model·asset의 경계가 명확하다.
5. 사용자가 MIT 또는 Apache-2.0 중 하나를 명시적으로 승인했다.

승인 전에는 `LICENSE`·`NOTICE`를 생성하지 않는다. `UNKNOWN`, `CONFLICT`,
non-redistributable component가 배포 경계에 하나라도 남으면 `BLOCKED`로
중단한다. 이 감사는 공학적 compliance evidence이며 법률 자문이 아니다.
해석 충돌이 계속되면 전문가 확인 전 release 통합을 중단한다.

### G-02 — SECURITY 신고 채널

**현재 상태:** `DEFERRED`. 현재 source-only P0 제출 Gate를 막지 않으며,
첫 지원 release·외부 배포 또는 외부 기여 접수 중 먼저 도래하는 시점에
재개한다. 실제로 운영 가능한 비공개 신고 채널이 확인되기 전에는
`SECURITY.md`를 게시하지 않는다.

1. GitHub Private Vulnerability Reporting을 사용자가 활성화하고 실제
   advisory 접수 화면을 검증하거나,
2. 사용자가 전용 연락처를 제공하고 수신 가능성을 검증한다.

재개 뒤 둘 중 하나가 충족되지 않으면 해당 거버넌스 release를 `BLOCKED`한다.
가짜 이메일, 사용자가 확인하지 않은 개인 주소, 공개 Issue를 보안 신고 기본
경로로 쓰지 않는다. `SUPPORT.md`의 Issues·Discussions 경로도 repository
기능이 실제 활성화됐는지 확인한다.

### G-03 — 도구·GitHub 쓰기 권한

- SBOM/license/link 검사 도구는 자체 라이선스·version·재현성을 비교한 뒤
  사용자가 변경 범위를 승인해야 한다.
- IMPLEMENT 시작 전 최신 `origin/main`과 staged 상태를 확인한다. 현재 사용자
  `AGENTS.md` 변경이 안전한 branch 전환을 막으면 우회하지 않고 중단한다.
- 사용자가 GitHub 쓰기를 승인한 경우에만 실제 Issue를 생성하고 spec에 URL을
  기록한 뒤 최신 main에서 임시 branch `PRZ-002-open-source-readiness`를 만든다.
- IMPLEMENT·VERIFY·AUDIT가 끝난 뒤에만 PR을 만든다. 실제 reviewer가 없으면
  `REVIEW_NOT_AVAILABLE_SOLO`와 독립 감사를 기록하며, 이를 GitHub review
  증거라고 주장하지 않는다.

## IMPLEMENT 순서와 완료 조건

### 1. 공식 source register

**작업:** 공식 URL·artifact hash·조항·쪽·재배포 경계와 OT 보조 근거를 단일
register에 기록하고, 요구사항 ID와 연결한다.

**완료 조건:** 두 공식 artifact를 원본 URL과 제공본 hash로 교차 확인하고,
모든 source에 근거 등급·수집일·권리 상태가 있으며 원문 파일이나 캡처가
tracked file에 없다.

### 2. 전체 license·provenance 감사

**작업:** 위 감사 범위와 배포 경계를 사람이 검토 가능한 inventory로 만들고,
자동 도구 결과·artifact LICENSE/NOTICE·실제 bundle/image 내용을 대조한다.

**완료 조건:** 모든 발견 component에 필수 필드와 상태가 있고 배포 범위에
`UNKNOWN`·`CONFLICT`가 0건이다. 미해결이면 다음 단계로 진행하지 않고
`BLOCKED` evidence를 남긴다.

### 3. MIT·Apache-2.0 비교와 사용자 승인

**작업:** 감사 결과에 따른 호환성·patent·NOTICE·운영 비용 비교와 추천안을
사용자에게 제시한다.

**완료 조건:** 사용자의 명시적 선택과 결정 근거가 비민감 기록으로 남는다.
승인 없이 default license를 정하지 않는다.

### 4. `LICENSE`·`NOTICE`

**작업:** 승인된 표준 원문을 루트 `LICENSE`에 그대로 적용하고, `Jaemin Jeong`,
연도와 제3자 고지·model·asset 경계를 audit 결과대로 `NOTICE`에 기록한다.

**완료 조건:** license 원문 checksum 또는 canonical 원문 대조가 통과하고,
fat JAR·frontend bundle·배포 image별 NOTICE 의무가 누락되지 않는다.

### 5. SBOM·AI 모델 명세

**작업:** 사람용 감사표와 machine-readable CycloneDX 또는 동등한 SBOM을
재현 가능한 명령으로 생성한다. backend runtime/test/build, frontend runtime/dev,
container image, CI, model, fixture·asset을 구분한다.

AI 명세에는 Ollama, `bge-m3`, Codex를 서로 다른 사용 유형으로 기록하고,
version/revision/digest·license·약관·용도·탑재/호출 방식·가중치 배포 여부와
`UNKNOWN`을 숨기지 않는다.

**완료 조건:** clean checkout에서 같은 component set을 재생성·검증할 수 있고,
credential·로컬 경로·원문 문서·모델 cache가 결과에 없다. 제출 시점에는 명령,
환경, 생성 시각, commit, 결과 hash를 evidence에 고정한다.

### 6. 기여·행동강령·보안·지원·maintainer 정책 — `DEFERRED`

**재개 조건:** 외부 기여 접수를 공식 지원하거나 첫 지원 release·외부 배포를
준비하는 시점 중 먼저 도래하는 때 G-02와 함께 재개한다.

**재개 후 작업:** `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
`SUPPORT.md`와 최소 maintainer 정책을 작성한다. 개발 명령, test Gate,
Flyway forward-only, owner/security, dependency/license, sensitive data와
AI 보조도구 disclosure 원칙을 포함한다.

**완료 조건:** G-02가 통과하고 모든 안내 경로가 실제 작동한다. maintainer
권한·release·dependency update·취약점 처리 책임과 solo project의 review
한계가 정직하게 기록된다.

### 7. Issue Form·PR Template — `DEFERRED`

**재개 조건:** 외부 Issue·PR 접수를 공식 지원하기 전에 재개한다.

**재개 후 작업:** Bug, Feature, Documentation Issue Form과 PR Template을 만든다.
환경·재현·범위·test·migration·security/ownership·dependency/license·문서·
`NOT_RUN`을 구조화하되 secret·JWT·문서 원문 업로드를 막는다.

**완료 조건:** GitHub schema 검증과 실제 preview가 통과하고, blank issue와
contact link 정책이 의도대로 동작한다. template 존재를 실제 issue·review
증거로 계산하지 않는다.

### 8. README·Quickstart·docs index 현행화

**작업:** 문제·제품 경계·현재 기능·Quickstart·검증 환경·제약·roadmap·
license·SBOM 링크와 현재 외부 기여·보안 운영 상태 순서로 첫 진입 경로를
정리한다. 존재하지 않는 기여·보안 문서 링크는 만들지 않는다.

**완료 조건:** 현재 구현, 계획, 환경 미검증을 표로 분리하고 실제 OpenSQL
single-node SQL Gate `PASS`를 OpenProxy·OpenHA·DB failover·전체 사용자 흐름
성공으로 확대하지 않는다. CareerFact·portfolio·MCP·멀티모듈은 계획으로
유지하고 clean-clone Quickstart는 재현되거나 blocker가 정확히 기록된다.

### 9. Markdown·링크·license/SBOM CI

**작업:** 로컬 재현 명령과 CI에서 required OSS file, Markdown local link,
code fence, trailing whitespace, license inventory coverage, 금지·미확인
license, SBOM 재생성, model/cache·secret 제외를 검증한다.

GitHub Actions는 감사된 full commit SHA로 pin하고, 외부 link 검사는 네트워크
일시 오류와 실제 누락을 구분한다. 도구 자체도 SBOM에 넣는다.

**완료 조건:** clean checkout의 local 명령과 GitHub Actions가 같은 결과를
내며, 실패가 component와 근거를 식별한다. GitHub UI의 실제 성공 check URL을
evidence에 남긴다.

### 10. 독립 읽기 전용 감사

**작업:** 구현자가 아닌 별도 검토 관점에서 source·artifact·generated SBOM·
CI·Git diff와 G-02·T-06·T-07의 deferral 기록을 직접 확인한다.

**완료 조건:** 라이선스 충돌, source 누락, 민감정보 노출, 가짜 신고 경로,
deferral 누락과 구현 과장에 CRITICAL/HIGH/MEDIUM finding이 0건이다. finding이 있으면
`IN_PROGRESS`, 외부 결정 없이는 해결할 수 없으면 `BLOCKED`로 유지한다.
GitHub repository visibility가 실제 `PUBLIC`인지 확인하고, 감사한 commit의
clean clone에 빌드에 필요한 직접 작성 backend·frontend source, V1~V13
migration, wrapper, 공개 config와 문서가 모두 있는지 검사한다. secret·model
cache·업로드 원본 같은 제외 대상은 없어야 하며, 제출 source의 commit·tree
hash가 감사한 공개 commit과 일치해야 한다.

## Gamium 참고 경계

| 반영할 구조 | 적용 방법 |
|---|---|
| README 진입 구조 | PRIZM 문제·현재 범위·Quickstart·문서 링크를 앞에서 찾게 한다. |
| CONTRIBUTING | 외부 기여 접수를 시작할 때 실제 개발·test·license·보안·문서 절차를 PRIZM 명령으로 새로 쓴다. |
| LICENSE와 고지 | 표준 license 원문과 PRIZM 감사 결과만 사용한다. |
| Issue Form·PR Template | 외부 Issue·PR 운영을 열 때 PRIZM 상태·owner·migration·`NOT_RUN` Gate에 맞게 새로 설계한다. |
| 문서 Quickstart | clean clone에서 재현할 수 있는 최소 경로와 알려진 blocker를 둔다. |
| 링크 검증 | local hard fail과 외부 network 오류 보고를 분리한다. |
| release 정책 | tag·commit·SBOM·NOTICE·지원 범위와 rollback evidence를 연결한다. |

다음은 도입하지 않는다.

- 현재 규모에 필요하지 않은 Docusaurus 문서 사이트
- Unity 중심 다중 플랫폼 CI
- `latest` 같은 floating dependency를 재현성 근거로 사용하는 방식
- 자기 자신만 지정한 CODEOWNERS로 review가 생긴 것처럼 보이는 방식
- 형식적인 `LGTM` 또는 Agent 감사를 제3자 review·community evidence로 주장

참고 저장소의 문구·template·code를 그대로 복사하지 않는다. 구조 아이디어만
참고하고 PRIZM의 source·명령·위험에 맞게 새로 작성한다.

## 단계별 검증과 `NOT_RUN` 기록

### IMPLEMENT

- 각 task는 변경 파일·source URL·component 상태와 중단 이유를 기록한다.
- dependency graph와 image/model metadata를 조사할 때 사용한 OS, Java,
  Node/npm, Gradle, Docker, PostgreSQL·pgvector, Ollama의 실제 사용 여부를
  구분한다.
- PRZ-002 구현·감사 자체에서는 OpenSQL·OpenProxy·OpenHA를 호출하지 않고
  `NOT_RUN_IN_THIS_TASK`로 기록한다. 저장소 전체 현재 상태는 PRZ-003의 실제
  OpenSQL single-node SQL Gate `PASS`와 OpenProxy·OpenHA `NOT_RUN`을 분리한다.

### VERIFY

- Markdown local/external link, code fence, trailing whitespace와
  `git diff --check`
- 전체 unit test, PostgreSQL integration test, frontend lint/build,
  `docker compose config`는 실제 변경 영향에 따라 실행한다.
- 현재 source-only SBOM/license verification과 clean checkout 대조를
  실행한다. fat JAR·frontend bundle·runtime image 내용 대조는 해당 산출물을
  실제 배포 범위에 넣을 때의 별도 release Gate다.
- 필요한 Docker·PostgreSQL·pgvector·Ollama를 실제로 사용했는지, 미실행
  test와 이유를 evidence에 남긴다. 환경 부재는 PASS가 아니라 `NOT_RUN`이다.

### AUDIT

- source register와 실제 공식 artifact hash 재검증
- audit inventory와 lockfile/resolved graph/image/model digest 재대조
- root license·NOTICE·SBOM·AI 명세 간 일치 검사
- public repository에서 secret·사용자 문서·local path·model cache 부재 확인
- GitHub visibility `PUBLIC`, clean clone의 전체 빌드 source와 제출
  commit·tree hash 일치 확인
- 존재하는 GitHub PR·CI 링크의 실제 동작과 권한 확인. deferred 신고·Issue
  경로는 존재하는 것처럼 링크하지 않았는지와 재개 조건을 확인

## 평가 evidence Gate

- `EVAL-R1-02`가 primary다. outgoing license 승인, 전체 감사, 실제 NOTICE,
  SBOM·AI 모델 명세와 현재 source-only 배포 범위의 blocking unknown 0건이
  evidence다. deferred 기여·보안 경로는 구현된 evidence로 계산하지 않는다.
- `EVAL-R1-03`은 secondary다. source register, SBOM·AI 명세, Quickstart,
  재현 명령과 현재/계획/`NOT_RUN` 구분이 evidence다.
- `EVAL-R1-05`는 secondary다. 실제 future Issue·PR·CI·merge가 생겼을 때만
  관리 evidence로 연결한다. template, 계획, Agent 감사만으로 올리지 않는다.
- 공식 점수를 예측하지 않는다. 각 Gate는 경로, 명령, 결과, commit, 환경,
  검증일처럼 다시 확인 가능한 증거만 허용한다.

## 예상 변경 파일과 PLAN 실제 변경 파일

### 후속 IMPLEMENT에서 예상하는 파일

- `docs/contest/2026-source-register.md`
- `docs/contest/2026-license-audit.md`
- `docs/contest/2026-sbom.md`
- `docs/contest/2026-ai-model-provenance.md`
- `LICENSE`, `NOTICE`
- 재개 조건 충족 뒤 `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  `SUPPORT.md`, `MAINTAINERS.md`
- 재개 조건 충족 뒤 `.github/ISSUE_TEMPLATE/bug_report.yml`,
  `.github/ISSUE_TEMPLATE/feature_request.yml`,
  `.github/ISSUE_TEMPLATE/documentation.yml`,
  `.github/ISSUE_TEMPLATE/config.yml`, `.github/pull_request_template.md`
- `README.md`, `docs/README.md`, `docs/development-log.md`
- `docs/contest/2026-requirements-traceability.md`
- `docs/contest/2026-tmaxtibero-plan.md`, `docs/roadmap.md`
- `specs/PRZ-002-open-source-readiness/spec.md`
- `specs/PRZ-002-open-source-readiness/tasks.md`
- `specs/PRZ-002-open-source-readiness/evidence.md`
- `specs/README.md`
- `.github/workflows/ci.yml` 또는 감사 후 승인된 별도 OSS 검증 workflow
- 감사 후 승인된 `scripts/oss/` 아래의 재현 가능한 local verification script
- 도구 도입에 꼭 필요한 경우에만 `build.gradle`, `frontend/package.json`,
  `frontend/package-lock.json`, `.gitignore`; production 동작 변경은 제외

파일명과 도구는 감사·사용자 승인 후 확정한다. 존재하지 않는 future 파일을
현재 구현으로 링크하지 않는다.

### 이번 PLAN에서 실제 수정 가능한 파일

- `specs/PRZ-002-open-source-readiness/plan.md`
- `specs/PRZ-002-open-source-readiness/tasks.md`
- `docs/development-log.md`

`AGENTS.md`를 포함한 다른 파일은 수정·stage하지 않는다.

## 위험·중단 조건·사용자 결정

| 위험 또는 결정 | 처리 |
|---|---|
| 외부 code·asset·fixture provenance 불명 | `UNKNOWN`으로 두고 release 통합 중단 |
| dependency/model license 충돌 | 대체·제거·별도 허가를 비교하고 해결 전 `BLOCKED` |
| 모델 revision·Ollama manifest 식별 실패 | model Gate 실패, PostgreSQL test 성공으로 대체 금지 |
| 배포 산출물 범위 미확정 | NOTICE·SBOM 확정 중단, 사용자에게 제출·배포 형태 결정 요청 |
| outgoing license 미승인 | `LICENSE`·`NOTICE` 생성 금지 |
| 비공개 security channel 미확정 | 현재 source-only P0에서는 G-02·T-06을 `DEFERRED`; 첫 지원 release·외부 배포 또는 외부 기여 접수 전에 재개하고, 그때까지 `SECURITY.md` 게시 금지 |
| 감사 도구의 license·출력이 불명 | 도구 채택 금지, 수동 inventory 유지 |
| 공식 자료 변경·hash 불일치 | source register를 `CONFLICT`로 바꾸고 원인 확인 |
| 기존 dirty user change와 branch 전환 충돌 | reset·stash로 우회하지 않고 중단 |
| 실제 reviewer 부재 | `REVIEW_NOT_AVAILABLE_SOLO`, review evidence 주장 금지 |
| 공개 repository에 민감정보 발견 | 즉시 integration 중단, 노출 대응과 history 영향 별도 판단 |

사용자가 IMPLEMENT 전에 결정해야 할 항목은 다음과 같다.

1. 감사 결과를 본 뒤 MIT 또는 Apache-2.0 outgoing license 선택
2. 첫 지원 release·외부 배포 또는 외부 기여 접수 전 GitHub Private
   Vulnerability Reporting 또는 검증 가능한 전용 신고 채널
3. 실제 제출·배포 산출물: source, JAR, `dist`, image, model 포함 여부
4. 감사된 SBOM/license 도구의 변경 범위
5. GitHub Issue·branch·PR 생성 권한과 가능한 실제 reviewer

## PLAN 종료 조건

- [x] 정확한 감사 범위와 배포 경계를 정의했다.
- [x] 10단계 IMPLEMENT 순서와 각 완료 조건을 정의했다.
- [x] outgoing license와 SECURITY 신고 채널을 사용자 승인 Gate로 분리했다.
- [x] Gamium 참고·제외 경계와 평가 evidence 원칙을 정했다.
- [x] 예상 IMPLEMENT 파일과 PLAN 허용 파일을 분리했다.
- [x] 위험·중단 조건·단계별 `NOT_RUN` 기록 방식을 정의했다.

이 계획의 현재 source-only IMPLEMENT·VERIFY·AUDIT는 완료됐다. INTEGRATE는
T-10 교정 commit·push와 실제 commit·tree 기록을 기다린다. governance·template은
기록된 재개 조건까지 `DEFERRED`이며, binary·image·model 배포와 제출 직전
snapshot은 별도 후속 Gate에서 다시 감사한다.
