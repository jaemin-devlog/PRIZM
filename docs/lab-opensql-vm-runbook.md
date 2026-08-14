# PRIZM 연구실 OpenSQL VM 운영·복구 Runbook

이 문서는 Windows 연구실 PC에서 VirtualBox의 `<LAB_VM_NAME>` VM을 켜고,
OpenSQL·OpenProxy·Ollama와 PRIZM을 순서대로 확인·실행하는 운영 매뉴얼입니다.
실제 비밀번호, JWT secret, license key와 token은 이 문서나 Git에 저장하지 않습니다.

공개판은 PRZ-003의 비공개 운영 경계에 따라 실제 VM·사용자·네트워크 식별값과 사용자
절대 경로를 placeholder로 표시합니다. 연구실 PC에서는 Git-ignore된
`docs/lab-opensql-vm-runbook.local.md`를 먼저 열어 아래 placeholder의 실제값을 확인합니다.
로컬 파일에도 비밀번호는 저장하지 않습니다.

```text
<LAB_VM_NAME>
<LAB_LOGIN_USER>
<LAB_HOSTNAME>
<GUEST_NAT_NIC>
<GUEST_HOST_ONLY_NIC>
<GUEST_NAT_IP>
<HOST_ONLY_IP>
<VBOX_NAT_GATEWAY_CIDR>
<FLYWAY_NAT_RULE>
<RUNTIME_NAT_RULE>
<PRIZM_REPO_ROOT>
```

상태 표기의 의미는 다음과 같습니다.

- `LAB VERIFIED — 2026-08-15`: 이번 연구실 세션에서 실제 환경을 읽기 전용으로 확인했습니다.
- `PRZ-013 VERIFIED — 2026-08-14`: PRZ-013 단일 Primary Gate에서 실행 증거를 남겼습니다.
- `HISTORICAL`: 과거 장애·운영 기록이며 현재 상태로 단정하지 않습니다.
- `VERIFY`: 명령을 실행해 현재 세션의 결과를 확인해야 합니다.

> [!IMPORTANT]
> 현재 Windows의 DB 경로는 Host-only IP 직접 접속이 아닙니다. Flyway는
> `localhost:5432`, runtime은 `localhost:6432/opensql`을 사용하며, 두 포트 모두
> VirtualBox NAT 포워딩을 거쳐 VM으로 들어갑니다.

## 1. 5분 Quick Start

### 1.1 Windows에서 VirtualBox와 VM 시작

PowerShell을 열고 다음을 실행합니다. VirtualBox 기본 설치 경로가 다르면 GUI에서
직접 실행합니다.

```powershell
$vboxManage = 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe'
& $vboxManage showvminfo '<LAB_VM_NAME>' --machinereadable |
  Select-String '^VMState='
```

`VMState="poweroff"`이면 다음으로 VM 콘솔을 엽니다. 이미 `running`이면 다시 시작하지
않고 VirtualBox Manager에서 실행 중인 VM의 **Show**를 누릅니다.

```powershell
& $vboxManage startvm '<LAB_VM_NAME>' --type gui
```

### 1.2 VM 로그인과 네트워크 확인

VirtualBox 콘솔에서 다음 계정으로 로그인합니다.

```text
username: <LAB_LOGIN_USER>
password: <LAB_SECRET — NOT STORED IN GIT>
```

로그인 뒤 VM 터미널에서 실행합니다.

```bash
hostname
timedatectl
ip -brief address
```

정상 기준은 hostname `<LAB_HOSTNAME>`, timezone `Asia/Seoul`, NAT NIC
`<GUEST_NAT_NIC>`의 `<GUEST_NAT_IP>/24`입니다. `<GUEST_HOST_ONLY_NIC>` Host-only
NIC도 현재 존재하지만 PRIZM DB 연결에는 사용하지 않습니다.

### 1.3 etcd → Patroni/OpenSQL → OpenProxy 순서로 확인·시작

먼저 상태만 확인합니다.

```bash
systemctl is-active opensql-etcd
systemctl is-active patroni
systemctl is-active openproxy
```

`opensql-etcd`가 `active`여야 합니다. inactive라면 바로 서비스를 연쇄 시작하지 말고
상태와 로그를 먼저 확인합니다.

```bash
systemctl status opensql-etcd --no-pager -l
journalctl -u opensql-etcd -b --no-pager -n 100
```

etcd가 `active`이고 Patroni만 inactive일 때만 다음을 실행합니다. Patroni의 자동 시작
정책은 바꾸지 않습니다.

```bash
sudo systemctl start patroni
systemctl is-active patroni
ss -ltn | grep ':5432'
```

`:5432`가 LISTEN한 뒤 OpenProxy가 inactive일 때만 시작합니다.

```bash
sudo systemctl start openproxy
systemctl is-active openproxy
ss -ltn | grep ':6432'
```

정상이라면 두 포트 모두 `0.0.0.0`에서 LISTEN합니다.

### 1.4 Windows NAT 경로와 Ollama 확인

VM 터미널은 그대로 두고 Windows PowerShell에서 실행합니다.

```powershell
Test-NetConnection -ComputerName localhost -Port 5432 -InformationLevel Detailed
Test-NetConnection -ComputerName localhost -Port 6432 -InformationLevel Detailed
```

두 결과의 `TcpTestSucceeded`가 모두 `True`여야 합니다. 이는 TCP 확인일 뿐 SQL 인증
성공을 뜻하지 않습니다.

