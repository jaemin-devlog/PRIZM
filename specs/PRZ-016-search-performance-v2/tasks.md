# PRZ-016 Search Performance V2 Tasks

## P6 Retrieval Architecture Shadow Benchmark

- [x] ORIENT: branch/HEAD/worktree, workflow, architecture/status/roadmap, PRZ-008 P13/P14,
  PRZ-016 P0~P5, 실행 중 PostgreSQL/Ollama/API 확인
- [x] SPEC: D0/L1/H1/H2, freeze 순서, 보존·비범위와 mandatory gate 확정
- [x] PLAN: evaluation-only 파일, read-only DB, 검증·중단·Git 계획 확정
- [x] P6-A: 기존 P13 lexical repository/RRF 재사용 contract 고정
- [x] P6-A: external corpus runner와 D0/L1/H1 Q0·end-to-end diagnostics 구현
- [x] P6-A: 72 development와 P5 diagnostic 실행·결과 저장
- [x] P6-B 준비: 24~32개 Identifier Stress Set과 ground truth 직접 검증
- [x] P6-B 준비: stress dataset/ground truth/production source SHA-256 freeze
- [x] P6-B: Generic Literal Evidence Gate와 per-query diagnostics 구현
- [x] P6-B: stress/72/P5/legacy/numeric/positive identifier guard 실행
- [x] VERIFY: owner/ACTIVE, P3 sequential, score/distance, latency와 전체 backend 회귀
  (`test` PASS, `integrationTest` 기존 production 회귀 1건 FAIL)
- [x] AUDIT: production diff 0, freeze 불변, 금지 범위, 결과 정합성, 민감정보 확인
- [x] 최종 판정 `NO_GO`와 56개 보고 항목 작성 후 종료

## GPT-J1 Evidence Judge Shadow Spike

- [x] ORIENT: 최신 main 병합, Docker·unit·integration 기준선과 알려진 P4 상태 회귀 고정
- [x] ORIENT: OpenAI Responses API Structured Outputs·Data Controls와 API key 상태 확인
- [x] SPEC: shadow-only, 최소 snippet, DB owner·ACTIVE·원문 재검증과 성공 기준 고정
- [x] PLAN: evaluation-only source, offline tests, live P5 48-query 실행과 rollback 계획 고정
- [x] IMPLEMENT: Responses API client, Top10 snippet 조립, fail-closed DB verifier 구현
- [x] IMPLEMENT: P5 P4 대 P4+GPT 비교 runner와 민감정보 비추적 결과 경계 구현
- [x] VERIFY: focused 9개와 전체 unit 530개 PASS, Docker integration은 기존 P4 1건만 FAIL
- [x] VERIFY: process-only API key, Responses quota preflight와 21초 pacing으로 live P5 48-query 실행
- [x] AUDIT: `src/main` 변경 0, secret·snippet 비추적, owner·ACTIVE·원문 검증 우회 0 확인
- [x] 판정: Negative FPR 0%지만 완료 positive 회귀 2건·incomplete 4건으로 `NO_GO`

## P7-A Cross-Document Dataset Freeze

- [x] ORIENT: branch·HEAD·origin/main·production source·P0~P6·P0/P5 목록과 hash 고정
- [x] SPEC/PLAN: P7-A 생성·검증·동결과 P7-B 독립 실행 경계 고정
- [x] IMPLEMENT: 합성 사용자 4명, ACTIVE TXT 4/PDF 4, inactive fixture 1개 작성
- [x] IMPLEMENT: 신규 질문 48개와 검색 전 Ground Truth 48개 작성
- [x] VERIFY: owner/ACTIVE/source/page/anchor/negative absence·PDF 시각 품질 PASS
- [x] VERIFY: P0/P5 exact·normalized·near duplicate 및 기존 fact 재사용 0
- [x] FREEZE/AUDIT: SHA-256 manifest, production 변경 0, 검색·commit·push·PR `NOT_RUN`
- [x] `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`에서 종료

## P7-A v2 Document Density Replacement

