# PRZ-016 Search Performance V2 — R&D History

- 문서 상태: `OFFICIAL R&D HISTORY — DOCUMENTATION ONLY`
- 정리 기준일: 2026-08-17
- 허가 범위: documentation-only `ORIENT → reduced IMPLEMENT → VERIFY/AUDIT`
- 원칙: 실행 artifact가 spec·계획 문서보다 우선하며, Shadow 결과는 Production 적용으로 간주하지 않는다.
- 범위: PRZ-008에서 확인한 선행 검색 실험과 PRZ-016 P0–P7 및 후속 evidence-verification Shadow 연구

## 1. 목적

PRIZM 검색의 목표는 사용자의 이력서·포트폴리오·경력 문서에 **실제로 존재하는 경험 근거만** 찾아, 소유자와 현재 활성 문서 버전의 출처와 함께 제시하는 것이다. 관련 단어가 있다는 이유만으로 없는 경험을 근거로 인정하거나, 원문에 없는 경험·기술·성과·숫자를 생성해서는 안 된다.

성능 목표는 한 사용자의 익숙한 문서와 개발 질문에만 맞는 검색이 아니다. 새로운 사용자가 새로운 문서를 등록하고 처음 보는 질문을 했을 때도 근거 검색과 무근거 거절이 함께 일반화되어야 한다. 따라서 이 기록은 성능 상승뿐 아니라 holdout 실패, false positive, positive regression, incomplete run과 `NO_GO` 판단까지 그대로 보존한다.

### 판정 용어

| 상태 | 의미 |
|---|---|
| `ADOPTED` | 현재 Production 기본 경로에 source로 존재한다. |
| `PASS` | 해당 단계의 사전 Gate를 통과했다. 이후 일반화까지 자동 보장하지 않는다. |
| `PASS_COMPONENT_ONLY` | 격리된 component Gate만 통과했다. Production·end-to-end 채택 근거가 아니다. |
| `SHADOW_ONLY` | Production 응답을 바꾸지 않는 평가 전용 구현·실행이다. |
| `FAIL` | 사전에 정한 단계 Gate를 통과하지 못했다. |
| `NO_GO` / `REJECTED` | 해당 구조를 Production 후보로 올리지 않고 연구를 종료하거나 재설계한다. |
| `OBSERVED` | 별도 Gate 없이 측정값만 기록한다. |

이 문서가 추적하는 판정 단위는 **36개 주요 R&D milestone**이다. P0–P6 7개, GPT-J1 1개, P7 v1/v2 freeze와 P7-B 3개, rejection baseline·v1·v2·adversarial·veto 5개, NLI/numeric 4개, P7 C0/C1/C2/localizer 4개, Qwen v1/v2/v3/v3+numeric 4개, candidate/result-level과 stage audit 3개, PRZ-008 선행 chunking family·exact rescue·FTS/RRF·Sparse·reranker 5개를 센 수다. 단순 smoke·syntax check와 P8 reference baseline은 제외했다.

### 문서 상태 정합성

상위 [`spec.md`](spec.md), [`evidence.md`](evidence.md)와 Registry는 P7-B 48/48 실행,
`P7-B FAIL`, `DEFERRED / PRZ_016_STATE_FROZEN` 상태로 정리했다. P7-A v2 manifest의
`p7bStatus: NOT_STARTED`는 실행 전 동결된 역사적 입력이므로 수정하지 않는다.

## 2. 검색 시스템 시작점

역사적 출발점은 질문과 문서 chunk를 같은 임베딩 공간에 놓고 가까운 chunk를 고르는 단순 Dense Retrieval이었다.

```text
query
→ Ollama bge-m3 embedding (1024 dimensions)
→ PostgreSQL pgvector exact cosine distance (<=>)
→ owner + documents.active_version_id + version ACTIVE scope
→ Top-K candidates
→ source/evidence response
```

현재 source도 이 축을 유지한다. [`SearchService.java`](../../src/main/java/com/prizm/search/service/SearchService.java)는 Ollama embedding을 검증한 뒤 검색 profile을 실행하고, [`VectorSearchRepository.java`](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java)는 owner와 현재 `active_version_id`, `ACTIVE` 상태를 동시에 제한한다. 기본 profile은 Dense Top20, score floor `0.50`, 최종 최대 5건이며 pgvector ANN index가 아닌 exact cosine 정렬을 사용한다. 모델은 [`application.yml`](../../src/main/resources/application.yml)의 `bge-m3`, 차원은 1024다.

이 단순 축 위에 숫자·identifier 안전장치, 제한적 exact-token rescue, evidence-quality reranking, query fallback, evidence localization과 snippet 표현 계층이 순차적으로 쌓였다. P7-A v2가 기록한 Production search source 30개 aggregate SHA-256은 `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`이며, P7-B 전후에도 같았다.

## 3. PRZ-016 Baseline

P0는 이후 모든 P1–P4 비교의 고정 개발 기준선이었다. 데이터셋은 72문항(Positive 56, Negative 16)이며 결과 확인 뒤 ground truth를 바꾸지 않았다.

| Metric | P0 |
|---|---:|
| Top1 | 57.14% |
| Recall@3 | 66.07% |
| Recall@5 | 67.86% |
| MRR@5 | 0.6146 |
| Negative FPR | 6.25% |
| PASS / FAIL | 47 / 25 |
| Warm average / P95 | 291.675ms / 341.154ms |

실패 25건은 ranking 6, numeric/identifier 5, query understanding 5, candidate recall 4, evidence localization 4, false positive 1로 분류됐다. 즉 단순 Dense score 순서만으로는 정확한 수치·식별자, 질문 표현 변형, 정답 근거 위치를 모두 해결할 수 없었다.

- Artifact: [`p0-benchmark/baseline-results.json`](p0-benchmark/baseline-results.json)
- SHA-256: `a8048a2a5d5f5d9952b86061cedf590b671235d5c4a152b2618879ec8b94f35d`
- 판정: `BASELINE FROZEN`
- Production 적용: 비교 기준이며 별도 신규 기능 적용 없음

## 4. Numeric + Strong Identifier

P1의 가설은 숫자·단위와 강한 식별자는 일반 semantic similarity보다 exact consistency가 우선해야 한다는 것이었다. 정상 Dense 선택이 비었을 때 owner의 ACTIVE corpus에서 숫자 anchor 후보를 제한적으로 복구하고, 숫자+단위를 같은 문맥에서 확인했다. 강한 identifier는 owner의 전체 ACTIVE corpus에 실제 존재하는지도 확인해 near miss와 기술명 환각을 차단했다.

대표 guard는 `2,329` 대 `2,330`, `4,400`, `675`, `1,654`처럼 한 자리 차이가 의미를 바꾸는 사례였다. Focused 검증에서 기존 numeric 실패 5/5가 근거를 찾았고, `4,401회`, `676건`, `2,330행` near miss 3/3은 결과 없음으로 유지됐다.

| Metric | P0 | P1 |
|---|---:|---:|
| Top1 | 57.14% | 60.71% |
| Recall@5 | 67.86% | 71.43% |
| MRR@5 | 0.6146 | 0.6503 |
| Negative FPR | 6.25% | 0% |
| FAIL | 25 | 22 |

신규 regression은 0건이었다. D03·D07과 GraphQL negative가 PASS로 바뀌었고, 정답 위치가 frozen GT와 달랐던 일부 numeric 사례는 실패로 남겼다.

- Artifact: [`p1-numeric-identifier/benchmark-results.json`](p1-numeric-identifier/benchmark-results.json)
- SHA-256: `cee543d2602f42ec2c742c411ac47a2f996df5abee308c78ad393d08366d2f9b`
- 판정: `PASS`
- Production 적용: `ADOPTED` — bounded numeric rescue/context match와 strong-identifier guard

## 5. Evidence-Aware Reranking

P2는 Dense similarity를 버리지 않고, 이미 회수된 후보 안에서 실제 수행·문제·결과와 query anchor가 더 잘 결속된 근거에 작은 deterministic boost를 주었다. 핵심은 새 후보 생성이 아니라 **같은 candidate pool의 순서 개선**이었다.

