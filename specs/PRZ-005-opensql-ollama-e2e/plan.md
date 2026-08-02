# PRZ-005 계획

> **문서 성격:** 이 문서는 구현 전에 작성한 계획이다. 실제 구현 과정에서 일부 방법이
> 변경됐다. 최종 적용 결과는 [작업 보고서](implementation-report.md)에서 확인한다.

## 계획 대비 변경 사항

- default privileges는 사용하지 않고 객체별 명시적 `GRANT`를 적용했다.
- 6개 시퀀스에는 `USAGE`만 부여했다.
- OpenProxy 인증과 SQL routing은 안전한 공식 구성을 확인하지 못해 보류했다.
- 핵심 E2E는 OpenSQL 직접 포트 `5432`로 완료했다.

## 기준선

- 기준 `main`: `f3591875f5b2df458db342bb1f46c80504acae64`
- 작업 branch: `PRZ-005-opensql-ollama-e2e`
- 계획 작성일: 2026-08-01

## 확인된 현재 환경

| 항목 | 읽기 전용 확인 결과 |
|---|---|
| VM | `PRIZM-OpenSQL`, 실행 중 |
| guest | Rocky Linux 9.7, hostname `prizm-opensql-01` |
| CPU topology | 1 socket, 4 cores/socket, 1 thread/core |
| Host-only 주소 | Windows에서 접근 가능한 고정 주소 사용 |
| OpenSQL 직접 포트 | `5432`, Windows TCP 연결 성공 |
| OpenProxy 대기 포트 | `6432`, Windows TCP 연결 성공. 기능은 `NOT_VERIFIED` |
| OpenSQL process | single-node postgres, patroni, etcd와 license checker 관찰 |
| 시간 | `System clock synchronized: no`, 약 19시간 지연 |
| journal | watchdog·core dump·corrupted journal 메시지 반복 관찰 |

전체 IP, JDBC URL, credential과 공급 설정은 공개 Evidence에 기록하지 않는다.

## 선택한 접근

### 1. 먼저 VM 건강 상태를 복구한다

OpenSQL과 인증 토큰의 시간 판단, 로그 순서와 검증 시각이 신뢰할 수 있으려면 VM 시간이
맞아야 한다. 다음 구현 단계의 첫 Gate는 chrony를 안전하게 정상화한 뒤
`System clock synchronized: yes`를 확인하는 것이다. system journal 반복 장애도 원인을
읽기 전용으로 확인하고, 로그 증거를 잃을 위험이 있으면 애플리케이션 검증을 시작하지 않는다.

### 2. 첫 연결은 OpenSQL 직접 포트로 제한한다

PRZ-003에서 검증한 single-node OpenSQL의 직접 포트 `5432`를 사용한다. Windows에서
`5432`와 `6432`의 TCP 연결은 모두 확인했지만, `6432`는 OpenProxy 기능 증거가 아니다.
OpenProxy 연동은 별도 Spec으로 남긴다.

### 3. 데이터베이스 역할을 분리한다

관리자 credential은 대화형 입력으로만 받아 다음 항목을 한 번 구성한다.

- 전용 database: `prizm`
- migration login: `prizm_owner`
- runtime login: `prizm_app`

정확한 이름이 이미 사용 중이면 기존 객체를 변경하지 않고 중단한다. 새 이름을 정한 뒤
Spec과 계획을 갱신한다. 역할은 `LOGIN`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`,
`NOREPLICATION`을 기본으로 한다. database owner는 migration 역할로 두고 runtime 역할에는
다음 최소 권한만 부여한다.

- database `CONNECT`
- schema `USAGE`
- PRIZM table의 `SELECT`, `INSERT`, `UPDATE`, `DELETE`
- PRIZM sequence의 `USAGE`, `SELECT`

`PUBLIC`의 schema `CREATE` 권한은 제거한다. Flyway 실행 전에 migration 역할의 default
privileges를 설정해 이후 생성되는 table·sequence 권한이 runtime 역할에 전달되게 한다.
V1의 `CREATE EXTENSION vector`가 관리자 권한을 요구하면 migration 역할을 superuser로
올리지 않는다. 관리자가 전용 DB에 extension만 선행 설치한 뒤 Flyway를 처음부터 다시
실행한다.

### 4. Windows 호스트에서 애플리케이션을 실행한다

첫 전체 흐름에서는 Docker 백엔드를 사용하지 않는다. Windows Spring Boot를 직접 실행해
네트워크 변수를 줄이고 다음 값을 process 환경변수로만 전달한다.

- `SPRING_PROFILES_ACTIVE=opensql`
- runtime JDBC URL·계정·비밀번호
- Flyway JDBC URL·계정·비밀번호
- `PRIZM_OLLAMA_BASE_URL=http://localhost:11434`
- `PRIZM_EMBEDDING_MODEL=bge-m3`
- `PRIZM_EMBEDDING_DIMENSIONS=1024`
- JWT와 일회성 demo `USER` bootstrap 값

전체 JDBC URL과 credential은 출력하지 않는다. demo 계정을 만든 뒤 bootstrap을
비활성화하고 서버를 재시작한다.

### 5. API와 브라우저 흐름을 검증한다

PRZ-004의 first-party 합성 TXT/PDF와 verifier를 재사용한다. 실제 OpenSQL 환경이라는 점만
달라지므로 소스나 migration을 바꾸지 않는다. 로그인, 업로드, `ACTIVE`, TXT/PDF 출처
검색, ownership와 로그아웃 뒤 `401`을 확인한다. 브라우저 시험은 별도 체크표로 기록한다.

## 예상 변경

