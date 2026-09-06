# PRZ-034 Plan

- 상태: `COMPLETED / PROMISING`

1. `ORIENT` — PRZ-033/032/029/026 dependency와 clean 기준점, local artifact/model을 확인한다.
2. `SPEC` — CHILD_DENSE_V1, Top5, query-vector 한계, Typed source-order prepare/state/eligibility 보존과
   selected Evidence 변경 허용 경계를 결과 전에 고정한다.
3. `PLAN` — Gold-free B3/query-vector parity, input/prediction freeze, same-Passage sorter, Top5
   `PreparedChild` dense overlay와 비용 report를 설계한다.
4. `IMPLEMENT` — `src/searchEvaluation/**`에 selector/runner/focused test만 추가한다. Typed S1은
   PRZ-029 source-order prepared corpus의 validation content를 바꾸지 않고 Top5 Child 순서 overlay 후
   기존 `EvidenceValidationSelector.select`를 재호출한다.
5. `VERIFY` — code freeze 후 동일 BGE-M3로 공식 S0/S1 비교를 1회 실행한다.
6. `AUDIT` — Oracle capture, slices, Typed applicability/parsed constraint/state parity, selected Evidence
   precision·match tier·provenance, safety/cost, SEALED/diff/OSS를 독립 감사한다.
7. `INTEGRATE` — aggregate evidence와 Registry를 갱신하고 local commit한다. push/PR/merge는 하지 않는다.

실패 시 raw local artifact와 판정을 보존하고 같은 dataset에서 policy나 두 번째 Selector를 재실행하지
않는다. Production rollback은 필요하지 않다. Production 변경 자체가 금지되기 때문이다.
