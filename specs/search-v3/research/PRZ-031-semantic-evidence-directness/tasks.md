# PRZ-031 Tasks

- 상태: `VERIFIED / D1_HISTORICAL_PROTOCOL_NO_GO / D2_PROTOCOL_V2_PASS / D2_SEMANTIC_NO_GO`

- [x] PRZ-030 HEAD, dependency, origin/main, clean tree를 확인했다.
- [x] 제품 의미, relation, D0/D1, Top10, Gold-after-output와 Capability Gate를 문서화했다.
- [x] 기존 evaluation-only model tooling과 현재 local model inventory를 감사했다.
- [x] 적합한 exact local model 부재를 `BLOCKED_MODEL_SELECTION`으로 기록했다.
- [x] 별도 승인 후 official Qwen3-4B GGUF revision/file과 local blob identity를 확정했다.
- [x] model/instruction/schema/config/ranking policy를 `execution-contract.json`에 고정했다.
- [x] 후보 재사용과 Gold-free input freeze의 남은 경계를 감사했다.
- [x] 기존 candidate/Gold guard focused test와 benchmark integrity validator를 실행했다.
- [x] OSS readiness, Registry link, diff/scope와 SEALED metadata를 감사했다.
- [x] 독립 audit에서 발견한 O10/capture, semantic/typed, no-support comparator 모호성을
  공식 inference 전에 해소했다.
- [x] exact model/revision/license/size와 instruction/schema/policy freeze
- [x] Gold-free combined candidate input freeze/hash
- [x] 최소 adapter/phase guard/evaluator/test 구현 및 현재 source focused 검증
- [x] 공식 directness inference 1회 시작 — 첫 response contract failure, output freeze 없음
- [x] Gold join/metric은 `NOT_EVALUABLE`, 최종 판정은 `NO_GO`로 기록
- [x] Production/Typed 통합/SEALED FINAL 검색/push/PR/merge를 실행하지 않음

## D2 Output Protocol V2

- [x] D1 `PROTOCOL_NO_GO / SEMANTIC_QUALITY_NOT_EVALUATED`와 local marker/failure를 보존
- [x] relation-only V2 contract, strict parser와 16-pair generic conformance 구현
- [x] protocol parser/freeze/evaluator focused test 통과
- [x] protocol conformance parse/schema 100%, enum/extra field 0 확인
- [x] D1 candidate/input SHA와 full candidate payload parity 검증 후 V2 input 봉인
- [x] code/model/instruction/schema/config/candidate/ranking hash 동결
- [x] 공식 D2 inference 1회와 578/578 output freeze/hash/verify
- [x] output 검증 뒤 Gold join, D0/D2 metrics·slice·Gate 산출
- [x] Production/SEALED/scope/OSS audit와 `NO_GO` 판정 기록
