# PRZ-033 Evidence

- 상태: `IN_PROGRESS / OFFICIAL_CEILING_NOT_RUN`
- 시작: `PRZ-032-minimal-v3-shadow-comparison@7e9c1361ca47a06a3957e62fdc34e9793c2a9863`
- Production 변경: `0`

## 시작 확인

- local/origin PRZ-032 parity: `7e9c1361ca47a06a3957e62fdc34e9793c2a9863`
- 시작 working tree: `CLEAN`
- output file SHA-256: `647bf37eae00d5e8c9b909faf0767befeb69e2b31d77b36fa863d7cb2231b1f7`
- output canonical SHA-256: `d6b29ce518f9571f7313a92feb7e1d8ac8b4b207d2fb7dc7fa0f8527dfc414a4`
- report SHA-256: `29af223023a50564aaf276261459b60eb521c3fcd37045588248b0907ffd8847`
- BGE-M3 digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- official PRZ-032 재실행: `0`
- PRZ-033 official ceiling: `NOT_RUN`

첫 code-freeze `016eb6ac1e6fbb50c7ce0fd16420362ceb24345c`의 official test body는 candidate
freeze/verify와 Gold join 뒤 첫 Oracle relation에서 중단됐다. 원인은 benchmark Gold Parent ID
(`SV3-U03-P01`)와 parser structural parent candidate ID
(`SV3-U03-D01-V01-SB-0001`)라는 서로 다른 namespace를 문자열로 비교한 validator 결함이다.
prediction/report/metric은 생성되지 않았고 BGE 실행은 `0`이다. candidate file은 CREATE_NEW로
보존하며, source-span parent containment 검증으로 수정한 새 code-freeze에서 hash parity 후
재사용한다. 이 시도는 `INVALID_PRE_RESULT_VALIDATOR_ATTEMPT`로 역사 보존한다.

PRZ-032 historical F0는 Direct-positive `85`, final Top1/MRR/nDCG@5/Recall@5
`0.5412/0.7576/0.7942/0.9882`, user-macro Top1/MRR `0.5880/0.7827`, typed selected
Evidence precision `0.6316`이다. 이는 아직 PRZ-033 결과가 아니다.

## 현재 검증 상태

- candidate Passage/EvidenceChild identity replay: `NOT_RUN`
- Gold-after-candidate guard: `NOT_RUN`
- LOCAL_CHILD_ORACLE: `NOT_RUN`
- failure-stage distribution: `NOT_RUN`
- Capability 판정: `NOT_RUN`

SEALED FINAL은 combined SHA-256
`e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`, manifest SHA-256
`d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa`, git tree
`a129080861d7dafd32a9b3b3357b61aebb237e59`, `opened=false`, `searchExecuted=false`,
`CURRENT_FRESH_BASELINE=NOT_RUN`이다.
