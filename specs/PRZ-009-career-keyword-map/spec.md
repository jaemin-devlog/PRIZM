# PRZ-009 — 경력 키워드 맵

> **상태:** `IMPLEMENTED_UNVERIFIED`
> **유형:** Feature
> **선행 문서:** [PRZ-000](../PRZ-000-platform-baseline/spec.md)
> **핵심 기능 소스:** `d52c6d01a3bef916e80a3c983a43c7b1fad1139b`
> **UI·문서 관리 확장 소스:** `3af28492` (`PRZ-009-keyword-tags-ui`, origin push 완료)
> **통합:** 핵심 기능 merge `5a8ea8d2b85e7d87342e11e96d1d58d1181ab6b8`; 확장 소스 PR·merge `NOT_RUN`
> **최종 확인:** 2026-08-21

## 상태

`IMPLEMENTED_UNVERIFIED` — 구현·전체 PostgreSQL integration·최종 감사와 확장 브랜치 commit/push 완료,
OpenSQL opt-in `NOT_RUN`

시작 기준 source는 `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`이다.

## 목적과 사용자 흐름

사용자가 등록한 이력서와 포트폴리오의 현재 검색 가능 원문에서 확인된 기술명과
공학 개념을 태그로 둘러본다. 이 화면은 숙련도·점수·중요도를 판단하는 분석
도구가 아니라, 이미 확인된 키워드에서 실제 문서 근거로 이어지는 Browse 기능이다.

```text
로그인 → 경력 키워드 태그 목록 → category 선택 또는 키워드 선택
→ 해당 키워드의 문서·페이지/텍스트 근거 확인 → TXT/PDF 원본 열람
```

이 결과는 CareerFact나 검증된 역량 판정이 아니다. 원문에서 직접 산출한 탐색용
`문서 키워드 인덱스`이며, PRIZM에 등록되지 않은 기술이나 경험을 생성하지 않는다.

## 기능 구성

- owner의 ACTIVE 이력서·포트폴리오 chunk를 원문 단위로 조립한다.
- overlap을 제거한 원문에서 등록된 기술명·공학 개념을 canonical keyword로 집계한다.
- API는 keyword, 빈도, 문서 수와 실제 source 근거를 함께 반환한다.
- UI는 category·고정 순서·URL 선택 상태를 적용하고 TXT/PDF owner-scoped viewer로 연결한다.

## 원문과 키워드 계약

- 인증된 사용자가 소유한 `RESUME`, `PORTFOLIO` 문서만 포함한다.
- `documents.active_version_id`가 가리키고 상태가 `ACTIVE`인 version의 chunk만
  포함한다. 과거·처리 중·실패 version은 제외한다.
- TXT는 active version의 chunk 전체를 하나의 원문 단위로, PDF는 페이지별 원문
  단위로 조립한다. 인접 chunk의 동일 suffix/prefix를 한 번만 남겨 overlap으로 인한
  빈도 중복을 줄인다.
- 키워드는 조립한 원문에 실제 등장하고 내장 기술 사전이 지원하는 기술명·개발
  도구·공학 개념과 명시적인 기술 복합어에서만 만든다. 프로젝트명·인명·도메인
  일반어·문장어·조사와 숫자 전용 token은 제외한다.
- 영문 대소문자는 같은 키워드로 정규화한다. 복합 기술어에 포함된 단일 token은
  같은 위치에서 중복 집계하지 않는다.
- `frequency`는 조립한 원문에서 겹치지 않게 확인된 출현 수, `documentCount`는 해당
  키워드가 등장한 서로 다른 문서 수다.
- 키워드 맵은 빈도 내림차순과 이름 오름차순으로 최대 60개를 반환한다. 동일한
  입력 source에는 결정적으로 같은 결과를 반환한다.

## API 계약

### 키워드 정규화와 분류

- 같은 기술의 대소문자, 한글/영문 별칭, 붙여 쓰기는 하나의 canonical keyword로 합친다.
  최소 계약은 `백엔드/Backend`, `프론트엔드/Frontend`, `데이터베이스/DB`,
  `아웃박스/Outbox`, `SpringBoot/Spring Boot`, `NodeJS/Node.js`를 포함한다.
- `Java8`, `Java11`, `Java17`, `Java21`은 `Java` 빈도에 합산하되 실제 문서에서 확인된
  표기는 `variants`와 근거의 `matchedTerms`에 보존한다.
- 각 keyword는 `LANGUAGE`, `FRAMEWORK`, `DATABASE`, `INFRASTRUCTURE`, `MESSAGING`,
  `SECURITY`, `TESTING`, `WEB`, `TOOLING`, `ENGINEERING_CONCEPT` 중 하나의 category를 가진다.
- canonical keyword의 근거 조회는 canonical 표기와 등록된 모든 별칭의 실제 출현을 함께 찾는다.

### 키워드 맵

- `GET /api/career-keywords`
- 성공: `200 OK`
- 응답은 대상 active 문서 수와 `keyword`, `category`, `frequency`, `documentCount`,
  실제 확인 표기 `variants` 목록을 포함한다.
