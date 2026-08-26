# PRZ-017 — 채용공고 항목별 Career Evidence V1 Tasks

## ORIENT / SPEC / PLAN

- [x] 후보 신뢰성 Phase branch/HEAD/origin/main/status와 실제 stash 1개 확인
- [x] query planner, merge, presentation, workspace와 관련 frontend test 흐름 확인
- [x] backend segmentation·PRZ-016 동결 및 candidate/provenance/direct guard 계약 반영

- [x] `8be904d` branch/HEAD, `origin/main@d44f30e`, clean worktree와 stash 1개 확인
- [x] 실제 ATAD 입력의 변경 전 selectable 45개 API 결과와 noise 종류 기록
- [x] 실사용 segmentation·`중 1개 이상` query composition 수용 기준을 Spec/Plan에 반영

- [x] branch, HEAD, `origin/main`, dirty/untracked 파일과 stash 목록 확인
- [x] 기존 Search, Evidence UI, PDF/TXT 이동, 인증·owner/ACTIVE 경계 확인
- [x] Qwen/LLM과 과거 requirement/claim 구조의 Production 참조 여부 확인
- [x] migration/persistence 부재와 V9 `JOB_POSTING` DocumentType 경계 확인
- [x] V1 Spec, Plan, Tasks와 초기 Evidence 작성

## IMPLEMENT

### 2026-08-26 개인 문서 원문 일치 안정화

- [x] 연속 application field, 보상 범위, 기술 목록 안내문과 구조용 하위 제목 noise 보정
- [x] `Required ...` 자격 문장의 application form 오인 방지
- [x] 기존 원문+명시적 compound variant 계약 유지
- [x] 공백 없는 단독 query의 exact 원문 우선과 semantic 후보 보존
- [x] 경험 진위·충족 판정이 아니라 원문 발췌와 문서 위치를 보여 주는 UI 안내 유지
- [x] 특정 사용자 문장·회사명·기술명 사전 없는 최소 회귀 추가

### 2026-08-26 주변 내용 polish (historical checkpoint)

- [x] 미리보기를 포함하면서 추가 원문을 제공하는 문맥만 노출
- [x] 유효하지 않거나 중복인 `주변 내용 보기` action 제거
- [x] 열린 reader를 Evidence 본문 폭으로 확장
- [x] presentation/component regression test 추가

### 2026-08-26 결과 표시 polish (historical checkpoint)

- [x] 긴 Evidence 후보 5줄 미리보기와 기존 주변 내용·원문 action 유지
- [x] 결과 제목 focus를 PRIZM focus token으로 표시
- [x] document version과 같은 제목 group `n/N` 구분 표시
- [x] component regression test 추가

- [x] `등`·`등의` 명시적 enumeration query composition
- [x] PRZ-017 candidate matched query provenance와 original-first merge
- [x] 짧은 identifier variant direct Evidence guard
- [x] matched display query 기반 extractive Evidence anchor
- [x] Search 후보 중심 UI 문구와 비판정 안내
- [x] query/guard/provenance/presentation/UI focused regression test

- [x] `►/▶` heading과 list hierarchy를 보존하는 최소 ParsedLine 보정
- [x] introduction/benefits section과 child가 있는 grouping parent 제외
- [x] list item atomicity와 기존 500자 제한 보존
- [x] 명시적 `중 N개 이상` comma alternative query 계획 추가
- [x] ATAD backend fixture와 query planner positive/negative 회귀 test 추가

