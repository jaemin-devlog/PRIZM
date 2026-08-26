# PRZ-017 — 채용공고 항목별 Career Evidence V1 Plan

## 상태와 목표

- 상태: `IN_PROGRESS`
- 기준선: `d44f30eb4346353c4363d559be478024f191a878`
- 현재 source: `de98bcf`
- 목표: 입력 → 결정적 분리 → 사용자 선택 → 기존 Search → 항목별 Evidence → 원문 이동의
  최소 수직 흐름을 완성한다.

## 선택한 접근

### 2026-08-24 Search 후보 신뢰성 보정

- backend segmentation과 PRZ-016 SearchService/threshold/ranking/rescue/embedding/SQL/Evidence
  localization은 동결한다. 변경은 PRZ-017 frontend consumer와 해당 표시·test에 한정한다.
- query planner에 `등`·`등의` 명시적 enumeration suffix를 추가하되 기존 짧은 comma-list
  guard를 재사용한다. original-first, variant 최대 5와 item 동시성 3을 유지한다.
- Search 응답은 PRZ-017 내부 `JobEvidenceCandidate`로 감싸 raw result, matched query 목록,
  display query와 direct-identifier 여부를 보존한다. 같은 chunk는 첫 raw result와 순서를
  유지하고 이후 matched query만 합친다. Top 5 밖 신규 후보는 추가하지 않되 이미 채택된 후보의
  provenance는 계속 합친다.
- decomposition variant가 ASCII 기반의 짧은 identifier/identifier phrase일 때만 Unicode-aware
  boundary direct match를 `content` 또는 `snippet`에서 확인한다. 특정 기술 사전과 동의어는
  사용하지 않는다.
- PRZ-017 presentation은 candidate display query를 기존 extractive helper의 anchor로 넘긴다.
  identifier anchor에는 사용 경험 문맥을 부여해 실제 identifier unit을 선택하되 출력은 원문만
  사용한다. 공통 Search presentation 구현은 수정하지 않는다.
- UI 문구를 Search 후보 중심으로 낮추고 충족 판정이 아니라는 안내를 추가한다. PDF/TXT callback,
  결과 순서, 탭과 requirement 번호는 유지한다.
- focused test 후 frontend 전체 unit/typecheck/lint/build와 Docker browser Gate를 수행한다.
  PDF는 renderer를 수정하지 않고 일반 Chrome 확인 가능 여부만 별도 기록한다.

### 2026-08-26 결과 표시 polish

- 실제 인증 결과 화면에서 확인한 긴 PDF page 후보의 과도한 높이, 결과 제목의 브라우저 기본
  focus outline과 같은 제목 document group의 구분 부족만 수정한다.
- PRZ-017 presentation에서 과도하게 긴 highlight는 더 짧은 기존 extractive context로
  fallback하고, CSS에서 후보를 5줄 미리보기로 제한한다. 기존 주변 내용 `<details>`와 PDF/TXT
  원문 action은 유지한다. Search response와 공통 Search presentation은 수정하지 않는다.
- document group은 기존 Search response의 `versionNo`를 표시하고 같은 제목 group만 원래
  순서 기반 `n/N` 보조 문구로 구분한다. document ID 같은 내부 식별자는 사용자에게 노출하지
  않는다.
- 결과 제목의 programmatic focus 이동은 접근성 계약으로 유지하고, outline만 기존 PRIZM focus
  token으로 일관되게 표시한다.
- component test, frontend 전체 unit/typecheck/lint/build, Docker frontend rebuild와 실제 인증
  브라우저의 desktop/mobile 높이·overflow·focus·group label을 검증한 뒤 최종 diff를 감사한다.

### 2026-08-26 주변 내용 polish

- 실제 인증 화면에서 `<details>`가 summary 버튼의 flex 너비로 수축하고, 일부 후보의 문맥이
  현재 미리보기를 포함하지 않은 문서 머리말을 보여 주는 문제를 수정한다.
- PRZ-017 presentation에서 정규화한 문맥이 미리보기를 포함하고 추가 원문도 제공하는 경우만
  유효한 주변 문맥으로 판정한다. Search 결과·공통 Search presentation·ranking은 변경하지 않는다.