```powershell
$tags = Invoke-RestMethod 'http://localhost:11434/api/tags' -TimeoutSec 5
$tags.models | Select-Object name, model
```

목록에 `bge-m3` 또는 `bge-m3:latest`가 있어야 합니다.

### 1.5 PRIZM backend 실행

로컬 overlay의 `<PRIZM_REPO_ROOT>`에서 새 PowerShell을 엽니다. 먼저 현재
프로세스에만 secret을 넣는 함수를 준비합니다. 입력값은 화면과 명령 기록에 표시되지
않습니다.

```powershell
function Set-ProcessSecret([string]$Name) {
  $secure = Read-Host "Enter $Name" -AsSecureString
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    ([Environment])::SetEnvironmentVariable($Name, $plain, 'Process')
  }
  finally {
    if ($null -ne $plain) { $plain = $null }
    ([Runtime.InteropServices.Marshal])::ZeroFreeBSTR($pointer)
  }
}
```

OpenSQL profile과 두 DB 경로를 지정하고 secret을 입력합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'opensql'
$env:PRIZM_DB_URL = 'jdbc:postgresql://localhost:6432/opensql'
$env:PRIZM_DB_USERNAME = 'prizm_app'
$env:PRIZM_FLYWAY_URL = 'jdbc:postgresql://localhost:5432/prizm_integration_test'
$env:PRIZM_FLYWAY_USERNAME = 'prizm_owner'
$env:PRIZM_OLLAMA_BASE_URL = 'http://localhost:11434'
$env:PRIZM_EMBEDDING_MODEL = 'bge-m3'
$env:PRIZM_EMBEDDING_DIMENSIONS = '1024'

Set-ProcessSecret 'PRIZM_DB_PASSWORD'
Set-ProcessSecret 'PRIZM_FLYWAY_PASSWORD'

.\gradlew.bat bootRun --no-daemon
```

`Started ...Application`이 출력되고 `http://localhost:8080/actuator/health`가 응답하면
backend가 기동된 것입니다. runtime URL의 데이터베이스 이름은 반드시 `/opensql`입니다.

### 1.6 frontend 실행과 웹 접속

저장소 루트에서 또 다른 PowerShell을 열어 실행합니다.

```powershell
npm.cmd --prefix frontend run dev -- --host 127.0.0.1
```

현재 source와 `.env` 기준 웹 주소는 다음과 같습니다.

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
Health:   http://localhost:8080/actuator/health
```

과거 `http://localhost:15174` 기록은 현재 `.env`에서 확인되지 않았습니다. 별도 외부
포트 매핑을 명시적으로 사용하지 않는 한 `5173`을 사용합니다.

## 2. 현재 확정된 인프라 구조

대회용 OpenSQL 범위는 다음과 같습니다.

```text
OpenSQL Single only
OpenHA 사용 안 함
Replica 없음
Witness 없음
자동 failover 검증 안 함
```

PRZ-014의 다중 노드 OpenHA 시도는 `REJECTED`입니다. Replica, Witness, etcd cluster
확장, DB failover와 OpenProxy 이중화는 이 Runbook의 복구 대상이 아닙니다.

```text
Windows Host

PRIZM Flyway
→ jdbc:postgresql://localhost:5432/prizm_integration_test
→ VirtualBox NAT forwarding
→ OpenSQL Primary :5432

PRIZM Runtime
→ jdbc:postgresql://localhost:6432/opensql
→ VirtualBox NAT forwarding
→ OpenProxy :6432
→ OpenSQL Primary
→ prizm_integration_test

Ollama
→ http://localhost:11434
→ bge-m3
→ 1024 dimensions
```

PostgreSQL·pgvector 환경의 성공을 OpenSQL 증거로 사용하지 않고, OpenSQL Single의
성공을 OpenHA나 failover 증거로 확대하지 않습니다.

## 3. VM 정보

| 항목 | 현재 값 | 근거 |
|---|---|---|
| VM 이름 | `<LAB_VM_NAME>` | 실제값은 로컬 overlay; `LAB VERIFIED — 2026-08-15` |
| 현재 VM 상태 | `running` | 문서 작성 시 `VBoxManage showvminfo` |
| 게스트 OS | Rocky Linux 9.7 x86_64 | PRZ-003 검증 환경 |
| 구성 | OpenSQL Single | PRZ-003·PRZ-014 |
| Linux 로그인 사용자 | `<LAB_LOGIN_USER>` | 실제값은 로컬 overlay; `LAB VERIFIED — 2026-08-15` |
| hostname | `<LAB_HOSTNAME>` | 실제값은 로컬 overlay; `LAB VERIFIED — 2026-08-15` |
| timezone | `Asia/Seoul` | `LAB VERIFIED — 2026-08-15` |
| 시간 동기화 | `NTPSynchronized=no` 관찰 | `LAB VERIFIED — 2026-08-15`; 시간 기반 로그 비교 전 재확인 |
| NIC 1 | `<GUEST_NAT_NIC>`, NAT, `<GUEST_NAT_IP>/24` | 실제값은 로컬 overlay; `LAB VERIFIED — 2026-08-15` |
| NIC 2 | `<GUEST_HOST_ONLY_NIC>`, Host-only, `<HOST_ONLY_IP>/24` | 실제값은 로컬 overlay; `LAB VERIFIED — 2026-08-15` |

문서 작성 시점의 service/listener 스냅샷도 다음과 같이 확인했습니다.

```text
opensql-etcd: active, enabled
patroni: active, disabled
openproxy: active, disabled
guest 0.0.0.0:5432: LISTEN
guest 0.0.0.0:6432: LISTEN
Windows localhost:5432 TCP: PASS
Windows localhost:6432 TCP: PASS
```

