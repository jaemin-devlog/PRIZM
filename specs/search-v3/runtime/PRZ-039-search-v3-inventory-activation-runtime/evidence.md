# PRZ-039 Evidence

## 최종 판정

`INVENTORY_ACTIVATION_READY`

실제 PostgreSQL inventory를 독립 검증해 `BUILDING → READY`로 전환하고, 같은 Production
`DocumentVersion` 안에서 Search V3 generation을 원자적으로 활성화했다. PRZ-039 집중 시나리오
`11/11`이 통과했고 blocking finding은 없다.

- branch: `PRZ-039-search-v3-inventory-activation-runtime`
- 기준: `refactor/search-v3@dab65383c0b3221fc2845951486cda986cdaf712`
- 시작 working tree: `CLEAN`
- Production Search V2 source·query·API·frontend·MCP 변경: `0`
- PRZ-039 activation의 `documents.active_version_id`, `document_versions.status`, `document_chunks` 변경: `0`
- `OPENSQL_VALIDATION=NOT_RUN`

## 구현 내용

| 구성 | 책임 |
| --- | --- |
| V19 migration | verified inventory fingerprint 저장, V2 active version 변경·해제 시 stale V3 pointer 분리 |
| `SearchV3InventoryActivationRepository` | full claim lineage 잠금, inventory 독립 조회, READY·activation 조건부 변경 |
| `SearchV3InventoryVerifier` | exact logical manifest와 vector payload fingerprint 검증 |
| `SearchV3InventoryActivationService` | `BUILDING → READY`, 같은-version 원자 activation과 rollback |

V19은 적용된 V18을 수정하지 않고 `verified_inventory_sha256`을 추가했다. Production V2가
`active_version_id`를 바꾸거나 null로 해제할 때는 기존 ACTIVE V3 generation을 `SUPERSEDED`로 바꾸고
V3 pointer만 비운다. 새 active version, V2 version 상태와 chunk에는 관여하지 않는다.

## Inventory와 manifest

- Passage·Child·두 vector 계열을 별도로 읽어 누락을 join으로 숨기지 않았다.
- count뿐 아니라 logical key, 전체/Passage 내부 순서, membership, text hash와 provenance를 비교했다.
- `document_source_sha256`은 추출 원문 계보 안에서 일치·형식·단일성을 확인했다. 원본 파일 byte hash인
  `document_versions.content_hash`와 같다고 가정하지 않았다.
- vector는 artifact당 1개인지와 input hash, model ID, resolved digest, dimension, input policy, finite,
  non-zero를 확인했다.
- fingerprint는 DB ID·timestamp·Java `toString()`을 제외하고 raw float32 bit까지 포함했다.
- expected manifest는 DB insert 전 메모리 fixture에서 만들고 literal SHA-256
  `fe4b2c577c38d6b76bf01d485133043625f2344bb0da79e95076f13758f019f1`로 고정했다.

같은 count의 logical mismatch, 잘못된 Child membership, vector 누락·metadata 불일치와 READY 뒤 vector
변조는 모두 거부됐다.

## READY와 activation

`markReady`는 현재 `PROCESSING` job, full owner·문서·version·generation·claim version, recovery lock 없음,
`BUILDING` generation과 exact inventory를 모두 확인했다. 성공 시 generation만 `READY`로 바꾸고
`build_completed_at`과 verified fingerprint를 저장했으며 job은 `PROCESSING`으로 유지했다. stale claim과
recovery-locked claim은 거부됐다.

activation은 verified fingerprint와 현재 DB inventory를 다시 비교한 뒤 다음 변경을 한 transaction에서
수행했다.

```text
old ACTIVE -> SUPERSEDED       (있을 때)
new READY -> ACTIVE
new PROCESSING job -> COMPLETED
documents.active_search_v3_generation_id -> new
```

첫 shadow activation과 같은-version 재색인이 통과했다. 다른 version, null active version, stale claim,
recovery lock, 잘못된 old pointer는 거부했다. 의도적으로 마지막 pointer update를 실패시킨 테스트에서 기존
ACTIVE와 새 READY, job, pointer가 모두 원상 복구됐다.

동일 generation 경쟁과 서로 다른 READY generation 경쟁 모두 최종 ACTIVE/pointer를 하나로 유지했다.
V2 lifecycle transaction이 document row를 먼저 잡은 경로에서는 V3가 PostgreSQL `NOWAIT`와 SQLSTATE
`55P03` 처리로 즉시 실패하고 부분 상태를 남기지 않았다. 이 테스트는 실제 문서 삭제 service 전체가 아니라
핵심 경계인 `active_version_id → NULL`과 V19 trigger를 검증했다.

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| PRZ-039 PostgreSQL runtime | `11/11`, failures `0`, errors `0`, skips `0` |
| PRZ-038 job fencing | `6/6`, failures `0` |
| Search V3 shadow/V19 migration 회귀 | `8/8`, failures `0` |
| 선택 PostgreSQL 6개 suite 합계(위 3개 포함) | `67`, failures/errors `0`, skips `3` |
| 기존 V2 Worker 집중 unit | `19/19`, failures `0` |
| PRZ-036 lifecycle·Child reuse | `28/28`, failures `0` |
| dataset·SEALED guard | `15/15`, failures `0` |
| 전체 backend `check` | unit `610` + integration `143`, failures/errors `0`; skips `20` + `9` |
| OSS readiness | `PASS` — Markdown·link·tracked-file safety, SBOM verifier `16/16` |
| `git diff --check` | `PASS` |

전체 `check` 첫 실행에서는 새 테스트가 statement마다 새 JDBC socket을 열어 Windows ephemeral port를
소진하면서 `BindException` 1건이 발생했다. assertion 실패는 아니었으며, 테스트 전용 DataSource를 작은
Hikari pool로 재사용하고 `@AfterEach`에서 닫도록 고친 뒤 같은 전체 `check`가 통과했다.

PRZ-037의 historical migration 결과 `7/7`은 변경하지 않았다. 위 `8/8`은 V19 upgrade case를 추가한 현재
branch의 회귀 결과다.

구현 감사 중에는 `documents.updated_at` 비변경, V2 lifecycle 호환 trigger, 원본 byte hash와 추출 원문
hash 분리, JSON 배열의 명시적 ordinality, document `NOWAIT`, inventory 검증을 document lock 전에 수행하는
잠금 순서를 확정했다. 최종 독립 source 감사의 blocking finding은 `0`이다.

## SEALED FINAL과 남은 범위

- dataset: `search-v3-fresh-seed-1.0.1`
- combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- manifest SHA-256: `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`
- Git tree: `a129080861d7dafd32a9b3b3357b61aebb237e59`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`

validator는 metadata와 hash만 확인했으며 검색은 실행하지 않았다. 실제 구조 분석 Worker, Passage·Child와
embedding 생성, coordinator, Search V3 query/API/cutover, cleanup·retention은 이번 범위가 아니다.
