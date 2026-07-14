# 검색 품질 평가

이 도구는 현재 `bge-m3` Dense 검색을 기준선으로 측정합니다. 프로덕션 `POST /api/career-evidence/search`의 최대 5개 계약과 검색 순위는 변경하지 않습니다.

## 데이터 위치와 형식

- 추적 가능한 합성 예제: `src/test/resources/search-evaluation/sample/`
- 실제 개인 평가 데이터: `local/search-evaluation/<dataset>/`
- 실행 결과 기본 위치: `local/search-evaluation/results/`

`local/search-evaluation/`은 Git에서 제외됩니다. 실제 이력서 원문, 질문, 이메일, 지원 회사 정보, JWT나 실행 결과를 저장소에 커밋하지 않습니다.

데이터셋 디렉터리에는 두 파일이 필요합니다.

- `corpus.json`: `datasetId`와 합성 문서 목록
- `questions.jsonl`: 한 줄에 질문 하나

문서는 `fixtureId`, `title`, 실제 `DocumentType`, `fileType`, `pages`, `evidenceAnchors`를 가집니다. `evidenceAnchors`는 DB의 가변적인 chunk ID 대신 원문 내 고유한 짧은 문자열에 안정적인 `fixtureEvidenceId`를 부여합니다.

질문 필드:

- `questionId`, `query`, `category`, `noEvidence`
- `expectedEvidence[]`: `fixtureEvidenceId`, `relevance`, `evidenceGroupId`
- relevance `2`: 직접 근거, `1`: 부분 근거, `0`: 무관·hard negative

`noEvidence=true` 질문에는 relevance 1 또는 2를 넣을 수 없습니다. 같은 근거가 overlap 청크 여러 개에 포함되면 같은 `evidenceGroupId`로 중복률을 측정합니다.

## 실행

Docker Desktop, PostgreSQL·pgvector Testcontainer와 로컬 Ollama `bge-m3`가 필요합니다.

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

- 요약과 질문별 결과: `dense-baseline-<UTC timestamp>.json`
- 임계값 분석용 원시 후보: `dense-baseline-candidates-<UTC timestamp>.csv`

전체 원문은 결과에 복사하지 않습니다. JSON에는 질문, 예상 근거, 반환 chunk ID, relevance 순서, top1 score·distance, 중복 여부와 지연이 기록됩니다. CSV에는 후보 rank, score, distance, relevance와 evidence group이 기록됩니다.

지표는 Recall@20, direct Recall@20, Precision@5, direct Precision@5, MRR@20, evidence-group 기준 nDCG@5, 중복 결과 비율, 평균·p95 검색 지연입니다. 근거 있음/없음 질문의 top1 score·distance 분포도 별도로 기록합니다.

이 수치는 이후 Reranker, Hybrid Search, 청킹 실험의 비교 기준입니다. 현재 score에 운영 임계값을 적용하거나 정답 확률로 해석하지 않습니다.
