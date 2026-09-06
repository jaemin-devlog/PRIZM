# PRZ-029 근거 검증과 선택 결과

## 최종 판정

`VERIFIED / PROMISING`

PRZ-028의 Typed Validation을 순위 점수로 쓰지 않고, B3 후보에서 조건에 맞는 원문 근거를
고르는 단계로 배치했다. 일반 의미 검색 69/69의 순서와 provenance를 보존하면서 Typed
Stress의 state macro F1과 선택 precision이 모두 1.0이었고 신규 loss는 0건이었다.

## 기준선

- 시작 branch / HEAD: `PRZ-028-typed-exact-constraints@33c702aa0bff86502f7f70a343b60c59c13eb80f`
- 현재 branch: `PRZ-029-evidence-validation-selection`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- PRZ-025 dependency: `5f8229f88251938dc5b34588676cc69edf409c99`
- PRZ-026 B3 lifecycle close: `1bbc1d761bd314a17e8f3ed4e2bcceb23a2fc96a` (`PROMISING`)
- PRZ-028 closeout body: `bef9d1055afa0e750e4b97ffdee25e45c6b78332`
- PRZ-028 final metadata HEAD: `33c702aa0bff86502f7f70a343b60c59c13eb80f`
- 시작 working tree: `CLEAN`

## 1. 시작 사실과 사전 계약

`33c702a`는 `bef9d10`의 closeout 내용을 바꾸지 않고 그 exact SHA를 기록한 후속 metadata commit이므로
실제 PRZ-028 final HEAD다. 추적성 문서 교정이나 benchmark 재실행은 필요하지 않았다.

B3 full owner-scoped Dense ranking은 E0/E1이 한 번만 공유한다. PRZ-028 parser/extractor/evaluator는
동결해 source-only validation으로만 재사용하고, 비채택된 stable partition은 재사용하지 않는다.
Gold와 answerability는 runtime 입력에서 분리한다.

역사 상태는 그대로 유지한다. PRZ-026 C1 Parent Context의 공식 판정은 `NEEDS_ADJUSTMENT`이고
후속 기준선에서는 비채택했다. Parent Dense는 `DEFER`, PRZ-027 GTE Cross Encoder는 `NO_GO`,
QueryPlanner와 Sparse는 `DEFER`, PRZ-028 ranking은 비채택이며 이번 Phase에서는
`EVIDENCE_VALIDATION_ONLY` 경계만 재사용한다. 이들은 PRZ-029에서 새로 실행하지 않는다.

### DOCUMENTATION_CORRECTION

이 절의 이전 문장은 PRZ-026 C1을 `NO_GO`라고 적어 후속 기준선의 비채택 결정과 당시 공식
판정 `NEEDS_ADJUSTMENT`를 혼동했다. PRZ-026 source·spec·tasks·evidence에 맞춰 표현만 바로잡았고
PRZ-029의 결과, 수치와 판정은 바꾸지 않았다.

Stress 1.1.0 frozen per-unit states의 query reduction을 검색 결과 전에 확인했다.

| split | FOUND | NONE | PARTIAL |
| --- | ---: | ---: | ---: |
| DEV | 8 | 3 | 1 |
| CALIBRATION | 8 | 3 | 1 |
| 전체 | 16 | 6 | 2 |

`PARTIAL` 두 건은 `SV3-U42-Q04`, `SV3-U45-Q04`이며 owner-scoped expected state가 모두
`UNKNOWN`이다. 일반 questions answerability를 직접 매핑하면 `16/8/0`이 되어 observation 부재를
`NONE`으로 과장하므로 typed-state oracle로 사용하지 않는다.

## 2. 코드 동결과 독립 감사

Code freeze는 `9347a7497585ba9111e9e20caf9578f34cfd0c0c`이다. 독립 코드 감사에서 발견된
Gold-before-selection, unrelated UNKNOWN의 false `PARTIAL/NONE`, 첫 child만 검사한 selection metric,
상수 cross-parent metric, contributor trace와 parse latency 누락을 동결 전에 수정했다. Runtime selector는
Gold/answerability를 받지 않고, Stress의 DEV/CAL runtime selection을 모두 끝낸 뒤 evaluation Gold를 붙인다.

