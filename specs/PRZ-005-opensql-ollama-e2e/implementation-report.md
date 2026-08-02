# PRZ-005 실제 OpenSQL 통합 작업 보고서

이 문서는 [PRZ-005 Spec](spec.md), [실행 계획](plan.md), [작업 목록](tasks.md)에 따라 실제로 수행한 OpenSQL 환경 준비와 애플리케이션 검증 결과를 정리한다. PostgreSQL과 OpenSQL의 증거, 과거 실행 상태와 현재 최종 상태를 구분한다. 실패·중단·rollback 기록도 재현성과 판단 근거로 보존한다.

## 30초 요약

| 확인할 내용 | 현재 결과 |
|---|---|
| PRZ-005 최종 상태 | `VERIFIED` |
| 실제 OpenSQL API E2E | `VERIFIED` |
| 브라우저 UI E2E | `VERIFIED` |
| 두 사용자 격리 | `VERIFIED` |
| 자동 OpenSQL 통합 테스트 | `VERIFIED` |
| 전체 회귀·OSS·SBOM 감사 | `VERIFIED` |
| OpenProxy SQL 인증·routing | `AUTH_BLOCKED`·`NOT_VERIFIED`, 적용 `DEFERRED` |
| 다음 후보 단계 | P2 DB 장애복구 Gate |

## 최종 검증 결과

| 영역 | 결과 | 핵심 증거 |
|---|---|---|
| 애플리케이션 | `VERIFIED` | Spring Boot → OpenSQL `5432` 연결, Ollama `bge-m3`, 로그인, TXT/PDF 업로드, 임베딩, `ACTIVE` 전환, 벡터 검색과 원문 위치를 API와 브라우저에서 확인했다. |
| 데이터베이스 | `VERIFIED` | Flyway `V1`–`V13` 13개, 현재 V13, pending·실패 0개이며 6개 테이블과 6개 시퀀스의 소유자는 `prizm_owner`다. vector는 `0.8.1`, 소유자는 `postgres`다. |
| 보안·격리 | `VERIFIED` | `prizm_owner`와 `prizm_app`을 분리했고, 두 USER의 목록·상세·검색 격리와 DB owner 불일치 0건을 확인했다. |
| 자동 테스트 | `VERIFIED` | 격리된 `prizm_integration_test`에서 OpenSQL opt-in integration test 1개가 성공했고 실패·오류·skip은 0건이었다. |
| 회귀·OSS | `VERIFIED` | 백엔드 단위 262개·통합 69개, 프론트엔드 lint·typecheck·build, OSS readiness·SBOM·문서·민감정보 감사를 통과했다. 프론트엔드 unit test는 공식 명령이 없어 `NOT_RUN`이다. |

## 현재 보류 범위

| 항목 | 상태 | 현재 판단 |
|---|---|---|
| OpenProxy TCP 연결 | `VERIFIED` | Windows Host-only 주소에서 VM의 `6432`까지 연결됐다. |
| OpenProxy SQL routing | `NOT_VERIFIED` | TCP 연결과 달리 실제 SQL 반환은 확인하지 못했다. |
| OpenProxy 인증 | `AUTH_BLOCKED` | OpenProxy 1.1.3에서 안전한 외부 비밀정보 주입 방식을 확인하지 못했다. |
| OpenProxy 적용 | `DEFERRED` | 공급사의 안전한 공식 인증 구성 답변 뒤 별도 Gate로 재개한다. |
| OpenHA·DB failover | `DEFERRED` | PRZ-005는 single-node 범위이며 P2 DB 장애복구 Gate의 후속 후보로 남긴다. |
| 영구 journal | `DEFERRED` | 적용 필요성과 로그 보존 정책을 별도로 결정한다. |

## 전체 연결 흐름

```text
브라우저 UI → React/Vite → Spring Boot
                              ├─ Flyway: prizm_owner → OpenSQL 5432 → prizm DB
                              ├─ Runtime: prizm_app → OpenSQL 5432 → pgvector
                              └─ Embedding: Ollama 0.32.3 → bge-m3:latest

Windows Host-only → OpenProxy 6432: TCP만 VERIFIED
OpenProxy 6432 → OpenSQL SQL routing: NOT_VERIFIED
```

PostgreSQL clean-clone 결과는 [PRZ-004 Evidence](../PRZ-004-clean-clone-demo/evidence.md), 실제
OpenSQL single-node SQL Gate 결과는 [PRZ-003 Evidence](../PRZ-003-opensql-single-node-gate/evidence.md)에
각각 보존한다. PostgreSQL 성공을 OpenSQL 성공으로 바꾸어 기록하지 않는다.

## 상태 코드

<details>
<summary>상태 코드 설명 보기</summary>

| 코드 | 의미 |
|---|---|
| `VERIFIED` | 실제 실행 결과로 정상 동작을 확인했다. |
| `PARTIALLY_VERIFIED` | 필요한 구간 중 일부만 확인했다. |
| `NOT_VERIFIED` | 대상은 준비됐지만 실제 동작을 확인하지 못했다. |
| `NOT_RUN` | 계획만 있고 실행하지 않았다. |
| `AUTH_BLOCKED` | 네트워크 연결 뒤 인증 단계에서 보안 요구를 만족하지 못해 중단했다. |
| `DEFERRED` | 현재 범위에서 제외하고 이후 작업으로 미뤘다. |

</details>

## 주요 구성요소

<details>
<summary>OpenSQL 구성요소 설명 보기</summary>

### OpenSQL

OpenSQL은 TmaxTibero가 제공하는 PostgreSQL 기반 DBMS 플랫폼이다. PRIZM은 커리어 문서의
메타데이터, 처리 상태, 추출 텍스트와 벡터를 실제 지정과제 환경에 저장하기 위해
OpenSQL을 사용한다. 이번 작업에서는 single-node 설치의 실행 구조와 직접 포트 인증을
확인했다.

### PostgreSQL

PostgreSQL은 PRIZM이 기존 개발과 clean-clone 검증에서 사용한 관계형 데이터베이스다.
OpenSQL이 PostgreSQL 생태계와 호환되므로 기존 SQL, Flyway migration과 JDBC 연결을
재사용할 수 있는지 확인해야 한다. PostgreSQL에서의 성공은 OpenSQL 검증 증거와 별도로
관리한다.

### Patroni

Patroni는 PostgreSQL 프로세스의 실행 상태와 Leader 상태를 관리한다. 이 VM에서는
OpenSQL 데이터베이스를 직접 실행하는 대신 Patroni가 실행과 종료를 관리한다. 이번
작업에서는 단일 멤버가 `Leader`이며 `running` 상태인지 확인했다.

### etcd

etcd는 Patroni가 클러스터 상태와 Leader 정보를 공유하는 분산 키-값 저장소다. 현재는
single-node이지만 Patroni의 관리 구조에 필요하다. 따라서 OpenSQL보다 먼저 etcd의
상태를 확인하고 기동했다.

