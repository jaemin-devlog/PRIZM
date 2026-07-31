# PRZ-004 Evidence — 안전한 clean-clone demo

## 현재 판정

`IN_PROGRESS`

- 공개 기준 main: `936e957132fcf54b5cee1f58d83f8d591e5786e2`
- 작업 branch: `PRZ-004-clean-clone-demo` (local only)
- 최종 구현 source commit: 검증 전
- GitHub Issue·PR·CI·review·merge: 없음 (`NOT_RUN`)

이 문서는 구현과 검증이 끝난 뒤 실제 명령, 환경, 결과와 남은 제한을 기록한다.
현재 source·test가 아직 최종 commit으로 고정되지 않았으므로 요구사항을 `PASS`로
판정하지 않는다.

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
- 제공 ZIP과 기존 작업 폴더의 성공 주장은 독립 증거로 사용하지 않는다.

## 남은 작업

최종 source commit 고정, 두 `--no-hardlinks` clone의 API·브라우저 검증, 자동
테스트, 비밀정보·ownership·SBOM·license 독립 감사를 완료한 뒤 이 문서를
최종 결과로 교체한다.
