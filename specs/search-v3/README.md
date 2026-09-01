# Search V3 개발 기록

Search V3는 검색 알고리즘을 먼저 정하지 않고, 같은 Fresh Generalization 기준으로 기존 검색과
비교해 순증을 확인한 구성만 남기는 장기 리팩터링 작업이다. 현재 결과는 모두 evaluation/shadow
경로의 검증 기록이며 Production Search V2를 바꾸지 않았다.

## 현재 상태

| 항목 | 상태 |
| --- | --- |
| 통합 개발선 | `refactor/search-v3` |
| 생성 기준 HEAD | `PRZ-034-atomic-evidence-child-selector@500e53453b01b6cf922f221794257eb523a80bd6` |
| Production 검색 적용 | `NOT_RUN` |
| Production 변경 | `0` |
| Shadow 저장 구조 | PRZ-037 V18 migration `SHADOW_STORAGE_READY` |
| SEALED FINAL | `opened=false`, `searchExecuted=false` |
| Fresh baseline | `CURRENT_FRESH_BASELINE=NOT_RUN` |

현재까지 남은 최소 구성은 구조 기반 `EvidenceChild`, B3 `RetrievalPassage`, BGE-M3 Dense 검색,
Typed Validation과 Evidence Selection, 같은 Passage 안에서 동작하는 `CHILD_DENSE_V1`이다.
이는 Production 채택 구성이 아니라 다음 검증의 기준선이다.

## PRZ 흐름

| PRZ | 검증 내용 | 결론 |
| --- | --- | --- |
| [PRZ-025](../PRZ-025-search-v3-foundation/evidence.md) | 평가·근거 계약과 Fresh benchmark seed 봉인 | `FRESH_BENCHMARK_SEED_FROZEN`, Registry `IN_PROGRESS` |
| [PRZ-026](../PRZ-026-structural-parsing-parent-child/evidence.md) | 구조 분할, B3 RetrievalPassage, C1 Parent Context | B3 `PROMISING`; C1 공식 판정 `NEEDS_ADJUSTMENT`, 후속 기준선에서 비채택 |
| [PRZ-027](../PRZ-027-cross-encoder-reranking/evidence.md) | GTE Cross Encoder 재정렬 | `NO_GO` |
| [PRZ-028](../PRZ-028-typed-exact-constraints/evidence.md) | 숫자·날짜·식별자 조건 처리 | 순위 구성에서는 미채택, `EVIDENCE_VALIDATION_ONLY` |
| [PRZ-029](../PRZ-029-evidence-validation-selection/evidence.md) | 조건 검증 뒤 근거 선택 | `PROMISING` |
| [PRZ-030](../PRZ-030-semantic-evidence-validation-ceiling/evidence.md) | 의미 검색의 Oracle 상한과 병목 위치 | `BUILD_SEMANTIC_VALIDATOR`; 실제 validator 성공이 아님 |
| [PRZ-031](../PRZ-031-semantic-evidence-directness/evidence.md) | Qwen 직접성 판별 | D1 `PROTOCOL_NO_GO`, D2 `NO_GO` |
| [PRZ-032](../PRZ-032-minimal-v3-shadow-comparison/evidence.md) | 기존 Search V2와 최소 V3 비교 | `MIXED_NEEDS_NEXT_CAPABILITY`; Child 선택 병목 확인 |
| [PRZ-033](../PRZ-033-atomic-evidence-child-selection-ceiling/evidence.md) | Passage 내부 Child 선택 Oracle 상한 | `BUILD_CHILD_SELECTOR` |
| [PRZ-034](../PRZ-034-atomic-evidence-child-selector/evidence.md) | 동일 BGE-M3로 Passage 내부 Child 선택 | `PROMISING` |
| [PRZ-035](../PRZ-035-child-embedding-operation-strategy/evidence.md) | Child embedding 계산·보관 시점 비교 | `PRECOMPUTE_CHILD_EMBEDDINGS` |
| [PRZ-036](../PRZ-036-search-v3-index-lifecycle/evidence.md) | generation, manifest, activation·복구 생명주기 | `SHADOW_INDEX_LIFECYCLE_READY` |
| [PRZ-037](../PRZ-037-search-v3-shadow-storage/evidence.md) | PostgreSQL shadow 저장 구조와 제약 | `SHADOW_STORAGE_READY` |

PRZ-027은 PRZ-026에서 갈라진 `NO_GO` side branch이며 PRZ-028 이후 commit의 조상이 아니다.
PRZ-031은 `NO_GO` 실험 기록을 포함하지만 그 다음 PRZ가 같은 branch 계보에서 이어졌다. 실패
결과도 설계 근거이므로 branch와 문서를 그대로 보존한다.

## 실제 Git 계보

```text
origin/main
└─ PRZ-025
   └─ PRZ-026
      ├─ PRZ-027  NO_GO side branch
      └─ PRZ-028
         └─ PRZ-029
            └─ PRZ-030
               └─ PRZ-031
                  └─ PRZ-032
                     └─ PRZ-033
                        └─ PRZ-034
                           └─ refactor/search-v3
                              └─ PRZ-035
                                 └─ PRZ-036
                                    └─ PRZ-037  SHADOW_STORAGE_READY
```

## Search V3 branch 운영

저장소의 일반 원칙은 `main`을 유일한 장기 branch로 유지하는 것이다. 다만 Search V3 안정화
기간에는 이번 작업에서 명시적으로 승인한 `refactor/search-v3`를 전용 통합선으로 쓴다. 이
branch는 릴리스 기준선이나 Production 배포 승인을 뜻하지 않는다.

새 Search V3 PRZ는 다음 순서로 진행한다.

1. `refactor/search-v3`의 검증된 HEAD에서 `PRZ-###-...` branch를 만든다.
2. 해당 PRZ의 계약과 평가 Gate를 먼저 고정한다.
3. 검증을 통과한 변경만 `refactor/search-v3` 통합 대상으로 삼는다.
4. `NO_GO` 실험은 원격 branch와 근거 문서에 보존하되 채택 구성에는 넣지 않는다.
5. SEALED FINAL과 release Gate를 통과하기 전에는 `main` 병합을 검토하지 않는다.

따라서 다음 Search V3 PRZ는 `main`이 아니라 `refactor/search-v3`에서 시작한다. 최종 cutover는
Fresh baseline과 finalist V3의 독립 비교, 안전·품질·운영 Gate를 모두 통과한 뒤 별도 절차로
검토한다.