### OpenProxy

OpenProxy는 OpenSQL 앞에서 클라이언트 연결을 받아 백엔드 데이터베이스로 전달하는
연결 계층이다. PRIZM에서는 향후 연결 경로와 장애 대응 기능을 검증할 후보지만 현재
핵심 E2E의 필수 조건은 아니다. Windows에서 OpenProxy `6432`까지의 연결은
`VERIFIED`다. SQL routing은 `NOT_VERIFIED`이고 인증은 `AUTH_BLOCKED`다. 안전한 공식
인증 방식을 공급사에 확인할 때까지 OpenProxy SQL 연결은 `DEFERRED`다.

### systemd와 systemd unit

systemd는 Rocky Linux에서 서비스를 시작하고 상태를 관리한다. systemd unit은 실행
사용자, 시작 순서와 명령을 선언하는 서비스 정의 파일이다. 공급 설치 결과에 없던
Patroni와 OpenProxy unit을 추가해 `etcd → Patroni/OpenSQL → OpenProxy` 순서를 명확히
했다.

### Flyway

Flyway는 버전이 붙은 SQL migration을 순서대로 데이터베이스에 적용한다. PRIZM에서는
`prizm_owner`가 `V1`–`V13`을 적용하고 애플리케이션 역할과 DDL 권한을 분리한다.
`V1`–`V13`과 예상 객체를 조사했고, D2A에서 Spring Context 없이 Flyway만 실행하는 테스트
전용 경로를 추가했다. D2B에서 실제 OpenSQL에 `V1`–`V13`을 적용하고 이력을 검증했다.

### Ollama bge-m3

Ollama는 로컬에서 AI 모델을 실행하는 도구다. `bge-m3`는 PRIZM 문서 텍스트를
1024차원 임베딩으로 변환하는 모델이다. 일반 PostgreSQL clean-clone에서 이 흐름을
검증했고, 이 시점에는 실제 OpenSQL 연결이 `NOT_RUN`이었다. 이후 D5에서 Ollama
`0.32.3`과 `bge-m3:latest`의 모델 digest를 확인하고, 1024차원·0이 아닌 임베딩을
실제 OpenSQL에 저장해 TXT/PDF 검색을 검증했다.

</details>

## 시작 당시 상태

<details>
<summary>시작 당시 환경과 준비 상태 보기</summary>

| 항목 | 시작 상태 | 의미 |
|---|---|---|
| Rocky Linux VM | Rocky Linux 9.7 설치와 고정 Host-only 주소 구성 | 공급사가 제공한 실제 OpenSQL single-node 검증 환경은 준비돼 있었다. |
| OpenSQL 설치 상태 | single-node 설치 파일과 데이터가 존재 | 제품은 설치됐지만 재부팅 뒤 공식 기동 경로를 다시 확인해야 했다. |
| OpenSQL `5432` | Windows TCP 연결을 일부 확인 | 포트 연결만으로 애플리케이션 호환성을 증명할 수 없었다. |
| OpenProxy `6432` | VM에서 구성 요소가 관찰됐으나 기능은 `NOT_VERIFIED` | TCP, 인증과 SQL routing을 분리해 확인해야 했다. |
| etcd | 설치 구성 요소가 존재 | Patroni보다 먼저 상태와 기동 순서를 확인해야 했다. |
| Patroni | 프로세스와 설정은 존재했으나 전용 systemd unit이 없었음 | 재부팅 뒤 일관된 서비스 관리 절차가 없었다. |
| VM 시간 | 시스템 시간이 약 19시간 느리고 동기화되지 않음 | 로그 순서와 검증 시각을 신뢰할 수 없었다. |
| `prizm` DB | 없음 | PRIZM 전용 저장 공간을 새로 만들어야 했다. |
| `prizm_owner` | 없음 | Flyway 전용 소유 역할을 새로 만들어야 했다. |
| `prizm_app` | 없음 | 애플리케이션 최소 권한 역할을 새로 만들어야 했다. |
| 제품 코드와 migration | 기준 commit의 코드와 `V1`–`V13` 유지 | VM 작업을 위해 제품 구현을 바꿀 이유가 없었다. |

</details>

## 계획과 초기 환경 점검

PostgreSQL과 OpenSQL 증거를 분리하고, VM·네트워크·시간을 바꾸기 전에 중단 조건부터 정했다. 초기 점검에서 약 19시간의 시간 오차와 journald·커널 관련 finding을 확인했으므로 서비스 변경보다 환경 정상화를 우선했다.

<details>
<summary>범위 확정, 초기 VM 점검과 시간 동기화 기록 보기</summary>

### 1. ORIENT → SPEC → PLAN

- **목표:** 기존 PostgreSQL 결과와 실제 OpenSQL 결과를 섞지 않고 작업 범위와 중단 조건을 고정했다.
- **조치:** 저장소의 AGENTS 규칙, PRZ-003·PRZ-004 Evidence와 현재 OpenSQL 관련 문서를 확인했다. PRZ-005 Spec, 계획과 작업 목록을 작성했다. 인프라 준비, 직접 SQL, 애플리케이션 E2E와 OpenProxy를 서로 다른 Gate로 나눴다.
- **결과:** OpenSQL SQL Gate 통과만으로 로그인부터 검색까지의 애플리케이션 흐름을 주장할 수 없었다. 작업 기준 commit과 변경 금지 범위를 문서에 고정했다. 계획 당시 제품 코드와 migration 변경은 없었다.
- **후속:** VM 건강 상태를 먼저 확인한 뒤에만 서비스와 데이터베이스를 변경하도록 순서를 정했다.

### 2. 초기 VM과 OpenSQL 점검

- **목표:** 설치 환경, 네트워크와 실행 중인 구성 요소의 실제 상태를 파악했다.
- **조치:** Rocky Linux 버전, hostname, CPU 구조, Host-only 네트워크, 포트와 프로세스를 읽기 전용으로 확인했다. DB 변경을 중단하고 시간, 파일시스템과 서비스 관리 구조 확인을 우선했다.
- **결과:** 시간 동기화가 꺼져 있었고 journald 오류 기록이 있었다. SSH `22`와 OpenSQL `5432`가 일시적으로 응답하지 않는 상황도 발생했다. 지원 OS와 고정 VM identity는 유지됐다. OpenSQL 공급 자산은 저장소에 포함하지 않았다. 이 단계에서는 시스템 설정과 DB를 변경하지 않았다.
- **후속:** 검증 시각을 신뢰하기 위해 시간 동기화부터 바로잡아야 했다.

### 3. 시간 동기화 문제 확인

