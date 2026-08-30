# PRZ-026 Phase 1 Evidence

- 상태: `IN_PROGRESS / PHASE_1_RETRIEVAL_PASSAGE_PROMISING`
- 기록일: 2026-08-30 (Asia/Seoul)
- 선행 조건: `DEPENDS_ON_PRZ_025`
- 최종 판정: `PROMISING` — 독립 robustness Gate 통과; Production 채택 근거 아님
- Phase 1 역사 판정: `NEEDS_ADJUSTMENT` — standalone heading 회귀
- Phase 1 Adjustment 판정: `NEEDS_ADJUSTMENT` — heading은 제거됐으나 장문 ranking 순증 없음
- Phase 1 Retrieval Passage 판정: `NEEDS_ADJUSTMENT` — 비용과 전체 metric은 개선·유지했으나
  `FRONTEND_MOBILE` 신규 회귀
- Phase 1 Retrieval Passage Robustness 판정: `PROMISING` — 결과 전 입력/Gate 봉인 후 독립 suite 통과
- Production 변경·적용: `0 / NOT_RUN`

## 1. 역사적 Phase 1 시작 상태

1–11절은 commit `a9d093d...`에서 끝난 B1의 역사 기록이며 Adjustment 검증 결과로
소급 해석하지 않는다. 해당 절의 PASS/audit는 B1 source에 한정된다.

| 항목 | 실제 확인 값 |
| --- | --- |
| branch | `PRZ-026-structural-parsing-parent-child` |
| 시작 HEAD / PRZ-025 HEAD | `5f8229f88251938dc5b34588676cc69edf409c99` |
| `origin/main` | `2c8fd5c0d2f62b154642d703a0970389f8abed8e` |
| PRZ-025 merge 여부 | 미병합; branch가 `origin/main`보다 2 commits ahead, 0 behind |
| 시작 worktree | clean |
| 원래 checkout | PRZ-016 local 변경 존재; 별도 worktree로 격리하고 수정하지 않음 |

## 2. Frozen input

| 항목 | 값 |
| --- | --- |
| dataset | `search-v3-fresh-seed-1.0.1` |
| overall SHA-256 | `1f36c4bbb6948b97c4321821cc3d6b8a9e38ab44b81adb1594614c6f7e97289e` |
| SEALED FINAL SHA-256 | `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383` |
| SEALED flags | `opened=false`, `searchExecuted=false` |
| DEV | 3 bundles, 4 ACTIVE documents, 13 queries |
| CALIBRATION | 2 bundles, 3 ACTIVE documents, 8 queries |
| retrieval denominator | direct-support query 14; no-direct query 7은 raw diagnostic only |

## 3. Environment preflight

`http://localhost:11434/api/tags`에서 `bge-m3:latest`, digest
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, embedding length 1024와
embedding capability를 확인했다. 두 경로 모두 이 model의 1024차원 vector와 in-memory cosine을
사용했다. query vector는 문항당 한 번 생성해 A/B가 공유했다.

## 4. A/B 구성과 실행 범위

| 항목 | A Fixed | B Structural Child |
| --- | --- | --- |
| chunk/child | actual Production `TextChunker`, max 800 UTF-16 chars, overlap 120 | general layout block, max 800 code points, global overlap 0 |
| source/retrieval text | 동일 | 기본 동일; table data row 2개만 traced header context 추가 |
| ranking | raw BGE-M3 cosine | raw BGE-M3 cosine |
| query policy | 없음 | 없음 |
| owner/source scope | query의 ACTIVE `userBundleId` | 동일 |

입력은 DEV 3 bundles/4 ACTIVE documents/13 queries와 CALIBRATION 2 bundles/3 ACTIVE
documents/8 queries다. 21문항 중 `DIRECT_SUPPORT`가 있는 14문항만 retrieval metric 분모이고,
7개 no-direct 문항은 expected relation rank 진단만 기록했다. 모든 ACTIVE 문서가 800자 미만이라
A는 문서당 1 candidate다. 따라서 이 run은 긴 fixed chunk의 boundary recall을 검증하지 못하고
Recall@5 이상에 명백한 ceiling이 있다. 결과를 본 뒤 fixture, query, gold 또는 manifest를
수정하지 않았다. PDF fixture도 추가하지 않았다.

## 5. Aggregate quality

| 집계 | Profile | Top1 | MRR | Recall@5 | @10 | @20 | @50 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| query-micro, n=14 | A | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |
| query-micro, n=14 | B | 0.7143 | 0.8452 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |
| user-macro, users=5 | A | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |
| user-macro, users=5 | B | 0.7333 | 0.8556 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |
| DEV, n=8 | B | 0.6250 | 0.7917 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |
| CALIBRATION, n=6 | B | 0.8333 | 0.9167 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |

Gold Evidence Unit, Group와 Parent coverage@5/10/20/50도 A/B 모두 1.0000이었다. 이 동률은
B의 recall 순증이 아니라 작은 owner-scoped candidate pool ceiling이다.

### Profession slice

| Profession group | Direct queries | A Top1 / MRR | B Top1 / MRR | B Recall@5 |
| --- | ---: | ---: | ---: | ---: |
| BACKEND | 3 | 1.0000 / 1.0000 | 0.6667 / 0.8333 | 1.0000 |
| DESIGN_PRODUCT | 3 | 1.0000 / 1.0000 | 0.3333 / 0.6111 | 1.0000 |
| MARKETING_SALES | 2 | 1.0000 / 1.0000 | 1.0000 / 1.0000 | 1.0000 |
| FRONTEND_MOBILE | 3 | 1.0000 / 1.0000 | 0.6667 / 0.8333 | 1.0000 |
| DATA_AI_INFRA | 3 | 1.0000 / 1.0000 | 1.0000 / 1.0000 | 1.0000 |

### Language slice

| Language | Direct queries | A Top1 / MRR | B Top1 / MRR |
| --- | ---: | ---: | ---: |
| KO | 4 | 1.0000 / 1.0000 | 0.5000 / 0.7500 |
| EN | 4 | 1.0000 / 1.0000 | 0.7500 / 0.8750 |
| KO_EN_MIXED | 6 | 1.0000 / 1.0000 | 0.8333 / 0.8889 |

## 6. Query-level raw rank

양성 행은 첫 `DIRECT_SUPPORT` rank/score, no-direct 행은 첫 annotated relation
(`CONTRADICTS`, `RELATED` 또는 `INSUFFICIENT`)의 diagnostic rank/score다. `—`는 source absence가
gold인 no-answer 질문이다. diagnostic rank를 answerability FPR로 해석하지 않는다.

