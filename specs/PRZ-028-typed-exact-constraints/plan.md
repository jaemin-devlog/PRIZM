# PRZ-028 Plan

- 상태: `IN_PROGRESS / STRESS_1.1.0_INPUT_FROZEN`
- 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT(evaluation-only) → VERIFY → AUDIT → INTEGRATE(commit only)`
- baseline: PRZ-026 B3; PRZ-027 Cross Encoder `NO_GO` 제외

## 1. 입력 freeze

1. 기존 DEV/CAL의 category와 gold constraint count만 확인하고 검색 결과는 보지 않는다.
2. 부족한 유형을 채우는 synthetic stress fixture 24문항을 별도 version으로 materialize한다.
3. schema/count/lineage/source span/per-file hash/combined hash와 SEALED metadata 불변을 검증한다.
4. stress fixture와 PRZ-028 계약을 local input-freeze commit으로 먼저 고정한다.

## 2. 구현 순서

1. evaluation-only value model과 deterministic query constraint parser를 만든다.
2. candidate `sourceText` observation extractor와 source offset 검증을 만든다.
3. `SATISFIED/CONTRADICTED/UNKNOWN` evaluator를 만든다.
4. truncation 전 full B3 raw Dense ranking에서 gold-free source-only input view를 만들고
   candidate-preserving stable partition을 적용한다.
5. gold를 runtime 입력과 분리한 metric/report calculator와 DEV/CAL-only runner를 만든다.
6. raw report는 ignored `local/search-v3-evaluation/prz028/`에만 저장한다.
7. official runner와 판정 Gate를 source와 함께 local code-freeze commit으로 고정한다. Runner는 실제
   clean `HEAD`가 전달된 freeze SHA와 같은 경우에만 실행한다.

## 3. 검증

- unit: operator/value/unit/qualifier, percentage direction, date precision/range,
  identifier-number, literal normalization과 세 match 상태
- invariants: no-constraint exact parity, candidate identity parity, stable order, source offsets,
  gold runtime non-use, full-ranking guard와 SEALED semantic path guard
- integrity: stress materializer `--check`, PRZ-025 validator와 관련 PRZ-025/026 tests
- official evaluation: 입력 freeze commit 이후 T0/T1 한 번 실행, query/type/user aggregate와 latency 기록
- operation: query/candidate parsing 및 전체 T1 추가 latency, heap 관찰값, persistent index/storage 0 기록
- verdict: predicted hard-negative state는 안전성, Gold-expected `CONTRADICTED@1` 감소는 개선으로 분리하고
  qualifier/date/identifier-number family와 distinct winning user/kind를 확인
- audit: `git diff --check`, OSS readiness, forbidden scope/secret/SEALED hash 검사

Full backend integration, frontend와 Docker 검증은 evaluation-only 변경에 필요하지 않으면
`NOT_RUN`으로 기록한다.

## 4. 중단과 복구

Stress Set 봉인 뒤 fixture/gold 또는 parser/ranking policy 의미 변경이 필요하면 현재 결과를
`HISTORICAL_RESULT`로 보존하고 새 version과 freeze가 필요하다. semantic parity, candidate identity,
source provenance, Production/SEALED 불변 중 하나라도 실패하면 결과 판정 전에 중단한다.

PR, push, merge와 Sparse 실험은 실행하지 않는다.

## 5. 공식 결과와 다음 조정 Gate

- code freeze: `2e9c9ff2fb21744a6fea9b8bcf03962e392c84f8`
- official T0/T1: DEV/CAL 4 suite, 1회 실행, `NEEDS_ADJUSTMENT`
- 유지된 Gate: candidate/semantic parity, Recall, nDCG, latency envelope, persistent storage 0
- 미측정: exact additional heap (`NOT_MEASURED`)
- 관찰된 순증: typed hard-negative Gold-expected `CONTRADICTED@1` `7→1`
- 미충족 Gate: direct rank improvement, distinct winning users/kinds, qualifier mismatch improvement

따라서 현재 freeze에서 추가 튜닝이나 공식 재실행을 하지 않는다. 다음 작업이 parser·gold·stress 구성의
의미를 바꾸면 기존 결과를 historical로 유지하고 새 version/input/code freeze를 만든다. PRZ-028의
조정 또는 비채택 결정을 먼저 끝내며 Sparse 실험은 `NOT_RUN`을 유지한다.

## 6. Final adjustment 실행 순서

1. 결과를 보지 않고 Stress 1.1.0의 6 bundles/24 queries, distribution, reason label과 역할 Gate를
   spec에 사전 등록한다.
2. Stress 1.1.0 materializer와 입력만 작성하고 schema-contract/Gold/span/lineage/inventory/hash,
   overwrite guard와 SEALED metadata 불변을 검증한 뒤 input-only commit으로 봉인한다.
3. Grounded qualifier를 바꾸지 않는 comparison-token compatibility와 status/reason 분리 API를
   evaluation-only 코드에 구현한다.
4. Stress 1.0.1과 1.1.0을 독립 hash로 함께 읽는 loader, 다섯 suite 분리 report, 역할 Gate와
   dataset-global `CREATE_NEW` official-run claim 및 atomic terminal output guard를 구현한다.
5. 모든 non-BGE unit/regression, materializer, scope와 Final guard를 통과한 source/config/model/K/policy를
   code-freeze commit으로 봉인한다. K는 `ALL_OWNER_SCOPED_B3_PASSAGES`, ranking은 state-only stable
   partition으로 고정한다.
6. Input/code freeze가 일치하는 Stress 1.1.0 T0/T1 BGE를 단 한 번 실행한다. 기존 네 suite는
   regression으로만 별도 보고한다.
7. 사전 Gate를 기계적으로 적용해 `RANKING_COMPONENT`, `EVIDENCE_VALIDATION_ONLY`, `DROP` 중 하나를
   결정하고 문서·scope를 감사해 local commit한다. PR/push/merge와 Sparse는 실행하지 않는다.