- [x] deterministic segmentation DTO/service/controller 구현
- [x] 500자 이하 무손실 분할과 최대 100개 Search fan-out 상한 구현
- [x] segmentation endpoint를 활성 `ROLE_USER` 경계에 추가
- [x] 입력·분리·checkbox·전체 선택/해제·선택 수·재수정 UX 구현
- [x] 선택 항목별 기존 Career Evidence Search orchestration 구현
- [x] 항목별 Evidence/empty/error와 재시도 UI 구현
- [x] 기존 PDF page/TXT document detail 이동 연결
- [x] 금지된 판정·score UI와 Qwen/LLM Production 참조 0 확인
- [x] section heading을 그룹 제목으로 분리하고 career child만 selectable하게 보정
- [x] metadata section/독립 metadata 제외와 Unicode bullet 정규화 보정
- [x] frontend section grouping과 selectable count 표시 보정
- [x] 명확한 alternative compound query를 원문과 최대 5개 variant로 결정적 분해
- [x] 원문 우선 결과 병합·selected chunk dedup·Top 5 제한 구현
- [x] segmentation 결과를 접근 가능한 항목 선택 modal로 분리
- [x] 검색 시작 시 결과 전용 route와 loading 화면으로 전환
- [x] requirement rail → document/version → Evidence row workspace 구현
- [x] 화면상 정확 중복 행 정리와 같은 page의 서로 다른 Evidence 보존
- [x] 항목 다시 선택·입력 복귀·결과 route 직접 진입 안전 처리
- [x] desktop 2열·mobile 단일 열과 PDF/TXT row 이동 유지
- [x] requirement rail을 검색 후보 있음·검색된 후보 없음 상태 탭으로 분리
- [x] loading/error를 조건부 확인 필요 탭으로 분리
- [x] 상태 탭 안에서 원래 requirement 순서·번호와 Search 비호출 유지

## VERIFY

### 2026-08-27 mixed bullet 마지막 Gate

- [x] 같은 들여쓰기의 mixed `•`/`-`를 sibling leaf로 처리
- [x] 실제로 더 들여쓴 parent-child 구조 유지
- [x] segmentation service/controller focused 50/50, 실패·오류·skip 0
- [x] production/test compile과 `git diff --check` `PASS`
- [x] 인증 browser mixed bullet selectable 10→11, metadata 제외·순서 유지
- [x] Java·Docker 2개 항목 Evidence와 PDF 2페이지 target smoke `PASS`
- [x] PRZ-016 Search Production·PRZ-009·기존 주석·unrelated·stash 보호

### 2026-08-26 개인 문서 원문 일치 안정화 (historical checkpoint)

- [x] frontend PRZ-017 focused unit/component 33/33 `PASS`
- [x] backend segmentation focused 43/43 `PASS`
- [x] frontend 전체 unit 80/80·typecheck·lint·build `PASS`
- [x] backend 전체 unit `PASS` — 89 suites, 627 tests, 실패·오류 0, 기존 조건부 test 20건 skip
- [ ] backend integration 최종 재실행 — 실행 중 사용자 요청으로 중단, `ABORTED`
- [ ] 최신 working tree 실제 인증 browser 원문·PDF page 이동 Gate — `NEEDS_ADJUSTMENT`:
  동일 bullet 11개·Search·empty·context와 자동 PDF page target은 통과했고 실제 PDF 표시는 사용자가
  확인했으나, 혼합 bullet 첫 업무 문장 1개 오인
- [ ] TXT 이동과 PayPay India·Lean In 최신 재평가 — `NOT_RUN`
- [x] Search Production·PRZ-009·기존 주석·staged/stash/untracked 보호 최종 감사

### 2026-08-26 desktop 동기화 (historical checkpoint)

- [x] 원격 source `84f9191` fast-forward와 변경 파일 13개 확인
- [x] frontend 전체 unit 77/77·typecheck·lint·build `PASS`
- [x] backend segmentation service/controller focused 42/42 `PASS`
- [ ] 최종 mobile viewport browser 재실행 — 사용자 결정으로 `NOT_RUN`; 이번 checkpoint의
  필수 Gate에서는 제외

### 2026-08-26 주변 내용 polish (historical checkpoint)

- [x] PRZ-017 focused component/unit test — 30/30 `PASS`
- [x] frontend 전체 unit·typecheck·lint·build — 77/77와 정적·production 검증 `PASS`
- [x] Docker 최신 source rebuild와 실제 인증 browser Gate
- [x] `git diff --check`, 보호 영역·중복·민감정보 감사

### 2026-08-26 결과 표시 polish (historical checkpoint)

