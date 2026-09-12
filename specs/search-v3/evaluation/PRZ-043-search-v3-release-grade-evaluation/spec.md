# PRZ-043 Search V3 Release-grade Evaluation

- 상태: `VERIFIED / EVALUATION_INVALID`
- branch: `PRZ-043-search-v3-release-grade-evaluation`
- 평가 하네스 기준: `PRZ-042-search-v3-final-evaluation@e389d27235e16a5e14d05272999a60b7fc95c35f`
- Search V2/V3 source 기준: `refactor/search-v3@0e95472bb68f72accf0d6b2171c22f0719fe6941`
- 선행 결과: PRZ-042 `V3_NO_GO / SEED_FINAL_PROTOCOL_RESULT` — 역사 기록 유지
- Production cutover: `NOT_RUN`

## 실행 종료 상태

공식 prediction 전에 Gold 제외용 glob이 Windows 경로 구분자와 맞지 않아
`sealed/gold.json`의 일부 item-level 의미 필드가 명령 출력에 포함됐다. JSON parser를 실행하거나
질문·근거 본문을 의도적으로 읽은 것은 아니지만, 이 문서에서 결과 전에 고정한 Gold 접근 경계를
넘은 사실은 같다. 따라서 공식 attempt를 시작하지 않고 최종 판정을 `EVALUATION_INVALID`로
확정했다.

- official attempt: `0`
- V2/V3 prediction: `NOT_RUN / NOT_RUN`
- Gold metric join: `NOT_RUN`
- Search V2/V3 source 변경: `0`
- dataset 변경: `0`

아래 계약은 접근 사고 전에 고정한 원문을 보존한다. 같은 데이터셋으로 이 판정을 다시 실행하지
않는다.

## 목적

현재 Production Search V2와 `SEARCH_V3_RUNTIME_READY` 상태의 Search V3를 새로운 독립
Release-grade 데이터셋에서 한 번만 비교한다. 검색 구현이나 데이터셋을 결과에 맞춰 수정하지
않고, 처음 보는 75명·15개 직군의 문서와 질문에서 어느 검색이 더 잘 일반화하는지 판정한다.

PRZ-042의 2-user seed 결과는 소급 변경하지 않는다. 이번 결과는 별도 데이터셋과 별도 공식
attempt를 사용하는 더 강한 일반화 근거다.

## 범위와 고정 대상

### Search V2

현재 `SearchService`, `VectorSearchRepository`, profile, fallback/rescue, `TextChunker`와
localization 경로를 그대로 사용한다.

### Search V3

현재 runtime을 그대로 사용한다.

```text
EvidenceChild
→ B3 RetrievalPassage
→ BGE-M3 Dense Top20
→ Typed Validation/Selection
→ Top5 Passage 내부 CHILD_DENSE_V1
→ 최대 5개 원문 근거
```

BM25, Sparse, FTS, RRF, Cross Encoder, Qwen, query rewrite, Parent Dense, MMR, 새 heuristic,
boost와 threshold는 추가하지 않는다. `src/main/**` 검색·색인·query source는 수정하지 않는다.

## 데이터셋 계약

| 항목 | 동결값 |
| --- | --- |
| dataset | `prizm-release-eval-v1.0.2` |
| ZIP SHA-256 | `b513fbfced174b8ced15d122a80d7e12c0315376d1f9770a3f2ac29ade07cdee` |
| manifest SHA-256 | `05de47a4f55a6488c9b5bd9df68cb2c6788234cecfc472e525695547746b0ea0` |
| manifest canonical SHA-256 | `4afb20787a68d7a16ec79431619a50e298406bef6f92596689aa1335352990a9` |
| combined SHA-256 | `78b8612eab01ec09be4e30f665366b45366b0f12f1d9d516ab247bf6a33617a8` |
| Gold raw SHA-256 | `0707b2effa3e0c4573c31fbe0252f483fa2d18f89259bcaf7f5235c3b03ce715` |
| 규모 | user `75`, document `90`, query `600` |
| 파일 | TXT `45`, text-layer PDF `45` |
| 직군 | `15`, 직군별 user `5` |
| 언어 | query/document 모두 `KO` |
| 프로젝트명 exact slice | present `247`, absent `353` |

