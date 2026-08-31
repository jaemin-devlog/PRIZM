# PRZ-028 Evidence

- 상태: `IN_PROGRESS / INPUT_FROZEN / OFFICIAL_T0_T1_RUN / NEEDS_ADJUSTMENT`
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
- DEV/CAL T0/T1: code freeze에서 공식 1회 실행
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

## 5. Evaluation-only 구현과 code freeze 근거

- 지원 kind: `QUANTITY`, `DATE`, `IDENTIFIER_NUMBER`, `LITERAL_IDENTIFIER`
- runtime 입력: query text와 atomic EvidenceChild `sourceText`/provenance만 사용; Gold·category·answerability·
  retrievalText·heading context·runtime DB ID 입력 0
- ranking: 같은 full B3 후보를 `SATISFIED → UNKNOWN → CONTRADICTED`로 stable partition; 추가·삭제 0
- semantic guard: parser-empty query는 원래 list object/order 그대로 보존
- storage: persistent index/write 0; in-memory observation cache의 candidate/observation/payload count만 측정
- nDCG: repeated group 0 gain, exponential gain, exact top-5 ideal search
- code-freeze guard: 전달 SHA 형식뿐 아니라 실제 clean Git `HEAD` 일치 필수

Pure DEV/CAL stress conformance는 query constraint `24/24 exact`, candidate observation `24/25 exact`
(`precision=recall=F1=0.96`)이다. 남은 1건은 U33 source qualifier `community operations pilot`과
frozen annotation `community operations`의 exact mismatch다. 이를 고치기 위해 fixture/gold를 재봉인하거나
문자열 예외를 추가하지 않았다. Unit-state는 104개 중 102개 일치하며 남은 두 mismatch는
`SV3-U33-Q01/Q02 × SV3-U33-P02-E01`의 expected `CONTRADICTED`, predicted `UNKNOWN`이다.

Pre-freeze 독립 감사에서 발견해 수정한 항목:

- query-dependent cosine score가 candidate Gold identity를 깨던 official 실행 차단
- DEV/CAL strict stress 일부만 조용히 집계할 수 있던 split coverage gap
- passage state를 104 unit label에 복제하던 state metric 오염
- stale Dense rank, evaluator 이중 실행과 slice-total latency 표기
- greedy IDCG가 nDCG를 1보다 크게 만들 수 있던 문제
- predicted state를 hard-negative 개선으로 잘못 쓰던 metric과 단일-user PROMISING 가능성

실제 검증:

| 명령/검사 | 실제 결과 |
| --- | --- |
| 관련 typed/structural evaluation tests | `PASS`; 97 tests, failures/errors/skipped 0 |
| corrected stress DEV/CAL inventory | `PASS`; 24 query / 25 observation / 104 state label |
| query extraction pure conformance | `PASS`; 24/24 exact |
| observation extraction pure conformance | `KNOWN_LIMITATION`; 24/25 exact, U33 1 mismatch |
| unit-state pure conformance | `KNOWN_LIMITATION`; 102/104, U33 2 `CONTRADICTED→UNKNOWN` |
| official BGE T0/T1 | code freeze 전 당시 `NOT_RUN` |
| SEALED FINAL search/prediction/result | `NOT_RUN`; metadata/hash verification only |

## 6. 공식 T0/T1 실행

- code freeze commit: `2e9c9ff2fb21744a6fea9b8bcf03962e392c84f8`
- official input freeze: `3e3bf652c5661a5bab34eb68e174dcea7459d6b5`
- official stress: `search-v3-typed-constraints-stress-1.0.1`, combined
  `96c1ddc6cbdd6722619d7806cbe418babc414c0d5179af84d4694a94c8ed015b`
- model: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, 1024 dimensions, cosine
- raw local report: `local/search-v3-evaluation/prz028/typed-constraint-t1.json`
- report SHA-256: `5bc0016a4807af099b0aff3e1fff76c63a1271a82c8f52b56dd660b2cae50d9e`
- 실행 횟수: 공식 BGE T0/T1 `1`

공식 실행은 ignored local init script로 test JVM에 freeze SHA를 전달했고, runner가 실제 clean `HEAD`와
SHA 일치를 검증한 뒤에만 시작했다. T0/T1은 query embedding과 full B3 Dense ranking을 공유했다.
실행 명령은 `gradlew.bat --init-script local/search-v3-evaluation/prz028/code-freeze.init.gradle
searchEvaluation --tests com.prizm.search.evaluation.searchv3.structural.Prz028TypedConstraintBenchmarkTest
--rerun-tasks`였고 `1/1 PASS`였다.