| Query | Gold | A rank / cosine | B rank / cosine | Direct rank delta |
| --- | --- | ---: | ---: | ---: |
| SV3-U01-Q01 | DIRECT | 1 / 0.6600 | 1 / 0.6720 | 0 |
| SV3-U01-Q02 | DIRECT | 1 / 0.4951 | 1 / 0.4855 | 0 |
| SV3-U01-Q03 | diagnostic | 1 / 0.5431 | 1 / 0.6085 | n/a |
| SV3-U01-Q04 | DIRECT | 1 / 0.5479 | 2 / 0.5521 | +1 |
| SV3-U01-Q05 | no answer | — | — | n/a |
| SV3-U04-Q01 | DIRECT | 1 / 0.5517 | 2 / 0.5396 | +1 |
| SV3-U04-Q02 | diagnostic | 1 / 0.5661 | 1 / 0.6640 | n/a |
| SV3-U04-Q03 | DIRECT (partial) | 1 / 0.6061 | 3 / 0.5947 | +2 |
| SV3-U04-Q04 | DIRECT | 1 / 0.5309 | 1 / 0.5465 | 0 |
| SV3-U06-Q01 | DIRECT | 1 / 0.6041 | 1 / 0.6443 | 0 |
| SV3-U06-Q02 | diagnostic | 1 / 0.5824 | 1 / 0.6276 | n/a |
| SV3-U06-Q03 | diagnostic | 1 / 0.4838 | 1 / 0.7627 | n/a |
| SV3-U06-Q04 | DIRECT | 1 / 0.5774 | 1 / 0.5717 | 0 |
| SV3-U02-Q01 | DIRECT | 1 / 0.5532 | 1 / 0.6781 | 0 |
| SV3-U02-Q02 | DIRECT | 1 / 0.4605 | 1 / 0.5750 | 0 |
| SV3-U02-Q03 | diagnostic | 1 / 0.5300 | 1 / 0.6736 | n/a |
| SV3-U02-Q04 | DIRECT | 1 / 0.5262 | 2 / 0.6160 | +1 |
| SV3-U03-Q01 | DIRECT | 1 / 0.5493 | 1 / 0.6468 | 0 |
| SV3-U03-Q02 | DIRECT | 1 / 0.5039 | 1 / 0.5933 | 0 |
| SV3-U03-Q03 | DIRECT | 1 / 0.6232 | 1 / 0.6464 | 0 |
| SV3-U03-Q04 | diagnostic | 1 / 0.5745 | 1 / 0.6934 | n/a |

## 7. Construction quality and cost

| Metric | A | B | 변화 |
| --- | ---: | ---: | ---: |
| candidate / embedding count | 7 / 7 | 38 / 38 | +442.86% |
| DEV / CAL candidate | 4 / 3 | 22 / 16 | +18 / +13 |
| length min / avg / max (code points) | 93 / 244.86 / 480 | 5 / 43.89 / 176 | avg -82.07% |
| fragmented active Gold Units | 0/20 | 0/20 | 동률 |
| contaminated candidates | 4/7 (57.14%) | 0/38 (0%) | -57.14pp |
| duplicate group mappings | 0 | 0 | 동률 |
| table rows with traced header context | 0 | 2 | source block ID 추적 |
| construction wall time | 1.726ms | 6.944ms | +5.218ms |
| embedding wall time | 209.485ms | 413.514ms | +204.029ms |
| total indexing wall time | 211.211ms | 420.458ms | +99.07% |

wall time은 단일 로컬 실행의 작은 batch 측정이며 Production scale latency로 일반화하지 않는다.

## 8. Query latency

21 query embedding은 A/B가 공유했고 p50 `24.516ms`, p95 `36.824ms`였다.

| Metric | A | B | delta |
| --- | ---: | ---: | ---: |
| query total p50 | 24.5715ms | 24.6127ms | +0.0412ms |
| query total p95 | 36.9969ms | 37.1289ms | +0.1320ms |
| ranking-only p50 | 0.0750ms | 0.0883ms | +0.0133ms |
| ranking-only p95 | 0.1726ms | 0.3754ms | +0.2028ms |

## 9. 개선과 회귀 사례

구조적으로 확인된 개선은 서로 다른 Gold Parent가 한 candidate에 함께 들어간 비율이
57.14%에서 0%로 줄었고, active Gold Unit 20개를 하나의 child가 보존해 fragmentation 0%를
유지한 점이다. DATA_AI_INFRA 3건과 MARKETING_SALES 2건은 Top1/MRR 1.0을 유지하면서 table row
header provenance와 parent separation을 보존했다. 이는 ranking 순증이 아니라 contamination
감소를 동반한 비회귀 사례다.

순위 회귀 4건은 모두 standalone heading child가 본문 direct child보다 먼저 나온 경우다.

- `SV3-U01-Q04`: `장애 재발 방지` heading rank 1, direct body rank 2
- `SV3-U04-Q01`: `사용자 조사` heading rank 1, research/design direct body rank 2
- `SV3-U04-Q03`: `사용자 조사` heading rank 1, other-actor evidence rank 2, self direct body rank 3
- `SV3-U02-Q04`: `Accessibility release` heading rank 1, direct accessibility body rank 2

따라서 heading을 독립 Evidence Child로 색인하는 정책, heading-only eligibility와 body 연결 방식은
Structural Parser/Child 안에서 먼저 조정해야 한다. 이 결과를 Parent Context(C)의 성공 근거로
사용하지 않는다.

## 10. Report와 판정

- ignored local report: `local/search-v3-evaluation/prz026/structural-child-dense-v1.json`
- report SHA-256: `45f049f8466f5bdd21f18525f640e2d2ccc2337fa7bab67b7e4c879fef50a55e`
- A/B 검색 대상: DEV/CAL only
- SEALED FINAL 검색/prediction/result: `NOT_RUN / 0 files`
- `opened=false`, `searchExecuted=false`
- `CURRENT_FRESH_BASELINE = NOT_RUN` — A는 full Current Search가 아닌 fixed-chunk raw Dense ablation

최종 판정은 `NEEDS_ADJUSTMENT`다. B는 contamination을 제거했지만 Candidate Recall 순증 0,
Top1 -28.57pp, MRR -0.1548, candidate/embedding +442.86%, indexing wall time +99.07%였다.
현 상태로 `PROMISING` 또는 Production 후보라고 할 수 없다. heading-only child 정책과 긴 문서
DEV/CAL coverage를 Structural Parser 단계에서 먼저 보완·재검증해야 하며, Parent Context(C)로
진행하는 것은 아직 안전하지 않다.

## 11. Validation과 audit

