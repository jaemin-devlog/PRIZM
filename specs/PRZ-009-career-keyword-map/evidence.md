# PRZ-009 — 사용자 관리형 Document Tag Evidence

## 판정

`VERIFIED`

- AUDIT Gate: `PASS`

- 시작 기준 source: `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`
- 현재 P4 source: `1c1d8d22065832da48c604e1b8278699a485c2da`,
  [PR #51](https://github.com/jaemin-devlog/PRIZM/pull/51) — merge 전
- 역사적 P1–P3 자동 추출 source: `d52c6d01a3bef916e80a3c983a43c7b1fad1139b`,
  `main` merge `5a8ea8d2b85e7d87342e11e96d1d58d1181ab6b8`
- 역사적 UI·문서 관리 확장 source: `3af28492` ([PR #49](https://github.com/jaemin-devlog/PRIZM/pull/49)
  merge `550c9d4c5b1852f13335be061d1624d22ddec382`)
- 최종 검증일: `2026-08-24`
- 환경: Windows PowerShell, Java 17, Gradle 9.5.1, Node/npm,
  Docker Desktop, PostgreSQL+pgvector Testcontainers, Codex In-app Browser

## 역사 기록 — 폐기된 P1–P3 자동 추출 및 기존 UI 확장 Evidence

> 아래 2026-08-21 기록은 P4 전환 전 자동 keyword 추출과 기존 Browse UI를 당시
> source 기준으로 검증한 역사다. 이 구간의 `현재`, 판정과 남은 Gate는 각 기록 당시를
> 뜻하며 현재 Production 계약이나 P4 완료 상태가 아니다. 현재 사실은 뒤의 P4 기록만 따른다.

### 2026-08-21 Presentation update — 태그 Browse UX

이 항목은 `PRZ-009-keyword-tags-ui`에서 구현해 확장 source `3af28492`에 포함했다.
PRZ-009의 backend source, API DTO·endpoint, migration, keyword normalization, category,
owner/ACTIVE isolation과 Search production source는 변경하지 않았다.

이전의 keyword map presentation(세 ranking selector, 균형 점수, 상위 15개 구름과 순위 밖
목록)은 2026-08-10 구현·검증 이력으로 보존한다. 현재 presentation은 모든 keyword를 같은
크기의 tag로 표시하고, API `frequency` 내림차순·이름 안정 정렬 및 `?keyword=` 상세 Browse로
교체했다. `frequency`는 조립 원문에서 확인된 언급 수이며 숙련도·점수·중요도가 아니다.

- **명령·검증:** bundled Node `--experimental-strip-types --test` (frontend presentation 3 files)
  - 결과: `PASS` — 21 pass, 0 fail. 신규 tag order, category empty, URL round-trip,
    frequency label와 기존 Search Evidence presentation을 함께 확인했다.
- **명령·검증:** frontend `tsc -b --pretty false`, ESLint, Vite production build
  - 결과: `PASS` — TypeScript 오류 0, ESLint 오류 0, Vite build 성공.
- **명령·검증:** `./gradlew.bat test --tests CareerKeywordServiceTest --tests CareerKeywordControllerTest --no-daemon`
  - 결과: `PASS` — service 4, controller 3, failures/errors 0.
- **명령·검증:** `git diff --check` 및 Search production path diff
  - 결과: `PASS` — whitespace 오류 0, `src/main`, `src/test`, `src/integrationTest` 변경 0.
- **명령·검증:** 로그인한 synthetic browser의 tag→detail→TXT/PDF original 흐름
  - 결과: `NOT_RUN` — 이번 worktree를 가리키는 로그인 test account와 fixture를 생성·사용하지
    않았다. 이전 2026-08-10 browser 결과를 이 UI 변경의 실행 증거로 재사용하지 않는다.

이 결과는 OpenSQL 검증을 대신하지 않으며 전체 상태를 `VERIFIED`로 올리지 않는다.

### 2026-08-21 Evidence presentation refinement

이 항목도 확장 source `3af28492`에 포함했다. backend/API, database,
Flyway, keyword normalization·classification, owner/ACTIVE isolation, SearchService와 PRZ-016
search presentation source는 변경하지 않았다.

- keyword preview와 펼친 주변 내용은 이메일, 전화번호, URL, GitHub URL, 이름만 있는 행과
  contact/profile metadata 행을 제외한다. 원본 파일·DB content는 바꾸지 않고 owner-scoped
  `문서에서 보기`는 원문을 그대로 연다.
- category chip은 `키워드 N개`, 상세 header는 evidence 배열 길이와 `totalFrequency`를
  `관련 기록 N개 · 총 M회 언급`으로 한 번만 표시한다.
- source row와 full-width 원본 버튼의 중복을 제거했다. 각 source card는 concise preview,
  optional surrounding-context toggle과 원본 action 하나만 제공하며 처음 세 기록 뒤는 펼친다.
- detail list 복귀는 상단 clickable breadcrumb 하나로 통일했다.

- **명령·검증:** bundled Node frontend presentation tests
  - 결과: `PASS` — 23 pass, 0 fail. synthetic `user@example.com`, `010-1234-5678`,
    `github.com/example`, profile/name metadata가 concise preview와 context에 보이지 않음을 확인했다.
- **명령·검증:** frontend TypeScript, ESLint, Vite production build
  - 결과: `PASS` — TypeScript/ESLint 오류 0, build 성공.
- **명령·검증:** CareerKeyword service/controller focused tests
  - 결과: `PASS` — service 4, controller 3, failures/errors 0.
- **명령·검증:** Docker Compose rebuild and runtime health
  - 결과: `PASS` — `prizm-prz009-tags-ui` frontend/backend images rebuilt; DB healthy,
    backend health `UP`, frontend HTTP `200`.
- **명령·검증:** logged-in synthetic browser detail flow
  - 결과: `NOT_RUN` — synthetic account and documents were not created. Historical browser evidence
    is not reused for this refinement.

### 2026-08-21 Evidence fallback 및 laptop density polish

이 항목은 동일한 확장 source `3af28492`의 presentation-only 변경이다.
CareerKeyword backend/API, keyword extraction·normalization·occurrence, owner/ACTIVE isolation, SearchService와
PRZ-016 production search source, DB/Flyway는 변경하지 않았다.

- preview helper는 contact token과 URL만 제거한 뒤 의미 있는 기술 문구가 남으면 이를 concise preview로
  사용한다. 안전한 문구가 없을 때만 UI가 generic fallback을 사용한다. 추가 context는 concise의 처음 두
  안전 단위를 제외한 뒤 최대 네 단위만 반환하므로 동일 문장을 반복하지 않는다.
- `1121px–1599px`에 공통 compact desktop density를 적용했다. sidebar는 228px, page padding은 36px/30px,
  공통 card·filter·tag padding/gap은 줄이고 body/title/button 글자 크기는 유지했다. `1600px` 이상은 기본
  248px sidebar와 넉넉한 desktop spacing을 유지한다. mobile/tablet 규칙은 변경하지 않았다.
- **명령·검증:** bundled Node presentation test 3 files
  - 결과: `PASS` — 25 pass, 0 fail. synthetic email·phone·URL 제거, `Java / Spring Backend Developer`의
    안전한 잔여 문구 보존, generic fallback 조건, context 중복 제거와 기존 Search presentation을 확인했다.
- **명령·검증:** frontend `tsc -b --pretty false`, ESLint, Vite production build
  - 결과: `PASS` — TypeScript/ESLint 오류 0, Vite build 성공.
- **명령·검증:** CareerKeyword service/controller focused test
  - 결과: `PASS` — `--rerun-tasks` 결과 service/controller 7 pass, failures/errors/skipped 0.
- **명령·검증:** In-app Browser 100% viewport smoke (로그인 전 common layout)
  - 결과: `PASS` — 1366×768과 1440×900에서 compact sidebar 228px, 1920×1080에서 default sidebar 248px,
    390×844 mobile까지 horizontal overflow 0을 확인했다.
- **명령·검증:** authenticated keyword list/detail, surrounding-context expansion, document viewer의 100% viewport
  - 결과: `NOT_RUN` — 실제 사용자 data는 사용하지 않았고, 이번 runtime에서는 synthetic browser session을
    준비하지 못했다. 로그인 전 breakpoint smoke를 해당 화면의 검증 증거로 확대하지 않는다.

### 2026-08-21 Document type folder browse

`DocumentSummary` 목록만 frontend에서 DocumentType별로 grouping했다. supplied PNG는
`frontend/public/assets/prizm-document-folder.png`에 추가했으며 backend/API/DB/Flyway와 Search·CareerKeyword
production source는 변경하지 않았다.

- `?type=` URL과 breadcrumb로 folder 상태를 유지하고, title search는 type을 해제해 전체 문서를 검색한다.
- root의 type/status dropdown은 제거했다. status select는 folder 내부에서만 전체·검색 가능·처리 중·실패로 표시한다.
- **검증:** frontend presentation tests 27 pass, 0 fail; TypeScript/ESLint/Vite build `PASS`; Docker frontend rebuild `PASS`.
- authenticated folder UI browser viewport는 synthetic session이 없어 `NOT_RUN`이다.

### 2026-08-21 Career Vault visual language polish

문서 보관함의 3D folder card를 visual reference로 삼아 로그인·회원가입, sidebar, 문서 카드·상세,
경력 키워드 목록·상세, 내 경험 찾기, 업로드, modal·viewer와 상태 UI를 동일한 soft surface 언어로
정리했다. 기존 component와 route를 유지하고 CSS token·presentation style만 변경했다.

- card radius를 키우고 border 대비를 낮췄으며, blue/black/gray soft background와 낮은 shadow를 공통 적용했다.
- 검색 결과의 outer card와 업로드의 outer form box를 제거해 box 안의 box 반복을 줄였다. 업로드는 guide와
  form을 각각 하나의 명확한 content surface로 구분했다.
- sidebar active item, button, tag, document/result/evidence card는 150–180ms의 작은 elevation과 press feedback을
  공유한다. `prefers-reduced-motion`에서는 해당 transition과 transform을 제거한다.
- 로그인·회원가입의 왼쪽 소개 영역은 독립된 둥근 카드가 아니라 전체 왼쪽 grid column을 채우는 full-bleed surface로
  표시한다. 오른쪽 인증 form panel의 구조와 스타일은 유지한다.
- CSS `zoom`과 전체 layout `scale()`은 사용하지 않았다. existing responsive grid와 desktop compact spacing을 유지했다.
- **명령·검증:** bundled Node frontend presentation tests
  - 결과: `PASS` — 27 pass, 0 fail.
- **명령·검증:** frontend `tsc -b --pretty false`, ESLint, Vite production build
  - 결과: `PASS` — TypeScript/ESLint 오류 0, production build 성공.
- **명령·검증:** Docker Compose rebuild and runtime health
  - 결과: `PASS` — `prizm-prz009-tags-ui` images rebuilt; backend health `UP`, frontend HTTP `200`.
- **명령·검증:** `git diff --check`, prohibited scale와 backend/Search diff 감사
  - 결과: `PASS` — whitespace 오류 0, CSS `zoom`·전체 `transform: scale()` 0,
    backend/Search/DB/Flyway production source 변경 0.
- **명령·검증:** In-app Browser 1440×900 login/signup visual smoke
  - 결과: `PASS` — 100% viewport에서 horizontal overflow 없이 soft intro surface, auth card, input/button state를 확인했다.
- **명령·검증:** authenticated document/keyword/search/upload/detail/viewer browser flow 및 1366×768·1920×1080
  - 결과: `NOT_RUN` — 사용자 후속 지시에 따라 내부 화면 직접 조작을 중단했다. source/static/Docker 결과를
    authenticated browser 증거로 확대하지 않는다.

브라우저 확인을 위해 사용자 계정과 분리된 local-only synthetic account 2개를 만들었으나 문서 업로드,
사용자 계정 접근 또는 기존 data 변경은 수행하지 않았다. 확장 source는 이후 PR #49 merge `550c9d4`로 `main`에 통합됐다.

### 2026-08-21 Reference palette alignment

사용자 제공 palette를 기준으로 공통 UI token을 blue/black/gray 중심으로 정렬했다. layout, component 구조,
route와 기능은 바꾸지 않았고 3D folder asset도 기존 blue 자산을 그대로 유지했다.

- primary/hover는 `#0071e3`/`#0066cc`, soft surface는 `#f5f5f7`로 적용했다.
- foreground/body/muted는 각각 `#1d1d1f`/`#515154`/`#6e6e73`, border는 neutral hairline으로 통일했다.
- red·green은 오류·성공 상태에만 사용해 의미 색상을 분리했다.
- **검증:** palette 변경 뒤 frontend presentation tests 27 pass, TypeScript·ESLint·Vite build,
  Docker runtime health `UP`, frontend HTTP `200`, `git diff --check`를 재실행했다.
  결과는 모두 `PASS`이며 기존 purple literal 검사 결과는 0건, backend production source diff는 0건이다.

### 2026-08-21 Keyword browse layout refinement

키워드 목록 route에서만 정보 밀도를 다시 배치했다. 내 문서의 키워드는 기존 기술 분류 영역을 넓은 주 콘텐츠
surface로 사용하고, 기술 분류는 desktop에서 우측 세로 rail 안에 표시한다. rail은 viewport 높이를 넘으면 내부에서
스크롤한다. 1120px 이하에서는 rail을 주 콘텐츠 아래로 배치하고, mobile에서는 가로 스크롤 pill row로 전환한다.
키워드 선택 뒤의 detail route와 근거 UI는 변경하지 않았다.

최종 polish에서 rail의 불필요한 두 번째 grid 열을 제거해 분류 button이 가용 폭을 채우도록 했고,
제목·항목을 가운데 정렬했다. 키워드가 많을 때는 desktop 주 콘텐츠 목록만 제한 높이 안에서 독립 스크롤하며,
1120px 이하에서는 문서 흐름에 맞게 전체 높이로 펼친다.

- **검증:** frontend unit tests 27, TypeScript, ESLint, Vite production build, `git diff --check` 모두 `PASS`.
  기본 `dist`는 실행 중 컨테이너가 점유하고 있어 build는 별도 verification output에 생성했다. backend/Search
  production source diff는 0건이다.

### 2026-08-21 Document management modal refinement

문서 상세·관리 modal은 API와 상태 계약을 바꾸지 않고 presentation만 Soft Minimal 방식으로 정리했다.

- 기본 정보와 version history는 stacked card 대신 divider·spacing 기반 section으로 바꿨다. 제목·유형 저장은 값이 실제로 바뀐 경우에만 활성화된다.
- `+ 새 버전 추가`를 선택했을 때만 기존 파일 선택·등록 form을 노출한다. 등록 완료 뒤에는 form을 닫고 기존 upload API 응답을 그대로 사용한다.
- version row는 compact history 형식으로 표시한다. active version은 `검색에 사용 중`, 완료된 비활성 version은 `이전 버전 · 검색 제외`로 표기하며, 완료 version의 progress summary/progress bar는 렌더링하지 않는다. in-flight version만 기존 processing summary와 progress를 사용한다.
- danger surface는 작은 `문서 관리` 영역으로 축소했고, 실제 삭제 전 confirmation은 되돌릴 수 없는 삭제 범위를 명확히 고지한다.

다음 2026-08-10 기록은 당시 정규화·category·세 순위 기준, 상위 15개 구름과 순위 밖 목록,
문서별 근거 묶기, TXT/PDF 원본 위치 이동을 구현했던 이전 presentation의 이력이다. 전체 PostgreSQL integration suite와 최종 감사도
완료했다. 다만 OpenSQL opt-in integration은 전용 대상이 구성되지 않아 `NOT_RUN`이며,
PostgreSQL 결과를 OpenSQL 증적으로 확장하지 않기 위해 전체 상태는 `VERIFIED`로
판정하지 않는다.

### 검증한 수직 흐름

```text
PostgreSQL의 owner·ACTIVE 이력서·포트폴리오 조회
↓
overlap 제거와 canonical keyword 집계
↓
category·세 정렬 기준 API 응답
↓
브라우저 keyword 선택과 문서별 근거 확인
↓
TXT 강조·PDF page/search 원본 이동
```

이 절의 과거 자동 keyword 구현 당시 OpenSQL opt-in integration과 browser는
`NOT_RUN`이었으므로 당시 상태는 `IMPLEMENTED_UNVERIFIED`였다.

### 구현 근거

- owner·active·문서 유형 SQL: 과거 `CareerKeywordRepository` (P4에서 제거)
- 별칭·Java 버전 정규화, category, 실제 표기 보존: 과거 `CareerKeywordExtractor`,
  `CareerKeywordCategory` (P4에서 제거)
- 빈도·문서 수 집계와 source 근거: 과거 `CareerKeywordService` (P4에서 제거)
- category filter, 언급 수·문서 수·균형 점수, 문서별 근거와 위치 viewer:
  [`App.tsx`](../../frontend/src/App.tsx),
  [`styles.css`](../../frontend/src/styles.css)
- owner-scoped TXT/PDF original:
  [`DocumentThumbnailService`](../../src/main/java/com/prizm/document/service/DocumentThumbnailService.java),
  [`DocumentThumbnailController`](../../src/main/java/com/prizm/document/controller/DocumentThumbnailController.java)
- PostgreSQL owner·active·별칭 집계: 과거 `CareerKeywordDatabaseIntegrationTest` (P4에서 제거)

Flyway migration과 dependency는 추가하거나 변경하지 않았다. keyword 결과는 요청 시
active chunk에서 계산하며 별도 영구 keyword table이나 생성형 모델을 사용하지 않는다.

### 실행 결과

- **명령·검증:** `.\gradlew.bat test --no-daemon`
  - 결과: `PASS`
  - 실제 범위: 323개 중 308 pass, 기존 조건부 15 skip, 실패·오류 0
- **명령·검증:** `.\gradlew.bat integrationTest --tests com.prizm.infrastructure.CareerKeywordDatabaseIntegrationTest --no-daemon --rerun-tasks`
  - 결과: `PASS`
  - 실제 범위: 실제 PostgreSQL+pgvector에서 owner·active·문서 유형 격리와 canonical 별칭 집계
- **명령·검증:** 전체 `.\gradlew.bat integrationTest --no-daemon --rerun-tasks`
  - 결과: `PASS`
  - 실제 범위: 71개 중 68 pass, 조건부 3 skip, 실패·오류 0. OpenSQL opt-in 1개와 `SecureDirectoryStream` 미지원 시 fail-closed 계약을 확인하는 cleanup 2개가 조건부 skip
- **명령·검증:** `npm run lint` (`frontend`)
  - 결과: `PASS`
  - 실제 범위: ESLint 오류 0
- **명령·검증:** `npm run build` (`frontend`)
  - 결과: `PASS`
  - 실제 범위: TypeScript와 Vite production build 성공
- **명령·검증:** `docker compose config --quiet`
  - 결과: `PASS`
  - 실제 범위: Compose 구성 검증 성공. sandbox의 사용자 Docker 설정 파일 접근 경고는 결과에 영향 없음
- **명령·검증:** `docker compose up -d --build`
  - 결과: `PASS`
  - 실제 범위: backend/frontend 이미지 build와 db health, 컨테이너 재기동 성공
- **명령·검증:** synthetic browser 검증
  - 결과: `PASS`
  - 실제 범위: 25개 기술·10 category, 세 순위 기준, 상위 15개, 문서별 3개 PDF 근거 접기, TXT 강조 2개, PDF `#page=5&search=백엔드`, browser warning/error 0
- **명령·검증:** OpenSQL opt-in integration·browser
  - 결과: `NOT_RUN`
  - 실제 범위: `RUN_OPENSQL_TESTS`가 활성화되지 않았고 전용 대상이 구성되지 않음. PostgreSQL 결과와 분리함
- **명령·검증:** `git diff --check`와 최종 diff 감사
  - 결과: `PASS`
  - 실제 범위: whitespace 오류 0, 금지 경로 0, migration·dependency·license 변경 0, blocking finding 0

브라우저 검증에는 전용 synthetic `USER`, active TXT 1개, 3페이지 PDF source 1개를
사용했다. 검증 후 계정 1개, 문서 2개, version 2개, chunk 4개와 원본 fixture 2개를
정확한 식별자·경로로 확인해 모두 제거했다.

### 요구사항 추적

- **요구사항:** `R1` owner active 이력서·포트폴리오만 사용
  - 근거: repository SQL, 전용 PostgreSQL integration
  - 현재 판정: `PASS`
- **요구사항:** `R2` overlap 제거 빈도·문서 수
  - 근거: assembler·extractor·service unit test
  - 현재 판정: `PASS`
- **요구사항:** `R3` source와 active 원본 version 연결
  - 근거: service/controller test, synthetic browser
  - 현재 판정: `PASS`
- **요구사항:** `R4` PDF·TXT 보안 원본
  - 근거: original service/controller test, browser
  - 현재 판정: `PASS`
- **요구사항:** `R5` 상태·반응형·키보드 화면
  - 근거: React source, lint·build, browser
  - 현재 판정: 범위 검증 `PASS`; 자동 UI test 없음
- **요구사항:** `R6` 기존 검색·처리 계약 보존
  - 근거: 전체 unit·PostgreSQL integration test
  - 현재 판정: unit·전체 PostgreSQL integration `PASS`
- **요구사항:** `R7` canonical 별칭과 source 표기 보존
  - 근거: extractor/service/integration test
  - 현재 판정: `PASS`
- **요구사항:** `R8` category와 빈도·이름 기반 단일 Browse 순서
  - 근거: React source, frontend presentation test
  - 현재 판정: `PASS`
- **요구사항:** `R9` document/version 근거 묶기
  - 근거: React source, browser 3개 근거 접기·펼치기
  - 현재 판정: `PASS`
- **요구사항:** `R10` PDF/TXT 위치 이동
  - 근거: browser TXT mark, PDF page/search fragment
  - 현재 판정: `PASS`

### 남은 Gate

표준 PostgreSQL 구현 범위의 전체 테스트와 감사는 완료했다. 전체 상태를 OpenSQL 범위까지
`VERIFIED`로 올리려면 별도로 구성한 OpenSQL 대상에서 opt-in integration과 필요한 browser
검증을 수행해야 한다. 현재 PostgreSQL 성공을 OpenSQL 검증으로 대체하지 않는다.

### 2026-08-21 P3.6 버전별 삭제와 처리 상태 용어

- **구현:** `DELETE /api/documents/{documentId}/versions/{versionId}`는 owner·document·version을
  잠근 뒤, 검색에 사용 중인 version과 non-terminal processing job은 `409`으로 보호한다. 삭제 가능한
  이전 version은 기존 안전한 file-cleanup 등록 흐름을 거친 뒤 해당 version의 change log, processing
  job, chunk, metadata만 제거한다. document와 active version pointer는 변경하지 않는다.
- **UI:** version history의 삭제 가능한 행에만 확인 절차가 있는 휴지통 icon을 제공한다. 문서 전체
  삭제는 기존대로 모든 version을 함께 삭제한다. 내부 처리 표현은 `검색 준비 중`,
  `문서를 읽고 검색할 수 있게 준비 중`, `검색에 사용 중`,
  `이전 버전 · 검색 제외`로 바꿨다.
- **명령·검증:** focused `DocumentManagementServiceTest`와 `DocumentControllerTest`
  - 결과: `PASS`
- **명령·검증:** `DocumentManagementDatabaseIntegrationTest`
  - 결과: `PASS` (PostgreSQL+pgvector 6 tests, failures/errors 0)
- **명령·검증:** 전체 `./gradlew.bat test --no-daemon`
  - 결과: `PASS` (576 tests, failures/errors 0; 이후 추가한 controller case는 focused test로 재검증)
- **명령·검증:** 전체 `./gradlew.bat integrationTest --no-daemon --rerun-tasks`
  - 결과: `PASS` (114 tests, failures/errors 0, conditional 8 skipped)
- **명령·검증:** frontend Node test 27개, TypeScript build, ESLint, Vite production build
  - 결과: 모두 `PASS`
- **명령·검증:** `docker compose up -d --build` 후 frontend `200`, backend health `200`/`UP`
  - 결과: `PASS`
- **명령·검증:** browser authenticated interaction
  - 결과: `NOT_RUN` (사용자 지시에 따라 직접 브라우저 확인을 수행하지 않음)

### 2026-08-21 문서 마감 감사

- 확장 source의 실제 SHA `3af284920943ba03c6cd109366698c32dc25d3fc`와
  PR #49 merge `550c9d4c5b1852f13335be061d1624d22ddec382`를 spec·plan·status·roadmap·registry에 반영했다.
- 구현 완료 task와 실행하지 않은 browser/OpenSQL Gate를 분리했다. 후자는 `NOT_RUN`이며
  전체 상태를 `VERIFIED`로 올리지 않는 근거다.
- 변경한 Markdown 8개에서 159개 로컬 링크, code fence·후행 공백·Markdown 기본 형식을 검사했다.
  결과는 `PASS`다.
- `git diff --check` 결과는 `PASS`이며, 이 문서 마감에서는 production source·test·migration·dependency를
  변경하지 않았다.

## 2026-08-23 P4 사용자 관리형 Document Tag 전환 — 초기 검증 기록

- **계약 변경:** active 원문을 Java dictionary로 스캔하던 `CareerKeywordExtractor` 계약을
  사용자가 document에 직접 연결하는 SYSTEM/USER tag metadata로 교체했다. 과거 P1~P3
  기록은 당시 구현 evidence로 보존하지만 현재 Production 계약으로 사용하지 않는다.
- **DB/API:** V16에 `tags`, `document_tags`, SYSTEM seed와 정규화 unique/index를 추가했다.
  `/api/tags` 검색·생성·사용 집계·연결 문서 조회와 `/api/documents/{id}/tags` 조회·교체·삭제를
  추가했다. upload의 반복 `tagIds`는 기존 document/version/ChangeLog transaction 안에서
  owner 접근 가능성을 검증하고 연결한다.
- **당시 UI:** 공용 `TagModal`을 upload와 document detail에서 재사용한다. DB 검색, 모달을 닫지
  않는 연속 선택, 정규화 중복 차단, USER tag 즉시 생성과 chip 제거를 제공한다. 경력
  키워드 화면은 실제 연결 tag별 document 수와 문서 제목·종류만 보여 줬다. 이 상세
  계약은 2026-08-24 Evidence 연결 단계에서 기존 Career Evidence Search 기반으로 교체됐다.
- **제거:** `com.prizm.careerkeyword` Production package, 기존 `/api/career-keywords` DTO/API,
  hardcoded alias/category/phrase dictionary, extractor/source assembler와 그 전용 unit·integration,
  frontend keyword API/presentation helper를 제거했다.
- **Search 경계:** `src/main/java/com/prizm/search` diff는 0이다. tag metadata를 embedding,
  query, ranking, localization에 주입하지 않았다.
- **backend focused:** documenttag와 document upload/query/controller focused suite `PASS`.
- **backend 전체 unit:** `./gradlew.bat test --no-daemon` — 577 tests, 0 failures,
  0 errors, 20 skipped, `PASS`.
- **당시 frontend:** lint, typecheck, 24 unit tests, Vite production build 모두 `PASS`.
- **당시 PostgreSQL integration:** `DocumentTagDatabaseIntegrationTest`는 Testcontainers 초기화에서
  Docker client를 찾지 못해 `BLOCKED_BY_ENVIRONMENT`. test assertion은 실행되지 않았으며
  PostgreSQL owner isolation이나 V16 migration을 `PASS`로 기록하지 않았다. 이 환경 blocker는
  2026-08-24 Docker·PostgreSQL 재검증에서 해소됐다.
- **OpenSQL/browser:** `NOT_RUN`.
- **repository safety:** commit/push/reset/clean/stash 변경 없음. 기존 stash 2개와 unrelated
  untracked work를 보존했다.

## 2026-08-24 P4 Tag API 보안 경로 회귀 수정

- **재현:** 인증된 USER가 upload 화면에서 Tag Modal을 열 때 `GET /api/tags`가
  `SecurityConfiguration`의 `denyAll()`에 걸려 `403`을 반환했고, frontend가 이를 세션
  만료로 처리해 로그인 화면으로 이동했다.
- **수정:** 기존 USER 전용 matcher에 `/api/tags`, `/api/tags/**`만 추가했다. 미인증 요청과
  `SYSTEM_ADMIN` 접근 차단, tag service의 owner 격리 규칙은 변경하지 않았다.
- **회귀 test:** `AuthenticationIntegrationTest`에 USER `200`, 미인증 `401`,
  `SYSTEM_ADMIN` `403`을 함께 검증하는 case를 추가했다.
- **focused unit:** `TagControllerTest`, `DocumentTagControllerTest` 5 tests, failures/errors 0.
- **focused PostgreSQL integration:** `AuthenticationIntegrationTest` 32 tests와
  `DocumentTagDatabaseIntegrationTest` 2 tests, failures/errors 0.
- **전체 PostgreSQL integration:** 116 tests 중 108 pass, failures/errors 0, 기존 8 skipped.
- **Docker runtime:** frontend nginx를 통한 합성 USER 가입·로그인 후
  `/api/tags?query=Redis`는 `200`, 미인증 `/api/tags`는 `401`; 합성 계정은 검증 후 삭제했다.
- **browser:** 수정 후 실제 modal interaction은 사용자 수동 확인 대상으로 남겼다.

## 2026-08-24 P4 경력 키워드 Evidence 상세 연결

- **원인:** 경력 키워드 상세 화면이 `GET /api/tags/{tagId}/documents`를 메인 데이터로
  사용해, tag metadata가 연결된 문서 카드만 표시하고 원문 evidence를 조회하지 않았다.
- **연결:** 목록의 tag 이름을 기존 `POST /api/career-evidence/search`의 원본 query로 전달한다.
  tag가 연결된 문서로 검색 범위를 제한하지 않으며, PRZ-016의 owner·ACTIVE 범위와
  relevance·ranking·localization 계약을 그대로 사용한다.
- **UI:** 상세 header를 `관련 기록 N건 · M개 문서`로 바꾸고 문서 제목·종류,
  source/page, snippet과 주변 context를 evidence card에 표시한다. PDF는 기존 viewer를
  해당 page에서 열고, TXT는 기존 문서 상세 route로 이동한다. loading·empty·error와
  재시도 상태를 기존 화면 스타일로 제공한다.
- **호환성:** 경력 키워드 목록의 문서 수는 tag metadata 집계값을 유지한다. 기존 tag
  document API는 삭제하지 않았지만 상세 화면의 메인 source로는 더 이상 사용하지 않는다.
- **frontend focused:** typecheck, ESLint, 28 unit tests, Vite production build 모두 `PASS`.
  Search response mock으로 정확한 `Spring Boot` query, 여러 evidence, 문서 종류 mapping,
  0건과 error 전달을 검증했다.
- **Search 경계:** `src/main/java/com/prizm/search`와 기존 Search API Production source의
  diff는 0이다. 새 Search API나 tag 기반 Search filter를 추가하지 않았다.
- **Docker/browser:** 현재 source로 frontend/backend image rebuild는 `PASS`했다. Codex
  in-app browser에서 host Docker/Vite 주소 접근이 `ERR_CONNECTION_REFUSED`라 실제 인증
  interaction은 `NOT_VERIFIED`; 검증용 합성 계정과 임시 Vite cache는 즉시 제거했다.

## 2026-08-24 P4 태그 동작의 세션 오판 방지

- **원인:** Tag Modal 검색·생성·저장, 문서 상세 tag 제거, 경력 키워드 tag 집계·Search
  연결이 `401 Unauthorized`뿐 아니라 `403 Forbidden`도 세션 만료로 처리했다. 403은
  인증된 요청의 권한/범위 실패이므로 토큰을 지우고 로그인 화면으로 보내면 안 된다.
- **수정:** tag 관련 frontend 흐름은 401에서만 session을 종료하고, 403과 기타 오류는
  현재 화면의 검색·저장·집계 오류 상태로 남긴다. document tag 교체 API의 CORS preflight가
  허용되도록 기존 allowed method에 `PUT`을 추가했다.
- **전 경로 audit:** Docker USER로 tag 검색·USER tag 생성·tag 포함 upload·document detail·
  document tags 조회/교체/삭제·usage·tag documents를 실제 호출해 모두 200/201/204임을
  확인했다. Integration은 같은 7개 tag API 각각의 USER 허용과 미인증 401,
  SYSTEM_ADMIN 403 경계를 고정한다.
- **frontend:** 29 unit tests, typecheck, ESLint 모두 `PASS`.
- **focused integration:** `AuthenticationIntegrationTest` 32 tests, failures/errors 0,
  `PASS`. USER tag를 만드는 새 case에 맞춰 test cleanup에서 USER tag를 users보다 먼저
  제거하도록 FK-safe 순서만 보완했다.
- **Docker:** 현재 backend/frontend source를 다시 build하고 기존 container를 recreate했다.
  DB container와 volume은 유지했다.

## 2026-08-24 P4 최종 검증과 최초 독립 감사 보완

- **현재 계약:** 경력 키워드 목록의 문서 수는 document-tag metadata 집계다. tag 상세는
  선택한 이름을 `POST /api/career-evidence/search` 원본 query로 전달하며, tag가 연결된
  문서가 아니라 현재 owner의 ACTIVE 문서 전체에서 evidence를 찾는다.
- **PostgreSQL/V16:** V16 적용, SYSTEM seed, normalized duplicate, document-tag CRUD,
  USER tag owner isolation과 다른 owner tag 연결 차단을 focused integration에서 확인했다.
- **backend 전체 unit:** 578 tests 중 558 pass, failures/errors 0, 기존 20 skipped.
- **전체 PostgreSQL integration:** 116 tests 중 108 pass, failures/errors 0, 기존 8 skipped.
  이 결과를 OpenSQL 검증으로 확대하지 않는다.
- **migration regression:** P4 전체 diff는 V16의 table·constraint·index·privilege를 검증하도록
  기존 PostgreSQL/OpenSQL compatibility fixture와 assertion을 함께 확장했다. 그 검증 뒤 남은
  stale 실패 4건은 성공 migration 수를 15로 고정한 `CareerPlatformMigrationTest` 3곳과
  `PgVectorInfrastructureTest` 1곳이었고, 해당 교정 단계에서는 이 네 기대값만 16으로 갱신했다.
  Production source는 이 stale assertion 교정으로 변경하지 않았다.
- **frontend:** 45 tests, typecheck, ESLint와 Vite production build가 `PASS`했다.
  실제 Production helper/view를 사용해 Evidence card render·loading/empty/error·문서/page
  navigation, deep-link resolution·retry, 401/403 세션 정책과 Tag Modal/editor의
  선택·생성·중복·저장 표시·잠금·focus 정책을 검증했다. React hook과 browser event를
  실제 DOM에서 구동하는 자동 E2E는 아니며 인증된 상호작용은 아래 `USER_CONFIRMED`로 구분한다.
- **modal 안전성:** USER tag 생성·저장 중 닫기 action을 잠그고 keyboard focus 순환,
  Escape 전파 차단과 종료 뒤 trigger focus 복원을 구현했다. 자동 test는 rendered disabled
  control·callback과 공용 focus policy helper를 검증하며 실제 browser event 자동화를 주장하지 않는다.
- **입력 경계:** NFKC 뒤 빈 문자열이 되는 NBSP 입력과 Unicode 소문자 변환 뒤 DB의
  100자 제한을 넘는 이름을 `INVALID_TAG_NAME`으로 차단한다. U+2028/U+2029 같은 Unicode
  whitespace도 일반 공백으로 접으며 동일 owner 중복 재사용을 unit·PostgreSQL integration에서 확인했다.
- **stale wiring:** 제거된 `/api/career-keywords`, `/api/career-keywords/**` security matcher를
  삭제했으며 Production `career-keywords` 참조가 남지 않았음을 확인했다.
- **인증 브라우저:** 사용자가 upload Tag Modal, USER tag 생성, document detail 추가·삭제,
  경력 키워드 evidence와 tag 관련 로그인 유지 흐름이 모두 정상이라고 확인했다
  (`USER_CONFIRMED`). Agent가 직접 관찰한 browser `PASS`로 표현하지 않는다.
- **Search 경계:** tag 상세는 기존 Search API를 호출할 뿐 PRZ-016 Production Search source,
  embedding, retrieval, ranking과 localization을 변경하지 않았다.
- **최초 독립 감사:** 문서의 과거 연결 문서 Browse 계약과 frontend 회귀 test 부족을
  blocking finding으로 확인했다. 당시 상태를 `IN_PROGRESS`, AUDIT Gate
  `READY_FOR_REAUDIT`로 유지하고 commit/push/PR/merge를 수행하지 않았다.
- **첫 재감사 보정:** Unicode whitespace 중복 우회, 폐기된 P1–P3 문서 경계, 403 세션 오판,
  tag deep-link 무한 loading, 제거 중 modal 경합, PDF focus와 검증 범위 과장을 확인했다.
  일반화된 정규화·상태·modal 정책과 명시적 역사 구간으로 보정한 뒤 최종 source에서
  backend unit·전체 PostgreSQL integration·frontend 검증을 모두 다시 실행했다.
- **최종 독립 재감사:** 구현에 참여하지 않은 별도 감사자들이 backend/security/migration,
  frontend/UX, 문서/Git scope를 읽기 전용으로 나눠 검토했다. 세 감사 모두 P0–P3 finding
  0건, blocking finding 0건으로 `PASS`했다. 이는 Agent 감사이며 GitHub review 증거는 아니다.
- **현재 상태:** 필수 VERIFY와 AUDIT를 통과해 `VERIFIED`, AUDIT Gate `PASS`다.
  구현 commit `1c1d8d2`와 PR #51을 생성했으며 merge는 아직 수행하지 않았다.
- **GitHub review:** `REVIEW_NOT_AVAILABLE_SOLO` — 별도 reviewer는 없으며, 사용자 병합
  승인과 수정에 참여하지 않은 세 관점의 Agent 독립 재감사를 통합 근거로 기록한다.
