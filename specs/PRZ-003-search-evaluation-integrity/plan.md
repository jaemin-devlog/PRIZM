# PRZ-003 계획

## 변경 경로

- `SearchEvaluationDatasetLoader`: 양성 fixture evidence의 split 간 재사용 차단
- 샘플 `questions.jsonl`: TUNING/TEST 배치를 바꿔 양성 근거를 분리
- `SearchEvaluationMetrics`: 직접 근거 질문만 Direct MRR@20 분모로 사용하고 JSON 필드를 `directMrrAt20`으로 명시
- `SearchEvaluationReportWriter`: timestamp 뒤 run token을 붙여 파일 덮어쓰기 방지
- `application-search-evaluation.yml`: 평가 전용 Ollama endpoint를 localhost로 고정
- 단위 테스트와 평가 문서: 변경 계약과 과거 수치의 해석을 반영

## 영향과 제외

- Flyway migration, production source/config, API, frontend, PostgreSQL schema는 변경하지 않는다.
- 검색 결과의 순서·점수·후보 수를 바꾸지 않는다.
- `local/`, `outputs/`, Python virtual environment·cache와 reranker model cache는 ignore만 추가하며 삭제하지 않는다.
- 데이터셋은 합성 fixture만 수정한다. 실제 개인 평가 데이터는 열거나 변경하지 않는다.

## 검증 계획

1. 검색 평가 관련 단위 테스트와 전체 unit test를 실행한다.
2. Docker·PostgreSQL·pgvector·Ollama가 준비된 경우에만 `searchEvaluation`을 별도로 실행한다.
3. Markdown 링크, code fence, `git diff --check`와 ignore 결과를 점검한다.

## Git 계획

- 임시 브랜치: `PRZ-003-search-evaluation-integrity`
- Issue, commit, push, PR은 이번 사용자 요청에 포함되지 않으므로 생성하지 않는다.
