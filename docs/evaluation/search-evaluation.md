# 검색 품질 평가

이 도구는 현재 `bge-m3` Dense 검색을 기준선으로 측정합니다. 프로덕션 `POST /api/career-evidence/search`의 최대 5개 계약과 검색 순위는 변경하지 않습니다.

## 데이터 위치와 형식

- 추적 가능한 합성 예제: `src/test/resources/search-evaluation/sample/`
- PRZ-008 Dataset v2: `src/test/resources/search-evaluation/v2/`
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

## PRZ-008 Dataset v2

`prizm-search-evidence-synthetic-v2`는 기존 파일럿을 재라벨링하지 않고 별도로 추가한
합성 Dataset입니다. TUNING 10문항과 TEST 10문항이며, 각 split은 서로 다른 문서와
원문 사실을 사용합니다. 일반·유사 주제 무근거, 없는 회사·자격증·기술, 바뀐 역할·수치,
다른 사용자 문서, 과거 version, 검색 가능한 문서가 없는 사용자, 직접 근거,
paraphrase, 날짜·숫자·고유명사, PDF gold page와 overlap 중복 사례를 포함합니다.

기존 `sample` Dataset 30문항과 아래 과거 기준선은 변경하지 않았습니다. Dataset v2의
실제 `searchEvaluation` 실행, Ollama·PostgreSQL 측정과 threshold 분석은 Batch 1A
범위가 아니므로 `NOT_RUN`입니다.

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

평가기는 합성 corpus를 현재 `TextChunker`와 실제 임베딩으로 색인하고, 프로덕션 `SearchService`의 top 5와 동일 owner·ACTIVE 조건의 평가 전용 top 20 순서가 일치하는지 확인합니다.

## 결과

- 요약과 질문별 결과: `dense-baseline-<UTC timestamp>-<run token>.json`
- 임계값 분석용 원시 후보: `dense-baseline-candidates-<UTC timestamp>-<run token>.csv`

전체 원문은 결과에 복사하지 않습니다. JSON에는 질문, 예상 근거, 반환 chunk ID, relevance 순서, top1 score·distance, 중복 여부와 지연이 기록됩니다. CSV에는 split·category와 후보 rank, score, distance, relevance, evidence group이 기록됩니다.

지표는 전체, TUNING, TEST, category별로 구분합니다. 각 구분에서 Recall@20, direct Recall@20, Precision@5, direct Precision@5, Direct MRR@20, evidence-group 기준 nDCG@5, 중복 결과 비율, 평균·p95 검색 지연을 기록합니다. JSON의 machine-readable 필드는 `directMrrAt20`이며, PRZ-001 교정 이전 결과의 `mrr` 필드와 직접 비교하지 않습니다. 근거 있음/없음 질문의 top1 score·distance 분포도 별도로 기록합니다.

합성 파일럿 기준선은 실제 개인 문서나 서비스 전체 검색 성능을 보장하지 않습니다. 이 수치는 이후 Reranker, Hybrid Search, 청킹 실험의 비교 기준입니다. 현재 score에 운영 임계값을 적용하거나 정답 확률로 해석하지 않습니다.

## 2026-07-14 합성 기준선

실제 PostgreSQL 16.14·pgvector와 Ollama `bge-m3`로 실행한 파일럿 결과입니다.

| 지표 | 결과 |
|---|---:|
| Recall@20 | 1.0000 |
| Precision@5 | 0.1933 |
| Direct Precision@5 | 0.1600 |
| Legacy aggregate direct-rank score (PRZ-001 교정 이전 전체 질문 분모, Direct MRR@20과 비교 불가) | 0.6556 |
| nDCG@5 | 0.8543 |
| 중복 결과 비율 | 0.0067 |
| 평균 / p95 검색 지연 | 864.20ms / 999ms |

합성 corpus가 실제로 만든 청크는 14개이므로 Recall@20은 사실상 작은 corpus의 hit-rate 성격이며 운영 규모 회수 성능을 증명하지 않습니다. 이 결과는 평가 파이프라인의 재현 가능한 기준선이지 제품 품질 보증이 아닙니다.

## 2026-07-24 PRZ-001 정합성 교정 후 재측정

Docker Desktop 29.6.2, Testcontainers PostgreSQL 16.14·pgvector, 로컬 Ollama
`bge-m3`로 `./gradlew.bat searchEvaluation --no-daemon`을 실행한 결과입니다.
TUNING과 TEST 사이 양성 근거를 분리한 뒤, Direct MRR@20은 직접 근거가 있는
질문만 분모로 계산합니다. 따라서 아래 TEST 값만 이후 설정 변경의 최종 비교에
사용하고, TUNING 값은 파라미터 탐색에만 사용합니다.

| 구분 | Recall@20 | Precision@5 | Direct MRR@20 | nDCG@5 | 평균 / p95 검색 지연 |
|---|---:|---:|---:|---:|---:|
| 전체 30문항 | 1.0000 | 0.1933 | 0.8551 | 0.8543 | 779.50ms / 1080ms |
| TEST 10문항 | 1.0000 | 0.2000 | 0.7917 | 0.8494 | 737.40ms / 1005ms |
| TUNING 20문항 | 1.0000 | 0.1900 | 0.8889 | 0.8572 | 800.55ms / 1080ms |

이 수치는 합성 파일럿 코퍼스와 현 시점의 로컬 하드웨어·Ollama 실행 상태에
한정됩니다. OpenSQL, OpenProxy, OpenHA 호환성이나 운영 규모의 검색 성능을
증명하지 않습니다.

같은 후보에 `BAAI/bge-reranker-v2-m3`를 적용한 CPU 실험은 상위 5개 직접 근거 품질을 개선하지 못하고 큰 지연·메모리 비용을 보여 운영 도입에서 제외했습니다. 코드가 아니라 조건·수치·거절 근거만 [BGE Reranker 평가와 비채택 결정](../archive/experiments/2026-07-14-bge-reranker-evaluation.md)에 보존합니다.
