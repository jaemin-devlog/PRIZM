# Search V3 개발 기록

Search V3는 고정 길이 문서 조각의 근거 경계를 개선하고, 같은 질문에서 기존 검색보다 나은
결과를 내는 구성만 남기려고 진행한 리팩터링이다. 구현·실험·평가 기록은 성격에 따라 세
디렉터리로 나눴다.

| 디렉터리 | 범위 |
| --- | --- |
| [`research/`](research/) | PRZ-025~035: 평가 계약, 구조 검색, 채택·비채택 실험, Child 선택 |
| [`runtime/`](runtime/) | PRZ-036~041: 색인 생명주기, 저장 구조, Worker, shadow query runtime |
| [`evaluation/`](evaluation/) | PRZ-042~045: 최종·release-grade 평가와 Top2 문서 순위 집계 |

PRZ-031·032·034·035·042·044의 실행 가능한 동결 JSON은 기존 코드가 경로와 SHA를 함께
검증한다. 이 파일만 `specs/PRZ-.../` 경로에 남기고, 사람이 읽는 네 문서는 위 디렉터리로
옮겼다. 동결 계약과 receipt의 내용은 바꾸지 않았다.

## 현재 상태

| 항목 | 상태 |
| --- | --- |
| 보관 브랜치 | `refactor/search-v3` |
| 통합 HEAD | `45bfb683a03effd976661f218a4659cce9be77f8` |
| 최신 작업 | PRZ-045 `TOP2_AGGREGATION_PRODUCTION_PASS` |
| 기본 검색 API | 기존 Search V2 유지 |
| Search V3 | shadow runtime과 Top2 문서 순위 집계 구현 |
| PRZ-044 공식 실행 | V2/V3 각 `600/600` prediction 동결 |
| PRZ-044 Gold·정량 판정 | Gold 미제공으로 `NOT_RUN`, `EVALUATION_INTEGRITY_BLOCKED` |

현재 Search V3 구성은 구조 기반 `EvidenceChild`, B3 `RetrievalPassage`, BGE-M3 Dense 검색,
Typed Validation과 Evidence Selection, 같은 Passage 안의 `CHILD_DENSE_V1`, 문서별 비중복
Passage 최대 2개 평균 집계다. `main`과 기본 Search V2는 이 리팩터링 통합 작업에서 바꾸지
않았다.

## 연구와 선택

| PRZ | 검증 내용 | 결론 |
| --- | --- | --- |
| [PRZ-025](research/PRZ-025-search-v3-foundation/evidence.md) | 평가·근거 계약과 Fresh benchmark seed 봉인 | `FRESH_BENCHMARK_SEED_FROZEN`, Registry `IN_PROGRESS` |
| [PRZ-026](research/PRZ-026-structural-parsing-parent-child/evidence.md) | 구조 분할, B3 RetrievalPassage, C1 Parent Context | B3 `PROMISING`, C1 `NEEDS_ADJUSTMENT` |
| [PRZ-027](research/PRZ-027-cross-encoder-reranking/evidence.md) | GTE Cross Encoder 재정렬 | `NO_GO` |
| [PRZ-028](research/PRZ-028-typed-exact-constraints/evidence.md) | 숫자·날짜·식별자 조건 처리 | `EVIDENCE_VALIDATION_ONLY` |
| [PRZ-029](research/PRZ-029-evidence-validation-selection/evidence.md) | 조건 검증 뒤 근거 선택 | `PROMISING` |
| [PRZ-030](research/PRZ-030-semantic-evidence-validation-ceiling/evidence.md) | 의미 검색의 이론적 상한과 병목 위치 | `BUILD_SEMANTIC_VALIDATOR` |
| [PRZ-031](research/PRZ-031-semantic-evidence-directness/evidence.md) | Qwen 직접성 판별 | D1 `PROTOCOL_NO_GO`, D2 `NO_GO` |
| [PRZ-032](research/PRZ-032-minimal-v3-shadow-comparison/evidence.md) | 기존 Search V2와 최소 V3 비교 | `MIXED_NEEDS_NEXT_CAPABILITY` |
| [PRZ-033](research/PRZ-033-atomic-evidence-child-selection-ceiling/evidence.md) | Passage 내부 Child 선택 상한 | `BUILD_CHILD_SELECTOR` |
| [PRZ-034](research/PRZ-034-atomic-evidence-child-selector/evidence.md) | 동일 BGE-M3로 Passage 내부 Child 선택 | `PROMISING` |
| [PRZ-035](research/PRZ-035-child-embedding-operation-strategy/evidence.md) | Child embedding 계산·보관 시점 비교 | `PRECOMPUTE_CHILD_EMBEDDINGS` |

