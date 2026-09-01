# PRZ-035 Tasks

- 상태: `IN_PROGRESS`

- [x] `refactor/search-v3` local/origin parity와 PRZ-034 ancestry 확인
- [x] Production 단건 embedding과 평가 전용 batch 경계 확인
- [x] 전체 corpus Child 241개와 Top5 고유 Child 227개 구분
- [x] A/B 정의, 결과 parity, 비용 산식과 판정 Gate 고정
- [x] 평가 전용 A/B simulator와 focused unit test 구현
- [ ] code freeze와 execution contract 확정
- [ ] 공식 BGE-M3 A/B 비교 1회 실행
- [ ] result/metric/provenance parity 검증
- [ ] 색인·query·저장량·반복 검색 projection 기록
- [ ] 문서 새 버전과 CPU/GPU/self-hosted 경계 분석
- [ ] SEALED/diff/OSS audit
- [ ] 최종 판정과 local commit/origin push

Production/DB/dependency/frontend/MCP/Docker 변경, SEALED FINAL 검색, cache와 새 검색 기능은
`NOT_RUN`이다.
