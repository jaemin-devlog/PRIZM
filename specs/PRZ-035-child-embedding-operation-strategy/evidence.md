# PRZ-035 Evidence

## 현재 상태

`IN_PROGRESS / OFFICIAL_COMPARISON_NOT_RUN`

- 기준: `refactor/search-v3@a6fb5ee5240b0b1fcc59f78b329b55563512df1d`
- branch: `PRZ-035-child-embedding-operation-strategy`
- Production 변경: `0`
- 평가 입력: PRZ-034 DEV/CAL 117 query 재사용
- SEALED FINAL: 미개봉·검색 미실행

## 사전 감사

PRZ-034의 227개는 117 query의 Top5에서 접근한 고유 Child 수다. PRZ-032 frozen B3 index에는
160 Passage와 241 EvidenceChild가 있다. 따라서 색인 시 모두 미리 계산하는 전략 A는 241개를,
no-cache 전략 B는 query별 `3 / 평균 6.8718 / 최대 16`, 합계 804회를 계산하도록 고정했다.
전략 B의 고유 Child는 227개이고 반복 재계산은 577회다.

Raw 저장량 사전 산식:

- vector 1개: `4,096 B`
- Passage 160개: `655,360 B`
- Child 241개: `987,136 B`
- Passage+Child 401개: `1,642,496 B`
- Passage-only 대비 총 vector/storage 증가: `+150.625%`

PRZ-034에서 기록한 `160→387, +141.875%`는 Top5 고유 Child 227개만 대상으로 한 selector 평가
비용이다. PRZ-035 전략 A의 전체 색인 저장량과 의미가 다르며, 과거 수치를 바꾸지 않고 이 차이를
`DOCUMENTATION_CLARIFICATION`으로 남긴다.

## 실행 전 경계

- Production `EmbeddingService`: single-text `embed(String)`
- PRZ-035: evaluation-only `embedAll`, batch size 32
- Production batch inference, DB row/index overhead, CPU-only 성능, content-hash 재사용:
  `NOT_VERIFIED / NOT_IMPLEMENTED`
- 전략 A의 query 시간은 in-memory vector map 조회이며 영구 저장소 read·DB index overhead는
  `NOT_MEASURED`다.
- 전략 A의 전체 색인 수치는 B3 구간과 warmed Child embedding 구간을 더한
  `SUMMED_OBSERVATION_NOT_CONTIGUOUS_WALL_TIME`으로만 기록한다.
- 공식 raw output/report: ignored `local/search-v3-evaluation/prz035/`

공식 결과와 최종 판정은 code freeze 및 1회 실행 후 이 문서에 추가한다.
