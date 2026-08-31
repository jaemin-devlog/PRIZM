# PRZ-028 Tasks

- 상태: `IN_PROGRESS / CODE_FREEZE_READY`

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
- [x] INTEGRATE-CODE-FREEZE: 구현·runner·판정 계약 local commit `2e9c9ff`
- [x] VERIFY: Original/Long-form/Robustness/Stress DEV/CAL T0/T1 공식 1회 실행
- [x] VERIFY: extraction/match/ranking/type/user/hard-negative와 latency 기록
- [x] AUDIT: result 문서 반영 후 diff scope, sensitive data, SEALED hash/flags, OSS readiness
- [x] INTEGRATE: 검증 결과와 `NEEDS_ADJUSTMENT` 판정 local commit
- [ ] INTEGRATE: push·PR·main merge — `NOT_RUN` (금지)
- [ ] Sparse 실험 — `NOT_RUN` (후속 Gate)

## Final adjustment

- [x] ORIENT: `d195f3b` clean HEAD, Stress 1.0.1 historical freeze와 SEALED metadata 경계 확인
- [x] SPEC: Stress 1.1.0 분포, qualifier status/reason 계약과 최종 역할 Gate를 결과 전에 고정
- [x] PLAN: input freeze → implementation → code freeze → official BGE 1회 → role audit 순서 고정
- [x] DATA: synthetic DEV/CAL Stress 1.1.0 6 bundles / 24 queries materialize
- [x] VERIFY-DATA: schema-contract/Gold/span/lineage/inventory/SHA/overwrite/SEALED guard
- [x] INTEGRATE-DATA: Typed 구현 변경 전 input-only local commit `e32b968`
- [x] IMPLEMENT: qualifier compatibility와 diagnostic reason; ranking은 state-only stable partition 유지
- [x] IMPLEMENT: dual stress loader, five-suite report, dataset-global claim/atomic output와 final role policy
- [x] VERIFY-CODE: non-BGE 71 tests, regression, materializer, forbidden scope/SEALED/OSS audit
- [ ] INTEGRATE-CODE-FREEZE: source/input/model/K/policy local commit
- [ ] VERIFY-OFFICIAL: Stress 1.1.0 T0/T1 BGE 공식 1회와 기존 네 suite regression
- [ ] AUDIT: 사전 Gate 기반 역할 판정, aggregate evidence, scope/final/OSS 검증
- [ ] INTEGRATE: PRZ-028 최종 역할·종료 판단 local commit
- [ ] PR/push/merge/Sparse — `NOT_RUN` (금지)
