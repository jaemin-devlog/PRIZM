# PRZ-013: Search Performance V2

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
| P2 Evidence-aware Lightweight Reranking | `DONE` | 기존 candidate 내부 deterministic evidence-quality reranking | 초기 `PRZ-016` 문서 |
| P3 Query Understanding | `DONE` | 자연어 fallback, 보수적 semantic alias와 최대 2개 limited multi-query dense retrieval | 초기 `PRZ-013` 문서 |
| P4 Evidence Localization | `DONE` | 맞는 문서의 상세 근거 page/chunk 정확도 개선 | — |
| P5 Final Holdout Validation | `DONE — FAIL` | 별도 unseen holdout으로 과적합 여부와 최종 성능 확인 | — |
| P6 Retrieval Architecture Shadow Benchmark | `DONE — NO_GO` | D0/L1/H1/H2 평가 전용 비교, frozen identifier stress set, regression·격리·계약 검증 | PRZ-008 P13/P14 |

P4는 focused 검증과 동일 72-query benchmark를 통과했다. P5 평가는 완료했지만 sealed
48-query holdout에서 Negative FPR 25%와 검색 품질 기준 미달이 확인되어 최종 판정은
`FAIL`이다. production 검색 코드는 수정하지 않았고 Search Performance V2는 동결하지 않는다.

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

## 보존 계약

각 Phase는 기존 owner·ACTIVE version 격리, bge-m3, pgvector, threshold `0.50`,
Top20, max5, P4, P18, PRZ-012 Evidence Presentation/Expansion 계약을 명시적으로
보존하거나 별도 검증한다. Phase 간 검색 결과와 API 계약을 임의로 바꾸지 않는다.

## 관리 구조 정리 기록

2026-08-14에 Search Benchmark V2(`PRZ-014`), Numeric + Strong Identifier
(`PRZ-015`), Evidence-aware Reranking(`PRZ-016`)의 작업 문서를 이 상위 PRZ의
Phase로 이동했다. 이는 문서 관리 단위를 정리한 것이며 과거 측정값·시점·구현 사실을
소급 변경하지 않는다. `PRZ-014`에 OpenHA Topology Gate 문서가 있었다는 근거는 현재
Registry와 `specs/`에서 확인되지 않았다.
