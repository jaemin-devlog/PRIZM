# PRZ-028 Search V3 정확 조건 검증

- 상태: `VERIFIED / FINAL_ROLE_EVIDENCE_VALIDATION_ONLY / STRESS_1.0.1_HISTORICAL_FROZEN / STRESS_1.1.0_OFFICIAL_RESULT_FROZEN`
- 기준 branch: `PRZ-028-typed-exact-constraints`
- 기준 source: `PRZ-026-structural-parsing-parent-child@a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9`
- 선행 조건: `DEPENDS_ON_PRZ_025@5f8229f88251938dc5b34588676cc69edf409c99`, `DEPENDS_ON_PRZ_026_B3`
- 제외 계보: `PRZ-027` Cross Encoder `NO_GO` 구현은 baseline과 branch ancestry에 포함하지 않음
- Production 적용: `NOT_RUN`

## 1. 목적과 단일 가설

현재 V3 기준선인 B3 Structural RetrievalPassage + Ollama `bge-m3` 1024차원 cosine raw Dense의
후보 집합을 그대로 사용한다. 숫자, 날짜, identifier-number/version과 literal identifier 조건을
query와 candidate `sourceText`에서 deterministic하게 추출하고 정확 조건 상태로 stable partition할
때 typed query의 순위 품질이 개선되는지를 DEV/CAL에서만 검증한다.

T0는 B3 Dense, T1은 같은 B3 Dense 후보에 Typed Constraint stable partition만 적용한다. 이번
Phase의 Typed Constraint는 후보를 만들거나 삭제하지 않으며 Production 채택 기능도 아니다.

## 2. 범위와 비범위

구현은 `src/searchEvaluation/**`, PRZ-028 문서와 사전 봉인한 DEV/CAL stress fixture에만 둔다.
`src/main/**`, Production parser/search/TextChunker, DB·migration, dependency, frontend, MCP, Docker와
`v1.0.0`은 변경하지 않는다.

Sparse, FTS/BM25, RRF, Parent Context, Parent Dense, Cross Encoder, QueryPlanner, rewrite,
fallback/rescue, threshold, MMR, Evidence Selector와 LLM은 `NOT_RUN`이다. 별도 runtime
index/storage/DB schema도 만들지 않는다. DEV/CAL 평가 정답을 표현하는 PRZ-028 전용 annotation
schema는 이 금지 대상이 아니다.

## 3. Typed Constraint와 Observation 계약

지원 범위는 다음 네 종류다.

1. `QUANTITY`: `EQ/GT/GTE/LT/LTE/RANGE`, normalized numeric value, 원문 unit과 nearby qualifier
2. `DATE`: `YEAR/YEAR_MONTH/FULL_DATE` precision과 비교/range
3. `IDENTIFIER_NUMBER`: 일반적인 identifier + version/number surface (`Java 17`, `HTTP/2`, `v2.0` 등)
4. `LITERAL_IDENTIFIER`: quoted literal 또는 generic identifier surface의 exact normalized match

모든 query constraint와 candidate observation은 원문 code-point `[start,end)` offset을 보존한다.
Candidate observation은 `sourceText`에서만 추출하고 `retrievalText`, heading context, gold annotation을
입력으로 사용하지 않는다. Unicode/case/공백·구두점 정리와 최소 한국어 조사 제거만 허용한다.
직무·기술·제품 ontology, 동의어 확장, query 문자열 예외와 모델 추론은 금지한다.

일반 normalization은 NFKC, Locale-independent case fold, 숫자 comma 제거, `%`와 ASCII unit의
case/space 정리이며 한국어 count unit surface는 보존한다. `semanticType`, 직무별 unit alias나
ontology label은 runtime match에 사용하지 않는다. Date는 English `after=GT`, `before=LT`, Korean
`이후/부터=GTE`, `이전=LT`, range 양끝 inclusive다. precision은 `YEAR`, `YEAR_MONTH`,
`FULL_DATE`로 보존한다.

