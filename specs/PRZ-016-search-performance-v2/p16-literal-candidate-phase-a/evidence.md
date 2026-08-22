# PRZ-016 P16 Literal Candidate Phase A Evidence

- 최종 판정: `NEEDS_ADJUSTMENT`
- 기준 source: `05044b11038eeebaebac650c67d0d90136ae10bc`
- 실행일: 2026-08-22
- 환경: Docker Desktop 29.6.2, Testcontainers PostgreSQL 16.14,
  pgvector image `0.8.2-pg16-bookworm`, 로컬 Ollama `bge-m3:latest`
- OpenSQL/OpenProxy: `NOT_RUN`
- Production 적용·commit·push·PR·Phase B: `NOT_RUN`
- v2 authoritative result: [phase-a-results.json](phase-a-results.json)
- latest v3 realistic-PDF result:
  [phase-a-results-v3-realistic-pdf.json](phase-a-results-v3-realistic-pdf.json)

## Dataset freeze

주 owner의 현재 `ACTIVE` corpus는 88 chunks다. Positive 7개와 safety 5개 query를 포함한
dataset v2 SHA-256은
`55c7f2819462cf90d6645be40b7d3987c41f3153d6c6203fd20ae1feb2d5041d`다.

최초 v1 freeze는 near-miss 설명 문장 자체가 `FooEngineX` 뒤에 `FooEngine`, `Bundle` 뒤에
`Bun`을 다시 써서 boundary negative와 모순됐다. 첫 실행에서 이 오염을 확인했고, 설명의
exact token만 제거한 v2를 authoritative 실행 전에 다시 freeze했다. 구현과 query/expected
mapping은 이 교정에 맞춰 분기하거나 whitelist를 추가하지 않았다. v1 hash와 supersession
사유는 [freeze manifest](freeze-manifest.json)에 보존했다.

## 구조와 실행 결과

- Literal repository는 dataset 기술명을 모르는 1~5 token expression만 받고, SQL parameter
  binding과 document/version/chunk의 owner 조건, `active_version_id`, `ACTIVE` status를 적용한다.
- case-insensitive NFKC/whitespace normalization과 Unicode identifier boundary를 적용한다.
- D1은 Dense Top20과 Literal Top20을 chunk ID로 deduplicate하고 기존 dense score/distance
  순서로 현재 Q0 filter를 실행한다. RRF, FTS, boost, reranker, threshold 변경은 없다.
- 기존 `ProductionSearchDecisionTracer`가 실제 `SearchService` 응답과 D0 candidate/output을
  비교했고 frozen 12 query 모두 parity PASS다.

Positive 7개는 Dense candidate, Literal candidate와 union에 모두 존재했다. 그러나 실제
BGE-M3 전체 Dense rank가 Redis, Spring Boot, Tauri, Bun, LangGraph, FooEngine,
Quartz Harbor Mesh 모두 1위였다. 따라서 literal-only 정답은 0건이며 “Dense가 놓친 정답을
Literal이 추가했다”는 Phase A 필수 근거는 얻지 못했다.

Safety 5개는 모두 literal candidate 0건이었다.

- owner corpus에 없는 `AbsentNimbus`: PASS
- 다른 owner에만 있는 `ForeignOnlyStack`: PASS
- inactive version에만 있는 `DormantGraph`: PASS
- 다른 owner의 `FooEngineX`에 대한 `FooEngine` exact boundary: PASS
- 다른 owner의 `Bundle`에 대한 `Bun` short boundary: PASS
- lower-case `redis`와 원문 `REDIS` case 차이: PASS

## Verification

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat test --tests com.prizm.search.evaluation.PhaseALiteralQueryExpressionTest --no-daemon` | PASS, 4/4 |
| `.\gradlew.bat p16LiteralCandidatePhaseA --no-daemon --rerun-tasks` | FAIL, 1 test / 1 failure: `literalOnlyRecoveryCount >= 1`에서 actual 0 |
| `.\gradlew.bat test --no-daemon` | PASS, 581 tests / failures 0 / errors 0 / skipped 20 |
| `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | PASS, 114 tests / failures 0 / errors 0 / skipped 8 |
| `git diff --check`와 P16 trailing-whitespace scan | PASS |

