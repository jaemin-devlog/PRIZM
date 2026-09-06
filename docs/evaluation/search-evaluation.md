# 검색 품질 평가

이 도구는 `bge-m3` Dense 후보 검색과 선택한 평가 profile을 측정합니다. 현재 제품
`POST /api/career-evidence/search`의 최대 5개 결과 계약과 검색 순위는 변경하지 않습니다.
날짜별 측정 결과와 당시 판단은
[검색 평가 실행 이력](../archive/evaluation/search-evaluation-history.md)에 보존합니다.

## 데이터 위치와 형식

- 추적 가능한 합성 예제: `src/test/resources/search-evaluation/sample/`
- PRZ-008 Dataset v2.2(보존): `src/test/resources/search-evaluation/v2/`
- PRZ-008 Dataset v2.3(현재 TUNING): `src/test/resources/search-evaluation/v2-3/`
- PRZ-016 P17 전용 합성 Dataset v1.0: `src/test/resources/search-evaluation/prizm-v1/`
- 실제 개인 평가 데이터: `local/search-evaluation/<dataset>/`
- 실행 결과 기본 위치: `local/search-evaluation/results/`

`local/search-evaluation/`은 Git에서 제외됩니다. 실제 이력서 원문, 질문, 이메일, 지원 회사 정보, JWT나 실행 결과를 저장소에 커밋하지 않습니다.

데이터셋 디렉터리에는 두 파일이 필요합니다.

- `corpus.json`: `datasetId`와 합성 문서 목록
- `questions.jsonl`: 한 줄에 질문 하나

문서는 `fixtureId`, `title`, 실제 `DocumentType`, `fileType`, `pages`, `evidenceAnchors`를 가집니다. `evidenceAnchors`는 DB의 가변적인 chunk ID 대신 원문 내 고유한 짧은 문자열에 안정적인 `fixtureEvidenceId`를 부여합니다.

질문 필드:

- `questionId`, `query`, `split`, `category`, `noEvidence`
- `expectedEvidence[]`: `fixtureEvidenceId`, `relevance`, `evidenceGroupId`
- relevance `2`: 직접 근거, `1`: 부분 근거, `0`: 무관·hard negative

`noEvidence=true` 질문에는 relevance 1 또는 2를 넣을 수 없습니다. 같은 근거가 overlap 청크 여러 개에 포함되면 같은 `evidenceGroupId`로 중복률을 측정합니다.

Dataset v2는 `schemaVersion: 2`를 명시하고 다음 메타데이터를 추가합니다.

- 문서: `split`, anchor별 `sourceFactId`
- 질문: `fixtureIds`, `questionGroupId`, `ownerScenario`, `versionScenario`
- PDF 직접 근거 질문: `goldPage`

v2 loader는 문서·`evidenceGroupId`·`questionGroupId`·`sourceFactId`가 TUNING과
TEST에 걸쳐 재사용되는 경우와 정규화한 동일 질문을 거부합니다. 또한 owner·과거
version 경계 질문은 `noEvidence=true`여야 하며, PDF 직접 근거의 gold page가 실제
anchor 위치와 일치해야 합니다.

추적되는 파일럿 데이터는 가상 문서 11개와 질문 30개로 구성됩니다. 질문 구성은 기술·도구 8개, 문제 해결 6개, 협업·역할 4개, 수치·고유 표현 6개, 실제 근거 없음 6개입니다. 기술명만 같은 문서와 수치가 다른 문서를 포함한 hard negative 질문은 11개입니다.

`split`은 다음 용도로만 사용합니다.

- `TUNING` 20개: 향후 임계값이나 후보 수를 결정할 때만 사용
- `TEST` 10개: 결정이 끝난 뒤 최종 비교에만 사용

동일한 정규화 질문은 두 split에 들어갈 수 없습니다. 의미가 같은 패러프레이즈와 같은 원문 근거를 묻는 질문이 split 사이에 반복되지 않는지도 파일럿 작성 시 수동 검토했습니다. `TEST` 결과를 보고 임계값이나 라벨을 다시 맞추지 않습니다.
양성 expected evidence(`relevance` 1 또는 2)는 split 사이에 반복될 수 없으며 로더가 이를 실행 전에 차단합니다. `relevance` 0 hard negative의 반복은 허용합니다.

