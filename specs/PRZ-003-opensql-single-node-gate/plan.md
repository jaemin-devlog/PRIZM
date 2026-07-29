# PRZ-003 계획

## 외부 의존성과 권한

공급사의 서면 답변으로 VirtualBox의 Rocky Linux VM에서 대회용 테스트 라이선스를
사용할 수 있음을 확인했다. 원문 correspondence, 신청값과 발급 자산은 비공개로
보존한다. 공개 문서에는 라이선스 귀속 VM의 식별값과 CPU topology를 고정하고,
비공개 고정 네트워크와 시간 동기화를 유지해야 한다는 운영 경계만 남긴다.

후속 공급 자료는 Rocky Linux 9.7과 `single` 구성을 요구했다. 설치 파일과
라이선스에는 공개 재배포 권한을 가정하지 않으며, 공급 자산의 이름·버전·hash·
metadata·설정·log를 공개 저장소에 기록하지 않는다.

## 선택지와 결정 근거

| 판단 항목 | 검토한 선택지 | 선택 | 이유와 경계 |
|---|---|---|---|
| 실행 OS | Windows 직접 설치, WSL, 지원 Linux VM | Rocky Linux 9.7 x86_64 VM | 공급사가 지정한 Linux 환경을 사용한다. 이는 설치 대상 선택일 뿐 OpenSQL 호환성 성공을 뜻하지 않는다. |
| 실행 격리 | Windows 개발 환경 공유, 별도 VM | VirtualBox 전용 VM | 설치·라이선스 적용·DB 복구 실패를 기존 Windows 개발 환경과 PostgreSQL 구성에서 분리한다. VM은 재생성 또는 snapshot 복구가 가능하지만, snapshot 사용 가능 여부는 공급사 라이선스 안내를 따른다. |
| 단일 노드 자원 | 최소 자원, 대규모 성능 자원 | 신청 후 고정한 단일 VM topology | Flyway, vector, 검색, Worker SQL의 기능 검증을 위한 제한된 기준선이다. 정확한 라이선스 귀속 값은 비공개이며 공급사 권장 사양, 성능 벤치마크, OpenHA 다중 노드 용량 산정이 아니다. |
| 네트워크 | NAT만 사용, 브리지 공개, NAT + Host-only | NAT + Host-only | NAT는 게스트 패키지·공급사 설치 접근에 한정하고, Host-only는 Windows 호스트에서 반복 가능한 비공개 접속 경로로 사용한다. 브리지와 공용 포트 포워딩은 선택하지 않는다. |
| 주소 할당 | DHCP, 고정 Host-only IP | 비공개 고정 Host-only IP | 공급사 환경 조건과 Windows 호스트의 반복 가능한 Gate 실행을 만족한다. 실제 주소는 공개 Quickstart나 배포 계약에 포함하지 않는다. |
| 시간 설정 | 설치 후 필요 시 확인, 사전 동기화 | Asia/Seoul 및 NTP 사전 확인 | 향후 별도 범위의 OpenHA 실험이 요구할 수 있는 시간 일관성의 사전 조건을 먼저 기록한다. OpenHA 설정·장애 조치를 증명하지 않는다. |

## 환경 계획

| 항목 | 값 |
|---|---|
| Windows 호스트 역할 | 개발, Gradle Gate 실행, Ollama/RTX 호스트 |
| VM 식별값 | 라이선스 귀속 값으로 고정, Git에는 비공개 |
| 게스트 OS | Rocky Linux 9.7 x86_64 |
| 연산 자원 | 신청 후 고정, 정확한 topology는 Git에는 비공개 |
| 저장소 | 격리된 동적 할당 가상 디스크 |
| 어댑터 1 | 게스트 패키지 접근용 NAT |
| 어댑터 2 | Host-only, 비공개 고정 IP |

## 수행 순서

1. 연구실 Windows 호스트에 Oracle VirtualBox를 설치하고 동작을 확인한다.
2. 지원되지 않는 초기 guest disk는 삭제하지 않고 분리해 보존한 뒤, 공식
   Rocky Linux 9.7 ISO의 checksum을 확인해 새 guest disk를 구성한다.
3. hostname, CPU topology, Host-only 고정 IP와 시간 동기화를 확인하고 정확한
   출력은 비공개 운영 근거로 보존한다.
4. 기록된 게스트 값만 라이선스 절차에 사용하고 공급 자산·비밀번호·설정은
   Git에 넣지 않는다.
5. 공급된 자산을 guest에 직접 전달해 허가된 `single` 구성만 설치한다.
   OpenHA나 두 번째 노드는 구성하지 않는다.
