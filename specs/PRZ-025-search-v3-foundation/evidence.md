# PRZ-025 Search V3 Foundation Evidence

- 상태: `IN_PROGRESS / FRESH_BENCHMARK_SEED_FROZEN`
- 기록일: 2026-08-30 (Asia/Seoul)
- 기준 source: `origin/main@2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- seed materialization/integrity validation: `COMPLETED`
- 검색 benchmark/model/generalization 실행: `NOT_RUN`
- Search V3/Production 변경: `NOT_RUN`

이 파일은 이번 Phase에서 직접 확인하거나 실행한 사실만 기록한다. 계약상 목표나 미래 수치는
`spec.md`의 `PROPOSED_TARGET`이며 여기서 PASS로 취급하지 않는다.

## 1. 시작 Git 상태

| 확인 항목 | 실제 결과 |
| --- | --- |
| fetch | `git fetch --prune origin` 실행 완료 |
| `origin/main` | `2c8fd5c0d2f62b154642d703a0970389f8abed8e` |
| local `main` | 같은 SHA; `main...origin/main = 0 ahead / 0 behind` |
| merge base | 같은 SHA |
| annotated tag object | `v1.0.0^{tag} = 964d9d403b26a237e3b1c40e44a3c5a3bae74b0e` |
| peeled tag commit | `v1.0.0^{} = 76a87482a70d89b3bb5c7dabed69dff4764e04bb` |
| GitHub Release | 존재; non-draft, non-prerelease, 2026-08-29 게시 |
| Release target | `76a87482a70d89b3bb5c7dabed69dff4764e04bb` |
| 기존 PRZ-025 | local/`origin/main` tree와 repository search에서 없음 |
| 동목적 branch/spec | local/remote ref와 source search에서 없음 |
| open PR / Issue | GitHub 조회 결과 각각 0 |

GitHub Release 이름은 `PRIZM v1.0.0 — 첫 번째 소스 릴리스`, URL은
`https://github.com/jaemin-devlog/PRIZM/releases/tag/v1.0.0`이다. tag와 Release를 수정하지 않았다.

### 기존 checkout과 격리

원래 checkout은 `codex/PRZ-016-prizm-dataset` branch였고 시작부터 다음 사용자 작업이 있었다.

- modified: `docs/evaluation/search-evaluation.md`, `docs/project-status.md`,
  `specs/PRZ-016-search-performance-v2/README.md`, `spec.md`, `specs/README.md`,
  Search evaluation test 2개
- untracked: dataset generator, PRZ-016 `p17-prizm-dedicated-dataset/`, evaluation test 1개,
  `src/test/resources/search-evaluation/prizm-v1/`

이를 수정·stage·reset하지 않았다. `origin/main`에서 별도 worktree를 만들고
`PRZ-025-search-v3-foundation` branch를 생성했으며 branch worktree의 시작 상태는 clean이었다.

## 2. 직접 확인한 문서와 source

다음 기준 문서를 읽었다.

- `AGENTS.md`, `README.md`, `docs/ai-agent-workflow.md`
- `docs/architecture.md`, `docs/project-status.md`, `docs/evaluation/search-evaluation.md`
- `specs/README.md`
- PRZ-001, PRZ-008, PRZ-012, PRZ-017, PRZ-022, PRZ-024의 tracked 자료
- PRZ-016 current summary/architecture, root spec/plan/tasks/evidence/R&D history와 phase 자료

PRZ-016의 tracked 218개 파일을 inventory하고 JSON 118개, JSONL 5개/860행을 parse했다.
invalid JSON/JSONL은 0이었고 PDF 8개와 PNG 12개의 file signature도 유효했다. 이 검사는
역사 artifact 형식 확인이며 benchmark 재실행이나 성능 검증이 아니다.

다음 Production 구현을 직접 확인했다.

- `SearchService`, `VectorSearchRepository`, `CompositeSearchProfile`
- `EvidenceQualityReranker`, `NaturalLanguageQueryFallback`, `NumericAnchorRescueProfile`,
  `ShortGeneralExactTokenRescueProfile`
