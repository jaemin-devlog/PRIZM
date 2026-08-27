# PRZ-016 현재 검색 요약

> **현재 문서:** 2026-08-27 `origin/main` `4e80417`의 `src/main`을 기준으로
> 작성했다. PRZ-016의 형식 상태는 `IN_PROGRESS`이며, 과거 통합 당시의 요약은
> [역사 snapshot](history/2026-08-search-integration-summary.md)에 원문 그대로 보존한다.

이 문서는 현재 제품 검색을 빠르게 확인하려는 사용자와 개발자를 위한 요약이다.
구성 요소와 호출 순서는 [현재 검색 아키텍처](SEARCH-FINAL-ARCHITECTURE.md), 단계별
수치와 판정은 [Evidence](evidence.md)를 따른다.

## 현재 제품 검색

기본 profile은 `source-dedup-evidence-signals-v1`이다. 검색은 로그인한 사용자의
현재 `ACTIVE` 문서 버전만 대상으로 질문과 관련된 원문을 최대 5건까지 찾는다.
TXT는 텍스트 구간, PDF는 페이지 번호를 함께 반환한다.

현재 흐름은 다음과 같다.

1. 질의를 검증하고 Ollama `bge-m3` 임베딩을 만든다.
2. owner와 `ACTIVE` 범위를 SQL에서 제한한 exact cosine Dense Top20을 조회한다.
3. 직접 구현·완료·identifier 질의에는 strong-identifier corpus guard를 적용한다.
4. source 위치가 겹치는 후보를 먼저 정리한다.
5. 제한된 관련성 신호로 후보 자격을 판정한다.
6. 같은 질문 근거의 반복을 정리하고, GENERAL 검색은 `EvidenceQualityReranker`의 제한된
   보정값을 포함해 순위를 정한 뒤 최대 5건을 선택한다.
7. 짧은 단일 exact-token 질의는 정해진 좁은 점수 구간에서 한 건만 복구할 수 있다.
8. 단위가 붙은 숫자 질의는 주변 문맥을 확인한다.
9. 결과가 비고 질의 형태가 허용될 때만 최대 두 개의 자연어 변형으로 후보를 넓힌다.
10. 그래도 비면 단위 숫자의 정확한 경계를 확인하는 rescue를 마지막으로 시도한다.
11. 표시 원문이 같은 결과를 정리한 뒤, 선택 chunk에서 먼저 원문 위치를 찾는다.
12. 선택 chunk가 부족할 때만 같은 owner·document·현재 `ACTIVE` version의 주변 근거를
    확인한다. 가능하면 연속된 원문 1–3문장을 snippet으로 만들고, 문장 분리나 위치화가
    실패하면 선택 chunk 원문을 유지한다.

자연어 변형은 후보 검색만 넓힌다. 최종 선택과 위치 찾기는 원래 질의를 기준으로 하며,
주변 근거 확장은 선택과 순위 또는 score를 바꾸지 않는다. 상세 순서는
[현재 검색 아키텍처](SEARCH-FINAL-ARCHITECTURE.md)에 정리했다.

## 제품이 판정하지 않는 것

현재 검색은 관련 원문을 찾는 도구다. 다음 항목은 판정하지 않는다.

- 원문의 진위 또는 사용자가 실제로 수행한 경험인지 여부
- 원문의 주체, 채택 상태, 부정·모순과 metric 의미의 최종 판단
- 채용 요구사항 충족, 직무 적합도 또는 합격 가능성

과거 문서에 등장하는 `StructuredClaimSupportEvaluator`, `QueryClaimRequirements`,
`ClaimSupportDecision`은 현재 `src/main`에 없다. GPT judge, NLI verifier, FTS/RRF와 P16
literal candidate도 현재 제품 검색에 적용되지 않았다.

## 보존하는 연구 판정

아래 판정은 현재 검색의 성공으로 바꾸어 해석하지 않는다.

| 항목 | 보존 판정 | 핵심 경계 |
|---|---|---|
| PRZ-016 전체 | `IN_PROGRESS` | P15와 P16의 미완료·비채택 상태를 보존 |
| P5 | `FAIL` | 48-query holdout Top1 50.00%, Recall@3/5 61.11%, MRR@5 0.5509, Negative FPR 25.00% |
| P6 | `NO_GO` | H1 후보 recall 개선 0pp, H2에서 72-query 회귀 5건. PostgreSQL+pgvector 결과이며 OpenSQL 결과가 아님 |
| GPT Judge | `NO_GO` | Negative FPR 0%였지만 정상 완료 positive 회귀 2건과 incomplete 4건 |
| P7-B | `FAIL` | 48건 Top1 33.33%, Recall@3/5 58.33%, MRR@5 0.4491, Negative FPR 41.67% |
| P15 | `NOT_VERIFIED` | 인증된 실제 PDF 페이지 이동의 브라우저 검증이 남음 |
| P16 | `NEEDS_ADJUSTMENT` | positive 7건 모두 Dense rank 1, literal-only recovery 0. 제품 검색에 미적용 |

P5, P7-B와 P16의 기록에는 owner·`ACTIVE` 격리 확인이 별도로 남아 있다. PostgreSQL
결과와 OpenSQL 결과, 제품 경로와 평가 전용 경로는 각 evidence의 범위를 따라 구분한다.

## 소스와 검증 근거

- [SearchService](../../src/main/java/com/prizm/search/service/SearchService.java)
- [CompositeSearchProfile](../../src/main/java/com/prizm/search/profile/CompositeSearchProfile.java)
- [EvidenceQualityReranker](../../src/main/java/com/prizm/search/profile/EvidenceQualityReranker.java)
- [VectorSearchRepository](../../src/main/java/com/prizm/search/repository/VectorSearchRepository.java)
- [NaturalLanguageQueryFallback](../../src/main/java/com/prizm/search/profile/NaturalLanguageQueryFallback.java)
- [NumericAnchorRescueProfile](../../src/main/java/com/prizm/search/profile/NumericAnchorRescueProfile.java)
- [ShortGeneralExactTokenRescueProfile](../../src/main/java/com/prizm/search/profile/ShortGeneralExactTokenRescueProfile.java)
- [EvidenceExpansionService](../../src/main/java/com/prizm/search/service/EvidenceExpansionService.java)
- [SearchSnippetGenerator](../../src/main/java/com/prizm/search/service/SearchSnippetGenerator.java)
- [SearchServiceTest](../../src/test/java/com/prizm/search/service/SearchServiceTest.java)
- [EvidenceExpansionServiceTest](../../src/test/java/com/prizm/search/service/EvidenceExpansionServiceTest.java)

연구 단계, frozen dataset, manifest, raw 결과와 runner는 [PRZ-016 문서 안내](README.md)의
연구 기록 색인에서 확인한다. 경로와 hash 계약이 있는 자산은 현재 위치를 유지한다.