- [x] PRZ-017 focused component/unit test — 29/29 `PASS`
- [x] frontend 전체 unit·typecheck·lint·build — 76/76와 정적·production 검증 `PASS`
- [x] Docker frontend 최신 source rebuild와 실제 인증 desktop browser Gate
- [ ] 최종 patch mobile viewport browser 재실행 — in-app viewport 전환 불가로 `NOT_RUN`;
  직전 mobile Gate와 responsive CSS 회귀만 확인
- [ ] backend focused test 재실행 — 기존 `searchEvaluation` source-set/compile 환경 오류로
  test 실행 전에 `BLOCKED_BY_BASELINE`; 이번 Phase backend source 변경 0
- [x] 화면상 exact duplicate 0과 cross-document 반복 Evidence의 출처 보존·구분 표시 확인
- [x] `git diff --check`, 보호 영역·민감정보·unrelated 변경 감사

- [x] frontend focused·전체 unit·typecheck·lint·build
- [x] ATAD 18개, Java direct Evidence, Docker enumeration과 후보 UI browser Gate
- [x] console/network 오류와 PDF Chrome Gate 상태 기록
- [x] backend segmentation·PRZ-016 Production diff 0 확인

- [x] ATAD After selectable 수·noise 0·핵심 leaf·순서·1-based ID 확인
- [ ] backend focused/controller/전체 unit 실행 — focused 29개 `PASS`, 전체는 기존 `searchEvaluation` 컴파일 오류로 `BLOCKED_BY_BASELINE`
- [x] frontend unit/typecheck/lint/build 실행
- [ ] 가능한 인증 runtime/browser에서 ATAD와 Java compound Evidence 확인 — 인증 API `PASS`, browser는 로그인 확인 부재로 `BLOCKED_BY_AUTH_CONFIRMATION`
- [x] 기준 source `8be904d` 대비 Search/embedding/auth/migration/dependency diff 0 확인

- [x] backend segmentation 구조·경계·상한 계약 unit test
- [x] backend controller validation·인증 test
- [x] 선택·다중 Search·그룹 Evidence·empty·error·이동 frontend test
- [x] PRZ-009 Tag, upload/detail, 인증, 기존 Search focused regression
- [x] 전체 backend unit
- [x] PostgreSQL integration
- [x] frontend unit·typecheck·lint·build
- [x] `git diff --check`와 Markdown local link 검사
- [x] Search Production·migration diff 0 확인
- [x] Docker 최신 source rebuild와 인증 compound/PDF targeted browser Gate
- [ ] 기존 44개 전체 원문과 TXT 이동 browser Gate 재실행
- [x] segmentation UX 보정 focused/backend/frontend/integration regression
- [x] Search Production 추가 diff 0과 compound posting Before/After 확인
- [x] compound query frontend unit·typecheck·lint·build
- [x] 동일 owner/ACTIVE PDF에서 Docker·Git compound 실제 브라우저 재검증
- [x] 선택 modal·결과 route·requirement 전환·document grouping frontend test
- [x] 새 UI frontend unit·typecheck·lint·build
- [ ] 실제 브라우저 modal → 결과 workspace → PDF page Gate — `BLOCKED_BY_AUTH_ENVIRONMENT`
- [x] 결과 상태 탭 count·전환·empty/error 구분 frontend test
- [ ] Docker 최신 frontend에서 기록 있음·기록 없음 탭 browser Gate — `BLOCKED_BY_AUTH_ENVIRONMENT`

## AUDIT / INTEGRATE

### 2026-08-26 desktop 동기화 (historical checkpoint)

- [x] local HEAD와 `origin/PRZ-017-job-evidence-v1`이 `84f9191`로 일치
- [x] tracked clean·staged 0, untracked 5개 해시와 stash 2개 보존
- [x] merge commit·새 로컬 commit·PR·merge 없음

### 2026-08-26 주변 내용 polish (historical checkpoint)

- [x] AC39와 presentation/workspace/CSS/component test 추적성 확인
- [x] browser의 유효 context action 6개, 미리보기 불포함·중복 문맥 0 확인
- [x] 열린 details/reader/본문 폭 672px 일치와 후보 2/5/5/2 유지 확인
- [x] exact duplicate 0과 Search·embedding·SQL·Flyway·auth·dependency diff 0 재확인
- [x] 민감정보 추가 0, `git diff --check` `PASS`, stash 1개 보존
- [x] blocking finding 0, commit·push·PR·merge `NOT_RUN`