`opensql` OS user는 OpenSQL/OpenProxy binary와 제한된 설정을 소유하고 서비스를 실행하는
계정입니다. 일상적인 대화형 로그인 계정으로 사용하지 않고, 관리자 socket 접근이나
binary 확인처럼 필요한 명령만 `sudo -u opensql ...`로 실행합니다.

현재 Host-only NIC는 존재하지만 Windows PRIZM의 DB 경로에는 필요하지 않습니다.
Host-only IP `<HOST_ONLY_IP>`를 runtime URL에 넣지 않습니다. 기존 어댑터를 삭제하거나
재구성할 이유도 없습니다.

VM 상태를 읽기 전용으로 다시 확인하려면 Windows PowerShell에서 실행합니다.

```powershell
$vboxManage = 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe'
& $vboxManage list vms
& $vboxManage list runningvms
& $vboxManage showvminfo '<LAB_VM_NAME>' --machinereadable
```

## 4. VirtualBox 실행과 VM 로그인

1. Windows 시작 메뉴에서 **Oracle VM VirtualBox**를 실행합니다.
2. 왼쪽 목록에서 로컬 overlay의 `<LAB_VM_NAME>`을 선택합니다.
3. 상태가 **Powered Off**이면 **Start**를 누릅니다. **Running**이면 **Show**를
   누릅니다.
4. Rocky Linux login 화면이 나올 때까지 기다립니다.
5. username에 로컬 overlay의 `<LAB_LOGIN_USER>`를 입력합니다.
6. password는 연구실에서 관리하는 값을 직접 입력합니다.

```text
username: <LAB_LOGIN_USER>
password: <LAB_SECRET — NOT STORED IN GIT>
```

비밀번호 입력 중 화면에 문자가 나타나지 않는 것은 정상입니다. 로그인 성공 시 prompt는
대략 다음 형태입니다.

```text
[<LAB_LOGIN_USER>@<LAB_HOSTNAME> ~]$
```

## 5. VM 부팅 직후 확인 명령

아래 명령은 먼저 읽기 전용으로 실행합니다.

```bash
hostname
date
timedatectl
ip -brief address
systemctl status opensql-etcd --no-pager -l
systemctl status patroni --no-pager -l
systemctl status openproxy --no-pager -l
ss -ltn
```

| 명령 | 정상 기준 | 비정상일 때 다음 확인 |
|---|---|---|
| `hostname` | `<LAB_HOSTNAME>` | 다른 VM에 로그인했는지 VirtualBox VM 이름 확인 |
| `date`, `timedatectl` | timezone `Asia/Seoul`; 현재 시각이 크게 어긋나지 않음 | 인증·로그 시간 비교 전에 시간 상태만 진단; 이 문서에서 NTP 설정을 변경하지 않음 |
| `ip -brief address` | `<GUEST_NAT_NIC> UP <GUEST_NAT_IP>/24` | NAT NIC cable/state와 DHCP 확인; Host-only부터 재구성하지 않음 |
| etcd status | `active (running)` | `journalctl -u opensql-etcd -b --no-pager -n 100` |
| Patroni status | `active (running)` | etcd가 active인지 먼저 확인한 뒤 Patroni 로그 확인 |
| OpenProxy status | `active (running)` | 먼저 guest `:5432` LISTEN 확인 후 OpenProxy 로그 확인 |
| `ss -ltn` | `0.0.0.0:5432`, `0.0.0.0:6432` | 빠진 포트를 소유한 서비스만 조사 |

process 이름까지 보려면 sudo로 다음을 실행합니다.

```bash
sudo ss -lntp | grep -E '(:5432|:6432)'
```

## 6. OpenSQL 시작 절차

### 역할과 시작 순서

- `opensql-etcd`: Patroni가 Primary 상태와 leader 정보를 판단할 때 쓰는 분산 상태
  저장소입니다. 현재 Single 구성에서도 설치 구조상 필요합니다.
- `patroni`: OpenSQL 프로세스의 상태와 Primary 기동을 관리합니다.
- OpenSQL: 실제 PostgreSQL 호환 SQL 서버이며 `:5432`를 LISTEN합니다.

과거와 현재 설정에서 `opensql-etcd`는 boot enable이고, Patroni는 설치돼 있지만
auto-start가 disabled입니다. 이 정책을 임의로 `enable`하지 않습니다.

```bash
systemctl is-enabled opensql-etcd
systemctl is-enabled patroni
systemctl is-active opensql-etcd
systemctl is-active patroni
```

정상 시작 순서는 다음과 같습니다.

1. etcd가 `active`인지 확인합니다.
2. etcd가 정상인데 Patroni만 inactive이면 Patroni를 시작합니다.
3. Patroni가 `active`인지 확인합니다.
4. guest `:5432`가 LISTEN하는지 확인합니다.

```bash
sudo systemctl start patroni
systemctl status patroni --no-pager -l
ss -ltn | grep ':5432'
```

실패 로그는 현재 boot와 최근 줄만 봅니다. 전체 journal을 대량 출력하지 않습니다.

```bash
journalctl -u patroni -b --no-pager -n 100
journalctl -u opensql-etcd -b --no-pager -n 100
```

`patroni.service`가 active인데 `:5432`가 없으면 Windows NAT나 credential을 만지기
전에 Patroni 로그에서 OpenSQL process 기동 실패를 확인합니다.

