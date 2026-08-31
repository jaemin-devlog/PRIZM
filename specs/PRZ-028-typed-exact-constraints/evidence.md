# PRZ-028 Evidence

- 상태: `IN_PROGRESS / INPUT_FROZEN / BENCHMARK_NOT_RUN`
- 시작 branch / HEAD: `PRZ-027-cross-encoder-reranking@7271654b80ba7db3bc9cec89cba8ba1000660132`
- 현재 branch base: `PRZ-028-typed-exact-constraints@a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- PRZ-025 dependency: `5f8229f88251938dc5b34588676cc69edf409c99`
- PRZ-026 B3 lifecycle close: `1bbc1d761bd314a17e8f3ed4e2bcceb23a2fc96a` (`PROMISING`)
- PRZ-027 final: `7271654b80ba7db3bc9cec89cba8ba1000660132` (`NO_GO`, branch ancestry에서 제외)
- 시작 working tree: `CLEAN`

## 1. 시작 사실

PRZ-028 branch는 PRZ-027의 세 commit을 제외한 PRZ-026 HEAD `a7dbb12`에서 생성했다. C1
source evidence의 역사 판정은 `NEEDS_ADJUSTMENT`이며 ancestry에 남아 있다. 이번 요청의 C1
비채택/`NO_GO` 방향은 PRZ-028 baseline 제외 결정으로만 적용하고 과거 판정을 소급 변경하지 않는다.
T0/T1 baseline은 B3 `sourceText` raw Dense path다.
Parent Context, Parent Dense, Cross Encoder와 QueryPlanner는 사용하지 않는다.

기존 Original/Long-form/Robustness DEV/CAL 총 69문항의 annotation coverage만 검사했다.

| 항목 | 실제 count |
| --- | ---: |
| machine-readable numeric constraint query | 6 |
| machine-readable date constraint query | 1 |
| machine-readable entity constraint query | 6 |
| identifier-number/version-like positive | 1 |
| numeric operator | `GTE 5 / EQ 1 / 나머지 0` |
| Long-form/Robustness machine-readable query constraint | `0 / 0` |

따라서 typed extraction과 mismatch를 판정하기에 부족하여 결과 실행 전에 별도 24문항 stress input이
필요하다고 판정했다. 이 판단에는 Dense ranking/result를 사용하지 않았다.

## 2. 보존 상태

- SEALED FINAL combined SHA-256:
  `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- flags: `opened=false`, `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`
- T0/T1/Stress benchmark: `NOT_RUN`
- Production/dependency/migration/frontend/MCP/Docker 변경: `0`

## 3. 최초 Typed Stress input freeze — INVALID_INPUT_HISTORICAL

- dataset: `search-v3-typed-constraints-stress-1.0.0`
- generator: `scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs`
- generation source revision: `a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9`
- root combined SHA-256:
  `693331c20cd483a8e90696be8e8a39e845475d4e330505c45804b91b80614aae`
- split SHA-256: DEV `5a1902e416e9414d5c9345b87ec9138a066839eb0eb285b261588b346bf02b00`,
  CALIBRATION `603ae2cc0f2b283f464d75b564eea57d756004972bdb29de1634e62ed1a50d22`
- 규모: synthetic 6 bundles / 6 documents / 24 queries / 26 Evidence Units / 25 typed observations
- split: DEV 3 bundles·12 queries / CALIBRATION 3 bundles·12 queries
- query language: KO 8 / EN 8 / KO_EN_MIXED 8
- profession: DATA_AI_INFRA, DESIGN_PRODUCT, FRONTEND_MOBILE, MARKETING_SALES,
  NON_DEVELOPMENT_GENERAL, PLANNING 각 1; BACKEND 0
- answerability: SUPPORTED 13 / NOT_SUPPORTED 11
- typed kind: QUANTITY 11 / DATE 5 / IDENTIFIER_NUMBER 4 / LITERAL_IDENTIFIER 4
- expected evidence-unit match labels: SATISFIED 13 / CONTRADICTED 19 / UNKNOWN 72

`typed-annotations.json`은 24개 query constraint와 code-point query offset, 25개 source-grounded
observation absolute offset, query가 속한 bundle의 모든 Evidence Unit에 대한 104개 expected match
state를 보존한다. Runtime passage/DB ID는 0이다. Original/Long-form/Robustness DEV/CAL과
user/document/version/template/generator/source-fact/query lineage 충돌은 0이다.

실행 전 검증:

| 명령/검사 | 실제 결과 |
| --- | --- |
| `node --check scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs` | `PASS` |
| materializer 최초 생성 | `PASS`; 19 files |
| materializer `--check` | `PASS`; exact bytes/inventory/count/source offset/lineage/hash |
| frozen output 비-`--check` 재실행 | `EXPECTED_FAIL`; exit 1, overwrite 거부, 변경 0 |
| PRZ-025 validator unit | `PASS`; 18/18, fail/skipped 0 |
| PRZ-025 benchmark validator | `PASS`; combined `1f36c4...`, Final search false |
| SEALED manifest metadata | `PASS`; combined `e5b315...`, opened/search false |

Stress input 생성·검증 중 BGE-M3, Dense ranking, T0/T1, prediction/result는 실행하지 않았다. 최초
input과 계약은 local commit `4bbbc5de040aa3c84fcb9869ece2fce85d983c0c`로 고정했다.

커밋 직후 parser 코드 작성 전에 독립 설계 감사를 수행했다. v1.0.0에는 허용 normalization만으로
gold 없이 exact qualifier를 복원할 수 없는 U32/U35 문장, 일부 quantity core span의 qualifier 포함,
English `after`와 Korean `이후`를 하나의 operator로 표현한 모호성, 실제 English인 U36 query의
mixed label이 있었다. 검색·prediction·result 0인 상태에서 발견했으므로 v1.0.0 파일과 generator를
변경하지 않고 `INVALID_INPUT_HISTORICAL`로 보존했다. 이 hash와 materializer `--check`는 계속
통과하며 성능 결과로 사용하지 않는다.

## 4. Corrected Typed Stress input freeze

- dataset: `search-v3-typed-constraints-stress-1.0.1`
- generator: `scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.0.1.mjs`
- generation source revision: `4bbbc5de040aa3c84fcb9869ece2fce85d983c0c`
- root combined SHA-256:
  `96c1ddc6cbdd6722619d7806cbe418babc414c0d5179af84d4694a94c8ed015b`
- split SHA-256: DEV `35c6e84b85302aad5f1499bc5f8a96fdeeb3a635a3d2da3595f4473654e17350`,
  CALIBRATION `b754d92e49246aec955c3bef252eeb09a6978272b7b7ba869059bf5a536e606e`
- 규모와 distribution: v1.0.0과 동일한 synthetic 6 bundles / 6 documents / 24 queries /
  26 Evidence Units / 25 observations, DEV/CAL 12/12, KO/EN/mixed 8/8/8
- operator: `EQ 2 / EXACT 8 / GT 1 / GTE 8 / LT 2 / RANGE 3`
- expected states: SATISFIED 13 / CONTRADICTED 19 / UNKNOWN 72

교정 사항은 query/source에 exact nominal qualifier를 사용하고, quantity constraint span을 numeric
core로 통일하며, query/observation qualifier code-point span 16/16과 percentage direction span 3/3을
추가한 것이다. English `after=GT`, Korean `이후=GTE`, `before/이전=LT`, range inclusive로
operator 의미를 분리했고 U36 Java query를 실제 mixed 문장으로 바꿨다. 모든 변경은 구현·검색 전에
수행됐으며 query/gold 의미나 expected match state count는 바꾸지 않았다.

| 명령/검사 | 실제 결과 |
| --- | --- |
| v1.0.1 generator `node --check` | `PASS` |
| v1.0.1 최초 생성 / `--check` | `PASS`; 19 files, combined `96c1ddc...` |
| v1.0.0 materializer `--check` | `PASS`; old tree diff 0, combined `693331c...` |
| qualifier/direction/code-point grounding | `PASS`; query qualifier 16, observation qualifier 16, direction 3 |
| v1.0.1 frozen overwrite 재실행 | `EXPECTED_FAIL`; exit 1, 변경 0 |
| external lineage separation | `PASS`; Original/Long-form/Robustness 충돌 0, v1.0.0 continuity 명시 |
| SEALED manifest metadata | `PASS`; combined `e5b315...`, opened/search false |

v1.0.1이 PRZ-028 구현과 공식 T0/T1의 유일한 stress input이다.

## 5. 아직 생성되지 않은 근거

Extraction accuracy, observation accuracy, match precision/recall, T0/T1 ranking metric, latency와
최종 판정은 모두 `NOT_RUN / NOT_VERIFIED`다. 실제 실행 전에는 이 절을 PASS로 바꾸지 않는다.