### 2026-08-26 결과 표시 polish (historical checkpoint)

- [x] AC36~AC38와 구현·component test 추적성 확인
- [x] 같은 document/version/source/display text의 exact duplicate 0 확인
- [x] 서로 다른 document의 반복 Evidence를 합치지 않고 version·`같은 제목 문서 n/N`으로 구분
- [x] Search·embedding·SQL·Flyway·auth·dependency diff 0 재확인
- [x] blocking finding 0 확인
- [x] commit·push·PR·merge `NOT_RUN`

### 2026-08-24 segmentation V1 stabilization (historical checkpoint)

- [x] branch·HEAD·origin/main·dirty 12개·stash 1개와 보호 파일 hash 기록
- [x] block-level V1 계약·acceptance criteria와 test-first 계획 반영
- [ ] generic application/form·metadata/table·subheading·UNKNOWN fixture 추가
- [ ] Production 수정 전 generic fixture 실패 재현
- [ ] 최소 범위 block boundary·subheading·UNKNOWN 보정 구현
- [ ] focused service/controller test 통과
- [ ] development/regression 10건과 ATAD 18개 Gate
- [ ] service/test freeze hash 기록
- [ ] freeze 뒤 unseen 공개 공고 3~4건 평가
- [ ] origin/main clean candidate 전체 backend test
- [ ] Search·embedding·SQL·Flyway·auth·dependency와 hardcoding 감사
- [ ] 최종 Evidence 16개 항목과 V1 판정 기록
- [x] commit/push/PR/merge `NOT_RUN`

### 2026-08-24 segmentation generalization (historical checkpoint)

- [x] line 구조, section role, UNKNOWN fallback과 leaf-first 정책 구현
- [x] 회사명·공고 문장을 복제하지 않은 generic 구조 fixture 추가
- [x] development 공개 공고 6건의 requirement loss/noise 측정
- [x] ATAD 18개(업무 6·자격/필수 7·우대 5) 회귀 확인
- [x] service 29개·controller 6개 focused test 통과
- [x] service/test SHA-256 기록 후 동결
- [x] 동결 뒤 unseen 공개 공고 3건을 실제 DOM 원문으로 평가
- [x] holdout 3건 중 반복 heading/metadata noise를 발견하고 source/test 재수정 없이
  `SEGMENTATION_GENERALIZATION_NEEDS_ADJUSTMENT`로 고정
- [x] origin/main clean candidate 전체 backend test와 최종 보호영역 감사
- [x] 최종 Evidence 14개 항목과 blocking finding 기록
- [x] commit/push/PR/merge `NOT_RUN`

- [x] 후보 신뢰성 Phase final diff와 AC21~AC24 독립 감사
- [x] 보호 대상·dependency diff 0, stash 1개 보존, 민감정보·whitespace 검사
- [x] Docker 실행 상태와 browser 실제 결과를 Evidence에 분리 기록
- [x] 이번 Phase commit·push·PR·merge `NOT_RUN`

- [x] `8be904d` 대비 변경 파일 8개가 PRZ-017 service/test/frontend/Spec 문서에만 한정됨을 확인
- [x] `git diff --check`, 변경 문서 local link와 민감정보 diff 검사 `PASS`
- [x] 시작 stash 1개 보존과 임시 focused-test helper 제거 확인
- [x] 이번 보정 commit·push·PR·merge `NOT_RUN`

- [x] Spec acceptance criteria와 최종 diff 독립 감사
- [x] blocking finding 0 확인 또는 수정 뒤 재감사
- [x] Production 주석 `6cc4726`, PRZ-017 source `de98bcf`를 현재 branch에 commit·push
- [x] PR/merge는 사용자 지시에 따라 `NOT_RUN`
- [x] stash와 unrelated dirty/untracked 파일 보존
- [x] 새 UI diff 독립 감사와 보호 대상 해시 재확인