- 유효한 action만 렌더링하고 열린 PRZ-017 details를 Evidence 본문 폭으로 확장한다. focused test,
  frontend 전체 검증, Docker rebuild와 실제 인증 browser 측정 뒤 diff를 다시 감사한다.

### 2026-08-24 실사용 보정 범위

- 기준 source는 `8be904d`이며 ATAD 공고의 변경 전 API 결과 45개를 재현했다.
- backend는 `ParsedLine`에 명시적 heading marker와 list hierarchy를 최소 추가한다.
  `►/▶` heading, introduction/benefits exclusion, 바로 뒤의 더 깊은 list child가 있는 grouping
  parent 제외와 list item atomicity만 보정한다. DTO/controller/API 계약은 변경하지 않는다.
- frontend는 `jobEvidenceSearchQueries`에서 쉼표 목록 뒤의 명시적인 `중 N개 이상` 또는
  `중 하나 이상` 문법만 보수적으로 인식한다. 원문 첫 query, variant 최대 5, Top 5 merge,
  동시성 3과 Search API는 유지한다.
- 수정 대상은 segmentation service/test, `frontend/src/jobEvidence.ts`와 해당 unit test,
  PRZ-017 Spec/Plan/Tasks/Evidence뿐이다. Search/embedding/auth/migration/dependency는 수정하지
  않는다.
- 이번 보정은 사용자 지시에 따라 commit·push·PR·merge를 모두 실행하지 않는다.

### Backend segmentation

1. `jobposting` 경계에 request/response DTO, controller와 stateless segmentation service를
   둔다.
2. service는 줄·list prefix·문장 경계와 category-level section 성격을 해석하고 공백
   정규화, section 전달, 명백한 채용 metadata 제외, exact deduplication과 짧은 항목
   제거를 순서 보존 collection으로 처리한다. Unknown section의 child는 기본 보존한다.
   Search query 길이에 맞춘 500자 이하 분할과 최대 100개 결과 상한을 함께 적용한다.
3. 특정 회사·기술·평가 corpus 문자열은 사용하지 않는다.
4. endpoint를 기존 Security allowlist에 `ROLE_USER`로만 추가한다.
5. Search service, repository, profile과 embedding은 호출하지 않는다.

### Frontend 선택과 Search orchestration

1. 기존 Career Vault shell과 디자인 token 안에서 입력 화면, 항목 선택 dialog와 결과 전용
   route를 하나의 `JobEvidencePage` controller가 관리한다. 입력과 결과 route를 같은 component로
   렌더링해 route 간 이동에서 현재 page session state를 보존하고, state 없는 결과 route 직접
   진입은 입력 route로 복귀시킨다.
2. 분리 응답은 modal에서 원문 순서의 section 그룹으로 표시하되 heading에는 checkbox를 만들지
   않는다. 실제 항목의 checkbox state, 전체 선택/해제, selectable 수와 0개 실행 방지, Escape,
   backdrop, focus containment와 trigger 복귀를 검증한다.
3. 선택 항목별 query plan은 원문을 첫 항목으로 유지한다. 명확한 `A / B`, 3항 compact
   slash 목록, `또는`, 독립 영문 `or` 구조만 최대 5개 variant로 결정적으로 분해한다.
   2항 결합 식별자와 경로는 제외하고, 기존 동시성 3의 item worker 안에서 원문과 variant를
   순차적으로 `searchCareerEvidence(query)`에 전달한다.
4. query plan 순서와 Search 응답 내부 순서를 유지하면서
   `documentId + documentVersionId + chunkId`로 중복을 제거하고 최종 5건만 원래 항목의
   성공·empty·error 그룹에 넣는다. query 간 score 재정렬은 하지 않는다.
5. 문서 종류는 기존 문서 목록 metadata를 한 번 읽어 result document ID에 mapping한다.
6. PRZ-017 결과는 `KeywordEvidencePanel` flat card 반복을 재사용하지 않고 requirement rail과
   활성 requirement 하나의 `document/version → Evidence row` workspace로 표시한다. rail은
   화면상 Evidence가 있는 requirement와 없는 requirement를 개수와 함께 상태 탭으로 분리한다.
   loading/error는 별도 `확인 필요` 탭으로 유지해 empty와 섞지 않는다. 각 탭은 원래 requirement
   순서·번호를 유지하며 탭 전환은 Search를 다시 호출하지 않는다. 원래 Search 순서 안에서
   문서를 처음 나타난 순서로 묶고 문서 metadata를 한 번만 표시한다.
