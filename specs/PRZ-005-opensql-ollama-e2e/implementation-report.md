# PRZ-005 실제 OpenSQL 통합 작업 보고서

이 문서는 [PRZ-005 Spec](spec.md), [실행 계획](plan.md), [작업 목록](tasks.md)에 따라
실제로 수행한 OpenSQL 환경 준비와 애플리케이션 검증 결과를 정리한다. 인프라 검증과
전체 사용자 흐름의 실행 범위는 구분해 기록한다.

## 상태 코드

- `VERIFIED`: 실제 실행 결과로 정상 동작을 확인했다.
- `PARTIALLY_VERIFIED`: 필요한 구간 중 일부만 확인했다.
- `NOT_VERIFIED`: 대상은 준비됐지만 실제 동작을 확인하지 못했다.
- `NOT_RUN`: 계획만 있고 실행하지 않았다.
- `AUTH_BLOCKED`: 네트워크 연결 뒤 인증 단계에서 보안 요구를 만족하지 못해 중단했다.
- `DEFERRED`: 현재 범위에서 제외하고 이후 작업으로 미뤘다.

## 1. 처음 읽는 사람을 위한 현재 상태

- PRIZM은 일반 PostgreSQL 환경에서 로그인부터 업로드와 검색까지 전체 기능 실행을 이미 검증했다.
- 이번 작업은 같은 서비스를 실제 Tmax OpenSQL에 연결하기 위한 준비 작업이다.
- 마지막 확인 결과를 기준으로 Rocky Linux 9.7 VM에서 etcd, Patroni, OpenSQL, OpenProxy가 현재 실행 중이다.
- etcd는 재부팅 시 자동으로 시작되도록 활성화돼 있다.
- Patroni와 OpenProxy는 `active`이지만 `disabled`다. `disabled`는 재부팅 시 자동으로 시작되지 않는다는 뜻이다.
- PRIZM 전용 `prizm` 데이터베이스를 생성했다.
- Flyway용 `prizm_owner`와 애플리케이션용 `prizm_app` 역할을 생성했다.
- OpenSQL 직접 포트 `5432`에서 두 역할의 인증을 확인했다.
- `prizm_app`이 데이터베이스, 역할, 스키마를 만들지 못하도록 최소 권한을 확인했다.
- Flyway V1~V13의 파일·객체·권한 요구사항을 조사하고 두 역할의 `5432` 직접 인증을
  재확인했다.
- 기존 저장소에는 일반 애플리케이션 기능을 시작하지 않고 Flyway만 실행하는 경로가 없어
  Batch D2를 `MIGRATION_EXECUTION_PATH_BLOCKED`로 중단했다.
- D2A에서 Spring Context 없이 Flyway Java API만 사용하는 테스트 전용 경로를 추가하고,
  기본 실행에서 건너뛰는 것을 확인했다. 첫 D2B는 빈 DB에서 migrate 전 `validate()`가
  pending V1~V13을 검증 실패로 판단해 중단됐고 DB 변경은 없었다. 실행 순서를 교정한
  D2B 재검증에서는 V1~V13을 모두 적용하고 두 번째 migrate의 신규 적용이 0개임을 확인했다.
- D3에서 `prizm_app`에 객체별 최소 runtime 권한을 부여했다. 허용 동작과 거부 동작을
  실제로 확인했고, probe 데이터와 객체는 남지 않았다.
- D4에서 `opensql` profile의 Spring Boot를 `prizm_app`으로 OpenSQL `5432`에 연결했다.
  Flyway V13과 JPA schema validation, health `UP`을 확인했으며 실행 전후 데이터는 0건으로
  유지됐다.
- Windows에서 OpenProxy 포트 `6432`까지의 네트워크 연결을 확인했다.
- OpenProxy를 통한 실제 SQL 실행은 인증 문제로 확인하지 못했다.
- 현재 확인된 구성은 OpenProxy가 backend 비밀번호의 평문 저장을 요구해 보안상 적용하지 않았다.
- 변경했던 OpenProxy 설정은 원래 상태로 정확히 복원했다.
- D5에서 Ollama `bge-m3`를 연결하고 demo `USER` 로그인, 합성 TXT/PDF 업로드, 임베딩,
  `ACTIVE` 전환과 원문 근거 검색을 실제 OpenSQL에서 확인했다.
- D6에서 두 demo `USER`의 문서 목록·상세·검색 격리와 DB ownership을 확인했다. 브라우저
  UI에서도 로그인, 문서 상세, PDF 원문, TXT/PDF 업로드·처리 완료·검색과 로그아웃 뒤 보호
  경로 차단을 확인했다.
- T-17에서 실제 `prizm` DB와 분리된 `prizm_integration_test`를 사용해 현재 source의
  OpenSQL opt-in integration test를 실행했다. V1~V13, 객체별 테스트 전용 최소 권한,
  vector 검색과 Worker SQL 검증이 통과했고 테스트 데이터는 0건으로 정리됐다.
- 따라서 PRIZM의 실제 OpenSQL 직접 `5432` API·브라우저·두 사용자 격리는 `VERIFIED`다.
  현재 source의 OpenSQL opt-in integration test와 T-18 backend·frontend·OSS readiness·
  SBOM 회귀 및 최종 감사도 `VERIFIED`다. frontend unit test는 공식 명령이 없어
  `NOT_RUN`이다. PRZ-005의 핵심 완료 범위는 `VERIFIED`다.

## 2. 작업 목적

PRIZM은 이력서, 자기소개서와 같은 커리어 문서의 원본과 버전을 관리한다. 문서에서
추출한 텍스트, 임베딩, 처리 상태와 검색 출처도 데이터베이스에 저장한다. 문서 소유자와
검색 결과의 사용자 경계도 데이터베이스 기록을 기준으로 유지한다.

일반 PostgreSQL·pgvector 환경에서는 안전한 demo `USER` 생성, 로그인, 합성 TXT/PDF
업로드, 비동기 색인, `ACTIVE` 전환과 원문 근거 검색을 두 개의 독립 clean-clone 환경에서
검증했다. 이 결과는 [PRZ-004 Evidence](../PRZ-004-clean-clone-demo/evidence.md)에 기록돼
있다. PostgreSQL 성공을 OpenSQL 성공으로 바꾸어 기록하지 않는다.

실제 OpenSQL에서는 먼저 SQL 호환성 Gate를 통과했다. 그 범위는
[PRZ-003 Evidence](../PRZ-003-opensql-single-node-gate/evidence.md)에 기록돼 있다.
이번 PRZ-005는 실제 애플리케이션과 OpenSQL을 연결하는 별도의 환경 검증이다.

최종 목표 흐름은 다음과 같다.

```text
Spring Boot
→ 실제 OpenSQL
→ Ollama bge-m3
→ TXT/PDF 업로드
→ 텍스트 추출
→ 임베딩 저장
→ 문서 ACTIVE 전환
→ 벡터 검색
→ 원문 위치 확인
```

이 시점에는 OpenSQL 실행 구조, PRIZM 전용 데이터베이스, Flyway V1~V13,
`prizm_app` 최소 runtime 권한과 Spring Boot의 직접 연결까지 검증했고, Ollama와
업로드·검색을 하나로 연결한 실제 OpenSQL E2E는 `NOT_RUN`이었다. 이후 D5와 D6에서
실제 OpenSQL API·브라우저 E2E와 두 사용자 격리를 검증했다.

