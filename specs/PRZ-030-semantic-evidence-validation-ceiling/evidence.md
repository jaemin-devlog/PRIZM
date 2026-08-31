# PRZ-030 Evidence

- 상태: `IN_PROGRESS / ORACLE_NOT_RUN`
- 시작 branch / HEAD: `PRZ-029-evidence-validation-selection@f7e4a7adffd5574526d6c00c76ece9113a68d69f`
- 현재 branch: `PRZ-030-semantic-evidence-validation-ceiling`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- PRZ-025 dependency: `5f8229f88251938dc5b34588676cc69edf409c99`
- PRZ-026 B3 lifecycle close: `1bbc1d761bd314a17e8f3ed4e2bcceb23a2fc96a`
- PRZ-028 final: `33c702aa0bff86502f7f70a343b60c59c13eb80f`
- PRZ-029 final: `f7e4a7adffd5574526d6c00c76ece9113a68d69f`
- 시작 working tree: `CLEAN`

## 1. 검색 전 coverage audit

Typed Stress를 제외한 Gold metadata만 읽었고 retrieval/model/SEALED semantic artifact는
실행하지 않았다.

| dataset | query / bundles | answerability S/P/N | DIRECT / RELATED / CONTRADICTS / INSUFFICIENT query |
| --- | ---: | ---: | ---: |
| Original | 21 / 5 | 13 / 1 / 7 | 14 / 2 / 4 / 3 |
| Long-form | 24 / 6 | 14 / 1 / 9 | 15 / 0 / 9 / 1 |
| Robustness | 24 / 6 | 24 / 0 / 0 | 24 / 0 / 0 / 0 |
| 전체 | 69 / 17 | 51 / 2 / 16 | 53 / 2 / 13 / 4 |

semantic paraphrase 26건과 abstract competency 18건은 모두 supported였고,
other-actor 7건은 positive 0, negation 7건은 positive 0이었다. partial은 mixed-language
compound query 2건뿐이다. 따라서 기존 자산은 ranking smoke/positive funnel에는
유용하지만 일반 semantic state ceiling을 독립적으로 판정하기엔 부족하다.

`semantic-support-stress-1.0.1`을 기존 Robustness 문서 재사용 overlay로 추가했다.
신규 문서/Production 변경은 0이며 검색 전 `INPUT_FROZEN`이다.

## 2. Semantic Stress input freeze

- 계약 revision: `c5297f2` (Oracle 결과 전 `CAPABILITY_GATE` 동결)
- dataset: `semantic-support-stress-1.0.1`
- 이전 `1.0.0`: retrieval/model 실행 전에 partial의 required DIRECT aspect 누락을 발견해
  `INVALID_INPUT_HISTORICAL`로 철회; 결과·candidate·embedding 0
- Gold-free runtime SHA-256: `c20d42920ee4cc509981de5e50dd70cfa6f5ebf9a5c3fdfad229c1ae546528af`
- full input SHA-256: `b541a570eb304970d165ce25e835f15576381d29670c7439bd60c25f3e46f75d`
- payload: 11 files; DEV/CAL 12/12 query, 6 bundles, base TXT 6개 참조, 문서 복사 0
- answerability: `SUPPORTED 8 / PARTIALLY_SUPPORTED 8 / NOT_SUPPORTED 8`
- relation: `DIRECT 16 / RELATED 4 / INSUFFICIENT 4 / CONTRADICTS 8`
- language: `KO 8 / EN 10 / KO_EN_MIXED 6`
- aspect: 24/24 explicit; partial 8/8이 required `SUPPORTED DIRECT` aspect와 required
  `NOT_SUPPORTED RELATED/INSUFFICIENT` aspect를 함께 보존
- other-actor: 7 query 중 source-grounded `TEAM` positive 1건 포함
- Gold span/anchor SHA 24/24, base document SHA 6/6, runtime/Gold question identity 24/24
- runtime question schema: `queryId / userBundleId / query / language` only
- freeze 시점 execution: retrieval `false`, embedding `false`, SEALED access `false`

독립 input-freeze audit에서 root manifest가 Gold 분포와 Gold payload hash를 포함한 채
pre-freeze runtime loader에 노출되는 문제를 발견했다. 결과 실행 전에 Gold-free
`runtime-manifest.json`을 분리했고, candidate freeze는 runtime SHA만 사용한다. full input
manifest와 Gold는 verified candidate freeze 후에만 접근한다.

Gold-free B3 replay, candidate canonical hash/phase guard, Oracle stable partition/failure stage/metric과
stress loader를 evaluation-only 신규 파일로 격리했다. ExpectedEvidence가 판단하지 않은 candidate는
`INSUFFICIENT`로 추정하지 않고 `UNJUDGED`로 보존한다. Focused 검사 26건은
`PASS 25 / SKIPPED 1`이며 skipped 1건은 opt-in 공식 benchmark다. 이 검증은
모델/benchmark 실행이 아니다.

## 3. 시작 무결성

- 기존 B3 raw report: Original/Long-form `acc4c7e7bdae9296e7ae543ded16dde2f92ad39911df90171c6b09606fca2918`,
  Robustness `f0bf5481a572ad5e21f91916e5cd0fc6c309c50ec59e2f75ac2386433133324d`
- B3 builder SHA-256: `64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39`
- SEALED FINAL tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- SEALED FINAL combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- flags: `opened=false`, `searchExecuted=false`; `CURRENT_FRESH_BASELINE=NOT_RUN`
- Oracle, Stress B3 retrieval, semantic validator, Sparse, Parent Dense: `NOT_RUN`

## 4. 결과

`NOT_RUN`. Stress input freeze와 code freeze 후에만 기록한다.
