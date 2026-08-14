# P0 Search Benchmark / Baseline 결과와 실패 분석

> 역사 보존: 초기 `PRZ-014 Search Benchmark V2`에서 기록한 분석을 PRZ-013의 P0로
> 이동했다. 아래 측정값과 failure taxonomy는 변경하지 않았다.

## 고정 조건

- 정상 `USER` 로그인과 owner-scoped API를 사용했다.
- 평가 문서 2개, ACTIVE version 2개, PDF 2종, 총 18 chunks를 고정했다.
- ground truth 72개를 검색 실행 전에 확정했다.
- 검색 도중 업로드, 재색인, version 변경을 하지 않았다.
- API가 반환하지 않은 Top20 내부 후보나 threshold 아래 후보는 추정값을 만들지 않고 `NA`로 기록했다.
- 검색 production 코드는 변경하지 않았다.

## 지표

| 지표 | 결과 |
|---|---:|
| 전체 | 72 |
| Positive | 56 |
| Negative | 16 |
| Top1 Accuracy | 57.14% (32/56) |
| Recall@3 | 66.07% (37/56) |
| Recall@5 | 67.86% (38/56) |
| MRR@5 | 0.6146 |
| Negative False Positive Rate | 6.25% (1/16) |
| 실패 query | 25 (Positive 24, Negative 1) |

전체 72건 latency는 평균 304.066ms, median 284.984ms, P95 351.500ms,
max 1183.819ms다. 첫 요청 cold latency는 1183.819ms였고, 이를 제외한 warm
71건은 평균 291.675ms, median 284.984ms, P95 341.154ms다.

## 카테고리별 결과

| 카테고리 | 질의 | Top1 통과 | 실패 |
|---|---:|---:|---:|
| 직접 기술/경험 | 12 | 7 | 5 |
| 자연어 표현 변형 | 16 | 10 | 6 |
| 간접 문제 해결 | 12 | 8 | 4 |
| 숫자/identifier | 8 | 3 | 5 |
| 복합 자연어 | 8 | 4 | 4 |
| Negative | 16 | 15 | 1 |

## 실패 taxonomy

| 유형 | 개수 | 관찰 |
|---|---:|---|
| RANKING | 6 | 정답 evidence가 2~4위에 있으나 요약·다른 경험이 먼저 배치됨 |
| NUMERIC_IDENTIFIER | 5 | 짧은 숫자 표현 5개가 모두 반환 경계에서 누락됨 |
| QUERY_UNDERSTANDING | 5 | 배포·운영·갱신·상태 재확인 등의 간접 표현이 원문과 연결되지 않음 |
| CANDIDATE_RECALL | 4 | canonical ground-truth chunk가 Top5 응답에 없음 |
| EVIDENCE_LOCALIZATION | 4 | 같은 문서를 찾았으나 다른 페이지 근거가 snippet으로 연결되지 않음 |
| FALSE_POSITIVE | 1 | 없는 GraphQL 경험에 일반 API 경험이 반환됨 |

## 실패별 근거와 root-cause 가설

`score/distance`는 API 응답값이다. 정답이 반환되지 않은 경우 정답 score와 rank는
`NA`이며 threshold 0.50과의 관계도 판정하지 않는다.

