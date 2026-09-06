# PRZ-036 Tasks

- 상태: `VERIFIED`

- [x] PRZ-035 local/origin parity, 기준 HEAD와 clean worktree 확인
- [x] Production DocumentVersion·완료 transaction·claim fencing·owner FK 확인
- [x] generation·논리 job 상태와 원자 활성화 Gate 고정
- [x] 독립 manifest와 Passage·Child vector 완전성 계약 구현
- [x] evaluation-only lifecycle, lease/recovery lock과 full-state fencing 구현
- [x] Child vector exact reuse key와 완료된 source generation 자격 구현
- [x] generation 상태·활성화·실패·owner·stale Worker 테스트
- [x] 0/20/50/100% reuse와 model/digest/dimension/policy invalidation 테스트
- [x] 새 Child provenance 보존과 cross-owner 재사용 차단 테스트
- [x] `SUPERSEDED`/`FAILED` cleanup 계약과 후속 `OPEN_DECISION` 기록
- [x] 독립 코드 감사 blocker `0`
- [x] SEALED hash/state 불변 확인
- [x] Production·migration·dependency·frontend·MCP·Docker diff `0` 확인
- [x] Registry와 PRZ 문서 정합성 확인
- [x] 최종 판정 `SHADOW_INDEX_LIFECYCLE_READY`

다음은 이번 Phase에서 실행하지 않았다.

- PostgreSQL DDL/FK/transaction/concurrency/query/cleanup: `NOT_RUN`
- Search V3 benchmark와 model inference: `NOT_RUN`
- SEALED FINAL 검색: `NOT_RUN`
- Production 저장 구조와 migration: `NOT_RUN`