Production search 35개 source aggregate SHA-256은 전후 모두
`743c767b4f893d112199b99888b34e9727771e1020259c0d3ae9465678510ee5`이며,
`src/main`, Flyway migration과 frontend diff는 0개다. Literal parser/repository/union
implementation에서 dataset identifier 이름 검색 결과도 0건이다. 기존 사용자 미추적
디렉터리와 stash 2개는 수정·삭제·reset·clean·drop하지 않았다.

## Audit와 다음 Gate

코드 scope, ownership, ACTIVE, boundary, score 보존과 Production diff의 blocking finding은
0건이다. 그러나 acceptance criterion 4의 실행 근거가 없으므로 P16은 PASS가 아니다.
Frozen 결과를 보고 target을 Dense Top20 밖으로 밀도록 같은 dataset을 tuning하지 않는다.

Phase B D0/D1 최종 비교는 시작하기에 안전하지 않다. 다음 Phase A 시도는 현재 v2를 수정하지
않고, 사전에 독립적으로 동결한 새 synthetic corpus가 실제 BGE-M3 Dense miss를 포함하는지
먼저 확인할 수 있는 재개 절차를 별도로 합의해야 한다.

## v3 realistic PDF fast-track 재검증

사용자 승인에 따라 기존 P16 방향을 유지하고 별도 spec/plan/tasks를 추가하지 않은 채
fast-track으로 실행했다. v2 dataset, manifest와 result는 수정하지 않았다. 최신 판정은 여전히
`NEEDS_ADJUSTMENT`다.

### 독립 corpus와 실제 ingestion 경로

- 합성 A4 프로젝트 회고 PDF 1개: 27페이지, PDFBox 추출 63,470자.
- Production 기본 `TextChunker` 설정 `800자/120자 overlap` 적용 결과 PDF `PAGE` chunks
  101개. version-isolation용 ACTIVE TXT 1개를 더한 주 owner ACTIVE corpus는 102 chunks다.
- Redis, Spring Boot, Tauri, Bun, LangGraph, FooEngine, Quartz Harbor Mesh는 표지·목차·제목·
  bullet이 아니라 서로 다른 장문 본문 문장 안에만 존재한다.
- 27페이지 전체를 Poppler PNG로 렌더링했다. 표지, target 포함 5·6·10·14·17페이지와 마지막
  27페이지를 확대 검사했으며 clipping, overlap, 깨진 한글 글꼴, 빈 본문 페이지는 없었다.
- 최초 문장 초안의 `Redis를` 같은 한글 조사는 Unicode exact-boundary 계약을 만족하지 않아
  Dense/Literal 실행 전에 `Redis 기반` 형태의 공백 경계로 교정했다. 이후 PDF/source/dataset/
  generator를 동결했고 검색 결과를 본 뒤 수정하지 않았다.

동결 자산은
[freeze-manifest-v3-realistic-pdf.json](freeze-manifest-v3-realistic-pdf.json)에 기록했다.

| 자산 | SHA-256 |
|---|---|
| PDF | `a226f5497d66f9480645c52e1236736eef4e55d82e1de45667e5c276125b6843` |
| source data | `77a3f37099b09969ce2b87f516a233ea3cd31302b09c83c226eb61e87cca88bb` |
| generic generator | `bf0ea34f3a5fbec364d8c83371bb3b1f50e09f0958fee3b2f5ae675018ce20ce` |
| v3 dataset | `3f48b56b7445fc19f745f7ae0ed9b3a2fe56ffaf6d256c8453227deab75109b6` |

generator와 evaluation implementation에는 위 identifier 이름 상수나 이름별 분기가 없고,
identifier와 expected mapping은 source/dataset data에만 있다.

### D0/D1 Candidate와 Final 관찰

| 관찰 | D0 | D1 | 차이 |
|---|---:|---:|---:|
| positive expected candidate | 7/7 | 7/7 | 0 |
| positive expected literal candidate | — | 7/7 | +literal channel 확인 |
| literal-only recovery | — | 0/7 | 0 |
| D0 candidate와 union candidate 완전 동일 | — | 12/12 queries | 동일 |
| D0/D1 Q0 Production filter ID 순서 동일 | — | 12/12 queries | 동일 |
| 실제 D0 Production positive final hit | 4/7 | candidate 입력 동일로 변화 없음 | 0 |
| 실제 D0 Production safety false positive | 0/5 | candidate 입력 동일로 변화 없음 | 0 |

