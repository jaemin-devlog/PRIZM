# PRIZM Contributor and AI Agent Workflow

## 문서 역할

이 문서는 사람 기여자와 AI 에이전트가 PRIZM을 변경할 때 따르는 상세 절차를
정의한다. 프로젝트의 핵심 불변식은 [AGENTS.md](../AGENTS.md)가 규정하며, 이
문서는 불변식을 실제 작업 단계와 검증 기록으로 연결한다.

제품 기능, API 계약과 구현 완료 여부는 이 문서가 아니라 소스 코드(source
code), Flyway 마이그레이션(migration)과 실행 가능한 테스트(test)로 판단한다.
공식 평가항목과 현재 evidence의 연결은
[요구사항·평가기준 추적표](contest/2026-requirements-traceability.md)를 따른다.
내부 예상 점수와 Assessment 이력은 공개 문서에서 관리하지 않는다. 이
workflow는 PRIZM 내부 품질 정책이지 공식 대회 요구사항이 아니다.

## 작업 전에 지킬 네 가지 원칙

1. **가정과 영향을 먼저 확인한다.** 결과, 보안, 데이터 계약이나 외부 상태를
   바꿀 수 있는 모호함은 선택지와 영향을 밝히고 필요하면 사용자 판단을 구한다.
2. **요청을 해결하는 최소 변경을 선택한다.** 단일 사용처를 위한 추상화나 아직
   요청하지 않은 미래 기능을 추가하지 않는다.
3. **관계없는 작업을 보존한다.** 요청과 직접 관계없는 코드, 문서, 포맷, 생성
   파일과 사용자 변경은 수정하지 않는다.
4. **성공 조건을 먼저 정한다.** 변경 전에 관찰 가능한 완료 조건과 검증 명령을
   정하고, 실제 결과를 확인한 뒤에만 완료로 기록한다.

이 원칙은 개발 환경에 설치한 Karpathy-inspired Codex skill의 작업 자세를
PRIZM에 맞게 정리한 것이다. 참고한 공개 자료는
[multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills)의
MIT 표기 `karpathy-guidelines` skill이다. 해당 skill 파일이나 외부 실행 파일은
PRIZM 배포물에 포함하지 않으며, 이 원칙은 런타임 기능이나 대회 구현 증거가
아니다.

## 어떤 절차를 적용하는가

### 전체 7단계가 필요한 변경

다음 변경은 `ORIENT -> SPEC -> PLAN -> IMPLEMENT -> VERIFY -> AUDIT ->
INTEGRATE`를 모두 적용한다.

- 새 기능
- 사용자가 관찰할 수 있는 동작·API·데이터 계약 변경
- Flyway migration
- 인증, 권한, 사용자 소유권과 파일 안전성을 포함한 보안 변경
- Docker, DB, OpenSQL, 배포와 CI를 포함한 인프라 변경
- 대회 제출 기능
- 여러 모듈이나 핵심 데이터 흐름을 바꾸는 큰 구조 리팩터링
- 기존 `AS_BUILT_BASELINE` 또는 `VERIFIED` 계약을 실질적으로 고치는 작업

### 축소 절차가 가능한 변경

아래 작업은 제품 동작을 바꾸지 않는다는 사실을 확인한 뒤
`ORIENT -> IMPLEMENT -> VERIFY` 또는 사용자가 명시한 문서 전용 단계로 줄일 수
있다.

- 오타와 맞춤법
- 깨진 로컬 링크
- 기준일·검증일처럼 이미 확인된 날짜의 현행화
- 구현을 바꾸지 않는 설명 개선과 문서 역할 분리
- 이미 존재하는 사실의 잘못된 표현 교정

축소 절차는 새 기능, 동작 계약, migration, 설정, dependency, 테스트 동작,
보안, 소유권, 인프라 또는 실행 환경 주장을 바꾸는 데 사용할 수 없다. 작업 중
이런 영향이 발견되면 즉시 중단하고 `SPEC` 또는 `PLAN`으로 돌아간다.

문서 전용 변경도 다음을 남긴다.

