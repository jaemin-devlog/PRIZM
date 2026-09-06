# PRZ-035 Evidence

## 최종 판정

`PRECOMPUTE_CHILD_EMBEDDINGS`

두 전략은 PRZ-034 `CHILD_DENSE_V1`의 최종 결과를 정확히 재현했다. 미리 계산한 전략 A는 query
p50/p95 `25.8553/36.8953ms`, no-cache 전략 B는 `86.1798/143.8280ms`였다. B는 117 query에서
804개 vector를 계산해 577회를 반복했고, A의 241개 Child raw 저장량은 `987,136 B`였다. 저장량,
응답 경로, 약 35회 검색의 계산량 손익분기점을 함께 적용한 사전 Gate에 따라 A를 운영 설계 후보로
선택한다. 이는 Production 적용 완료가 아니다.

## 기준과 실행 무결성

- 기준: `refactor/search-v3@a6fb5ee5240b0b1fcc59f78b329b55563512df1d`
- branch: `PRZ-035-child-embedding-operation-strategy`
- code freeze: `4526437ebc3e36e7eec43d3a74889a6b055c066e`
- Production 변경: `0`
- 평가 입력: PRZ-034 DEV/CAL 117 query 재사용
- BGE-M3: `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, 1024 dimensions, cosine
- input canonical SHA-256:
  `778b79117d47344433bed8d01f0f18a39ab4ae20f8f0ff444b2d8d5bd41c43ca`
- strategy/config/source SHA-256:
  `117df007373724426c4beb2e670088bda118d6a6b7e90eae2b46580bc692e7fb` /
  `b203179dfe2f180fe579ea212ec46cd6d71b8f19ceb97f6da2ca4fb9584377f3` /
  `c089dee699df8a8492f9f38082baf589ba1fc75b2569a13e266ef0c303341a6a`

공식 실행은 code freeze 뒤 1회 수행했다. A/B output을 각각 CREATE_NEW로 봉인·검증한 후에만 Gold를
열어 지표를 계산했다.

`execution-contract.json`의 `OFFICIAL_COMPARISON_NOT_RUN`은 공식 실행 직전에 고정한 상태다. 실행
완료 여부는 이 문서와 ignored report가 기록하며, report가 계약 파일 SHA-256
`8c9286cef0b895e52f3164291bf291f5d3be9d54767e7da66dcdb40c928309cb`를 참조하므로 실행 뒤 계약 파일을
수정하지 않았다.

- A output SHA-256: `ff9f850328bbcbe70e1a4522526bcbae74d56d0f4f4b5a74ff0678fff6367475`
- B output SHA-256: `1cf7f5bb5a18104ee5deceb8727b7ba8e8badcbbc1e3bebf56f910212f8d276f`
- report SHA-256: `3da9f6d323fd842c3aa548c41fb232b78e9a2308bebdffd817e2c95f366438f1`
- prediction/final identity SHA-256:
  `652bf70fb9d045de5e14d30a25e106142e45e8a082d80caa064e2cf6bfd6ea32` /
  `88cc2b41d6be73de8f0ac19d0dd575bf7130442b9f9dc481ccb24895c41a6385`

## 결과 parity

Passage 순서, Child 후보와 순서, query vector SHA-256, 최종 EvidenceChild ID, provenance와 모든 품질
지표가 A/B/PRZ-034 사이에서 일치했다.

| DEV/CAL 117 query | 전략 A | 전략 B |
| --- | ---: | ---: |
| Top1 | 0.9059 | 0.9059 |
| MRR | 0.9412 | 0.9412 |
| nDCG@5 | 0.9258 | 0.9258 |
| Recall@5 | 0.9882 | 0.9882 |
| user-macro Top1 | 0.9006 | 0.9006 |
| user-macro MRR | 0.9397 | 0.9397 |

`RESULT_PARITY_FAIL`은 발생하지 않았다. 검색 품질을 새로 튜닝하지 않았으며 PRZ-034 결과를
재현하는 데 그쳤다.

## Child와 embedding 비용

PRZ-034의 227개는 117 query의 Top5에서 접근한 고유 Child 수다. PRZ-032 frozen B3 index에는
160 Passage와 241 EvidenceChild가 있다. 따라서 색인 시 모두 미리 계산하는 전략 A는 241개를,
no-cache 전략 B는 query별 `3 / 평균 6.8718 / 최대 16`, 합계 804회를 계산하도록 고정했다.
전략 B의 고유 Child는 227개이고 반복 재계산은 577회다. 공식 관찰값은 다음과 같다.

| 항목 | 전략 A: 미리 계산 | 전략 B: 요청 시 계산 |
| --- | ---: | ---: |
| Child embedding 발생 횟수 | 241 | 804 |
| 고유 접근 Child | 241 | 227 |
| 애플리케이션 model 호출 | 1 | 117 |
| 실제 batch 수 | 8 | 117 |
| Child embedding 합계 | 2,159.0225ms | 8,307.2327ms |
| 반복 재계산 | 0 | 577 |

B의 query당 Child는 최소 3개, 평균 6.8718개, 최대 16개였다. Child embedding p50/p95는
`60.2641/118.3368ms`였다. A의 query 시간은 저장된 vector를 in-memory map으로 읽는 모의이며,
영구 저장소 조회 비용은 포함하지 않는다.

색인 관찰:

- B3 구조 생성 `26.0550ms`, Passage embedding `4,937.0450ms`, indexing wall `4,946.0464ms`
- A 추가 Child embedding `2,159.0225ms`
- A 합산 `7,131.1239ms`, B 합산 `4,972.1014ms`

A 합산은 B3 색인과 warmed Child embedding 구간을 더한
`SUMMED_OBSERVATION_NOT_CONTIGUOUS_WALL_TIME`이다. 실제 연속 Production 색인 wall time이 아니다.

Raw 저장량 사전 산식:

- vector 1개: `4,096 B`
- Passage 160개: `655,360 B`
- Child 241개: `987,136 B`
- Passage+Child 401개: `1,642,496 B`
- Passage-only 대비 총 vector/storage 증가: `+150.625%`

Child 수가 100/1,000/10,000이면 raw vector는 각각 `409,600 / 4,096,000 / 40,960,000 B`로
선형 증가한다(`PROJECTED`). DB row/index overhead는 포함하지 않는다.

PRZ-034에서 기록한 `160→387, +141.875%`는 Top5 고유 Child 227개만 대상으로 한 selector 평가
비용이다. PRZ-035 전략 A의 전체 색인 저장량과 의미가 다르며, 과거 수치를 바꾸지 않고 이 차이를
`DOCUMENTATION_CLARIFICATION`으로 남긴다.

## 반복 검색 projection

관측 평균을 같은 corpus에 적용한 `PROJECTED` 값이다.

| query 수 | A Child 계산 | B Child 계산 | A 총 vector 계산 | B 총 vector 계산 | A Child 시간 | B Child 시간 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 241.0000 | 6.8718 | 401.0000 | 166.8718 | 2,159.0225ms | 71.0020ms |
| 10 | 241.0000 | 68.7179 | 401.0000 | 228.7179 | 2,159.0225ms | 710.0199ms |
| 50 | 241.0000 | 343.5897 | 401.0000 | 503.5897 | 2,159.0225ms | 3,550.0994ms |
| 100 | 241.0000 | 687.1795 | 401.0000 | 847.1795 | 2,159.0225ms | 7,100.1989ms |

Child embedding 횟수 손익분기점은 약 `35.0709 query`다. 문서 업로드보다 검색이 많을 가능성은
운영 가정일 뿐 실제 사용량으로 검증되지 않았다.

## 새 문서 버전 비용

100개 Child 중 20개 `sourceText`가 바뀐 예시에서 A는 재사용 구현이 없으면 100개를 다시 계산한다.
exact `sourceText` SHA-256, model digest, dimension과 입력 정책이 모두 같다는 조건으로 vector bytes를
재사용할 수 있다면 20개를 계산하고 80개를 재사용할 수 있다(`PROJECTED_DESIGN_ONLY`). 새 버전의
Child ID와 provenance 연결은 다시 만들어야 한다. B는 저장 Child vector가 없지만 이후 검색마다 필요한
Child를 다시 계산한다.

문서 버전과 embedding generation을 분리하고 configured/resolved model ID, digest, dimension,
content hash와 입력 정책 버전을 저장하는 방향을 후속 Production 설계 후보로 남긴다. 구현은
`NOT_RUN`이다.

## self-hosted 경계

- Production `EmbeddingService`: single-text `embed(String)`
- PRZ-035: evaluation-only `embedAll`, batch size 32
- Production batch inference, DB row/index overhead, CPU-only 성능, content-hash 재사용:
  `NOT_VERIFIED / NOT_IMPLEMENTED`
- 전략 A의 query 시간은 in-memory vector map 조회이며 영구 저장소 read·DB index overhead는
  `NOT_MEASURED`다.
- 전략 A의 전체 색인 수치는 B3 구간과 warmed Child embedding 구간을 더한
  `SUMMED_OBSERVATION_NOT_CONTIGUOUS_WALL_TIME`으로만 기록한다.
- 공식 raw output/report: ignored `local/search-v3-evaluation/prz035/`

공식 local 실행은 NVIDIA GeForce RTX 5080에서 Ollama가 `bge-m3:latest`를 `100% GPU`로 올린
상태였다(`ollama ps` loaded size 664MB; 감사 시 `nvidia-smi` 사용량 1,797MiB). peak VRAM 측정은
아니다. CPU-only는 `NOT_RUN`이므로 절대 지연을 일반화하지 않는다. 다만 A는 query 경로에서 모델
호출을 제거하므로 CPU-only 기본 profile의 응답시간 변동을 줄이는 방향에 더 가깝다. 이 판단은 실제
CPU-only 검증이 필요한 설계 근거다.

실행 순서는 fresh B3 replay → A → B였다. B가 더 warmed 된 순서이므로 local 시간은 오히려 B에
유리할 수 있지만, 단일 장비 결과를 다른 환경의 배율로 일반화하지 않는다.

## 검증

- 공식 A/B comparison: `1 test / 0 failures / 0 errors / 0 skipped`
- focused integrity suite: `24 tests / 0 failures / 0 errors / 2 opt-in skipped`
- `git diff --check`: `PASS`
- `node scripts/verify-oss-readiness.mjs`: `PASS`; Markdown 227개·local link 802개, Node verifier
  `16/16`, SBOM·external link 검사 포함
- full backend unit/integration, frontend test/build: `NOT_RUN` (Production 변경 없음)
- Production source/migration/dependency/frontend/MCP/Docker diff: `0`
- SEALED FINAL: combined
  `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, manifest
  `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`, tree
  `a129080861d7dafd32a9b3b3357b61aebb237e59`, `opened=false`, `searchExecuted=false`,
  `CURRENT_FRESH_BASELINE=NOT_RUN`

## 다음 단계

다음 Phase는 선택된 A를 바로 Production에 적용하는 작업이 아니다. Search V3 shadow 저장 구조에서
Document version과 embedding generation, 활성화 원자성, owner scope, model digest, exact content-hash
재사용과 재색인 정책을 먼저 설계·검증해야 한다. 실제 migration과 cutover는 별도 Gate 뒤에 진행한다.
