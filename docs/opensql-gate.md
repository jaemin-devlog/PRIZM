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
| 대회용 테스트 라이선스 적용 | `PASS_PRIVATE_EVIDENCE` — 자산은 Git 밖에서 관리 |
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
- OpenHA 장애전환, RTO와 RPO

OpenProxy와 OpenHA는 이 단일 환경 Gate가 통과한 뒤 별도 spec으로 착수 여부를
판단한다.

## 실패·미확보 처리

- 재실행하지 못한 새 환경의 결과는 기존 환경의 `PASS`로 대체하지 않고
  `NOT_RUN`으로 기록한다.
- 라이선스나 재배포 조건이 불명확하면 구성요소를 저장소에 포함하지 않는다.
- 실패 결과에는 migration 번호, SQLState와 SQL 기능 범주만 기록한다.
- 전체 JDBC URL, host, 계정과 비밀번호는 출력하지 않는다.
- OpenSQL이 없으면 PostgreSQL 16+pgvector 회귀 test를 유지하되 별도 결과로 기록한다.

현재 구현은 [현재 구현 현황](project-status.md), 설치 환경과 실행 결과는
[PRZ-003 Evidence](../specs/PRZ-003-opensql-single-node-gate/evidence.md)를 기준으로 한다.