- `EvidenceExpansionService/Repository`, `SearchSnippetGenerator`
- `TextChunker`, `DocumentTextExtractor`, `DocumentIndexingProcessor`, `IndexingCompletionService`
- Search properties/profile, embedding validation, current API controllers/DTO
- MCP `CareerEvidenceMcpTool`과 관련 source tests/config/migration

## 3. Current Search 정적 확인

- 기본 profile은 `source-dedup-evidence-signals-v1`, embedding 기본 모델은 Ollama `bge-m3`,
  설정과 DB vector dimension은 1024다.
- default candidate는 owner/current ACTIVE-scoped exact pgvector cosine Top20이고 final은 최대 5다.
- query와 chunk embedding 모두 dimension/finite/non-zero norm 검증을 거친다.
- identifier/numeric 조회, localization expansion도 document/version/chunk owner와 현재 ACTIVE
  version을 다시 제한한다.
- ranking은 bounded heuristic이며 fallback 최대 2, short exact-token rescue와 exact numeric rescue가 있다.
- TXT/PDF는 기본 800/120 fixed character chunk다. PDF는 PDFBox text-layer page extraction이며 OCR,
  layout/table/section reconstruction과 persisted/exposed source char/line span·bbox는 없다.
- snippet은 원문 연속 최대 3문장이고, 주변 evidence는 같은 current ACTIVE document version만 사용한다.
- MCP는 별도 알고리즘 없이 authenticated user의 `searchCareerEvidenceV2` 결과를 재사용한다.
- 현재 source test는 bare identifier에서 negation/review/other actor를 함께 반환하고 질문형 완료
  문장도 `EVIDENCE_FOUND`일 수 있음을 명시한다. Current Search는 answerability/actor/truth
  verifier가 아니다.

`CURRENT_FRESH_BASELINE = NOT_RUN`. 위 내용은 source 정적 기준선이며 현재 source에서 fresh
성능·latency·generalization을 실행했다는 뜻이 아니다.

## 4. 역사 판정

기존 판정을 소급 변경하지 않았다.

- PRZ-001의 TUNING/TEST와 Direct MRR은 predecessor evaluation contract이며 V3 final이 아니다.
- PRZ-008의 P12 exact-token 시도는 당시 evaluation-only/hold였고 P17/P18 검증 뒤 좁은 rescue가
  Production 채택됐다. P13 FTS/RRF, P14 sparse, P15 reranker는 evaluation-only/비권고로 남는다.
- PRZ-012는 result와 표시 evidence source가 다를 수 있음을 보여 준다. 다만 그 `spec.md`와
  `plan.md`, `tasks.md` header는 `IMPLEMENTED_UNVERIFIED`, `evidence.md`와 Registry는
  `VERIFIED`인 기존 internal documentation mismatch가 있다. PRZ-025 blocker가 아니며
  이번 Phase에서 수정하지 않았다.
- PRZ-016 P5 `FAIL`, P6 `NO_GO`, GPT Judge `NO_GO`, P7-B `FAIL`, 인증된 실제 PDF page
  navigation browser verification인 P15 `NOT_VERIFIED`, P16 `NEEDS_ADJUSTMENT`를 그대로 보존한다.
- P6 lexical+dense+RRF는 candidate recall gain 0과 regression으로 `NO_GO`였다.
- P7-B는 historical diagnostic이며 Top1 33.33%, Recall@5 58.33%, MRR 0.4491,
  Negative FPR 41.67%로 Gate `FAIL`이었다.
- P16 literal candidate는 두 frozen corpus에서 literal-only recovery 0으로 `NEEDS_ADJUSTMENT`다.
- PRZ-022는 historical artifact consistency와 safety evidence를 검증했지만 current-main retrieval은
  `NOT_RUN`; 현재 정확도 증명이 아니다.

기존 synthetic/software 중심 corpus는 V3의 role-diverse `SEALED_FINAL_TEST`로 재사용하지 않는다.

## 5. 이번 Phase validation

