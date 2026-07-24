# OpenSQL 단일 환경 기술 Gate

> 상태: `NOT_RUN` (2026-07-23)

이 문서는 실제 OpenSQL 환경을 확보하기 전까지 사용하는 임시 체크리스트다. 실제
작업을 시작하면 다음 available spec ID의 OpenSQL Gate `spec.md`, `plan.md`,
`tasks.md`, `evidence.md`로 내용을 옮기고 이 파일은 제거한다.

PostgreSQL 성공을 OpenSQL 성공으로 바꾸어 표현하지 않는다.

## 완료 기준

다음 조건을 모두 실제 OpenSQL 환경에서 확인해야 `PASS`로 바꿀 수 있다.

1. OpenSQL과 pgvector가 설치된 검증 전용 DB 또는 schema에 JDBC로 접속한다.
2. Flyway V1~V13 migration을 순서대로 적용한다.
3. `vector(1024)`, `vector_dims`, exact cosine `<=>`를 실제 실행한다.
4. `VectorSearchRepository`의 owner·ACTIVE version 조건을 검증한다.
5. Indexing·Cleanup claim, lease, fencing, recovery와 두 connection의
   `FOR UPDATE SKIP LOCKED`를 검증한다.
6. 설치 버전, 실행 명령, 결과와 한계를 비밀정보 없이 `evidence.md`에 기록한다.

## 실행 전 확인

| 항목 | 상태 |
|---|---|
| OpenSQL 설치 파일·버전·체크섬 | 미확인 |
| 개발·시연·재배포 라이선스 | 미확인 |
| pgvector 제공 버전 | 미확인 |
| 검증 전용 DB/schema | 미확인 |
| Flyway 계정과 runtime 계정 | 미확인 |
| TLS·네트워크 요구사항 | 미확인 |

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
.\gradlew.bat integrationTest --no-daemon --tests com.prizm.infrastructure.OpenSqlInfrastructureTest
```

`RUN_OPENSQL_TESTS`가 없으면 test는 `SKIPPED`이며 성공 증거가 아니다. Suite는
Flyway가 만든 비활성 UUID marker를 runtime 계정이 같은 ID로 읽어야만 fixture를
시작한다. 전역 `DELETE`나 `TRUNCATE` 없이 실행별 UUID와 생성 ID만 정리한다.

## 현재 Gate에 포함하지 않는 것

- Ollama와 실제 embedding 생성
- Indexing·Cleanup Scheduler 실행과 실제 파일 삭제
- HNSW 성능
- OpenProxy runtime 연결
- OpenHA 장애전환, RTO와 RPO

OpenProxy와 OpenHA는 이 단일 환경 Gate가 통과한 뒤 별도 spec으로 착수 여부를
판단한다.

## 실패·미확보 처리

- 실제 환경을 확보하지 못하면 `NOT_RUN`을 유지한다.
- 라이선스나 재배포 조건이 불명확하면 구성요소를 저장소에 포함하지 않는다.
- 실패 결과에는 migration 번호, SQLState와 SQL 기능 범주만 기록한다.
- 전체 JDBC URL, host, 계정과 비밀번호는 출력하지 않는다.
- OpenSQL이 없으면 PostgreSQL 16+pgvector 회귀 test를 유지하되 별도 결과로 기록한다.

현재 구현과 환경별 검증 상태는 [현재 구현 현황](project-status.md)과
[PRZ-000 Evidence](../specs/PRZ-000-platform-baseline/evidence.md)를 기준으로 한다.