- **목표:** 로그 시각과 인증 판단의 기준이 되는 시스템 시간을 정상화했다.
- **조치:** chrony 상태, timezone과 시스템 동기화 여부를 점검했다. Chrony 동기화가 완료될 때까지 서비스 검증을 진행하지 않았다.
- **결과:** VM 시간이 약 19시간 느렸다. 재부팅 후 Chrony가 약 18.4초를 추가 보정했다. TSC 시간원 불안정과 관련된 finding도 확인했다. 최종 확인 시 `System clock synchronized: yes`였다. TSC finding의 장기 영향과 재발 여부는 계속 관찰해야 한다.
- **후속:** 동기화된 시각을 기준으로 재부팅 이후의 서비스 상태를 다시 확인했다.

</details>

## VM 장애 대응과 OpenSQL 서비스 준비

VM 응답 중단 뒤 파일시스템·로그·서비스 구조를 순서대로 확인했다. OpenSQL을 직접 실행하지 않고 `etcd → Patroni/OpenSQL → OpenProxy` 관리 경로를 구성했으며, Windows Host-only 주소에만 `6432/tcp`를 허용했다.

<details>
<summary>VM 복구, systemd unit 등록, 서비스 기동과 방화벽 검증 기록 보기</summary>

### 4. Linux 커널 응답 중단과 VM 재부팅

- **목표:** 응답하지 않는 VM을 안전하게 복구하고 원인을 추측하지 않은 채 관찰 사실을 보존했다.
- **조치:** `/home/opensql` 관련 항목을 확인하던 중 VM 응답이 중단됐다. 화면에서 NMI 관련 경고와 `NMI handler took too long` 메시지를 확인했다. 먼저 ACPI 정상 종료를 요청했다. 응답하지 않을 때 Save State를 사용하지 않고 VM을 종료한 뒤 다시 시작했다.
- **결과:** SSH `22`와 OpenSQL `5432`도 응답하지 않았다. 디렉터리 확인 작업과 커널 중단 사이의 직접적인 인과관계는 확인되지 않았다. VM이 다시 부팅됐고 Host-only 네트워크가 복구됐다. 커널 응답 중단의 확정 원인은 남아 있다.
- **후속:** 서비스를 바로 시작하지 않고 파일시스템, 시간과 이전 부팅 로그를 먼저 확인했다.

### 5. 재부팅 후 파일시스템·시간·journald 점검

- **목표:** 강제 종료 뒤 데이터 손상과 로그 신뢰성 문제를 확인했다.
- **조치:** 파일시스템 기본 상태, 시간 동기화, systemd-journald 상태와 이전 부팅의 커널 메시지를 점검했다. 명백한 파일시스템 손상이 없는지 확인했다. 영구 journal 적용은 이번 핵심 연결 작업과 분리해 `DEFERRED`로 남겼다.
- **결과:** journald watchdog, core dump와 corrupted journal 관련 메시지가 있었다. 영구 journal은 구성돼 있지 않았다. 부팅과 기본 파일 접근은 가능했다. 오프라인 전체 파일시스템 검사는 실행하지 않았으므로 파일시스템 상태는 `PARTIALLY_VERIFIED`다. 영구 journal 적용 여부와 journald 재발 관찰이 남았다.
- **후속:** 서비스를 임의로 실행하지 않고 공급 설치 구조에서 공식 기동 방법을 조사했다.

### 6. OpenSQL 공식 기동 구조 조사

- **목표:** PostgreSQL/OpenSQL을 직접 실행하지 않고 공급 구조에 맞는 시작과 종료 순서를 확인했다.
- **조치:** 설치 문서, 공급 스크립트, 제한된 설정 경로와 기존 etcd unit을 확인했다. 기동 순서를 `etcd → Patroni/OpenSQL → OpenProxy`로 정했다. OpenSQL은 Patroni를 통해서만 기동하도록 결정했다.
- **결과:** etcd unit은 있었지만 Patroni와 OpenProxy를 관리할 systemd unit은 없었다. Patroni가 OpenSQL 프로세스를 관리하고 OpenProxy는 정상화된 database 뒤에 시작해야 함을 확인했다. Patroni와 OpenProxy systemd unit이 필요했다.
- **후속:** 실제 등록 전에 unit 초안을 만들고 정적 검증했다.

### 7. Batch A1 — Patroni·OpenProxy systemd unit 초안 작성

- **목표:** 서비스 시작 순서, 실행 계정과 종료 방식을 실제 등록 전에 검증했다.
- **조치:** `opensql` 사용자와 그룹, 작업 디렉터리, 실행 명령과 의존 관계를 반영한 두 unit 초안을 로컬 Git 제외 경로에 작성했다. 자동 rollback 프레임워크와 불필요한 파싱을 제외하고 unit 등록에 필요한 최소 구성만 남겼다.
- **결과:** 처음 검토한 설치 자동화는 현재 목적보다 복잡했다. 두 unit 초안은 `systemd-analyze verify`를 통과했다. 아직 `/etc/systemd/system`에는 등록하지 않은 상태였다.
- **후속:** 검증된 파일만 Batch A2에서 등록했다.

### 8. Batch A2 — systemd unit 등록

- **목표:** 재현 가능한 서비스 관리 지점을 만들되 자동 부팅은 활성화하지 않았다.
- **조치:** `patroni.service`와 `openproxy.service`를 root 소유 `0644`로 등록했다. Patroni가 요구하는 runtime 환경 파일을 `opensql` 전용 `0600`으로 만들었다. `patroni.yml`과 `openproxy.toml`도 `opensql:opensql 0600`으로 제한했다. 공급 설치 자료의 기존 값을 화면에 출력하지 않고 권한이 제한된 파일로 옮겼다.
- **결과:** 민감한 runtime 값은 화면과 Git에 노출할 수 없었다. daemon reload와 정적 검증을 통과했다. 등록 직후 두 서비스는 `disabled`, `inactive`였다. unit, runtime 환경 파일과 제한된 설정 파일 권한이 VM에 남았다.
- **후속:** Gate별 확인을 거쳐 수동으로 서비스를 시작할 준비가 됐다.

### 9. Batch B — etcd·Patroni·OpenSQL·OpenProxy 기동

- **목표:** 공식 순서대로 single-node 서비스를 기동하고 각 단계의 건강 상태를 확인했다.
- **조치:** etcd health를 먼저 확인했다. 그 뒤 Patroni를 시작하고 Leader와 OpenSQL 상태를 확인했다. 마지막으로 OpenProxy를 시작했다. `active`는 현재 실행 상태로 기록했다. `disabled`는 재부팅 시 자동 시작되지 않는 상태로 기록했다.
- **결과:** 서비스의 `active`와 `disabled`가 서로 다른 의미라는 점을 문서에서 구분해야 했다. 단일 Patroni 멤버가 `Leader`, `running`이었다. `5432`, Patroni API `8008`과 `6432`가 LISTEN 상태였다. Patroni와 OpenProxy의 반복 재시작은 없었다. 이 단계의 마지막 검증 시점에 etcd는 실행 중이었고 재부팅 자동 시작이 활성화돼 있었다. Patroni와 OpenProxy도 실행 중이었지만 재부팅 자동 시작은 활성화하지 않았다.
- **후속:** VM 내부 상태가 정상화된 뒤 Windows 네트워크 연결을 확인했다.

