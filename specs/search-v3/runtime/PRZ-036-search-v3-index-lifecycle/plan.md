# PRZ-036 Plan

- 상태: `VERIFIED`

1. `ORIENT` — PRZ-035 기준, Production version/claim/activation 계약과 SEALED 상태를 확인한다.
2. `SPEC` — generation·작업 상태, 독립 manifest, owner·fencing·재사용·정리·원자적 활성화 계약을 결과 전에 고정한다.
3. `IMPLEMENT` — `src/searchEvaluation`에 불변 lifecycle 모델과 Child vector 재사용 planner만 추가한다.
4. `VERIFY` — 상태 전이, 실패 보존, inventory, owner, lease/recovery lock, 재사용 비율·무효화·provenance를 결정적 fixture로 검사한다.
5. `AUDIT` — Production/migration/dependency diff 0, SEALED 불변, 문서·Registry 정합성과 구현 차단 항목을 독립 검토한다.
6. `INTEGRATE` — 결과를 branch에 commit하고 origin에 백업한다. PR과 branch merge는 하지 않는다.

PostgreSQL의 DDL/FK, transaction·concurrency, owner query와 cleanup SQL은 Production schema가 없는 이번
Phase에서 `NOT_RUN`으로 분리한다. 메모리 상태 모델 성공을 PostgreSQL 검증으로 표현하지 않는다.
