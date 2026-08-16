# PRZ-013 — OpenProxy 단일 Primary SQL Gate

> **상태:** `VERIFIED`
> **유형:** Infrastructure verification
> **선행 문서:** [PRZ-005](../PRZ-005-opensql-ollama-e2e/spec.md)
> **기준 소스:** `a65f91d`
> **검증일:** 2026-08-14

## 목적

실제 연구실 OpenSQL 단일 Primary 앞에 OpenProxy를 두고 PRIZM runtime SQL과
TXT/PDF 검색 흐름이 동작하는지 검증한다. Flyway는 OpenSQL direct `:5432`를
유지하고 runtime만 OpenProxy `:6432`를 사용한다.

## 범위

- 기존 OpenProxy 1.1.3 설치·systemd unit·single-primary config를 재사용한다.
- 검증 전용 DB와 `prizm_app`만 OpenProxy pool에 연결한다.
- `:6432` TCP, `prizm_app` SQL SELECT/WRITE, Spring runtime E2E와 owner isolation을
  실제 환경에서 검증한다.
- 안전하면 OpenProxy만 재시작하고 기존 애플리케이션 연결이 새 연결로 회복되는지
  확인한다.

## 제외 범위

- 다중 DB node, Primary 장애 주입, Replica와 승격, etcd·Patroni topology 변경
- VIP/VRRP, OpenProxy·PRIZM 이중화, MCP
- production code, migration, 검색·권한 정책 변경
- Flyway를 OpenProxy로 라우팅하는 구성

## 보안 및 ownership 계약

- OpenProxy runtime 계정은 `prizm_app`만 사용한다.
- `postgres`, 관리자와 `prizm_owner`를 runtime proxy credential로 사용하지 않는다.
- OpenProxy 1.1.3이 요구하는 runtime 비밀번호는 VM의 `opensql:opensql` 소유
  `0600` config에만 저장하고 로그·Git·명령 기록에 출력하지 않는다.
- 실제 application 흐름은 `prizm_app`, Flyway와 migration metadata는 direct
  `:5432`의 `prizm_owner` datasource를 사용한다.
- 기존 owner isolation과 최소 runtime 권한을 변경하지 않는다.

## 요구사항 및 완료 조건

- `PRZ-013-R1`: OpenProxy가 `:6432`에서 LISTEN하고 현재 Primary `:5432` 하나만
  backend로 사용한다.
- `PRZ-013-R2`: `prizm_app` 인증으로 실제 SELECT와 정상 runtime WRITE가 성공한다.
- `PRZ-013-R3`: PRIZM runtime datasource는 `:6432`, Flyway datasource는 direct
  `:5432`를 사용한다.
- `PRZ-013-R4`: TXT upload → ChangeLog → indexing → ACTIVE → search가 성공한다.
- `PRZ-013-R5`: 사용자 A/B owner isolation과 실패 version의 기존 ACTIVE 보존이
  유지된다.
- `PRZ-013-R6`: 가능한 경우 OpenProxy 재시작 뒤 애플리케이션 재시작 없이 새 DB
  연결이 회복된다. 환경상 안전하지 않으면 `NOT_RUN`으로 분리한다.
- `PRZ-013-R7`: production code와 migration 변경은 0건이고 `git diff --check`가
  통과한다.

## SPEC Gate

단일 Primary routing, datasource 분리, 인증·ownership과 다중 노드 제외 범위가 실행
결과로 판정 가능하다.

판정: `PASS`
