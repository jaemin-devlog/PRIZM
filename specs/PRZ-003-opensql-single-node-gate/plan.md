# PRZ-003 계획

## 외부 의존성과 권한

2026-07-27 티맥스티베로 기술지원팀 이메일 답변으로, OpenSQL 테스트 라이선스는
VirtualBox의 Rocky Linux 9 VM 안에서 사용할 수 있으며 신청서에는 게스트 hostname과
CPU/core/thread 값을 사용해야 함을 확인했다. 또한 게스트에는 고정(비 DHCP) IP와 시간
동기화가 필요하다. 라이선스 자산과 공급사 설치 절차가 도착하기 전에는 OpenSQL을
설치하거나 호환성을 주장하지 않는다.

## 선택지와 결정 근거

| 판단 항목 | 검토한 선택지 | 선택 | 이유와 경계 |
|---|---|---|---|
| 실행 OS | Windows 직접 설치, WSL, 지원 Linux VM | Rocky Linux 9 x86_64 VM | 제공된 OpenSQL 3.0 지원표에 포함된 Linux 계열을 사용한다. 이는 설치 대상 선택일 뿐 OpenSQL 설치·호환성 성공을 뜻하지 않는다. |
| 실행 격리 | Windows 개발 환경 공유, 별도 VM | VirtualBox 전용 VM | 설치·라이선스 적용·DB 복구 실패를 기존 Windows 개발 환경과 PostgreSQL 구성에서 분리한다. VM은 재생성 또는 snapshot 복구가 가능하지만, snapshot 사용 가능 여부는 공급사 라이선스 안내를 따른다. |
| 단일 노드 자원 | 최소 자원, 대규모 성능 자원 | 1 socket x 4 cores x 1 thread, 12 GiB RAM, 120 GiB 동적 디스크 | Flyway, vector, 검색, Worker SQL의 기능 검증을 위한 제한된 기준선이다. 공급사 권장 사양, 성능 벤치마크, OpenHA 다중 노드 용량 산정은 아니다. |
| 네트워크 | NAT만 사용, 브리지 공개, NAT + Host-only | NAT + Host-only | NAT는 게스트 패키지·공급사 설치 접근에 한정하고, Host-only는 Windows 호스트에서 반복 가능한 비공개 접속 경로로 사용한다. 브리지와 공용 포트 포워딩은 선택하지 않는다. |
| 주소 할당 | DHCP, 고정 Host-only IP | `192.168.56.10/24` 고정 Host-only IP | 공급사 안내의 비 DHCP 조건과 Windows 호스트의 반복 가능한 Gate 실행을 만족한다. 이 주소는 환경 근거이며 공개 Quickstart나 배포 계약의 의존값이 아니다. |
| 시간 설정 | 설치 후 필요 시 확인, 사전 동기화 | Asia/Seoul 및 NTP 사전 확인 | 향후 별도 범위의 OpenHA 실험이 요구할 수 있는 시간 일관성의 사전 조건을 먼저 기록한다. OpenHA 설정·장애 조치를 증명하지 않는다. |

## 환경 계획

| 항목 | 값 |
|---|---|
| Windows 호스트 역할 | 개발, Gradle Gate 실행, Ollama/RTX 호스트 |
| VM 이름 | `PRIZM-OpenSQL` |
| 게스트 hostname | `prizm-opensql-01` |
| 게스트 OS | Rocky Linux 9 x86_64 |
| 연산 자원 | 4 vCPU, 12 GiB RAM |
| 저장소 | 동적 할당 120 GiB 가상 디스크 |
| 어댑터 1 | 게스트 패키지 접근용 NAT |
| 어댑터 2 | Host-only, 어댑터 생성 뒤 고정 IP 지정 |

## 수행 순서

1. 연구실 Windows 호스트에 Oracle VirtualBox를 설치하고 동작을 확인한다.
2. 공식 Rocky Linux 9 x86_64 ISO를 내려받아 checksum을 검증하고, 정한 하드웨어
   할당으로 게스트를 생성한다.
3. Rocky Linux를 설치하고 hostname, Host-only 고정 IP를 설정한 뒤 `chronyc tracking`
   또는 동등한 시간 동기화 결과를 확인한다.
4. 라이선스 신청에 사용할 `hostnamectl --static`, `lscpu`, `nproc --all` 결과를 기록한다.
5. 기록된 게스트 값만 신청서에 제출한다. 라이선스, 비밀번호, `.env`는 commit하지 않는다.
6. 라이선스 자산과 공급사 설치 절차를 받은 뒤에만 OpenSQL을 설치하고 전용 검증
   데이터베이스 또는 스키마를 생성한다.
7. runtime과 Flyway URL을 명시적으로 분리한 상태에서 Windows 호스트로부터 문서화된
   OpenSQL Gate를 실행한다.

## 보안과 복구

- 데이터베이스 접근은 Host-only 네트워크에만 바인딩하고 공용 포트 포워딩을 만들지 않는다.
- OpenSQL 게스트를 OpenSQL 환경 근거의 단일 원천으로 둔다. 노트북 PostgreSQL 데이터는
  게스트에 복사하지 않는다.
- VM snapshot은 운영자 복구 선택지일 뿐 라이선스 허용을 뜻하지 않는다. 공급사가 제약을
  두면 그 지침을 따른다.

## Git 계획

작업은 `PRZ-003-opensql-single-node-gate`에서 수행한다. 실제 GitHub Issue나 PR은
외부 쓰기가 승인된 경우에만 생성하며, 과거 기록을 새로 만들지 않는다.
