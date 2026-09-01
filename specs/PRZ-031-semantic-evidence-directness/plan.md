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

D1은 첫 response의 strict relation/reasonCode pair validation 실패로 output freeze와 Gold
join 전에 fail-closed했고 `PROTOCOL_NO_GO / SEMANTIC_QUALITY_NOT_EVALUATED`로 보존한다.
D2는 동일 model/instruction/config/candidate/ranking에서 output을 relation 단일 필드로만
줄인다. 16-pair generic conformance는 16/16 strict parse/schema, enum/extra/malformed 0으로
`PROTOCOL_V2_PASS`다. 다음 Gate는 code/contract를 commit으로 동결하고 D1 candidate
payload를 V2 envelope로 재봉인하는 것이다.

## 공식 실행 조건

- 고정한 Qwen artifact의 local manifest/blob identity를 실행 직전 재검증한다.
- PRZ-030 Stress 후보는 Gold-bearing report에서 재사용하지 않고 동일 BGE digest로 한 번
  재생성해 기존 canonical freeze SHA와 exact parity를 확인한다.
- PRZ-031 Gold-free input에 query text, Top20 candidate identities, Top10 cutoff, model,
  instruction/schema/policy hash를 포함해 공식 inference 전에 봉인한다.
- code/contract는 local commit으로, ignored local candidate/input은 CREATE_NEW와 file/canonical
  SHA-256으로 고정한 뒤 한 번의 official inference만 허용한다. output을 검증·봉인하기
  전에는 Gold supplier를 호출하지 않는다.
- D1 candidate/input hash를 먼저 검증하고 candidate payload를 그대로 V2 contract에
  재봉인한다. candidate retrieval과 BGE-M3는 재실행하지 않는다.
- V2 conformance report를 CREATE_NEW로 한 번 생성하고 PASS hash를 official runner의
  선행 조건으로 강제한다.
- D2 output/marker/report는 D1과 다른 local 경로를 사용한다.
