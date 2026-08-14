# OpenSQL 단일 환경 기술 Gate

> 상태: `PASS_SINGLE_NODE_SQL_GATE` (2026-07-30)

실제 OpenSQL single-node 설치와 비식별 환경 근거는
[PRZ-003](../specs/PRZ-003-opensql-single-node-gate/evidence.md)에서 관리한다.
이 문서는 실제로 실행한 PRIZM 단일 SQL Gate의 명령·완료 조건과 재실행 경계를
제공한다.

PostgreSQL 성공을 OpenSQL 성공으로 바꾸어 표현하지 않는다.

## 완료 기준

다음 조건을 실제 OpenSQL single-node 환경에서 모두 확인해 `PASS`로 판정했다.

1. OpenSQL과 pgvector가 설치된 검증 전용 DB 또는 schema에 JDBC로 접속한다.
2. Flyway V1~V13 migration을 순서대로 적용한다.
3. `vector(1024)`, `vector_dims`, exact cosine `<=>`를 실제 실행한다.
4. `VectorSearchRepository`의 owner·ACTIVE version 조건을 검증한다.
5. Indexing·Cleanup claim, lease, fencing, recovery와 두 connection의
   `FOR UPDATE SKIP LOCKED`를 검증한다.
6. 공개 가능한 제품 범주, PRIZM Gate 명령, 비식별 결과와 한계를
   `evidence.md`에 기록한다. 공급 package/version, installer 내부 명령과 log는
   Git 밖의 비공개 근거로만 보존한다.

## 실행 환경 확인

| 항목 | 상태 |
|---|---|
| OpenSQL single 설치 | `PASS_INSTALLATION_ONLY` — 공급 자산 세부정보는 비공개 |
| 공급사 테스트 라이선스 적용 | `PASS_PRIVATE_EVIDENCE` — 자산은 Git 밖에서 관리 |
| 기본 인증 DB 질의 | `PASS_INSTALLATION_ONLY` |
| `vector` extension·`vector(1024)` 실행 검증 | `PASS` |
| 일회성 검증 전용 DB | `PASS_PRIVATE_EVIDENCE` — 실행 뒤 제거 확인 |
| 분리된 Flyway 계정과 runtime 계정 | `PASS_PRIVATE_EVIDENCE` — 실행 뒤 제거 확인 |
| TLS 요구사항 | `NOT_RUN` — 비공개 Host-only 단일 환경 Gate 범위 밖 |
| 비공개 Host-only 네트워크 | `PASS_PRIVATE_EVIDENCE` |

민감한 JDBC URL, 사용자명, 비밀번호와 host 목록은 Git에 기록하지 않는다.

## 실행

Flyway와 runtime datasource는 migration 전에 각각 base table이 없는지 확인한다.
조회 권한이 부족하거나 기존 table이 있으면 fixture 생성 전에 실패해야 한다.

```powershell
$env:RUN_OPENSQL_TESTS='true'
$env:PRIZM_OPENSQL_VERIFICATION_TARGET_CONFIRMED='true'
$env:PRIZM_DB_URL='<runtime JDBC URL>'
$env:PRIZM_DB_USERNAME='<runtime user>'
$env:PRIZM_DB_PASSWORD='<runtime password>'
$env:PRIZM_FLYWAY_URL='<migration JDBC URL>'
$env:PRIZM_FLYWAY_USERNAME='<migration user>'
$env:PRIZM_FLYWAY_PASSWORD='<migration password>'
.\gradlew.bat integrationTest --no-daemon --rerun-tasks `
  --tests com.prizm.infrastructure.OpenSqlInfrastructureTest
