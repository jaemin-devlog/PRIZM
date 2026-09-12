# PRZ-016 검색 문서 안내

이 페이지는 현재 제품 검색을 설명하는 문서와 PRZ-016의 연구·평가 기록을 구분해
안내한다. 먼저 [현재 검색 요약](SEARCH-FINAL-SUMMARY.md)을 읽고, 구현 순서가 필요하면
[현재 검색 아키텍처](SEARCH-FINAL-ARCHITECTURE.md)를 확인한다.

PRZ-016의 형식 상태는 `IN_PROGRESS`다. P15 `NOT_VERIFIED`와 제품에 적용하지 않은 P16
`NEEDS_ADJUSTMENT`를 소급해서 닫지 않는다. P17 평가셋은 전체 unit과 AUDIT를 통과했지만
실제 검색 방식 비교가 `NOT_RUN`이다. P17은 평가 자산 작업이며 Production 검색 기능을
바꾸지 않는다.

## 현재 검색을 확인할 때

| 문서 | 역할 |
|---|---|
| [현재 검색 요약](SEARCH-FINAL-SUMMARY.md) | 현재 제품 범위, 검색 흐름, 비범위와 핵심 연구 판정 |
| [현재 검색 아키텍처](SEARCH-FINAL-ARCHITECTURE.md) | 현재 `src/main`의 구성 요소, fallback·rescue·localization 순서 |
| [Spec](spec.md) | PRZ-016의 보존 계약, 단계별 범위와 acceptance criteria |
| [Plan](plan.md) | 단계별로 검토한 설계와 실행 계획. 현재 호출 순서는 아키텍처 문서를 우선 |
| [Tasks](tasks.md) | 완료·미완료 검증 체크리스트와 당시 실행 기록 |
| [Evidence](evidence.md) | benchmark 수치, 격리 결과, 최종 판정과 알려진 한계 |

`Spec`, `Plan`, `Tasks`, `Evidence`에는 PRZ-016 진행 당시의 단계별 기록도 함께 남아
있다. 현재 제품 동작과 충돌하면 위 두 현재 문서와 실제 source·test를 우선한다.

## 반드시 보존하는 판정

| 항목 | 상태 | 상세 근거 |
|---|---|---|
| 전체 | `IN_PROGRESS` | [Evidence](evidence.md) |
| P5 | `FAIL` | [P5 최종 검증](p5-final-holdout/final-validation.md) |
| P6 | `NO_GO` | [P6 Evidence](p6-retrieval-shadow/evidence.md) |
| GPT Judge | `NO_GO` | [GPT Judge Evidence](gpt-evidence-judge-shadow/evidence.md) |
| P7-B | `FAIL` | [P7-B Evidence](p7-b-independent-generalization/evidence.md) |
| P15 | `NOT_VERIFIED` | [Evidence의 P15 기록](evidence.md#p15-pdf-document-confirmation-ux) |
| P16 | `NEEDS_ADJUSTMENT`, 제품 미적용 | [P16 Evidence](p16-literal-candidate-phase-a/evidence.md) |
| P17 | `IMPLEMENTED — VERIFY_AND_AUDIT_PASS`, 통합·실제 검색 비교 `NOT_RUN` | [P17 Spec](p17-prizm-dedicated-dataset/spec.md) |

PostgreSQL·pgvector 결과를 OpenSQL 결과로 바꾸어 쓰지 않는다. 각 단계의 owner·`ACTIVE`
격리 결과와 제품 경로·평가 전용 경로의 구분도 해당 evidence에 기록된 범위 그대로
해석한다.

## 연구와 평가 기록

- [검색 R&D 전체 기록](PRZ-016-SEARCH-RND-HISTORY.md) — 당시 단계, 실패, rollback과
  비채택 판단을 시간순으로 보존한다.
- `p0-benchmark/`부터 `p5-final-holdout/` — 초기 기준선과 순차 개선, 최종 holdout 실패.
- `p6-retrieval-shadow/`, `gpt-evidence-judge-shadow/` — 제품 미적용 shadow 실험.
- `p7-b-independent-generalization/`과 `p7-*` — 독립 일반화, trace, semantic shadow와
  frozen 평가 기록.
- `p16-literal-candidate-phase-a/` — literal candidate 실험과 `NEEDS_ADJUSTMENT` 근거.
- `p17-prizm-dedicated-dataset/` — 프로젝트·식별자·source fact가 겹치지 않는 A/B/C
  cohort, 114문서·300문항으로 만든 PRIZM 전용 합성 평가셋의 계약·계획·검증 상태.
  실제 검색 방식 비교는 `NOT_RUN`.
- [과거 통합 요약](history/2026-08-search-integration-summary.md)과
  [과거 통합 아키텍처](history/2026-08-search-integration-architecture.md) — 현재 소스에서
  제거된 structured claim evaluator를 포함하던 당시 snapshot의 원문.

## 경로를 유지하는 검증 자산

frozen dataset, ground truth, manifest, raw JSON·JSONL, CSV·TSV, PDF·TXT·PNG, benchmark
runner와 평가용 Java/MJS/Python은 이동하거나 다시 저장하지 않는다. `build.gradle`, test,
script, `.gitattributes`, OSS readiness fixture와 manifest hash가 이 경로들을 사용한다.
과거 raw 결과에 포함된 로컬 절대 경로도 byte 보존 대상이며 현재 환경 경로로 해석하지
않는다.
