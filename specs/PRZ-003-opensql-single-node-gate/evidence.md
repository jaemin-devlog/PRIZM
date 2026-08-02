# PRZ-003 검증 근거

## 최종 상태

| 항목 | 값 |
|---|---|
| Spec 상태 | `VERIFIED` |
| 기준 source commit | `777e184f206d2a2770d055940ddabf139abfed9d` |
| Gate PR head | `bb6f9406bbabf924df62962b8e767d7b66c67104` |
| 마지막 검증일 | 2026-07-30 |
| 실제 기술 판정 | OpenSQL single-node SQL Gate `PASS` |
| GitHub Issue | `NOT_CREATED` — 환경 준비 작업을 소급해 설명하지 않음 |
| Review | `REVIEW_NOT_AVAILABLE_SOLO` |

이 결과는 OpenSQL single-node의 SQL 호환성 범위다. OpenProxy·OpenHA·DB
failover, OpenSQL+Ollama 전체 사용자 흐름과 browser demo를 증명하지 않는다.

## 요구사항별 판정

| 요구사항 | 판정 | 근거 |
|---|---|---|
| 요구사항 1 | `PASS_PRIVATE_EVIDENCE` | 전용 VirtualBox guest 한 대와 고정 식별값. 공급사 승인·신청값과 VM 식별 정보는 Git 밖에 보존 |
| 요구사항 2 | `PASS_PRIVATE_EVIDENCE` | Rocky Linux 9.7 x86_64와 고정 CPU topology의 발급 기준 일치 확인 |
| 요구사항 3 | `PASS_PRIVATE_EVIDENCE` | NAT + 비공개 고정 Host-only 네트워크, 공용 port forwarding 없음 |
| 요구사항 4 | `PASS_PRIVATE_EVIDENCE` | 신청 전 hostname·CPU/core/thread 확인. 정확한 값은 비공개 운영 근거 |
| 요구사항 5 | `PASS` | Asia/Seoul, NTP active, `NTPSynchronized=yes` 확인 |
| 요구사항 6 | `PASS` | Fresh target에서 실제 `OpenSqlInfrastructureTest` 1건 통과 |
| 요구사항 7 | `PASS_PRIVATE_EVIDENCE` | 공급 자산을 허가된 `single` 구성에만 사용하고 archive·license·fingerprint·내부 log는 Git 밖에 보존 |
| 요구사항 8 | `PASS` | Windows UTF-8 기본 명령 재실행과 Linux `SecureDirectoryStream` 경로 검증 |

## 실제 환경

| 환경·서비스 | 실제 결과 |
|---|---|
| OpenSQL guest | Rocky Linux 9.7 x86_64, 라이선스 적용 single-node VM 사용 |
| Network | NAT와 비공개 Host-only 사용; 실제 hostname·IP·topology는 비공개 |
| Gate client | Windows host, Java 17, Gradle Wrapper |
| Gate database | Fresh 전용 database, 분리된 Flyway owner와 최소 권한 runtime role |
| OpenProxy | `NOT_VERIFIED` |
| OpenHA·DB failover | `NOT_RUN` |
| Ollama·`bge-m3`·Spring application context·browser | OpenSQL Gate에서는 `NOT_RUN` |
| 설치 후 전체 service 재부팅 지속성 | `NOT_RUN` |
| Windows 회귀 | Docker PostgreSQL·pgvector와 Ollama `bge-m3` 사용; OpenSQL 근거와 분리 |
| Linux 파일 안전성 | JDK Linux container와 Testcontainers로 재실행 |

## 주요 검증 이력

- PR #17에서 Rocky Linux 9.7 single-node 검증 환경을 준비하고 공급 자산과 VM
  식별 정보를 Git 밖의 비공개 근거로 분리했다.
- 최초 Windows 통합 테스트에서 UTF-8 TXT 비교가 기본 문자셋을 사용해
  `MalformedInputException`이 발생했다. 파일을 `StandardCharsets.UTF_8`로
  읽도록 test를 교정한 뒤 전체 unit·integration 검증을 통과했다.
- Windows에서 실행할 수 없는 `SecureDirectoryStream` 성공 경로는 Linux에서
  별도로 재검증했다. Windows fail-closed 결과를 Linux 성공으로 바꾸지 않았다.
- 실제 OpenSQL fresh target에서 single-node SQL Gate를 통과하고 전용 database,
  임시 역할과 helper를 제거했다. 비밀정보는 저장하지 않았다.
- PR #24와 CI로 통합했으며 OpenProxy·OpenHA·DB failover, Ollama 색인과 browser
  흐름은 계속 `NOT_VERIFIED` 또는 `NOT_RUN`이다.

날짜별 상세 과정은 아래 실제 PR·CI와 Git history에서 확인한다.

## 실제 OpenSQL single-node Gate

실행 명령:

```powershell
.\gradlew.bat integrationTest --no-daemon --rerun-tasks `
  --tests com.prizm.infrastructure.OpenSqlInfrastructureTest
