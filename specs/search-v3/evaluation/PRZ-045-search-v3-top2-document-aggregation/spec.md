# PRZ-045 Search V3 Top2 문서 순위 집계

- 상태: `VERIFIED`
- 유형: Search V3 shadow query 최종 순위 변경
- branch: `PRZ-045-search-v3-top2-document-aggregation`
- 기준: `PRZ-044-search-v3-release-grade-evaluation@48f15e5f82fb4c7c90973c73f32bd0220443576b`
- Production Search V2 적용: `NO_CHANGE`

## 목적

Search V3의 Top20 `RetrievalPassage` 후보와 기존 Child 선택은 유지하면서, 여러 관련 Passage가 있는
문서가 최종 결과에서 단일 Passage 최고 점수만으로 밀리지 않도록 문서 단위 순위를 계산한다.

## 순위 계약

문서별로 Dense Top20 안의 Passage를 기존 순서대로 읽고, 같은 EvidenceChild 또는 동일 원문 범위를
공유하지 않는 상위 최대 2개를 집계한다.

- Passage 2개: `documentScore = (score1 + score2) / 2`
- Passage 1개: `documentScore = score1`
- Passage가 일부라도 이미 집계한 Child ID 또는 source span과 겹치면 중복 Passage로 보고 제외
- 문서 점수 동점: 가장 높은 기존 Passage 순위, document ID 순
- 같은 문서의 최종 근거 순서: 기존 Child 선택 순서 유지

후보 추가·삭제, Top20 조회, cosine 계산, `CHILD_DENSE_V1`, typed validation과 최대 5개 근거 선택은
바꾸지 않는다. 최종 선택된 근거 집합은 그대로 두고 문서 점수에 따라 순서만 바꾼다.

## 보존 경계

- Structural parsing, `EvidenceChild`, B3 `RetrievalPassage`, 480 code-point 상한 불변
- BGE-M3 모델·digest·dimension과 query vector 불변
- owner·ACTIVE generation·document version 격리 불변
- source/page/line/code-point provenance 불변
- Search V2 source·query·API 불변
- migration·dependency·frontend·MCP 변경 없음

## 수용 기준

- 단일 Passage 문서는 기존 점수를 유지한다.
- 비중복 Passage 2개는 단순 평균하고, 강한/약한 Passage에도 같은 공식을 적용한다.
- 동일 Child 또는 source span은 두 번 집계하지 않는다.
- 문서 순위와 동점 순서는 결정적이다.
- 최종 최대 5개 EvidenceChild의 identity와 provenance는 순위 변경 전후 동일하다.
- Search V3 focused·integration과 전체 backend test가 통과한다.
- 기존 90문항 local diagnostic을 실행할 수 있으면 `89/90`, 기존 정답 `85/85`, Recall@5 `90/90`을
  확인하고, 실행하지 못하면 `NOT_RUN`으로 기록한다.

## 비범위

- Top3, 가중 평균, threshold, boost 또는 다른 heuristic
- 새 평가 dataset·attempt·receipt·contract·framework
- 새 모델, embedding 변경, Passage/Child 생성 변경
- Search V2 변경, Search V3 API cutover, migration