### 10. Batch B2 — Windows 6432 연결과 방화벽 설정

- **목표:** Windows 호스트만 OpenProxy에 접근할 수 있는 최소 네트워크 경계를 만들었다.
- **조치:** Host-only 인터페이스의 기본 차단 정책을 유지했다. Windows Host-only 주소 한 개에서 오는 `6432/tcp`만 runtime 규칙으로 허용한 뒤 연결을 확인했다. 성공 후 같은 규칙을 permanent에 적용했다. 전체 서브넷이나 모든 출발지에 포트를 열지 않았다. 기존 `22`와 `5432` 규칙도 변경하지 않았다.
- **결과:** 변경 전에는 `6432`가 방화벽에서 차단됐다. Windows에서 VM의 `6432`까지 TCP 연결이 성공했다. 이 결과는 SQL routing 증거가 아니다. Windows Host-only 주소 한 개에만 적용되는 `6432/tcp` runtime·permanent 규칙이 남았다.
- **후속:** 애플리케이션용 DB와 역할을 만든 뒤 실제 인증 경로를 분리해 검증했다.

</details>

## DB·역할·Flyway·최소 권한 구성

관리자 비밀번호를 추측하지 않고 Unix 로컬 소켓 관리자 경로를 사용했다. Flyway는 `prizm_owner`, runtime은 `prizm_app`으로 분리했으며, OpenProxy의 안전한 인증 방식이 확인되지 않자 설정을 원복하고 OpenSQL `5432` 직접 경로로 진행했다.

<details>
<summary>DB·역할 생성, OpenProxy 인증 판단, Flyway `V1`–`V13`과 최소 권한 기록 보기</summary>

### 11. Batch C1 — prizm DB와 최소 권한 역할 생성

- **목표:** 관리자 계정을 애플리케이션에서 사용하지 않고 Flyway와 runtime 권한을 분리했다.
- **핵심 조치**
  - Unix 로컬 소켓 관리자 경로로 `prizm_owner`, `prizm_app`과 `prizm` DB를 만들었다.
  - `PUBLIC`의 불필요한 CONNECT·스키마 CREATE 권한을 제거하고 vector `0.8.1`을 만들었다.
  - postgres 비밀번호를 추측하거나 초기화하지 않았다. 실패한 실행은 새로 만든 DB와 역할만 제거했다.
- **결과**
  - postgres 네트워크 비밀번호는 확인할 수 없었다. 권한 검증 가정과 실제 OpenSQL 동작에도 차이가 있었다.
  - 중간 검증에서 세 번 rollback한 뒤 최종 실행이 성공했다. 기존 데이터와 서비스 설정은 영향을 받지 않았다.
  - 두 역할의 `5432` 직접 인증을 확인했다. `prizm_app`의 DB·역할·스키마 생성은 거부됐다.
  - probe 객체는 0건이었다. `prizm` DB, 두 login 역할과 vector 확장이 남았다.
- **후속:** `prizm_app`을 OpenProxy에만 제한해 SQL routing을 확인하려 했다.

### 12. Batch C2 — OpenProxy 인증 검증과 설정 복원

- **목표:** 관리자 역할을 노출하지 않고 `prizm_app → prizm` 경로만 OpenProxy에서 검증하려 했다.
- **핵심 조치**
  - OpenProxy 1.1.3의 공식 도움말과 현재 설정 구조를 확인했다.
  - SCRAM verifier, `server_password`와 인증 유형을 제한적으로 검토했다.
  - 정적 검증 뒤 OpenProxy만 재시작했다. 평문 비밀번호 저장은 승인하지 않았다.
  - 변경 전 백업으로 설정을 정확히 복원하고 OpenProxy만 정상 재시작했다.
- **결과**
  - 확인된 구성은 백엔드 접속용 평문 비밀번호를 `openproxy.toml`에 요구했다. 안전한
    `query_auth`, 환경변수나 별도 secret 파일 구성은 확인하지 못했다.
  - 인증은 `AUTH_BLOCKED`, SQL routing은 `NOT_VERIFIED`로 남았다. Windows TCP 연결은 유지됐다.
  - 복원 후 OpenProxy는 `active`, 재시작 0, `6432` LISTEN 상태였다. 설정 파일은
    `opensql:opensql 0600`이었다.
  - 백업본과 복원본의 SHA-256은
    `ad78d290d745b3f8b69692c87e7390a787b7d5c01602d3fdb5411096c23c0873`으로 일치했다.
    OpenProxy 인증 변경은 남지 않았고 PRIZM DB와 역할도 그대로 유지했다.
- **후속:** OpenProxy를 핵심 E2E의 선행 조건에서 제외하고 `5432` 직접 연결로 진행하기로 했다.

### 13. OpenSQL 5432 직접 연결 정책 결정

- **목표:** 검증되지 않은 인증 우회를 만들지 않고 다음 애플리케이션 작업의 안전한 경로를 정했다.
- **조치:** Flyway는 `prizm_owner`, Spring Boot runtime은 `prizm_app`으로 OpenSQL `5432`에 직접 연결하는 정책을 확정했다. OpenProxy 공급사 문의와 핵심 E2E를 분리했다. 관리자 역할은 OpenProxy에 노출하지 않는다.
- **결과:** OpenProxy SQL routing은 아직 확인할 수 없지만 실제 OpenSQL 직접 인증은 이미 가능했다. 이 단계에서는 직접 연결 정책만 확정했으며 Flyway와 Spring Boot는 `NOT_RUN`이었다. 이 정책 결정으로 제품 코드, migration과 dependency는 변경하지 않았다.
- **후속:** 다음 Gate는 `V1`–`V13` 객체와 권한을 조사한 뒤 Flyway를 실제 실행하는 것이다.

### 14. Batch D1–D2A — migration 조사와 실행 경로 Gate

- **목표:** `V1`–`V13` 객체와 `prizm_app` 권한을 확인하고, Spring Boot 없이 Flyway만 실행할 경로를 판단했다.
- **핵심 조치**
  - migration 연속성, 예상 테이블·시퀀스와 객체 소유권을 읽기 전용으로 조사했다.
  - Patroni Leader, OpenSQL, 빈 대상 DB, vector `0.8.1`과 두 역할의 `5432` 인증을 확인했다.
  - 별도 Flyway task·CLI와 기존 실행 경로를 비교한 뒤 `MIGRATION_EXECUTION_PATH_BLOCKED`로 중단했다.
  - D2A에서 `OpenSqlFlywayMigrationOnlyTest`를 추가했다. 첫 D2B 실패 뒤 pending `1`–`13`,
    applied 0, current 없음부터 확인하도록 순서를 교정했다.
