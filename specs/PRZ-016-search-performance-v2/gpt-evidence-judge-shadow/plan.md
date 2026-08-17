# PRZ-016 GPT-J1 Evidence Judge Shadow Spike Plan

## 예상 변경

- `src/searchEvaluation/java/com/prizm/search/evaluation/judge/`
  - 최소 request/response record
  - JDK `HttpClient` 기반 Responses API client
  - 제출 후보와 DB 원문을 대조하는 fail-closed verifier
- `src/searchEvaluation/java/com/prizm/search/evaluation/GptEvidenceJudgeShadowBenchmarkTest.java`
  - P5 48-query current P4 baseline과 GPT-J1 비교 runner
- `src/test/java/com/prizm/search/evaluation/judge/`
  - strict request, response parse, refusal/error, forged output, 원문 검증 단위 테스트
- `build.gradle`
  - 명시적으로 실행하는 `gptEvidenceJudgeShadow` evaluation task
- `specs/PRZ-016-search-performance-v2/gpt-evidence-judge-shadow/`
  - 계약·계획·민감정보 없는 evidence

새 dependency는 추가하지 않는다. Java 17 `HttpClient`와 현재 Jackson/JdbcTemplate만 사용한다.
따라서 SBOM·license·redistribution 경계는 바뀌지 않는다.

## 실행 설정

- 필수 secret: process environment의 `OPENAI_API_KEY`
- 선택 model: `OPENAI_EVIDENCE_JUDGE_MODEL`, 기본 고정 snapshot
  `gpt-5-mini-2025-08-07`
- 요청 간격: `OPENAI_EVIDENCE_JUDGE_MIN_REQUEST_INTERVAL_MILLIS`, 기본 `21000`
  (Shadow 평가가 project request-rate limit을 넘지 않도록 HTTP 재시도까지 동일하게 적용)
- endpoint: `https://api.openai.com/v1/responses`
- tracked 파일이나 `.env`에 키를 작성하지 않는다.
- full diagnostic 결과는 gitignored `local/gpt-evidence-judge/`에만 쓴다.

기본 snapshot은 well-defined structured classification에 맞는 비용 민감형 모델이고 Responses API와
Structured Outputs를 지원한다. model ID, token usage와 latency를 결과에 기록해 재현성을 보존한다.

## 구현 순서

1. 순수 decision·candidate·verified result 계약을 작성한다.
2. strict schema와 최소 payload를 만드는 client를 구현한다.
3. transient HTTP 오류만 최대 2회 재시도하고 그 외 오류·refusal·incomplete는 명시적 실패로 둔다.
4. 제출 후보 membership과 owner·ACTIVE·원문 exact match verifier를 구현한다.
5. P5 frozen 입력과 P4 source hash를 확인하는 read-only runner를 구현한다.
6. baseline P4 final result와 Q0 dense Top10 judge result의 지표를 각각 산출한다.
7. offline focused/unit, 전체 unit, Docker integration을 실행한다.
8. key가 있을 때만 live 48-query task를 실행하고 결과를 audit한다.

## 보안·ownership

- SQL은 chunk/version/document owner 일치와 현재 ACTIVE version을 한 번에 확인한다.
- model이 반환한 ID·문장은 DB 검증 전에는 결과로 사용하지 않는다.
- prompt·response raw body와 snippet은 tracked evidence나 일반 로그에 출력하지 않는다.
- HTTP Authorization header와 API key는 예외 메시지에 포함하지 않는다.
- DB, corpus, migration과 production scheduler는 변경하지 않는다.

## 검증

```powershell
.\gradlew.bat test --tests 'com.prizm.search.evaluation.judge.*' --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
.\gradlew.bat gptEvidenceJudgeShadow --no-daemon --rerun-tasks
git diff --check
```

Docker integration의 알려진 P4 state 회귀 1건은 baseline으로 분리한다. 새 실패가 생기면
IMPLEMENT로 돌아간다. live task에 key가 없으면 실패를 숨기지 않고 `NOT_RUN`으로 기록한다.

## Rollback·중단

- 평가 전용 source/task/spec 변경만 제거하면 rollback되며 production rollback은 없다.
- P5 freeze, P4 source, owner·ACTIVE 또는 최소 payload 경계가 달라지면 live API 호출 전에 중단한다.
- API response가 strict schema로 파싱되지 않거나 DB 검증을 우회하면 `NO_GO`다.
- 실험 성공 후에도 새 unseen holdout 승인 전 production 설계를 시작하지 않는다.
