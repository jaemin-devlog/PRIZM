# PRZ-035 Plan

- 상태: `COMPLETED / PRECOMPUTE_CHILD_EMBEDDINGS`

1. `ORIENT` — `refactor/search-v3`, PRZ-034 ancestry, clean worktree, local artifact와 SEALED 상태를 확인한다.
2. `SPEC` — A/B 정의, exact result parity, 비용 산식과 판정 Gate를 결과 전에 고정한다.
3. `PLAN` — PRZ-034 input/prediction을 재사용하고 fresh B3 parity replay 뒤 A 241개, B Top5 query별
   no-cache 실행을 설계한다.
4. `IMPLEMENT` — `src/searchEvaluation/**`에 운영 모의·공식 runner·focused test만 추가한다.
5. `VERIFY` — code freeze 뒤 같은 BGE-M3로 공식 비교를 1회 실행하고 두 output을 Gold 전에 봉인한다.
6. `AUDIT` — 품질 parity, 비용, projection, 문서 버전 분석, SEALED/diff/OSS 경계를 감사한다.
7. `INTEGRATE` — 결과 문서와 Registry를 갱신해 branch에 commit하고 origin에 백업한다. PR과 merge는
   하지 않는다.

명백한 측정 코드 버그가 있으면 결과를 `INVALID`로 보존하고 같은 결과를 공식 판정에 쓰지 않는다.
