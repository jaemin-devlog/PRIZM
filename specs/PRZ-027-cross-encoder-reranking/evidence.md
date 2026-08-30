# PRZ-027 Evidence

- 상태: `IN_PROGRESS / BENCHMARK_NOT_RUN`
- branch / 시작 HEAD: `PRZ-027-cross-encoder-reranking` / `a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9`
- origin/main: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- dependencies: `PRZ-025@5f8229f88251938dc5b34588676cc69edf409c99`, `PRZ-026 B3@a7dbb12...`
- 시작 working tree: clean
- Production / SEALED FINAL search / CURRENT_FRESH_BASELINE: `0 / NOT_RUN / NOT_RUN`

## 1. 실행 전 model metadata

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
- R0/R1 result, raw pair scores, QueryPlanner: `NOT_RUN`
