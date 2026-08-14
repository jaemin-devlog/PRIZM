# P1: Numeric + Strong Identifier Retrieval

> 역사 보존: 이 Phase는 초기 `PRZ-015` 문서에서 생성됐으며, 2026-08-14에
> PRZ-013 Search Performance V2의 P1으로 이동했다.

- 상태: `VERIFIED`
- 범위: 명시적인 숫자+단위 근거의 제한적 fallback과, 명시적 기술 경험 질의의 false-positive 차단

기존 dense `bge-m3`, threshold `0.50`, Top20, max5, P4, P18, 자연어 fallback,
PRZ-012 표시·확장 계약을 유지한다. 숫자 rescue와 identifier 존재 확인은 인증된 owner의
ACTIVE 문서 버전 안에서만 수행하며 원래 score와 distance를 바꾸지 않는다.

P2 reranking, 검색 모델·chunking·threshold·ranking weight 변경은 범위 밖이다.