### 6.1 Candidate·semantic·retrieval parity

| suite | query | candidate identity | parser-empty semantic order | Recall@5/10/20 T0→T1 |
| --- | ---: | ---: | ---: | --- |
| Original Seed | 21 | 21/21 | 15/15 | `1/1/1 → 1/1/1` |
| Long-form | 24 | 24/24 | 19/19 | `1/1/1 → 1/1/1` |
| Robustness | 24 | 24/24 | 23/23 | `1/1/1 → 1/1/1` |
| Typed Stress | 24 | 24/24 | 0/0 | `1/1/1 → 1/1/1` |
| 합계 | 93 | 93/93 | 57/57 | 비열화 0 |

후보 추가·삭제·중복은 0이며 parser-empty query는 candidate ID와 순서가 완전히 같았다. 모든 suite의
nDCG@5도 비열화하지 않았다.

### 6.2 Ranking과 user-macro

| suite | direct query | query-micro Top1 T0→T1 | MRR T0→T1 | nDCG@5 T0→T1 | direct W/L/T |
| --- | ---: | --- | --- | --- | --- |
| Original Seed | 14 | `0.9286→0.9286` | `0.9643→0.9643` | `0.9736→0.9736` | `0/0/14` |
| Long-form | 15 | `0.8000→0.8000` | `0.8833→0.8833` | `0.9128→0.9128` | `0/0/15` |
| Robustness | 24 | `1.0000→1.0000` | `1.0000→1.0000` | `1.0000→1.0000` | `0/0/24` |
| Typed Stress | 13 | `1.0000→1.0000` | `1.0000→1.0000` | `1.0000→1.0000` | `0/0/13` |

User-macro도 Original `Top1 0.9333 / MRR 0.9667 / nDCG 0.9754`, Long-form
`0.8333 / 0.9028 / 0.9274`, Robustness와 Stress `1/1/1`로 T0/T1이 같았다. Stress의 6 profession과
KO/EN/KO_EN_MIXED slice 모두 Top1/MRR `1→1`, 신규 direct regression 0이었다. Direct win도 0이라
winning user와 typed kind는 각각 0이다.

### 6.3 Extraction과 three-state 판정

| 항목 | 실제 결과 |
| --- | --- |
| query constraint extraction | `24/24 exact`; precision/recall/F1 `1/1/1` |
| candidate observation extraction | `24/25 exact`; precision/recall/F1 `0.96/0.96/0.96` |
| unit-state accuracy | `102/104 = 0.9808` |
| SATISFIED | expected 13 / predicted 13 / correct 13; precision/recall `1/1` |
| CONTRADICTED | expected 19 / predicted 17 / correct 17; precision/recall `1/0.8947` |
| UNKNOWN | expected 72 / predicted 74 / correct 72; precision/recall `0.9730/1` |

두 state mismatch는 모두 U33 `community operations pilot` source와 frozen annotation
`community operations` qualifier 차이에서 발생한 expected `CONTRADICTED`, predicted `UNKNOWN`이다.
결과를 본 뒤 qualifier 예외, ontology 또는 gold 수정은 하지 않았다.

Typed kind별 direct Top1/MRR은 모두 `1→1`이었다: QUANTITY 6, DATE 3, IDENTIFIER_NUMBER 2,
LITERAL_IDENTIFIER 2 direct query. Numeric qualifier mismatch family는 Gold-expected
`CONTRADICTED@1 0→0`으로 비열화는 없지만 개선 기회도 입증하지 못했다. Date mismatch는 `2→0`,
identifier-number mismatch는 `2→0`으로 개선됐다.

### 6.4 Hard negative

Typed `NOT_SUPPORTED` 11문항에서 predicted `SATISFIED@1`은 `0→0`으로 안전성 비열화를 만들지
않았다. Predicted `CONTRADICTED@1`은 `7→0`, Gold-expected `CONTRADICTED@1`은 `7→1`이었다.
여섯 개선은 wrong quantity, date-before 2건, range/wrong-value, HTTP/3 mismatch, Java 21 mismatch다.
남은 1건은 U33 qualifier mismatch 때문에 runtime은 `UNKNOWN`이지만 frozen Gold는
`CONTRADICTED`인 제한 사항이다. 이는 no-answer threshold 성능으로 표현하지 않는다.

