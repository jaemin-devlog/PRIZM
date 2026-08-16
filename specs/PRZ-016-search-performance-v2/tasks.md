# PRZ-016 Search Performance V2 Tasks

## P6 Retrieval Architecture Shadow Benchmark

- [x] ORIENT: branch/HEAD/worktree, workflow, architecture/status/roadmap, PRZ-008 P13/P14,
  PRZ-016 P0~P5, 실행 중 PostgreSQL/Ollama/API 확인
- [x] SPEC: D0/L1/H1/H2, freeze 순서, 보존·비범위와 mandatory gate 확정
- [x] PLAN: evaluation-only 파일, read-only DB, 검증·중단·Git 계획 확정
- [x] P6-A: 기존 P13 lexical repository/RRF 재사용 contract 고정
- [x] P6-A: external corpus runner와 D0/L1/H1 Q0·end-to-end diagnostics 구현
- [x] P6-A: 72 development와 P5 diagnostic 실행·결과 저장
- [x] P6-B 준비: 24~32개 Identifier Stress Set과 ground truth 직접 검증
- [x] P6-B 준비: stress dataset/ground truth/production source SHA-256 freeze
- [x] P6-B: Generic Literal Evidence Gate와 per-query diagnostics 구현
- [x] P6-B: stress/72/P5/legacy/numeric/positive identifier guard 실행
- [x] VERIFY: owner/ACTIVE, P3 sequential, score/distance, latency와 전체 backend 회귀
  (`test` PASS, `integrationTest` 기존 production 회귀 1건 FAIL)
- [x] AUDIT: production diff 0, freeze 불변, 금지 범위, 결과 정합성, 민감정보 확인
- [x] 최종 판정 `NO_GO`와 56개 보고 항목 작성 후 종료
