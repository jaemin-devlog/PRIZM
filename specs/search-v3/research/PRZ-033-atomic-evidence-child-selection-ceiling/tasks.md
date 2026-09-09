# PRZ-033 Tasks

- 상태: `VERIFIED / BUILD_CHILD_SELECTOR`

- [x] PRZ-032 HEAD/origin/working tree와 artifact/report hash를 확인했다.
- [x] PRZ-033 branch를 PRZ-032 검증 HEAD에서 생성했다.
- [x] LOCAL_CHILD_ORACLE과 배타적 failure-stage 계약을 고정했다.
- [x] 결과 전 Capability Gate 수치를 고정했다.
- [x] Gold-free B3 Passage/EvidenceChild identity replay와 candidate freeze 구현
- [x] Gold-after-candidate guard와 Child-only stable partition 구현
- [x] failure-stage, F0/O_CHILD metric/slice/typed/safety report 구현
- [x] focused unit/integrity test 및 code freeze
- [x] official ceiling 1회 실행과 aggregate evidence 기록
- [x] Production/SEALED/diff/OSS audit
- [x] local commit

실제 Child Selector, BGE 재실행, SEALED FINAL 검색은 `NOT_RUN`이다. 후속 단계는 같은 frozen
candidate에서 실제 atomic Child Selector를 독립 ablation해야 한다.