## PRZ-008 Dataset v2.2와 v2.3

`prizm-search-evidence-synthetic-v2.2`는 기존 파일럿을 재라벨링하지 않고 별도로 추가한
합성 Dataset입니다. TUNING 15문항과 TEST 10문항이며, 각 split은 서로 다른 문서와
원문 사실을 사용합니다. 일반·유사 주제 무근거, 없는 회사·자격증·기술, 바뀐 역할·수치,
다른 사용자 문서, 과거 version, 검색 가능한 문서가 없는 사용자, 직접 근거,
paraphrase, 날짜·숫자·고유명사, PDF gold page와 overlap 중복 사례를 포함합니다.

v2.1은 실제 문서에서 관찰한 실패 구조만 합성한 백엔드 이력서·포트폴리오 2개와 TUNING
5문항을 추가합니다. 정답 질문, 오타 질문, 동일 PDF 페이지의 overlap 중복과 유사 주제
무근거 질문을 포함합니다. 실제 프로젝트명·개인정보·원문·성과 수치는 포함하지 않았습니다.
v2.2는 overlap 경계에 걸린 직접 근거 anchor를 보완하되 질문·split은 바꾸지 않습니다.
기존 TEST 10문항은 줄 단위 SHA-256
`6eeeffed3a93b53edbc474e8a57f2eba6b627c6f4358cbafdc7b2f0b2b29fce9`로 고정했습니다.

`prizm-search-evidence-synthetic-v2.3`은 v2.2를 덮어쓰지 않고 별도 경로에 추가한
교정 Dataset입니다. 질문 25개와 TUNING·TEST split, relevance, evidence group,
gold page는 v2.2와 byte 단위로 같습니다. 변경된 원문은 합성 Atlas PDF의 gold page
한 곳뿐이며, 해당 페이지 본문에 `합성 Atlas 장애 기록`이라는 정확한 문서명을 포함해 제목을
보지 않아도 질문의 대상과 완료 사실을 함께 확인할 수 있게 했습니다. 결합 SHA-256은
`f1bf3cffd1cc51d7c5f972e55fe99a8afe9dce45e403ef742a7e3d0b25bb7f9f`입니다.

v2·v2.1·v2.2·v2.3의 날짜별 측정 결과와 profile 채택 과정은
[검색 평가 실행 이력](../archive/evaluation/search-evaluation-history.md)에서 확인합니다.

## PRIZM 전용 합성 Dataset v1.0

`prizm-career-evidence-synthetic-v1.0`은 기존 v2.3을 덮어쓰지 않고 schema version 2로
추가한 PRIZM 전용 평가셋입니다. 프로젝트·식별자·source fact가 겹치지 않는 A/B/C 세
cohort로 합성 문서 114개와 질문 300개를 구성합니다. TUNING은
`180=90 positive+90 no-evidence`, 동결 TEST는 `120=60 positive+60 no-evidence`입니다.
12개 `DocumentType`과 schema v2의 15개 평가 category를 모두 포함합니다. positive 가운데
60개는 프로젝트명·식별자·ASCII 기술명·수치를 뺀 한국어 `PARAPHRASE` 질문입니다.

고정 fact matrix를 corpus와 questions로 렌더링하는 결정적 generator와 SHA-256 freeze
manifest를 함께 둡니다. 아래 명령은 생성 파일이 fact matrix와 generator에서 다시 만든
canonical bytes와 같은지 확인합니다.

```powershell
node scripts/generate-prizm-search-evaluation-dataset.mjs --check
```

TEST 질문 파일의 hard-coded SHA-256은
`c07e105023287663542601133e82fbfa78f3a341e75efc326989a0aadcc63600`입니다.
`humanize-korean` 기본 강도 로컬 작성 감사에서는 문장 항목 576개와 보호 요소 619개를 대조했고,
보호 요소 변경과 새 경력 사실 추가는 각각 0건으로 `PASS`했습니다. generator drift 검사와
dataset·loader·selector focused test 24건, 전체 backend unit, SBOM·OSS readiness와 최종
diff 검사도 `PASS`했습니다. 독립 AUDIT의 blocking finding은 0건입니다.

