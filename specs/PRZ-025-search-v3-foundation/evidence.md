# PRZ-025 Search V3 Foundation Evidence

- 상태: `IN_PROGRESS`
- 기록일: 2026-08-30 (Asia/Seoul)
- 기준 source: `origin/main@2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- benchmark/model/generalization 실행: `NOT_RUN`
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
