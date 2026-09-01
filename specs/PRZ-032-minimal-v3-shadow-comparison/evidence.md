# PRZ-032 Evidence

- 상태: `IN_PROGRESS / OFFICIAL_COMPARISON_NOT_RUN`
- 시작 branch / HEAD: `PRZ-031-semantic-evidence-directness@a68e95a8b1adb9915fc6359cc6687e9d55068b45`
- 현재 branch: `PRZ-032-minimal-v3-shadow-comparison`
- `origin/main`: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- 시작 working tree: `CLEAN`
- Production 변경: `0`

## 1. Push-only backup

force, rebase, squash, PR, merge와 tag 변경 없이 다음 local HEAD를 동명 origin branch로
push하고 upstream을 연결했다.

| branch | backed-up HEAD |
| --- | --- |
| PRZ-025-search-v3-foundation | `5f8229f88251938dc5b34588676cc69edf409c99` |
| PRZ-026-structural-parsing-parent-child | `a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9` |
| PRZ-027-cross-encoder-reranking | `7271654b80ba7db3bc9cec89cba8ba1000660132` |
| PRZ-028-typed-exact-constraints | `33c702aa0bff86502f7f70a343b60c59c13eb80f` |
| PRZ-029-evidence-validation-selection | `f7e4a7adffd5574526d6c00c76ece9113a68d69f` |
| PRZ-030-semantic-evidence-validation-ceiling | `aca58a6c11b517557d6081756a3ea2cdc5f0550c` |
| PRZ-031-semantic-evidence-directness | `a68e95a8b1adb9915fc6359cc6687e9d55068b45` |

계보는 `main → 025 → 026`, 이후 PRZ-027 NO_GO side branch와
`026 → 028 → 029 → 030 → 031`이다. PRZ-027은 Minimal V3 ancestry에 포함되지 않는다.
기본 worktree의 별도 PRZ-016 local 변경은 clean PRZ-031 worktree와 섞지 않았다.

## 2. 실행 전 사실

- `CURRENT_FRESH_BASELINE=NOT_RUN`; 새로운 V2 fresh candidate/result는 아직 없음
- PRZ-031의 Gold-free B3 freeze는 93-query semantic 계보 자산이며 PRZ-032의 Typed Stress
  1.1.0 포함 전체 비교 결과가 아님
- PRZ-029 Typed applicability는 Typed Stress 1.1.0의 frozen runtime identity에만 입증됨
- official comparison, Gold join, metric, performance: `NOT_RUN`
- SEALED FINAL combined SHA-256:
  `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- SEALED state: `opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`

## 3. 실제 결과

`NOT_RUN`. code/input/model freeze와 1회 output 검증 뒤에만 갱신한다.

## 4. 공식 실행 전 검증

- Gold-free runtime projection: `117 queries / 61 DEV / 56 CALIBRATION / 23 users /
  26 versions (25 ACTIVE, 1 inactive)`
- runtime input SHA-256:
  `47a2356bde46cd80204466a5f419b3fcbb6233d31f59d64a5792b5f9a27807db`
- 실제 V2 source object 사용: `SearchService`, `VectorSearchRepository`,
  `CompositeSearchProfile`, `TextChunker`, fallback/rescue와 localization
- JDBC boundary: `PRODUCTION_SERVICE_REPOSITORY_SOURCE_WITH_EVALUATION_JDBC_ROWS`;
  `POSTGRESQL_SQL_RUNTIME_NOT_REVERIFIED`
- focused integrity/metric 및 PRZ-026 B3·PRZ-029 selection 관련 테스트: `PASS`
- `compileSearchEvaluationJava`: `PASS`
- `node scripts/verify-oss-readiness.mjs`: `PASS`
- 전체 `searchEvaluation`: `FAIL (HISTORICAL/ENVIRONMENTAL)` — Docker/PostgreSQL 부재,
  과거 one-shot artifact와 historical benchmark Gate 때문에 276개 중 22개 실패, 5개 skip.
  PRZ-032 focused test는 이 실행에서도 통과했으며 공식 PRZ-032 output은 생성되지 않았다.
