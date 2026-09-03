# PRZ-042 Evidence

## 최종 판정

`V3_NO_GO`

| 항목 | 결과 |
| --- | --- |
| 결과 범위 | `SEED_FINAL_PROTOCOL_RESULT` |
| 기준 source | `0e95472bb68f72accf0d6b2171c22f0719fe6941` |
| 공식 attempt | `1`, `COMPLETED` |
| 평가 inventory | user `2`, TXT document `3`, KO query `8`, DIRECT-positive `5` |
| Fresh V2 baseline | `EXECUTED` |
| Search V3 final | `EXECUTED` |
| Production cutover | `NOT_RUN` |

V3는 Recall@5와 구조 경계를 유지했지만 Top1/MRR/nDCG@5가 V2보다 낮았다. typed evidence와
localization Gate도 실패했다. 현재 V3를 기본 검색으로 채택하지 않는다.

## Phase A — PRZ-041 통합

`PRZ-041-search-v3-runtime-completion@0e95472bb68f72accf0d6b2171c22f0719fe6941`이
`refactor/search-v3@ded1adb4002e904eb4b5652db556faa9a0d6f8a2`에서 1 commit 앞선 직선
계보임을 확인했다. `--ff-only`로 통합하고 local/origin parity를 확인한 뒤, 독립 commit `0`과
spec/evidence 보존을 확인해 PRZ-041 local·origin branch를 일반 삭제했다. merge commit, rebase,
amend, force push와 PR은 만들지 않았다.

## Append-only PRZ-042 receipt

| Receipt | 시각 | 행위/역할 | 상태 | 근거와 제한 |
| --- | --- | --- | --- | --- |
| `PRZ-042-R000` | 2026-09-02 | pre-result contract author | `FINAL_EVALUATION_NOT_RUN` | PRZ-025 기반 숫자 Gate를 결과 전에 문서화. sealed manifest metadata만 확인했으며 questions/gold/corpus/documents semantic content는 읽지 않음. |
| `PRZ-042-R001` | 2026-09-02T05:42:54.467231300Z | official evaluator | `COMPLETED / V3_NO_GO` | attempt `1`; `opened=true`; `searchExecuted=true`; `CURRENT_FRESH_BASELINE=EXECUTED`; scope `SEED_FINAL_PROTOCOL_RESULT`; release adequacy와 quality Gate 미통과. |

`R000`은 당시 사실을 소급 변경하지 않은 역사 기록이다. 이후 구현 감사에서
`questions.json`에 query와 annotation이 함께 있음을 확인했고, 허용 query field만 projection한 뒤
prediction을 동결하고 Gold를 join했다.

## Artifact와 SEALED identity

| Artifact | SHA-256 |
| --- | --- |
| execution contract | `bcc7b2ed27f1f97894f27cb7760aa9bf23f9d7a4843aafea08db3e1004e4c5ec` |
| completion receipt | `32b50c3a9a4a3da0648238a676588248adb3ef2c32fbbd6e9e0b5e1cd44c1410` |
| metrics report | `07251f4a7a4c5bf2aee72848440435ef31d1704e2c465022e401f2e283d77a10` |
| predictions canonical | `1d6d4b9cc5d59ebdde966665edea799adaf701d3e621ec5aad4400a9b2f53dc5` |
| predictions file | `57013ef2c0dcedde4b710f9bc2bbc697561ed07afb788829696fba131fcde43f` |
| SEALED manifest | `d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa` |
| SEALED combined | `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383` |

공식 receipt 기준 실행 상태는 `opened=true`, `searchExecuted=true`,
`CURRENT_FRESH_BASELINE=EXECUTED`다. immutable manifest bytes는 바뀌지 않았으며 그 안의 봉인 당시
`mutable=false`, `opened=false`, `searchExecuted=false`도 그대로다.

## V2와 V3 비교

### 순위 품질

| 지표 | V2 | V3 | delta |
| --- | ---: | ---: | ---: |
| query-micro Top1 | 1.0 | 0.8 | -0.2 |
| query-micro MRR | 1.0 | 0.9 | -0.1 |
| query-micro nDCG@5 | 0.9447353132522084 | 0.8920109923338452 | -0.05272432091836321 |
| query-micro Recall@5 | 1.0 | 1.0 | 0.0 |
| user-macro Top1 | 1.0 | 0.75 | -0.25 |
| user-macro MRR | 1.0 | 0.875 | -0.125 |

paired user bootstrap는 sample `10,000`, seed `42042`였다. Top1 delta point estimate는 `-0.25`,
95% CI는 `[-0.5, 0.0]`이다.

| Query 분류 | 수 |
| --- | ---: |
| `BOTH_CORRECT` | 4 |
| `V2_ONLY_CORRECT` | 1 |
| `V3_ONLY_CORRECT` | 0 |
| `BOTH_WRONG` | 0 |
| `NOT_APPLICABLE` | 3 |

### 구조와 localization

| 지표 | V2 | V3 |
| --- | ---: | ---: |
| cross-parent contamination | 0.6875 | 0.0 |
| duplicate rate | 0.0 | 0.0 |
| fragmentation rate | 0.0 | 0.0 |
| localization precision | 0.5030232678120002 | 0.6329196293902177 |
| localization recall | 0.8 | 1.0 |

