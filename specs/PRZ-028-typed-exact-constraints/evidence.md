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

## 3. Typed Stress input freeze

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

Stress input 생성·검증 중 BGE-M3, Dense ranking, T0/T1, prediction/result는 실행하지 않았다. 이
input과 계약을 local commit으로 고정한 뒤에만 deterministic parser 구현을 시작한다.

## 4. 아직 생성되지 않은 근거

Extraction accuracy, observation accuracy, match precision/recall, T0/T1 ranking metric, latency와
최종 판정은 모두 `NOT_RUN / NOT_VERIFIED`다. 실제 실행 전에는 이 절을 PASS로 바꾸지 않는다.