Focused ranking 실패 6건 중 Spring Boot, 동시성, 외부 호출 대기, Redis+DB lock 4건이 Top1으로 전환됐다. TourAPI와 일반 동시성 질문은 후보를 잃지 않은 채 rank 2·3으로 남았다. 기존 positive 13/13, negative 7/7, numeric/identifier guard를 유지했다.

| Metric | P1 | P2 |
|---|---:|---:|
| Top1 | 60.71% | 67.86% |
| Recall@5 | 71.43% | 71.43% |
| MRR@5 | 0.6503 | 0.6935 |
| Negative FPR | 0% | 0% |
| Ranking FAIL | 6 | 2 |

- Artifact: [`p2-evidence-reranking/benchmark-results.json`](p2-evidence-reranking/benchmark-results.json)
- SHA-256: `a32df4e81eb0f03cc4b7c066b86f95f1e3ac349c7c3127ac8205c0dc983178f8`
- 판정: `PASS`
- Production 적용: `ADOPTED` — Dense를 주 신호로 보존하는 bounded evidence-quality reranking

## 6. Query Understanding

P3는 표면 keyword 하나를 늘리는 대신, 원 query가 실패했을 때만 표기·조사·질문형 표현을 보수적으로 정규화한 variant를 최대 2개 순차 실행했다. variant는 원 query의 strong identifier와 numeric anchor를 보존하며 새 기술이나 해법을 주입하지 않는다. candidate는 chunk ID로 병합하고 원래 Dense score/distance를 유지한다.

`AirConnect에서 뭐했어?`, Spring Boot 표기 변형, Redis 사용 이유, 배포·동시성 질문 같은 실제 문서 focused 검증을 통과했다. 개발 benchmark에서는 A04·B14·C08·E04가 새 PASS가 됐고 기존 PASS regression은 0건이었다. 72건 중 7건에서 fallback을 실행해 평균 embedding 호출은 1.1528회였지만, 순차 variant 때문에 warm P95는 280.405ms에서 600.227ms로 늘었다.

| Metric | P2 | P3 |
|---|---:|---:|
| Top1 | 67.86% | 75.00% |
| Recall@3 / @5 | 71.43% | 78.57% |
| MRR@5 | 0.6935 | 0.7649 |
| Negative FPR | 0% | 0% |

- Artifact: [`p3-query-understanding/benchmark-results.json`](p3-query-understanding/benchmark-results.json)
- SHA-256: `9a561d58d89bc234ca34bd0197feec9e369e1c6fe33df8cdb49a6240076846a4`
- 판정: `PASS`
- Production 적용: `ADOPTED` — 원 query 우선, 실패 시 limited sequential multi-query

## 7. Evidence Localization

P4는 검색 result identity와 순위를 바꾸지 않고, 선택된 chunk가 질문에 충분한 직접 근거를 주지 못할 때만 같은 owner·document·ACTIVE version 안의 bounded evidence를 비교했다. query token coverage, 인접 phrase, exact number, strong anchor, 서술형 수행 문맥을 사용해 snippet과 evidence source를 선택했다.

이 구조는 PRZ-012 Evidence Presentation과 같은 경계를 공유한다. [`PRZ-012 spec`](../PRZ-012-search-evidence-presentation/spec.md)은 최종 결과의 `content`에서 원문 1–3문장을 추출하되 result ID·순서·score·distance는 바꾸지 않는 표현 계약이다. P4는 이 표현 계층을 동일 ACTIVE version의 근거 위치화까지 확장했다.

| Metric | P3 | P4 |
|---|---:|---:|
| Top1 | 75.00% | 82.14% |
| Recall@3 / @5 | 78.57% | 85.71% |
| MRR@5 | 0.7649 | 0.8363 |
| Negative FPR | 0% | 0% |
| Evidence Localization FAIL | 5 | 1 |

신규 PASS는 B02·B04·B12·C06, 신규 FAIL은 0건이었다. latency는 warm average `296.972ms`, median `256.445ms`, P95 `729.198ms`, max `776.127ms`였다. 이 시점의 개발 평가셋 성능은 높았지만, 다음 holdout이 이 결과의 일반화 한계를 드러냈다.

- Artifact: [`p4-evidence-localization/benchmark-results.json`](p4-evidence-localization/benchmark-results.json)
- SHA-256: `64bf9229e73e25b8aa9befcdd54556f746fb9e3da5755a900302042364d900b3`
- 판정: `PASS`
- Production 적용: `ADOPTED` — same-owner/same-document/ACTIVE-version bounded evidence expansion과 extractive snippet

## 8. Holdout Generalization Failure

P5는 개발 질문을 더 튜닝하지 않고, freeze한 unseen 48문항(Positive 36, Negative 12)에서 P4를 검증했다. 기존 development query와 exact·near duplicate가 없고, Positive anchor와 Negative 부재를 검색 전에 확인했다.

| Metric | P4 development | P5 holdout | Delta |
|---|---:|---:|---:|
| Top1 | 82.14% | 50.00% | -32.14pp |
| Recall@3 | 85.71% | 61.11% | -24.60pp |
| Recall@5 | 85.71% | 61.11% | -24.60pp |
| MRR@5 | 0.8363 | 0.5509 | -0.2854 |
| Negative FPR | 0% | 25.00% | +25.00pp |
| PASS / FAIL | 62 / 10 | 31 / 17 | — |

수동 taxonomy는 evidence localization 7, candidate recall 5, numeric/identifier 1, false positive 3, other 1이었다. OpenTelemetry/Zipkin, AWS RDS Multi-AZ, Spring WebFlux처럼 ACTIVE corpus에 없는 strong identifier가 semantic Dense score `0.50`을 넘은 것이 안전성 실패의 대표 사례였다. owner와 ACTIVE isolation은 모두 PASS했다.

P5는 개발 평가셋의 82.14%가 unseen 질문에 자동으로 일반화되지 않음을 증명했다. 결과를 본 뒤 threshold·GT·query를 바꾸거나 실패를 고치기 위한 Production 튜닝을 하지 않았고, `DONE — FAIL`로 보존했다.

- Artifact: [`p5-final-holdout/holdout-results.json`](p5-final-holdout/holdout-results.json)
- SHA-256: `5c1e57e5950fc4fa060bcc3c756ec5a000b7a2db10aa03e68fc3c271b53cf752`
- Frozen dataset / GT SHA-256: `4e28c0fb2b99b31f15640eb39776e04a533938b63db02e1ba0bfec22168532aa` / `da915300974c27967e859c0586ec1f76347c314c62f0ca57f77b5b64c3e0180d`
- 판정: `FAIL`
- Production 적용: P5에서 변경 0

## 9. Hybrid Search Experiments

### 선행 PRZ-008 실험

[`PRZ-008 evaluation comparison`](../PRZ-008-search-evidence-reliability/evaluation-comparison.md)(SHA-256 `69a7a2d61543e1cd402b14793ac878c9be05e23dec20630e18e6976a583fd530`)에는 동일 실문서 40문항의 retrieval 대안이 보존돼 있다.

Hybrid에 앞서 candidate 단위 자체를 바꾸는 P9–P11과 좁은 rescue P12를 비교했다.

| 방식 | Top1 | Recall@5 | MRR@5 | 없는 경험 오탐 | 판단 |
|---|---:|---:|---:|---:|---|
| P9 section-paragraph-v1 | 73.53% | 79.41% | 0.7647 | 2/6, 2 results | 비권고 |
| P10 Production chunking + deferred dedup | 73.53% | 79.41% | 0.7647 | 1/6, 1 result | 비권고 |
| P10 section-v1 + deferred dedup | 76.47% | 82.35% | 0.7892 | 2/6, 2 results | 비권고 |
| P11 section-paragraph-v2 | 73.53% | 76.47% | 0.7500 | 2/6, 2 results | 비권고 |
| P12 exact-token rescue | 82.35% | 85.29% | 0.8382 | 1/6, 1 result | P17/P18 추가 검증 후 Production 채택 |