- 수정한 파일과 수정하지 않은 인접 파일
- 확인한 source·migration·test 또는 기존 evidence
- 실행한 검증 명령과 정확한 결과
- 제품 테스트를 실행하지 않았다면 그 이유와 `NOT_RUN` 사실
- 새 Spec·Plan을 생략했다면 제품 동작이 바뀌지 않는다고 판단한 근거

장기적인 설계·workflow 결정이나 의미 있는 구현 결과는
[개발 기록](development-log.md)에 짧게 남긴다. 단순 오타나 링크 교정까지 기록을
늘리지는 않는다.

## 공통 진행 규칙

- 작업을 시작할 때 허가된 단계와 목표를 밝힌다.
- 사용자가 처음부터 끝까지 실행하도록 명시하지 않았다면 한 단계의 Gate를
  통과한 뒤 멈춘다.
- 단계가 바뀔 때 완료한 일, 현재 작업, 남은 단계와 실패 여부를 알린다.
- 한 단계의 Gate가 충족되지 않으면 다음 단계나 완료 상태로 넘어가지 않는다.
- `PASS`, `FAIL`, `SKIPPED`, `NOT_RUN`, `NOT_VERIFIED`,
  `HISTORICAL_PASS_NOT_RERUN`을 서로 바꾸어 쓰지 않는다.
- 관련 `AGENTS.md`와 사용자 지시가 충돌하면 사용자에게 충돌을 설명하고
  임의로 규칙을 약화하지 않는다.

## 1. ORIENT

목표는 현재 사실, 변경 범위와 위험을 수정 전에 고정하는 것이다.

필수 확인:

1. 현재 branch, HEAD, 원격 기준선과 작업 트리 변경
2. [AGENTS.md](../AGENTS.md), [문서 안내](README.md),
   [현재 구현 현황](project-status.md), [Architecture](architecture.md),
   [개발 로드맵](roadmap.md), [Spec Registry](../specs/README.md)
3. 관련 spec·evidence, source, migration, 설정과 test
4. 대회 작업이면 [대회 계획](contest/2026-tmaxtibero-plan.md),
   [요구사항·평가기준 추적표](contest/2026-requirements-traceability.md)와 관련
   라이선스 감사
5. 사용자 변경, 보존할 공개 계약, ownership·보안·migration·라이선스 영향
6. 수정할 정확한 파일과 실행할 검증 명령

출력과 Gate:

- 현재 동작과 미구현 경계
- 가정, 가능한 선택지와 선택 이유
- 예상 변경 파일과 건드리지 않을 파일
- 성공 조건, 검증 환경과 명령
- blocking ambiguity 없음

`ORIENT`에서는 파일을 수정하지 않는다.

## 2. SPEC

새 기능, 관찰 가능한 계약 변경 또는 기존 검증 계약에 대한 실질적인 교정은 새
`PRZ-###` 또는 현재 진행 중인 관련 Spec에서 정의한다.

`spec.md`에 기록할 내용:

- 사용자 시나리오와 문제
- 범위와 명시적 비범위
- 요구사항과 보존할 기존 계약
- 보안·ownership·migration·호환성 영향
- 측정 가능한 acceptance criteria
- 필요한 source·test·환경·문서·라이선스 근거

새 ID는 실제 `SPEC`을 시작할 때만 [Spec Registry](../specs/README.md)에서
할당한다. 미래 작업 번호를 미리 예약하지 않는다. 완료된 과거 작업을 위해
Issue나 Spec을 소급 생성하지 않는다.

제품 동작을 바꾸지 않는 문서 전용 교정은 새 Spec을 생략할 수 있다. 그 경우
생략 이유와 확인한 구현 근거를 작업 보고에 남긴다.

Gate:

- 요구사항과 제외 범위가 모순되지 않음
- acceptance criteria를 실행 결과로 판정할 수 있음
- 보존할 계약과 필요한 환경이 명시됨

## 3. PLAN

대회 범위 제품 코드에는 구현 전에 `plan.md`와 `tasks.md`를 작성한다.

`plan.md`에 기록할 내용:

