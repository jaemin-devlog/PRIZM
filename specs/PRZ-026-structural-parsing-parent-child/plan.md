# PRZ-026 Phase 1 Plan

- 상태: `IN_PROGRESS / PHASE_1_NEEDS_ADJUSTMENT`
- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT(evaluation-only) → VERIFY → AUDIT → INTEGRATE(commit only)`
- 선행 조건: `DEPENDS_ON_PRZ_025`

## 1. 입력과 freeze

1. PRZ-025 HEAD, `origin/main`, branch 관계와 clean worktree를 기록한다.
2. `search-v3-fresh-seed-1.0.1`의 DEV/CAL manifest, gold와 source만 loader가 연다.
3. PRZ-025 validator를 전후 실행하고 SEALED FINAL combined hash와 flags를 byte-level로 비교한다.
4. 이 Phase에서는 corpus/PDF를 추가하지 않는다. 짧은 seed의 candidate ceiling은 결과 한계로
   공개하며, 보완 fixture가 필요하면 기존 version을 덮지 않는 별도 DEV/CAL version으로 후속
   계획한다.

## 2. 구현 순서

1. `src/searchEvaluation/java/.../searchv3/structural/`에 source/provenance model을 만든다.
2. 일반적인 line/layout 신호의 `StructuralBlockParser`를 구현한다.
3. source/retrieval text를 분리한 `StructuralEvidenceChildBuilder`를 구현한다.
4. actual Production `TextChunker` adapter와 PRZ-025 DEV/CAL-only loader를 만든다.
5. dependency 추가 없이 Java HTTP client로 local Ollama `/api/embed`의 `bge-m3`를 호출한다.
6. in-memory cosine ranker와 source-grounded gold mapper/metric calculator를 만든다.
7. report는 Git 제외 경로 `local/search-v3-evaluation/prz026/`에 쓰고, 재현에 필요한 query별
   결과와 report SHA-256을 `evidence.md`에 요약한다.

## 3. Test와 verification

- Parser: heading, paragraph, bullet, numbered list, key-value, table, blank boundary,
  Korean/English/mixed와 type fallback
- Builder: exact source/provenance, heading boundary, cross-parent 비혼합, table header trace,
  long fallback split와 global overlap 0
- Evaluation: same corpus/query/model, Production TextChunker 800/120, runtime ID contamination 0,
  DEV/CAL allowlist, SEALED FINAL guard, source-grounded Unit mapping
- Runtime: local Ollama tag/digest와 1024 dimensions 확인 후 DEV/CAL A/B 1회 실행
- Repository: PRZ-025 validator/test, targeted Gradle tests, `git diff --check`, OSS readiness,
  forbidden-path diff와 sealed hash/flags audit

Backend 전체 unit/integration, frontend와 Docker runtime은 Production 변경이 없고 이 in-memory
evaluation에 필요하지 않으면 `NOT_RUN`으로 기록한다.

## 3.1 실제 Phase 1 deviation

PDF/long-document fixture를 추가하지 않고 frozen seed를 그대로 사용했다. DEV/CAL ACTIVE 문서
7개가 모두 800자 미만이어서 A는 문서당 1 candidate였고 Recall@5 이상에 ceiling이 생겼다.
이는 실행 전 확인한 입력 특성이며 결과에 맞춰 dataset을 바꾸지 않았다. 이 Phase 결과는
구조·runner 검증과 조정 필요성의 근거이고, 긴 문서 일반화나 release-grade 품질 근거가 아니다.

## 4. 실패와 rollback

Ollama/model이 없거나 필수 benchmark가 실패하면 품질 판정을 검증 완료로 표현하지 않는다.
scope 밖 diff가 생기면 commit하지 않고 evaluation-only 변경만 조사한다. rollback 대상은 새
PRZ-026 파일과 Registry 한 행뿐이며 PRZ-025, 사용자 PRZ-016 worktree, tag/history는 reset,
rebase 또는 rewrite하지 않는다. push, PR, main merge는 금지한다.