Section chunking은 이메일/Kakao·알림 같은 일부 문항을 복구했지만 짧은 조각과 문맥 손실, Kafka 오탐, 새 순위 회귀를 만들었다. Deferred page dedup도 일부 recall을 높였지만 FPR 또는 Top1을 보존하지 못했다. 반면 P12는 기존 결과가 비어 있는 2–4자 단일 GENERAL token, 본문 exact token 일치, 원 score `0.49 <= score < 0.50`, 최대 1건이라는 좁은 조건으로 알림·동시성만 복구했다. P17의 추가 28문항과 P18 Product parity 뒤 이 rescue만 Production에 채택됐다.

| 방식 | Top1 | Recall@5 | MRR@5 | 없는 경험 오탐 | 판단 |
|---|---:|---:|---:|---:|---|
| Dense Production baseline | 76.47% | 79.41% | 0.7794 | 1/6, 1 result | 기준선 |
| PostgreSQL FTS + Dense + RRF | 82.35% | 85.29% | 0.8382 | 1/6, 1 result | 보류 후 비채택 |
| BGE-M3 Sparse + Dense + RRF | 79.41% | 88.24% | 0.8333 | 3/6, 15 results | `REJECTED` |
| Sparse 후보 + BGE reranker | 70.59% | 88.24% | 0.7892 | 3/6, 15 results | `REJECTED` |

PostgreSQL `simple` FTS는 lexical hit가 5/40에 그쳤고 긴 한국어·영문 혼합 query를 거의 돕지 못했다. BGE-M3 Sparse는 Recall을 높였지만 낮은 subword overlap도 독립 후보가 되어 Kubernetes·Kafka·결제 같은 오탐을 크게 늘렸다. reranker는 후보 순서만 바꾸므로 이 eligibility 문제를 해결하지 못하고 Top1을 더 낮췄다.

### P6 retrieval shadow

P6는 현재 P0–P4 경로를 D0로 두고 L1 PostgreSQL FTS, H1 `k=60` RRF, H2 generic literal gate를 동일 corpus에서 Shadow 비교했다. 18개 chunk corpus에서 Dense Top20 candidate recall은 development·P5·stress 모두 100%였고 lexical-only 후보는 0이었다. 따라서 H1은 D0에 새 후보를 추가하거나 final rank를 개선하지 못했다.

| P5 diagnostic | D0 Dense | L1 FTS | H1 RRF | H2 literal gate |
|---|---:|---:|---:|---:|
| Top1 | 50.00% | 0% | 50.00% | 47.22% |
| Recall@5 | 61.11% | 0% | 61.11% | 58.33% |
| MRR@5 | 0.5509 | 0 | 0.5509 | 0.5231 |
| Negative FPR | 25.00% | 0% | 25.00% | 0% |

H2는 frozen stress의 Negative FPR을 5/14에서 0/14로 낮추고 P5 false positive 3/3을 막았지만, development D0 PASS 5건과 positive Nginx guard 1건을 회귀시켰다. Recall 이득이 없는 상태에서 literal safety gate가 positive를 잃었으므로 구조 전체를 `NO_GO`로 종료했다.

- Artifact: [`p6-retrieval-shadow/p6-b-results.json`](p6-retrieval-shadow/p6-b-results.json)
- SHA-256: `a5db336c91204034f3fd8aad88febaeb087eb07bad0d7281852ded291f508504`
- 판정: `DONE — NO_GO`
- Production 적용: 0; P6 전후 source aggregate 동일

## 10. GPT Evidence Judge

GPT-J1은 P4 결과 뒤에 OpenAI Responses API 기반 evidence judge를 Shadow로 붙였다.

```text
P4 result / owner-scoped ACTIVE Dense Top10
→ GPT evidence choice
→ selected chunk ID 검증
→ owner, active_version_id, ACTIVE, DB 원문 exact substring 재검증
```

48건을 시도해 44 calls가 완료되고 4건은 `max_output_tokens`로 incomplete였다. 완료 결과를 포함한 diagnostic lower bound는 Top1·Recall@3·Recall@5 `66.67%`, Negative FPR `0/12`였다. 그러나 정상 완료 Positive regression 2건(H15, H31)과 incomplete 4건(H13, H33, H34, H35)이 있어 `positive regression 0`, `judge error 0` Gate를 모두 실패했다. Positive 24/36=`66.67%`는 incomplete를 miss로 센 값이므로 완전한 48-query 성능으로 과장하지 않는다.

- Artifact: [`gpt-evidence-judge-shadow/evidence.md`](gpt-evidence-judge-shadow/evidence.md)
- Artifact SHA-256: `1bcaa88b4d7dd41987790c39347e5847869c7352139eb7bd5ee0639bd290e420`
- Local raw SHA-256: `6c164e19696af5e067e04d5320bf47610e0bf2ef12549bf8b5d9feeb1dd43533` (gitignored)
- 판정: `DONE — NO_GO`
- Production 적용: 0

## 11. P7 Cross-User Generalization

P7-A v1은 4-user synthetic holdout을 먼저 freeze했지만 PDF가 1페이지 요약 카드 수준이라 실제 문서 밀도가 부족했다. 검색 전에 `PRESERVED — SUPERSEDED_BEFORE_RUN` 처리하고 사용하지 않았다. v2는 서로 다른 신규 도메인 네 종류를 구성했다.

- construction dispatch
- satellite imagery pipeline
- railway permit simulator
- music copyright catalog

사용자 4명, owner별 ACTIVE 문서 2개(PDF 이력서 1 + TXT 포트폴리오 1), 총 8문서와 질문 48개(Positive 36, Negative 12)를 만들었다. SYN2-U03 inactive V0 fixture는 과거 version으로만 남기고 ACTIVE V1 pointer로 교체했다.

### P7-A v2 freeze

| Frozen item | SHA-256 |
|---|---|
| Active corpus aggregate | `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498` |
| Questions | `85c2e41bba5c293ca5172b48f77f41587d49be996252479ce5a71ed17763b868` |
| Ground truth | `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680` |
| Freeze manifest | `539ebae29e4f111257a453893abb0eda78bcf922b525fa3d3c8a79ae0d8cca5f` |
| Production search aggregate | `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31` |

Ground truth는 corpus/query freeze 뒤 작성·검증했고 Positive anchor 67개와 Negative active-corpus 부재 12개를 확인했다. v1 27 assets와 v2 31 assets 모두 실행 전후 hash mismatch 0이었다.

### P7-B independent result

| Metric | P7-B |
|---|---:|
| Top1 | 12/36 = 33.33% |
| Recall@3 | 21/36 = 58.33% |
| Recall@5 | 21/36 = 58.33% |
| MRR@5 | 0.4491 |
| Negative FPR | 5/12 = 41.67% |
| PASS / FAIL | 28 / 20 |

Owner isolation, ACTIVE pointer isolation, V0 exclusion은 모두 PASS했다. 실패 taxonomy는 candidate recall 7, evidence localization 5, numeric/identifier 3, false positive 5였다. P7-B 전용 Positive quality Gate는 문서화돼 있지 않아 Positive 수치는 `OBSERVED`로 남겼지만, 기존 Mandatory Gate인 Negative false positive 0은 명백히 실패했다.

| 일반화 단계 | Top1 | Negative FPR |
|---|---:|---:|
| 내 문서 + 개발 질문(P4) | 82.14% | 0% |
| 내 문서 + unseen 질문(P5) | 50.00% | 25.00% |
| unseen 사용자 + unseen 문서 + unseen 질문(P7-B) | 33.33% | 41.67% |

이 세 칸은 같은 수치의 단순 과적합 지표는 아니지만, 문서·사용자·질문이 새로워질수록 Top1이 하락하고 false positive가 증가하는 일반화 실패를 분명히 보여 준다.

- Artifact: [`p7-b-independent-generalization/evaluated-results.json`](p7-b-independent-generalization/evaluated-results.json)
- SHA-256: `a1b85aafcf516c2370724e7a7c311619da4692269e2b0971f54da88b9de696b8`
- Raw SHA-256: `defc5e35dbf26f48a640f3df673e2247c14437b0cb65e8c8c05a0bd3b6e2cb2e`
- 판정: `P7-B FAIL`
- Production 적용: 0

## 12. Rule-Based Rejection Experiments

