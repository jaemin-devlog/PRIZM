# PRZ-031 Evidence

## 1. 시작 상태

- branch: `PRZ-031-semantic-evidence-directness`
- 시작 HEAD / PRZ-030: `aca58a6c11b517557d6081756a3ea2cdc5f0550c`
- origin/main: `2c8fd5c0d2f62b154642d703a0970389f8abed8e`
- dependencies: PRZ-029 `f7e4a7ad`, PRZ-026 B3 `1bbc1d76`, PRZ-025 `5f8229f8`
- 시작 working tree: `CLEAN`
- Production 변경: `0`

## 2. Model Selection Audit

2026-09-01 현재 local Ollama `0.33.2`의 `/api/tags`에는 다음 한 모델만 있었다.

| model | digest | size | capability | 판정 |
|---|---|---:|---|---|
| `bge-m3:latest` | `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` | 1,157,672,605 bytes | `embedding` | relation classifier로 부적합 |

`/api/show` 기준 family는 `bert`, parameter size는 `566.70M`, quantization은 `F16`,
license metadata는 MIT였다. 생성형 instruction/chat capability가 없으므로 네 relation을
출력할 수 없다.

repository에는 `scripts/evaluation/run_semantic_support_judge.py`의 evaluation-only Ollama
chat/strict-schema harness와 과거 `qwen3:4b-instruct` 기록이 있다. 그러나 현재
`/api/show`는 이 tag에 `404 model not found`를 반환했다. 과거 tracked artifact도 mutable
tag만 기록하고 exact digest/revision/size/license를 보존하지 않아 이번 model identity
Gate를 충족하지 않는다. OpenAI judge는 local/self-hosted 조건에 맞지 않는다.

추가 evaluation tooling도 확인했다. `scripts/evaluation/run_semantic_nli.py`의 기본
`MoritzLaurer/mDeBERTa-v3-base-mnli-xnli` artifact는 local cache에 없었다. Hugging Face
cache의 `Alibaba-NLP/gte-multilingual-reranker-base`는 config/README/tokenizer metadata
3개, 8,490 bytes뿐이고, `BAAI/bge-reranker-v2-m3`도 2개, 40 bytes뿐이라 model weights가
없다. 두 reranker 계열은 네 relation을 출력하는 instruction classifier가 아니며 GTE는
PRZ-027에서 이미 `NO_GO`였다. 이 audit에서 download나 model inference는 하지 않았다.

결론: `BLOCKED_MODEL_SELECTION`. model download, 다른 모델 시험, inference는 모두 0건이다.

## 3. Candidate / Gold 경계 사전 감사

PRZ-030의 기존 candidate freeze는 다음과 같다.

| suite | query / candidate | canonical SHA-256 | 재사용 경계 |
|---|---:|---|---|
| Original | 21 / 63 | `fe69d2cbbc3d679b49e449d5d2b7a4c7387069d3d0b29b43df8772dc76be6d79` | Gold-free replay 가능 |
| Long-form | 24 / 288 | `0935f6eeaad188005011d25374f012b66e843f34b7653a1ec981645a4e182570` | Gold-free replay 가능 |
| Robustness | 24 / 200 | `20346aea334c7cb662dd459b7ca5b8e44a3a4dffa4382006f892c0c99fd0fba9` | Gold-free replay 가능 |
| Stress | 24 / 200 | `ee3142abfe2097799f03998cb6b7acfd35ebc0c70a58618c43c33cd8ab709da8` | 별도 Gold-free artifact 없음 |

Stress ranking은 Gold-joined PRZ-030 local report에만 남아 있으므로 그 report를 inference
input으로 읽지 않는다. 재개 시 동일 B3/BGE digest로 Stress만 한 번 재생성하고 위 SHA와
exact parity를 확인해야 한다. Original/Long/Robustness는 BGE 재실행 없이 replay한다.

semantic core는 79 query, Top20-or-less candidate 670개이며 Top10 inference 예상 pair는
578개다. 기존 Gold가 판단한 pair는 92개뿐이고 나머지 486개는 `UNJUDGED`다. 따라서 향후
relation accuracy/macro F1은 judged pair만 대상으로 하고 judged coverage를 함께 기록한다.
`UNJUDGED`를 `INSUFFICIENT`로 간주하지 않는다.

PRZ-031 combined candidate input SHA-256: `NOT_CREATED / NOT_RUN`.
instruction/model/output SHA-256: `NOT_CREATED / NOT_RUN`.

독립 사전 audit에서 O10/capture 식, semantic core와 typed-overlap 분리, no-support comparator
정의가 충분히 명시되지 않은 것을 발견했다. 공식 inference 전 spec을 다음처럼 보강했다.

- O10은 Gold Top10만 partition하고 11~20을 보존하며, capture는
  `(D1-D0)/(O10-D0)`, 분모 0은 `NOT_APPLICABLE`, clamp 없음
- Gate는 semantic core 79에만 적용하고 typed-overlap 14는 diagnostic weight 0
- no-support predicted-DIRECT metric은 `NOT_SUPPORTED` 22 query의 D0 Dense Top1과 D1
  final ranked Top1 기준으로 정의하되, D0 classifier comparator가 없어 이번 비교의 감소
  clause는 `NOT_APPLICABLE`
- rank1 retention frozen 분모는 D0 Top1 DIRECT 50건이며 valid partial도 direct-positive 포함

