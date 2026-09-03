# PRZ-045 Evidence

## 최종 판정

`TOP2_AGGREGATION_PRODUCTION_PASS`

Search V3가 선택한 최대 5개 원문 근거의 집합을 유지하면서, 문서별 비중복 Passage 최대 2개 평균으로
최종 문서 순서만 정하도록 구현했다. Production Search V2, 후보 Top20, `CHILD_DENSE_V1`, typed 상태와
provenance는 변경하지 않았다.

## 구현

- 단일 Passage 문서: 기존 cosine score 유지
- 복수 Passage 문서: 기존 순서에서 처음 만나는 비중복 Passage 최대 2개의 단순 평균
- 중복: 같은 EvidenceChild ID 또는 document version·page·code-point source span이 겹치는 Passage 제외
- 동점: 기존 최상위 Passage rank, document ID 순
- 적용 위치: Child/typed selection 이후. 선택된 EvidenceChild identity는 유지하고 문서 순서만 변경

## 검증 결과

| 검증 | 결과 |
| --- | --- |
| focused unit | `PASS`, 10 tests, failures/errors/skips `0/0/0` |
| Search V3 PostgreSQL integration | `PASS`, 46 tests, failures/errors/skips `0/0/0` |
| 실제 BGE-M3 integration | `PASS`, 위 46건에 포함 |
| 전체 backend unit | `PASS`, 676 tests, failures/errors/skips `0/0/20` |
| 전체 backend integration | `PASS`, 164 tests, failures/errors/skips `0/0/9` |
| 전체 backend `check` | `PASS`, 10 tasks |
| OSS readiness | `PASS`, Markdown 267 files·local links 849·external links 97 |
| `git diff --check` | `PASS` |
| OpenSQL | `NOT_RUN` |

첫 focused 실행에서 평균 `0.85`, `0.55`의 이진 부동소수점 표현을 exact equality로 비교한 테스트 2건이
실패했다. 정책이나 구현은 바꾸지 않고 assertion을 `1e-12` 허용 오차로 고친 뒤 10/10 통과했다.

## 90문항 회귀 진단

Git에 추가하지 않은 기존 local holdout과 실제 PostgreSQL·BGE-M3·Production Search V3 runtime을 사용했다.
임시 진단 runner는 실행 직후 삭제했다.

| 지표 | 구현 전 V3 | Top2 Production 구현 |
| --- | ---: | ---: |
| Top1 | `85/90` | `89/90` |
| MRR@5 | `0.9722` | `0.994444444` |
| Recall@5 | `90/90` | `90/90` |
| 기존 Top1 보존 | - | `85/85` |

이 값은 기존 90문항 diagnostic의 재현 결과이며 새로운 독립 성능 주장에 사용하지 않는다. 앞서 고정한
fresh 30문항에서는 V2 `25/30`, 기존 V3와 Top2 V3가 각각 `29/30`으로 같았으며 추가 회귀는 없었다.

## 보호 경계

- `SearchV3ShadowQueryRepository`의 SQL·Top20·cosine·owner/ACTIVE 조건 변경 `0`
- Structural parsing, Passage/Child 생성, 480 상한과 embedding 변경 `0`
- Search V2, migration, dependency, frontend, MCP 변경 `0`
- PRZ-044 attempt·contract·receipt·artifact 변경 `0`
- 새 평가 infrastructure와 tracked raw artifact `0`