| 명령/검사 | 실제 결과 |
| --- | --- |
| `git fetch origin --prune --tags` | `PASS`; `origin/main@2c8fd5c...` |
| PRZ-026 unit test filter | `PASS`; 20 tests, failure/error/skipped 0 |
| successful DEV/CAL benchmark test | `PASS`; 1 test, failure/error/skipped 0, local report 생성 |
| 최초 benchmark 명령의 뒤쪽 `-D` option | `COMMAND_ERROR_BEFORE_TEST`; Gradle이 task로 해석해 model/data 호출 전 종료, 기본 output 명령으로 재실행 |
| PRZ-025 deterministic validator | `PASS`; counts/distribution/hash finding 0 |
| PRZ-025 validator support test | `PASS`; 18/18 |
| `node scripts/verify-oss-readiness.mjs` | `PASS`; Markdown 185, local links 764, tracked safety 919, verifier 16/16, external 97/97 |
| `git diff --cached --check` | `PASS`; 최초 EOF blank-line 8건을 수정한 뒤 출력 0 |
| PRZ-026 allowlist | `PASS`; staged 20 paths, violation 0 |
| forbidden scope | `PASS`; Production/src-test/integration/frontend/migration/dependency/MCP/SEALED diff 각 0 |
| report-to-evidence contract audit | `PASS`; recall/top1/contamination/final flags mismatch 0 |
| secret/credential pattern scan | `PASS`; finding 0 |
| SEALED FINAL after run | `PASS`; combined hash 유지, `opened=false`, `searchExecuted=false`, result-like file 0 |
| full backend `test` / `integrationTest` | `NOT_RUN`; Production/backend source 변경 없음, 관련 evaluation source set test만 실행 |
| frontend lint/build | `NOT_RUN`; frontend 변경 0 |
| Docker/PostgreSQL integration | `NOT_RUN`; in-memory cosine + local Ollama evaluation이며 DB/Production path 범위 밖 |
| PR/push/main merge | `NOT_RUN`; 금지 범위 |

역사적 Phase 1 Agent read-only audit의 blocking finding은 0이었다. 비차단 한계는 7개 short TXT bundle의
Recall ceiling, PDF/long-document fixture 0, standalone heading ranking 회귀, 단일 local latency
sample, no-answer selector 미구현이다. 이는 `NEEDS_ADJUSTMENT`와 Parent Context 진입 차단 이유에
반영했다. 이 audit는 GitHub review 증거가 아니다.

## 12. Phase 1 Adjustment 시작 상태

