# PRZ-016 Search Performance V2 Evidence

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
| GPT-J1 | GPT Evidence Judge shadow: Negative FPR 0%이나 완료 positive 회귀 2건·incomplete 4건; `NO_GO` |

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
- GPT-J1 shadow 평가: [evidence](gpt-evidence-judge-shadow/evidence.md)

## 관리 구조 정리 당시 확인 결과

- production code 변경: 0
- 검색·API·평가 데이터 변경: 0
- 재번호화 전 branch 기준 `PRZ-014` OpenHA 문서: 당시에는 확인되지 않았으며, 이후 최신
  `main`의 공식 Registry에서 별도 Spec으로 확인됨
- 내부 문서 경로: 새 Phase 구조로 갱신

## 현재 Phase 상태

P0·P1·P2·P3·P4는 `DONE`이며 해당 deterministic 검색 구현은 채택했다. P5와 P7-B는
`DONE — FAIL`, P6·GPT-J1과 후속 rule/NLI/Qwen/Hybrid shadow는 `DONE — NO_GO`다.
P7-B는 frozen v2 corpus와 질문 48개를 독립 실행해 Top1 33.33%, Recall@5 58.33%,
Negative FPR 41.67%를 기록했다. Owner와 ACTIVE version 격리는 통과했지만 일반화 Gate는
실패했다. P7-B는 앞으로 diagnostic/historical 자료로만 보존하고 추가 tuning에 사용하지 않는다.

현재 PRZ-016 상태는 `DEFERRED / PRZ_016_STATE_FROZEN`이다. 재개 조건은 P7-B를 재사용하지
않는 `FRESH_GENERALIZATION_EVALUATION_V2`다. P5 이후 실험은 production 검색에 연결하지
않았으며, PR 생성 전 감사에서 확인된 완료 경험 질의의 빈 결과 상태 계약만 별도로 교정했다.

## Spec ID 충돌 해소

2026-08-16에 최신 `main`이 `PRZ-013` OpenProxy, `PRZ-014` OpenHA, `PRZ-015` MCP를
공식 Registry에서 사용 중임을 확인했다. 이 검색 작업은 아직 `main`에 병합되지 않았으므로
다음 빈 ID `PRZ-016`으로 재번호화했다. 파일 경로와 현재 문서·실행 코드 참조만 바꾸고,
frozen dataset/ground truth/raw result 내부의 역사적 phase·benchmark 라벨과 측정값은
변경하지 않았다.

## P7-A Cross-Document Dataset Freeze

- 결과: `DATASET_FROZEN — READY_FOR_INDEPENDENT_RUN`
- 합성 사용자 4명, ACTIVE 문서 8개(TXT 4/PDF 4), 질문 48개(Positive 36/Negative 12)
- P0/P5 exact·normalized duplicate 0, 명백한 near duplicate 0
- corpus SHA-256:
  `4ad8a564bee353c877a9c0938d6cf1f866d6d824750b01c5bf5f976b71a25ae1`
- questions SHA-256:
  `7e1055db772034e0d7257de781944c9d5ba368888b5d1757baea7f864fcab957`
- ground truth SHA-256:
  `cbb78a66f3563ab82d86702220c409a3674ce2a6f5db1ad366795d085e6186f9`
- production 변경 0, 검색·benchmark·GPT Judge `NOT_RUN`
- 상세: [P7-A evidence](p7-cross-document-generalization/evidence.md)

## P7-A v2 Document Density Replacement

P7-A v1은 검색 실행 전에 보존했지만 PDF가 1페이지 요약 카드 수준이라 실제 이력서의 문서 밀도와
주변 정보가 부족했다. v1 27개 frozen asset은 hash mismatch 0으로 그대로 보존하고
`SUPERSEDED_BEFORE_RUN` 처리했다.

- v2 결과: `DATASET_FROZEN — USED_BY_P7-B`
- PDF 이력서 4개 × 2페이지, 장문 TXT 포트폴리오 4개
- 질문 48개(Positive 36/Negative 12), Positive anchor 67개와 Negative 부재 12개 검증
- P0/P5/v1 exact·normalized duplicate 및 threshold 초과 near duplicate: 0
- corpus SHA-256:
  `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498`
- questions SHA-256:
  `85c2e41bba5c293ca5172b48f77f41587d49be996252479ce5a71ed17763b868`
- ground truth SHA-256:
  `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680`
- production 변경 0, 검색·benchmark·GPT Judge `NOT_RUN`
- 상세: [P7-A v2 evidence](p7-cross-document-generalization-v2/evidence.md)

## P7-B Independent Generalization

- 입력: P7-A v2 frozen corpus·questions·ground truth
- 실행: 48/48, Positive 36 / Negative 12
- Top1: 33.33%
- Recall@5: 58.33%
- Negative FPR: 5/12, 41.67%
- Owner isolation: `PASS`
- ACTIVE version isolation: `PASS`
- 최종 판정: `P7-B FAIL`
- 후속 정책: `DIAGNOSTIC / HISTORICAL DATASET`, further tuning `NOT_ALLOWED`
- 상세: [P7-B evidence](p7-b-independent-generalization/evidence.md)

## 종료 상태

- 채택: P1 Numeric + Strong Identifier, P2 Evidence-Aware Reranking,
  P3 Query Understanding, P4 Evidence Localization
- 비채택: Hybrid/FTS/RRF/Sparse, GPT Judge, rule-based rejection,
  NLI model swap, Qwen 4B verifier와 fail-closed semantic filtering
- 현재 상태: `DEFERRED / PRZ_016_STATE_FROZEN`
- 재개 조건: `FRESH_GENERALIZATION_EVALUATION_V2`

## PR 생성 전 최종 검증

- 완료 경험 질의의 identifier guard 빈 결과를 기존 `NO_EVIDENCE` 계약에 맞췄다.
  검색 순위, threshold, retrieval과 P7-B frozen 입력은 변경하지 않았다.
- `SearchServiceTest`: 29개 PASS
- 전체 unit: 533개 중 실패 0, skip 16
- 전체 integration: 113개 중 실패 0, skip 8
- frontend lint와 production build: PASS
- Docker Compose config: PASS
- P7-A v2 frozen asset 31개, v1 manifest와 corpus/questions/ground truth hash: PASS
- Markdown 로컬 링크와 `git diff --check origin/main`: PASS
- 검색 benchmark와 모델 inference: `NOT_RUN` (state freeze 이후 재실행 금지)
