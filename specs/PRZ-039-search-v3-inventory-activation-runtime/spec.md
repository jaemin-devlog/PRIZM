# PRZ-039 Search V3 Inventory & Activation Runtime

- 상태: `VERIFIED`
- 유형: Search V3 PostgreSQL inventory 검증·활성화 runtime
- branch: `PRZ-039-search-v3-inventory-activation-runtime`
- 기준: `refactor/search-v3@dab65383c0b3221fc2845951486cda986cdaf712`
- 선행 작업: PRZ-037 `SHADOW_STORAGE_READY`, PRZ-038 `JOB_FENCING_READY`
- Production Search V2 적용: `NO_CHANGE`

## 목적과 범위

V18 shadow table에 저장된 Passage, Child와 두 vector 계열을 실제 DB inventory에서 독립 검증한 뒤
`BUILDING → READY`와 같은 `DocumentVersion` 안의 Search V3 generation 활성화를 원자적으로 수행한다.

이번 작업은 Search V3 shadow runtime만 추가한다. `documents.active_version_id`,
`document_versions.status`, `document_chunks`, Production Search V2 query·API·frontend·MCP는 변경하지 않는다.
구조 분석, artifact·embedding 생성, Worker coordinator, Search V3 query와 cutover도 범위 밖이다.

## Hash 계약

generation 생성 전에 논리 inventory를 다음 입력으로 canonicalize해 `expected_manifest_sha256`으로 동결한다.

- 고정 format version과 UTF-8 길이 구분 필드
- Passage logical key·순서, 원문·검색 입력 hash, 모든 provenance
- Child logical key·전체/Passage 내부 순서, Passage logical key membership, 원문 hash와 모든 provenance
- DB auto-generated ID, timestamp와 Java `toString()`은 제외

READY 검증은 DB text에서 hash를 다시 계산하고 저장된 hash와 대조한다. Passage·Child·vector를 독립적으로
읽어 누락을 inner join으로 숨기지 않는다. 모든 Passage와 Child는 동결 manifest에 포함된 하나의
`document_source_sha256`을 공유해야 한다. 이 값은 추출 원문의 hash이므로 원본 파일 byte hash인
`document_versions.content_hash`와 같다고 가정하지 않는다. vector는 artifact당 정확히 하나여야 하며 input
hash, model ID, resolved digest, dimension, input policy, 유한값과 non-zero norm을 확인한다.

검증된 실제 inventory fingerprint는 논리 manifest hash와 각 vector의 metadata·정규화한 float32 bit 값을
함께 SHA-256으로 계산한다. V18에는 이를 저장할 필드가 없으므로 V18을 수정하지 않고 V19에 nullable
`verified_inventory_sha256`을 추가한다. 기존 row의 null은 migration 호환을 위해 허용하지만 PRZ-039
activation은 값이 없는 READY generation을 거부한다.

## BUILDING → READY

현재 PRZ-038 claim만 다음 순서로 처리할 수 있다.

1. full lineage와 claim version이 같은 `PROCESSING` job을 잠근다.
2. recovery lock이 없는지 확인한다.
3. 같은 lineage의 `BUILDING` generation을 잠근다.
4. Passage → Child → Passage vector → Child vector를 결정적 순서로 잠가 읽는다.
5. expected count와 논리 manifest SHA-256을 비교하고 전체 vector 계약을 검증한다.
6. generation을 `READY`로 바꾸고 DB 시간의 `build_completed_at`과 verified fingerprint를 저장한다.

job은 `PROCESSING`과 현재 claim을 유지한다. lease 만료만으로 claim을 소급 무효화하지 않으며, PRZ-038의
recovery lock 또는 reclaim으로 fencing된 claim만 stale로 본다. mismatch, stale claim 또는 recovery lock이
있으면 어떤 상태도 바꾸지 않는다.

## 같은 DocumentVersion 활성화

activation은 `candidate job → candidate generation → document version → candidate inventory → document → 현재
ACTIVE generation` 순서로 잠근다. candidate claim/generation과 실제 inventory를 먼저 고정·재검증하되,
Production document는 검증 계산이 끝난 뒤 짧게 잠근다. 공유 객체는 기존 V2와 V19 호환 trigger가 따르는
`document → old ACTIVE generation` 순서를 지킨다. candidate version은 `documents.active_version_id`와 정확히
같고 `ACTIVE` 상태여야 한다.