- 예상 파일·API·데이터 흐름 변경
- Flyway forward-only 전략
- 인증·ownership·보안 영향
- dependency·license·배포 경계 영향
- 단위·통합·실환경 검증 방법
- 실패·복구와 rollback 전략
- 임시 branch, commit과 PR 계획

`tasks.md`는 구현·test·문서·검증 순서를 실제 파일 경로와 함께 나눈다. 범위가
달라지면 구현을 계속하지 말고 `SPEC` 또는 `PLAN`을 갱신한다.

제품 동작이 그대로인 문서 전용 변경은 `plan.md`와 `tasks.md`를 생략할 수 있다.
문서 수정 목록, 사실 근거와 검증 방법을 작업 보고에서 먼저 고정해야 한다.

Gate:

- 모든 acceptance criterion이 작업과 test에 연결됨
- migration·security·ownership·license 영향이 빠지지 않음
- 실패했을 때 되돌리거나 중단할 방법이 있음

## 4. IMPLEMENT

전체 절차가 필요한 제품 변경은 최신 `main`에서 임시 `PRZ-###-<slug>` branch를
만들어 진행한다. 축소된 문서 작업은 사용자가 허가한 현재 branch 범위를 따른다.

구현 규칙:

- 승인된 최소 수직 슬라이스만 수정한다.
- 관계없는 사용자 변경과 생성 파일을 보존한다.
- 이미 적용한 Flyway migration을 수정하지 않는다.
- 새 동작에는 실행 가능한 test를 추가하거나 기존 test를 갱신한다.
- `tasks.md`를 실제 진행 상태와 맞춘다.
- 비밀정보, 원본 파일, 모델, DB volume과 공급사 전용 자산을 저장소에 넣지
  않는다.
- 구현이 승인 범위를 실질적으로 넘으면 `SPEC` 또는 `PLAN`으로 돌아간다.

Gate:

- 변경 줄이 승인된 요구사항이나 검증 실패와 직접 연결됨
- 보존 계약이 유지됨
- 필요한 test가 코드와 함께 존재함
- 관계없는 파일 변경 없음

## 5. VERIFY

Spec acceptance criteria와 변경 위험에 해당하는 검증을 실제 환경에서 실행한다.
명령 존재나 과거 성공만으로 현재 결과를 `PASS` 처리하지 않는다.