| 항목 | 실제 확인 값 |
| --- | --- |
| branch | `PRZ-026-structural-parsing-parent-child` |
| Adjustment 시작 HEAD | `a9d093dd48e99a8d19675b3a8caa09c794d2888b` |
| `origin/main` | `2c8fd5c0d2f62b154642d703a0970389f8abed8e` |
| PRZ-025 dependency | `5f8229f88251938dc5b34588676cc69edf409c99` |
| 시작 worktree | clean |
| 이전 판정 | `NEEDS_ADJUSTMENT`; C/D `NOT_RUN` |
| SEALED FINAL | `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, `opened=false`, `searchExecuted=false` |

## 13. 변경 전 회귀 재현

Adjustment 코드를 수정하기 전에 기존 benchmark를 현재 HEAD에서 다시 실행했다. report SHA-256은
`c6789ddf4b0573db868462a214502bd0bbd2807b503ea36cec4fef6219b2fe02`였고 Top1 `0.7143`,
MRR `0.8452`와 네 heading 회귀가 재현됐다. 아래 source/retrieval text는 동일하며 heading에는
자기 block ID, 본문에는 바로 앞 heading ID가 parent candidate로 기록됐다.

| Query | rank 1 / type / score / parent | DIRECT rank / score / parent | exact source = retrieval |
| --- | --- | --- | --- |
| `SV3-U01-Q04` | `SB-0002-C01` / `HEADING` / 0.623402 / `SB-0002` | 2 / 0.552143 / `SB-0002` | rank 1 `장애 재발 방지`; direct `본인은 반복 장애의 로그와 배포 이력을 대조해 원인을 좁혔다.\n재발 방지 체크리스트를 작성하고 월별 복구 훈련을 운영했다.` |
| `SV3-U04-Q01` | `SB-0002-C01` / `HEADING` / 0.628311 / `SB-0002` | 2 / 0.539592 / `SB-0002` | rank 1 `사용자 조사`; direct `본인은 소상공인 12명을 인터뷰해 주문 취소 과정의 혼란 지점을 분류했다.\n관찰 결과를 토대로 단계 명칭과 오류 안내 문구를 다시 설계했다.` |
| `SV3-U04-Q03` | `SB-0002-C01` / `HEADING` / 0.663702 / `SB-0002` | 3 / 0.594701 / `SB-0002` | rank 1 `사용자 조사`; direct는 위 사용자 조사 본문, rank 2는 다른 actor의 A/B test 본문 |
| `SV3-U02-Q04` | `SB-0002-C01` / `HEADING` / 0.738326 / `SB-0002` | 2 / 0.615965 / `SB-0002` | rank 1 `Accessibility release`; direct `I led keyboard-navigation and screen-reader improvements... findings from 18 to 4.` |

## 14. 변경 전 짧은 Child 분석

Phase 1의 38개 Structural Child를 query 결과와 source span으로 다시 대조했다.

| 길이(code points) | Child | Gold-mapped Child | direct-query rank 1 | direct rank-1 miss |
| --- | ---: | ---: | ---: | ---: |
| 1–10 | 8 | 0 | 3 | 3 |
| 11–20 | 10 | 0 | 0 | 0 |
| 21–40 | 6 | 1 | 2 | 1 |
| 41–80 | 6 | 3 | 2 | 0 |
| 81+ | 8 | 5 | 7 | 0 |

1–10자 noise의 원인은 길이 자체가 아니라 context-only여야 할 heading이었다. 따라서 최소 길이
threshold나 인접 Child merge는 도입하지 않았다. 서로 다른 heading/Gold Parent를 다시 섞을 위험을
감수할 근거도 없었다.

## 15. Structural Child v2 변경

- `HEADING`은 source block, parent boundary와 provenance로 유지하되 검색 Child로 만들지 않는다.
- paragraph/list/table/key-value의 `retrievalText`에는 heading을 붙이지 않는다. B2는 Parent Context가 아니다.
- 날짜·수치·inline value가 있는 독립 assertion과 `key: value`는 evidence-bearing block으로 보존한다.
- `정보처리기사\n2026.08 취득`처럼 짧은 label 바로 뒤에 compact date/value가 오는 구조는 하나의
  paragraph로 보존한다.
- Phase 1 실패 문자열이나 자격증·기술명 사전은 사용하지 않았다.
- 길이 기반 후보 제외, 인접 병합, global overlap은 추가하지 않았다.

## 16. Long-form DEV/CAL 1.1.0 freeze

| 항목 | 값 |
| --- | --- |
| dataset | `search-v3-fresh-devcal-1.1.0` |
| previous | `search-v3-fresh-seed-1.0.1` |
| combined SHA-256 | `a1fcd76c93dfc52dbd1c201c069f9fc1e1fd3ab25d99759f6c8cc686e47d41df` |
| lineage revision semantics | `materializationBaseRevision=a9d093d...`; generator/실행 source revision이라고 주장하지 않음 |
| execution policy | `DEV_CAL_EVALUATION_ALLOWED`; 실제 실행은 manifest flag가 아니라 ignored report와 이 evidence에 기록 |
| split | DEV 3 documents/12 queries, CALIBRATION 3 documents/12 queries |
| document length | 1,929–4,823 code points; 3,000+ 2 documents |
| profession | DESIGN_PRODUCT, DATA_AI_INFRA, MARKETING_SALES, FRONTEND_MOBILE, PLANNING, NON_DEVELOPMENT_GENERAL 각 1 |
| language | KO 2, EN 2, KO_EN_MIXED 2 documents |
| Gold | 24 parents, 22 groups/units, source-grounded exact spans |
| query | 24; direct-support metric 대상 15 |
| source | synthetic, Apache-2.0, 개인정보 0 |

category는 semantic paraphrase 7, abstract competency 4, numeric 7, other actor 4, negation 5,
completion state 7, multi-evidence 3, hard negative 10을 포함한다. 개발 직무 문서는 DATA_AI_INFRA와
FRONTEND_MOBILE 2/6로 절반 이하이며, 각 bundle은 고유 template/generator seed lineage를 가진다.

PDF는 `BLOCKED_FOR_LATER_LAYOUT_PHASE`다. 현재 loader는 한 version을 단일 `StructuralDocument`와
nullable page에 연결하므로 multi-page PDF를 조용히 추가하면 page-local gold/runtime 모델까지 바뀐다.
이 ablation에서 Production parser를 수정하거나 그 계약을 확장하지 않았다.

## 17. Original Seed B2 재평가

동일 `bge-m3`, 1024 dimensions, cosine, query vector 공유 조건이다.

| Metric | A Fixed | B1 historical | B2 Adjustment |
| --- | ---: | ---: | ---: |
| candidate/embedding | 7 | 38 | 17 |
| Top1 | 1.0000 | 0.7143 | 0.9286 |
| MRR | 1.0000 | 0.8452 | 0.9643 |
| Recall@5/10/20/50 | 1/1/1/1 | 1/1/1/1 | 1/1/1/1 |
| contamination | 57.14% | 0% | 0% |
| fragmentation | 0% | 0% | 0% |
| heading candidate/rank1 | n/a | 21/4 | 0/0; context-only 21 |

네 기존 query 중 `U01-Q04`, `U04-Q01`, `U02-Q04`는 direct rank 1로 회복했다.
`U04-Q03`은 heading이 사라졌지만 다른 actor의 A/B test가 rank 1, 본인 인터뷰 근거가 rank 2여서
직접 Top1 회귀 하나가 남았다. actor/reranker 정책은 이 Phase 범위가 아니므로 수정하지 않았다.

Original B2 user-macro는 Top1 `0.9333`, MRR `0.9667`이다. DESIGN_PRODUCT만 Top1 `0.6667`,
MRR `0.8333`이고 다른 직무 group은 1.0이다. KO/EN은 1.0, mixed는 Top1 `0.8333`, MRR
`0.9167`이다.

## 18. Long-form A/B 결과

최종 ignored local report는
`local/search-v3-evaluation/prz026/structural-child-dense-v2-adjustment.json`, SHA-256은
`663ef12f068a2c8bbe9a0bd65def0e925de9061b57c0c833ece72f95e2fc12e4`다. report가 기록한
evaluation source 7개 파일의 content-addressed combined SHA-256은
`472e85576c9092652b6dfbcf324fd359711bc3bf2c19576d802238c9c7dedfd7`이며,
`a9d093d...`는 Adjustment 시작 commit으로만 기록된다. report 내부 Original/Long-form decision과
top-level final decision은 모두 `NEEDS_ADJUSTMENT`다.

| Metric | A Fixed | B2 Structural |
| --- | ---: | ---: |
| candidate/embedding | 28 | 128 |
| length min/avg/max | 212 / 730.64 / 800 | 25 / 130.86 / 338 |
| Top1 | 0.8000 | 0.8000 |
| MRR | 0.8833 | 0.8833 |
| Recall@5/10/20/50 | 1/1/1/1 | 1/1/1/1 |
| Unit/Group/Parent coverage@5+ | 1/1/1 | 1/1/1 |
| contamination | 20/28 (71.43%) | 0/128 (0%) |
| fragmentation | 0/22 | 0/22 |
| duplicate mapping | 0 | 0 |
| heading candidate/rank1 | n/a | 0/0; context-only 30 |

Phase 1부터 유지된 source-table header context 예외는 Original 2 Child, Long-form 9 Child에
활성화됐다. C의 Evidence Parent/section/heading context는 `NOT_RUN`이며, 둘을 같은 의미로
표현하지 않는다. Plain table의 첫 row를 header로 보는 기존 heuristic은 headerless table에서
모호할 수 있으나 이번 heading-only treatment와 섞어 조정하지 않았다.

Fixed chunk당 overlapping Gold Parent 분포는 1 parent 8개, 2 parents 18개, 3 parents 1개,
4 parents 1개다. Structural은 128개 모두 1 parent다.

Long-form user-macro Top1은 A/B 모두 `0.8056`, MRR은 모두 `0.8889`다. profession별 B2는
PLANNING Top1 `0.5→1.0`, DATA_AI_INFRA MRR `0.75→0.8333`으로 개선됐지만,
NON_DEVELOPMENT_GENERAL Top1 `1.0→0.5`, MARKETING_SALES MRR `0.8333→0.75`로 회귀했다.
FRONTEND_MOBILE과 DESIGN_PRODUCT는 동률이다. 언어별 B2는 KO Top1 `0.8→1.0`, EN
`0.875→0.75`, mixed Top1 동률 0.5/MRR `0.75→0.625`다.

직접 query rank 개선은 `SV3-LF-U102-Q03` 4→2와 `SV3-LF-U105-Q02` 2→1이다. 회귀는
`SV3-LF-U103-Q04` 2→4와 `SV3-LF-U106-Q02` 1→2다. 나머지 11개 direct query는 rank가
같았다. 좋은 사례와 나쁜 사례를 상쇄한 aggregate 동률이다.

## 19. 비용과 latency

| Dataset | Profile | construction | embedding | indexing total | query p50/p95 | ranking p50/p95 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Original | A | 1.184ms | 174.098ms | 175.283ms | 25.937/29.731ms | 0.096/0.170ms |
| Original | B2 | 5.774ms | 184.606ms | 190.380ms | 25.898/29.718ms | 0.111/0.150ms |
| Long-form | A | 2.120ms | 333.125ms | 335.245ms | 24.867/29.444ms | 0.082/0.144ms |
| Long-form | B2 | 12.044ms | 1122.888ms | 1134.932ms | 24.885/29.512ms | 0.125/0.765ms |

Original에서는 B1 38개에서 B2 17개로 후보가 55.26% 줄었다. Long-form에서는 Fixed 대비
후보/embedding이 357.14%, indexing wall time이 238.54% 늘었으나 Top1/MRR/Recall 순증은 0이다.
latency는 단일 local run의 작은 in-memory batch이며 Production 성능 근거가 아니다.

## 20. Phase 1 Adjustment 판정

판정은 `NEEDS_ADJUSTMENT`다. heading noise와 contamination 제거는 재현 가능한 개선이지만,
네 원래 회귀 중 하나가 actor 혼동으로 남았고 Long-form aggregate ranking/recall 순증이 없으며
후보 비용이 크게 늘었다. contamination 이점이 명확하므로 이 결과를 바로 `NO_GO`로 소급하지
않지만, `PROMISING`이나 Parent Context 진입 근거로도 사용할 수 없다.

`Structural Child + Parent Context`와 Parent Dense는 계속 `NOT_RUN`이다. 다음 진입 조건은 B2의
remaining ranking/직무·언어 회귀와 candidate cost를 별도 정책 변경으로 다시 검증하는 것이다.

## 21. Adjustment validation과 audit 상태

| 명령/검사 | 실제 결과 |
| --- | --- |
| `node scripts/evaluation/search-v3/materialize-prz026-devcal.mjs --check` | `PASS`; 17 files, 6 documents, 24 queries, combined `a1fcd76...` |
| PRZ-026 structural source-set 전체 | `PASS`; 5 suites, 40 tests, failure/error/skipped 0; local BGE-M3 report 재생성 |
| model parity | `PASS`; `bge-m3`, digest `790764...`, 1024 dimensions, cosine, A/B query vector 공유 |
| gold/evaluator strictness | `PASS`; nonempty span, owner/document/parent/group, required group, ALL/ANY, fixed whitespace-window mapping, macro denominator tests |
| SEALED byte verification | `PASS`; manifest 9 files size/SHA/combined 검증, sealed combined `e5b315...`, flags false |
| report provenance | `PASS`; ignored report `663ef12...`, execution source snapshot `472e855...` |
| source/test read-only auto audit | `PASS`; blocking 0, latest report/source hash 일치 |
| history/scope read-only auto audit | `PASS`; blocking/nonblocking 0, sealed tree 동일, forbidden diff 0 |
| contract/lifecycle read-only auto audit | `PASS`; 초기 8 findings 해소, blocking/nonblocking 0 |
| `node scripts/verify-oss-readiness.mjs` | `PASS`; staged 포함 tracked safety 937, Markdown 186/links 764, verifier 16/16, external 97/97 |
| staged scope | `PASS`; 31/31 allowed, forbidden/Production/SEALED 0 |
| `git diff --cached --check` | `PASS`; 출력 0 |
| remote GitHub PR 상태 | `NOT_VERIFIED`; 로컬 ref/history에는 push/PR/merge 징후 없음 |
| full backend unit/integration | `NOT_RUN`; Production 변경 0, 관련 `searchEvaluation` source set만 실행 |
| frontend lint/build | `NOT_RUN`; frontend 변경 0 |
| Docker/PostgreSQL integration | `NOT_RUN`; DB/Production path 범위 밖 |
| Parent Context/Parent Dense/push/PR/merge | `NOT_RUN`; 금지 범위 |

자동 read-only audit가 처음 제기한 source revision, DEV/CAL 실행 상태, table context 명명,
Original/Long-form 계약 충돌, lifecycle 표기, heading Gate 의미와 normalized fact signature 문제는
수정했다. Source/test, history/scope와 contract/lifecycle 최종 재감사는 모두 blocking finding
0으로 끝났다. 첫 계약 재감사가 차단한 staging 전 lifecycle 표기 1건은 staged 31-path
scope·OSS readiness·diff check 결과를 기록하고 tasks Gate를 닫은 뒤 해소됐다.

## 22. Phase 1 Retrieval Passage 시작 상태와 고정 정책

| 항목 | 값 |
| --- | --- |
| 시작 branch / HEAD | `PRZ-026-structural-parsing-parent-child` / `e5012fd4949b05f4b8a136186ddefb60046985f8` |
| 시작 worktree | clean |
| dataset | Original `search-v3-fresh-seed-1.0.1`, Long-form `search-v3-fresh-devcal-1.1.0`; 신규 fixture 0 |
| SEALED FINAL | `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, flags false |
| B1 / B2 | `NEEDS_ADJUSTMENT` / `NEEDS_ADJUSTMENT`; 역사 판정 유지 |
| B3 size policy | minimum target 120, target maximum 320, absolute maximum 480 code points |
| grouping | same document/version/page/structural Parent, source-order adjacent, overlap 0 |
| forbidden inputs | Gold, query, profession, language, actor, negation, completion state |
| Parent Context / Parent Dense | `NOT_RUN / NOT_RUN` |

