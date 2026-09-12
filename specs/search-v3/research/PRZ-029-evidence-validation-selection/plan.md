# PRZ-029 Plan

- 상태: `COMPLETED`

## 1. ORIENT / SPEC

1. 실제 PRZ-028 final HEAD와 clean tree를 확인한다.
2. B3 runtime/provenance와 PRZ-028 validation-only 경계를 확인한다.
3. selector 입력, validation scope, typed applicability와 state oracle을 결과 전에 동결한다.

## 2. IMPLEMENT

1. Gold가 없는 B3 passage/query DTO와 source-only typed validator adapter를 만든다.
2. full Dense ranking을 보존한 채 top-20 passage를 child 단위로 검증한다.
3. state tier 안에서 Dense/source 순서를 유지하는 최대 5개 deterministic selector를 만든다.
4. E0/E1 shared-run report에 parity, state/selection, slice, latency와 provenance metric을 추가한다.
5. raw report는 ignored local 경로에만 원자적으로 쓴다.

## 3. VERIFY / AUDIT

- unit: 사례 A~E, multi-constraint same-scope, cross-passage/Parent merge 금지, semantic bypass,
  dedupe, provenance와 max-5
- regression: B3/PRZ-028 parser/evaluator/benchmark guard
- evaluation: Original, Long-form, Robustness, Typed Stress 1.1.0 DEV/CAL만 같은 B3 run으로 실행
- integrity: candidate identity/Recall@20, SEALED metadata/byte 불변, forbidden scope와 secret audit
- repository: `git diff --check`, OSS readiness

Full backend/integration/frontend/Docker는 evaluation-only 변경에 필요하지 않으면 `NOT_RUN`으로 기록한다.
Gate가 실패하면 결과를 숨기지 않고 `NEEDS_ADJUSTMENT` 또는 `NO_GO`로 닫는다.
