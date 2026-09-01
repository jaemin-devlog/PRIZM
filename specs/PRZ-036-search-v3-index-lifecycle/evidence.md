# PRZ-036 Evidence

## 최종 판정

`SHADOW_INDEX_LIFECYCLE_READY`

문서 버전과 Search V3 색인 세대를 분리하고, 완성된 세대만 원자적으로 활성화하는 계약을 evaluation-only
상태 모델로 검증했다. 새 세대가 실패하거나 stale Worker가 늦게 완료해도 기존 ACTIVE 검색은 유지된다.
Child vector는 owner와 model 계약이 정확히 같고 완료된 세대에서 가져온 경우에만 bytes를 재사용한다.

이 판정은 실제 PostgreSQL 저장 구조, migration 또는 Production 적용 완료를 뜻하지 않는다.

## 시작 기준

- branch: `PRZ-036-search-v3-index-lifecycle`
- 기준: `PRZ-035-child-embedding-operation-strategy@42e0e12a13ae9bc5da57f5607086cdc0b533f84a`
- PRZ-035 local/origin parity: `PASS`
- `refactor/search-v3` local/origin: `a6fb5ee5240b0b1fcc59f78b329b55563512df1d`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- 시작 working tree: `CLEAN`
- Production 변경: `0`

## 검증한 상태 계약

| 시나리오 | 결과 |
|---|---|
| `BUILDING`, `READY`, `FAILED`, `SUPERSEDED` 검색 노출 | `0` |
| 새 문서 버전 활성화 | version·두 pointer·generation·논리 job을 한 immutable commit으로 전환 |
| 같은 버전 재색인 | `DocumentVersion`과 `active_version_id` 유지, generation만 교체 |
| 여섯 실패 단계 | 새 generation/job만 실패, 기존 ACTIVE 유지 |
| 독립 manifest보다 축약된 inventory | 거부 |
| Passage/Child vector 누락·잘못된 payload | 거부 |
| owner·document·version·generation lineage 불일치 | 거부 |
| 같은 revision의 다른 상태에 activation plan 적용 | 거부 |

`READY`는 frozen manifest와 실제 Passage·Child·vector가 정확히 같고, 각 vector의 kind, input hash, model
계약, dimension, finite value와 non-zero norm이 모두 유효할 때만 허용된다.

## stale Worker와 recovery

단순한 `leaseExpired=true` 입력을 신뢰하지 않는다. generation에 저장된 lease 시각이 지난 뒤 현재 claim에
`RecoveryLock`을 먼저 기록하고, 정확한 lock token을 가진 요청만 claim version을 증가시킬 수 있다.
recovery lock 이후 이전 claim의 ready·fail·activate 완료는 모두 거부됐다.

활성화 계획도 revision만 비교하지 않는다. document pointer, trusted source versions, generation metadata,
manifest, inventory fingerprint, claim, lease와 recovery lock을 포함한 전체 상태가 달라지면 stale plan으로
거부한다.

## Child vector 재사용

100개 Child의 결정적 fixture 결과다.

| sourceText 변경 | 재사용 | 재계산 |
|---:|---:|---:|
| 0% | 100 | 0 |
| 20% | 80 | 20 |
| 50% | 50 | 50 |
| 100% | 0 | 100 |

다음 경우에는 원문이 같아도 재사용이 `0`이었다.

- model ID 변경
- resolved model digest 변경
- dimension 변경
- Child embedding 입력 정책 변경
- owner 변경
- 출처 generation이 `BUILDING`, `READY`, `FAILED`이거나 논리 job이 미완료·실패
- vector 누락, dimension 불일치, non-finite 값, zero vector

`ACTIVE` 또는 `SUPERSEDED`이면서 `COMPLETED`인 출처만 허용했다. 재사용 assignment는 새 generation의
Child ID와 새 source provenance를 유지했고 이전 vector bytes만 방어 복사했다.

