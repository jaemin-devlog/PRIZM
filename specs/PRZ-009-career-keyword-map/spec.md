# PRZ-009 — 경력 키워드 맵

## 상태

`IMPLEMENTED_UNVERIFIED` — 구현·전체 PostgreSQL integration·최종 감사 완료, OpenSQL opt-in `NOT_RUN`

기준 source: `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`

구현 source: 현재 `PRZ-009-career-keyword-map` 작업 트리(아직 commit하지 않음)

## 목적과 사용자 흐름

사용자가 등록한 이력서와 포트폴리오의 현재 검색 가능 원문에서 반복되는 기술명과
핵심 단어를 문장이 아닌 키워드로 모아 본다. 키워드 크기는 원문 출현 빈도를
반영하며, 키워드를 선택하면 오른쪽 근거 목록에서 해당 원문과 원본 파일을 다시
확인할 수 있어야 한다.

```text
로그인 → 경력 키워드 → 빈도 기반 키워드 맵 → 키워드 선택
→ 문서·버전·페이지/텍스트 근거 확인 → TXT/PDF 원본 열람
```

이 결과는 CareerFact나 검증된 역량 판정이 아니다. 원문에서 직접 산출한 탐색용
`문서 키워드 인덱스`이며, PRIZM에 등록되지 않은 기술이나 경험을 생성하지 않는다.

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
- 가운데 맵은 빈도 상위 15개 기술 키워드만 연한 보라색 구름 모양 버튼으로
  표시하고, 빈도가 높을수록 글자가 커진다. 순위 밖 기술 키워드는 아래의 작은 버튼
  목록으로 분리한다.
- 사용자는 전체 또는 현재 문서에 존재하는 기술 category 하나를 선택해 맵과 하위 목록을
  필터링할 수 있다.
- 사용자는 `언급 수`, `등장 문서 수`, `균형 점수` 중 하나를 선택할 수 있고 선택한 값이
  상위 15개 순서, 구름 크기, 하위 목록 값에 동일하게 적용된다. 균형 점수는
  `log1p(frequency) * (1 + log1p(documentCount))`로 계산해 한 문서의 단순 반복 영향을 줄인다.
- 키보드 focus, 선택 상태와 빈도에 대한 접근 가능한 이름을 제공한다.
- 오른쪽 패널은 선택 전 안내, loading, 빈 결과, 오류, 근거 목록 상태를 구분한다.
  근거 카드에는 파일 형식과 페이지/텍스트 위치를 강조하고 키워드 주변의 짧은
  문맥만 표시하며 선택 키워드를 강조한다.
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
- 기존 Flyway V1~V13, chunk·embedding 저장, 처리 job, 활성화·실패 복구와 Career
  Evidence API 계약은 변경하지 않는다.

## 제외 범위

- 생성형 chat model, 외부 NLP API와 사용자 문서 외부의 기술 추천
- 키워드 수동 편집·숨김, 숙련도·연차 판정
- CareerFact 후보·확인·거절과 portfolio 생성
- 영구 keyword table, 별도 backfill/worker, 청킹·embedding·검색 score 변경
- PRZ-008의 근거 있음/없음 판정과 검색 API 응답 변경

## 요구사항과 완료 조건

| ID | 요구사항 |
|---|---|
| `PRZ-009-R1` | owner의 active 이력서·포트폴리오에서만 결정적 키워드 맵을 만든다. |
| `PRZ-009-R2` | overlap 중복을 줄인 실제 원문 출현 빈도와 문서 수를 반환한다. |
| `PRZ-009-R3` | 키워드 선택 시 정확한 source 근거와 active 원본 version을 연결한다. |
| `PRZ-009-R4` | PDF와 TXT 원본을 기존 보안 header와 소유권 경계 안에서 열람한다. |
| `PRZ-009-R5` | 화면은 loading·empty·error·selection과 반응형·키보드 동작을 구분한다. |
| `PRZ-009-R6` | 기존 검색·색인·활성화·실패 복구와 PRZ-008 계약을 변경하지 않는다. |
| `PRZ-009-R7` | 등록된 별칭과 Java 버전 표기는 canonical keyword로 합산되고 실제 표기는 근거에 보존된다. |
| `PRZ-009-R8` | category 필터와 세 정렬 기준이 같은 keyword 집합에 결정적으로 적용된다. |
| `PRZ-009-R9` | 같은 document/version의 근거는 문서 카드 하나로 묶이고 추가 근거는 접기·펼치기로 확인한다. |
| `PRZ-009-R10` | PDF는 해당 페이지, TXT는 첫 일치 표기로 owner-scoped 원본 viewer가 이동한다. |

완료에는 backend 단위·controller·PostgreSQL integration test, frontend lint·build,
Docker Compose 구성 확인과 최종 ownership·diff 감사가 필요하다. OpenSQL에서 새 SQL을
실행하지 못하면 OpenSQL 범위는 `NOT_RUN`으로 기록하고 PostgreSQL 결과로 대신하지
않는다.