```

| 항목 | 결과 |
|---|---|
| JUnit | 1건, failure 0, error 0, skip 0 |
| Gradle | 종료 코드 0, `BUILD SUCCESSFUL`, 21초 |
| Flyway | Fresh target에 V1~V13 적용 |
| Vector | `vector(1024)`, CAST, `vector_dims`와 exact cosine 검색 통과 |
| Ownership | Owner·ACTIVE-version 격리 통과 |
| Worker SQL | Processing·cleanup claim, lease, fencing, recovery와 두 connection의 `SKIP LOCKED` 통과 |
| Secret 검사 | 출력에서 JDBC URL과 password·secret assignment 형태 0건 |
| 정리 | 전용 database·두 login role과 helper 부재 확인 |

Cleanup 대상 파일의 실제 삭제, OpenProxy runtime, OpenHA·DB failover, Ollama
색인과 browser 흐름은 이 Gate에 포함하지 않았다.

## Windows UTF-8과 플랫폼 재검증

| 단계 | 결과 |
|---|---|
| 최초 Windows 통합 테스트 | `FAIL` — UTF-8 TXT를 기본 문자셋으로 읽어 `MalformedInputException` 발생 |
| 임시 `file.encoding=UTF-8` 실행 | `PASS`였지만 문서화된 기본 명령의 성공 근거로 사용하지 않음 |
| 교정 | 실제 저장 파일을 `StandardCharsets.UTF_8`로 읽도록 test만 수정 |
| 교정 후 전체 integration | `PASS` — 68건, failure·error 0, 환경 조건 skip 3 |
| 교정 후 unit | `PASS` — 245건, failure·error 0, Windows 플랫폼 skip 14 |
| Linux cleanup 재검증 | `PASS` — 2건, failure·error·skip 0 |
| Linux `LocalFileStorageTest` | `PASS` — 23건, failure·error·skip 0 |

Windows의 `SecureDirectoryStream` 부재 때문에 건너뛴 성공 경로를 Windows
`PASS`로 바꾸지 않았다. Windows에서 실행되는 fail-closed 회귀와 Linux 성공
경로를 분리했다.

## 그 밖의 실행 검증

| 검증 | 결과 |
|---|---|
| `.\gradlew.bat test --no-daemon --rerun-tasks` | `PASS` — 245 tests, 환경 조건 skip 14 |
| `.\gradlew.bat integrationTest --no-daemon --rerun-tasks` | `PASS` — PostgreSQL·pgvector 68 tests, 환경 조건 skip 3 |
| `npm.cmd --prefix frontend run lint` | `PASS` |
| `npm.cmd --prefix frontend run build` | `PASS` |
| `docker compose config --quiet` | `PASS` |
| `node scripts/verify-oss-readiness.mjs` | `PASS` — 필수 파일, Markdown 38개·로컬 링크 262개, tracked file 300개, SBOM·checksum과 Node 회귀 12건 |
| 외부 링크 | 94개 `PASS`, HTTP 403 1개 `INDETERMINATE`, 영구 실패 0개 |
| `git diff --check` | `PASS` |

PostgreSQL·pgvector·Ollama 회귀 결과는 OpenSQL Gate 결과와 별개다.

## GitHub 통합과 review

| 작업 | 실제 기록 |
|---|---|
| 환경 준비 | [PR #17](https://github.com/jaemin-devlog/PRIZM/pull/17), source `8a633e8`, merge `b36f6b2` |
| 최종 Gate | [PR #24](https://github.com/jaemin-devlog/PRIZM/pull/24), source `bb6f9406bbabf924df62962b8e767d7b66c67104`, merge `777e184f206d2a2770d055940ddabf139abfed9d` |
| PR #24 OSS Readiness | [run 30477029567](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477029567) `PASS` |
| PR #24 CI | [run 30477029251](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477029251) `PASS` |
| 병합된 `main` OSS Readiness | [run 30477035697](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035697) `PASS` |
| 병합된 `main` CI | [run 30477035700](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035700) `PASS` |

PR #24에 requested reviewer·comment·review thread가 없었다. 독립 agent audit과
사용자 병합 승인은 GitHub review가 아니므로 `REVIEW_NOT_AVAILABLE_SOLO`다.

## 공개와 비공개 경계

공개할 수 있는 정보:

- Rocky Linux 9.7 VM의 OpenSQL `single` 설치 완료
- 비공개 Host-only 네트워크와 시간 동기화
- 기본 DB 연결과 실제 PRIZM single-node SQL Gate `PASS`
- OpenProxy·OpenHA·DB failover의 `NOT_RUN`·`NOT_VERIFIED` 경계

공개하지 않는 정보:

- 공급 archive·추출물, 테스트 license와 correspondence 원문
- Package/license fingerprint, 비공개 build metadata와 installer 명령·log·설정
- Credential·key, hostname·IP·CPU 귀속값과 사용자 절대 경로

OpenSQL은 PRIZM source-only 저장소에 포함하거나 재배포하지 않는 외부 runtime이다.

## 남은 제한

- 설치 후 전체 service 재부팅 지속성은 `NOT_RUN`이다.
- OpenProxy의 애플리케이션 인증·runtime 연결은 `NOT_VERIFIED`다.
- OpenHA와 DB failover는 `NOT_RUN`이다.
- OpenSQL+Ollama 색인, Spring Boot 전체 흐름과 browser demo는 `NOT_RUN`이다.
- Single-node 결과를 성능, 고가용성이나 다중 노드 근거로 확대할 수 없다.
