# PRZ-044 Search V3 Release-grade Prediction Freeze

- 상태: `VERIFIED`
- 최종 판정: `PREDICTION_PHASE_BLOCKED`
- branch: `PRZ-044-search-v3-release-grade-evaluation`
- 기준: `PRZ-043-search-v3-release-grade-evaluation@82606d242c2e1077c25ffefaeae98c2cdb51c4b4`
- Search V2/V3 Production source: `refactor/search-v3@0e95472bb68f72accf0d6b2171c22f0719fe6941`
- PRZ-042: `V3_NO_GO / SEED_FINAL_PROTOCOL_RESULT` — 역사 기록 유지
- PRZ-043: `EVALUATION_INVALID` — 역사 기록 유지
- Gold 채점: `NOT_RUN`

공식 `attempt-1`은 `RUNTIME_V2` 단계에서 입력 문서 유형 호환성 오류로 종료됐다. One-shot
계약에 따라 같은 dataset으로 다시 실행하지 않았으며, prediction과 completion receipt는 생성되지
않았다. 아래 계약은 결과를 보기 전에 동결한 원문 그대로 유지한다.

## 목적

Gold가 물리적으로 빠진 `prizm-release-eval-v1.0.3` 입력 패키지로 현재 Search V2와 Search V3의
prediction만 정확히 한 번 생성하고 동결한다. 이번 PRZ에서는 Gold를 찾거나 열지 않으며 metric과
V3 채택 판정을 계산하지 않는다.

## 변경 범위

- `src/searchEvaluation/**`: Gold-free 입력 검증, prediction runtime, canonical freeze, one-shot guard
- `build.gradle`: PRZ-044 focused/preflight/official task
- `specs/PRZ-044-search-v3-release-grade-evaluation/**`, `specs/README.md`

`src/main/**`, migration, dependency, frontend, MCP, Search V2/V3 검색·색인 정책은 수정하지 않는다.

## 입력 데이터 계약

| 항목 | 동결값 |
| --- | --- |
| dataset | `prizm-release-eval-v1.0.3` |
| INPUT ZIP SHA-256 | `8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0` |
| manifest raw SHA-256 | `1c6a363f06765c4715a03e70d2cb70e3f045259d651e6be621b5ddb92b9dede1` |
| manifest canonical SHA-256 | `762b520be8618657f4f57e6829c60b68857c87c86b142d7003a7c2f9156d890a` |
| physical input payload combined SHA-256 | `8413cf153302754c0625fb2d594bea4e10df8ac73f35259b7f7fe4695dad63b0` |
| manifest combined commitment | `6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec` |
| sealed Gold commitment | `d0a507764449315645fabac06d785c1ef8598b1f9ab131674b6e20ad58dda696` |
| 규모 | user `75`, document `90`, query `600` |
| 파일 | TXT `45`, text-layer PDF `45` |
| 직군 | `15`, 직군별 user `5` |

Manifest combined commitment는 실제 92개 입력 payload와 물리적으로 없는
`sealed/gold.json` commitment 레코드를 함께 canonicalize한 값이다. 실제 입력 payload만의
combined SHA는 execution contract에 별도 필드로 기록한다. 둘을 같은 검증값으로 표현하지 않는다.

ZIP에는 `sealed/` 또는 이름에 `gold`가 포함된 physical entry가 없어야 한다. 절대 경로,
`..`, 역슬래시, drive/ADS 형태, symlink, 암호화, raw·NFC/casefold 중복도 거부한다. 입력은
메모리에서 검증·읽으며 dataset을 재패키징하거나 수정하지 않는다.

## Search source 동결

### Search V2

현재 Production `SearchService`, repository/profile, fallback/rescue, localization과 V2 indexing
구성요소를 그대로 사용한다.

### Search V3

현재 `SEARCH_V3_RUNTIME_READY` runtime을 그대로 사용한다.

```text
query embedding
→ ACTIVE+COMPLETED RetrievalPassage exact cosine Top20
→ 기존 Typed Validation/Selection
→ Top5 Passage 내부 CHILD_DENSE_V1
→ 최대 5 EvidenceChild
```

BM25, Sparse, RRF, Cross Encoder, Qwen, rewrite, Parent Dense, MMR, 새 boost·threshold·heuristic과
query별 예외 처리는 추가하지 않는다.

## 실제 Runtime 계약

