# PRZ-026 Phase 1 Tasks

- 상태: `IN_PROGRESS / PHASE_1_NEEDS_ADJUSTMENT`
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
- [x] AUDIT: 실패 사례와 판정 독립 검토, blocking finding 0 확인
- [x] INTEGRATE: 허용 파일만 local branch commit
- [ ] INTEGRATE: push·PR·main merge — `NOT_RUN` (금지)