재감사는 global two-pass Gold 경계와 ACTIVE 원문 기준 owner/document/version/path/page/hash/
code-point/line/parent provenance 대조를 확인했으며 남은 blocking finding은 0건이다.

## 3. 실행과 결과

- `compileSearchEvaluationJava --rerun-tasks`: `PASS`; 표시된 deprecation warning은 기존
  Production/Jackson API 사용 위치이며 PRZ-029 신규 파일의 compile error는 0건이다.
- PRZ-026 structural/parser/passage/dense, PRZ-028 deterministic parser/evaluator/dataset/verdict와
  PRZ-029 selector/engine/input contract의 13개 test class, 141 tests: `PASS`
- 동결 SHA의 PRZ-029 E0/E1 BGE benchmark: `PASS`, quality Gate finding 0
- 모델: Ollama `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, 1024 dimensions, cosine
- ignored raw report: `local/search-v3-evaluation/prz029/evidence-validation-selection.json`
- raw report SHA-256: `f08fe34c7f502463ea8c846e0a8c7a77d07b725ac3ca223eb2fb350efe276d16`

| suite | query | semantic exact parity | E0/E1 Recall@20 | direct rank-1 gain/loss |
| --- | ---: | ---: | ---: | ---: |
| Original Seed | 21 | 21/21 | 14/14 → 14/14 | 0/0 |
| Long-form | 24 | 24/24 | 15/15 → 15/15 | 0/0 |
| Robustness | 24 | 24/24 | 24/24 → 24/24 | 0/0 |
| Typed Stress 1.1.0 | 24 | `UNASSESSED` | 16/16 → 16/16 | 2/0 |

일반 semantic query 69/69는 candidate/order/selection/source/provenance exact parity를 유지했다. Typed
Stress의 runtime constraint conformance, state, query selection은 각각 24/24였고 state macro F1은 1.0이다.
Confusion은 `FOUND 16/16`, `NONE 6/6`, `PARTIAL 2/2`다. 선택된 child는 38/38이 frozen state tier에
속해 precision 1.0이며 duplicate/cross-parent merge는 0, provenance는 전체 349/349다.

`NONE` exclusion evidence 12/38은 사전 허용된 `CONTRADICTED` 선택이다. `FOUND/PARTIAL` support output의
contradicted selection은 0/26이며, `PARTIAL` UNKNOWN fallback은 2/2다. Quantity, date,
identifier-number와 qualifier mismatch, percentage direction, range/boundary family 모두 state/selection
accuracy 1.0이고 신규 loss는 0이다.

Typed query당 추가 latency는 p50 `0.2008 ms`, p95 `1.0399 ms`; 후보별 validation은 p50
`0.0214 ms`, p95 `0.0627 ms`; selection은 p50 `0.0071 ms`, p95 `0.3387 ms`다. JVM heap point
observation은 `+21.83 MiB`지만 네 suite 실행·JIT가 섞인 비격리 관측이므로 persistent memory 증가로
해석하지 않는다. 새 persistent index/storage write는 0이다.

직접 근거 rank-1 개선은 `SV3-U41-Q01`(wrong value)과 `SV3-U41-Q02`(qualifier mismatch) 두 건이다.
신규 direct rank-1 loss, state/selection 오류, semantic 회귀는 0건이다. 다만 PARTIAL은 두 query뿐이고
fixture는 synthetic이므로 일반 semantic sufficiency나 Production 품질 근거로 확대 해석하지 않는다.

## 4. 안전 경계와 판정

SEALED FINAL combined SHA-256은
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, `opened=false`,
`searchExecuted=false`, `mutable=false`다. SEALED semantic search/prediction/result와
`CURRENT_FRESH_BASELINE`은 모두 `NOT_RUN`이다.

Production, migration, dependency/build, frontend, MCP, Docker와 dataset 변경은 0이다. PR, push,
merge, Sparse, Grounded Answer, QueryPlanner와 일반 semantic state 판정은 `NOT_RUN`이다.

최종 판정은 `PROMISING`이다. Typed Validation을 B3 이후 Evidence Selection에 배치할 실질 가치가
확인됐으며, 다음 별도 Phase에서 일반 semantic Evidence Validation을 시작할 수 있다. Grounded Answer나
Production 적용 승인은 아니다.
