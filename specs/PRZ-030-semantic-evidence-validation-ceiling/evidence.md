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

`semantic-support-stress-1.0.0`을 기존 Robustness 문서 재사용 overlay로 추가한다.
신규 문서/Production 변경은 0이며 현재 상태는 `PRE_FREEZE / NOT_RUN`이다.

## 2. 시작 무결성

- 기존 B3 raw report: Original/Long-form `acc4c7e7bdae9296e7ae543ded16dde2f92ad39911df90171c6b09606fca2918`,
  Robustness `f0bf5481a572ad5e21f91916e5cd0fc6c309c50ec59e2f75ac2386433133324d`
- B3 builder SHA-256: `64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39`
- SEALED FINAL tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- SEALED FINAL combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- flags: `opened=false`, `searchExecuted=false`; `CURRENT_FRESH_BASELINE=NOT_RUN`
- Oracle, Stress B3 retrieval, semantic validator, Sparse, Parent Dense: `NOT_RUN`

## 3. 결과

`NOT_RUN`. Stress input freeze와 code freeze 후에만 기록한다.
