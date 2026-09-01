# PRZ-027 Cross Encoder 재정렬 근거

## 최종 판정

`VERIFIED / NO_GO / PHASE_COMPLETE`

동일한 B3 Dense Top20을 GTE Cross Encoder로 재정렬했지만 전체 Top1은 그대로였고
MRR·nDCG@5·Recall@5·user-macro가 하락했다. win/loss/tie는 `2/2/49`다. 이 구성은
Search V3에 채택하지 않았으며 QueryPlanner도 시작하지 않았다.

## 기준선

- branch / 시작 HEAD: `PRZ-027-cross-encoder-reranking` / `a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9`
- origin/main: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- dependencies: `PRZ-025@5f8229f88251938dc5b34588676cc69edf409c99`, `PRZ-026 B3@a7dbb12...`
- 시작 working tree: clean
- Production / SEALED FINAL search / CURRENT_FRESH_BASELINE: `0 / NOT_RUN / NOT_RUN`

## 1. 실행 전 모델 정보

Hugging Face metadata API에서 2026-08-31 확인했다. model은 public/non-gated,
`Alibaba-NLP/gte-multilingual-reranker-base@8215cf04918ba6f7b6a62bb44238ce2953d8831c`,
license `apache-2.0`, 305,959,681 F16 parameters, `model.safetensors` 611,934,706 bytes다. config의
`auto_map`은 `Alibaba-NLP/new-impl`을 가리키므로 code도
`40ced75c3017eb27626c9d4ea981bde21a2662f4`로 별도 pin했다. code license도 `apache-2.0`이다.
공식 README usage는 instruction 없이 query/document pair, `trust_remote_code=True`, max length 512다.

현재 CLI host는 Python 3.13.5, PyTorch 2.9.0+cpu, CUDA 없음, RAM 68,305,182,720 bytes다. 처음 검토한
evaluation-only `transformers==5.15.0`은 pinned weight를 load했지만 non-benchmark smoke pair에서
remote code의 position-id `IndexError`가 발생해 실행 계약으로 채택하지 않았다. 이는 result를 보기 전
runtime 호환성 확인이며 dataset query 실행은 0건이다. model config가 기록한 제작 runtime과 같은
`transformers==4.39.1`로 교정한다. Python 3.13에는 해당 버전의 tokenizer wheel이 없으므로 Codex
bundled Python 3.12.13과 evaluation-only PyTorch 2.9.0 CPU/psutil 5.9.8을 별도 local 경계에 고정해
다시 검증했다. Production dependency는 바꾸지 않는다.

호환 runtime smoke 결과는 `VERIFIED`다. non-benchmark generic pair만 사용했고 dataset query는 0건이다.
parameters 305,959,681, weight 611,934,706 bytes이며 SHA-256은 weight
`10ebaa49322dd7e01a13a91c49810939e3f91f231aceaa47fdf0cab3083954f6`, config
`995730781d157e147c13ccdfe0eb20a0875c486b6c4de8c97f0bbd845549dbc0`, remote configuration
`3411088045ffb8a9a0aa9936eae275896b39983a2ee5b08f091b44e6289e4fe4`, remote modeling
`374670b416fcc82f081c9cd28b5fd61c2bd91bbe18eb4798fcc48a81f9c250a0`이다. CPU float32
smoke score는 finite였다. benchmark inference와 결과 판정은 아직 `NOT_RUN`이다.

## 2. 실행 전 freeze

