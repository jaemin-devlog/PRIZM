# PRZ-016: Search Performance V2

- 상태: `IN_PROGRESS`
- 목표: PRIZM 커리어 근거 검색의 retrieval, ranking, query understanding과 evidence
  localization을 같은 평가 기준으로 측정하고 단계적으로 개선한다.

## 관리 원칙

PRZ는 독립적인 기능 또는 자체적으로 완료·폐기할 수 있는 기술 목표에만 사용한다. 같은
목표의 반복 실험과 순차 개선은 새 PRZ를 만들지 않고 이 문서 아래 Phase로 관리한다.

## Phase

| Phase | 상태 | 범위 | 역사적 출처 |
|---|---|---|---|
| P0 Benchmark / Baseline | `DONE` | 72-query dataset, ground truth, baseline, failure taxonomy, latency | 초기 `PRZ-014` 문서 |
| P1 Numeric + Strong Identifier | `DONE` | 숫자+단위 fallback, near-miss 보호, identifier guard | 초기 `PRZ-015` 문서 |
| P2 Evidence-aware Lightweight Reranking | `DONE` | 기존 candidate 내부 deterministic evidence-quality reranking | 통합 전 임시 `PRZ-016` 문서 |
| P3 Query Understanding | `DONE` | 자연어 fallback, 보수적 semantic alias와 최대 2개 limited multi-query dense retrieval | 통합 전 임시 `PRZ-013` 문서 |
| P4 Evidence Localization | `DONE` | 맞는 문서의 상세 근거 page/chunk 정확도 개선 | — |
| P5 Final Holdout Validation | `DONE — FAIL` | 별도 unseen holdout으로 과적합 여부와 최종 성능 확인 | — |
| P6 Retrieval Architecture Shadow Benchmark | `DONE — NO_GO` | D0/L1/H1/H2 평가 전용 비교, frozen identifier stress set, regression·격리·계약 검증 | PRZ-008 P13/P14 |
| GPT-J1 Evidence Judge Shadow Spike | `DONE — NO_GO` | P5 48개에서 현재 P4와 GPT evidence 판정을 비교. Negative FPR 0%지만 정상 완료 positive 회귀 2건과 judge incomplete 4건으로 종료 | — |
| P7-A v1 Cross-Document Dataset Freeze | `PRESERVED — SUPERSEDED_BEFORE_RUN` | 최초 synthetic holdout. 검색 전에 보존했으나 PDF 문서 밀도 부족으로 사용하지 않음 | [v1 evidence](p7-cross-document-generalization/evidence.md) |
| P7-A v2 Cross-Document Dataset Freeze | `DATASET_FROZEN — USED_BY_P7-B` | 2페이지 PDF 이력서 4개·장문 TXT 포트폴리오 4개·신규 질문 48개와 pre-search ground truth 동결 | [v2 evidence](p7-cross-document-generalization-v2/evidence.md) |
| P7-B Independent Generalization Run | `DONE — FAIL` | 독립 Codex 세션에서 frozen P7-A v2 자산으로 48/48 실행. Top1 33.33%, Recall@5 58.33%, Negative FPR 41.67% | [P7-B evidence](p7-b-independent-generalization/evidence.md) |
| P8 Evaluation Observability + Fresh Generalization V2 | `VERIFIED_BASELINE_FROZEN` | production 정책을 고정한 구조화 stage trace, P7-B 재검증, 새 사용자·문서·질문·multiple acceptable evidence 기반 독립 baseline | [P8 evidence](fresh-generalization-evaluation-v2/evidence.md) |
| P8.1 Judge-Realistic + Retrieval-Stress | `BASELINE_FROZEN` | 실제 ingestion을 거친 Judge 36-query와 owner별 30 ACTIVE chunk Stress 28-query 독립 기준선 | [P8.1 evidence](p8-1-judge-realistic-retrieval-stress/evidence.md) |
| P9 Structured Claim-Support Eligibility | `PASS` | query claim constraint와 candidate-local support/contradiction 판정을 eligibility에만 적용 | [P9 evidence](p9-structured-claim-support-eligibility/evidence.md) |
| P10 Evidence Localization | `VERIFIED` | P9 selected result 안의 hard-wrap-aware extractive 1–3문장 claim-complete evidence 표시 | [P10 evidence](p10-evidence-localization/evidence.md) |
| P11 Source Consolidation Redesign | `PARTIAL_PASS` | 같은 PDF page의 별도 evidence는 보존하고 meaningful overlap만 축약. 실제 이력서 retention은 개선됐지만 frozen Stress 1건의 중복 결과가 2개 증가 | [P11 evidence](p11-source-consolidation-redesign/evidence.md) |
| P11.1 Duplicate Evidence Consolidation | `PASS` | P11 source identity는 유지하고 same-version repeated evidence만 QEV에서 축약. Stress 결과 수·duplicate extras·exact final result를 P10 수준으로 복구 | [P11.1 evidence](p11-1-duplicate-evidence-consolidation/evidence.md) |
| P12 Simple Tech Usage Eligibility | `PASS` | simple USE 질문에 project-scoped technology declaration 또는 직접 usage를 인정하고 P9 안전 gate 유지 | [P12 evidence](p12-simple-tech-usage-eligibility/evidence.md) |
| P12.1 Direct-Support Floor Bypass Contract | `PASS` | evaluator가 직접 지원한 claim 질문은 action/numeric 추출 공백만으로 dense floor에서 제거하지 않되 기존 direct-anchor fallback 계약은 유지 | [P12.1 evidence](p12-1-direct-support-floor-bypass/evidence.md) |
| P13 Evidence Expansion Safety | `PASS` | selected evidence의 직접 ASCII anchor를 보존하고 cross-chunk expansion이 이를 잃지 않게 제한 | [P13 evidence](p13-evidence-expansion-safety/evidence.md) |
| P14 Claim-Complete Snippet | `PASS` | 해결 질문은 contiguous problem/action/result window를 단문보다 우선 | [P14 evidence](p14-claim-complete-snippet/evidence.md) |
| P15 PDF Document Confirmation UX | `IMPLEMENTED — PAGE_NAVIGATION_NOT_BROWSER_VERIFIED` | 검색 카드에서 기존 owner-scoped PDF original viewer를 열고 evidence page로 이동. 검색 정책·결과는 불변 | — |