| 명령/검사 | 실제 결과 |
| --- | --- |
| PRZ-025 문서/Registry link와 status 정합성 | `PASS` — 필수 파일·query category·contract term 누락 0, Registry 1행 `IN_PROGRESS` |
| historical/fresh, `PROPOSED_TARGET`/`FROZEN_GATE`, `NOT_RUN` 표현 audit | `PASS` — blocking/nonblocking finding 0 |
| `git diff --check` + 새 Markdown whitespace/fence scan | `PASS` — finding 0 |
| `node scripts/verify-oss-readiness.mjs` | `PASS` — Markdown 180/links 762, verifier tests 16/16, external links 97/97 |
| diff allowlist와 Production/migration/dependency/frontend/MCP 0 확인 | `PASS` — allowlist violation 0, 금지 path 변경 0 |
| backend unit/integration | `NOT_RUN` — docs-only scope |
| frontend test/build | `NOT_RUN` — docs-only scope |
| Docker/runtime benchmark | `NOT_RUN` — 금지 범위 |

OSS verifier는 backend/frontend SBOM 재생성과 verifier test를 실행했고 생성 결과 diff는 0이었다.
application unit/integration, frontend test/build 또는 runtime benchmark를 실행한 것으로 확대하지 않는다.

독립 audit는 처음에 같은 Parent context와 negation/planned/other-actor answerability의 두
모호성을 찾았다. same-Parent `contextSpanIds`, child 단독 `INSUFFICIENT`, constituent span
direct-hit 규칙과 S/P/N query 상태로 수정한 뒤 재감사에서 blocking/nonblocking finding 0을
확인했다. Current source baseline audit와 historical verdict audit도 각각 finding 0이었다.

## 6. 미실행 항목

- fresh 50-user corpus 생성: `NOT_RUN`
- Current Search fresh baseline: `NOT_RUN`
- Search V3 구현·model/fusion/parser 비교: `NOT_RUN`
- quality/latency/memory 비교: `NOT_RUN`
- Search V3 generalization/adoption 판정: `NOT_RUN`
- PR 생성·main merge·push: `NOT_RUN`

## 7. 다음 Phase 판정

`SAFE_TO_START_NEXT_PHASE`

Evidence/Query/Answerability/split/gold/metric/adoption 계약과 Current Search frozen baseline이
명확하고 독립 audit의 unresolved contradiction이 0이며 Production 변경이 0이다. 이 판정은
benchmark data governance와 materialization, source-grounded gold adjudication, bundle split,
`SEALED_FINAL_TEST` 봉인을 시작해도 된다는 뜻만 가진다. PRZ-026, Search V3 algorithm 구현,
Production 변경, PR 또는 main merge를 승인하지 않는다.

## 8. Phase 2 시작 상태

| 확인 항목 | 실제 결과 |
| --- | --- |
| branch | `PRZ-025-search-v3-foundation` |
| 시작 HEAD | `14073dd1664fddb009a5bea8823a228e582abf51` |
| `origin/main` | `2c8fd5c0d2f62b154642d703a0970389f8abed8e` |
| 관계 | branch가 `origin/main`보다 1 commit ahead, 0 behind |
| 시작 working tree | clean |
| Phase 1 diff | PRZ-025 문서 4개 추가와 `specs/README.md` Registry 1행 수정만 존재 |
| Phase 1 commit | `docs(search): define PRZ-025 Search V3 foundation contracts` |

원래 `codex/PRZ-016-prizm-dataset` checkout의 기존 local 변경은 계속 별도 worktree에 있고
이번 branch에서 stage, edit, reset, clean, stash 또는 copy하지 않았다. Phase 2 시작 시
PRZ-025 네 문서와 `docs/evaluation/search-evaluation.md`, PRZ-001 전체 문서, PRZ-016 root
contract/evidence와 P5/P6/P16 판정을 다시 읽었다.

## 9. SEALED FINAL 전 계약 명료화

검색을 실행하거나 결과를 보기 전에 다음 ambiguity를 확인했고, 최초 materialization 전에
schema/validator에 반영했다.

