# PRZ-003 근거

## 현재 근거

| 항목 | 결과 | 근거 |
|---|---|---|
| 공급사 승인과 환경 조건 | `PASS_PRIVATE_EVIDENCE` | VM 사용 승인과 환경 조건을 서면으로 확인했다. 원문 correspondence와 신청값은 Git 밖의 비공개 근거로 보존한다. |
| VirtualBox 격리 환경 | `PASS` | 연구실 Windows 호스트에 전용 VirtualBox guest 한 대를 구성했다. |
| OS 교정 | `PASS` | 지원 대상과 달랐던 초기 guest disk는 OpenSQL 설치 전에 분리해 보존하고, 공식 Rocky Linux 9.7 설치 매체를 checksum 검증해 새 guest disk에 설치했다. |
| 라이선스 귀속 환경 | `PASS_PRIVATE_EVIDENCE` | guest hostname과 CPU topology를 발급 기준과 동일하게 유지했다. 정확한 식별값·topology·로컬 경로는 공개 저장소에 기록하지 않는다. |
| 비공개 네트워크 | `PASS_PRIVATE_EVIDENCE` | 고정 Host-only 연결과 패키지 접근용 NAT를 분리했으며 공용 port forwarding은 구성하지 않았다. 실제 IP와 host 목록은 비공개다. |
| 시간 동기화 | `PASS` | Asia/Seoul, NTP active, `NTPSynchronized=yes`, Chrony Stratum 3과 `Leap status: Normal`을 확인했다. |
| 테스트 라이선스 | `ISSUED_AND_APPLIED_PRIVATE` | 발급된 대회용 테스트 라이선스를 guest에 적용했다. 파일·이름·fingerprint·서명 데이터·유효기간·host 귀속값은 저장소에 없다. |
| 공급 OpenSQL 자산 | `PASS_PRIVATE_EVIDENCE` | 공급 자산을 로컬에서 확인하고 허가된 환경에만 전달했다. package 이름·version·fingerprint·metadata·추출 파일과 내부 log는 Git 밖에 보존한다. |
| dependency 준비 | `PASS_PRIVATE_EVIDENCE` | 로컬 환경 조정 뒤 설치 prerequisite를 충족했다. 상세 진단은 공급사 지원용 비공개 근거로 보존하며 공급 파일을 수정하거나 재배포하지 않았다. |
| OpenSQL Single 설치 | `PASS_INSTALLATION_ONLY` | 2026-07-29 공급된 `single` 설치 절차가 완료됐다. 완료 log와 생성 설정은 guest 안에만 있으며 설치 성공을 PRIZM 호환성 성공으로 표현하지 않는다. |
| 기본 DB 연결 | `PASS_INSTALLATION_ONLY` | 직접 인증 연결에서 읽기 전용 `SELECT 1`이 성공했다. Flyway·vector·검색·소유권·Worker SQL 검증은 포함하지 않았다. |
| 지원 service 기본 상태 | `PASS_INSTALLATION_ONLY` | 설치 직후 single-node DB를 유지하는 지원 service의 실행 상태와 primary health를 확인했다. 세부 port·unit·내부 구성은 공개 근거에서 제외한다. |
| OpenProxy 기능 검증 | `NOT_VERIFIED` | 설치 과정에서 관찰된 component 여부와 관계없이 인증·runtime 연결·호환성 Gate는 이 범위에서 완료하지 않았다. readiness claim을 하지 않는다. |
| 설치 후 재부팅 지속성 | `NOT_RUN` | 설치 완료 뒤 전체 service의 재부팅 지속성을 검증하지 않았다. |
| PRIZM OpenSQL Gate | `NOT_RUN` | PRIZM Flyway migration, `vector(1024)`, cosine 검색, ownership, lease/fencing과 Worker SQL을 이 대상에서 실행하지 않았다. |
| 공개 저장소 안전 Gate | `PASS` | `node scripts/verify-oss-readiness.mjs`: 필수 파일, Markdown 38개·로컬 링크 262개, tracked file 300개 안전성, source-only license, SBOM 재생성·구조·checksum, 회귀 test 12건, `git diff --check` 통과. 외부 링크는 94개 성공, 1개 `INDETERMINATE`, 영구 실패 0개. |
| 독립 공개 감사 | `PASS` | 공급 자산·라이선스·VM/개인 식별값·내부 설치 정보의 공개 여부와 상태 표현을 독립적으로 재검사했으며 차단 항목이 없었다. 이는 전체 PRZ-003 기술 감사나 GitHub review가 아니다. |
| GitHub Issue | `NOT_CREATED` | 환경 준비 작업을 소급해 설명하는 Issue는 생성하지 않음. |
| GitHub PR | `MERGED` | [PR #17](https://github.com/jaemin-devlog/PRIZM/pull/17), source commit `8a633e8`, merge commit `b36f6b2`. |

## 2026-07-29 통합 전 합본 검증

- 대상: 노트북에서 푸시된 `origin/main` `3be415a`와 공개 경계를 정리한
  `33208b9`의 합본. `origin/main`은 현재 브랜치의 직접 조상이며 누락되거나
  충돌한 원격 변경은 없다.
- 환경: Windows host, Java 17, Docker Engine 29.6.2,
  Testcontainers PostgreSQL·pgvector, Ollama 0.32.3과
  `bge-m3:latest`를 실제 사용했다. OpenSQL은 이 회귀 검증에 사용하지 않았다.
- `.\gradlew.bat test --no-daemon`: `PASS`.
- `npm.cmd --prefix frontend run lint`: `PASS`.
- `npm.cmd --prefix frontend run build`: `PASS`.
- `docker compose config --quiet`: `PASS`.
- `.\gradlew.bat integrationTest --no-daemon --rerun-tasks`: 68건 중
  실패 1건, 건너뜀 3건으로 `FAIL`. 기존 테스트가 UTF-8 원본을 Windows 기본
  문자셋으로 비교하면서 `MalformedInputException`이 발생했다.
- 동일한 합본에서 `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`을 일시 지정한
  전체 통합 테스트는 68건 중 실패 0건, 건너뜀 3건으로 `PASS`했다.
- 실패 줄은 기존 commit `b9f01b04`에서 도입되어 노트북 최신 변경이나
  `33208b9`의 회귀는 아니다. 그러나 문서화된 기본 명령이 그대로 통과하지
  않으므로 상태는 `INTEGRATION_BLOCKED_RETURN_TO_SPEC`이며 `main` 병합 근거로
  사용하지 않는다.

## 2026-07-30 Windows UTF-8 교정 VERIFY

- 대상: `origin/main` `3be415a`와 PRZ-003 공개 경계 commit `33208b9`,
  통합 차단 근거 commit `0fdc2c3` 위의 교정 worktree.
- 수정: TXT 저장 통합 테스트가 실제 저장 파일을
  `StandardCharsets.UTF_8`로 명시해 읽도록 바꿨다. 제품 source, API,
  Flyway migration, ownership/security 계약, dependency와 license는
  변경하지 않았다.
- Windows 환경: Java 17, Docker Engine 29.6.2, Testcontainers
  PostgreSQL·pgvector, Ollama 0.32.3과 `bge-m3:latest`를 실제 사용했다.
  OpenSQL은 이 회귀 검증에 사용하지 않았다.
- `.\gradlew.bat integrationTest --no-daemon --rerun-tasks --tests
  "com.prizm.infrastructure.PgVectorInfrastructureTest.uploadsTxtAsQuarantinedDocumentAndStoresFile"`:
  `PASS`. `JAVA_TOOL_OPTIONS`와 `file.encoding` 강제 없이 실행했다.
- `.\gradlew.bat integrationTest --no-daemon --rerun-tasks`: 68건,
  실패 0건, 오류 0건, 건너뜀 3건으로 `PASS`.
- `.\gradlew.bat test --no-daemon`: `PASS`; 이어서
  `.\gradlew.bat test --no-daemon --rerun-tasks`로 실제 245건을 재실행해
  실패 0건, 오류 0건, Windows 플랫폼 건너뜀 14건을 확인했다.
- `npm.cmd --prefix frontend run lint`, `npm.cmd --prefix frontend run build`,
  `docker compose config --quiet`: 모두 `PASS`.
- `node scripts/verify-oss-readiness.mjs`: 필수 파일, Markdown 38개와
  로컬 링크 262개, tracked file 300개, source-only license, SBOM 재생성·
  구조·checksum, 회귀 테스트 12건, `git diff --check`를 통과했다.
  외부 링크는 94개 성공, 1개 HTTP 403 `INDETERMINATE`, 영구 실패 0개였다.

### 건너뜀 재감사

| 범위 | Windows 결과 | 재감사 결과 |
|---|---|---|
| OpenSQL 전용 Gate 1건 | `SKIPPED` | `RUN_OPENSQL_TESTS`, 전용 대상 확인, runtime/Flyway endpoint·credential이 현재 프로세스에 모두 없음을 값 노출 없이 확인했다. 공급 OpenSQL 대상에서 실행하지 않았으므로 결과는 계속 `NOT_RUN`이다. |
| cleanup 통합 테스트 2건 | `SKIPPED` | Windows가 `SecureDirectoryStream`을 제공하지 않아 보안 전제에서 중단됐다. `eclipse-temurin:17-jdk-jammy` Linux 컨테이너와 Docker Testcontainers에서 두 테스트만 현재 source로 재실행해 2건, 실패 0건, 오류 0건, 건너뜀 0건으로 `PASS`했다. OpenSQL은 사용하지 않았다. |
| 파일 저장 단위 테스트 14건 | `SKIPPED` | Windows의 symbolic-link 권한 또는 `SecureDirectoryStream` 부재 때문이며, fail-closed 회귀 테스트는 Windows에서도 실행되어 통과했다. 같은 Linux 컨테이너에서 `LocalFileStorageTest` 23건을 재실행해 실패 0건, 오류 0건, 건너뜀 0건으로 `PASS`했다. |

Linux 재검증 명령은 다음 두 종류였다.

```powershell
docker run --rm --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  --volume "${PWD}:/workspace" `
  --volume '/var/run/docker.sock:/var/run/docker.sock' `
  --volume 'prizm-gradle-cache:/root/.gradle' --workdir /workspace `
  eclipse-temurin:17-jdk-jammy bash -lc `
  "./gradlew integrationTest --no-daemon --rerun-tasks --tests 'com.prizm.infrastructure.PgVectorInfrastructureTest.cleanupWorkerDeletesPendingFilesTreatsMissingFilesAsCompletedAndDoesNotReclaimCompletedJobs' --tests 'com.prizm.infrastructure.PgVectorInfrastructureTest.cleanupWorkerConvergesToCompletedAfterCompletionUpdateFailureAndLeaseRecovery'"

docker run --rm --volume "${PWD}:/workspace" `
  --volume 'prizm-gradle-cache:/root/.gradle' --workdir /workspace `
  eclipse-temurin:17-jdk-jammy bash -lc `
  "./gradlew test --no-daemon --rerun-tasks --tests 'com.prizm.infrastructure.storage.LocalFileStorageTest'"
```

이 교정으로 기존 Windows UTF-8 차단은 해소됐다. 다만 OpenSQL 전용 Gate,
설치 후 재부팅 지속성, OpenProxy와 OpenHA는 계속 `NOT_RUN` 또는
`NOT_VERIFIED`이며 PostgreSQL·pgvector나 Linux cleanup 결과로 대체하지 않는다.

## 2026-07-30 corrective AUDIT

- `LOCAL_READ_ONLY_AUDIT_PASS`: 최종 diff 전체를 spec, 보존 계약, 실행 결과,
  공개 경계와 대조했다. 이는 GitHub review 또는 제3자 review가 아니다.
- 변경 파일은 기존 통합 테스트 1개와 PRZ-003 spec·plan·tasks·evidence,
  development log뿐이다. 제품 source, migration, dependency manifest,
  `LICENSE`, `NOTICE`와 배포 자산에는 변경이 없다.
- UTF-8 assertion은 실제 파일 읽기에만 문자셋을 명시하며 전역 JVM 설정을
  바꾸지 않는다. 기존 업로드·격리·ownership 계약도 변경하지 않는다.
- 비공개 OpenSQL 자산·식별값·credential·내부 log·사용자 절대 경로가 새 diff에
  포함되지 않았고 `git diff --check`와 OSS readiness가 통과했다.
- blocking finding은 0건이다. Windows UTF-8 회귀와 cleanup 플랫폼 경로에는
  실행 근거가 있다. 남은 감점 요인은 OpenSQL 전용 Gate와 설치 후 재부팅,
  OpenProxy/OpenHA가 아직 실행되지 않은 점이며 내부 평가 점수는 변경하지 않는다.

## 검증 경계

직접 `SELECT 1` 성공은 설치된 OpenSQL DB endpoint가 기본 인증 질의를 처리했다는
설치 근거일 뿐이다. PRIZM schema, Flyway, pgvector(1024), 검색, ownership,
Worker SQL, OpenProxy, failover 또는 OpenHA 호환성을 증명하지 않는다.
PostgreSQL-container, Docker와 Ollama 결과도 이후 OpenSQL Gate 결과와 분리한다.

## 공개 경계

- 공개 가능: Rocky Linux 9.7 VM의 OpenSQL `single` 설치 완료, 비공개
  Host-only 네트워크와 시간 동기화, 기본 DB 질의 결과와 `NOT_RUN` 경계.
- 공개 금지: 공급 archive·추출물, 테스트 라이선스, correspondence 원문,
  package/license fingerprint, 비공개 build metadata, 내부 installer command·
  log·설정, credential·key, hostname·IP·CPU 귀속값과 사용자 절대 경로.
- OpenSQL은 source-only PRIZM 저장소에 포함하거나 재배포하지 않는 외부 제공
  runtime이다. 개별 bundled OSS 고지가 공급사 전용 bundle 전체의 재배포 권한을
  의미한다고 가정하지 않는다.

## 평가 evidence

- 공개 안전성 작업의 주 평가 렌즈는 `EVAL-R1-03`, 보조 렌즈는
  `EVAL-R1-02`다.
- 설치 사실과 미실행 Gate를 구분하고 비공개 공급물의 고유값을 제거했다.
- Windows UTF-8 교정의 주 평가 렌즈는 `EVAL-R1-01`, 보조 렌즈는
  `EVAL-R1-03`, `EVAL-R1-05`다. 기본 명령의 실패를 제거하고 환경별 skip을
  실행 가능한 근거와 `NOT_RUN` 경계로 분리했다.
- 설치 전용 근거만으로 내부 추정 점수를 변경하지 않는다. `EVAL-R1-01`의
  다음 Gate인 PRIZM OpenSQL 실행 검증은 계속 `NOT_RUN`이다.