## 3. 주요 구성요소

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

OpenProxy는 OpenSQL 앞에서 클라이언트 연결을 받아 backend 데이터베이스로 전달하는
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
`prizm_owner`가 V1~V13을 적용하고 애플리케이션 역할과 DDL 권한을 분리한다.
V1~V13과 예상 객체를 조사했고, D2A에서 Spring Context 없이 Flyway만 실행하는 테스트
전용 경로를 추가했다. D2B에서 실제 OpenSQL에 V1~V13을 적용하고 이력을 검증했다.

### Ollama bge-m3

Ollama는 로컬에서 AI 모델을 실행하는 도구다. `bge-m3`는 PRIZM 문서 텍스트를
1024차원 임베딩으로 변환하는 모델이다. 일반 PostgreSQL clean-clone에서 이 흐름을
검증했고, 이 시점에는 실제 OpenSQL 연결이 `NOT_RUN`이었다. 이후 D5에서 Ollama
`0.32.3`과 `bge-m3:latest`의 모델 digest를 확인하고, 1024차원·0이 아닌 임베딩을
실제 OpenSQL에 저장해 TXT/PDF 검색을 검증했다.

## 4. 시작 당시 상태

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
| 제품 코드와 migration | 기준 commit의 코드와 V1~V13 유지 | VM 작업을 위해 제품 구현을 바꿀 이유가 없었다. |

## 5. 단계별 작업 기록

### 1. ORIENT → SPEC → PLAN

#### 목적

기존 PostgreSQL 결과와 실제 OpenSQL 결과를 섞지 않고 작업 범위와 중단 조건을 고정했다.

#### 수행 내용

저장소의 AGENTS 규칙, PRZ-003·PRZ-004 Evidence와 현재 OpenSQL 관련 문서를 확인했다.
PRZ-005 Spec, 계획과 작업 목록을 작성했다.

#### 발생한 문제 또는 발견

OpenSQL SQL Gate 통과만으로 로그인부터 검색까지의 애플리케이션 흐름을 주장할 수 없었다.

#### 대응

인프라 준비, 직접 SQL, 애플리케이션 E2E와 OpenProxy를 서로 다른 Gate로 나눴다.

#### 검증 결과

작업 기준 commit과 변경 금지 범위를 문서에 고정했다.

#### 실제로 남은 변경

계획 당시 제품 코드와 migration 변경은 없었다.

#### 다음 단계와의 관계

VM 건강 상태를 먼저 확인한 뒤에만 서비스와 데이터베이스를 변경하도록 순서를 정했다.

### 2. 초기 VM과 OpenSQL 점검

#### 목적

설치 환경, 네트워크와 실행 중인 구성 요소의 실제 상태를 파악했다.

#### 수행 내용

Rocky Linux 버전, hostname, CPU 구조, Host-only 네트워크, 포트와 프로세스를 읽기 전용으로
확인했다.

#### 발생한 문제 또는 발견

시간 동기화가 꺼져 있었고 journald 오류 기록이 있었다. SSH `22`와 OpenSQL `5432`가
일시적으로 응답하지 않는 상황도 발생했다.

#### 대응

DB 변경을 중단하고 시간, 파일시스템과 서비스 관리 구조 확인을 우선했다.

#### 검증 결과

지원 OS와 고정 VM identity는 유지됐다. OpenSQL 공급 자산은 저장소에 포함하지 않았다.

#### 실제로 남은 변경

이 단계에서는 시스템 설정과 DB를 변경하지 않았다.

#### 다음 단계와의 관계

검증 시각을 신뢰하기 위해 시간 동기화부터 바로잡아야 했다.

### 3. 시간 동기화 문제 확인

#### 목적

로그 시각과 인증 판단의 기준이 되는 시스템 시간을 정상화했다.

#### 수행 내용

chrony 상태, timezone과 시스템 동기화 여부를 점검했다.

#### 발생한 문제 또는 발견

VM 시간이 약 19시간 느렸다. 재부팅 후 Chrony가 약 18.4초를 추가 보정했다. TSC 시간원
불안정과 관련된 finding도 확인했다.

#### 대응

Chrony 동기화가 완료될 때까지 서비스 검증을 진행하지 않았다.

#### 검증 결과

최종 확인 시 `System clock synchronized: yes`였다.

#### 실제로 남은 변경

TSC finding의 장기 영향과 재발 여부는 계속 관찰해야 한다.

#### 다음 단계와의 관계

동기화된 시각을 기준으로 재부팅 이후의 서비스 상태를 다시 확인했다.

### 4. Linux 커널 응답 중단과 VM 재부팅

#### 목적

응답하지 않는 VM을 안전하게 복구하고 원인을 추측하지 않은 채 관찰 사실을 보존했다.

#### 수행 내용

`/home/opensql` 관련 항목을 확인하던 중 VM 응답이 중단됐다. 화면에서 NMI 관련 경고와
`NMI handler took too long` 메시지를 확인했다.

#### 발생한 문제 또는 발견

SSH `22`와 OpenSQL `5432`도 응답하지 않았다. 디렉터리 확인 작업과 커널 중단 사이의
직접적인 인과관계는 확인되지 않았다.

#### 대응

먼저 ACPI 정상 종료를 요청했다. 응답하지 않을 때 Save State를 사용하지 않고 VM을
종료한 뒤 다시 시작했다.

#### 검증 결과

VM이 다시 부팅됐고 Host-only 네트워크가 복구됐다.

#### 실제로 남은 변경

커널 응답 중단의 확정 원인은 남아 있다.

#### 다음 단계와의 관계

서비스를 바로 시작하지 않고 파일시스템, 시간과 이전 부팅 로그를 먼저 확인했다.

### 5. 재부팅 후 파일시스템·시간·journald 점검

#### 목적

강제 종료 뒤 데이터 손상과 로그 신뢰성 문제를 확인했다.

#### 수행 내용

파일시스템 기본 상태, 시간 동기화, systemd-journald 상태와 이전 부팅의 커널 메시지를
점검했다.

#### 발생한 문제 또는 발견

journald watchdog, core dump와 corrupted journal 관련 메시지가 있었다. 영구 journal은
구성돼 있지 않았다.

#### 대응

명백한 파일시스템 손상이 없는지 확인했다. 영구 journal 적용은 이번 핵심 연결 작업과
분리해 `DEFERRED`로 남겼다.

#### 검증 결과

부팅과 기본 파일 접근은 가능했다. 오프라인 전체 파일시스템 검사는 실행하지 않았으므로
파일시스템 상태는 `PARTIALLY_VERIFIED`다.

#### 실제로 남은 변경

영구 journal 적용 여부와 journald 재발 관찰이 남았다.

#### 다음 단계와의 관계

서비스를 임의로 실행하지 않고 공급 설치 구조에서 공식 기동 방법을 조사했다.

### 6. OpenSQL 공식 기동 구조 조사

#### 목적

PostgreSQL/OpenSQL을 직접 실행하지 않고 공급 구조에 맞는 시작과 종료 순서를 확인했다.

#### 수행 내용

설치 문서, 공급 스크립트, 제한된 설정 경로와 기존 etcd unit을 확인했다.

#### 발생한 문제 또는 발견

etcd unit은 있었지만 Patroni와 OpenProxy를 관리할 systemd unit은 없었다.