| ID | Query | Actual | 정답 rank · score/distance | Top candidate | 유형 | Root-cause 가설 |
|---|---|---|---|---|---|---|
| A01 | Spring Boot 백엔드 경험 | EVIDENCE_FOUND | 2 · 0.5186/0.4814, threshold 이상 | 포트폴리오 p1 · 0.5891/0.4109 | RANKING | 기술 헤더가 실제 프로젝트 수행 근거보다 높은 dense 유사도를 가짐 |
| A02 | Redis 후보 탐색 사용 경험 | EVIDENCE_FOUND | NA | 이력서 p1 · 0.6093/0.3907 | CANDIDATE_RECALL | 상세 ground truth인 포트폴리오 p2 대신 중복 요약 근거만 반환됨. 사용자 답변은 관련되지만 canonical-source 기준 실패 |
| A03 | 동시성 처리 경험 | EVIDENCE_FOUND | 4 · 0.5139/0.4861, threshold 이상 | 이력서 p1 · 0.5612/0.4388 | RANKING | 넓은 `동시성 처리` 표현에서 소개·요약 chunk가 상세 검증보다 우선됨 |
| A04 | Docker Compose 배포 경험 | NO_EVIDENCE | NA | 없음 | CANDIDATE_RECALL | 직접 기술명은 있으나 배포 수행 문장이 최종 evidence gate를 통과하지 못함 |
| A05 | TourAPI 연동 경험 | EVIDENCE_FOUND | 3 · 0.5582/0.4418, threshold 이상 | 이력서 p2 · 0.6347/0.3653 | RANKING | 이력서 요약이 포트폴리오 상세 수행 근거보다 우선됨 |
| B01 | 여러 요청이 동시에 들어오면 어떻게 처리했어? | EVIDENCE_FOUND | 3 · 0.5592/0.4408, threshold 이상 | 포트폴리오 p5 · 0.5901/0.4099 | RANKING | 일반적인 동시 처리 표현이 TourAPI 병렬 처리와 Worker 동시성을 함께 활성화함 |
| B02 | 데이터가 중복 저장되는 문제 해결한 적 있어? | EVIDENCE_FOUND | NA | 포트폴리오 p3 · 0.5903/0.4097 | EVIDENCE_LOCALIZATION | 같은 문서의 Outbox 중복 처리 근거가 선택되고 매칭 중복 저장 p2로 확장되지 않음 |
| B04 | 서버에 서비스를 올려본 적 있어? | EVIDENCE_FOUND | NA | 이력서 p1 · 0.5056/0.4944 | EVIDENCE_LOCALIZATION | 이력서를 찾았지만 배포 p2 대신 소개 p1이 선택되고 상세 배포 근거로 확장되지 않음 |
| B05 | 실제 운영 환경에 배포해본 경험 알려줘 | NO_EVIDENCE | NA | 없음 | QUERY_UNDERSTANDING | `실제 운영 환경`과 GCP/Docker Compose 원문의 semantic gap이 남음 |
| B12 | 실제 사용자가 있는 서비스를 운영해본 경험은? | NO_RELEVANT_RESULTS | NA | 없음 | QUERY_UNDERSTANDING | 실사용·누적 사용자 표현과 질의의 `실제 사용자` 표현이 retrieval 경계에서 연결되지 않음 |
| B14 | 스프레드시트에서 기존 데이터만 골라 갱신한 적 있어? | NO_RELEVANT_RESULTS | NA | 없음 | QUERY_UNDERSTANDING | 원문의 `엑셀 업로드`와 질의의 `스프레드시트` 간 표현 차이가 큼 |
| C03 | 외부 호출 대기 시간이 누적되는 문제를 줄인 방법은? | EVIDENCE_FOUND | 2 · 0.5779/0.4221, threshold 이상 | 포트폴리오 p1 · 0.5846/0.4154 | RANKING | 포트폴리오 요약이 상세 원인·해결 페이지보다 근소하게 우선됨 |
| C06 | 오래 멈춘 작업을 다시 처리할 수 있게 한 경험은? | EVIDENCE_FOUND | NA | 포트폴리오 p5 · 0.5001/0.4999 | EVIDENCE_LOCALIZATION | 같은 포트폴리오를 찾았으나 Outbox 복구 p3 대신 병렬 작업 p5가 선택됨 |
| C08 | 후보 상태가 바뀌었는지 확정 직전에 다시 확인한 경험은? | NO_RELEVANT_RESULTS | NA | 없음 | QUERY_UNDERSTANDING | `확정 직전 재확인`의 간접 표현과 row lock 상태 재검증 문장의 semantic gap |
| C11 | 업로드 파일을 애플리케이션 대신 웹 서버가 제공하게 한 경험은? | NO_RELEVANT_RESULTS | NA | 없음 | QUERY_UNDERSTANDING | `웹 서버가 제공`과 원문의 `Nginx가 직접 서빙` 표현이 연결되지 않음 |
| D01 | 4,400회 테스트 | NO_RELEVANT_RESULTS | NA | 없음 | NUMERIC_IDENTIFIER | 숫자와 일반 명사만 있는 짧은 질의가 구체 검증 chunk를 회수하지 못함 |
| D02 | 675건 갱신 | NO_RELEVANT_RESULTS | NA | 없음 | NUMERIC_IDENTIFIER | 숫자·단위 표기와 page 내 표 형식 정보의 결합이 약함 |
| D03 | 2,329행 처리 | NO_RELEVANT_RESULTS | NA | 없음 | NUMERIC_IDENTIFIER | 숫자·단위 표기와 엑셀 처리 근거가 반환 경계를 통과하지 못함 |
| D07 | 1,480건 선점 | NO_RELEVANT_RESULTS | NA | 없음 | NUMERIC_IDENTIFIER | 숫자·선점 용어가 Outbox 검증 표의 근거를 회수하지 못함 |
| D08 | 1,654건 제외 | NO_RELEVANT_RESULTS | NA | 없음 | NUMERIC_IDENTIFIER | 숫자·제외 용어가 표 내부 근거와 충분히 연결되지 않음 |
| E01 | Redis와 DB lock을 같이 사용해서 동시성 문제를 해결한 경험이 있어? | EVIDENCE_FOUND | 2 · 0.6533/0.3467, threshold 이상 | 이력서 p1 · 0.6631/0.3369 | RANKING | 상세 포트폴리오보다 동일 경험의 이력서 요약이 0.0098 높음 |
| E03 | Outbox로 FCM 실패를 격리하고 중복 발송도 막은 경험은? | EVIDENCE_FOUND | NA | 이력서 p1 · 0.6455/0.3545 | CANDIDATE_RECALL | 이력서 요약은 회수했지만 실패 격리·중복 차단을 함께 설명하는 포트폴리오 p3가 Top5에 없음 |
| E04 | GCP에서 Docker Compose와 Nginx로 Spring Boot 서비스를 배포한 경험은? | NO_EVIDENCE | NA | 없음 | CANDIDATE_RECALL | 여러 정확한 기술명이 있어도 배포 수행 근거가 최종 결과로 남지 않음 |
| E07 | TourAPI를 병렬 처리하고 엑셀 2,329행 중 675건을 갱신한 경험은? | EVIDENCE_FOUND | NA | 포트폴리오 p1 · 0.6491/0.3509 | EVIDENCE_LOCALIZATION | 요약·이력서는 찾았으나 두 조건을 함께 만족하는 p4~5 근거로 확장되지 않음 |
| F06 | GraphQL API 구현 경험 | EVIDENCE_FOUND | 정답 없음 | 이력서 p2 · 0.5312/0.4688 | FALSE_POSITIVE | 존재하지 않는 `GraphQL`보다 공통 `API`·`구현 경험` 의미가 우세해 일반 API 경험이 threshold를 통과함 |

