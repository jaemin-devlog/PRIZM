# PRZ-029 Evidence

- 상태: `IN_PROGRESS / BENCHMARK_NOT_RUN`
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

역사 상태는 그대로 유지한다. PRZ-026 C1 Parent Context는 `NO_GO`, Parent Dense는 `DEFER`,
PRZ-027 GTE Cross Encoder는 `NO_GO`, QueryPlanner와 Sparse는 `DEFER`, PRZ-028 ranking은 비채택이며
이번 Phase에서는 `EVIDENCE_VALIDATION_ONLY` 경계만 재사용한다. 이들은 PRZ-029에서 새로 실행하지 않는다.

Stress 1.1.0 frozen per-unit states의 query reduction을 검색 결과 전에 확인했다.

| split | FOUND | NONE | PARTIAL |
| --- | ---: | ---: | ---: |
| DEV | 8 | 3 | 1 |
| CALIBRATION | 8 | 3 | 1 |
| 전체 | 16 | 6 | 2 |

`PARTIAL` 두 건은 `SV3-U42-Q04`, `SV3-U45-Q04`이며 owner-scoped expected state가 모두
`UNKNOWN`이다. 일반 questions answerability를 직접 매핑하면 `16/8/0`이 되어 observation 부재를
`NONE`으로 과장하므로 typed-state oracle로 사용하지 않는다.

## 2. 아직 실행하지 않은 항목

- PRZ-029 E0/E1 BGE benchmark: `NOT_RUN`
- semantic parity / Candidate Recall / state·selection metric: `NOT_RUN`
- 추가 latency / memory: `NOT_RUN`
- Production 적용: `NOT_RUN`
- SEALED FINAL search/prediction/result: `NOT_RUN`

SEALED FINAL 기준값은 combined
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`이다.

## 3. 코드 동결 전 검증

- `compileSearchEvaluationJava --rerun-tasks`: `PASS`; 표시된 deprecation warning은 기존
  Production/Jackson API 사용 위치이며 PRZ-029 신규 파일의 compile error는 0건이다.
- PRZ-026 structural/parser/passage/dense, PRZ-028 deterministic parser/evaluator/dataset/verdict와
  PRZ-029 selector/engine/input contract의 13개 test class, 141 tests: `PASS`
- 실제 BGE E0/E1 benchmark와 raw report: `NOT_RUN`

독립 코드 감사에서 발견된 Gold-before-selection, unrelated UNKNOWN의 false `PARTIAL/NONE`, 첫 child만
검사한 selection metric, 상수 cross-parent metric, contributor trace와 parse latency 누락을 코드 동결 전에
수정했다. Runtime selector는 Gold/answerability를 받지 않고, Stress의 모든 runtime selection 완료 뒤에만
evaluation Gold를 붙인다.

수정 후 독립 재감사는 global DEV/CAL two-pass Gold 경계와 ACTIVE 원문 기준 owner/document/version/
path/page/hash/code-point/line/parent provenance 대조를 확인했으며 남은 blocking finding은 0건이다.
