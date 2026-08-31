# PRZ-030 Evidence

- 상태: `VERIFIED / BUILD_SEMANTIC_VALIDATOR`
- 시작 branch / HEAD: `PRZ-029-evidence-validation-selection@f7e4a7adffd5574526d6c00c76ece9113a68d69f`
- 현재 branch: `PRZ-030-semantic-evidence-validation-ceiling`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- PRZ-025 dependency: `5f8229f88251938dc5b34588676cc69edf409c99`
- PRZ-026 B3 lifecycle close: `1bbc1d761bd314a17e8f3ed4e2bcceb23a2fc96a`
- PRZ-028 final: `33c702aa0bff86502f7f70a343b60c59c13eb80f`
- PRZ-029 final: `f7e4a7adffd5574526d6c00c76ece9113a68d69f`
- 공식 code freeze: `39d8b3553d5b4c8b33c98297b7969f9296539a5a`
- 시작 working tree: `CLEAN`

## 1. 검색 전 coverage audit

Typed Stress를 제외한 기존 69 query의 Gold metadata만 읽었고 retrieval/model/SEALED
semantic artifact는 실행하지 않았다. 이후 Gold-free typed parser inventory에서 기존
69건은 semantic core 55건 / typed-overlap 14건으로 분리됐다.

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

공식 실행 정책은 기존 Original/Long-form/Robustness B3 ranking의 exact replay(model call 0)와
Stress의 freeze 후 동일 BGE-M3 B3 candidate export 1회만 허용한다. semantic validator,
추가 모델, Gold 선행 접근은 허용하지 않는다.

독립 input-freeze audit에서 root manifest가 Gold 분포와 Gold payload hash를 포함한 채
pre-freeze runtime loader에 노출되는 문제를 발견했다. 결과 실행 전에 Gold-free
`runtime-manifest.json`을 분리했고, candidate freeze는 runtime SHA만 사용한다. full input
manifest와 Gold는 verified candidate freeze 후에만 접근한다.

Gold-free B3 replay, candidate canonical hash/phase guard, Oracle stable partition/failure stage/metric과
stress loader를 evaluation-only 신규 파일로 격리했다. ExpectedEvidence가 판단하지 않은 candidate는
`INSUFFICIENT`로 추정하지 않고 `UNJUDGED`로 보존한다. Focused 검사 26건은
`PASS 25 / SKIPPED 1`이며 skipped 1건은 opt-in 공식 benchmark다. 이 검증은
모델/benchmark 실행이 아니다.

독립 input audit는 runtime/full manifest canonical bytes와 SHA, 24개 source span/anchor,
owner/document/version, query identity, aspect/relation 합집합, partial 8건의 multi-aspect 계약,
split·generator lineage를 다시 계산했고 finding 0이었다. 이후 Gold join/runner 통합 검사는
최종 강제 재실행 기준 `PASS 36 / SKIPPED 1 / FAIL 0`이었다. skipped 1건은 여전히 opt-in 공식 benchmark이며 raw
report는 생성되지 않았다.

공식 실행 전 query track도 고정했다. 전체 93건 중 `DeterministicTypedQueryParser`가 비어 있는
semantic core는 79건, typed-overlap은 14건이다. Gate, user-macro, profession/language/focus
slice는 semantic core만 사용하고 typed-overlap은 `DIAGNOSTIC_ONLY_DECISION_WEIGHT_0`이다.
ordered ID/track inventory SHA-256은
`6eb8db7e2cbbb4c4821e857dc3c72f70526c0014abf99fcc596951588cdd02c4`다. 이 분류는 candidate
생성/BGE/Gold load 전에 검증된다.

코드 동결 전 교차 검토에서 multi-aspect query가 일부 DIRECT aspect만 Top20에 있어도
recoverable로 분류될 수 있는 오류를 발견했다. 실행 전에 Recall@5/20과 failure stage를
required aspect/group completeness 기준으로 교정했다. Top1/MRR은 첫 DIRECT Evidence
ranking이라는 별도 의미를 유지한다.

## 3. 시작 무결성

- 기존 B3 raw report: Original/Long-form `acc4c7e7bdae9296e7ae543ded16dde2f92ad39911df90171c6b09606fca2918`,
  Robustness `f0bf5481a572ad5e21f91916e5cd0fc6c309c50ec59e2f75ac2386433133324d`