기본 명령:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat integrationTest --no-daemon --rerun-tasks
npm --prefix frontend run lint
npm --prefix frontend run build
docker compose config --quiet
```

변경에 따라 OpenSQL, Ollama, 브라우저 E2E, 검색 평가, 라이선스·SBOM 검사와
spec 전용 명령을 추가한다. 적용되지 않는 명령은 자동으로 성공 처리하지 말고
실행하지 않은 이유를 적는다.

기록할 내용:

- 정확한 명령, source commit과 실행 환경
- 성공·실패·오류·건너뜀 수
- Docker, PostgreSQL, pgvector, Ollama, OpenSQL, OpenProxy와 OpenHA의 실제 사용
  여부
- 실패 또는 skip의 원인과 검증 범위
- 요구사항별 source·migration·test·환경 결과
- 실제로 존재하는 Issue·PR·CI URL

PostgreSQL·pgvector 결과를 OpenSQL 결과로 대체하지 않는다. OpenSQL
single-node 결과를 OpenProxy·OpenHA·DB failover 또는 전체 사용자 흐름으로
확대하지 않는다. 필요한 환경이 없으면 `NOT_RUN`으로 남긴다.

문서 전용 검증은 최소한 다음을 포함한다.

- Markdown 로컬 링크
- 명령·파일명·환경 변수의 실제 저장소 대조
- 구현·미구현·검증 상태의 문서 간 일치
- 후행 공백과 code fence
- `git diff --check`

Gate:

- 모든 필수 acceptance criterion에 실제 결과가 있음
- 필수 test 실패 없음
- 필요한 환경이 `NOT_RUN`이면 해당 기능을 `VERIFIED`로 표시하지 않음
- `evidence.md`와 상태 표현이 실제 결과와 일치함

## 6. AUDIT

수정 작업과 분리된 관점에서 최종 diff를 읽기 전용으로 다시 검토한다. Agent의
감사는 품질 근거지만 GitHub review가 아니다.

감사 범위:

- Spec·Plan·acceptance criteria와 실제 diff
- 사용자 ownership, 인증과 보안 경계
- Flyway forward-only와 데이터 호환성
- 실패 버전·기존 active version·Worker 복구 계약
- test의 실패·skip·환경 범위
- 문서의 구현·미구현·OpenSQL 표현
- dependency, license와 배포 경계
- 관계없는 사용자 변경과 민감정보 포함 여부

finding은 심각도, 파일·위치, 영향과 필요한 수정으로 기록한다. Blocking finding이
있으면 완료로 표시하지 않고 `SPEC`, `PLAN` 또는 `IMPLEMENT`로 돌아간다. 수정한
뒤 같은 항목을 다시 감사한다.

Gate:

- blocking finding 0건
- 남은 비차단 한계와 `NOT_RUN` 결과 공개
- Agent 감사와 실제 GitHub review를 구분함

## 7. INTEGRATE

GitHub 쓰기와 병합이 사용자의 범위에 포함될 때만 실행한다.

1. 실제 변경을 담은 PR을 만들고 관련 Issue, spec, tasks와 evidence를 연결한다.
2. Issue는 작업 시점에 실제로 필요한 경우에만 만들며, 완료된 과거 작업이나
   점수 상승을 위해 소급 생성하지 않는다.
3. 필수 CI를 실행하고 실제 review를 요청한다.
4. 혼자 유지보수해 reviewer가 없으면 독립 감사, 사용자 승인과
   `REVIEW_NOT_AVAILABLE_SOLO`를 evidence에 기록한다. 이것은 review 증거가
   아니다.
5. 병합 뒤 evidence와 Spec Registry에 실제 PR, merge/source commit과 마지막
   검증일을 기록한다.
6. 통합된 `main`을 먼저 push한다.
7. 임시 branch의 merge base, unique commit, 변경 파일, 연결 PR과 merged 상태를
   확인한 뒤 정확한 local·remote branch만 삭제한다.

GitHub 접근이나 권한이 없지만 사용자가 local-only 작업을 명시적으로 허가하면
구현과 로컬 검증은 계속할 수 있다. 이 결과를 Issue·PR·review·merge 증거로
표현하지 않는다. 외부 권한이 필요한 Gate에서는 식별자나 URL을 만들지 말고
한계를 기록한다.

Gate:

- 실제 PR·CI·병합 상태가 evidence와 일치함
- solo 예외가 필요하면 사용자 승인이 기록됨
- `main` push와 branch 삭제 전 안전 확인 완료
- 장기 branch가 `main`만 남음

## 상태 전이

| 시점 | Spec 상태 |
|---|---|
| 승인된 SPEC | `PLANNED` |
| IMPLEMENT 시작 | `IN_PROGRESS` |
| 코드 완료, 필수 근거 부족 | `IMPLEMENTED_UNVERIFIED` |
| 필수 VERIFY와 AUDIT 완료 | `VERIFIED` |
| 재개 조건을 두고 중단 | `DEFERRED` |
| 검토·실험 뒤 비채택 | `REJECTED` |

`NOT_RUN`인 필수 환경이 있거나 blocking audit finding이 남으면 `VERIFIED`로
전환하지 않는다. 통합 뒤 Registry의 source commit과 last-verified date를 실제
값으로 갱신한다.

## 중단 조건

- 요구사항, acceptance criteria나 보존 영향이 모호하면 `IMPLEMENT` 전에 멈춘다.
- 구현이 승인 범위를 넘어가면 `VERIFY`로 가지 않고 `SPEC` 또는 `PLAN`으로
  돌아간다.
- 필수 test 실패, 필수 환경 `NOT_RUN`, 라이선스 충돌, 문서·source 모순이나
  blocking finding이 있으면 `VERIFIED` 또는 `INTEGRATE`로 가지 않는다.
- reviewer가 없는 대회 범위 제품 변경은 독립 감사와 사용자 승인 없이 병합하지
  않는다.
- branch 안전 확인과 `main` push가 끝나기 전에는 임시 branch를 삭제하지 않는다.

## 문서 갱신 기준

| 문서 | 갱신 시점 |
|---|---|
| `AGENTS.md` | 프로젝트 방향, 핵심 불변식 또는 workflow 적용 경계가 바뀔 때 |
| `docs/ai-agent-workflow.md` | 단계, Gate, 검증, branch·PR 또는 예외 절차가 바뀔 때 |
| `docs/README.md` | 문서를 추가·이동·이름 변경·보관·삭제하거나 독자 경로가 바뀔 때 |
| `docs/project-status.md` | source와 실행 근거가 현재 구현·검증 상태를 바꿀 때 |
| `docs/architecture.md` | 현재 구성 요소, 데이터 흐름 또는 설계 계약이 바뀔 때 |
| `docs/roadmap.md` | 제품 우선순위와 순서가 바뀔 때 |
| `docs/contest/2026-tmaxtibero-plan.md` | 대회 일정, P0~P10 범위·우선순위·중단 조건이 바뀔 때 |
| `docs/contest/2026-requirements-traceability.md` | 통합된 근거가 공식 매핑, 평가 evidence, 환경·제출 상태를 바꿀 때 |
| `specs/PRZ-###/spec.md` | 구현 전 의도·범위·acceptance criteria가 바뀔 때 |
| `specs/PRZ-###/plan.md`, `tasks.md` | 구현 전 계획과 구현 중 작업 상태·deviation이 바뀔 때 |
| `specs/PRZ-###/evidence.md` | VERIFY·AUDIT 결과와 실제 GitHub 통합 근거가 생길 때 |
| `specs/README.md` | Spec 상태, source commit 또는 마지막 검증일이 바뀔 때 |
| `README.md`, `docs/quickstart.md` | 공개 기능, 설치·demo, 지원 환경이나 attribution이 바뀔 때 |
| `LICENSE`, `NOTICE`, license audit | dependency, 모델, 데이터, asset 또는 재배포 산출물이 바뀔 때 |
| `docs/development-log.md` | 의미 있는 구현·설계·검증·비채택·통합 결정이 생길 때 |

