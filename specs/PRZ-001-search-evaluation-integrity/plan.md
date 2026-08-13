# PRZ-001 — 검색 평가 기준선 정합성 Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** PRZ-001 구현 전 검색 평가 source
>
> 구현 전 계획을 보존한다. 실제 결과는 [Tasks](tasks.md)와
> [Evidence](evidence.md)를 따른다.

## P1. Dataset split 정합성

- 목표: TUNING과 TEST 사이의 양성 근거 재사용을 차단한다.
- 변경 범위: `SearchEvaluationDatasetLoader`와 합성 `questions.jsonl` fixture.
- 검증: relevance 1·2 근거 중복을 거부하고 relevance 0 hard negative 반복은
  허용하는 단위 테스트를 실행한다.
- Rollback: loader와 fixture 변경을 함께 되돌린다.
- 중단 조건: 실제 개인 평가 data를 읽거나 수정해야 하면 중단한다.

## P2. Metric과 결과 보존

- 목표: Direct MRR@20 정의와 결과 파일 고유성을 명확히 한다.
- 변경 범위: `SearchEvaluationMetrics`, `SearchEvaluationReportWriter`, 평가 전용
  Ollama endpoint와 관련 문서·test.
- 검증: 직접 근거 질문만 분모에 포함하고 `directMrrAt20`을 기록하며, 같은 시각의
  실행 결과가 덮어써지지 않는지 확인한다.
- Rollback: metric·writer·설정 변경을 함께 되돌린다.
- 중단 조건: 생산 검색 순위·score·후보 수를 바꿔야 하면 중단한다.

## P3. 검증과 통합

- 목표: 단위·실환경 평가와 문서 감사를 완료한다.
- 변경 범위: test, 평가 문서, ignore와 Spec Evidence.
- 검증: 전체 unit test, 가능한 경우 PostgreSQL·pgvector·Ollama
  `searchEvaluation`, Markdown 링크와 `git diff --check`를 실행한다.
- Rollback: 검증 실패 시 `VERIFIED`로 기록하지 않는다.
- 중단 조건: PostgreSQL 결과를 OpenSQL 결과로 확대해야 하면 중단한다.

## 공통 위험과 대응

- 생산 source·config·API·frontend·schema와 Flyway migration은 수정하지 않는다.
- `local/`, `outputs/`, Python 환경·cache와 reranker model cache는 삭제하지 않고
  Git 제외만 확인한다.

## Dependency 및 license 영향

- dependency와 배포 경계 변경은 없다.

## Branch와 통합 경계

- 과거 branch·commit의 옛 PRZ 번호 표기는 실제 이력으로 보존한다.
- canonical ID만 실제 착수 순서인 PRZ-001로 유지한다.

## 계획 대비 주요 변경

- 1차 감사에서 JSON 필드, model cache ignore와 과거 지표 표기를 보완했다.
- source·merge commit과 최종 수치는 [Evidence](evidence.md)에 기록한다.
