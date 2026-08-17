# PRZ-016 P7 Cross-Document Generalization Validation

- P7-A 상태: `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`
- P7-B 상태: `NOT_STARTED`
- 기준 branch: `PRZ-016-search-performance-v2`
- 기준 HEAD: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- 최신 포함 main: `af6145a975031770f807ef4466a78f084e8223a2`

## 목적

P7은 현재 PRIZM 검색이 처음 보는 자연어 질문, 처음 보는 다른 사용자의 이력서·포트폴리오,
두 조건이 동시에 발생하는 상황에서 일반화되는지 독립적으로 검증한다. P7-A는 검색 성능을
측정하거나 개선하지 않고 완전히 새로운 합성 corpus, 질문과 사람이 검토 가능한 ground truth를
검색 전에 만들고 동결하는 단계다.

P7-B는 새 Codex 세션에서 frozen 자산을 수정하지 않고 현재 production 검색을 실행하는 별도
단계다. P7-A 결과나 문서 작성 과정에서 검색 결과를 관찰하지 않는다.

## 고정 기준선

- branch: `PRZ-016-search-performance-v2`
- HEAD: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- `origin/main`: `af6145a975031770f807ef4466a78f084e8223a2`, 현재 HEAD에 포함
- production search source: 30 files, aggregate SHA-256
  `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`
- P0 dataset SHA-256:
  `51414093346646a47c1fb65c934111706e43dc123fc0355344545c4769dea9d6`
- P5 dataset SHA-256:
  `4e28c0fb2b99b31f15640eb39776e04a533938b63db02e1ba0bfec22168532aa`
- P5 ground truth SHA-256:
  `da915300974c27967e859c0586ec1f76347c314c62f0ca57f77b5b64c3e0180d`

P0~P4는 `DONE`, P5는 `DONE — FAIL`, P6와 GPT-J1은 `DONE — NO_GO`다. P7-A는
이 결과를 수정하거나 재해석하지 않는다.

P7-A 동결 완료 후 P7-B는 `NOT_STARTED`이며, 반드시 새 Codex 세션에서 frozen hash 검증 후
실행한다.

## P7-A 산출물

- 서로 다른 합성 사용자 4명
- 사용자별 이력서 1개와 포트폴리오 1개, 활성 논리 문서 총 8개
- 활성 문서 형식 TXT 4개, PDF 4개
- ACTIVE version 격리 검증용 비활성 predecessor version fixture 1개
- 사용자별 positive 9개, negative 3개, 전체 질문 48개
- 검색 전에 작성한 positive/negative ground truth
- 기존 P0 72개와 P5 48개에 대한 exact/normalized/near-duplicate 및 fact 재사용 검사
- 문서별·corpus aggregate·questions·ground-truth SHA-256 freeze manifest

모든 사용자·회사·프로젝트·성과·수치는 합성이며 실제 개인정보나 실제 경력 주장을 포함하지 않는다.

## 질문 구성

각 사용자별 12개를 정확히 다음과 같이 구성한다.

| Category | 사용자별 | 전체 |
|---|---:|---:|
| Direct Experience | 2 | 8 |
| Natural Variation | 2 | 8 |
| Indirect Problem | 2 | 8 |
| Numeric / Identifier | 1 | 4 |
| Complex Natural Language | 2 | 8 |
| Negative | 3 | 12 |

Positive 질문은 원문 문장을 그대로 복사하지 않고 대화형 의미 변형을 사용한다. Negative는 유사
기술, 가까운 숫자, 수행하지 않은 기능, 계획-only, 다른 사용자에게만 있는 사실, inactive version
사실을 섞으며 현재 사용자의 ACTIVE corpus에서 근거 부재를 직접 검토한다.

## Ground truth 계약

검색 실행 전에 각 positive에 다음을 기록한다.

- synthetic user key
- logical document key
- version key와 `ACTIVE` 상태
- `TEXT_CHUNK` line/section 또는 PDF `PAGE` 위치
- acceptable evidence anchor
- 질문이 문서 문장의 복사가 아닌 이유와 판정 설명

각 negative에는 부재 이유, negative 유형, 검토한 ACTIVE 문서, 유사하지만 거절해야 하는 문장과
위치를 기록한다. inactive predecessor의 사실은 ACTIVE 문서 ground truth로 인정하지 않는다.

## 명시적 금지

P7-A에서는 다음을 실행하거나 수정하지 않는다.

- SearchService와 모든 PRIZM 검색·benchmark
- embedding, pgvector query, threshold, fallback, identifier, reranking
- GPT Evidence Judge, Hybrid, RRF, H2
- production `src/main`, runtime config, Flyway, DB schema/index, frontend
- 검색 결과를 본 뒤 query 또는 ground truth 수정
- commit, push, PR

## Acceptance criteria

1. 합성 사용자 4명과 활성 논리 문서 8개(TXT 4/PDF 4)가 존재한다.
2. 질문은 48개이며 positive 36, negative 12와 category 구성이 정확하다.
3. 모든 positive anchor가 지정한 ACTIVE 문서와 source/page에 검색 없이 존재한다.
4. 모든 negative의 주장 근거가 해당 owner ACTIVE corpus에 없고 유사 거절 근거가 기록된다.
5. P0/P5 exact·normalized duplicate 0, 명백한 near duplicate 0, 기존 project/fact 재사용 0이다.
6. PDF를 렌더링해 clipping, overlap, 깨진 한글과 페이지 누락이 0건이다.
7. 문서별·corpus·questions·ground-truth SHA-256을 freeze manifest에 기록한다.
8. production search source hash가 전후 동일하고 production 변경 파일이 0개다.
9. 검색·benchmark·GPT Judge 실행은 `NOT_RUN`이다.
10. `git diff --check`가 통과하고 P7-A를
    `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`으로 종료한다.