Parser precedence는 date range/date → explicit quantity(number+unit/comparator) →
identifier-number/version → literal identifier다. 먼저 소비한 number span을 낮은 우선순위 parser가
중복 constraint로 만들지 않는다. Literal은 quoted non-blank text, 또는 mixed-case·대문자 acronym·
내부 숫자/`-_.` 구조를 가진 Latin identifier만 unquoted generic signal로 인정한다. 일반 소문자
영어 문장을 identifier 사전처럼 수집하지 않는다.

숫자 표기는 comma가 있는 정수/소수와 일반 decimal을 지원한다. `1.3k`, `천 명 넘게`처럼 scale 또는
자연어 수량 해석이 필요한 표현은 첫 버전 지원 밖이며 억지로 변환하지 않고 `UNKNOWN`으로 남긴다.

Qualifier가 불확실하거나 같은 대상임을 입증할 수 없으면 `UNKNOWN`이다. 예를 들어 `사용자
1000명 이상`과 `데이터 1300건`은 값이 커도 `SATISFIED`가 아니다. Percentage의 `감소/증가`도
qualifier 일부이며 반대 방향을 같은 observation으로 취급하지 않는다.

## 4. Match와 ranking 계약

각 constraint의 candidate 상태는 `SATISFIED`, `CONTRADICTED`, `UNKNOWN`이다. 같은 unit/qualifier
또는 identifier가 확인되고 값이 조건을 충족하면 `SATISFIED`, 같은 대상의 명시값이 조건을
위반하면 `CONTRADICTED`, 충분한 원문 관찰이 없으면 `UNKNOWN`이다.

동일 constraint에 여러 observation이 있으면 하나라도 충족할 때 `SATISFIED`, 그렇지 않고 같은
대상의 명시적 위반이 하나라도 있으면 `CONTRADICTED`, 나머지는 `UNKNOWN`으로 줄인다. 여러
constraint는 모두 충족할 때만 candidate `SATISFIED`다. 하나라도 명시적으로 위반하면
`CONTRADICTED`, 나머지는 `UNKNOWN`이다. T1은 constraint-bearing query에서만 기존 B3 순위를
`SATISFIED → UNKNOWN → CONTRADICTED`로 stable partition한다. 같은 상태 내부의 candidate ID와
순서는 T0와 동일해야 한다. 수작업 boost, threshold, query·직무·언어별 가중치는 없다.

Constraint가 없는 query는 candidate ID와 순서까지 `T0 == T1`이어야 한다. 모든 query에서 후보
multiset도 완전히 동일해야 한다.

## 5. Fresh Typed Stress Set

현재 69개 DEV/CAL annotation은 machine-readable numeric 6, date 1, identifier-number positive 1로
범위가 부족하다. 검색 결과를 보기 전에 별도 `search-v3-typed-constraints-stress-1.0.0`을 만든다.
DEV 12 / CALIBRATION 12 query를 quantity boundary, qualifier mismatch, wrong value, range,
percentage direction, duration, date before/after/range, identifier-number exact/mismatch, literal
exact/near-match와 NOT_SUPPORTED hard negative에 배분한다.

Stress Set은 synthetic·비개인 fixture, 독립 user/document/template/generator/source-fact/query
lineage만 사용한다. materializer의 per-file/combined SHA-256과 count를 구현 전에 freeze하고 이후
수정하지 않는다. typed annotation은 expected query constraint와 query offset, evidence-unit별 expected
observation과 absolute document provenance, query/evidence-unit expected match state를 포함한다.
Constraint core와 qualifier/direction은 각각 source-grounded code-point span을 가져야 하며 qualifier를
constraint core span에 선택적으로 포함하지 않는다.
Runtime passage/DB ID는 허용하지 않는다. 기존 Original/Long-form/Robustness와 SEALED FINAL을
덮어쓰지 않는다.