## 4. 실행 상태

| 항목 | 상태 |
|---|---|
| D0 B3 replay | `NOT_RUN` |
| D1 model inference | `NOT_RUN` |
| Gold join | `NOT_RUN` |
| relation accuracy / macro F1 | `NOT_RUN` |
| DIRECT precision / recall | `NOT_RUN` |
| Top1 / MRR / nDCG@5 / user-macro | `NOT_RUN` |
| win / loss / tie / retention | `NOT_RUN` |
| Oracle capture ratio | `NOT_RUN` |
| profession/language/category slices | `NOT_RUN` |
| no-support Dense Top1 / D1 final Top1 diagnostics | `NOT_RUN` |
| latency / RSS / GPU / VRAM | `NOT_RUN` |

실행하지 않은 값을 PASS나 0으로 해석하지 않는다. 최종
`PROMISING / NEEDS_ADJUSTMENT / NO_GO` 판정도 `NOT_RUN`이다.

## 5. SEALED FINAL과 scope

- combined SHA-256:
  `e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383`
- `opened=false`
- `searchExecuted=false`
- `CURRENT_FRESH_BASELINE=NOT_RUN`
- SEALED semantic load/search/prediction/result: `0`
- candidate export, BGE execution, model inference, Gold join: `0`
- `src/main/**`, migration, dependency/build, frontend, MCP, Docker, `v1.0.0`: 변경 `0`

## 6. 재개 결정

현재는 Evidence Selection 통합 Phase로 진행할 수 없다. 별도 승인 아래 exact local
instruction artifact의 digest/revision/license/size를 고정한 뒤 이 PRZ의 input/code/instruction
freeze와 단 한 번의 공식 inference부터 재개해야 한다.

## 7. 검증

| 명령/검사 | 실제 결과 |
|---|---|
| Ollama `/api/version`, `/api/tags`, `/api/show` model audit | `PASS`; version `0.33.2`, 설치 model 1개, Qwen `404` |
| focused `searchEvaluation` unit test 3 suites | `PASS`; 17 tests, failure/error/skip 0 |
| `node scripts/evaluation/search-v3/validate-search-v3-benchmark.mjs` | `PASS`; `FRESH_BENCHMARK_SEED_FROZEN`, sealed search false |
| benchmark validator unit test | `PASS`; 18 tests, failure 0 |
| `node scripts/verify-oss-readiness.mjs` | `PASS`; Markdown 206, tracked safety 1088, verifier tests 16 |
| PRZ-031 Registry link/file existence | `PASS` |

Focused Gradle test의 최초 sandbox 실행은 wrapper distribution network 접근이 거부돼
실패했다. 기존 사용자 Gradle cache 접근을 승인받아 같은 명령을 재실행했고 17 tests가
통과했다. 전체 backend unit/integration, frontend test, model inference benchmark는 이
documentation/model-selection-blocker 범위에서 `NOT_RUN`이다.

실행한 정확한 명령은 다음과 같다.

```powershell
.\gradlew.bat searchEvaluation `
  --tests "com.prizm.search.evaluation.searchv3.structural.Prz030SemanticEvidenceValidationCeilingBenchmarkPolicyTest" `
  --tests "com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreezeTest" `
  --tests "com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleGoldJoinerTest"
node scripts/evaluation/search-v3/validate-search-v3-benchmark.mjs
node --test scripts/evaluation/search-v3/validate-search-v3-benchmark.test.mjs
node scripts/verify-oss-readiness.mjs
git diff --cached --check
```

Registry link 검사는 네 PRZ-031 문서 경로에 PowerShell `Test-Path -LiteralPath`를 적용해
누락 0을 확인했다. scope 검사는 `git diff --cached --name-only`가 Registry와 PRZ-031
문서 네 개만 포함하는지 allowlist로 비교했다.

Model inventory와 blocker 근거에 사용한 read-only PowerShell 절차는 다음과 같다.

```powershell
Invoke-RestMethod -Uri 'http://localhost:11434/api/version' -Method Get
Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -Method Get
$body = @{model='bge-m3:latest'} | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:11434/api/show' -Method Post `
  -ContentType 'application/json' -Body $body
$body = @{model='qwen3:4b-instruct'} | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:11434/api/show' -Method Post `
  -ContentType 'application/json' -Body $body
$hub = Join-Path $env:USERPROFILE '.cache\huggingface\hub'
Get-ChildItem -LiteralPath $hub -Directory | Where-Object {
  $_.Name -match 'gte-multilingual-reranker-base|bge-reranker-v2-m3|mDeBERTa|mnli|xnli'
} | ForEach-Object {
  $files = Get-ChildItem -LiteralPath $_.FullName -Recurse -File
  [pscustomobject]@{
    name = $_.Name
    fileCount = $files.Count
    totalBytes = ($files | Measure-Object Length -Sum).Sum
  }
}
```

Registry/scope 검사는 다음 명령 형태로 수행했다.

```powershell
$prz031Docs = @(
  'specs/PRZ-031-semantic-evidence-directness/spec.md',
  'specs/PRZ-031-semantic-evidence-directness/plan.md',
  'specs/PRZ-031-semantic-evidence-directness/tasks.md',
  'specs/PRZ-031-semantic-evidence-directness/evidence.md'
)
$prz031Docs | Where-Object { -not (Test-Path -LiteralPath $_) }
git diff --cached --name-only
```
