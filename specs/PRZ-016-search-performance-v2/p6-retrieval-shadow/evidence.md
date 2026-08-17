# P6 Retrieval Architecture Shadow Benchmark Evidence

- 상태: `DONE — NO_GO`
- 실행일: 2026-08-14
- 범위: evaluation/benchmark/test/spec only
- 환경: owner `1`, ACTIVE 문서 2개, chunk 18개, PostgreSQL+pgvector `15433`, Ollama `bge-m3`
- Production 적용·DB/schema/index 변경: 0
- Git commit/push/PR: 수행하지 않음
- authoritative raw result: [p6-b-results.json](p6-b-results.json)

P6-A는 `2026-08-14T09:55:11.235637300Z`, stress freeze는
`2026-08-14T09:57:29.561165300Z`, P6-B authoritative 재실행은
`2026-08-14T10:23:06.507898100Z`에 각각 기록됐다. 따라서 P6-A → freeze → H2 순서를
지켰다. AUDIT 중 P6-A/P6-B 최초 runner가 후보 위치와 최종 evidence 위치를 섞어 P5 H36을
잘못 PASS 처리한 문제를 발견했다. 기존 P5 runner와 동일하게 최종 evidence chunk/page만
정답 위치로 인정하도록 evaluation-only 판정을 고쳤고 P6-B를 재실행했다. H36은 다시 FAIL,
P5 D0는 역사 기준인 Top1 50.00%, Recall@3/5 61.11%, MRR@5 0.5509로 복원됐다. 시간 순서
증거인 P6-A raw 파일은 덮어쓰지 않았고, 아래 최종 수치는 corrected P6-B를 기준으로 한다.

## 완료 보고

1. **P6 전체 목적:** PRZ-008 P13의 PostgreSQL lexical + dense + RRF를 현재 P0~P4
   pipeline에 shadow로 연결하고, generic literal evidence gate가 hybrid의 false positive를
   일반적으로 제어하면서 recall을 보존하는지 평가했다.

2. **Production 변경 파일 수:** P6에서 `0개`. P0~P5 작업으로 이미 dirty였던 production
   파일은 시작 시점 그대로 보존했다. P6 전후 30개 production search source aggregate는
   모두 `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31`이다.

3. **재사용한 PRZ-008 P13/P14 코드와 파일:**
   `SearchEvaluationLexicalCandidateRepository.java`의 owner/ACTIVE-scoped PostgreSQL
   `simple` FTS `plainto_tsquery` Top20과
   `SearchEvaluationHybridRrfProfile.java`의 chunk-ID merge/dedup 및 `k=60` RRF를 수정 없이
   재사용했다. P13/P14 결과와 `SearchEvaluationBaselineTest`도 역사 비교 근거로 확인했다.

4. **새 evaluation-only 실행 파일 수와 목록:** `5개`.
   `P6ShadowBenchmarkTest.java`, `P6StressSetFreezeTest.java`,
   `SearchEvaluationLiteralAnchorExtractor.java`, `SearchEvaluationLiteralEvidenceGate.java`,
   `SearchEvaluationLiteralAnchorExtractorTest.java`. 이와 별도로 Gradle task 2개와 P6 dataset,
   ground truth, freeze/raw result/evidence 자산을 추가했다.

5. **Identifier Stress Set 생성 시점:** H2 구현 전 작성·직접 검증 후
   `2026-08-14T09:57:29.561165300Z`에 freeze했다. P6-A raw 결과보다 138.326초 뒤이며 H2
   구현보다 앞선다.

6. **Stress Set Positive / Negative 수:** 총 28개, Positive 14개 / Negative 14개. P5의
   알려진 3개 false positive와 별개인 새 identifier를 포함했다.

7. **Stress Set freeze hash:** dataset
   `0dfdd5aa5d51fb8f5116904ef4f998b5f4ce73e35a5657159f35d80fc15859f5`, ground truth
   `f356a3ce914343caf49ed8edc4131cc29c933599708cc390c0eec3d9f8b218d8`, 직접 확인 corpus
   aggregate `ab09ef6d206ca62b7773fa0a1d604192bf06bdbff9cebbb59907abb4d73c8d81`.

8. **H2 구현 이후 Stress Set 변경 여부:** 없음. P6-B 시작 시 freeze hash와
   `FROZEN_PRE_H2`/`h2ImplementedAtFreeze=false`를 다시 assert했고 최종 `Get-FileHash`도 일치했다.

9. **D0 구조:** 현재 production `SearchService.searchCareerEvidenceV2`를 그대로 호출하는
   bge-m3 dense Top20 + threshold 0.50 + P1/P2/P3/P4 기준선이다. 별도 shadow orchestration의
   chunk ID·score·distance가 production 응답과 매 query 동일함을 assert했다.

