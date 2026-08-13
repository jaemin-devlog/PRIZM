# PRZ-008 검색 개선·평가 비교

## 문서 목적과 상태

이 문서는 PRZ-008 P0–P15에서 수행한 검색 개선과 평가 실험을 한곳에서 비교하고,
각 방식이 왜 적용·보류·비권고됐는지 추적하기 위한 기록이다.

여기서 **Production 적용**은 통합 source `2190d47ff013384cf9d8c441449149233f79b0e9`의
Production source와 기본 검색 경로에 구현됐다는 뜻이다. PR #40 merge
`9b24808b37424f2d11ca0afe374d5703c81868fc`로 `main`에 반영됐다. 반대로 P8–P15의
실험 profile, PDF 분할안, PostgreSQL FTS, BGE-M3 Sparse, reranker는 평가 전용이며
Production 검색 경로에 적용되지 않았다.

이 문서 작성 시점에도 최종 Production 검색 방식은 선택하지 않았다. 현재 비교 후보는
다음 두 가지다.

1. Dense + 제한적 exact-token rescue
2. Dense + PostgreSQL FTS + RRF

두 후보 모두 추가 검증 전에는 Production에 적용하지 않는다.

## 평가 목적과 기준

평가의 우선순위는 다음과 같다.

1. owner·현재 `ACTIVE` version·embedding 검증·완료 Claim Gate 같은 안전 계약을 지킨다.
2. 관련 근거의 Top1, Recall@3, Recall@5, MRR@5를 높인다.
3. 문서에 없는 경험에서 무관 결과를 늘리지 않는다.
4. 개선 폭이 작다면 복잡도, 추가 조회, GPU·메모리·지연 비용이 작은 방식을 우선한다.

Top1·Recall·MRR의 분모는 관련 근거가 있는 질문이다. 없는 경험 오탐은 무근거 질문 중
하나 이상의 무관 결과를 반환한 질문 수와 반환 결과 수를 함께 기록한다. score는 cosine
similarity에서 유도된 정렬 신호이며 정확도나 확률로 해석하지 않는다.

## 평가 데이터셋

P5와 P8 이후 평가는 corpus와 목적이 다르므로 수치를 직접 합치거나 증감률로 비교하지
않는다.

- **구분:** P5
  - 데이터: PRZ-008 v2.3 합성 TUNING
  - 질문: 15
  - 구성: 근거 8, 무근거 7; 실제 후보는 질의당 11개
  - 목적: `0.50`·Top20 유지 여부 확인
- **구분:** P8–P15
  - 데이터: 실제 포트폴리오 PDF 5쪽 + 이력서 PDF 2쪽
  - 질문: 40
  - 구성: 근거 34, 무근거 6
  - 목적: 실문서 검색 품질과 후속 실험 비교

실문서 40문항은 query type별로 직접 기술명 4, 기술명 표기 변형 4, 자연어
패러프레이즈 4, 프로젝트 4, 트러블슈팅 4, 성과·수치 4, 간접 의미 4, 없는 경험 4,
짧고 애매한 GENERAL 4, 완료 배포·출시 질문 4개다. Production chunking의 corpus는
18개 청크라 모든 질의에서 Dense Top20이 실제 corpus 전체를 포함한다. 따라서 P8–P15의
결과는 큰 corpus에서의 Top20 candidate recall을 증명하지 않는다.

주요 근거 산출물은 다음과 같다.

| 단계 | 근거 |
|---|---|
| P5 | `local/search-evaluation/prz008-tuning-v23-p5-current-20260812/`, `prz008-tuning-v23-p5-profile-20260812/` |
| P8 | `local/search-evaluation/p8-real-docs/output/p8-analysis.json` |
| P9 | `local/search-evaluation/p9-pdf-chunking/p9-comparison.json` |
| P10 | `local/search-evaluation/p10-pdf-page-dedup/p10-comparison.json` |
| P11 | `local/search-evaluation/p11-section-paragraph-v2/p11-comparison.json` |
| P12 | `local/search-evaluation/p12-short-query-rescue/p12-comparison.json` |
| P13 | `local/search-evaluation/p13-hybrid-rrf/production-final/`, `hybrid-corrected-final/` |
| P14 | `local/search-evaluation/p14-bge-m3-sparse/final/` |
| P15 | `local/search-evaluation/p15-bge-reranker/final/` |