P7-B false positive를 직접 튜닝하지 않고 별도 24-case rejection set을 만들었다. baseline은 Positive 3/8, Negative false positive 5/16이었다. false positive 유형은 비교 후 미채택 1, 과거 prototype/현재 미사용 2, numeric near miss 2였다.

`EvidenceClaimEvaluator` v1/v2는 target/action 결속, polarity, adoption/currentness와 numeric consistency를 regex·substring 중심으로 검사했다. tuning에서는 baseline-correct Positive 3/3 보존과 FP 5/5 차단, 두 번째 tuning에서는 Positive 8/8·Negative 16/16까지 맞췄다. 그러나 adversarial Positive에서는 `검토 후 실제 도입`, `계획 후 완료`, `prototype → production`, `중단 없이 운영`, `폐기물`, 다른 기술의 deprecated 상태처럼 같은 단어가 반대 상태 전이에 쓰이는 자연스러운 문장을 과도하게 거절했다. 당시 확정 실행 기록의 adversarial Positive acceptance는 1/10으로 Gate 실패였다. Repository에는 frozen dataset·GT·runner는 남아 있지만 이 v2 실행의 standalone result JSON은 보존돼 있지 않으므로, 이 수치는 JSON artifact가 아니라 단계 종료 기록으로 구분한다.

이를 완화한 `EvidenceContradictionVetoEvaluator` v3는 명백한 contradiction만 막고 나머지는 `PASS_THROUGH`하도록 바꿨다. 보존된 focused XML 결과는 다음과 같다.

| Frozen set | Positive PASS_THROUGH | Negative VETO |
|---|---:|---:|
| tuning v1 | 3/3 | 4/5 |
| tuning v2 | 8/8 | 12/16 |
| adversarial | 10/10 | 6/6 |

강한 rule은 정상 Positive를 죽였고, 약한 veto는 tuning Negative를 통과시켰다. 특정 기술명·query ID·동의어 목록을 더 추가하는 방향은 일반화 설계가 아니라 dataset 적합화가 되므로 중단했다.

- Baseline artifact: [`rejection-tuning/baseline-summary.json`](rejection-tuning/baseline-summary.json), SHA-256 `25ac1e24c35a029909434885ecc4f7b4da17a9bd73eeb558eab80f48b94d8c6a`
- Unseen dataset SHA-256: `1f404a1e18a17c2d7234dc30338f6560ca72b21ba518ab732bb6142ae91617f0`
- Adversarial dataset SHA-256: `1475d0cc867dcc7eca2bbbdb8f3da242e0a8cbb1597d7f9026400078cd5e9c8f`
- Veto result artifact: `build/test-results/searchEvaluation/TEST-com.prizm.search.evaluation.ContradictionVetoSpikeTest.xml`, SHA-256 `6e642077c14de2dcfc634b82d783130395439901cf05e82e3e4c60d639ee2614`
- 판정: `RULE-BASED REJECTION FINAL = NO_GO`
- Production 적용: 0; source는 `src/searchEvaluation`에만 존재

## 13. NLI Semantic Verification

### 13.1 mDeBERTa

`MoritzLaurer/mDeBERTa-v3-base-mnli-xnli`를 premise=evidence, hypothesis=frozen declarative career claim으로 사용했다. entailment→`SUPPORT`, contradiction→`CONTRADICT`, neutral→`UNKNOWN`을 argmax로 매핑했고 threshold tuning은 하지 않았다.

| GT | SUPPORT | CONTRADICT | UNKNOWN |
|---|---:|---:|---:|
| SUPPORT 26 | 26 | 0 | 0 |
| CONTRADICT 38 | 2 | 36 | 0 |
| unrelated UNKNOWN 8 | 0 | 5 | 3 |

Positive SUPPORT는 26/26으로 강했고 Negative false SUPPORT 2건은 모두 numeric contradiction이었다. 반면 unrelated 8건 중 5건을 UNKNOWN이 아니라 CONTRADICT로 보아, strict NLI label 의미와 PRIZM의 evidence sufficiency 의미가 다름을 드러냈다. 평균 latency는 588.049ms, P95는 925.799ms였다.

- Artifact: [`semantic-nli-capability/results.json`](semantic-nli-capability/results.json)
- SHA-256: `87ae490643e95f3bba0b54a93bf29710ac6a3f49de1ba14ad0bdf6381746fa0c`
- 판정: raw NLI `OBSERVED`; 단독 Production judge는 비채택

### 13.2 Korean NLI

동일 frozen 113 pairs에서 `Huffon/klue-roberta-base-nli`를 mDeBERTa와 비교했다.

| Model | Overall | Group A | Group B | Core P7 SUPPORT | Avg / P95 latency |
|---|---:|---:|---:|---:|---:|
| mDeBERTa | 80.53% | 90.28% | 72.22% | 0/5 | 891.609 / 1,146.978ms |
| KLUE-RoBERTa | 87.61% | 91.67% | 91.67% | 0/5 | 62.929 / 81.199ms |

KLUE는 전체 accuracy와 CPU latency를 크게 개선했지만 핵심 5개 career-support 사례를 mDeBERTa와 똑같이 0/5로 놓쳤고, 기존 SUPPORT 하나를 CONTRADICT로 회귀시켰다. 모델 교체가 task mismatch를 해결하지 못했다.

- Artifact: [`p7-b-semantic-shadow/nli-model-comparison-evaluation.json`](p7-b-semantic-shadow/nli-model-comparison-evaluation.json)
- SHA-256: `eab0366dec8a3ab63126285a5e1ab43624c5321592bc0def4e116eafcd54b846`
- 판정: `KOREAN_NLI_CANDIDATE_NO_GO`
- Production 적용: 0

## 14. Semantic + Numeric Verification

NLI가 숫자 존재 자체를 semantic support로 오해하는 문제를 보완하기 위해 deterministic value+unit consistency verifier를 결합했다. capability 72 pairs에서 NLI의 numeric false SUPPORT 2건을 모두 차단해 Positive 26/26, CONTRADICT 38/38, unrelated false SUPPORT 0/8을 유지했다.

새 36-pair Fresh validation은 freeze 뒤 단 한 번 실행했고 다음 결과로 사전 Gate를 통과했다.

| Fresh GT | SUPPORT | CONTRADICT | UNKNOWN |
|---|---:|---:|---:|
| SUPPORT 14 | 14 | 0 | 0 |
| CONTRADICT 14 | 1 | 13 | 0 |
| UNKNOWN 8 | 0 | 8 | 0 |

이는 `PASS_COMPONENT_ONLY`였다. 실제 P7-B returned snippet 86개에 fail-closed로 적용하자 Negative FP는 5/12에서 1/12로 줄었지만 baseline-correct Positive 21개 중 7개만 남았다.

| P7-B metric | Baseline | Strict Semantic + Numeric C0 |
|---|---:|---:|
| Top1 | 33.33% | 13.89% |
| Recall@5 | 58.33% | 19.44% |
| MRR@5 | 0.4491 | 0.1667 |
| Negative FPR | 41.67% | 8.33% |
| Positive retained | 21/21 | 7/21 |

Numeric veto는 P7-B 86 pair에서 0건이었다. 남은 numeric FP `V2-U01-N03`은 hypothesis가 `340ms`라는 값·단위는 갖지만 uppercase metric token이 없어 verifier가 value comparison 전에 unresolved로 빠진 metric-binding miss였다.

- Fresh artifact: [`semantic-nli-fresh/numeric-results.json`](semantic-nli-fresh/numeric-results.json), SHA-256 `7f15097b6c354d8a8e64a4e59835a1e962b6d0a9d16b3d344a7c442f3db67207`
- P7 artifact: [`p7-b-semantic-shadow/evaluation.json`](p7-b-semantic-shadow/evaluation.json), SHA-256 `93b797f2c4b6c1f40a090614caa7bf5e52a6cf1b423dc9b32644921228fe1006`
- 판정: component `PASS`, P7 end-to-end `P7_B_SEMANTIC_SHADOW_FAIL`
- Production 적용: 0

## 15. Claim-Aware Content Localizer