V3 localization은 V2보다 좋아졌지만 frozen absolute precision 기준 `0.95`에 미달해 Gate는
`FAIL`이다.

### Typed 진단

- state accuracy: `1.0`
- constraint-correct evidence precision: `0.3333333333333333`
- wrong date: `4`
- wrong value/version/qualifier: `0/0/0`
- Typed Gate: `FAIL`

## Gate 결과

| Gate | 결과 | 근거 |
| --- | --- | --- |
| Release adequacy | `FAIL` | 2 users, KO/TXT only, 대표 분포·독립 adjudication 없음 |
| Hard Safety | `PASS` | owner/inactive/lifecycle/duplicate/mixed violation 모두 `0` |
| Primary quality | `NOT_ASSESSED` | release adequacy 미충족 |
| Secondary quality | `FAIL` | Top1/MRR/nDCG@5 regression |
| Adequate slice | `NOT_ASSESSED` | slice 표본 기준 미충족 |
| Localization | `FAIL` | V3 precision `0.6329196293902177 < 0.95` |
| Typed contract | `FAIL` | evidence precision `0.3333333333333333`, wrong date `4` |
| Query latency | `PASS` | frozen p95 budget 이내 |
| Actual runtime/resources | `PASS` | actual BGE-M3, 추가 model/service/GPU `0` |
| Indexing/storage | `NOT_ASSESSED` | V2 full `DocumentIndexingProcessor` 경로 아님 |
| No-answer/PDF final | `NOT_ASSESSED` | semantic state 미구현, SEALED TXT only |

## 실제 model과 운영 관측값

- model: `bge-m3`
- resolved digest: `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`
- dimension/similarity: `1024` / cosine
- real Ollama runtime: `PASS`
- official SEALED TXT runtime: `PASS`
- text-layer PDF runtime regression: `PASS` — 기존 PRZ-041 integration 경로를 현재 source에서 재실행
- PDF final 검색 품질: `NOT_ASSESSED` — SEALED dataset이 TXT only
- OCR/image-only PDF: `NOT_SUPPORTED`

| 관측값 | V2 | V3 |
| --- | ---: | ---: |
| query p50 | 30.3997 ms | 37.8123 ms |
| query p95 | 40.3765 ms | 58.7597 ms |
| indexing wall | 121.4686 ms | 581.2809 ms |
| embedding vector 수 | 3 | 10 |
| raw vector bytes | 12,288 | 40,960 |

색인 시간과 storage는 V2가 full Production indexing path를 통과하지 않았으므로 비교 Gate 근거가
아니다.

## Safety와 blocking finding

- owner leakage: `0`
- inactive-version leakage: `0`
- lifecycle violation: `0`
- duplicate/mixed artifact: `0/0`

공식 실행은 한 번뿐이었고 같은 run directory에서는 create-new guard가 재실행을 막는다. 다만
verifier가 contract의 `runDirectory`와 `officialRunsAllowed`를 강제하지 않으며
`-Pprz042RunDir` override가 가능하다. 다른 경로의 재실행을 자동 차단하지 못하는 one-shot path
binding 한계가 남았다. 실제 재실행 횟수는 `0`이다.

Gold loader는 frozen predictions file과 hash parity를 다시 검증했다. 다만 evaluator에 전달한
객체는 frozen file에서 역직렬화한 결과가 아니라, freeze 직전에 사용한 동일 immutable in-memory
bundle이다. 현재 prediction/hash 불일치나 첫 결과 무효 증거는 없지만 낮은 위험의 integrity
limitation으로 남긴다. 결과를 본 뒤 source를 수정하거나 공식 평가를 재실행하지 않는다.

## 실행한 검증

| 검증 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| searchEvaluation unit/integrity | 16 | 0 | 0 | 0 |
| non-sealed runtime smoke | 1 | 0 | 0 | 0 |
| official SEALED evaluation | 1 | 0 | 0 | 0 |
| backend unit | 657 | 0 | 0 | 20 |
| PostgreSQL integration | 164 | 0 | 0 | 9 |

- frontend lint/build: `PASS / PASS`
- Docker Compose config: 검증 전용 local `.env`로 `PASS`; 파일은 실행 직후 삭제
- OSS readiness: `PASS` — Markdown 255 files/843 local links, external links 97 OK,
  verifier 16 tests PASS
- `git diff --check`: `PASS`
- evaluator source hash / SEALED manifest hash / SEALED Git tree: `PASS / PASS / PASS`
- Production·integration·frontend source diff: `0`
- OpenSQL: `OPENSQL_VALIDATION=NOT_RUN`
- Production cutover: `NOT_RUN`
- 같은 SEALED 재실행: `0`

## 결론

구조 오염과 localization recall은 개선됐지만 Search V3 finalist가 V2의 Direct Top1, MRR,
nDCG@5를 유지하지 못했고 typed evidence Gate도 실패했다. 현재 결과와 protocol 한계를 그대로
보존하며 Production Search V2를 유지한다.