7개 positive target의 BGE-M3 전체 Dense rank는 v2와 마찬가지로 모두 1위였다. Literal
repository도 7개를 모두 찾았고 union에도 모두 존재했지만 Dense Top20 밖 target이 없어서
literal-only candidate는 0건이었다. 모든 query에서 D1 union ID·순서가 D0 Dense와 완전히
같고 같은 Q0 Production filter 결과도 같으므로, 이 frozen run에서는 D1 final 결과가 달라질
candidate 변화가 없다.

D0 실제 Production final은 Spring Boot, Bun, LangGraph, FooEngine 4개에서 expected chunk를
선택했고 Redis, Tauri, Quartz Harbor Mesh는 빈 결과였다. 이 final 차이는 P3 fallback을 포함한
현재 Production 경로의 결과이며, Literal recovery 근거로 해석하지 않는다. Phase B 성능 결론이나
Production 적용은 수행하지 않았다.

### 격리와 focused/full verification

- owner isolation: `PASS` — 다른 owner의 exact 표현이 primary literal candidate에 없음.
- ACTIVE isolation: `PASS` — inactive version의 표현이 literal candidate에 없음.
- exact boundary: `PASS` — 다른 owner의 `FooEngineX`는 `FooEngine` exact match가 아님.
- short boundary: `PASS` — 다른 owner의 `Bundle`은 `Bun` exact match가 아님.
- case-insensitive exact match: `PASS` — query `redis`가 본문 `Redis`를 찾음.
- D0 Production parity: `PASS`, 12/12 query에서 Q0 Dense ID 순서와 실제 SearchService trace가
  일치하고 production response match도 통과.

| 명령 | 결과 |
|---|---|
| `searchEvaluation --tests PhaseARealisticPdfCorpusTest` | PASS, 1/1; 27 pages / 63,470 chars / 101 chunks |
| `p16LiteralCandidatePhaseA --rerun-tasks` | FAIL, 1/1; 필수 `literalOnlyRecoveryCount >= 1`, actual 0 |
| `test --no-daemon` | PASS, 581 tests / failures 0 / errors 0 / skipped 20 |
| `integrationTest --rerun-tasks` | PASS, 114 tests / failures 0 / errors 0 / skipped 8 |
| frozen asset hash 재검증 | PASS, 4/4 hash·bytes 일치 |

Production search 35개 source aggregate SHA-256은
`743c767b4f893d112199b99888b34e9727771e1020259c0d3ae9465678510ee5`이고,
`src/main`, Flyway migration, frontend diff는 0개다. commit, push, PR과 Production 적용은
`NOT_RUN`이다.

### v3 최종 판정

현실형 장문 PDF 생성, 실제 extraction/chunking, D0 parity, literal candidate 조회, owner/ACTIVE/
boundary와 전체 regression은 통과했다. 그러나 독립적으로 동결한 101-chunk PDF에서도 positive
7개가 모두 Dense rank 1이어서 Phase A의 핵심 recovery criterion은 다시 충족하지 못했다.
따라서 판정은 `NEEDS_ADJUSTMENT`이며 이 corpus를 성공 결과에 맞게 다시 조정하지 않는다.

## Eligibility Precision fast-track

기존 23-case matrix와 v3 corpus를 수정하지 않고 actor, mere mention, bare-identifier negation만
일반 규칙으로 보정했다. 기술명 또는 case ID별 분기는 없다.

| 관찰 | Before | After |
|---|---:|---:|
| Positive | 12/12 | 12/12 |
| Negative | 3/11 | 11/11 |
| Actor reject | 0/5 | 5/5 |
| Mere mention reject | 0/2 | 2/2 |
| False positive | 8 | 0 |
| False negative | 0 | 0 |

대조형 positive 4/4는 유지됐고 명시적 negation 4/4는 모두 차단됐다. Redis는 v3 final 성공을
유지했으며 Tauri와 Quartz Harbor Mesh는 이번 범위 밖인 기존 dense floor 결과를 그대로
유지했다. Matrix freeze SHA-256은
`1da06f10751a97b9ef4856249f2c15efee88d161ec151305efda647b369baa4b`로 변하지 않았다.

