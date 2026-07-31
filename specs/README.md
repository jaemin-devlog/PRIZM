# PRIZM Spec Registry

`specs/`는 기능의 의도, 사전 계획, 작업 상태와 검증 근거를 연결한다. Spec
문서는 구현 증거가 아니며 실제 상태는 source code, 적용된 Flyway migration과
실행 가능한 test로 판정한다.

## 상태

| 상태 | 의미 |
|---|---|
| `AS_BUILT_BASELINE` | Spec Registry 도입 전에 존재하던 기능을 현재 구현 근거로 기록한 기준선 |
| `PLANNED` | 구현 전 요구사항과 범위가 합의된 상태 |
| `IN_PROGRESS` | 구현 또는 검증이 진행 중인 상태 |
| `IMPLEMENTED_UNVERIFIED` | 코드가 있으나 필수 환경 검증이 끝나지 않은 상태 |
| `VERIFIED` | 요구사항과 필수 자동·환경 검증을 모두 충족한 상태 |
| `DEFERRED` | 범위와 재개 조건을 기록하고 미룬 상태 |
| `REJECTED` | 검토 또는 실험 후 채택하지 않은 상태 |

환경별 결과는 Spec 상태와 분리한다.

| 결과 | 의미 |
|---|---|
| `PASS` | 표시한 source commit과 환경에서 실제 검증을 실행해 통과 |
| `FAIL` | 검증을 실행했으나 실패 |
| `SKIPPED` | 명시적인 환경 조건 때문에 검증이 실행되지 않음. `PASS`나 구현 증거가 아님 |
| `NOT_RUN` | 해당 환경이나 명령을 실행하지 않음 |
| `NOT_VERIFIED` | 일부 사실은 확인했지만 목표 동작을 입증하지 못함 |
| `HISTORICAL_PASS_NOT_RERUN` | 과거 성공 기록은 있으나 현재 기준선에서 재실행하지 않음 |

## 문서별 역할

| 문서 | 단일 역할 |
|---|---|
| `spec.md` | 목적, 범위, 요구사항, 보존 계약, 제외 범위와 측정 가능한 완료 조건 |
| `plan.md` | 구현·검증 전에 선택한 접근, 예상 변경, 위험, 검증 환경, rollback·중단 조건, dependency·license와 branch·PR 계획 |
| `tasks.md` | ID, 작업, 최종 상태와 결과 문서 링크만 담은 짧은 체크리스트 |
| `evidence.md` | 최종 상태, 기준 source commit, 요구사항별 판정, 실제 환경·명령·결과, GitHub 통합·review와 남은 제한 |

최초 실패와 보완 결과 중 현재 판정에 필요한 핵심 이력은 `evidence.md`에 짧게
남긴다. 날짜별 명령과 상세 과정은 실제 Git commit, PR과 CI 기록으로 확인한다.
실행 결과를 spec·plan에 되돌려 넣거나 긴 실행 로그와 선택 이유를 tasks에
복제하지 않는다.

## 운영 규칙

- ID는 `PRZ-###` 형식으로 이 Registry에서 유일하게 관리한다. 실제 `SPEC`을
  시작할 때만 다음 번호를 발급하며 미래 작업 번호를 예약하지 않는다.
- Registry 도입 전 기능은 `AS_BUILT_BASELINE`으로만 기록한다. 존재하지 않았던
  Issue·PR·review를 만들거나 과거에 있었던 것처럼 기록하지 않는다.
- 새 기능과 observable contract 변경은 구현 전에 `spec.md`를 작성한다.
- 대회 범위 product code에는 구현 전에 `plan.md`와 `tasks.md`가 필요하다.
  제품 동작을 바꾸지 않는 문서 전용 수정은 생략 이유와 확인 결과를 남길 수 있다.
- 요구사항은 `evidence.md`에서 source·migration·test·실행 환경·결과와 연결한다.
- 대회 평가 ID는 evidence에서 실제 근거와 남은 제한에만 연결한다. 공식
  평가항목과 현재 evidence의 연결은
  [요구사항·평가기준 추적표](../docs/contest/2026-requirements-traceability.md)를
  따른다. 내부 예상 점수는 공개 문서에서 관리하지 않는다.
- Issue, PR, CI, merge와 review URL은 실제 존재할 때만 기록한다.
  `REVIEW_NOT_AVAILABLE_SOLO`는 review evidence가 아니다.
- [AGENTS.md](../AGENTS.md)가 프로젝트 불변식의 원본이다. Spec에 같은 규칙을
  별도 헌법처럼 복제하지 않는다.
- PostgreSQL 성공을 OpenSQL·OpenProxy·OpenHA 성공으로 바꾸어 표현하지 않는다.
- 안정 상태에서 장기 branch는 `main`만 유지한다. Branch 이름은 보존 수단이 아니다.

## Registry

| Spec ID | 이름 | 상태 | Source commit | Last verified |
|---|---|---|---|---|
| [PRZ-000](PRZ-000-platform-baseline/spec.md) | 플랫폼 기반 및 Career Vault 기준선 | `AS_BUILT_BASELINE` | `e995a5f` | 2026-07-23 |
| [PRZ-001](PRZ-001-search-evaluation-integrity/spec.md) | 검색 평가 분할 및 지표 정합성 | `VERIFIED` | `36c8610` | 2026-07-24 |
| [PRZ-002](PRZ-002-open-source-readiness/spec.md) | 오픈소스 준비: 출처·라이선스·기여 기준선 | `VERIFIED` | `f54e3d9` | 2026-07-30 |
| [PRZ-003](PRZ-003-opensql-single-node-gate/spec.md) | OpenSQL 단일 노드 검증 환경 | `VERIFIED` | `777e184` | 2026-07-30 |
| [PRZ-004](PRZ-004-clean-clone-demo/spec.md) | 안전한 demo USER와 clean-clone 전체 흐름 | `IN_PROGRESS` | `936e957` 기준 local 작업 | `NOT_RUN` |

다음 신규 Spec의 우선순위는
[2026 티맥스티베로 지정과제 대응 계획](../docs/contest/2026-tmaxtibero-plan.md)을
따른다. 실제로 착수하는 작은 수직 슬라이스만 추가한다.
