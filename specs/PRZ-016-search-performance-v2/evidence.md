# PRZ-016 Search Performance V2 Evidence

관리 구조 정리일: 2026-08-14

## 역사 보존

이 PRZ는 기존 검색 성능 작업 문서를 Phase로 재배치한 관리 정리다. 평가셋, ground
truth, benchmark 결과, 실행 시점과 production 구현은 변경하지 않았다.

| Phase | 보존된 결과 |
|---|---|
| P0 | Top1 57.14%, Recall@3 66.07%, Recall@5 67.86%, MRR@5 0.6146, Negative FPR 6.25% |
| P1 | Top1 60.71%, Recall@3 69.64%, Recall@5 71.43%, MRR@5 0.6503, Negative FPR 0% |
| P2 | Top1 67.86%, Recall@3 71.43%, Recall@5 71.43%, MRR@5 0.6935, Negative FPR 0% |
| P3 | Top1 75.00%, Recall@3/5 78.57%, MRR@5 0.7649, Negative FPR 0%; Query Understanding 완료 |
| P4 | Top1 82.14%, Recall@3/5 85.71%, MRR@5 0.8363, Negative FPR 0%; Evidence Localization 완료 |
| P5 | 48-query holdout: Top1 50.00%, Recall@3/5 61.11%, MRR@5 0.5509, Negative FPR 25%; `FAIL` |
| P6 | PostgreSQL lexical+dense+RRF+literal gate shadow: H1 candidate recall 개선 0pp, H2 stress FPR 0%이나 72-query 회귀 5건; `NO_GO` |
| GPT-J1 | GPT Evidence Judge shadow: Negative FPR 0%이나 완료 positive 회귀 2건·incomplete 4건; `NO_GO` |

- P0 자산: [dataset](p0-benchmark/evaluation-dataset.json),
  [baseline](p0-benchmark/baseline-results.json),
  [failure analysis](p0-benchmark/failure-analysis.md)
- P1 검증: [evidence](p1-numeric-identifier/evidence.md)
- P2 검증: [evidence](p2-evidence-reranking/evidence.md)
- P3 검증: [evidence](p3-query-understanding/evidence.md)
- P4 검증: [evidence](p4-evidence-localization/evidence.md)
- P5 최종 평가: [final validation](p5-final-holdout/final-validation.md)
- P6 shadow 평가: [56-item evidence](p6-retrieval-shadow/evidence.md),
  [authoritative raw result](p6-retrieval-shadow/p6-b-results.json)
- GPT-J1 shadow 평가: [evidence](gpt-evidence-judge-shadow/evidence.md)

## 관리 구조 정리 당시 확인 결과

- production code 변경: 0
- 검색·API·평가 데이터 변경: 0
- 재번호화 전 branch 기준 `PRZ-014` OpenHA 문서: 당시에는 확인되지 않았으며, 이후 최신
  `main`의 공식 Registry에서 별도 Spec으로 확인됨
- 내부 문서 경로: 새 Phase 구조로 갱신

## 현재 Phase 상태

P0·P1·P2·P3·P4는 `DONE`이며 해당 deterministic 검색 구현은 채택했다. P5와 P7-B는
`DONE — FAIL`, P6·GPT-J1과 후속 rule/NLI/Qwen/Hybrid shadow는 `DONE — NO_GO`다.
P7-B는 frozen v2 corpus와 질문 48개를 독립 실행해 Top1 33.33%, Recall@5 58.33%,
Negative FPR 41.67%를 기록했다. Owner와 ACTIVE version 격리는 통과했지만 일반화 Gate는
실패했다. P7-B는 앞으로 diagnostic/historical 자료로만 보존하고 추가 tuning에 사용하지 않는다.

P7-B 종료 시점의 상태는 `DEFERRED / PRZ_016_STATE_FROZEN`이었고 재개 조건은 P7-B를
재사용하지 않는 `FRESH_GENERALIZATION_EVALUATION_V2`였다. 2026-08-18 P8에서 그 조건을
충족해 evaluation-only trace와 fresh benchmark를 동결·실행했다. 현재 상태는
`IN_PROGRESS / P8 VERIFIED_BASELINE_FROZEN`이다. P5 이후 shadow 실험은 production 검색에
연결하지 않았고 P8도 production source, 정책, API를 변경하지 않았다.