문서 설명만 바꾸어 source 구현 상태나 평가 evidence를 과장하지 않는다. 대회
평가 ID와 category별 현재 evidence는
[요구사항·평가기준 추적표](contest/2026-requirements-traceability.md)에서만
관리하며, 내부 예상 점수와 assessment 변경 이력은 공개 문서에 두지 않는다.

## DEFERRED와 REJECTED 처리

작업을 미루거나 채택하지 않으면 사라지게 두지 않는다.

1. Spec 상태를 `DEFERRED` 또는 `REJECTED`로 바꾼다.
2. `evidence.md`와 필요한 경우 [개발 기록](development-log.md)에 이유, 재개 조건
   또는 비채택 근거를 적는다.
3. 실제 Issue·PR이 있으면 현재 상태를 연결한다. 존재하지 않는 기록은 만들지
   않는다.
4. 임시 branch의 unique commit과 변경 파일을 확인한다.
5. 보존할 결정 근거를 `main`에 반영하고 push한 뒤 branch를 삭제한다.

단순히 어렵거나 시간이 걸린다는 이유만으로 검증되지 않은 작업을 완료로 바꾸지
않는다.

## 이 workflow의 한계

- Markdown 규칙은 기술적으로 작업을 차단하지 않는다. CI, 권한, sandbox와
  review가 별도로 필요하다.
- test 통과만으로 실제 OpenSQL·OpenProxy·OpenHA 또는 외부 서비스 호환성을
  증명할 수 없다.
- `REVIEW_NOT_AVAILABLE_SOLO`는 정직한 절차 기록일 뿐 제3자 review가 아니다.
- 로컬 Codex skill은 설치한 컴퓨터에서만 동작한다. 저장소 공통 규칙은
  [AGENTS.md](../AGENTS.md)와 이 문서에 남긴다.
