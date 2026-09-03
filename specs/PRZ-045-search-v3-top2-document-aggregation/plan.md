# PRZ-045 Plan

1. 현재 Search V3 Top20 조회, Child 선택, typed validation과 결과 DTO 계약을 확인한다.
2. Passage와 Child provenance만 받는 순수 문서 순위 구성 요소를 추가한다.
3. 기존 최종 근거 집합을 바꾸지 않고 문서 순위에 따라 결과 순서만 재배치한다.
4. 단일/복수/중복/동점/순서/provenance 계약을 단위 테스트로 고정한다.
5. Search V3 focused·PostgreSQL integration과 전체 backend test를 실행한다.
6. 최종 diff와 Search V2·migration·dependency 비변경을 감사한 뒤 evidence와 Registry를 갱신한다.
7. Gate가 통과한 경우에만 현재 PRZ-045 branch에 일반 commit을 만든다.

Rollback은 새 순위 구성 요소 호출과 파일을 제거하는 것으로 끝난다. DB와 저장 artifact 형식은 바뀌지 않는다.
