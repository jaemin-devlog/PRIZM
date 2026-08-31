# PRZ-028 Tasks

- 상태: `IN_PROGRESS / INPUT_FROZEN / IMPLEMENTATION_VERIFIED / OFFICIAL_T0_T1_NOT_RUN`

- [x] ORIENT: branch/HEAD/origin/main, PRZ-025/026 dependency와 clean worktree 확인
- [x] ORIENT: PRZ-027 `NO_GO`를 제외한 B3 baseline 계보 확인
- [x] ORIENT: 기존 DEV/CAL typed annotation coverage만 확인
- [x] SPEC: constraint/observation/match/stable partition와 비범위 고정
- [x] PLAN: stress input freeze, implementation, verification과 rollback 계획
- [x] DATA: DEV/CAL Typed Constraint Stress Set 24문항 materialize
- [x] VERIFY-DATA: schema/source span/lineage/hash/SEALED guard 검증
- [x] INTEGRATE-DATA: 최초 input-freeze local commit `4bbbc5d` — annotation feasibility finding으로
  `INVALID_INPUT_HISTORICAL`, benchmark 0
- [x] AUDIT-DATA: 구현 전 qualifier/span/date operator/language consistency finding 확인
- [x] DATA-CORRECTION: v1.0.0 보존, corrected v1.0.1 별도 materialize
- [x] VERIFY-DATA-CORRECTION: v1.0.0/v1.0.1 byte/hash/lineage/SEALED guard 검증
- [x] INTEGRATE-DATA-CORRECTION: corrected v1.0.1 local input-freeze commit `3e3bf65`
- [x] IMPLEMENT: deterministic query constraint parser와 candidate observation extractor
- [x] IMPLEMENT: three-state evaluator와 full-ranking candidate-preserving stable partition
- [x] IMPLEMENT: Gold-free runtime projection, exact top-5 nDCG, unit-state/hard-negative/latency metric
- [x] TEST: parser/evaluator/provenance/parity/stable order/SEALED guard
- [x] TEST: candidate identity, split 1:1, stale-rank, exact IDCG와 pre-registered verdict policy
- [ ] INTEGRATE-CODE-FREEZE: 구현·runner·판정 계약 local commit
- [ ] VERIFY: Original/Long-form/Robustness/Stress DEV/CAL T0/T1 공식 실행
- [ ] VERIFY: extraction/match/ranking/type/user/hard-negative와 latency 기록
- [ ] AUDIT: diff scope, sensitive data, SEALED hash/flags, OSS readiness
- [ ] INTEGRATE: 검증 결과와 판정 local commit
- [ ] INTEGRATE: push·PR·main merge — `NOT_RUN` (금지)
- [ ] Sparse 실험 — `NOT_RUN` (후속 Gate)
