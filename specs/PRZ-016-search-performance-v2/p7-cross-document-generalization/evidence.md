# PRZ-016 P7-A Dataset Freeze Evidence

- 상태: `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`
- 검색 실행: `NOT_RUN`
- benchmark 실행: `NOT_RUN`
- GPT Judge 실행: `NOT_RUN`
- P7-B: `NOT_STARTED`

## Frozen dataset

| 항목 | 결과 |
|---|---:|
| 합성 사용자 | 4 |
| ACTIVE 논리 문서 | 8 |
| TXT / PDF | 4 / 4 |
| 비활성 predecessor fixture | 1 |
| 질문 | 48 |
| Positive / Negative | 36 / 12 |
| Ground Truth entry | 48 |

Category는 Direct Experience 8, Natural Variation 8, Indirect Problem 8,
Numeric / Identifier 4, Complex Natural Language 8, Negative 12로 고정했다.

## Pre-search ground truth verification

- Positive acceptable anchor 58개가 지정 owner의 ACTIVE version과 지정 source/page에 존재: `PASS`
- Negative 12개 forbidden claim이 해당 owner의 ACTIVE corpus에 부재: `PASS`
- plan-only, unimplemented, near-number, cross-user, inactive-version-only 거절 근거 존재: `PASS`
- 질문 원문 전체 복사: 0개
- PDF 4개 `pdfplumber` 추출: 각 1 page, 누락 0
- PDF 4 page 렌더링 및 전 페이지 시각 검사: clipping 0, overlap 0, 깨진 한글 0

위 검증은 `tools/validate-p7.py`가 dataset 파일과 PDF text만 읽어 수행했다. PRIZM 검색,
embedding, 데이터베이스, API는 호출하지 않았다.

## Leakage review

- P0 72 query 비교: 완료
- P5 48 query 비교: 완료
- raw exact duplicate: 0
- NFKC/lowercase/문장부호·공백 제거 duplicate: 0
- SequenceMatcher, character-bigram Dice, token Jaccard 후보 threshold 초과: 0
- 낮은 유사도 상위 12 pair 수동 검토 결과 명백한 문장 재작성: 0
- 기존 AirConnect, MoneyWay, TourAPI 및 P0/P5 성과 identifier 재사용: 0
- 신규 질문 내부 normalized duplicate: 0

## Frozen hashes

Active corpus aggregate는 `document path UTF-8 + NUL + raw bytes + NUL`을 document path
오름차순으로 누적한 뒤 SHA-256을 계산했다.

- corpus aggregate: `4ad8a564bee353c877a9c0938d6cf1f866d6d824750b01c5bf5f976b71a25ae1`
- questions: `7e1055db772034e0d7257de781944c9d5ba368888b5d1757baea7f864fcab957`
- ground truth: `cbb78a66f3563ab82d86702220c409a3674ce2a6f5db1ad366795d085e6186f9`

문서별 hash와 inactive fixture, PDF source, rendered page 및 검증 도구 hash는
`freeze-manifest.json`에 기록한다.

## Production and repository audit

- production search source: 30 files
- 시작/종료 aggregate:
  `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`
- `src/main` 및 `frontend/src` P7-A 변경: 0
- synthetic asset 내 실제 이름·이메일·전화번호·API key·secret: 0
- `git diff --check`: `PASS`
- commit/push/PR: `NOT_RUN`

P7-B는 새 Codex 세션에서 먼저 `freeze-manifest.json`을 검증하고, frozen dataset과 Ground
Truth를 수정하지 않은 채 현재 PRIZM 검색만 실행해야 한다.
