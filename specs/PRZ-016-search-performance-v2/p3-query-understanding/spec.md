# P3: Query Understanding / 자연어 검색 retrieval fallback

> 역사 보존: 이 Phase는 통합 전 임시 `PRZ-013 자연어 검색 retrieval fallback` 문서다.
> 상위 PRZ 관리 구조로 확장하면서 검증된 fallback은 유지하고, 더 넓은 query
> understanding은 후속 범위로 남긴다.

- 완료된 하위 범위: `VERIFIED` (초기 자연어 retrieval fallback)
- Phase 상태: `DONE`
- 범위: 원본 semantic 결과가 비어 있을 때만 실행하는 보수적 query understanding과
  제한적 multi-query dense retrieval

## 요구사항

- 원본 질의를 먼저 그대로 임베딩한다.
- Spring Boot 표기와 영문 기술명 뒤 한국어 조사를 보조 표현에서만 정규화한다.
- 질문 관계를 보존한 자연어 variant를 최대 2개 사용하며 단일 키워드로 축약하지 않는다.
- 원본 결과가 충분하면 variant를 생성해도 실행하지 않는다.
- `스프레드시트↔엑셀`, `운영 환경에 올리다↔배포`, `파일 제공↔파일 서빙`,
  `다시 확인↔재검증`, `실제 사용자가 있는 서비스↔실사용 서비스`처럼 높은 신뢰도의
  의미만 보조 표현으로 사용한다.
- variant는 원문의 strong identifier와 numeric anchor를 모두 보존한다.
- variant별 Top20 candidate를 기존 threshold `0.50`으로 평가하고 chunk ID로 합친다.
  같은 chunk는 가장 높은 정상 dense similarity의 score/distance를 유지한다.
- 경험형 질의는 질문의 구체 핵심어가 원문에 직접 존재하는 결과만 허용한다.
- 기존 threshold `0.50`, Top20, max5, P4, P18, Claim Gate, owner·ACTIVE 경계를 유지한다.
- PRZ-012 Evidence Presentation / Expansion은 변경하지 않는다.

## 완료 조건

P3 target 7개 중 최소 4개를 개선하고, 기존 자연어·P1 numeric·strong identifier
negative 회귀가 없어야 한다. 동일 72-query benchmark에서 Recall@5와
QUERY_UNDERSTANDING/CANDIDATE_RECALL이 개선되고 owner·ACTIVE 격리, backend test와
`git diff --check`가 통과해야 한다.

P4 Evidence Localization과 P5 holdout validation은 범위 밖이다.
