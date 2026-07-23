# PRIZM Spec Registry

`specs/`는 PRIZM의 기능 의도, 구현 계획과 검증 근거를 연결하는 registry다. spec 문서 자체를 구현 증거로 사용하지 않으며, 실제 상태는 항상 source code, Flyway migration과 실행 가능한 test로 판정한다.

## 상태

| 상태 | 의미 |
|---|---|
| `AS_BUILT_BASELINE` | spec registry 도입 전에 이미 존재하던 기능을 현재 구현 근거로 사후 기록한 기준선 |
| `PLANNED` | 구현 전 요구사항과 범위가 합의된 상태 |
| `IN_PROGRESS` | 구현 또는 검증이 진행 중인 상태 |
| `IMPLEMENTED_UNVERIFIED` | 코드가 있으나 필수 환경 검증이 완료되지 않은 상태 |
| `VERIFIED` | 요구사항과 필수 자동·환경 검증을 모두 충족한 상태 |
| `DEFERRED` | 범위와 재개 조건을 기록하고 뒤로 미룬 상태 |
| `REJECTED` | 검토 또는 실험 후 채택하지 않은 상태 |

`AS_BUILT_BASELINE`은 과거의 계획 문서가 아니다. 해당 기능을 위해 존재하지 않았던 Issue·PR·review를 새로 만들거나 과거에 있었던 것처럼 기록하지 않는다. 실제로 존재하는 Git 이력만 근거로 사용한다.

환경별 검증 상태는 다음처럼 별도로 기록한다.

| 검증 상태 | 의미 |
|---|---|
| `PASS` | 표시한 source commit에서 해당 명령·환경 검증을 실행해 통과 |
| `FAIL` | 검증을 실행했으나 실패 |
| `NOT_RUN` | 해당 환경이나 명령을 실행하지 않음 |
| `HISTORICAL_PASS_NOT_RERUN` | 이전 실제 성공 기록은 있으나 표시한 기준선 작업에서는 다시 실행하지 않음 |

## 문서 규칙

- ID는 `PRZ-###` 형식을 사용하고 [이 registry](#registry)에서 유일하게 관리한다.
- 기존 기능은 `AS_BUILT_BASELINE`으로만 backfill한다.
- 새 기능과 observable contract 변경은 구현 전에 `spec.md`를 작성한다.
- 대회 범위 product code에는 구현 전에 `plan.md`와 `tasks.md`가 필수다.
  제품 동작을 바꾸지 않는 문서 전용 수정은 생략 사유를
  `docs/development-log.md`에 기록하고 이 두 파일을 생략할 수 있다.
- `contracts/`와 `quickstart.md`는 해당 수직 슬라이스에 필요할 때만 추가한다.
- 모든 기능은 `evidence.md`에서 요구사항을 source·migration·test·실행 환경·결과와 연결한다.
- `AGENTS.md`가 프로젝트 불변식과 구현 판단 기준의 규범 원본이다. spec에 같은 규칙을 복제해 별도 헌법처럼 운영하지 않는다.
- PostgreSQL 성공을 OpenSQL·OpenProxy·OpenHA 성공으로 바꾸어 표현하지 않는다.
- 안정 상태에서 장기 브랜치는 `main`만 유지한다. branch 이름은 spec ID나 보존 수단이 아니다.

## Registry

| Spec ID | 이름 | 상태 | Source commit | Last verified |
|---|---|---|---|---|
| [PRZ-000](PRZ-000-platform-baseline/spec.md) | Platform foundation and Career Vault baseline | `AS_BUILT_BASELINE` | `e995a5f` | 2026-07-23 |

다음 신규 spec의 우선순위는 [2026 티맥스티베로 지정과제 대응 계획](../docs/contest/2026-tmaxtibero-plan.md)을 따른다. registry에 디렉터리를 미리 대량 생성하지 않고 실제로 착수하는 작은 수직 슬라이스만 추가한다.
