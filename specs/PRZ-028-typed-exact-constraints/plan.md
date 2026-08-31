# PRZ-028 Plan

- 상태: `IN_PROGRESS / INPUT_FROZEN / IMPLEMENTATION_VERIFIED / OFFICIAL_T0_T1_NOT_RUN`
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
