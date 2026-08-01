# PRZ-004 Evidence — 안전한 clean-clone demo

## 현재 판정

`IN_PROGRESS`

- 공개 기준 main: `936e957132fcf54b5cee1f58d83f8d591e5786e2`
- 작업 branch: `PRZ-004-clean-clone-demo` (local only)
- 정적 감사 대상 후보: `0d20454eb9a3c3d9b8c7812d54a20781415b0378`
- 현재 후보 상태: `IMPLEMENTATION_CANDIDATE_UNVERIFIED`
- 최종 구현 source commit: 검증 전
- GitHub Issue·PR·CI·review·merge: 없음 (`NOT_RUN`)

이 문서는 구현과 검증이 끝난 뒤 실제 명령, 환경, 결과와 남은 제한을 기록한다.
현재 source·test가 아직 최종 commit으로 고정되지 않았으므로 요구사항을 `PASS`로
판정하지 않는다.

## Spec·Plan 대조 이력

- 초기 구현 후보 일부는 최종 Spec·Plan 확정 전에 만들어졌다.
- Spec·Plan과 구현이 처음 함께 기록된 `f7d600f`를 사전 승인된 계획으로
  소급하지 않는다.
- 이후 범위를 축소하고 `0d20454eb9a3c3d9b8c7812d54a20781415b0378` 후보를
  Spec·Plan과 사후 대조해 conformance baseline을 기록했다.
- 두 clean clone, 브라우저, 전체 회귀와 공급망 최종 검증은 아직 `NOT_RUN`이다.

## 요구사항별 상태

| 요구사항 | 현재 상태 | 근거 |
|---|---|---|
| PRZ-004-R01 안전한 demo USER | `IN_PROGRESS` | source와 test 구현 후 전체 검증 예정 |
| PRZ-004-R02 로컬 설정과 Compose 격리 | `IN_PROGRESS` | 실행 도구 구현 후 두 clone 검증 예정 |
| PRZ-004-R03 합성 fixture | `IN_PROGRESS` | 생성기와 무결성 test 검증 예정 |
| PRZ-004-R04 API smoke | `IN_PROGRESS` | 실제 PostgreSQL·pgvector·Ollama 실행 예정 |
| PRZ-004-R05 기존 계약 보존 | `NOT_VERIFIED` | 전체 unit·integration test 예정 |
| PRZ-004-R06 재현성과 공급망 기록 | `IN_PROGRESS` | SBOM·license와 두 clone 검증 예정 |
| PRZ-004-R07 상태와 Evidence | `IN_PROGRESS` | 최종 실행 뒤 현행화 예정 |

## Blocking finding 교정 결과

2026-08-01에 후보 `0d20454eb9a3c3d9b8c7812d54a20781415b0378` 위 작업
트리에서 최소 교정과 제한 검증을 수행했다. 이는 전체 PRZ-004 검증이나 최종
source commit 검증이 아니다.

- 전체 PRZ-004 상태: `IN_PROGRESS`
- 이번 교정 단계 판정: `CORRECTION_PASS_READY_FOR_FULL_VERIFY`

| 항목 | 판정 | 교정 근거 |
|---|---|---|
| B1 demo email 정규화 | `RESOLVED` | null 확인 뒤 trim·`Locale.ROOT` lowercase·blank·email 형식 검증을 거쳐 같은 정규화 값으로 조회·저장한다. 기존 계정은 변경하지 않는다. |
| B2 Compose shell override | `RESOLVED` | child process에서 실제 `.env`의 모든 key와 Compose 제어 key를 대소문자 구분 없이 제거한다. 값은 출력하지 않는다. |
| B3 verifier 설정 기준 | `RESOLVED` | 보안 관련 shell override는 key 이름만 표시하고 fail-closed하며, 검증 값은 `.env`에서만 읽는다. |
| B4 공급망 상태 | `RESOLVED` | PRZ-002 역사적 `VERIFIED`와 PRZ-004 `IMPLEMENTATION_CANDIDATE_UNVERIFIED`·최종 검증 `NOT_RUN`을 분리했다. |
| 검색 ownership allowlist | `RESOLVED` | 빈 결과와 이번 실행 허용 목록 밖의 document·version·source를 모두 거부한다. |
| polling deadline·상한 | `RESOLVED` | 180초 deadline, 1초 간격, 최대 181회와 요청별 timeout을 함께 적용하고 늦게 도착한 `ACTIVE`도 성공시키지 않는다. |

