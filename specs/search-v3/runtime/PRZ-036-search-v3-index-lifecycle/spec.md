# PRZ-036 Search V3 색인 생명주기

- 상태: `VERIFIED`
- 유형: Search V3 shadow 저장 계약 검증
- branch: `PRZ-036-search-v3-index-lifecycle`
- 기준: `PRZ-035-child-embedding-operation-strategy@42e0e12a13ae9bc5da57f5607086cdc0b533f84a`
- 선행 결정: `PRECOMPUTE_CHILD_EMBEDDINGS`
- Production 적용: `NO_CHANGE`

## 문제와 범위

`DocumentVersion`은 업로드한 원본의 불변 버전이다. 같은 원본이어도 embedding model digest, dimension,
구조 분석 정책이나 입력 정책이 바뀌면 색인을 다시 만들 수 있어야 한다. 반대로 새 원본의 색인에 실패하면
현재 검색 중인 문서 버전과 색인을 그대로 유지해야 한다. 따라서 문서 버전과 검색 색인 세대를 분리한다.

```text
Document
└─ DocumentVersion
   └─ SearchIndexGeneration
      ├─ RetrievalPassage + embedding
      └─ EvidenceChild + embedding
```

이번 Phase는 `src/searchEvaluation`의 불변 상태 모델로 이 계약을 검증한다. Production entity, repository,
worker, 검색 코드, Flyway, `document_chunks`, dependency, frontend, MCP와 Docker는 변경하지 않는다. 검색
품질과 benchmark도 다시 조정하지 않는다.

기존 `DocumentVersion.status`는 ACTIVE 원본의 재색인을 표현할 수 없으므로 generation 상태로 재사용하지
않는다. 기존 `processing_jobs`도 generation identity 없이 그대로 쓴다고 가정하지 않는다. 평가 모델에서는
한 generation과 그 논리 작업 상태를 하나의 aggregate에 묶지만, 실제 저장 단계에서는 별도의 Search V3
job identity, lease, claim version과 완료 시각을 둬야 한다.

## 상태와 작업 계약

| generation | 논리 작업 | 검색 가능 | 허용되는 다음 상태 |
|---|---|---:|---|
| `BUILDING` | `PROCESSING` | 아니요 | `READY`, `FAILED` |
| `READY` | `PROCESSING` | 아니요 | `ACTIVE`, `FAILED` |
| `ACTIVE` | `COMPLETED` | 예, active pointer가 가리킬 때만 | `SUPERSEDED` |
| `FAILED` | `FAILED` | 아니요 | 없음 |
| `SUPERSEDED` | `COMPLETED` | 아니요 | 없음 |

각 generation은 다음 정보를 가진다.

- generation ID, ownerUserId, documentId, documentVersionId
- 구조 분석, Passage, Child 정책 버전
- Passage embedding 계약과 Child embedding 계약
- model ID, resolved model digest, dimension, 입력 정책 버전
- claim version, lease, recovery lock, 생성 시각, 실패 단계
- 독립적으로 동결한 예상 manifest와 실제 저장 inventory

Passage와 Child는 같은 model ID, digest, dimension을 사용한다. 입력 정책은 서로 독립적이다. Passage는
`RetrievalPassage.retrievalText`, Child는 `EvidenceChild.sourceText`만 사용하며 각 정책 버전을 따로 남긴다.

## READY와 inventory

Builder가 보고한 개수만으로 `READY`가 될 수 없다. build 시작 때 예상 Passage, Child, 순서, 소속,
provenance와 입력 hash를 manifest로 동결한다. 저장 완료 시 실제 inventory가 manifest와 정확히 같아야 한다.

- Passage와 Child 누락·초과·중복 0
- 각 Child는 같은 generation의 Passage 하나에만 소속
- owner·document·version·generation lineage 일치
- Passage/Child vector가 artifact와 1:1이고 입력 hash와 embedding 계약 일치
- vector dimension 일치, 유한값만 포함, non-zero norm
- 저장 완료 표시 확인

일관되게 축약된 inventory도 동결 manifest와 다르므로 거부한다.

## 활성화 계약

한 문서의 검색은 active generation pointer가 가리키는 `ACTIVE + COMPLETED` 세대 하나만 사용한다.

새 문서 버전은 다음 변경을 하나의 짧은 transaction으로 확정한다.

```text
new DocumentVersion: PROCESSING -> ACTIVE
Document.active_version_id: oldVersion -> newVersion
Search V3 active generation pointer: oldGeneration -> newGeneration
newGeneration: READY/PROCESSING -> ACTIVE/COMPLETED
oldGeneration: ACTIVE/COMPLETED -> SUPERSEDED/COMPLETED
Search V3 generation job: PROCESSING -> COMPLETED
```

이전 `DocumentVersion` row의 `ACTIVE` 상태는 과거 완료 기록으로 남을 수 있다. 실제 검색 버전은 status가
아니라 `Document.active_version_id`로 결정한다. `SUPERSEDED`는 Search generation에만 적용한다.

같은 문서 버전 재색인은 `DocumentVersion` 상태와 `active_version_id`를 바꾸지 않는다. 새 generation,
generation job, active generation pointer와 이전 generation만 같은 완료 경계에서 전환한다.

활성화 계획은 revision 숫자 하나가 아니라 document pointer, 신뢰한 문서 버전, 모든 generation metadata,
claim, 상태, lease·recovery lock, manifest와 inventory fingerprint 전체에 묶인다. 이 값이 달라지면 계획을
폐기하고 다시 잠금 아래 판단한다. 후속 DB 구현은 현재 완료 경계의 `job -> version -> document` 잠금 순서를
보존해야 한다.

## 실패 복구와 fencing