성능이 개선되지 않은 Parent Context, GTE Cross Encoder, Qwen 직접성 판별은 채택 구성에서
제외했다. 실패 기록은 삭제하지 않고 해당 evidence에 보존했다.

## Shadow runtime

| PRZ | 검증 내용 | 결론 |
| --- | --- | --- |
| [PRZ-036](runtime/PRZ-036-search-v3-index-lifecycle/evidence.md) | generation, manifest, activation·복구 생명주기 | `SHADOW_INDEX_LIFECYCLE_READY` |
| [PRZ-037](runtime/PRZ-037-search-v3-shadow-storage/evidence.md) | PostgreSQL shadow 저장 구조와 제약 | `SHADOW_STORAGE_READY` |
| [PRZ-038](runtime/PRZ-038-search-v3-job-fencing-runtime/evidence.md) | claim·lease·recovery token·stale Worker 차단 | `JOB_FENCING_READY` |
| [PRZ-039](runtime/PRZ-039-search-v3-inventory-activation-runtime/evidence.md) | exact inventory, READY와 같은-version 원자 활성화 | `INVENTORY_ACTIVATION_READY` |
| [PRZ-040](runtime/PRZ-040-search-v3-shadow-indexing-worker/evidence.md) | 실제 원문 구조·embedding 저장과 shadow activation | `SHADOW_INDEXING_WORKER_READY` |
| [PRZ-041](runtime/PRZ-041-search-v3-runtime-completion/evidence.md) | 자동 dispatch·recovery와 ACTIVE-only shadow query | `SEARCH_V3_RUNTIME_READY` |

## 평가와 마무리

| PRZ | 검증 내용 | 결론 |
| --- | --- | --- |
| [PRZ-042](evaluation/PRZ-042-search-v3-final-evaluation/evidence.md) | Search V3 최종 평가 | evidence의 역사적 판정 유지 |
| [PRZ-043](evaluation/PRZ-043-search-v3-release-grade-evaluation/evidence.md) | 독립 release-grade 평가 | evidence의 역사적 판정 유지 |
| [PRZ-044](evaluation/PRZ-044-search-v3-release-grade-evaluation/evidence.md) | Passage 상한 수정과 prediction 동결 | `EVALUATION_INTEGRITY_BLOCKED`, practical validation `PASS` |
| [PRZ-045](evaluation/PRZ-045-search-v3-top2-document-aggregation/evidence.md) | 문서별 Top2 Passage 평균 순위 | `TOP2_AGGREGATION_PRODUCTION_PASS` |

PRZ-044는 실제 PostgreSQL·pgvector·BGE-M3 환경에서 V2/V3 문서 `90/90`, prediction
`600/600`을 완료했다. 정식 Gold가 없어 release-grade 정량 품질 판정은 실행하지 않았다.
PRZ-045는 별도 진단에서 확인한 문서 순위 병목을 Top2 평균 집계로 구현하고 기존 Search V2와
후보 검색 계약을 그대로 유지했다.

## Git 계보와 범위

PRZ-027은 PRZ-026에서 갈라진 `NO_GO` 실험이었으며, 독립 커밋은
`archive/prz-027-cross-encoder-no-go` 태그로 보존했다. 나머지 Search V3 작업은
`refactor/search-v3`에 통합했다.

`refactor/search-v3`는 Search V3 완성본을 보관하는 브랜치다. `main` 병합이나 기본 검색
전환을 뜻하지 않는다. 현재 제품 상태는 [현재 구현 현황](../../docs/project-status.md), 전체 PRZ
상태는 [Spec Registry](../README.md)를 기준으로 확인한다.