C0 실패 14건의 다수는 정답 result `content` 안에 anchor가 있지만 Production snippet이 다른 문장을 골라 NLI premise가 빈약해진 문제였다. 단순히 더 긴 context(C1/C2)를 주는 실험은 Positive retained 8/21에 그쳐 모두 실패했다. 그래서 localizer는 다음 제한 안에서 claim-aware 후보를 만들었다.

- 같은 original result 안에서만 탐색
- 1–3 sentence windows
- deterministic preselection Top5
- chunk/document/version/result identity와 original rank 불변
- 다른 result·chunk·document로 이동하거나 새 evidence 생성 금지

| Metric | C0 | Claim-Aware |
|---|---:|---:|
| Top1 | 13.89% | 30.56% |
| Recall@5 | 19.44% | 38.89% |
| MRR@5 | 0.1667 | 0.3472 |
| Negative FPR | 1/12 | 1/12 |
| Baseline-correct Positive retained | 7/21 | 14/21 |

C0 regression 14건 중 7건을 복구했고, content-non-adjacent 유형은 5/7을 복구했다. 그러나 Positive regression 7건이 남아 Top1·Recall@5·retention Gate를 모두 실패했다. 남은 원인은 preselection miss뿐 아니라 NLI support miss, multi-sentence context, truncated content가 섞여 있었다. 즉 localization 개선은 유효했지만 단독 해법은 아니었다.

- Artifact: [`p7-b-semantic-shadow/claim-localizer-evaluation.json`](p7-b-semantic-shadow/claim-localizer-evaluation.json)
- SHA-256: `5e491ed5f9232797cb1b0a4666e2d4e5bcb0b5c5e6f3d46f33bf7a8c565afb5c`
- C0/C1/C2 artifact: [`p7-b-semantic-shadow/context-comparison.json`](p7-b-semantic-shadow/context-comparison.json), SHA-256 `e2a51ee0b80702f6141b634c75866c282d15e11b156c4beda6a6d4d8b287be0e`
- 판정: `CLAIM_AWARE_LOCALIZER_SHADOW_FAIL`
- Production 적용: 0

## 16. Semantic Support Judge — Qwen3 4B

두 NLI 모델이 핵심 5건을 모두 0/5로 놓친 뒤 task를 바꿨다. 질문은 더 이상 “premise가 hypothesis를 strict entail하는가?”가 아니라 “현재 evidence만으로 이 career claim을 합리적으로 뒷받침하는가?”였다. 허용 범위는 같은 evidence window 안의 직접 support, 조치+직접 결과의 causal support, 구현 절차의 procedural support, 구체 component와 명시된 기능 사이의 좁은 abstraction이었다. 외부 지식, 관련 keyword만의 support, evidence에 없는 기술·성과 추론은 금지했다.

모델은 로컬 Ollama `qwen3:4b-instruct`(약 2.5GB)를 사용한 evaluation-only spike였다. Java Production dependency나 요청 경로에 연결하지 않았다.

### 16.1 Judge v1

v1 출력은 `label`, `supportType`, `evidenceSpans`, `reasonCode`였다. 113 pairs에서 Group C 3/5, A+B SUPPORT 37/40, CONTRADICT false SUPPORT 0/52, UNKNOWN false SUPPORT 0/16이었지만 `MODEL_OUTPUT_INVALID`가 70건이었다. semantic 판단보다 복잡한 output contract가 병목이었다.

- Artifact: [`p7-b-semantic-shadow/semantic-support-judge-results.json`](p7-b-semantic-shadow/semantic-support-judge-results.json)
- SHA-256: `4ac18144642ec5b4573a557e340885cde96bbc5563e1a1993798b9e841930a8a`
- 판정: `SEMANTIC_SUPPORT_JUDGE_NO_GO`

### 16.2 Judge v2

출력을 `label`과 extractive `evidenceSpans`로 단순화했다. Group C 3/5, A+B SUPPORT 39/40, CONTRADICT false SUPPORT 0/52, UNKNOWN false SUPPORT 0/16으로 좋아졌고 invalid는 1건으로 줄었다. 남은 F14는 원문 `47초로 낮췄고`를 모델이 `47초로 낮췄다`로 바꾸어 exact-substring validation에 실패한 output-contract bug였다.

- Artifact: [`p7-b-semantic-shadow/semantic-support-judge-v2-results.json`](p7-b-semantic-shadow/semantic-support-judge-v2-results.json)
- SHA-256: `e69ed9759f01df4c94cd7057dd745502b3a9613e759c90dcf4e356ae54544949`
- 판정: `SEMANTIC_SUPPORT_JUDGE_NO_GO`

### 16.3 Judge v3 Sentence-ID Grounding

v3는 evidence를 deterministic sentence unit `S1`, `S2`, `S3`로 나누고 모델이 `label`과 `evidenceSentenceIds`만 반환하게 했다. runner가 ID를 원문과 다시 연결하므로 paraphrase span, 존재하지 않는 ID, 다른 pair ID를 구조적으로 차단했다. semantic policy는 v2와 동일하게 유지했다.

| v3 result | Count |
|---|---:|
| Group C SUPPORT | 4/5 |
| A+B SUPPORT | 40/40 |
| CONTRADICT false SUPPORT | 3/52 |
| UNKNOWN false SUPPORT | 0/16 |
| MODEL_OUTPUT_INVALID | 0 |

Grounding contract bug는 제거됐지만 semantic false SUPPORT 3건이 생겨 단독 Gate는 실패했다.

- Artifact: [`p7-b-semantic-shadow/semantic-support-judge-v3-results.json`](p7-b-semantic-shadow/semantic-support-judge-v3-results.json)
- SHA-256: `697c8f33714c23d6e94ab8447ea0833701ca50db0e25a111c8929014f718c39d`
- 판정: `SEMANTIC_SUPPORT_JUDGE_NO_GO`

## 17. Judge v3 + Numeric

Qwen output이나 prompt를 다시 튜닝하지 않고, v3의 `SUPPORTED` numeric claim만 기존 deterministic verifier로 검사했다. 결과는 Group C 4/5, A+B SUPPORT 39/40, CONTRADICT false SUPPORT 1/52, UNKNOWN false SUPPORT 0/16, invalid 0이었다. numeric binding이 되지 않은 3건은 `NUMERIC_UNRESOLVED`로 별도 노출하고 fail-closed 처리했다. 남은 false SUPPORT F21은 숫자가 아니라 actor-attribution semantic 오류였다.

사전 component Gate는 모두 통과해 `SEMANTIC_NUMERIC_JUDGE_PROMISING`이었지만, 이 상태는 다음 P7 end-to-end 실행의 가치만 인정한 것이며 Production 적용 Gate가 아니었다.

- Artifact: [`p7-b-semantic-shadow/semantic-support-judge-v3-numeric-shadow-results.json`](p7-b-semantic-shadow/semantic-support-judge-v3-numeric-shadow-results.json)
- SHA-256: `34fc11e83be1d13db46d7a1b2dbac75557ba3912c45f4607912dfe32d8bb0398`
- 판정: `PASS_COMPONENT_ONLY — SEMANTIC_NUMERIC_JUDGE_PROMISING`
- Production 적용: 0

## 18. P7 Candidate-Level Full Judge

P7-B의 86 original results에서 claim-aware Top5 windows 430개를 freeze하고, 각 window를 Judge v3+Numeric으로 판정했다. candidate 하나라도 `SUPPORTED`이면 original result를 살리는 OR aggregation이었다.

| Metric | P7-B baseline | Candidate-level |
|---|---:|---:|
| Top1 | 33.33% | 36.11% |
| Recall@3 | 58.33% | 52.78% |
| Recall@5 | 58.33% | 52.78% |
| MRR@5 | 0.4491 | 0.4444 |
| Negative FPR | 41.67% | 33.33% |
| Baseline-correct retained | 21/21 | 19/21 |
| PASS / FAIL | 28 / 20 | 27 / 21 |

Top1과 FPR은 일부 좋아졌지만 Recall과 MRR이 하락하고 positive regression 2건이 생겼다. 다섯 window 중 한 번의 false SUPPORT만으로 result 전체가 살아나는 OR aggregation이 Negative 안전성을 제한했다.

