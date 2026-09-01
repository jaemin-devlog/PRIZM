# PRZ-033 Evidence

- 상태: `VERIFIED / BUILD_CHILD_SELECTOR`
- 시작: `PRZ-032-minimal-v3-shadow-comparison@7e9c1361ca47a06a3957e62fdc34e9793c2a9863`
- 공식 code freeze: `03a2285e148aa0a45b032746266fdc9802be690d`
- Production 변경: `0`
- BGE/model 실행: `0`

## Artifact와 실행 순서

- PRZ-032 output file SHA-256: `647bf37eae00d5e8c9b909faf0767befeb69e2b31d77b36fa863d7cb2231b1f7`
- PRZ-032 output canonical SHA-256: `d6b29ce518f9571f7313a92feb7e1d8ac8b4b207d2fb7dc7fa0f8527dfc414a4`
- PRZ-032 report SHA-256: `29af223023a50564aaf276261459b60eb521c3fcd37045588248b0907ffd8847`
- PRZ-032 input SHA-256: `166a8aef77f59d322216d5b1b77cb872d0c18a6e78cfbab07757f281441e83cf`
- BGE-M3 digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- candidate canonical/file SHA-256:
  `9d056dffc19a3e919b0da5bd6fd1ce0b2f3d2b7bb9d0dab892b95de1e8fd3c9b` /
  `b6d70c26164aa5234ad5f49148e490ca8b25571ef040113a7149cec5b4c526da`
- F0/O_CHILD candidate identity SHA-256:
  `6ab67cf3277c97b628a8e5e6ec1e14aabf6fa121da4e2cc0c28d8a0378ec3e17`
- official report SHA-256:
  `700a39a80865af0c83c806e7f284f820448c43f902a4dc66230a38ecbe35f7d8`

Gold-free replay가 frozen `160`개 Passage의 ID, parent, 순서, span을 복원한 뒤 candidate를
봉인했다. `CANDIDATE_INPUT_VERIFIED` 이후에만 Gold를 읽었고, Oracle은 Passage 순서·score·후보를
바꾸지 않았다. PRZ-032 공식 비교와 embedding은 재실행하지 않았다.

첫 code freeze `016eb6ac1e6fbb50c7ce0fd16420362ceb24345c`의 실행은 Gold Parent annotation ID와
parser structural parent candidate ID라는 독립 namespace를 문자열로 비교해 첫 Oracle relation
검증에서 중단됐다. prediction/report/metric은 생성되지 않았다. 이를
`INVALID_PRE_RESULT_VALIDATOR_ATTEMPT`로 보존하고 source-span containment 검증으로 수정했다.

## Failure Stage

Direct-positive `85`건의 배타 분류 결과다.

| Stage | Count |
|---|---:|
| `FINAL_ALREADY_CORRECT` | 46 |
| `TOP_PASSAGE_CHILD_RECOVERABLE` | 32 |
| `LOWER_PASSAGE_RECOVERABLE` | 6 |
| `DEEP_PASSAGE_RECOVERABLE` | 0 |
| `RETRIEVAL_MISS` | 0 |
| `MULTI_ASPECT_SELECTION_ERROR` | 1 |

Top Passage recoverable query는 `9`개 user bundle에 걸쳤다. multi-aspect 1건
`SV3-LF-U103-Q04`의 underlying first Direct Passage는 rank 4다.

## F0와 LOCAL_CHILD_ORACLE

| Metric | F0 | O_CHILD | Gain |
|---|---:|---:|---:|
| Direct Top1 | 0.5412 | 0.9176 | +0.3765 |
| MRR | 0.7576 | 0.9471 | +0.1894 |
| nDCG@5 | 0.7942 | 0.9008 | +0.1066 |
| Recall@5 | 0.9882 | 0.9882 | 0.0000 |
| user-macro Top1 | 0.5880 | 0.9224 | +0.3344 |
| user-macro MRR | 0.7827 | 0.9506 | +0.1679 |

candidate Top1 ceiling `0.9176`을 그대로 회복해 headroom capture ratio는 `1.0000`이다.
Top1 개선은 32건, loss는 0건이며 rank가 악화된 query도 0건이다.

## Profession과 Language Slice

| Profession | F0 Top1 | O_CHILD Top1 | Gain |
|---|---:|---:|---:|
| frontend/mobile | 0.6364 | 0.9545 | +0.3182 |
| backend | 1.0000 | 1.0000 | 0.0000 |
| planning | 0.5000 | 1.0000 | +0.5000 |
| data/AI/infra | 0.4000 | 0.8667 | +0.4667 |
| non-development/general | 0.4545 | 1.0000 | +0.5455 |
| marketing/sales | 0.3333 | 0.8667 | +0.5333 |
| design/product | 0.7333 | 0.8667 | +0.1333 |

| Language | F0 Top1 | O_CHILD Top1 | Gain |
|---|---:|---:|---:|
| Korean | 0.3030 | 0.9697 | +0.6667 |
| English | 0.7838 | 0.9189 | +0.1351 |
| Korean-English mixed | 0.4667 | 0.8000 | +0.3333 |

## Typed와 구조 안전

Typed query `24`건의 state accuracy/macro F1은 양쪽 `1.0000`, false NONE은 `0`이고 selected
Evidence 집합·순서는 정확히 동일하다. 따라서 strict child-only Typed Evidence precision ceiling은
F0와 같은 `0.6316`; 이번 reordering만으로 Typed precision 손실은 회복할 수 없다.

최종 노출 결과의 contamination, fragmentation, duplicate와 새 cross-parent merge는 모두 `0`이며
provenance parity는 `100%`다. 다만 frozen B3 index의 기존 Gold-overlap contaminated Passage
`1/160`은 그대로이며, 이를 전체 구조 contamination `0`으로 재해석하지 않는다.

## SEALED FINAL과 판정

SEALED FINAL combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, manifest SHA-256
`d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`, git tree
`a129080861d7dafd32a9b3b3357b61aebb237e59`은 불변이다. `opened=false`,
`searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`이다.

사전 Gate를 모두 통과했으므로 판정은 `BUILD_CHILD_SELECTOR`다. 다음 Phase는 같은 frozen Passage
ranking에서 실제 atomic Child Selector만 구현·ablation하고 Oracle 결과를 성능 근거로 사용하지
않아야 한다.

## 검증

- focused searchEvaluation unit/integrity tests: `PASS` — 33 tests, failure/error/skip 0
- official PRZ-033 ceiling test: `PASS` (1회 유효 실행)
- `git diff --check`: `PASS`
- `node scripts/verify-oss-readiness.mjs`: `PASS` — Markdown 214 files/776 local links,
  tracked safety 1121 files, external links 97 OK, verifier tests 16/16
- 전체 backend/frontend test: `NOT_RUN` (Production 변경 없음)
