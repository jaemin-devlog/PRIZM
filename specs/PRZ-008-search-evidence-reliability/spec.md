# PRZ-008 — 검색 근거 신뢰성

## 상태

`PLANNED` — 0단계 Spec 검토 대기. 제품 구현은 시작하지 않았다.

이 문서에서 `CONFIRMED`는 source·test·migration으로 확인한 사실,
`OPEN_DECISION`은 후속 단계에서 측정 후 확정할 사항을 뜻한다.

## 목적

Career Vault 검색이 단순히 가까운 청크를 반환하는 데서 그치지 않고, 사용자의
`ACTIVE` 문서에 질문을 뒷받침할 원문 근거가 있는지 신뢰할 수 있게 판정하도록
공통 계약을 정한다.

기존 [PRZ-001](../PRZ-001-search-evaluation-integrity/spec.md)은 TUNING·TEST 분할과
Direct MRR 계산을 교정한 완료 Spec이다. PRZ-008은 그 평가 기반을 재사용해
제품 검색의 근거 판정과 후속 실험을 다룬다.

## 범위

### 현재 구현 기준선

`CONFIRMED` 근거 위치:

- 검색 흐름과 응답: [`SearchService`](../../src/main/java/com/prizm/search/service/SearchService.java),
  [`VectorSearchRepository`](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java),
  [`search/controller`](../../src/main/java/com/prizm/search/controller),
  [`search/dto`](../../src/main/java/com/prizm/search/dto)
- 프론트엔드: [`searchApi.ts`](../../frontend/src/api/searchApi.ts),
  [`App.tsx`](../../frontend/src/App.tsx)
- 청킹과 색인: [`TextChunker`](../../src/main/java/com/prizm/ingestion/service/TextChunker.java),
  [`IngestionProperties`](../../src/main/java/com/prizm/ingestion/config/IngestionProperties.java),
  [`DocumentIndexingProcessor`](../../src/main/java/com/prizm/ingestion/service/DocumentIndexingProcessor.java)
- source 위치와 ownership: [`V8`](../../src/main/resources/db/migration/V8__add_document_ownership.sql),
  [`V10`](../../src/main/resources/db/migration/V10__add_chunk_source.sql),
  [`V11`](../../src/main/resources/db/migration/V11__support_pdf_page_sources.sql)
- 평가: [검색 품질 평가](../../docs/evaluation/search-evaluation.md),
  [`SearchEvaluationMetrics`](../../src/searchEvaluation/java/com/prizm/search/evaluation/SearchEvaluationMetrics.java),
  [`questions.jsonl`](../../src/test/resources/search-evaluation/sample/questions.jsonl)

| 항목 | 현재 동작 |
|---|---|
| 요청 | 질의를 검증하고 Ollama로 1024차원 임베딩한 뒤 pgvector 검색을 호출한다. |
| 검색 경계 | 로그인 사용자가 소유한 문서 중 `active_version_id`가 가리키는 `ACTIVE` 버전의 청크만 검색한다. |
| 순위·점수 | exact cosine distance 오름차순이며 `score = 1 - distance`다. score는 확률이나 검증된 신뢰도가 아니다. |
| 결과 수 | `/api/search`는 1건, `/api/career-evidence/search`는 최대 5건이다. 제품 검색에 threshold는 없다. |
| 빈 후보 | 검색 가능한 청크가 없으면 단일 검색은 `404 SEARCH_NO_RESULT`, Career Evidence는 `200`과 빈 배열을 반환한다. |
| 무관한 질문 | 청크가 하나라도 있으면 의미상 근거가 없어도 가장 가까운 결과를 반환한다. |
| UI | 빈 배열은 근거 없음으로, 결과 score는 “관련도”로 표시한다. 인증 오류 외 오류는 일반 오류로 표시한다. |
| 청킹·색인 | TXT와 PDF 각 페이지를 최대 800자·overlap 120자로 나누고 청크마다 Ollama를 순차 호출한다. PDF는 `PAGE`와 page 번호를 저장한다. |
| 평가 | 합성 문서 11개·질문 30개를 TUNING 20개와 TEST 10개로 나눠 top-20 순위 지표와 전체 지연을 측정한다. |

현재 상태 문서의 “근거가 없을 때 안내”는 빈 배열 처리만 뜻했다. 검색 가능한
청크가 있지만 질문과 무관한 경우를 판정하는 기능은 아직 없다.

### 검색 실패 사례