일부 B/C 사실은 같은 검색 위험을 다른 기술과 맥락으로 바꾼 평행 시나리오군입니다. 세
cohort나 같은 fact의 질문 변형을 통계적으로 독립한 표본이라고 가정하지 않습니다. 전체 수치와
함께 cohort·평행 시나리오군·fact/question group별 결과와 정확한 오류 건수를 봐야 합니다.

현재 evaluation-only PostgreSQL FTS는 자연어 질문 전체를 `simple` 구성의 AND 조건으로
바꿉니다. 한국어 조사와 질문 종결어까지 모두 일치해야 해 이 데이터셋과의 실행 적합성은
`NOT_VERIFIED`입니다. Hybrid/RRF 비교 전에 FTS 질의 구성 방식을 별도로 사전 등록하고,
lexical candidate가 실제로 회수되는지 먼저 확인해야 합니다.

TEST retrieval은 holdout을 보존하려고 이번 생성 단계에서 `NOT_RUN`으로 유지합니다. 실제
PDF ingestion, OpenSQL/OpenProxy와 Production Dense·PostgreSQL FTS+Dense RRF·Cross-encoder
Reranker·chunking 비교도 `NOT_RUN`입니다. 따라서 지금 확인된 내용은 dataset의 구조와
격리 계약이며, 특정 검색 방식이 더 낫다는 성능 근거가 아닙니다. 세부 계약과 남은 Gate는
[PRZ-016 P17 Spec](../../specs/PRZ-016-search-performance-v2/p17-prizm-dedicated-dataset/spec.md)을
따릅니다.

## 실행

Docker Desktop, PostgreSQL·pgvector Testcontainer와 로컬 Ollama `bge-m3`가 필요합니다.
평가 profile은 기본적으로 `http://localhost:11434`만 사용합니다. 다른 endpoint가 필요한 경우 일반 `.env`가 아니라 `PRIZM_SEARCH_EVALUATION_OLLAMA_BASE_URL`을 명시해야 합니다.

```powershell
.\gradlew.bat searchEvaluation
```

개인 로컬 데이터셋을 지정할 때:

```powershell
.\gradlew.bat searchEvaluation -PsearchEvaluationDataset=local/search-evaluation/my-dataset
```

결과 위치를 바꾸려면 `-PsearchEvaluationOutput=<local-path>`를 추가합니다.

Dataset v2는 split을 명시하지 않으면 실행을 거부합니다. 기존 sample Dataset의 기본
실행 계약은 유지합니다. TUNING 실행은 현재 PowerShell 프로세스에 다음 환경변수를
설정합니다.

```powershell
$env:PRIZM_SEARCH_EVALUATION_SPLIT = 'TUNING'
$env:PRIZM_SEARCH_EVALUATION_PROFILE = 'source-dedup-evidence-signals-v1'
.\gradlew.bat searchEvaluation --no-daemon `
  -PsearchEvaluationDataset=src/test/resources/search-evaluation/v2 `
  -PsearchEvaluationOutput=local/search-evaluation/prz008-tuning-baseline
Remove-Item Env:PRIZM_SEARCH_EVALUATION_SPLIT
Remove-Item Env:PRIZM_SEARCH_EVALUATION_PROFILE
```

선택기는 질문과 corpus 문서를 같은 split으로 함께 제한합니다. 다른 사용자 전용 fixture는
별도 합성 owner로 저장하고, 과거 version fixture는 `active_version_id`에 연결하지 않아
제품과 동일한 owner·ACTIVE 검색 조건 밖에 둡니다.

평가기는 합성 corpus를 현재 `TextChunker`와 실제 임베딩으로 색인하고, 프로덕션 `SearchService`의 top 5와 동일 owner·ACTIVE 조건의 평가 전용 top 20 순서가 일치하는지 확인합니다.

## 결과