- datasets: 기존 Original / Long-form 1.1.0 / robustness 1.0.0 DEV/CAL만 사용
- B3/C1 source 판정 불일치: 실제 C1은 `NEEDS_ADJUSTMENT`; PRZ-027 baseline에서 제외
- SEALED combined: `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- official input-freeze commit: `69a4b1e1b924c81423717324b62fd8b0c155fd8c`
- R0/R1 result: `EXECUTED_ONCE`; QueryPlanner: `NOT_RUN`

## 3. 동결 산출물과 identity

- questions / pair: `69 / 551`; Dense Top20 외 후보 생성·삭제 없음
- input SHA-256: `3cd063129462593d6ffcc7acf8130ab99c804d97e33af9f183daf3fefbd4ffcb`
- B3 baseline SHA-256: `f0680a831c0aa2aa84f0822776637862dc735242c1331af265f233c4507cca2c`
- score SHA-256: `1fb0ccba37ae154a041d38703481d013a406f9a645cda261119bb7934ff0c259`
- local aggregate result SHA-256: `fe50d5cebfdbcb68b8a78ab318a2fd6c6d89184bc48e375d7d7c621142c5f6ac`
- input digest: `7f1feb8bdcffc6840f785a40c3432669b219aaec7422f95f640c6786876a4af6`
- R0/R1 full candidate set, sourceText, EvidenceChild IDs, provenance, owner/document/version parity: `PASS`
- inference input Gold/expected/answerability/category/covered relation field: `0`

Raw pair score와 query별 artifact는 `local/search-v3-evaluation/prz027/`의 ignored 경계에만 두었다.

## 4. 품질 결과

DIRECT_SUPPORT 53 queries 기준이다.

| 범위 | R0 Top1 | R1 Top1 | R0 MRR | R1 MRR | R0 nDCG@5 | R1 nDCG@5 | R0/R1 Recall@5 | R0/R1 Recall@20 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Combined micro | 0.9245 | 0.9245 | 0.9575 | 0.9511 | 0.9684 | 0.9578 | 1.0000 / 0.9811 | 1.0000 / 1.0000 |
| Original | 0.9286 | 1.0000 | 0.9643 | 1.0000 | 0.9736 | 1.0000 | 1.0000 / 1.0000 | 1.0000 / 1.0000 |
| Long-form | 0.8000 | 0.7333 | 0.8833 | 0.8274 | 0.9128 | 0.8508 | 1.0000 / 0.9333 | 1.0000 / 1.0000 |
| Robustness | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 / 1.0000 | 1.0000 / 1.0000 |

Combined user-macro Top1은 `0.9216 → 0.9118`, MRR은 `0.9559 → 0.9443`, nDCG@5는
`0.9671 → 0.9525`다. win/loss/tie는 `2/2/49`; rank 2~5에서 1로 승격 1건, 기존 direct rank1
손실 1/49건(`2.04%`)이다.

충분 표본 Gate에서 DATA_AI_INFRA MRR은 `0.9500 → 0.9077`, EN MRR은
`0.9583 → 0.9407`, KO Top1은 `1.0000 → 0.9524`로 회귀했다. KO_EN_MIXED는 Top1
`0.7500 → 0.8750`, MRR `0.8438 → 0.9167`로 개선됐지만 전체·user-macro 회귀를 상쇄하지 못한다.

집중 category 중 other-actor direct 1건은 rank `2 → 1`로 개선했다. completion-state direct 4건은
`1 win / 0 loss / 3 tie`, Top1 동일 0.7500, MRR `0.8125 → 0.8333`이다. negation 7건은 모두
NOT_SUPPORTED라 rank win/loss 대상이 아니며 top score 범위 `-0.0154..0.7737`만 기록했다. threshold나
no-answer 판정은 만들지 않았다. semantic-paraphrase Top1은 `0.9231 → 0.8846`, abstract-competency
Top1은 `0.8889 → 0.8333`으로 하락했다.

## 5. 순위 변화와 실패 분석

- 개선 `SV3-U04-Q03`: 다른 actor의 A/B test가 앞서던 rank 2 직접 근거를 rank 1로 올렸다.
- 개선 `SV3-LF-U103-Q04`: multi-evidence/completion 직접 근거의 첫 rank가 `4 → 3`이었다.
- 회귀 `SV3-LF-U101-Q01`: 현장 관찰·인터뷰 직접 근거가 결과 prototype passage 아래로 `1 → 2`가 됐다.
- 회귀 `SV3-LF-U102-Q03`: preventive ownership checklist 직접 근거가 `2 → 13`으로 크게 밀렸다.

관찰 guard `SV3-LF-U104-Q01`은 `2 → 2`, `SV3-LF-U106-Q02`는 `1 → 1`이다. 좋은 결과만을
선별하지 않았고 공식 실행 뒤 input/model/TopK를 변경하거나 재실행하지 않았다.

## 6. 운영과 안전

- CPU pair 평균 `45.08 ms`; query Top20 p50/p95 `398.42 / 630.09 ms`
- model load/warmup `670.40 / 704.22 ms`
- process RSS before/after/peak `222,203,904 / 1,716,977,664 / 1,990,225,920 bytes`
- peak RSS 증가 `1,768,022,016 bytes`; model cache `629,087,675 bytes`
- GPU/VRAM `NOT_USED / 0`; model weight `611,934,706 bytes`
- contamination/fragmentation/cross-parent violation `0/0/0`; Gold EvidenceChild `100%`
- SEALED FINAL combined `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`,
  `opened=false`, `searchExecuted=false`, `CURRENT_FRESH_BASELINE=NOT_RUN`
- Production, dataset, dependency, migration, frontend, MCP 변경: `0`

## 7. 검증

- Python scorer unit tests: `5 PASS`
- 관련 Structural/Dataset/Engine/robustness/PRZ-027 Java tests: `66 PASS / 0 FAIL`
- official pair export/import tests: 각 `1 PASS`; R1 candidate/provenance parity `PASS`
- `node scripts/verify-oss-readiness.mjs`: `PASS` (16 node tests, external links 97 OK)
- `git diff --check`: `PASS`
- 전체 backend/frontend test: `NOT_RUN` (evaluation-only scope)

첫 export 명령 2회는 각각 PowerShell `-D` parsing과 test-worker property 전달 문제로 test 첫 assertion에서
중단됐으며 dataset/BGE 실행과 artifact 생성은 0건이었다. 환경변수 전달 fix를 별도 commit한 뒤 official
export·score·import를 각 1회 실행했다.

## 8. 최종 결정

최종 판정은 `NO_GO`다. Candidate Recall@20과 구조 안전성, CPU 운영 target은 만족했지만 combined
Top1/MRR 순증이 없고 MRR·nDCG@5·Recall@5·user-macro 및 충분 slice가 회귀했으며 wins가 losses를
초과하지 않았다. 이 Cross Encoder baseline은 Search V3에 채택하지 않는다. `QueryPlannerAllowed=false`;
QueryPlanner 실험으로 넘어가면 안 된다. 다른 reranker/model 비교도 이번 Phase에서 `NOT_RUN`이다.