## 기존 Production 계약과의 관계

- `Document.active_version_id`는 완료 transaction 전까지 이전 버전을 유지한다.
- `IndexingCompletionService`는 claim·owner·PROCESSING 상태와 chunk/vector 완전성을 잠금 아래 다시 확인한
  뒤 version, document pointer와 processing job을 함께 완료한다.
- `processing_jobs.claim_version`은 lease 재선점 뒤 stale Worker 완료를 거부한다.
- owner는 현재 Document, DocumentVersion, chunk와 processing job의 composite FK 계층으로 보호된다.
- Production에는 Search V3 generation, model digest metadata와 Child vector 재사용 구현이 없다.

이번 모델은 기존 잠금 순서를 바꾸지 않고 Search V3에 필요한 별도 generation/job 계약을 제안한다. 현재
`document_chunks`는 Search V2 경로로 그대로 유지한다.

## 개념 저장 경계

후속 구현에는 generation, Search V3 job/lease, frozen manifest, Passage, Child, 두 vector 계열과 문서별
active generation pointer가 필요하다. 모든 artifact는 owner-document-version-generation composite lineage를
가져야 한다. 실제 table 이름, retention 기간과 정규화 방식은 아직 확정하지 않았다.

## 독립 감사

첫 감사에서 revision-only activation, self-asserted lineage, self-reported inventory, source version/job 완료
누락을 blocker로 확인했다. full-state token, trusted `DocumentVersionRef`, 독립 manifest·실제 vector 검증과
원자 상태 전환으로 보완했다. 이어서 recovery 권한과 vector 재사용 출처 상태를 강화했다.

최종 읽기 전용 재감사 결과는 `READY`였으며 blocking finding은 `0`이었다.

비차단 후속 경계:

- 실제 PostgreSQL row lock/CAS, rollback, composite FK와 owner-scoped query 검증
- DB row에서 trusted lifecycle DTO와 재사용 후보를 만드는 adapter
- 활성 문서 버전이 아직 없는 최초 업로드 상태
- `SUPERSEDED`/`FAILED` retention 기간과 cleanup SQL

## 검증 결과

| 검증 | 결과 |
|---|---|
| lifecycle 집중 테스트 | `15/15 PASS` |
| Child vector reuse 집중 테스트 | `13/13 PASS` |
| 기존 owner·lease·failure·recovery 회귀 테스트 | `19/19 PASS` |
| 기존 Search V3 dataset/SEALED guard | `15/15 PASS` |
| PostgreSQL DDL/FK/transaction/concurrency/query/cleanup | `NOT_RUN` |
| 검색 benchmark, model 실행 | `NOT_RUN` |

집중 테스트의 최종 직렬 실행은 `28/28`, failure·error·skip `0`이었다. 독립 감사 중 같은 Gradle build
directory에서 두 `--rerun-tasks`가 겹친 한 번의 실행은 test discovery가 사라져 `INVALID_TOOLING_RUN`으로
폐기했고, agent 실행을 끝낸 뒤 직렬로 다시 검증했다.

실행한 명령:

```text
gradlew.bat searchEvaluation --tests ...SearchV3IndexLifecycleTest --tests ...SearchV3ChildEmbeddingReusePlannerTest --no-daemon --rerun-tasks
gradlew.bat test --tests ...IndexingCompletionOwnershipTest --tests ...ProcessingJobLeaseServiceTest --tests ...WorkerLeaseHeartbeatTest --tests ...IndexingFailureServiceTest --tests ...ProcessingJobRecoveryServiceTest --no-daemon --rerun-tasks
gradlew.bat searchEvaluation --tests ...SearchV3DenseAblationDatasetTest --tests ...SearchV3MinimalShadowIntegrityTest --no-daemon --rerun-tasks
```

## SEALED FINAL

- combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- manifest SHA-256: `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`
- Git tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`

검색은 실행하지 않았고 manifest metadata와 hash만 확인했다.