| 기존 계약 | 발견한 문제 | seal 전 명료화 |
| --- | --- | --- |
| template family는 "가능한 한" split을 넘기지 않음 | blocking 여부가 결정론적이지 않음 | template family와 generator name/revision/seed가 split을 넘으면 FAIL |
| source char/line/hash field 후보 | Unicode·newline·boundary 의미가 불명확 | UTF-8 no-BOM/LF, code-point 0-based `[start,end)`, line 1-based, exact text SHA-256 |
| required/optional aspect | AND/OR/부분 충족 표현이 불완전 | `ALL/ANY/AT_LEAST + minShouldMatch`와 aspect별 S/P/N |
| evaluator/hash 봉인 뒤 runner 조정 가능 | evaluator 의미 변경과 runner bugfix 경계가 모호 | schema/gold/mapping 의미 변경은 새 version/seal; DEV harness의 의미 불변 bugfix만 허용 |
| Hard Safety 0건 | decoy 없는 run도 형식상 0건 가능 | multi-owner, inactive/wrong-version, unauthorized exclusion 없는 run은 Gate 판정 불가 |
| 숫자 Gate와 구현 순서 | 측정 전 숫자 Gate를 정하면 임의값이 되고 calibration보다 먼저 구현이 필요해 순환 | input/gold/schema는 구현 전 seal; 숫자 Gate는 DEV/CAL 측정 뒤 finalist 전에, Final 실행 전 seal |
| output과 gold mapping | Current/V3 공통 result adapter가 없음 | stable source locator prediction schema와 all-constituent-span direct-hit mapping 추가 |
| Current Search score/snippet/rescue 요약 | fallback variant score와 full-chunk snippet fallback 조건이 생략됨 | 실제 source 기준으로 variant raw score, 좁은 rescue 선행 조건, MCP evidence fallback을 명시 |

이는 기존 결과에 맞춘 tuning이 아니다. 이 시점에는 Current Search와 Search V3를 새 dataset에
실행하지 않았고 query/gold 성능 결과도 존재하지 않았다. 변경 전/후는 이 표에 보존하며
seal 뒤 같은 dataset version의 계약 의미를 바꾸지 않는다.

## 10. Materialized benchmark

tracked root는 `src/test/resources/search-v3-evaluation/`이고 기존 Search V2 dataset을 복사하거나
재라벨하지 않았다. tracked fixture는 완전 synthetic, 비개인정보 TXT이며 project Apache-2.0
license를 따른다. 실제 개인 원문은 추가하지 않았다. `/local/`은 기존 `.gitignore`로 제외되므로
동의 기반 실제 자료의 계약 경로는 `local/search-v3-evaluation/`로 정했다.

### 규모와 분포

| 항목 | 실제 materialized 값 |
| --- | --- |
| user bundles | 7 |
| logical documents / versions | 10 / 11 |
| ACTIVE / inactive versions | 10 / 1 |
| queries | 29 |
| Evidence Parent / Group / Unit | 20 / 28 / 28 |
| split bundles | DEV 3 / CALIBRATION 2 / SEALED FINAL 2 |
| split queries | DEV 13 / CALIBRATION 8 / SEALED FINAL 8 |
| profession | 7개 macro group 각 1 bundle |
| document language | KO 7 / EN 1 / KO-EN mixed 3 |
| query language | KO 12 / EN 7 / KO-EN mixed 10 |
| answerability | SUPPORTED 18 / PARTIALLY_SUPPORTED 1 / NOT_SUPPORTED 10 |
| file type | TXT 11 |

문서 구조는 short résumé 3, long portfolio 2, career description 2, narrative/self-introduction 1,
table-like 1, certification/training 2다. U01과 U03/U07은 문서 2개 이상을 가진다. backend,
frontend/mobile, data/AI/infra, design/product, planning, marketing/sales,
non-development/general을 각각 한 bundle로 표현했다.

모든 요구 category가 적어도 한 query에 존재한다. manifest의 실제 category count는
`abstract_competency 4`, `attempted_prototype 2`, `completed_production 4`,
`completion_state 5`, `date_range 4`, `english 7`, `hard_negative 10`,
`job_requirement 3`, `korean 12`, `korean_english_mixed 10`, `literal_identifier 3`,
`multi_aspect 4`, `multi_evidence 6`, `negation 3`, `no_answer 2`,
`numeric_quantity 6`, `numeric_range_comparison 3`, `other_actor 3`, `planned 1`,
`semantic_paraphrase 6`, `typo_format_variation 2`다.