claim에는 owner, document, document version, generation identity와 claim version을 포함한다. recovery는
저장된 lease가 만료된 뒤 해당 claim에 recovery lock을 먼저 기록해야 한다. 정확한 lock token을 가진
recovery만 claim version을 증가시키고 새 lease를 받을 수 있다.

recovery lock 이후 이전 Worker의 `READY`, `FAILED`, `ACTIVE` 완료 요청은 모두 거부한다. Passage 생성,
Passage embedding, Child 생성, Child embedding, 저장, 활성화 중 어느 단계에서 실패해도 현재 ACTIVE
generation과 document pointer는 유지한다. 새 문서 버전의 실패는 그 버전만 `FAILED`로 만들며 현재 버전은
계속 `ACTIVE`다.

## owner와 vector 재사용

Document, DocumentVersion, generation, Passage, Child와 vector row는 같은 owner·document·version·generation
계층이어야 한다. 다른 owner의 동일 원문과 hash는 공유하지 않는다.

Child vector는 다음 key가 모두 같을 때만 재사용한다.

```text
owner scope
EvidenceChild.sourceText SHA-256
embedding model ID
resolved model digest
dimension
Child embedding input policy version
```

재사용 출처는 inventory 검증을 마친 `ACTIVE` 또는 `SUPERSEDED` generation의 `COMPLETED` 작업으로 제한한다.
`BUILDING`, `READY`, `FAILED` 또는 미완료 작업의 vector는 사용하지 않는다. Passage vector 재사용은 이번
계약의 범위가 아니다.

재사용하는 것은 vector bytes뿐이다. 새 generation의 Child ID, Passage 소속, source span, page·line·offset,
Parent 관계와 provenance는 새로 만든다. 이전 Child row에 대한 참조를 남기지 않는다.

## SUPERSEDED와 정리

- 새 generation 활성화 전에는 이전 generation을 삭제하지 않는다.
- active pointer가 가리키는 generation은 삭제할 수 없다.
- `SUPERSEDED`는 보존 기간 뒤 정리할 수 있고, `FAILED`는 terminal job과 fencing 확인 뒤 별도 정책으로
  정리할 수 있다. `BUILDING`과 `READY`는 generic cleanup 대신 lease recovery 대상으로 취급한다.
- 정리 작업은 owner·document·generation과 active pointer를 다시 잠금·확인하고 idempotent해야 한다.
- artifact/vector를 먼저 지우고 generation을 지우거나 동등한 안전한 cascade를 하나의 정리 경계로 둔다.
- `SUPERSEDED` vector를 재사용할 때는 새 vector row로 bytes를 복사하므로 이후 출처 세대를 삭제해도 새
  generation에 dangling reference가 생기지 않아야 한다.
- 원본 `DocumentVersion` 정리와 Search generation 정리는 분리한다. generation 정리가 원본을 삭제하면 안 된다.

보존 기간과 즉시 삭제 여부는 `OPEN_DECISION`이다. 이번 Phase에서는 cleanup을 구현하지 않는다.

## 개념 저장 구조

실제 이름과 정규화는 migration Phase에서 확정한다. 필요한 책임은 다음과 같다.

- generation: owner·document·version, 구조/Passage/Child 정책, Passage/Child embedding 계약, 상태
- Search V3 job: generation identity, 상태, lease, claim version, attempt, recovery lock, 완료 시각
- expected manifest: generation별 Passage/Child count와 manifest hash
- Passage·Child: generation-scoped ID, 순서·소속과 provenance
- Passage·Child vector: generation·artifact·입력 hash·model 계약과 payload
- document별 active Search V3 generation pointer

owner-document-version-generation composite FK, generation 내 artifact/vector unique, pointer와 `ACTIVE` 상태의
정합성, 문서당 검색 가능한 generation 하나를 DB와 transaction 양쪽에서 강제해야 한다. Search V2의
`document_chunks`는 나란히 유지한다.

## PostgreSQL 검증 경계

이번 메모리 모델은 PostgreSQL 증거가 아니다. 다음은 모두 `NOT_RUN`이다.

- DDL/Flyway와 실제 composite FK·unique constraint
- 실제 transaction rollback과 isolation/concurrency
- lock order와 recovery lock SQL
- owner-scoped active-generation 검색 query
- cleanup/cascade/retention SQL

## 판정 Gate

`SHADOW_INDEX_LIFECYCLE_READY`는 다음이 모두 충족될 때만 가능하다.

- 상태·작업 조합과 invalid transition 테스트 통과
- 독립 manifest, artifact, vector 완전성·lineage 테스트 통과
- 새 버전 활성화와 같은 버전 재색인의 원자 상태 모델 테스트 통과
- 모든 실패 단계에서 이전 ACTIVE 보존
- lease 만료, recovery lock, claim version과 full-state activation fencing 통과
- owner·document·version·generation 혼입 0
- 0/20/50/100% 재사용 결과가 `100/80/50/0`이고 model ID·digest·dimension·Child 입력 정책 변경 시 재사용 0
- cross-owner와 미완료/실패 generation 재사용 0, 새 provenance 보존
- cleanup 계약, SEALED 불변, Production diff 0, 독립 감사 blocker 0

PostgreSQL 항목이 `NOT_RUN`이어도 evaluation-only 판정은 가능하다. 이 판정은 실제 저장 구조, migration,
Production 적용 또는 cutover 완료를 뜻하지 않는다.

- `SHADOW_INDEX_LIFECYCLE_READY`: 위 Gate 통과
- `NEEDS_ADJUSTMENT`: lifecycle·재사용 경계 또는 독립 감사 blocker가 남음
- `NO_GO`: generation 분리가 기존 ACTIVE·owner·Worker 안전 계약과 충돌