| 명령 | 결과 |
|---|---|
| frozen eligibility matrix focused run | PASS, 23/23; Production parity 23/23 |
| Structured Claim / search profile focused regression | PASS, 57/57 |
| backend unit 1회 | 585 tests / failures 5 / skipped 20; 변경 직후 발견된 관련 회귀 5건 |
| 위 5건 포함 focused 재검증 | PASS, 57/57 |
| frozen v3 asset hash 재검증 | PASS, 4/4 hash·bytes 일치 |
| `git diff --check` | PASS |

전체 unit은 사용자 지시에 따라 반복하지 않았고 integration도 `NOT_RUN`이다. Eligibility
Precision phase 판정은 `PASS`다. 다음 단계에서 floor 정책을 별도로 평가할 수 있지만, 이번
변경에는 threshold, floor bypass, fallback, retrieval, ranking 변경이 포함되지 않는다.

## Dense floor fast-track diagnostic

Production threshold를 변경하지 않고 기존 v3 102-chunk corpus를 case별 distractor로 재사용한
24-case matrix를 실행했다. Matrix는 실행 전에 SHA-256
`2db0da24057a3aa6bdc4d9bc364bc576383ad7cf1ce9f8b5c5d2b8cdbacd4b42`로 동결했다.
No-floor shadow는 모든 candidate score에 같은 `+0.50` 상수를 적용해 순서와 score 간격을
보존하면서 기존 `CompositeSearchProfile`의 절대 floor 비교만 제거했다.

| 관찰 | 결과 |
|---|---:|
| Matrix | 24 cases: Positive 12 / Negative 12 |
| Positive Dense rank 1 | 12/12 |
| D0 floor-loss Positive | 0/12 |
| D0 rank-1 / Top5 / direct-support floor loss | 0 / 0 / 0 |
| D1 no-floor recovered Positive | 0/12 |
| Negative Dense Top20 및 score 0.50 이상 | 9/12 |
| D0 Negative false positive | 2/12 |
| Negative direct-support 오인 위험 | 3/12 |
| D1 Negative false positive | 2/12 |
| D1에서 새로 살아난 Negative | 0/12 |
| Production trace parity | 24/24 |

새 matrix의 Positive 2건(P06, P12)은 각각 `ENTITY_NOT_BOUND_TO_ACTION`,
`INSUFFICIENT_CLAIM_SUPPORT`로 floor 이전에 탈락했다. Negative 2건(N05, N06)은 다른 actor의
경험인데도 D0와 D1에서 이미 통과했고, N03은 direct-support로 오인됐지만 direct-anchor
post-filter에서 제거됐다. 이 결과는 floor 효과와 별개인 precision confound이므로 dataset을
실행 후 수정하거나 Production을 튜닝하지 않았다.

기존 v3에서는 Redis가 D0와 D1 모두 성공했다. Tauri와 Quartz Harbor Mesh는 D0에서 기존
`BELOW_DENSE_FLOOR` 상태를 유지했고 no-floor shadow에서 둘 다 복구됐다. 다만 structured
direct-support 판정은 Quartz만 `true`이고 Tauri는 `false`여서, 현재 predicate를 그대로 쓰는
제한적 direct-support bypass는 두 사례를 모두 포괄하지 못한다.

따라서 현재 0.50 floor가 특정 실제 손실의 원인이라는 근거는 유지되지만, 새 독립 matrix에서는
일반화된 floor 병목이 재현되지 않았다. 전역 threshold 하향 근거는 부족하다. 제한적 bypass는
후속 shadow 실험 가치가 있으나, 긴 actor/비참여 문장에 대한 precision predicate를 먼저
명확히 해야 한다.

| 명령 | 결과 |
|---|---|
| frozen floor matrix + D0/no-floor shadow | PASS, 1/1; Production parity 24/24 |
| frozen eligibility matrix 재실행 | PASS, 1/1; Positive 12/12, Negative 11/11 |
| Structured Claim focused regression | PASS, 20/20 |
| floor matrix freeze hash 재검증 | PASS |
| `git diff --check` | PASS |

전체 backend/integration, Production 적용, commit, push는 `NOT_RUN`이다. Floor 진단 판정은
`NEEDS_ADJUSTMENT`다.

## Direct-support safety와 제한적 bypass expansion shadow

기존 frozen v3, Eligibility 23-case matrix와 Floor 24-case matrix를 그대로 재사용했다. 새
corpus/PDF/spec/plan/tasks는 만들지 않았고 0.50 threshold도 바꾸지 않았다.

