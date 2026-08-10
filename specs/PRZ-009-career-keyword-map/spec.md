# PRZ-009 — 경력 키워드 맵

## 상태

`PLANNED` — Spec·Plan·Tasks 작성 완료, 구현 미착수

기준 source: `83631f13c21eab54ac0f32ebb0f893b6c5acea0f`

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
- 키워드는 조립한 원문에 실제 등장한 영문·한글·숫자 혼합 token과 명시적으로
  지원하는 기술 복합어에서만 만든다. 일반 문장어·조사·불용어와 숫자 전용 token은
  제외한다.
- 영문 대소문자는 같은 키워드로 정규화한다. 복합 기술어에 포함된 단일 token은
  같은 위치에서 중복 집계하지 않는다.
- `frequency`는 조립한 원문에서 겹치지 않게 확인된 출현 수, `documentCount`는 해당
  키워드가 등장한 서로 다른 문서 수다.
- 키워드 맵은 빈도 내림차순과 이름 오름차순으로 최대 60개를 반환한다. 동일한
  입력 source에는 결정적으로 같은 결과를 반환한다.

## API 계약

### 키워드 맵

- `GET /api/career-keywords`
- 성공: `200 OK`
- 응답은 대상 active 문서 수와 `keyword`, `frequency`, `documentCount` 목록을
  포함한다.
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
- 가운데 키워드 맵은 버튼 목록이며 빈도가 높을수록 글자가 커진다. 극단적인 크기
  차이를 막기 위해 로그 비율과 최소·최대 글자 크기를 사용한다.
- 키보드 focus, 선택 상태와 빈도에 대한 접근 가능한 이름을 제공한다.
- 오른쪽 패널은 선택 전 안내, loading, 빈 결과, 오류, 근거 목록 상태를 구분한다.
- 근거 카드에서 PDF 또는 UTF-8 TXT 원본을 인증된 요청으로 불러와 별도 viewer에서
  열 수 있다. object URL은 닫기·교체·unmount 때 해제한다.
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
- 키워드 수동 편집·병합·숨김, 카테고리·숙련도·연차 판정
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

완료에는 backend 단위·controller·PostgreSQL integration test, frontend lint·build,
Docker Compose 구성 확인과 최종 ownership·diff 감사가 필요하다. OpenSQL에서 새 SQL을
실행하지 못하면 OpenSQL 범위는 `NOT_RUN`으로 기록하고 PostgreSQL 결과로 대신하지
않는다.
