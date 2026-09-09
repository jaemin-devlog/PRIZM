# PRZ-043 Plan

## 단계

1. `ORIENT`: Git/source parity와 ZIP raw identity를 Gold-free로 감사한다.
2. `SPEC`: metric, Gate, one-shot 경로와 Gold 접근 순서를 결과 전에 고정한다.
3. `PLAN`: PRZ-042 하네스의 path binding·PDF·별도 prediction freeze 한계를 보완한다.
4. `IMPLEMENT`: `src/searchEvaluation/**`와 PRZ-043 문서만 최소 변경한다.
5. `VERIFY`:
   - synthetic fixture로 path binding, hash, Gold-after-prediction과 metric을 검증한다.
   - 실제 BGE-M3 mixed TXT/PDF smoke와 PostgreSQL runtime을 확인한다.
   - 공식 dataset은 전체 V2 600건, 전체 V3 600건을 정확히 한 번 실행한다.
6. `AUDIT`: source/dataset/prediction hash, Gold timing, 결과와 문서 표현을 독립 점검한다.
7. `INTEGRATE`: 검증을 통과한 PRZ-043 branch만 commit/push한다. PR과 merge는 하지 않는다.

## 변경 예상 범위

- `build.gradle`: PRZ-043 preflight/focused/official task
- `src/searchEvaluation/java/**/Prz043*`: ZIP loader, freeze, runtime adapter, Gold join, metric/Gate
- `specs/search-v3/evaluation/PRZ-043-search-v3-release-grade-evaluation/**`
- `specs/README.md`

`src/main/**`, migration, frontend, MCP, dataset ZIP/payload는 수정하지 않는다.

## 실패와 중단

- model/dataset/source parity 실패: official attempt claim 전 중단
- official attempt 시작 뒤 runtime 실패: failure receipt를 남기고 재실행하지 않음
- Gold 조기 접근 또는 prediction freeze 위반: `EVALUATION_INVALID`
- metric/Gate 실패: 결과를 유지하고 `V3_NEEDS_ADJUSTMENT` 또는 `V3_NO_GO`

## 실제 종료

`IMPLEMENT` 중 Gold 제외 glob이 Windows 경로에서 적용되지 않아 `sealed/gold.json`의 일부 의미
필드가 prediction 전에 출력됐다. 계약의 즉시 중단 조건에 따라 공식 attempt, runtime 검색,
Gold join과 metric 계산을 모두 실행하지 않았다. 미완성 하네스 파일은 tracked 변경으로 남기지
않았고, 무결성 사고와 `EVALUATION_INVALID` 판정만 기록한다.

## 검증 명령

정확한 Gradle task는 구현 뒤 `tasks.md`와 evidence에 고정한다. 최소 범위는 PRZ-043 focused
test, PRZ-038~041 회귀, migration, backend, PostgreSQL integration, frontend lint/build,
OSS readiness와 `git diff --check`다. OpenSQL은 실행하지 않으면 `NOT_RUN`으로 남긴다.
