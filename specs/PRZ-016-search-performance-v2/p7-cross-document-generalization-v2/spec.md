# PRZ-016 P7-A v2 Cross-Document Generalization Dataset

- 상태: `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`
- v1 상태: `PRESERVED — SUPERSEDED_BEFORE_RUN`
- P7-B 상태: `NOT_STARTED`
- 기준 branch: `PRZ-016-search-performance-v2`
- 기준 HEAD: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- 포함된 `origin/main`: `af6145a975031770f807ef4466a78f084e8223a2`

## 목적

P7-A v1은 검색 전에 정상 동결됐지만 PDF 문서가 1페이지 요약 카드 수준이라 실제 이력서의
문서 밀도와 주변 정보가 부족했다. v2는 v1을 수정하지 않고 별도 자산으로 다시 만들어, 처음 보는
사용자의 더 현실적인 이력서·포트폴리오에서 현재 PRIZM 검색의 일반화를 독립 검증할 입력을 제공한다.

이번 단계는 검색 성능 개선이나 측정 단계가 아니다. 검색·embedding·DB·API·GPT Judge를 실행하지
않고 문서, 질문, 검색 전 Ground Truth, 누출 검증과 해시 동결까지만 수행한다.

## v1 보존 계약

- `p7-cross-document-generalization/` 아래 파일은 수정·재생성·삭제하지 않는다.
- v1 freeze manifest SHA-256은
  `0b46f12562050c58c6d7ccefe940378a5c42550192d0f35dffc7e2599eae3b79`다.
- v1은 P7-B에서 사용하지 않으며 상태를 `SUPERSEDED_BEFORE_RUN`으로 기록한다.
- v2는 `p7-cross-document-generalization-v2/`에서 독립된 hash와 manifest를 가진다.

## v2 산출물

- 합성 사용자 4명과 서로 다른 분야·프로젝트·문체
- 사용자별 PDF 이력서 1개(각 2페이지)와 TXT 포트폴리오 1개, ACTIVE 문서 총 8개
- inactive predecessor version fixture 1개
- 사용자별 Positive 9개, Negative 3개, 총 질문 48개
- 검색 전에 작성하고 owner·ACTIVE·source/page·anchor를 기록한 Ground Truth 48개
- P0 72개, P5 48개, P7-A v1 48개에 대한 exact·normalized·near-duplicate 검사
- 문서별·corpus aggregate·questions·ground-truth SHA-256 freeze manifest

## 현실적 문서 밀도 기준

각 PDF 이력서는 정확히 2페이지이며 다음을 포함한다.

- synthetic identity와 요약
- 2개 이상의 경력 또는 프로젝트 구간
- 역할·설계·운영 책임·협업 범위
- 수치 성과와 검증 조건
- 기술 스택·교육 또는 활동
- 계획·검토·타 팀 범위 등 Negative 경계를 판단할 명시적 문장
- 질문 정답과 직접 관계없는 자연스러운 주변 정보

TXT 포트폴리오는 문제, 제약, 선택지, 구현, 장애·검증, 결과, 회고를 갖는 장문 case study로 만든다.

## 질문 구성

| Category | 사용자별 | 전체 |
|---|---:|---:|
| Direct Experience | 2 | 8 |
| Natural Variation | 2 | 8 |
| Indirect Problem | 2 | 8 |
| Numeric / Identifier | 1 | 4 |
| Complex Natural Language | 2 | 8 |
| Negative | 3 | 12 |

질문은 문서 문장을 그대로 복사하지 않는다. Negative는 계획-only, 미구현 기능, 가까운 오답 숫자,
다른 사용자에게만 있는 사실, inactive version 사실을 섞고 owner의 모든 ACTIVE 문서에서 부재를
검증한다.

## Acceptance criteria

1. PDF 이력서 4개가 각각 2페이지이고 TXT 포트폴리오 4개가 장문 구조를 갖는다.
2. PDF 8페이지 전체를 렌더링해 clipping, overlap, 깨진 한글, 빈 페이지가 0건이다.
3. 질문 48개와 Positive 36/Negative 12, category 구성이 정확하다.
4. 모든 Positive anchor가 지정 owner ACTIVE 문서의 source/page에 존재한다.
5. 모든 Negative 주장이 owner ACTIVE corpus에 없고 유사 거절 근거가 존재한다.
6. P0/P5 raw·normalized duplicate 0, 명백한 near duplicate 0이다.
7. v1 질문과 exact·normalized duplicate 0이며 의도 중복은 replacement audit로 별도 기록한다.
8. production search source 30개 aggregate가 시작과 종료에 동일하고 production 변경은 0개다.
9. 검색·benchmark·GPT Judge는 `NOT_RUN`, commit/push/PR은 수행하지 않는다.
10. freeze 이후 v2 파일을 수정하지 않고
    `P7-A v2 DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`으로 종료한다.
