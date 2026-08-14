# P2: Lightweight Evidence-aware Reranking

> 역사 보존: 이 Phase는 초기 `PRZ-016` 문서에서 생성됐으며, 2026-08-14에
> PRZ-013 Search Performance V2의 P2로 이동했다.

- 상태: `VERIFIED`
- 범위: 기존 검색 계약을 통과한 candidate 안에서 원문 근거 품질을 작은 보조 점수로 반영

기존 dense score와 P4 soft ranking을 주 신호로 유지하고, 질문 관련성·수행 행동·문제/행동/결과·
수치 및 구체 근거를 deterministic evaluator로 평가한다. 보정값은 `-0.035`부터 `+0.065`까지로
제한하며 기술 스택·제목·프로필형 내용은 감점한다.

새 candidate 조회나 제거, score/distance/API 변경은 하지 않는다. threshold `0.50`, Top20,
max5, P18, P1 numeric/identifier 보호, owner/ACTIVE 격리와 PRZ-012 표시·확장 계약도 유지한다.
