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
6. `AUDIT / INTEGRATE` — Gate와 운영 비용을 감사하고 Production/SEALED/scope와 문서
   정합성을 확인한 뒤 local commit만 남긴다.

## 현재 Gate

code/contract/input/model identity를 동결하고 official inference를 한 번 시작했다. 첫 response가
strict relation/reasonCode pair validation을 통과하지 못해 output freeze와 Gold join 전에
fail-closed했다. 같은 dataset 재실행, prompt/model/policy 수정과 다른 모델 시험은 하지 않는다.
PRZ-031 판정은 `NO_GO`이며 Evidence Selection 통합 단계로 진행하지 않는다.

## 공식 실행 조건

- 고정한 Qwen artifact의 local manifest/blob identity를 실행 직전 재검증한다.
- PRZ-030 Stress 후보는 Gold-bearing report에서 재사용하지 않고 동일 BGE digest로 한 번
  재생성해 기존 canonical freeze SHA와 exact parity를 확인한다.
- PRZ-031 Gold-free input에 query text, Top20 candidate identities, Top10 cutoff, model,
  instruction/schema/policy hash를 포함해 공식 inference 전에 봉인한다.
- code/contract는 local commit으로, ignored local candidate/input은 CREATE_NEW와 file/canonical
  SHA-256으로 고정한 뒤 한 번의 official inference만 허용한다. output을 검증·봉인하기
  전에는 Gold supplier를 호출하지 않는다.