10. **L1 구조:** 같은 Q0/variant embedding과 query로 owner/ACTIVE-scoped PostgreSQL lexical
    Top20만 조회하고 기존 selection contract를 통과시켰다. Production SQL/index는 바꾸지 않았다.

11. **H1 구조:** Dense Top20과 lexical Top20을 chunk ID로 dedup하고 `1/(60+rank)` 합으로
    shadow 정렬했다. 선택된 dense candidate의 기존 score/distance를 유지했다. Lexical-only
    candidate는 이번 18-chunk corpus에서 0건이었다.

12. **H2 Generic Literal Gate 구조:** H1의 각 candidate와 현 P4 Evidence Expansion이 고른
    bounded evidence에서 모든 strong anchor를 검사한다. owner/document/ACTIVE version scope가
    맞지 않거나 anchor 하나라도 없으면 reject하며 production API에는 diagnostics를 노출하지 않는다.

13. **Literal strong anchor 판정 규칙:** 숫자+단위, 구체적 영문 multi-token/version phrase,
    CamelCase, ALL CAPS acronym, 하이픈·점·슬래시·코드형 identifier를 사용했다. `API`, `DB`,
    `UI`, `UX`, `ID`와 일반 한국어/영어 단어는 단독 hard gate에서 제외했다. 여러 anchor는 모두
    만족해야 한다.

14. **Normalization 규칙:** Unicode NFKC, lowercase, trim/중복 공백, 보수적 구두점 및
    하이픈/공백 정규화만 적용했다. synonym, 의미 추론, 기술명 변환, solution injection은 없다.

15. **Corpus rarity 사용 방식:** token document frequency와 rarity를 diagnostics에만 기록했다.
    2문서·18chunk의 희귀도는 gate 판정 조건이 아니다.

16. **Q0 Dense Candidate Recall@20:** development 100.00%, P5 diagnostic 100.00%, frozen
    stress 100.00%, regression guards 91.67%.

17. **Q0 Lexical Candidate Recall@20:** development 8.93%, P5 diagnostic 0%, frozen stress
    0%, regression guards 33.33%.

18. **Q0 H1 Candidate Recall@20:** development 100.00%, P5 diagnostic 100.00%, frozen stress
    100.00%, regression guards 91.67%. 어느 dataset에서도 D0보다 개선되지 않았다.

19. **Q0 H2 usable Candidate Recall@20:** development 89.29%, P5 diagnostic 94.44%, frozen
    stress 100.00%, regression guards 75.00%. H1 대비 각각 -10.71pp, -5.56pp, 0pp,
    -16.67pp다. H1의 D0 대비 추가 recall은 0이므로 보존할 증가분 자체가 없었다.

20. **Dense-only / Lexical-only / Both candidate 분석:** query-candidate pair 기준
    development `1278/0/18`, P5 `864/0/0`, stress `504/0/0`, guards `297/0/9`였다. P5 D0
    positive 실패 14건 모두 GT가 Dense Top20에는 있었고 lexical에는 없었다. H1은 최종 rank를
    한 건도 개선하지 못했으며 H2는 기존 실패 H30을 candidate 단계에서 reject하고, 기존 PASS
    H32도 새로 reject했다.

21. **기존 72 D0/L1/H1/H2 Top1:** `82.14% / 10.71% / 75.00% / 69.64%`.

22. **기존 72 Recall@3:** `85.71% / 10.71% / 82.14% / 76.79%`.

23. **기존 72 Recall@5:** `85.71% / 10.71% / 82.14% / 76.79%`.

24. **기존 72 MRR@5:** `0.8363 / 0.1071 / 0.7798 / 0.7262`.

25. **기존 72 Negative FPR:** 네 mode 모두 `0%`.

26. **기존 72 regression 수:** D0 PASS → H1 FAIL `2건`(A06, A07), D0 PASS → H2 FAIL
    `5건`(A06, A07, A12, E01, E05). H1 → H2 추가 회귀는 A12, E01, E05 `3건`이다.
    목표 0건을 충족하지 못했다.

27. **P5 diagnostic D0/L1/H1/H2 Top1:** `50.00% / 0% / 50.00% / 47.22%`.

28. **P5 diagnostic Recall@3:** `61.11% / 0% / 61.11% / 58.33%`.

29. **P5 diagnostic Recall@5:** `61.11% / 0% / 61.11% / 58.33%`.

30. **P5 diagnostic MRR@5:** `0.5509 / 0 / 0.5509 / 0.5231`.

31. **P5 diagnostic Negative FPR:** `25.00% / 0% / 25.00% / 0%`.

