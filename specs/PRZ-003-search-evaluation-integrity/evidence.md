# PRZ-003 검증 근거

## 현재 상태

- Source commit: 작업 중 — commit/push/PR은 이번 요청에 포함되지 않음
- GitHub Issue: 생성하지 않음 — 이번 요청에 외부 GitHub write 권한이 없음
- OpenSQL/OpenProxy/OpenHA: `NOT_RUN` — 이번 범위 아님

## 요구사항 매핑

| 요구사항 | 구현·테스트 근거 | 상태 |
|---|---|---|
| split 간 양성 근거 분리 | `SearchEvaluationDatasetLoader`, dataset loader/sample test | `PASS` |
| Direct MRR@20 정의와 JSON 필드 | `SearchEvaluationMetrics`, `directMrrAt20`, metrics test | `PASS` |
| 결과 파일 덮어쓰기 방지 | `SearchEvaluationReportWriter`, writer test | `PASS` |
| 개인 데이터 endpoint 보호 | `application-search-evaluation.yml`, 평가 문서 | `PASS` |
| 생성물 Git 제외 | `.gitignore`, `git status --ignored` | `PASS` |

## 실행 결과

- `./gradlew.bat test --no-daemon`: `PASS` — 245 tests, 0 failures, 0 errors, 14 skipped.
- `docker compose config --quiet`: `PASS` — Compose syntax and interpolation 확인.
- `git diff --check`: `PASS`.
- Markdown local link/code fence/trailing whitespace: `PASS` — tracked Markdown과 PRZ-003 spec 범위만 검사.
- `./gradlew.bat searchEvaluation --no-daemon`: `PASS` — Docker Desktop 29.6.2에서 Testcontainers PostgreSQL 16.14·pgvector와 로컬 Ollama `bge-m3`를 실제 사용했다. 새 JSON은 `directMrrAt20`을 직렬화하고 legacy `mrr` 키를 포함하지 않는다. 30문항 전체 Direct MRR@20 `0.8551`, 분리 TEST 10문항 Direct MRR@20 `0.7917`, nDCG@5 `0.8494`, p95 `1005ms`를 기록했다.
- OpenSQL/OpenProxy/OpenHA: `NOT_RUN` — 이번 PostgreSQL 기반 기준선 재실행은 호환성 근거가 아니다.

이 PostgreSQL·Ollama 기준선은 OpenSQL 호환성 근거가 아니다.

## 독립 감사

- 1차 감사: `FAIL` — spec ID 중복, machine-readable MRR 의미 불명확,
  reranker model cache ignore 누락, 역사 기록의 지표명 충돌을 확인했다.
- 1차 보완: 검색 평가를 PRZ-002로 재번호화하고 JSON 필드를 `directMrrAt20`으로
  변경했으며 model cache ignore와 역사 기록을 정정했다.
- 2차 감사: `FAIL` — PRZ-002가 clean-clone demo에 이미 예약돼 있어 ID 충돌이
  남아 있음을 확인했다.
- 2차 보완: OpenSQL `PRZ-001`과 clean-clone demo `PRZ-002` 예약을 보존하고
  검색 평가 작업만 사용되지 않은 `PRZ-003`으로 재번호화했다.
- 최종 재감사: `PASS` — OpenSQL `PRZ-001`, clean-clone demo `PRZ-002`,
  검색 평가 `PRZ-003`의 현재·계획·역사 문맥이 일관되며 새 blocking finding이 없다.
- 현재 상태: 필수 검증과 독립 재감사를 통과해 `VERIFIED`. commit·push·PR은
  사용자의 별도 승인 전까지 수행하지 않는다.
