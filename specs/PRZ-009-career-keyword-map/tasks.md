# PRZ-009 작업 목록

현재 상태: `PLANNED` — 구현 미착수

## 구현

- [ ] owner·active·RESUME/PORTFOLIO keyword source query를 구현한다.
- [ ] overlap 조립과 결정적 keyword·frequency 추출을 구현한다.
- [ ] keyword map·evidence API와 USER 보안 경계를 구현한다.
- [ ] TXT/PDF 원본 열람과 기존 PDF 계약을 함께 보존한다.
- [ ] 경력 키워드 route·맵·근거 패널·원본 viewer를 구현한다.
- [ ] 현재 architecture·status·roadmap을 구현 사실과 일치시킨다.

## 검증·감사

- [ ] backend 단위·controller test를 실행한다.
- [ ] PostgreSQL integration test와 두 사용자 격리를 실행한다.
- [ ] frontend lint·build를 실행한다.
- [ ] Docker Compose 구성과 가능한 runtime/browser 흐름을 확인한다.
- [ ] OpenSQL 실행 여부를 PostgreSQL과 분리해 기록한다.
- [ ] final diff, ownership, active version, 기존 검색 회귀를 감사한다.
- [ ] 실제 결과를 `evidence.md`와 Registry 상태에 기록한다.
