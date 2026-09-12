# PRZ-037 Search V3 Shadow Storage

- 상태: `VERIFIED`
- 유형: Search V3 PostgreSQL shadow 저장 구조
- branch: `PRZ-037-search-v3-shadow-storage`
- 기준: `PRZ-036-search-v3-index-lifecycle@7accea2b28d3cfb1a3d09dd50cf0237c72b627b9`
- Production 검색 적용: `NO_CHANGE`

## 목적과 범위

PRZ-036에서 검증한 `SearchIndexGeneration` 생명주기를 실제 PostgreSQL·Flyway 저장 구조로 옮긴다.
Search V3는 기존 `document_chunks`와 Search V2 옆에 shadow로만 저장하며 검색 API, Worker, frontend,
MCP에는 연결하지 않는다.

```text
Document
└─ DocumentVersion
   └─ SearchIndexGeneration
      ├─ Search V3 indexing job
      ├─ RetrievalPassage ─ Passage embedding
      └─ EvidenceChild ──── Child embedding
```

## 기존 Production DB 감사

- 현재 Flyway 최신 버전은 V17이며 새 migration 번호는 V18이다.
- `documents.active_version_id`는 nullable이고 owner가 같은 `document_versions`만 가리킨다.
- `document_versions`는 문서별 불변 version이며 기존 Search V2 결과는 `document_chunks vector(1024)`에 있다.
- `processing_jobs`는 `(document_version_id, job_type)`이 unique라 같은 version의 여러 generation을 표현할
  수 없다. 따라서 Search V3는 별도 job table을 사용한다.
- owner 계보는 V8의 composite FK를 따른다. 기존 FK만 조합하면 같은 owner의 다른 문서 version을 연결할
  수 있으므로 `(document_version_id, document_id, owner_user_id)` unique/FK를 추가한다.
- 기존 완료 잠금 순서는 `job -> version -> document`다. 후속 Search V3 활성화 service도 이 순서를
  유지하고 generation row는 V3 job과 함께 잠가야 한다.
- 기존 claim은 DB 시간 기반 lease, `claim_version`, `FOR UPDATE SKIP LOCKED`로 stale Worker를 차단한다.
  V3 전용 job도 generation identity, lease, claim version, attempt와 recovery token을 별도로 저장한다.

## 선택한 저장 책임

| 저장 대상 | 책임 |
| --- | --- |
| `search_v3_index_generations` | owner·문서·version과 독립된 generation, 정책·모델 계약, frozen manifest, 상태와 실패 단계 |
| `search_v3_indexing_jobs` | generation별 단일 작업, claim·lease·attempt·recovery fencing metadata |
| `search_v3_retrieval_passages` | generation 안의 결정적 Passage 순서, 검색 원문·입력 hash와 provenance |
| `search_v3_evidence_children` | 같은 generation Passage에 속한 원문 근거, 순서·hash·source span·Parent provenance |
| `search_v3_passage_embeddings` | Passage와 최대 1:1인 BGE-M3 vector 및 입력·모델 metadata |
| `search_v3_child_embeddings` | Child와 최대 1:1인 미리 계산 vector 및 재사용 key metadata |
| `documents.active_search_v3_generation_id` | 문서별 검색 가능한 Search V3 generation pointer. nullable |

이번 Phase는 schema와 제약을 먼저 검증한다. JPA entity/repository와 Worker·활성화 service는 추가하지
않는다. 사용되지 않는 mapping API를 미리 확정하지 않고 후속 transaction 구현에서 실제 접근 패턴과 함께
추가한다.

## DB 제약과 service 책임

DB가 강제하는 항목:

- owner-document-version-generation composite lineage
- generation 안의 Passage/Child key·순서 uniqueness
- Child가 같은 generation의 Passage만 참조
- vector의 artifact 1:1 상한, orphan·cross-owner 연결 차단
- vector dimension metadata `1024`, payload dimension, non-zero norm
- generation별 job 하나와 generation/job lineage
- active pointer가 같은 owner·document·active version의 generation만 참조
- 문서별 `ACTIVE` generation 최대 하나
- first upload의 두 pointer null 상태와 실패 generation 비노출 상태

후속 service transaction이 강제할 항목:

- frozen manifest와 실제 artifact/vector inventory의 exact equality
- vector finite value와 모든 embedding 계약 검증의 최종 확인
- pointer 대상이 `ACTIVE`이고 V3 job이 `COMPLETED`인지 확인
- `job -> version -> document` 잠금, claim fencing과 원자 활성화
- 이전 ACTIVE를 `SUPERSEDED`로 바꾸고 새 version·두 pointer·generation·job을 한 transaction에서 전환

## 활성화와 재색인

새 version 활성화는 후속 service가 version, `active_version_id`, Search V3 pointer, 새/이전 generation과
V3 job을 한 transaction에서 바꾼다. 같은 version 재색인은 `active_version_id`를 유지하고 Search V3
generation만 교체할 수 있다.

최초 업로드는 `active_version_id = null`, `active_search_v3_generation_id = null`을 정상 상태로 허용한다.
BUILDING generation이 실패해도 두 pointer는 null로 남는다. Search V3 검색 가능 조건은 향후
`pointer 대상 + ACTIVE generation + COMPLETED V3 job`으로 제한한다.

## Manifest와 embedding 재사용 경계

generation 생성 시 예상 Passage 수, Child 수와 deterministic manifest SHA-256을 저장한다. Worker가
완료 시 보고한 count만으로 READY가 될 수 없다. Passage와 Child는 같은 configured model ID, resolved
digest와 dimension을 사용하되 입력 정책 버전은 분리한다.

PRZ-035의 `PRECOMPUTE_CHILD_EMBEDDINGS`를 보존한다. Child vector 재사용 key는 owner,
`sourceText` SHA-256, configured model ID, resolved digest, dimension과 Child 입력 정책이다. 재사용은
후속 Worker 범위이며 이전 generation의 Child row를 직접 참조하지 않는다.

## 정리 경계

active pointer가 가리키는 generation 삭제는 FK로 막는다. generation 삭제가 허용되면 job, Passage,
Child와 vector는 같은 generation 경계에서 cascade할 수 있다. `SUPERSEDED`와 `FAILED` 보존 기간은
`OPEN_DECISION`이다. `BUILDING`·`READY`는 generic cleanup이 아니라 recovery 대상이다.

## 호환성과 비범위

- V18은 additive이며 기존 `document_chunks`, Search V2 schema와 기존 migration을 수정하지 않는다.
- Production 검색, ingestion Worker, API, frontend, MCP, Docker와 dependency는 변경하지 않는다.
- Search V3 활성화 service, owner-scoped search query, cleanup Worker와 cutover는 구현하지 않는다.
- DEV/CAL benchmark와 SEALED FINAL 검색은 실행하지 않는다.

## 수용 기준

- 실제 PostgreSQL에서 V1~V18 migration이 적용된다.
- 기존 Search V2 테이블과 `document_chunks vector(1024)`가 유지된다.
- 복합 FK·unique·check constraint가 owner 및 generation 혼입과 orphan을 거부한다.
- 같은 `DocumentVersion`에 여러 generation, same-version reindex와 최초 업로드 상태를 표현한다.
- active pointer가 다른 owner·문서·version generation을 가리키지 못한다.
- 관련 기존 owner·lease·색인 핵심 테스트가 회귀하지 않는다.
- Production 검색 경로와 SEALED FINAL 상태가 변하지 않는다.