정책은 benchmark 전에 한 번 고정했고 결과 확인 뒤 재튜닝하지 않았다. B2 EvidenceChild는 그대로
유지하며 B3 passage는 ordered `evidenceChildIds[]`와 각 atomic provenance range를 갖는다. Gold hit는
passage 합성 범위가 아니라 하나의 포함 Child range가 Gold Unit의 모든 span을 덮을 때만 인정했다.
480을 넘는 atomic Child는 fail-closed하며 현재 frozen corpus에는 해당 Child가 없었다.

## 23. B3 report와 동일 비교 조건

- ignored report: `local/search-v3-evaluation/prz026/structural-retrieval-passage-b3.json`
- report SHA-256: `acc4c7e7bdae9296e7ae543ded16dde2f92ad39911df90171c6b09606fca2918`
- execution source snapshot: `3406e788698a4d90f240cdce63783b1ef5a046aaedda10c577964e05a822a2a0`
- model: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, 1024 dimensions, cosine
- A/B2/B3는 문항당 같은 query embedding을 공유했고 owner-bundle ACTIVE scope를 사용했다.
- sparse, FTS, RRF, reranker, QueryPlanner, rewrite, rescue/fallback와 LLM은 사용하지 않았다.

## 24. Original Seed A/B2/B3

| Metric | A Fixed | B2 Atomic | B3 Passage |
| --- | ---: | ---: | ---: |
| candidate / embedding | 7 / 7 | 17 / 17 | 15 / 15 |
| query-micro Top1 / MRR | 1.0000 / 1.0000 | 0.9286 / 0.9643 | 0.9286 / 0.9643 |
| user-macro Top1 / MRR | 1.0000 / 1.0000 | 0.9333 / 0.9667 | 0.9333 / 0.9667 |
| Recall@5/10/20/50 | 1/1/1/1 | 1/1/1/1 | 1/1/1/1 |
| contamination / fragmentation | 57.14% / 0% | 0% / 0% | 0% / 0% |
| indexing wall time | 204.871ms | 227.552ms | 164.280ms |
| query p50 / p95 | 22.663 / 25.294ms | 22.650 / 25.124ms | 22.642 / 25.112ms |