`local/` 산출물은 Git 제외 대상인 실행 근거다. 이 문서의 수치는 위 JSON과 최종 비교
리포트에서 확인했으며 서로 다른 실행의 embedding 지연 차이를 알고리즘 비용으로
간주하지 않았다.

## P0–P7 Production 검색 개선

- **단계:** P0
  - Production source의 변화: 기존 owner·ACTIVE version·embedding·완료 주장 안전 계약과 strict baseline을 regression test로 고정
  - 검색 결과 영향: 없음
  - 판정: 적용
- **단계:** P1
  - Production source의 변화: `GENERAL`과 `COMPLETED_RELEASE_EVIDENCE` intent 경계 추가. unsupported 완료 질문도 completed에 남도록 fail-closed
  - 검색 결과 영향: 없음; 두 intent 모두 당시 strict profile 사용
  - 판정: 적용
- **단계:** P2
  - Production source의 변화: GENERAL에서 identifier·숫자·core-term coverage·explicit-evidence 문자열 조건을 hard rejection으로 사용하지 않음
  - 검색 결과 영향: 의미상 관련 Dense 후보가 문자열 불일치만으로 사라지지 않음
  - 판정: 적용
- **단계:** P3
  - Production source의 변화: NFKC·소문자화 후 `Spring Boot`, `SpringBoot`, `Springboot`, `Spring-Boot`, `spring_boot`를 `springboot`로 비교. `C++`, `C#`, `Node.js` 의미 문자는 보존
  - 검색 결과 영향: 기술명 표기 차이 비교 일관화
  - 판정: 적용
- **단계:** P4
  - Production source의 변화: GENERAL에 identifier 최대 `0.020`, core-term 최대 `0.005`, number 최대 `0.005`의 bounded boost 추가
  - 검색 결과 영향: 총 문자열 boost 최대 `0.030`; Dense가 주 신호이며 불일치 후보는 제거하지 않음
  - 판정: 적용
- **단계:** P5
  - Production source의 변화: Dense floor `0.50`, Dense candidate Top20 유지
  - 검색 결과 영향: 값 변경 없음
  - 판정: 적용(현행 유지)
- **단계:** P6
  - Production source의 변화: v2 상태를 `EVIDENCE_FOUND`, `NO_SEARCHABLE_DOCUMENTS`, `NO_RELEVANT_RESULTS`, `NO_EVIDENCE`로 구분
  - 검색 결과 영향: 결과·순위 무변경; GENERAL 빈 결과와 completed 검증 실패 의미 분리
  - 판정: 적용
- **단계:** P7
  - Production source의 변화: 선택된 결과에서 질문 관련 문장과 인접 문장을 snippet으로 생성하고 전체 content를 보존
  - 검색 결과 영향: 후보·순위·score 무변경; UI는 snippet 기본 표시와 원문 펼치기 제공
  - 판정: 적용

P0–P7 전체에서 owner 범위, 현재 `active_version_id`, `ACTIVE` 상태, 1024차원·finite·
non-zero embedding, Dense Top20, 최종 최대 5건, 완료 Claim Gate의 질문·부정·철회·정정
차단은 유지됐다.

## P5: threshold와 TopK 검증

P5는 실문서 40문항보다 앞선 **합성 TUNING 15문항** 평가다. P8 이후 수치와 합산하지
않는다.

| 항목 | 현재 제품 | composite profile |
|---|---:|---:|
| 질문 | 15 | 15 |
| Recall@20 / Direct Recall@20 | 1.0000 / 1.0000 | 1.0000 / 1.0000 |
| Direct MRR@5 / @20 | 1.0000 / 1.0000 | 1.0000 / 1.0000 |
| nDCG@5 | 0.9783 | 0.9783 |
| 근거 질문 오거부율 | 0.0000 | 0.0000 |
| 무근거 거부율 | 0.5714 | 0.5714 |
| 근거 top-1 score 최소 | 0.5582 | 0.5582 |
| 후보 수 | 질의당 11 | 질의당 11 |
| 평균 / P95 검색 시간 | 136.27 / 149ms | 132.53 / 156ms |

검토한 Dense floor 후보는 `0.50`, `0.45`, `0.40`이었다. `0.50`에서 관련 근거가
누락된 사례가 없었고 근거 top-1 최소값도 `0.5582`였다. 따라서 더 낮은 floor가
필요하다는 근거가 없었다. 또한 corpus가 질의당 11개 후보뿐이라 Top20이 이미 전부를
포함했고 Top30은 후보를 추가하지 못한다. 큰 corpus에서 Top20 대 Top30의 차이를
검증한 결과는 아니다.