최초 freeze `1.0.0`은 검색 전 독립 annotation 감사에서 qualifier/span 계약 결함이 발견되어
`INVALID_INPUT_HISTORICAL`로 보존했다. 이를 덮어쓰지 않고 교정·재봉인한 `1.0.1`만 PRZ-028의
최초 조정 단계 공식 stress input으로 사용했다. Final adjustment에서는 별도 봉인한 `1.1.0`을 공식
input으로 사용하며 `1.0.1` 결과는 `HISTORICAL_RESULT`로만 보존한다. 각 version의 변경 이유,
lineage와 hash는 evidence에 고정한다.

## 6. 평가와 acceptance criteria

전체는 Recall@5/10/20, Top1, MRR, nDCG@5와 user-macro를 기록한다. nDCG relevance는 candidate가
포함한 required `DIRECT_SUPPORT` Evidence Unit의 distinct count이며, 이미 상위 candidate에서 credit한
Evidence Group은 이후 0 gain이다. gain은 `2^relevance-1`, discount는 `log2(rank+1)`을 사용한다. IDCG는
candidate ID tie-break를 가진 exact top-5 dynamic search로 계산하며 greedy 근사치를 사용하지 않는다.
Typed subset은 constraint 및
observation extraction accuracy, `SATISFIED`/`CONTRADICTED` precision·recall, `UNKNOWN` rate,
Top1/MRR/nDCG@5, direct rank win/loss/tie와 유형별·hard-negative 결과를 기록한다.
Extraction exact match는 type, operator, normalized value/range, normalized unit, ordered normalized
qualifier, precision/identifier와 `[start,end)`가 모두 같은 annotation item 단위로 계산한다. Precision과
recall은 predicted item set과 frozen expected item set을 기준으로 하며 누락과 추가 추출을 모두 센다.
Direct rank win/loss/tie 분모는 DIRECT_SUPPORT query다. NOT_SUPPORTED typed query는 runtime predicted
`SATISFIED@1` 안전성, Gold-expected rank-1 state transition과 expected/predicted `CONTRADICTED@1`을
분리해 기록한다. Stable partition 구조상 predicted `SATISFIED@1` 감소는 개선 metric이 될 수 없으므로,
hard-negative 개선은 Gold-expected `CONTRADICTED@1` 감소로 판단하며 no-answer threshold 성능으로
표현하지 않는다.

필수 Gate:

- truncation 전 owner-scoped full B3 ranking을 사용하고 모든 query의 T0/T1 candidate identity
  parity와 Candidate Recall 비열화 0
- semantic query candidate·ordering exact parity
- runtime input view는 query text, candidate ID, `sourceText`, document/version과 source provenance만
  허용; `DatasetSlice`, `GoldUnit`, `ExpectedEvidence`, `coveredUnitIds/GroupIds/ParentIds`, answerability와
  category를 parser/evaluator에 전달하지 않음
- observation provenance와 gold/runtime 입력 분리; gold annotation은 partition 완료 뒤 metric
  계산에만 사용
- typed query rank 품질의 실제 순증 또는 정직한 비채택 판정
- query parsing, candidate별 observation parsing과 T1 추가 latency 측정; 별도 index/storage 0. 운영 Gate는
  T1 added p95가 같은 실행의 shared B3 query-embedding p95와 Dense-ranking p95 합을 넘지 않아야 한다.
