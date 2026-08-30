# PRZ-025 Search V3 Foundation Contract

- 상태: `IN_PROGRESS / FRESH_BENCHMARK_SEED_FROZEN`
- Phase: Architecture Contract + Fresh Generalization Evaluation Contract + materialized seed freeze
- 기준: `origin/main@2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- Search V3 구현: `NOT_RUN`
- Fresh seed materialization/integrity validation: `COMPLETED`
- Search benchmark 실행: `NOT_RUN`
- Current Fresh Baseline: `NOT_RUN`

## 1. 목적과 우선순위

이 Phase는 Search V3 알고리즘을 구현하지 않는다. 어떤 후보 알고리즘에도 같은 기준을
적용해 Current Search보다 실제로 좋아졌음을 독립적으로 증명하지 못하면 Production에
들어갈 수 없도록 계약을 먼저 고정한다.

순서는 다음과 같다.

1. 이 문서의 평가·근거 계약을 고정한다.
2. 후속 Phase에서 fresh corpus와 gold를 만들고 `SEALED_FINAL_TEST`를 봉인한다.
3. `DEV`와 `CALIBRATION`만 보며 ablation을 수행한다.
4. 동결한 Current Search와 최종 V3 후보를 같은 sealed benchmark에서 독립 비교한다.
5. Adoption Gate를 모두 통과한 구성만 별도 변경 절차를 거쳐 Production 후보가 된다.

기존 V2 결과, 실패 사례 또는 final 결과를 보고 이 계약이나 sealed gold를 성공에 맞춰
수정하는 것은 금지한다.

## 2. 범위와 불변 조건

Phase 1의 변경 범위는 이 PRZ 디렉터리와 `specs/README.md`뿐이었다. Phase 2는 별도
Search V3 evaluation resource와 dependency 없는 validator/support script만 추가한다.
두 Phase 모두 Production 검색, DB schema와 migration, API, frontend, MCP, dependency와
runtime configuration을 변경하지 않는다. `v1.0.0` tag와 Release, 기존 PRZ의 상태와 판정도
변경하지 않는다.

Search V3는 다음 불변 조건을 보존해야 한다.

- 인증된 owner의 현재 `ACTIVE` document version만 검색·표시한다.
- 실패·미완료·과거 version과 권한 없는 source는 후보, 문맥, snippet에 들어갈 수 없다.
- 원문에 없는 경력, 행위, 숫자, 결과를 만들지 않는다.
- 검색은 근거 retrieval/localization이지 경력 진위, 채용 적합도 또는 사실 확정 API가 아니다.
- Current Search는 독립 비교가 끝날 때까지 삭제·변경·deprecated 처리하지 않는다.
- self-hosted open-source 사용성을 품질과 함께 평가한다.

## 3. Current Search Frozen Baseline

이 기준선은 문서가 아니라 현재 Production source를 `origin/main`의 위 commit에서 직접 읽은
결과다. 과거 PRZ-016 수치는 `HISTORICAL_RESULT`이며 이 기준선의 fresh 성능이 아니다.
`CURRENT_FRESH_BASELINE = NOT_RUN`이다.

| 항목 | Frozen baseline |
| --- | --- |
| Profile | 기본 `source-dedup-evidence-signals-v1`; rollback `legacy-dense-v1` |
| Embedding | Spring AI Ollama `bge-m3` 기본값은 model override 가능; Production schema는 1024차원 고정 |
| Ingestion | strict UTF-8 TXT 또는 text-layer PDF를 기본 800자/120자 overlap 고정 character chunk로 분할; PDF는 blank page를 건너뛰고 page마다 overlap을 다시 시작 |
| Candidate retrieval | PostgreSQL pgvector exact cosine `<=>`, score=`1-distance`, default Top20 |
| Final limit | Career Evidence 최대 5; legacy 최대 5; `/api/search` nearest 1 |
| Isolation | SQL에서 document/version/chunk owner, `active_version_id`, version `ACTIVE`를 모두 확인 |
| Identifier | ASCII 중심 strong identifier와 boundary guard; `Spring Boot` formatting canonicalization |
| Numeric | exact normalized number와 제한된 unit context; comparator/range 의미 계산 없음 |
| Eligibility | GENERAL dense floor 0.50; exact identifier(+required number) 또는 dense Top5 core-term의 bounded 예외 |
| Consolidation | source-location overlap과 query anchor 기반 representative 선택; DB gold가 아님 |
| Ranking | GENERAL에서 dense + bounded identifier/core/number boost + reranker로 순서를 정하되 응답 score는 선택된 query variant candidate의 raw `1-distance` |
| Fallback | GENERAL 또는 experience-request의 초기 결과가 없을 때 anchor를 보존하는 variant 최대 2개; fallback이 선택되면 score는 original query가 아니라 그 variant의 값일 수 있음 |
| Rescue | initially rejected GENERAL이고 모든 candidate가 0.50 미만일 때의 단일 2~4 code-point exact token score `[0.49, 0.50)` 좁은 rescue, exact numeric+unit rescue |
| Localization | 선택 chunk 우선; 부족할 때 같은 owner/document/current ACTIVE version만 확장 |
| Snippet | 생성형 요약이 아닌 원문 연속 최대 3문장; 실패 시 선택 chunk 원문 |
| Source | TXT `TEXT_CHUNK`, PDF 원래 1-based `PAGE`; persisted/exposed source char/line span·bbox 없음 |
| API input | JSON `query`, nonblank, 최대 500자 |
| `/api/search` | exact nearest 1건; searchable chunk가 없으면 기존 not-found error |
| Career Evidence API | USER 인증; browser의 `POST /api/career-evidence/search`는 최대 5개 raw list로 빈 state 구분을 소실; `POST /api/v2/career-evidence/search`는 `{state, results}` |
| V2 states | `EVIDENCE_FOUND`, `NO_RELEVANT_RESULTS`, `NO_EVIDENCE`, `NO_SEARCHABLE_DOCUMENTS` |
| MCP | read-only `search_career_evidence`가 인증 사용자로 V2 service를 재사용; evidence/provenance/IDs와 state를 옮기고 별도 ranking과 raw score/distance field는 없음. snippet fallback이면 evidence text가 선택 chunk 전체일 수 있음 |

현재 제한도 기준선 일부로 동결한다.

- answerability와 aspect별 support relation을 반환하지 않는다.
- bare `Kafka` 검색은 긍정, 부정, 검토, 다른 actor 문장을 함께 반환할 수 있고 완료·출시
  질의에서 질문형 문장도 `EVIDENCE_FOUND`로 반환될 수 있다. 이는 source test에 명시된
  현재 non-judgment 동작이다.
- typed numeric comparator/range, 일반 entity/date/actor/completion-state 판정이 없다.
- persisted/exposed source-grounded char/line span·bbox, Evidence Parent/Child/Group가 없다.
- 고정 chunk와 기술·action/problem/result 중심 heuristic은 직무 일반화를 보장하지 않는다.
- anchor consolidation과 exact-content dedup이 서로 다른 경험을 축약할 위험이 있다.
- Dense 이외의 Sparse/BM25/FTS/RRF/Cross Encoder/LLM/Parent-Child는 Production 경로가 아니다.
- OCR/image PDF, layout/table reconstruction, section hierarchy가 없다.

`docs/architecture.md`의 겹치는 “source span”은 현재 저장된 좌표가 아니라 content boundary
overlap 추론이다. V3의 명시적 source span과 동일시하지 않는다. PRZ-016의 current summary는
더 오래된 source snapshot이지만 relevant Search behavior의 material conflict는 발견되지 않았다.
그래도 이 문서에서는 위 commit의 source를 권위 기준으로 사용한다.

## 4. Evidence Architecture Contract

### 4.1 Source Block

파서가 원문에서 관찰한 구조 단위다. heading, paragraph, bullet, table row/cell group,
key-value block, page-local text block 등을 표현할 수 있다. 특정 이력서 양식이나
`프로젝트 → 기술 → 성과` vocabulary를 전제로 하지 않는다. 순서와 원문 좌표를 보존하며,
파생 요약문은 Source Block이 아니다.

### 4.2 Evidence Unit / Evidence Child

질문을 직접 뒷받침할 수 있는 최소 검색 단위다. 하나 이상의 연속 Source Span에 반드시
grounding되어야 하고, span 밖의 사실을 포함할 수 없다. DB `chunkId`는 실행 추적값일 뿐
Evidence Unit의 정체성이나 gold truth가 아니다.

### 4.3 Evidence Parent

하나 이상의 Evidence Unit에 공통 문맥을 제공하는 상위 구조다. heading, section 또는
page-local group일 수 있다. Parent는 child의 actor, 기간, 대상 같은 문맥을 보완할 수 있지만,
그 자체가 질문을 직접 입증하지 않으면 `DIRECT_SUPPORT` gold가 아니다.

Child span만으로 actor/date/target 등 required fact가 완성되지 않으면 child 단독 relation은
`INSUFFICIENT`다. annotator는 필요한 최소 same-Parent context span을 같은 multi-span
Evidence Unit의 `contextSpanIds`로 명시할 수 있다. 이때 모든 constituent span이 함께
retrieval/presentation되어야 `DIRECT_SUPPORT` hit이고, Parent Recall만으로 direct hit가 되지
않는다. 서로 다른 Parent의 context로 하나의 Unit을 완성하는 것은 금지한다.

### 4.4 Evidence Group

같은 사실 또는 같은 경험을 함께 뒷받침하는 관련 Evidence Unit의 평가 묶음이다. 각 Unit의
source와 relation은 독립적으로 남긴다. 서로 다른 경험·프로젝트 또는 서로 다른 Parent의
내용을 임의로 합쳐 하나의 직접 근거처럼 만들 수 없다. multi-evidence 질문은 여러 Group을
요구할 수 있지만 이를 합성 source span으로 바꾸지 않는다.

### 4.5 Experience Unit

명확한 구조 신호가 있을 때만 만들 수 있는 optional derived concept다. 없어도 Source Block과
Evidence Unit으로 검색 가능해야 하며, 회사·프로젝트·경력 같은 명칭이나 특정 직무에
의존하지 않는다. parser의 필수 기본 구조도, gold의 필수 단위도 아니다.

### 4.6 Source grounding invariant

각 Evidence Unit은 stable fixture/document reference, document version, source type과
page/line/char 좌표를 가진다. layout parser가 있는 format은 bbox를 추가할 수 있다.
좌표를 제공할 수 없는 파서 output은 direct-evidence gold로 봉인할 수 없다. Parent context,
정규화 text, runtime chunk와 파생 label은 모두 원문 span으로 역추적 가능해야 한다.

## 5. 직무 일반화 조건

Evidence 구조는 기술 스택 유무와 관계없이 다음 primary role을 모두 표현해야 한다.

| Role | 예시 Source Block / Evidence |
| --- | --- |
| backend | API 운영 기록, 장애 대응 paragraph, 성능 table row |
| frontend | 접근성 개선 bullet, interaction 설계와 사용자 결과 |
| mobile | release note, crash 개선 수치, store 운영 기록 |
| data | 데이터 품질 규칙, pipeline 결과, 분석 보고서 |
| AI/ML | 실험 조건·평가 결과·배포 범위; prototype과 production 구분 |
| infrastructure / DevOps | topology, runbook, incident·복구 기록 |
| security | 위협 분석, 통제 적용, audit finding과 remediation |
| design | research insight, design rationale, usability outcome |
| product | problem framing, decision log, product outcome |
| planning | 계획서, 일정·범위 결정, 실행 여부가 구분된 기록 |
| marketing | campaign brief, 채널 실행, attribution 결과 |
| sales | account action, pipeline/result, 본인과 팀 성과 구분 |
| operations | SOP, 처리량·품질 개선, 현장 운영 기록 |
| 기타 비개발 | 자격·교육·정책·고객 지원·행사 등 직무 고유 원문 구조 |

직무별 vocabulary나 기술 목록은 Evidence 성립 조건이 아니다. 직무별로 Source Block,
직접 근거, actor, 상태, 수치/기간을 표현할 수 없는 schema는 Gate 전에 수정하고 다시 봉인한다.

## 6. Query Contract

Answerability 표기의 `S/P/N`은 각각 `SUPPORTED`, `PARTIALLY_SUPPORTED`,
`NOT_SUPPORTED`다. 아래 metric은 해당 category의 필수 slice metric이며 전체 계층 metric도
동시에 계산한다.

| Category | 검색상의 어려움 | 가능 상태 | 주요 실패 위험 | 필요한 gold label | 필수 metric |
| --- | --- | --- | --- | --- | --- |
| literal / identifier | exact boundary와 alias/버전 | S/N | substring·동명이인·alias miss | entity type, canonical/value/version, span | exact/entity recall, entity fidelity |
| semantic paraphrase | 원문과 다른 표현 | S/N | lexical miss·과도한 의미 확장 | paraphrase family, direct span | group Recall, MRR, direct correctness |
| abstract competency | 여러 행동에서 역량을 입증 | S/P/N | 추상어를 topic mention으로 대체 | aspect, behavior rubric, groups | aspect coverage, nDCG, user-macro |
| numeric / quantity | 표기·단위 정규화 | S/N | 가까운 숫자·단위 오결합 | value, unit, semantic type | numeric recall/fidelity |
| numeric range / comparison | comparator와 range 관계 | S/P/N | literal 부재를 miss, 잘못된 부등식 | operator, bounds, unit, comparator | constraint recall, numeric fidelity |
| date / period / range | 불완전 날짜·기간 계산 | S/P/N | 서로 다른 기간 결합 | normalized date/range, precision | date fidelity, aspect coverage |
| no-answer | 관련 source가 없음 | N | 유사 topic을 답으로 반환 | corpus absence audit, aspects | precision/recall/F1, FPR/FNR |
| hard negative | 유사하지만 조건이 틀림 | N | near match를 direct로 승인 | confound type, insufficient/contradict span | FPR, direct correctness |
| negation | 부정 대상과 scope | S/P/N | negated claim을 긍정으로 반환 | requested polarity, scope, relation | negation fidelity, FPR |
| planned | 미래·계획 상태 | S/P/N | 계획을 완료로 오인 | requested state=PLANNED, span | completion-state fidelity |
| attempted / prototype | 시도·PoC와 성공 범위 | S/P/N | prototype을 production으로 확대 | state, outcome, scope | completion-state fidelity |
| completed / production | 실제 완료·운영 직접 근거 | S/P/N | 질문·계획·review 문장을 완료로 승인 | state, actor, target, date | direct correctness, state fidelity |
| other-actor evidence | 본인/팀/타 조직 구분 | S/P/N | 타 actor 성과를 사용자 경험으로 귀속 | target actor, role, attribution | actor fidelity, FPR |
| multi-evidence | 복수 span/group 필요 | S/P/N | 한 span만으로 전체 답 처리·임의 merge | required groups, relation per unit | group recall, coverage, merge violation |
| multi-aspect | AND/OR aspect 충족도 | S/P/N | 일부 충족을 전체 S로 표시 | required/optional aspects, per-aspect state | partial F1, aspect coverage |
| job requirement | 요구사항별 career evidence | S/P/N | fit/합격 판단 또는 requirement 혼합 | requirement/aspect ID, direct spans | per-item recall, user-macro |
| Korean | 조사·어미·동형어 | S/P/N | 형태 변화 miss | language, normalized form | language-slice recall/nDCG |
| English | inflection·abbreviation | S/P/N | generic technical term FP | language, entity/phrase | language-slice recall/nDCG |
| Korean-English mixed | 조사 결합·code switching | S/P/N | identifier boundary·token 분리 | language mix, canonical entity | mixed-slice recall, entity fidelity |
| typo | edit/noise가 있는 질의 | S/P/N | 잘못된 자동 교정·entity 변형 | original, intended form, error type | robust recall, FPR |
| spacing / hyphen / formatting variation | Unicode·공백·기호 alias | S/N | substring collision·format miss | original/canonical forms | variation recall, entity fidelity |

각 query는 하나 이상의 category를 가질 수 있고, category별 slice를 모두 집계한다. job
requirement query도 기존 Job Posting 기능처럼 evidence를 찾을 뿐 fit judgment를 gold로 만들지 않는다.

## 7. Answerability와 Support Relation

### 7.1 Answerability

- `SUPPORTED`: 모든 required aspect가 제약을 만족하는 `DIRECT_SUPPORT`를 하나 이상 가진다.
- `PARTIALLY_SUPPORTED`: 하나 이상의 required aspect는 직접 지원되지만 나머지 required
  aspect는 지원되지 않거나 불충분하거나 모순된다.
- `NOT_SUPPORTED`: required aspect 어느 것도 직접 지원되지 않거나 질문 전체를 지지할
  충분한 원문 근거가 없다.

각 aspect의 상태와 relation을 별도로 저장한다. 전체 enum만으로 모순이나 누락을 숨기지
않는다. 예를 들어 “Spring과 Kafka를 사용했는가”에서 Spring만 직접 지원되면 전체는
`PARTIALLY_SUPPORTED`다.

### 7.2 Support relation

- `DIRECT_SUPPORT`: 질문의 해당 aspect와 필요한 actor/entity/numeric/date/completion
  제약을 입증하는 사실이 실제 source span에 있다.
- `RELATED`: 주제는 관련 있지만 해당 aspect를 직접 입증하지 못한다.
- `CONTRADICTS`: source가 질문의 주장과 명시적으로 반대된다.
- `INSUFFICIENT`: 관련 정보는 있으나 판단에 필요한 actor, 값, 상태, 기간 등이 부족하다.

다른 Parent의 내용을 결합해야만 성립하는 주장은 하나의 `DIRECT_SUPPORT`가 아니다. 정당한
multi-evidence 질문은 각 Unit의 direct relation과 필요한 Group 집합을 명시한다.
같은 Parent의 child+context가 필요한 경우에는 4.3의 multi-span Unit 규칙을 적용하며,
required constituent span이 하나라도 반환되지 않으면 direct hit가 아니다.

## 8. Gold Label Contract

### 8.1 필수 core

- benchmark/schema version, query ID와 category/language
- `userBundleId`, document fixture/reference, document version
- Evidence Group ID, Evidence Unit ID, Parent ID 또는 명시적 `NONE`
- required/optional aspect ID와 overall/per-aspect answerability
- Unit별 support relation
- source type, stable page/line/char start/end 중 format에 해당하는 좌표
- 원문 hash 또는 fixture hash와 acceptable span text hash
- multi-span Unit이면 primary span과 same-Parent `contextSpanIds`

### 8.2 조건부 field

- bbox와 coordinate system: layout-aware parser를 평가할 때
- required entity: type, canonical value, surface form, version/alias policy
- required numeric: operator, normalized value 또는 lower/upper bound, unit, semantic type
- required date: operator, normalized value/range, precision/timezone policy
- actor와 attribution, completion state, polarity/scope
- acceptable 대체 Evidence Unit/Group와 exclusion/hard-negative reason

`expectedChunkId`는 runtime mapping 또는 regression trace로만 둘 수 있고 단독 gold가 될 수
없다. gold 수정 이력은 append-only manifest로 남기며 sealed final을 개봉한 뒤 수정하면 해당
run은 historical로 보존하고 새로운 final set을 봉인한다.

### 8.3 Numeric / entity example

질문 `1000명 이상 사용한 서비스 경험`과 source `1,300명이 사용`은 literal `1000`이 없어도
`operator >=`, `value 1000`, `unit USER_COUNT` 제약을 만족할 수 있다. 반대로 actor나 서비스
귀속이 다르면 numeric match만으로 `DIRECT_SUPPORT`가 아니다. 이번 Phase는 이 schema만
고정하며 QueryPlanner나 Typed Constraint를 구현하지 않는다.

## 9. Evaluation Split Contract

split assignment의 최소 단위는 query가 아니라 `userBundleId`다. 한 bundle은 동일 사용자의
모든 문서, 모든 version, 모든 query와 source fact를 포함한다.

| Split | 공개 범위와 용도 | 허용되는 조정 |
| --- | --- | --- |
| `DEV` | 개발자가 반복 관찰 가능; parser/runner/schema/bug 확인 | 구조·구현 디버깅 |
| `CALIBRATION` | 제한적으로 반복 관찰 가능 | threshold, K, candidate size, model, fusion, reranker, parser/chunk/operational profile |
| `SEALED_FINAL_TEST` | 독립 evaluator만 보관; 최종 일반화 판정 때 한 번 개봉 | 없음 |

다음 leakage 방지 규칙은 강제한다.

- 같은 사용자와 그 사용자의 모든 resume/version은 split을 넘지 않는다.
- 같은 template family 또는 generator name/revision/seed의 복제는 하나의 leakage group으로
  묶고 materialized benchmark에서는 split을 넘으면 validator가 실패한다.
- 같은 source fact의 paraphrase query, question/evidence group은 split을 넘지 않는다.
- 같은 프로젝트·회사·고유 식별자를 공유하는 사실은 leakage key로 기록하고 과도한 교차를 막는다.
- 같은 synthetic generator seed, source template, fact template의 변형을 독립 사용자로 세지 않는다.
- normalized duplicate, near duplicate, source span/fact hash, template/generator lineage를 seal 전에 검사한다.

`SEALED_FINAL_TEST`의 corpus, query, gold, split manifest, 평가 code/version과 hash는 주요 V3
알고리즘 구현 전에 정의·봉인한다. final 결과를 확인한 뒤 threshold, K, model, parser,
chunking, fusion, reranker, query policy 또는 gate를 바꾸면 그 test는 더 이상 final이 아니다.
기존 결과는 `HISTORICAL_RESULT`로 보존하고 새로운 independent final을 봉인한다.

Final runner는 candidate, filter/rejection, rank, localization stage trace를 hash와 함께 보존해
원인 없는 추측을 막는다. 단, 해당 trace는 final 개봉 전 개발자에게 노출하지 않는다.

## 10. Fresh Generalization Benchmark Contract

- Release-grade 규모: `PROPOSED_TARGET >= 50 user bundles`
- 확장 목표: `PROPOSED_TARGET >= 100 user bundles`
- 실제 corpus 생성: 이 Phase `NOT_RUN`
- 기존 V2 dataset: historical baseline, regression, failure-analysis reference 전용

50-bundle primary-role 분포 초안은 다음과 같으며 데이터 확보 전 확정값이 아니다.

| Primary role | `PROPOSED_TARGET` bundles |
| --- | ---: |
| backend | 5 |
| frontend | 3 |
| mobile | 3 |
| data | 4 |
| AI/ML | 4 |
| infrastructure / DevOps | 4 |
| security | 3 |
| design | 4 |
| product / planning | 5 (각 role 포함) |
| marketing | 3 |
| sales | 3 |
| operations | 4 |
| 기타 비개발 | 5 |

보고 group은 `backend`, `frontend/mobile`, `data/AI`, `infra/security`, `design/product`,
`business/non-dev`를 출발점으로 하되 실제 표본 전 `OPEN_DECISION`이다.

문서 유형에는 짧은 이력서, 장문 포트폴리오, 경력기술서, 자기소개서, 프로젝트 설명,
표 중심 문서, 자격증/수료/교육 문서, 다단 PDF, OCR/image PDF, Korean, English,
Korean-English mixed를 포함한다. V2 비교 가능성은 별도 표기한다.

| Benchmark slice | Current V2 분류 | V3 contract |
| --- | --- | --- |
| 짧은 이력서 | `CURRENT_V2_SUPPORTED` — UTF-8 TXT/text-layer PDF | `V3_TARGET` |
| 장문 포트폴리오·경력기술서 | `CURRENT_V2_SUPPORTED` — fixed chunk 제한 존재 | `V3_TARGET` |
| 자기소개서·프로젝트 설명 | `CURRENT_V2_SUPPORTED` — 구조 hierarchy 없음 | `V3_TARGET` |
| 자격증/수료/교육 문서 | `CURRENT_V2_SUPPORTED` — text-layer 기준 | `V3_TARGET` |
| 표 중심 문서 | `CURRENT_V2_LIMITED` — table reconstruction 없음 | `V3_TARGET` |
| 다단 PDF | `CURRENT_V2_LIMITED` — reading order 보장 없음 | `V3_TARGET` |
| OCR/image PDF | `CURRENT_V2_UNSUPPORTED` | `V3_TARGET` |
| Korean | `CURRENT_V2_SUPPORTED` — fresh role generalization은 `NOT_RUN` | `V3_TARGET` |
| English / Korean-English mixed | `CURRENT_V2_SUPPORTED` — fresh language quality는 `NOT_RUN` | `V3_TARGET` |

- `CURRENT_V2_SUPPORTED`: 현재 ingestion과 source contract로 공정 비교 가능
- `CURRENT_V2_LIMITED`: ingest 가능하지만 table/layout/reading order/span 보존이 제한됨
- `CURRENT_V2_UNSUPPORTED`: OCR/image-only, encrypted 등 현재 입력 경로가 처리하지 못함
- `V3_TARGET`: V3 benchmark capability 목표에 포함

`CURRENT_V2_UNSUPPORTED` fixture는 V2 search-quality 분모에서 `NOT_APPLICABLE`로 분리하고
ingestion/capability gap으로 보고한다. parser 실패를 retrieval/ranking 실패로 계산하지 않는다.
공정한 Search V2 대 V3 품질 비교는 두 시스템이 같은 source fact를 검색 가능한 comparable
cohort에서 수행한다.

## 11. Benchmark 데이터 출처와 개인정보

| 출처 | 용도와 장점 | 한계·필수 조건 |
| --- | --- | --- |
| Synthetic | 제약·hard negative·직무 분포를 통제하고 재배포 가능 | template/seed leakage와 부자연스러움; real generalization 대체 불가 |
| Public / Redistributable | 실제 문서 다양성에 가까움 | license, attribution, derivative/redistribution 조건을 fixture별 검증 |
| Consented Real Data | 실제 표현·layout·노이즈를 반영 | 명시적 동의·익명화·접근 통제·삭제 정책이 있는 별도 운영 계약 필요 |

실제 개인 원문은 Git repository에 commit하지 않는다. Consented Real Data를 쓰려면 익명화,
동의 범위, 보관 위치, 접근 권한, retention과 삭제 절차를 먼저 별도 계약으로 승인한다.
이번 Phase에는 개인정보 문서가 없다.

## 12. Metrics Contract

### A. Parsing

source order preservation, section hierarchy preservation, table reconstruction, page preservation,
source span availability를 format별로 측정한다.

### B. Evidence construction

direct evidence preservation rate, parent context preservation, cross-parent contamination,
evidence fragmentation을 측정한다.

### C. Candidate retrieval

Recall@20/50/100, Parent Recall, Evidence Group Recall, exact/entity constraint recall, numeric
constraint recall을 측정한다. 채널이 추가되면 channel별 recall과 union 순증을 별도로 기록한다.

### D. Final ranking

Top1 accuracy, Recall@5, Precision@5, MRR, nDCG@5를 측정한다. relevance는 source-grounded
relation과 Evidence Group을 기준으로 하며 runtime chunk ID를 정답으로 쓰지 않는다.

### E. Answerability / no-answer

전체와 `SUPPORTED/PARTIALLY_SUPPORTED/NOT_SUPPORTED` class별 precision, recall, F1,
confusion matrix, no-answer FPR/FNR을 측정한다. partial exact match와 per-aspect coverage/F1을
별도로 보고한다.

### F. Evidence quality

direct support correctness, duplicate evidence ratio, cross-parent direct merge violation,
evidence diversity, aspect coverage, numeric/entity/completion-state/actor fidelity를 측정한다.

### G. Localization

PDF page accuracy, TXT source accuracy, source-span correctness를 측정한다. layout parser가
채택되면 bbox accuracy를 추가한다.

### H. Safety

owner leakage, inactive-version leakage, wrong-document-version leakage, unauthorized-source
exposure는 건수와 query/user를 기록한다.

### I. Operation

indexing latency, query p50/p95, RAM, VRAM, index size, CPU/GPU requirement, Docker/service count,
model download size와 startup complexity를 동일 hardware/profile에서 측정한다.

### Aggregation

모든 핵심 quality metric은 `query-micro`와 `user-macro`를 함께 보고한다. adoption의 primary는
각 user bundle에 같은 가중치를 주는 `user-macro`다. role group별 metric과 worst-group
regression도 보고하며, 전체 평균 개선이 특정 주요 직군의 명확한 악화를 숨길 수 없다.

Retrieval recall의 hit는 acceptable `DIRECT_SUPPORT` Evidence Unit/Group이 candidate에 있을 때만
성립한다. Parent Recall은 정답 Unit을 포함하는 expected Parent 도달 여부이고, Group Recall은
한 Group의 acceptable Unit 중 하나 이상 도달했는지다. `RELATED`, `CONTRADICTS`,
`INSUFFICIENT`는 direct hit로 세지 않는다. no-answer query는 retrieval recall 분모가 아니라
Answerability/FPR 분모에서 평가한다. 같은 metric 정의와 denominator를 Current/V3 모두에 쓴다.

## 13. Adoption Gate

### 13.1 Hard Safety Gate — `FROZEN_GATE`

다음은 모두 정확히 0건이어야 한다. 하나라도 실패하면 V3를 기본 검색으로 채택할 수 없다.

- owner leakage
- inactive/non-current version leakage
- wrong document version source
- unauthorized source exposure
- cross-parent direct evidence merge violation

### 13.2 Search Quality Gate — 현재 `PROPOSED_TARGET`

Current Search와 finalist V3를 같은 fresh sealed benchmark에서 비교한다. 숫자 threshold와
허용 regression 폭은 corpus를 만든 뒤 final 개봉 전에 `FROZEN_GATE`로 전환한다.

- user-macro를 primary, query-micro를 secondary로 판단한다.
- 직무별 worst-group regression, answerability/no-answer, localization, direct support
  correctness와 aspect/numeric/entity/actor/state fidelity를 함께 본다.
- 평균이 조금 오르더라도 주요 slice가 frozen tolerance를 넘게 악화되면 자동 채택하지 않는다.
- 공정한 비교에서 명확한 순증을 증명하지 못하거나 동률이면 Current Search를 유지한다.

### 13.3 Operational Gate — 현재 `PROPOSED_TARGET`

p95 latency, indexing latency, RAM/VRAM, index/model 크기, CPU/GPU 필수 여부, 추가 service와
Docker/startup 복잡도, self-hosted 설치성을 측정한다. 품질 순증에 비해 운영 비용이 frozen
budget을 넘으면 default profile로 채택하지 않는다. 구체 숫자는 runtime 측정 후 final 개봉
전에 동결한다.

`PROPOSED_TARGET`은 PASS 기준이 아니다. target을 `FROZEN_GATE`로 바꿀 때 근거, 날짜,
benchmark manifest와 변경자를 기록한다.

## 14. Ablation-first와 Complexity Budget

후속 개발은 한 번에 전체 Hybrid RAG를 구현하지 않는다. 다음은 sequencing 후보이며
PRZ 번호 예약이나 시작 승인이 아니다.

- PRZ-026 후보: Fixed Chunk + Dense → Structural Child + Dense → Parent Context 추가 →
  Parent-Child candidate. 목적은 구조 효과만 분리하는 것이다.
- PRZ-027 후보: V3 Dense baseline → Cross Encoder 후보 → QueryPlanner 후보.
- PRZ-028 후보: Dense+reranker → Typed Exact 후보 → Sparse 후보 → 둘의 조합.
- fusion이 필요할 때만 simple union, RRF, weighted rank, dense-priority union,
  reranker-over-union을 비교한다.

각 단계는 `DEV/CALIBRATION`에서 이전 단계 대비 quality gain, worst-group regression과
operational cost를 함께 보고한다. 순증이 없거나 비용이 과도한 구성은 제거한다. 과거
FAIL/NO_GO 기술을 다시 시험하려면 corpus, structure, model, gate가 왜 다른지 사전에 쓰고
과거 판정을 성공 근거로 바꾸지 않는다.

## 15. Algorithm neutrality / OPEN_DECISION

다음은 모두 `CANDIDATE / OPEN_DECISION`이며 Search V3의 확정 구성요소가 아니다.

- Parent-Child, Dense와 BGE-M3 유지 여부
- Sparse, BM25/FTS, Exact/Typed Constraint, sparse storage, pgvector `sparsevec`
- RRF, weighted fusion, simple/priority union, MMR
- Query Rewrite, Multi-query, QueryPlanner
- Cross Encoder, LLM reranker, Answer LLM, Claim Verifier
- Docling, OCR, HNSW와 layout parser
- Parent/Child 크기, overlap, Dense/Sparse K, candidate size와 threshold
- Default/Quality profile 분리와 benchmark 실제 공급 방식

Default/Quality 두 profile도 실제로 독립적인 gain/cost 근거가 있을 때만 제안할 수 있다.

## 16. 다음 Phase 진입 조건

Phase 1의 문서 정합성·scope 검증과 audit가 끝나면 benchmark materialization과 독립 봉인
절차를 시작할 수 있다. Phase 2의 seed freeze가 검증되면 Structural Parsing 후보는
`DEV/CALIBRATION`에서만 구현·비교할 수 있다. 이는 Production 변경, release-grade final
실행 또는 adoption 승인이 아니다.

평가 schema, gold 의미, split/lineage와 sealed input은 구현 전에 봉인한다. 품질·운영의
숫자 Gate는 실제 `DEV/CALIBRATION` 측정 근거가 생긴 뒤 finalist 선택 전에 정하고,
반드시 `SEALED_FINAL_TEST`를 실행하거나 결과를 보기 전에 `FROZEN_GATE`로 봉인한다.
Final을 본 뒤 evaluator 의미나 Gate를 바꾸는 것은 허용하지 않는다.

## 17. Phase 2 — materialized Fresh Seed Contract

### 17.1 저장·상태

- versioned root: `src/test/resources/search-v3-evaluation/`
- dataset version: `search-v3-fresh-seed-1.0.1`
- schema version: `1.0.0`
- state: `FRESH_BENCHMARK_SEED_FROZEN`
- Current Search / V3 검색 실행: `NOT_RUN`
- `CURRENT_FRESH_BASELINE = NOT_RUN`

기존 `search-evaluation/v2*`는 포함·복사·재라벨하지 않았다. tracked seed는 Apache-2.0으로
배포하는 완전 synthetic TXT만 포함한다. 공개 자료는 fixture별 license 검토 뒤에만 넣고,
동의 기반 실제 데이터는 익명화·동의·접근·retention·삭제 계약을 승인한 뒤 Git에서 제외된
`local/search-v3-evaluation/`에만 둔다.

### 17.2 좌표와 ID

Gold ID는 `SV3-U07-P03-E02` 같은 annotation ID이며 runtime DB ID가 아니다. source는
UTF-8 no-BOM/LF로 정규화한다. `charStart`는 Unicode code-point 기준 0-based inclusive,
`charEnd`는 0-based exclusive이고 line은 1-based inclusive다. exact span text와 UTF-8
SHA-256이 실제 document version과 일치해야 한다. Parent, Unit의 모든 constituent span과
document/version/user가 일치하지 않으면 실패한다.

### 17.3 Answerability expression과 result adapter

Query는 `ALL`, `ANY`, `AT_LEAST`와 `minShouldMatch`로 required aspect 조합을 표현한다.
`SUPPORTED`는 expression 전체, `PARTIALLY_SUPPORTED`는 엄격한 일부, `NOT_SUPPORTED`는
required aspect의 direct support 0개를 뜻한다. relation은 Phase 1의 네 enum을 그대로 쓴다.

future prediction adapter는 Current Search와 모든 V3 후보가 stable document/version/source
locator를 같은 형태로 제출하게 한다. direct hit는 같은 Parent 안에서 Unit의 모든 required
span을 반환한 경우에만 성립한다. mapping되지 않은 returned evidence는 Precision에서
non-direct로 계산하며 누락하지 않는다. runtime chunk/parent ID는 diagnostic일 뿐 gold가 아니다.

### 17.4 materialized leakage와 safety

다음 key가 둘 이상의 split에 나타나면 blocking failure다.

- `userBundleId`, logical document와 version lineage
- `sourceFactId`와 normalized source-fact signature
- normalized query와 `questionGroupId`
- document/template family
- generator name/revision/seed lineage

Seed에는 서로 다른 owner bundle, inactive/wrong version과 unauthorized-source exclusion을
명시한다. 후속 safety runner는 여러 owner와 inactive version을 같은 run에 실제 적재하고
owner/inactive/wrong-version/unauthorized exposure를 각각 0건으로 확인해야 한다. safety decoy가
없는 run으로 Hard Safety Gate를 통과시킬 수 없다.

### 17.5 freeze와 Gate 순서

split manifest는 corpus metadata, question, gold, lineage와 두 schema의 SHA-256을 포함한다.
overall manifest는 이 자산과 split manifest, tracked source fixture를 모두 봉인한다.
`sealed-final`은 `opened=false`, `searchExecuted=false`이고 result/prediction file을 허용하지 않는다.
여기서 `opened`는 source/gold integrity validation이 아니라 검색 결과가 개발자에게 노출됐는지를
뜻한다.

schema, gold/evaluator 의미, sealed input 또는 query policy를 바꾸면 같은 version을 수정하지
않고 새 dataset version과 새 seal을 만든다. 이미 개봉한 결과는 `HISTORICAL_RESULT`로만
보존한다. 미래 `FROZEN_GATE` record는 primary endpoint, query-micro/user-macro denominator,
worst-group non-regression tolerance, no-answer/localization/direct-support 기준, uncertainty와
failed-query policy, hardware와 operational budget을 명시해야 한다. 현재 숫자 Gate는 여전히
`PROPOSED_TARGET`이며 PASS가 아니다.

### 17.6 seed와 release-grade 경계

materialized seed는 user bundle 7개로 schema/integrity/generalization coverage를 확인하는
자산이다. Release-grade `PROPOSED_TARGET >= 50 user bundles`를 대체하지 않고 Search V3의
성능 또는 adoption 근거가 아니다. OCR/image PDF와 DOCX는 schema의 `V3_TARGET` 또는
`FUTURE_OPTIONAL` capability로 표현할 수 있지만 이번 comparable seed와 Production ingest에는
포함하지 않는다.

Current `/api/search` 단일 결과, Career Evidence 최대 5개, owner/current `ACTIVE` filtering,
V2 state와 MCP reuse boundary는 frozen baseline의 normative invariants다. 별도 Product/API
workflow 없이 Structural Parsing 실험이 이 계약을 바꿀 수 없다.