#### 대응

기동 순서를 `etcd → Patroni/OpenSQL → OpenProxy`로 정했다. OpenSQL은 Patroni를 통해서만
기동하도록 결정했다.

#### 검증 결과

Patroni가 OpenSQL 프로세스를 관리하고 OpenProxy는 정상화된 database 뒤에 시작해야 함을
확인했다.

#### 실제로 남은 변경

Patroni와 OpenProxy systemd unit이 필요했다.

#### 다음 단계와의 관계

실제 등록 전에 unit 초안을 만들고 정적 검증했다.

### 7. Batch A1 — Patroni·OpenProxy systemd unit 초안 작성

#### 목적

서비스 시작 순서, 실행 계정과 종료 방식을 실제 등록 전에 검증했다.

#### 수행 내용

`opensql` 사용자와 그룹, 작업 디렉터리, 실행 명령과 의존 관계를 반영한 두 unit 초안을
로컬 Git 제외 경로에 작성했다.

#### 발생한 문제 또는 발견

처음 검토한 설치 자동화는 현재 목적보다 복잡했다.

#### 대응

자동 rollback 프레임워크와 불필요한 파싱을 제외하고 unit 등록에 필요한 최소 구성만
남겼다.

#### 검증 결과

두 unit 초안은 `systemd-analyze verify`를 통과했다.

#### 실제로 남은 변경

아직 `/etc/systemd/system`에는 등록하지 않은 상태였다.

#### 다음 단계와의 관계

검증된 파일만 Batch A2에서 등록했다.

### 8. Batch A2 — systemd unit 등록

#### 목적

재현 가능한 서비스 관리 지점을 만들되 자동 부팅은 활성화하지 않았다.

#### 수행 내용

`patroni.service`와 `openproxy.service`를 root 소유 `0644`로 등록했다. Patroni가 요구하는
runtime 환경 파일을 `opensql` 전용 `0600`으로 만들었다. `patroni.yml`과
`openproxy.toml`도 `opensql:opensql 0600`으로 제한했다.

#### 발생한 문제 또는 발견

민감한 runtime 값은 화면과 Git에 노출할 수 없었다.

#### 대응

공급 설치 자료의 기존 값을 화면에 출력하지 않고 권한이 제한된 파일로 옮겼다.

#### 검증 결과

daemon reload와 정적 검증을 통과했다. 등록 직후 두 서비스는 `disabled`, `inactive`였다.

#### 실제로 남은 변경

unit, runtime 환경 파일과 제한된 설정 파일 권한이 VM에 남았다.

#### 다음 단계와의 관계

Gate별 확인을 거쳐 수동으로 서비스를 시작할 준비가 됐다.

### 9. Batch B — etcd·Patroni·OpenSQL·OpenProxy 기동

#### 목적

공식 순서대로 single-node 서비스를 기동하고 각 단계의 건강 상태를 확인했다.

#### 수행 내용

etcd health를 먼저 확인했다. 그 뒤 Patroni를 시작하고 Leader와 OpenSQL 상태를 확인했다.
마지막으로 OpenProxy를 시작했다.

#### 발생한 문제 또는 발견

서비스의 `active`와 `disabled`가 서로 다른 의미라는 점을 문서에서 구분해야 했다.

#### 대응

`active`는 현재 실행 상태로 기록했다. `disabled`는 재부팅 시 자동 시작되지 않는 상태로
기록했다.

#### 검증 결과

단일 Patroni 멤버가 `Leader`, `running`이었다. `5432`, Patroni API `8008`과 `6432`가
LISTEN 상태였다. Patroni와 OpenProxy의 반복 재시작은 없었다.

#### 실제로 남은 변경

etcd는 현재 실행 중이며 재부팅 자동 시작이 활성화돼 있다. Patroni와 OpenProxy도 현재
실행 중이지만 재부팅 자동 시작은 활성화하지 않았다.

#### 다음 단계와의 관계

VM 내부 상태가 정상화된 뒤 Windows 네트워크 연결을 확인했다.

### 10. Batch B2 — Windows 6432 연결과 방화벽 설정

#### 목적

Windows 호스트만 OpenProxy에 접근할 수 있는 최소 네트워크 경계를 만들었다.

#### 수행 내용

Host-only 인터페이스의 기본 차단 정책을 유지했다. Windows Host-only 주소 한 개에서 오는
`6432/tcp`만 runtime 규칙으로 허용한 뒤 연결을 확인했다. 성공 후 같은 규칙을 permanent에
적용했다.

#### 발생한 문제 또는 발견

변경 전에는 `6432`가 방화벽에서 차단됐다.

#### 대응

전체 서브넷이나 모든 출발지에 포트를 열지 않았다. 기존 `22`와 `5432` 규칙도 변경하지
않았다.

#### 검증 결과

Windows에서 VM의 `6432`까지 TCP 연결이 성공했다. 이 결과는 SQL routing 증거가 아니다.

#### 실제로 남은 변경

Windows Host-only 주소 한 개에만 적용되는 `6432/tcp` runtime·permanent 규칙이 남았다.

#### 다음 단계와의 관계

애플리케이션용 DB와 역할을 만든 뒤 실제 인증 경로를 분리해 검증했다.

### 11. Batch C1 — prizm DB와 최소 권한 역할 생성

#### 목적

관리자 계정을 애플리케이션에서 사용하지 않고 Flyway와 runtime 권한을 분리했다.

#### 수행 내용

Unix 로컬 소켓의 관리자 경로를 사용해 `prizm_owner`, `prizm_app`과 `prizm` DB를 만들었다.
`PUBLIC`의 불필요한 CONNECT와 스키마 CREATE 권한을 제거했다. 관리자 경로로 vector
`0.8.1` 확장을 만들었다.

#### 발생한 문제 또는 발견

postgres 네트워크 비밀번호를 알 수 없었다. 검증 절차가 가정한 권한 확인 방식과 실제
OpenSQL 동작 사이에 차이가 있었다. 중간 검증 과정에서 세 번 rollback이 발생했다.

#### 대응

postgres 비밀번호를 추측하거나 초기화하지 않았다. Unix 로컬 소켓의 운영체제 관리자
경로를 사용했다. 실패한 각 실행에서는 그 실행이 새로 만든 DB와 역할만 제거했다.

#### 검증 결과

최종 실행은 성공했다. 두 역할은 `5432`에서 직접 인증됐다. `prizm_app`은 DB, 역할과
스키마를 만들 수 없었다. probe 객체는 남지 않았다.

#### 실제로 남은 변경

`prizm` DB, 두 login 역할과 vector 확장이 남았다. 기존 OpenSQL 데이터와 서비스 설정은
rollback의 영향을 받지 않았다.

#### 다음 단계와의 관계

`prizm_app`을 OpenProxy에만 제한해 SQL routing을 확인하려 했다.

### 12. Batch C2 — OpenProxy 인증 검증과 설정 복원

#### 목적

관리자 역할을 노출하지 않고 `prizm_app → prizm` 경로만 OpenProxy에서 검증하려 했다.

#### 수행 내용

OpenProxy 1.1.3의 공식 도움말과 현재 설정 구조를 확인했다. SCRAM verifier,
`server_password`와 인증 유형을 제한적으로 검토하고 정적 검증 뒤 OpenProxy만
재시작했다.

#### 발생한 문제 또는 발견

