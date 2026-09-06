# PRZ-033 Plan

1. `ORIENT` — PRZ-032 branch/artifact/report/BGE/SEALED parity와 clean 기준점을 확인한다.
2. `SPEC` — LOCAL_CHILD_ORACLE, 배타적 failure stage, metric과 Capability Gate를 결과 전에 고정한다.
3. `PLAN` — Gold-free B3 identity replay, candidate freeze guard, Child-only oracle와 report를 설계한다.
4. `IMPLEMENT` — evaluation-only loader/oracle/focused test를 최소 범위로 구현한다.
5. `VERIFY` — source/test/code freeze 후 BGE 없이 official ceiling을 한 번 실행한다.
6. `AUDIT` — F0 parity, slice/typed/safety/SEALED/diff scope를 감사한다.
7. `INTEGRATE` — aggregate evidence와 Registry를 갱신하고 local commit한다. push/PR/merge는 하지 않는다.

## 공식 실행 Gate

- PRZ-032 output/report/canonical/BGE hash parity
- embedding 없는 replay의 passage ID/parent/span/order parity 100%
- candidate freeze가 검증되기 전 Gold access 0
- F0 metric이 PRZ-032 공식 결과와 일치
- output path가 존재하지 않는 clean code-freeze HEAD
- SEALED FINAL metadata/hash 불변

## 완료 결과

- 공식 code freeze: `03a2285e148aa0a45b032746266fdc9802be690d`
- BGE/model 실행: `0`
- F0 Top1: `0.5412`
- LOCAL_CHILD_ORACLE Top1: `0.9176`
- user-macro Top1 gain: `+0.3344`
- 판정: `BUILD_CHILD_SELECTOR`

실제 Child Selector 구현과 Production 통합은 이 PRZ의 범위가 아니다.