```

`RUN_OPENSQL_TESTS`가 없으면 test는 `SKIPPED`이며 성공 증거가 아니다. Suite는
Flyway가 만든 비활성 UUID marker를 runtime 계정이 같은 ID로 읽어야만 fixture를
시작한다. 전역 `DELETE`나 `TRUNCATE` 없이 실행별 UUID와 생성 ID만 정리한다.

## 2026-07-30 실행 결과

- `OpenSqlInfrastructureTest` 1건, 실패 0·오류 0·건너뜀 0
- Gradle 종료 코드 0, `BUILD SUCCESSFUL`
- Flyway V1~V13, `vector(1024)`·`vector_dims`·cosine 검색, owner·ACTIVE
  version 격리, processing/cleanup job의 claim·lease·fencing·recovery와 두
  connection의 `FOR UPDATE SKIP LOCKED` 검증
- 전용 DB와 두 login role, 임시 helper의 실행 후 제거 확인
- 실제 통합: [PR #24](https://github.com/jaemin-devlog/PRIZM/pull/24),
  merge commit `777e184f206d2a2770d055940ddabf139abfed9d`

상세 비식별 결과와 보안 경계는
[PRZ-003 Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md)를
현재 진실로 사용한다.

## 현재 Gate에 포함하지 않는 것

- Ollama와 실제 embedding 생성
- Indexing·Cleanup Scheduler 실행과 실제 파일 삭제
- HNSW 성능
- OpenProxy runtime 연결
- 다중 DB 노드 장애전환, RTO와 RPO

OpenProxy 단일 Primary SQL routing은 별도 spec에서 검증한다. 대회 제공 OpenSQL은
공식 안내에 따라 단일 서버 설치만 사용하며, 다중 DB 노드 장애전환은 착수하지 않는다.

## 실패·미확보 처리

- 재실행하지 못한 새 환경의 결과는 기존 환경의 `PASS`로 대체하지 않고
  `NOT_RUN`으로 기록한다.
- 라이선스나 재배포 조건이 불명확하면 구성요소를 저장소에 포함하지 않는다.
- 실패 결과에는 migration 번호, SQLState와 SQL 기능 범주만 기록한다.
- 전체 JDBC URL, host, 계정과 비밀번호는 출력하지 않는다.
- OpenSQL이 없으면 PostgreSQL 16+pgvector 회귀 test를 유지하되 별도 결과로 기록한다.

현재 구현은 [현재 구현 현황](project-status.md), 설치 환경과 실행 결과는
[PRZ-003 Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md)를 기준으로 한다.

## 2026-08-13 OpenSQL V15 G0 기준선 재검증

### 목적과 범위

G0는 OpenProxy 작업 전에 최신 `main`의 PRIZM이 실제 연구실 OpenSQL direct
`:5432` 환경에서 정상 동작하는지 다시 확인하는 Gate였다. 검증 범위는 Flyway V1~V15,
pgvector, `prizm_owner` / `prizm_app` 권한 분리, Worker SQL, TXT/PDF 색인, 실제 Ollama
`bge-m3`, owner isolation, 그리고 실패한 새 version 발생 시 기존 ACTIVE version 보존이다.

### 최종 결과

- G0-1 `OpenSqlInfrastructureTest`: `PASS`
- Flyway V1~V15: `PASS`; pending migration: `0`
- runtime privilege contract, `vector(1024)` / cosine: `PASS`
- Processing/Cleanup Worker claim·lease·fencing·recovery 및 `FOR UPDATE SKIP LOCKED`: `PASS`
- G0-2 `PgVectorInfrastructureTest`: `PASS` (30개 중 28 PASS, 0 FAIL, 2 SKIP)
- SKIP 2건은 Windows에서 `SecureDirectoryStream`을 지원하지 않는 cleanup-worker 케이스다.
- TXT upload → ChangeLog → indexing → ACTIVE → search, PDF PAGE indexing → search,
  실제 Ollama `bge-m3` 1024차원, 사용자 A/B owner isolation, 실패한 새 version 발생 시
  기존 ACTIVE 유지 및 검색: 모두 `PASS`

`G0 FINAL: PASS`

### 검증 과정에서 확인한 환경·운영 계약

#### Patroni 시작

VM 재부팅 후 `opensql-etcd`는 active였고, Patroni는 설치되어 있었으나 auto-start가
disabled여서 OpenSQL direct `:5432`가 열리지 않았다. 복구에는 기존 관리 경로인
`systemctl start patroni`를 사용했다. Patroni auto-start 정책은 이번 작업에서 변경하지
않았다.

#### Fresh verification DB

`OpenSqlInfrastructureTest`는 fresh 상태의 검증 전용 DB를 요구한다. 사용 DB는
`prizm_integration_test`였으며, 실제 사용자 DB `prizm`은 사용하지 않았다. 실패한
Flyway 실행 흔적이 남아 있으면 해당 검증 DB만 안전하게 재생성한 뒤 다시 검증해야 한다.

#### vector extension bootstrap

V1 migration에는 `CREATE EXTENSION IF NOT EXISTS vector;`가 있다. 그러나
`prizm_owner`는 의도적으로 non-superuser이므로 fresh OpenSQL DB에서 최초 vector
extension을 생성할 권한이 없다. 신규 DB 준비 순서는 다음과 같다.

```text
Privileged DB administrator
→ CREATE EXTENSION vector

prizm_owner
→ Flyway V1~V15

prizm_app
→ application runtime
```

`prizm_owner`를 superuser로 승격하지 않는다.

### 역할 분리 계약

```text
Privileged administrator
→ DB infrastructure prerequisite
→ vector extension bootstrap

prizm_owner
→ Flyway / schema ownership

