# PRZ-003 근거

## 현재 근거

| 항목 | 결과 | 근거 |
|---|---|---|
| VM 라이선스 정책 | `PASS` | 2026-07-27 티맥스티베로 이메일 답변: VirtualBox Rocky Linux 9 VM 사용 가능, 게스트 hostname 및 vCPU/core/thread 제출, 고정 IP와 시간 동기화 필요. |
| VirtualBox 설치 | `PASS` | 2026-07-27 연구실 Windows 호스트에 Oracle VirtualBox 7.2.12 설치. |
| VM 하드웨어 | `PASS` | `PRIZM-OpenSQL`: EFI, 1 socket x 4 cores x 1 thread(총 4 vCPU), 12 GiB RAM, 동적 120 GiB VDI, NAT 및 DHCP 비활성 Host-only 어댑터. |
| Rocky Linux 설치 매체 | `PASS` | 공식 Rocky Linux 9.8 boot ISO SHA-256 확인: `d6eeefdc8437c593d41a3150fcca4a734c55642ed472eecdda99720bb1370881`. |
| Rocky Linux 게스트 | `PASS` | `PRIZM-OpenSQL`에 Rocky Linux 9.8 설치, 정적 hostname은 `prizm-opensql-01`. |
| 고정 Host-only 네트워크 | `PASS` | `enp0s8`은 `192.168.56.10/24`이며 Windows Host-only 주소 `192.168.56.1`까지 0% packet loss로 통신. 비공개 Host-only 경로이고 공용 포트 포워딩은 구성하지 않음. |
| 시간 동기화 | `PASS` | Asia/Seoul 설정, `timedatectl`의 system clock synchronized/NTP active, `chronyc tracking`의 Stratum 3 및 `Leap status: Normal` 확인. |
| 라이선스 신청 값 | `SUBMITTED` | 게스트 `lscpu` 기준으로 제출: hostname `prizm-opensql-01`; cpu(Socket(s)) `1`; core(Core(s) per socket) `4`; thread(Thread(s) per core) `1`; 계산된 총 vCPU `4`. Windows 호스트 CPU 모델값이 아님. |
| 테스트 라이선스 신청 | `SUBMITTED` | 2026-07-27 기록된 게스트 값으로 신청. 라이선스 발급 및 적용은 아직 `NOT_RUN`. |
| 설치 후 재부팅 및 관리자 확인 | `PASS` | 설치 매체 제거 뒤 Rocky Linux 재부팅, `jaemin@prizm-opensql-01`의 `sudo -v` 성공 확인. |
| OpenSQL 설치/라이선스 적용 | `NOT_RUN` | 라이선스 자산과 게스트 설치 근거 대기. |
| OpenSQL Gate | `NOT_RUN` | OpenSQL 대상과 전용 credential이 아직 없음. |
| GitHub Issue / PR | `NOT_CREATED` | 환경 준비 단계에서는 GitHub Issue/PR을 생성하지 않음. |

## 검증 경계

PostgreSQL, Docker, 로컬 Ollama 결과는 이후 OpenSQL 대상 결과와 분리해 기록한다.
이 문서의 어떤 결과도 OpenProxy 또는 OpenHA 호환성을 증명하지 않는다.

## 환경 선택 근거

Rocky Linux 9 x86_64와 격리된 VirtualBox 게스트는 연구실 Windows 개발 환경을 바꾸지
않으면서 공급사 지원 Linux 대상으로 시작하기 위해 선택했다. 4 vCPU, 12 GiB, 120 GiB
구성은 단일 노드의 기능 검증 기준선이며, 공급사 권장 사양이나 성능 결과가 아니다.
NAT는 설치 접근에, 고정 Host-only 경로는 Windows 호스트에서 반복 실행할 수 있는 비공개
접속에 사용한다. private IP와 hostname은 환경 근거일 뿐 credential, 공용 service endpoint,
이식 가능한 배포 요구사항이 아니다. 시간 동기화는 이후 별도 범위로 다룰 OpenHA 실험의
사전 조건으로 기록했으며 OpenHA는 계속 `NOT_RUN`이다.
