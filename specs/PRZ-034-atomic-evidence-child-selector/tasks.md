# PRZ-034 Tasks

- 상태: `IN_PROGRESS / OFFICIAL_COMPARISON_NOT_RUN`

- [x] PRZ-033 HEAD와 PRZ-032/029/026 dependency, clean worktree를 확인했다.
- [x] CHILD_DENSE_V1 단일 변수와 사전 Gate를 고정하고, Typed selected-set 불변 계약을 결과 전에
  Top5 PreparedChild dense overlay 계약으로 교정했다.
- [x] Gold-free Top5 input/policy freeze와 shared-query-vector B3 parity 구현
- [x] sourceText-only Child embedding과 same-Passage stable sorter 구현
- [x] prediction-before-Gold guard와 S0/S1/Oracle evaluator 구현
- [x] PRZ-029 source-order prepare/validation을 보존한 Top5 PreparedChild overlay와 기존 select 재호출 구현
- [x] Typed applicability/parsed constraint/state exact parity, selected Evidence match tier/provenance와
  precision 변화, failure/cost report 구현
- [x] model-free focused test와 Typed/provenance overlay preflight
- [ ] code-freeze commit
- [ ] official S0/S1 비교 1회 실행
- [ ] result/evidence, SEALED/diff/OSS audit
- [ ] local commit

Production, SEALED FINAL, 다른 Selector, push/PR/merge는 `NOT_RUN`이다.
