# PRZ-016 GPT-J1 Evidence Judge Shadow Spike Evidence

- 상태: `DONE — NO_GO`
- 시작 source: `4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e`
- Production 적용: 없음

## 시작 기준선

| 검증 | 결과 |
|---|---|
| unit | `PASS` — 521 tests, failure/error 0, conditional skip 16 |
| Docker daemon | `PASS` — Docker Desktop 29.7.2, Linux/x86_64 |
| integration | `FAIL` — 113 tests, 104 pass, 8 skip, 기존 P4 상태 회귀 1 fail |

실패는 `PRIZM API`/`PRIZM- API` 경계에서 expected `NO_EVIDENCE`, actual
`NO_RELEVANT_RESULTS`인 기존 P4 strong-identifier 조기 종료다. P6에 기록된 실패와 같으며
GPT-J1 변경 전부터 존재한다.

## OpenAI 문서 확인

- Responses API Structured Outputs는 `text.format`의 `json_schema`와 `strict=true`를 지원한다.
- API 입력은 명시적으로 opt in하지 않는 한 모델 학습에 사용되지 않는다.
- 기본 abuse-monitoring log는 고객 content를 포함할 수 있고 최대 30일 보존될 수 있다.
- `/v1/responses`에는 `store=false`를 명시하며, ZDR은 승인된 조직·project의 별도 설정이다.

## 구현·검증 결과

### 구현

- `src/searchEvaluation`에만 JDK `HttpClient` 기반 Responses API client와 P5 비교 runner를
  추가했다. OpenAI SDK나 새 dependency는 추가하지 않았다.
- request는 `store=false`, strict JSON Schema와 질문·`chunkId`·extractive `snippet`만 사용한다.
  model은 `gpt-5-mini-2025-08-07`, 출력 상한은 1200 token이다.
- 선택 ID가 제출 Top10인지 먼저 확인하고, JDBC에서 chunk/version/document owner 일치,
  document의 현재 active version, version `ACTIVE`, DB 원문 exact substring을 모두 확인한다.
- full query diagnostic은 gitignored `local/gpt-evidence-judge/results.json`에만 쓰고 일반 로그에는
  snippet, 판정 reason, response body를 출력하지 않는다.
- P4 final API 결과는 baseline으로만 읽고, GPT 후보는 기존 owner-scoped ACTIVE dense Top20의
  순서를 보존한 Top10에서 만든다. Production 요청 경로에는 연결하지 않았다.
- project의 request-rate limit을 넘지 않도록 live client의 HTTP 시도 간격을 21초로 제한했다.
  이 pacing은 evaluation source에만 있고 production latency나 요청 경로에는 영향이 없다.

### 검증

| 검증 | 결과 |
|---|---|
| focused judge unit | `PASS` — 11 tests, failure/error/skip 0 |
| 전체 unit | `PASS` — 532 tests, failure/error 0, conditional skip 16 |
| Docker integration | `FAIL` — 113 tests, 104 pass, 8 skip, 기존 P4 상태 회귀 1 fail |
| Shadow Gradle task 구성 | `PASS` — `gptEvidenceJudgeShadow --dry-run` |
| P5 dataset SHA-256 | `PASS` — `4e28c0fb2b99b31f15640eb39776e04a533938b63db02e1ba0bfec22168532aa` |
| P5 ground truth SHA-256 | `PASS` — `da915300974c27967e859c0586ec1f76347c314c62f0ca57f77b5b64c3e0180d` |
| Production search source | `PASS` — 30 files, aggregate `32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31` |
| Production·runtime·frontend diff | `PASS` — 0 files |
| `git diff --check` | `PASS` |
| real API key pattern scan | `PASS` — 발견 0 |
| live P5 48-query | `DONE` — 48 attempted, 44 completed calls, 4 incomplete |
| API key 잔존 | `PASS` — parent process·`.env`·tracked source 모두 없음 |
| raw local result | gitignored, SHA-256 `6c164e19696af5e067e04d5320bf47610e0bf2ef12549bf8b5d9feeb1dd43533` |

