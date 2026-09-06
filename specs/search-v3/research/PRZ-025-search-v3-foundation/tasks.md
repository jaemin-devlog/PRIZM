# PRZ-025 Search V3 Foundation Tasks

- 상태: `IN_PROGRESS / FRESH_BENCHMARK_SEED_FROZEN`
- Production Search V3 구현: `NOT_RUN`
- Fresh seed materialization/integrity validation: `COMPLETED`
- Search benchmark/generalization 실행: `NOT_RUN`

## 이번 Phase

- [x] ORIENT: 최신 `origin/main`, local `main`, start working tree와 격리 worktree 확인
- [x] ORIENT: `v1.0.0` tag commit과 GitHub Release 존재·불변 경계 확인
- [x] ORIENT: 기존 PRZ-025, 동목적 branch/spec, open PR/Issue 부재 확인
- [x] ORIENT: 필수 기준 문서와 PRZ-001/008/012/016/017/022/024 확인
- [x] ORIENT: Production 검색·ingestion·API·MCP source와 관련 source tests 확인
- [x] SPEC: Current Search frozen baseline과 known limitations 기록
- [x] SPEC: Source Block, Evidence Unit/Child, Parent, Group, optional Experience Unit 계약
- [x] SPEC: 직무 일반화와 query category 계약
- [x] SPEC: answerability, support relation, numeric/entity/date gold 계약
- [x] SPEC: DEV/CALIBRATION/SEALED_FINAL_TEST와 leakage/seal 규칙
- [x] SPEC: fresh benchmark target, data source와 privacy 경계
- [x] SPEC: metric hierarchy, user-macro/worst-group와 adoption Gate
- [x] SPEC: ablation-first, complexity budget와 algorithm neutrality
- [x] PLAN: benchmark materialization, seal, calibration, finalist와 independent final 순서
- [x] PLAN: 대규모 raw artifact를 PRZ 폴더에 누적하지 않는 보관 방향
- [x] IMPLEMENT: `spec.md`, `plan.md`, `tasks.md`, `evidence.md` 작성
- [x] IMPLEMENT: `specs/README.md` Registry에 PRZ-025 `IN_PROGRESS` 추가
- [x] VERIFY: 문서 정합성, local Markdown link, status vocabulary 확인
- [x] VERIFY: `git diff --check`와 repository verifier 실행·실제 결과 기록
- [x] VERIFY: diff allowlist와 Production/migration/dependency/frontend/MCP 변경 0 확인
- [x] AUDIT: 기존 FAIL/NO_GO/NEEDS_ADJUSTMENT와 fresh/historical 경계 독립 검토
- [x] AUDIT: `SAFE_TO_START_NEXT_PHASE` — benchmark materialization/seal 범위로 한정
- [x] INTEGRATE: 허용 파일만 branch commit
- [ ] INTEGRATE: PR 생성·main merge — `NOT_RUN` (금지 범위)

## Phase 2 — Fresh Seed materialization

- [x] ORIENT: Phase 1 HEAD, `origin/main`, clean working tree와 Phase 1 diff 재확인
- [x] ORIENT: PRZ-025, search evaluation, PRZ-001과 PRZ-016 historical evidence 재확인
- [x] SPEC: pre-seal 좌표, aspect expression, adapter, strict lineage와 Gate 순서 명료화
- [x] PLAN: 기존 V2와 분리한 `search-v3-evaluation/` 저장·봉인 구조 확정
- [x] IMPLEMENT: dependency 없는 benchmark/prediction JSON schema 추가
- [x] IMPLEMENT: 7개 직무군, 11 document versions, 29 query synthetic seed materialize
- [x] IMPLEMENT: Source Parent/Unit/Group와 exact source span/numeric/entity/date gold materialize
- [x] IMPLEMENT: DEV 3 / CALIBRATION 2 / SEALED FINAL 2 bundle manifest와 SHA-256 봉인
- [x] IMPLEMENT: deterministic schema/source/relation/constraint/leakage/freeze validator 추가
- [x] VERIFY: validator 정상 입력과 invalid mutation 17종 support unit test 통과
- [x] VERIFY: manifest/count/distribution 재검증과 sealed-final 검색 결과 파일 0 확인
- [x] VERIFY: `git diff --check`, OSS readiness와 diff scope Gate
- [x] AUDIT: 문서·schema·gold·manifest 정합성과 개인정보/역사 판정 독립 검토
- [x] INTEGRATE: Phase 2 결과 local branch commit
- [ ] INTEGRATE: push·PR·main merge — `NOT_RUN` (금지 범위)

## 후속 Phase — 아직 시작하지 않음

- [ ] `OPEN_DECISION`: benchmark data 공급·license/privacy 운영 방식 승인
- [ ] `NOT_RUN`: 최소 50 user-bundle release-grade corpus와 role/document/language 분포 materialization
- [x] seed only: bundle-first split assignment와 automatic leakage audit
- [x] seed only: source-span gold annotation과 deterministic validation
- [x] seed only: `SEALED_FINAL_TEST` manifest/hash 봉인; 검색 실행 `NOT_RUN`
- [ ] `NOT_RUN`: Search Quality·Operational 숫자 Gate를 final 개봉 전에 `FROZEN_GATE`로 전환
- [ ] `NOT_RUN`: DEV/CALIBRATION ablation과 complexity budget 비교
- [ ] `NOT_RUN`: frozen Current Search와 finalist V3의 independent final comparison
- [ ] `NOT_RUN`: adoption 판정과 별도 Production change workflow