## 7. OpenSQL binary와 psql

확인된 client 경로와 버전은 다음과 같습니다.

```text
/home/opensql/bin/psql
psql 17.8
```

binary 경로가 일반 로그인 사용자에게 제한될 수 있으므로 다음처럼 확인합니다.

```bash
sudo -u opensql /home/opensql/bin/psql --version
```

관리자 Unix socket 접근 구조는 다음과 같습니다.

```bash
sudo -u opensql /home/opensql/bin/psql \
  -h /home/opensql/tmp \
  -p 5432 \
  -U postgres \
  -d postgres
```

이 명령은 vector 최초 bootstrap, DB role 상태 확인처럼 승인된 DB 관리자 작업에만
사용합니다. 일상적인 PRIZM runtime 확인에 쓰지 않으며, SQL prompt에 들어갔다고 해서
role/password/schema를 임의 변경하지 않습니다. 종료는 `\q`입니다.

## 8. DB role 구조

| Role | 용도 | 사용 위치 |
|---|---|---|
| Privileged administrator | DB infrastructure prerequisite와 vector extension 최초 bootstrap | 제한된 관리자 작업 |
| `prizm_owner` | Flyway migration과 schema ownership | direct `localhost:5432` |
| `prizm_app` | PRIZM runtime DML | OpenProxy `localhost:6432/opensql` |

다음 경계를 유지합니다.

- `prizm_owner`를 runtime 계정으로 사용하지 않습니다.
- `prizm_app`에 migration 또는 DDL 권한을 확대하지 않습니다.
- `prizm_owner`를 superuser로 승격하지 않습니다.
- 장애가 나도 role을 새로 만들거나 password를 먼저 변경하지 않습니다.

## 9. Verification DB

연구실 검증 DB는 다음입니다.

```text
prizm_integration_test
```

실제 사용자용 DB `prizm`과 완전히 별개로 취급합니다. fresh DB가 필요한 검증에서 이전
Flyway 흔적이 보여도 다음 원칙을 지킵니다.

- 실제 사용자 DB `prizm`을 삭제하거나 초기화하지 않습니다.
- 명시적으로 승인된 검증용 DB `prizm_integration_test`만 대상으로 합니다.
- DB 삭제·재생성 전에 대상 이름, owner, 연결 사용자를 다시 확인합니다.
- 이 Runbook의 일반 시작 절차에는 DB 삭제·재생성을 포함하지 않습니다.

## 10. pgvector 최초 준비

OpenSQL은 PostgreSQL 17 기반이며, 검증된 pgvector 버전은 `0.8.1`, embedding column
계약은 `vector(1024)`입니다.

Flyway V1에는 다음 SQL이 있습니다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

그러나 `prizm_owner`는 non-superuser이므로 완전히 새 검증 DB에서는 최초 extension
생성이 실패할 수 있습니다. 정상 책임 분리는 다음과 같습니다.

```text
Privileged DB administrator
→ CREATE EXTENSION vector

prizm_owner
→ Flyway

prizm_app
→ runtime
```

새 검증 DB를 명시적으로 준비하는 작업에서만 관리자 session으로 대상 DB를 확인한 뒤
실행합니다.

```sql
SELECT current_database(), current_user;
CREATE EXTENSION IF NOT EXISTS vector;
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
```

이 절은 복구 시 자동 실행 목록이 아닙니다. 기존 DB에서 Flyway가 실패하면 먼저 이미
설치된 extension과 접속 DB 이름을 조회합니다.

## 11. VirtualBox NAT Port Forwarding

현재 정상 경로는 다음 두 규칙입니다.

| Rule 이름 | Protocol | Host IP | Host port | Guest IP | Guest port |
|---|---|---|---:|---|---:|
| `<FLYWAY_NAT_RULE>` | TCP | `127.0.0.1` | `5432` | `<GUEST_NAT_IP>` | `5432` |
| `<RUNTIME_NAT_RULE>` | TCP | `127.0.0.1` | `6432` | `<GUEST_NAT_IP>` | `6432` |

두 rule과 guest IP 명시는 `LAB VERIFIED — 2026-08-15`입니다. NIC 1은 NAT이며 cable이
연결돼 있습니다.

Windows PowerShell에서 읽기 전용으로 다시 확인합니다.

```powershell
$vboxManage = 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe'
& $vboxManage showvminfo '<LAB_VM_NAME>' --machinereadable |
  Select-String '^(nic1|natnet1|cableconnected1|Forwarding)'
```

정상 출력의 핵심은 다음과 같습니다.

```text
nic1="nat"
cableconnected1="on"
Forwarding(0)="<FLYWAY_NAT_RULE>,tcp,127.0.0.1,5432,<GUEST_NAT_IP>,5432"
Forwarding(1)="<RUNTIME_NAT_RULE>,tcp,127.0.0.1,6432,<GUEST_NAT_IP>,6432"
```

rule이 없다면 VM·guest 서비스·guest IP를 먼저 확인합니다. 기존 rule을 삭제하거나 같은
이름으로 덮어쓰기 전에 별도 승인을 받고, VirtualBox GUI의 **Settings → Network →
Adapter 1 (NAT) → Advanced → Port Forwarding**에서 현재 값과 위 표를 대조합니다.
Host-only adapter를 대체 경로로 만들지 않습니다.

## 12. VM firewall

NAT에서 guest가 보는 source CIDR은 `<VBOX_NAT_GATEWAY_CIDR>`입니다. 정상 연구실
구성은 public zone에서
다음을 허용합니다.

