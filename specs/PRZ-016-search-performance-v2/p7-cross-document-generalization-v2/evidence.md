# PRZ-016 P7-A v2 Dataset Freeze Evidence

- 상태: `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`
- v1: `PRESERVED — SUPERSEDED_BEFORE_RUN`
- 검색·benchmark·GPT Judge: `NOT_RUN`
- P7-B: `NOT_STARTED`

## Document density correction

v1의 1페이지 요약형 PDF를 직접 수정하지 않고 v2를 별도 생성했다. v2는 모든 사용자에게
2페이지 PDF 이력서와 장문 TXT 포트폴리오를 하나씩 제공한다.

| 항목 | v2 결과 |
|---|---:|
| 합성 사용자 | 4 |
| ACTIVE 문서 | 8 |
| PDF 이력서 | 4개 × 2페이지 = 8페이지 |
| TXT 포트폴리오 | 4 |
| PDF 추출 본문 밀도 | 페이지당 1,076~1,294 normalized characters |
| TXT 크기 | 3,194~4,270 bytes |
| inactive predecessor fixture | 1 |

PDF는 주 경력, 역할, 설계 책임, 운영 결과, 인접 프로젝트, 기술 profile, 교육·활동, 명시적
범위 제한을 포함한다. 8페이지를 전부 PNG로 렌더링해 clipping, overlap, 깨진 한글, 빈 페이지가
각각 0건임을 시각 확인했다.

## Dataset and Ground Truth

- 질문: 48개, Positive 36 / Negative 12
- Direct Experience 8
- Natural Variation 8
- Indirect Problem 8
- Numeric / Identifier 4
- Complex Natural Language 8
- Negative 12
- Positive acceptable anchor 67개: 지정 owner ACTIVE document/source/page에서 확인
- Negative 12개: owner의 모든 ACTIVE 문서에서 forbidden claim 부재 확인
- query 전체 문장 원문 복사: 0

## Leakage review

- P0 72개, P5 48개, P7-A v1 48개 비교
- raw exact duplicate: 각 0
- NFKC normalized duplicate: 각 0
- SequenceMatcher·character-bigram Dice·token Jaccard threshold 초과 후보: 0
- 유사도 상위 16 pair 수동 검토 후 명백한 재작성: 0
- P0/P5 및 v1 project·fact identifier 직접 재사용: 0

## Required SHA-256

Active corpus aggregate는 document path 순으로 `UTF-8 path + NUL + raw bytes + NUL`을
누적해 계산했다.

- corpus aggregate: `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498`
- questions: `85c2e41bba5c293ca5172b48f77f41587d49be996252479ce5a71ed17763b868`
- ground truth: `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680`

문서별·PDF source·rendered page·도구·문서 hash는 `freeze-manifest.json`에 기록한다.

## Audit

- v1 freeze manifest SHA-256:
  `0b46f12562050c58c6d7ccefe940378a5c42550192d0f35dffc7e2599eae3b79`
- v1 frozen asset hash mismatch: 0
- production search source: 30 files, aggregate
  `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`
- production 변경: 0
- 실제 이름·이메일·전화번호·API key·secret: 0
- `git diff --check`: `PASS`
- commit/push/PR: `NOT_RUN`

P7-B는 새 Codex 세션에서 v2 freeze manifest를 먼저 검증하고 v1이 아닌 v2 ACTIVE corpus만
사용해야 한다.