| 질문 유형 | 목표 결과 | 현재 예상 동작 | 단계 | 평가 |
|---|---|---|---|---|
| 문서에 답이 없는 일반 질문 | `NO_EVIDENCE` | 가까운 결과 반환 | 1·2 | 포함 |
| 주제는 비슷하지만 근거가 없는 질문 | `NO_EVIDENCE` | 용어가 가까운 결과 반환 가능 | 1·2 | 포함 |
| 없는 회사·자격증·기술 질문 | `NO_EVIDENCE` | 가까운 결과 반환 | 1·2 | 포함 |
| 실제 역할·수치를 바꾼 질문 | `NO_EVIDENCE` | 원래 프로젝트를 반환할 수 있음 | 1·2 | 포함 |
| 다른 사용자의 문서에만 있는 근거 | 현재 사용자에게 청크가 있으면 `NO_EVIDENCE`, 없으면 `NO_SEARCHABLE_DOCUMENTS` | owner 경계 안의 결과만 반환 | 1·2 | 포함 |
| 과거 버전에만 있는 근거 | active 청크가 있으면 `NO_EVIDENCE`, 없으면 `NO_SEARCHABLE_DOCUMENTS` | 과거 버전 제외 | 1·2 | 포함 |
| 검색 가능한 문서가 없는 사용자 | `NO_SEARCHABLE_DOCUMENTS` | 단일 404, Career Evidence 빈 배열 | 1·2 | 포함 |
| overlap 구간 반복 | 중복 없는 `EVIDENCE_FOUND` | 같은 사실이 반복될 수 있음 | 1·4A·4B | 포함 |
| 직접 근거 | `EVIDENCE_FOUND` | 가까운 결과 반환 | 1·2 | 포함 |
| 의미가 같은 다른 표현 | `EVIDENCE_FOUND` | dense 순위에 따라 반환 | 1·2·4A | 포함 |
| 날짜·숫자·고유명사 | 정확한 값이 있을 때만 `EVIDENCE_FOUND` | 비슷한 다른 값도 반환 가능 | 1·2·4A | 포함 |

현재 TEST의 `exact-fifty-percent`는 실제 30% 근거를 relevance 1로 두고
`noEvidence=false`로 분류한다. 새 계약과 맞지 않으므로 1단계에서 dataset
version과 TEST 정책을 다시 고정한다. 조용히 재라벨링한 뒤 같은 TEST를 최종
검증에 재사용하지 않는다.

### 검색 상태 계약

| 상태 | 의미 | 결과 배열 |
|---|---|---|
| `EVIDENCE_FOUND` | owner 범위의 검색 가능한 `ACTIVE` 청크가 있고 판정 기준을 통과한 근거가 있다. | 1개 이상 |
| `NO_EVIDENCE` | 검색 가능한 청크는 있지만 판정 기준을 통과한 근거가 없다. | 비어 있음 |
| `NO_SEARCHABLE_DOCUMENTS` | 현재 사용자에게 검색 가능한 `ACTIVE` 청크가 없다. | 비어 있음 |

세 상태는 정상적인 검색 결과이므로 `200`이 적합하다. 잘못된 질의는 `400`,
인증·권한 문제는 `401`·`403`, 임베딩·DB 장애는 기존 `5xx` 계약을 유지한다.

후속 API는 상태와 결과 배열을 함께 반환하는 방향으로 설계한다. 구체 필드명,
기존 단일 검색 404와 Career Evidence 배열 응답의 이행 방식, `distance`·`score`
공개 여부와 검색 profile version 노출 여부는 2단계 `OPEN_DECISION`이다.

### 평가 정책과 지표

- TUNING은 threshold, 후보 수, 오거부율, 중복 기준과 청킹 매개변수 선택에만 쓴다.
- TEST는 모든 설정과 라벨을 고정한 뒤 최종 비교에만 쓴다.
- 같은 문서·사실·`evidenceGroup`과 그 paraphrase를 두 split에 나누지 않는다.
- 관련 청크는 relevance `2`(직접)·`1`(부분)·`0`(무관)으로 표시하고, 같은 사실의
  반복 청크는 같은 `evidenceGroup`으로 묶는다.

| 지표 | 계산 기준 |
|---|---|
| Precision@5 | top-5의 relevance 1 이상 청크 수를 5로 나눈 질문별 평균. 정답 청크가 하나뿐이면 질문별 최대값은 0.2다. |
| Direct MRR@5·@20 | relevance 2가 있는 질문만 대상으로 cutoff 안 첫 직접 근거의 역순위를 평균한다. 거부되면 0이다. |
| nDCG@5 | gain은 `2^relevance - 1`이며 같은 `evidenceGroup`의 두 번째 결과부터 gain 0으로 계산한다. |
| 무관 질문 거부율 | 검색 가능한 문서가 있는 no-evidence 질문 중 `NO_EVIDENCE` 비율이다. |
| 근거 질문 오거부율 | relevance 1 이상 근거가 있는데 `NO_EVIDENCE`로 판정된 비율이다. |
| top-1 직접 정확도 | 직접 근거 질문 중 첫 결과의 group relevance가 2인 비율이다. |
| page 인용 정확도 | PDF 직접 근거 질문 중 반환 `PAGE` index가 gold page와 일치한 비율이다. |
| 중복 결과 비율 | top-5에서 앞선 `evidenceGroup`을 반복한 결과 수 ÷ 실제 반환 수다. |
| 결과 개수 | 질문별 사용자 반환 수를 상태·split·category별로 기록한다. |
| 전체 지연 | embedding 시작부터 DB 결과 mapping 종료까지의 p50·p95다. |
| embedding 지연 | Ollama 요청 시작부터 벡터 검증 종료까지다. |
| DB 지연 | JDBC 호출 직전부터 row mapping 종료까지다. |