P4는 focused 검증과 동일 72-query benchmark를 통과했다. P5와 P7-B는 서로 다른 unseen
조건에서 모두 일반화 Gate를 통과하지 못했다. P7-B는 앞으로 `DIAGNOSTIC / HISTORICAL
DATASET`으로만 보존하며 threshold, prompt, rule 또는 model tuning에 다시 사용하지 않는다.
P1~P4의 deterministic 검색 구현은 유지하되 현재 연구 Phase는 `STATE_FROZEN`으로 닫고,
새 사용자·문서·질문과 multiple acceptable evidence 계약을 갖춘
`FRESH_GENERALIZATION_EVALUATION_V2`에서만 재개한다.

P8은 위 재개 조건에 따라 시작한다. P7-B는 instrumentation 정합성 검증에만 사용하고
질문·Ground Truth·결과를 P8 fresh dataset 설계나 검색 tuning에 사용하지 않는다.

P9는 frozen P8.1을 변경하지 않고 eligibility만 수정했다. Judge Recall@5/selected
87.5%, Negative FPR 0%, Stress Recall@5/selected 100%, Negative FPR 0%이며 Dense
Recall과 owner/ACTIVE isolation은 유지됐다. 남은 주요 단계는 localization이다.

## P15 PDF Document Confirmation UX

### 사용자 시나리오

검색 결과가 PDF page evidence를 가리킬 때 사용자는 카드의 `문서에서 보기`를 눌러,
자신이 업로드한 원본 PDF의 같은 page를 확인할 수 있다. 결과가 없는 검색에는 버튼이
나타나지 않는다.

### 범위와 보존 계약

- 기존 `GET /api/documents/{documentId}/versions/{versionId}/original`와 Blob URL iframe
  viewer를 재사용한다. 이 endpoint는 현재 사용자 owner와 document/version을 함께
  검증하며, 서버 저장 경로를 응답에 노출하지 않는다.
- search response의 `documentId`, `documentVersionId`, `evidenceSourceType`,
  `evidenceSourceIndex`만 사용한다. PDF `PAGE`의 `sourceIndex`는 ingestion의 1-based
  page number다.
- iframe URL에 `#page=N&zoom=page-width` fragment를 붙여 브라우저 PDF viewer의 page
  이동을 사용한다.
- SearchService, retrieval, eligibility, ranking, threshold, consolidation, fallback,
  ingestion, chunking, embedding, database schema, migration, API response와 검색
  result ID·순서·개수·snippet·주변 내용은 변경하지 않는다.