**최종 선택:** 가장 보수적인 `0.50 / Top20`을 그대로 유지했다.

## P8: 실제 문서 Production baseline

| 지표 | 결과 |
|---|---:|
| 관련 질문 | 34 |
| Top1 | 26/34, 76.47% |
| Recall@3 | 27/34, 79.41% |
| Recall@5 | 27/34, 79.41% |
| MRR@5 | 0.7794 |
| 없는 경험 오탐 | 1/6 질문, 1개 결과 |

강점은 자연어 패러프레이즈·프로젝트·트러블슈팅·기술명 표기 변형이 각각 4/4
Top1이었다는 점이다. 주요 실패는 다음과 같았다.

- `알림` `0.494675`, `동시성` `0.490510`, 이메일/Kakao 통합 `0.495141`은 관련 Dense
  후보가 1위였지만 `0.50` 바로 아래라 반환되지 않았다.
- `배포`의 가장 좋은 라벨 정답은 `0.431200`으로 경계 누락이 아니었다.
- 엑셀 `2,329 / 675 / 1,654` 정답은 2위였다.
- 없는 `결제 시스템`은 `0.523321`의 무관 청크 1건을 반환했다.
- 같은 PDF 페이지에서 혼합 주제 청크 대표가 먼저 선택돼 정답 청크가 사라지는 사례가
  있었다.

P8은 측정 단계이므로 Production 변경이나 채택 판정은 하지 않았다.

## P9–P11: chunking과 page dedup 실험

### 청크 크기

- **방식:** Production
  - 청크 수: 18
  - 최소: 145자
  - 평균: 637.00자
  - 최대: 800자
- **방식:** P9 `section-paragraph-v1`
  - 청크 수: 33
  - 최소: 59자
  - 평균: 306.79자
  - 최대: 600자
- **방식:** P11 `section-paragraph-v2`
  - 청크 수: 32
  - 최소: 178자
  - 평균: 320.16자
  - 최대: 600자

P9 v1은 섹션·문단 경계를 살려 이메일/Kakao 통합과 `알림`을 Top1으로 복구했다.
그러나 59자 조각을 만들고 주변 문맥을 잃어 `FOR UPDATE SKIP LOCKED`가 `0.493406`으로
내려가 누락됐으며, 외부 API 동시 작업 수가 사라지고 TourAPI가 1위에서 2위로
하락했다. 없는 Kafka도 새로 오탐했다.

P10은 같은 페이지 청크를 모두 검증·ranking한 뒤 최종 결과에서 dedup하는 방식을
실험했다.

- **P10 방식:** Production 조기 dedup
  - Top1: 76.47%
  - Recall@3: 79.41%
  - Recall@5: 79.41%
  - MRR@5: 0.7794
  - 없는 경험 오탐: 1/6, 1건
- **P10 방식:** Production chunking + deferred dedup
  - Top1: 73.53%
  - Recall@3: 79.41%
  - Recall@5: 79.41%
  - MRR@5: 0.7647
  - 없는 경험 오탐: 1/6, 1건
- **P10 방식:** section-v1 + 조기 dedup
  - Top1: 73.53%
  - Recall@3: 79.41%
  - Recall@5: 79.41%
  - MRR@5: 0.7647
  - 없는 경험 오탐: 2/6, 2건
- **P10 방식:** section-v1 + deferred dedup
  - Top1: 76.47%
  - Recall@3: 82.35%
  - Recall@5: 82.35%
  - MRR@5: 0.7892
  - 없는 경험 오탐: 2/6, 2건

section-v1 + deferred dedup은 외부 API 동시 작업 수를 Top1으로 복구했지만 TourAPI는
3위로 더 내려갔다. Production chunking에서 deferred dedup만 적용하면 Top1과 MRR이
하락했다. 완료 Claim Gate 결과는 네 completed 질문에서 모두 유지됐다.

P11 v2는 짧은 조각을 같은 섹션의 주변 내용과 합쳐 최소 길이를 59자에서 178자로
높였다. TourAPI는 v1의 2위에서 Top1으로 회복했지만, 외부 호출을 commit 이후로
분리한 설계가 새로 누락됐다. `알림`과 이메일/Kakao 복구는 유지했으나 전체 지표는
v1보다도 낮았다.