7. 화면상 동일 문서·버전·evidence source 위치·정규화 표시 원문이 같은 행만 presentation
   단계에서 한 번 표시한다. 기존 chunk dedup/Top 5와 원본 결과 배열은 바꾸지 않고 같은 page의
   다른 원문과 다른 page는 보존한다.
8. 기존 Evidence highlight/context와 PDF target, authenticated original viewer,
   `documentDetailPath`를 재사용한다. 각 Evidence row의 action이 해당 PDF page 또는 TXT 문서
   상세로 이동하도록 유지한다.
9. Evidence 기본 표시는 긴 highlight의 더 짧은 extractive context fallback과 CSS 5줄
   미리보기로 한정하고 full text를 삭제하거나 Search 결과를 재작성하지 않는다. 같은 제목
   group은 version과 화면 순번으로만 구분한다.

### 문서와 상태

- [Spec](spec.md), 이 Plan, [Tasks](tasks.md), [Evidence](evidence.md)와 Registry를
  `IN_PROGRESS`로 유지한다.
- VERIFY와 AUDIT 결과가 실제로 생기기 전에는 `VERIFIED` 또는 `PASS`로 올리지 않는다.
- 사용자 승인에 따라 현재 작업을 branch checkpoint로 commit·push하되 PR과 merge는 수행하지 않는다.

## 예상 변경 표면

- Backend: `src/main/java/com/prizm/jobposting/**`, 대응 unit/controller test,
  `SecurityConfiguration`
- Frontend: segmentation API helper, 선택·그룹 orchestration/presentation helper,
  `App.tsx`, PRZ-017 전용 workspace component, `styles.css`, 대응 frontend test
- 문서: `specs/PRZ-017-job-posting-evidence-v1/**`, `specs/README.md`,
  `docs/architecture.md`, `docs/roadmap.md`, `docs/project-status.md`

다음은 수정하지 않는다.

- `src/main/java/com/prizm/search/**`
- `src/main/java/com/prizm/embedding/**`
- PRZ-016 Search 설정과 평가 자료
- V1–V16 Flyway migration과 PRZ-009 Tag schema/logic
- unrelated untracked 파일과 stash

## 데이터 흐름

```text
JWT USER
  → stateless segmentation API
  → ordered {section?, item}[]
  → browser checkbox selection
  → item 원문 + bounded deterministic alternative query plan
  → query별 기존 POST /api/career-evidence/search
  → owner + ACTIVE scoped Search response의 original-first dedup/Top 5 병합
  → 결과 전용 route의 requirement rail
  → 활성 item의 document/version group과 화면상 고유 Evidence row
  → PDF original #page=N 또는 TXT document detail
```

DB에 추가 write path는 없다. segmentation 결과와 선택은 frontend session state이며 Search는
기존 read path다.

## 위험과 대응