현재 확인된 구성에서는 backend 접속을 위해 평문 비밀번호를 `openproxy.toml`에 넣어야
했다. 안전한 `query_auth`, 환경변수 또는 별도 secret 파일 구성은 확인하지 못했다. SQL
접속은 인증 단계에서 중단됐다.

#### 대응

평문 비밀번호 저장을 승인하지 않았다. 공급사 답변 전까지 인증을 `AUTH_BLOCKED`로
판정했다. 변경 전 백업으로 설정을 정확히 복원하고 OpenProxy만 정상 재시작했다.

#### 검증 결과

복원 후 OpenProxy는 `active`, 재시작 횟수 0, `6432` LISTEN 상태였다. Windows TCP 연결도
유지됐다. 설정 파일은 `opensql:opensql 0600`이었다. 백업본과 복원본의 SHA-256은
`ad78d290d745b3f8b69692c87e7390a787b7d5c01602d3fdb5411096c23c0873`으로 일치했다.

#### 실제로 남은 변경

OpenProxy 인증 변경은 남지 않았다. PRIZM DB와 역할은 그대로 유지했다.

#### 다음 단계와의 관계

OpenProxy를 핵심 E2E의 선행 조건에서 제외하고 `5432` 직접 연결로 진행하기로 했다.

### 13. OpenSQL 5432 직접 연결 정책 결정

#### 목적

검증되지 않은 인증 우회를 만들지 않고 다음 애플리케이션 작업의 안전한 경로를 정했다.

#### 수행 내용

Flyway는 `prizm_owner`, Spring Boot runtime은 `prizm_app`으로 OpenSQL `5432`에 직접
연결하는 정책을 확정했다.

#### 발생한 문제 또는 발견

OpenProxy SQL routing은 아직 확인할 수 없지만 실제 OpenSQL 직접 인증은 이미 가능했다.

#### 대응

OpenProxy 공급사 문의와 핵심 E2E를 분리했다. 관리자 역할은 OpenProxy에 노출하지 않는다.

#### 검증 결과

이 단계에서는 직접 연결 정책만 확정했으며 Flyway와 Spring Boot는 `NOT_RUN`이었다.

#### 실제로 남은 변경

이 정책 결정으로 제품 코드, migration과 dependency는 변경하지 않았다.

#### 다음 단계와의 관계

다음 Gate는 V1~V13 객체와 권한을 조사한 뒤 Flyway를 실제 실행하는 것이다.

### 14. Batch D1·D2·D2A — migration 조사와 실행 경로 Gate

#### 목적

V1~V13이 만들 객체와 `prizm_app`에 필요한 권한을 먼저 확인한 뒤, 일반 Spring Boot 기능을
시작하지 않고 Flyway만 실행할 수 있는지 판단했다.

#### 수행 내용

V1~V13의 연속성, 예상 테이블·시퀀스와 객체 소유권을 읽기 전용으로 조사했다. Patroni
Leader와 OpenSQL 상태, 빈 migration 대상, vector `0.8.1`, 두 역할의 `5432` 직접 인증도
재확인했다. 별도 Flyway task·CLI, Spring Boot 실행 설정과 기존 OpenSQL 통합 테스트의
범위를 비교했다.

#### 발생한 문제 또는 발견

V1~V13에는 누락이 없고 migration 후 예상되는 도메인 테이블과 BIGSERIAL 시퀀스는 각각
6개다. 그러나 저장소에는 Flyway만 실행하는 별도 task나 CLI가 없다. 일반 `bootRun`은 웹
보안과 JPA 등 애플리케이션 구성을 함께 시작하며, 기존 OpenSQL 통합 테스트는 migration
외의 SQL·DML 검증까지 수행한다.

첫 D2B에서는 빈 DB의 V1~V13이 모두 pending인 상태에서 migrate 전 `validate()`가
`FlywayValidateException`을 반환했다. `migrate()`는 호출되지 않았고, 읽기 전용 확인에서
Flyway 이력·도메인 테이블·BIGSERIAL 시퀀스가 모두 0개임을 확인했다. 별도 public 객체
목록 조회는 OpenSQL의 내부 `char` 타입인 `relkind`를 text와 바로 연결해 실패했다. 이후
조회에서는 `relkind::text`로 명시적으로 변환해야 한다.

#### 대응

승인 범위를 벗어난 실행 설정, 임시 task나 테스트를 만들지 않았다. Batch D2의 중단
조건에 따라 `MIGRATION_EXECUTION_PATH_BLOCKED`로 판정했다. 이후 승인된 D2A에서
`OpenSqlFlywayMigrationOnlyTest` 하나를 추가했다. 이 테스트는 Spring Context를 시작하지
않고 Flyway Java API를 직접 사용하며, 명시적 승인 환경변수가 없으면 건너뛴다. 첫 D2B
실패 후에는 migrate 전 pending 버전 `1`~`13`, applied 0과 current 없음부터 확인하고,
migrate 성공 뒤에만 `validate()`를 실행하도록 순서를 교정했다.

#### 검증 결과

첫 D2B 실패 시점에는 `flyway_schema_history`, 6개 도메인 테이블과 6개 BIGSERIAL 시퀀스가
모두 없었다. 교정된 D2A 테스트 소스 컴파일은 통과했고, 승인 환경변수 없는 단독 실행은
1개 테스트가 `SKIPPED`됐다.

교정 후 D2B 재실행은 성공했다. V1~V13이 정확히 13개 적용됐고 현재 버전은 V13, pending과
실패 이력은 0개였다. 두 번째 migrate의 신규 적용도 0개였다. 생성된 6개 도메인 테이블,
6개 BIGSERIAL 시퀀스와 `flyway_schema_history`의 소유자는 모두 `prizm_owner`였다. 모든
도메인 테이블은 0건이었고, vector는 `0.8.1`, 소유자는 `postgres`로 유지됐다.

#### 다음 단계와의 관계

D2B가 통과해 migration 객체가 준비됐다. 이어서 D3에서 `prizm_app`의 객체별 최소 runtime
권한을 적용하고 검증했다.

### 15. Batch D3 — prizm_app 최소 runtime 권한

#### 수행 내용

`prizm_app`에 `prizm` DB의 CONNECT와 `public` schema의 USAGE를 부여했다. 테이블은 실제
runtime 동작에 필요한 권한만 객체별로 부여했다.

| 테이블 | 부여한 권한 |
|---|---|
| `users` | SELECT, INSERT |
| `documents` | SELECT, INSERT, UPDATE, DELETE |
| `document_versions` | SELECT, INSERT, UPDATE, DELETE |
| `document_chunks` | SELECT, INSERT, DELETE |
| `processing_jobs` | SELECT, INSERT, UPDATE, DELETE |
| `file_cleanup_jobs` | SELECT, INSERT, UPDATE |

6개 BIGSERIAL 시퀀스에는 USAGE만 부여했다. SELECT와 UPDATE는 부여하지 않았다.
`flyway_schema_history`에는 `prizm_app`과 PUBLIC 권한을 부여하지 않았다. ALTER DEFAULT
PRIVILEGES도 사용하지 않았다.

#### 검증 결과

