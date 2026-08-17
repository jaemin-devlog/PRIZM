# PRZ-016 P7-A v2 Plan

## 문서 구성

| User | Domain | PDF resume | TXT portfolio | 핵심 스타일 |
|---|---|---:|---:|---|
| SYN2-U01 | 건설 자재 배차·계근 | 2 pages | 장문 case study | 운영 수치·책임 범위 중심 |
| SYN2-U02 | 위성 영상 모자이크 처리 | 2 pages | 실험 일지형 | 데이터 품질·배치 최적화 중심 |
| SYN2-U03 | 철도 운행 제어 시뮬레이션 | 2 pages | 설계 결정 기록형 | 장애·일관성·안전 검증 중심 |
| SYN2-U04 | 음악 저작권 카탈로그 | 2 pages | 제품 연대기형 | 검색·수집·권리 충돌 처리 중심 |

## 작업 순서

1. v1 manifest와 production search source hash를 read-only로 검증한다.
2. v2 명세와 synthetic fact matrix를 먼저 고정한다.
3. PDF source와 TXT 포트폴리오를 작성한다.
4. PDF 4개를 생성하고 8페이지를 렌더링·시각 검사한다.
5. 문서를 기준으로 신규 질문 48개와 Ground Truth를 검색 전에 작성한다.
6. owner·ACTIVE·page·anchor·Negative 부재·count·누출을 자동 및 수동 검증한다.
7. production 불변과 민감정보 부재, `git diff --check`를 확인한다.
8. 모든 파일 hash를 계산하고 freeze manifest를 마지막으로 작성한다.
9. 동결 후에는 read-only 재검증만 수행하고 종료한다.

## PDF 제작·검증

- ReportLab과 맑은 고딕을 사용하며 A4 2페이지를 명시적으로 구분한다.
- 제목·경력 timeline·성과 card·본문·기술 tag·footer를 일관되게 구성한다.
- page별 본문 밀도는 충분히 유지하되 8.5pt 미만으로 낮추지 않는다.
- `pdfplumber` 추출과 Poppler PNG 렌더링을 모두 수행한다.
- 최종 PDF는 사용자가 지정한 frozen dataset 경계인 `dataset/documents/`에 둔다.

## 누출·Ground Truth 검증

- P0/P5/v1 raw exact와 NFKC normalized duplicate
- character-bigram Dice, SequenceMatcher, token Jaccard 후보와 수동 판정
- query 전체가 owner document에 그대로 포함되는지 검사
- 기존 project/fact identifier 직접 재사용 검사
- Positive anchor는 지정 ACTIVE version 및 PDF page/TXT section에서 검증
- Negative forbidden claim은 owner의 모든 ACTIVE 문서에서 부재 검증
- cross-user와 inactive-only 근거는 반대 위치에 존재하는지 검증

## 금지·중단

- SearchService, production source, threshold, fallback, identifier, reranking을 수정하지 않는다.
- PRIZM 검색, benchmark, embedding, DB, API, GPT Judge, Hybrid/RRF를 실행하지 않는다.
- 검색 결과를 본 뒤 질문이나 Ground Truth를 수정하지 않는다.
- commit, push, PR을 수행하지 않는다.
- v1 파일 변경 또는 v2 freeze 후 hash mismatch가 발생하면 성공 판정하지 않는다.