## P8 Evaluation Observability와 Fresh Baseline

P8은 현재 production response와 44/44 일치하는 구조화 decision trace를 추가하고, 기존
P7-B 14개 positive failure의 최초 소실 분포(dense floor 7, negation 2, source consolidation
1, query-evidence consolidation 4)를 재현했다. 별도의 synthetic 사용자 4명, ACTIVE 문서
8개, inactive version 4개, Positive 24/Negative 20 query를 첫 실행 전에 동결했다.

Fresh baseline은 Candidate Recall@20 100%, Top1/Recall@5 87.5%, Negative FPR 50%,
localization correctness 79.17%, owner/ACTIVE isolation PASS였다. 이 수치에 맞추기 위한
검색 수정은 하지 않았다. 상세 구조, hash, query별 trace와 frozen 입력은 local-only
evaluation artifact로 보존하며 공개 저장소에는 aggregate 결과만 남긴다.

## P9 Structured Claim-Support Eligibility

P9는 retrieval·fallback·consolidation·ranking·localization을 바꾸지 않고 Production
eligibility에 deterministic structured claim support를 추가했다. Frozen P8.1의
dataset/original runner mismatch는 0이며, pre-P9 Production source hash 차이는 의도된
eligibility 구현 delta로 분리 기록했다.

Judge는 Top1/Recall@5/selected correctness가 56.25/62.5/62.5%에서 모두 87.5%로,
Negative FPR은 15%에서 0%로 바뀌었다. Stress는 Top1/Recall@5/selected correctness가
75%에서 100%로, Negative FPR은 75%에서 0%로 바뀌었다. Dense Recall@1/5/10/20,
owner isolation, ACTIVE isolation은 회귀 없이 PASS다. Displayed/localization은 각각
Judge 68.75%, Stress 65%/60%로 남아 다음 Phase 대상이다. 상세 판정과 query별 trace는
local-only evaluation artifact로 보존하며 공개 저장소에는 aggregate 결과만 남긴다.

## P10 Evidence Localization

P10은 retrieval, P9 eligibility, consolidation, ranking과 API result identity를
변경하지 않고 selected chunk 내의 extractive evidence 표시를 수정했다.
PDF hard wrap을 semantic sentence boundary와 분리하고, 같은 block의 연속
1–3문장 window를 query/claim completeness로 비교한다. Selected chunk가
충분하면 expansion하지 않으며, 부족할 때만 기존 same-owner,
same-document, same-ACTIVE-version 조회를 유지한다.

Judge의 Top1/Recall@5/selected correctness 87.5%와 FPR 0%는 불변이고,
displayed/localization은 68.75% → 87.5%다. Stress의 Dense Recall@1/5/10/20,
Top1/Recall@5/selected correctness 100%와 FPR 0%는 불변이고,
displayed/localization은 65/60% → 100/100%다. P9 localization PASS → P10 FAIL
회귀는 0건이며 owner/ACTIVE isolation과 trace/Production parity는 PASS다.
Frozen dataset/original runner mismatch는 0이다. 상세 구조, 예시, expansion
전수 조사, complexity warning과 명령 결과는 local-only evaluation artifact로 보존하며
공개 저장소에는 aggregate 결과만 남긴다.

## P11 Source Consolidation Redesign

P11은 동일 PDF page 전체를 하나의 evidence로 보던 기준을 교정해, 기존 meaningful
exact-boundary-overlap을 만족하는 chunk만 source consolidation에서 합친다. 실제
이력서 read-only trace에서 Spring Boot/Java 직접 근거 retention은 2→4,
Docker/MySQL은 2→3으로 개선됐고 Java final은 0→2가 됐다.

Frozen Judge/Stress의 Dense, Top1, Recall@5, selected correctness, Negative FPR,
localization, owner/ACTIVE isolation과 parity metric은 유지됐다. 그러나 Stress 1건의
final 결과가 3→5로 늘면서 normalized duplicate snippet도 2개 증가해 원본 P10
runner의 exact-result assertion과 P11 duplicate Gate가 실패했다. 따라서 P11은
`PARTIAL_PASS / IMPLEMENTED_UNVERIFIED`이며, 상세 query-level 결과는 local-only
evaluation artifact로 보존한다.