독립 정적 재감사에서 위 항목의 blocking·partial finding은 없었다.

## 이번 교정 VERIFY

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat test --no-daemon --tests "*DemoUserBootstrapRunnerTest" --tests "*BootstrapAccountConflictGuardTest" --tests "*BcryptPasswordPolicyTest"` | `PASS`; 지정 3개 class 13 tests, 실패·오류·skip 0 |
| `.\gradlew.bat test --no-daemon` | `PASS`; 261 tests, 247 passed, 14 skipped, 실패·오류 0 |
| `node --test scripts/clean-clone-demo.test.mjs` | `PASS`; 27 tests, 26 passed, Windows에서 POSIX `0600` 검사 1 skipped, 실패 0 |
| `git diff --check` | `PASS` |

이번 교정에서는 integration test, 두 clean clone, 실제 API smoke, 브라우저,
frontend·공급망 최종 검증을 실행하지 않았다. dependency·lockfile·SBOM·migration,
frontend UI와 OpenSQL 관련 파일도 수정하지 않았다.

## 검증 경계

- PostgreSQL·pgvector·Ollama clean-clone 전체 흐름: `NOT_RUN`
- 브라우저 UI 시험: `NOT_RUN`
- OpenSQL+Ollama 전체 사용자 흐름: `NOT_RUN`
- OpenProxy: `NOT_RUN`
- OpenHA·DB failover: `NOT_RUN`
- Windows `SecureDirectoryStream`: 환경 조건에 따라 `SKIPPED`일 수 있으며
  `PASS`로 바꾸지 않는다.

## 중간 발견 사항

- 일반 `docker compose config`는 생성된 설정의 비밀값을 렌더링하므로 공개 절차와
  Evidence에서는 `docker compose config --quiet`만 사용한다.
- 기준 main의 full npm audit에서 개발 전용 transitive dependency high finding
  2건을 재현했다. 교정 결과는 최종 공급망 검증 뒤 확정한다.
- 초기 후보의 custom frontend port와 기본 `5173` CORS origin 불일치는 정적
  설정·script 대조에서 발견했다. `207143b`에서 port 연결을 추가하고
  `0d20454`에서 URL 기본 port `80` 정규화 회귀를 교정했지만, 실제 브라우저
  CORS 시험은 `NOT_RUN`이다.
- 제공 ZIP과 기존 작업 폴더의 성공 주장은 독립 증거로 사용하지 않는다.

## 공급망 상태 경계

| 범위 | 상태 | 근거 |
|---|---|---|
| PRZ-002 공개 source `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1` | `VERIFIED` | GitHub CI 기준 `777e184f206d2a2770d055940ddabf139abfed9d`의 역사적 source-only 결과 |
| PRZ-004 local candidate | `IMPLEMENTATION_CANDIDATE_UNVERIFIED` | dependency·Docker·CI·SBOM·checksum candidate 파일 갱신과 정적 일관성만 확인 |
| PRZ-004 최종 공급망 검증 | `NOT_RUN` | final source의 `npm ci`, lint·build, full·production audit, builder identity, SBOM 재생성, checksum·license·OSS readiness 미실행 |

## 남은 작업

최종 source commit 고정, 두 `--no-hardlinks` clone의 API·브라우저 검증, 자동
테스트, 비밀정보·ownership·SBOM·license 독립 감사를 완료한 뒤 이 문서를
최종 결과로 교체한다.
