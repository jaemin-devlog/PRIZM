# PRZ-009 작업 목록

현재 상태: `IMPLEMENTED_UNVERIFIED`

## 화면 피드백 개선

- [x] 일반 문장어·프로젝트명 대신 지원 기술·공학 개념만 추출하도록 제한한다.
- [x] 빈도 상위 15개와 순위 밖 기술 키워드를 분리한다.
- [x] 상위 키워드를 연한 보라색의 부유하는 구름 모양으로 표현한다.
- [x] 근거 발췌를 줄이고 파일 형식·페이지/텍스트 위치와 선택 키워드를 강조한다.
- [x] 개선된 backend/frontend 회귀와 Docker runtime을 검증한다.
- [x] 실제 로컬 브라우저 화면과 선택 상호작용을 확인한다.

## 개발자 활용 개선

- [x] 한영 별칭·붙여쓰기·Java 버전 표기를 canonical keyword로 통합하고 category를 부여한다.
- [x] summary에 category와 실제 source variants, evidence에 matched terms를 반환한다.
- [x] 전체/카테고리 필터와 언급 수/등장 문서 수/균형 점수 전환을 구현한다.
- [x] 동일 document/version 근거를 한 카드로 묶고 추가 근거 접기·펼치기를 구현한다.
- [x] PDF page/search 이동과 TXT 첫 일치 강조·스크롤을 구현한다.
- [x] 별칭 집계, category, 근거 매칭과 기존 owner·active 격리를 자동 테스트한다.
- [x] Docker 브라우저에서 필터·정렬·그룹·원본 위치 이동을 확인한다.

## 구현

- [x] owner·active·RESUME/PORTFOLIO keyword source query를 구현한다.
- [x] overlap 조립과 결정적 keyword·frequency 추출을 구현한다.
- [x] keyword map·evidence API와 USER 보안 경계를 구현한다.
- [x] TXT/PDF 원본 열람과 기존 PDF 계약을 함께 보존한다.
- [x] 경력 키워드 route·맵·근거 패널·원본 viewer를 구현한다.
- [x] 현재 architecture·status·roadmap을 구현 사실과 일치시킨다.

## 검증·감사

- [x] backend 단위·controller test를 실행한다.
- [x] PRZ-009 전용 PostgreSQL integration test와 두 사용자 격리를 실행한다.
- [x] 전체 `integrationTest --rerun-tasks`를 실행한다. 71개 중 68 pass·조건부 3 skip·실패 0.
- [x] frontend lint·build를 실행한다.
- [x] Docker Compose build/runtime과 synthetic TXT/PDF browser 흐름을 확인한다.
- [x] OpenSQL 실행 여부를 PostgreSQL과 분리해 기록한다. 현재 source는 `NOT_RUN`.
- [x] final diff, ownership, active version, 기존 검색 회귀를 감사한다.
- [x] 실제 결과를 `evidence.md`와 Registry 상태에 기록한다.