```text
source: <VBOX_NAT_GATEWAY_CIDR>
TCP 5432
TCP 6432
runtime rule 존재
permanent rule 존재
```

이 두 runtime/permanent 규칙은 2026-08-15 연구실 복구 세션에서 확인했습니다. 현재
세션에서 다시 볼 때는 VM에서 다음 읽기 전용 명령을 실행합니다. sudo 비밀번호는 화면에
직접 입력합니다.

```bash
sudo firewall-cmd --get-active-zones
sudo firewall-cmd --zone=public --list-rich-rules
sudo firewall-cmd --permanent --zone=public --list-rich-rules
```

정상 rule의 의미는 다음과 같습니다. 출력 순서와 따옴표 표현은 다를 수 있습니다.

```text
rule family="ipv4" source address="<VBOX_NAT_GATEWAY_CIDR>" port port="5432" protocol="tcp" accept
rule family="ipv4" source address="<VBOX_NAT_GATEWAY_CIDR>" port port="6432" protocol="tcp" accept
```

### 복구 시에만 사용

아래 변경 명령은 rule이 실제로 빠졌고, NAT rule·guest LISTEN 상태를 이미 확인했으며,
사용자가 firewall 복구를 승인한 경우에만 사용합니다. 기존 연구실 rule을 지우거나 public
zone 전체를 열지 않습니다.

```bash
sudo firewall-cmd --zone=public \
  --add-rich-rule='rule family="ipv4" source address="<VBOX_NAT_GATEWAY_CIDR>" port port="5432" protocol="tcp" accept'
sudo firewall-cmd --zone=public \
  --add-rich-rule='rule family="ipv4" source address="<VBOX_NAT_GATEWAY_CIDR>" port port="6432" protocol="tcp" accept'
```

runtime 검증이 성공한 뒤에만 같은 두 rule을 permanent에 추가합니다.

```bash
sudo firewall-cmd --permanent --zone=public \
  --add-rich-rule='rule family="ipv4" source address="<VBOX_NAT_GATEWAY_CIDR>" port port="5432" protocol="tcp" accept'
sudo firewall-cmd --permanent --zone=public \
  --add-rich-rule='rule family="ipv4" source address="<VBOX_NAT_GATEWAY_CIDR>" port port="6432" protocol="tcp" accept'
```

## 13. Windows에서 연결 확인

### TCP 확인

```powershell
Test-NetConnection localhost -Port 5432 -InformationLevel Detailed
Test-NetConnection localhost -Port 6432 -InformationLevel Detailed
```

`TcpTestSucceeded : True`가 정상입니다.

> [!WARNING]
> TCP PASS는 SQL PASS가 아닙니다. TCP는 listener까지 도달했다는 뜻일 뿐, DB 이름,
> role, password, OpenProxy client/backend 인증과 query 실행을 증명하지 않습니다.

### 실제 SQL handshake와 `SELECT 1`

현재 Windows host에서는 `psql`이 PATH에 없는 상태가 `LAB VERIFIED — 2026-08-15`로
확인됐습니다. SQL Gate에는 승인된 PostgreSQL client가 필요합니다. `psql`을 새로
설치했다고 가정하지 말고 먼저 확인합니다.

```powershell
Get-Command psql -ErrorAction Stop
```

승인된 `psql`이 있으면 password를 command line에 넣지 말고 현재 PowerShell process의
`PGPASSWORD`에 숨김 입력합니다. 1.5절의 `Set-ProcessSecret` 함수를 먼저 정의합니다.

```powershell
Set-ProcessSecret 'PGPASSWORD'

psql -X -v ON_ERROR_STOP=1 -h localhost -p 5432 -U prizm_app -d prizm_integration_test -Atc 'SELECT current_user, current_database(), 1;'

psql -X -v ON_ERROR_STOP=1 -h localhost -p 6432 -U prizm_app -d opensql -Atc 'SELECT current_user, current_database(), 1;'

Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
```

두 명령 모두 exit code 0이어야 하며 `current_user`는 `prizm_app`, backend database는
`prizm_integration_test`, 상수 결과는 `1`이어야 합니다. 두 번째 명령의 client 접속
DB는 반드시 논리 이름 `opensql`입니다. Windows client가 없으면 TCP PASS만 기록하고
SQL을 `NOT_RUN`으로 남깁니다. 다른 PostgreSQL/Testcontainers 성공으로 대체하지 않습니다.

## 14. OpenProxy

확정 정보는 다음과 같습니다.

```text
Version: 1.1.3
Binary: /home/opensql/bin/openproxy
Config: /home/opensql/etc/openproxy/openproxy.toml
systemd unit: openproxy
Config owner/mode: opensql:opensql 0600
```

version과 config permission은 `PRZ-013 VERIFIED — 2026-08-14`입니다. 현재 service의
실행 경로, user/group과 working directory는 `LAB VERIFIED — 2026-08-15`입니다.
문서 작성 시 service는 `active`였고 enable 상태는 `disabled`였습니다. 부팅 정책을
임의로 바꾸지 말고, 매 부팅 뒤 상태를 확인해 필요할 때만 수동 시작합니다.

```bash
systemctl status openproxy --no-pager -l
systemctl cat openproxy
systemctl show openproxy \
  -p ExecStart -p User -p Group -p WorkingDirectory \
  --no-pager
```

정상 핵심 값은 다음과 같습니다.

```text
ExecStart=/home/opensql/bin/openproxy /home/opensql/etc/openproxy/openproxy.toml
User=opensql
Group=opensql
WorkingDirectory=/home/opensql
```

