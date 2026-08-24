# PRZ-017 — 채용공고 항목별 Career Evidence V1 Evidence

## 현재 판정

- 상태: `IN_PROGRESS`
- 기준선: `d44f30eb4346353c4363d559be478024f191a878`
- 현재 source: `uncommitted worktree`
- VERIFY: 결과 상태 탭 포함 새 UI 자동 검증 `PASS`, 최신 frontend rebuild `PASS`, 새 인증 browser
  Gate `BLOCKED_BY_AUTH_ENVIRONMENT`; 이전 compound/PDF targeted Gate `PASS`
- AUDIT: 새 UI 독립 감사와 결과 상태 탭 최종 자체 감사 `PASS` — blocking finding 0,
  보호 대상 해시 변경 0
- commit/push/PR/merge: 사용자 지시에 따라 `NOT_RUN`

현재 source에는 V1 수직 흐름, bounded compound query composition, section별 선택 modal,
전용 결과 workspace와 결과 상태 탭이 구현됐다. 새 UI 자동 검증과 독립 감사는 통과했다. 인증 browser
Gate는 기존 세션이 segmentation 요청에서 만료돼 로그인 화면으로 이동했고 비밀번호를
코드나 자동화에 노출하지 않았으므로 `BLOCKED_BY_AUTH_ENVIRONMENT`다. 이전 Phase에서는 같은
owner의 ACTIVE PDF를 사용한 Docker·Git compound와 PDF page 이동을 확인했다. 기존 44개
전체 원문과 TXT 이동은 다시 실행하지 않았고 source도 uncommitted worktree이므로 전체
상태는 `IN_PROGRESS`로 유지한다.

## ORIENT 근거

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

## 기존 구조 분류

| 분류 | 대상 |
|---|---|
| 그대로 재사용 | Career Evidence Search API/service/response, owner/ACTIVE SQL, snippet/context, authenticated original endpoint, PDF Blob viewer, TXT document detail |
| 수정 후 재사용 | Career Vault shell, Evidence group presentation, Security USER matcher, 문서 종류 metadata mapping |
| 제거 | 현재 PRZ-017 Production에는 제거할 Qwen/LLM/fit 코드가 없음 |
| 신규 구현 | stateless segmentation, 입력·선택 UI, 선택 항목별 Search orchestration과 그룹 상태 |

## 요구사항 추적

| 요구사항 | source/test | 현재 결과 |
|---|---|---|
| R1 deterministic segmentation | `jobposting` service/controller, service 21개·controller 6개 test | `PASS` |
| R2 사용자 선택 | 입력 전용 `JobEvidencePanel`, 접근 가능한 selection modal과 component test | 자동 test `PASS`, browser `BLOCKED_BY_AUTH_ENVIRONMENT` |
| R3 기존 Search 소비 | 단순 item 원문 1회, compound 원문+최대 5 variant, 동시성 3, original-first dedup·Top 5 test | `PASS` |
| R4 그룹 Evidence·중립 상태 | 전용 route의 상태별 requirement rail, document group, Evidence row와 result/empty/error/retry test | 자동 test `PASS`, browser `BLOCKED_BY_AUTH_ENVIRONMENT` |
| R5 PDF/TXT 이동 | 기존 Evidence page target/detail callback wiring test | 자동 test `PASS`, 브라우저 `PENDING` |
| R6 인증·owner/ACTIVE | USER 200·무인증 401·admin 403와 전체 PostgreSQL integration | `PASS` |
| migration·dependency·Search diff 0 | origin/main 기준 final path diff·독립 감사 | `PASS` |

## 검증 결과

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

## 선택 modal·전용 결과 workspace 보정 근거

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

## 결과 상태 탭 보정 근거

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

## 작업공간 보호 감사

- branch, HEAD, `origin/main`, merge-base는 모두 기준선 `d44f30e`로 유지됐고 staged는 0,
  stash 2개는 시작 시점과 동일하다.
- 시작 시 SHA-256 기준과 비교해 이번 UI·Spec 대상 14개만 변경·추가됐다. PRZ-016 Search,
  `keywordEvidencePanel`, `jobEvidence.ts`, PRZ-009 Tag, Production Java 주석과 나머지 dirty
  파일은 이번 작업 전후 해시가 동일하다.
- 이전 UI Phase 종료 시 파일시스템에서 사라져 있던 대회 결과보고서 관련 untracked 문서
  4개는 결과 상태 탭 Phase 기준선 152개에는 없었지만 최종 감사에서 다시 나타났다. 이번
  작업의 명령에는 해당 문서 생성·복구·이동·수정이 없고 파일 내용도 열지 않았다. 외부에서
  다시 나타난 이 4개를 그대로 보존했으며, 그 밖의 예상치 못한 변경·추가는 없다.

## Segmentation UX 보정 근거

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

## Compound query composition 보정 근거

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

## 남은 Gate

이번 compound query Phase의 자동 검증·인증 targeted browser Gate·독립 AUDIT는 완료됐다.
기존 44개 전체 공고와 TXT 이동은 이번 Phase에서 재실행하지 않았다. commit/push/PR/merge도
사용자 지시에 따라 `NOT_RUN`이며 전체 상태는 `IN_PROGRESS`다.
