# PRZ-028 Search V3 Typed Exact Constraints

- 상태: `IN_PROGRESS / STRESS_INPUT_FROZEN / IMPLEMENTATION_NOT_STARTED`
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

## 6. 평가와 acceptance criteria

전체는 Recall@5/10/20, Top1, MRR, nDCG@5와 user-macro를 기록한다. nDCG relevance는 candidate가
포함한 required `DIRECT_SUPPORT` Evidence Unit의 distinct count이고, 같은 query candidate set에서
만든 ideal ordering으로 정규화한다. Typed subset은 constraint 및
observation extraction accuracy, `SATISFIED`/`CONTRADICTED` precision·recall, `UNKNOWN` rate,
Top1/MRR/nDCG@5, direct rank win/loss/tie와 유형별·hard-negative 결과를 기록한다.
Extraction exact match는 type, operator, normalized value/range, normalized unit, ordered normalized
qualifier, precision/identifier와 `[start,end)`가 모두 같은 annotation item 단위로 계산한다. Precision과
recall은 predicted item set과 frozen expected item set을 기준으로 하며 누락과 추가 추출을 모두 센다.
Direct rank win/loss/tie 분모는 DIRECT_SUPPORT query다. NOT_SUPPORTED typed query는
`SATISFIED@1` false-positive rate, top1 expected-state transition과 `CONTRADICTED` top1 count를 별도
분모로 보고하며 no-answer threshold 성능으로 표현하지 않는다.

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
- query parsing, candidate parsing과 T1 추가 latency 측정; 별도 index/storage 0
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