GRANT와 사후 검증은 하나의 트랜잭션에서 수행하고 모든 조건이 일치한 뒤 COMMIT했다.
합성 데이터로 허용된 SELECT·INSERT·UPDATE·DELETE를 확인했다. CREATE TABLE·SCHEMA·DB·ROLE,
금지된 테이블 변경, `flyway_schema_history` 접근과 TRUNCATE는 모두 거부됐다. 검증 트랜잭션은
ROLLBACK했고 probe 데이터·테이블·스키마·DB·역할은 모두 0건이었다.

`prizm_app`과 PUBLIC에는 TEMPORARY 권한이 없었다. 이 결과는 확인만 했으며 권한을
변경하지 않았다.

#### 다음 단계와의 관계

다음 단계인 D4에서 `prizm_app`으로 Spring Boot를 OpenSQL `5432`에 연결하고 JPA schema
validation과 health를 확인했다.

### 16. Batch D4 — Spring Boot 직접 연결과 JPA validation

#### 수행 내용

`opensql` profile을 사용해 Flyway는 `prizm_owner`, runtime datasource는 `prizm_app`으로
OpenSQL `5432`의 `prizm` DB에 직접 연결했다. demo·관리자 bootstrap, indexing Worker와
cleanup Worker는 기존 설정으로 비활성화했다. JPA의 `ddl-auto`는 `validate`를 유지했다.

첫 실행 도구는 Windows PowerShell 환경에서 지원하지 않는 난수 생성 API 때문에
애플리케이션을 시작하기 전에 중단됐다. 비밀 환경변수는 제거됐고 DB 변경은 없었다.
호환되는 난수 생성 API로 실행 도구만 교정한 뒤 같은 조건으로 다시 시작했다.

#### 검증 결과

Spring ApplicationContext와 JPA EntityManagerFactory가 초기화됐고 Tomcat은 로컬 포트에서
시작됐다. health endpoint는 HTTP 200과 `UP`을 반환했다. 실행 중 OpenSQL에서
`prizm_app` 세션 1개를 확인했다.

Flyway 이력은 V1~V13 13개, 현재 V13, pending·실패 0개로 유지됐고 새 migration은 적용되지
않았다. 6개 테이블과 6개 시퀀스도 그대로 유지됐다. 관찰한 시작 로그에는
CREATE·ALTER·DROP이 없었고 JPA schema validation이 통과했다. bootstrap과 Worker를
비활성화한 상태에서 사용자, 문서, chunk와 두 작업 테이블은 모두 0건이었다.

검증 후 Spring Boot를 종료했다. 로컬 포트와 애플리케이션 프로세스가 사라졌고 임시
환경변수 제거를 확인했다. 종료 후 `prizm_app` 세션은 0개였다. Patroni와 OpenProxy는
계속 `active`였고 재시작 횟수는 모두 0이었다. Flyway 이력, 스키마와 데이터도 변하지 않았다.

#### 다음 단계와의 관계

D4 시점의 Spring Boot OpenSQL 직접 연결과 JPA validation은 `VERIFIED`였다. Ollama `bge-m3`,
로그인, TXT/PDF 업로드, 임베딩과 검색을 연결한 실제 OpenSQL E2E는 당시 `NOT_RUN`이었다.

### 17. Batch D5 — Ollama와 TXT/PDF 핵심 API 흐름

#### 실행 환경

OpenSQL `5432` 직접 연결 정책을 유지했다. Flyway는 `prizm_owner`, runtime datasource는
`prizm_app`을 사용했다. demo `USER` bootstrap은 한 번만 활성화한 뒤 끄고 정상 모드로
재시작했다. indexing Worker는 활성화하고 cleanup Worker와 관리자 bootstrap은 비활성화했다.

호스트 Ollama `0.32.3`과 `bge-m3:latest`를 사용했다. 확인한 모델 digest는
`7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`이며, 합성 문장의
임베딩이 1024차원이고 유한한 0이 아닌 벡터임을 먼저 확인했다. 모델을 새로 받거나
갱신하지 않았다. `latest`는 가변 태그이므로 이 결과를 항상 동일한 바이너리의 재현으로
표현하지 않는다.

#### 핵심 API E2E 결과

기존 clean-clone 검증기를 재사용해 demo `USER` 로그인과 JWT 발급을 확인했다. 합성 TXT와
PDF를 각각 한 개 업로드했고 두 문서 버전 모두 제한 시간 안에 `ACTIVE`가 됐다. 두
processing job의 최종 상태는 `COMPLETED`였다.

TXT 검색은 `TEXT_CHUNK`와 source index 1, PDF 검색은 `PAGE`와 source index 1을 반환했다.
각 결과에서 합성 fixture의 고유 문장을 확인했다. 로그아웃 상태의 보호 API 요청은
`401`로 거부됐다. 비밀번호와 JWT는 결과에 출력하거나 파일에 저장하지 않았다.

읽기 전용 DB 검증에서는 사용자 1명, 문서 2개, 버전 2개, chunk 2개와 processing job
2개를 확인했다. 두 embedding은 모두 1024차원이고 0이 아닌 값이었다. 문서·버전·chunk·job의
owner 불일치는 0건이었고, 두 문서의 active version 연결도 일치했다. Flyway는 V13 13개,
실패 0개였으며 테이블·시퀀스·이력 테이블의 소유자는 `prizm_owner`로 유지됐다.

#### 사용자 격리와 종료

기존 demo bootstrap과 owner-scoped API만 사용하면 두 번째 USER 검증이 가능함을 source와
통합 테스트에서 확인했다. 실제 실행에서는 두 번째 demo 비밀번호 확인이 일치하지 않아
애플리케이션 시작 전에 안전하게 중단했다. 두 번째 사용자는 생성되지 않았으므로 실제
두 USER 격리는 `NOT_RUN`으로 유지한다. 브라우저 UI도 이번 Batch에서 실행하지 않았다.

Spring Boot 종료 후 애플리케이션 포트와 `prizm_app` 세션은 0개였다. Patroni와 OpenProxy는
계속 `active`였고 재시작 횟수는 0이었다. 사용자 1명과 두 문서의 증거 데이터, Flyway V13,
스키마 소유권은 그대로 유지됐다.

### 18. Batch D6 — 두 사용자 격리와 브라우저 UI

#### 두 사용자 격리

D5의 기존 demo `USER`를 USER_A로 유지하고 합성 USER_B를 기본 비활성화 bootstrap으로 한
번만 생성한 뒤 bootstrap을 다시 껐다. 두 사용자는 각자의 JWT로 로그인했다. USER_A는 기존
문서 2개, USER_B는 새 합성 TXT 문서 1개를 조회했다.

문서 목록에서 상대 사용자 문서가 보이지 않았고, 상대 문서 ID의 상세 조회도 차단됐다.
각 사용자의 검색 결과에는 자신의 문서만 포함됐다. DB에서는 사용자별 문서 수가 USER_A 2개,
USER_B 1개였고 문서·버전·chunk·processing job의 owner 불일치는 0건이었다.

#### 브라우저 UI

React UI에서 USER_A 로그인을 확인한 뒤 기존 TXT/PDF의 문서 목록과 처리 완료 상태를 확인했다.
PDF 상세 화면에서 `ACTIVE` 버전과 원문 PDF 뷰어를 열었다. 경력 근거 검색에서는 TXT의
`TEXT_CHUNK` 1과 고유 문장, PDF의 `PAGE` 1과 고유 문장을 각각 확인했다.