- **결과**
  - `V1`–`V13`에는 누락이 없고 예상 테이블과 시퀀스는 각각 6개였다. Flyway 전용
    task·CLI는 없었고 `bootRun`과 기존 통합 테스트는 더 넓은 구성을 실행했다.
  - 첫 D2B의 사전 `validate()`는 `FlywayValidateException`을 반환했다. `migrate()`는 호출되지 않았다.
    당시 Flyway 이력·도메인 테이블·시퀀스는 0개였고 `relkind` 조회는 타입 연결 오류로 실패했다.
  - 이후 `relkind::text`를 사용했다. 교정된 테스트는 컴파일·기본 `SKIPPED`를 통과했고,
    재실행에서 현재 V13, pending·실패 0, 두 번째 migrate 신규 적용 0을 확인했다.
  - 6개 테이블·6개 시퀀스와 이력 소유자는 `prizm_owner`, 데이터는 0건이었다.
    vector는 `0.8.1`, 소유자는 `postgres`로 유지됐다.
- **후속:** D2B가 통과해 migration 객체가 준비됐다. 이어서 D3에서 `prizm_app`의 객체별 최소 runtime 권한을 적용하고 검증했다.

### 15. Batch D3 — prizm_app 최소 runtime 권한

- **목표:** Flyway 소유 역할과 애플리케이션 실행 역할을 분리하고, `prizm_app`에는 실제 runtime에 필요한 권한만 부여한다.
- **핵심 조치**
  - `prizm` DB CONNECT와 `public` schema USAGE를 부여했다.
  - `users`에는 SELECT·INSERT를 부여했다. `documents`·`document_versions`·`processing_jobs`에는
    SELECT·INSERT·UPDATE·DELETE를 부여했다.
  - `document_chunks`에는 SELECT·INSERT·DELETE, `file_cleanup_jobs`에는 SELECT·INSERT·UPDATE를 부여했다.
  - 6개 시퀀스에는 USAGE만 부여했다. 이력 권한과 ALTER DEFAULT PRIVILEGES는 사용하지 않았다.
- **결과**
  - GRANT와 사후 검증을 한 트랜잭션에서 수행하고 조건이 일치한 뒤 COMMIT했다.
  - 합성 데이터로 허용된 SELECT·INSERT·UPDATE·DELETE를 확인했다.
  - CREATE TABLE·SCHEMA·DB·ROLE, 금지된 DML, 이력 접근과 TRUNCATE는 거부됐다.
  - probe 트랜잭션은 ROLLBACK했고 잔여 객체는 0건이었다. `prizm_app`과 PUBLIC에는
    TEMPORARY 권한도 없었다.
- **후속:** D4에서 `prizm_app`으로 Spring Boot를 OpenSQL `5432`에 연결해 JPA schema validation과 health를 확인했다.

</details>

## Spring Boot·Ollama·API·브라우저 E2E

먼저 애플리케이션 시작과 JPA validation만 검증한 뒤 Ollama·업로드·검색을 단계적으로 연결했다. D5 API E2E와 D6 두 사용자 격리·브라우저 E2E가 통과했으며, 실제 개인정보 대신 first-party 합성 TXT/PDF만 사용했다.

<details>
<summary>Spring Boot 기동, Ollama·API E2E와 두 사용자·브라우저 검증 기록 보기</summary>

### 16. Batch D4 — Spring Boot 직접 연결과 JPA validation

- **목표:** 로그인·업로드·Ollama를 실행하기 전에 Spring Boot가 분리된 두 DB 역할로 안전하게 시작되는지 확인한다.
- **핵심 조치**
  - Flyway는 `prizm_owner`, runtime datasource는 `prizm_app`으로 OpenSQL `5432`의 `prizm` DB에 연결했다.
  - demo·관리자 bootstrap, indexing Worker와 cleanup Worker를 기존 설정으로 비활성화했다.
  - JPA `ddl-auto=validate`를 유지했다. 첫 실행 도구는 지원되지 않는 난수 API로 시작 전에 중단됐다.
  - 비밀 환경변수를 제거한 뒤 Windows PowerShell 호환 API로 실행 도구만 교정했다.
- **결과**
  - Spring ApplicationContext, JPA EntityManagerFactory와 Tomcat이 시작됐다.
  - health는 HTTP 200·`UP`이었고 OpenSQL에서 `prizm_app` 세션 1개를 확인했다.
  - Flyway V13·이력 13개·pending/실패 0, 6개 테이블·6개 시퀀스와 데이터 0건을 유지했다.
  - CREATE·ALTER·DROP은 없었다. 종료 뒤 프로세스·포트·DB 세션과 서비스 재시작 횟수는 0이었다.
- **후속:** D4 당시 Ollama `bge-m3`, 로그인, TXT/PDF 업로드, 임베딩과 검색을 연결한 실제 OpenSQL E2E는 `NOT_RUN`이었다. 이후 D5와 D6에서 검증했다.

### 17. Batch D5 — Ollama와 TXT/PDF 핵심 API 흐름

- **목표:** Spring Boot → OpenSQL `5432` → Ollama `bge-m3` → 업로드·색인·검색의 핵심 API E2E를 검증한다.
- **핵심 조치**
  - Flyway는 `prizm_owner`, runtime은 `prizm_app`을 사용했다.
  - demo `USER` bootstrap을 한 번만 켠 뒤 끄고 indexing Worker만 활성화했다.
  - Ollama `0.32.3`과 `bge-m3:latest`를 사용했다. 모델 digest는
    `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`였다.
  - 1024차원·유한·0이 아닌 벡터를 확인했다. 모델을 갱신하거나 bit-identical로 표현하지 않았다.
- **결과**
  - demo 로그인과 JWT 발급, 합성 TXT/PDF 각 1개 업로드를 확인했다. 두 버전은
    `ACTIVE`, 두 job은 `COMPLETED`가 됐다.
  - TXT는 `TEXT_CHUNK` 1, PDF는 `PAGE` 1과 고유 문장을 반환했다. 비인증 API는 `401`이었다.
  - DB에는 사용자 1명과 문서·버전·chunk·job 각 2개가 남았다. 1024차원·0이 아닌
    embedding은 2개, owner 불일치는 0건이었다.
  - 두 번째 demo 비밀번호 확인 불일치로 생성 전에 중단됐다. 당시 두 USER 격리와 UI는 `NOT_RUN`이었다.
- **후속:** Spring Boot 종료 뒤 포트·`prizm_app` 세션은 0이었고, Patroni·OpenProxy는 active·재시작 0을 유지했다. 두 USER 격리와 브라우저 흐름은 D6에서 별도로 검증했다.

### 18. Batch D6 — 두 사용자 격리와 브라우저 UI

- **목표:** 두 사용자의 문서·검색 경계를 API와 DB에서 확인하고, 기존 React UI의 핵심 흐름을 실제 OpenSQL 위에서 검증한다.
- **핵심 조치**
  - D5 USER를 USER_A로 유지하고 USER_B를 기본 비활성 bootstrap으로 한 번만 생성했다.
  - 각 JWT로 문서 목록·상세·검색을 교차 확인했다.
  - React UI에서 로그인, 목록·상세, PDF 원문과 경력 근거 검색을 확인했다.
  - 합성 TXT/PDF 추가 업로드와 로그아웃 뒤 보호 경로도 확인했다.