Tauri가 `directSupport=false`였던 원인은 기술명이 아니라 문장 형태였다. evaluator가
`검토용`의 `검토`를 비단정 표현으로 오인했고, 실제 행위인 `구성해`를 affirmative action으로
인식하지 못했다. 함께 확인한 actor false positive는 다른 actor 문장 뒤의 `직접 ... 하지
않았다`가 first-person marker로 해석되어 actor mismatch를 상쇄한 것이 원인이었다. Bare
identifier의 명시적 사용 부정은 query action set이 비어 있어 target-bound negation 검사를
타지 못했다.

일반 규칙으로 affirmative action의 연결형(`해`/`하여`)을 인식하고, `검토용`과 실제 검토를
구분하며, 다른 actor 표지를 first-person 단어 하나로 상쇄하지 않도록 했다. Entity 가까이의
명시적 action negation과 비참여 표현을 target에 결합했고, 후보/목록/비교표와 문서 열람은
affirmative action이 없을 때 mere mention으로 처리했다. 기술명·case ID별 분기는 없다.

### Gate A

| 관찰 | 결과 |
|---|---:|
| Eligibility Positive | 12/12 |
| Eligibility Negative | 11/11 |
| Actor 오판 | 0 |
| Mere mention 오판 | 0 |
| Negation 오판 | 0 |
| Tauri형 실제 선언 direct support | PASS |
| Quartz Harbor Mesh 기존 direct support | PASS |
| Production parity | 23/23 |

### D0와 D1 shadow

D1은 Production bypass를 변경하지 않는다. Dense Top5 안에서 현재 singleton rejection이 오직
`DENSE_SCORE_BELOW_TUNING_FLOOR`이고, 기존 `claimSupport.directSupport()`가 true이며,
query anchor가 evidence에 직접 결합되고 matched claim window에 affirmative action이 있는
candidate만 evaluation에서 floor 통과 상태로 만들어 기존 profile을 다시 실행했다. 다른
candidate score와 모든 Production 정책은 유지했다.

| Query | Dense rank / score | D0 final | D1 shadow final |
|---|---:|---:|---:|
| Redis | 1 / 0.424269 | PASS | PASS |
| Tauri | 1 / 0.337798 | FAIL | PASS |
| Quartz Harbor Mesh | 1 / 0.442412 | FAIL | PASS |

Floor matrix 24건(Positive 12 / Negative 12)에서는 D0와 D1 모두 Positive 10/12, Negative
12/12였다. 기존 P06과 P12는 각각 `ENTITY_NOT_BOUND_TO_ACTION`,
`INSUFFICIENT_CLAIM_SUPPORT`로 floor 전에 탈락해 이번 shadow 대상이 아니며 상태가 변하지
않았다. D1 새 false positive는 0건, 새 false negative도 0건이며 negative direct-support
오인 위험도 0건이었다. Frozen v3에서는 Tauri와 Quartz Harbor Mesh 2건만 복구됐고 Redis는
성공을 유지했다.

| 명령 | 결과 |
|---|---|
| Structured Claim / search profile focused regression | PASS |
| frozen Eligibility matrix focused run | PASS, Positive 12/12, Negative 11/11, parity 23/23 |
| conservative bypass shadow + Floor matrix | PASS, 24/24 parity, 새 FP 0, 새 FN 0 |
| 두 DB-seeding evaluation method 동시 실행 | FAIL, 동일 context의 고정 seed duplicate key; 독립 실행은 각각 PASS |
| `./gradlew test` 최종 1회 | PASS, 588 tests / failures 0 / errors 0 / skipped 20 |
| `git diff --check` | PASS |

Production 변경은 `StructuredClaimSupportEvaluator`의 direct-support 안전성 보강 1개 파일뿐이다.
Production bypass, threshold, retrieval, fallback, ranking, consolidation, DB/Flyway, frontend 변경은
0개다. Integration, commit, push는 `NOT_RUN`이다.

판정은 `PASS`다. 전역 0.50 하향은 여전히 NO-GO이며, 이번 shadow와 동일한 보수 조건의 기존
direct-support bypass 범위 확장은 다음 Phase의 Production 적용 후보로 평가할 수 있다.

## Unseen direct-support safety gate

