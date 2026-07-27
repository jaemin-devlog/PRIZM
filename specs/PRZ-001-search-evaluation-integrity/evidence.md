# PRZ-001 검증 근거

## 현재 상태

- Source commit: `36c8610` (`테스트: 검색 평가 정합성 강화`)
- GitHub Pull Request: [#11](https://github.com/jaemin-devlog/PRIZM/pull/11), 병합 완료
- Merge commit: `9e4d96f` (`검색 평가 정합성 강화 병합`)
- GitHub Issue: 생성하지 않음 — 작업 당시 외부 Issue 생성 권한이 없었음
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
- Markdown local link/code fence/trailing whitespace: `PASS` — tracked Markdown과 검색 평가 spec 범위만 검사.
- `./gradlew.bat searchEvaluation --no-daemon`: `PASS` — Docker Desktop 29.6.2에서 Testcontainers PostgreSQL 16.14·pgvector와 로컬 Ollama `bge-m3`를 실제 사용했다. 새 JSON은 `directMrrAt20`을 직렬화하고 legacy `mrr` 키를 포함하지 않는다. 30문항 전체 Direct MRR@20 `0.8551`, 분리 TEST 10문항 Direct MRR@20 `0.7917`, nDCG@5 `0.8494`, p95 `1005ms`를 기록했다.
- OpenSQL/OpenProxy/OpenHA: `NOT_RUN` — 이번 PostgreSQL 기반 기준선 재실행은 호환성 근거가 아니다.

이 PostgreSQL·Ollama 기준선은 OpenSQL 호환성 근거가 아니다.

## 독립 감사

- 1차 감사: `FAIL` — spec ID 중복, machine-readable MRR 의미 불명확,
  reranker model cache ignore 누락, 역사 기록의 지표명 충돌을 확인했다.
- 1차 보완: JSON 필드를 `directMrrAt20`으로 변경하고 model cache ignore와
  역사 기록을 정정했다.
- 병합 전 번호 정책은 미래 OpenSQL·clean-clone 작업에 `PRZ-001`·`PRZ-002`를
  미리 예약해 검색 평가를 `PRZ-003`으로 기록했다. 이 사전 예약은 외부 사용자가
  `PRZ-000` 다음의 실제 작업을 이해하기 어렵게 만들었다.
- 병합 후 정책 정정: PR #11과 `36c8610`·`9e4d96f`의 과거 표기는 보존하고,
  현재 canonical spec을 실제 착수 순서의 `PRZ-001`로 현행화했다. 앞으로는
  roadmap·계획에서 번호를 예약하지 않고 실제 `SPEC` 시작 시 다음 번호를 발급한다.
- 현재 상태: 코드 검증과 독립 재감사를 통과해 `VERIFIED`다. 이 번호 정책 정정은
  문서 전용 후속 작업이며 애플리케이션 코드·테스트·migration을 바꾸지 않는다.