B3는 B2보다 candidate/embedding을 11.76%, 최종 단일-run indexing wall time을 27.81% 줄였고 모든
quality/slice metric을 B2와 동일하게 유지했다. Original 내부 B3 판정은 `PROMISING`이다.

Passage 통계는 Child 수 min/avg/max `1 / 1.1333 / 3`, retrieval length `32 / 91.07 / 176`,
single `14/15 (93.33%)`, multi `1/15 (6.67%)`다. cross-parent violation 0, heading candidate/rank1 0,
DIRECT Gold-mapped Child 보존은 100%다.

## 25. Long-form A/B2/B3

| Metric | A Fixed | B2 Atomic | B3 Passage |
| --- | ---: | ---: | ---: |
| candidate / embedding | 28 / 28 | 128 / 128 | 72 / 72 |
| query-micro Top1 / MRR | 0.8000 / 0.8833 | 0.8000 / 0.8833 | 0.8000 / 0.8833 |
| user-macro Top1 / MRR | 0.8056 / 0.8889 | 0.8056 / 0.8889 | 0.8333 / 0.9028 |
| Recall@5/10/20/50 | 1/1/1/1 | 1/1/1/1 | 1/1/1/1 |
| contamination / fragmentation | 71.43% / 0% | 0% / 0% | 0% / 0% |
| indexing wall time | 321.833ms | 1133.644ms | 687.901ms |
| query p50 / p95 | 24.863 / 34.603ms | 24.899 / 34.574ms | 24.840 / 34.554ms |

B3는 B2보다 candidate/embedding을 43.75%, 최종 단일-run indexing wall time을 39.32% 줄였다.
query-micro Top1/MRR과 Recall은 비열화가 없고 user-macro는 개선됐다. Passage Child 수 min/avg/max는
`1 / 1.7778 / 5`, retrieval length는 `84 / 233.89 / 362`, single `39/72 (54.17%)`, multi
`33/72 (45.83%)`다. cross-parent violation 0, heading candidate/rank1 0, DIRECT Gold-mapped Child
보존은 100%다. wall time은 단일 local batch이며 Production 성능 근거가 아니다.

## 26. B3 slice와 query 변화

Original profession/language slice는 B2와 모두 동일했다. Long-form language slice도 모두 동일했다.
Long-form profession에서는 다음 두 변화만 있었다.

- `NON_DEVELOPMENT_GENERAL`: Top1/MRR `0.5/0.75→1.0/1.0`
- `FRONTEND_MOBILE`: Top1/MRR `1.0/1.0→0.6667/0.8333`

직접 query rank 변화도 두 건뿐이었다.

- 개선 `SV3-LF-U106-Q02`: rank `2→1`. 완료된 emergency-response training Child가 같은 Parent의
  인접 설명과 묶이며 direct passage가 rank 1이 됐다.
- 회귀 `SV3-LF-U104-Q01`: rank `1→2`. offline-safe checkout direct Child에 같은 Parent의 두 bullet이
  붙은 passage score가 단독 Child보다 낮아졌고, 관련 배경 paragraph가 rank 1이 됐다.

actor, negation, completion 또는 language heuristic으로 이 회귀를 고치지 않았다. 첫 사례와 두 번째
사례가 query-micro에서 상쇄되지만 `FRONTEND_MOBILE` 신규 회귀는 Adoption Gate에서 숨기지 않는다.

## 27. B3 판정과 다음 Gate

최종 판정은 `NEEDS_ADJUSTMENT`다. Evidence 경계, heading, Recall, aggregate ranking과 운영 비용 Gate는
통과했지만 Long-form `FRONTEND_MOBILE` Top1 33.33pp 회귀가 남았다. 그러므로 전체 B3를
`PROMISING`으로 승격하지 않는다. 또한 비용 감소와 contamination 0의 순증이 명확하므로 `NO_GO`도
아니다. Parent Context와 Parent Dense는 계속 `NOT_RUN`이며, 이 결과만으로 C를 시작하지 않는다.

## 28. B3 validation 상태

| 명령/검사 | 실제 결과 |
| --- | --- |
| 관련 parser/child/passage/dataset/engine unit test | `PASS`; 5 suites, 48 tests, failure/error/skipped 0 |
| A/B2/B3 BGE-M3 benchmark | `PASS`; 1 test, Original 21 queries, Long-form 24 queries, report `acc4c7...` |
| SEALED FINAL | `PASS`; 9 files verified, hash 유지, `opened=false`, `searchExecuted=false` |
| report/source snapshot | canonical 9-file 순서 재계산 `PASS`; combined `3406e788...` |
| ad-hoc snapshot 첫 재계산 | `COMMAND_ERROR`; unordered JSON map 순서 사용으로 combined 불일치, 파일 변경 0; canonical 순서로 재실행해 해소 |
| DEV/CAL 1.1.0 materializer `--check` | `PASS`; 17 files, 6 documents, 24 queries, combined `a1fcd76...` |
| PRZ-025 frozen benchmark validator | `PASS`; status `FRESH_BENCHMARK_SEED_FROZEN`, overall `1f36c4...`, Final search false |
| `git diff --cached --check` | `PASS`; 출력 0 |
| OSS readiness | `PASS`; staged 포함 tracked 940, Markdown 186/links 764, verifier 16/16, external 97/97 |
| staged scope / secret scan | `PASS`; 10/10 allowed, forbidden/SEALED/secret finding 0 |
| full backend / frontend / Docker | `NOT_RUN`; evaluation-only scope |
| Parent Context / Parent Dense / push / PR / merge | `NOT_RUN`; 금지 범위 |

## 29. B3 robustness 입력 봉인 전 기록

- 기준 B3 HEAD: `01d9ae2f90eff691d96041579e42a02aa04a3486`
- 상태: `INPUT_READY / BENCHMARK_NOT_RUN`
- B3 policy 변경: 0; `120/320/480`, same-parent adjacency, heading context-only, overlap 0 유지
- dataset: `search-v3-fresh-devcal-robustness-1.0.0`
- combined SHA-256: `cb43832d48bb1f88e5a24abc520154b8562950ecc973295fdb16936aae08ab54`
- 규모: DEV 3 / CALIBRATION 3 bundles, synthetic TXT 6 documents, DIRECT-support 24 queries
- 직무: `FRONTEND_MOBILE` 2, `DATA_AI_INFRA` 1, `DESIGN_PRODUCT` 1,
  `MARKETING_SALES` 1, `NON_DEVELOPMENT_GENERAL` 1