기존 Eligibility/Floor/v3 문장을 재사용하지 않은 20-case dataset을 실행 전에 동결했다.
Positive 10 / Negative 10이며 HikariCP, OpenTelemetry, Pulumi, NATS, Apache Pulsar, Bazel,
SvelteKit과 새 multi-word identifier를 섞었다. Negative는 다른 팀·협력사·고객사, 검토,
후보 비교, 자료 확인, 명시적 미사용, 대체 기술, 장문 비참여, absent identifier를 각각 별도
case로 구성했다.

- Dataset: `unseen-direct-support-gate-v1.json`
- bytes: `9,408`
- SHA-256: `c9e800075ad257b60ddab7407abc2fdcad067e10206bb580553ba8755708c1c3`
- freeze 이후 문장, query, expected 수정: 0

| 관찰 | D0 | D1 conservative shadow |
|---|---:|---:|
| Positive 정확도 | 8/10 | 8/10 |
| Negative 정확도 | 8/10 | 8/10 |
| D1 복구 Positive | — | 0 |
| D1 신규 false positive | — | 0 |
| D1 신규 false negative | — | 0 |
| Production parity | 20/20 | — |

Positive UP04는 dense rank 1 / 0.693366이지만
`CLAIM_SUPPORT_UNSUPPORTED:INSUFFICIENT_CLAIM_SUPPORT`, UP07은 dense rank 1 / 0.750898이지만
`CLAIM_SUPPORT_UNSUPPORTED:ACTION_NOT_SUPPORTED`로 D0와 D1에서 동일하게 탈락했다. 둘 다
0.50 이상이어서 floor bypass 대상이 아니며, 이번 gate에서 규칙을 수정하지 않았다.

Negative UN02(`Helidon`)는 actor mismatch로 `claimSupport=CONTRADICTED`, `directSupport=false`,
D1 qualified candidate 0건인데도 D0와 D1 final에 남았다. UN05(`Redwood Stream Core`)도
candidate comparison으로 `claimSupport=UNSUPPORTED`, `directSupport=false`, D1 qualified 0건인데
D0와 D1 final에 남았다. 즉 두 false positive는 이번 direct-support bypass 확장이 새로 만든
것은 아니지만 unseen final 안전성 기준을 충족하지 못한다.

| Safety 유형 | D1 reject |
|---|---:|
| Actor / non-participation | 3/4 |
| Mere mention / review / comparison | 2/3 |
| Negation / replacement | 2/2 |
| Absent identifier | 1/1 |

UN08(`Envoy가 아니라 HAProxy`)은 evaluator의 `directSupport=true` 위험이 관찰됐지만 affirmative
action binding을 포함한 conservative shadow 조건을 충족하지 못해 qualified 0건, D0/D1 final
모두 reject였다. 이는 shadow 자체가 negation negative를 새로 살리지 않았다는 근거다.

| 명령 | 결과 |
|---|---|
| unseen dataset freeze hash 확인 | PASS, bytes/hash 일치 |
| frozen unseen D0/D1 focused run | FAIL, D1 Negative 8/10으로 safety assertion 실패 |
| `git diff --check` | PASS |

Production source, threshold, direct-support 규칙, bypass, retrieval, fallback, ranking,
consolidation은 수정하지 않았다. Backend 전체 unit과 integration, commit, push는 `NOT_RUN`이다.

신규 FP/FN은 0이므로 bypass 확장 자체의 즉시 NO-GO 근거는 없지만, actor와 mere-mention
negative가 기존 final 경로에서 통과하는 unseen confound를 분리 진단하기 전에는 Production
적용을 승인할 수 없다. 최종 추천은 `추가 검증 필요`, gate 판정은 `NEEDS_ADJUSTMENT`다.

## Unseen final false-positive focused diagnostic

Frozen unseen dataset은 수정하지 않고 UN02, UN05와 정상 대조 UP01, UN01, UN04만 실제
`ProductionSearchDecisionTracer`로 재실행했다. QuerySignals와 private eligibility reason은
evaluation-only reflection으로 관찰했고 SearchService 응답 parity는 5/5였다.

### UN02 — Helidon actor false positive

Original `Helidon`은 bare identifier, intent `GENERAL`이다. QuerySignals는
`requiredIdentifiers=[helidon]`, `positiveClaimQuestion=false`, `directAnchorRequired=true`이고,
requirements는 `claimQuestion=false`, `actorRequired=true`, `actions=[]`,
`entities=[helidon]`이다. Claim support는 `CONTRADICTED`, `directSupport=false`,
`ACTOR_MISMATCH`였고 original singleton 및 eligibility 모두
`CLAIM_SUPPORT_CONTRADICTED:ACTOR_MISMATCH`로 정상 reject됐다. Original dense rank/score는
1 / 0.489877936811081이다.