- **결과**
  - 상대 문서의 목록·상세·검색 결과는 노출되지 않았고 owner 불일치는 0건이었다.
  - 새 TXT/PDF는 처리 완료됐다. TXT `TEXT_CHUNK` 1과 PDF `PAGE` 1 근거가 표시됐다.
    브라우저 콘솔 warning·error는 0건이었다.
  - 첫 UI 로그인 때 백엔드가 종료돼 Vite proxy 연결이 거부됐다. 같은 승인 설정으로 재시작해 통과했다.
  - 최종 DB에는 사용자 2명과 문서·버전·chunk·job 각 5개가 남았다.
    `ACTIVE`·`COMPLETED`·1024차원 0이 아닌 embedding은 각 5개였고 소유자 불일치는 0건이었다.
  - OSS readiness는 Markdown 48개·로컬 링크 476개와 tracked-file 안전성을 확인했다.
    라이선스·SBOM 회귀와 별도 SBOM 구조 검사도 통과했다.
- **후속:** 백엔드·프론트엔드를 종료해 `18080`·`5173`과 `prizm_app` 세션을 0으로 만들었다. D6 시점에는 전체 백엔드·프론트엔드 회귀와 독립 감사가 `NOT_RUN`이었고, 이후 T-18에서 완료했다.

</details>

## 자동 통합 테스트·전체 회귀·병합 감사

실제 증거가 있는 `prizm` DB를 보호하기 위해 자동 OpenSQL 통합 테스트는 별도 `prizm_integration_test`에서 실행했다. 이후 백엔드·프론트엔드·OSS·SBOM·문서 감사를 완료하고, PR #26의 검증 커밋과 병합 커밋을 기록했다.

<details>
<summary>격리 통합 테스트, 전체 회귀와 GitHub 병합 증거 보기</summary>

### T-17 — 격리 OpenSQL 통합 테스트

- **목표:** 현재 소스의 `OpenSqlInfrastructureTest`를 실제 `prizm` 증거 DB와 분리해 실행한다.
- **핵심 조치**
  - 테스트는 `RUN_OPENSQL_TESTS=true`와
    `PRIZM_OPENSQL_VERIFICATION_TARGET_CONFIRMED=true`를 요구한다.
  - 정확한 대상 DB는 `prizm_integration_test`다.
  - migration·GRANT는 `prizm_owner`, DML·vector 검색·Worker SQL은 `prizm_app`으로 실행했다.
  - `V1`–`V13`, 객체·소유자와 vector가 일치할 때만 테스트 전용 최소 권한을 부여했다.
- **결과**
  - 테스트 1개가 성공했고 실패·오류·skip은 0건이었다.
  - `flyway_schema_history`의 `prizm_app`·PUBLIC 권한과 테스트 데이터는 0건이었다.
    격리 DB에는 `V1`–`V13` 객체와 명시적 최소 권한만 남았다.
  - 실제 `prizm` DB의 사용자 2명과 문서·버전·chunk·job 각 5개는 유지됐다.
  - `ACTIVE`·1024차원 embedding 각 5개, Flyway V13·이력 13개도 유지됐다. 사용자별 문서는
    `1:4,2:1`, 소유자 불일치는 0건으로 실행 전후가 같았다.
- **후속:** `clean`, `repair`, `baseline`, DB·schema DROP과 실제 `prizm` DB 변경은 수행하지 않았다. 다음 Gate에서 전체 회귀와 독립 감사를 진행했다.

### T-18 — 전체 회귀와 최종 감사

- **목표:** PRZ-005 변경과 검증 증거가 전체 저장소 회귀와 문서 상태를 깨뜨리지 않았는지 독립적으로 확인한다.
- **핵심 조치**
  - 백엔드·프론트엔드, OSS readiness, SBOM과 Markdown 링크를 감사했다.
  - 민감정보와 변경 범위를 확인하고 T-18A에서 오래된 현재형 표현을 교정했다.
- **결과**
  - 백엔드 단위 테스트 262개와 통합 테스트 69개가 실패·오류 없이 통과했다.
  - 기본 회귀의 OpenSQL opt-in 테스트는 승인 환경변수가 없어 정상적으로 `SKIPPED`됐다.
  - 프론트엔드 lint·typecheck·production build와 OSS·SBOM·문서·민감정보 감사가 통과했다.
  - 프론트엔드 unit test는 공식 명령이 없어 `NOT_RUN`이다.
- **후속:** T-18은 `DONE`, PRZ-005 핵심 범위는 `VERIFIED`로 판정했다. OpenProxy·OpenHA·영구 journal은 별도 후속 범위로 유지했다.

### GitHub 통합 기록

- **목표:** 검증된 소스가 실제 `main`에 반영됐다는 관리 증거를 남긴다.
- **조치:** 검증 소스 commit `eab32c870f06237d37048b6b8de1287e5e18ae66`을 [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26)으로 통합했다.
- **결과:** `main` merge commit은 `6dc982227bafe94f0879c22bf4381a6e47adf925`, 병합 시각은 2026-08-02 20:40:49 KST다. 백엔드 2건, 프론트엔드 2건, License·Markdown·SBOM 2건 등 GitHub checks 6건이 `SUCCESS`였다. 등록된 review는 없어 `REVIEW_NOT_AVAILABLE_SOLO`다.
- **후속:** 존재하지 않는 Issue나 review를 증거로 만들지 않았다. 다음 후보는 P2 DB 장애복구 Gate이며, PRZ-005의 비필수 보류 항목과 분리한다.

</details>

## 주요 문제와 해결 과정

