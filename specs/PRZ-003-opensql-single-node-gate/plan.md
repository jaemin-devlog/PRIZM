# PRZ-003 — OpenSQL 단일 노드 Gate Plan

> **문서 상태:** `VERIFIED`
> **계획 기준선:** PRZ-003 구현 전 `main`
>
> 구현 전 계획을 보존한다. 결과는 [Tasks](tasks.md)와 [Evidence](evidence.md)를
> 따른다.

## P1. 단일 노드 환경 준비

- 목표: 지원된 Rocky Linux 9.7 x86_64 single-node OpenSQL 환경을 만든다.
- 변경 범위: VirtualBox guest, NAT·Host-only 분리, NTP와 license identity.
- 검증: hostname·CPU topology, 고정 VM identity와 비공개 network를 확인한다.
- Rollback: 공급사 지침을 우선하고 일부 provisioning 대상은 재사용하지 않는다.
- 중단 조건: 지원 OS·topology가 다르거나 공급 asset·credential이 노출되면
  중단한다.

## P2. 전용 DB와 SQL Gate

- 목표: Windows host에서 OpenSQL direct DB 호환성을 검증한다.
- 변경 범위: 빈 database, Flyway owner, 최소 runtime role과 opt-in test.
- 검증: Flyway·vector·owner·ACTIVE 검색, Worker·cleanup SQL을 실제 OpenSQL에서
  확인한다.
- Rollback: 실패 target은 보존해 원인을 확인하고 새 database·credential로 재시도한다.
- 중단 조건: OpenSQL·vector·권한 준비가 확인되지 않으면 Gate를 시작하지 않는다.

## P3. 플랫폼 회귀

- 목표: Windows UTF-8과 Linux 안전 삭제 경계를 확인한다.
- 변경 범위: file read encoding assertion과 환경별 test.
- 검증: Windows에서 UTF-8을 명시하고 Linux `SecureDirectoryStream` 성공 경로와
  Windows fail-closed를 분리한다.
- Rollback: UTF-8 교정에 문제가 있으면 해당 assertion만 되돌린다.
- 중단 조건: platform skip을 `PASS`로 바꿔야 하면 중단한다.

## P4. 감사와 통합

- 목표: 공개·비공개 근거와 GitHub 통합을 감사한다.
- 변경 범위: Evidence, Registry와 실제 PR·CI 기록.
- 검증: 임시 DB·role·SSH key·helper 제거와 공개 diff를 확인한다.
- Rollback: 민감한 원시 근거는 Git 밖에 보존한다.
- 중단 조건: 설치 성공을 SQL Gate나 OpenProxy 검증으로 확대하면 중단한다.

## 공통 위험과 대응

- OpenProxy·OpenHA·failover는 제외한다.
- credential은 공개 file·command line·log에 남기지 않는다.
- PostgreSQL·pgvector 성공은 OpenSQL 성공을 대체하지 않는다.

## Dependency 및 license 영향

- VirtualBox, Rocky Linux와 OpenSQL은 저장소 외부 환경이다.
- 공급 archive, license, fingerprint와 내부 metadata는 공개하지 않는다.

## Branch와 통합 경계

- 실제 Issue·PR만 기록하며 과거 기록을 소급 생성하지 않는다.

## 계획 대비 주요 변경

- Windows UTF-8과 Linux 안전 삭제 경계를 실제 platform별로 보완했다.
