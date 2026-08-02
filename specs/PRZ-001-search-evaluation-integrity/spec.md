# PRZ-001 — 검색 평가 기준선 정합성

## 상태

`VERIFIED`

## 평가 범위

이번 교정은 평가 데이터가 실제로 독립적인지와 지표가 문서의 정의와 같은지를
검증 가능하게 만든다. 실제 OpenSQL, 검색 알고리즘, Reranker, Hybrid Search, 운영
API와 프런트엔드는 범위에 포함하지 않는다.

## 문제와 시나리오

1. 평가 작성자가 `TUNING` 결과로 후보 수나 임계값을 정한 뒤 `TEST` 결과를 비교한다.
   양성 근거(`relevance` 1 또는 2)가 두 split에 걸쳐 반복되면 로더는 입력을 거부해야
   한다. `relevance` 0 hard negative의 반복은 독립성 침해로 간주하지 않는다.
2. 직접 근거가 있는 질문과 부분 근거·무근거 질문이 함께 있을 때 `Direct MRR@20`은
   직접 근거(`relevance` 2)가 있는 질문만 분모로 사용해야 한다.
3. 같은 초에 두 평가가 실행되어도 서로의 JSON·CSV 결과를 덮어쓰지 않아야 한다.
4. 개인 문서를 넣는 평가 profile은 일반 `.env`의 Ollama endpoint를 상속하지 않고,
   명시적 평가 전용 환경변수가 없으면 `localhost`만 사용해야 한다.

## 보존 계약

- 평가 task는 현재 dense 검색, owner·ACTIVE 조건, top 5·20 검증을 계속 사용한다.
- 실제 개인 데이터와 결과는 Git에 커밋하지 않는다.
- 생산 검색 SQL, Flyway V1~V13, 문서·버전 처리, Cleanup Worker 동작은 변경하지 않는다.
- PostgreSQL 성공을 OpenSQL 호환성 성공으로 표현하지 않는다.

## 완료 조건

- split 간 양성 근거 중복을 거부하는 단위 테스트가 통과한다.
- 샘플 30문항이 20/10 split과 기존 category 분포를 유지하면서 로더를 통과한다.
- Direct MRR 분모를 검증하는 혼합 질문 단위 테스트가 통과한다.
- 동일 시각의 결과 파일이 run token으로 구분되는 단위 테스트가 통과한다.
- 문서가 split 규칙, Direct MRR 정의, 로컬 Ollama 기본값과 과거 지표의 비비교성을
  정확히 설명한다.