UI에서 같은 first-party 합성 TXT/PDF를 문서 2개로 추가 등록했다. 두 문서는 모두 처리 완료가
됐고 검색 결과에 새 문서 제목, TXT 원문 구간과 PDF 1페이지 근거가 표시됐다. 브라우저 콘솔의
warning·error는 0건이었다. 로그아웃 후 보호 경로를 직접 열었을 때 로그인 화면으로 돌아갔다.

첫 UI 로그인 시 사용자가 백엔드 실행 창을 종료해 Vite proxy 연결이 거부됐다. 제품 코드나
설정을 변경하지 않고 같은 승인 설정으로 백엔드를 다시 시작한 뒤 위 흐름을 통과했다.

#### 종료 후 상태

백엔드와 프런트엔드를 정상 종료했고 포트 `18080`과 `5173`은 해제됐다. `prizm_app` 세션은
0개였다. Patroni와 OpenProxy는 `active`, 재시작 횟수 0을 유지했다. 최종 DB에는 사용자 2명,
문서·버전·chunk·processing job 각 5개가 남았다. 5개 문서는 모두 `ACTIVE`, 5개 job은 모두
`COMPLETED`였고 1024차원·0이 아닌 embedding도 5개였다. owner 불일치와 잘못된 관계 객체
소유자는 각각 0건이었다. Flyway는 V13 13개, 실패 0을 유지했다.

D6 종료 시점의 문서 현행화 뒤 OSS readiness 검사는 Markdown 48개와 로컬 링크 476개,
tracked-file 안전성, 라이선스와 SBOM 회귀 검사를 통과했다. SBOM 구조 검사도 별도로
통과했다. 이 시점에는 전체 backend·frontend 회귀와 최종 독립 감사가 `NOT_RUN`이었다.
이후 T-18에서 해당 회귀와 감사를 완료했다.

## 6. 주요 문제와 해결 과정

| 문제 | 증상 | 확인한 원인 또는 판단 | 대응 | 결과 | 남은 한계 |
|---|---|---|---|---|---|
| VM 시간이 약 19시간 느림 | 시스템 시각과 실제 시각이 크게 달랐음 | 시간 동기화가 완료되지 않았음 | Chrony 상태를 정상화한 뒤 후속 검증 수행 | 최종 동기화 `VERIFIED` | 재부팅 뒤 시각을 계속 확인해야 함 |
| 재부팅 후 추가 시간 보정 | Chrony가 약 18.4초를 보정 | 재부팅 직후 남은 오차를 동기화함 | 동기화 완료를 기다림 | `System clock synchronized: yes` | 장기 재발 여부 미확인 |
| TSC 시간원 불안정 finding | 시간원 관련 커널 finding 관찰 | 영향 범위는 확정되지 않음 | 원인을 단정하지 않고 기록 | 서비스 검증은 가능했음 | 재발 관찰 필요 |
| systemd-journald watchdog 오류 | watchdog, core dump와 corrupted journal 메시지 | 직접 원인은 확정되지 않음 | 재부팅 후 journald와 이전 부팅 기록 점검 | 현재 부팅은 가능 | 영구 journal과 재발 관찰 `DEFERRED` |
| `NMI handler took too long` | VM 화면에 NMI 관련 경고 표시 | `/home/opensql` 확인 중 발생했으나 직접 원인은 모름 | VM을 정상 종료 요청 후 필요 시 강제 종료·재부팅 | VM 복구 | 커널 중단 원인 `NOT_VERIFIED` |
| SSH `22`와 OpenSQL `5432` 응답 불가 | Windows에서 두 포트에 연결할 수 없었음 | VM 전체 응답 중단과 함께 발생 | 재부팅 후 네트워크와 서비스 상태를 순서대로 점검 | 두 포트 복구 | 물리 호스트 장애 대응은 범위 밖 |
| 파일시스템 손상 우려 | 비정상 종료 뒤 데이터 손상 가능성 | 명백한 손상은 발견하지 못함 | 기본 상태와 파일 접근 확인 | `PARTIALLY_VERIFIED` | 오프라인 전체 검사는 `NOT_RUN` |
| 영구 journal 부재 | 이전 부팅의 로그 보존 범위가 제한됨 | persistent journal이 구성되지 않음 | 핵심 DB 연결과 분리해 기록 | 서비스 기동에는 영향 없음 | 적용 여부 `DEFERRED` |
| Patroni·OpenProxy systemd unit 부재 | 재부팅 뒤 표준 서비스 관리 지점이 없음 | 공급 설치 결과에 두 unit이 없었음 | 최소 unit을 작성·검증·등록 | unit 등록 `VERIFIED` | 자동 시작은 의도적으로 disabled |
| Windows에서 `6432` 차단 | OpenProxy는 LISTEN 중이나 Windows TCP 실패 | Host-only 방화벽에 `6432` 허용 규칙이 없었음 | Windows 주소 한 개에만 runtime·permanent 허용 | 연결 `VERIFIED` | SQL routing과 별개 |
| postgres DB 비밀번호를 알 수 없음 | 네트워크 관리자 인증 실패 | 현재 비밀번호를 확인할 수 없었음 | 추측·초기화 없이 Unix 로컬 소켓 관리자 경로 사용 | C1 완료 | 네트워크 관리자 인증은 사용하지 않음 |
| Batch C1 중간 검증 실패 | 역할 생성 후 권한과 결과를 검증하는 단계에서 실패 | OpenSQL의 실제 동작과 일부 검증 가정이 달랐음 | 매 시도에서 새 객체만 rollback한 뒤 검증식 교정 | 세 번 rollback 후 최종 성공 | 기존 데이터·설정 영향 없음 |
| OpenProxy의 평문 비밀번호 요구 | `prizm_app` SQL 인증 실패 | 현재 확인된 1.1.3 구성에서 안전한 외부 secret 주입을 확인하지 못함 | 평문 저장 거부, 설정 복원, 공급사 문의로 전환 | `AUTH_BLOCKED` | 공식 안전 구성 확인 필요 |
| OpenProxy SQL 인증 차단 | TCP는 성공하지만 SQL이 backend로 전달되지 않음 | 인증 계층을 안전하게 구성하지 못함 | routing 완료를 주장하지 않음 | SQL routing `NOT_VERIFIED` | 공급사 답변 전까지 `DEFERRED` |

## 7. 주요 기술 결정

- PostgreSQL/OpenSQL 프로세스는 Patroni를 통해 실행한다. 공급 관리 구조를 우회한 직접
  실행은 상태 판단과 정상 종료를 어렵게 만들 수 있기 때문이다.
- postgres 관리자 역할은 PRIZM 애플리케이션에서 사용하지 않는다. 관리자 권한이 사용자
  요청 처리 경로에 노출되면 최소 권한 원칙을 지킬 수 없기 때문이다.
- Flyway와 애플리케이션 실행 역할을 분리한다. DDL을 수행하는 migration과 일상적인 DML을
  같은 권한으로 실행하지 않기 위해서다.
- `prizm_owner`는 `prizm` DB, V1~V13이 생성한 객체와 `flyway_schema_history`를 소유한다.
- `prizm_app`은 애플리케이션 실행 전용 최소 권한 역할이다. 승인한 객체별 DML만 허용하고,
  DB·역할·스키마 생성과 Flyway 이력 접근은 거부되는 것을 확인했다.
- `PUBLIC`의 불필요한 database CONNECT와 schema CREATE 권한을 제거했다. 명시적으로
  승인된 역할만 PRIZM DB에 접근하게 하기 위해서다.
