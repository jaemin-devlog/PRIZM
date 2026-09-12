# PRZ-037 Plan

## 1. ORIENT / SPEC

- PRZ-036 기준 SHA와 working tree를 확인한다.
- 기존 문서·version·chunk·processing job DDL과 완료·실패·복구 흐름을 감사한다.
- 별도 V3 job, composite lineage, active pointer와 manifest 책임을 고정한다.

## 2. IMPLEMENT

- additive V18 migration으로 generation, V3 job, Passage, Child, 두 vector table과 nullable pointer를 만든다.
- Search V2의 table·source·dependency는 수정하지 않는다.
- PostgreSQL 전용 제약을 검증하는 focused integration test를 추가한다.

## 3. VERIFY / AUDIT

- 실제 pgvector PostgreSQL에서 migration, owner/generation FK, vector 1:1과 first-upload/reindex를 검증한다.
- 기존 owner·lease·색인 핵심 회귀와 OSS readiness를 실행한다.
- schema가 PRZ-036 계약을 어디까지 강제하는지 DB/service 책임으로 다시 감사한다.
- 문서, Registry, SEALED metadata와 diff scope를 확인하고 판정한다.