OCR/image PDF와 DOCX는 이번 fixture에 넣지 않았다. schema의 capability scope로만 표현할 수
있으며 현재 비교 가능한 seed는 `SUPPORTED_BY_CURRENT` TXT다. 이 seed는 release-grade
`PROPOSED_TARGET >= 50 bundles`의 성능 근거가 아니다.

## 11. Gold와 validator 확인

Gold ID는 annotation ID이고 runtime chunk/parent DB ID가 아니다. validator는 실제 source
bytes에서 exact source text, code-point/line 좌표, text/document SHA-256을 다시 계산한다.
Parent·Unit·Group/user/document/version cross-reference, multi-span same-Parent, relation,
answerability expression, actor/state/entity/numeric/date constraint를 함께 확인한다.

`1,300건`의 normalized numeric value와 `요청`, `qualified leads`, `paid customers` 같은
semantic type/qualifier를 독립 field로 검증한다. 같은 숫자라도 `REQUEST_COUNT`,
`QUALIFIED_LEAD_COUNT`, `PAID_CUSTOMER_COUNT`가 다르면 서로의 direct support가 아니다.

support unit test는 정상 seed 1건과 다음 invalid mutation 17종을 실행했다.

- NOT_SUPPORTED+DIRECT, SUPPORTED without DIRECT
- 다른 user expected evidence, child/parent user mismatch, 없는 version
- cross-Parent multi-span
- source와 다른 numeric, source에 없는 entity, 잘못된 source span
- duplicate query/evidence ID
- template, generator, normalized query와 source fact split leakage
- runtime chunk ID gold contamination
- sealed-final source mutation과 SHA-256 mismatch

18 tests 모두 통과했다. 테스트 통과는 검색 품질 PASS가 아니라 validator가 정상 입력을 받고
각 invalid 입력을 거부했다는 의미다.

## 12. Freeze manifest

| manifest | 상태 | combined SHA-256 |
| --- | --- | --- |
| DEV | `MATERIALIZED` | `e6d6e872045b002f8f74ddbabdd9fa220993db803d1d6f6829d4675434ee17c5` |
| CALIBRATION | `MATERIALIZED` | `92095ea10504063ebdd5c1284c34396155923a1639e6163322e341337cbf6598` |
| SEALED FINAL | `SEALED` | `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383` |
| overall | `FRESH_BENCHMARK_SEED_FROZEN` | `1f36c4bbb6948b97c4321821cc3d6b8a9e38ab44b81adb1594614c6f7e97289e` |

definitive dataset version은 `search-v3-fresh-seed-1.0.1`, sealed timestamp는
`2026-08-30T18:42:16.0307918+09:00`이다. SEALED FINAL과 overall의
`opened=false`, `searchExecuted=false`를 validator가 확인했다. `sealed-final/` 안의 result,
prediction 또는 output file은 0개다. generator는 기존 sealed manifest를 덮어쓰지 않고 새
dataset version을 요구한다.

최초 pre-commit draft `search-v3-fresh-seed-1.0.0`은 overall
`b6ef342eda3f65d2be025c8360ab9ab1103bab823cc613deadc80dbb2eb935d6`, SEALED FINAL
`b9eb3fbe1911a2a2bcac61ed289bafe8612cdec9fb41d6efb8ef414f575843b8`로 materialize됐지만,
staged `git diff --check`에서 frozen README와 schema의 불필요한 EOF blank line 2건을 발견했다.
검색 실행·결과·개봉은 0이었고 `opened=false/searchExecuted=false`를 확인했다. 같은 version의
hash를 덮어쓰지 않고 `1.0.0 = SUPERSEDED_BEFORE_USE`로 기록한 뒤 byte-only LF 정규화와 새
dataset version `1.0.1`로 재봉인했다. schema 의미, corpus fact, query와 gold label 변경은 0이다.

## 13. Phase 2 실행 결과

