# PRZ-008 작업 목록

> 현재 상태: 검색 개선 Batch 1A `BATCH_1A_READY_FOR_REVIEW`

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

Batch 1A 검증과 감사 통과 전에는 지표 계산·threshold 분석·실제 평가를 시작하지 않는다.