| 위험 | 대응 |
|---|---|
| heading을 항목으로 잘못 반환 | 다음 list와 종결 부호를 이용한 구조 규칙과 unit test |
| `►/▶` heading과 상하위 bullet이 평탄화됨 | marker 종류와 최소 hierarchy level을 보존하고 실제 ATAD fixture로 leaf-only 결과 검증 |
| list item 내부 마침표가 requirement를 분리 | list item은 atomic하게 유지하고 일반 paragraph sentence split 회귀를 별도 검증 |
| intro/benefits heading 변형으로 noise가 재유입 | 회사명 사전 없이 명백한 section canonical form만 제외하고 unknown section recall은 보존 |
| 모호한 heading 추정으로 실제 항목 유실 | `:` 또는 compact 구조만 heading으로 인정하고 recall 우선 |
| 대량 항목이 Search 요청을 증폭 | segmentation 결과 최대 100개, 초과 입력은 명시적 400 |
| compound variant가 Search 요청·지연을 증폭 | variant 최대 5, item 내부 순차 호출과 기존 동시성 3 유지, 실제 3~5개 항목 latency 확인 |
| 쉼표·경로 구분자를 대안으로 오해 | 쉼표 단독 분해 금지, 짧고 명확한 OR 앞 목록만 허용, 2항 결합 식별자·경로 guard, 원문 query 항상 보존 |
| `중 N개 이상` 확장이 일반 쉼표 문장을 오분해 | 명시적 선택 suffix와 짧은 식별자 목록을 모두 요구하고 숫자 comma·일반 문장 negative test 유지 |
| `등` 열거를 일반 comma 문장으로 오인 | `등`·`등의` suffix와 기존 짧은 identifier-list guard를 동시에 요구 |
| variant가 무관한 semantic 결과를 반환 | 짧은 identifier decomposition에만 독립 token direct Evidence guard 적용 |
| provenance 병합이 original-first 순위를 변경 | 첫 raw result와 insertion order를 유지하고 matched query metadata만 합침 |
| matched identifier 대신 긴 requirement의 숫자·다른 문장이 표시 | display query provenance를 PRZ-017 extractive anchor로 사용 |
| Search 후보를 충족 근거로 오해 | 후보 중심 문구와 원문 직접 확인 안내를 결과 workspace에 표시 |
| query별 점수를 직접 비교해 순위를 왜곡 | 원문→variant와 각 응답 순서를 유지하고 score 합산·전역 정렬 금지 |
| variant가 같은 Evidence를 반복 반환 | document/version/selected chunk identity로 먼저 나온 결과만 유지하고 최종 5건 제한 |
| 항목 순서·section 손실 | 입력 index와 insertion-order deduplication 사용 |
| 일부 Search 실패가 전체 결과 제거 | 항목별 상태와 재시도, 성공 그룹 보존 |
| 문서 metadata 중복 호출 | result 전체 ID를 모은 뒤 기존 문서 목록을 한 번 mapping |
| 입력·선택·결과가 한 페이지에 누적 | 선택은 modal, 검색은 같은 controller의 전용 결과 route로 분리 |
| keyword·경험 화면과 같은 flat card 반복 | PRZ-017만 requirement rail과 document group 전용 workspace 사용 |
| 결과 없음과 loading/error를 같은 상태로 오인 | `기록 없음`은 확정 empty만 포함하고 loading/error는 조건부 `확인 필요` 탭으로 분리 |
| 상태 탭으로 원래 requirement 순서·번호 손실 | 전체 group index를 보존한 presentation filter와 component test |
| 화면상 같은 문구가 다른 chunk로 반복 | source 위치와 표시 원문이 완전히 같은 행만 presentation dedup |
| PDF ranked chunk와 표시 Evidence page 혼동 | `evidenceSourceType/index`만 viewer target으로 사용 |
| 결과 route 새로고침으로 session state 부재 | 검색 state가 없으면 입력 route로 안전하게 복귀 |
| 403을 세션 만료로 오판 | 기존 session policy대로 401에서만 로그아웃 |
| Search/Tag 범위 침범 | final path diff와 owner/ACTIVE/Tag 회귀 test |

## 검증 계획

### Focused

```powershell
.\gradlew.bat test --tests '*JobPosting*' --no-daemon
npm --prefix frontend run test:unit
```

segmentation 계약, controller validation과 frontend 입력·선택·compound query plan·병합,
그룹·empty·error·이동을 먼저 확인한다.