- 요약과 질문별 결과: `dense-baseline-<UTC timestamp>-<run token>.json`
- 임계값 분석용 원시 후보: `dense-baseline-candidates-<UTC timestamp>-<run token>.csv`

전체 원문은 결과에 복사하지 않습니다. JSON에는 질문, 예상 근거, 반환 chunk ID,
relevance 순서, 검색 상태, top1 score·distance, 사용자 반환 수, 후보 수와 분리 지연이
기록됩니다. CSV에는 이 값과 후보 rank, source type·index, relevance,
`evidenceGroupId`, 평가 profile을 기록합니다.

한 보고서의 모든 결과에는 다음 profile 중 하나를 명시합니다.

- `CURRENT_PRODUCT`: 현재 제품 동작을 그대로 측정
- `EVALUATION_THRESHOLD`: TUNING에서 검토할 평가용 판정 profile
- `EVALUATION_COMPOSITE`: 출처 중복 축약과 비점수 근거 신호를 결합한 TUNING profile

평가용 profile 결과를 제품 threshold나 현재 제품 동작으로 표현하지 않습니다.

지표는 전체, TUNING, TEST, category별로 구분합니다.

| 지표 | 계산 계약 |
|---|---|
| Recall@20 | relevance 1 이상 근거가 있는 질문에서 top-20 hit 비율 |
| Direct Recall@20 | relevance 2가 있는 질문에서 top-20 직접 근거 hit 비율 |
| Precision@5 | top-5 relevance 1 이상 결과 수를 항상 5로 나눈 질문별 평균 |
| Direct Precision@5 | top-5 relevance 2 결과 수를 항상 5로 나눈 질문별 평균 |
| Direct MRR@5·@20 | relevance 2가 있는 질문만 분모로 하며 cutoff 안 첫 직접 근거의 역순위. 평가용 거부 시 0 |
| nDCG@5 | `2^relevance - 1` gain을 사용하고 같은 `evidenceGroupId`의 두 번째 결과부터 gain 0 |
| 중복 결과 비율 | 사용자에게 반환된 top-5 안에서 이미 나온 evidence group이 다시 나온 비율 |
| 무관 질문 거부율 | 검색 가능한 문서가 있는 no-evidence 질문 중 `NO_EVIDENCE` 비율 |
| 근거 질문 오거부율 | relevance 1 이상 근거 질문 중 `NO_EVIDENCE` 비율 |
| top-1 직접 근거 정확도 | relevance 2가 있는 질문 중 첫 반환 결과가 relevance 2인 비율 |
| PDF page 인용 정확도 | PDF 직접 근거 질문 중 첫 직접 근거의 `PAGE` index가 gold page와 일치한 비율 |
| 결과 개수 | 질문별 사용자 반환 수와 평가용 후보 수의 min·평균·max |

Precision@5는 결과가 5개보다 적어도 분모를 5로 유지합니다. 직접 정답 하나만
label한 질문의 최대 Precision@5는 0.2이며, 이는 오류가 아니라 고정 분모 계약입니다.
`NO_SEARCHABLE_DOCUMENTS`는 무관 질문 거부율에서 제외하고 별도 질문 수와 상태 정확도로
집계합니다. 근거 있음/없음 질문의 top1 score·distance 분포도 계속 별도로 기록합니다.

지연은 nearest-rank 방식의 p50·p95와 평균을 함께 기록합니다. `embedding`은 Ollama
요청 시작부터 벡터 검증 종료, `DB`는 JDBC 호출 직전부터 row mapping 종료,
`total`은 embedding 시작부터 DB mapping 종료까지입니다. 기존
`averageSearchTimeMillis`와 `p95SearchTimeMillis`도 total 지연의 호환 필드로 유지합니다.
JSON의 `directMrrAt20`은 PRZ-001 교정 이전 결과의 `mrr` 필드와 직접 비교하지 않습니다.

합성 파일럿 기준선은 실제 개인 문서나 서비스 전체 검색 성능을 보장하지 않습니다. 이 수치는 이후 Reranker, Hybrid Search, 청킹 실험의 비교 기준입니다. 현재 score에 운영 임계값을 적용하거나 정답 확률로 해석하지 않습니다.