| 문제 | 증상 | 확인한 원인 또는 판단 | 대응 | 결과 | 남은 한계 |
|---|---|---|---|---|---|
| 시간 동기화와 TSC finding | VM 시간이 약 19시간 느렸고 재부팅 뒤 Chrony가 약 18.4초를 추가 보정했다. | 초기 동기화가 완료되지 않았으며 TSC finding의 영향은 확정되지 않았다. | Chrony 동기화 완료 전 후속 검증을 중단했다. | `System clock synchronized: yes`, 시간 동기화 `VERIFIED` | TSC 영향과 재부팅 뒤 재발 여부는 계속 관찰해야 한다. |
| journald 오류와 영구 journal 부재 | watchdog·core dump·corrupted journal 메시지가 있었고 이전 부팅 로그 보존이 제한됐다. | 직접 원인은 확정되지 않았고 persistent journal이 구성되지 않았다. | 재부팅 뒤 상태를 점검하고 핵심 DB 연결과 분리했다. | 현재 부팅과 서비스 검증은 가능했다. | 영구 journal과 재발 관찰은 `DEFERRED` |
| VM 응답 중단·NMI·파일시스템 우려 | `NMI handler took too long` 뒤 SSH `22`와 OpenSQL `5432`가 응답하지 않았다. | `/home/opensql` 확인 중 발생했지만 직접 인과관계와 커널 원인은 확인되지 않았다. | ACPI 정상 종료를 먼저 요청하고 필요 시 강제 종료·재부팅한 뒤 파일 접근과 서비스를 점검했다. | VM과 두 포트가 복구됐고 명백한 손상은 발견되지 않았다. | 커널 원인 `NOT_VERIFIED`, 파일시스템 `PARTIALLY_VERIFIED`, 오프라인 전체 검사 `NOT_RUN` |
| Patroni·OpenProxy unit 부재 | 재부팅 뒤 표준 서비스 관리 지점이 없었다. | 공급 설치 결과에 두 systemd unit이 없었다. | 최소 unit을 작성·정적 검증·등록했다. | unit 등록 `VERIFIED` | 자동 시작은 의도적으로 `disabled` |
| Windows `6432` 차단 | OpenProxy가 LISTEN 중이지만 Windows TCP 연결이 실패했다. | Host-only 방화벽에 `6432` 허용 규칙이 없었다. | Windows Host-only 주소 한 개에만 runtime·permanent 규칙을 추가했다. | TCP 연결 `VERIFIED` | SQL routing과 인증 증거는 아니다. |
| 관리자 인증과 C1 rollback | postgres 네트워크 인증이 실패했고 역할 생성 뒤 권한·결과 검증도 세 번 실패했다. | 관리자 비밀번호를 확인할 수 없었고 일부 검증 가정이 OpenSQL 동작과 달랐다. | 비밀번호를 추측·초기화하지 않고 Unix 소켓 관리자 경로를 사용했으며, 각 실패 실행의 새 객체만 rollback했다. | DB·역할·vector 구성이 최종 성공했다. | 기존 데이터와 서비스 설정에는 영향이 없었다. |
| OpenProxy 인증·SQL routing | TCP는 성공했지만 `prizm_app` SQL이 백엔드로 전달되지 않았다. | OpenProxy 1.1.3에서 안전한 외부 secret 주입을 확인하지 못했고 현재 구성은 평문 백엔드 비밀번호를 요구했다. | 평문 저장을 거부하고 백업본으로 설정을 복원해 공급사 문의로 전환했다. | 인증 `AUTH_BLOCKED`, routing `NOT_VERIFIED`; 복원 SHA-256 일치 | 안전한 공식 방식 확인 전 적용 `DEFERRED` |

## 주요 기술 결정

### OpenSQL 실행 구조

- Patroni를 통해 OpenSQL 프로세스를 실행한다 — 공급 관리 구조를 우회하면 상태 판단과 정상 종료가 어려워진다.
- `etcd → Patroni/OpenSQL → OpenProxy` 순서를 사용한다 — Leader 상태가 준비된 뒤에만 연결 계층을 시작해야 한다.
- single-node의 Leader·기동 결과를 OpenHA·DB failover로 확대하지 않는다 — 다중 노드 장애 전환을 실행하지 않았다.
- 마지막 검증 시점에 etcd는 `active`·자동 시작이었고 Patroni·OpenProxy는 `active`·`disabled`였다 — 실행 상태와 재부팅 자동 시작 설정은 서로 다르다.

### 계정·권한 분리

- postgres 관리자는 애플리케이션에서 사용하지 않는다 — 사용자 요청 경로에 관리자 권한을 노출하지 않는다.
- Flyway는 `prizm_owner`, runtime은 `prizm_app`으로 실행한다 — DDL과 일상 DML 권한을 분리한다.
- `prizm_owner`가 DB·`V1`–`V13` 객체·`flyway_schema_history`를 소유한다 — migration 이력과 객체 소유권을 한 역할로 고정한다.
- `prizm_app`에는 승인한 객체별 DML과 시퀀스 USAGE만 부여한다 — DB·역할·스키마 생성과 Flyway 이력 접근을 차단한다.
- PUBLIC의 불필요한 CONNECT·CREATE를 제거하고 vector는 관리자 경로로 생성한다 — 명시된 역할만 접근하게 하고 `prizm_owner`를 superuser로 올리지 않는다.

### OpenProxy 보안 판단

- OpenProxy에는 postgres와 `prizm_owner`를 노출하지 않는다 — 인증 검증 대상을 `prizm_app`으로 제한한다.
- 백엔드 평문 비밀번호 저장은 승인하지 않는다 — 연결 편의보다 비밀정보 경계를 우선한다.
- 핵심 E2E는 OpenSQL `5432` 직접 경로를 사용한다 — 두 전용 역할의 실제 인증이 확인됐다.
- OpenProxy SQL routing은 공급사의 안전한 인증 구성 답변 뒤 검증한다 — TCP 성공을 SQL 성공으로 확대하지 않는다.

### 검증·재현성 정책

- PostgreSQL과 OpenSQL 결과를 분리한다 — PostgreSQL 성공은 OpenSQL 실행 증거가 아니다.
- 실제 `prizm` DB와 자동 통합 테스트 DB를 분리한다 — D5·D6 증거 데이터를 보존한다.
- migration-only와 opt-in 통합 테스트는 명시적 환경변수 Gate가 없으면 건너뛴다 — 일반 회귀가 실제 OpenSQL을 변경하지 않게 한다.
- `bge-m3:latest`의 digest를 기록하되 bit-identical 재현으로 표현하지 않는다 — `latest`는 가변 태그다.
- 실패·rollback·당시 `NOT_RUN`을 현재 `VERIFIED`와 함께 보존한다 — 최종 결과만으로 실행 과정을 지우지 않는다.

## 시스템에 실제로 남은 변경

| 변경 | 현재 상태 |
|---|---|
| `patroni.service` | `/etc/systemd/system`에 등록, root 소유 `0644` |
| `openproxy.service` | `/etc/systemd/system`에 등록, root 소유 `0644` |
| `patroni-runtime.env` | Patroni 실행에 필요한 제한된 환경 파일 생성, `opensql:opensql 0600` |
| `patroni.yml` | 내용은 유지하고 접근 권한을 `opensql:opensql 0600`으로 제한 |
| `openproxy.toml` | 내용은 변경 전 상태로 복원하고 `opensql:opensql 0600` 유지 |
| `6432/tcp` 방화벽 | Windows Host-only 주소 한 개에만 runtime·permanent 허용 |
| etcd 실행 상태 | 마지막 검증 시점에 `active`, 재부팅 자동 시작 활성화 |
| `prizm` 데이터베이스 | owner `prizm_owner`로 생성 |
| `prizm_owner` | Flyway·객체 소유용 제한된 login 역할로 생성 |
| `prizm_app` | 애플리케이션용 제한된 login 역할로 생성 |
| vector 확장 | `prizm` DB에 `0.8.1` 생성, 확장 소유자는 `postgres` |
| Flyway 객체 | `V1`–`V13`, 6개 도메인 테이블, 6개 BIGSERIAL 시퀀스와 이력 테이블 생성; 소유자 `prizm_owner` |
| `prizm_app` 권한 | DB CONNECT, schema USAGE와 승인된 객체별 DML·시퀀스 USAGE 부여 |
| Patroni·OpenProxy 실행 상태 | 마지막 검증 시점에 `active`, 재부팅 자동 시작은 `disabled` |