32. **P5 Candidate Recall@20 변화:** D0 100.00% → H1 100.00%로 개선 0pp, H2 94.44%로
    H1 대비 -5.56pp다. 작은 corpus라 dense Top20이 매 query 18chunk 전체를 이미 회수해 lexical
    channel이 추가 후보를 만들 수 없었다.

33. **P5 3 false positive D0/H1/H2 결과:** OpenTelemetry/Zipkin(N02), AWS RDS
    Multi-AZ(N06), Spring WebFlux(N08)은 D0와 H1에서 모두 false positive, H2에서 모두
    `NO_RELEVANT_RESULTS`로 3/3 차단됐다.

34. **Frozen Stress Positive Recall:** H1과 H2 모두 Top1 78.57%, Recall@3/5 85.71%,
    MRR@5 0.8214, Candidate Recall@20 100%다. H2는 H1이 성공한 stress positive를 새로 막지
    않았고 12/14를 Top5에서 보존했다.

35. **Frozen Stress Negative FPR:** D0/H1은 5/14=`35.71%`, H2는 0/14=`0%`.

36. **Partial-anchor negative 결과:** PARTIAL_TOKEN 7/7, SEMANTIC_NEAR 2/2,
    VERSION_NEAR_MISS 2/2, NUMERIC_NEAR_MISS 2/2, CODE_PARTIAL 1/1을 H2가 모두 차단했다.

37. **Legacy Kubernetes/Kafka regression:** LG02 Kubernetes와 LG03 Kafka는 D0/L1/H1/H2
    모두 `NO_RELEVANT_RESULTS`; PASS.

38. **과거 `배포` regression 결과:** LG01 `배포`는 D0에서 결과 없음, L1/H1/H2에서 correct
    rank 1로 회수됐다. P13의 알려진 regression을 재현하지 않고 개선했다.

39. **Numeric exact / near-miss 결과:** exact는 2,329행과 1,480건만 correct rank 1로
    `2/5 PASS`; 4,400회, 675건, 1,654건은 D0/H1/H2 모두 정답 위치를 반환하지 못해 `3/5 FAIL`.
    near-miss 4,401회/676건/2,330행은 H2에서 3/3 차단했다. H2가 exact를 새로 회귀시키지는
    않았지만 P1 contract 전체 회귀 suite는 통과하지 못했다.

40. **Positive Strong Identifier regression:** D0/H1에서 성공한 Spring Boot, Docker Compose,
    Nginx, TourAPI, FOR UPDATE SKIP LOCKED 중 H2가 Nginx(PI04)를 새로 막아 `1건 regression`.
    Redis(PI02)는 D0부터 정답 미회수였다. H2 결과는 4/6 correct다.

41. **H2 gate rejection 대표 사례와 이유:** `Redis Streams`, `Nginx Unit`, `TourAPI v2`는
    candidate와 bounded expanded evidence 모두에 전체 literal이 없어
    `MISSING_STRONG_LITERAL_ANCHOR`로 reject됐다. 각 missing anchor와 candidate/expanded 발견
    여부, evidence chunk/page, scope validity가 raw JSON에 남는다.

42. **H2 gate positive acceptance 대표 사례와 이유:** `GCP Ubuntu`+`HTTPS`,
    `FOR UPDATE SKIP LOCKED`, `TourAPI`+`680건`+`6.8초`는 같은 bounded evidence scope에서
    모든 anchor를 찾아 `ALL_STRONG_ANCHORS_FOUND`로 통과했다.

43. **P3 sequential variant behavior 불변 여부:** production 코드는 0건 변경했고 D0 shadow의
    선택 chunk ID/score/distance를 실제 production 응답과 query마다 동일하게 assert했다.
    각 mode도 Q0 → 필요 시 Q1 → 필요 시 Q2 및 첫 선택 시 종료 순서를 유지했다. variant 추가,
    병렬화, production early-stop 변경은 없다.

44. **Hybrid 때문에 end-to-end early-stop이 달라진 사례 여부:** 있음. H1은 development D01,
    D02, D03, D07, D08에서 D0의 Q0 CONTINUE를 Q0 EARLY_STOP으로 바꿨다. H2는 gate 차단 때문에
    development A12/E01/E05, P5 H32/N02/N06/N08, stress SN02/SN06/SN08/SN13/SN14,
    guard PI04 등의 Q0 stop을 CONTINUE로 바꿨다. Production 도입 위험이다.

45. **Owner isolation:** PASS. 임의의 `Long.MAX_VALUE` owner로 dense/lexical 조회가 모두 빈
    결과였고 모든 corpus/gate SQL이 document/version/chunk owner를 동시에 제한했다.

46. **ACTIVE isolation:** PASS. `document.active_version_id = version.id`와
    `version.status='ACTIVE'`를 강제했고 비ACTIVE/non-active-version chunk와 결과의 교집합이
    없음을 assert했다.

