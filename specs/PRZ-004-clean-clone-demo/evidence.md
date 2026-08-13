# PRZ-004 — 안전한 clean-clone demo Evidence

## 현재 판정

`VERIFIED` — 필수 `VERIFY`, 독립 `AUDIT`, GitHub CI와 `main` 통합 완료

- 작업 시작 main: `936e957132fcf54b5cee1f58d83f8d591e5786e2`
- 전체 clean-clone 검증 source commit: `25d09e9eee9837cf4a63d7461699825ff22743e2`
- 최종 Windows·Linux 경로 교정·CI source commit:
  `aff3e87a9a912e44fcf217291a45328cf451cfc9`
- GitHub 통합 merge commit: `1f9a5ad964778a2e72de9949a0fadae042008392`
- 작업 branch: `PRZ-004-clean-clone-demo` (PR #25로 병합)
- 자동 검증: `339 PASS / 18 SKIP / 0 FAIL`
- 첫 번째 독립 clone: `CLEAN_CLONE_01_PASS`
- 두 번째 독립 clone: `CLEAN_CLONE_02_ISOLATION_PASS_WITH_FINDINGS`
- 독립 감사: `AUDIT_PASS_WITH_NON_BLOCKING_FINDINGS`, blocking finding 0건
- GitHub: Issue 없음, [PR #25](https://github.com/jaemin-devlog/PRIZM/pull/25),
  CI 6건 성공, review `REVIEW_NOT_AVAILABLE_SOLO`, merge 완료

전체 clean-clone 실행 결과는 `25d09e9...`에서 얻었다. 이후 GitHub Linux CI에서
발견한 플랫폼 경로 차이를 `aff3e87...`에서 교정하고 Windows·Linux Node test와
GitHub check를 통과했다. 두 전체 clean clone을 `aff3e87...`에서 다시 실행한
것으로 확대하지 않는다.

## 검증한 수직 흐름

```text
독립 clean clone과 로컬 환경 준비
↓
기본 비활성 demo USER를 명시적으로 활성화
↓
합성 TXT·PDF 업로드와 ACTIVE 전환
↓
API·브라우저 원문 근거 검색
↓
두 번째 clone의 project·volume·데이터 격리 확인
```

이 흐름은 PostgreSQL·pgvector와 host Ollama 기준이다. OpenSQL은 PRZ-004에서
`NOT_RUN`이다.

## 2026-08-04 미커밋 확장안 정리

PRZ-004의 원래 clean-clone demo는 위 근거대로 `VERIFIED` 상태를 유지한다. 이후 별도
working tree에서 인증 모드 추상화, 회원가입·비밀번호 관리, 자동 모델 준비까지 포함하려던
미커밋 확장안은 이 Spec의 승인 범위를 넘었다. 해당 초안은 commit·VERIFY·AUDIT·배포를
거치지 않았으며, PRZ-006이 채택한 좁은 로컬 보관함 빠른 시작으로 대체됐다.

따라서 이 **미커밋 확장안만 `REJECTED`**로 정리한다. 원래 PRZ-004 결과를 부정하거나
되돌리는 결정은 아니다. 다중 사용자 계정 관리나 모델 자동 준비가 다시 필요해지면 새
Spec으로 범위와 보안·운영 조건을 먼저 정의한다.

## 요구사항별 상태

- **요구사항:** PRZ-004-R01 안전한 demo USER
  - VERIFY 결과: `PASS`
  - 근거: one-time `USER` bootstrap, 충돌·중복 계정 fail-closed, BCrypt 경계와 로그인 검증
- **요구사항:** PRZ-004-R02 로컬 설정과 Compose 격리
  - VERIFY 결과: `PASS`
  - 근거: 두 환경의 project·port·DB/runtime volume 분리, shell override 차단과 CORS 확인
- **요구사항:** PRZ-004-R03 합성 fixture
  - VERIFY 결과: `PASS`
  - 근거: ignored 로컬 경로에 생성한 first-party TXT·PDF와 서로 다른 marker 사용
- **요구사항:** PRZ-004-R04 API smoke
  - VERIFY 결과: `PASS`
  - 근거: 로그인, 업로드, `ACTIVE`, source metadata, 검색 allowlist와 비로그인 `401` 확인
- **요구사항:** PRZ-004-R05 기존 계약 보존
  - VERIFY 결과: `PASS`
  - 근거: 전체 unit·PostgreSQL integration 실패 0건, 첫 환경 정보의 두 번째 환경 노출 0건
- **요구사항:** PRZ-004-R06 재현성과 공급망 기록
  - VERIFY 결과: `PASS`
  - 근거: 두 `--no-hardlinks` clone, 모델 identity·1024차원, npm audit·SBOM·OSS readiness와 GitHub CI 통과
- **요구사항:** PRZ-004-R07 상태와 Evidence
  - VERIFY 결과: `PASS`
  - 근거: source·환경별 결과, 독립 감사, PR·CI·review 부재·merge를 구분해 기록

전체 사용자 흐름에 대한 위 판정은 `25d09e9...`의 실행 결과를 기준으로 한다.
플랫폼 경로 교정과 GitHub 통합 근거는 아래 별도 절에서 확인한다.

## 자동 검증

검증 기준은 `25d09e9eee9837cf4a63d7461699825ff22743e2`다.

- **명령:** `.\gradlew.bat test --no-daemon`
  - 결과: `247 PASS / 14 SKIP / 0 FAIL`
- **명령:** `.\gradlew.bat integrationTest --no-daemon --rerun-tasks`
  - 결과: `66 PASS / 3 SKIP / 0 FAIL`; PostgreSQL·pgvector 결과
- **명령:** `node --test scripts/clean-clone-demo.test.mjs`
  - 결과: `26 PASS / 1 SKIP / 0 FAIL`
- **명령:** `npm.cmd --prefix frontend ci`
  - 결과: `PASS`
- **명령:** `npm.cmd --prefix frontend run lint`
  - 결과: `PASS`
- **명령:** `npm.cmd --prefix frontend run build`
  - 결과: `PASS`
- **명령:** `npm.cmd --prefix frontend audit --json`
  - 결과: `PASS`; vulnerability 0
- **명령:** `npm.cmd --prefix frontend audit --omit=dev --json`
  - 결과: `PASS`; vulnerability 0
- **명령:** `docker compose config --quiet`
  - 결과: `PASS`
- **명령:** `node scripts/verify-sbom.mjs`
  - 결과: `PASS`
- **명령:** `node scripts/verify-oss-readiness.mjs`
  - 결과: `PASS`
- **명령:** `git diff --check`
  - 결과: `PASS`

총 자동 결과는 `339 PASS / 18 SKIP / 0 FAIL`이다.

## 최종 플랫폼 교정과 GitHub CI

GitHub Linux CI에서 Windows 절대 경로 표현에 의존하던 Node test 두 곳을 발견했다.
최종 source `aff3e87a9a912e44fcf217291a45328cf451cfc9`에서 운영체제와 무관한 경로
표현으로 교정한 뒤 다음을 확인했다.

- **검증:** Windows `node --test scripts/clean-clone-demo.test.mjs`
  - 결과: `26 PASS / 1 SKIP / 0 FAIL`
- **검증:** Linux Node 22.17 Docker의 같은 명령
  - 결과: `27 PASS / 0 SKIP / 0 FAIL`
- **검증:** GitHub push CI backend·frontend
  - 결과: 2건 `PASS`
- **검증:** GitHub push OSS Readiness·License·Markdown·SBOM
  - 결과: 1건 `PASS`
- **검증:** GitHub PR CI backend·frontend
  - 결과: 2건 `PASS`
- **검증:** GitHub PR OSS Readiness·License·Markdown·SBOM
  - 결과: 1건 `PASS`

이 교정은 test fixture 경로의 플랫폼 호환성만 바꿨다. 제품 동작이나 앞서 실행한
두 clean clone 환경은 바꾸지 않았으며, 두 전체 clean clone은 `aff3e87...`에서
재실행하지 않았다.

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
직접 관찰 `PASS`로 바꾸지 않는다. 독립 `AUDIT`에서도 비차단 finding으로
판정했다.

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

- **대상:** PostgreSQL·pgvector+Ollama clean-clone 전체 흐름
  - 상태: `PASS`
- **대상:** 실제 OpenSQL single-node SQL Gate
  - 상태: 기존 PRZ-003 `PASS`
- **대상:** OpenSQL+Ollama 전체 사용자 흐름
  - 상태: `NOT_RUN`
- **대상:** OpenProxy
  - 상태: `NOT_VERIFIED`
- **대상:** OpenHA
  - 상태: `NOT_RUN`
- **대상:** DB failover
  - 상태: `NOT_RUN`

이번 PostgreSQL clean-clone 결과를 OpenSQL 전체 흐름이나 고가용성 검증으로
확대하지 않는다.

## GitHub 통합 상태

- **항목:** 공개 GitHub main 반영
  - 상태: `PASS`; merge `1f9a5ad964778a2e72de9949a0fadae042008392`
- **항목:** push
  - 상태: `PASS`; head `aff3e87a9a912e44fcf217291a45328cf451cfc9`
- **항목:** Issue
  - 상태: 없음; 과거 Issue를 소급 생성하지 않음
- **항목:** PR
  - 상태: `PASS`; [PR #25](https://github.com/jaemin-devlog/PRIZM/pull/25)
- **항목:** GitHub CI
  - 상태: `PASS`; [push CI](https://github.com/jaemin-devlog/PRIZM/actions/runs/30698202866), [push OSS](https://github.com/jaemin-devlog/PRIZM/actions/runs/30698202833), [PR CI](https://github.com/jaemin-devlog/PRIZM/actions/runs/30698204334), [PR OSS](https://github.com/jaemin-devlog/PRIZM/actions/runs/30698204330)
- **항목:** review
  - 상태: `REVIEW_NOT_AVAILABLE_SOLO`; GitHub review 0건이며 review evidence가 아님
- **항목:** merge
  - 상태: `PASS`; 2026-08-01 20:46:40 KST

PR #25의 head에서 push·pull_request event별 backend, frontend, OSS check 6건이
모두 성공했다. 사용자 병합 승인과 독립 감사는 GitHub review로 계산하지 않는다.

## 핵심 교정 이력

- 초기 구현 후보는 최종 Spec·Plan 확정 전에 만들어졌으며 사전 승인된 계획으로
  소급하지 않는다.
- 정적 감사에서 demo email 정규화, Compose shell override, verifier 설정 기준,
  검색 allowlist와 polling 상한을 교정했다.
- 제공 ZIP과 기존 작업 폴더의 성공 주장은 검증 근거로 복사하지 않았다.
- 교정 뒤 자동 검증과 두 독립 clone을 최종 구현 commit에서 다시 실행했다.
- 독립 최종 감사는 `f4c252944148ebf7b4abb0ff5b26d158e2534cf8` 기준
  `AUDIT_PASS_WITH_NON_BLOCKING_FINDINGS`였고 blocking finding은 0건이었다.
- GitHub Linux CI의 경로 finding은 `aff3e87...`에서 교정해 Windows·Linux Node
  test와 GitHub CI로 재검증했다.

## 남은 단계

PRZ-004의 필수 관리 단계는 완료했다. 다음 기능 Gate는 OpenSQL과 Ollama를 함께
사용하는 전체 사용자 흐름이다. OpenProxy·OpenHA·DB failover는 계속 별도
`NOT_VERIFIED` 또는 `NOT_RUN` 범위로 남긴다.