- B3 builder SHA-256: `64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39`
- SEALED FINAL tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- SEALED FINAL combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- flags: `opened=false`, `searchExecuted=false`; `CURRENT_FRESH_BASELINE=NOT_RUN`
- 시작 시 Oracle, Stress B3 retrieval, semantic validator, Sparse, Parent Dense: `NOT_RUN`

## 4. 결과

### 4.1 공식 실행 경계

- raw report: ignored `local/search-v3-evaluation/prz030/semantic-evidence-validation-ceiling.json`
- report SHA-256 / bytes:
  `833160910e2cdc5d8228bd0a59e46cd18b330ffdf7d832c79b688a23d872d5be` / `3,769,137`
- code freeze: `39d8b3553d5b4c8b33c98297b7969f9296539a5a`; 실행 전후 tracked/untracked tree `CLEAN`
- model: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, 1024, cosine
- Stress full/runtime SHA-256:
  `b541a570eb304970d165ce25e835f15576381d29670c7439bd60c25f3e46f75d` /
  `c20d42920ee4cc509981de5e50dd70cfa6f5ebf9a5c3fdfad229c1ae546528af`
- Stress Git tree: `f6236d6a2f86072687b040d15fc8c4046df1a66d`; 입력은 BGE 실행 전에
  `INPUT_FROZEN`
- Original/Long-form/Robustness는 동결 B3 exact replay(model call 0), Stress만 동일 BGE-M3
  candidate export를 실행했다. 네 candidate freeze가 모두 `VERIFIED`가 된 뒤 Gold를 열었다.
- 독립 raw audit: 93 query / 93 unique ID, S0/O1 candidate-set·stable-order parity error 0
- query track: semantic core 79 / typed-overlap diagnostic 14; inventory SHA-256 `6eb8db7e...`

| candidate freeze | query / candidates | canonical SHA-256 |
| --- | ---: | --- |
| Original | 21 / 63 | `fe69d2cbbc3d679b49e449d5d2b7a4c7387069d3d0b29b43df8772dc76be6d79` |
| Long-form | 24 / 288 | `0935f6eeaad188005011d25374f012b66e843f34b7653a1ec981645a4e182570` |
| Robustness | 24 / 200 | `20346aea334c7cb662dd459b7ca5b8e44a3a4dffa4382006f892c0c99fd0fba9` |
| Semantic Stress | 24 / 200 | `ee3142abfe2097799f03998cb6b7acfd35ebc0c70a58618c43c33cd8ab709da8` |

### 4.2 Semantic core aggregate

Answerability는 `SUPPORTED 47 / PARTIALLY_SUPPORTED 10 / NOT_SUPPORTED 22`이며,
DIRECT-positive는 supported+partial 57건이다.

| metric | S0 B3 Dense | O1 Gold Oracle | 변화 |
| --- | ---: | ---: | ---: |
| Direct Recall@5 | 1.0000 | 1.0000 | 0 |
| Direct Recall@20 | 1.0000 | 1.0000 | 0 |
| Direct Top1 | 0.8772 | 1.0000 | +12.28pp |
| Direct MRR | 0.9313 | 1.0000 | +0.0687 |
| judged nDCG@5 lower bound | 0.9063 | 0.9620 | +0.0557 |

Direct Recall은 required aspect/group completeness, Top1/MRR은 첫 DIRECT Evidence의 순위다.
O1 nDCG가 1이 아닌 이유는 explicit positive-gain judgment가 없는 NO_SUPPORT 3건을 0으로
보존했기 때문이다. query ceiling state는 `FOUND 47 / PARTIAL 10 / NONE 22`, 정합
`79/79`다.

```text
DIRECT-positive 57
ALREADY_CORRECT       50
RANKING_RECOVERABLE    7
RETRIEVAL_MISS          0

DIRECT 없는 22
FALSE_POSITIVE_RISK    15
NO_SUPPORT              7
PARTIAL_ONLY            0
```

Recoverable 7건은 7개 bundle에 분포하며 DIRECT의 기존 rank는 2/2/4/2/2/3/2였다.
typed-overlap 14건은 별도 diagnostic이고 Gate weight는 0이다.

### 4.3 Suite와 user-macro

| suite | semantic q / positive | S0→O1 Top1 | S0→O1 MRR | recoverable / miss |
| --- | ---: | ---: | ---: | ---: |
| Original | 14 / 8 | .8750→1 | .9375→1 | 1 / 0 |
| Long-form | 18 / 10 | .7000→1 | .8250→1 | 3 / 0 |
| Robustness | 23 / 23 | 1→1 | 1→1 | 0 / 0 |
| Semantic Stress | 24 / 16 | .8125→1 | .8958→1 | 3 / 0 |

