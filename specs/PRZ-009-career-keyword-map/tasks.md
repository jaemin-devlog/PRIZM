# PRZ-009 — 경력 키워드 맵 Tasks

> **현재 상태:** `IMPLEMENTED_UNVERIFIED`

## P1. 키워드 집계 기반

- [x] owner·active·RESUME·PORTFOLIO source query를 구현했다.
- [x] overlap 조립과 결정적 keyword·frequency 추출을 구현했다.
- [x] 한영 alias·붙여쓰기·Java version을 canonical keyword로 합산했다.
- [x] 일반 문장어 대신 기술·공학 개념을 추출하도록 제한했다.

## P2. API와 원본 근거

- [x] keyword map·evidence API와 USER 보안 경계를 구현했다.
- [x] summary category·variants와 evidence matched terms를 반환했다.
- [x] 같은 document·version의 evidence를 한 그룹으로 묶었다.
- [x] TXT·PDF original 열람과 기존 PDF 계약을 보존했다.

## P3. 키워드 맵 UX

- [x] keyword route·map·evidence panel·original viewer를 구현했다.
- [x] category filter와 세 정렬 기준을 구현했다.
- [x] PDF page·search 이동과 TXT 첫 일치 강조·scroll을 구현했다.
- [x] responsive·keyboard와 browser 상호작용을 확인했다.

## P4. 검증과 감사

- [x] backend unit·controller와 PRZ-009 PostgreSQL integration을 실행했다.
- [x] 전체 integration은 71개 중 68 pass·조건부 3 skip·실패 0이었다.
- [x] frontend lint·build와 Docker synthetic TXT·PDF browser 흐름을 확인했다.
- [x] owner·active version, 기존 검색과 최종 diff를 감사했다.
- [x] OpenSQL opt-in은 `NOT_RUN`으로 분리 기록했다.

## P3.1. 태그 Browse UX 단순화

- [x] ranking·word-cloud·상위 15개·순위 밖 목록을 고정 크기 태그 목록으로 교체했다.
- [x] API의 `frequency` 내림차순과 keyword 이름 안정 정렬을 presentation helper와 test로 고정했다.
- [x] 모든 backend category enum chip과 category empty state를 제공했다.
- [x] `?keyword=` 상세 URL과 back/forward 동작을 추가하고, 기존 TXT/PDF owner-scoped original viewer를 재사용했다.
- [x] focused frontend·Search presentation·backend regression·lint/build과 diff 감사를 실행했다.
- 검증 제외: 로그인한 synthetic browser 재관찰은 fixture를 만들지 않았으므로 `NOT_RUN`이다.
  2026-08-10의 이전 UI browser 결과를 현재 presentation의 증거로 재사용하지 않는다.

## P3.2. Evidence presentation refinement

- [x] keyword preview/context에서 synthetic contact·URL·profile metadata 행을 제외하는 작은 presentation helper를 추가했다.
- [x] category keyword 수와 evidence 위치 수·총 언급 수를 기존 API 의미에 따라 구분했다.
- [x] 상세 breadcrumb 하나로 목록 복귀를 통일하고 중복 문서 action을 제거했다.
- [x] concise evidence card, 주변 내용 toggle, 최대 3개 초기 표시와 관련 기록 펼치기/접기를 적용했다.
- [x] focused frontend·Search presentation·backend regression·lint/build과 diff 감사를 실행했다.

## P3.3. Evidence fallback 및 laptop density polish

- [x] synthetic metadata가 섞인 profile line에서도 안전한 기술 문구를 보존하고 generic fallback 조건을 test로 고정한다.
- [x] concise preview와 중복되지 않는 추가 context가 있을 때만 toggle을 표시한다.
- [x] 761–1599px 공통 Career Vault shell·page·card·filter/tag spacing을 compact density로 조정한다.
- 검증 제외: authenticated keyword screen의 100% viewport는 synthetic session을 준비하지 않아 `NOT_RUN`이다.
- [x] 로그인 전 공통 breakpoint/mobile smoke, focused regression·lint/build·Docker·diff 감사를 실행하고 기록한다.

## P3.4. Document type folder browse

- [x] existing summary를 type별 folder card로 grouping하고 supplied folder asset을 적용한다.
- [x] `?type=` folder 선택, breadcrumb/back-forward, root title search와 folder-local status filter를 구현한다.
- [x] existing document card/detail을 재사용하고 frontend test/lint/build과 Docker rebuild를 실행했다.

## P3.5. Career Vault visual language polish

- [x] 공통 surface·card·button·input·sidebar·state·modal/viewer를 folder card의 soft visual language로 통일한다.
- [x] 문서 root/folder/detail, keyword 목록/detail, search, upload, login/signup의 기존 기능과 정보 구조를 보존한다.
- [x] 공통 palette를 reference의 blue/black/gray scale로 전환하고 red·green을 의미 상태에만 사용한다.
- [x] 키워드 browse 목록에서 내 문서의 키워드를 주 콘텐츠 영역으로 확장하고, 기술 분류는 데스크톱 우측의 세로 스크롤 rail로 이동한다. 키워드 상세 route는 유지한다.
- [x] 문서 상세·관리 modal의 version history를 compact row로 정리하고 새 버전 upload form을 토글 방식으로 제공한다.
- 검증 제외: authenticated 1366×768·1920×1080 내부 화면은 사용자 지시에 따라 `NOT_RUN`이다.
- [x] 1440×900 login/signup smoke와 frontend tests·lint·build·Docker health·`git diff --check`,
  backend/Search production source 무변경을 확인한다.

## P3.6. Version-specific removal and plain-language processing status

- [x] owner-scoped historical version DELETE API와 active/in-flight 보호를 구현하고 service/controller test를 추가한다.
- [x] version history에 confirmation이 있는 휴지통 icon action을 추가하고 검색에 사용 중인 version은 보호한다.
- [x] 문서 processing 상태와 업로드 안내를 일반 사용자 언어로 정리한다.
- [x] backend/frontend regression, Docker, cleanup/owner/ACTIVE diff audit을 실행하고 evidence에 기록한다.

## 외부 검증 Gate

- OpenSQL opt-in 검증은 전용 target이 없어 `NOT_RUN`이다. 구현 작업은 완료했지만 이 Gate 때문에
  전체 상태를 `VERIFIED`로 올리지 않는다.

## 문서 마감

- [x] 확장 source `3af28492`의 commit·origin push 상태를 spec·plan·evidence와 상태 문서에 반영했다.
- [x] 현재 단일 Browse UI, folder 보관함, 공통 visual language와 version별 삭제 계약을 문서 간 일치시켰다.
- [x] 변경 Markdown의 로컬 링크·code fence·후행 공백과 `git diff --check`를 검증했다.