47. **API score/distance 불변 여부:** PASS. D0 모든 query의 chunk ID, score, distance가 실제
    API pipeline과 동일했다. H1/H2 RRF 값은 API 값으로 대체하지 않았고 선택된 dense
    candidate의 score/distance를 그대로 유지했다. API model 변경은 0건이다.

48. **D0 latency:** 165 samples, avg `271.980ms`, median `247.889ms`, P95 `469.207ms`.
    Q0 dense DB는 avg `3.846ms`, median `3.735ms`, P95 `4.745ms`.

49. **L1 latency:** 165 samples, avg `284.085ms`, median `229.840ms`, P95 `480.977ms`.
    Q0 lexical DB는 avg `2.470ms`, median `2.425ms`, P95 `3.288ms`.

50. **H1 latency:** 165 samples, avg `261.457ms`, median `234.776ms`, P95 `472.007ms`.
    logical sequential variant channel 실행은 dense 181회/lexical 181회(Q0 165, Q1 12, Q2 4),
    Q0 fusion은 avg `0.079ms`, median `0.043ms`, P95 `0.097ms`.

51. **H2 latency:** 165 samples, avg `284.195ms`, median `241.892ms`, P95 `509.390ms`.
    logical sequential variant channel 실행은 dense/lexical 각각 190회(Q0 165, Q1 21, Q2 4).
    Q0 gate per-candidate 2,970 samples는 avg `8.972ms`, median `9.277ms`, P95 `13.774ms`.
    현재 small corpus 결과를 production scale 성능으로 일반화하지 않는다.

52. **전체 backend regression test:** `test`는 515 tests, 0 failure/error, 16 skipped로 PASS.
    `integrationTest --rerun-tasks`는 112 tests, 1 failure, 7 skipped로 FAIL했고, 실패한
    `PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql`
    단독 재실행도 1/1 FAIL로 재현됐다. `PRIZM API를 출시한 이력이 있나요?`/`PRIZM- API를
    배포했습니다.` 시나리오에서 expected `NO_EVIDENCE`, actual `NO_RELEVANT_RESULTS`다. 이는
    P6 evaluation code가 아닌 시작 시점 P0~P5 production 동작이며 production 수정 금지로 남겼다.

53. **`git diff --check`:** PASS, 출력 없음.

54. **Production search diff 0 여부:** PASS for P6. P6-B가 전후 30개 파일 수와 aggregate hash
    동일, `productionSearchMutation=0`을 기록했다. 전체 worktree에는 사용자/기존 P0~P5
    production 변경이 있으므로 repository 전체가 clean하다는 뜻은 아니다. Flyway, schema,
    index, runtime config, frontend 변경은 P6에서 0건이다.

55. **P6 최종 판정: `NO_GO`.** H1이 D0 Candidate Recall을 어느 dataset에서도 개선하지 못했고,
    H2는 safety stress와 알려진 false positive를 차단했지만 development candidate recall
    -10.71pp, D0 PASS→FAIL 5건, positive identifier regression 1건, P5 final metric 하락을
    만들었다. Numeric exact guard와 전체 integration regression도 통과하지 못했다.

56. **다음 권고:** PostgreSQL lexical+RRF+현재 H2 gate를 production 설계 후보로 올리지 않는다.
    18chunk보다 큰 대표 corpus에서 lexical analyzer/query 구성과 후보 pool 효과를 먼저 별도
    설계하고, generic gate는 positive identifier/근거 위치 보존을 다시 설계한 뒤 새 development
    stress로 검증해야 한다. 이번 작업에서는 production 수정, migration/index, P7, BGE-M3 sparse,
    새 final holdout, commit/push/PR을 시작하지 않는다.

## 실행·감사 명령

```text
.\gradlew.bat p6FreezeIdentifierStressSet --no-daemon                         PASS
.\gradlew.bat p6ShadowBenchmark --no-daemon -Pp6Phase=A                      PASS (pre-H2 raw)
.\gradlew.bat p6ShadowBenchmark --no-daemon -Pp6Phase=B                      PASS (authoritative)
.\gradlew.bat test --no-daemon                                               PASS
.\gradlew.bat integrationTest --no-daemon --rerun-tasks                      FAIL (112/1/7)
.\gradlew.bat integrationTest --no-daemon --tests <failing test>             FAIL reproduced (1/1)
docker compose --file compose.yaml --env-file .env ... config --quiet        PASS
git diff --check                                                              PASS
stress dataset/ground-truth SHA-256 recheck                                  PASS
P6 credential-assignment pattern scan                                        PASS (no match)
```

P6 benchmark DB 작업은 read-only이며 `databaseMutation=0`이다. Raw JSON에는 per-query Q0 channel,
candidate, variant, gate, score/distance와 latency diagnostics가 보존돼 있다.