- PDF.js text layer가 없으므로 snippet highlight, bounding box 저장, OCR, 좌표 schema와
  별도 PDF processing pipeline은 이번 범위에서 제외한다. native viewer의 `search` fragment는
  highlight 성공 계약으로 취급하지 않는다.

### acceptance criteria

1. PDF `PAGE` evidence 결과에만 `문서에서 보기`가 표시된다.
2. 클릭하면 인증된 Blob PDF가 열리고 `evidenceSourceIndex` page로 이동한다.
3. 원본 조회가 401/403이면 기존 세션 만료 동작을 유지하고, 다른 owner의 document/version은
   기존 backend authorization에서 열리지 않는다.
4. highlight를 지원하지 않아도 정확한 page 표시가 실패하지 않는다.
5. 검색 결과의 ID·순서·개수·찾은 내용·주변 내용이 전후 동일함을 frontend regression으로
   확인한다.
6. frontend lint/build/unit, 관련 backend authorization test, 브라우저 확인, `git diff --check`
   를 실행한다.

## P6 Retrieval Architecture Shadow Benchmark

P6는 현재 production 검색을 바꾸지 않고 같은 owner의 ACTIVE corpus에서 다음 네 mode를
shadow로 비교한다.

- `D0`: 현재 bge-m3 dense Top20과 P1~P4/P3 순차 fallback을 포함한 production 기준선
- `L1`: PRZ-008 P13의 PostgreSQL `simple` FTS lexical Top20만 사용하는 평가 채널
- `H1`: Dense Top20 + lexical Top20을 chunk ID로 합치고 `k=60` RRF로 정렬하는 평가 채널
- `H2`: H1 뒤에 benchmark 기술명 hardcoding이 없는 Generic Literal Evidence Gate를 적용

P6-A에서 D0/L1/H1을 먼저 구현·실행·기록하고, H2 구현 전에 24~32개 Identifier Stress
Set과 ground truth를 실제 ACTIVE 문서에서 확인하여 SHA-256으로 동결한다. P6-B는 동결
후 H2만 추가하고 stress 입력을 수정하지 않는다.

### P6 범위

- 기존 72-query development set은 regression dataset으로 사용하고 ground truth를 바꾸지 않는다.
- P5 48-query set은 결과가 공개된 diagnostic/regression dataset으로만 사용한다.
- Q0 original query에서 채널별 Top20과 Candidate Recall@20을 먼저 분리 측정한다.
- end-to-end shadow는 현재 P3의 Q0 → 필요 시 Q1 → 필요 시 Q2 순차 실행과 early-stop을
  유지하며 각 variant의 실행 채널을 기록한다.
- PRZ-008 P13의 lexical SQL, owner/ACTIVE 조건, lexical normalization과 RRF를 재사용한다.
- PRZ-008의 `배포`, Kubernetes, Kafka 알려진 문제와 P5 false positive 3건을 별도 regression
  guard로 기록한다.
- 각 mode의 Top1, Recall@3, Recall@5, MRR@5, Negative FPR, Candidate Recall@20과
  avg/median/P95 latency를 기록한다.

### Generic Literal Evidence Gate 계약

- strong anchor 후보는 숫자+단위, 구체적인 영문 multi-token phrase, CamelCase, ALL CAPS
  acronym, 하이픈·슬래시·코드 스타일 identifier로 제한한다.
- API, 서비스, 개발, 구현, 경험, 백엔드, 서버, 문제, 처리, 운영, 데이터, 사용자 같은
  일반 단어와 일반 영단어 전체는 hard gate로 만들지 않는다.
- 정규화는 Unicode NFKC, lowercase, 앞뒤·중복·안전한 구두점 공백, 보수적 하이픈/공백
  표현과 이미 검증된 한국어 조사 제거만 허용한다. synonym·의미 추론·기술명 변환·solution
  injection은 금지한다.
- corpus rarity/document frequency는 diagnostic으로만 기록하고 gate 조건으로 쓰지 않는다.
- 여러 strong anchor가 있으면 모두 확인한다. 같은 chunk일 필요는 없지만 같은 owner·document·
  ACTIVE version에서 candidate와 현재 Evidence Expansion이 연결한 bounded evidence 안에 있어야 한다.
- 각 candidate에 extracted/normalized/type/found-in-candidate/found-in-expanded/missing/
  PASS-or-REJECT/reason을 기록한다. 이 정보는 production API에 노출하지 않는다.
- H2가 선택한 Dense candidate의 기존 score/distance는 보존하고 RRF 값은 diagnostic으로만 둔다.

