# PRZ-026 Phase 1 Plan

- 상태: `IN_PROGRESS / PHASE_1_ADJUSTMENT_NEEDS_ADJUSTMENT`
- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT(evaluation-only) → VERIFY → AUDIT → INTEGRATE(commit only)`
- 선행 조건: `DEPENDS_ON_PRZ_025`

## 1. 역사적 Phase 1 입력과 freeze

1. PRZ-025 HEAD, `origin/main`, branch 관계와 clean worktree를 기록한다.
2. `search-v3-fresh-seed-1.0.1`의 DEV/CAL manifest, gold와 source만 loader가 연다.
3. PRZ-025 validator를 전후 실행하고 SEALED FINAL combined hash와 flags를 byte-level로 비교한다.
4. 최초 Phase 1에서는 corpus/PDF를 추가하지 않는다. 짧은 seed의 candidate ceiling은 결과 한계로
   공개하며, 보완 fixture가 필요하면 기존 version을 덮지 않는 별도 DEV/CAL version으로 후속
   계획한다.

이 절은 B1 실행 계약의 역사 기록이다. 아래 Phase 1 Adjustment plan은 별도 version
`search-v3-fresh-devcal-1.1.0`을 추가하도록 이 corpus 제한만 supersede하며 Original Seed와
SEALED FINAL은 변경하지 않는다.

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

## 5. Phase 1 Adjustment plan

1. 변경 전 HEAD에서 Phase 1 네 회귀의 rank, block/source/retrieval/parent와 score를 재현한다.
2. 기존 Child 길이를 1–10/11–20/21–40/41–80/81+로 나눠 Gold와 rank noise를 확인한다.
3. parser가 날짜·수치 value를 가진 독립 assertion을 heading으로 버리지 않도록 일반 구조 test를
   먼저 추가한다.
4. builder는 `HEADING`을 context-only boundary로 보존하되 Child로 만들지 않는다. heading을
   retrieval text에 넣거나 길이 기반 merge를 추가하지 않는다.
5. 별도 `search-v3-fresh-devcal-1.1.0` 경로에 DEV/CAL 각 3개의 1,500+ code-point synthetic
   장문 문서와 전체 24+ query를 materialize한다. 이전 seed와 SEALED FINAL은 덮어쓰지 않는다.
6. manifest·source span·lineage validator를 실행한 뒤 dataset input을 freeze한다. 결과 확인 뒤
   query/gold/fixture를 조정하지 않는다.
7. 동일 `bge-m3` query vector로 Original Seed와 Long-form A/B를 별도 report로 실행한다.
   report에는 Adjustment 시작 commit과 실행에 사용한 evaluation source 파일별/combined SHA-256을
   기록한다. DEV/CAL manifest는 실행 가능 정책만 담고 실제 실행 사실은 ignored report와
   `evidence.md`에 기록한다.
8. 결과와 실패 사례를 evidence에 추가하고 scope/hash/test/audit Gate를 통과한 변경만 commit한다.

PDF는 page-local document model과 gold 좌표를 기존 TXT loader에 조용히 섞지 않는다. 기존
PDFBox/Production extractor를 재사용한 evaluation-only fixture가 A/B 계약을 확장하지 않고
구현 가능한지 검토하고, 그렇지 않으면 `BLOCKED_FOR_LATER_LAYOUT_PHASE`로 기록한다.