DIRECT-positive user 16명 기준 user-macro Top1은 `.8452 → 1.0`(+15.48pp), MRR은
`.9053 → 1.0`(+0.0947)이다.

### 4.4 Profession / language slice

모든 slice의 Direct Recall@20은 1.0이고 신규 regression은 없다. 작은 DEV/CAL slice이므로
release-grade 일반화 결과로 해석하지 않는다.

| profession | q / positive | S0→O1 Top1 | S0→O1 MRR |
| --- | ---: | ---: | ---: |
| BACKEND | 2 / 1 | 1→1 | 1→1 |
| FRONTEND_MOBILE | 23 / 19 | .9474→1 | .9737→1 |
| DATA_AI_INFRA | 15 / 11 | .8182→1 | .8939→1 |
| DESIGN_PRODUCT | 15 / 11 | .8182→1 | .9091→1 |
| PLANNING | 2 / 1 | 1→1 | 1→1 |
| MARKETING_SALES | 11 / 7 | .7143→1 | .8214→1 |
| NON_DEVELOPMENT_GENERAL | 11 / 7 | 1→1 | 1→1 |

| language | q / positive | S0→O1 Top1 | S0→O1 MRR |
| --- | ---: | ---: | ---: |
| KO | 25 / 22 | .9545→1 | .9773→1 |
| EN | 38 / 28 | .8929→1 | .9464→1 |
| KO_EN_MIXED | 16 / 7 | .5714→1 | .7262→1 |

### 4.5 Semantic focus와 no-support ceiling

| slice | q / positive | S0→O1 Top1 | S0→O1 MRR | recoverable / FP risk |
| --- | ---: | ---: | ---: | ---: |
| other actor | 13 / 8 | .6250→1 | .7917→1 | 3 / 2 |
| completion | 30 / 20 | .9000→1 | .9375→1 | 2 / 9 |
| abstract | 28 / 24 | .8750→1 | .9375→1 | 3 / 1 |
| paraphrase | 45 / 39 | .8718→1 | .9316→1 | 5 / 3 |
| negation | 15 / 0 | 0→0 | 0→0 | 0 / 11 |

negation slice는 DIRECT-positive가 없어 positive ranking 근거가 아니라 NOT_SUPPORTED state와
false-positive-risk 근거다. NOT_SUPPORTED 22건 중 명시적으로 judged된 rank-1
RELATED/CONTRADICTS 15건(13 bundles)을 완벽한 validator가 `NONE`으로 판정할 수 있다.
이는 expectedEvidence 밖 candidate를 추정하지 않은 lower bound이며 실제 validator 성능이 아니다.

### 4.6 Gate와 아키텍처 판정

- retrieval blocker: Direct Recall@20 `1.0`, retrieval miss bundle `0` → 없음
- user-macro Top1 Gate: `+15.48pp` ≥ `+5pp` → PASS
- recoverable Gate: `7 bundles` ≥ `3` → PASS
- false-positive-risk Gate: `15 query / 13 bundles` ≥ `2 / 2` → PASS
- 최종: `BUILD_SEMANTIC_VALIDATOR`
- retrieval augmentation / Sparse: `DEFER`
- Parent Dense: `DEFER`

판정은 실제 validator가 성공했다는 뜻이 아니다. 동일 B3 후보를 유지한 evaluation-only 실제
semantic validator ablation을 다음 별도 Phase에서 검증할 가치가 있다는 뜻이다.

### 4.7 SEALED / Production / 검증

- SEALED FINAL tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- SEALED combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- `opened=false`, `searchExecuted=false`, `mutable=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`
- Production, migration, dependency/build, frontend, MCP, Docker, `v1.0.0`: 변경 0
- 공식 runner: `PASS 1 / FAIL 0`; raw SHA 출력과 `BUILD_SEMANTIC_VALIDATOR` 판정 확인
- PRZ-030 강제 focused: `PASS 36 / SKIPPED 1 / FAIL 0`; skip은 opt-in 공식 runner
- independent raw candidate parity: `93 query / error 0`
- `node scripts/verify-oss-readiness.mjs`: PASS; Markdown 202 / local link 770,
  tracked safety 1084, SBOM 및 verifier test 16 PASS, external link 97 OK
- `git diff --check`: PASS
- 전체 backend unit/integration, frontend app test/build: `NOT_RUN` (evaluation-only scope)
- push / PR / merge / 실제 semantic validator: `NOT_RUN`
