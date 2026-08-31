# PRZ-028 Tasks

- 상태: `IN_PROGRESS / INPUT_FROZEN / IMPLEMENTATION_NOT_STARTED`

- [x] ORIENT: branch/HEAD/origin/main, PRZ-025/026 dependency와 clean worktree 확인
- [x] ORIENT: PRZ-027 `NO_GO`를 제외한 B3 baseline 계보 확인
- [x] ORIENT: 기존 DEV/CAL typed annotation coverage만 확인
- [x] SPEC: constraint/observation/match/stable partition와 비범위 고정
- [x] PLAN: stress input freeze, implementation, verification과 rollback 계획
- [x] DATA: DEV/CAL Typed Constraint Stress Set 24문항 materialize
- [x] VERIFY-DATA: schema/source span/lineage/hash/SEALED guard 검증
- [ ] INTEGRATE-DATA: 결과 실행 전 input-freeze local commit
- [ ] IMPLEMENT: deterministic query constraint parser와 candidate observation extractor
- [ ] IMPLEMENT: three-state evaluator와 full-ranking candidate-preserving stable partition
- [ ] TEST: parser/evaluator/provenance/parity/stable order/SEALED guard
- [ ] VERIFY: Original/Long-form/Robustness/Stress DEV/CAL T0/T1 공식 실행
- [ ] VERIFY: extraction/match/ranking/type/user/hard-negative와 latency 기록
- [ ] AUDIT: diff scope, sensitive data, SEALED hash/flags, OSS readiness
- [ ] INTEGRATE: 검증 결과와 판정 local commit
- [ ] INTEGRATE: push·PR·main merge — `NOT_RUN` (금지)
- [ ] Sparse 실험 — `NOT_RUN` (후속 Gate)
