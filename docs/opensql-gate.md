# OpenSQL 기술 Gate

이 문서는 로컬 PostgreSQL 개발 결과를 OpenSQL 3 환경으로 옮기기 전에 확인할 외부 의존성과 증거를 기록한다. 체크되지 않은 항목을 추정으로 완료 처리하지 않는다.

## 완료 기준

다음 조건을 모두 만족해야 OpenSQL Gate를 통과한 것으로 본다.

- OpenSQL과 pgvector를 설치한 실제 환경에 JDBC로 접속할 수 있다.
- 문장 임베딩을 `vector(1024)`로 저장하고 exact cosine 검색 결과를 확인한다.
- 런타임 애플리케이션 JDBC URL이 OpenProxy를 가리킨다.
- OpenHA/DCS 토폴로지와 Primary 장애 시연 절차가 확정되어 있다.
- 설치 버전, 실행 명령, 결과 로그가 재현 가능한 형태로 남아 있다.

## 1. 제공물과 권한

| 항목 | 확인 내용 | 상태 | 증거/메모 |
|---|---|---|---|
| OpenSQL 3 설치 파일 | 배포 파일명, 체크섬, 제공 주체 | 미확인 | |
| 사용 권한/라이선스 | 개발·시연·배포 가능 범위 | 미확인 | |
| OpenProxy | 바이너리와 설정 예제 제공 여부 | 미확인 | |
| OpenHA/Patroni | 바이너리와 운영 권한 | 미확인 | |
| etcd 또는 witness | 최소 쿼럼 구성 가능 여부 | 미확인 | |
| OpenCrypto | ARIA/SEED 모듈 제공 여부 | 조건부 | 미제공 시 MVP에서 제외 |
| 장애 시연 권한 | Primary 종료와 승격 로그 수집 허용 여부 | 미확인 | |

## 2. 환경과 버전

실제 명령 출력 전체를 증거 파일 또는 실행 기록에 보관한다.

| 항목 | 기대 기준 | 실제 값 | 상태 |
|---|---|---|---|
| OS | 공식 지원 Linux 배포판 | | 미확인 |
| OpenSQL | 3.x | | 미확인 |
| PostgreSQL | OpenSQL 패키지 제공 버전 | | 미확인 |
| pgvector | 설치 여부와 `extversion` | | 미확인 |
| OpenProxy | 버전과 listen 주소 | | 미확인 |
| Patroni/OpenHA | 버전과 cluster name | | 미확인 |
| etcd/DCS | 버전과 member 수 | | 미확인 |
| JDBC driver | Gradle resolved version | | 미확인 |

확인 명령 예시:

```sql
SELECT version();
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

SELECT vector_dims(array_fill(0::real, ARRAY[1024])::vector);
SELECT '[1,0]'::vector <=> '[1,0]'::vector AS cosine_distance;
```

런타임 JDBC와 별도로 확정한 Flyway 경로에 환경변수를 주입한 뒤 다음 smoke test로 확인한다.

```powershell
$env:RUN_OPENSQL_TESTS='true'
.\gradlew.bat integrationTest --no-daemon --tests com.prizm.infrastructure.OpenSqlInfrastructureTest
```

## 3. 연결과 역할

| 항목 | 성공 조건 | 상태 | 증거/메모 |
|---|---|---|---|
| Migration 연결 | 검증된 migration endpoint에서 Flyway migration 성공 | 미확인 | OpenProxy DDL 지원 여부 확인 후 endpoint 확정 |
| Runtime 연결 | 애플리케이션 계정으로 CRUD 가능 | 미확인 | |
| 역할 분리 | Runtime 계정에 DDL·`BYPASSRLS` 권한 없음 | 미확인 | |
| OpenProxy 경유 | 런타임 애플리케이션이 직접 Primary 주소 없이 기동 | 미확인 | |
| TLS/네트워크 | 요구되는 SSL mode와 방화벽 규칙 확정 | 미확인 | |
| Connection pool | timeout과 pool 크기 기준 기록 | 미확인 | |

민감한 JDBC URL, 사용자명, 비밀번호, 호스트 목록은 이 문서에 직접 기록하지 않는다.

## 4. pgvector 호환성 PoC

다음 순서만 수행하며 도메인 기능 구현과 분리한다.

1. `vector` extension 존재와 버전을 확인한다.
2. 1024차원 테스트 벡터를 저장한다.
3. `<=>` 연산자로 exact cosine 검색을 실행한다.
4. 문서 상태와 `active_version_id`에 해당하는 일반 컬럼 필터를 함께 적용한다.
5. 실행 계획과 소요 시간을 저장한다.
6. 로컬 PostgreSQL 결과와 상위 결과 ID가 일치하는지 확인한다.

HNSW 인덱스는 이 Gate의 완료 조건이 아니다. 10,000청크 exact 검색이 성능 목표를 넘을 때만 별도 Gate로 연다.

## 5. OpenHA/OpenProxy 토폴로지

| 확인 항목 | 상태 | 증거/메모 |
|---|---|---|
| Primary/Standby 노드 수와 주소 | 미확인 | |
| etcd/DCS member 수와 쿼럼 | 미확인 | |
| Patroni leader 조회 방법 | 미확인 | |
| OpenProxy backend 상태 조회 방법 | 미확인 | |
| 애플리케이션 접속 endpoint | 미확인 | 비밀값은 별도 보관 |
| Primary 중지 명령과 복구 명령 | 미확인 | |
| 실패 시 수동 복구 담당자 | 미확인 | |

두 데이터 노드만으로 안전한 자동 선출이 끝난다고 가정하지 않는다. witness 또는 3개 DCS member 구성을 제공 환경의 공식 지침으로 확인한다.

## 6. 장애 실험 진입 조건

- 합성 문서만 사용한다.
- 정상 상태 검색이 최소 3분 동안 안정적으로 성공한다.
- 마지막 committed ID를 기록할 수 있다.
- Primary 중지와 복구 명령을 사전 리허설한다.
- OpenHA, OpenProxy, 애플리케이션 시간을 같은 표준시로 동기화한다.
- CSV와 JSON 결과를 저장할 쓰기 경로를 준비한다.

실험 결과에는 장애 감지 시각, 승격 시각, 첫 연속 성공 5건 시각, RTO, 총 요청 수, 최종 성공률, 정상·장애 구간 p95, 마지막 committed ID 기반 RPO를 포함한다.

## 7. 미확보 시 처리

- OpenSQL이 없으면 PostgreSQL 16 + pgvector에서 자동 통합 테스트를 계속한다.
- OpenProxy/OpenHA가 없으면 로컬 PostgreSQL 결과를 OpenSQL HA 결과로 표현하지 않는다.
- 7월 17일까지 환경을 확보하지 못하면 지원 요청과 대체 PoC를 별도 기록한다.
- 8월 15일까지 HA 환경이 없으면 제출물에 환경 의존 미완료를 명시하고 기능 범위를 더 늘리지 않는다.
