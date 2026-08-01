# PRZ-004 Evidence — 안전한 clean-clone demo

## 현재 판정

`IMPLEMENTED_UNVERIFIED` — `VERIFY` 완료, 독립 최종 `AUDIT` 대기

- 공개 기준 main: `936e957132fcf54b5cee1f58d83f8d591e5786e2`
- 검증된 구현 commit: `25d09e9eee9837cf4a63d7461699825ff22743e2`
- 작업 branch: `PRZ-004-clean-clone-demo` (local only)
- 자동 검증: `339 PASS / 18 SKIP / 0 FAIL`
- 첫 번째 독립 clone: `CLEAN_CLONE_01_PASS`
- 두 번째 독립 clone: `CLEAN_CLONE_02_ISOLATION_PASS_WITH_FINDINGS`
- GitHub Issue·push·PR·CI·review·merge: 없음 (`NOT_RUN`)

필수 자동 검증과 두 독립 clone의 환경 검증은 끝났다. 다만 독립 최종
`AUDIT`를 아직 실행하지 않았으므로 이 Spec을 `VERIFIED`로 판정하지 않는다.
공개 GitHub main에도 PRZ-004가 아직 통합되지 않았다.

## 요구사항별 상태

| 요구사항 | VERIFY 결과 | 근거 |
|---|---|---|
| PRZ-004-R01 안전한 demo USER | `PASS` | one-time `USER` bootstrap, 충돌·중복 계정 fail-closed, BCrypt 경계와 로그인 검증 |
| PRZ-004-R02 로컬 설정과 Compose 격리 | `PASS` | 두 환경의 project·port·DB/runtime volume 분리, shell override 차단과 CORS 확인 |
| PRZ-004-R03 합성 fixture | `PASS` | ignored 로컬 경로에 생성한 first-party TXT·PDF와 서로 다른 marker 사용 |
| PRZ-004-R04 API smoke | `PASS` | 로그인, 업로드, `ACTIVE`, source metadata, 검색 allowlist와 비로그인 `401` 확인 |
| PRZ-004-R05 기존 계약 보존 | `PASS` | 전체 unit·PostgreSQL integration 실패 0건, 첫 환경 정보의 두 번째 환경 노출 0건 |
| PRZ-004-R06 재현성과 공급망 기록 | `VERIFY PASS — AUDIT PENDING` | 두 `--no-hardlinks` clone, 모델 identity·1024차원, npm audit·SBOM·OSS readiness 통과 |
| PRZ-004-R07 상태와 Evidence | `VERIFY PASS — AUDIT PENDING` | 문서 현행화와 읽기 전용 정합성 검증 통과; 독립 최종 `AUDIT`에서 최종 판정 |

위 표는 구현 commit의 `VERIFY` 결과다. 최종 Spec 상태는 독립 `AUDIT`에서
blocking finding이 0건인지 확인한 뒤 결정한다.

## 자동 검증

검증 기준은 `25d09e9eee9837cf4a63d7461699825ff22743e2`다.

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat test --no-daemon` | `247 PASS / 14 SKIP / 0 FAIL` |
| `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `66 PASS / 3 SKIP / 0 FAIL`; PostgreSQL·pgvector 결과 |
| `node --test scripts/clean-clone-demo.test.mjs` | `26 PASS / 1 SKIP / 0 FAIL` |
| `npm.cmd --prefix frontend ci` | `PASS` |
| `npm.cmd --prefix frontend run lint` | `PASS` |
| `npm.cmd --prefix frontend run build` | `PASS` |
| `npm.cmd --prefix frontend audit --json` | `PASS`; vulnerability 0 |
| `npm.cmd --prefix frontend audit --omit=dev --json` | `PASS`; vulnerability 0 |
| `docker compose config --quiet` | `PASS` |
| `node scripts/verify-sbom.mjs` | `PASS` |
| `node scripts/verify-oss-readiness.mjs` | `PASS` |
| `git diff --check` | `PASS` |

총 자동 결과는 `339 PASS / 18 SKIP / 0 FAIL`이다.

### SKIP 경계

- Windows symbolic-link 권한이 필요한 검사는 `SKIPPED`다.
- Windows에서 `SecureDirectoryStream`을 사용할 수 없어 관련 검사는
  `SKIPPED`다. 안전한 삭제 기능의 `PASS`로 바꾸지 않는다.
- POSIX `.env` mode `0600` 검사는 Windows에서 `SKIPPED`다.
- PostgreSQL 통합 검증에서는 실제 OpenSQL 전용 통합 테스트를 실행하지 않았다.

## 첫 번째 독립 clean clone

판정: `CLEAN_CLONE_01_PASS`

- 구현 commit을 `--no-hardlinks`로 독립 clone했다.
- 첫 번째 실행 전용 Compose project와 신규 DB·runtime volume을 사용했다.
- 사용 port는 DB `15433`, backend `18081`, frontend `15174`다.
- Docker·PostgreSQL·pgvector가 정상 기동했다.
- demo `USER`를 한 번 생성한 뒤 bootstrap을 비활성화하고 backend를 다시 만들었다.
- 로그인 직후 문서 목록은 0건이었다.
- TXT는 `ACTIVE` 뒤 `TEXT_CHUNK` marker를 반환했다.
- PDF는 `ACTIVE` 뒤 `PAGE`와 page number `1`을 반환했다.
- 브라우저에서 로그인, 문서 표시, PDF viewer, 검색, 로그아웃을 확인했다.
- 인증정보가 없는 보호 API 요청은 `401`을 반환했다.
- secret·JWT·demo email의 로그 노출은 0건이었다.
- 검증 뒤 Git 작업 트리는 깨끗했다.

