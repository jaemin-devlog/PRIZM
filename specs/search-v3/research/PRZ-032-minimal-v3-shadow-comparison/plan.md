# PRZ-032 Plan

1. `ORIENT` — PRZ-025~031 local/remote ancestry와 clean target worktree를 확인하고 push-only
   backup을 완료한다.
2. `SPEC` — V2/V3 구성, input/Gold 경계, metric과 판정을 결과 전에 고정한다.
3. `PLAN` — Gold-free runtime projection, 실제 V2 source + evaluation JDBC adapter, Minimal V3 runner와
   output phase guard를 설계한다.
4. `IMPLEMENT` — evaluation-only loader/seeder/comparison/freeze와 focused test를 구현한다.
5. `VERIFY` — source/config/input/model을 동결하고 official DEV/CAL shadow output을 한 번 만든 뒤
   검증된 output에만 Gold를 join한다.
6. `AUDIT` — candidate/final, structure, typed/semantic, slice, cost, owner/version/SEALED와 diff
   scope를 감사한다.
7. `INTEGRATE` — 문서와 Registry를 실제 결과에 맞추고 local commit 후 PRZ-032 branch만
   push한다. PR과 main merge는 하지 않는다.

공식 비교는 실행 계약 HEAD `6b7cfab`에서 한 번 완료했다. 결과는
`MIXED_NEEDS_NEXT_CAPABILITY`이며, 문서 감사·local commit·push-only backup까지 완료했다.

## 공식 실행 Gate

- Production source/profile과 Minimal V3 dependency SHA가 freeze와 일치
- same canonical corpus/query/model/dimension/cosine
- actual Production `SearchService`/repository/profile source 사용; JDBC row boundary의
  `POSTGRESQL_SQL_RUNTIME_NOT_REVERIFIED` 명시
- output verified 전 Gold access 0
- Typed applicability는 frozen Typed Stress 1.1.0만 true
- SEALED metadata/hash 불변
- official output path가 존재하지 않는 clean code-freeze HEAD
