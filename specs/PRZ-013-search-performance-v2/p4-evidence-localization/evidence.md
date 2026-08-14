# P4 Evidence Localization Evidence

검증일: 2026-08-14

## 구현 전 재분류

| Query | 분류 | 근거 |
|---|---|---|
| 데이터가 중복 저장되는 문제 해결한 적 있어? | `LOCALIZATION` | 최종 candidate는 포트폴리오 p3이었지만 같은 ACTIVE version의 p2에 `중복 저장 0건` 직접 근거가 존재했다. |
| 서버에 서비스를 올려본 적 있어? | `LOCALIZATION` | 최종 candidate는 이력서 p1이었지만 같은 ACTIVE version의 p2에 GCP·Docker Compose 배포 직접 근거가 존재했다. |
| 오래 멈춘 작업을 다시 처리할 수 있게 한 경험은? | `LOCALIZATION` | 최종 candidate는 포트폴리오 p5였지만 같은 ACTIVE version의 p3에 만료 작업 복구·재처리 직접 근거가 존재했다. |
| TourAPI를 병렬 처리하고 엑셀 2,329행 중 675건을 갱신한 경험은? | `CANDIDATE_RECALL` | 최종 후보에 기대 포트폴리오 candidate가 없었으므로 P4 범위 밖이다. |
| 실제 운영 환경에 배포해본 경험 알려줘 | `QUERY_UNDERSTANDING` | Q1에서 결과가 생겨 sequential fallback이 종료되어, 단독 실행 시 정답이던 Q2 candidate가 localization 이전에 실행·병합되지 않았다. |

## 최소 구현

- 기존 검색 candidate가 확정된 뒤 현재 chunk의 직접 근거 충분성을 먼저 판정한다.
- 현재 근거가 부족한 경우에만 기존 repository의 동일 owner·document·ACTIVE version 범위에서 대체 evidence를 비교한다.
- P4 전용 localization 선택에서 query token coverage, 인접 phrase, exact 숫자, strong anchor, 서술형·구조화된 상세 근거를 사용한다.
- 제목·요약·기술 목록은 질문 직접성이 낮으면 우선하지 않는다.
- 검색 result ID, 순서, score, distance, content는 변경하지 않고 snippet과 evidence source만 바꾼다.

## Focused Verify

| Query | Before | After |
|---|---|---|
| 데이터가 중복 저장되는 문제 해결한 적 있어? | 포트폴리오 p3 Outbox 근거 | 동일 result chunk를 유지하고 포트폴리오 p2 `중복 저장 0건` 근거 |
| 서버에 서비스를 올려본 적 있어? | 이력서 p1 소개/요약 | 동일 result chunk를 유지하고 이력서 p2 GCP·Docker Compose·Nginx 배포 근거 |
| 오래 멈춘 작업을 다시 처리할 수 있게 한 경험은? | 포트폴리오 p5 TourAPI 근거 | 동일 result chunk를 유지하고 포트폴리오 p3 만료 작업 복구·재처리 근거 |
| TourAPI + 2,329행 + 675건 | 이력서 p2 | 변경 없음, `OUT_OF_SCOPE` |
| 실제 운영 환경에 배포 | 이력서 p1 evidence | 변경 없음, `OUT_OF_SCOPE` |

- focused localization target: PASS
- P2/P3 대표 regression guard 9건: PASS
- numeric exact guard 5건과 near-miss 3건: PASS
- strong identifier negative 11건: PASS
- candidate result ID·순서·score·distance·content 변경: 0건
- USER 인증 경로와 ACTIVE version 확인: PASS

## 동일 72-query Benchmark

| 지표 | P3 Before | P4 After |
|---|---:|---:|
| Top1 Accuracy | 75.00% | 82.14% |
| Recall@3 | 78.57% | 85.71% |
| Recall@5 | 78.57% | 85.71% |
| MRR@5 | 0.7649 | 0.8363 |
| Negative FPR | 0% | 0% |
| 전체 실패 | 14 | 10 |
| Evidence Localization 실패 | 5 | 1 |

- 신규 PASS: B02, B04, B12, C06
- 신규 FAIL: 없음
- regression: 0건
- ground truth 변경: 없음

## Latency

| 지표 | P3 Before | P4 After |
|---|---:|---:|
| Warm avg | 239.694ms | 296.972ms |
| Warm median | 207.341ms | 256.445ms |
| Warm P95 | 600.227ms | 729.198ms |
| Max | 635.891ms | 776.127ms |

P4는 현재 근거가 부족한 결과에만 동일 ACTIVE version의 evidence 후보를 비교한다. 이번 Phase에서는 latency 최적화를 수행하지 않았다.

## Gate

- Focused gate: `PASS`
- 72-query benchmark regression gate: `PASS`
- P4 최종 판정: `PASS`
- Phase 상태: P4 `DONE`, P5 `PLANNED`