- 대상 문서 또는 추출 가능한 키워드가 없어도 빈 목록은 정상 응답이다.

### 키워드 근거

- `GET /api/career-keywords/evidence?keyword={keyword}`
- `keyword`는 공백일 수 없고 최대 100자다.
- 성공: `200 OK`
- 응답은 정규화된 키워드의 전체 빈도와 최대 50개의 owner-scoped 원문 근거를
  포함한다.
- 각 근거는 document/version 식별자, 문서 제목·유형, 원본 파일명·파일 형식,
  source 위치, 해당 원문의 출현 수와 발췌문을 포함한다.
- 현재 source에서 키워드를 찾지 못하면 `totalFrequency=0`, 빈 근거 목록을
  반환한다. 의미상 검색 성공이나 CareerFact로 바꾸지 않는다.

## 화면 계약

- `/career-vault/keywords`와 사이드바 `경력 키워드` 메뉴를 추가한다.
- 목록은 모든 keyword를 같은 크기의 pill/tag로 표시한다. 글자 크기·색·위치는
  빈도, 문서 수 또는 계산한 점수에 따라 바꾸지 않는다. `frequency`는 작은 숫자로만
  표시하며, 접근 가능한 이름에서는 `문서에서 확인된 언급 N회`로 설명한다.
- 기본 순서는 기존 API 계약과 같은 빈도 내림차순, 같은 빈도면 keyword 이름의 안정
  오름차순이다. ranking selector, 균형 점수, 상위 15개와 순위 밖 목록은 제공하지 않는다.
- 사용자는 전체 또는 backend enum의 모든 기술 category 하나를 선택할 수 있다. 선택한
  category에 keyword가 없으면 해당 category의 empty state를 표시한다.
- 키보드 focus와 category의 `aria-pressed`를 제공하며, 색상만으로 선택 상태를 표현하지 않는다.
- keyword 선택은 `/career-vault/keywords?keyword={canonical keyword}`로 이동한다. browser
  back/forward는 목록과 상세를 자연스럽게 전환한다.
- 상세는 키워드, 실제 문서 발췌, 문서 제목·유형·페이지/텍스트 위치, 추가 근거와
  owner-scoped TXT/PDF 원본 열람만 표시한다. version number와 분석 score는 기본 화면에 표시하지 않는다.
- keyword detail의 concise preview는 이메일, 전화번호, URL, GitHub URL 및 연락처·프로필
  metadata 행을 표시하지 않는다. 이는 화면 presentation만의 제외이며, source 원본·DB content·
  keyword extraction은 변경하지 않고 `문서에서 보기`는 원본을 그대로 연다.
- 상세 상단은 evidence 배열의 위치 수와 `totalFrequency`를 `관련 기록 N개 · 총 M회 언급`으로
  한 번만 표시한다. category chip의 수는 해당 category keyword 개수로 `키워드 N개`로 표시한다.
- 상세 breadcrumb의 `경력 키워드`만 목록 복귀 action으로 제공한다. source row와 큰 원본 버튼을
  중복 표시하지 않고, 각 concise evidence card는 `주변 내용 보기`와 `문서에서 보기` action을 최대 하나씩 제공한다.
- concise preview는 안전한 의미 단위가 남아 있으면 이를 먼저 표시하고, 개인정보·프로필 metadata를
  제외한 뒤 표시할 문장이 전혀 없을 때만 generic fallback을 사용한다. `주변 내용 보기`는 concise preview와
  중복되지 않는 추가 안전 문맥이 있을 때만 제공한다.
- 761px 이상 desktop viewport는 공통 shell·page·card·filter/tag spacing을 100% browser zoom 기준의
  compact density로 표시한다. 1366×768·1440×900·1920×1080에서 같은 visual rhythm을 유지하되,
  mobile/tablet breakpoint 계약과 text/button의 읽기·조작 크기는 줄이지 않는다.
- 같은 문서/version의 여러 페이지 또는 문맥은 하나의 문서 카드로 묶고 대표 근거 하나만
  먼저 표시한다. 나머지는 사용자가 `근거 N개 더 보기`로 펼치거나 다시 접을 수 있다.
- 근거 카드에서 PDF 또는 UTF-8 TXT 원본을 인증된 요청으로 불러와 별도 viewer에서
  열 수 있다. object URL은 닫기·교체·unmount 때 해제한다.
- PDF 근거는 viewer를 해당 `PAGE` source index와 검색어 fragment로 열고, TXT 근거는
  원문 안의 첫 일치 표기를 강조해 가운데로 스크롤한다. source 위치를 직접 눌러도 같은
  위치 이동을 수행한다.
- 좁은 화면에서는 키워드 맵 다음에 근거 목록과 원본 viewer가 쌓인다.

## 보안·보존 계약

- 모든 keyword query는 document·version·chunk의 `owner_user_id`, active pointer와
  `ACTIVE` 상태를 DB query에서 함께 제한한다.
- `SYSTEM_ADMIN`은 개인 `USER` keyword API를 우회하지 않는다.
- original endpoint는 저장 경로를 반환하지 않고 기존 owner-scoped version 확인,
  private no-store, `nosniff`, sandbox와 안전한 파일명 header를 유지한다.
