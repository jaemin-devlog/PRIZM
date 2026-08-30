# PRZ-025 Search V3 Foundation Plan

- 상태: `IN_PROGRESS`
- 이 Phase의 허용 단계: `ORIENT → SPEC → PLAN → IMPLEMENT(docs only) → VERIFY → AUDIT → INTEGRATE(commit only)`
- Production 구현·PR·main merge: `NOT_RUN`

## 1. 이번 Phase

1. 최신 `origin/main`, local `main`, `v1.0.0` tag/Release, working tree와 중복 PRZ/branch/PR을
   실제 Git/GitHub 상태로 확인한다.
2. 필수 기준 문서, PRZ-001/008/012/016/017/022/024와 현재 Production source를 읽는다.
3. source와 문서가 충돌하면 source를 frozen baseline으로 쓰고 불일치만 기록한다.
4. Evidence, Query, Answerability, split, fresh benchmark, gold, metric, adoption 계약을 쓴다.
5. Registry에 PRZ-025를 `IN_PROGRESS`로 추가한다.
6. 허용 diff와 문서 정합성을 검증하고 독립 audit 후 허용 파일만 commit한다.

## 2. 후속 benchmark materialization 순서

### Gate A — data governance

- Synthetic, Public/Redistributable, Consented Real Data별 공급·license·privacy manifest를 만든다.
- 실제 개인 원문은 Git 밖의 승인된 저장소만 사용한다.
- Consented Real Data가 있으면 익명화, 동의, 접근 권한, retention과 삭제 절차를 먼저 승인한다.

### Gate B — bundle과 split 선할당

- 문서와 query를 나누기 전에 user, version, template/generator lineage, source fact, project/company
  identifier를 leakage group으로 만든다.
- group-aware assignment로 `DEV`, `CALIBRATION`, `SEALED_FINAL_TEST`를 정한다.
- 같은 user bundle, version family, paraphrase/source fact는 split을 넘지 않는지 자동·수동 검토한다.

### Gate C — source-grounded gold

- parser output이 아니라 원문 fixture에서 Source Block, Evidence Unit/Parent/Group와 span을 annotation한다.
- required aspect, answerability, support relation, actor/state/entity/numeric/date 제약을 기록한다.
- 두 명 이상 또는 독립 adjudication 절차의 구체 방식은 data 공급 전 정하되, seal 전 disagreement와
  acceptable alternative evidence를 해결한다.
- `expectedChunkId`만 있는 gold는 final package로 승인하지 않는다.

### Gate D — final seal

- corpus, source fixtures, query, gold, split manifest, schema, evaluator와 metric definition의
  version/hash를 고정한다.
- role/document/language 분포와 leakage audit 결과를 기록한다.
- independent evaluator만 `SEALED_FINAL_TEST` content와 key에 접근한다.
- 이 Gate가 끝날 때까지 주요 V3 algorithm implementation을 시작하지 않는다.

### Gate E — calibration과 숫자 Gate freeze

- `DEV/CALIBRATION`에서만 runner, threshold, K, model, parser/chunk, fusion/reranker와 operational
  profile을 조정한다.
- Current Search의 fresh final 결과를 미리 보지 않는다.
- quality regression tolerance와 operational budget을 근거와 함께 `FROZEN_GATE`로 바꾼다.

### Gate F — ablation과 finalist freeze

- 한 번에 한 구조/검색 구성만 추가하고 직전 단계 대비 user-macro, worst-group, safety와 cost를 비교한다.
- source revision, model artifacts, dependency/runtime, parser/search config와 hardware profile을 동결한다.
- 순증이 없는 candidate는 finalist에서 제거한다.

### Gate G — independent final comparison

- 동일한 sealed bundle과 comparable cohort에서 frozen Current Search와 frozen finalist를 실행한다.
- 실행 순서·hardware·indexing 상태·failure handling을 동일하게 하고, system별 unsupported ingestion은
  search-quality 분모와 분리한다.
- per-stage candidate/rejection/rank/localization trace와 aggregate/user/group metric을 hash와 함께 보존한다.
- Hard Safety, Search Quality, Operational Gate를 순서대로 판정한다.

Final 결과를 본 뒤 어느 구현·설정·Gate라도 바꾸면 해당 run은 `HISTORICAL_RESULT`가 되고
새로운 independent `SEALED_FINAL_TEST`가 필요하다.

## 3. 후보 ablation 원칙

- PRZ-026/027/028은 사용자 제시 sequencing label일 뿐 Registry ID를 선점하지 않는다.
- Parent-Child, Dense, Sparse, Exact/Typed, RRF/fusion, Cross Encoder, QueryPlanner, LLM은 모두
  candidate다.
- 각 experiment는 hypothesis, 단일 변경점, frozen input, quality delta, group regression,
  operation delta, stop rule을 실행 전에 쓴다.
- 과거 FAIL/NO_GO 후보를 다시 실험할 때는 기존 실험과 달라진 조건을 사전에 명시한다.
- `Quality Gain / Operational Cost`가 불리하면 default 구성에서 제외한다.

## 4. Artifact 보관 정책 제안

PRZ-025 폴더에는 계약, 작은 manifest/link와 검증 요약만 둔다. 대규모 raw result, model output,
PDF/PNG corpus와 반복 log를 이 폴더에 누적하지 않는다.

후속 선택지는 `OPEN_DECISION`이다.

1. license가 허용된 synthetic/public fixture는 별도 versioned benchmark tree와 작은 manifest/hash로 관리한다.
2. 대규모 raw run은 Git 밖의 access-controlled artifact store에 두고 repository에는 schema,
   content hash, source revision, command, environment, retention과 immutable URI만 기록한다.
3. 개인정보 가능성이 있는 fixture/result는 Git에 넣지 않고 별도 privacy policy를 적용한다.

어떤 방식을 택해도 artifact는 benchmark version, baseline commit, split/seal version, environment,
status와 limitation을 포함해야 한다.

## 5. Verification plan

- 새 문서 사이의 enum, split, gold, metric, gate와 status 표현을 교차 검토한다.
- 기존 V2 결과가 `HISTORICAL_RESULT`/regression/failure-analysis로만 쓰였는지 확인한다.
- `PROPOSED_TARGET`, `FROZEN_GATE`, `NOT_RUN`을 검색해 PASS 오표기가 없는지 확인한다.
- Registry 링크와 `IN_PROGRESS` 상태를 확인한다.
- `git diff --check`와 repository Markdown/OSS verifier를 합리적인 범위에서 실행한다.
- diff path를 allowlist와 비교하고 Production, migration, dependency, frontend, MCP, tag/Release 변경 0을 확인한다.
- 전체 backend/frontend/runtime CI는 docs-only diff와 무관하므로 실행하지 않으면 `NOT_RUN`과 이유를 남긴다.

## 6. 중단·rollback

- Production path나 허용 범위 밖 파일이 변경되면 commit하지 않고 원인을 조사한다.
- source와 계약의 해결되지 않은 모순, final leakage loophole, gold ambiguity가 남으면
  `NOT_SAFE_TO_START_NEXT_PHASE`로 종료한다.
- 문서 변경 rollback은 PRZ-025 새 파일과 Registry의 단일 행만 대상으로 한다. 기존 사용자 변경,
  tag, Release, history를 reset/rebase/force-push하지 않는다.
- INTEGRATE는 허용 파일의 local branch commit까지만 수행한다. push, PR, main merge는 금지한다.
