# PRZ-027 Search V3 Cross Encoder Reranking

- 상태: `IN_PROGRESS / INPUT_CONTRACT_FROZEN / BENCHMARK_NOT_RUN`
- 시작 source: `PRZ-026-structural-parsing-parent-child@a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9`
- 선행 조건: `DEPENDS_ON_PRZ_025`, `DEPENDS_ON_PRZ_026_B3`
- Production 적용: `NOT_RUN`

## 1. 단일 가설과 비교

R0는 PRZ-026 B3 `RetrievalPassage → bge-m3 Dense` 순위다. R1은 동일 Dense Top20의
`originalQuery ↔ RetrievalPassage.sourceText` pair를 Cross Encoder로 점수화해 재정렬한다.
R1은 후보 추가·삭제·교체, threshold와 score 확률 해석을 하지 않는다. C1 context, Parent Dense,
QueryPlanner, rewrite, sparse/FTS/RRF, exact/rescue, MMR, LLM은 `NOT_RUN`이다.

PRZ-026의 실제 C1 판정은 `NEEDS_ADJUSTMENT`이며 PRZ-027 baseline으로 선택하지 않는다. 요청문의
`NO_GO` 표현으로 역사 판정을 소급 변경하지 않는다.

## 2. 동결한 모델·입력

| 항목 | 동결값 |
| --- | --- |
| model | `Alibaba-NLP/gte-multilingual-reranker-base` |
| model revision | `8215cf04918ba6f7b6a62bb44238ce2953d8831c` |
| remote code | `Alibaba-NLP/new-impl@40ced75c3017eb27626c9d4ea981bde21a2662f4` |
| license | model/code 모두 `Apache-2.0` |
| parameters / weight file | `305,959,681` / `611,934,706 bytes` |
| runtime | evaluation-only Python 3.12.13, `transformers==4.39.1`, PyTorch 2.9.0 CPU float32 |
| pair | query 원문과 B3 `sourceText`; generic instruction 없음 |
| cutoff | Dense Top20 고정, max length 512, batch 8, CPU threads 8 |
| score | finite raw logit, 순위에만 사용 |

Model과 remote-code revision, 다운로드 파일 SHA-256, 실제 runtime·RAM은 실행 artifact와 evidence에
기록한다. revision/license/실행이 검증되지 않으면 다른 모델로 대체하지 않고 `BLOCKED`다.

## 3. Identity·Gold·Safety 계약

- R0 full candidate ID set과 R1 full candidate ID set은 정확히 같아야 한다.
- rerank 전 Top20 ID와 dense rank는 export/import에서 정확히 같아야 한다.
- pair ID는 dataset/split/query/candidate와 query/source SHA-256으로 결정한다.
- duplicate, missing, unknown pair, score, query/source hash는 fail-closed한다.
- 동점은 score 내림차순, dense rank 오름차순, candidate ID 오름차순이다.
- inference input에는 Gold, expected relation, answerability, category, covered unit/group/parent가 없다.
- Gold는 import 후 Top1/MRR/nDCG/Recall과 win/loss/tie 계산에만 사용한다.
- sourceText, EvidenceChild IDs, provenance, owner/document/version, contamination/fragmentation은 B3와
  동일해야 한다.
- SEALED FINAL은 integrity metadata 외 load/search/prediction/result `NOT_RUN`이다.

## 4. 평가와 nDCG

Original, Long-form 1.1.0, robustness 1.0.0 DEV/CAL을 각각 보고한다. nDCG@5 relevance는
source range가 query의 `DIRECT_SUPPORT` Gold Unit을 포함하면 1, 아니면 0인 binary direct-support로
고정한다. negative query score는 threshold 없이 min/median/p95/max 진단만 기록한다.

필수 metric은 Recall@20, Recall@5, Top1, MRR, nDCG@5, user-macro, profession/language/category,
DIRECT rank, win/loss/tie, rank2~5→1, rank1 loss와 CPU pair/query latency·RSS·model size다.

## 5. 사전 판정 Gate

`PROMISING`은 다음을 모두 요구한다.

- candidate/provenance parity, contamination/fragmentation/cross-parent violation 0, Gold Child 100%
- 세 dataset 모두 Candidate Recall@20 비열화 0
- combined query-micro Top1 또는 MRR 개선, user-macro Top1/MRR 비열화 0
- wins가 losses보다 많고, 서로 다른 2 user bundles 이상의 win
- 기존 rank1 DIRECT loss 비율 5% 이하
- 3 bundles·10 DIRECT queries 이상 profession/language slice의 Top1/MRR 신규 회귀 0
- CPU p95 query rerank 3,000ms 이하, peak RSS 증가 4GiB 이하, model cache 1GiB 이하, GPU 불필요

Safety/identity 실패는 모델 판정 전 `INVALID`. Safety가 유효해도 aggregate Top1/MRR 순증이 없거나
loss가 win보다 많거나 rank1 loss 비율이 10%를 넘으면 `NO_GO`다. 그 외 quality/slice/3~10초 CPU
latency 문제는 `NEEDS_ADJUSTMENT`; 10초 초과 또는 RSS 8GiB 초과는 `NO_GO`다. 결과를 본 뒤 model,
input, TopK나 threshold를 바꾸지 않는다. QueryPlanner 진입은 `PROMISING`일 때만 가능하다.