- Artifact: [`p7-b-semantic-shadow/p7-full-final-evaluation.json`](p7-b-semantic-shadow/p7-full-final-evaluation.json)
- SHA-256: `6613a8a4fe6b5aa886440dd27cfe18291dcb3b1d25e7e319595b2e7ced6cd1c6`
- 판정: `P7_FULL_VERIFICATION_SHADOW_FAIL`
- Production 적용: 0

## 19. Result-Level Evidence Set Judge

마지막 구조는 5개 window를 각각 판정하지 않고 같은 original result의 evidence set으로 한 번에 판단했다. 86/86 result를 평가했고 invalid는 0이었다. Numeric 결합 후 label distribution은 `SUPPORTED 58`, `INSUFFICIENT 23`, `REFUTED 3`, `NUMERIC_UNRESOLVED 2`였다. NON_SUPPORT와 unresolved는 fail-closed로 제거하고 original rank를 보존·압축했다.

| Metric | Baseline | Candidate-level | Result-level |
|---|---:|---:|---:|
| Top1 | 33.33% | 36.11% | 14/36 = 38.89% |
| Recall@3 | 58.33% | 52.78% | 20/36 = 55.56% |
| Recall@5 | 58.33% | 52.78% | 20/36 = 55.56% |
| MRR@5 | 0.4491 | 0.4444 | 0.4676 |
| Negative FPR | 5/12 = 41.67% | 4/12 = 33.33% | 3/12 = 25.00% |
| PASS / FAIL | 28 / 20 | 27 / 21 | 29 / 19 |

Baseline 대비 Top1 `+5.56pp`, MRR `+0.0185`, FPR `-16.67pp`였지만 Recall@5는 `-2.78pp`였다. Baseline-correct는 20/21을 보존했고 `V2-U03-NV01` 한 건이 regression으로 남았다. 사전 Gate 중 `FPR <= 1/12`와 `Recall@5 >= 58.33%`를 실패했다.

- Artifact: [`p7-b-semantic-shadow/p7-result-level-final-evaluation.json`](p7-b-semantic-shadow/p7-result-level-final-evaluation.json)
- SHA-256: `c476246f030862dc62d248a7f728499451be784747e2951a83e7766aa9ace5dc`
- 판정: `RESULT_LEVEL_EVIDENCE_SET_JUDGE_FAIL`
- NEXT: `STOP_QWEN4B_VERIFIER_AND_REASSESS`
- Production 적용: 0

## 20. Search Stage Ceiling Audit

Qwen 4B 연구를 종료한 뒤, 새로운 모델을 시도하는 대신 P7-B Positive 36건의 정답이 검색 pipeline 어디에서 사라지는지 frozen artifact만으로 감사했다.

| Stage | Observed ceiling | 확정 loss |
|---|---:|---:|
| S0 Corpus Oracle | 36/36 | 0 |
| S1 Chunk Oracle | 36/36 | 0 |
| S2 Pre-filter candidate | 최소 22/36 | 14 unknown |
| S3 Post-filter | 최소 22/36 | 14 unknown |
| S4 Final Top5 diagnostic | 22/36 | S2/S3 trace 부재로 원인 미확정 |
| S5 Result content | 22/36 | 0 additional |
| S6 Localization | 19/36 | 3 |

S4의 `22/36`은 stage-oracle diagnostic count이며 frozen benchmark Recall@5 `21/36`을 다시 채점하거나 덮어쓴 수치가 아니다. 이 audit의 목적은 품질 metric 변경이 아니라 stage presence 확인이다.

확정 taxonomy는 `CORPUS_MISSING 0`, `CHUNKING 0`, `LOCALIZATION 3`, `UNKNOWN 14`, `PASSED_ALL_OBSERVED_STAGES 19`다. Localization loss ID는 `V2-U03-NV01`, `V2-U03-IP01`, `V2-U03-CN01`이다. 나머지 14건은 frozen pre-filter/post-filter candidate ID가 없어 candidate recall, filtering, ranking 중 최초 소실 단계를 판정할 수 없다. 따라서 0으로 기록된 S2–S4 원인은 “문제 없음”이 아니라 “그 단계에서 소실이 증명되지 않음”이다.

- Artifact: [`p7-b-stage-ceiling-audit/stage-ceiling.json`](p7-b-stage-ceiling-audit/stage-ceiling.json)
- SHA-256: `7ffec6f2c76539e0dd122e144adced030c470e061a69ac3d7080768f10919198`
- 판정: `UNKNOWN_STAGE_TRACE_REQUIRED`
- NEXT: `INSUFFICIENT_TRACE_DATA`
- Production 적용: 0

## 21. 적용하거나 평가한 기술 목록

| Technology | Purpose | Result | Status | Production? |
|---|---|---|---|---|
| BGE-M3 | multilingual embedding | Dense 검색의 안정된 기본 축 | `ADOPTED` | YES, Ollama Dense |
| pgvector | vector 저장·검색 | exact cosine 검색과 owner/ACTIVE SQL 유지 | `ADOPTED` | YES |
| Cosine similarity | 후보 유사도 정렬 | `score = 1 - distance`; 확률로 해석하지 않음 | `ADOPTED` | YES |
| Dense Retrieval | 기본 candidate retrieval | P4 dev 강점, P5/P7 일반화 한계 | `ADOPTED` | YES |
| BGE-M3 Sparse | lexical weight 후보 확장 | Recall 상승, Negative 결과 15건으로 악화 | `REJECTED` | NO |
| PostgreSQL FTS | lexical candidate channel | hit 5/40, P6 lexical-only 0 | `NO_GO` | NO |
| Hybrid Search | Dense+lexical 결합 | P6에서 Dense recall 이득 0 | `NO_GO` | NO |
| Reciprocal Rank Fusion | score scale 독립 fusion | `k=60`; rank·FPR trade-off | `NO_GO` | NO |
| Numeric fallback | exact number+unit evidence rescue | P1 개선·near miss 보호 | `ADOPTED` | YES, bounded path |
| Numeric consistency verification | value/unit exact consistency | NLI numeric FP 차단; metric binding unresolved 존재 | `ADOPTED` + `PASS_COMPONENT_ONLY` | YES for bounded search guard; post-Judge verifier NO |
| Strong Identifier Guard | 없는 고유 기술명 evidence 차단 | P1 FPR 0%; identifier corpus existence 확인 | `ADOPTED` | YES |
| Exact token gate/rescue | threshold 경계의 짧은 GENERAL 복구 | PRZ-008 P12/P17/P18 안전성 검증 후 채택 | `ADOPTED` | YES |
| Claim Gate | 완료 배포·출시 근거 fail-closed | GENERAL과 completed 상태 분리 | `ADOPTED` | YES |
| Query Understanding | 보수적 query variant | P3 Top1 75%, P95 증가 | `ADOPTED` | YES |
| Evidence-Aware Reranking | candidate 내부 근거 품질 순위 | P2 Top1 67.86%, regression 0 | `ADOPTED` | YES |
| Semantic Chunking | section/paragraph 경계 보존 | 일부 복구, 전체 지표·FPR 회귀 | `REJECTED` | NO |
| Evidence Localization | same ACTIVE version의 직접 근거 선택 | P4 dev Top1 82.14% | `ADOPTED` | YES |
| Claim-Aware Localization | same result Top5 windows | 7/14 regression 복구, Gate 실패 | `SHADOW_ONLY — FAIL` | NO |
| Rule-Based Evidence Verification | polarity·state·numeric rejection | tuning 적합, adversarial overfit | `NO_GO` | NO |
| Evidence Contradiction Veto | 명백한 contradiction만 veto | Positive 보존, Negative 누락 | `NO_GO` | NO |
| mDeBERTa NLI | SUPPORT/CONTRADICT/UNKNOWN | fresh component PASS, core P7 0/5 | `PASS_COMPONENT_ONLY / NO_GO as final judge` | NO |
| KLUE-RoBERTa NLI | 한국어 NLI 대안 | overall 87.61%, core P7 0/5 | `NO_GO` | NO |
| GPT Evidence Judge | external semantic evidence selection | FPR 0, regression 2 + incomplete 4 | `NO_GO` | NO |
| Qwen3 4B Semantic Support Judge | career evidence sufficiency judge | v3+numeric component promising, P7 Gate 실패 | `NO_GO` | NO |
| Sentence-ID Grounding | extractive grounding contract | invalid 70→0 | `PASS_COMPONENT_ONLY` | NO |
| Candidate-Level Verification | window별 judge 후 OR aggregation | Top1 +2.78pp, Recall/FPR Gate 실패 | `NO_GO` | NO |
| Result-Level Evidence Set Verification | original result 단위 joint judgment | Top1 38.89%, FPR 25%, Gate 실패 | `NO_GO` | NO |
| Fail-Closed Filtering | NON_SUPPORT/unresolved 제거 | FPR 감소와 Positive/Recall 손실 동반 | `SHADOW_ONLY — NO_GO` | NO |

