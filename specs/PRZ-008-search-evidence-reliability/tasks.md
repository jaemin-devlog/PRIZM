# PRZ-008 작업 목록

> 현재 상태: Batch 2A `PRODUCT_APPLICATION_CONTRACT_FROZEN` — v1 호환·v2 세 상태
> 응답·versioned profile 설정·제품 검증 Gate를 확정했으며 제품 구현과 TEST는 `NOT_RUN`

| ID | 작업 | 최종 상태 | 결과 문서 |
|---|---|---|---|
| `S0-01` | 현재 제품 검색·청킹·평가 기준선 확인 | `DONE` | [Spec](spec.md) |
| `S0-02` | 검색 실패 사례와 세 상태 계약 정의 | `DONE` | [Spec](spec.md) |
| `S0-03` | TUNING·TEST 정책과 필수 지표 정의 | `DONE` | [Spec](spec.md) |
| `S0-04` | 1~7단계 범위·Gate·중단 조건 분리 | `DONE` | [Plan](plan.md) |
| `S0-05` | 0단계 문서·링크·변경 범위 자체 검증 | `DONE` | [Spec](spec.md), [Plan](plan.md) |
| `S0-06` | Spec 검토와 다음 단계 착수 승인 | `DONE` | [Spec](spec.md), [Plan](plan.md) |
| `S1A-01` | 기존 Dataset·과거 기준선을 보존하고 합성 Dataset v2 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1A-02` | TUNING·TEST 문서·근거·질문 그룹 누출 validation 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1A-03` | owner·version·PDF gold page 라벨 불변식 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1A-04` | Dataset v2 loader와 의도적 실패 fixture 검증 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-01` | Precision@5·Direct MRR@5·@20·group 중복 nDCG 계산 계약 교정 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-02` | 거부·오거부·no-searchable-documents·top-1·PDF page 지표 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-03` | 사용자 반환 수·후보 수와 total·embedding·DB 지연 분리 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1B-04` | 현재 제품·평가용 threshold profile을 분리한 보고서 계약과 경계 테스트 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-01` | Dataset v2 TUNING-only 선택과 owner·version fixture 실행 경계 추가 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-02` | PostgreSQL·pgvector·Ollama 실제 TUNING 10문항 기준선 측정 | `DONE` | [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-03` | 후보 수·지연과 threshold별 거부·오거부·품질 분석 | `BLOCKED` | `THRESHOLD_NOT_SEPARABLE`; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-04` | 실제 실패 구조를 합성한 이력서·포트폴리오와 TUNING 5문항 추가 | `DONE` | 정답·오타·동일 페이지 중복·무근거; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-05` | Dataset v2.1 TUNING 15문항 실제 재측정 | `DONE` | top-1 직접 0.8750·중복 0.0933·무관 거부 0.0000; [평가 문서](../../docs/evaluation/search-evaluation.md) |
| `S1C-06` | overlap 경계에서 잘린 직접 근거 라벨과 Dataset v2.2 교정 | `DONE` | TEST 10문항 byte 고정 유지 |
| `S1C-07` | 출처·본문·반복 요약 축약과 식별자·수치·핵심어·부정 표현 TUNING profile | `DONE` | 제품 source와 score 단독 threshold 변경 없음 |
| `S1C-08` | TUNING 15문항 top-1·오타·중복·거부 Gate 재측정 | `DONE` | top-1 8/8·오타 2/2·중복 0·무관 거부 1.0·오거부 0 |
| `S2A-01` | 기존 `/api/search`와 Career Evidence 배열의 호환 경계 확정 | `DONE` | [Spec](spec.md) |
| `S2A-02` | v2 `state`·`results`와 `NO_EVIDENCE`·`NO_SEARCHABLE_DOCUMENTS` 응답 확정 | `DONE` | [Spec](spec.md) |
| `S2A-03` | `PRIZM_SEARCH_PROFILE` 선택·기본값·실패·rollback 계약 확정 | `DONE` | [Spec](spec.md) |
| `S2A-04` | unit·API·frontend·PostgreSQL·OpenSQL·최종 TEST Gate 확정 | `DONE` | [Spec](spec.md), [Plan](plan.md) |

Dataset v2와 v2.1에서 근거·무근거 top-1 score 분포가 겹쳐 score 단독 threshold는
`THRESHOLD_NOT_SEPARABLE`로 유지한다. v2.2의 평가 전용 profile은 TUNING 15문항에서
직접 근거 top-1 8/8, 오타 top-1 2/2, 중복 0, 무관 질문 거부율 1.0과 오거부율 0으로
사전 Gate를 통과했다. 제품 source는 변경하지 않았고 TEST와 Batch 1D는 `NOT_RUN`이다.
제품 적용 계약은 확정됐다. 다음 Batch 2B에서 새 profile을 opt-in으로 구현하고 기존
API 호환과 세 상태를 검증한다. 모든 설정과 구현을 고정하기 전에는 TEST를 실행하지 않으며,
TEST와 실제 OpenSQL Gate가 통과하기 전 새 profile을 기본값으로 승격하지 않는다.
