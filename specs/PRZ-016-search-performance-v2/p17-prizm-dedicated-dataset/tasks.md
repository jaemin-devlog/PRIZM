# PRZ-016 P17 PRIZM Dataset Tasks

- [x] ORIENT: schema v2, runner, TEST gate, Production chunking과 기존 frozen 경계 확인
- [x] SPEC: 114문서·300문항, 세 cohort와 split별 positive/no-evidence 균형, provenance, qrel과 비범위 고정
- [x] PLAN: 파일·생성 순서·검증·중단 조건 Gate 확인
- [x] IMPLEMENT: frozen fictional fact matrix와 deterministic generator
- [x] IMPLEMENT: `$humanize-korean` 문장별 윤문과 로컬 보호 요소 감사 — 문장 항목 576개·보호 요소 619개, 변경·추가 0건 `PASS`
- [x] IMPLEMENT: corpus.json, questions.jsonl, dataset card와 freeze manifest
- [x] IMPLEMENT: dataset contract test와 explicit frozen TEST allowlist
- [x] IMPLEMENT: PRZ-016 registry와 검색 평가 문서 갱신
- [x] VERIFY: generator drift와 dataset·loader·selector focused test 24건 `PASS`
- [x] VERIFY: full backend unit `PASS`
- [x] VERIFY: SBOM/OSS readiness, diff와 기존 frozen asset 비변경 `PASS`
- [x] VERIFY 기록: frozen TEST·실제 PDF·OpenSQL·Production 방식 비교 `NOT_RUN` — holdout과 근거 경계 보존
- [x] VERIFY 기록: 현재 evaluation FTS와 dataset 실행 적합성 `NOT_VERIFIED` — 자연어 전체 `simple` AND 질의
- [x] AUDIT: privacy·license·split leakage·qrel·scope 독립 점검 — blocking finding 0건
- [ ] INTEGRATE: commit/push/PR/merge `NOT_RUN` — 사용자 승인 범위 밖