- [x] v1 freeze 27개 자산 hash 불일치 0 확인 및 `SUPERSEDED_BEFORE_RUN` 보존
- [x] PDF 이력서 4개를 각 2페이지로 재설계하고 장문 TXT 포트폴리오 4개 작성
- [x] 신규 질문·검색 전 Ground Truth 48개 작성
- [x] PDF 8페이지 추출·렌더링·전 페이지 시각 검사 PASS
- [x] Positive anchor 67개, Negative ACTIVE 부재 12개 검증
- [x] P0/P5/v1 exact·normalized·near duplicate 검사 PASS
- [x] production 변경 0, 검색·benchmark·GPT Judge·commit·push·PR `NOT_RUN`
- [x] `P7-A v2 DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`에서 종료

## P7-B와 State Freeze

- [x] frozen P7-A v2 자산 hash를 검증하고 독립 P7-B 48/48 실행
- [x] Top1 33.33%, Recall@5 58.33%, Negative FPR 41.67%와 `P7-B FAIL` 보존
- [x] Owner/ACTIVE version isolation PASS 기록
- [x] Hybrid, rule, NLI, GPT/Qwen과 후속 filter/localizer 실험의 NO_GO 결과 보존
- [x] P7-B를 `DIAGNOSTIC / HISTORICAL DATASET`으로 전환하고 추가 tuning 금지
- [x] `PRZ_016_STATE_FROZEN`, 재개 조건 `FRESH_GENERALIZATION_EVALUATION_V2` 기록

## PR 생성 전 감사 보완

- [x] 완료 경험 질의의 identifier guard 빈 결과를 `NO_EVIDENCE`로 정합화
- [x] 일반 질의의 `NO_RELEVANT_RESULTS`와 owner/ACTIVE guard 보존 test 추가
- [x] Spec·Evidence·Registry·R&D History의 P7-B와 state freeze 표현 정합화
- [x] frozen 파일 byte/hash를 유지하는 path-specific whitespace attribute 추가
- [x] focused unit/integration test PASS
- [x] 전체 unit/integration test PASS
- [x] frozen 31개 자산·v1 manifest hash 재검증 PASS
- [x] Markdown link와 `git diff --check` PASS
- [x] 최종 AUDIT blocking finding 0

## P8 Evaluation Observability + Fresh Generalization V2

- [x] ORIENT/SPEC/PLAN Gate 완료
- [x] 구조화 production decision trace와 parity 검증 구현
- [x] P7-B trace validation 실행
- [x] fresh corpus/questions/Ground Truth V2 작성·동결
- [x] fresh baseline, 전체 검증과 AUDIT 완료

## P9 Structured Claim-Support Eligibility

- [x] Query claim requirements와 candidate support decision 구현
- [x] `CompositeSearchProfile` eligibility에만 통합
- [x] frozen P8.1 Judge/Stress after-run과 structured trace 생성
- [x] Judge Recall@5/selected 87.5%, FPR 0% 달성
- [x] Stress Recall@5/selected 100%, FPR 0% 달성
- [x] Dense regression 0, owner/ACTIVE isolation, full unit/integration PASS
- [x] Localization은 변경하지 않고 다음 Phase 판단으로 분리

## P10 Evidence Localization

- [x] 실제 snippet/expansion 호출 흐름과 P9 localization 실패 11건 재확인
- [x] P9·P8.1 freeze, localization-only 범위, 성공/중단 조건 고정
- [x] hard-wrap-aware sentence/window extraction과 claim-complete scoring 구현
- [x] selected-chunk-first, owner/document/ACTIVE-version-scoped expansion 유지
- [x] Frozen Judge/Stress before-after, selection parity, isolation, regression 검증
- [x] focused 31개, backend unit 554개, integration 113개 실행
- [x] complexity/scope/security 감사 — `ACCEPTABLE_BUT_COMPLEX`, blocking finding 0
- [x] commit/push/PR `NOT_RUN` — 사용자 금지 지시
- [x] sentence extraction, window scoring, orchestration, expansion 책임과 LOC 계획 작성
- [ ] selected-chunk-first extractive localization 구현 및 focused test
- [ ] frozen Judge/Stress before-after, regression/isolation/parity 검증
- [ ] complexity·scope·hardcoding 감사와 evidence 완료

