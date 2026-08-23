# PRZ-009 — 사용자 관리형 Document Tag Plan

> **문서 상태:** `VERIFIED`
> **AUDIT Gate:** `PASS`
> **계획 기준선:** `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`
>
> 구현 전 계획을 보존한다. 실제 결과는 [Tasks](tasks.md)와
> [Evidence](evidence.md)를 따른다.

## 역사 기록 — 폐기된 P1–P3 자동 추출 계획

> 아래 P1–P3 내용은 P4 전환 전 계획의 추적 기록이며 현재 구현 계획이 아니다.

### P1. 키워드 집계 기반

- 목표: owner의 active 이력서·포트폴리오에서 결정적 키워드 집계를 만든다.
- 변경 범위: canonical definition·category·alias, source repository와 overlap 조립.
- 검증: canonical 합산, 실제 원문 빈도와 owner·active·문서 유형 경계를 확인한다.
- Rollback: 신규 `careerkeyword` source를 제거한다.
- 중단 조건: 추론 기반 유사어 병합이나 원문에 없는 keyword 생성이 필요하면 중단한다.

### P2. API와 원본 근거

- 목표: keyword summary와 연결된 source evidence를 제공한다.
- 변경 범위: service·DTO·controller, USER matcher와 TXT·PDF original response 확장.
- 검증: 인증·입력·빈 결과, owner isolation, keyword 60개·evidence 50개 상한을
  확인한다.
- Rollback: 신규 endpoint와 TXT 확장을 함께 되돌린다.
- 중단 조건: 기존 PDF 보안 header나 검색 API 계약을 바꿔야 하면 중단한다.

### P3. 키워드 맵 UX

- 목표: category·빈도 기반 map과 source viewer를 제공한다.
- 변경 범위: route, API client, map·evidence panel, TXT·PDF viewer와 style.
- 검증: filter·정렬·그룹, keyboard·responsive, PDF page와 TXT 첫 일치 이동을
  확인한다.
- Rollback: frontend route와 API client를 제거한다.
- 중단 조건: 새 PDF renderer나 design system dependency가 필요하면 중단한다.

### P1–P3 구현 검증과 감사

- 목표: 전체 PostgreSQL 회귀와 실제 browser 흐름을 확인한다.
- 변경 범위: unit·integration, frontend, Docker와 Evidence·상태 문서.
- 검증: backend, integration, lint·build, Compose·browser와 diff 감사를 실행한다.
- Rollback: 필수 검증 실패 시 `IMPLEMENTED_UNVERIFIED`보다 높이지 않는다.
- 중단 조건: OpenSQL `NOT_RUN`을 `PASS`로 기록하거나 기존 검색이 회귀하면 중단한다.

## P4. 사용자 관리형 Document Tag 전환

1. V16 forward migration으로 `tags`, `document_tags`, SYSTEM seed와 정규화 unique/index를 추가한다.
2. owner-scoped tag search/create/document-link/usage API를 구현한다.
3. 최초 document upload의 선택 tag를 기존 DB transaction 안에서 검증·연결한다.
4. 공용 Tag Modal을 upload와 document detail에서 재사용한다.
5. 경력 키워드 목록은 document-tag usage를 집계하고, tag 상세는 선택한 이름을 기존
   Career Evidence Search 원본 query로 전달해 owner ACTIVE 전체 문서 evidence를 표시한다.
6. 참조가 사라진 CareerKeywordExtractor·dictionary·source assembler와 전용 test를 제거한다.
7. focused 검증 뒤 전체 backend unit·integration, frontend unit·lint·typecheck·build와 diff 감사를 수행한다.
8. 최초 독립 감사 finding을 보완하고 별도 read-only 재감사를 수행한다.

Rollback은 V16을 수정하거나 되돌리는 방식이 아니라, 병합 전 현재 변경을 폐기하거나 병합 후
새 forward migration으로 수행한다. PRZ-016 Search source가 변경되거나 다른 owner USER tag가
노출되면 즉시 중단한다.

## 역사 기록 — 기존 P3 UI·문서 관리 확장 계획

> 아래 P3.1–P3.6과 공통 경계는 이미 통합된 과거 UI·문서 관리 확장의 기록이며,
> 사용자 관리형 Document Tag P4의 현재 계획이나 완료 조건이 아니다.

### P3.1. 태그 Browse UX 단순화

- 목표: 기존 keyword map을 동일 위계의 태그 목록과 근거 상세 Browse 흐름으로 교체한다.
- 변경 범위: frontend route query state, keyword presentation helper·test, `App.tsx`, `styles.css`와
  현행 UX 문서만 변경한다. CareerKeyword API·service·repository·extractor와 Search production source는 변경하지 않는다.
- 검증: fixed frequency/name order, 모든 category chip과 category empty, URL detail/back-forward,
  tag count의 `frequency` 의미, 기존 TXT/PDF original navigation, Search presentation 회귀를 확인한다.
- Rollback: presentation helper와 frontend UI·style만 이전 UI로 되돌린다. API·migration·data rollback은 없다.
- 중단 조건: DTO 또는 existing original endpoint가 부족해 backend contract 확장이 필요하면 구현 전에 중단하고 보고한다.

### P3.2. Evidence presentation refinement

- 목표: Tags Browse 방향을 유지하면서 개인정보 없는 concise preview와 compact evidence scan을 제공한다.
- 변경 범위: keyword frontend presentation helper·test, `App.tsx`, `styles.css`와 PRZ-009 문서만 수정한다.
  CareerKeyword backend/API, SearchService와 PRZ-016 frontend/search presentation source는 변경하지 않는다.