### P6 보존·비범위

- `src/main`, production repository/SQL/API model/runtime 설정/Flyway/DB schema/index 변경은 0건이다.
- SearchService, NaturalLanguageQueryFallback, CompositeSearchProfile, EvidenceQualityReranker,
  EvidenceExpansionService, SearchSnippetGenerator와 P3 production 동작을 수정하지 않는다.
- BGE-M3 sparse, sparse schema/server, threshold·embedding·chunking 변경, cross-encoder, 새 reranker,
  LLM rewrite, synonym, 새 P3 variant/early-stop, section-aware evidence, Evidence Ledger는 제외한다.
- owner·ACTIVE isolation과 API ID/score/distance 계약을 그대로 보존한다.
- P6 결과가 좋아도 production 적용, P7, 새 final holdout, commit·push·PR을 수행하지 않는다.

### P6 acceptance criteria

1. production 검색 source hash와 파일 수가 P6 전후 동일하고 production 변경 파일이 0개다.
2. P6-A D0/L1/H1의 Q0 채널·Candidate Recall@20·최종 품질·FPR·latency가 기록된다.
3. H2 구현 전에 24~32개 positive/negative stress set, 직접 확인 ground truth와 hash가 동결된다.
4. P6-B H2의 anchor diagnostics, H1 대비 recall retention과 false-positive 차단이 기록된다.
5. 72 development, P5 diagnostic, frozen stress, legacy/P5/numeric/positive identifier guard를 실행한다.
6. owner/ACTIVE isolation, P3 sequential behavior와 API ID/score/distance 불변을 실행 근거로 확인한다.
7. 전체 backend regression과 `git diff --check`가 통과하고 AUDIT blocking finding이 0건이다.
8. 결과를 `GO_FOR_PRODUCTION_DESIGN`, `NEEDS_IMPROVEMENT`, `NO_GO` 중 하나로만 판정한다.

### P6 결과

P6는 2026-08-14에 `DONE — NO_GO`로 종료했다. H1은 D0보다 Candidate Recall@20을
개선하지 못했고, H2는 frozen stress Negative FPR 0%와 P5 false positive 3/3 차단에는
성공했지만 기존 72-query D0 PASS를 5건 회귀시키고 positive Nginx guard도 막았다. 전체
`integrationTest`의 기존 production 검색 contract 실패 1건도 재현됐다. Production search
source 30개의 P6 전후 hash는 동일했고 production 적용, P7, 새 final holdout은 시작하지 않았다.
상세 근거는 [P6 evidence](p6-retrieval-shadow/evidence.md)에 있다.

## GPT-J1 Evidence Judge Shadow Spike

GPT-J1은 검색 알고리즘을 더 확장하는 P7이 아니라, 현재 P4가 사용하는 owner-scoped
ACTIVE dense 후보 중 상위 10개에 자연어 evidence 판정만 추가하는 작은 shadow 실험이다.
Production 요청 경로와 응답에는 연결하지 않는다. 상세 계약과 결과는
[GPT-J1 Spec](gpt-evidence-judge-shadow/spec.md)과
[Evidence](gpt-evidence-judge-shadow/evidence.md)에서 관리한다.

## 보존 계약

각 Phase는 기존 owner·ACTIVE version 격리, bge-m3, pgvector, threshold `0.50`,
Top20, max5, P4, P18, PRZ-012 Evidence Presentation/Expansion 계약을 명시적으로
보존하거나 별도 검증한다. Phase 간 검색 결과와 API 계약을 임의로 바꾸지 않는다.

## 관리 구조 정리 기록

2026-08-14에 당시 branch-local Search Benchmark V2(`PRZ-014`), Numeric + Strong
Identifier(`PRZ-015`), Evidence-aware Reranking(`PRZ-016`) 작업 문서를 하나의 상위
Search Performance V2 Phase로 이동했다. 이후 최신 `main`에서 공식 Registry의
`PRZ-013`~`PRZ-015`가 각각 OpenProxy, OpenHA, MCP에 사용된 사실을 확인하여 2026-08-16에
이 상위 Spec을 다음 빈 ID인 `PRZ-016`으로 재번호화했다. frozen JSON과 raw result 안의
기존 `PRZ-013-P5/P6`, `PRZ-014` benchmark 라벨은 당시 산출물의 해시와 역사 보존을 위해
바꾸지 않는다. 이 정리는 과거 측정값·시점·구현 사실을 소급 변경하지 않는다.