| 명령/검사 | 실제 결과 |
| --- | --- |
| `node --check` materializer/validator/test | `PASS` — syntax error 0 |
| one-time seed materializer | `PASS` — 7 bundles, 11 versions, 29 queries와 manifest 생성 |
| `node scripts/evaluation/search-v3/validate-search-v3-benchmark.mjs` | `PASS` — schema/source/relation/constraint/leakage/hash finding 0 |
| `node --test scripts/evaluation/search-v3/validate-search-v3-benchmark.test.mjs` | `PASS` — 18/18 |
| JSON Schema Draft 2020-12 + format validation | `PASS` — schema 2개 유효, benchmark artifact 14개 오류 0 |
| sealed generator 재실행 | expected `REJECT` — 기존 `sealed-final/manifest.json` overwrite 전 write 없이 종료 |
| `git diff --check` | `PASS` — 출력 없음 |
| `node scripts/verify-oss-readiness.mjs` | `PASS` — Markdown 181/links 762, verifier tests 16/16, external links 97/97 |
| diff allowlist | `PASS` — 전체 branch changed paths 36, allowlist violation 0, Production/dependency path 0 |
| SEALED FINAL result/prediction/output file | `PASS` — 0개 |
| Current Search on DEV/CALIBRATION | `NOT_RUN` — materialization/integrity 범위 |
| Current Search on SEALED FINAL | `NOT_RUN` — 금지 |
| Search V3 on any split | `NOT_RUN` — 미구현 |
| `CURRENT_FRESH_BASELINE` | `NOT_RUN` |
| backend unit/integration | `NOT_RUN` — Production/backend source 변경 없음 |
| frontend test/build | `NOT_RUN` — frontend 변경 없음 |
| Docker/runtime | `NOT_RUN` — 검색·운영 benchmark 금지 범위 |

OSS readiness 과정의 backend/frontend SBOM 재생성은 diff 0이었다. 이 명령의 SBOM/verifier
test를 application backend unit/integration, frontend test/build 또는 runtime benchmark로
확대하지 않는다.

## 14. Phase 2 audit와 다음 Gate

문서, schema, manifest, data count/distribution, source span과 lineage를 교차 확인했다. 기존 V2
dataset/result는 historical/regression/failure-analysis로만 남고 새 Final에 포함되지 않았다.
PRZ-016의 P5 `FAIL`, P6/GPT Judge `NO_GO`, P7-B `FAIL`, P15 `NOT_VERIFIED`, P16
`NEEDS_ADJUSTMENT`도 변경하지 않았다. 개인정보 fixture, Production source, migration,
dependency, frontend, MCP, Docker runtime와 `v1.0.0` 변경은 모두 0이다.

Phase 2 판정은 `FRESH_BENCHMARK_SEED_FROZEN`이다. 이는 Search V3 또는 Current Search의
품질 검증 완료가 아니다. PRZ-025 Registry는 다음 항목 때문에 계속 `IN_PROGRESS`다.

- release-grade 50+ bundle corpus와 실제 target distribution
- public fixture license review 또는 consented-real 운영 계약
- 독립 gold adjudication과 release-grade final custodianship/access policy
- DEV/CAL 측정에 근거한 Search Quality·Operational 숫자 `FROZEN_GATE`
- Current Search fresh baseline과 finalist V3 independent final comparison

`SAFE_TO_START_PRZ_026_DEV_CAL_ONLY`로 판단한다. Evidence/Query/Gold/split/metric-readiness와
seed validator가 명확하고 unresolved contract contradiction이 없으며 Production 변경이 0이다.
PRZ-026 Structural Parsing은 새 별도 workflow에서 `DEV/CALIBRATION`만 사용해 시작할 수 있다.
다음 조건은 그대로 유지한다.

- `SEALED_FINAL_TEST`에 Current Search나 V3를 실행하지 않는다.
- sealed query/gold/evaluator 의미를 parsing 결과에 맞춰 수정하지 않는다.
- OCR/Docling/model/dependency/Production ingestion은 별도 승인 전 추가하지 않는다.
- release-grade final/adoption은 50+ corpus, independent adjudication과 숫자 Gate 전에는 판정하지 않는다.

PRZ-026 구현, PR, push와 main merge는 이번 Phase에서 `NOT_RUN`이다.