6. 설치 완료 log와 기본 service 상태는 비공개로 보존하고 공개 문서에는
   비식별 결과와 검증 경계만 기록한다.
7. 설치와 기본 연결 확인 뒤, 사용자가 승인한 후속 Gate에서 runtime과 Flyway
   credential을 분리한 전용 빈 검증 대상을 구성하고 PRIZM OpenSQL Gate를 실행한다.

## 보안과 복구

- 데이터베이스 접근은 Host-only 네트워크에만 바인딩하고 공용 포트 포워딩을 만들지 않는다.
- OpenSQL 게스트를 OpenSQL 환경 근거의 단일 원천으로 둔다. 노트북 PostgreSQL 데이터는
  게스트에 복사하지 않는다.
- 공급 archive, 라이선스, 추출 파일, 생성 설정, credential, key, screenshot과
  installer log는 저장소 또는 source 배포물에 포함하지 않는다.
- VM snapshot은 운영자 복구 선택지일 뿐 라이선스 허용을 뜻하지 않는다. 공급사가 제약을
  두면 그 지침을 따른다.
- Gate credential은 실행 중에만 메모리와 환경 변수로 전달하고 명령행, 공개 log,
  저장소 파일에 남기지 않는다.
- Windows OpenSSH의 대화형 비밀번호 입력과 표준입력 기반 비밀 전달을 동시에 사용하지
  않는다. Gate 세션에만 쓰는 일회성 SSH key를 Git 밖에서 생성·등록하고, 정확한 key
  comment를 기준으로 Gate 종료 시 guest의 `authorized_keys`와 Windows 임시 key를
  모두 제거한다.
- 실제 Linux 명령과 출력을 운영자가 VirtualBox console에서 동시에 확인할 수 있도록
  guest의 일회성 `tmux` 세션을 공유한다. Codex는 SSH로 같은 세션에 명령을 보내고,
  비밀번호 입력은 운영자가 guest console에서 직접 수행한다. `tmux`는 검증 운영
  도구이며 PRIZM runtime 또는 배포 dependency로 추가하지 않는다.
- Gate 실패 시 Flyway가 적용된 대상을 빈 대상으로 간주해 재사용하지 않는다. 실패
  대상은 원인 확인 전 보존하고, 재시도는 새 전용 데이터베이스와 새 credential로 한다.

## 실행 기록

- 공식 Rocky Linux 9.7 설치 매체를 checksum 검증한 뒤 사용했다.
- 공급 자산과 라이선스는 Git 밖에서만 전달·적용했으며 공급 파일을 수정하거나
  재배포하지 않았다.
- 로컬 dependency 조정이 필요했던 상세 진단은 공급사 지원을 위한 비공개 근거로
  남겼다. 공개 문서에는 비공개 build의 내부 경로·오류·우회 명령을 싣지 않는다.
- OpenSQL `single` 설치와 직접 인증 기본 SQL 질의는 완료했다.
- PRIZM Flyway V1~V13·`vector(1024)`·검색·ownership·Worker SQL Gate는 실제
  OpenSQL single-node에서 통과했다. OpenProxy 기능 검증과 설치 후 재부팅
  지속성, OpenHA·DB failover는 아직 실행하지 않았다.

## 첫 OpenSQL Gate 계획

- Windows 호스트에서 OpenSQL 게스트의 직접 데이터베이스 endpoint를 사용한다.
  OpenProxy와 OpenHA는 이 Gate의 검증 대상이 아니다.
- 관리자는 기존 기본 데이터베이스를 변경하지 않고 일회성 전용 데이터베이스,
  Flyway owner, runtime role을 생성한다. 역할 이름, endpoint와 credential은
  비공개 실행 정보로만 보존한다.
- 관리자는 전용 데이터베이스에 `vector` extension을 준비한다. Flyway owner는
  V1~V13 schema migration을 수행하고, runtime role은 `CONNECT`, `TEMPORARY`,
  `public` schema `USAGE`, 생성된 table의 `SELECT`·`INSERT`·`UPDATE`·`DELETE`,
  sequence의 `USAGE`·`SELECT`·`UPDATE`만 받는다.
- Flyway owner의 default privilege를 runtime role에 연결해 migration이 생성하는
  table과 sequence에 위 권한이 적용되도록 한다. 두 역할은 같은 전용 데이터베이스와
  schema를 사용하되 서로 다른 credential을 사용한다.
- 관리 SQL은 OpenSQL OS 계정의 로그인 환경에서 실행한다. root 또는 일반 계정의
  기본 Unix socket 위치를 OpenSQL client 접속 경로로 가정하지 않는다.
