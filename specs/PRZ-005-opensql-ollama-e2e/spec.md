# PRZ-005: OpenSQL·Ollama 전체 사용자 흐름

## 상태

`VERIFIED`

[최종 구현·검증 결과](implementation-report.md)

## 목적

Windows에서 실행한 PRIZM Spring Boot 서버를 Rocky Linux 9.7 VM의 실제 OpenSQL
single-node 데이터베이스와 호스트 Ollama `bge-m3`에 연결한다. 안전한 demo `USER`가
로그인한 뒤 합성 TXT/PDF를 업로드하고, 문서가 `ACTIVE`가 된 후 원문 근거를 검색하는
전체 흐름을 재현한다.

이 Spec은 PRZ-003의 SQL 호환성 검증과 PRZ-004의 PostgreSQL clean-clone 검증을
결합한 새로운 환경 검증이다. 두 과거 결과만으로 이 흐름을 `PASS`라고 판단하지 않는다.

## 범위

- Rocky Linux 9.7 VM의 OpenSQL single-node 직접 포트 연결
- PRIZM 전용 데이터베이스와 Flyway/runtime 역할 분리
- Flyway `V1`–`V13` 적용과 Hibernate schema validation
- Windows Spring Boot와 호스트 Ollama `bge-m3` 연동
- 기본 비활성화된 demo `USER`의 일회성 생성과 재비활성화
- 합성 TXT/PDF 업로드, 비동기 색인, `ACTIVE` 전환과 원문 출처 검색
- 사용자 ownership, 인증과 실패 version 제외 계약 확인
- 실제 환경, 명령, 결과와 비공개 경계 기록

## 요구사항

### PRZ-005-R01 — 환경 기준선과 시간 동기화

- VM hostname, Rocky Linux 버전, CPU topology와 고정 Host-only 주소를 바꾸지 않는다.
- OpenSQL 라이선스가 묶인 VM identity와 공급 자산을 복제하거나 공개 저장소에 넣지 않는다.
- 실행 전에 `System clock synchronized: yes`와 정상적인 chrony 상태를 확인한다.
- system journal이 반복해서 중단되는 현상은 로그 증거의 신뢰성에 미치는 영향을 먼저 확인한다.

### PRZ-005-R02 — 네트워크와 endpoint 경계

- 첫 전체 흐름은 Windows에서 VM의 OpenSQL 직접 포트 `5432`로 연결한다.
- Host-only 네트워크의 Windows 호스트만 데이터베이스에 접근하도록 제한한다.
- OpenProxy `6432`는 별도 기능 검증 전까지 PRIZM runtime endpoint로 사용하지 않는다.
- TCP 연결 성공만으로 OpenProxy 호환성이나 장애전환을 주장하지 않는다.

### PRZ-005-R03 — 전용 데이터베이스와 최소 권한 역할

- PRIZM 전용 데이터베이스를 사용한다.
- Flyway migration 역할과 애플리케이션 runtime 역할을 서로 분리한다.
- migration 역할만 schema DDL을 수행하며 runtime 역할에는 테이블 DDL 권한을 주지 않는다.
- runtime 역할은 PRIZM schema의 연결·사용과 필요한 DML·sequence 권한만 가진다.
- `PUBLIC`의 불필요한 schema 생성 권한을 제거한다.
- 계정이 이미 존재할 때 비밀번호나 권한을 조용히 덮어쓰지 않고 중단한다.
- 관리자·DB·JWT·demo credential은 파일, Git, 명령 이력과 로그에 남기지 않는다.

### PRZ-005-R04 — 실제 애플리케이션 전체 흐름

- `opensql` Spring profile이 runtime과 Flyway JDBC URL을 명시적으로 받는다.
- `bge-m3`의 실제 모델 identity와 1024차원 embedding을 확인한다.
- demo bootstrap은 한 번만 활성화하고 계정 생성 후 다시 비활성화한다.
- 로그인, 합성 TXT/PDF 업로드, `ACTIVE` polling과 검색을 실행한다.
- TXT 결과는 `TEXT_CHUNK`, PDF 결과는 `PAGE`와 유효한 page 번호를 포함한다.
- 로그아웃 뒤 보호 API가 차단되고 다른 사용자의 문서·검색 결과가 노출되지 않는다.

### PRZ-005-R05 — 구현 계약 보존

- 적용된 Flyway migration을 수정하지 않는다.
- 실패하거나 완료되지 않은 version은 검색 대상이나 `ACTIVE`가 되지 않는다.
- 기존 active version, Worker lease·recovery·claim-version fencing과 안전한 파일 정리를 보존한다.
- PostgreSQL, OpenSQL, OpenProxy와 OpenHA 결과를 서로 바꾸어 기록하지 않는다.

### PRZ-005-R06 — 증거와 공개 경계

- 실제 source commit, VM 환경, OpenSQL 직접 endpoint 범주, Ollama/model identity와 검증 결과를 기록한다.
- 비밀번호, 전체 JDBC URL, 공급 archive·라이선스·설정·로그와 host inventory는 공개하지 않는다.
- `PASS`, `FAIL`, `SKIPPED`, `NOT_RUN`과 `NOT_VERIFIED`를 실제 결과대로 구분한다.
- GitHub Issue·PR·CI·review가 실제로 존재할 때만 링크한다.

## 보존 계약

- JWT 인증과 DB 사용자 재검증
- owner-scoped 문서·version·job·chunk·검색 경계
- immutable version과 원본 파일 보존
- 성공한 version만 원자적으로 활성화하는 계약
- `bge-m3` 1024차원 및 finite·non-zero vector 검증
- OpenSQL 공급 자산 비공개·비재배포 경계

## 제외 범위

- OpenProxy runtime 호환성
- OpenHA와 다중 노드 구성
- DB failover, RTO와 RPO
- 물리 호스트 장애 복구
- MCP, 변경 로그 동기화, CareerFact와 portfolio
- OpenSQL, Ollama model 또는 DB volume의 저장소·이미지 포함
- 제품 migration, dependency, SBOM 또는 license identity 변경

## 측정 가능한 완료 조건

1. VM 시간 동기화와 호스트 상태의 차단 문제가 해소된다.
2. Windows에서 실제 OpenSQL `5432` 연결과 전용 DB의 분리된 역할 권한을 확인한다.
3. Flyway `V1`–`V13`과 OpenSQL opt-in integration test가 실패·오류·skip 0건으로 통과한다.
4. Windows Spring Boot가 OpenSQL과 호스트 Ollama를 사용해 demo `USER` 전체 API 흐름을 통과한다.
5. 브라우저에서 로그인, 문서 상세, PDF 원문, 검색과 로그아웃 후 차단을 확인한다.
6. credential과 공급 자산이 Git·공개 로그에 포함되지 않았음을 확인한다.
7. PostgreSQL 결과와 OpenSQL 결과, 실행 항목과 미실행 항목을 Evidence에서 분리한다.

완료 조건 3의 OpenSQL opt-in integration test는 D5·D6 증거가 있는 `prizm` DB를 변경하지
않고, 빈 전용 `prizm_integration_test` DB에 `V1`–`V13`과 테스트 전용 최소 권한을 적용해
실패·오류·skip 0건으로 통과했다.