- vector 확장은 승인된 Unix 로컬 소켓 관리자 경로로 생성했다. `prizm_owner`를
  superuser로 올리지 않기 위해서다.
- OpenProxy에 postgres와 `prizm_owner`를 노출하지 않는다. 인증 실험은 `prizm_app`만
  대상으로 제한했다.
- OpenProxy 설정에 backend 평문 비밀번호를 저장하는 방식을 승인하지 않았다. 연결
  편의보다 비밀정보 경계를 우선했다.
- 핵심 E2E는 우선 OpenSQL `5432` 직접 연결로 진행한다. 이 경로는 두 전용 역할의 실제
  인증이 확인됐다.
- OpenProxy SQL routing은 공급사의 안전한 인증 구성 답변 이후로 미룬다. TCP 연결 성공을
  SQL 성공으로 확대하지 않는다.
- 단일 VM에서는 다중 노드 장애 전환을 검증할 수 없다. 현재 결과는 single-node 서비스
  기동과 Leader 상태로 제한한다.
- etcd는 현재 `active`이며 자동 시작이 활성화돼 있다.
- Patroni와 OpenProxy는 현재 `active`이지만 `disabled`다. `active`는 지금 실행 중이라는
  뜻이다. `disabled`는 재부팅 시 자동으로 시작되지 않는다는 뜻이며 현재 실행 상태와는
  별개다.

## 8. 현재 검증 상태

| 항목 | 상태 | 확인 결과 | 남은 작업 |
|---|---|---|---|
| 파일시스템 | `PARTIALLY_VERIFIED` | 재부팅 뒤 기본 접근과 서비스 데이터 사용 가능, 명백한 손상 미발견 | 필요 시 유지보수 창에서 오프라인 검사 |
| 시간 동기화 | `VERIFIED` | Chrony 보정 후 시스템 동기화 확인 | 재부팅 때 재확인 |
| 영구 journal | `DEFERRED` | persistent journal 미구성 확인 | 적용 필요성과 보존 정책 결정 |
| etcd | `VERIFIED` | 서비스 active, 자동 시작 활성화, Patroni보다 먼저 정상 상태 확인 | 운영 재부팅 절차 문서화 |
| Patroni | `VERIFIED` | 서비스 active·disabled, 반복 재시작 없음 | 자동 시작 여부 결정 |
| OpenSQL Leader | `VERIFIED` | 단일 멤버 Leader·running 확인 | 운영 재부팅 절차 정리 |
| OpenSQL `5432` | `VERIFIED` | 두 역할의 직접 인증, Spring Boot runtime과 핵심 API E2E 연결 성공 | 운영 재현 절차 정리 |
| Patroni API `8008` | `VERIFIED` | LISTEN과 Leader health 확인 | 운영 관찰 범위 결정 |
| OpenProxy | `PARTIALLY_VERIFIED` | 복원 후 active·disabled, 재시작 횟수 0, `6432` LISTEN | 안전한 인증 방식과 SQL routing 확인 |
| Windows → OpenProxy `6432` | `VERIFIED` | Host-only TCP 연결 성공 | SQL 인증과 routing 별도 검증 |
| OpenProxy SQL routing | `NOT_VERIFIED` | 실제 읽기 전용 SQL이 반환되지 않음 | 공급사 답변 뒤 재검증 |
| OpenProxy 인증 | `AUTH_BLOCKED` | 평문 backend 비밀번호 없이 인증 구성 불가 | 안전한 공식 secret 주입 방식 확인 |
| `prizm` DB | `VERIFIED` | owner가 `prizm_owner`인 전용 DB, V1~V13과 핵심 API 흐름 확인 | 증거 데이터 보존 |
| `prizm_owner` | `VERIFIED` | Flyway 이력·테이블·시퀀스 소유와 직접 인증 확인 | migration 전용으로 유지 |
| `prizm_app` | `VERIFIED` | 직접 인증, 최소 runtime 권한, Spring Boot 세션과 핵심 API E2E 확인 | 최소 권한 유지 |
| 최소 권한 분리 | `VERIFIED` | 허용 DML과 금지 DDL·DML, Flyway 이력 보호와 핵심 흐름 확인 | 권한 회귀 방지 |
| vector `0.8.1` | `VERIFIED` | 소유자 `postgres`, 1024차원 embedding 5개 저장·검색 확인 | 모델·차원 계약 유지 |
| Flyway | `VERIFIED` | V1~V13 13개 적용, 현재 V13, pending·실패 0, 두 번째 migrate 신규 적용 0 | 전용 실행 경로와 이력 보호 유지 |
| OpenSQL opt-in integration test | `VERIFIED` | 격리 DB에서 테스트 1개 성공, 실패·오류·skip 0; vector 검색·Worker SQL 통과, 데이터 잔여 0건 | 격리 DB와 권한 경계 유지 |
| 전체 회귀·최종 감사 | `VERIFIED` | backend 단위 262개·통합 69개, frontend lint·typecheck·build, OSS readiness·SBOM·문서·민감정보 감사 통과 | frontend unit test는 공식 명령이 없어 `NOT_RUN` |
| Spring Boot | `VERIFIED` | `opensql` profile, Flyway V13, JPA validation, health `UP`과 핵심 API·브라우저 E2E 확인 | 최종 회귀 검증 |
| Ollama `bge-m3` | `VERIFIED` | Ollama `0.32.3`, 모델 digest와 1024차원 embedding 확인 | mutable tag 경계 유지 |
| 사용자 로그인 | `VERIFIED` | demo `USER` 로그인, JWT 발급과 현재 사용자 확인 | 비밀정보 비출력 유지 |
| TXT/PDF 업로드 | `VERIFIED` | D5 API 문서 2개, D6 USER_B TXT 1개와 UI TXT/PDF 2개 등록 확인 | 증거 fixture 보존 |
| 임베딩 저장 | `VERIFIED` | 1024차원·0이 아닌 embedding 5개 확인 | 차원·유효성 회귀 방지 |
| 문서 `ACTIVE` 전환 | `VERIFIED` | 문서 5개와 job 5개가 각각 `ACTIVE`·`COMPLETED` | Worker 복구는 별도 검증 |
| 벡터 검색 | `VERIFIED` | API와 UI에서 TXT/PDF 고유 문장과 출처 검색 확인 | 검색 품질 평가는 별도 범위 |
| 사용자 격리 | `VERIFIED` | 두 USER의 목록·상세·검색 상호 차단과 DB owner 불일치 0건 확인 | 회귀 테스트 유지 |
| 브라우저 UI | `VERIFIED` | 로그인, 문서 상세, PDF 원문, UI TXT/PDF 업로드·검색, 로그아웃 차단 확인 | UI 회귀 검증 유지 |
| 원문 위치 확인 | `VERIFIED` | TXT `TEXT_CHUNK` 1, PDF `PAGE` 1과 고유 문장 확인 | 다중 페이지는 별도 범위 |
| OpenHA 장애 전환 | `DEFERRED` | PRZ-005는 single-node 범위이며 OpenHA를 명시적으로 제외 | 다중 노드와 라이선스 범위 확인 후 별도 검증 |

## 9. 시스템에 실제로 남은 변경

