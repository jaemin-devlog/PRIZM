# PRZ-027 Tasks

- 상태: `IN_PROGRESS / BENCHMARK_NOT_RUN`

- [x] ORIENT: PRZ-026 `a7dbb12...`, origin/main, dependency와 clean tree 확인
- [x] SPEC: R0/R1 단일 변수, Top20, identity/Gold/Safety와 판정 Gate 고정
- [x] PLAN: export → input freeze → inference → strict import → audit 순서 고정
- [x] TEST/IMPLEMENT: Gold-free pair export와 B3 baseline 분리
- [x] TEST/IMPLEMENT: fixed model/code revision CPU scorer
- [x] TEST/IMPLEMENT: strict score import, Top20-only rerank, deterministic tie-break
- [x] TEST/IMPLEMENT: Top1/MRR/nDCG/Recall, macro/slice/category/operation Gate
- [ ] VERIFY: 관련 PRZ-025/026 integrity, SEALED guard, scope/OSS
- [ ] INTEGRATE-INPUT: result 전 local commit
- [ ] VERIFY: 공식 R0/R1 세 dataset 실행 1회
- [ ] AUDIT: 모든 loss와 QueryPlanner 진입 판정
- [ ] INTEGRATE: result/evidence local commit only
- [ ] QueryPlanner/Production/PR/push/merge — `NOT_RUN`
