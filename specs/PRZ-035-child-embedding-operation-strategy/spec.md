# PRZ-035 Child embedding 운영 전략

- 상태: `IN_PROGRESS`
- 유형: Search V3 운영 전략 비교
- branch: `PRZ-035-child-embedding-operation-strategy`
- 기준: `refactor/search-v3@a6fb5ee5240b0b1fcc59f78b329b55563512df1d`
- 선행 작업: PRZ-034 `CHILD_DENSE_V1` (`PROMISING`)
- Production 적용: `NO_CHANGE`

## 목적

PRZ-034의 검색 품질과 `Top5 RetrievalPassage → EvidenceChild` 선택 정책을 그대로 둔 채,
Child vector를 색인 시 미리 계산할지 검색 시 필요한 만큼 계산할지 비교한다. 결과가 같은지 먼저
검증하고, 응답시간·색인시간·저장공간·반복 검색 계산량을 함께 보고 한 전략을 선택한다.

## 고정된 검색 계약

- 구조 분석, `EvidenceChild`, B3 `RetrievalPassage`, BGE-M3, 1024 dimensions, cosine
- 같은 query vector와 Passage 순위
- `CHILD_DENSE_V1`: Top5 Passage 내부 `EvidenceChild.sourceText`만 비교
- PRZ-028/029 Typed Validation과 최종 근거 의미
- DEV/CAL 117 query, 새 데이터와 새 query 없음

두 전략의 Passage 후보·순서, Child 후보, 최종 EvidenceChild ID, Top1, MRR, Recall@5와 provenance가
모두 같아야 한다. 하나라도 다르면 `RESULT_PARITY_FAIL`이며 비용 비교를 채택 근거로 쓰지 않는다.

## 비교 전략

### A. `PRECOMPUTE_CHILD_EMBEDDINGS`

색인할 때 활성 문서의 모든 EvidenceChild를 embedding하고 저장한다고 모의한다. 검색할 때는 저장된
vector만 읽어 같은 Passage 안에서 cosine으로 Child를 정렬한다. 이 평가 corpus에서는 전체 Child
241개를 계산해야 한다. PRZ-034의 227개는 Top5에 한 번이라도 등장한 Child 수이므로 A의 전체 색인
비용으로 사용하지 않는다.

### B. `ON_DEMAND_CHILD_EMBEDDINGS`

색인에는 Passage vector만 둔다. 매 query마다 Top5 Passage의 Child를 묶어 embedding하고 query 종료
후 버린다. 애플리케이션 수준 cache는 사용하지 않는다. Ollama 내부 동작까지 cache가 없다고
주장하지 않는다.

평가 코드는 `embedAll`로 묶음 호출하지만 현재 Production `EmbeddingService`는 단일 text 인터페이스다.
따라서 이번 결과는 평가 전용 운영 모의이며 Production batch 구현 근거가 아니다.

## 비용과 판정 계약

Raw vector 저장량은 `vector 수 × 1024 × 4 bytes`로 계산한다. DB row/index overhead는
`NOT_MEASURED`다. 1/10/50/100회 검색 projection은 관측 평균을 사용하고 `PROJECTED`로 표시한다.

공식 판정은 다음 복합 조건으로 고정한다.

- `PRECOMPUTE_CHILD_EMBEDDINGS`: A/B 결과 parity, Child raw 저장량 1 MiB 이하, B의 p95가 A보다
  10ms 이상이면서 1.25배 이상, embedding 횟수 손익분기점 50 query 이하
- `ON_DEMAND_CHILD_EMBEDDINGS`: B의 p95가 A의 1.10배 이하이고 손익분기점이 100 query 초과
- 그 밖의 경우 `NEEDS_HYBRID_LATER`

1 MiB는 241개 Child인 현재 DEV/CAL corpus 전체에만 적용하는 비교 Gate이며 일반적인 저장 용량
상한이 아니다. 100/1,000/10,000 Child의 선형 raw vector projection도 함께 기록한다. 모든 저장 추정은
DB row/index overhead와 읽기 비용을 제외한다. 이 Gate는 이 corpus와 local in-memory 실행에서 전략을
고르기 위한 기준이며 Production-scale 또는 모든 장비의 성능 기준이 아니다.

## 문서 버전과 저장 metadata

A를 선택해도 이번 PRZ에서 DB schema를 확정하지 않는다. 후속 설계에서 최소한 Child ID,
`sourceText` SHA-256, configured/resolved model ID, model digest, dimension, 입력 정책 버전,
embedding generation과 vector가 필요하다. 새 문서 버전의 provenance는 새로 만들되, exact source hash와
model·dimension·입력 정책이 모두 같을 때 vector bytes를 재사용할 가능성만 검토한다. 문서 버전과
embedding generation은 분리하는 방향을 권고하며, 구현은 `NOT_RUN`이다.

## 비범위

Migration/Production entity·repository·검색·dependency·frontend·MCP·Docker, cache, Sparse, Parent Dense,
reranker, Qwen, QueryPlanner, rewrite, FTS/BM25, RRF, MMR, Grounded Answer와 selector tuning은 하지 않는다.

SEALED FINAL은 combined
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
`opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`을 유지한다.
