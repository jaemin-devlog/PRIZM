# PRZ-013 Search Performance V2 Evidence

관리 구조 정리일: 2026-08-14

## 역사 보존

이 PRZ는 기존 검색 성능 작업 문서를 Phase로 재배치한 관리 정리다. 평가셋, ground
truth, benchmark 결과, 실행 시점과 production 구현은 변경하지 않았다.

| Phase | 보존된 결과 |
|---|---|
| P0 | Top1 57.14%, Recall@3 66.07%, Recall@5 67.86%, MRR@5 0.6146, Negative FPR 6.25% |
| P1 | Top1 60.71%, Recall@3 69.64%, Recall@5 71.43%, MRR@5 0.6503, Negative FPR 0% |
| P2 | Top1 67.86%, Recall@3 71.43%, Recall@5 71.43%, MRR@5 0.6935, Negative FPR 0% |
| P3 | Top1 75.00%, Recall@3/5 78.57%, MRR@5 0.7649, Negative FPR 0%; Query Understanding 완료 |
| P4 | Top1 82.14%, Recall@3/5 85.71%, MRR@5 0.8363, Negative FPR 0%; Evidence Localization 완료 |
| P5 | 48-query holdout: Top1 50.00%, Recall@3/5 61.11%, MRR@5 0.5509, Negative FPR 25%; `FAIL` |
| P6 | PostgreSQL lexical+dense+RRF+literal gate shadow: H1 candidate recall 개선 0pp, H2 stress FPR 0%이나 72-query 회귀 5건; `NO_GO` |

- P0 자산: [dataset](p0-benchmark/evaluation-dataset.json),
  [baseline](p0-benchmark/baseline-results.json),
  [failure analysis](p0-benchmark/failure-analysis.md)
- P1 검증: [evidence](p1-numeric-identifier/evidence.md)
- P2 검증: [evidence](p2-evidence-reranking/evidence.md)
- P3 검증: [evidence](p3-query-understanding/evidence.md)
- P4 검증: [evidence](p4-evidence-localization/evidence.md)
- P5 최종 평가: [final validation](p5-final-holdout/final-validation.md)
- P6 shadow 평가: [56-item evidence](p6-retrieval-shadow/evidence.md),
  [authoritative raw result](p6-retrieval-shadow/p6-b-results.json)

## 관리 구조 정리 당시 확인 결과

- production code 변경: 0
- 검색·API·평가 데이터 변경: 0
- `PRZ-014` OpenHA 문서: 현재 Registry와 `specs/`에서 확인되지 않음
- 내부 문서 경로: 새 Phase 구조로 갱신

## 현재 Phase 상태

P0·P1·P2·P3·P4는 `DONE`이다. P5는 `DONE — FAIL`, P6는 `DONE — NO_GO`다. P6의 H1은
Dense보다 Candidate Recall@20을 개선하지 못했고 H2는 안전성 개선과 함께 positive regression을
만들었다. PRZ-013은 `IN_PROGRESS`를 유지하며 Search Performance V2는 동결하지 않는다.
production 검색 코드는 P5와 P6에서 변경하지 않았다.