### 전체 회귀

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run build
git diff --check
```

integration은 PostgreSQL·pgvector 결과이며 OpenSQL 증거로 확대하지 않는다.

### Search Production 감사

```powershell
git diff origin/main -- src/main/java/com/prizm/search src/main/java/com/prizm/embedding src/main/resources/application.yml
git diff origin/main -- src/main/resources/db/migration
```

첫 명령은 PRZ-017과 무관한 Search/embedding 설정 변경 0, 둘째는 migration 변경 0이어야
한다. 차이가 있으면 PRZ-017 계층에서 해결하고 이유가 명확하지 않은 Search 변경은
승격하지 않는다.

### 실제 브라우저 Gate

가능하면 현재 source로 Docker backend/frontend를 rebuild한 뒤 다음을 실제로 확인한다.

```text
로그인 → 채용공고 입력 → 항목 분리 modal → 여러 항목 선택
→ 관련 경력 찾기 → 결과 전용 route → requirement rail 전환
→ 기록 있음/기록 없음 상태 탭 전환 → document별 여러/빈 Evidence
→ 항목 다시 선택 → 문서에서 보기
→ PDF 해당 page 또는 TXT 문서 상세
```

세션, fixture, Docker 또는 browser 접근 문제로 실행할 수 없으면 `NOT_RUN` 또는
`NOT_VERIFIED`로 기록하고 코드 실패와 환경 실패를 분리한다.

## 2026-08-24 segmentation generalization 검증 계획

1. 기존 parser를 line 구조와 section role 중심으로 확장하고, 회사명·공고 문장·URL을 모르는
   generic fixture로 구조 계약을 고정한다.
2. 공개 development 공고 6건과 ATAD를 local/browser 입력으로만 검증하고 공고별
   requirement loss와 selectable noise를 기록한다.
3. focused service/controller test 완료 뒤 service/test SHA-256을 기록해 동결한다.
4. 동결 뒤 처음 보는 공개 공고 3건 이상을 수집해 실제 DOM text로 평가한다. 결과가 실패해도
   이번 Phase에서는 source/test를 수정하지 않는다.
5. origin/main 기반 clean candidate에 PRZ-017 대상 파일만 복사해 전체 backend test,
   `git diff --check`, PRZ-016 Search/embedding/application, migration, dependency 보호영역을
   감사한다.
6. holdout에서 반복 noise 또는 대량 loss가 발견되면 `NEEDS_ADJUSTMENT`를 기록하고
   commit/push/PR/merge 없이 종료한다.

## 2026-08-24 segmentation V1 stabilization 계획

1. Production을 수정하기 전에 application/form, metadata/table, searchable subheading,
   무표식 requirement run, UNKNOWN plain/bullet과 legal/privacy를 추상화한 generic fixture를
   추가하고 현재 service에서의 실제 실패를 기록한다.
2. 기존 `ParsedLine`과 section scan은 유지한다. 각 index에서 주변 line을 이용해 경량
   block boundary와 structural subheading을 계산하며 전체 parser를 재작성하지 않는다.
3. form block은 apply/upload 문맥과 연속된 짧은 label/question 구조로, metadata block은
   separator·range·인접 pair 구조로 판별한다. searchable inline requirement가 metadata로
   오분류되지 않도록 section role과 requirement-like value를 우선한다.
4. generic fixture와 기존 focused test를 통과한 뒤 실제 development/regression 10건을
   browser/local input으로 재평가한다. 실제 공고 전문은 tracked fixture로 저장하지 않는다.
5. Development Gate를 통과하면 service/test SHA-256을 기록하고 source/test를 동결한다.
   이후 처음 보는 공개 공고 3~4건을 수집해 평가만 수행한다.
6. origin/main 기반 clean candidate에는 PRZ-017 대상 파일만 복사하고 전체 backend test,
   `git diff --check`, Search/embedding/SQL/Flyway/auth/dependency 보호영역을 감사한다.
7. 사용자 선택으로 쉽게 제거 가능한 소수 noise는 known limitation으로 허용하되 대량 form,
   metadata, 소개 noise나 requirement 대량 유실이 반복되면 `SEGMENTATION_V1_NEEDS_ADJUSTMENT`로
   종료한다.

예상 Production 변경은 segmentation service 1개이며 test 1개와 PRZ-017 Spec 문서만 갱신한다.
DB write, migration, dependency, model, authentication, Search와 frontend 변경은 없다.

## Rollback과 중단 조건

- DB 변경이 없으므로 결과 route, 선택 modal과 PRZ-017 전용 workspace만 되돌리면 이번 UI
  변경 전 단일 페이지 표현으로 돌아간다.
- 새 migration, LLM·model·runtime dependency, Search algorithm 변경, Tag filter 또는
  요구사항 판정이 필요해지면 구현을 멈추고 Spec/Plan으로 돌아온다.
- 필수 unit/integration/frontend 실패나 blocking audit finding이 남으면 `VERIFIED`로
  전환하지 않는다.

## Dependency·license·통합 경계

- 새 dependency, model, asset과 license 영향은 예상하지 않는다.
- 기존 Ollama dependency는 PRZ-016 embedding 전용으로 보존한다.
- 이번 segmentation generalization Phase의 commit/push/PR/merge는 모두 `NOT_RUN`이다.
- 이번 segmentation V1 stabilization Phase의 commit/push/PR/merge도 모두 `NOT_RUN`이다.
