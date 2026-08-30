# PRZ-026 Phase 1 Evidence

- 상태: `IN_PROGRESS / PHASE_1_NEEDS_ADJUSTMENT`
- 기록일: 2026-08-30 (Asia/Seoul)
- 선행 조건: `DEPENDS_ON_PRZ_025`
- 최종 판정: `NEEDS_ADJUSTMENT`
- Production 변경·적용: `0 / NOT_RUN`

## 1. 시작 상태

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

Agent read-only audit의 blocking finding은 0이다. 비차단 한계는 7개 short TXT bundle의
Recall ceiling, PDF/long-document fixture 0, standalone heading ranking 회귀, 단일 local latency
sample, no-answer selector 미구현이다. 이는 `NEEDS_ADJUSTMENT`와 Parent Context 진입 차단 이유에
반영했다. 이 audit는 GitHub review 증거가 아니다.