version과 config metadata를 확인할 때는 secret 본문을 출력하지 않습니다.

```bash
sudo -u opensql /home/opensql/bin/openproxy --version
sudo stat -c '%U:%G %a %n' /home/opensql/etc/openproxy/openproxy.toml
```

로그는 실패 직후의 작은 범위만 봅니다.

```bash
journalctl -u openproxy -b --no-pager -n 100
```

## 15. OpenProxy 정상 config 구조

실제 secret을 출력하지 않은 정상 구조는 다음과 같습니다.

```toml
[general]
port = 6432

[pools.opensql]
default_role = "primary"
pool_mode = "Session"

[pools.opensql.shards.0]
database = "prizm_integration_test"

[[pools.opensql.shards.0.servers]]
host = "<PRIMARY_HOST>"
port = 5432
role = "Primary"

[pools.opensql.users.0]
username = "prizm_app"
password = "<MASKED-CLEARTEXT>"
server_username = "prizm_app"
server_password = "<MASKED-CLEARTEXT>"
pool_size = 10
statement_timeout = 0
```

인증 계약은 두 층으로 구분합니다.

```text
Client → OpenProxy
password = cleartext

OpenProxy → OpenSQL
server_password = cleartext

OpenSQL role 저장
SCRAM-SHA-256 verifier
```

- `openproxy encode` 결과를 client `password`로 넣지 않습니다.
- 현재 정상 구성은 `query_auth`를 사용하지 않습니다.
- cleartext라는 말은 config field의 입력 형식입니다. 실제 값을 문서·콘솔 출력·Git diff에
  남겨도 된다는 뜻이 아닙니다.
- config를 조회할 때 전체 파일을 화면에 출력하지 말고 non-secret key 또는 metadata만
  확인합니다.

## 16. Troubleshooting History

### 오류 A — Windows localhost 5432/6432 둘 다 실패

처음에는 Host-only NIC 문제로 오인했습니다. 다음 조사와 복구 시도가 이어졌습니다.

- VirtualBox Host-Only adapter 조사
- Host-only guest NIC 조사
- legacy Host-only adapter recreation
- `hostonlynet` 조사
- VM soft-lockup 발생
- graceful reboot

최종 확인한 정상 경로는 다음입니다.

```text
연구실 정상 DB 경로는 Host-only 직접 연결이 아니라
VirtualBox NAT port forwarding
```

다음에 두 포트가 함께 실패하면 아래 순서로 봅니다.

```text
NAT forwarding
→ VM 서비스
→ guest firewall
```

Host-only adapter부터 재구성하지 않습니다.

### 오류 B — localhost:5432 PASS, :6432 FAIL

OpenSQL direct 경로는 정상이지만 OpenProxy SQL handshake가 실패했습니다. 당시 로그에는
다음이 보였습니다.

```text
ClientAuthImpossible("prizm_app")
No default auth query configured
```

처음에는 credential 문제로 오인해 다음을 조사했습니다.

- `.env` credential 일치 여부
- OpenProxy `password`와 `server_password`
- SCRAM verifier 형식
- `openproxy encode` 결과
- 한 차례 최소 config 시도와 rollback

이 항목들은 최종 원인이 아니었습니다. 같은 증상에서 password부터 바꾸지 않습니다.

### 오류 C — 최종 실제 원인: OpenProxy 접속 DB 이름 오류

> [!CAUTION]
> OpenProxy client는 backend 실제 DB 이름이 아니라 pool/logical database
> `/opensql`로 접속해야 합니다. runtime URL을
> `localhost:6432/prizm_integration_test`로 바꾸면 안 됩니다.

정상 URL은 다음 두 개입니다.

```text
Runtime JDBC:
jdbc:postgresql://localhost:6432/opensql

Flyway JDBC:
jdbc:postgresql://localhost:5432/prizm_integration_test
```

OpenProxy는 `/opensql` pool로 받은 연결을 backend의 `prizm_integration_test`로
routing합니다. 잘못된 `/prizm_integration_test` 접속은 등록된 pool/user mapping을
찾지 못해 credential 문제처럼 보이는 client-auth 오류를 만들 수 있습니다.

이번 복구의 최종 결과는 다음과 같습니다.

```text
localhost:5432 SQL handshake PASS
localhost:6432 SQL handshake PASS
SELECT 1 PASS
OpenProxy config 변경 0
Production 변경 0
```

이는 `LAB VERIFIED — 2026-08-15`의 같은 복구 세션에서 얻은 SQL 결과입니다. 다음
부팅에서는 20절 checklist를 다시 실행하고 과거 PASS를 현재 PASS로 복사하지 않습니다.

## 17. PRIZM 환경변수

실제 secret은 `.env`, 보호된 secret store 또는 현재 process에서만 관리합니다. Runbook,
명령 history, screenshot과 Git diff에는 넣지 않습니다.

연구실 OpenSQL 실행에 필요한 구조는 다음과 같습니다.

```dotenv
SPRING_PROFILES_ACTIVE=opensql

PRIZM_DB_URL=jdbc:postgresql://localhost:6432/opensql
PRIZM_DB_USERNAME=prizm_app
PRIZM_DB_PASSWORD=<LAB_SECRET>

PRIZM_FLYWAY_URL=jdbc:postgresql://localhost:5432/prizm_integration_test
PRIZM_FLYWAY_USERNAME=prizm_owner
PRIZM_FLYWAY_PASSWORD=<LAB_SECRET>

PRIZM_OLLAMA_BASE_URL=http://localhost:11434
PRIZM_EMBEDDING_MODEL=bge-m3
PRIZM_EMBEDDING_DIMENSIONS=1024
```

