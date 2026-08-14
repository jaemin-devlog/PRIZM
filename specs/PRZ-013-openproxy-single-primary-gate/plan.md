# PRZ-013 — OpenProxy 단일 Primary SQL Gate Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** `main` `a65f91d`

## P1. 환경과 기존 구성 확인

- OpenSQL Primary, OpenProxy 설치·unit·config·listen·backend·인증 구조를 읽기
  전용으로 확인한다.
- 기존 G0 source·문서 변경과 로컬 untracked 메모를 보존한다.
- 중단 조건: Primary direct SQL이 비정상이거나 backend 대상이 불명확하다.

## P2. OpenProxy 최소 구성

- 기존 config를 권한 보존 백업한다.
- 검증 전용 DB의 `prizm_app` 단일 pool로 최소 수정하고 `0600`을 유지한다.
- 관리자·owner 계정, Replica와 VIP를 추가하지 않는다.
- 정적 검증 실패 시 OpenProxy를 시작하지 않고 백업으로 복구한다.

## P3. TCP와 SQL Gate

- OpenProxy만 시작해 Guest·Windows에서 `:6432` LISTEN/TCP를 확인한다.
- `prizm_app`으로 current user와 database, SELECT와 제한된 test fixture
  INSERT/UPDATE/DELETE를 확인한다.
- SQL 실패 시 E2E로 넘어가지 않는다.

## P4. PRIZM focused E2E

- Flyway는 direct `:5432`, Spring runtime은 OpenProxy `:6432`로 실행한다.
- TXT/PDF 색인·ACTIVE·검색, owner isolation과 실패 version 보존을 focused
  integration test로 확인한다.
- G0-1과 전체 integration suite는 재실행하지 않는다.

## P5. 재시작 회복과 감사

- 안전하면 실행 중 OpenProxy만 재시작하고 애플리케이션 재시작 없이 새 연결 회복을
  확인한다.
- config 권한·민감정보·backend 단일성, repository diff와 결과 범위를 감사한다.
- 다중 노드 구성과 failover는 실행하지 않는다.

## Rollback 및 중단 조건

- 변경 전 config 백업을 보존하고 OpenProxy 정지 후 원래 config로 되돌릴 수 있게 한다.
- 인증이 평문 config 이외에 확장되거나 관리자 권한이 필요하면 중단한다.
- production code, migration, 권한 정책 변경이 필요하면 G1 `FAIL`로 종료한다.

## PLAN Gate

PRZ-013-R1–R7이 P1–P5에 연결됐고 rollback과 보안 경계가 명시됐다.

판정: `PASS`
