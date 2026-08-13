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

## 후속 또는 제외 범위

- [ ] OpenSQL opt-in 검증이 남아 있어 상태를 `VERIFIED`로 올리지 않는다.