현재 `.env`를 읽기 전용으로 확인한 결과 기본 profile은 `local`이고 DB가 분해된
Host-only 값으로 남아 있습니다. 이는 이 Runbook의 OpenProxy runtime 계약이 아닙니다.
연구실 OpenSQL 실행 시 위 process 환경변수로 명시적으로 override합니다. `.env`를
출력하거나 password를 복사해 문서에 넣지 않습니다.

backend를 종료한 뒤 같은 PowerShell을 계속 사용할 경우 secret과 lab override를
지웁니다.

```powershell
'PRIZM_DB_PASSWORD',
'PRIZM_FLYWAY_PASSWORD',
'PRIZM_DB_URL',
'PRIZM_DB_USERNAME',
'PRIZM_FLYWAY_URL',
'PRIZM_FLYWAY_USERNAME',
'SPRING_PROFILES_ACTIVE' |
  ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
```

## 18. Ollama

Ollama는 VM이 아니라 현재 Windows 연구실 host에서 실행합니다.

```text
Base URL: http://localhost:11434
Model: bge-m3
Embedding dimensions: 1024
```

API와 모델 목록을 확인합니다.

```powershell
$tags = Invoke-RestMethod 'http://localhost:11434/api/tags' -TimeoutSec 5
$tags.models | Select-Object name, model
```

실제 embedding 차원을 읽기 전용 probe로 확인합니다.

```powershell
$body = @{ model = 'bge-m3'; input = 'PRIZM dimension probe' } | ConvertTo-Json
$result = Invoke-RestMethod -Method Post -Uri 'http://localhost:11434/api/embed' -ContentType 'application/json' -Body $body -TimeoutSec 30
@($result.embeddings[0]).Count
```

정상 결과는 `1024`입니다. API, `bge-m3` 존재와 1024차원 응답은
`LAB VERIFIED — 2026-08-15`입니다. 모델이 없으면 바로 `ollama pull`이나 재설치를 하지
말고 현재 프로젝트 문서와 승인된 모델 준비 절차를 확인합니다.

## 19. PRIZM 실행

### Backend

저장소 루트에서 17절 환경변수와 secret을 현재 process에 설정한 뒤 실행합니다.

```powershell
.\gradlew.bat bootRun --no-daemon
```

필수 profile은 `opensql`입니다. Flyway는 direct `:5432`, runtime datasource는
OpenProxy `:6432/opensql`이어야 합니다. 시작 로그에 secret이 노출되지 않았는지 확인하고,
Flyway가 다른 DB를 가리키면 즉시 종료합니다.

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/health' -TimeoutSec 5
```

### Frontend

별도 PowerShell에서 실행합니다.

```powershell
npm.cmd --prefix frontend run dev -- --host 127.0.0.1
```

현재 `frontend/vite.config.ts`는 dev port `5173`을 고정하고, `.env`의 frontend proxy
target은 `http://localhost:8080`입니다.

```text
LAB VERIFIED source/current .env:
http://localhost:5173

HISTORICAL — VERIFY CURRENT .env/external mapping:
http://localhost:15174
```

`15174`를 쓰려면 현재 세션에 그 포트를 만드는 별도 승인된 실행·포트 매핑이 실제로
있는지 먼저 확인합니다. 과거 URL만 보고 접속 주소를 바꾸지 않습니다.

## 20. 정상 기동 Verification Checklist

각 항목은 이번 세션에서 실제 확인한 뒤 체크합니다. 이전 PASS를 현재 PASS로 복사하지
않습니다.

```text
[ ] VM boot
[ ] etcd active
[ ] Patroni active
[ ] guest :5432 LISTEN
[ ] OpenProxy active
[ ] guest :6432 LISTEN

[ ] Windows localhost:5432 TCP PASS
[ ] Windows localhost:5432 SQL PASS

[ ] Windows localhost:6432 TCP PASS
[ ] Windows localhost:6432 prizm_app SQL PASS
[ ] SELECT 1 PASS

[ ] Ollama PASS
[ ] bge-m3 PASS
[ ] 1024 dimension

[ ] PRIZM backend started
[ ] Flyway direct :5432 PASS
[ ] runtime OpenProxy :6432 PASS
[ ] frontend/web PASS
```

Windows `psql`이 없어서 SQL을 실행하지 못했으면 해당 SQL 항목은 `NOT_RUN`입니다.
TCP만 성공한 상태에서 체크하지 않습니다.

## 21. 안전한 종료 절차

정상 종료 순서는 다음과 같습니다.

1. frontend PowerShell에서 `Ctrl+C`를 누릅니다.
2. backend PowerShell에서 `Ctrl+C`를 누르고 종료 로그를 기다립니다.
3. 17절의 process 환경변수를 제거하거나 해당 PowerShell 창을 닫습니다.
4. VM console에서 작업 중인 shell과 SQL session을 종료합니다.
5. VM을 정상 shutdown합니다.
6. VirtualBox가 VM을 `Powered Off`로 표시한 뒤 VirtualBox Manager를 닫습니다.

VM console의 정상 종료 명령은 다음입니다.

```bash
sudo systemctl poweroff
```

콘솔 입력이 불가능할 때만 Windows에서 ACPI 종료 신호를 보낼 수 있습니다.