### 6.5 운영 관찰

Stress에서 query parse p95 `0.1673 ms`, one-time candidate observation parse p95 `0.3340 ms`,
match/partition p95 `0.0567 ms`, online added p95 `0.1885 ms`였다. 같은 실행의 shared query embedding
p95 `36.5127 ms`와 Dense ranking p95 `0.1671 ms` 합보다 작아 사전 latency envelope Gate를 통과했다.
Persistent index/storage 0 Gate도 통과했지만 exact additional heap은 측정하지 않았다. T0/T1
end-to-end p95는 `36.6063→36.7628 ms`였다. Suite별 online added p95는 Original `2.5701 ms`,
Long-form `0.2664 ms`, Robustness `0.2002 ms`, Stress `0.1885 ms`이며 모두 각 shared B3 envelope 안이다.

Stress observation cache는 24 candidate / 25 observation / canonical payload 2,854 UTF-8 bytes였고
persistent index/storage write는 `0/0`이다. JVM heap point observation은
`22,800,480→47,307,296 bytes`였지만 실행 전체를 포함한 비격리 관찰이므로 Typed Exact 메모리
증가로 해석하지 않는다. Exact additional heap은 `NOT_MEASURED`다.

### 6.6 판정

Candidate/semantic parity, Recall, nDCG, predicted SATISFIED@1 안전성, latency envelope와 persistent
storage 0 hard gate는 모두 통과했다. Exact additional heap은 `NOT_MEASURED`다. 하지만 Typed Stress
direct Top1/MRR 순증, 최소 2 direct win, 복수 winning user/kind와
qualifier/date/identifier-number 세 family 전부의 개선 조건은 충족하지 못했다. Hard-negative의
Gold-expected contradiction `7→1`은 제한된 순증이므로 사전 정책에 따른 최종 판정은
`NEEDS_ADJUSTMENT`다.

현재 T1은 Production 채택 대상이 아니며 Sparse 단계 진입 근거도 아니다. 후속 조정은 direct ranking
개선 기회가 있는 새 stress version과 qualifier observation 계약을 먼저 재동결해야 한다. 현재 input,
code와 결과는 `HISTORICAL_RESULT`로 보존한다.

## 7. SEALED FINAL과 금지 범위 확인

- SEALED FINAL combined:
  `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- `opened=false`, `searchExecuted=false`; PRZ-028 official evaluator의 semantic
  access/prediction/result `false/false/false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`
- Production/dependency/migration/frontend/MCP/Docker 변경: `0`
- Sparse, PR, push, merge: `NOT_RUN`

PRZ-025 integrity validator는 요청된 기존 integrity test로서 SEALED fixture를 schema/source/hash
검증 목적으로 읽었지만 embedding, retrieval, ranking, prediction과 result는 생성하지 않았다. 이는
PRZ-028 official evaluator의 SEALED semantic access가 아니며 manifest flags와 tracked bytes를 바꾸지
않았다.

## 8. 최종 검증

| 명령/검사 | 실제 결과 |
| --- | --- |
| official T0/T1 benchmark | `PASS`; code freeze에서 1/1, 이후 재실행 0 |
| 관련 non-BGE searchEvaluation unit/regression | `PASS`; 14 suites / 116 tests, failure/error/skipped 0 |
| PRZ-025 validator unit | `PASS`; 18/18 |
| PRZ-025 validator CLI | `PASS`; `FRESH_BENCHMARK_SEED_FROZEN`, Final search false |
| v1.0.0 / v1.0.1 stress materializer `--check` | `PASS`; `693331c...` / `96c1ddc...` |
| `node scripts/verify-oss-readiness.mjs` | `PASS`; Markdown 193, local links 766, verifier 16/16, external 97/97 |
| `git diff --check` | `PASS` |
| code-freeze 대비 forbidden scope audit | `PASS`; PRZ-028 문서 4개 외 변경 0 |
| SEALED manifest blob/hash/flags | `PASS`; 기준 HEAD와 동일, `e5b315...`, false/false |
| full backend/integration/frontend/Docker | `NOT_RUN`; evaluation-only 변경에 불필요 |

독립 read-only audit에서 metric 수치, 문서 상태, Production/SEALED 경계와 금지 경로 diff를 다시
대조했다. 이 검증은 공식 BGE benchmark를 재실행하거나 결과를 보고 parser/gold/policy를 수정하지
않았다.