## 가장 큰 실패 유형과 후속 후보

1. `RANKING` 6건: 동일 경험의 요약/헤더와 상세 근거를 구분하는 lightweight reranking 검토 후보.
2. `NUMERIC_IDENTIFIER` 5건: 숫자·단위 표기 보존과 exact identifier retrieval 검토 후보.
3. `QUERY_UNDERSTANDING` 5건: 의미를 단일 키워드로 축약하지 않는 제한적 query variant 검토 후보.

그 밖에 `FALSE_POSITIVE`에는 query 핵심 기술명과 evidence의 직접 일치를 강화하는 gate가
후보가 될 수 있다. 이 문서에서는 어떤 개선도 구현하거나 실험하지 않았다.

## 해석 제한

- 평가셋은 현재 두 개인 문서에 특화되어 일반 사용자 전체 성능을 대표하지 않는다.
- 이력서와 포트폴리오에 같은 경험의 요약·상세 근거가 중복되어 있다. ground truth는
  검색 전에 선택한 canonical document/page를 유지했으므로, 일부 canonical-source 실패는
  사용자 관점에서 완전히 무관한 결과를 뜻하지 않는다.
- PRZ-008과 평가셋·문서·runtime 조건이 다르므로 동일 조건의 전후 비교로 해석하지 않는다.