integration의 유일한 실패는 시작 기준선과 같은
`PgVectorInfrastructureTest.optInProfileRequiresAnAssertedCompletedReleaseClaimInPostgreSql`이다.
GPT-J1이 추가한 source는 integration source set에 포함되지 않으며 새 실패는 없다.
첫 전체 unit 실행은 실제 `.env`의 Docker CORS origin이 MCP test의 기본
`http://localhost:5173`과 달라 기존 MCP test 1건이 403으로 실패했다. `.env`를 바꾸지 않고
test process에만 기본 origin을 명시한 targeted 재실행과 전체 532개 재실행은 모두 통과했다.

### Live 비교 결과

실행 시각은 `2026-08-16T08:51:30.618243100Z`이고, raw 결과는 민감 snippet과 reason을
포함하므로 gitignored `local/gpt-evidence-judge/results.json`에만 보관한다.

| 지표 | 현재 P4 | P4 + GPT Judge |
|---|---:|---:|
| Top1 accuracy | 50.00% | 66.67% |
| Recall@3 | 61.11% | 66.67% |
| Recall@5 | 61.11% | 66.67% |
| MRR@5 | 55.09% | 66.67% |
| Negative FPR | 25.00% | 0.00% |

Judge는 하나만 선택하므로 Judge의 Top1·Recall@3·Recall@5·MRR@5 값이 같다. 4개 positive
query가 `max_output_tokens`로 완료되지 않아 positive 지표는 4건을 miss로 센 diagnostic
lower bound이며 완전한 48-query 성능 추정치로 사용하지 않는다. Negative 12건에는 API
오류가 없었고 모두 false positive를 만들지 않아 Negative FPR 0%는 관측됐다.

| 실행 진단 | 결과 |
|---|---:|
| completed calls | 44 / 48 |
| server verification `ACCEPTED` | 29 |
| model `NONE` | 15 |
| server verification rejection | 0 |
| judge incomplete | 4 — H13, H33, H34, H35 |
| completed positive regression | 2 — H15, H31 |
| input tokens | 33,884 |
| output tokens | 22,916 |
| call latency avg / median / P95 | 19.90s / 20.11s / 22.57s |

H15와 H31은 GPT가 반환한 문장이 제출 snippet과 owner-scoped ACTIVE DB 원문에 실제로
존재해 서버 검증은 통과했지만 frozen ground truth의 근거 chunk와 달랐다. 두 query 모두
chunk 91을 선택했고 candidate rank는 각각 10, 4였다. frozen 정답 chunk는 각각 98과
95/96이다. 즉 서버 재검증은 위조 ID·다른 owner·inactive version·원문에 없는 문장을 막지만,
원문에 존재하는 다른 문장의 의미 적합성까지 보증하지는 못한다.

### 최종 판정

`NO_GO`

핵심 목표인 Negative FPR 25% → 0%는 달성했다. 그러나 정상 완료 결과에서도 positive 회귀
2건이 남았고 4건은 출력 한도로 완료되지 않아 `positive 회귀 0`, `judge 오류 0` gate를 모두
실패했다. 미완료 4건을 재실행해도 이미 확인된 H15/H31 회귀 때문에 GO 조건은 회복되지 않으므로
추가 API 호출을 중단했다. 새 unseen holdout을 만들지 않고 GPT Judge를 production에 적용하지
않으며, 검색 연구를 여기서 종료하고 실제 PRIZM 기능 개발로 돌아간다.

raw result를 만든 당시 runner는 judge error가 있으면 `NOT_VERIFIED`로 직렬화했다. 하지만 이
live 실행은 시작되지 않은 환경 부족이 아니라 48건 실행을 완료한 결과이고 정상 완료 positive
회귀도 이미 존재하므로 acceptance criterion에 따라 audit 판정은 `NO_GO`다. 현재 evaluation
source는 같은 조건에서 `NO_GO`를 반환하도록 바로잡았으며, 판정을 바꾸기 위한 추가 live 호출은
하지 않았다.
