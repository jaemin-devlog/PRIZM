# PRZ-043 Tasks

## ORIENT / SPEC / PLAN

- [x] PRZ-042/refactor Git·Production source parity 확인
- [x] ZIP/path/manifest/payload/raw Gold hash 독립 확인 — Gold parse `0`
- [x] 75 users, 90 documents, 600 queries, TXT/PDF와 15 professions 확인
- [x] metric, Gate, Gold 접근 순서와 one-shot 경로 동결

## IMPLEMENT

- [ ] `NOT_RUN` — 안전한 ZIP loader와 Gold-free preflight
- [ ] `NOT_RUN` — contract-bound `officialRunsAllowed=1`과 고정 run directory 강제
- [ ] `NOT_RUN` — V2/V3 별도 prediction 생성·canonicalization·hash freeze
- [ ] `NOT_RUN` — TXT/PDF Production runtime adapter와 page-local provenance
- [ ] `NOT_RUN` — 동결 파일 reload 뒤 completion receipt, Gold-open receipt와 Gold loader
- [ ] `NOT_RUN` — 전체/사용자/직군/category/project-name/typed/no-answer/localization metric과 Gate
- [ ] `NOT_RUN` — PRZ-043 focused·mixed PDF·one-shot integrity test

Gold 의미 필드가 prediction 전에 노출돼 계약상 `EVALUATION_INVALID`로 중단했다. 이후 구현은
평가 결과를 유효하게 만들 수 없으므로 진행하지 않았다.

## VERIFY / AUDIT

- [ ] `NOT_RUN` — synthetic non-final preflight
- [x] 실제 BGE-M3 model/digest/dimension 확인 — neutral embed만 실행
- [ ] `NOT_RUN` — 공식 attempt
- [ ] `NOT_RUN` — Gold reference/span/page integrity와 metric 계산
- [ ] `NOT_RUN` — PRZ-038~041, migration, backend/PostgreSQL/frontend 회귀
- [x] OSS readiness
- [x] Markdown link, `git diff --check`, 변경 범위 감사

## INTEGRATE

- [x] `evidence.md`, `final-run-receipt.json`, `metrics-report.json`
- [x] 판정과 Registry 상태 일치
- [ ] PRZ-043 branch commit/push
- [ ] `NOT_RUN`: PR, refactor/main merge, cutover, 다음 PRZ