- **방식:** Production
  - Top1: 76.47%
  - Recall@3: 79.41%
  - Recall@5: 79.41%
  - MRR@5: 0.7794
  - 없는 경험 오탐: 1/6, 1건
- **방식:** section-v1
  - Top1: 73.53%
  - Recall@3: 79.41%
  - Recall@5: 79.41%
  - MRR@5: 0.7647
  - 없는 경험 오탐: 2/6, 2건
- **방식:** section-v2
  - Top1: 73.53%
  - Recall@3: 76.47%
  - Recall@5: 76.47%
  - MRR@5: 0.7500
  - 없는 경험 오탐: 2/6, 2건

**판정:** P9 v1, P10 deferred dedup, P11 v2 모두 Production 적용 비권고다. 특정 실패를
고쳤지만 전체 지표나 오탐이 악화됐고 새로운 회귀가 생겼다.

## P12: 짧은 GENERAL exact-token rescue

평가 전용 단일 조건은 다음과 같았다.

- intent가 `GENERAL`이고 기존 Production 결과가 비어 있어야 한다.
- 정규화한 질문이 2–4 code point의 정확히 한 token이어야 한다.
- 후보 본문에 동일한 normalized token이 substring이 아닌 token 단위로 존재해야 한다.
- 원래 Dense score가 `0.49 <= score < 0.50`이어야 한다.
- 최대 1건만 살리며 원래 score·distance를 그대로 반환한다.

| 지표 | Production | P12 rescue |
|---|---:|---:|
| Top1 | 76.47% | 82.35% |
| Recall@3 | 79.41% | 85.29% |
| Recall@5 | 79.41% | 85.29% |
| MRR@5 | 0.7794 | 0.8382 |
| 없는 경험 오탐 | 1/6, 1건 | 1/6, 1건 |

변경된 문항은 `알림`과 `동시성` 두 개뿐이며 모두 원점수 그대로 Top1으로 복구됐다.
`Redis`처럼 이미 성공한 결과, completed 질문, `배포`, Kubernetes, Kafka, 결제 시스템은
변하지 않았다. `배포` 정답 `0.431200`은 의도적으로 좁힌 구간 밖이었다.

40문항에는 rescue 조건을 실제로 통과하는 짧은 한 단어 무근거 질문이 없다. 따라서
새 오탐 0건은 유효한 관측이지만 unseen 단어까지 안전하다는 일반화 근거는 아니다.

**판정:** 가장 단순하고 성능 개선 근거가 강한 현재 후보로 **보류**한다. Production에는
아직 적용하지 않았다.

## P13: Dense + PostgreSQL FTS + RRF

평가 전용 lexical branch는 현재 owner의 active version 본문에
`to_tsvector('simple', content) @@ plainto_tsquery('simple', normalizedQuery)`를 적용하고
`ts_rank_cd`로 Top20 순위를 만들었다. Dense Top20과 lexical Top20을 가중치 없는
1-based RRF `1 / (60 + rank)`로 합쳤다. lexical score 크기를 Dense score에 더하지
않았다. GENERAL에서 lexical 채널은 독립 후보 경로지만 completed는 기존 Dense `0.50`과
Claim Gate를 그대로 유지했다.

| 지표 | Production | P13 hybrid |
|---|---:|---:|
| Top1 | 76.47% | 82.35% |
| Recall@3 | 79.41% | 85.29% |
| Recall@5 | 79.41% | 85.29% |
| MRR@5 | 0.7794 | 0.8382 |
| 없는 경험 오탐 | 1/6, 1건 | 1/6, 1건 |

`알림`과 `동시성`을 Top1으로 복구해 headline 지표는 P12와 같았다. 그러나 lexical
branch가 비어 있지 않은 질문은 5/40뿐이었다. `simple` config와 `plainto_tsquery`의 AND
조건 때문에 이메일/Kakao, `FOR UPDATE SKIP LOCKED 사용 경험`, 엑셀 수치처럼 긴
혼합 질의는 lexical 결과가 없었다. `배포`는 관련 청크도 찾았지만 같은 페이지의 무관
대표가 선택돼 빈 결과에서 무관 결과로 바뀌었다.

**판정:** hybrid 구조는 현재 최종 후보 중 하나로 **보류**한다. 다만 현재 구현 그대로의
Production 적용은 비권고다. P12와 같은 순지표 개선에 추가 SQL 조회와 `배포` 회귀가
있고, index 없는 18청크 평가 비용은 운영 규모 근거가 아니다.

## P14: Dense + BGE-M3 Sparse

