# PRZ-030 Plan

## 단계

1. `ORIENT` — PRZ-029 HEAD, B3/PRZ-028/PRZ-025 dependency, clean tree, 기존 B3 raw SHA를 확인한다.
2. `SPEC` — Gold metadata만으로 coverage를 감사하고 Stress 필요성과 `CAPABILITY_GATE`를 고정한다.
3. `PLAN` — Stress source span/split/lineage/hash를 검증하고 검색 전 `INPUT_FROZEN`으로 커밋한다.
4. `IMPLEMENT` — Gold-free candidate projection/freeze, 후행 Gold join, Oracle stable partition과 metric을 evaluation-only로 구현한다.
5. `VERIFY` — 기존 frozen B3 ranking을 replay하고 Stress만 동결 입력으로 B3 후보를 만든 뒤 Oracle ceiling을 1회 계산한다.
6. `AUDIT / INTEGRATE` — identity parity, Gate, Production/SEALED/scope, 문서 정합성을 감사하고 local commit만 남긴다.

## Gate

- Stress freeze 전 source span, answerability/relation, owner/version, DEV/CAL lineage, SHA-256 오류 0
- Gold access guard의 candidate hash freeze 전 접근 0
- S0/O1 candidate identity/set/cosine parity 100%, stable relation order
- Typed Stress 제외, SEALED semantic access/search 0
- Production·migration·dependency·frontend·MCP·Docker·PRZ-029 변경 0
- 실행하지 않은 검증은 `NOT_RUN`

## 종료 판정

`ORIENT → SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT → INTEGRATE`를 완료했다.
Oracle 결과는 `BUILD_SEMANTIC_VALIDATOR`; 실제 validator, Sparse, Parent Dense, Production,
SEALED FINAL은 이 PRZ에서 시작하지 않는다.