## P11.1 Duplicate Evidence Consolidation

P11 source identity를 유지한 상태에서 same-version repeated text span만
query-evidence consolidation의 representative 기준으로 축약했다. Stress RS-S02-P02는
final 5→3, duplicate extras 5→3과 exact final result가 P10 수준으로 복구됐고 실제
이력서 source retention 4/4/3/3과 P8.1/P9/P10 metric·FPR·localization·owner/ACTIVE
isolation은 유지됐다. P11.1 판정은 `PASS`다. 상세 query-level 결과는 local-only
evaluation artifact로 보존한다.

## P12 Simple Tech Usage Eligibility

P12는 Dense retrieval과 P11/P11.1 consolidation을 바꾸지 않고, P9의 simple
`USE` claim boundary만 보완했다. 같은 candidate의 project-scoped technology
declaration 또는 직접 entity-bound usage는 허용하되, negation, not-adopted,
other-actor, review/comparison, numeric/metric, prototype/production state gate는
그대로 유지한다. Read-only real-resume trace에서 PostgreSQL은 eligibility/final
`0/0 → 2/2`, OAuth2는 `0/0 → 1/1`로 복구됐고 production-response parity는
유지됐다. Judge는 Candidate Recall@20 `1.0`, Top1/Recall@5/localization `0.875`,
FPR `0`; Stress는 모두 `1.0`, FPR `0`; owner/ACTIVE isolation과 P10 exact
result assertion은 PASS다. 상세 query-level 결과는 local-only evaluation artifact로
보존한다.

## Spec ID 충돌 해소

2026-08-16에 최신 `main`이 `PRZ-013` OpenProxy, `PRZ-014` OpenHA, `PRZ-015` MCP를
공식 Registry에서 사용 중임을 확인했다. 이 검색 작업은 아직 `main`에 병합되지 않았으므로
다음 빈 ID `PRZ-016`으로 재번호화했다. 파일 경로와 현재 문서·실행 코드 참조만 바꾸고,
frozen dataset/ground truth/raw result 내부의 역사적 phase·benchmark 라벨과 측정값은
변경하지 않았다.

## P7-A Cross-Document Dataset Freeze

- 결과: `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`
- 합성 사용자 4명, ACTIVE 문서 8개(TXT 4/PDF 4), 질문 48개(Positive 36/Negative 12)
- P0/P5 exact·normalized duplicate 0, 명백한 near duplicate 0
- corpus SHA-256:
  `4ad8a564bee353c877a9c0938d6cf1f866d6d824750b01c5bf5f976b71a25ae1`
- questions SHA-256:
  `7e1055db772034e0d7257de781944c9d5ba368888b5d1757baea7f864fcab957`
- ground truth SHA-256:
  `cbb78a66f3563ab82d86702220c409a3674ce2a6f5db1ad366795d085e6186f9`
- production 변경 0, 검색·benchmark·GPT Judge `NOT_RUN`
- 상세: [P7-A evidence](p7-cross-document-generalization/evidence.md)

## P7-A v2 Document Density Replacement

P7-A v1은 검색 실행 전에 보존했지만 PDF가 1페이지 요약 카드 수준이라 실제 이력서의 문서 밀도와
주변 정보가 부족했다. v1 27개 frozen asset은 hash mismatch 0으로 그대로 보존하고
`SUPERSEDED_BEFORE_RUN` 처리했다.

- v2 결과: `DATASET_FROZEN — USED_BY_P7-B`
- PDF 이력서 4개 × 2페이지, 장문 TXT 포트폴리오 4개
- 질문 48개(Positive 36/Negative 12), Positive anchor 67개와 Negative 부재 12개 검증
- P0/P5/v1 exact·normalized duplicate 및 threshold 초과 near duplicate: 0
- corpus SHA-256:
  `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498`
- questions SHA-256:
  `85c2e41bba5c293ca5172b48f77f41587d49be996252479ce5a71ed17763b868`
- ground truth SHA-256:
  `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680`
- production 변경 0, 검색·benchmark·GPT Judge `NOT_RUN`
- 상세: [P7-A v2 evidence](p7-cross-document-generalization-v2/evidence.md)

## P7-B Independent Generalization

