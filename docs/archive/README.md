# PRIZM 문서 보관소

> **역사 기록 안내:** 이 디렉터리의 문서는 작성 당시의 실험, 구현 단계와 검증
> 결과를 보존합니다. 현재 제품 동작의 근거로 바로 사용하지 마세요.

현재 구현은 [현재 구현 현황](../project-status.md), 현재 구조는
[아키텍처](../architecture.md), 실행 방법은 [로컬 빠른 시작](../quickstart.md)을
먼저 확인합니다.

## 보관 기준

- 종료되거나 대체된 실험과 비채택 판단
- 이후 구현으로 대체된 초기 기능·검증 snapshot
- 날짜별 개발·평가·운영 검증 기록
- 현재 절차에서는 필요하지 않지만 재현성과 판단 근거를 위해 보존해야 하는 내용

역사 기록의 날짜, 수치, 당시 환경과 `PASS`, `FAIL`, `NOT_RUN`, `NOT_VERIFIED` 같은
판정은 현재 상태에 맞추어 소급 변경하지 않습니다. 현재 설명과 충돌하면 기록을
고치는 대신 현재 문서로 연결하고 적용 범위를 분명히 표시합니다.

## 디렉터리 안내

| 구분 | 내용 |
|---|---|
| [전체 개발 기록](development-log-full-history.md) | 당시 계획·실험·실패와 판정을 포함해 주요 변경과 검증을 시간순으로 보존한 원문 |
| [Spec Registry 도입 전 구현 이력](../../specs/000-pre-spec-implementation-history.md) | `e995a5f` source cut까지 source·migration·test로 확인한 구현 순서 |
| [`experiments/`](experiments/) | 가설, 조건, 결과와 비채택 여부를 남긴 실험 기록 |
| [`verification/`](verification/) | 특정 날짜·소스·환경에서 수행한 구현·호환성 검증 기록 |
| [검색 평가 실행 이력](evaluation/search-evaluation-history.md) | 현재 평가 계약에서 분리한 날짜별 검색 평가 결과 |

`experiments`는 선택지를 검토하고 채택 여부를 판단한 과정에 초점을 둡니다.
`verification`은 정해진 범위와 환경에서 어떤 검사를 실행해 어떤 결과를 얻었는지에
초점을 둡니다. 두 기록 모두 현재 소스에서 다시 실행하지 않았다면 역사적 결과입니다.

현재 평가 도구 사용법은 [검색 품질 평가](../evaluation/search-evaluation.md)에서
확인합니다.