## 22. Production vs Experimental

### 현재 Production에 존재하는 것

현재 repository source를 기준으로 기본 `source-dedup-evidence-signals-v1` profile에는 다음이 있다.

1. Ollama `bge-m3` 1024차원 embedding과 finite/non-zero 검증
2. pgvector exact cosine Dense Top20, threshold `0.50`, 최종 최대 5건
3. document·version·chunk owner scope와 현재 `active_version_id`/`ACTIVE` 제한
4. GENERAL/completed-release intent와 fail-closed Claim Gate
5. 강한 identifier의 owner ACTIVE corpus 존재 검증
6. 숫자+단위 contextual exact match와 bounded numeric rescue
7. Dense를 주 신호로 유지하는 bounded evidence-quality reranking
8. exact short-token rescue와 conservative query variant 최대 2개
9. exact full-content dedup
10. 선택된 result를 바꾸지 않는 same-document/ACTIVE-version evidence expansion과 원문 1–3문장 snippet
11. `EVIDENCE_FOUND`, `NO_RELEVANT_RESULTS`, `NO_EVIDENCE`, `NO_SEARCHABLE_DOCUMENTS` 상태와 source metadata 반환

주요 source는 [`SearchService.java`](../../src/main/java/com/prizm/search/service/SearchService.java), [`CompositeSearchProfile.java`](../../src/main/java/com/prizm/search/profile/CompositeSearchProfile.java), [`VectorSearchRepository.java`](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java), [`EvidenceExpansionService.java`](../../src/main/java/com/prizm/search/service/EvidenceExpansionService.java), [`SearchSnippetGenerator.java`](../../src/main/java/com/prizm/search/service/SearchSnippetGenerator.java)다.

### Experimental / Shadow에만 존재하는 것

PostgreSQL FTS·RRF hybrid, BGE-M3 Sparse, BGE reranker, section-aware chunking, generic literal gate, GPT Judge, rule-based claim/veto evaluator, mDeBERTa/KLUE NLI, post-search numeric verifier, context C1/C2, Claim-Aware Top5 localizer, Qwen v1–v3, sentence-ID grounding, candidate/result-level verification, fail-closed semantic filtering은 `src/searchEvaluation`, `scripts/evaluation`, `specs/.../p7-b-semantic-shadow`에만 있다. 이들은 Production `src/main` 요청 경로에 연결되지 않았다.

P5 이후 semantic 연구가 평가 전용 파일을 많이 남겼지만 **Production 검색 알고리즘은 semantic 실험으로 변경되지 않았다**. “평가했다”, “component Gate를 통과했다”, “Production에 적용했다”는 서로 다른 상태다.

## 23. 핵심 교훈

1. **개발 평가셋 상승은 일반화 보장이 아니다.** P0→P4 Top1은 57.14%→82.14%로 올랐지만 P5는 50%, P7-B는 33.33%였다.
2. **Recall과 false positive는 함께 봐야 한다.** Sparse와 넓은 semantic acceptance는 Recall을 높일 수 있지만 없는 경험을 더 많이 살렸다. 반대로 strict fail-closed filtering은 FPR을 낮추며 Positive Recall을 크게 잃었다.
3. **Deterministic rule은 좁은 failure에는 강하지만 언어 변형에 overfit한다.** 계획/검토/prototype/폐기 같은 단어는 상태 전이의 앞·뒤 어디에 있는지에 따라 의미가 반대다.
4. **일반 NLI와 career evidence sufficiency는 다른 task다.** 두 NLI 모델 모두 전체 accuracy는 높았지만 causal·procedural·abstraction support가 필요한 core 5를 0/5로 놓쳤다.
5. **LLM Judge도 자동 해법은 아니다.** Qwen v3는 sentence grounding과 Positive support를 개선했지만 actor attribution과 result aggregation에서 false SUPPORT가 남았다.
6. **Component PASS와 end-to-end PASS는 다르다.** Fresh Semantic+Numeric과 Judge v3+Numeric은 component Gate를 통과했지만 실제 P7 result filtering은 Recall/FPR Gate를 실패했다.
7. **사전 Gate와 중단 규칙이 Production을 보호했다.** 실패한 Hybrid, GPT, rule, NLI, Qwen 구조를 성공처럼 포장하거나 Production에 연결하지 않았다.
8. **현재 P7 corpus와 chunking은 주 병목이 아니다.** corpus oracle과 chunk oracle은 모두 36/36이었다.
9. **다음 핵심은 새 모델이 아니라 stage trace다.** 14개 Positive가 candidate recall, filtering, ranking 중 어디서 처음 사라졌는지 현재 frozen artifact만으로는 알 수 없다.

## 24. 현재 상태

| 항목 | 현재 상태 |
|---|---|
| Qwen 4B verifier | `STOP / NO_GO` |
| Rule-based rejection | `FINAL NO_GO` |
| NLI model swap | `NO_GO` |
| Semantic + Numeric | Fresh component PASS, P7 end-to-end FAIL |
| Production | semantic/LLM/Hybrid 실험으로 인한 변경 없음 |
| P7-B baseline | Top1 33.33%, Recall@5 58.33%, FPR 41.67%, `FAIL` |
| Corpus oracle | 36/36 |
| Chunk oracle | 36/36 |
| Final Top5 stage diagnostic | 22/36 |
| Localization | 19/36; known loss 3 |
| Unknown S2/S3/S4 loss | 14 |
| Primary bottleneck | `UNKNOWN_STAGE_TRACE_REQUIRED` |
| NEXT | `INSUFFICIENT_TRACE_DATA` |

다음 작업은 14개 Positive query에 대한 **evaluation-only Stage Tracer**다. 각 query의 correct chunk ID를 기준으로 S2 Candidate Recall → S3 Filtering → S4 Ranking의 입력·출력을 기록해 최초 소실 단계를 확정해야 한다. Production behavior, threshold, ranking, query, GT를 바꾸거나 새 AI model을 추가하는 작업이 아니다.

## 25. Resume / Portfolio Friendly Summary

PRIZM의 경력 문서 검색을 Dense vector baseline에서 숫자·식별자 검증, 근거 기반 재정렬, 질문 변형 처리, 근거 위치화까지 단계적으로 개선해 개발 평가셋 Top1을 57.14%에서 82.14%로 높였다. 이후 frozen holdout과 독립 사용자·문서 평가를 별도로 수행해 Top1이 50.00%, 33.33%로 낮아지고 Negative FPR이 25.00%, 41.67%로 높아지는 일반화 한계를 확인했다.

PostgreSQL FTS/RRF, BGE-M3 Sparse, deterministic rejection, multilingual·Korean NLI, GPT와 로컬 Qwen 기반 semantic judge, candidate/result-level evidence verification을 사전 Gate로 비교했다. 일부 방식은 Recall이나 false positive를 개선했지만 Positive regression 또는 end-to-end Gate 실패가 남아 Production에 적용하지 않았다. 현재 corpus와 chunk에 정답 근거는 36/36 존재하므로, 다음 단계는 새 모델 추가가 아니라 Candidate → Filter → Ranking 단계별 trace로 14개 미확정 손실의 최초 지점을 찾는 것이다.

---

### 주요 authoritative artifact index