## 두 번째 독립 clean clone

판정: `CLEAN_CLONE_02_ISOLATION_PASS_WITH_FINDINGS`

- 첫 번째와 다른 Compose project와 신규 DB·runtime volume을 사용했다.
- 사용 port는 DB `15434`, backend `18082`, frontend `15175`다.
- 첫 번째 환경의 volume mount는 0건이었다.
- 새로운 secret과 별도 demo 계정을 사용했다.
- 업로드 전 문서 목록, 첫 번째 계정, 첫 번째 marker 검색 결과는 모두 0건이었다.
- 첫 번째 환경의 credential로 두 번째 환경에 로그인하면 `401`을 반환했다.
- 첫 번째 계정·문서·marker의 노출은 0건이었다.
- TXT는 `ACTIVE` 뒤 `TEXT_CHUNK`, PDF는 `ACTIVE` 뒤 `PAGE` 결과를 반환했다.
- 모든 검색 결과의 document/version이 이번 실행 allowlist에 속했다.
- 상위 shell override는 차단됐고 CORS origin은 두 번째 frontend port와 일치했다.
- 인증정보는 loopback backend로만 전송했다.
- secret·JWT·email의 로그 노출은 0건이었고 검증 뒤 작업 트리는 깨끗했다.

### 두 번째 browser 시험

- 로그인 화면과 demo `USER` 로그인을 확인했다.
- TXT와 PDF 문서가 표시되고 두 문서의 처리가 완료된 것을 확인했다.
- PDF의 `ACTIVE` 상태와 iframe 원문 표시를 확인했다.
- PDF marker 검색 결과에서 1페이지 출처를 확인했다.
- 로그아웃 뒤 보호 화면 접근이 차단되는 것을 확인했다.

### 비차단 browser finding

`Browser initial empty-list observation: NOT_RUN`

두 번째 브라우저 시험은 API smoke가 TXT·PDF를 업로드한 뒤 시작해 빈 목록 UI를
직접 관찰하지 못했다. 대신 업로드 전 API 문서 0건, 첫 번째 계정 0건, 첫 번째
marker 0건, 서로 다른 project·volume, 첫 번째 credential 로그인 `401`과 검색
allowlist로 초기 DB와 환경 격리를 확인했다. API의 업로드 전 0건 확인을 브라우저
직접 관찰 `PASS`로 바꾸지 않는다. 이 항목은 최종 `AUDIT`에서 비차단 finding으로
다시 검토한다.

## Ollama·모델 재현성

- Ollama: `0.32.3`
- model: `bge-m3:latest`
- 확인한 manifest digest: `790764...6bab`
- embedding dimension: `1024`
- 두 clean-clone의 실제 임베딩·검색 흐름에서 사용: `PASS`

`latest`는 바뀔 수 있는 tag다. 이번 결과는 위 identity를 확인한 실행 기록이며,
이름이 같은 미래 모델까지 byte 단위로 동일하다는 뜻은 아니다. 모델 파일은
저장소나 Docker image에 포함하지 않는다.

## 공급망·저장소 안전

- frontend `npm ci`·lint·build: `PASS`
- full·production npm audit: vulnerability 0
- SBOM verification: `PASS`
- OSS readiness: `PASS`
- `.env`, credential, JWT, fixture, model, DB volume과 image의 Git 포함: 0건
- 제품 동작 검증 뒤 Git 작업 트리: clean

## PostgreSQL·OpenSQL 경계

| 대상 | 상태 |
|---|---|
| PostgreSQL·pgvector+Ollama clean-clone 전체 흐름 | `PASS` |
| 실제 OpenSQL single-node SQL Gate | 기존 PRZ-003 `PASS` |
| OpenSQL+Ollama 전체 사용자 흐름 | `NOT_RUN` |
| OpenProxy | `NOT_VERIFIED` |
| OpenHA | `NOT_RUN` |
| DB failover | `NOT_RUN` |

이번 PostgreSQL clean-clone 결과를 OpenSQL 전체 흐름이나 고가용성 검증으로
확대하지 않는다.

## GitHub 통합 상태

| 항목 | 상태 |
|---|---|
| 공개 GitHub main 반영 | `NOT_RUN` |
| push | `NOT_RUN` |
| Issue·PR | `NOT_RUN` |
| GitHub CI | `NOT_RUN` |
| review | `NOT_RUN` |
| merge | `NOT_RUN` |

존재하지 않는 URL이나 GitHub 활동을 증거로 만들지 않는다.

## 핵심 교정 이력

- 초기 구현 후보는 최종 Spec·Plan 확정 전에 만들어졌으며 사전 승인된 계획으로
  소급하지 않는다.
- 정적 감사에서 demo email 정규화, Compose shell override, verifier 설정 기준,
  검색 allowlist와 polling 상한을 교정했다.
- 제공 ZIP과 기존 작업 폴더의 성공 주장은 검증 근거로 복사하지 않았다.
- 교정 뒤 자동 검증과 두 독립 clone을 최종 구현 commit에서 다시 실행했다.

## 남은 단계

다음 단계는 구현·검증 결과를 분리된 관점에서 확인하는 독립 최종 `AUDIT`다.
blocking finding 0건을 확인하기 전에는 PRZ-004를 `VERIFIED`로 바꾸거나 GitHub
통합 가능 상태로 선언하지 않는다.
