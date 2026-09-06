# PRZ-042 Search V3 Final Evaluation

- 상태: `VERIFIED / V3_NO_GO`
- 결과 범위: `SEED_FINAL_PROTOCOL_RESULT`
- branch: `PRZ-042-search-v3-final-evaluation`
- 기준: `0e95472bb68f72accf0d6b2171c22f0719fe6941`
- 선행 계약: [PRZ-025 Search V3 기반 계약](../../research/PRZ-025-search-v3-foundation/spec.md)
- 선행 runtime: [PRZ-041 Search V3 Runtime Completion](../../runtime/PRZ-041-search-v3-runtime-completion/spec.md)
- Production Search V2 적용: `NO_CHANGE`
- 공식 SEALED 실행: `COMPLETED_ONCE`

## 목적과 결론

현재 Production Search V2와 PRZ-041의 실제 Search V3 shadow runtime을 같은 SEALED 입력과
Gold로 비교했다. source, model, 입력, 지표와 Gate를 결과 전에 동결했으며 공식 실행은 한 번만
수행했다.

Search V3는 Recall@5 `1.0`과 서로 다른 경험의 혼합 `0`을 유지했지만, Direct Top1, MRR,
nDCG@5가 V2보다 낮았다. typed evidence precision과 localization 절대 기준도 통과하지 못했다.
최종 판정은 `V3_NO_GO`이며 Search V2를 유지한다.

이번 결과는 2-user/3-TXT/8-KO-query seed에서 얻은 `SEED_FINAL_PROTOCOL_RESULT`다. release-grade
표본이 아니므로 결과가 좋았더라도 `V3_ADOPT`는 불가능했다.

## 비교 대상

### Search V2

질문은 현재 Production의 `SearchService`, `VectorSearchRepository`, 검색 profile,
fallback/rescue와 localization 경로로 실행했다. 비교 색인은 실제 `TextChunker`, embedding과
repository 구성요소를 사용했지만 `DocumentIndexingProcessor` 전체 경로는 아니었다. 따라서
색인 시간과 storage 수치는 관측값이며 공식 Gate는 `NOT_ASSESSED`다.

### Search V3

PRZ-041의 실제 shadow runtime을 그대로 사용했다.

```text
EvidenceChild
→ B3 RetrievalPassage
→ BGE-M3 Dense Top20
→ Typed Validation/Selection
→ Top5 Passage 내부 CHILD_DENSE_V1
→ 최대 5개 원문 근거
```

새 model, reranker, sparse 검색, query rewrite나 tuning은 추가하지 않았다.

## 입력·Gold 경계

SEALED `questions.json`에는 질문과 annotation이 함께 있다. 파일 접근을 물리적으로 분리했다고
주장하지 않는다. 공식 attempt 뒤 query ID, 질문 원문, user bundle과 언어만 runtime DTO로
projection했으며 answerability, category, constraint와 expected evidence는 prediction에 사용하지
않았다. prediction을 create-new로 저장하고 hash를 검증한 뒤에만 Gold를 join했다.

실행 순서는 다음과 같았다.

1. contract, manifest, source와 model hash 검증
2. `attempt.json` create-new 생성
3. `input-opened-receipt.json`
4. `search-started-receipt.json`
5. prediction 저장·검증과 `predictions-frozen-receipt.json`
6. Gold join, metric/Gate 계산
7. `completion-receipt.json`

`failure-receipt.json`은 생성되지 않았다. 공식 attempt는 정확히 한 번 실행했으며 재실행하지
않는다.

## Frozen Gate와 결과

| Gate | 동결 기준 | 결과 |
| --- | --- | --- |
| Release adequacy | independent user bundle `>= 50`, 대표 profession/language/PDF, 독립 adjudication | `FAIL` |
| Hard Safety | owner/inactive/wrong-version/unauthorized/cross-parent 위반 각각 `0` | `PASS` |
| Primary | user-macro Top1 delta `>= +0.03`, paired 95% CI lower `> 0` | `NOT_ASSESSED` |
| Secondary | query-micro Top1/MRR/nDCG@5/Recall@5, user-macro MRR delta 각각 `>= -0.01` | `FAIL` |
| Adequate slice | user `>= 3`, DIRECT-positive query `>= 5`, delta `>= -0.05` | `NOT_ASSESSED` |
| Localization | V2 대비 delta `>= -0.01`, V3 precision `>= 0.95` | `FAIL` |
| Query p95 | `V3 <= min(V2 * 1.5, V2 + 100 ms)` | `PASS` |
| Indexing/storage | comparable full path에서 각각 `<= 3x` | `NOT_ASSESSED` |
| 추가 model/service/필수 GPU | 각각 `0` | `PASS` |
| Typed contract | state 정확성, false NONE `0`, wrong constraint `0`, precision `>= 0.95` | `FAIL` |
| No-answer/PDF final quality | representative final data 필요 | `NOT_ASSESSED` |

paired bootstrap은 user bundle 단위 sample `10,000`, seed `42042`로 실행했다. diagnostic delta는
`-0.25`, 95% CI는 `[-0.5, 0.0]`이었다.

## SEALED 상태

공식 receipt 기준 실제 상태는 다음과 같다.

- `opened=true`
- `searchExecuted=true`
- `CURRENT_FRESH_BASELINE=EXECUTED`

기존 manifest는 실행 상태를 기록하는 mutable 파일이 아니다. manifest bytes와 SHA-256은
불변이며 내부의 봉인 당시 `mutable=false`, `opened=false`, `searchExecuted=false`도 그대로다.
실제 실행 상태는 append-only receipt가 사실 기준이다.

## 확인된 protocol 한계

create-new receipt는 같은 run directory의 재실행을 차단했다. 그러나 verifier가 contract의
`runDirectory`와 `officialRunsAllowed`를 강제하지 않고 Gradle의 `-Pprz042RunDir` override를
허용한다. 다른 경로를 지정하는 재실행까지 자동 차단하지 못한다. 실제 재실행은 `0`이지만,
one-shot path binding은 불완전한 blocking finding으로 남긴다.

## 비범위와 후속 경계

- Search V3를 Production 기본 검색으로 전환하지 않는다.
- Search V2, API, frontend, MCP, migration과 Production 설정을 바꾸지 않는다.
- 결과를 보고 finalist, Gate, query/Gold를 고치거나 같은 SEALED를 다시 실행하지 않는다.
- 다음 실험이나 cutover는 새 계약과 새 평가 자산이 준비된 별도 PRZ에서만 검토한다.

상세 수치와 artifact hash는 [evidence.md](evidence.md)에 기록한다.
