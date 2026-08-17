# P0: Search Benchmark / Baseline

> 역사 보존: 이 Phase는 초기 `PRZ-014 Search Benchmark V2` 경로에서 생성됐다.
> 2026-08-14 관리 구조 정리로 Search Performance V2의 P0로 이동했고, 상위 Spec은
> 2026-08-16 공식 Registry 충돌 해소를 위해 PRZ-016으로 재번호화했다.
> dataset·ground truth·baseline 결과는 변경하지 않았다.

## 목적

현재 production 검색 기준선을 실제 USER의 이력서와 포트폴리오 ACTIVE version으로
측정한다. 검색 결과를 본 뒤 정답을 수정하지 않으며, 이번 작업에서는 검색 production
코드를 변경하지 않는다.

## 범위

- 검색 전에 72개 질의와 ground truth를 고정한다.
- 정상 로그인과 owner-scoped API만 사용한다.
- 평가 시작 후 문서 업로드, 재색인, version 변경을 하지 않는다.
- Positive의 Top1, Recall@3, Recall@5, MRR@5와 Negative false-positive rate를 계산한다.
- 상태와 latency를 기록하고 모든 실패를 지정 taxonomy로 분류한다.

## 동결 계약

SearchService, VectorSearchRepository, SearchTokenNormalizer, 자연어 fallback, P4, P18,
threshold 0.50, Top20, max5, ranking weight, bge-m3, chunking, Evidence Expansion,
snippet selector를 변경하지 않는다.

## 평가 자산

- `evaluation-dataset.json`: 검색 전에 확정한 ground truth
- `baseline-results.json`: production API 원응답의 개인정보·전체 원문을 제외한 평가 기록
- `failure-analysis.md`: 지표, 실패 taxonomy와 root-cause 가설