prizm_app
→ application runtime DML
```

`prizm_app`에는 필요 이상의 권한을 부여하지 않는다. 특히 Flyway metadata 접근 권한을
runtime에 추가하지 않으며, cleanup test를 이유로 production runtime DELETE 권한을
확대하지 않는다. test fixture/cleanup과 migration metadata 검증은 owner datasource를
사용하고, 실제 application 흐름은 runtime datasource를 사용하도록 테스트를 정리했다.

### 실제 OpenSQL과 기존 테스트 계약 차이

#### Flyway

기존 test helper 일부가 V14를 마지막 migration으로 고정하고 있었다. 현재 main의
V15 기준으로 current version은 14에서 15로, expected versions는 V1~V14에서 V1~V15로
test-only contract를 보정했다.

#### PostgreSQL major

기존 `PgVectorInfrastructureTest`는 PostgreSQL 16만 허용했다. 검증된 범위는
PostgreSQL 16(Testcontainers)과 PostgreSQL 17(OpenSQL)이며, 두 major만 명시적으로
허용한다.

#### pgvector

Testcontainers는 pgvector `0.8.2`, 실제 OpenSQL은 `0.8.1`을 사용한다. 두 환경 모두
필요한 `vector(1024)`, cosine 및 PRIZM E2E를 통과했으므로 테스트는 `0.8.1`과 `0.8.2`만
명시적으로 허용한다. 무제한 버전 허용으로 바꾸지 않는다.

### 코드 변경 범위

G0 과정의 production 코드 및 migration 변경은 0건이다. source code 변경은
integration-test contract 보정에만 한정되었다. 최종 `git diff --name-only` 기준 추적
변경 파일은 문서 기록을 포함해 다음 3개다.

- `src/integrationTest/java/com/prizm/infrastructure/OpenSqlRuntimePrivilegePreparation.java`
- `src/integrationTest/java/com/prizm/infrastructure/PgVectorInfrastructureTest.java`
- `docs/opensql-gate.md`

### 다음 Gate

```text
G0 OpenSQL direct baseline
→ VERIFIED

NEXT:
G1 OpenProxy single-primary SQL Gate

PRIZM runtime
→ OpenProxy :6432
→ current OpenSQL Primary :5432
```

이 단계에서는 단일 Primary SQL routing만 검증한다.

## 2026-08-14 OpenProxy 단일 Primary G1 검증

### 목적과 구성

G1은 runtime SQL 경로만 OpenProxy로 바꿔 실제 PRIZM 흐름을 검증한
단일 Primary Gate였다.

```text
Flyway / test maintenance
→ OpenSQL Primary direct :5432

PRIZM runtime (prizm_app)
→ OpenProxy :6432
→ current OpenSQL Primary :5432
```

OpenProxy 1.1.3의 기존 systemd unit과 config를 재사용했고 backend는 Primary
하나만 등록했다. config는 VM 내 관리 경로에서 `0600`을 유지하며
Git에 포함하지 않는다.

### 검증 결과

- OpenProxy `:6432` LISTEN과 Windows TCP: `PASS`
- `prizm_app` client/backend 인증: `PASS`
- OpenProxy → 검증 DB의 현재 Primary: `PASS`
- SELECT과 임시 INSERT/UPDATE/DELETE, rollback: `PASS`
- Flyway direct `:5432` / runtime proxy `:6432` 분리: `PASS`
- `PgVectorInfrastructureTest`: `28 PASS / 2 SKIP / 0 FAIL`
- TXT → ChangeLog → indexing → ACTIVE → search: `PASS`
- PDF PAGE indexing/search: `PASS`
- 실제 Ollama `bge-m3`, 1024 dimension: `PASS`
- owner isolation: `PASS`
- 실패한 새 version 발생 시 기존 ACTIVE 보존·검색: `PASS`
- OpenProxy restart 후 새 SQL connection: `PASS`
- 지속 실행 PRIZM/Hikari process의 무재시작 회복: `NOT_RUN`

SKIP 2건은 Windows에서 `SecureDirectoryStream`을 지원하지 않아 fail-closed하는
기존 cleanup-worker case다.

### credential과 권한 계약

로컬 Git-ignored `.env`와 실제 role credential의 불일치를 기존 관리자
socket에서 동기화했다. 변경 대상은 `prizm_app`과 `prizm_owner`의
비밀번호뿐이며 role 속성, object 소유권, runtime 권한 정책은 변경하지
않았다. `prizm_owner`를 runtime proxy credential로 사용하지 않았다.

### 판정과 범위 종료

```text
G1 OpenProxy single-primary SQL Gate
→ VERIFIED
```

대회 제공 OpenSQL은 공식 안내에 따라 Single 구성만 사용한다. 따라서 이 문서에는
다중 노드 후속 Gate를 두지 않는다. G1은 Replica 승격, Primary 장애 주입,
OpenProxy 이중화나 VIP를 검증한 결과가 아니다.