```powershell
$vboxManage = 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe'
& $vboxManage controlvm '<LAB_VM_NAME>' acpipowerbutton
& $vboxManage showvminfo '<LAB_VM_NAME>' --machinereadable |
  Select-String '^VMState='
```

바로 `poweroff`로 강제 전원을 끄는 방식을 기본 절차로 사용하지 않습니다. shutdown이
오래 걸리면 console과 서비스 로그를 확인하고 데이터 손상 위험을 평가합니다.

## 22. 절대로 하지 말아야 할 것

> [!CAUTION]
> 아래 작업은 이 연구실 환경의 일반 복구 방법이 아닙니다.

```text
OpenSQL 재설치 금지
새 OpenSQL VM 생성 금지
DB 전체 초기화 금지
실제 prizm DB 삭제 금지

OpenHA 구성 금지
Replica/Witness 생성 금지
etcd cluster 확장 금지

prizm_owner superuser 승격 금지
prizm_app 권한 확대 금지

문제 발생 시 credential부터 변경하지 말 것
Host-only network부터 재구성하지 말 것

OpenProxy runtime URL의 /opensql을
/prizm_integration_test로 임의 변경하지 말 것
```

또한 기존 NAT/firewall rule, NIC, OpenProxy config를 조사 없이 삭제·덮어쓰지 않습니다.
복구가 필요하면 대상과 rollback을 먼저 고정하고 한 계층만 최소 변경합니다.

## 23. 장애 발생 시 진단 순서

순서를 건너뛰지 않습니다. 앞 단계가 실패하면 뒤 계층을 수정하지 않습니다.

1. VM이 실행 중인가?
2. VM OS가 정상 응답하는가?
3. etcd가 active인가?
4. Patroni가 active인가?
5. guest `:5432`가 LISTEN인가?
6. Windows `localhost:5432` TCP가 되는가?
7. `localhost:5432` SQL handshake가 되는가?
8. OpenProxy가 active인가?
9. guest `:6432`가 LISTEN인가?
10. Windows `localhost:6432` TCP가 되는가?
11. Runtime DB URL이 `/opensql`인가?
12. `prizm_app` SQL handshake와 `SELECT 1`이 되는가?
13. Ollama API, `bge-m3`, 1024차원이 정상인가?
14. 그 다음에야 PRIZM profile, Flyway와 application log를 조사합니다.

이 순서가 중요한 이유는 네트워크·listener·SQL 인증·application을 서로 다른 Gate로
분리하기 위해서입니다.

## 24. 오류별 빠른 판별표

| 증상 | 가장 먼저 확인 | 다음 판정 |
|---|---|---|
| `5432` TCP FAIL | VM 상태 → Patroni → guest LISTEN → NAT → firewall | credential을 바꾸지 않음 |
| `5432` TCP PASS + SQL FAIL | direct DB 이름, `prizm_app` credential·role 상태 | OpenProxy 문제로 분류하지 않음 |
| `5432` PASS + `6432` TCP FAIL | OpenProxy service/LISTEN → NAT `<RUNTIME_NAT_RULE>` → firewall | DB role 변경 금지 |
| `6432` TCP PASS + SQL FAIL | **client DB URL이 `/opensql`인지 먼저 확인** | 그 다음에만 client/backend auth log 확인 |
| `ClientAuthImpossible` | 접속 DB가 `/opensql`인지, 해당 pool user mapping | 메시지만 보고 password 불일치로 단정하지 않음 |
| Ollama FAIL | Windows host Ollama `:11434` | VM/OpenSQL을 수정하지 않음 |
| `bge-m3` 없음 | `/api/tags`, 승인된 모델 준비 문서 | 임의 설치 전 확인 |
| embedding 차원 오류 | model 이름과 `PRIZM_EMBEDDING_DIMENSIONS=1024` | DB schema를 바꾸지 않음 |
| Flyway FAIL | direct `:5432`, DB `prizm_integration_test`, `prizm_owner`, vector prerequisite | runtime `/opensql`과 혼동하지 않음 |
| Runtime FAIL | proxy `:6432/opensql`, `prizm_app`, OpenProxy log | Flyway owner로 우회 금지 |
| web `15174` FAIL | 현재 Vite `5173`, 외부 port mapping 존재 여부 | 역사적 주소를 현재 주소로 단정하지 않음 |
| 로그 시간이 맞지 않음 | `date`, `timedatectl`, `NTPSynchronized` | 시간 설정 변경 전에 원인·승인 확인 |

## 25. Source of Truth

충돌할 때 다음 우선순위를 사용합니다.

1. 현재 실제 실행 환경의 read-only 조회와 실제 command 결과
2. 이 Runbook의 `LAB VERIFIED` 항목
3. [PRZ-013 OpenProxy 단일 Primary Gate](../specs/PRZ-013-openproxy-single-primary-gate/spec.md)의 `VERIFIED` 기록
4. [OpenSQL 기술 Gate](opensql-gate.md)
5. 과거 대화 기록

관련 세부 근거:

- [PRZ-003 OpenSQL single-node Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md)
- [PRZ-014 OpenHA 시도 거절 기록](../specs/PRZ-014-openha-topology-gate/spec.md)
- [환경 변수 예제](../.env.example)
- [OpenSQL profile](../src/main/resources/application-opensql.yml)
- [공통 application 설정](../src/main/resources/application.yml)

과거 기록과 현재 환경이 충돌하면 현재 실제 read-only 확인 결과를 우선합니다. 단, 현재
TCP 결과를 SQL·OpenProxy routing·application E2E 결과로 확대하지 않습니다.