- 입력: P7-A v2 frozen corpus·questions·ground truth
- 실행: 48/48, Positive 36 / Negative 12
- Top1: 33.33%
- Recall@5: 58.33%
- Negative FPR: 5/12, 41.67%
- Owner isolation: `PASS`
- ACTIVE version isolation: `PASS`
- 최종 판정: `P7-B FAIL`
- 후속 정책: `DIAGNOSTIC / HISTORICAL DATASET`, further tuning `NOT_ALLOWED`
- 상세: [P7-B evidence](p7-b-independent-generalization/evidence.md)

## 종료 상태

- 채택: P1 Numeric + Strong Identifier, P2 Evidence-Aware Reranking,
  P3 Query Understanding, P4 Evidence Localization
- 비채택: Hybrid/FTS/RRF/Sparse, GPT Judge, rule-based rejection,
  NLI model swap, Qwen 4B verifier와 fail-closed semantic filtering
- 현재 상태: `DEFERRED / PRZ_016_STATE_FROZEN`
- 재개 조건: `FRESH_GENERALIZATION_EVALUATION_V2`

## PR 생성 전 최종 검증

- 완료 경험 질의의 identifier guard 빈 결과를 기존 `NO_EVIDENCE` 계약에 맞췄다.
  검색 순위, threshold, retrieval과 P7-B frozen 입력은 변경하지 않았다.
- `SearchServiceTest`: 29개 PASS
- 전체 unit: 533개 중 실패 0, skip 16
- 전체 integration: 113개 중 실패 0, skip 8
- frontend lint와 production build: PASS
- Docker Compose config: PASS
- P7-A v2 frozen asset 31개, v1 manifest와 corpus/questions/ground truth hash: PASS
- Markdown 로컬 링크와 `git diff --check origin/main`: PASS
- 검색 benchmark와 모델 inference: `NOT_RUN` (state freeze 이후 재실행 금지)

## P12.1 Direct-Support Floor Bypass Contract

P12.1은 threshold 자체를 변경하지 않았다. 기존의 `directSupport &&
(actions 또는 numeric)` bypass에, 기존 direct-anchor fallback query가 아닌 actual
claim 질문의 direct support만 추가했다. FCM chunk 108 (dense score `0.425492`)은
`SUPPORTED/directSupport`로 eligibility와 final result를 통과했다. P10 Judge는
Top1/Recall@5/localization `0.875`, FPR `0`; Stress는 각각 `1.0`, FPR `0`; owner와
ACTIVE isolation은 모두 `PASS`다. 상세 query-level 결과는 local-only evaluation
artifact로 보존한다.

## P15 PDF Document Confirmation UX

- 구현 경계: frontend `EvidencePage`만 변경했다. 검색 API, SearchService, 검색 profile,
  database schema와 ingestion은 변경하지 않았다.
- PDF `PAGE` evidence의 `documentId`, `documentVersionId`, `evidenceSourceIndex`로 기존
  authenticated original endpoint를 호출하고 Blob iframe URL에 `#page=N&zoom=page-width`를 붙인다.
  `TEXT_CHUNK`에는 버튼을 노출하지 않는다.
- highlight: `NOT_IMPLEMENTED`. 현재 PDF.js/text layer가 없으므로 좌표·OCR·새 dependency를
  추가하지 않았고, native viewer `search` fragment experiment는 동작하지 않아 제거했다.
  highlight 실패가 PDF page 열기를 막지 않는다.
- frontend presentation unit: 17 PASS. PDF page selector는 `PAGE` 2를 2로, `TEXT_CHUNK`를
  `null`로 확인했다.
- frontend lint/typecheck/production build: PASS. Docker frontend image build: PASS.
- Docker runtime: frontend HTTP 200, backend actuator health UP. In-app browser에서
  unauthenticated 화면 렌더링까지 확인했다. `PRIZM_BOOTSTRAP_DEMO_USER_ENABLED=false`이고
  사용자의 로그인 세션 또는 PDF fixture가 없어 authenticated PDF page navigation은 `NOT_VERIFIED`다.
- backend source 변경 0이므로 별도 backend 변경 test는 없다. 기존 original endpoint의
  `DocumentThumbnailService.resolveOwnedVersion` owner-scoped lookup과 controller test를 source로
  재확인했다.