현재 Ollama `/api/embed`는 1024차원 Dense 배열만 반환해 BGE-M3 sparse weights를
노출하지 않았다. 따라서 평가 환경에서 공식 FlagEmbedding 1.4.0의 `BAAI/bge-m3`
`lexical_weights`를 사용했다. Production Dense Top20과 Sparse Top20을 가중치 없는
RRF `k=60`으로 결합했으며 P12나 PostgreSQL FTS는 섞지 않았다.

- **지표:** Top1
  - Production: 76.47%
  - P13: 82.35%
  - P14 Dense+Sparse: 79.41%
- **지표:** Recall@3
  - Production: 79.41%
  - P13: 85.29%
  - P14 Dense+Sparse: 88.24%
- **지표:** Recall@5
  - Production: 79.41%
  - P13: 85.29%
  - P14 Dense+Sparse: 88.24%
- **지표:** MRR@5
  - Production: 0.7794
  - P13: 0.8382
  - P14 Dense+Sparse: 0.8333
- **지표:** 없는 경험 오탐
  - Production: 1/6, 1건
  - P13: 1/6, 1건
  - P14 Dense+Sparse: 3/6, 15건

이메일/Kakao를 Top1으로 새로 복구하고 `배포` 정답을 3위에서 찾았지만, `FOR UPDATE
SKIP LOCKED` 정답은 Top5 밖으로 밀렸다. Kubernetes와 Kafka가 각각 무관 결과 5건을
새로 반환했고 기존 결제 오탐도 1건에서 5건으로 늘었다. 낮은 양의 subword overlap도
독립 sparse 후보가 돼 18청크 전체에 영향을 준 것이 주된 원인이다.

**판정:** Recall 개선보다 오탐과 순위 회귀가 크므로 Production 적용 **비권고**다.

## P15: BGE reranker

P15는 P14가 이미 찾은 GENERAL 후보만 `BAAI/bge-reranker-v2-m3` raw logit으로
재정렬했다. 새로운 후보를 찾거나 cutoff를 추가하지 않았다. completed는 P14의 기존
결정을 그대로 반환했다.

`FOR UPDATE SKIP LOCKED` 정답은 P14 final 결과에는 없었지만 pre-final eligible pool
9위에 존재했다. reranker는 이 정답을 1위로 복구했다. 엑셀 수치도 2위에서 1위로
올랐다. 반면 외부 API 동시 작업 수 정답이 Top1에서 Top5 밖으로 사라지고, `알림`은
1위에서 3위, `동시성`과 Redis는 1위에서 2위로 하락했다.

| 지표 | P14 | P15 reranker |
|---|---:|---:|
| Top1 | 79.41% | 70.59% |
| Recall@3 | 88.24% | 88.24% |
| Recall@5 | 88.24% | 88.24% |
| MRR@5 | 0.8333 | 0.7892 |
| 없는 경험 오탐 | 3/6, 15건 | 3/6, 15건 |

순서만 바꾸고 rejection cutoff를 추가하지 않았으므로 Kubernetes·Kafka·결제 시스템의
오탐은 구조적으로 줄지 않았다.

**판정:** Top1이 Production보다도 낮고 Recall·오탐 개선 없이 지연과 GPU 비용만
증가했으므로 Production 적용 **비권고**다.

## 동일 40문항 전체 성능 비교

다음 표는 모두 동일한 P8 실문서 40문항, 즉 관련 질문 34개와 무근거 질문 6개를 쓴다.

- **방식:** P8 Production baseline
  - Top1: 26/34, 76.47%
  - Recall@3: 27/34, 79.41%
  - Recall@5: 27/34, 79.41%
  - MRR@5: 0.7794
  - 없는 경험 오탐: 1/6, 1건
- **방식:** P9 section-paragraph-v1
  - Top1: 25/34, 73.53%
  - Recall@3: 27/34, 79.41%
  - Recall@5: 27/34, 79.41%
  - MRR@5: 0.7647
  - 없는 경험 오탐: 2/6, 2건
- **방식:** P10 Production chunking + deferred dedup
  - Top1: 25/34, 73.53%
  - Recall@3: 27/34, 79.41%
  - Recall@5: 27/34, 79.41%
  - MRR@5: 0.7647
  - 없는 경험 오탐: 1/6, 1건
- **방식:** P10 section-v1 + deferred dedup
  - Top1: 26/34, 76.47%
  - Recall@3: 28/34, 82.35%
  - Recall@5: 28/34, 82.35%
  - MRR@5: 0.7892
  - 없는 경험 오탐: 2/6, 2건
