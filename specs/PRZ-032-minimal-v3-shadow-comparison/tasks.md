# PRZ-032 Tasks

- 상태: `VERIFIED / MIXED_NEEDS_NEXT_CAPABILITY`

- [x] PRZ-025~031 local HEAD, ancestry, worktree와 origin 부재를 확인했다.
- [x] PRZ-025~031을 force/rebase 없이 동일 HEAD로 push-only backup했다.
- [x] PRZ-031 검증 HEAD에서 PRZ-032 branch를 생성했다.
- [x] 실제 Production V2와 Minimal V3의 허용 구성·비범위를 고정했다.
- [x] Gold-after-output, canonical dedup, candidate/final metric 분리를 고정했다.
- [x] Gold-free DEV/CAL input loader와 source/config freeze 구현
- [x] 실제 Production V2 source + owner/ACTIVE-scoped evaluation JDBC adapter 구현
- [x] Minimal V3 B3/Typed Selection output 구현
- [x] source span Gold join, metric/slice/classification 구현
- [x] focused integrity/unit test
- [x] code/input/model freeze와 official comparison 1회
- [x] output SHA 검증 뒤 Gold join 및 aggregate evidence 기록
- [x] Production/SEALED/diff/OSS audit
- [ ] local commit과 PRZ-032 branch push-only backup

`CURRENT_FRESH_BASELINE=NOT_RUN`은 SEALED FINAL에서 유지한다. official DEV/CAL shadow가
완료돼도 SEALED FINAL baseline 실행으로 재라벨링하지 않는다.
