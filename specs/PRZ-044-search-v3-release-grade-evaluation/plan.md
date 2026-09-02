# PRZ-044 Plan

## 단계

1. `ORIENT`: Git/source parity와 INPUT ZIP의 Gold physical absence·hash를 read-only로 확인한다.
2. `SPEC`: source/model/dataset, canonical prediction, one-shot, Gold release와 다음 metric Gate를
   결과 전에 고정한다.
3. `PLAN`: PRZ-042 runtime을 재사용하되 v1.0.3 raw TXT/PDF와 V2-all → V3-all freeze 경계를
   별도 PRZ-044 하네스로 구현한다.
4. `IMPLEMENT`: `src/searchEvaluation/**`, `build.gradle`, PRZ-044 문서만 최소 변경한다.
5. `VERIFY`:
   - Gold 없는 synthetic ZIP/path/one-shot 단위 test
   - synthetic TXT/PDF + 실제 PostgreSQL/BGE-M3 preflight
   - source/model/contract/input freeze
   - official 90-document indexing과 V2 600 → V3 600 prediction 1회
6. `AUDIT`: separate file/hash/reload, Gold absent/access false, source·dataset 불변과 변경 범위를
   독립 점검한다.
7. `INTEGRATE`: completion receipt와 evidence를 PRZ-044 branch에만 commit/push한다.

## 예상 변경

- `build.gradle`: PRZ-044 focused/preflight/official task
- `src/searchEvaluation/java/**/Prz044*`: dataset, freeze, prediction DTO/writer, runtime, tests
- `specs/PRZ-044-search-v3-release-grade-evaluation/**`
- `specs/README.md`

Gold loader, metric evaluator와 `src/main/**`는 만들거나 수정하지 않는다.

## 검증 명령

- `.\gradlew.bat compileSearchEvaluationJava --no-daemon`
- PRZ-044 focused unit tests
- PRZ-044 synthetic PostgreSQL/BGE-M3 preflight
- PRZ-044 official prediction task — 정확히 1회
- PRZ-038~041 관련 회귀, migration/backend/integration
- frontend lint/build, OSS readiness, `git diff --check`

실행하지 않은 항목은 `NOT_RUN`으로 기록한다. PostgreSQL 결과를 OpenSQL 근거로 바꾸지 않는다.

## 실패 처리

- preflight/input/model/source 실패: official attempt 전에 중단
- official claim 뒤 실패: failure receipt를 남기고 재실행 금지
- Gold physical entry 발견 또는 Gold 탐색 경로 발견: 즉시 중단
- prediction freeze/hash/reload 불일치: `PREDICTION_PHASE_BLOCKED`

## 실제 종료

`attempt-1` 실패 후 dataset type 3개를 Production enum에 명시적으로 매핑하고, 90/90 매핑과
소규모 실제 색인 preflight를 통과했다. 별도 V2 계약으로 실행한 `attempt-2`는 V2 600건을
동결했지만, V3 색인 중 8개 문서에서 atomic child가 passage 절대 상한을 넘어 종료됐다.
`attempt-2`도 소비됐으며 Gold·metric 단계는 실행하지 않았다.