원본 ZIP은 read-only로 취급한다. 안전한 ignored 경로로만 추출하며 payload bytes를 바꾸지 않는다.
Gold를 제외한 ZIP 구조, path, CRC, manifest, payload hash, 사용자·문서·질문 연결과 중복을 공식
attempt 전에 검증한다. PDF text layer는 Production `DocumentTextExtractor`로 사전 확인한다.

### Gold 접근 충돌 해소

Gold reference·span·page 의미 검증은 `sealed/gold.json` parsing이 필요하므로 prediction 전에는
실행할 수 없다. 사전에는 Gold 파일 존재·size·raw SHA만 확인한다. V2/V3 prediction이 각각
동결되고 completion receipt가 생성된 뒤 Gold-open receipt를 먼저 남기고, 그 다음 Gold를 한 번
parse해 reference·owner·source span·line·page 무결성과 metric을 함께 검증한다. 이 순서를
어기면 `EVALUATION_INVALID`다.

## Runtime 계약

- disposable PostgreSQL + pgvector에 synthetic owner 75명을 서로 분리해 적재한다.
- V2와 V3는 같은 owner, document, version, source bytes, 질문과 BGE-M3를 사용한다.
- model은 `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, dimension `1024`,
  cosine으로 고정한다.
- 모델 identity는 official attempt claim 전에 실제 Ollama에서 확인하고 실행 중 다시 확인한다.
- 중립 warm-up 한 건을 측정 전에 수행한다.
- 모든 문서 색인을 끝낸 뒤 600개 V2 prediction 전체를 만들고, 그 다음 V3 prediction 전체를
  만든다. 질의별 V2/V3 교차 실행은 하지 않는다.
- V2/V3 prediction은 별도 파일과 별도 canonical/file SHA로 동결한다.
- metric 입력은 동결 파일을 다시 읽어 구성한다. 동결 전 in-memory 결과를 사용하지 않는다.

PDF Gold는 pypdf 전역 code-point 좌표이고 runtime은 PDFBox 페이지-local 좌표다. Gold 개봉 뒤
동일 document/version/page의 `exactEvidenceText`를 Production 추출 page에서 유일하게 찾은 경우에만
runtime-local span으로 변환한다. 모호하거나 찾지 못하면 Gold integrity `FAIL`이다.

## 공식 one-shot 계약

공식 attempt는 dataset combined SHA, source SHA, contract SHA, model digest와 아래 고정 경로에
결합한다.

`local/search-v3-evaluation/prz043/official/78b8612eab01ec09be4e30f665366b45366b0f12f1d9d516ab247bf6a33617a8/attempt-1`

- `officialRunsAllowed=1`
- Gradle property나 system property로 official run directory를 바꿀 수 없다.
- verifier가 contract 경로와 허용 횟수를 읽고 정확히 일치할 때만 `CREATE_NEW`로 claim한다.
- 같은 official root의 다른 attempt와 두 번째 claim은 거부한다.
- preflight는 별도 synthetic non-final 경로만 사용하며 공식 dataset query를 실행하지 않는다.

## Metric 정의

순위 metric의 분모는 `DIRECT_SUPPORT` span이 하나 이상 있는 query다. 결과가 동일 owner의 동일
document/version/page에서 하나의 DIRECT span 전체를 포함하면 direct hit다. V2는 선택 chunk를,
V3는 선택 `EvidenceChild`를 순위 판정에 사용한다. 표시 근거의 span은 localization에 별도로 쓴다.

- Top1: 1위가 direct hit인 query 비율
- MRR: 첫 direct hit 순위의 reciprocal mean
- nDCG@5: Evidence Group 중복 credit 없이 `DIRECT_SUPPORT=3`, `RELATED=2`,
  `CONTRADICTS=1`, `INSUFFICIENT/UNJUDGED=0`
- Recall@5: 질문의 required direct Evidence Group 조건을 Top5가 충족한 비율
- user-macro: 사용자별 값을 같은 가중치로 평균
- localization precision/recall: 표시 span과 DIRECT span의 code-point overlap
- no-answer: `NOT_SUPPORTED`에서 최종 결과가 0건이면 correct; 한 건 이상이면 false positive
- project-name slice: 같은 owner의 `projectNames`가 질문에 NFC+casefold exact substring으로
  존재하는지 prediction 전에 고정하며 검색 입력에는 전달하지 않는다.

전체, 15개 직군, `DIRECT/SEMANTIC/TYPED/MULTI_ASPECT/NOT_SUPPORTED`, project-name
present/absent를 각각 보고한다. 영어·혼합 언어 일반화는 이 데이터셋에서 `NOT_ASSESSED`다.

## 결과 전 동결 Gate

### Release adequacy

- user `>=50`, query `>=500`
- TXT/PDF 각각 `>=30`
- 직군 `>=10`, 직군별 user `>=3`
- normalized duplicate query/document `0`
- Gold reference/owner/span/line/page integrity 위반 `0`

### Hard Safety

다음은 모두 `0`이어야 한다.

- cross-owner result
- inactive/wrong-version result
- failed/superseded/stale generation result
- duplicate/mixed artifact corruption
- V3 cross-parent direct evidence merge
- dataset/source/prediction identity 위반

### Primary quality

- user-macro Top1 delta `>= +0.03`
- user-paired bootstrap `10,000`회, seed `43043`, 95% CI lower `> 0`

### Secondary quality

query-micro Top1/MRR/nDCG@5/Recall@5와 user-macro MRR delta가 각각 `>= -0.01`이어야 한다.

### Slice non-regression

user `>=3`이고 DIRECT-positive query `>=5`인 직군, category와 project-name slice는
Top1/MRR delta `>= -0.05`, Recall@5 delta `>= -0.01`이어야 한다. 하나라도 실패하면 자동 채택하지
않는다.

### 근거 품질

- V3 localization precision `>=0.95`, recall `>=0.95`
- 두 localization delta `>=-0.01`
- V3 duplicate evidence ratio가 V2보다 `0.01`을 초과해 악화되지 않음
- TYPED direct evidence precision `>=0.95`, V2 대비 delta `>=-0.01`
- TYPED wrong value/date/version/comparator Top1 `0`
- `NOT_SUPPORTED` false-positive rate `<=0.05`, V2 대비 delta `<=+0.01`

Gold가 필요한 세부 필드가 실제 schema에 없으면 해당 세부 fidelity는 `NOT_ASSESSED`로 남기며
`PASS`로 바꾸지 않는다. TYPED 또는 no-answer 핵심 Gate가 `NOT_ASSESSED`면 `V3_ADOPT`는 금지한다.

### 운영 Gate

- V3 query p95는 `min(V2 p95 × 1.5, V2 p95 + 100ms)` 이하여야 한다.
- 실제 full indexing 경로가 양쪽에서 동등하면 indexing wall과 raw vector bytes는 각각 V2의
  `3.0배` 이하여야 한다. 경계가 다르면 수치는 기록하되 Gate는 `NOT_ASSESSED`다.
- 추가 model/service/GPU 필수화는 `0`이다.

## 판정

- `EVALUATION_INVALID`: 조기 Gold 접근, dataset/source 변조, hash 불일치, official prediction
  재실행, run-directory 우회 또는 freeze 위반
- `V3_ADOPT`: Release adequacy, Safety, Primary, Secondary, Slice, 근거 품질, no-answer,
  typed, runtime과 운영 Gate를 모두 통과
- `V3_NO_GO`: Hard Safety 실패 또는 주요 전체/slice quality가 허용 회귀 폭을 넘음
- `V3_NEEDS_ADJUSTMENT`: 평가 무결성은 유효하고 일부 순증은 있으나 adoption Gate 일부 미충족

결과가 나빠도 같은 dataset을 다시 실행하거나 V2/V3/Gate/Gold를 수정하지 않는다.

## 비범위

- Production API cutover, Search V2 삭제·deprecated, worker 기본 활성화
- frontend, MCP, migration, dependency와 `src/main/**` 변경
- main/refactor merge, PR 생성
- 결과 기반 tuning과 같은 Final 재실행
- OCR/image-only PDF와 OpenSQL 검증
