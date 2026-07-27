# PRZ-003: OpenSQL 단일 노드 검증 환경

## 상태

`IN_PROGRESS`

## 목적

연구실 Windows 호스트에 Rocky Linux 9 VirtualBox 게스트 한 대를 만들고 PRIZM의
전용 OpenSQL 검증 대상으로 사용한다. 이 환경은 티맥스티베로가 확인한 테스트
라이선스 조건, 즉 게스트 hostname 및 vCPU/core/thread 값을 신청서에 사용하고,
고정(비 DHCP) 주소와 시간 동기화를 확인하는 조건을 따른다.

## 평가 연계

- 주: `EVAL-R1-01` — 재현 가능하고 격리된 OpenSQL 검증 대상
- 보조: `EVAL-R1-03` — 설정 과정, 명령, 환경 근거의 문서화

## 요구사항

1. 호스트에는 Oracle VirtualBox를 사용하고, 최초 OpenSQL 게스트는 정확히 한 대인
   `PRIZM-OpenSQL` / `prizm-opensql-01`로 둔다.
2. 게스트는 Rocky Linux 9 x86_64, 4 vCPU, 12 GiB RAM, 동적 할당 120 GiB
   가상 디스크를 사용한다.
3. 게스트에는 패키지 접근용 NAT와 고정 주소를 사용하는 Host-only 어댑터를 둔다.
   OpenSQL 서비스를 공용 인터넷에 노출하지 않는다.
4. 라이선스 신청 전에 게스트의 hostname 및 실제 CPU/core/thread 값을 기록한다.
   티맥스티베로의 별도 안내가 없는 한 신청한 구성을 유지한다.
5. 라이선스 적용 전에 게스트 시간 동기화를 확인한다.
6. 첫 실행 Gate는 새 전용 OpenSQL 데이터베이스 또는 스키마에서 Flyway,
   `vector(1024)`, cosine 검색, 소유자/active-version 제약, Worker SQL 호환성을
   검증한다. 이 Gate는 OpenProxy 또는 OpenHA 호환성을 주장하지 않는다.

## 보존 계약

- PostgreSQL/pgvector는 노트북의 로컬 개발 환경으로 유지한다.
- `compose.yaml`은 PostgreSQL 로컬 개발 구성으로 유지한다.
- runtime JDBC와 Flyway JDBC endpoint는 별도 입력이며, 대상 환경에서 입증되기 전에는
  동일하다고 가정하지 않는다.
- source, migration, 실행 가능한 test가 구현 사실의 기준이다.

## 제외 범위

- `prizm-app-01`, OpenProxy, OpenHA, 다중 노드 장애 조치, Worker/Ollama 애플리케이션
  통합, OpenSQL Compose override는 이 작업 범위 밖이다.
- 이 환경 준비만으로 OpenSQL 호환성이 입증되는 것은 아니다. 실제 Gate 실행이 필요하다.

## 완료 조건

1. VirtualBox가 설치되어 있고 게스트 구성이 요구사항 1~3과 일치한다.
2. 게스트 출력에 hostname, CPU/core/thread, 고정 IP, 동기화된 시간이 기록되어 있다.
3. 라이선스 신청서는 Windows 호스트 값이 아니라 기록된 게스트 값을 사용한다.
4. `OpenSqlInfrastructureTest`는 OpenSQL과 전용 test credential이 준비된 뒤에만
   실행한다. 그 전까지 결과는 `NOT_RUN`으로 기록한다.
