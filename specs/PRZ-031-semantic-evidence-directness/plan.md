# PRZ-031 Plan

## 단계

1. `ORIENT` — PRZ-030 HEAD와 PRZ-025/026 B3/029 dependency, origin/main, clean tree를 확인한다.
2. `SPEC` — 제품 의미, 네 relation, Top10 stable partition, Gold-after-output 순서와
   Capability Gate를 고정한다.
3. `PLAN / MODEL_SELECTION_GATE` — 기존 local tooling과 설치 model의 exact
   digest/revision/license/size를 감사한다.
4. `IMPLEMENT` — 적합한 model이 있을 때만 Gold-free input freeze, local adapter, strict
   output/phase guard, Top10 partition과 metric을 evaluation-only로 구현한다.
5. `VERIFY` — code/input/instruction/model freeze 뒤 공식 inference를 한 번 실행하고 output을
   봉인한 다음 Gold를 join한다.
6. `AUDIT / INTEGRATE` — 실행됐다면 Gate와 운영 비용을 감사한다. 차단된 현재 상태에서는
   blocker, Production/SEALED/scope와 문서 정합성만 감사하고 local commit을 남긴다.

## 현재 Gate

`ORIENT`, `SPEC`, `MODEL_SELECTION_GATE`까지 수행했다. 설치된 유일한 Ollama model은
embedding-only `bge-m3`이고, 과거 Qwen tag는 exact identity와 license를 고정할 수 없어
`BLOCKED_MODEL_SELECTION`이다. 계약에 따라 4~5단계와 공식 inference를 시작하지 않는다.
6단계는 blocker와 보존 경계 감사로만 제한한다.

## 재개 조건

- 하나의 local instruction model artifact에 대해 immutable digest/revision, license, size와
  Korean/English/mixed relation classification capability를 검증한다.
- 모델 취득·보관이 repository/dependency/Production을 바꾸지 않으며 별도 승인을 받는다.
- PRZ-030 Stress 후보는 Gold-bearing report에서 재사용하지 않고 동일 BGE digest로 한 번
  재생성해 기존 canonical freeze SHA와 exact parity를 확인한다.
- PRZ-031 Gold-free input에 query text, Top20 candidate identities, Top10 cutoff, model,
  instruction/schema/policy hash를 포함해 공식 inference 전에 봉인한다.