C2에서 확인한 일치 SHA-256은 설정 내용 자체를 공개하지 않고 복원 무결성을 확인하는
근거다.

제품 소스 코드, 기존 Flyway migration 파일과 dependency는 변경하지 않았다. 비밀번호,
토큰과 실제 runtime 환경변수 값도 저장소에 기록하지 않았다.

## 현재 연결 정책

### Flyway

- `prizm_owner`를 사용한다.
- OpenSQL `5432`에 직접 연결한다.
- `prizm` DB를 대상으로 한다.
- `V1`–`V13` 적용과 이력 검증 상태는 `VERIFIED`다.

### Spring Boot

- `prizm_app`을 사용한다.
- OpenSQL `5432`에 직접 연결한다.
- `prizm` DB를 대상으로 한다.
- JPA schema validation과 health를 포함한 직접 연결 상태는 `VERIFIED`다.

### OpenProxy

- Windows에서 `6432`까지의 네트워크 연결은 `VERIFIED`다.
- 실제 SQL routing은 `NOT_VERIFIED`다.
- 인증은 `AUTH_BLOCKED`다.
- 공급사 답변 전까지 `DEFERRED`다.
- postgres와 `prizm_owner` 같은 관리자 역할은 OpenProxy에 노출하지 않는다.

다음 환경변수 이름만 사용한다. 실제 값은 터미널 출력, 문서와 Git에 기록하지 않는다.

- `SPRING_PROFILES_ACTIVE`
- `PRIZM_FLYWAY_URL`
- `PRIZM_FLYWAY_USERNAME`
- `PRIZM_FLYWAY_PASSWORD`
- `PRIZM_DB_URL`
- `PRIZM_DB_USERNAME`
- `PRIZM_DB_PASSWORD`

## 프로젝트에서의 의미

PRIZM은 일반 PostgreSQL 환경의 전체 흐름과 PRZ-003의 실제 OpenSQL SQL 호환성을 먼저
검증했다. PRZ-005에서는 전용 DB·역할과 OpenSQL 실행 구조를 준비한 뒤 Spring Boot,
Ollama, API·브라우저 E2E, 두 USER 격리와 자동 OpenSQL 통합 테스트를 실제 OpenSQL
환경에서 확인했다. T-18 전체 회귀와 T-18A 문서 재감사도 통과해 핵심 범위는
`VERIFIED`다.

OpenProxy TCP 연결은 확인했지만 안전한 인증 방식과 SQL routing은 검증하지 못했다.
OpenHA·DB failover도 single-node 범위에서 제외했으므로 완료로 확대하지 않는다. PRIZM은
안전하지 않은 우회 대신 검증된 `5432` 직접 경로를 사용하며, 남은 항목은 별도 Gate로
진행한다.

## 용어 정리

<details>
<summary>기술 용어 설명 보기</summary>

- **OpenSQL**: PostgreSQL 기반의 TmaxTibero DBMS 플랫폼이다. PRZ-005의 실제 지정과제
  데이터베이스 환경이다.
- **PostgreSQL**: PRIZM의 기존 개발과 clean-clone 검증에 사용한 관계형 데이터베이스다.
  OpenSQL 결과와 PostgreSQL 결과는 별도로 기록한다.
- **Patroni**: PostgreSQL/OpenSQL 프로세스와 Leader 상태를 관리하는 도구다. 이 VM에서는
  OpenSQL 프로세스의 시작과 실행 상태를 관리한다. 종료할 때는 Patroni 상태와 공식 종료
  절차를 확인해야 한다.
- **etcd**: Patroni가 멤버와 Leader 상태를 공유하는 분산 키-값 저장소다.
- **OpenProxy**: 클라이언트 연결을 OpenSQL 백엔드로 전달하는 연결 계층이다. 현재 TCP만
  검증했고 SQL routing은 검증하지 못했다.
- **systemd unit**: Linux 서비스의 실행 사용자, 명령과 의존 순서를 정의하는 파일이다.
- **Flyway**: 버전이 붙은 SQL을 순서대로 적용하고 이력을 기록하는 migration 도구다.
- **migration**: 데이터베이스 구조를 한 버전 앞으로 변경하는 SQL 단위다. 적용된 파일은
  수정하지 않고 새 migration을 추가한다.
- **Leader**: Patroni 클러스터에서 쓰기 가능한 주 데이터베이스 멤버다. 현재 single-node
  멤버 한 개가 Leader다.
- **Unix socket**: 같은 Linux 시스템 안에서 프로세스끼리 통신하는 로컬 연결 방식이다.
  C1에서는 네트워크 관리자 비밀번호 대신 승인된 운영체제 관리자 경로로 사용했다.
- **Host-only network**: Windows 호스트와 VM 사이에만 만든 가상 네트워크다. 학교 외부망에
  DB 포트를 공개하지 않고 로컬 통합 검증에 사용한다.
- **DDL**: 데이터베이스, 역할, 스키마와 테이블 구조를 만들거나 바꾸는 SQL이다.
- **DML**: 테이블의 데이터를 조회, 추가, 수정하거나 삭제하는 SQL이다.
- **PUBLIC 권한**: 별도 역할 지정 없이 모든 DB 역할에 기본으로 적용될 수 있는 권한이다.
  PRIZM DB에서는 불필요한 CONNECT와 CREATE를 제거했다.
- **role**: PostgreSQL/OpenSQL에서 로그인과 권한을 묶는 주체다. PRIZM은 migration과
  runtime 역할을 분리한다.
- **vector 확장**: 숫자 벡터 저장과 유사도 검색 기능을 제공하는 PostgreSQL 확장이다.
- **임베딩**: 문서 텍스트의 의미를 검색 가능한 숫자 벡터로 변환한 결과다.
- **E2E**: 사용자 입력부터 최종 결과까지 전체 경로를 실제 환경에서 확인하는
  end-to-end 검증이다.
- **rollback**: 실패한 변경을 시작 전 상태로 되돌리는 작업이다. C1에서는 각 실패 실행이
  새로 만든 DB와 역할만 제거했다.
- **AUTH_BLOCKED**: 네트워크 연결 뒤 안전한 인증 구성을 확인하지 못해 작업을 중단한
  상태다.
- **active**: systemd 서비스가 현재 실행 중인 상태다.
- **disabled**: systemd 서비스가 재부팅 때 자동 시작되지 않는 상태다. 현재 active인지와는
  별개의 설정이다.

</details>