| 범위 | 예상 변경 |
|---|---|
| Spec | PRZ-005 spec·plan·tasks·최종 evidence |
| VM | 시간 동기화 교정, 전용 DB와 두 login role 구성, host-only 접근 제한 확인 |
| 애플리케이션 | 원칙적으로 소스 변경 없음. 실제 차단 오류가 재현될 때만 PLAN으로 복귀 |
| migration | 변경 없음 |
| dependency·SBOM·license | 변경 없음. identity가 달라지면 compliance Gate로 복귀 |
| 공개 문서 | 검증 완료 후 project status와 roadmap만 현행화 |

## 구현 Batch

### Batch 1 — VM 사전 Gate

1. 임시 SSH 접근 방식을 다시 승인받고 작업 후 즉시 제거한다.
2. chrony와 시스템 시간을 정상화하고 재부팅 뒤에도 동기화를 확인한다.
3. journal 장애의 현재 상태와 로그 보존 영향을 확인한다.
4. OpenSQL process, 직접 포트와 host-only 방화벽·접근 규칙을 확인한다.
5. 실패 시 DB provisioning을 시작하지 않는다.

### Batch 2 — 전용 DB와 역할

1. DB 관리자 credential을 저장하지 않는 대화형 세션을 연다.
2. database·role 이름 충돌을 확인한다.
3. 전용 database와 migration/runtime 역할을 생성한다.
4. schema·default privileges와 runtime 최소 권한을 적용한다.
5. 관리자·migration·runtime 역할별 허용·거부 쿼리를 확인한다.
6. V1 extension 권한이 부족하면 관리자 선행 설치 방식으로만 처리한다.

### Batch 3 — SQL과 전체 사용자 흐름

1. OpenSQL opt-in integration test로 Flyway `V1`–`V13`과 SQL 계약을 재검증한다.
2. Ollama version, `bge-m3` identity와 1024차원을 확인한다.
3. Windows Spring Boot를 `opensql` profile로 실행한다.
4. demo `USER`를 일회성 생성하고 bootstrap을 비활성화해 재시작한다.
5. 합성 TXT/PDF의 로그인·업로드·`ACTIVE`·검색 API 흐름을 실행한다.
6. 브라우저에서 문서 상세·PDF 원문·검색·로그아웃 차단을 확인한다.

### Batch 4 — 검증·감사·통합 준비

1. 전체 백엔드·프론트엔드·OSS readiness·SBOM 검증을 실행한다.
2. credential·로컬 절대 경로·공급 자산이 tracked file에 없는지 검사한다.
3. OpenSQL과 PostgreSQL 결과, 미실행 OpenProxy·OpenHA 항목을 분리해 Evidence를 쓴다.
4. 독립 AUDIT 뒤 실제 GitHub 권한과 사용자 승인 범위에서만 통합한다.

## 검증 환경과 명령 범주

- Rocky Linux 9.7 VM: chrony, OpenSQL process·port, 역할과 권한 확인
- Windows host: Java 17, Spring Boot, Ollama와 API/browser 흐름
- 실제 OpenSQL: `OpenSqlInfrastructureTest`
- 공개 회귀: unit·integration test, 프론트엔드 lint·build·audit, OSS readiness와 SBOM

credential이 포함된 명령 전문은 Evidence에 복사하지 않는다. 각 검증은 종료 코드와
test·failure·error·skip 수만 공개한다.

## 위험과 대응

| 위험 | 대응 |
|---|---|
| VM 시간 지연 | 시간 동기화 전 provisioning·JWT·Evidence 실행 금지 |
| journal 반복 장애 | 로그 신뢰성 확인 전 장시간 시험 금지 |
| 기존 role/database 충돌 | 변경·비밀번호 덮어쓰기 없이 중단하고 이름 재계획 |
| extension 권한 부족 | runtime/migration 권한 확대 금지, 관리자 선행 설치 |
| host-only 범위 밖 DB 노출 | 방화벽·접근 규칙 확인 후 진행 |
| credential 노출 | 대화형 입력, process 환경변수, 출력 redaction, 임시 파일 금지 |
| OpenProxy 과장 | 6432는 포트 관찰로만 기록하고 runtime 검증에서 제외 |
| 공급 자산 노출 | archive·license·설정·원시 로그를 Git 밖 비공개로 유지 |

## 중단과 rollback 조건

- hostname, CPU topology, VM UUID 또는 라이선스 조건 변경이 필요하면 중단한다.
- 시간 동기화가 안정화되지 않거나 journal 장애가 검증 로그를 훼손하면 중단한다.
- 기존 database·role을 변경해야 하거나 최소 권한으로 Flyway/runtime을 분리할 수 없으면 중단한다.
- OpenSQL과 현재 migration의 비호환이 발견되면 우회하지 않고 PLAN으로 돌아간다.
- credential·공급 자산 노출 가능성이 생기면 즉시 중단하고 공개 diff와 history 영향을 감사한다.
- 실패 시 이번에 만든 전용 database와 역할만 정확히 식별해 사용자 승인 후 제거한다.

## Dependency·license

새 dependency, Docker image, model 또는 공급 파일을 저장소에 추가하지 않는다. OpenSQL은
외부 비공개 runtime이며 Ollama와 `bge-m3`도 사용자가 별도로 준비한다. 실제
model identity가 기존 provenance 기록과 다르면 조용히 갱신하지 않고 license·SBOM Gate로
돌아간다.

## Branch·PR 계획

- 임시 branch: `PRZ-005-opensql-ollama-e2e`
- 구현과 검증은 사용자 승인 후 같은 branch에서 단계별로 진행한다.
- 실제 Issue·PR·CI가 생긴 경우에만 Evidence에 기록한다.
- 최종 AUDIT와 사용자 승인 전에는 push·PR·merge·branch 삭제를 하지 않는다.