- 관리자 사전 점검은 서버 연결, 관리자 SQL 접속, `vector` extension 가용성만
  확인한다. 이는 PRIZM OpenSQL 호환성 성공 근거가 아니다.
- 성공 조건은 새 빈 대상에서 `OpenSqlInfrastructureTest` 1건이 실행되어 V1~V13,
  `vector(1024)` exact cosine 검색, 소유자·active-version 제약, processing/cleanup
  lease·fencing·recovery·`SKIP LOCKED` SQL을 모두 통과하는 것이다.
- 실행 명령은 아래와 같다. 모든 환경 변수는 비공개 실행 세션에서만 설정한다.

```powershell
.\gradlew.bat integrationTest --no-daemon --rerun-tasks `
  --tests com.prizm.infrastructure.OpenSqlInfrastructureTest
```

- 실패하면 단계명과 비식별 오류만 공개 근거에 기록하고 기존 대상은 재사용하지 않는다.
  성공해도 검증 대상은 감사 완료 전까지 보존하며, 삭제는 정확한 대상 확인 뒤 별도
  정리 단계에서 수행한다.

## Windows UTF-8 회귀 교정 계획

- 기존 통합 테스트는 UTF-8로 저장한 TXT를 AssertJ의 기본 문자셋으로 읽어
  Windows에서 `MalformedInputException`을 발생시킨다. 애플리케이션 저장 동작이
  아니라 검증 코드의 문자셋 의존 문제이므로 기존 PRZ-003 안에서 교정한다.
- 실제 저장 파일과 기대값을 모두 `StandardCharsets.UTF_8`로 명시해 비교한다.
  Gradle 또는 JVM 전체의 기본 문자셋은 강제하지 않는다.
- 수정 뒤 문제를 재현한 단일 테스트와 전체 `integrationTest`를 Windows에서
  정확한 기본 명령으로 다시 실행한다. Docker, PostgreSQL·pgvector와 Ollama
  사용 여부 및 전체 실패·건너뜀 수를 기록한다.
- OpenSQL opt-in 테스트는 전용 endpoint와 credential 없이 실행하지 않는다.
  Windows가 제공하지 않는 `SecureDirectoryStream` 기반 삭제 성공 경로는
  보안 전제를 낮춰 실행하지 않으며, Windows에서 실행되는 fail-closed 단위
  테스트와 현재 Linux 재실행 가능 여부를 별도로 감사한다.
- 이 교정은 제품 source, API, Flyway, ownership/security 계약, dependency와
  license를 변경하지 않는다. 복구가 필요하면 해당 테스트 assertion만 되돌린다.

## Git 계획

환경 준비는 기존 PR로 병합됐다. 설치 근거의 공개 안전성 보완은 최신 `main`에서
분기한 `codex/PRZ-003-opensql-install-evidence`에서 수행한다. 실제 GitHub Issue나 PR은
외부 쓰기가 승인된 경우에만 생성하며, 과거 기록을 새로 만들지 않는다.

## 관리자 권한 재계획 (2026-07-30)

- Gate 전용 데이터베이스와 분리된 Flyway/runtime 역할을 만들려면 OpenSQL DB 관리자 역할의 권한이 필요하다.
- 현재 설치 서비스 역할 `opensql`은 `CREATEROLE`과 `CREATEDB` 권한이 없어, 첫 `CREATE ROLE`에서 중단됨을 확인했다. 이 시도는 역할·데이터베이스·테이블을 만들지 않았다.
- Linux OS의 `postgres` 계정은 존재하지 않음을 읽기 전용으로 확인했다. 따라서 OS 계정 전환을 관리자 DB 접속 방법으로 가정하지 않는다.
- 설치 과정에서 설정된 DB 관리자 `postgres`의 인증은 VirtualBox console의 비밀 입력 프롬프트로 직접 확인했다. 다음 provisioning은 비밀번호를 실행 중 메모리에만 두고 전용 DB와 역할 bootstrap에만 사용한다. 비밀번호 추측·재설정이나 권한 강제 변경은 범위에서 제외한다.
- 첫 provisioning은 전용 대상 구성까지 성공했으나, 비밀 context 전달을 기다리기 전에 VM 명령 전송 SSH가 종료되지 않아 Gradle을 시작하지 못했다. 대상에는 migration·PRIZM 데이터가 없었으며 정확한 임시 DB와 두 역할을 제거했다. 이후 명령 전송은 표준 입력을 닫는 방식으로 재실행한다.
