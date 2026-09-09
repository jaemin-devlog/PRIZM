# PRZ-016 P17 PRIZM Dataset Plan

- 현재 Gate: `IMPLEMENT·VERIFY·AUDIT complete`, `INTEGRATE NOT_RUN`

## 최소 변경

- `src/test/resources/search-evaluation/prizm-v1/`에 dataset card, fictional fact matrix,
  generated corpus/questions와 freeze manifest를 둔다.
- `scripts/generate-prizm-search-evaluation-dataset.mjs`는 고정 fact matrix를 schema v2로
  결정적으로 렌더링하고 `--check`에서 byte drift를 실패시킨다.
- `PrizmCareerEvidenceDatasetTest`가 기존 loader, split selector와 Production `800/120`
  chunker를 사용해 수량·qrel·격리·privacy·hash 계약을 검사한다.
- evaluation-only `SearchEvaluationBaselineTest`의 frozen TEST ID allowlist에 새 dataset ID를
  추가하고 기존 명시적 환경변수 gate는 유지한다.
- parent PRZ-016 registry와 검색 평가 사용 문서에는 P17의 실제 상태와 실행 방법만 반영한다.

## 데이터 생성 순서

1. A/B/C 각 cohort에 서로 겹치지 않는 TUNING 12개와 TEST 8개의 허구 career fact를 fact
   matrix에 고정한다.
2. 각 fact를 구체 프로젝트 문서와 cohort별 낮은 관련도의 요약 문서에 렌더링한다.
3. 모든 fact에 direct와 paraphrase 질문을 만들고, TXT/PDF 수가 같은 고정 subset에
   exact-value/PDF 질문을 추가한다. detail `2`, summary `1` 또는 exact query의 summary `0`
   qrel을 기록한다.
4. cohort·split별 8개 negative fixture에서 plan/theory/altered/absent/owner/version/
   no-searchable 질문을 생성해 각 split의 positive/no-evidence 수를 같게 맞춘다.
5. page-text PDF는 gold page를, boundary fixture는 Production overlap에 두 번 포함되는 짧은
   anchor를 갖는다.
6. corpus/questions/dataset card의 canonical LF bytes와 SHA-256 manifest를 생성한다.
7. 생성이 끝난 한국어 문장을 `$humanize-korean` 기본 강도로 검토한다. 보호 목록을 먼저
   고정하고, 새 사실을 만들거나 qrel을 바꾸지 않은 국소 교정만 fact matrix에 반영한다.

## 검증 명령

```powershell
node scripts/generate-prizm-search-evaluation-dataset.mjs --check
.\gradlew.bat test --tests "com.prizm.search.evaluation.PrizmCareerEvidenceDatasetTest" --tests "com.prizm.search.evaluation.SearchEvaluationDatasetLoaderTest" --tests "com.prizm.search.evaluation.SearchEvaluationDatasetSelectorTest" --no-daemon --rerun-tasks
.\gradlew.bat test --no-daemon
node scripts/verify-sbom.mjs
node scripts/verify-oss-readiness.mjs
git diff --check
git status --short --branch
```

TEST retrieval은 데이터 생성 검증에 필요하지 않고 holdout을 소비하므로 이번 단계에서 실행하지
않는다. 이후 최종 방식 비교에서만 아래처럼 명시적으로 한 번 실행한다.

```powershell
$env:PRIZM_SEARCH_EVALUATION_SPLIT='TEST'
$env:PRIZM_SEARCH_EVALUATION_ALLOW_FROZEN_TEST='true'
$env:PRIZM_SEARCH_EVALUATION_PROFILE='current-product'
$env:PRIZM_SEARCH_EVALUATION_CHUNKING='production'
.\gradlew.bat searchEvaluation --tests "com.prizm.search.evaluation.SearchEvaluationBaselineTest" --no-daemon --rerun-tasks -PsearchEvaluationDataset=src/test/resources/search-evaluation/prizm-v1 -PsearchEvaluationOutput=local/search-evaluation/prizm-v1-test
```

live evaluation에는 Docker, PostgreSQL·pgvector와 로컬 Ollama `bge-m3`가 필요하다. 실행하지
않은 검사는 `NOT_RUN`으로 기록하고 PostgreSQL 결과를 OpenSQL 근거로 확대하지 않는다.
현재 evaluation-only FTS의 `plainto_tsquery('simple', 전체 자연어 질문)`은 모든 lexeme를
AND로 묶는다. 이 데이터셋과의 실행 적합성은 `NOT_VERIFIED`이므로 Hybrid/RRF 실행 전에
질의 구성 방식과 lexical candidate 회수 Gate를 별도로 사전 등록한다.

현재 generator `--check`와 dataset·loader·selector focused test 24건은 `PASS`다. TEST
질문 파일은 hard-coded SHA-256
`c07e105023287663542601133e82fbfa78f3a341e75efc326989a0aadcc63600`로 고정했다.
`$humanize-korean` 로컬 작성 감사도 문장 항목 576개·보호 요소 619개, 보호 요소 변경 0건·새 사실
추가 0건으로 `PASS`다. 전체 backend unit, SBOM·OSS readiness와 diff 검사도 `PASS`했고,
독립 AUDIT의 blocking finding은 0건이다. 실제 TEST 검색과 INTEGRATE는 `NOT_RUN`이다.

## 중단 조건

- 기존 frozen asset, `src/main`, migration 또는 frontend diff가 생기면 중단한다.
- 실제 개인 정보나 외부 dataset 파생 문구가 발견되면 해당 내용을 동결하지 않고 제거 근거를
  기록한 뒤 다시 감사한다.
- split 누수, positive anchor 누락, 잘못된 PDF page, primary ACTIVE chunk 150 미만 또는 hash
  drift가 있으면 dataset을 PASS로 표시하지 않는다.
- 생성 결과를 보고 현재 검색 점수가 좋아지도록 TEST 사실·질문·qrel을 조정하지 않는다.
