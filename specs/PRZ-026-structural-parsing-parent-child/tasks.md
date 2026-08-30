# PRZ-026 Phase 1 Tasks

- 상태: `IN_PROGRESS / PHASE_1_RETRIEVAL_PASSAGE_PROMISING`
- A/B 실행 결과: `COMPLETED — PROMISING` (B1/B2 및 최초 B3 `NEEDS_ADJUSTMENT` 기록 유지)
- C1 Parent Context: `INPUT_READY / BENCHMARK_NOT_RUN`; Parent Dense: `NOT_RUN`

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

## Phase 1 C1 Structural Heading Path Parent Context

- [x] ORIENT: clean HEAD `1bbc1d7...`, B3/EvidenceChild hashes와 SEALED guard 확인
- [x] SPEC: B3 불변·heading-path-only treatment·parity/Safety/Search Gate 고정
- [x] PLAN: unit test → input-freeze commit → B3/C1 1회 benchmark → audit 순서 고정
- [x] TEST/IMPLEMENT: contextual passage와 `STRUCTURAL_HEADING_PATH_V1` builder
- [x] TEST/IMPLEMENT: B3/C1 parity, source-only Gold와 context-only false-hit 진단
- [x] IMPLEMENT: Original/Long-form/robustness B3/C1 동일 query-vector runner
- [ ] INTEGRATE-INPUT: 결과 전 source/test/contract local commit
- [ ] VERIFY: 동일 BGE-M3 B3/C1 benchmark 1회 실행
- [ ] VERIFY: aggregate/user/profession/language와 direct win/loss/tie 기록
- [ ] VERIFY: SEALED hash/flags, PRZ-025 validator, diff/OSS/scope와 관련 test
- [ ] AUDIT: blocking finding과 C1/Parent Dense 진입 판정
- [ ] INTEGRATE: result/evidence local commit only
- [ ] Parent Dense/push/PR/merge — `NOT_RUN` (금지)

## Phase 1 Retrieval Passage

- [x] ORIENT: `e5012fd...`, clean tree, B2 evidence와 SEALED hash 확인
- [x] SPEC/PLAN: same-parent adjacency와 `120/320/480`, overlap 0 정책을 실행 전에 고정
- [x] TEST/IMPLEMENT: RetrievalPassage, ordered EvidenceChild ID/provenance와 table context 보존
- [x] TEST/IMPLEMENT: cross-parent/heading/non-adjacent/max-bound/Gold-input 금지 검증
- [x] IMPLEMENT: A/B2/B3 동일 corpus/query/model/query-vector runner
- [x] VERIFY: Original Seed A/B2/B3 raw Dense 실행
- [x] VERIFY: Long-form DEV/CAL A/B2/B3 raw Dense 실행
- [x] VERIFY: passage/cost/ranking/user/profession/language/boundary 결과 기록
- [x] VERIFY: SEALED hash/flags와 관련 evaluation test
- [x] AUDIT: final diff/OSS/scope와 문서 정합성
- [x] INTEGRATE: 허용 파일만 local branch commit
- [ ] Parent Context/Parent Dense/push/PR/merge — `NOT_RUN` (금지)

## Phase 1 Retrieval Passage Robustness

- [x] ORIENT: 요청 worktree 격리, clean HEAD `01d9ae2...`, B3 역사 판정과 Final guard 확인
- [x] SPEC: B3 policy freeze, 별도 robustness suite와 sample-sufficiency/paired Gate 고정
- [x] PLAN: fixture freeze commit 후 benchmark 실행, rollback/금지 범위 고정
- [x] DATA: 독립 DEV/CAL 6 bundles, 24 DIRECT queries와 lineage/manifest materialize
- [x] TEST/IMPLEMENT: robustness loader, leakage/hash validation과 paired clustered-bootstrap Gate
- [x] INTEGRATE-INPUT: benchmark 전 fixture·계약 local commit `0fe0b3c...`
- [x] VERIFY: 동일 BGE-M3 A/B2/B3 robustness benchmark 1회 실행
- [x] VERIFY: fresh/cumulative profession-language 표본·paired delta·uncertainty 기록
- [x] VERIFY: SEALED hash/flags, PRZ-025 validator, diff/OSS/scope와 관련 test
- [x] AUDIT: blocking finding 0, 다음 Parent Context experiment 진입 가능 독립 검토
- [x] INTEGRATE: result/evidence local commit `b1949df15120e30f915101575919fadf9300b6a2`
- [ ] Parent Context/Parent Dense/push/PR/merge — `NOT_RUN` (금지)
