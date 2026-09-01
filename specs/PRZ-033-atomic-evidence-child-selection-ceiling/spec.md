# PRZ-033 Atomic EvidenceChild Selection Ceiling

- 상태: `IN_PROGRESS / OFFICIAL_CEILING_NOT_RUN`
- 기준 branch: `PRZ-033-atomic-evidence-child-selection-ceiling`
- 기준 source: `PRZ-032-minimal-v3-shadow-comparison@7e9c1361ca47a06a3957e62fdc34e9793c2a9863`
- Production 적용: `NO_CHANGE`

## 1. 목적과 제품 경계

PRZ-032의 frozen B3 RetrievalPassage 순위와 후보를 바꾸지 않고, 각 Passage 안에서 질문에
직접 답하는 atomic EvidenceChild를 완벽히 선택할 수 있다는 Gold oracle ceiling만 측정한다.
실제 selector, model, scoring 또는 retrieval을 구현하지 않는다. 이 평가는 경력의 진위가 아니라
`query ↔ source EvidenceChild relevance`만 다룬다.

## 2. 입력과 실행 순서

PRZ-032 ignored output/report를 다음 identity로 검증하고 재사용한다.

- output file SHA-256: `647bf37eae00d5e8c9b909faf0767befeb69e2b31d77b36fa863d7cb2231b1f7`
- output canonical SHA-256: `d6b29ce518f9571f7313a92feb7e1d8ac8b4b207d2fb7dc7fa0f8527dfc414a4`
- report SHA-256: `29af223023a50564aaf276261459b60eb521c3fcd37045588248b0907ffd8847`
- BGE-M3 digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`

실행 순서는 `frozen output verify → Gold-free B3 identity replay → candidate input freeze/verify →
Gold join → oracle`다. replay는 embedding과 query를 실행하지 않고 parser/child/passage 구조만
결정론적으로 복원한다. 모든 Passage의 ID, parent, 순서와 source span이 frozen output과 정확히
일치해야 한다. 그 뒤에만 EvidenceChild ID를 Gold relation에 연결한다.

## 3. LOCAL_CHILD_ORACLE

Passage 순서, Dense score, Top20 후보와 provenance는 불변이다. 각 Passage 안에서만 atomic
Child를 `DIRECT_SUPPORT → RELATED → 기타` 순서로 stable partition하고, 같은 relation에서는
원래 source 순서를 유지한다. Passage 순서대로 펼쳐 최대 5 EvidenceChild를 만든다.

Typed query는 PRZ-029가 고른 EvidenceChild 집합·순서와 `FOUND/PARTIAL/NONE` state를 정확히
보존한다. Gold oracle이 Typed eligibility/tier를 우회하거나 제외된 Child를 되살리지 않는다.
Typed precision ceiling은 공식 O_CHILD와 분리해 진단한다. `TOP5_PASSAGE_ORACLE`은 Direct Child
위치 진단일 뿐 ranking 개선치로 주장하지 않는다.

## 4. Failure Stage 계약

Direct-positive query는 아래 순서로 정확히 하나에 분류한다.

1. `MULTI_ASPECT_SELECTION_ERROR`: 둘 이상의 required aspect/group을 요구하며 Top20에
   필요한 Direct Child가 있지만 현재 final Top5가 요구 coverage를 놓쳤다.
2. `FINAL_ALREADY_CORRECT`: 위 coverage 오류가 없고 현재 F0 final rank 1이 Direct다.
3. `TOP_PASSAGE_CHILD_RECOVERABLE`: rank 1 Passage에 Direct Child가 있다.
4. `LOWER_PASSAGE_RECOVERABLE`: 최초 Direct Child가 Passage rank 2~5에만 있다.
5. `DEEP_PASSAGE_RECOVERABLE`: 최초 Direct Child가 rank 6~20에만 있다.
6. `RETRIEVAL_MISS`: Top20 Passage에 Direct Child가 없다.

Multi-aspect 분류에는 underlying first Direct Passage tier도 별도 diagnostic으로 남긴다. 분류
합계는 Direct-positive inventory와 같아야 한다.

## 5. Metric과 안전 계약

F0와 Oracle 모두 동일 PRZ-032 evaluator 정의로 Final Direct Top1, MRR, nDCG@5, Recall@5,
user-macro Top1/MRR, profession/language slice를 계산한다. 추가로 failure-stage count와 distinct
user bundle, typed Evidence precision/state parity, duplicate, contamination, cross-parent,
provenance를 기록한다.

candidate identity와 Passage rank/score/hash는 100% 같아야 한다. Oracle 결과는 Gold Unit의
모든 source span을 하나의 Child가 포함할 때만 Direct다. 서로 다른 Passage·Parent의 Child를
합치지 않는다.

## 6. 사전 동결 Capability Gate

안전/parity/Gold 순서 위반은 `BLOCKED/INVALID`로 즉시 중단한다. 유효한 결과는 다음 고정
순서로 판정한다.

- `BUILD_CHILD_SELECTOR`: user-macro Top1 gain `>= +0.0300`,
  `TOP_PASSAGE_CHILD_RECOVERABLE >= 5`, recoverable user bundle `>= 3`, 지정 회귀 profession
  (`marketing/sales`, `frontend/mobile`, `non-development/general`) 중 하나 이상과 language
  (`KO`, `KO_EN_MIXED`) 중 하나 이상에서 Top1 gain `> 0`, `RETRIEVAL_MISS = 0`, 그리고 모든
  구조 안전 조건 통과
- `PASSAGE_RANKING_FIRST`: 위 조건을 통과하지 못하고 user-macro Top1 gain `< +0.0300`이며
  `LOWER_PASSAGE_RECOVERABLE + DEEP_PASSAGE_RECOVERABLE`이
  `TOP_PASSAGE_CHILD_RECOVERABLE`보다 큼
- `CHILD_SELECTOR_NOT_JUSTIFIED`: 그 밖의 유효 결과

결과를 본 뒤 threshold나 분류 정의를 바꾸지 않는다.

## 7. 범위와 봉인

새 dataset/query, BGE 실행, 실제 Child Selector, Qwen/reranker/Sparse/FTS/RRF/Parent
Dense/Context/QueryPlanner/rewrite/MMR/LLM과 Production 변경은 금지한다. raw per-query 결과는
ignored `local/search-v3-evaluation/prz033/`에만 저장한다.

SEALED FINAL은 combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 유지한다.