- 문서 언어: KO 2 / EN 2 / KO_EN_MIXED 2
- 새 `FRONTEND_MOBILE`: 2 bundles / 8 direct queries; 기존 1.1.0과 누적 3 bundles / 11 direct queries
- lineage: Original Seed와 DEV/CAL 1.1.0의 user/document/version/template/generator/source-fact/query
  lineage 충돌 0

실행 전 Gate는 profession/language slice `3 bundles + 10 direct queries`, clustered user bootstrap
10,000회, seed `260830026`, candidate 감소 최소 25%로 고정했다. 충분 slice에서 Top1 또는 MRR
paired delta의 95% interval 상한이 0 미만일 때만 `BLOCKING_REGRESSION`으로 정의했다. 새 suite
전체와 새 frontend point delta가 음수면 bootstrap과 무관하게 robustness `PROMISING`을 허용하지
않는다. 이 규칙은 다음 evaluation-only 실험 진입 판단이며 Production Adoption Gate가 아니다.

입력 봉인 전 실제 검증은 materializer byte check `PASS`(17 files), 관련 searchEvaluation
6 suites / 58 tests `PASS`(failure/error/skipped 0), PRZ-025 validator `PASS`와 validator unit
18/18 `PASS`다. 최초 Gradle sandbox 실행은 wrapper download network 차단으로 `COMMAND_ERROR`였고
파일 변경은 없었다. 허용된 외부 실행에서 compile과 test가 성공했다. BGE-M3 robustness benchmark,
Parent Context, Parent Dense와 SEALED FINAL search는 모두 `NOT_RUN`; `CURRENT_FRESH_BASELINE = NOT_RUN`이다.

첫 자동 계약 감사의 5개 finding(fragmentation/Recall predicate, bootstrap seed, manifest 실제 검증,
최종 decision test)을 수정한 뒤 현재 source에서 58 tests를 재실행했다. robustness loader는 고정
SHA/count와 16개 실제 file hash/size/combined hash를 fail-closed 검증하고 mutation test를 포함한다.
수정 후 계약 재감사의 마지막 evidence count finding도 이 기록으로 해소했으며, 결과 실행 전
input-freeze 감사의 blocking finding은 0이다.

## 30. B3 robustness 실행 결과