- **방식:** P11 section-paragraph-v2
  - Top1: 25/34, 73.53%
  - Recall@3: 26/34, 76.47%
  - Recall@5: 26/34, 76.47%
  - MRR@5: 0.7500
  - 없는 경험 오탐: 2/6, 2건
- **방식:** P12 exact-token rescue
  - Top1: 28/34, 82.35%
  - Recall@3: 29/34, 85.29%
  - Recall@5: 29/34, 85.29%
  - MRR@5: 0.8382
  - 없는 경험 오탐: 1/6, 1건
- **방식:** P13 Dense + PostgreSQL FTS + RRF
  - Top1: 28/34, 82.35%
  - Recall@3: 29/34, 85.29%
  - Recall@5: 29/34, 85.29%
  - MRR@5: 0.8382
  - 없는 경험 오탐: 1/6, 1건
- **방식:** P14 Dense + BGE-M3 Sparse
  - Top1: 27/34, 79.41%
  - Recall@3: 30/34, 88.24%
  - Recall@5: 30/34, 88.24%
  - MRR@5: 0.8333
  - 없는 경험 오탐: 3/6, 15건
- **방식:** P15 P14 후보 + BGE reranker
  - Top1: 24/34, 70.59%
  - Recall@3: 30/34, 88.24%
  - Recall@5: 30/34, 88.24%
  - MRR@5: 0.7892
  - 없는 경험 오탐: 3/6, 15건

P12와 P13은 같은 headline 지표를 냈다. P12는 정확히 두 경계 문항만 복구했고 P13은
같은 두 문항을 복구하면서 결과 tail과 `배포` 동작까지 바꿨다. P14의 Recall@3/5가 가장
높지만 무근거 오탐이 1건에서 15건으로 증가했다. P15는 Recall을 더 높이지 못한 채
Top1을 3문항 낮췄다.

## 검색 시간과 GPU·메모리 비용

아래 시간은 서로 다른 실행의 평균/P95이므로 Ollama embedding 변동을 포함한다.
같은 행 안의 측정에는 의미가 있지만 단순 차이를 전부 알고리즘 비용으로 해석하지 않는다.

- **방식:** P8 Production baseline
  - 평균 / P95 전체 시간: 122.40 / 127ms
  - 직접 추가 비용: 없음
  - GPU·메모리: Dense Ollama 메모리 별도 미측정
- **방식:** P9 section-v1
  - 평균 / P95 전체 시간: 115.18 / 131ms
  - 직접 추가 비용: 청크 수 18→33; 별도 모델 없음
  - GPU·메모리: 추가 GPU 없음
- **방식:** P10 section-v1 + deferred dedup
  - 평균 / P95 전체 시간: 115.50 / 133ms
  - 직접 추가 비용: Java 후보 검증·최종 dedup
  - GPU·메모리: 추가 GPU 없음
- **방식:** P11 section-v2
  - 평균 / P95 전체 시간: 116.58 / 129ms
  - 직접 추가 비용: 청크 수 18→32; 별도 모델 없음
  - GPU·메모리: 추가 GPU 없음
- **방식:** P12 exact-token rescue
  - 평균 / P95 전체 시간: 114.45 / 132ms
  - 직접 추가 비용: Java exact-token 후처리; 별도 조회 없음
  - GPU·메모리: 추가 GPU 없음
- **방식:** P13 PostgreSQL FTS + RRF
  - 평균 / P95 전체 시간: 132.28 / 202ms
  - 직접 추가 비용: lexical SQL 평균 2.40ms + 질의당 DB 1회; 평가 profile 평균 6.75ms
  - GPU·메모리: 추가 GPU 없음; 운영 index 비용 미검증
- **방식:** P14 BGE-M3 Sparse
  - 평균 / P95 전체 시간: 162.98 / 186ms
  - 직접 추가 비용: sparse encode+18문서 score 평균 17.53ms, fusion/profile 평균 17.53ms
  - GPU·메모리: 모델 load 3.24s, corpus encode 391ms, peak allocated 약 1.22GB
- **방식:** P15 BGE reranker
  - 평균 / P95 전체 시간: 205.55 / 248ms
  - 직접 추가 비용: GENERAL reranker 평균 50.59ms, P95 60.95ms; post-rerank profile 평균 22.91ms
  - GPU·메모리: load 3.68s, peak allocated 1.23GiB, reserved 1.65GiB, 관측 RSS 2.79GiB

