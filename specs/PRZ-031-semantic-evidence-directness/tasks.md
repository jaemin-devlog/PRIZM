# PRZ-031 Tasks

- 상태: `IN_PROGRESS / BLOCKED_MODEL_SELECTION`

- [x] PRZ-030 HEAD, dependency, origin/main, clean tree를 확인했다.
- [x] 제품 의미, relation, D0/D1, Top10, Gold-after-output와 Capability Gate를 문서화했다.
- [x] 기존 evaluation-only model tooling과 현재 local model inventory를 감사했다.
- [x] 적합한 exact local model 부재를 `BLOCKED_MODEL_SELECTION`으로 기록했다.
- [x] 후보 재사용과 Gold-free input freeze의 남은 경계를 감사했다.
- [x] 기존 candidate/Gold guard focused test와 benchmark integrity validator를 실행했다.
- [x] OSS readiness, Registry link, diff/scope와 SEALED metadata를 감사했다.
- [x] 독립 audit에서 발견한 O10/capture, semantic/typed, no-support comparator 모호성을
  공식 inference 전에 해소했다.
- [ ] exact model/revision/license/size와 instruction/schema/policy freeze — `NOT_RUN`
- [ ] Gold-free combined candidate input freeze/hash — `NOT_RUN`
- [ ] 최소 adapter/phase guard/evaluator/test 구현 — `NOT_RUN`
- [ ] 공식 directness inference와 output freeze — `NOT_RUN`
- [ ] Gold join, relation/ranking/slice/cost metric과 최종 판정 — `NOT_RUN`
- [ ] Production, Typed 통합, SEALED FINAL 검색, push/PR/merge — `NOT_RUN`