- input-freeze commit: `0fe0b3c54d86016a5d2b5c4fe8f1d26216ee3105`
- 실행 dataset: `search-v3-fresh-devcal-robustness-1.0.0`
- combined SHA-256: `cb43832d48bb1f88e5a24abc520154b8562950ecc973295fdb16936aae08ab54`
- ignored raw report SHA-256: `f0bf5481a572ad5e21f91916e5cd0fc6c309c50ec59e2f75ac2386433133324d`
- model: `bge-m3:latest`, digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`,
  1024 dimensions, cosine, raw Dense only
- 실행: DEV/CAL 6 bundles, 6 documents, DIRECT-support 24 queries; SEALED FINAL 평가 dataset
  load/search 0. manifest와 9개 파일은 byte/hash/flags 무결성 검증 목적으로만 읽음

두 최초 실행 시도는 검색 전에 input-freeze SHA 전달 guard에서 종료됐다. 첫 시도는 PowerShell의
`-D` 해석 오류, 두 번째는 Gradle test JVM으로 property가 전달되지 않은 오류였다. 파일·prediction·
benchmark result는 생성되지 않았다. 동일 SHA를 test JVM에 전달한 세 번째 시도만 실제 평가로
기록한다.

### Fresh independent suite

| Metric | A Fixed | B2 Atomic | B3 Passage |
| --- | ---: | ---: | ---: |
| candidate / embedding | 19 / 19 | 72 / 72 | 50 / 50 |
| Recall@5/10/20/50 | 1 / 1 / 1 / 1 | 1 / 1 / 1 / 1 | 1 / 1 / 1 / 1 |
| query-micro Top1 / MRR | 0.8333 / 0.9097 | 1 / 1 | 1 / 1 |
| user-macro Top1 / MRR | 0.8333 / 0.9097 | 1 / 1 | 1 / 1 |
| indexing wall time | 410.2399ms | 979.7674ms | 674.6776ms |
| query p50 / p95 | 33.6726 / 41.4352ms | 33.6537 / 41.3427ms | 33.6383 / 41.3282ms |
| contamination / fragmentation | 73.68% / 0 | 0 / 0 | 0 / 0 |

B3는 B2보다 candidate/embedding을 `72→50`(30.56%), 단일-run indexing wall time을
`979.7674ms→674.6776ms`(31.14%) 줄였다. B2/B3 query 24건은 Top1과 reciprocal rank가 모두
tie였다. passage는 50개, child membership은 72개였고 passage당 child min/avg/max는
`1/1.44/3`, 길이는 `109/220.36/312` code points였다. single/multi-child 비율은 72%/28%,
cross-parent violation 0, heading-only candidate/rank1 0, DIRECT Gold Child 보존 24/24(100%)다.

### Historical Long-form 재현과 paired 변화

기존 `search-v3-fresh-devcal-1.1.0`도 같은 실행에서 재현했다. B2/B3 candidate는 `128→72`
(43.75%), 단일-run indexing wall time은 `1898.2931ms→1106.9957ms`(41.69%)였다. query-micro
Top1/MRR은 양쪽 `0.8/0.8833`, Recall@5/10/20/50은 모두 1, contamination/fragmentation은
0/0, Gold Child 보존은 100%였다. user-macro Top1/MRR은 `0.8056/0.8889→0.8333/0.9028`이다.

paired rank 변화는 두 건뿐이다.

- 개선 `SV3-LF-U106-Q02`: direct 완료 교육 근거 rank `2→1`; 같은 Parent의 설명과 묶였다.
- 회귀 `SV3-LF-U104-Q01`: offline-safe checkout direct 근거 rank `1→2`; 관련 배경 paragraph가
  rank 1이 됐다. query-specific heuristic으로 수정하지 않았다.

누적 충분 slice 판정에서 KO는 `NON_INFERIOR`(17 queries, interval `[0,0]`)였다. EN은 20 queries에서
1 win/1 loss로 Top1 interval `[-0.1364,0.1765]`, MRR interval `[-0.0682,0.0882]`인
`INCONCLUSIVE`였다. `FRONTEND_MOBILE`은 3 bundles/11 queries에서 0 win/1 loss, Top1 delta
`-0.0909`와 interval `[-0.3333,0]`, MRR delta `-0.0455`와 interval `[-0.1667,0]`로
`INCONCLUSIVE`다. 나머지 profession과 mixed language는 사전 기준 미달 `INSUFFICIENT_SAMPLE`이다.
충분 slice의 `BLOCKING_REGRESSION`은 0이다. 이 불확실성을 비회귀 증명이나 Production 채택
근거로 사용하지 않는다.

## 31. B3 robustness 판정과 경계

사전 동결 Gate의 실제 판정은 `PROMISING`이다. fresh 전체 B2/B3 point delta는 0이고 95% interval도
`[0,0]`인 `NON_INFERIOR`; fresh frontend는 2 bundles/8 queries로 point delta 0이지만
`INSUFFICIENT_SAMPLE`; 누적 충분 slice의 blocking regression은 0이다. 구조 경계·Recall·Gold 보존
Gate와 최소 25% 비용 감소 Gate도 모두 충족했다.

이 판정은 `Structural Child + Parent Context`의 evaluation-only 실험을 시작해도 된다는 뜻에만
한정한다. Parent Context/Parent Dense/Production 적용은 아직 `NOT_RUN`이며 별도 입력·단일 변경점·
Gate를 먼저 고정해야 한다. SEALED FINAL combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, tree
`a129080861d7dafd32a9b3b3357b61aebb237e59`, `opened=false`, `searchExecuted=false`,
`CURRENT_FRESH_BASELINE = NOT_RUN`은 유지됐다.

## 32. B3 robustness validation

| 명령/검사 | 실제 결과 |
| --- | --- |
| BGE-M3 robustness benchmark | `PASS`; 실제 평가 1회, report `f0bf5481...`, decision `PROMISING` |
| 관련 evaluation unit test | `PASS`; 6 suites / 58 tests, failure/error/skipped 0 |
| robustness materializer `--check` | `PASS`; 17 files, 6 bundles, 24 queries, combined `cb43832d...` |
| PRZ-025 benchmark validator | `PASS`; `FRESH_BENCHMARK_SEED_FROZEN`, combined `1f36c4...`, Final search false |
| PRZ-025 validator unit test | `PASS`; 18/18 |
| SEALED FINAL byte/tree/flags | `PASS`; tree `a129080...`, combined `e5b315...`, flags false |
| `git diff --check` | `PASS`; 출력 0 |
| OSS readiness | `PASS`; Markdown 187/links 764, tracked safety 961, verifier 16/16, external 97/97 |
| result diff scope | `PASS`; input-freeze 이후 변경은 PRZ-026 문서 4개, forbidden 경로 0 |
| full backend/frontend/Docker | `NOT_RUN`; evaluation-only 범위, Production 변경 0 |
| Parent Context/Parent Dense/push/PR/merge | `NOT_RUN`; 이번 범위 밖 |

최종 자동 scope/history 감사는 blocking 0, 해소된 체크리스트 상태 finding 외 0이었다. 최초 결과
계약 감사의 두 finding은 (1) SEALED integrity read를 `접근 0`으로 잘못 축약한 표현과 (2) VERIFY/
AUDIT lifecycle checkbox 불일치였다. 각각 integrity-only read를 명시하고 lifecycle 순서를 맞춘 뒤
재감사했으며 blocking/nonblocking finding 0으로 `PASS`했다. 판정 수치와 Gate 계산 finding은 없었다.
결과 commit 전 lifecycle 완료 선표기 finding은 `INTEGRATE_PENDING`으로 되돌려 해소하고, 실제
result/evidence commit 이후 post-commit 상태에서 최종 확인한다.

result/evidence는 commit `b1949df15120e30f915101575919fadf9300b6a2`로 통합했다. 해당 commit 직후
working tree는 clean이었고, lifecycle close는 결과 내용을 변경하지 않는 별도 문서 commit으로
기록한다.

## 33. C1 시작 상태와 실행 전 계약

| 항목 | 값 |
| --- | --- |
| branch / 시작 HEAD | `PRZ-026-structural-parsing-parent-child` / `1bbc1d761bd314a17e8f3ed4e2bcceb23a2fc96a` |
| 시작 worktree | clean |
| `origin/main` | `2c8fd5c0d2f62b154642d703a0970389f8abed8e` |
| B3 builder SHA-256 | `64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39` |
| EvidenceChild builder SHA-256 | `6ff76f49df332319fac987a59be4ead11d7ecda90b44f0d11e0cb538acd6cb83` |
| SEALED FINAL | tree `a129080861d7dafd32a9b3b3357b61aebb237e59`, combined `e5b3159798...`, flags false |
| B3 robustness report | `f0bf5481a572ad5e21f91916e5cd0fc6c309c50ec59e2f75ac2386433133324d` |
| dataset 변경 | 0; Original/Long-form/robustness DEV/CAL 재사용 |
| C1 benchmark / Parent Dense | `NOT_RUN / NOT_RUN` |

실행 전 C1 policy는 `STRUCTURAL_HEADING_PATH_V1`, 명시적 Markdown hierarchy와 일반 heading의
nearest-only, depth 2, 120 code points로 한 번 고정했다. B3와 C1의 유일한 차이는 heading-derived
`contextText`를 document embedding 입력 앞에 붙이는 것이다. candidate/source/evidence/provenance
parity, source-range-only Gold, context-only false-hit와 판정 Gate는 spec 13절에 고정했다. 이 기록
시점의 `CURRENT_FRESH_BASELINE = NOT_RUN`, SEALED FINAL search/prediction/result `NOT_RUN`이다.

## 34. C1 input-freeze 전 검증

`ContextualRetrievalPassage`는 B3 `sourceText`, ordered `evidenceChildIds`를 constructor에서 강제하고,
`StructuralHeadingPathContextBuilder`는 source block과 B3 passage만 입력받는다. runner는 candidate ID,
source range, source/table provenance, Parent ID와 Child mapping을 일대일 비교한 뒤 같은 query vector로
B3/C1을 순서대로 rank한다. `ParentContextAblationGate`와 benchmark entry point는 결과를 보기 전에
spec 13절 Gate를 코드로 고정했다. EvidenceChild/B3 builder SHA-256 상수도 entry point에서 검증한다.

| 명령/검사 | 실제 결과 |
| --- | --- |
| `compileSearchEvaluationJava` | `PASS`; C1 source compile 성공 |
| PRZ-026 비-benchmark evaluation tests | `PASS`; 8 suites / 71 tests, failure/error/skipped 0 |
| `searchEvaluationTest` 최초 시도 | `NOT_RUN`; repository에 해당 task가 없어 command selection error, 실제 test는 `searchEvaluation`로 재실행 |
| B3/EvidenceChild source hash | `PASS`; section 33의 SHA-256과 동일 |
| C1 BGE-M3 benchmark | `NOT_RUN`; input-freeze commit 이후 한 번 실행 예정 |
| Parent Dense / SEALED FINAL search | `NOT_RUN / NOT_RUN` |
