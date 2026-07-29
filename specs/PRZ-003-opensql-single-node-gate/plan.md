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
7. 설치와 기본 연결 확인 뒤 멈춘다. runtime과 Flyway credential을 분리한
   PRIZM OpenSQL Gate는 별도 승인된 후속 VERIFY에서 실행한다.

## 보안과 복구

- 데이터베이스 접근은 Host-only 네트워크에만 바인딩하고 공용 포트 포워딩을 만들지 않는다.
- OpenSQL 게스트를 OpenSQL 환경 근거의 단일 원천으로 둔다. 노트북 PostgreSQL 데이터는
  게스트에 복사하지 않는다.
- 공급 archive, 라이선스, 추출 파일, 생성 설정, credential, key, screenshot과
  installer log는 저장소 또는 source 배포물에 포함하지 않는다.
- VM snapshot은 운영자 복구 선택지일 뿐 라이선스 허용을 뜻하지 않는다. 공급사가 제약을
  두면 그 지침을 따른다.

## 실행 기록

- 공식 Rocky Linux 9.7 설치 매체를 checksum 검증한 뒤 사용했다.
- 공급 자산과 라이선스는 Git 밖에서만 전달·적용했으며 공급 파일을 수정하거나
  재배포하지 않았다.
- 로컬 dependency 조정이 필요했던 상세 진단은 공급사 지원을 위한 비공개 근거로
  남겼다. 공개 문서에는 비공개 build의 내부 경로·오류·우회 명령을 싣지 않는다.
- OpenSQL `single` 설치와 직접 인증 기본 SQL 질의는 완료했다.
- OpenProxy 기능 검증, 설치 후 재부팅 지속성, PRIZM Flyway·vector·검색·Worker
  SQL Gate는 아직 실행하지 않았다.

## Git 계획

환경 준비는 기존 PR로 병합됐다. 설치 근거의 공개 안전성 보완은 최신 `main`에서
분기한 `codex/PRZ-003-opensql-install-evidence`에서 수행한다. 실제 GitHub Issue나 PR은
외부 쓰기가 승인된 경우에만 생성하며, 과거 기록을 새로 만들지 않는다.