- 기존 PDF original 계약은 유지하고 TXT에는 `text/plain; charset=UTF-8`을 추가한다.
- 기존 Flyway V1–V13, chunk·embedding 저장, 처리 job, 활성화·실패 복구와 Career
  Evidence API 계약은 변경하지 않는다.

## 제외 범위

- 생성형 chat model, 외부 NLP API와 사용자 문서 외부의 기술 추천
- 키워드 수동 편집·숨김, 숙련도·연차 판정
- CareerFact 후보·확인·거절과 portfolio 생성
- 영구 keyword table, 별도 backfill/worker, 청킹·embedding·검색 score 변경
- PRZ-008의 근거 있음/없음 판정과 검색 API 응답 변경

## 요구사항과 완료 조건

### `PRZ-009-R1` — 요구사항

owner의 active 이력서·포트폴리오에서만 결정적 키워드 맵을 만든다.

### `PRZ-009-R2` — 요구사항

overlap 중복을 줄인 실제 원문 출현 빈도와 문서 수를 반환한다.

### `PRZ-009-R3` — 요구사항

키워드 선택 시 정확한 source 근거와 active 원본 version을 연결한다.

### `PRZ-009-R4` — 요구사항

PDF와 TXT 원본을 기존 보안 header와 소유권 경계 안에서 열람한다.

### `PRZ-009-R5` — 요구사항

화면은 loading·empty·error·selection과 반응형·키보드 동작을 구분한다.

### `PRZ-009-R6` — 요구사항

기존 검색·색인·활성화·실패 복구와 PRZ-008 계약을 변경하지 않는다.

### `PRZ-009-R7` — 요구사항

등록된 별칭과 Java 버전 표기는 canonical keyword로 합산되고 실제 표기는 근거에 보존된다.

### `PRZ-009-R8` — 요구사항

category 필터는 backend category 계약을 그대로 사용하며, 목록은 빈도 내림차순과
이름 안정 정렬의 단일 Browse 순서를 사용한다.

### `PRZ-009-R9` — 요구사항

각 source evidence는 compact card 하나로 표시하며, 처음 세 개 뒤의 source evidence는
`관련 기록 N개 더 보기`와 `추가 기록 접기`로 확인한다.

### `PRZ-009-R10` — 요구사항

PDF는 해당 페이지, TXT는 첫 일치 표기로 owner-scoped 원본 viewer가 이동한다.

### `PRZ-009-R11` — 요구사항

keyword evidence의 preview는 개인정보를 최소화하면서 keyword가 발견된 이유를 이해할 수 있는 안전한
문구를 우선 표시하고, 노트북 100% viewport에서도 공통 Career Vault 화면의 정보 밀도를 유지한다.

### `PRZ-009-R12` — 요구사항

문서 보관함 root는 existing document summary를 frontend에서 DocumentType별로 묶은 folder card만 표시한다.
비어 있는 type은 숨기며, folder 선택은 `?type=` URL state·breadcrumb·browser back/forward로 유지한다.
제목 검색은 모든 type을 대상으로 하고, folder 내부에서만 상태 filter와 기존 document card를 제공한다.

### `PRZ-009-R13` — 요구사항

Career Vault의 로그인·회원가입, sidebar, 문서 보관함과 폴더 내부, 경력 키워드 목록·상세,
내 경험 찾기, 문서 업로드, modal·viewer 및 loading·empty·error state는 blue/black/gray palette와
3D folder card의 `Soft Minimal + Friendly Productivity SaaS` 디자인 언어를 공유한다. 공통 surface는
큰 radius·약한 border·soft shadow·정돈된 spacing을 사용하고, card·button·tag hover는 작은 elevation만
제공하며 `prefers-reduced-motion`을 존중한다. 기능·API·result ID/order/count·PDF navigation은 변경하지 않는다.

### `PRZ-009-R14` — 요구사항

문서 상세의 각 과거 version은 owner-scoped 휴지통 action으로 개별 삭제할 수 있다. 삭제는 원본 파일 정리,
change log·processing job·chunk 정리를 기존 문서 삭제와 같은 순서와 안전한 background cleanup으로 수행한다.
현재 검색에 사용 중인 version과 처리 중인 version은 삭제할 수 없으며, 문서 전체 삭제 action은 그대로 유지한다.
상태 문구는 내부 처리 용어 대신 사용자가 이해할 수 있는 문장으로 표시한다. 예를 들어 현재 검색 대상은
`검색에 사용 중`, 새 파일의 진행 상태는 `문서를 읽고 검색할 수 있게 준비 중`, 완료된 비활성 version은
`이전 버전 · 검색 제외`로 표시한다.
이 변경은 활성화, 처리 상태, 검색 결과의 계약을 바꾸지 않는다.

완료에는 backend 단위·controller·PostgreSQL integration test, frontend lint·build,
Docker Compose 구성 확인과 최종 ownership·diff 감사가 필요하다. OpenSQL에서 새 SQL을
실행하지 못하면 OpenSQL 범위는 `NOT_RUN`으로 기록하고 PostgreSQL 결과로 대신하지
않는다.