기존 하네스에는 Direct MRR@5, 거부·오거부, top-1 직접 정확도, page 정확도,
결과 수와 분리 지연이 없다. 1단계에서 순위를 바꾸기 전에 이 측정을 교정한다.

## 요구사항

| ID | 요구사항 |
|---|---|
| `PRZ-008-R1` | 현재 dense 검색을 재현 가능하게 측정하고 TUNING·TEST 누출을 차단한다. |
| `PRZ-008-R2` | 세 상태를 배타적으로 판정하고 근거 없는 후보를 사용자 근거로 반환하지 않는다. |
| `PRZ-008-R3` | owner와 `ACTIVE` version 경계를 유지한다. 다른 사용자·과거 version은 현재 근거가 아니다. |
| `PRZ-008-R4` | UI는 세 상태와 server·인증 오류를 구분하고 score를 확률처럼 표시하지 않는다. |
| `PRZ-008-R5` | 의미 단위 청킹은 실험에서 Gate를 통과한 경우에만 제품에 적용한다. |
| `PRZ-008-R6` | 청킹·batch embedding·PDF 최적화는 source 위치, embedding 검증과 atomic activation을 보존한다. |
| `PRZ-008-R7` | PostgreSQL과 OpenSQL 결과를 별도로 실행·기록한다. |
| `PRZ-008-R8` | 각 단계는 별도 branch·PR로 수행하고 이전 Gate 통과 전 후속 구현을 섞지 않는다. |

## 보존 계약

- 문서·버전·청크·질의·결과의 사용자 ownership을 유지한다.
- 완성된 `ACTIVE` version만 검색하고 새 version 실패 시 기존 active를 유지한다.
- immutable version, 원본·hash, TXT `TEXT_CHUNK`와 PDF `PAGE` source를 유지한다.
- embedding의 1024차원·finite·0이 아닌 norm 검증을 유지한다.
- Worker lease·recovery·fencing, atomic activation과 파일 안전 계약을 약화하지 않는다.
- 적용된 Flyway migration을 수정하지 않고 PostgreSQL·OpenSQL 결과를 구분한다.

## 제외 범위

HNSW, IVFFlat, FTS, RRF, hybrid search, OCR, 다단 PDF layout 복원, page 경계를
넘는 청크, `document_chunk_spans`, reranker, MMR, Worker 부분 저장·checkpoint·
병렬화, 별도 vector DB와 LLM 답변 생성은 필수 계획에 포함하지 않는다.

0단계에서는 제품·test source, API DTO, UI, threshold, 청킹, Ollama·PDF 처리,
migration, dependency, 기존 문서 재색인을 변경하지 않는다.

## 완료 조건

### 0단계

- 현재 동작, 실패 유형, 세 상태와 평가 정책이 source 근거와 일치한다.
- [Plan](plan.md)에 각 단계의 입력·산출물·변경 범위·Gate·중단 조건이 있다.
- 비범위가 후속 필수 구현으로 표현되지 않는다.
- 제품·test source, migration과 dependency 변경이 0건이다.
- Markdown 링크·상태 일관성과 `git diff --check`가 통과한다.

### 후속 단계 잠정 목표

실측 전 수치를 성능 사실로 확정하지 않는다.

| 항목 | 초기 방향 | TUNING 후 | TEST |
|---|---|---|---|
| 무관 질문 거부율 | 현재 semantic rejection 부재보다 개선 | 수치 Gate 확정 | 고정 Gate 검증 |
| 근거 질문 오거부율 | 낮을수록 좋음 | 직접·부분 근거별 허용치 확정 | 설정 변경 없이 검증 |
| Direct MRR·nDCG | 의미 있는 회귀 금지 | 허용 하락 폭 확정 | 고정 폭 검증 |
| 중복률 | 현재 기준선보다 감소 | 4A 적용 Gate 확정 | 4B 결과 검증 |
| 색인 시간 | 안전·품질을 유지하며 감소 | 동일 corpus budget 확정 | 환경별 검증 |
| 검색 p50·p95 | 유의한 회귀 금지 | latency budget 확정 | 고정 profile 검증 |