Original 결과가 비자 Production은 bare identifier fallback `Helidon 경험`을 실행했다. 이
variant는 dense rank/score 1 / 0.57565273834351이었다. Composite QuerySignals의
`positiveClaimQuestion`은 true지만 Structured requirements는 `claimQuestion=false`,
`actorRequired=false`, `actions=[]`로 갈라졌다. 따라서 evaluator는 candidate를
`SUPPORTED`, `directSupport=false`, reason `NON_CLAIM_QUERY`로 반환했다. Contradicted/Unsupported
rejection branch를 모두 타지 않고 score가 0.50 이상이라 rejection reason이 빈 목록이 됐다.
이후 source consolidation representative → eligibility PASS → query-evidence representative →
ranking 1 → Top5 → direct-anchor post-filter PASS → SearchService final 1위로 남았다.

### UN05 — Redwood Stream Core mere-mention false positive

`Redwood Stream Core`는 bare multi-word identifier, intent `GENERAL`이다. QuerySignals는
`requiredIdentifiers=[core, redwood, stream]`, `positiveClaimQuestion=false`,
`directAnchorRequired=false`이고 requirements는 `claimQuestion=false`, `actorRequired=true`,
`actions=[]`, `entities=[core, redwood, stream]`이다. Claim support는 `UNSUPPORTED`,
`directSupport=false`, reasons `ENTITY_NOT_BOUND_TO_ACTION`, `ACTION_NOT_SUPPORTED`였다.

그러나 GENERAL rejection policy가 `UNSUPPORTED`를 reject하는 조건은
`claimRequirements.claimQuestion() && score >= 0.50`이다. `claimQuestion=false`라 해당 branch가
건너뛰어졌고 dense rank/score 1 / 0.630594830236235가 floor를 통과해 rejection reason은 빈
목록이었다. 이후 source consolidation representative → eligibility PASS → query-evidence
representative → ranking 1 → Top5 → post-filter PASS → SearchService final 1위가 됐다.

### 정상 대조

- UP01 `HikariCP`: bare identifier이지만 claim support가 `SUPPORTED/directSupport=true`이고
  score 0.521702354360247로 eligibility와 final을 통과했다.
- UN01 `Deno 사용 경험`: requirements가 `claimQuestion=true`, `actorRequired=true`,
  `actions=[USE]`; `CONTRADICTED:ACTOR_MISMATCH`로 eligibility에서 reject됐다.
- UN04 `Nomad 사용 경험`: requirements가 `claimQuestion=true`, `actorRequired=true`,
  `actions=[USE]`; `UNSUPPORTED` reasons가 claim-question rejection branch에 들어가 eligibility에서
  reject됐다.

공통 원인은 기술명이 아니라 query-form과 rejection policy의 결합이다. UN05는 bare query의
`claimQuestion=false` 때문에 structured `UNSUPPORTED`가 무시되고, UN02는 fallback rewrite가
original의 `actorRequired=true/CONTRADICTED` 의미를 보존하지 못해 `NON_CLAIM_QUERY`로 바뀐다.

후속 최소 수정 후보는 (1) fallback retrieval에서도 original query의 claim/actor policy를
보존하고, (2) bare entity query에서 `actorRequired=true`이면 `UNSUPPORTED` eligibility를
claim question과 동일하게 적용하는 일반 규칙이다. 수정 시에는 현재 score만으로 통과하는
새로운 표현의 실제 bare-identifier Positive를 과도하게 제거할 위험과 기존 fallback recall
회귀를 별도로 검증해야 한다. 이번 진단에서는 구현하지 않았다.

| 명령 | 결과 |
|---|---|
| UN02/UN05 + 대조 3건 focused trace | PASS, 5/5 Production parity |
| `git diff --check` | PASS |

Production source와 floor bypass diff는 0이다. 전체 backend/integration, commit, push는
`NOT_RUN`이다. 원인은 재현·분리됐으므로 diagnostic 판정은 `PASS`지만, Production 적용
준비 상태는 두 일반 규칙을 별도 Phase에서 검증할 때까지 `NEEDS_ADJUSTMENT`다.
