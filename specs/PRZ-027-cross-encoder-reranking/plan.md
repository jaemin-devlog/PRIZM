# PRZ-027 Plan

- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT(evaluation-only) → VERIFY → AUDIT → INTEGRATE(local only)`
- Production, dataset, SEALED FINAL, PR/push/merge: 변경·실행 금지

1. PRZ-026 B3 HEAD와 parser/child/passage SHA-256, model/code metadata와 Gate를 결과 전에 고정한다.
2. Java가 기존 B3 run에서 full baseline report와 Gold 없는 Dense Top20 pair input을 분리 export한다.
3. Python은 고정 model/code revision으로 input pair만 score하고 runtime/model file hash를 출력한다.
4. Java import는 pair identity와 candidate/provenance parity를 fail-closed 검증한 뒤 Top20만 재정렬한다.
5. 단위·mutation test와 dataset/SEALED validator 통과 후 source/test/contract를 input-freeze commit한다.
6. Original/Long-form/robustness export, CPU inference, import benchmark를 한 번 수행한다.
7. aggregate/slice/operation Gate와 모든 loss를 기록하고 local result commit으로 닫는다.

모델 또는 remote code를 exact revision으로 실행할 수 없으면 `BLOCKED`. 공식 실행 후에는 다른 모델,
instruction, TopK, batch, dtype를 시도하지 않는다.