| 변경 | 현재 상태 |
|---|---|
| `patroni.service` | `/etc/systemd/system`에 등록, root 소유 `0644` |
| `openproxy.service` | `/etc/systemd/system`에 등록, root 소유 `0644` |
| `patroni-runtime.env` | Patroni 실행에 필요한 제한된 환경 파일 생성, `opensql:opensql 0600` |
| `patroni.yml` | 내용은 유지하고 접근 권한을 `opensql:opensql 0600`으로 제한 |
| `openproxy.toml` | 내용은 변경 전 상태로 복원하고 `opensql:opensql 0600` 유지 |
| `6432/tcp` 방화벽 | Windows Host-only 주소 한 개에만 runtime·permanent 허용 |
| etcd 실행 상태 | 현재 `active`, 재부팅 자동 시작 활성화 |
| `prizm` 데이터베이스 | owner `prizm_owner`로 생성 |
| `prizm_owner` | Flyway·객체 소유용 제한된 login 역할로 생성 |
| `prizm_app` | 애플리케이션용 제한된 login 역할로 생성 |
| vector 확장 | `prizm` DB에 `0.8.1` 생성, 확장 소유자는 `postgres` |
| Flyway 객체 | V1~V13, 6개 도메인 테이블, 6개 BIGSERIAL 시퀀스와 이력 테이블 생성; 소유자 `prizm_owner` |
| `prizm_app` 권한 | DB CONNECT, schema USAGE와 승인된 객체별 DML·시퀀스 USAGE 부여 |
| Patroni·OpenProxy 실행 상태 | 현재 `active`, 재부팅 자동 시작은 `disabled` |

C2에서 확인한 일치 SHA-256은 설정 내용 자체를 공개하지 않고 복원 무결성을 확인하는
근거다.

제품 소스 코드, 기존 Flyway migration 파일과 dependency는 변경하지 않았다. 비밀번호,
토큰과 실제 runtime 환경변수 값도 저장소에 기록하지 않았다.

## 10. 현재 연결 정책

### Flyway

- `prizm_owner`를 사용한다.
- OpenSQL `5432`에 직접 연결한다.
- `prizm` DB를 대상으로 한다.
- V1~V13 적용과 이력 검증 상태는 `VERIFIED`다.

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

## 11. 최종 검증과 후속 작업

### T-17 격리 실행 결과

`com.prizm.infrastructure.OpenSqlInfrastructureTest`는 `RUN_OPENSQL_TESTS=true`와
`PRIZM_OPENSQL_VERIFICATION_TARGET_CONFIRMED=true`를 함께 요구한다. 테스트는 시작 시
정확한 대상 DB가 `prizm_integration_test`인지, runtime 사용자가 `prizm_app`인지, Flyway
사용자가 `prizm_owner`인지 확인한다. V1~V13 적용 뒤 테이블·시퀀스·소유자·vector 상태가
예상과 모두 일치할 때만 `prizm_app`에 객체별 테스트 전용 최소 권한을 부여한다.

단독 실행 결과는 테스트 1개 성공, 실패·오류·skip 0건이다. 실제 DML·vector 검색·Worker
SQL은 `prizm_app`으로 실행했고, 권한상 허용되지 않는 테스트 데이터 정리만 `prizm_owner`가
담당했다. `flyway_schema_history`의 `prizm_app`·`PUBLIC` 권한은 0건이고, 테스트 데이터도
모두 0건으로 정리됐다. 격리 DB에는 V1~V13 객체와 명시적 최소 권한만 남았다.

실제 `prizm` DB는 사용자 2명, 문서·버전·청크·처리 작업 각 5개, `ACTIVE` 문서와 1024차원
임베딩 각 5개, Flyway V13·이력 13개, 사용자별 문서 `1:4,2:1`과 소유자 불일치 0건으로
실행 전후가 같았다. Patroni와 OpenProxy도 active·재시작 0을 유지했다. `clean`, `repair`,
`baseline`, DB·schema DROP과 실제 `prizm` DB 변경은 수행하지 않았다.

### T-18 전체 회귀와 최종 감사 결과

backend 단위 테스트 262개와 통합 테스트 69개는 실패·오류 없이 통과했다. 기본 회귀에서
실제 OpenSQL opt-in 테스트는 승인 환경변수가 없어 `SKIPPED`됐다. frontend lint,
typecheck와 production build도 통과했다. frontend unit test는 공식 명령이 없어
`NOT_RUN`이다. OSS readiness, SBOM, Markdown 링크, 민감정보와 변경 범위 감사도 통과했다.
T-18A에서 오래된 현재형 표현을 교정하고 상태 문서를 다시 대조했다.

남은 비필수 후속 범위는 다음과 같다.

1. OpenProxy의 안전한 인증 방식과 SQL routing을 공급사 답변 뒤 검증
2. 영구 journal 적용 여부 결정
3. 이후 OpenHA, 변경 로그 동기화, MCP, CareerFact

## 12. 프로젝트에서의 의미

PRIZM은 일반 PostgreSQL 환경에서 전체 서비스 흐름을 이미 검증했다. 실제 OpenSQL에서도
PRZ-003을 통해 핵심 SQL 호환성을 확인했다.

이번 작업으로 OpenSQL의 공식 실행 구조를 파악했다. PRIZM 전용 DB와 역할을 실제 VM에
준비했고, Windows의 Spring Boot가 최소 권한 역할로 OpenSQL에 연결돼 시작되는 것도
검증했다.

이번 D5와 D6에서 Spring Boot, Ollama, 업로드, 임베딩과 검색을 실제 OpenSQL에 연결한 API와
브라우저 E2E를 확인했다. 두 USER의 문서·검색 격리도 확인했다. 다만 OpenProxy SQL routing은
아직 검증하지 않았고 OpenHA·DB failover는 범위에서 제외했으므로 해당 기능까지 완료했다고
확대해서 표현하지 않는다. 현재 source의 OpenSQL opt-in integration test는 격리 DB에서
통과했다. T-18 전체 회귀와 T-18A 문서 상태 재감사도 통과해 PRZ-005의 핵심 완료 범위는
`VERIFIED`다.

OpenProxy 인증 보류는 프로젝트 실패가 아니다. 필요한 비밀정보 보호 수준과 현재 확인한
OpenProxy 기능 사이의 제약을 발견한 결과다. PRIZM은 안전하지 않은 우회 설정을 추가하는
대신 검증된 `5432` 직접 경로로 핵심 E2E를 계속 진행한다.

## 13. 용어 정리

- **OpenSQL**: PostgreSQL 기반의 TmaxTibero DBMS 플랫폼이다. PRZ-005의 실제 지정과제
  데이터베이스 환경이다.
- **PostgreSQL**: PRIZM의 기존 개발과 clean-clone 검증에 사용한 관계형 데이터베이스다.
  OpenSQL 결과와 PostgreSQL 결과는 별도로 기록한다.
- **Patroni**: PostgreSQL/OpenSQL 프로세스와 Leader 상태를 관리하는 도구다. 이 VM에서는
  OpenSQL 프로세스의 시작과 실행 상태를 관리한다. 종료할 때는 Patroni 상태와 공식 종료
  절차를 확인해야 한다.
- **etcd**: Patroni가 멤버와 Leader 상태를 공유하는 분산 키-값 저장소다.
- **OpenProxy**: 클라이언트 연결을 OpenSQL backend로 전달하는 연결 계층이다. 현재 TCP만
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
