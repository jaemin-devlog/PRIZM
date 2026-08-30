# PRZ-026 Phase 1 Tasks

- 상태: `IN_PROGRESS / PHASE_1_ADJUSTMENT_NEEDS_ADJUSTMENT`
- A/B 실행 결과: `COMPLETED — NEEDS_ADJUSTMENT`
- C/D Parent 단계: `NOT_RUN`

- [x] ORIENT: `origin/main`, PRZ-025 HEAD/merge 관계와 격리 branch/worktree 확인
- [x] ORIENT: PRZ-025 계약, frozen manifests, Production TextChunker/model과 평가 인프라 확인
- [x] SPEC: A/B 단일 변경점, StructuralBlock/Child/provenance와 metric 분모 고정
- [x] PLAN: evaluation-only source, test, runtime report와 SEALED guard 계획
- [x] IMPLEMENT: structural source model/parser/child builder
- [x] IMPLEMENT: actual TextChunker baseline, DEV/CAL loader, BGE-M3 cosine runner
- [x] TEST: parser/builder 구조와 provenance 계약
- [x] TEST: A/B 동일 입력/model과 DEV/CAL-only/SEALED guard
- [x] VERIFY: local Ollama `bge-m3` DEV/CAL raw Dense A/B 실행
- [x] VERIFY: query/user/profession/language와 fragmentation/contamination/cost 기록
- [x] VERIFY: PRZ-025 validator, sealed hash/flags, Gradle, diff/OSS/scope 검사
- [x] AUDIT: 역사적 Phase 1 실패 사례와 판정 독립 검토, 당시 blocking finding 0 확인
- [x] INTEGRATE: 허용 파일만 local branch commit
- [ ] INTEGRATE: push·PR·main merge — `NOT_RUN` (금지)

## Phase 1 Adjustment

- [x] ORIENT: branch/HEAD/origin/main/PRZ-025 dependency, clean tree와 SEALED hash 확인
- [x] VERIFY-BEFORE-CHANGE: U01-Q04, U04-Q01, U04-Q03, U02-Q04 회귀 재현
- [x] ANALYZE: 기존 Child 길이 구간별 Gold/rank1/noise 확인
- [x] SPEC/PLAN: context-only heading, 별도 DEV/CAL 1.1.0과 분리 report Gate 고정
- [x] TEST/IMPLEMENT: heading assertion classification과 context-only eligibility
- [x] DATA: DEV/CAL 각 장문 문서 3개, 전체 신규 query 24개와 lineage/manifest
- [x] VERIFY: Original Seed B2 A/B 재실행 및 네 회귀 Before/After
- [x] VERIFY: Long-form A/B raw Dense 실행 및 query/user/profession/language 결과
- [x] VERIFY: contamination/fragmentation/parent-per-fixed/length/cost/latency
- [x] VERIFY: SEALED byte/hash/flags, diff scope, OSS readiness와 관련 test
- [x] AUDIT: blocking finding 0 및 최종 `NEEDS_ADJUSTMENT`
- [x] INTEGRATE: local branch commit only
- [ ] Parent Context/Parent Dense/push/PR/merge — `NOT_RUN` (금지)
