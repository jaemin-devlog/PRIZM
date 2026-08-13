# PRZ-001 — 검색 평가 기준선 정합성 Tasks

> **현재 상태:** `VERIFIED`

## P1. Dataset split 정합성

- [x] split 간 양성 근거 중복을 독립 점검했다.
- [x] 양성 근거 split 분리와 loader validation을 추가했다.
- [x] 합성 30문항의 20/10 split과 category 분포를 보존했다.

## P2. Metric과 결과 보존

- [x] Direct MRR 분모와 `directMrrAt20` 출력 필드를 교정했다.
- [x] 평가 endpoint와 결과 파일 고유성을 보강했다.
- [x] 생성물과 model cache의 Git 제외를 확인했다.

## P3. 검증과 통합

- [x] 전체 unit test와 split·metric·writer test를 실행했다.
- [x] PostgreSQL·pgvector·Ollama `searchEvaluation`을 실행했다.
- [x] Markdown 링크, code fence와 `git diff --check`를 검증했다.
- [x] 1차 감사 finding을 보완하고 독립 재감사를 통과했다.
- [x] 실제 PR·source·merge 기록을 [Evidence](evidence.md)에 남겼다.