| 구간 | Artifact | SHA-256 | 최종 상태 |
|---|---|---|---|
| P0 | `p0-benchmark/baseline-results.json` | `a8048a2a...f35d` | baseline |
| P1 | `p1-numeric-identifier/benchmark-results.json` | `cee543d2...2f9b` | PASS / Production |
| P2 | `p2-evidence-reranking/benchmark-results.json` | `a32df4e8...178f8` | PASS / Production |
| P3 | `p3-query-understanding/benchmark-results.json` | `9a561d58...46a4` | PASS / Production |
| P4 | `p4-evidence-localization/benchmark-results.json` | `64bf9229...00b3` | PASS / Production |
| P5 | `p5-final-holdout/holdout-results.json` | `5c1e57e5...f752` | FAIL |
| P6 | `p6-retrieval-shadow/p6-b-results.json` | `a5db336c...8504` | NO_GO |
| P7-B | `p7-b-independent-generalization/evaluated-results.json` | `a1b85aaf...96b8` | FAIL |
| Strict semantic P7 | `p7-b-semantic-shadow/evaluation.json` | `93b797f2...1006` | FAIL |
| Claim-aware | `p7-b-semantic-shadow/claim-localizer-evaluation.json` | `5e491ed5...fb5c` | FAIL |
| NLI comparison | `p7-b-semantic-shadow/nli-model-comparison-evaluation.json` | `eab0366d...b846` | NO_GO |
| Qwen v3 | `p7-b-semantic-shadow/semantic-support-judge-v3-results.json` | `697c8f33...c39d` | NO_GO alone |
| Qwen v3 + Numeric | `p7-b-semantic-shadow/semantic-support-judge-v3-numeric-shadow-results.json` | `34fc11e8...0398` | PASS_COMPONENT_ONLY |
| Candidate-level | `p7-b-semantic-shadow/p7-full-final-evaluation.json` | `6613a8a4...d1c6` | FAIL |
| Result-level | `p7-b-semantic-shadow/p7-result-level-final-evaluation.json` | `c476246f...e5dc` | FAIL |
| Stage ceiling | `p7-b-stage-ceiling-audit/stage-ceiling.json` | `7ffec6f2...9198` | INSUFFICIENT_TRACE_DATA |

짧은 SHA는 읽기 편의를 위한 표기이며, 각 본문 절에는 full SHA-256을 기록했다. Artifact 자체와 freeze manifest의 full hash가 최종 권위다.

## 26. 현재 상태 — 2026-08-17

이 절은 후속 Stage Trace와 정책 감사를 반영한 PRZ-016의 최종 상태다. 20절과 24절의 `UNKNOWN_STAGE_TRACE_REQUIRED`, `INSUFFICIENT_TRACE_DATA`는 추적 전 기록이며, 현재 판단은 이 절을 기준으로 한다.

### 요약

| 구분 | 결과 |
|---|---|
| P0 개발 기준선 | `FROZEN` — Top1 57.14%, Recall@5 67.86%, Negative FPR 6.25% |
| P1 Numeric + Strong Identifier | `ADOPTED` |
| P2 Evidence-Aware Reranking | `ADOPTED` |
| P3 Query Understanding | `ADOPTED` |
| P4 Evidence Localization | `ADOPTED` — 개발 평가 Top1 82.14% |
| P5 unseen-question holdout | `FAIL` — Top1 50.00%, Negative FPR 25.00% |
| P7-B cross-user/document/query | `FAIL` — Top1 33.33%, Recall@5 58.33%, Negative FPR 41.67% |

현재 Production에는 P1–P4의 제한된 deterministic 검색 경로만 반영돼 있다. Hybrid/FTS/RRF/Sparse, GPT Evidence Judge, rule-based rejection/veto, mDeBERTa·KLUE NLI, Qwen 4B verifier는 모두 `NO_GO` 또는 `REJECTED`다. 일부 component가 자체 Gate를 통과했더라도 Production 적용이나 end-to-end 일반화 성공을 뜻하지 않는다.

### Production 상태

- 기준 branch: `PRZ-016-search-performance-v2`
- 기준 HEAD: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- State Freeze 이후 production 변경: 완료 경험 질의의 identifier guard 빈 결과 상태를
  기존 `NO_EVIDENCE` 계약에 맞춘 `SearchService` 1파일 교정
- [`CompositeSearchProfile.java`](../../src/main/java/com/prizm/search/profile/CompositeSearchProfile.java)는 HEAD와 같으며, GENERAL candidate의 dense score `0.50` hard floor가 유지된다.
- Dense-floor 제거 실험, Qwen, GPT, NLI, Hybrid, post-search numeric verifier, Claim-Aware localizer는 Production 요청 경로에 남아 있지 않다.

### P7-B 진단

Corpus Oracle과 Chunk Oracle은 모두 `36/36`이었다. 원인이 불명확했던 Positive 14건은 correct candidate가 S2에 모두 있었지만 S3를 통과하지 못했다.

| 최초 제거 조건 | 건수 |
|---|---:|
| `DENSE_SCORE_BELOW_TUNING_FLOOR` | 7 |
| `QUERY_EVIDENCE_CONSOLIDATION` | 4 |
| `SOURCE_LOCATION_CONSOLIDATION` | 1 |
| `NEGATED_CLAIM` | 2 |

이 결과는 해당 14건에서 candidate-recall loss가 없었다는 뜻이다. BGE-M3가 언제나 완벽하다는 의미는 아니다. P7-B의 모든 실패가 Filter 때문인 것도 아니다. Final Top5 이후 localization loss 3건이 있었고, Negative false positive와 metric-binding 한계도 남아 있다.

### 후속 검토 결과

1. **Dense floor removal — BLOCKED.** `0.50` floor를 제거하자 Recall@5는 `58.33% → 75.00%`로 올랐지만 Negative FPR도 `41.67% → 83.33%`로 커졌다. 새 threshold는 채택하지 않았고 Production은 원래 동작으로 복원했다.
2. **Consolidation redesign — BLOCKED.** 선택된 representative가 GT의 chunk와 다른 사례가 있었지만, 다른 representative에도 유효한 evidence가 있었다. 단일 expected location을 전제로 한 평가의 한계가 있어 Production bug로 단정하지 않았다.
3. **NEGATED_CLAIM — AMBIGUOUS.** `않`·`없`이 들어간 prevention/guard 성과가 실제 claim 부정처럼 처리됐다. 현재 lexical signal만으로 미구현·미채택과 안전하게 구분할 일반 규칙은 찾지 못했다.
4. **Localization redesign — BLOCKED.** `V2-U03-NV01`, `V2-U03-IP01`, `V2-U03-CN01`은 supporting window를 만들 수 있었지만 semantic paraphrase 때문에 lexical top-5 preselection에서 빠졌다. P7-specific synonym, 새 model, threshold 없이 해결할 일반 규칙은 확인하지 못했다.

### 해석 범위

P7-B는 앞으로 `DIAGNOSTIC / HISTORICAL DATASET`으로만 보존한다. 48 query, corpus, GT와 기존 metric은 수정하지 않으며 threshold, prompt, rule, model tuning에도 사용하지 않는다.

Multiple acceptable evidence가 있는 query를 단일 chunk만으로 채점하면 valid evidence를 오답으로 볼 수 있다. 다음 평가는 Candidate → Filter → Ranking → Localization의 중간 identity와 rejection reason을 실행 시점부터 저장해야 한다. BGE-M3, dense floor, consolidation, negation, localization 중 어느 하나도 전체 일반화 실패의 단독 원인으로 확정하지 않는다.

### 다음 단계

다음 Phase는 `FRESH_GENERALIZATION_EVALUATION_V2`다.

- 새로운 사용자, 문서, query를 검색 전에 freeze한다.
- query마다 multiple acceptable evidence를 기록할 수 있는 GT 계약을 사용한다.
- Candidate → Filter → Ranking → Localization trace를 최초 실행부터 저장한다.
- P7-B의 문장, query, threshold, failure ID는 tuning에 사용하지 않는다.

이번 작업에서는 새 dataset을 만들거나 검색·모델을 실행하지 않았다.

### 종료 판정

- Production Search change: `0`
- New benchmark/model inference: `NOT_RUN`
- P7-B further tuning: `NOT_ALLOWED`
- Current phase: `CLOSED WITHOUT ADDITIONAL TUNING`
- Final: `PRZ_016_STATE_FROZEN`
