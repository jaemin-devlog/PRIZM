# PRZ-017 — 채용공고 항목별 Career Evidence V1 Evidence

## 현재 판정

- 상태: `VERIFIED`
- 기준선: `d44f30eb4346353c4363d559be478024f191a878`
- 현재 source: `94715cf`
- GitHub 통합: [PR #53](https://github.com/jaemin-devlog/PRIZM/pull/53),
  `main` merge commit `b78ec42e8cd06ebe001dd02fbaf2a3abd0e15d22`
- VERIFY: 2026-08-26 frontend focused 33/33·전체 80/80, typecheck·lint·build와 backend 전체
  89 suites·627 tests가 실패·오류 0, 기존 조건부 test 20건 skip으로 통과. integration
  `ABORTED`. 2026-08-27 최종 segmentation focused 50/50·compile과 인증 browser mixed bullet
  10→11, Evidence 2개 항목, PDF 2페이지 target `PASS`
- AUDIT: PRZ-016 Search Production·PRZ-009 보호 소스 추가 diff 0, 보호 파일 hash 변화 0,
  staged 0, stash 2개와 기존 untracked 보존, `git diff --check` 및 Markdown 135개·local link
  584개 검사 `PASS`
- commit/push: 최종 검증 source `94715cf`까지 원격 branch에 반영됨
- PR/merge: PR #53 merged, `main` merge commit `b78ec42`

`84f9191` 당시에는 실제 ATAD 공고의 noise 제거, 복합 질의의 직접 identifier Evidence,
긴 원문 후보, focus, 같은 제목 문서 식별과 PDF page 이동을 인증 browser에서 확인했다.
최종 통합 candidate에는 segmentation 구조 보정과 단독 identifier exact 우선·semantic 후보 보존이
추가됐다. 마지막 mixed-bullet false negative는 실제 indentation 기반 목록 깊이로 수정했고 focused
test·compile·인증 browser Gate를 통과했다. integration `ABORTED`는 최종 결함 수정 범위의 필수
Gate가 아니며 소급해 `PASS`로 바꾸지 않는다.

## 2026-08-27 mixed bullet 마지막 Gate

- 원인은 bullet 종류를 목록 깊이로 사용해 같은 indentation의 `•`를 parent, 뒤의 `-`를 child로
  오인한 것이었다. 목록 깊이를 실제 선행 indentation으로 계산하도록 수정했다.
- 같은 indentation의 mixed bullet 3개가 모두 sibling leaf로 남고, 실제로 더 들여쓴 child는 기존
  grouping parent 아래에 남는 회귀를 한 테스트 묶음으로 확인했다.
- segmentation service 44개와 controller 6개, 총 50개가 실패·오류·skip 없이 통과했고
  production/test compile과 backend Docker `bootJar`도 통과했다.
- 인증 browser에서 동일 공고의 mixed `•`/`-` selectable이 10개에서 기존 기준과 같은 11개로
  복구됐다. 첫 Java 업무 문장은 heading이 아닌 checkbox였고 metadata 제외와 원래 순서를 유지했다.
- Java·Docker 2개 항목은 각각 Evidence 3건을 표시했고, 올바른 PDF의
  `#page=2&zoom=page-width` target을 열었다.
- PRZ-016 Search Production과 PRZ-009 추가 diff 0, staged 0, stash 2개와 기존 unrelated
  dirty/untracked 보존, `git diff --check` `PASS`를 확인했다.

## 2026-08-26 개인 문서 원문 일치 안정화 (historical checkpoint)

### 제품 계약

PRZ-017은 사용자가 관리하는 이력서·포트폴리오에서 채용공고 항목과 관련된 원문을 찾아 문서와
PDF page 또는 TXT 상세 위치를 연결한다. 원문에 `Java`가 있으면 그 내용을 Evidence로 표시하는
것으로 충분하며, 사용자가 그 경험을 실제로 했는지 또는 채용 요구를 충족하는지는 판정하지 않는다.
이 원칙은 특정 개인 문서뿐 아니라 처음 등록되는 다른 사용자의 문서에도 동일하게 적용한다.

### 구현 근거

- segmentation은 반복 확인된 지원서 field, 보상 범위, 기술 목록 안내문과 구조용 하위 제목만
  구조 신호로 제외한다. 실제 기술 목록과 업무·자격 문장은 보존한다.
- query planner의 기존 원문+명시적 compound variant 계약은 그대로다. 공백 없는 단독 query는
  독립 token 직접 일치 후보를 먼저 배치하되 original semantic 후보를 삭제하지 않는다.
- 임의 식별자 `ZephyrDB`는 실제 token 일치 원문을 먼저 표시하고, 자연 역량 `Ownership`은 의미상
  관련된 원문을 계속 표시하는 회귀로 기술명 사전 없이 exact 우선과 recall을 함께 확인한다.
- PRZ-016의 ranking·threshold·fallback·rescue·embedding·localization은 수정하지 않았다.
- 결과와 empty 문구는 Search 후보 및 원문 위치만 설명하며 진위·충족·적합도 판정을 하지 않는다.

### 현재 검증

| 검증 | 결과 |
|---|---|
| PRZ-017 focused frontend | `PASS` — 33/33 |
| Frontend 전체 unit·typecheck·lint·build | `PASS` — 80/80, 정적 검사와 production build 통과 |
| Backend segmentation focused | `PASS` — 43/43 |
| Backend 전체 unit | `PASS` — 89 suites, 627 tests, 실패·오류 0, 기존 조건부 test 20건 skip |
| Backend integration | `ABORTED` — 실행 중 사용자 요청으로 중단 |
| 최신 source 실제 인증 browser | `NEEDS_ADJUSTMENT` — 동일 bullet 핵심 흐름은 통과, 혼합 bullet 첫 업무 문장 1개 오인 |
| PDF page target | `PASS` — 자동 검증으로 올바른 문서의 2페이지 blob target 확인 |
| PDF 실제 표시 | `USER_CONFIRMED` — 사용자가 실제 화면에서 정상 표시를 확인 |
| TXT 문서 이동 | `NOT_RUN` — 등록된 TXT fixture 없음 |
| Browser console·API | `PASS` — console error 0, 관련 API 모두 200, 반복 loop 없음 |
| Docker current-source build | `PASS` — frontend production build, backend `bootJar`, backend health `UP` |
| PayPay India·Lean In 최신 재평가 | `NOT_RUN` |

과거 PayPay India·Lean In holdout의 실패 수치는 당시 source의 역사적 결과로 아래에 그대로 보존한다.
이번 안정화 결과로 소급해 `PASS`로 바꾸지 않는다.

### 최신 인증 browser Gate

- 현재 working tree로 backend·frontend 이미지를 다시 빌드하고 기존 DB·문서 volume은 유지했다.
- 동일한 `-` bullet 공고는 업무 3개·자격 3개·우대 3개·기술요건 2개, 총 11개를 원래 순서로
  표시했다. 복지·전형·근무·접수·채용 인원은 selectable 에서 제외됐고 heading과 bullet prefix는
  checkbox에 포함되지 않았다.
- 전체 해제 시 `11개 중 0개 선택`과 검색 비활성화, 전체 선택 시 11개 선택을 확인했다.
- 5개 항목을 검색해 `검색 후보 있음` 3개, `검색된 후보 없음` 2개를 표시했다. 선택하지 않은
  항목의 결과 그룹은 만들지 않았고, empty state는 경험 부재나 요건 불충족으로 판정하지 않았다.
- 주변 내용은 extractive 원문으로 열렸고, PDF는 올바른 문서의 `#page=2&zoom=page-width`로
  연결됐다. PDF 실표시는 사용자가 확인했다. 등록 문서가 PDF 1개뿐이어서 TXT 이동은 `NOT_RUN`이다.
- 현재 로그인 계정의 등록 문서 1개에서만 결과가 표시됐고 owner/ACTIVE 이상 징후는 없었다.
  이번 Gate에서 두 사용자·비활성 버전 fixture를 새로 만들지는 않았으므로 기존 격리 회귀를
  다시 실행한 결과로 확대하지 않는다.
- 같은 들여쓰기에서 `•` 첫 항목 뒤에 `-` 항목이 이어진 공고는 첫 업무 문장을 grouping parent로
  해석해 selectable이 11개에서 10개로 줄었다. 기대한 checkbox 대신 heading으로 표시되므로
  browser Gate를 `PASS`로 올리지 않는다.
- 이번 문서·browser 작업 중 Production source와 test hash는 작업 시작 기준과 같았으며 코드 수정은
  없었다. current-source 확인을 위해 Docker backend·frontend 컨테이너만 다시 만들었고 DB와 저장
  volume은 유지했다.

## 2026-08-26 desktop 동기화(역사적 checkpoint)

- 원격 commit `84f9191`을 merge commit 없이 fast-forward했고 local HEAD와 원격 branch가 일치한다.
- 이 PC에서 frontend 전체 unit 77/77, typecheck·lint·build와 backend segmentation
  service/controller focused 42/42를 재실행해 모두 통과했다.
- 최종 mobile viewport는 사용자가 이번 checkpoint에 필요하지 않다고 결정해 `NOT_RUN`으로
  유지한다. 노트북에서 완료한 인증 desktop browser 결과를 mobile 검증으로 확대하지 않는다.
- 당시 tracked source는 깨끗했고 staged는 0이었다. 기존 untracked 5개와 stash 2개의 내용은
  동기화 전후 그대로 보존했다. 이는 이후 미커밋 working tree 상태를 뜻하지 않는다.

## 2026-08-26 주변 내용 polish (historical checkpoint)

실제 인증 화면에서 `주변 내용 보기`가 summary 버튼의 flex 너비를 기준으로 약 283px까지
수축하고, 일부 결과는 현재 미리보기를 포함하지 않은 문서 앞부분을 문맥으로 표시했다. PRZ-017
presentation에서 미리보기를 포함하면서 추가 원문을 제공하는 문맥만 유효하게 판정하고, 열린
details는 Evidence 본문 폭을 사용하도록 수정했다. Search response, ranking, 공통 Search
presentation과 후보 dedup은 변경하지 않았다.

| 검증 | 결과 |
|---|---|
| Focused frontend unit/component | `PASS` — 30/30 |
| Frontend 전체 unit | `PASS` — 77/77 |
| Frontend typecheck·lint·production build | `PASS` — Vite 37 modules |
| Docker frontend rebuild | `PASS` — 최신 source `npm run build` 완료 |
| Docker runtime | `PASS` — backend health `UP`, frontend HTTP 200, DB healthy |
| 변경 전 실제 browser | 14개 행에 action 14개, 유효 문맥 6개, 불필요·불일치 8개 |
| 변경 후 실제 browser | action 6개, 미리보기 불포함·중복 문맥 0 |
| 열린 reader 폭 | `PASS` — details/reader/본문이 모두 672px; 기존 283px 수축 제거 |
| 후보·중복 회귀 | `PASS` — 항목별 2/5/5/2 유지, exact duplicate 0 |

유효한 문맥이 없는 Java·Linux 후보에서는 action을 숨겼고, Spring 1개와 MySQL 5개에서는
미리보기부터 이어지는 추가 extractive 원문만 표시했다. PDF/TXT 원문 이동 action은 모든 행에
그대로 남아 있다.

## 2026-08-26 결과 표시 polish (historical checkpoint)

### 구현

- 과도하게 긴 Evidence highlight는 기존 metadata-free extractive context가 더 짧을 때 그 문맥을
  사용하고, blockquote는 최대 5줄 preview로 제한했다. 주변 내용과 PDF/TXT 원문 action은 유지한다.
- 결과 제목의 programmatic focus를 PRIZM focus token으로 표시한다.
- document group에 versionNo를 항상 표시하고, 같은 제목 group은 결과 순서대로
  `같은 제목 문서 n/N`을 표시한다. 내부 document ID는 노출하지 않는다.

### 자동 검증

| 검증 | 결과 |
|---|---|
| Focused frontend unit/component | `PASS` — 29/29 |
| Frontend 전체 unit | `PASS` — 76/76 |
| Frontend typecheck | `PASS` |
| Frontend lint | `PASS` |
| Frontend production build | `PASS` — Vite 37 modules |
| Docker frontend build/recreate | `PASS` — 최신 source로 `npm run build` 완료 |
| Docker runtime | `PASS` — backend health `UP`, frontend HTTP 200, DB healthy, demo bootstrap `false` |
| PRZ-016 Search·embedding·Flyway·dependency diff | `PASS` — 변경 0 |
| `git diff --check` | `PASS` |

backend focused test 재실행은 기존 `searchEvaluation` source-set compile 오류가 test 실행 전에
발생해 `BLOCKED_BY_BASELINE`으로 기록한다. 이번 polish의 backend source 변경은 0이며 직전 Phase의
focused service/controller 결과와 Docker backend 실행 상태는 유지된다.

### 실제 인증 browser Gate와 중복 감사

| 항목 | 결과 |
|---|---|
| 긴 Java 후보 | `PASS` — 2건 모두 35자·50px preview, email/phone/URL 노출 0 |
| 레이아웃 | `PASS` — desktop 가로 overflow 0, 주변 내용 action 2개 유지 |
| 제목 focus | `PASS` — programmatic focus와 PRIZM blue 3px outline 확인 |
| exact duplicate | `PASS` — 같은 document/version/source/display text 중복 0 |
| 같은 문장 반복 | Java 1, Spring 2, Linux 1 — 모두 서로 다른 document 사이 반복 |
| 같은 문서의 다른 source 반복 | `PASS` — 0 |
| 같은 제목 document group | `PASS` — version과 `같은 제목 문서 1/2`, `2/2`로 구분 |
| browser console | `PASS` — 오류 0 |
| 최종 patch mobile viewport | `NOT_RUN` — in-app viewport 전환 불가; 직전 mobile Gate와 responsive CSS만 재감사 |

서로 다른 등록 문서의 같은 문장은 독립 Evidence source이므로 합치지 않는다. 대신 같은 제목 문서도
사용자가 출처를 구별할 수 있게 표시한다. 화면상 완전 중복이나 사용을 막는 blocking finding은 없다.
사용자는 일반 브라우저에서 PDF가 정상 표시됨을 확인했으며, 앱 내 브라우저 renderer 차이는 이번
수정 대상에서 제외했다. commit·push·PR·merge는 실행하지 않았다.

## 2026-08-24 Search 후보 신뢰성 보정 (historical checkpoint)

### 구현 범위

- `A, B, C 등 ...`·`A, B, C 등의 ...`를 원문 우선과 최대 5개 variant 계약 안에서만
  보수적으로 확장한다. 일반 쉼표 문장, 숫자 comma, `CI/CD`, `OAuth2/JWT`, 경로는 원문-only다.
- PRZ-017 후보에 matched query provenance와 display query를 보존한다. 동일 chunk의 첫 raw
  result와 original-first 순서는 유지하고 이후 query provenance만 합친다.
- decomposition으로 만든 짧은 ASCII identifier/phrase에만 direct Evidence guard를 적용한다.
  `Git`은 `GitHub`만으로 통과하지 않으며 `Docker Compose`, `Java 17`, `C++`, `C#`, `Node.js`는
  독립 identifier로 보존한다.
- matched display query를 PRZ-017 extractive 표시 anchor로 사용한다. 공통 PRZ-016 Search
  presentation, SearchService, threshold, ranking, rescue, embedding, SQL은 수정하지 않는다.
- 결과 UI를 `검색 후보 있음`, `검색된 후보 없음`, `확인할 원문 후보`로 낮추고 요구사항 충족
  판정이 아니라는 안내를 표시한다. PDF/TXT callback과 원래 항목 번호·순서는 유지한다.

### 자동 검증

| 검증 | 결과 |
|---|---|
| Focused frontend unit/component | `PASS` — 27/27 |
| Frontend 전체 unit | `PASS` — 74/74 |
| Frontend typecheck | `PASS` |
| Frontend lint | `PASS` |
| Frontend production build | `PASS` — Vite 37 modules, `index-Bkub8t-x.js` |
| Docker Compose config | `PASS` |
| Docker runtime | `PASS` — frontend HTTP 200, backend health `UP`, DB healthy |
| PRZ-016 Search·embedding·Flyway·dependency diff | `PASS` — 변경 0 |
| `git diff --check` | `PASS` |

표준 Docker rebuild는 Docker Desktop DNS가 registry metadata를 해석하지 못해 완료되지 않았다.
대신 동일 old frontend image에 검증된 `frontend/dist`를 network-free overlay하고 frontend만
재생성했다. 실행 중 index는 `index-Bkub8t-x.js`와 `index-C2rDrxOc.css`를 제공한다. backend와
DB는 재생성하거나 변경하지 않았다.

### 실제 인증 browser Gate

| 항목 | 결과 |
|---|---|
| ATAD segmentation | `PASS` — selectable 18, 18개 기본 선택 |
| 결과 상태 | `PASS` — 검색 후보 있음 10, 검색된 후보 없음 8 |
| Java 항목 | `PASS` — 후보 3건 모두 표시 원문에 `Java` 직접 포함, MoneyWay 단독 문장 0 |
| Docker enumeration | `PASS` — 후보 2건, `Docker`와 `Docker Compose` 직접 포함 |
| 후보 UI·비판정 안내 | `PASS` — 결과·empty 상태 모두 후보 표현과 불충족 비판정 안내 확인 |
| PDF callback | `PASS` — 2페이지 blob iframe과 `#page=2&zoom=page-width` 연결 |
| 브라우저 console | `PASS` — 오류 0 |
| 일반 Chrome PDF 시각 확인 | `NOT_RUN` — 별도 Chrome 인증 세션 없이 앱 내 브라우저만 사용 |

앱 내 브라우저의 PDF iframe은 blob URL과 page target은 유효하지만 화면이 흰색이었다. 이번
Phase는 PDF renderer를 수정하지 않으므로 PRZ-017 Search 기능의 실패로 확대하지 않고, 일반
Chrome 사람이 보는 시각 결과는 `NOT_RUN`으로 분리한다.

## 2026-08-24 실사용 ATAD 보정 (historical checkpoint)

### 범위와 기준

- 작업 시작 source는 `8be904d3442cb52151fce1d9c29f4608c689d9d4`, 비교 기준
  `origin/main`은 `d44f30eb4346353c4363d559be478024f191a878`이다.
- 이번 보정은 PRZ-017 segmentation service/test, PRZ-017 frontend query planner/test와
  Spec·Plan·Tasks·Evidence에만 한정했다. commit·push·PR·merge는 실행하지 않는다.
- `PRZ-016` Search Production, embedding, auth, Flyway migration, dependency manifest는
  수정하지 않는다.

### Before / After

| 항목 | Before | After |
|---|---:|---:|
| ATAD selectable 항목 | 45 | 18 |
| 소개 문장 | 포함 | 0 |
| `►/▶` 구조 heading | 포함 | 0 |
| child가 있는 `•/●` grouping parent | 포함 | 0 |
| 혜택·복지 항목 | 포함 | 0 |
| 자격요건·우대사항 leaf | 혼재 | 12개 보존 |

After API 결과는 업무 leaf 6개, 자격요건 7개, 우대사항 5개를 원문 순서대로 반환했다.
`itemId`는 1부터 18까지 연속이며, explicit list item은 문장부호가 있어도 하나의 atomic item으로
유지됐다. 각 item은 500자 상한을 계속 적용한다.

### Compound query 보정

- 입력: `Kotlin, TypeScript, Python, Go, Java 중 1개 이상 개발 가능자`
- 변경 전: suffix가 명시적 alternative separator로 인식되지 않아 원문 한 번만 검색했다.
- 변경 후: 원문, `Kotlin`, `TypeScript`, `Python`, `Go`, `Java` 순서로 검색한다. 기존 규칙대로
  variant는 최대 5개, 원문 우선, 동일 chunk dedup, 최종 Top 5를 유지한다.
- 실제 인증 Search API 결과는 원문/Kotlin/TypeScript/Python/Go가 각각 0건, Java가 5건이었다.
  병합 결과는 5건이고 5건 모두 Java 결과와 일치했다.
- 일반 쉼표 문장, `1,000`, `CI/CD`, `src/main/java`는 분해하지 않는 negative test를 유지한다.

### 검증 결과

| 검증 | 결과 |
|---|---|
| ATAD 변경 후 segmentation API | `PASS` — selectable 18, noise 0, 순서·section·1-based ID 일치 |
| Backend focused | `PASS` — service 23/23, controller 6/6, 합계 29/29 |
| Backend 전체 unit | `BLOCKED_BY_BASELINE` — `compileSearchEvaluationJava`가 기존 `ClaimSupportDecision` 및 `candidateClaimSupport` 참조 6건으로 실패 |
| Frontend unit | `PASS` — 70/70 |
| Frontend typecheck/lint/build | 모두 `PASS` — production build 37 modules |
| Docker 최신 source rebuild | `PASS` — backend `bootJar`, frontend build, backend health `UP` |
| 인증 Java compound API | `PASS` — 6 query, Java 5건, merged Top 5의 Java overlap 5 |
| 실제 browser Gate | `BLOCKED_BY_AUTH_CONFIRMATION` — 로그인 화면까지 확인했으나 `.env` 자격증명 입력 승인이 없어 인증 동작을 실행하지 않음 |

전체 unit 장애 파일은 `src/searchEvaluation/java/com/prizm/search/evaluation/` 아래 두 기존 평가
테스트이며 이번 diff에 없다. 변경 범위의 source compile, focused test와 Docker runtime은 통과했다.
브라우저 Gate를 `PASS`로 대체하지 않으며, 실제 인증 API 결과와 분리해 기록한다.

## 이전 checkpoint integration (historical)

- Production Java 주석 정비는 commit `6cc4726`, PRZ-017 구현·test·Spec은 source commit
  `de98bcf`에 기록했다.
- 두 commit과 당시 통합 기록은 `PRZ-017-job-evidence-v1` 원격 branch에 push했다. PR과 merge는
  만들거나 실행하지 않았다. 이후 실사용 보정은 commit `84f9191`에 기록해 같은 원격 branch에
  push했다.
- 인증 browser Gate의 `BLOCKED_BY_AUTH_ENVIRONMENT`, 기존 44개 전체 공고와 TXT 이동
  `NOT_RUN`, 전체 상태 `IN_PROGRESS`를 그대로 남겨 노트북 환경에서 이어서 검증할 수 있게 한다.

## 초기 구현 checkpoint ORIENT 근거 (historical)

- 시작 branch `PRZ-017-job-evidence-v1`, `HEAD`와 `origin/main`은 모두 기준선
  `d44f30e`였다.
- stash 2개와 unrelated 대회 문서, P16 PDF untracked 파일을 목록으로만 확인하고
  변경하지 않았다.
- PRZ-017 Production source, API, test와 persistence는 기준선에 존재하지 않았다.
- V9에는 문서 metadata용 `JOB_POSTING` DocumentType이 있으나 붙여넣은 채용공고나 분리
  항목을 저장하는 schema는 없다. 최신 migration은 V16 Document Tag다.
- `src/main/resources/application.yml`은 chat model을 `none`으로 두고 Ollama `bge-m3`를
  embedding에만 사용한다.
- Qwen/OpenAI judge는 `scripts/evaluation`, `src/searchEvaluation`과 PRZ-016 연구 Evidence에
  남은 평가 자료이며 Production 경로가 아니다. 이 자료는 삭제하지 않는다.

## 기존 구조 분류 (historical checkpoint)

| 분류 | 대상 |
|---|---|
| 그대로 재사용 | Career Evidence Search API/service/response, owner/ACTIVE SQL, snippet/context, authenticated original endpoint, PDF Blob viewer, TXT document detail |
| 수정 후 재사용 | Career Vault shell, Evidence group presentation, Security USER matcher, 문서 종류 metadata mapping |
| 제거 | 현재 PRZ-017 Production에는 제거할 Qwen/LLM/fit 코드가 없음 |
| 신규 구현 | stateless segmentation, 입력·선택 UI, 선택 항목별 Search orchestration과 그룹 상태 |

## 요구사항 추적 (historical checkpoint)

| 요구사항 | source/test | 당시 결과 |
|---|---|---|
| R1 deterministic segmentation | `jobposting` service/controller, service 21개·controller 6개 test | `PASS` |
| R2 사용자 선택 | 입력 전용 `JobEvidencePanel`, 접근 가능한 selection modal과 component test | 자동 test `PASS`, browser `BLOCKED_BY_AUTH_ENVIRONMENT` |
| R3 기존 Search 소비 | 단순 item 원문 1회, compound 원문+최대 5 variant, 동시성 3, original-first dedup·Top 5 test | `PASS` |
| R4 그룹 Evidence·중립 상태 | 전용 route의 상태별 requirement rail, document group, Evidence row와 result/empty/error/retry test | 자동 test `PASS`, browser `BLOCKED_BY_AUTH_ENVIRONMENT` |
| R5 PDF/TXT 이동 | 기존 Evidence page target/detail callback wiring test | 자동 test `PASS`, 브라우저 `PENDING` |
| R6 인증·owner/ACTIVE | USER 200·무인증 401·admin 403와 전체 PostgreSQL integration | `PASS` |
| migration·dependency·Search diff 0 | origin/main 기준 final path diff·독립 감사 | `PASS` |

## 초기 구현 checkpoint 검증 결과 (historical)

| 검증 | 결과 |
|---|---|
| Backend focused | `PASS` — service 21/21, controller 6/6 |
| Backend 전체 unit | `PASS` — 605 tests, failure 0, error 0, 기존 skip 20 |
| PostgreSQL integration | `PASS` — 117 tests, failure 0, error 0, 기존 skip 8 |
| Frontend unit | `PASS` — 69/69 |
| Frontend typecheck/lint/build | 모두 `PASS`, production build 37 modules |
| 결과 상태 탭 | `PASS` — 기록 있음·없음과 조건부 확인 필요 count, 탭 전환, 원래 순서·번호, error/empty 구분 component test |
| 새 modal·결과 workspace browser Gate | `BLOCKED_BY_AUTH_ENVIRONMENT` — 기존 JWT가 segmentation 요청에서 만료돼 로그인 화면으로 이동; 인증 우회·source 변경 없음 |
| Docker rebuild | `PASS` — 기존 DB/volume과 backend 유지, frontend만 최신 source로 rebuild; `index-Db_oEg6I.js` 제공 |
| 최신 Docker 결과 상태 탭 browser Gate | `BLOCKED_BY_AUTH_ENVIRONMENT` — 연결 가능한 사용자 탭이 로그인 화면이고 별도 인증 브라우저는 없어 실제 결과 데이터로 탭 전환을 재검증하지 못함 |
| 인증 compound/PDF targeted Gate | `PASS` — owner 5, document 5, ACTIVE version 6에서 Docker 0→3건, Git/협업 0→0건, PDF 1페이지 viewer 확인 |
| 기존 44개 전체 원문·TXT 이동 재검증 | `NOT_RUN` — 이번 Phase는 compound query composition에 한정 |
| Search Production final diff | `PASS` — Search/embedding/application 설정 변경 0 |
| Migration/dependency final diff | `PASS` — Flyway·lockfile·runtime dependency 변경 0 |
| 최종 `git diff --check` | `PASS` — whitespace 오류 0 |
| Markdown 검사 | `PASS` — tracked Markdown 135개, local link 584개 |
| 독립 AUDIT | `PASS` — 초기 401/fan-out/heading/bullet/분할 finding 수정 뒤 blocking 0 |

## 선택 modal·전용 결과 workspace 보정 근거 (historical checkpoint)

- segmentation 성공 뒤 항목은 입력 화면 아래에 누적하지 않고 section별 modal에서 선택한다.
  heading은 그룹 제목만 담당하고 checkbox는 child 항목에만 있다. 전체 선택·해제, 선택 수와
  0개 Search 방지는 기존 계약을 유지한다.
- 검색 시작 시 `/career-vault/job-evidence/results`로 이동해 loading group부터 표시한다.
  왼쪽 requirement rail은 원래 선택 순서와 표시 건수를 유지하고, 오른쪽은 활성 항목 하나를
  `document/version → Evidence row`로 묶어 문서 metadata 반복을 줄인다.
- 화면상 중복은 동일 문서·버전·Evidence source 위치·공백 정규화 표시 원문이 모두 같은
  경우만 한 행으로 정리한다. Search 결과 배열, chunk identity, Top 5와 순위는 바꾸지 않으며
  같은 page의 서로 다른 원문과 다른 page의 원문은 보존한다.
- 결과 state 없이 results URL을 직접 열면 입력 URL을 history `replace`로 복귀시켜 Back loop를
  막는다. 일반 입력↔결과 이동은 `push`를 유지해 같은 page controller의 입력·선택 state를
  보존한다. 결과 route 최초 mount에서는 결과 제목으로 focus를 옮긴다.
- 독립 감사에서 최초 direct-results history loop 1건을 발견해 위 `replace` 경로로 수정했고,
  focus 전환도 보완했다. 재감사 결과 blocking finding은 0건이다.
- 실제 브라우저에서는 최신 입력 전용 화면 렌더링까지 확인했으나 segmentation API 요청이
  만료된 인증으로 401 처리되어 로그인 화면으로 이동했다. 새 modal, 결과 route, requirement
  전환과 PDF page 동작은 이번 UI Phase에서 실제 브라우저로 검증하지 못했다.

## 결과 상태 탭 보정 근거 (historical checkpoint)

- requirement rail 상단에 `기록 있음`과 `기록 없음` 탭을 항상 표시하고, loading/error가
  있을 때만 `확인 필요` 탭을 표시한다. 각 숫자는 Evidence 행이 아니라 requirement 수다.
- Evidence가 한 건 이상이면 `기록 있음`, 완료됐지만 0건이면 `기록 없음`, loading/error는
  `확인 필요`로 분류해 요청 실패를 Evidence 부재로 오해하지 않게 했다.
- 탭 안에서도 전체 선택 배열의 원래 순서와 번호를 유지한다. 탭을 전환하면 활성 표시 항목만
  바뀌며 Search orchestration과 결과 배열은 다시 실행하거나 변경하지 않는다.
- focused component 22건과 frontend 전체 unit 69건이 통과했고 typecheck·ESLint·37-module
  production build도 통과했다. frontend 컨테이너는 DB와 backend를 유지한 채 최신 source로
  교체됐다.
- 연결 가능한 브라우저 탭은 로그인 화면이었고 다른 인증 브라우저도 없어 실제 사용자
  결과에서 두 탭을 클릭하는 Gate는 `BLOCKED_BY_AUTH_ENVIRONMENT`로 남긴다. 인증 우회와
  Production/auth source 변경은 하지 않았다.

## 작업공간 보호 감사 (historical checkpoint)

- branch, HEAD, `origin/main`, merge-base는 모두 기준선 `d44f30e`로 유지됐고 staged는 0,
  stash 2개는 시작 시점과 동일하다.
- 시작 시 SHA-256 기준과 비교해 이번 UI·Spec 대상 14개만 변경·추가됐다. PRZ-016 Search,
  `keywordEvidencePanel`, `jobEvidence.ts`, PRZ-009 Tag, Production Java 주석과 나머지 dirty
  파일은 이번 작업 전후 해시가 동일하다.
- 이전 UI Phase 종료 시 파일시스템에서 사라져 있던 대회 결과보고서 관련 untracked 문서
  4개는 결과 상태 탭 Phase 기준선 152개에는 없었지만 최종 감사에서 다시 나타났다. 이번
  작업의 명령에는 해당 문서 생성·복구·이동·수정이 없고 파일 내용도 열지 않았다. 외부에서
  다시 나타난 이 4개를 그대로 보존했으며, 그 밖의 예상치 못한 변경·추가는 없다.

## Segmentation UX 보정 근거 (historical checkpoint)

- 기존에는 section heading이 `section` 보조 정보로만 전달되면서 frontend가 각 checkbox
  안에 반복 표시했고, 알려진 채용 metadata section과 독립 metadata를 Search item에서
  제외하지 않아 실제 공고에서 selectable 항목이 44개까지 늘어났다.
- category-level heading 분류로 직무·자격·우대 child는 보존하고 복지·전형·근무·접수 등
  명백한 metadata section child는 제외한다. Unknown section child는 기본 보존한다.
- 인원, 전형 단계, metadata `label: value`, 독립 시간 범위만 보수적으로 제외하며
  `지원자격: API 설계 경험` 같은 요구사항과 본문 속 시간 범위는 보존한다.
- item 시작의 `-`, `*`, `•`, `·`, `▪`, `○` bullet은 공백 유무와 무관하게 제거하되
  `-10ms`, `--enable-preview`, `*.java`는 보존한다.
- 대표 혼합 fixture는 career/자격/우대/unknown 6개만 원래 순서와 section으로 반환했고,
  metadata 10개와 section heading은 selectable 결과에 포함하지 않았다. 사용자가 확인한
  실제 `44 → After` 수치는 동일한 44개 원문을 이번 Phase 입력으로 사용하지 않아
  `NOT_VERIFIED`다.
- 사용자 제공 실제 형식에는 Markdown table pipe/separator, `**` emphasis, `##` heading,
  HTML `<br>`와 한국어 bullet `ㆍ`가 함께 있어 이 마크업이 Search query에 남는 문제가
  있었다. 구조 마크업을 먼저 정규화하는 회귀 fixture에서 업무 5개, 자격 5개, 우대 5개,
  총 15개만 selectable하게 반환하고 복지·학력/경력·채용인원·빈 표 셀은 제외했다.

## Compound query composition 보정 근거 (historical checkpoint)

- 구현 위치는 PRZ-017 전용 `frontend/src/jobEvidence.ts`다. 공통 Search API client와
  PRZ-016 Search Production은 수정하지 않았다.
- 단순 항목은 원문 1회만 검색한다. `또는`, 독립 영문 `or`, 공백으로 구분한 `A / B`,
  명확한 3항 compact slash 목록은 원문과 source-order variant를 검색한다. variant는 최대
  5개이며 빈 값·중복을 제거한다.
- 쉼표는 40 code point·2 token 이하의 명확한 OR 앞 목록에서만 분리한다. 일반 나열,
  천 단위 숫자, `CI/CD`, `OAuth2/JWT`, `and/or`, Unix/Windows 형태 경로는 원문-only다.
- item worker의 기존 동시성 3 안에서 query를 순차 호출한다. 한 worker가 401을 받으면 이미
  진행 중인 다른 worker도 현재 요청 뒤 다음 variant를 시작하지 않는다.
- query plan과 각 Search 응답 순서를 유지하며
  `documentId + documentVersionId + chunkId`가 같은 결과는 먼저 나온 하나만 남긴다. query별
  score는 비교하지 않고 최종 통합 결과를 5건으로 제한한다.
- 인증 브라우저 Case A `Docker, Kubernetes 또는 Cloud 환경 사용 경험`은 원문+3 variant,
  총 4회 Search 뒤 기존 0건에서 3건으로 바뀌었다. 세 결과는 같은 owner의 ACTIVE 이력서
  1·2페이지 Docker 원문이며 원래 requirement 제목 하나 아래 표시됐다.
- Case B `Git을 활용한 프로젝트 또는 협업 경험`은 원문+2 variant, 총 3회 Search 뒤에도
  0건이었다. 이는 독립 `Git` literal 부재와 `협업 경험` direct-anchor 한계라는 기존
  PRZ-016 recall 문제로 분리한다.
- 두 항목의 7회 요청은 모두 HTTP 200이었고 약 0.55초에 완료됐다. 단순·compound 5개 항목은
  12회 요청, 결과 그룹 5개, HTTP 오류 0, 약 0.73초였다. browser console error도 0건이었다.
- PDF Evidence의 `문서에서 보기`는 `백엔드 이력서-피드백용` 1페이지 viewer를 열었다.
- 독립 감사는 쉼표·slash·path·한 글자 identifier·401 중단 경계를 보강한 뒤 blocking
  finding 0건으로 끝났다.

PostgreSQL 결과는 OpenSQL evidence로 대체하지 않는다. 환경이 없거나 실행하지 않은 Gate를
`PASS` 또는 `VERIFIED`로 기록하지 않는다.

## 남은 Gate (historical checkpoint)

이전 compound query Phase의 자동 검증·인증 targeted browser Gate·독립 AUDIT는 완료됐다.
기존 44개 전체 공고와 TXT 이동은 당시 Phase에서 재실행하지 않았다. 이전 branch checkpoint는
commit·push했고 PR/merge는 실행하지 않았다. 이번 2026-08-24 보정은 commit·push하지 않았고
전체 상태는 `IN_PROGRESS`다.

## 2026-08-24 segmentation generalization Phase (historical checkpoint)

### 1. 기존 parser의 일반화 실패 원인

기존 parser는 알려진 section 밖의 `UNKNOWN`을 너무 쉽게 searchable로 되돌리고, heading과
grouping parent를 leaf보다 먼저 구조화하지 못했다. 그 결과 requirement loss보다 회사 소개,
직무명, heading, 근무 metadata, 복지·전형·법적 고지가 selectable로 반복 유입됐다.

### 2. 새 segmentation architecture

- 각 line에 정규화 본문, 원래 순서, list level, heading marker, contextual heading과
  grouping-parent 후보를 유지한다.
- semantic 판정보다 `section → parent → leaf`를 먼저 구성하고 child가 있는 parent 자체는
  제외한다.
- section은 `SEARCHABLE`, `EXCLUDED`, `STRUCTURAL`, `UNKNOWN`으로 분리한다.
- `SEARCHABLE` leaf는 적극 보존하고 `EXCLUDED` child와 `STRUCTURAL` 자체는 제외한다.
  `UNKNOWN`은 list/leaf, requirement-like 형태, 인접 section과 metadata 형태를 함께 보고
  보수적으로 보존한다.

### 3. 변경 파일

이번 generalization 구현·검증에서 직접 변경한 파일은 다음 6개다.

- `src/main/java/com/prizm/jobposting/service/JobPostingSegmentationService.java`
- `src/test/java/com/prizm/jobposting/service/JobPostingSegmentationServiceTest.java`
- `specs/PRZ-017-job-posting-evidence-v1/spec.md`
- `specs/PRZ-017-job-posting-evidence-v1/plan.md`
- `specs/PRZ-017-job-posting-evidence-v1/tasks.md`
- `specs/PRZ-017-job-posting-evidence-v1/evidence.md`

그 밖의 기존 미커밋 frontend 파일은 이번 Phase에서 수정하지 않았다.

### 4. 하드코딩 방지 감사

- Production segmentation source의 회사명·URL/domain·실제 공고 문장·selectable 개수 분기: 0건
- 새 generic test의 development/holdout 회사명·실제 문장 복제: 0건
- exact heading 문장 사전 확장: 사용하지 않음. 일반 의미 범주의 한글·영문 stem과 구조 규칙을
  사용했다.
- `아타드(ATAD)` 문자열 1건은 이번 Phase 이전부터 있던 명시적 회귀 fixture이며 Production
  판정에는 사용되지 않는다.
- source/test 동결 SHA-256은 holdout 전후 모두 각각
  `3C5A6C3BEE811444E53284A111A8E6113AABB2A395B24CEB8FEE058DAD3F1BB2`,
  `01F3B68B85FD877189DBAC12FEE74EFF37E9D7BAE38B77009F744A3E61824EC1`이다.

### 5. Development 공고 6개 결과

| 공고 | selectable | requirement leaf | loss | noise | noise rate | loss rate | 판정 |
|---|---:|---:|---:|---:|---:|---:|---|
| 토스뱅크 ML Backend Engineer | 10 | 10 | 0 | 0 | 0% | 0% | `PASS` |
| DeepAuto Backend Engineer (AI Platform) | 26 | 26 | 0 | 0 | 0% | 0% | `PASS` |
| Match Group/Tinder Seoul Senior Software Engineer, Backend | 21 | 21 | 0 | 0 | 0% | 0% | `PASS` |
| 채널코퍼레이션 Software Engineer | 14 | 14 | 0 | 0 | 0% | 0% | `PASS` |
| Moloco Senior Software Engineer, Ads Creative | 12 | 12 | 0 | 0 | 0% | 0% | `PASS` |
| LG AI Research AI-Native Engineering Project | 6 | 6 | 0 | 0 | 0% | 0% | `PASS` |

공개 공고 전문은 tracked fixture로 저장하지 않았고 browser/local 입력으로만 사용했다.

### 6. ATAD regression

selectable 18개를 유지했다. 업무 6, 자격/필수 7, 우대 5이며 소개, `►/▶` heading,
grouping parent와 복지 noise는 0개다.

### 7. 새 unseen holdout 공고

| 회사·직무 | URL | 구조 특징 |
|---|---|---|
| PayPay India — 01.Backend Engineer | [Greenhouse](https://job-boards.greenhouse.io/pay2dc/jobs/4024283006) | 영문, bullet이 빠진 DOM text, pipe 기술 스택, 긴 지원서·privacy block |
| Atomicwork — Backend Engineer | [Greenhouse](https://job-boards.greenhouse.io/atomicwork/jobs/4143684008) | 영문, label+설명 bullet, benefits·culture·지원절차·법적 block |
| Lean In — Backend Engineer | [Lever](https://jobs.lever.co/sgff.org/0da89c8f-0013-4f4e-a5b1-ef8def97a283) | 영문, 대문자 하위 heading, 무표식 leaf run, 보상 table·legal block |

한국어 GreetingHR 신규 후보 3건은 실제 브라우저에서 모두 `페이지를 찾을 수 없습니다`로
확인되어 캐시 text를 holdout으로 사용하지 않았다.

### 8. Holdout 결과

| 공고 | selectable | requirement leaf | preserved | loss | noise | noise rate | loss rate | 판정 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| PayPay India | 45 | 27 | 25 | 2 | 19 | 42.2% | 7.4% | `FAIL` |
| Atomicwork | 28 | 16 | 16 | 0 | 3 | 10.7% | 0% | `MINOR_LIMITATION` |
| Lean In | 35 | 26 | 26 | 0 | 9 | 25.7% | 0% | `FAIL` |

PayPay의 19개 noise는 `Tech Stack` 설명 2, Remarks/가치 안내 4, 지원서 field 13이다.
AI-first culture 아래 명시적 엔지니어 요구 2개는 searchable section 밖이라 유실됐다.
Atomicwork의 3개 noise는 responsibilities 소개 1개와 qualifications 소개가 두 문장으로
나뉜 결과다. Lean In의 9개 noise는 하위 heading 4개와 보상 metadata 5개다. 원문의 한 bullet이
여러 문장인 경우 selectable item이 분리돼도 같은 source requirement leaf의 보존으로 계산했다.

### 9. 자동 테스트 결과

- focused `*JobPosting*`: service 29/29, controller 6/6, 합계 35/35 `PASS`
- Docker backend rebuild와 health: `PASS`
- `git diff --check`: `PASS`

### 10. clean candidate backend 전체 test

`origin/main` `d44f30eb4346353c4363d559be478024f191a878`에서 분리한 candidate에 PRZ-017
대상 파일 31개만 복사했다. `./gradlew.bat test --no-daemon`은 89 suite, 613 test,
failure 0, error 0, 기존 skip 20으로 `PASS`했다.

### 11. 보호영역 diff

- PRZ-016 Production Search, embedding, `application.yml`: candidate diff 0
- pgvector/Search SQL과 Flyway migration: candidate diff 0
- Gradle 설정, dependency lockfile와 frontend lockfile: candidate diff 0
- `frontend/package.json`: dependency 변경 없이 PRZ-017 unit test 3개를 기존 script에 추가
- auth: 이번 Phase working diff 0. candidate에는 기존 PRZ-017 USER segmentation endpoint
  경계와 인증 integration test만 포함

### 12. Known limitations

- DOM copy가 bullet marker를 잃으면 한 source bullet의 여러 문장이 개별 item으로 분리될 수 있다.
- searchable section 내부의 인식되지 않은 하위 heading과 설명 paragraph가 leaf로 남는다.
- 지원서 form boundary와 보상 table에 명시적인 제외 heading이 없으면 UNKNOWN fallback이
  metadata를 다시 포함할 수 있다.
- 이번 unseen holdout은 실제 접근 가능한 영어 공고와 Greenhouse 2건·Lever 1건으로 구성됐다.
  한국어 신규 공고는 접근 불가로 평가하지 못했다.

### 13. Blocking finding

두 holdout에서 heading·metadata noise가 19개와 9개로 반복됐다. 이는 일부 애매한 문장 한두
개가 아니라 새로운 form/table/subheading 형식에서 UNKNOWN 경계가 다시 searchable로 열리는
일반화 결함이다. holdout 뒤 code/test는 수정하지 않았으며 이 finding은 다음 Phase의 blocker다.

### 14. 최종 판정

`SEGMENTATION_GENERALIZATION_NEEDS_ADJUSTMENT`

commit, push, PR, merge는 모두 `NOT_RUN`이다. PRZ-017 전체 `READY_TO_MERGE` 판정도 하지 않는다.