기존 V2 version 교체·active version 해제 경로는 document를 먼저 잠근다. 이 경로와 순환 대기를 만들지 않도록
V3 activation의 document 잠금은 `NOWAIT`이다. V2가 document를 이미 소유했다면 V3 transaction이 즉시
실패해 앞서 잡은 candidate 잠금을 모두 해제하며, retry 여부는 후속 coordinator 정책으로 남긴다.
`active_version_id = NULL`이거나 다른 version이면 거부한다.

문서의 V3 pointer가 null이면 같은 문서의 orphan `ACTIVE` generation이 없어야 한다. pointer가 있으면 그
generation은 같은 owner·문서·현재 version의 `ACTIVE`이고 작업은 `COMPLETED`여야 한다. 이 조건이 깨진
pointer는 자동 복구하지 않고 거부한다.

잠금 아래 actual inventory를 다시 읽어 expected manifest와 verified fingerprint를 모두 재검증한다. 통과하면
한 PostgreSQL transaction에서 다음 순서로 전환한다.

```text
old ACTIVE -> SUPERSEDED       (있을 때)
new READY -> ACTIVE
new PROCESSING job -> COMPLETED
documents.active_search_v3_generation_id old/null -> new
```

`documents.active_version_id`와 `document_versions.status`는 읽기·검증만 하며 변경하지 않는다. 각 update는
expected old value와 full claim identity를 조건으로 정확히 한 row가 바뀌어야 한다. 중간 실패는 전체
rollback한다.

V18 pointer의 복합 FK는 V3가 활성화된 뒤 기존 V2가 새 version을 활성화하거나 문서를 삭제하려 할 때
과거 V3 pointer 때문에 V2 transaction을 막을 수 있다. V19의 `BEFORE UPDATE OF active_version_id` trigger는
이 경우에만 기존 pointer 대상 `ACTIVE` V3 generation을 `SUPERSEDED`로 바꾸고 V3 pointer를 null로 해제한다.
새 `active_version_id`, `document_versions.status`, V2 chunk와 V2 job에는 관여하지 않는다. V3 pointer만
바꾸는 PRZ-039 activation은 `documents.updated_at`도 변경하지 않는다.

## 동시성과 fencing

- stale claim version과 recovery-locked claim은 READY와 activation을 수행할 수 없다.
- candidate generation 잠금은 FK 기반 신규 artifact insert와 충돌하고, 실제 artifact/vector row도 잠근다.
- 같은 generation의 concurrent activation은 하나만 완료할 수 있다.
- 서로 다른 READY generation이 같은 문서에서 경쟁해도 document row가 전환을 직렬화하며 최종 ACTIVE와
  pointer는 정확히 하나로 일치해야 한다.
- V2 version 교체·active version 해제와 V3 activation도 document를 old ACTIVE generation보다 먼저 잠가 교착 순서를
  일치시킨다.
- V2 lifecycle transaction이 document를 먼저 소유한 경우 V3는 `NOWAIT`로 fail-fast rollback해 역순 version 잠금을
  오래 보유하지 않는다.
- old ACTIVE를 먼저 `SUPERSEDED`로 바꿔 partial unique index 충돌을 피한다.

## 수용 기준

`INVENTORY_ACTIVATION_READY`는 실제 PostgreSQL에서 다음이 모두 확인될 때만 사용할 수 있다.

- exact count·logical manifest·verified inventory fingerprint PASS
- Passage/Child key·순서·membership·hash·provenance mismatch와 vector 누락·계약 위반 거부
- current claim의 `BUILDING → READY` 성공, stale·recovery claim 거부
- 첫 shadow activation과 같은 version 재색인 성공
- 다른 version과 null active version 거부, PRZ-039 activation의 `active_version_id` 변경 0
- READY 뒤 inventory 변조 감지
- old `ACTIVE → SUPERSEDED`, new `READY → ACTIVE`, job `PROCESSING → COMPLETED`, pointer 전환
- activation 실패 전체 rollback, concurrent partial state와 복수 ACTIVE 0
- Production Search V2·V18/V19·기존 Worker 회귀 0
- 활성 V3가 있어도 기존 V2 version 교체와 active version 해제를 V18 pointer FK가 막지 않음
- SEALED FINAL 불변, blocking finding 0

OpenSQL actual execution을 하지 않으면 `OPENSQL_VALIDATION=NOT_RUN`으로 남긴다.