P15 CPU 실행은 기술적으로 가능했지만 16개 후보 한 질의에 6.23초가 걸렸고 관측 최대
RSS는 2.47GiB였다. 이 값은 한 번의 warm-cache 측정이며 평균/P95가 아니지만 실시간
검색 경로로는 현실적이지 않다. P14와 P15 GPU 모델을 동시에 상주시킨 결합 peak는
측정하지 않았다.

## 주요 성공·실패 사례

`TopN`은 첫 관련 결과 순위다. `빈 결과`는 관련·무관 결과가 모두 없음을 뜻하고,
`오탐 N건`은 관련 근거 없이 무관 결과만 반환했음을 뜻한다.

- **질의:** 알림
  - Production: 빈 결과, 정답 `0.494675`
  - P9/P11 section 방식: Top1
  - P12: Top1
  - P13: Top1
  - P14: Top1
  - P15: Top3
- **질의:** 동시성
  - Production: 빈 결과, 정답 `0.490510`
  - P9/P11 section 방식: 빈 결과
  - P12: Top1
  - P13: Top1
  - P14: Top1
  - P15: Top2
- **질의:** 이메일/Kakao 통합
  - Production: 빈 결과, 정답 `0.495141`
  - P9/P11 section 방식: Top1
  - P12: 빈 결과
  - P13: 빈 결과
  - P14: Top1
  - P15: Top1
- **질의:** FOR UPDATE SKIP LOCKED
  - Production: Top1
  - P9/P11 section 방식: 빈 결과
  - P12: Top1
  - P13: Top1
  - P14: Top5 소실; pre-final 9위
  - P15: Top1 복구
- **질의:** 배포
  - Production: 빈 결과, 라벨 정답 `0.431200`
  - P9/P11 section 방식: 빈 결과
  - P12: 빈 결과
  - P13: 무관 결과 1건
  - P14: 관련 Top3, 앞 2건 무관
  - P15: 관련 Top2, Top1 무관
- **질의:** 없는 결제 시스템
  - Production: 오탐 1건
  - P9/P11 section 방식: 오탐 1건
  - P12: 오탐 1건
  - P13: 오탐 1건
  - P14: 오탐 5건
  - P15: 오탐 5건
- **질의:** 없는 Kafka GENERAL
  - Production: 올바른 빈 결과
  - P9/P11 section 방식: 오탐 1건
  - P12: 올바른 빈 결과
  - P13: 올바른 빈 결과
  - P14: 오탐 5건
  - P15: 오탐 5건
- **질의:** 없는 Kubernetes
  - Production: 올바른 빈 결과
  - P9/P11 section 방식: 올바른 빈 결과
  - P12: 올바른 빈 결과
  - P13: 올바른 빈 결과
  - P14: 오탐 5건
  - P15: 오탐 5건

핵심 해석은 다음과 같다.

- `알림`과 `동시성`은 Dense retrieval 실패가 아니라 `0.50` 바로 아래의 경계 누락이라
  P12의 좁은 rescue가 직접 원인을 겨냥했다.
- 이메일/Kakao는 chunking 또는 native sparse가 복구했지만 PostgreSQL `simple` FTS는
  `카카오`와 본문의 `Kakao` 표기 차이 및 AND query 때문에 복구하지 못했다.
- `FOR UPDATE SKIP LOCKED`는 P14에서도 후보 자체는 있었으나 sparse consensus가 없어
  RRF 9위로 밀렸다. P15는 이를 복구했지만 다른 질의 순위를 더 많이 악화시켰다.
- `배포`는 `0.50` 근처 문제가 아니며 same-page 대표와 낮은 lexical/sparse overlap을
  제한 없이 허용할 때 무관 결과가 먼저 나온다.
- 결제·Kafka·Kubernetes는 sparse 양의 overlap을 독립 eligibility로 사용하면 오탐이
  급증한다. 순서만 바꾸는 reranker로는 반환 여부를 고칠 수 없다.

## 단계별 최종 판정

- **단계·방식:** P0 안전 regression
  - Production 여부: Production test
  - 판정: 적용
  - 근거: 향후 완화 전 안전 계약 고정
- **단계·방식:** P1 intent 경계
  - Production 여부: Production
  - 판정: 적용
  - 근거: 결과 무변경으로 GENERAL/completed 분리
