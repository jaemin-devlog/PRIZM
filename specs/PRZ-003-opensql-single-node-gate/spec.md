# PRZ-003: OpenSQL 단일 노드 검증 환경

## 상태

`VERIFIED`

## 목적

연구실 Windows 호스트에 Rocky Linux 9.7 VirtualBox 게스트 한 대를 만들고
PRIZM의 전용 OpenSQL 검증 대상으로 사용한다. 공급사의 서면 승인과 환경 조건은
비공개 원본으로 보존하고, 공개 저장소에는 고정된 라이선스 귀속 환경·비공개
네트워크·시간 동기화라는 재현 조건과 검증 결과만 기록한다.

## 요구사항

1. 호스트에는 Oracle VirtualBox를 사용하고 OpenSQL 게스트는 한 대만 둔다.
   라이선스에 귀속된 hostname과 VM 식별자는 고정하되 공개 저장소에는 기록하지 않는다.
2. 게스트는 공급사가 지정한 Rocky Linux 9.7 x86_64와 고정된 CPU topology를
   사용한다. 정확한 라이선스 귀속 값과 로컬 경로는 비공개 근거로 보존한다.
3. 게스트에는 패키지 접근용 NAT와 고정 주소를 사용하는 Host-only 어댑터를 둔다.
   실제 주소와 host 목록은 공개하지 않고 OpenSQL 서비스를 공용 인터넷에 노출하지 않는다.
4. 라이선스 신청 전에 게스트의 hostname 및 실제 CPU/core/thread 값을 기록한다.
   공급사의 별도 안내가 없는 한 신청한 구성을 유지하며 정확한 값은 Git 밖에 둔다.
5. 라이선스 적용 전에 게스트 시간 동기화를 확인한다.
6. 첫 실행 Gate는 새 전용 OpenSQL 데이터베이스 또는 스키마에서 Flyway,
   `vector(1024)`, cosine 검색, 소유자/active-version 제약, Worker SQL 호환성을
   검증한다. 이 Gate는 OpenProxy 또는 OpenHA 호환성을 주장하지 않는다.
7. 공급된 OpenSQL 자산은 허가된 `single` 구성에만 사용한다. 설치 파일,
   라이선스, fingerprint, 내부 metadata·설정·log를 복제하거나 Git에 넣지 않는다.
8. 통합 전 Windows 회귀 Gate는 JVM 기본 문자셋에 기대지 않고 저장된 TXT를
   UTF-8로 명시해 검증한다. 실행되지 않은 테스트는 이유와 필요한 환경을
   분류하며, `NOT_RUN` 또는 플랫폼 제약을 `PASS`로 기록하지 않는다.

## 보존 계약

- PostgreSQL/pgvector는 노트북의 로컬 개발 환경으로 유지한다.
- `compose.yaml`은 PostgreSQL 로컬 개발 구성으로 유지한다.
- runtime JDBC와 Flyway JDBC endpoint는 별도 입력이며, 대상 환경에서 입증되기 전에는
  동일하다고 가정하지 않는다.
- source, migration, 실행 가능한 test가 구현 사실의 기준이다.

## 제외 범위

- App VM, OpenProxy 구성·인증·호환성 검증, OpenHA, 다중 노드 장애 조치,
  Worker/Ollama 애플리케이션 통합과 OpenSQL Compose override는 이 작업 범위 밖이다.
- 이 환경 준비만으로 OpenSQL 호환성이 입증되는 것은 아니다. 실제 Gate 실행이 필요하다.

## 완료 조건

1. VirtualBox가 설치되어 있고 게스트 구성이 요구사항 1~3과 일치한다.
2. hostname, CPU topology, 고정 IP와 시간 동기화는 비공개 운영 근거로 확인한다.
3. 라이선스 신청서는 Windows 호스트 값이 아니라 확인한 게스트 값을 사용한다.
4. 공급 자산과 라이선스는 로컬에서만 검증하고 정확한 식별자, fingerprint,
   credential과 log는 Git 밖에 둔다.
5. OpenSQL `single` 설치 완료와 기본 DB 연결 결과를 비식별 상태로 기록하되
   PRIZM 호환성 성공으로 표현하지 않는다.
6. `OpenSqlInfrastructureTest`는 OpenSQL과 전용 test credential이 준비된 뒤에만
   실행한다. 그 전까지 결과는 `NOT_RUN`으로 기록한다.
7. 별도 `JAVA_TOOL_OPTIONS`나 `file.encoding` 강제 없이 문서화된 Windows
   통합 테스트 명령이 UTF-8 TXT 저장 검증을 포함해 통과한다.
8. 전체 통합 테스트의 실패와 건너뜀 수를 다시 확인하고, OpenSQL opt-in Gate와
   `SecureDirectoryStream` 의존 경로를 실행 환경별로 구분해 근거에 기록한다.