- SEALED FINAL combined `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
  `opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`
- Production·dependency·migration 변경 0

입력 freeze에는 B3 builder SHA-256
`64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39`, EvidenceChild builder
SHA-256 `6ff76f49df332319fac987a59be4ead11d7ecda90b44f0d11e0cb538acd6cb83`, model
`bge-m3:latest` digest `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`,
1024 dimensions와 cosine을 포함한다. T0/T1은 query embedding 하나와 full candidate ranking 하나를
공유한다.

PRZ-028 loader/evaluator는 `sealed-final` semantic corpus/questions/gold 경로를 fail-closed로
거부한다. 허용되는 SEALED 확인은 manifest와 byte/hash/flags 무결성 검증뿐이며 search, prediction,
result와 semantic evaluation은 0건이어야 한다.

판정은 `PROMISING`, `NEEDS_ADJUSTMENT`, `NO_GO` 중 하나다. 결과를 본 뒤 stress input, parser
policy, ranking policy 또는 gold를 수정하지 않는다.

## 7. Implementation freeze 계약

구현은 evaluation-only `QUANTITY`, `DATE`, `IDENTIFIER_NUMBER`, `LITERAL_IDENTIFIER` parser,
atomic `sourceText` observation extractor, three-state evaluator와 candidate-preserving stable partition이다.
Parser/evaluator 입력에는 Gold·answerability·category·runtime DB ID가 없으며 Gold는 ranking 완료 뒤 metric에만
결합한다. Query constraint extraction의 pure conformance는 24/24이며 observation은 25개 중 24 exact다.
유일한 observation mismatch는 frozen U33 `community operations pilot` source와 annotation의
`community operations` qualifier 차이로, input을 다시 바꾸거나 `pilot` 예외를 넣지 않는다. 그 영향으로
104 unit-state 중 U33 두 label은 `CONTRADICTED→UNKNOWN`, 나머지 102개는 일치한다.

Candidate quantity range observation은 v1에서 false exact match를 막기 위해 전체 span을 reserve하고
`UNKNOWN`으로 둔다. 사전 없는 TitleCase-number 구조는 `Java 17`뿐 아니라 `Top 10`, `Phase 2`,
`May 2025`도 typed로 볼 수 있는 공개 한계이며 결과를 본 뒤 blacklist를 추가하지 않는다.

공식 판정의 hard gate는 candidate identity, parser-empty semantic order, suite별 Recall@5/10/20과 nDCG@5
비열화 없음, predicted hard-negative `SATISFIED@1` 비증가, 운영 Gate와 persistent index/storage 0이다.
`PROMISING`은 추가로 Stress Top1 또는 MRR 개선, direct win 최소 2건·loss 0, 최소 2 user와 2 typed kind의
win, qualifier/date/identifier-number mismatch 각 family의 Gold-expected `CONTRADICTED@1` 감소를 모두
요구한다. 일부 순증만 있으면 `NEEDS_ADJUSTMENT`, 순증이 없거나 hard gate가 깨지면 `NO_GO`다.
공식 runner는 전달받은 code-freeze SHA와 실제 clean `HEAD`를 비교한 뒤에만 BGE를 실행한다.

## 8. Stress 1.0.1 HISTORICAL_RESULT 공식 DEV/CAL 판정

당시 code freeze `2e9c9ff2fb21744a6fea9b8bcf03962e392c84f8`에서 Stress 1.0.1 공식 T0/T1을
한 번 실행했다.
Candidate identity `93/93`, parser-empty semantic order `57/57`, 모든 suite의 Recall@5/10/20과
nDCG@5 비열화 없음, persistent index/storage 0으로 hard gate는 통과했다. 그러나 Typed Stress의
DIRECT_SUPPORT 13문항은 T0부터 Top1/MRR이 1.0이어서 T1 direct win/loss/tie가 `0/0/13`이었고,
qualifier mismatch family도 Gold-expected rank-1 contradiction `0→0`으로 개선을 입증하지 못했다.

NOT_SUPPORTED typed hard negative 11문항에서는 Gold-expected `CONTRADICTED@1`이 `7→1`로 줄었다.
이는 잘못된 명시값을 Top1에서 뒤로 보내는 제한된 순증이지만 직접 순위 Gate와 세 mismatch family
Gate를 모두 충족하지 못하므로 판정은 `NEEDS_ADJUSTMENT`다. Production 채택과 Sparse 후속 실험은
승인하지 않는다. 현재 input·code freeze 결과는 `HISTORICAL_RESULT`로 보존하며 parser, gold 또는
판정 정책을 바꾸는 후속 조정은 새 dataset version과 새 freeze가 필요하다.

## 9. Final adjustment의 입력 우선 계약

Stress `1.0.1`과 code freeze `2e9c9ff`의 결과는 `HISTORICAL_FROZEN`이며 수정하거나 재평가
입력으로 다시 봉인하지 않는다. Final adjustment는 evaluator 변경 전에 별도
`search-v3-typed-constraints-stress-1.1.0`을 materialize하고 `INPUT_FROZEN`으로 커밋한다. 그 뒤
qualifier/evaluator, runner와 판정 정책을 구현·검증하고 별도 `CODE_FROZEN` commit을 만든다.
공식 Stress 1.1.0 T0/T1 BGE 실행은 두 freeze가 일치할 때 단 한 번만 허용한다. 결과를 본 뒤
입력·gold·코드·정책을 바꾸면 이 결과는 historical이며 새 stress version이 필요하다.

Stress 1.1.0은 synthetic DEV/CAL 전용 6 user bundles, 6 documents, 24 queries다. 각 split은
3 bundles, 12 queries, `SUPPORTED 8 / NOT_SUPPORTED 4`, Korean/English/mixed 각 4 query다.
Primary capability family는 `quantity_wrong_value`, `qualifier_mismatch`, `date`,
`identifier_number`, `percentage_direction`, `range_boundary`이며 각 family를 split마다 2 query,
전체 4 query로 고정한다. 같은 대상의 wrong/correct value, 같은 숫자의 다른/correct qualifier,
날짜, identifier-number, percentage 방향과 range/duration/boundary를 검색 결과를 보지 않고
자연스러운 문서 문맥에 배치한다.

1.1.0은 Original, Long-form, Robustness, Stress 1.0.0/1.0.1과 user/document/version/template/
generator/source-fact/question lineage 또는 normalized query를 공유하지 않는다. SEALED FINAL은
manifest hash/flags와 unified lineage identifier의 collision metadata만 확인하며 document/question/gold
semantic fixture는 읽지 않는다. Per-evidence annotation은 상태와 diagnostic reason을 함께 봉인한다.
Schema-contract, Gold/answerability, source와
query code-point span, qualifier/direction span, ID·lineage, inventory, per-file/combined SHA-256을
deterministic validator로 검증한다. Materializer는 overwrite를 거부하고 `--check`에서 byte-for-byte
재생성을 검증한다.

## 10. Final qualifier와 diagnostic 계약

Qualifier 비교는 grounded surface/span을 바꾸지 않는 별도 comparison token에만 NFKC, lowercase,
punctuation/space boundary normalization과 최소 한국어 조사 제거를 적용한다. 두 qualifier가 exact이거나
required qualifier의 whole-token sequence가 observed qualifier 안에 **연속된 동일 순서**로 포함될
때만 `EXACT` 또는 `REQUIRED_SUBSET` 호환이다. Empty는 empty와만 호환한다. Unordered set 포함,
부분 문자열 포함, synonym/ontology/직무·기술 사전, embedding과 LLM은 금지하며 애매하면 mismatch다.

- `SATISFIED`: qualifier/unit/identifier target이 호환되고 값·범위·방향 조건도 충족
- `CONTRADICTED`: 같은 target이 확인되고 값·범위·방향이 조건을 명시적으로 위반
- `UNKNOWN`: 다른 target이거나 같은 target인지 입증되지 않거나 관찰이 불충분

Qualifier mismatch는 값이 같아도 반드시 `UNKNOWN + QUALIFIER_MISMATCH`이며 `SATISFIED`가 될 수
없다. 같은 qualifier의 wrong value/direction은 `CONTRADICTED`다. Diagnostic reason은
`MATCHED`, `VALUE_MISMATCH`, `DIRECTION_MISMATCH`, `QUALIFIER_MISMATCH`, `UNIT_MISMATCH`,
`NO_MATCHING_OBSERVATION`, `AMBIGUOUS_OBSERVATION`의 generic 집합이다. Reason은 report/검증에만
쓰고 ranking에는 쓰지 않는다. Stable partition은 계속 상태만으로
`SATISFIED → UNKNOWN → CONTRADICTED`이며 동일 상태의 Dense 순서를 보존한다.

## 11. Final role Gate — 결과 전 동결

무결성, input/code/model SHA, candidate identity 또는 semantic parity가 실패하면 official run은
`INVALID_RESULT / ROLE_NOT_ASSESSED`이며 `DROP`으로 위장하지 않는다. 유효한 run은 다섯 suite를
Original, Long-form, Robustness, Stress 1.0.1 regression과 Stress 1.1.0 official capability로 분리한다.
역할은 Stress 1.1.0으로 판정하고 기존 네 suite는 신규 회귀 0 Gate로 사용한다.
공식 비교의 candidate K는 owner scope의 B3 Retrieval Passage 전체를 보존하는
`ALL_OWNER_SCOPED_B3_PASSAGES`다. 결과를 보기 전에는 별도 top-K truncation을 도입하지 않는다.

공통 validation/operation Gate:

- query extraction F1 `= 1.0`, observation extraction F1 `>= 0.95`, status accuracy `>= 0.95`
- SAT precision `= 1.0`, CONTR precision `>= 0.95`, CONTR recall `>= 0.90`
- qualifier-mismatch SAT false positive `= 0`
- same-qualifier wrong-value expected `CONTRADICTED` recall `= 1.0`
- Stress 1.1.0의 singular frozen diagnostic reason exact conformance `= 1.0`
- candidate identity와 parser-empty semantic ordering exact parity
- 모든 suite Recall@5/20과 nDCG@5 비열화 0, direct rank-1 loss 0
- persistent index/storage 0, online-added p95가 같은 run의 shared embedding p95 + Dense p95 이하

`RANKING_COMPONENT`는 공통 Gate에 더해 Stress 1.1.0 Top1 또는 MRR의 strict improvement,
direct win `>= 2`, loss `= 0`, winning user `>= 2`, winning primary family `>= 2`, 그리고 최소 한
Gold-expected wrong-condition rank-1 demotion과 predicted `SATISFIED@1` 비증가를 모두 요구한다.

공통 Gate는 통과하지만 Ranking Gate가 미달하면 `EVIDENCE_VALIDATION_ONLY`다. 이 경우 parser와
evaluator는 evidence selection 뒤 `FOUND/PARTIAL/NONE` 검증 후보로만 보존하고 Dense ranking에서는
제거한다. 공통 validation Gate가 실패하면 `DROP`이다. Sparse는 역할 판정과 PRZ-028 종료 전까지
`NOT_RUN`이다.

## 12. Final role 결정

Stress 1.1.0 input freeze `e32b9683a7e366e9f7298dc94f04657410abc08e`와 code freeze
`194bf80771f5ecedf91adb0cc6b8f835c4b21b16`을 일치시킨 뒤 공식 BGE T0/T1을 정확히 한 번
실행했다. 공통 integrity, extraction/state, candidate/semantic parity, Recall/nDCG, direct-rank1,
latency와 storage Gate는 통과했다. 그러나 Stress 1.1.0 DIRECT_SUPPORT 16문항의 T0/T1
Top1/MRR/nDCG@5가 모두 `1.0→1.0`, direct W/L/T가 `0/0/16`이어서 strict ranking improvement,
winning user와 winning primary family가 모두 0이었다.

따라서 Typed Constraint의 최종 역할은 `EVIDENCE_VALIDATION_ONLY`다. Parser/evaluator와 generic
diagnostic reason 계약은 향후 evidence selection 뒤 source-grounded `FOUND/PARTIAL/NONE` 판정을
검증하는 후보로 보존하지만 Search V3 Dense ranking 구성요소로 채택하지 않는다. Production 적용,
Sparse, PR, push와 merge는 `NOT_RUN`이며 PRZ-028의 evaluation-only 역할 판정은 완료됐다.