- **단계·방식:** P2 GENERAL hard gate 완화
  - Production 여부: Production
  - 판정: 적용
  - 근거: 문자열 불일치만으로 의미 후보 제거 방지
- **단계·방식:** P3 기술명 정규화
  - Production 여부: Production
  - 판정: 적용
  - 근거: Spring Boot 표기 변형만 제한적으로 통합
- **단계·방식:** P4 bounded soft ranking
  - Production 여부: Production
  - 판정: 적용
  - 근거: 최대 0.030으로 Dense 주 신호 유지
- **단계·방식:** P5 `0.50 / Top20`
  - Production 여부: Production
  - 판정: 적용
  - 근거: 낮춤·확대 필요 근거 없음
- **단계·방식:** P6 v2 상태
  - Production 여부: Production API
  - 판정: 적용
  - 근거: GENERAL 빈 결과와 completed 검증 실패 분리
- **단계·방식:** P7 snippet
  - Production 여부: Production 표현 계층
  - 판정: 적용
  - 근거: 결과·순위 무변경
- **단계·방식:** P8 실문서 baseline
  - Production 여부: 평가 전용
  - 판정: 측정 완료
  - 근거: 후속 비교 기준
- **단계·방식:** P9 section-v1
  - Production 여부: 평가 전용
  - 판정: 비권고
  - 근거: Top1·MRR 하락, Kafka 오탐·여러 회귀
- **단계·방식:** P10 deferred page dedup
  - Production 여부: 평가 전용
  - 판정: 비권고
  - 근거: 일부 복구에도 단독 Production 조합은 악화
- **단계·방식:** P11 section-v2
  - Production 여부: 평가 전용
  - 판정: 비권고
  - 근거: 최소 길이는 개선했지만 전체 지표 하락
- **단계·방식:** P12/P17/P18 exact-token rescue
  - Production 여부: Production
  - 판정: 적용
  - 근거: P17 추가 28문항에서 신규 rescue 오탐 0, P18 Product와 평가 프로필 68문항 차이 0
- **단계·방식:** P13 PostgreSQL FTS + RRF
  - Production 여부: 평가 전용
  - 판정: 보류, 현재 형태는 비권고
  - 근거: P12와 같은 지표지만 추가 DB 비용과 `배포` 회귀
- **단계·방식:** P14 BGE-M3 Sparse
  - Production 여부: 평가 전용
  - 판정: 비권고
  - 근거: Recall 상승보다 오탐 15건과 순위 회귀가 큼
- **단계·방식:** P15 BGE reranker
  - Production 여부: 평가 전용
  - 판정: 비권고
  - 근거: Recall·오탐 개선 없이 Top1 하락과 최고 비용

## 현재 최종 후보와 남은 결정

### 채택: Dense + 제한적 exact-token rescue

- 장점: P12에서 Top1·Recall@3/5·MRR@5가 개선됐고 새 오탐이나 회귀가 없었다.
- 장점: 새 DB 조회·별도 모델·GPU·index가 필요 없다.
- P17에서 실제 존재 단어, 부재 단어, 유사·부분일치, 조사·표기 변형 28문항을 추가해
  검증했다. 신규 정답 `토큰` 한 건을 복구했고 13개 무근거 질문에서 rescue가 만든
  신규 오탐은 0건이었다.
- P18에서 같은 조건을 Product GENERAL 경로에 적용했다. Product와 P12 평가 프로필의
  P8 40 + P17 28 결과 ID·상태·relevance·원래 score/distance는 모두 같았다.

### 후보 B: Dense + PostgreSQL FTS + RRF

- 장점: P12와 같은 headline 지표를 냈고 PostgreSQL 내장 기능으로 설명 가능한 lexical
  channel을 구성할 수 있다.
- 한계: lexical hit가 5/40에 그쳤고 긴 한국어·영문 혼합 질의를 거의 돕지 못했다.
- 한계: 질의당 DB 조회 1회, 운영용 FTS index 설계·migration·규모 성능 검증이 필요하다.
- 한계: `배포`에서 무관 결과를 만들었다.
- 다음 판단에 필요한 근거: query 구성 변경 없이 재현 가능한 precision 보호 조건과
  운영 규모 index 비용.

P17의 추가 안전성 검증 뒤 P18에서 후보 A를 Production에 채택했다. PostgreSQL FTS,
BGE-M3 Sparse와 BGE reranker는 계속 평가 전용이며 Production 경로에 포함하지 않는다.