- disposable PostgreSQL 16 + pgvector와 실제 local Ollama를 사용한다.
- model은 `bge-m3:latest`, digest
  `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, dimension `1024`,
  cosine으로 고정한다.
- 75 synthetic owner와 90 document/version을 동일 source bytes로 V2/V3에 제공한다.
- TXT와 text-layer PDF 모두 Production extractor/indexing component를 사용한다.
- 중립 warm-up은 official attempt claim 전에 한 번 수행하고 dataset query를 사용하지 않는다.
- 모델 ID/digest/dimension, Production source boundary 또는 dataset identity가 다르면 official
  attempt를 만들지 않는다.

## Prediction canonical 계약

V2와 V3는 별도 JSON artifact로 저장한다. 각 artifact는 다음을 포함한다.

- engine/profile, dataset·contract·model identity
- query ID, owner, profession, language, query text SHA-256
- public runtime state와 최대 5개 최종 결과
- 결과의 stable ID, 순위, score, document/version, source type/page/span, source text SHA-256
- indexing/runtime count, latency와 owner/lifecycle audit

JSON은 UTF-8/LF, recursively sorted object keys와 고정 array order로 compact canonicalize한다.
V2 600행을 모두 동결·disk reload한 뒤에만 V3 600행을 실행한다. Prediction 내용은 metric,
실패 분석 또는 검색 변경에 사용하지 않는다.

## Preflight 계약

공식 dataset과 무관한 synthetic TXT/PDF fixture로 다음을 먼저 검증한다.

- 실제 PostgreSQL·pgvector, 실제 BGE-M3, V2/V3 runtime
- prediction writer, canonical hash, disk reload
- Windows/Linux path와 ZIP 안전성
- `/sealed/gold.json`, `sealed\\gold.json`, case variant와 이름에 `gold`가 포함된 physical entry 거부
- 고정 run directory와 두 번째 claim 거부

Preflight가 하나라도 실패하면 official attempt는 `0`으로 유지한다.

## One-shot 계약

고정 official directory:

`local/search-v3-evaluation/prz044/official/6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec/attempt-1`

- `officialRunsAllowed=1`
- run directory를 Gradle/system property 또는 호출자 인자로 바꿀 수 없다.
- verifier가 dataset, Production/evaluator source, contract, model과 exact path를 검증한 뒤
  `CREATE_NEW`로 한 번만 claim한다.
- official root의 다른 attempt와 기존 marker가 있으면 거부한다.
- 실패한 attempt도 소비되며 같은 dataset으로 다시 실행하지 않는다.

## Gold release 조건

다음을 모두 만족한 completion receipt가 있어야 다음 별도 단계에서 Gold artifact를 받을 수 있다.

- V2/V3 row count `600 / 600`
- V2/V3 canonical prediction SHA-256과 file SHA-256 검증
- disk reload identity parity
- official attempt `1`
- `goldPresent=false`, `goldAccessed=false`
- dataset/source/model/contract mutation `0`

이번 PRZ에는 Gold loader, Gold path/property, metric task를 만들지 않는다. Gold commitment는
manifest identity 확인에만 사용한다.

## 다음 채점 단계의 Metric·Gate 동결

Gold가 별도로 제공되는 다음 단계에서만 계산한다.

- 전체: Direct Top1, MRR, nDCG@5, Recall@5, user-macro Top1/MRR
- slice: 15개 직군, `DIRECT/SEMANTIC/TYPED/MULTI_ASPECT/NOT_SUPPORTED`, project-name present/absent
- 품질: typed evidence/fidelity, no-answer, localization, contamination, duplicate
- 통계: user-paired bootstrap `10,000`, seed `44044`, percentile 95% CI

Adoption Gate는 PRZ-043에서 결과 전에 고정했던 값을 유지한다.

- user-macro Top1 delta `>=+0.03`, bootstrap CI lower `>0`
- secondary 전체 metric delta 각각 `>=-0.01`
- 충분한 slice(user `>=3`, direct-positive `>=5`) Top1/MRR `>=-0.05`, Recall@5 `>=-0.01`
- V3 localization precision/recall 각각 `>=0.95`, V2 delta 각각 `>=-0.01`
- typed direct evidence precision `>=0.95`, delta `>=-0.01`, wrong typed Top1 `0`
- NOT_SUPPORTED false-positive rate `<=0.05`, V2 대비 증가 `<=0.01`
- owner/inactive/failed/stale/duplicate/mixed leakage `0`
- V3 query p95 `<= min(V2×1.5, V2+100ms)`

Gold schema가 핵심 Gate 계산 정보를 제공하지 않으면 해당 Gate는 `NOT_ASSESSED`이며
`V3_ADOPT`를 허용하지 않는다. 이번 prediction 단계에서는 어떤 Gate도 채점하지 않는다.

## 완료 판정

`PREDICTIONS_FROZEN_READY_FOR_GOLD`는 다음이 모두 실제로 확인될 때만 사용한다.

- INPUT ZIP/hash/payload parity와 Gold physical absence `PASS`
- Production Search source와 dataset mutation `0`
- synthetic preflight 및 실제 PostgreSQL/BGE-M3 `PASS`
- 90 documents indexing `PASS`
- V2/V3 prediction `600 / 600`
- 별도 prediction SHA freeze와 completion receipt `PASS`
- official attempt `1`, Gold present/accessed `false/false`
- branch commit/push, origin parity, clean worktree

하나라도 실패하면 `PREDICTION_PHASE_BLOCKED`다. `V3_ADOPT`와 `V3_NO_GO`는 이번 PRZ에서
판정하지 않는다.

## 비범위

- Gold 탐색·요청·open·parse·metric·failure analysis
- Search V2/V3 tuning, Production API cutover, worker 기본 활성화
- main/refactor merge, PR 생성, 다음 PRZ 시작
- OpenSQL, OCR/image-only PDF