- 검증: synthetic email·phone·URL·GitHub metadata가 preview/context에 보이지 않는지, evidence/source count와
  total frequency의 의미가 분리되는지, 문서 action 중복이 없는지, URL breadcrumb 복귀·TXT/PDF navigation이 보존되는지 확인한다.
- Rollback: frontend presentation helper와 card markup/style만 되돌린다. 원본·DB·API rollback은 없다.
- 중단 조건: preview 제외에 backend content mutation, API 변경 또는 원본 viewer 변화가 필요하면 중단하고 보고한다.

### P3.3. Evidence fallback 및 laptop density polish

- 목표: 개인정보를 제외해도 남는 안전한 evidence 문구를 generic fallback보다 우선하고, 100% zoom의
  1366–1599px Career Vault를 spacing 중심으로 compact하게 만든다.
- 변경 범위: `careerKeywordPresentation.ts`와 해당 presentation test, `App.tsx`, `styles.css`, PRZ-009 문서다.
  backend/API/DB/Flyway, keyword extraction·normalization·occurrence, SearchService와 PRZ-016 production
  search source는 변경하지 않는다.
- 검증: synthetic contact/profile line에서 의미 있는 기술 문구 보존, no-safe-content fallback, concise와
  중복되지 않는 optional context, 1366×768·1440×900·1920×1080 100% 및 mobile smoke, frontend/backend focused
  regression, lint/build, Docker health와 diff audit을 실행한다.
- Rollback: frontend helper/UI/CSS와 documentation만 되돌린다. 원본·DB·API rollback은 없다.
- 중단 조건: safety를 위해 원본 content·backend evidence localization·API contract를 바꿔야 하면 중단한다.

### P3.4. Document type folder browse

- 목표: 문서 보관함 root를 type별 folder grid로 바꾸고 기존 card/detail을 folder 내부에서 재사용한다.
- 변경 범위: frontend presentation helper/test, `App.tsx`, `styles.css`, supplied PNG static asset와 PRZ-009 문서만 변경한다.
  DocumentType/API/service/repository, upload/version, owner isolation, Search·CareerKeyword backend와 DB/Flyway는 변경하지 않는다.
- 검증: grouping·빈 folder 제외·`?type=`/breadcrumb/back-forward·검색 전체 범위·folder status filter·detail 재사용,
  responsive grid, lint/build/backend focused regression/diff를 확인한다.

### P3.5. Career Vault visual language polish

- 목표: 문서 보관함의 3D folder card 감성을 로그인·회원가입과 전체 Career Vault surface에 확장해
  `Soft Minimal + Friendly Productivity SaaS` visual language로 통일한다.
- 변경 범위: 기존 markup 구조를 유지한 `App.tsx`의 최소 presentation 구분과 `styles.css`의 공통
  surface·card·button·input·sidebar·state·modal/viewer 스타일만 수정한다. 새 framework나 dependency는 추가하지 않는다.
- 검증: 문서 root/folder/detail, keyword 목록/detail, search, upload, login/signup을 100% zoom의
  1366×768·1440×900·1920×1080에서 확인하고 frontend tests·lint·build·Docker·`git diff --check`를 실행한다.
- Rollback: P3.5에서 추가한 presentation class와 CSS override만 제거한다. API·backend·DB rollback은 없다.
- 중단 조건: 요구한 시각 계층을 위해 Search/Keyword/Document API, result 계약, PDF navigation 또는
  owner/ACTIVE isolation을 바꿔야 하면 구현을 중단한다.

### P3.6. Version-specific removal and plain-language processing status

- 목표: 문서 전체 삭제와 과거 version 삭제를 명확히 구분하고, 처리 상태를 일반 사용자 언어로 설명한다.
- 변경 범위: owner-scoped `DELETE /api/documents/{documentId}/versions/{versionId}` controller/service/repository 경로,
  기존 document API client·detail modal·status presentation·tests와 PRZ-009 문서다. Flyway, schema, upload,
  ACTIVE activation, SearchService와 result 계약은 변경하지 않는다.
- 안전: document와 version을 owner scope로 잠그고, active version·in-flight version·non-terminal job은 거부한다.
  허용된 version은 기존 전체 삭제와 동일하게 cleanup job 등록 후 change log, job, chunk, version metadata 순으로
  제거한다. 원본 파일은 transaction 밖 cleanup worker가 정리한다.
- 검증: service/controller owner isolation·active/in-flight 거부·terminal historical version cleanup 순서,
  frontend trash confirmation/status copy, 기존 upload/active/search regression, lint/build/Docker/diff audit을 실행한다.
- Rollback: 새 DELETE route 및 version delete UI만 제거한다. 이미 요청된 cleanup job은 기존 cleanup contract로
  완료되며 schema migration은 없다.
- 중단 조건: active pointer를 추측해 재지정하거나 worker fencing/owner isolation을 약화해야 하면 중단한다.

### 공통 위험과 대응

- overlap은 동일 suffix·prefix를 한 번만 조립한다.
- 등록한 alias만 합치며 UI는 API의 frequency/name 순서만 사용한다.
- repository SQL에서 세 owner column과 active pointer·status·문서 유형을 제한한다.
- object URL은 교체·닫기·unmount 때 정리한다.

### Dependency 및 license 영향

- Flyway V1–V13, dependency와 embedding·chunk 계약은 바꾸지 않는다.

### Branch와 통합 경계

- PRZ-008 검색 판정·응답·평가 source는 수정하지 않는다.
- UI·문서 관리 확장은 `3af28492`로 commit하고 [PR #49](https://github.com/jaemin-devlog/PRIZM/pull/49) merge `550c9d4`로 `main`에 통합했다.

### 계획 대비 주요 변경

- 화면 피드백 뒤 기술·공학 keyword 제한, category·alias와 근거 그룹 기능을
  계획 범위 안에서 보강했다.