## P11 Source Consolidation Redesign

- [x] 같은 PAGE/sourceIndex 전체를 합치던 실제 Production 경로 재확인
- [x] 기존 meaningful exact-boundary-overlap 기준을 PDF evidence identity에 적용
- [x] same-page distinct, duplicate/overlap, different-page, TXT synthetic 회귀 추가
- [x] 실제 이력서 4개 질의 source retention read-only Before/After 확인
- [x] backend unit 560개, integration 113개 PASS
- [x] frozen Judge/Stress 품질·FPR·localization·isolation·parity metric 유지
- [x] frozen Stress 1건 exact-result drift와 duplicate snippet +2 확인
- [x] 최종 판정 `PARTIAL_PASS`; commit/push/PR `NOT_RUN`

## P11.1 Duplicate Evidence Consolidation

- [x] repeated same-version evidence를 QEV representative 기준으로만 축약
- [x] same-technology different-project synthetic 회귀와 repeated-span 회귀 추가
- [x] Stress 5→3, actual-resume source retention 4/4/3/3 유지 확인
- [x] unit 562개, integration 113개, metric/isolation, diff check PASS
- [x] frozen P10 exact chunk-ID assertion과 metric/isolation gate PASS
- [x] commit/push/PR `NOT_RUN`

## P12 Simple Tech Usage Eligibility

- [x] Reproduce P9 simple-technology `ACTION_NOT_SUPPORTED` / entity-binding
  decisions from the P11.1 state.
- [x] Add only the bounded project-scoped technology declaration/direct-use
  support rule to the existing evaluator.
- [x] Add synthetic positive and safety regressions without resume text or
  technology-specific production branches.
- [x] Run read-only real-resume trace, frozen P8.1/P9/P10/P11.1, full backend
  unit and integration suites, and diff audit.
- [x] Record branch-local `PASS`; commit/push/PR remain `NOT_RUN` by request.

## P13 Evidence Expansion Safety

- [x] Reproduce selected-result to expanded-evidence direct-anchor loss.
- [x] Add selected-anchor retention and cross-chunk preservation guards.
- [x] Add synthetic regression and read-only real-resume anchor audit.
- [x] Run frozen P10 and backend verification.
- [x] Commit/push/PR remain `NOT_RUN` by request.

## P12.1 Direct-Support Floor Bypass Contract

- [x] Reproduce the FCM direct-support candidate's below-floor rejection.
- [x] Extend only the existing dense-floor bypass with direct claim support while
  retaining the pre-existing direct-anchor fallback contract.
- [x] Add below-floor direct-support, mere-mention, and contradicted synthetic
  regressions.
- [x] Run read-only FCM/P12 real-resume audits, frozen P10, backend unit and
  integration suites, and diff audit.
- [x] Record branch-local `PASS`; commit/push/PR remain `NOT_RUN` by request.

## P15 PDF Document Confirmation UX

- [x] ORIENT: 기존 original API, Blob iframe viewer, search evidence document/version/page
  metadata와 owner-scoped backend lookup을 확인
- [x] SPEC/PLAN: page-only viewer, 검색 불변성, highlight 비범위, authorization과 rollback
  계약을 고정
- [x] IMPLEMENT: PDF `PAGE` evidence 카드에 `문서에서 보기`와 existing authenticated viewer
  재사용 연결
- [x] IMPLEMENT: `#page=N&zoom=page-width` page navigation과 viewer-local error handling
- [x] VERIFY: 결과 ID/order/count/snippet/context 불변 frontend regression
- [x] VERIFY: frontend unit/lint/build, Docker image build와 unauthenticated browser rendering
- [ ] VERIFY: authenticated PDF page navigation — local bootstrap user is disabled and no user session or
  document fixture was available; `NOT_VERIFIED`
- [x] AUDIT: 검색 source/API/DB 변경 0, owner boundary와 highlight `NOT_IMPLEMENTED` 확인
- [ ] commit/push/PR `NOT_RUN` — 사용자 금지 지시
