# PRZ-016 P16: Literal Candidate Phase A

- 상태: `NEEDS_ADJUSTMENT`
- 범위: evaluation/benchmark/test/spec only
- 기준 source: `05044b11038eeebaebac650c67d0d90136ae10bc`

## 문제와 목표

현재 Production은 owner의 `ACTIVE` chunk를 BGE-M3 cosine distance로 정렬해 Dense Top20을
만든 뒤 source consolidation, Structured Claim Support eligibility, query-evidence
consolidation, ranking, localization을 적용한다. Phase A는 단순 기술명·식별자 표현이 원문에
실제로 있어도 Dense Top20 밖에 남는 경우가 있는지, 그런 chunk를 evaluation-only literal
채널이 candidate 단계에 추가할 수 있는지만 확인한다.

## Mode

- `D0`: 변경하지 않은 Production Search와 동일한 BGE-M3 Dense Top20 및 현재 filter.
- `D1`: D0 Dense Top20과 같은 query 표현의 case-insensitive exact-boundary 원문 match
  Literal Top20을 chunk ID로 합친 뒤 현재 Production filter를 적용한다.
- Literal match는 candidate 추가 근거일 뿐 최종 evidence 승인이나 점수 boost가 아니다.

## Dataset

- 실제 개인정보나 경력 문서를 사용하지 않고 synthetic corpus만 사용한다.
- 주 owner의 `ACTIVE` corpus는 60~120 chunks로 고정하고 SHA-256 manifest로 freeze한다.
- positive identifier는 Redis, Spring Boot, Tauri, Bun, LangGraph, FooEngine과 새 multi-word
  identifier를 포함한다.
- negative/safety query는 owner corpus에 없음, 다른 owner에만 존재, inactive version에만
  존재, FooEngine/FooEngineX, Bun/Bundle, case 차이를 포함한다.
- identifier 값과 정답은 dataset에만 있고 implementation의 whitelist나 이름별 분기는 금지한다.

## 보존 계약과 비범위

- `src/main`, Production SQL/API/config, Flyway, DB schema/index, frontend는 수정하지 않는다.
- P6의 PostgreSQL FTS, RRF와 literal gate는 사용하지 않는다.
- BGE-M3 sparse, literal boost, reranker, threshold/score/distance 조작, 기술명 whitelist,
  migration과 Phase B 최종 품질 비교는 제외한다.
- owner와 `documents.active_version_id` + `version.status='ACTIVE'` 경계를 literal SQL의
  document/version/chunk 세 수준에서 모두 유지한다.
- commit, push, PR과 Production 적용은 수행하지 않는다.

## Acceptance criteria

1. frozen synthetic dataset의 주 owner `ACTIVE` chunk 수가 60~120이고 query/ground truth
   hash가 실행 전후 일치한다.
2. D0 evaluation 결과의 state와 final chunk ID 순서가 실제 `SearchService` 응답과 모든
   frozen query에서 동일하다.
3. query마다 dense 정답 존재, literal 정답 존재, literal-only 정답 존재, union 정답 존재를
   분리 기록한다.
4. positive query 중 최소 1개에서 Dense Top20 밖 정답이 Literal Top20으로 union에 추가된다.
5. 다른 owner match, inactive-only match, FooEngineX와 Bundle near-miss는 literal candidate가
   아니며 case 차이의 exact match는 candidate가 된다.
6. literal query parser/repository/union에 dataset identifier 이름의 whitelist나 분기가 없다.
7. Production search 35개 source의 aggregate SHA-256이 전후
   `743c767b4f893d112199b99888b34e9727771e1020259c0d3ae9465678510ee5`로 같고
   `src/main`, migration, frontend diff가 0개다.
8. focused P16, backend unit, integration, `git diff --check`가 통과하고 AUDIT blocking
   finding이 0건이다.
